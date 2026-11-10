import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { compile, createRenderer, nextTick, ssrContextKey, type App, type Component, type ComponentOptions } from 'vue'
import { parse } from 'vue/compiler-sfc'
import { createMemoryHistory, createRouter } from 'vue-router'
import CartView from './views/CartView.vue'
import OrderStatusView from './views/OrderStatusView.vue'
import orderStatusSource from './views/OrderStatusView.vue?raw'
import AppShell from './App.vue'
import appShellSource from './App.vue?raw'
import cartSource from './views/CartView.vue?raw'
import CommerceItems from './components/CommerceItems.vue'
import itemsSource from './components/CommerceItems.vue?raw'
import ProductView from './views/ProductView.vue'
import productSource from './views/ProductView.vue?raw'
import CatalogView from './views/CatalogView.vue'
import catalogSource from './views/CatalogView.vue?raw'
import RegisterView from './views/RegisterView.vue'
import registerSource from './views/RegisterView.vue?raw'
import ShippingRulesView from './views/ShippingRulesView.vue'
import shippingRulesSource from './views/ShippingRulesView.vue?raw'
import ReportsView from './views/ReportsView.vue'
import reportsSource from './views/ReportsView.vue?raw'
import InventoryTransfersView from './views/InventoryTransfersView.vue'
import inventoryTransfersSource from './views/InventoryTransfersView.vue?raw'
import FitAssistant from './components/FitAssistant.vue'
import fitAssistantSource from './components/FitAssistant.vue?raw'
import ProductPresentationSummary from './components/ProductPresentationSummary.vue'
import productPresentationSummarySource from './components/ProductPresentationSummary.vue?raw'
import StorefrontIntentLinks from './components/StorefrontIntentLinks.vue'
import storefrontIntentLinksSource from './components/StorefrontIntentLinks.vue?raw'
import { api, ApiError, type CartQuote, type FitAnalysis, type GeoReference, type Order, type ProductDetail, type ProductSummary, type ReconciliationEntry } from './api'
import { activateCart, addToCart, cart, setCartQuantity } from './cart'
import { readCheckout } from './checkout'
import { formatVnd } from './format'
import { session } from './session'
import { setLocale, t } from './i18n'

