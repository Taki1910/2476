import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { compile, createRenderer, nextTick, ssrContextKey, type App, type Component, type ComponentOptions } from 'vue'
import { parse } from 'vue/compiler-sfc'
import { createMemoryHistory, createRouter } from 'vue-router'
import CartView from './views/CartView.vue'
import cartSource from './views/CartView.vue?raw'
import CommerceItems from './components/CommerceItems.vue'
import itemsSource from './components/CommerceItems.vue?raw'
import ProductView from './views/ProductView.vue'
import productSource from './views/ProductView.vue?raw'
import CatalogView from './views/CatalogView.vue'
import catalogSource from './views/CatalogView.vue?raw'
import ShippingRulesView from './views/ShippingRulesView.vue'
import shippingRulesSource from './views/ShippingRulesView.vue?raw'
import ReportsView from './views/ReportsView.vue'
import reportsSource from './views/ReportsView.vue?raw'
import FitAssistant from './components/FitAssistant.vue'
import fitAssistantSource from './components/FitAssistant.vue?raw'
import ProductPresentationSummary from './components/ProductPresentationSummary.vue'
import productPresentationSummarySource from './components/ProductPresentationSummary.vue?raw'
import { api, ApiError, type CartQuote, type FitAnalysis, type GeoReference, type Order, type ProductDetail } from './api'
import { cart } from './cart'
import { readCheckout } from './checkout'
import { formatVnd } from './format'
import { session } from './session'
import { setLocale, t } from './i18n'

