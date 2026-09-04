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
import FitAssistant from './components/FitAssistant.vue'
import fitAssistantSource from './components/FitAssistant.vue?raw'
import { api, ApiError, type CartQuote, type FitAnalysis, type Order, type ProductDetail } from './api'
import { cart } from './cart'
import { readCheckout } from './checkout'
import { session } from './session'
import { setLocale, t } from './i18n'

// Vue's own renderer is sufficient for interaction tests; no DOM dependency or
// simulated API server. Real browser/SQL acceptance is a separate main-agent pass.
type Node = { tag: string; text: string; props: Record<string, unknown>; children: Node[]; parent: Node | null; focus: () => void }
const node = (tag: string, text = ''): Node => ({ tag, text, props: {}, children: [], parent: null, focus: vi.fn() })
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
productComponent.components = { FitAssistant: clientComponent(FitAssistant, fitAssistantSource) }
function text(target: Node): string { return target.tag === '#comment' ? '' : target.text + target.children.map(text).join('') }
function find(target: Node, predicate: (target: Node) => boolean): Node | undefined {
  if (predicate(target)) return target
  for (const child of target.children) { const result = find(child, predicate); if (result) return result }
}
async function settle() { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }

const A = 'aaaaaaaa-0000-0000-0000-000000000001'
const B = 'bbbbbbbb-0000-0000-0000-000000000002'
const quote: CartQuote = {
  id: 'quote', quotedAt: '2099-01-01T00:00:00Z', expiresAt: '2099-01-01T00:15:00Z', currency: 'VND', totalAmount: 550000,
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
  variants: [
    { id: 'ink-40', sku: 'FIT-INK-40', size: '40', color: 'Ink', availability: 'UNAVAILABLE' as const, amount: 1200000 },
    { id: 'chalk-41', sku: 'FIT-CHALK-41', size: '41', color: 'Chalk', availability: 'AVAILABLE' as const, amount: 1200000 },
    { id: 'chalk-40', sku: 'FIT-CHALK-40', size: '40', color: 'Chalk', availability: 'AVAILABLE' as const, amount: 1200000 },
  ],
} satisfies ProductDetail
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
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: CartView }] })
  await router.push(path)
  app = renderer.createApp(component).use(router).provide(ssrContextKey, {})
  app.mount(root)
  await settle()
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
  vi.spyOn(api, 'cartQuote').mockResolvedValue(quote)
  vi.spyOn(api, 'cartCheckout').mockResolvedValue(order)
  setLocale('en')
})
afterEach(() => { app?.unmount(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('cart view interaction', () => {
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
    const select = find(root, target => target.tag === 'select' && target.props.id === 'cart-quantity-' + B)!
    ;(select.props.onChange as (event: unknown) => void)({ target: { value: '3' } })
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
    expect(find(root, target => target.tag === 'select')?.props.disabled).toBe(true)
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
    vi.spyOn(api, 'product').mockResolvedValue({ id: A, name: 'Court Classic', variants: [{ id: A, sku: 'COURT-39', size: '39', color: 'White', amount: 150000, availability: 'AVAILABLE' }] } as ProductDetail)
    await mount(productComponent, '/products/' + A)
    await click('Add to cart')
    expect(text(root)).toContain(t('Browser storage is unavailable. Enable it before checkout so a retry cannot create another order.'))
    expect(text(root)).not.toContain(t('Added to cart.'))
    expect(cart.items[0].quantity).toBe(1)
  })
})

describe('fitting product-detail interaction', () => {
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