// Vue's own renderer is sufficient for interaction tests; no DOM dependency or
// simulated API server. Real browser/SQL acceptance is a separate main-agent pass.
type Node = { tag: string; text: string; props: Record<string, unknown>; children: Node[]; parent: Node | null; focus: () => void; options: Node[]; addEventListener: () => void; getRootNode: () => null }
const node = (tag: string, text = ''): Node => ({ tag, text, props: {}, children: [], parent: null, focus: vi.fn(), options: [], addEventListener: vi.fn(), getRootNode: () => null })
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
const orderComponent = clientComponent(OrderStatusView, orderStatusSource)
orderComponent.components = cartComponent.components
const shellComponent = clientComponent(AppShell, appShellSource)
shellComponent.components = { ProductComparison: { render: () => null } }
const productComponent = clientComponent(ProductView, productSource)
const productPresentationSummaryComponent = clientComponent(ProductPresentationSummary, productPresentationSummarySource)
productComponent.components = { FitAssistant: clientComponent(FitAssistant, fitAssistantSource), ProductPresentationSummary: productPresentationSummaryComponent }
const catalogComponent = clientComponent(CatalogView, catalogSource)
const registerComponent = clientComponent(RegisterView, registerSource)
const storefrontIntentLinksComponent = clientComponent(StorefrontIntentLinks, storefrontIntentLinksSource)
catalogComponent.components = { StorefrontHomepage: { render: () => null }, StorefrontIntentLinks: storefrontIntentLinksComponent, ProductPresentationSummary: productPresentationSummaryComponent }
const shippingRulesComponent = clientComponent(ShippingRulesView, shippingRulesSource)
const inventoryTransfersComponent = clientComponent(InventoryTransfersView, inventoryTransfersSource)
// The Node-only template compiler needs runtime syntax; erase TS non-null assertions only.
const reportsComponent = clientComponent(ReportsView, reportsSource.replace(/(?<=[\w)])!(?=[.)\]])/g, ''))
function text(target: Node): string { return target.tag === '#comment' ? '' : target.text + target.children.map(text).join('') }
function find(target: Node, predicate: (target: Node) => boolean): Node | undefined {
  if (predicate(target)) return target
  for (const child of target.children) { const result = find(child, predicate); if (result) return result }
}
function findAll(target: Node, predicate: (target: Node) => boolean): Node[] {
  return [...(predicate(target) ? [target] : []), ...target.children.flatMap(child => findAll(child, predicate))]
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
  presentation: null, evidence: null, fitSummary: { fitTendency: 'TRUE_TO_SIZE' as const, widthProfile: 'REGULAR' as const },
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
  options: [
    { color: 'Chalk', sizes: [
      { size: '40', variantId: 'chalk-40', sku: 'FIT-CHALK-40', availability: 'AVAILABLE' as const, amount: 1200000, availableAlternativeColors: [] },
      { size: '41', variantId: 'chalk-41', sku: 'FIT-CHALK-41', availability: 'AVAILABLE' as const, amount: 1200000, availableAlternativeColors: [] },
    ] },
    { color: 'Ink', sizes: [
      { size: '40', variantId: 'ink-40', sku: 'FIT-INK-40', availability: 'UNAVAILABLE' as const, amount: 1200000, availableAlternativeColors: ['Chalk'] },
      { size: '41', variantId: null, sku: null, availability: 'UNAVAILABLE' as const, amount: null, availableAlternativeColors: ['Chalk'] },
    ] },
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
    { path: '/orders/:id', component: orderComponent },
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
afterEach(() => { app?.unmount(); vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('guest registration cart handoff', () => {
  const guestLine = { productId: 'shoe', productName: 'Shoe', variantId: A, sku: 'SHOE-A', size: '40', color: 'Black', image: null, amount: 1200000, currency: 'VND' as const }
  const otherLine = { ...guestLine, variantId: B, sku: 'SHOE-B', size: '41' }
  function guestDemand() {
    cart.owner = null; cart.items = []
    addToCart(guestLine); setCartQuantity(A, 2)
    addToCart(otherLine); setCartQuantity(B, 3)
  }
  async function register() {
    await mount(registerComponent, '/register')
    for (const [id, value] of [['register-login', 'new.customer'], ['register-password', 'long-password-123'], ['register-confirmation', 'long-password-123']]) {
      const input = find(root, target => target.props.id === id)!
      ;(input as unknown as { value: string }).value = value
      ;(input.addEventListener as ReturnType<typeof vi.fn>).mock.calls.find(([event]) => event === 'input')?.[1]({ target: input })
    }
    const form = find(root, target => target.tag === 'form')!
    await (form.props.onSubmit as (event: { preventDefault: () => void }) => Promise<void>)({ preventDefault() {} })
    await settle()
  }
  beforeEach(() => {
    vi.stubGlobal('BroadcastChannel', class { postMessage() {} close() {} })
    vi.stubGlobal('Document', class {})
    vi.stubGlobal('ShadowRoot', class {})
    session.account = undefined
    guestDemand()
  })
  it('registers, logs in, merges two guest variants with account demand, and reloads once', async () => {
    saved.set('shoe-commerce:cart:v2:new', JSON.stringify({ owner: 'new', items: [{ ...guestLine, quantity: 9 }] }))
    vi.spyOn(api, 'register').mockResolvedValue({ accountId: 'new', login: 'new.customer' })
    vi.spyOn(api, 'login').mockResolvedValue({ accountId: 'new', login: 'new.customer', roles: [], permissions: ['ORDER_PLACE'] })
    await register()
    expect(api.register).toHaveBeenCalledWith('new.customer', 'long-password-123')
    expect(api.login).toHaveBeenCalledWith('new.customer', 'long-password-123')
    expect(session.account?.accountId).toBe('new')
    expect(cart.owner).toBe('new')
    expect(cart.items.map(({ variantId, quantity }) => ({ variantId, quantity }))).toEqual([{ variantId: A, quantity: 10 }, { variantId: B, quantity: 3 }])
    expect(saved.has('shoe-commerce:cart:v2:guest')).toBe(false)
    cart.items = []; cart.owner = null
    activateCart('new')
    expect(cart.items.map(({ variantId, quantity }) => ({ variantId, quantity }))).toEqual([{ variantId: A, quantity: 10 }, { variantId: B, quantity: 3 }])
  })
  it('preserves guest demand when registration or auto-login fails', async () => {
    vi.spyOn(api, 'register').mockRejectedValueOnce(new Error('registration rejected')).mockResolvedValueOnce({ accountId: 'new', login: 'new.customer' })
    vi.spyOn(api, 'login').mockRejectedValue(new Error('login rejected'))
    await register()
    expect(cart.owner).toBeNull()
    expect(cart.items.map(item => item.quantity)).toEqual([2, 3])
    expect(api.login).not.toHaveBeenCalled()
    app.unmount()
    await register()
    expect(cart.owner).toBeNull()
    expect(cart.items.map(item => item.quantity)).toEqual([2, 3])
    expect(saved.has('shoe-commerce:cart:v2:guest')).toBe(true)
  })
})

describe('order refresh privacy and recovery', () => {
  const privateOrder = { ...order, id: 'a', orderReference: 'PRIVATE-A', receiverName: 'Private receiver',
    locationName: 'Test floor', locationCode: 'FLOOR', itemCount: 2, currency: 'VND', deliveryFeeAmount: 0,
    receiverPhone: '0900000000', deliveryAddress: 'Private address', fulfillmentType: 'DELIVERY',
    pickupStatus: 'PENDING_PAYMENT', cancellationEligible: true, createdAt: '2026-10-01T00:00:00Z' } as Order

  it.each([401, 403, 404])('removes private details and actions after refresh returns %s', async status => {
    vi.spyOn(api, 'order').mockResolvedValueOnce(privateOrder).mockRejectedValueOnce(new ApiError(status, 'DENIED', ''))
    await mount(orderComponent, '/orders/a')
    expect(text(root)).toContain('Private address')
    await click('Refresh status')
    expect(text(root)).not.toContain('Private address')
    expect(text(root)).not.toContain('Private receiver')
    expect(text(root)).not.toContain('PRIVATE-A')
    expect(find(root, target => target.tag === 'dialog')).toBeUndefined()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Pay with VNPAY'))).toBeUndefined()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Cancel before dispatch'))).toBeUndefined()
    expect(find(root, target => target.props.role === 'alert')).toBeDefined()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Try again'))).toBeDefined()
  })

  it('allows an explicit retry after a temporary network failure without stale actions', async () => {
    vi.spyOn(api, 'order').mockResolvedValueOnce(privateOrder)
      .mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce(privateOrder)
    await mount(orderComponent, '/orders/a')
    await click('Refresh status')
    expect(text(root)).toContain(t('The service is temporarily unavailable. Please retry.'))
    expect(text(root)).not.toContain('Private address')
    await click('Try again')
    expect(text(root)).toContain('Private address')
    expect(find(root, target => target.props.role === 'alert')).toBeUndefined()
  })

  it('clears the old order on a fast ID change and ignores its late refresh', async () => {
    const old = deferred<Order>(), latest = deferred<Order>()
    vi.spyOn(api, 'order').mockResolvedValueOnce(privateOrder).mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise)
    const router = await mount(orderComponent, '/orders/a')
    await click('Refresh status')
    await router.push('/orders/b'); await settle()
    expect(text(root)).not.toContain('Private address')
    latest.resolve({ ...privateOrder, id: 'b', orderReference: 'CURRENT-B', deliveryAddress: 'Current address' }); await settle()
    old.resolve(privateOrder); await settle()
    expect(text(root)).toContain('CURRENT-B')
    expect(text(root)).not.toContain('PRIVATE-A')
    expect(text(root)).not.toContain('Private address')
  })

  it.each(['pending', 'failed'] as const)('retries B after A is %s without restoring A', async state => {
    const old = deferred<Order>(), retry = deferred<Order>()
    const currentOrder = { ...privateOrder, id: 'b', orderReference: 'CURRENT-B',
      receiverName: 'Receiver B', receiverPhone: '0800000000', deliveryAddress: 'Address B' }
    const reads = vi.spyOn(api, 'order').mockResolvedValueOnce(privateOrder)
      .mockReturnValueOnce(old.promise).mockRejectedValueOnce(new TypeError('B unavailable'))
      .mockReturnValueOnce(retry.promise)
    const router = await mount(orderComponent, '/orders/a')
    await click('Refresh status')
    if (state === 'failed') { old.reject(new TypeError('A unavailable')); await settle() }
    await router.push('/orders/b'); await settle()
    expect(find(root, n => n.props.role === 'alert')).toBeDefined()
    await click('Try again')
    expect(reads.mock.calls.map(args => args[0])).toEqual(['a', 'a', 'b', 'b'])
    expect(text(find(root, n => n.props.role === 'status')!)).toBe(t('Loading authoritative order status…'))
    expect(text(root)).not.toContain('PRIVATE-A')
    retry.resolve(currentOrder); await settle()
    if (state === 'pending') { old.resolve(privateOrder); await settle() }
    expect(text(root)).toContain('CURRENT-B')
    expect(text(root)).toContain('Address B')
    for (const secret of ['PRIVATE-A', 'Private receiver', '0900000000', 'Private address']) {
      expect(text(root)).not.toContain(secret)
    }
    expect(find(root, n => n.props.role === 'alert' || n.props.role === 'status')).toBeUndefined()
  })

  it.each(['PICKUP', 'DELIVERY'] as const)('renders authoritative location name and code for %s', async fulfillmentType => {
    vi.spyOn(api, 'order').mockResolvedValue({ ...privateOrder, fulfillmentType,
      locationName: 'Fixture location', locationCode: 'LOC-42' })
    await mount(orderComponent, '/orders/a')
    const label = t(fulfillmentType === 'PICKUP' ? 'Pickup location' : 'Dispatch location')
    const term = find(root, n => n.tag === 'dt' && text(n) === label)
    expect(term).toBeDefined()
    const value = term!.parent!.children.find(n => n.tag === 'dd')
    expect(value).toBeDefined()
    expect(text(value!)).toBe('Fixture location · LOC-42')
    expect(find(root, n => n.tag === 'dt' && text(n) === t(fulfillmentType === 'PICKUP' ? 'Dispatch location' : 'Pickup location'))).toBeUndefined()
  })

  it('clears private details and transaction actions while refresh remains deferred', async () => {
    const pending = deferred<Order>()
    vi.spyOn(api, 'order').mockResolvedValueOnce(privateOrder).mockReturnValueOnce(pending.promise)
    await mount(orderComponent, '/orders/a')
    expect(text(root)).toContain('PRIVATE-A')
    expect(find(root, n => n.tag === 'button' && text(n) === t('Pay with VNPAY'))).toBeDefined()
    expect(find(root, n => n.tag === 'button' && text(n) === t('Cancel before dispatch'))).toBeDefined()
    await click('Refresh status')
    const status = find(root, n => n.props.role === 'status')
    expect(status).toBeDefined()
    expect(text(status!)).toBe(t('Loading authoritative order status…'))
    for (const secret of ['PRIVATE-A', 'Private receiver', '0900000000', 'Private address']) expect(text(root)).not.toContain(secret)
    expect(find(root, n => n.tag === 'dialog')).toBeUndefined()
    expect(find(root, n => n.tag === 'button')).toBeUndefined()
    expect(find(root, n => n.props.role === 'alert')).toBeUndefined()
    pending.reject(new TypeError('offline')); await settle()
    expect(find(root, n => n.props.role === 'status')).toBeUndefined()
    expect(find(root, n => n.props.role === 'alert')).toBeDefined()
    expect(text(root)).not.toContain('PRIVATE-A')
  })

  it.each([false, true])('clears the actual app shell on account change (pending refresh: %s)', async pending => {
    vi.stubGlobal('window', { addEventListener: vi.fn(), removeEventListener: vi.fn(), setInterval: vi.fn(), clearInterval: vi.fn() })
    vi.stubGlobal('BroadcastChannel', class { close() {} })
    session.loaded = true; session.unavailable = false
    const old = deferred<Order>()
    const reads = vi.spyOn(api, 'order').mockResolvedValueOnce(privateOrder)
    if (pending) reads.mockReturnValueOnce(old.promise)
    reads.mockRejectedValueOnce(new ApiError(403, 'FORBIDDEN', ''))
    await mount(shellComponent, '/orders/a')
    expect(text(root)).toContain('Private address')
    if (pending) await click('Refresh status')
    session.account = { accountId: 'other', login: 'other', roles: [], permissions: ['ORDER_PLACE'] }
    session.generation++
    await settle()
    old.resolve(privateOrder); await settle()
    expect(text(root)).not.toContain('Private address')
    expect(text(root)).not.toContain('PRIVATE-A')
    expect(find(root, target => target.tag === 'button' && text(target) === t('Pay with VNPAY'))).toBeUndefined()
  })
})