// Vue's own renderer is sufficient for interaction tests; no DOM dependency or
// simulated API server. Real browser/SQL acceptance is a separate main-agent pass.
type Node = { tag: string; text: string; props: Record<string, unknown>; children: Node[]; parent: Node | null; focus: () => void; options: Node[]; addEventListener: () => void }
const node = (tag: string, text = ''): Node => ({ tag, text, props: {}, children: [], parent: null, focus: vi.fn(), options: [], addEventListener: vi.fn() })
const renderer = createRenderer<Node, Node>({
  createElement: tag => node(tag), createText: text => node('#text', text), createComment: text => node('#comment', text),
  setText: (target, text) => { target.text = text },
  setElementText: (target, text) => { target.text = text; target.children = [] },
  patchProp: (target, key, _old, value) => { target.props[key] = value },
  parentNode: target => target.parent,
  nextSibling: target => target.parent?.children[target.parent.children.indexOf(target) + 1] ?? null,
  insert: (target, parent, anchor = null) => {
    if (target.parent) target.parent.children.splice(target.parent.children.indexOf(target), 1)
    target.parent = parent
    parent.children.splice(anchor ? parent.children.indexOf(anchor) : parent.children.length, 0, target)
  },
  remove: target => { target.parent?.children.splice(target.parent.children.indexOf(target), 1); target.parent = null },
})
// Vitest's Node transform emits SSR templates. Compile the same templates for
// Vue's in-memory client renderer, preserving their real setup/event functions.
function clientComponent(component: Component, source: string): ComponentOptions {
  const options = component as ComponentOptions
  const setup = options.setup!
  return { ...options, render: compile(parse(source).descriptor.template!.content), setup(props, context) {
    return { ...setup(props, context) as Record<string, unknown> }
  } }
}
const cartComponent = clientComponent(CartView, cartSource)
cartComponent.components = { CommerceItems: clientComponent(CommerceItems, itemsSource) }
const productComponent = clientComponent(ProductView, productSource)
const productPresentationSummaryComponent = clientComponent(ProductPresentationSummary, productPresentationSummarySource)
productComponent.components = { FitAssistant: clientComponent(FitAssistant, fitAssistantSource), ProductPresentationSummary: productPresentationSummaryComponent }
const catalogComponent = clientComponent(CatalogView, catalogSource)
catalogComponent.components = { StorefrontHomepage: { render: () => null }, ProductPresentationSummary: productPresentationSummaryComponent }
const shippingRulesComponent = clientComponent(ShippingRulesView, shippingRulesSource)
// The Node-only template compiler needs runtime syntax; erase TS non-null assertions only.
const reportsComponent = clientComponent(ReportsView, reportsSource.replace(/(?<=[\w)])!(?=[.)\]])/g, ''))
function text(target: Node): string { return target.tag === '#comment' ? '' : target.text + target.children.map(text).join('') }
function find(target: Node, predicate: (target: Node) => boolean): Node | undefined {
  if (predicate(target)) return target
  for (const child of target.children) { const result = find(child, predicate); if (result) return result }
}
async function settle() { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

const A = 'aaaaaaaa-0000-0000-0000-000000000001'
const B = 'bbbbbbbb-0000-0000-0000-000000000002'
const quote: CartQuote = {
  id: 'quote', quotedAt: '2099-01-01T00:00:00Z', expiresAt: '2099-01-01T00:15:00Z', currency: 'VND',
  fulfillmentType: 'PICKUP', merchandiseAmount: 550000, merchandiseDiscountAmount:0,shippingFeeAmount: 0,shippingDiscountAmount:0, totalAmount: 550000,adjustments:[],
  items: [
    { variantId: A, productName: 'Court Classic', sku: 'COURT-39', size: '39', color: 'White', priceVersionId: 'p1', quantity: 1, unitPriceAmount: 150000, totalAmount: 150000 },
    { variantId: B, productName: 'Metro Runner', sku: 'RUN-42', size: '42', color: 'Black', priceVersionId: 'p2', quantity: 2, unitPriceAmount: 200000, totalAmount: 400000 },
  ],
  pickupLocations: [{ id: 'location', code: 'FLOOR', name: 'Sales floor' }],
}
const order = { id: 'one-order', orderReference: 'SC-ORDER', status: 'PENDING_PAYMENT', totalAmount: quote.totalAmount, quantity: 3, items: quote.items } as unknown as Order
const fitProduct = {
  id: 'fit-product', name: 'Court Classic', category: null, collection: null, featured: false, newArrival: false,
  campaignEligible: false, merchandisingRank: 0, heroImage: null, primaryImage: null, fitSupported: true,
  pricing: { state: 'SINGLE' as const, minimumAmount: 1200000, maximumAmount: 1200000, currency: 'VND' as const },
  media: [{ url: '/products/court-classic.png', position: 0, alt: 'Court Classic' }],
  presentation: null,
  fitGuidance: {
    sizeSystem: 'EU' as const, fitTendency: 'TRUE_TO_SIZE' as const, widthProfile: 'REGULAR' as const,
    fitAssistantSupported: true,
    ranges: [
      { size: '40', minimumFootLengthMm: 248, maximumFootLengthMm: 255, minimumFootWidthMm: 90, maximumFootWidthMm: 100 },
      { size: '41', minimumFootLengthMm: 255, maximumFootLengthMm: 262, minimumFootWidthMm: 92, maximumFootWidthMm: 102 },
    ],
  },
  variants: [
    { id: 'ink-40', sku: 'FIT-INK-40', size: '40', color: 'Ink', availability: 'UNAVAILABLE' as const, amount: 1200000 },
    { id: 'chalk-41', sku: 'FIT-CHALK-41', size: '41', color: 'Chalk', availability: 'AVAILABLE' as const, amount: 1200000 },
    { id: 'chalk-40', sku: 'FIT-CHALK-40', size: '40', color: 'Chalk', availability: 'AVAILABLE' as const, amount: 1200000 },
  ],
} satisfies ProductDetail
const twoMedia = [
  { url: '/products/court-primary.png', position: 0, alt: 'Court Classic' },
  { url: '/products/court-hero.png', position: 1, alt: 'Court Classic' },
]
const fitGuidance = fitProduct.fitGuidance
const fitSuccess: FitAnalysis = {
  status: 'SUCCESS', footLengthMm: 250.6, footWidthMm: 97.8, recommendedSize: '40', alternativeSize: '41',
  analysisConfidence: 'HIGH', explanation: 'FIT_TENDENCY_TRUE', recommendedAvailable: true,
  selectedColorAvailable: false, availableColors: ['Chalk'],
}
let root: Node
let app: App<Node>
let saved: Map<string, string>
async function mount(component: Component = cartComponent, path = '/cart') {
  root = node('root')
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/products/:id', component: CartView },
    { path: '/:pathMatch(.*)*', component: CartView },
  ] })
  await router.push(path)
  app = renderer.createApp(component).use(router).provide(ssrContextKey, {})
  app.mount(root)
  await settle()
  return router
}
async function click(label: string) {
  const button = find(root, target => target.tag === 'button' && text(target) === t(label))
  expect(button, label).toBeDefined()
  expect(button!.props.disabled).not.toBe(true)
  ;(button!.props.onClick as () => void)()
  await settle()
}
beforeEach(() => {
  saved = new Map()
  vi.stubGlobal('localStorage', { getItem: (key: string) => saved.get(key) ?? null, setItem: (key: string, value: string) => saved.set(key, value), removeItem: (key: string) => saved.delete(key) })
  vi.stubGlobal('window', { setInterval: vi.fn(), clearInterval: vi.fn() })
  session.account = { accountId: 'owner', login: 'customer', roles: [], permissions: ['ORDER_PLACE'] }
  cart.items = quote.items.map(line => ({ ...line, productId: line.variantId, amount: 150000, currency: 'VND', image: null }))
  cart.storageError = false
  cart.owner = 'owner'
  vi.spyOn(api, 'cartQuote').mockResolvedValue(quote)
  vi.spyOn(api, 'cartCheckout').mockResolvedValue(order)
  vi.spyOn(api, 'savedVouchers').mockResolvedValue({items:[],page:0,size:20,hasNext:false})
  setLocale('en')
})
afterEach(() => { app?.unmount(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('reporting interaction', () => {
  const context = { from:'2026-08-27T17:00:00Z',to:'2026-08-29T17:00:00Z',asOf:'2026-08-29T05:00:00Z',businessTimezone:'Asia/Ho_Chi_Minh' as const,
    scope:{branchId:'branch',branchCode:'BR',branchName:'Test branch',locationId:'location',locationCode:'LOC',locationName:'Test floor'} }
  const net = {context,onlineGross:'180000',posGross:'0',grossSales:'180000',successfulVoids:'180000',netSales:'0',exceptionAmount:'0',exceptionCount:0,currency:'VND' as const,
    merchandiseGross:'200000',itemDiscount:'0',orderDiscount:'50000',voucherDiscount:'0',merchandiseNetBeforeReversal:'150000',shippingGross:'30000',shippingDiscount:'0',shippingNetBeforeReversal:'30000',merchandiseVoids:'150000',shippingVoids:'30000',unallocatedLegacyVoids:'0'}
  const products = {context,rows:[{variantId:'variant',sku:'DISCOUNTED-SKU',size:'40',onlineGross:'200000',posGross:'0',grossSales:'200000',successfulVoids:'150000',netSales:'0',itemDiscount:'0',orderDiscount:'50000',voucherDiscount:'0',merchandiseNetBeforeReversal:'150000'}],
    onlineGross:'200000',posGross:'0',grossSales:'200000',successfulVoids:'150000',netSales:'0',currency:'VND' as const,itemDiscount:'0',orderDiscount:'50000',voucherDiscount:'0',merchandiseNetBeforeReversal:'150000',unallocatedLegacyVoids:'0'}
  function reportsFixture() {
    vi.spyOn(api,'reportScope').mockResolvedValue({asOf:context.asOf,businessTimezone:context.businessTimezone,defaultFromDate:'2026-08-28',defaultToDate:'2026-08-30',locations:[context.scope]})
    vi.spyOn(api,'netSales').mockResolvedValue(net)
    vi.spyOn(api,'productSales').mockResolvedValue(products)
    vi.spyOn(api,'inventoryReport').mockResolvedValue({context,rows:[],movements:[],reservations:[]})
    vi.spyOn(api,'reconciliation').mockResolvedValue({context,entries:[],exceptionAmount:'0',exceptionCount:0,currency:'VND'})
  }
  it('renders server merchandise discounts, shipping and legacy explanation in both locales', async () => {
    reportsFixture()
    vi.mocked(api.netSales).mockResolvedValue({...net,unallocatedLegacyVoids:'150000'})
    await mount(reportsComponent,'/reports')
    expect(text(root)).toContain('Merchandise before discounts')
    expect(text(root)).toContain('Shipping reversals')
    expect(text(root)).toContain('Legacy reversals are not assigned to products')
    expect(text(root)).toContain('50,000')
    expect(text(root)).toContain('30,000')
    setLocale('vi-VN'); await settle()
    expect(text(root)).toContain('Tiền hàng trước giảm giá')
    expect(text(root)).toContain('Hoàn phí vận chuyển')
  })
  it('discards stale report groups and hides old results when a refresh fails', async () => {
    reportsFixture(); await mount(reportsComponent,'/reports')
    const stale=deferred<typeof net>()
    vi.mocked(api.netSales).mockReturnValueOnce(stale.promise).mockResolvedValueOnce({...net,netSales:'777'})
    const submit=()=>{ (find(root,n=>n.tag==='form')!.props.onSubmit as (event:unknown)=>void)({preventDefault:()=>{}}) }
    submit(); await settle(); submit(); await settle()
    expect(text(root)).toContain('777')
    stale.resolve({...net,netSales:'999'}); await settle()
    expect(text(root)).toContain('777'); expect(text(root)).not.toContain('999')
    vi.mocked(api.productSales).mockRejectedValueOnce(new ApiError(403,'ACCESS_DENIED','private'))
    submit(); await settle()
    expect(text(root)).not.toContain('DISCOUNTED-SKU')
    expect(text(root)).toContain(t('This account is not authorized for this action or location.'))
  })
})

describe('cart view interaction', () => {
  it.each(['en', 'vi-VN'] as const)('keeps the guest cart review-only before sign-in in %s', async language => {
    setLocale(language)
    session.account = undefined
    cart.owner = null
    const pendingStateWrite = vi.fn()
    vi.stubGlobal('sessionStorage', { getItem: vi.fn(), setItem: pendingStateWrite, removeItem: vi.fn() })

    const router = await mount()
    const rendered = text(root)

    expect(rendered).toContain(t('Reference price'))
    expect(rendered).toContain(t('Estimated merchandise subtotal'))
    expect(rendered).toContain(t('Your cart will be kept when you sign in.'))
    expect(rendered).toContain(t('After sign-in, choose pickup or delivery and apply eligible offers or a voucher code.'))
    if (language === 'vi-VN') {
      expect(rendered).toContain('Giá tham khảo')
      expect(rendered).toContain('Đăng nhập để xem tổng tiền hiện tại')
      expect(rendered).toContain('Giỏ hàng sẽ được giữ nguyên khi bạn đăng nhập.')
    }
    expect(find(root, target => target.props.type === 'radio')).toBeUndefined()
    expect(find(root, target => target.props.id === 'saved-voucher')).toBeUndefined()
    expect(find(root, target => target.props.id === 'voucher-code')).toBeUndefined()
    expect(api.cartQuote).not.toHaveBeenCalled()
    expect(api.savedVouchers).not.toHaveBeenCalled()

    const signIn = find(root, target => target.tag === 'button' && text(target) === t('Sign in to see your current total'))!
    await (signIn.props.onClick as () => Promise<void>)()
    await settle()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.returnTo).toBe('/cart')
    expect(cart.items).toHaveLength(2)
    expect(pendingStateWrite).not.toHaveBeenCalled()
  })
  it('asks for fulfillment once while keeping the quoted pickup details usable', async () => {
    await mount()
    await click('Check price & availability')

    expect(text(root).split(t('How should we fulfill this order?'))).toHaveLength(2)
    expect(find(root, target => target.props.id === 'pickup-location')).toBeDefined()
  })
  it('renders stacked item, order, and delivery savings with the authoritative total', async () => {
    const promoted={...quote,fulfillmentType:'DELIVERY' as const,merchandiseAmount:550000,merchandiseDiscountAmount:70000,shippingFeeAmount:80000,shippingDiscountAmount:80000,totalAmount:480000,adjustments:[
      {name:'Item offer',layer:'ITEM',amount:50000},{name:'Order offer',layer:'ORDER_AUTOMATIC',amount:20000},{name:'Delivery offer',layer:'SHIPPING',amount:80000},
    ]} as CartQuote
    vi.mocked(api.cartQuote).mockResolvedValue(promoted)
    await mount();await click('Check price & availability')
    expect(text(root)).toContain('Item offer');expect(text(root)).toContain('Order offer');expect(text(root)).toContain('Delivery offer')
    expect(text(root)).toContain(t('Estimated merchandise subtotal')); expect(text(root)).toContain('450,000')
    expect(text(root)).toContain('550,000');expect(text(root)).toContain('50,000');expect(text(root)).toContain('20,000');expect(text(root)).toContain('80,000');expect(text(root)).toContain('480,000')
  })
  it('does not invent a shipping saving for pickup', async () => {
    await mount();await click('Check price & availability')
    expect(text(root)).not.toContain(t('Shipping offer'));expect(text(root)).not.toContain(t('Free shipping'))
  })
  it('requires a fresh quote after an offer-change conflict', async () => {
    vi.mocked(api.cartCheckout).mockRejectedValueOnce(new ApiError(409,'PROMOTION_QUOTE_STALE','offer changed'))
    await mount();await click('Check price & availability');await click('Confirm total & create order')
    expect(text(root)).toContain(t('Automatic offers changed. Request a fresh quote before checkout.'))
    expect(api.cartCheckout).toHaveBeenCalledOnce()
  })
  it('clears a submitted search immediately and ignores the old delayed response', async () => {
    let resolveOld!: (value: []) => void
    vi.spyOn(api, 'products').mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValue([{ id: A, name: 'Full catalog shoe', fromAmount: 1, availableVariantCount: 1, presentation: null }] as never)
    const router = await mount(catalogComponent, '/?q=court')
    const input = find(root, target => target.props.id === 'product-search')!
    await (input.props.onInput as (event: unknown) => Promise<void>)({ target: { value: '   ' } })
    await settle()
    expect(router.currentRoute.value.query.q).toBeUndefined()
    expect(api.products).toHaveBeenLastCalledWith('')
    expect(text(root)).toContain('Full catalog shoe')
    resolveOld([]); await settle()
    expect(text(root)).toContain('Full catalog shoe')
    ;(input.props.onInput as (event: unknown) => Promise<void>)({ target: { value: 'runner' } })
    await router.replace({ path: '/', query: { q: 'runner' } })
    await settle()
    expect(router.currentRoute.value.query.q).toBe('runner')
    expect(api.products).toHaveBeenLastCalledWith('runner')
    expect(text(root)).toContain('Search results')
  })
  it('guards rapid add activation and announces the current quantity', async () => {
    cart.items = []
    vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, variants: [{ ...fitProduct.variants[1], id: A }] })
    await mount(productComponent, '/products/fit-product')
    const size = find(root, target => target.tag === 'button' && String(target.props.class).includes('variant-option'))!
    ;(size.props.onClick as () => void)()
    await settle()
    const add = find(root, target => target.tag === 'button' && text(target) === t('Add to cart'))!
    ;(add.props.onClick as () => void)()
    ;(add.props.onClick as () => void)()
    await settle()
    expect(cart.items[0].quantity).toBe(1)
    expect(text(root)).toContain('Added Court Classic · In cart: 1')
    expect(add.props.disabled).toBe(true)
    expect(api.cartCheckout).not.toHaveBeenCalled()
    expect(JSON.parse(saved.get('shoe-commerce:cart:v2:owner')!).items[0].quantity).toBe(1)
  })
  it.each(['en', 'vi-VN'] as const)('requires a distinct review/confirm step and shows the changed second line in %s', async language => {
    setLocale(language)
    await mount()
    await click('Check price & availability')
    expect(api.cartCheckout).not.toHaveBeenCalled()
    expect(text(root)).toContain(t('{name} · Size {size} is now {amount}. Review the updated total before continuing.', { name: 'Metro Runner', size: '42', amount: language === 'en' ? '₫200,000' : '200.000 ₫' }))
    expect(text(root)).toContain(language === 'en' ? '₫550,000' : '550.000 ₫')
    await click('Confirm total & create order')
    expect(api.cartCheckout).toHaveBeenCalledOnce()
    expect(vi.mocked(api.cartCheckout).mock.calls[0].slice(0, 2)).toEqual(['quote', [{ variantId: A, quantity: 1 }, { variantId: B, quantity: 2 }]])
    expect(text(root)).toContain('SC-ORDER')
    expect(text(root)).toContain('RUN-42')
    expect(cart.items).toHaveLength(0)
  })
  it('invalidates a reviewed quote when any line quantity changes', async () => {
    await mount(); await click('Check price & availability')
    const increase = find(root, target => target.tag === 'button' && target.props['aria-label'] === t('Increase quantity') + ' · RUN-42')!
    ;(increase.props.onClick as () => void)()
    await settle()
    expect(text(root)).not.toContain(t('Confirm total & create order'))
    expect(text(root)).toContain(t('Check price & availability'))
    expect(readCheckout('owner')).toBeUndefined()
  })
  it('shows line-specific stock failure and sends no partial checkout', async () => {
    vi.mocked(api.cartQuote).mockRejectedValue(new ApiError(409, 'INSUFFICIENT_STOCK', 'internal quantity', B))
    await mount(); await click('Check price & availability')
    expect(text(root)).toContain('Metro Runner · Size 42 · RUN-42')
    expect(text(root)).toContain('Reduce the quantity or remove it')
    expect(api.cartCheckout).not.toHaveBeenCalled()
    expect(cart.items).toHaveLength(2)
  })
  it('restores an uncertain checkout after remount and retries without a new quote or key', async () => {
    vi.mocked(api.cartCheckout).mockRejectedValueOnce(new TypeError('network')).mockResolvedValue(order)
    await mount(); await click('Check price & availability'); await click('Confirm total & create order')
    const first = vi.mocked(api.cartCheckout).mock.calls[0]
    app.unmount(); await mount()
    expect(text(root)).toContain(t('Your last checkout may already have created an order. Retry the same request to recover it before changing your cart.'))
    expect(find(root, target => target.tag === 'button' && target.props['aria-label'] === t('Increase quantity') + ' · RUN-42')?.props.disabled).toBe(true)
    await click('Retry saved checkout')
    expect(vi.mocked(api.cartCheckout).mock.calls[1]).toEqual(first)
    expect(api.cartQuote).toHaveBeenCalledOnce()
    expect(text(root)).toContain('SC-ORDER')
  })
  it.each(['en', 'vi-VN'] as const)('offers a new quote after a definite rejection without silently submitting it in %s', async language => {
    setLocale(language)
    vi.mocked(api.cartCheckout).mockRejectedValueOnce(new ApiError(409, 'NO_COMMON_PICKUP_LOCATION', 'conflict'))
    await mount(); await click('Check price & availability'); await click('Confirm total & create order')
    const rejected = readCheckout('owner')!
    expect(rejected.rejected).toBe(true)
    expect(text(root)).toContain(t('No single pickup location can supply this whole cart. Adjust the items or quantities and check again.'))
    await click('Check price & availability')
    expect(api.cartQuote).toHaveBeenCalledTimes(2)
    expect(api.cartCheckout).toHaveBeenCalledOnce()
    expect(readCheckout('owner')).toBeUndefined()
    await click('Confirm total & create order')
    expect(vi.mocked(api.cartCheckout).mock.calls[1][2]).not.toBe(rejected.key)
  })
  it('ignores an old quote response after the cart changes during validation', async () => {
    let resolve!: (result: CartQuote) => void
    vi.mocked(api.cartQuote).mockReturnValue(new Promise(done => { resolve = done }))
    await mount(); await click('Check price & availability')
    cart.items[1].quantity = 3
    await settle(); resolve(quote); await settle()
    expect(text(root)).not.toContain(t('Confirm total & create order'))
    expect(api.cartCheckout).not.toHaveBeenCalled()
  })
  it('shows a recovery storage error when adding from a product cannot safely clear a rejected checkout', async () => {
    saved.set('shoe-commerce:checkout:owner', JSON.stringify({ accountId: 'owner', key: 'old-key', quoteId: 'quote', items: [{ variantId: A, quantity: 1 }], rejected: true }))
    vi.spyOn(localStorage, 'removeItem').mockImplementation(() => { throw new Error('denied') })
    vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, id: A, variants: [{ id: A, sku: 'COURT-39', size: '39', color: 'White', amount: 150000, availability: 'AVAILABLE' }] })
    await mount(productComponent, '/products/' + A)
    const size = find(root, target => target.tag === 'button' && String(target.props.class).includes('variant-option'))!
    ;(size.props.onClick as () => void)()
    await settle()
    await click('Add to cart')
    expect(text(root)).toContain(t('Browser storage is unavailable. Enable it before checkout so a retry cannot create another order.'))
    expect(text(root)).not.toContain(t('Added to cart.'))
    expect(cart.items[0].quantity).toBe(1)
  })

  it('applies and removes a code only through authoritative requotes', async () => {
    const applied={...quote,totalAmount:500000,adjustments:[{name:'Member code',layer:'VOUCHER_SELECTED' as const,amount:50000,acquisitionMode:'CODE' as const,maskedCode:'SA••10'}]}
    vi.mocked(api.cartQuote).mockResolvedValueOnce(applied).mockResolvedValueOnce(quote)
    await mount()
    const input=find(root,target=>target.props.id==='voucher-code')!
    ;(input.props.onInput as (event:unknown)=>void)({target:{value:' save-10 '}})
    await settle();expect(api.cartQuote).not.toHaveBeenCalled()
    const form=find(root,target=>target.props.class==='voucher-code-form')!;(form.props.onSubmit as (event:{preventDefault:()=>void})=>void)({preventDefault(){}});await settle()
    expect(vi.mocked(api.cartQuote).mock.calls[0][2]).toEqual({type:'CODE',code:'SAVE-10'})
    expect(text(root)).toContain('SA••10');expect(text(root)).not.toContain('SAVE-10')
    await click('Remove offer')
    expect(vi.mocked(api.cartQuote).mock.calls[1][2]).toEqual({type:'NONE'})
  })

  it('submits one owned saved claim without choosing a best offer', async () => {
    vi.mocked(api.savedVouchers).mockResolvedValue({items:[{claimId:'claim-a',claimStatus:'CLAIMED',claimedAt:'2099-01-01T00:00:00Z',offerAvailability:'AVAILABLE',offer:{familyId:'family-a',acquisitionMode:'CLAIMABLE',name:'Owned offer',customerSummary:'Saved',customerTerms:'Terms',effectType:'ORDER_FIXED',fixedAmount:50000,validFrom:'2099-01-01T00:00:00Z',claimable:true}}],page:0,size:20,hasNext:false})
    await mount()
    const select=find(root,target=>target.props.id==='saved-voucher')!
    ;(select.props.onChange as (event:unknown)=>void)({target:{value:'claim-a'}});await settle()
    expect(api.cartQuote).not.toHaveBeenCalled();await click('Apply saved offer')
    expect(vi.mocked(api.cartQuote).mock.calls[0][2]).toEqual({type:'CLAIM',claimId:'claim-a'})
  })

  it('stops stale checkout, shows both totals, and requires deliberate review', async () => {
    const applied={...quote,totalAmount:500000,adjustments:[{name:'Saved offer',layer:'VOUCHER_SELECTED' as const,amount:50000,acquisitionMode:'CLAIMABLE' as const,claimId:'claim-a'}]}
    vi.mocked(api.cartQuote).mockResolvedValueOnce(applied).mockResolvedValueOnce(quote)
    vi.mocked(api.cartCheckout).mockRejectedValueOnce(new ApiError(409,'VOUCHER_STALE','internal revision detail'))
    await mount();await click('Check price & availability');await click('Confirm total & create order')
    expect(text(root)).toContain(t('Your selected offer changed.'));expect(text(root)).not.toContain('internal revision detail')
    await click('Get fresh total')
    expect(vi.mocked(api.cartQuote).mock.calls[1][2]).toEqual({type:'NONE'})
    expect(text(root)).toContain('500,000');expect(text(root)).toContain('550,000')
    expect(find(root,target=>target.tag==='button'&&text(target)===t('Confirm total & create order'))?.props.disabled).toBe(true)
    await click('I reviewed the new total')
    expect(find(root,target=>target.tag==='button'&&text(target)===t('Confirm total & create order'))?.props.disabled).toBe(false)
  })
})