describe('transfer task filtering', () => {
  it('filters returned transfers and removes stale actions after a failed refresh', async () => {
    vi.spyOn(api, 'inventoryTransferOptions').mockResolvedValue({ locations: [], stock: [] })
    const reads = vi.spyOn(api, 'inventoryTransfers').mockResolvedValue([
      { transferId: 'draft-one', sourceLocationId: 'a', sourceLocationCode: 'A', sourceLocationName: 'A', destinationLocationId: 'b', destinationLocationCode: 'B', destinationLocationName: 'B', status: 'DRAFT', createdAt: '2026-10-01T00:00:00Z', dispatchedAt: null, receivedAt: null, items: [], replayed: false },
      { transferId: 'received-two', sourceLocationId: 'a', sourceLocationCode: 'A', sourceLocationName: 'A', destinationLocationId: 'b', destinationLocationCode: 'B', destinationLocationName: 'B', status: 'RECEIVED', createdAt: '2026-10-01T00:00:00Z', dispatchedAt: '2026-10-02T00:00:00Z', receivedAt: '2026-10-03T00:00:00Z', items: [], replayed: false },
    ])
    await mount(inventoryTransfersComponent, '/operations/inventory/transfers')
    const filter = find(root, target => target.tag === 'select' && !!target.props.onChange && text(target).includes('(2)'))!
    expect(text(root)).toContain('draft-one')
    ;(filter.props.onChange as Function)({ target: { value: 'RECEIVED' } })
    await settle()
    expect(text(root)).not.toContain('draft-one')
    expect(text(root)).toContain('received-two')
    ;(filter.props.onChange as Function)({ target: { value: 'DISPATCHED' } })
    await settle()
    expect(text(root)).toContain(t('No transfers match this status.'))
    reads.mockRejectedValueOnce(new ApiError(403, 'FORBIDDEN', 'Denied'))
    await click('Refresh')
    expect(text(filter)).toContain('(0)')
    expect(text(root)).not.toContain('received-two')
    expect(find(root, target => target.props.role === 'alert')).toBeDefined()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Dispatch'))).toBeUndefined()
  })
})