describe('district request ownership', () => {
  const provinces: GeoReference[] = [{ code: 'A', label: 'Province A' }, { code: 'B', label: 'Province B' }]

  it('keeps cart districts from the newest selected province', async () => {
    const oldRequest = deferred<GeoReference[]>(), currentRequest = deferred<GeoReference[]>()
    vi.spyOn(api, 'provinces').mockResolvedValue(provinces)
    vi.spyOn(api, 'districts').mockImplementation(code => code === 'A' ? oldRequest.promise : currentRequest.promise)
    await mount()
    ;(find(root, target => target.tag === 'input' && target.props.value === 'DELIVERY')!.props.onChange as () => void)()
    await settle()
    const province = find(root, target => target.props.id === 'delivery-province')!
    ;(province.props.onChange as (event: unknown) => void)({ target: { value: 'A' } }); await settle()
    ;(province.props.onChange as (event: unknown) => void)({ target: { value: 'B' } }); await settle()

    currentRequest.resolve([{ code: 'B-1', label: 'District B' }]); await settle()
    expect(text(root)).toContain('District B')
    oldRequest.resolve([{ code: 'A-1', label: 'District A' }]); await settle()
    expect(text(root)).toContain('District B')
    expect(text(root)).not.toContain('District A')
  })

  it('keeps Shipping Rules loading and error state owned by the newest province request', async () => {
    const oldRequest = deferred<GeoReference[]>(), currentRequest = deferred<GeoReference[]>()
    vi.spyOn(api, 'shippingRules').mockResolvedValue([])
    vi.spyOn(api, 'provinces').mockResolvedValue(provinces)
    vi.spyOn(api, 'shippingLocations').mockResolvedValue([])
    vi.spyOn(api, 'districts').mockImplementation(code => code === 'A' ? oldRequest.promise : currentRequest.promise)
    await mount(shippingRulesComponent, '/operations/shipping')
    const provinceLabel = find(root, target => target.tag === 'label' && text(target).startsWith(t('Province / city')))!
    const districtLabel = find(root, target => target.tag === 'label' && text(target).startsWith(t('District')))!
    const province = find(provinceLabel, target => target.tag === 'select')!
    const district = find(districtLabel, target => target.tag === 'select')!
    ;(province.props['onUpdate:modelValue'] as (value: string) => void)('A'); await settle()
    ;(province.props['onUpdate:modelValue'] as (value: string) => void)('B'); await settle()

    oldRequest.reject(new Error('Old district failure')); await settle()
    expect(district.props.disabled).toBe(true)
    expect(text(root)).not.toContain('Old district failure')
    currentRequest.resolve([{ code: 'B-1', label: 'District B' }]); await settle()
    expect(text(root)).toContain('District B')
    expect(district.props.disabled).toBe(false)
  })
})

describe('product presentation rendering', () => {
  it('renders compact copy only on the card that has published presentation', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'products').mockResolvedValue([
      {
        id: A, name: 'Court Classic', category: 'Court', collection: 'Court Originals', featured: false,
        newArrival: false, campaignEligible: false, merchandisingRank: 1, heroImage: null, primaryImage: null,
        variantCount: 4, availableVariantCount: 3, fromAmount: 1490000,
        presentation: { summary: { vi: 'Bản Court', en: 'Court evidence' } },
      },
      {
        id: B, name: 'Court High', category: 'Court', collection: 'Court Originals', featured: false,
        newArrival: false, campaignEligible: false, merchandisingRank: 2, heroImage: null, primaryImage: null,
        variantCount: 4, availableVariantCount: 3, fromAmount: 1690000, presentation: null,
      },
    ])
    await mount(catalogComponent, '/')

    const summaries: Node[] = []
    const collect = (target: Node) => {
      if (String(target.props.class).includes('product-presentation-summary')) summaries.push(target)
      target.children.forEach(collect)
    }
    collect(root)
    expect(summaries).toHaveLength(1)
    expect(summaries[0].props.class).toContain('compact')
    expect(text(summaries[0])).toBe('Court evidence')
  })

  it('places full PDP presentation after the purchase decision and omits the null state', async () => {
    vi.spyOn(api, 'product').mockResolvedValue({
      ...fitProduct, presentation: { summary: { vi: 'Bằng chứng VI', en: 'Published evidence' } },
    })
    await mount(productComponent, '/products/fit-product')

    const order: Node[] = []
    const collect = (target: Node) => { order.push(target); target.children.forEach(collect) }
    collect(root)
    const decision = find(root, target => String(target.props.class).includes('product-decision'))!
    const purchase = find(root, target => text(target) === t('Choose a size to add'))!
    const summary = find(root, target => String(target.props.class).includes('product-presentation-summary'))!
    expect(text(summary)).toBe('Published evidence')
    expect(order.indexOf(summary)).toBeGreaterThan(order.indexOf(decision))
    expect(order.indexOf(summary)).toBeGreaterThan(order.indexOf(purchase))

    vi.mocked(api.product).mockResolvedValue({ ...fitProduct, presentation: null })
    app.unmount()
    await mount(productComponent, '/products/fit-product')
    expect(find(root, target => String(target.props.class).includes('product-presentation-summary'))).toBeUndefined()
  })
})