describe('reporting interaction', () => {
  const context = { from:'2026-08-27T17:00:00Z',to:'2026-08-29T17:00:00Z',asOf:'2026-08-29T05:00:00Z',businessTimezone:'Asia/Ho_Chi_Minh' as const,
    scope:{branchId:'branch',branchCode:'BR',branchName:'Test branch',locationId:'location',locationCode:'LOC',locationName:'Test floor'} }
  const net = {context,onlineGross:'180000',posGross:'0',grossSales:'180000',successfulVoids:'180000',netSales:'0',exceptionAmount:'0',exceptionCount:0,currency:'VND' as const,
    merchandiseGross:'200000',itemDiscount:'0',orderDiscount:'50000',voucherDiscount:'0',merchandiseNetBeforeReversal:'150000',shippingGross:'30000',shippingDiscount:'0',shippingNetBeforeReversal:'30000',merchandiseVoids:'150000',shippingVoids:'30000',unallocatedLegacyVoids:'0'}
  const products = {context,rows:[{variantId:'variant',sku:'DISCOUNTED-SKU',size:'40',onlineGross:'200000',posGross:'0',grossSales:'200000',successfulVoids:'150000',netSales:'0',itemDiscount:'0',orderDiscount:'50000',voucherDiscount:'0',merchandiseNetBeforeReversal:'150000'}],
    onlineGross:'200000',posGross:'0',grossSales:'200000',successfulVoids:'150000',netSales:'0',currency:'VND' as const,itemDiscount:'0',orderDiscount:'50000',voucherDiscount:'0',merchandiseNetBeforeReversal:'150000',unallocatedLegacyVoids:'0'}
  function reportsFixture(entries: ReconciliationEntry[] = []) {
    vi.spyOn(api,'reportScope').mockResolvedValue({asOf:context.asOf,businessTimezone:context.businessTimezone,defaultFromDate:'2026-08-28',defaultToDate:'2026-08-30',locations:[context.scope]})
    vi.spyOn(api,'netSales').mockResolvedValue(net)
    vi.spyOn(api,'productSales').mockResolvedValue(products)
    vi.spyOn(api,'inventoryReport').mockResolvedValue({context,rows:[],movements:[],reservations:[]})
    vi.spyOn(api,'reconciliation').mockResolvedValue({context,entries,exceptionAmount:entries.length ? '125000' : '0',exceptionCount:entries.length,currency:'VND'})
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
  it('shows one explicit QueryDr action only to an authorized operator and reports the safe outcome', async () => {
    session.account = { accountId: 'operator', login: 'operator', roles: ['OPERATIONS'], permissions: ['REPORT_VIEW', 'PAYMENT_RECONCILE'] }
    reportsFixture([{ category:'PAYMENT_REVIEW',referenceId:'attempt-1',orderId:'order-1',status:'REVIEW_REQUIRED',amount:'125000',netEffect:'0',occurredAt:context.asOf,exception:true }])
    const reconcile = vi.spyOn(api,'reconcilePaymentAttempt').mockResolvedValue({attemptId:'attempt-1',orderId:'order-1',providerReference:'PAY123',previousState:'REVIEW_REQUIRED',currentState:'REVIEW_REQUIRED',outcome:'STILL_REVIEW_REQUIRED',resolved:false,replayed:false})
    await mount(reportsComponent,'/reports')

    await click('Reconcile with VNPAY')

    expect(reconcile).toHaveBeenCalledOnce()
    expect(text(root)).toContain(t('Still requires review'))
  })
  it('does not expose the financial action without PAYMENT_RECONCILE', async () => {
    session.account = { accountId: 'reader', login: 'reader', roles: ['OPERATIONS'], permissions: ['REPORT_VIEW'] }
    reportsFixture([{ category:'PAYMENT_REVIEW',referenceId:'attempt-1',orderId:'order-1',status:'REVIEW_REQUIRED',amount:'125000',netEffect:'0',occurredAt:context.asOf,exception:true }])
    await mount(reportsComponent,'/reports')

    expect(find(root, target => target.tag === 'button' && text(target) === t('Reconcile with VNPAY'))).toBeUndefined()
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
    expect(rendered).toContain(t('Selections'))
    expect(rendered).toContain(`${t('Size')} 39 · ${t('White')}`)
    expect(rendered).not.toContain('COURT-39')
    expect(rendered).not.toContain('SKU')
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
  it('quotes and checks out delivery only after ward resolution and maps address errors to the field', async () => {
    vi.spyOn(api, 'provinces').mockResolvedValue([{ code: '79', label: 'Ho Chi Minh City' }])
    vi.spyOn(api, 'districts').mockResolvedValue([{ code: '760', label: 'District 1' }])
    vi.spyOn(api, 'wards').mockResolvedValue([{ code: 'DEMO-760-01', label: 'Ben Nghe Ward' }])
    vi.mocked(api.cartCheckout).mockRejectedValueOnce(new ApiError(400, 'DELIVERY_ADDRESS_INVALID', 'private detail', undefined, undefined, { addressLine: 'INVALID' }))
    await mount()
    ;(find(root, target => target.tag === 'input' && target.props.value === 'DELIVERY')!.props.onChange as () => void)()
    await settle()
    ;(find(root, target => target.props.id === 'delivery-province')!.props.onChange as (event: unknown) => void)({ target: { value: '79' } })
    await settle()
    ;(find(root, target => target.props.id === 'delivery-district')!.props.onChange as (event: unknown) => void)({ target: { value: '760' } })
    await settle()
    ;(find(root, target => target.props.id === 'delivery-ward')!.props.onChange as (event: unknown) => void)({ target: { value: 'DEMO-760-01' } })
    await settle()
    await click('Check price & availability')
    expect(api.cartQuote).not.toHaveBeenCalled()
    expect(text(root)).toContain(t('Enter a valid street and house address.'))
    for (const [id, value] of [['receiver-name', 'Nguyen Van A'], ['receiver-phone', '0900000000'], ['delivery-address-line', '12 Nguyen Hue']] as const) {
      ;(find(root, target => target.props.id === id)!.props.onInput as (event: unknown) => void)({ target: { value } })
    }
    await settle()
    await click('Check price & availability')
    expect(vi.mocked(api.cartQuote).mock.calls[0][1]).toEqual({
      type: 'DELIVERY', destinationProvinceCode: '79', destinationDistrictCode: '760', destinationWardCode: 'DEMO-760-01',
    })
    await click('Confirm total & create order')
    expect(find(root, target => target.props.id === 'delivery-address-line')?.props['aria-invalid']).toBe('true')
    expect(text(root)).toContain(t('Enter a valid street and house address.'))
    expect(text(root)).not.toContain('private detail')
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
    let resolveOld!: (value: Awaited<ReturnType<typeof api.discovery>>) => void
    vi.spyOn(api, 'discovery').mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValue({ query: 'runner', results: [], suggestions: [], interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false } })
    vi.spyOn(api, 'products').mockResolvedValue([{ id: A, name: 'Full catalog shoe', fromAmount: 1, availableVariantCount: 1, presentation: null }] as never)
    const router = await mount(catalogComponent, '/?q=court')
    const input = find(root, target => target.props.id === 'product-search')!
    await (input.props.onInput as (event: unknown) => Promise<void>)({ target: { value: '   ' } })
    await settle()
    expect(router.currentRoute.value.query.q).toBeUndefined()
    expect(api.products).toHaveBeenLastCalledWith()
    expect(text(root)).toContain('Full catalog shoe')
    resolveOld({ query: 'court', results: [], suggestions: [], interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false } }); await settle()
    expect(text(root)).toContain('Full catalog shoe')
    ;(input.props.onInput as (event: unknown) => Promise<void>)({ target: { value: 'runner' } })
    await router.replace({ path: '/', query: { q: 'runner' } })
    await settle()
    expect(router.currentRoute.value.query.q).toBe('runner')
    expect(api.discovery).toHaveBeenLastCalledWith('runner')
    expect(text(root)).toContain('Results for “runner”')
  })
  it('debounces draft autocomplete, limits visible options, and keeps submitted results separate', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'products').mockResolvedValue([])
    const products = Array.from({ length: 8 }, (_, index) => ({ id: `${A}-${index}`, name: `Runner ${index}`, availableVariantCount: 1 })) as ProductSummary[]
    vi.spyOn(api, 'discovery').mockResolvedValue({ query: 'runner', results: products, suggestions: [], interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false } })
    await mount(catalogComponent, '/')
    vi.useFakeTimers()
    const input = find(root, target => target.props.id === 'product-search')!
    for (const value of ['r', 'run', 'runner']) (input.props.onInput as Function)({ target: { value } })
    await settle()
    expect(api.discovery).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(250); await settle()
    expect(api.discovery).toHaveBeenCalledTimes(1)
    expect(api.discovery).toHaveBeenCalledWith('runner')
    expect(findAll(root, target => target.props.role === 'option')).toHaveLength(6)
    expect(text(root)).not.toContain('Results for “runner”')
    vi.useRealTimers()
  })
  it('ignores stale autocomplete success and error after clear, and keeps keyboard focus on input', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'products').mockResolvedValue([])
    const old = deferred<Awaited<ReturnType<typeof api.discovery>>>()
    vi.spyOn(api, 'discovery').mockReturnValueOnce(old.promise).mockResolvedValue({ query: 'court', results: [{ id: A, name: 'Court shoe', availableVariantCount: 1, presentation: null, evidence: null, fitSummary: null }] as ProductSummary[], suggestions: [], interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false } })
    const router = await mount(catalogComponent, '/')
    vi.useFakeTimers()
    const input = find(root, target => target.props.id === 'product-search')!
    ;(input.props.onInput as Function)({ target: { value: 'runner' } })
    await vi.advanceTimersByTimeAsync(250)
    ;(input.props.onInput as Function)({ target: { value: 'court' } })
    await vi.advanceTimersByTimeAsync(250); await settle()
    old.reject(new Error('old failure')); await settle()
    expect(text(root)).toContain('Court shoe')
    expect(input.props['aria-expanded']).toBe(true)
    ;(input.props.onKeydown as Function)({ key: 'ArrowDown', preventDefault() {} }); await settle()
    expect(input.props['aria-activedescendant']).toBeTruthy()
    expect(input.focus).not.toHaveBeenCalled()
    ;(input.props.onKeydown as Function)({ key: 'Enter', preventDefault() {} }); await vi.advanceTimersByTimeAsync(0); await settle()
    expect(router.currentRoute.value.path).toBe(`/products/${A}`)
    vi.useRealTimers()
  })
  it('clears stale autocomplete on Escape, input clear, route change, and unmount', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'products').mockResolvedValue([])
    const old = deferred<Awaited<ReturnType<typeof api.discovery>>>()
    vi.spyOn(api, 'discovery').mockReturnValueOnce(old.promise).mockResolvedValue({ query: 'court', results: [], suggestions: [], interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false } })
    const router = await mount(catalogComponent, '/')
    vi.useFakeTimers()
    const input = find(root, target => target.props.id === 'product-search')!
    ;(input.props.onInput as Function)({ target: { value: 'runner' } })
    await vi.advanceTimersByTimeAsync(250); await settle()
    expect(text(root)).toContain(t('Searching products…'))
    ;(input.props.onKeydown as Function)({ key: 'Escape', preventDefault() {} }); await settle()
    expect(input.props['aria-expanded']).toBe(false)
    old.resolve({ query: 'runner', results: [{ id: A, name: 'Old runner', availableVariantCount: 1 }] as ProductSummary[], suggestions: [], interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false } }); await settle()
    expect(text(root)).not.toContain('Old runner')
    ;(input.props.onInput as Function)({ target: { value: 'co' } })
    ;(input.props.onInput as Function)({ target: { value: '' } })
    await vi.advanceTimersByTimeAsync(250); await settle()
    expect(api.discovery).toHaveBeenCalledTimes(1)
    ;(input.props.onInput as Function)({ target: { value: 'court' } })
    await vi.advanceTimersByTimeAsync(250); await settle()
    await router.replace('/?q=court'); await settle()
    expect(input.props['aria-expanded']).toBe(false)
    ;(input.props.onInput as Function)({ target: { value: 'trail' } })
    app.unmount()
    await vi.runOnlyPendingTimersAsync()
    expect(api.discovery).toHaveBeenCalledTimes(3)
  })
  it('shows empty and error recovery without changing submitted results, including Vietnamese copy', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'products').mockResolvedValue([])
    vi.spyOn(api, 'discovery').mockResolvedValueOnce({ query: 'zz', results: [], suggestions: [], interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false } }).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ query: 'zz', results: [], suggestions: [{ id: A, name: 'Nearby shoe', availableVariantCount: 1 }] as ProductSummary[], interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false } })
    await mount(catalogComponent, '/')
    vi.useFakeTimers()
    const input = find(root, target => target.props.id === 'product-search')!
    ;(input.props.onInput as Function)({ target: { value: 'zz' } })
    await vi.advanceTimersByTimeAsync(250); await settle()
    expect(text(root)).toContain(t('No suggestions yet. Press Enter to search.'))
    ;(input.props.onInput as Function)({ target: { value: 'zzz' } })
    await vi.advanceTimersByTimeAsync(250); await settle()
    expect(text(root)).toContain(t('Suggestions unavailable. Keep typing or search.'))
    setLocale('vi-VN'); await settle()
    expect(text(root)).toContain('Chưa tải được gợi ý')
    const retry = find(root, target => target.tag === 'button' && text(target) === t('Try again'))!
    ;(retry.props.onClick as Function)()
    await vi.advanceTimersByTimeAsync(250); await settle()
    expect(text(root)).toContain('Nearby shoe')
    expect(text(root)).toContain('Sản phẩm tương tự')
    expect(text(root)).not.toContain('Results for “zzz”')
  })
  it('keeps free Enter submission and Tab editing while ArrowUp selects the last option', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'products').mockResolvedValue([])
    vi.spyOn(api, 'discovery').mockResolvedValue({ query: 'runner', results: [{ id: A, name: 'First shoe', presentation: null, evidence: null, fitSummary: null }, { id: B, name: 'Last shoe', presentation: null, evidence: null, fitSummary: null }] as ProductSummary[], suggestions: [], interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false } })
    const router = await mount(catalogComponent, '/')
    vi.useFakeTimers()
    const input = find(root, target => target.props.id === 'product-search')!
    ;(input.props.onInput as Function)({ target: { value: 'runner' } })
    await vi.advanceTimersByTimeAsync(250); await settle()
    const prevent = vi.fn()
    ;(input.props.onKeydown as Function)({ key: 'Enter', preventDefault: prevent })
    ;(input.props.onKeydown as Function)({ key: 'Tab', preventDefault: prevent })
    expect(prevent).not.toHaveBeenCalled()
    const form = find(root, target => target.tag === 'form' && target.props.role === 'search')!
    await (form.props.onSubmit as Function)({ preventDefault() {} }); await vi.advanceTimersByTimeAsync(0); await settle()
    expect(router.currentRoute.value.query.q).toBe('runner')
    ;(input.props.onInput as Function)({ target: { value: 'runner' } })
    await vi.advanceTimersByTimeAsync(250); await settle()
    ;(input.props.onKeydown as Function)({ key: 'ArrowUp', preventDefault: prevent }); await settle()
    expect(input.props['aria-activedescendant']).toBe('product-search-option-1')
    ;(input.props.onKeydown as Function)({ key: 'Escape', preventDefault: prevent }); await settle()
    expect(input.props['aria-expanded']).toBe(false)
    expect(router.currentRoute.value.path).toBe('/')
  })
  it('keeps the new loading state when an older autocomplete request finishes', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'products').mockResolvedValue([])
    const old = deferred<Awaited<ReturnType<typeof api.discovery>>>()
    const current = deferred<Awaited<ReturnType<typeof api.discovery>>>()
    vi.spyOn(api, 'discovery').mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise)
    await mount(catalogComponent, '/')
    vi.useFakeTimers()
    const input = find(root, target => target.props.id === 'product-search')!
    ;(input.props.onInput as Function)({ target: { value: 'runner' } })
    await vi.advanceTimersByTimeAsync(250); await settle()
    ;(input.props.onInput as Function)({ target: { value: 'court' } })
    await vi.advanceTimersByTimeAsync(250); await settle()
    old.reject(new Error('stale')); await settle()
    expect(text(root)).toContain(t('Searching products…'))
    expect(text(root)).not.toContain(t('Suggestions unavailable. Keep typing or search.'))
    current.resolve({ query: 'court', results: [], suggestions: [], interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false } }); await settle()
    expect(text(root)).toContain(t('No suggestions yet. Press Enter to search.'))
  })
  it('keeps exact discovery results separate from available recovery suggestions', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'products').mockResolvedValue([])
    vi.spyOn(api, 'discovery').mockResolvedValue({
      query: 'purple trail', results: [],
      suggestions: [{
        id: A, name: 'Available trail shoe', category: 'Trail', collection: null, featured: false,
        newArrival: false, campaignEligible: false, merchandisingRank: 1, heroImage: null, primaryImage: null,
        variantCount: 2, availableVariantCount: 1, fromAmount: 1500000, presentation: null,
        evidence: null, fitSummary: null,
      }],
      interpreted: { colors: [], categories: ['TRAIL'], maximumPrice: null, skuLookup: false },
    })
    await mount(catalogComponent, '/?q=purple%20trail')

    expect(text(root)).toContain('Results for “purple trail”')
    expect(text(root)).toContain('No exact matches')
    expect(text(root)).toContain('You might like')
    expect(text(root)).toContain('Available trail shoe')
    expect(api.discovery).toHaveBeenCalledWith('purple trail')
  })
  it('sends selected supported intent filters through the existing q route and discovery API', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'products').mockResolvedValue([])
    vi.spyOn(api, 'discovery').mockResolvedValue({ query: 'running black under 1500000', results: [], suggestions: [], interpreted: { colors: ['BLACK'], categories: ['RUNNING'], maximumPrice: 1500000, skuLookup: false } })
    const router = await mount(catalogComponent, '/')
    const form = find(root, target => target.tag === 'form' && target.props.class === 'storefront-intent-filters')!
    const selects = findAll(form, target => target.tag === 'select')
    ;(selects[0]!.props.onChange as Function)({ target: { value: 'running' } })
    ;(selects[1]!.props.onChange as Function)({ target: { value: 'black' } })
    ;(find(form, target => target.tag === 'input')!.props.onInput as Function)({ target: { value: '1500000' } })
    await (form.props.onSubmit as Function)({ preventDefault() {} })
    await settle()
    const query = 'running black under 1500000'
    expect(router.currentRoute.value.query.q).toBe(query)
    expect(api.discovery).toHaveBeenLastCalledWith(query)
    await router.replace('/?q=running%20under%202%20million')
    await settle()
    expect(find(form, target => target.tag === 'input')!.props.value).toBe('')
    await (find(form, target => target.tag === 'button' && text(target) === t('Clear filters'))!.props.onClick as Function)()
    await settle()
    expect(router.currentRoute.value.query.q).toBeUndefined()
  })
  it('keeps a failed routed search labelled with the current query', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'discovery')
      .mockResolvedValueOnce({ query: 'first', results: [], suggestions: [], interpreted: { colors: ['WHITE'], categories: [], maximumPrice: null, skuLookup: false } })
      .mockRejectedValueOnce(new ApiError(503, 'CATALOG_UNAVAILABLE', 'try again'))
    const router = await mount(catalogComponent, '/?q=first')

    await router.replace('/?q=second')
    await settle()

    expect(text(root)).toContain('Results for “second”')
    expect(text(root)).not.toContain('Results for “first”')
  })
  it('explains recognized shopper intent and keeps search context on product links', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'discovery').mockResolvedValue({
      query: 'white running under 2 million',
      results: [{
        id: A, name: 'Metro Runner', category: 'Running', collection: null, featured: false,
        newArrival: false, campaignEligible: false, merchandisingRank: 1, heroImage: null, primaryImage: null,
        variantCount: 2, availableVariantCount: 2, fromAmount: 1500000, presentation: null,
        evidence: null, fitSummary: null,
      }],
      suggestions: [],
      interpreted: { colors: ['WHITE'], categories: ['RUNNING'], maximumPrice: 2000000, skuLookup: false },
    })

    await mount(catalogComponent, '/?q=white%20running%20under%202%20million')

    expect(text(root)).toContain('Results for “white running under 2 million”')
    expect(text(root)).toContain(`Matched: White · Running · Up to ${formatVnd(2000000)}`)
    const productLink = find(root, target => String(target.props.class).includes('product-card'))!
    expect(decodeURIComponent(String(productLink.props.href)).replaceAll('+', ' '))
      .toBe(`/products/${A}?q=white running under 2 million`)
  })
  it('offers all products and supported intent paths when discovery has no recovery suggestions', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    vi.spyOn(api, 'discovery').mockResolvedValue({
      query: 'purple moon boot', results: [], suggestions: [],
      interpreted: { colors: [], categories: [], maximumPrice: null, skuLookup: false },
    })

    await mount(catalogComponent, '/?q=purple%20moon%20boot')

    const recovery = find(root, target => String(target.props.class).includes('discovery-recovery'))!
    expect(text(recovery)).toContain('Try a broader product name, color, use, or price.')
    expect(text(recovery)).toContain(t('View all products'))
    expect(text(recovery)).toContain(t('Road running'))
    expect(text(recovery)).toContain(t('Everyday wear'))
    expect(text(recovery)).toContain(t('Trail'))
  })
  it('keeps the default catalog preview to six products and expands only through the view route', async () => {
    vi.spyOn(api, 'storefrontHomepage').mockResolvedValue({ publishedRevisionId: null, publishedAt: null, sections: [] })
    const catalog = Array.from({ length: 8 }, (_, index): ProductSummary => ({
      id: `product-${index}`, name: `Product ${index}`, category: 'Running', collection: 'Metro Motion',
      featured: false, newArrival: false, campaignEligible: true, merchandisingRank: index,
      heroImage: null, primaryImage: null, variantCount: 4, availableVariantCount: 3, fromAmount: 1000000 + index,
      presentation: null,
      evidence: { intendedUse: 'ROAD_RUNNING', primarySurface: 'ROAD_TRACK', upperConstruction: 'MESH_TEXTILE', cutProfile: 'LOW_TOP', primarySoleProfile: 'FLEX_GROOVED' },
      fitSummary: null,
    }))
    vi.spyOn(api, 'products').mockResolvedValue(catalog)

    const router = await mount(catalogComponent, '/')
    expect(findAll(root, target => target.props.class === 'product-card')).toHaveLength(6)
    expect(text(root)).toContain(t('View all products'))
    expect(text(root)).toContain(t('Road running'))

    const input = find(root, target => target.props.id === 'product-search')!
    ;(input.props.onInput as (event: unknown) => void)({ target: { value: 'not submitted' } })
    await router.push('/?view=all')
    await settle()
    expect(findAll(root, target => target.props.class === 'product-card')).toHaveLength(8)
    expect(text(root)).not.toContain(t('View all products'))
    expect(find(root, target => target.props.id === 'product-search')!.props.value).toBe('')
    expect(find(root, target => target.props.id === 'catalog-heading')!.focus).not.toHaveBeenCalled()
    expect(api.products).toHaveBeenCalledOnce()

    ;(find(root, target => target.props.id === 'product-search')!.props.onInput as (event: unknown) => void)({ target: { value: 'another draft' } })
    router.back()
    await vi.waitFor(() => expect(router.currentRoute.value.query.view).toBeUndefined())
    await settle()
    expect(find(root, target => target.props.id === 'product-search')!.props.value).toBe('')
    expect(findAll(root, target => target.props.class === 'product-card')).toHaveLength(6)
    expect(api.products).toHaveBeenCalledOnce()
  })
  it('guards rapid add activation and announces the current quantity', async () => {
    cart.items = []
    vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, variants: [{ ...fitProduct.variants[1], id: A }], options: [{ color: 'Chalk', sizes: [{ ...fitProduct.options[0].sizes[1], variantId: A }] }] })
    await mount(productComponent, '/products/fit-product')
    await click('Chalk')
    const size = find(root, target => target.tag === 'button' && String(target.props.class).includes('variant-option'))!
    ;(size.props.onClick as () => void)()
    await settle()
    const add = find(root, target => target.tag === 'button' && text(target) === t('Add to cart'))!
    ;(add.props.onClick as () => void)()
    ;(add.props.onClick as () => void)()
    await settle()
    expect(cart.items[0].quantity).toBe(1)
    expect(text(root)).toContain('Added Court Classic · In cart: 1')
    setLocale('vi-VN'); await settle()
    expect(text(root)).toContain('Đã thêm Court Classic · Trong giỏ: 1')
    expect(add.props.disabled).toBe(true)
    expect(api.cartCheckout).not.toHaveBeenCalled()
    expect(JSON.parse(saved.get('shoe-commerce:cart:v2:owner')!).items[0].quantity).toBe(1)
  })
  it('buys now once and opens the cart only after the add succeeds', async () => {
    cart.items = []
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    const router = await mount(productComponent, '/products/fit-product')
    await click('Chalk')
    const size = find(root, target => target.tag === 'button' && String(target.props.class).includes('variant-option'))!
    ;(size.props.onClick as () => void)()
    await settle()

    const push = vi.spyOn(router, 'push')
    const buy = find(root, target => target.tag === 'button' && text(target) === t('Buy now'))!
    ;(buy.props.onClick as () => void)()
    ;(buy.props.onClick as () => void)()
    await settle()

    expect(cart.items).toHaveLength(1)
    expect(cart.items[0].quantity).toBe(1)
    expect(push).toHaveBeenCalledWith('/cart')
  })
  it('keeps buy now unavailable without an available variant and stays put when adding fails', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    const router = await mount(productComponent, '/products/fit-product?variant=ink-40')
    expect(find(root, target => target.tag === 'button' && text(target) === t('Buy now'))?.props.disabled).toBe(true)

    await click('Chalk')
    const size = find(root, target => target.tag === 'button' && String(target.props.class).includes('variant-option') && text(target).includes('40'))!
    ;(size.props.onClick as () => void)()
    await settle()
    const push = vi.spyOn(router, 'push')
    cart.items = [{
      productId: fitProduct.id, productName: fitProduct.name, variantId: 'chalk-40', sku: 'FIT-CHALK-40',
      size: '40', color: 'Chalk', image: null, amount: 1200000, currency: 'VND', quantity: 10,
    }]

    await click('Buy now')

    expect(push).not.toHaveBeenCalled()
    expect(cart.items[0].quantity).toBe(10)
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
    const increase = find(root, target => target.tag === 'button' && target.props['aria-label'] === `${t('Increase quantity')} · Metro Runner · ${t('Size')} 42`)!
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
    const recovery = find(root, target => target.tag === 'a' && target.props.class === 'cart-line-recovery')!
    expect(recovery).toBeDefined()
    expect(String(recovery.props.href)).toContain(`variant=${B}`)
    expect(text(recovery)).toContain(t('Choose your size'))
    expect(api.cartCheckout).not.toHaveBeenCalled()
    expect(cart.items).toHaveLength(2)
  })
  it('restores an uncertain checkout after remount and retries without a new quote or key', async () => {
    vi.mocked(api.cartCheckout).mockRejectedValueOnce(new TypeError('network')).mockResolvedValue(order)
    await mount(); await click('Check price & availability'); await click('Confirm total & create order')
    const first = vi.mocked(api.cartCheckout).mock.calls[0]
    app.unmount(); await mount()
    expect(text(root)).toContain(t('Your last checkout may already have created an order. Retry the same request to recover it before changing your cart.'))
    expect(find(root, target => target.tag === 'button' && target.props['aria-label'] === `${t('Increase quantity')} · Metro Runner · ${t('Size')} 42`)?.props.disabled).toBe(true)
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
    vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, id: A, variants: [{ id: A, sku: 'COURT-39', size: '39', color: 'White', amount: 150000, availability: 'AVAILABLE' }], options: [{ color: 'White', sizes: [{ size: '39', variantId: A, sku: 'COURT-39', availability: 'AVAILABLE', amount: 150000, availableAlternativeColors: [] }] }] })
    await mount(productComponent, '/products/' + A)
    await click('White')
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
    vi.mocked(api.savedVouchers).mockResolvedValue({items:[{claimId:'claim-a',claimStatus:'CLAIMED',claimedAt:'2099-01-01T00:00:00Z',offerAvailability:'AVAILABLE',termsAvailable:true,offer:{familyId:'family-a',acquisitionMode:'CLAIMABLE',name:'Owned offer',customerSummary:'Saved',customerTerms:'Terms',effectType:'ORDER_FIXED',fixedAmount:50000,validFrom:'2099-01-01T00:00:00Z',claimable:true}}],page:0,size:20,hasNext:false})
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
        evidence: null, fitSummary: null,
      },
      {
        id: B, name: 'Court High', category: 'Court', collection: 'Court Originals', featured: false,
        newArrival: false, campaignEligible: false, merchandisingRank: 2, heroImage: null, primaryImage: null,
        variantCount: 4, availableVariantCount: 3, fromAmount: 1690000, presentation: null,
        evidence: null, fitSummary: null,
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
  it('returns to the originating discovery context', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product?q=white%20running')

    const back = find(root, target => String(target.props.class).includes('back-link'))!
    expect(text(back)).toBe('Back to results')
    expect(decodeURIComponent(String(back.props.href)).replaceAll('+', ' ')).toBe('/?q=white running#catalog-heading')
  })

  it('uses the full catalog as the fallback from a directly opened product', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product')

    const back = find(root, target => String(target.props.class).includes('back-link'))!
    expect(text(back)).toBe('All products')
    expect(String(back.props.href)).toBe('/?view=all#catalog-heading')
  })

  it('places concise product and fit evidence in the decision area without repeating selection instructions', async () => {
    vi.spyOn(api, 'product').mockResolvedValue({
      ...fitProduct,
      evidence: { intendedUse: 'ROAD_RUNNING', primarySurface: 'ROAD_TRACK', upperConstruction: 'MESH_TEXTILE', cutProfile: 'LOW_TOP', primarySoleProfile: 'FLEX_GROOVED' },
    })
    await mount(productComponent, '/products/fit-product')

    const decision = find(root, target => String(target.props.class).includes('product-decision-panel'))!
    expect(text(decision)).toContain('Mesh upper for road and track running.')
    expect(text(decision)).toContain('True to size · Regular')
    const media = find(root, target => String(target.props.class).includes('product-detail-figure'))!
    expect(text(media)).toContain('Illustrative product view. Your color and size selection below is what applies to the cart.')
    await click('Chalk')
    expect(text(decision)).toContain('Not sure about size?')
    const addPanel = find(decision, target => String(target.props.class).includes('product-add-panel'))!
    expect(find(addPanel, target => String(target.props.class).includes('selection-required'))).toBeUndefined()
  })

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
    const locked = find(root, target => target.tag === 'button' && text(target) === t('Choose a color first'))
    expect(locked).toBeDefined()
    expect(locked!.props.disabled).toBe(true)
    expect(text(root)).toContain(t('Choose a color before selecting a size.'))
  })

  it.each(['en', 'vi-VN'] as const)('uses generic localized gallery navigation in %s', async language => {
    setLocale(language)
    vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, media: [
      twoMedia[0], { ...twoMedia[1], alt: 'Side profile on foot' },
    ] })
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
    expect(mainImage.props.alt).toBe('Side profile on foot')
    expect(thumbnails[0].children[0].props.loading).toBe('lazy')
  })

  it('shows an honest placeholder if the selected product image fails', async () => {
    vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, media: twoMedia })
    await mount(productComponent, '/products/fit-product')
    const mainImage = find(root, target => target.tag === 'img' && String(target.props.width) === '1456')!

    expect(mainImage.props.onError).toBeTypeOf('function')
    ;(mainImage.props.onError as () => void)()
    await settle()
    expect(mainImage.props.src).toBe('/products/placeholder.svg')
    expect(mainImage.props.alt).toBe(t('No product image available'))
    ;(mainImage.props.onError as () => void)()
    await settle()
    expect(mainImage.props.src).toBe('/products/placeholder.svg')
  })

  it('enables the purchase action only after an explicit size choice', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product')
    await click('Chalk')

    const size = find(root, target => target.tag === 'button' && String(target.props.class).includes('variant-option') && text(target).includes('41'))!
    ;(size.props.onClick as () => void)()
    await settle()

    expect(size.props['aria-pressed']).toBe(true)
    const add = find(root, target => target.tag === 'button' && text(target) === t('Add to cart'))
    expect(add).toBeDefined()
    expect(add!.props.disabled).not.toBe(true)
    expect(text(size)).toContain(t('Selected'))
  })

  it('keeps a mobile purchase action visible and sends an unselected shopper to size choices', async () => {
    cart.items = []
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product')
    const stickyAction = find(root, target => String(target.props.class).includes('mobile-purchase-action'))!

    expect(text(stickyAction)).toContain(t('Choose a size'))
    ;(stickyAction.props.onClick as () => void)()
    await settle()
    expect(find(root, target => target.props.id === 'variant-heading')!.focus).toHaveBeenCalled()
    await click('Chalk')
    const size = find(root, target => target.tag === 'button' && String(target.props.class).includes('variant-option') && text(target).includes('41'))!
    ;(size.props.onClick as () => void)()
    await settle()
    expect(text(stickyAction)).toContain(t('Add to cart'))
    ;(stickyAction.props.onClick as () => void)()
    await settle()
    expect(cart.items).toHaveLength(1)
    expect(cart.items[0].variantId).toBe('chalk-41')
  })

  it('keeps a valid deep-linked variant selected', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product?variant=chalk-40')

    const selectedSize = find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)
    expect(selectedSize).toBeDefined()
    expect(text(selectedSize!)).toContain('40')
    expect(find(root, target => String(target.props.class).includes('color-option') && target.props['aria-pressed'] === true && text(target) === t('Chalk'))).toBeDefined()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Add to cart'))?.props.disabled).not.toBe(true)
  })

  it('reapplies a changed variant deep link without refetching the same product', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    const router = await mount(productComponent, '/products/fit-product?variant=chalk-40')

    await router.replace('/products/fit-product?variant=ink-40')
    await settle()

    const selectedSize = find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)!
    expect(text(selectedSize)).toContain(t('Requested · Unavailable'))
    expect(find(root, target => String(target.props.class).includes('color-option') && target.props['aria-pressed'] === true && text(target) === t('Ink'))).toBeDefined()
    expect(api.product).toHaveBeenCalledOnce()
  })

  it('opens and focuses the fit guide from the size decision', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product')
    await click('Chalk')

    const control = find(root, target => text(target) === t('View the size and fit guide'))!
    expect(control.tag).toBe('button')
    ;(control.props.onClick as () => void)()
    await settle()

    const guide = find(root, target => target.props.id === 'size-and-fit')! as Node & { open?: boolean }
    const summary = find(guide, target => target.tag === 'summary')!
    expect(guide.open).toBe(true)
    expect(summary.focus).toHaveBeenCalled()
  })

  it('keeps an unavailable deep-linked size explicit without offering purchase', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product?variant=ink-40')

    const selectedSize = find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)!
    expect(text(selectedSize)).toContain(t('Requested · Unavailable'))
    expect(text(root)).toContain(t('Available in {color}', { color: t('Chalk') }))
    expect(find(root, target => target.tag === 'button' && text(target) === t('Size unavailable'))?.props.disabled).toBe(true)
  })

  it('does not turn an invalid deep link into a default selection', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product?variant=not-a-variant')

    expect(find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)).toBeUndefined()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Choose a color first'))?.props.disabled).toBe(true)
  })

  it('preserves the requested size but clears a stale variant when color changes', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product?variant=chalk-40')

    await click('Ink')

    const requested = find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)!
    expect(text(requested)).toContain('40')
    expect(text(requested)).toContain(t('Requested · Unavailable'))
    expect(find(root, target => target.tag === 'button' && text(target) === t('Size unavailable'))?.props.disabled).toBe(true)
    expect(text(root)).toContain(t('Available in {color}', { color: t('Chalk') }))
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

  async function openFitChooser() {
    const entry = find(root, target => target.tag === 'button' && String(target.props.class).includes('fit-entry'))
    expect(entry).toBeDefined()
    ;(entry!.props.onClick as () => void)()
    await settle()
  }
  async function chooseFitMethod(label: string) {
    const button = find(root, target => target.tag === 'button' && text(target).startsWith(t(label)))
    expect(button, label).toBeDefined()
    ;(button!.props.onClick as () => void)()
    await settle()
  }
  async function openPhotoPicker() {
    await openFitChooser()
    await chooseFitMethod('Measure with a photo')
  }
  async function openQuickGuidance(size: string, perception: 'TIGHT' | 'ABOUT_RIGHT' | 'LOOSE') {
    await openFitChooser()
    await chooseFitMethod('I know my usual size')
    const sizeSelect = find(root, target => target.props.id === 'fit-usual-size')!
    ;(sizeSelect.props.onChange as (event: unknown) => void)({ target: { value: size } })
    const feeling = find(root, target => target.tag === 'input' && target.props.value === perception)!
    ;(feeling.props.onChange as () => void)()
    await settle()
    await click('Show my starting point')
  }
  async function chooseImage(type = 'image/png') {
    const upload = find(root, target => target.tag === 'input' && target.props.id === 'fit-photo-input')
    expect(upload).toBeDefined()
    ;(upload!.props.onChange as (event: Event) => void)({ target: { files: [new Blob(['image'], { type }) as File] } } as unknown as Event)
    await settle()
  }

  it.each([
    ['TIGHT', 'tight'],
    ['ABOUT_RIGHT', 'about right'],
    ['LOOSE', 'loose'],
  ] as const)('uses real Product evidence for %s quick guidance without adjusting the size', async (perception, feeling) => {
    cart.items = []
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product')
    await openQuickGuidance('40', perception)

    expect(text(root)).toContain('Start with EU 40')
    expect(text(root)).toContain(`Your usual EU 40 feels ${feeling}.`)
    expect(text(root)).toContain(t('True to size'))
    expect(text(root)).toContain(t('Regular'))
    expect(text(root)).toContain(t('This is a starting point, not an automatic size adjustment.'))
    expect(text(root)).not.toContain('EU 41')
    expect(find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)).toBeUndefined()
    expect(cart.items).toHaveLength(0)

    await click('Apply EU {size}'.replace('{size}', '40'))
    expect(find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)).toBeUndefined()
    const chooseChalk = find(root, target => target.tag === 'button' && text(target) === t('Choose {color}', { color: t('Chalk') }))!
    ;(chooseChalk.props.onClick as () => void)()
    await settle()
    expect(text(root)).toContain('Size 40 · Chalk')
    expect(cart.items).toHaveLength(0)
  })

  it.each(['en', 'vi-VN'] as const)('distinguishes the usual size, reference starting point, and applied size in %s', async language => {
    setLocale(language)
    cart.items = []
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product')
    await click('Chalk')
    await openQuickGuidance('40', 'ABOUT_RIGHT')

    expect(text(root)).toContain(t('Your usual size'))
    expect(text(root)).toContain(t('Reference starting point'))
    expect(text(root)).not.toContain(t('Selected size'))
    const apply = find(root, target => target.tag === 'button' && text(target) === t('Apply EU {size}', { size: '40' }))!
    ;(apply.props.onClick as () => void)()
    await settle()
    expect(text(root)).toContain(t('Selected size'))
    expect(text(root)).toContain('EU 40')
    expect(cart.items).toHaveLength(0)
  })

  it('keeps an unavailable usual size explicit and offers only real available colors', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product?variant=ink-40')
    await openQuickGuidance('40', 'ABOUT_RIGHT')

    expect(text(root)).toContain(t('This size is unavailable in the selected color.'))
    expect(text(root)).toContain(t('Available in another color:'))
    expect(text(root)).toContain(t('Chalk'))
    expect(text(root)).not.toContain(t('White'))
    expect(find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true && text(target).includes('40'))).toBeDefined()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Size unavailable'))?.props.disabled).toBe(true)
  })

  it('moves focus into the progressive flow and returns it to the entry control on close', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    await mount(productComponent, '/products/fit-product')
    const entry = find(root, target => target.tag === 'button' && String(target.props.class).includes('fit-entry'))!
    ;(entry.props.onClick as () => void)()
    await settle()
    expect(find(root, target => target.props.id === 'fit-panel-heading')!.focus).toHaveBeenCalled()

    await chooseFitMethod('I know my usual size')
    expect(find(root, target => target.props.id === 'fit-step-heading')!.focus).toHaveBeenCalled()
    await click('Close')
    expect(find(root, target => target.tag === 'button' && String(target.props.class).includes('fit-entry'))!.focus).toHaveBeenCalled()
  })

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
    expect(text(root)).toContain(t('Strong photo quality'))
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

  it('does not show a photo result after the customer closes the pending analysis', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    const request = deferred<FitAnalysis>()
    vi.spyOn(api, 'fitAnalysis').mockReturnValue(request.promise)
    await mount(productComponent, '/products/fit-product')
    await openPhotoPicker()
    await chooseImage()
    await click('Use this photo')
    await click('Close')

    request.resolve(fitSuccess)
    await settle()
    expect(text(root)).toContain(t('Find my size'))
    expect(text(root)).not.toContain(t('Your suggested size'))
    expect(find(root, target => target.props.id === 'fit-step-heading')).toBeUndefined()
  })

  it('labels MEDIUM only as a usable image estimate rather than fit probability', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    vi.spyOn(api, 'fitAnalysis').mockResolvedValue({ ...fitSuccess, analysisConfidence: 'MEDIUM' })
    await mount(productComponent, '/products/fit-product')
    await openPhotoPicker()
    await chooseImage()
    await click('Use this photo')

    expect(text(root)).toContain(t('Usable image estimate'))
    expect(text(root)).toContain(t('This describes image quality, not the probability that the shoe will fit.'))
    expect(text(root)).not.toContain('%')
  })

  it('does not select a Fit Assistant result until the customer accepts it', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    vi.spyOn(api, 'fitAnalysis').mockResolvedValue(fitSuccess)
    await mount(productComponent, '/products/fit-product')
    await openPhotoPicker()
    await chooseImage()
    await click('Use this photo')

    expect(find(root, target => String(target.props.class).includes('variant-option') && target.props['aria-pressed'] === true)).toBeUndefined()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Choose a color first'))?.props.disabled).toBe(true)

    const accept = find(root, target => target.tag === 'button' && text(target).startsWith('Select EU'))!
    ;(accept.props.onClick as () => void)()
    await settle()
    expect(find(root, target => target.tag === 'button' && text(target) === t('Choose a color first'))?.props.disabled).toBe(true)
    const chooseChalk = find(root, target => target.tag === 'button' && text(target) === t('Choose {color}', { color: t('Chalk') }))!
    ;(chooseChalk.props.onClick as () => void)()
    await settle()
    expect(text(root)).toContain('Size 40 · Chalk')
    expect(find(root, target => target.tag === 'button' && text(target) === t('Add to cart'))?.props.disabled).not.toBe(true)
  })

  it('rejects an invalid image locally', async () => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    vi.spyOn(api, 'fitAnalysis')
    await mount(productComponent, '/products/fit-product')
    await openPhotoPicker()
    await chooseImage('image/gif')
    expect(text(root)).toContain(t('Only PNG or JPEG images up to 5 MB are accepted.'))
    expect(api.fitAnalysis).not.toHaveBeenCalled()
  })

  it.each([
    'REFERENCE_NOT_FOUND', 'REFERENCE_CLIPPED', 'EXCESSIVE_PERSPECTIVE', 'IMAGE_TOO_BLURRY',
    'FOOT_NOT_FOUND', 'FOOT_PARTIAL', 'IMPLAUSIBLE_MEASUREMENT', 'ANALYSIS_INSUFFICIENT', 'FIT_PROFILE_OUT_OF_RANGE',
  ])('keeps RETAKE %s non-selectable with retry and quick-guidance recovery', async retakeReason => {
    vi.spyOn(api, 'product').mockResolvedValue(fitProduct)
    vi.spyOn(api, 'fitAnalysis').mockResolvedValue({ status: 'RETAKE', retakeReason, availableColors: [] })
    await mount(productComponent, '/products/fit-product')
    await openPhotoPicker()
    await chooseImage()
    await click('Use this photo')
    expect(text(root)).toContain(t('Try a clearer photo'))
    expect(text(root)).toContain(t('Retake photo'))
    expect(text(root)).toContain(t('Use my usual size instead'))
    expect(text(root)).not.toContain('Select EU')
    await click('Use my usual size instead')
    expect(text(root)).toContain(t('What EU size do you usually wear?'))
  })

  it('does not offer a generic fitting flow for a product without a complete profile', async () => {
    vi.spyOn(api, 'product').mockResolvedValue({ ...fitProduct, fitSupported: false })
    await mount(productComponent, '/products/fit-product')
    expect(text(root)).toContain(t('This shoe model does not have a supported fit profile yet.'))
    expect(find(root, target => target.tag === 'button' && text(target) === t('Find my size'))).toBeUndefined()
  })
})