describe('fitting product-detail interaction', () => {
  it.each([
    ['media only', { media: twoMedia, fitGuidance: null }, true, false],
    ['fit only', { media: [], fitGuidance }, false, true],
    ['neither', { media: [], fitGuidance: null }, false, false],
    ['both', { media: twoMedia, fitGuidance }, true, true],
  ])('renders %s without empty evidence sections', async (_label, evidence, hasGallery, hasGuide) => {
    vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, ...evidence })
    await mount(productComponent, '/products/fit-product')

    expect(Boolean(find(root, target => String(target.props.class).includes('product-media-picker')))).toBe(hasGallery)
    expect(Boolean(find(root, target => target.tag === 'details' && String(target.props.class).includes('product-fit-evidence')))).toBe(hasGuide)
    expect(text(root)).not.toContain('N/A')
  })

  it.each(['en', 'vi-VN'] as const)('shows price before selection and explains the locked purchase action in %s', async language => {
    setLocale(language)
    vi.spyOn(api, 'product').mockResolvedValue({
      ...fitProduct,
      pricing: { state: 'RANGE', minimumAmount: 1111000, maximumAmount: 1333000, currency: 'VND' },
    })
    await mount(productComponent, '/products/fit-product')

    expect(text(root)).toContain(`${formatVnd(1111000)} – ${formatVnd(1333000)}`)
    expect(text(root)).toContain(t('Size and fit guide'))
    expect(find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)).toBeUndefined()
    const locked = find(root, target => target.tag === 'button' && text(target) === t('Choose a size to add'))
    expect(locked).toBeDefined()
    expect(locked!.props.disabled).toBe(true)
    expect(text(root)).toContain(t('Select an available size before adding this product to your cart.'))
  })

  it.each(['en', 'vi-VN'] as const)('uses generic localized gallery navigation in %s', async language => {
    setLocale(language)
    vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, media: twoMedia })
    await mount(productComponent, '/products/fit-product')

    const controls = find(root, target => String(target.props.class).includes('product-media-picker'))!
    expect(controls.props['aria-label']).toBe(t('Product images'))
    const thumbnails = controls.children.filter(target => target.tag === 'button')
    expect(thumbnails.map(button => button.props['aria-label'])).toEqual([
      t('Show image {position} of {total}', { position: 1, total: 2 }),
      t('Show image {position} of {total}', { position: 2, total: 2 }),
    ])
    ;(thumbnails[1].props.onClick as () => void)()
    await settle()
    const mainImage = find(root, target => target.tag === 'img' && String(target.props.width) === '1456')!
    expect(mainImage.props.src).toBe('/products/court-hero.png')
    expect(mainImage.props.alt).toBe('Court Classic')
  })

  it('enables the purchase action only after an explicit size choice', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product')

    const size = find(root, target => target.tag === 'button' && String(target.props.class).includes('variant-option') && text(target).includes('41'))!
    ;(size.props.onClick as () => void)()
    await settle()

    expect(size.props['aria-pressed']).toBe(true)
    const add = find(root, target => target.tag === 'button' && text(target) === t('Add to cart'))
    expect(add).toBeDefined()
    expect(add!.props.disabled).not.toBe(true)
    expect(text(size)).toContain(t('Selected'))
  })

  it('keeps a valid deep-linked variant selected', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product?variant=chalk-40')

    const selectedSize = find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)
    expect(selectedSize).toBeDefined()
    expect(text(selectedSize!)).toContain('40')
    expect(text(selectedSize!)).toContain(t('Chalk'))
    expect(find(root, target => target.tag === 'button' && text(target) === t('Add to cart'))?.props.disabled).not.toBe(true)
  })

  it('keeps an unavailable deep-linked size explicit without offering purchase', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product?variant=ink-40')

    const selectedSize = find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)!
    expect(text(selectedSize)).toContain(t('Selected · Unavailable'))
    expect(find(root, target => target.tag === 'button' && text(target) === t('Size unavailable'))?.props.disabled).toBe(true)
  })

  it('does not turn an invalid deep link into a default selection', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product?variant=not-a-variant')

    expect(find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)).toBeUndefined()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Choose a size to add'))?.props.disabled).toBe(true)
  })

  it('ignores a late product response from the previous route', async () => {
    let resolveOld!: (value: ProductDetail) => void
    let resolveNew!: (value: ProductDetail) => void
    vi.spyOn(api, 'product')
      .mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockImplementationOnce(() => new Promise(resolve => { resolveNew = resolve }))
    const router = await mount(productComponent, '/products/old')

    await router.push('/products/new'); await settle()
    resolveNew({ ...fitProduct, id: 'new', name: 'New Shoe' }); await settle()
    resolveOld({ ...fitProduct, id: 'old', name: 'Old Shoe' }); await settle()

    expect(api.product).toHaveBeenNthCalledWith(1, 'old')
    expect(api.product).toHaveBeenNthCalledWith(2, 'new')
    expect(text(root)).toContain('New Shoe')
    expect(text(root)).not.toContain('Old Shoe')
  })

  it('resets gallery selection to the new product first image across route changes', async () => {
    vi.spyOn(api, 'product')
      .mockResolvedValueOnce({ ...fitProduct, id: 'old', media: twoMedia })
      .mockResolvedValueOnce({ ...fitProduct, id: 'new', name: 'New Shoe', media: [
        { url: '/products/new-primary.png', position: 0, alt: 'New Shoe' },
        { url: '/products/new-hero.png', position: 1, alt: 'New Shoe' },
      ] })
    const router = await mount(productComponent, '/products/old')
    const oldPicker = find(root, target => String(target.props.class).includes('product-media-picker'))!
    ;(oldPicker.children.filter(target => target.tag === 'button')[1].props.onClick as () => void)()
    await settle()

    await router.push('/products/new'); await settle()
    const mainImage = find(root, target => target.tag === 'img' && String(target.props.width) === '1456')!
    expect(mainImage.props.src).toBe('/products/new-primary.png')
  })

  async function openPhotoPicker() {
    const entry = find(root, target => target.tag === 'button' && String(target.props.class).includes('fit-entry'))
    expect(entry).toBeDefined()
    ;(entry!.props.onClick as () => void)()
    await settle()
    await click('Start with a photo')
  }
  async function chooseImage(type = 'image/png') {
    const upload = find(root, target => target.tag === 'input' && target.props.id === 'fit-photo-input')
    expect(upload).toBeDefined()
    ;(upload!.props.onChange as (event: Event) => void)({ target: { files: [new Blob(['image'], { type }) as File] } } as unknown as Event)
    await settle()
  }

  it('supports drop validation and photo preview without submitting automatically', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    const analyze = vi.spyOn(api, 'fitAnalysis')
    await mount(productComponent, '/products/fit-product')
    await openPhotoPicker()
    const zone = find(root, target => target.props.class === 'fit-drop-zone')!
    const drop = zone.props.onDrop as (event: unknown) => void
    drop({ preventDefault() {}, dataTransfer: { files: [{ type: 'image/png', size: 6 * 1024 * 1024 }] } })
    await settle()
    expect(text(root)).toContain(t('Only PNG or JPEG images up to 5 MB are accepted.'))
    drop({ preventDefault() {}, dataTransfer: { files: [new Blob(['image'], { type: 'image/png' })] } })
    await settle()
    expect(text(root)).toContain(t('Use this photo'))
    expect(analyze).not.toHaveBeenCalled()
  })

  it('keeps fit, selected color, and cart decisions explicit through a successful recommendation', async () => {
    cart.items = []
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    let resolveAnalysis!: (analysis: FitAnalysis) => void
    vi.spyOn(api, 'fitAnalysis').mockImplementation(() => new Promise(resolve => { resolveAnalysis = resolve }))
    await mount(productComponent, '/products/fit-product?variant=ink-40')
    await openPhotoPicker()
    await chooseImage()
    await click('Use this photo')
    expect(text(root)).toContain(t('Analyzing measurement…'))
    resolveAnalysis(fitSuccess)
    await settle()

    expect(vi.mocked(api.fitAnalysis).mock.calls[0].slice(0, 3)).toEqual(['fit-product', expect.anything(), 'Ink'])
    expect(text(root)).toContain('EU 40')
    expect(text(root)).toContain(t('Alternative size: EU {size}', { size: '41' }))
    expect(text(root)).toContain(t('Recommended size is unavailable in the selected color.'))
    expect(cart.items).toHaveLength(0)

    const chooseChalk = find(root, target => target.tag === 'button' && text(target) === t('Choose {color}', { color: t('Chalk') }))
    expect(chooseChalk).toBeDefined()
    ;(chooseChalk!.props.onClick as () => void)()
    await settle()
    expect(text(root)).toContain('Size 40 · Chalk')
    expect(text(root)).not.toContain(t('Recommended size is unavailable in the selected color.'))

    const selectRecommended = find(root, target => target.tag === 'button' && text(target) === t('Select EU {size}', { size: '40' }))
    ;(selectRecommended!.props.onClick as () => void)()
    await settle()
    expect(cart.items).toHaveLength(0)
    await click('Add to cart')
    expect(cart.items).toHaveLength(1)
    expect(cart.items[0].variantId).toBe('chalk-40')

    const manualSize = find(root, target => target.tag === 'button' && String(target.props.class).includes('variant-option') && text(target).includes('41'))
    expect(manualSize).toBeDefined()
    ;(manualSize!.props.onClick as () => void)()
    await settle()
    expect(text(root)).toContain('Size 41 · Chalk')
    expect(cart.items).toHaveLength(1)
  })

  it('does not select a Fit Assistant result until the customer accepts it', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    vi.spyOn(api, 'fitAnalysis').mockResolvedValue(fitSuccess)
    await mount(productComponent, '/products/fit-product')
    await openPhotoPicker()
    await chooseImage()
    await click('Use this photo')

    expect(find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)).toBeUndefined()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Choose a size to add'))?.props.disabled).toBe(true)

    const accept = find(root, target => target.tag === 'button' && text(target).startsWith('Select EU'))!
    ;(accept.props.onClick as () => void)()
    await settle()
    expect(text(root)).toContain('Size 40 · Chalk')
    expect(find(root, target => target.tag === 'button' && text(target) === t('Add to cart'))?.props.disabled).not.toBe(true)
  })

  it('rejects an invalid image locally and keeps a real retake result non-selectable', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    vi.spyOn(api, 'fitAnalysis').mockResolvedValue({ status: 'RETAKE', retakeReason: 'REFERENCE_NOT_FOUND', availableColors: [] })
    await mount(productComponent, '/products/fit-product')
    await openPhotoPicker()
    await chooseImage('image/gif')
    expect(text(root)).toContain(t('Only PNG or JPEG images up to 5 MB are accepted.'))
    expect(api.fitAnalysis).not.toHaveBeenCalled()

    await chooseImage()
    await click('Use this photo')
    expect(text(root)).toContain(t('Try a clearer photo'))
    expect(text(root)).toContain(t('Reference sheet not found'))
    expect(text(root)).toContain(t('Retake photo'))
    expect(text(root)).not.toContain('Select EU')
  })

  it('does not offer a generic fitting flow for a product without a complete profile', async () => {
    vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, fitSupported: false })
    await mount(productComponent, '/products/fit-product')
    expect(text(root)).toContain(t('This shoe model does not have a supported fit profile yet.'))
    expect(find(root, target => target.tag === 'button' && text(target) === t('Find my size'))).toBeUndefined()
  })
})
