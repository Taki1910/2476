import { afterEach, describe, expect, it, vi } from 'vitest'
import { compile, createRenderer, createSSRApp, nextTick, ssrContextKey, type App, type ComponentOptions, type SetupContext } from 'vue'
import { parse } from 'vue/compiler-sfc'
import { renderToString } from 'vue/server-renderer'
import { createMemoryHistory, createRouter } from 'vue-router'
import { api, type ProductDetail } from './api'
import { clearComparison, comparisonIds, hasCanonicalDifference, isCurrentComparisonRequest, selectMobilePair, toggleComparison } from './comparison'
import ProductComparison from './components/ProductComparison.vue'
import comparisonSource from './components/ProductComparison.vue?raw'
import { setLocale } from './i18n'

const detail = (id: string, name = `Shoe ${id}`): ProductDetail => ({
  id, name, category: 'RUNNING', collection: null, featured: false, newArrival: false,
  campaignEligible: false, merchandisingRank: 0, heroImage: null, primaryImage: null,
  variants: [], options: [{ color: 'BLACK', sizes: [{ size: '40', variantId: null, sku: null, availability: 'AVAILABLE', amount: 1200000, availableAlternativeColors: [] }] }],
  pricing: { state: 'RANGE', minimumAmount: 1200000, maximumAmount: 1400000, currency: 'VND' },
  media: [{ url: '/products/shoe.png', position: 0, alt: 'Shoe on foot' }], fitGuidance: null,
  presentation: null, evidence: { intendedUse: 'ROAD_RUNNING', primarySurface: 'ROAD_TRACK', upperConstruction: null, cutProfile: null, primarySoleProfile: null },
  fitSummary: { fitTendency: 'TRUE_TO_SIZE', widthProfile: 'REGULAR' },
})

async function render() {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/products/:id', component: { template: '<div />' } }] })
  return renderToString(createSSRApp(ProductComparison).use(router))
}

type Node = { tag: string; text: string; props: Record<string, unknown>; children: Node[]; parent: Node | null; focus: () => void; addEventListener: () => void; options: Node[]; style: Record<string, string> }
const node = (tag: string, text = ''): Node => ({ tag, text, props: {}, children: [], parent: null, focus: vi.fn(), addEventListener: vi.fn(), options: [], style: {} })
const renderer = createRenderer<Node, Node>({
  createElement: tag => node(tag), createText: text => node('#text', text), createComment: text => node('#comment', text),
  setText: (target, value) => { target.text = value },
  setElementText: (target, value) => { target.text = value; target.children = [] },
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
const component = ProductComparison as ComponentOptions
const mountedComponent = { ...component, render: compile(parse(comparisonSource).descriptor.template!.content),
  setup(props: {}, context: SetupContext) { return { ...component.setup!(props, context) as Record<string, unknown> } } }
mountedComponent.components = { RouterLink: { props: ['to'], template: '<a><slot /></a>' } }
let app: App<Node> | undefined
let root: Node
function content(target: Node): string { return target.tag === '#comment' ? '' : target.text + target.children.map(content).join('') }
function find(target: Node, predicate: (item: Node) => boolean): Node | undefined {
  if (predicate(target)) return target
  for (const child of target.children) { const result = find(child, predicate); if (result) return result }
}
async function settle() { for (let i = 0; i < 8; i++) { await Promise.resolve(); await nextTick() } }
async function mount() {
  root = node('root')
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/products/:id', component: { template: '<div />' } }] })
  app = renderer.createApp(mountedComponent).use(router).provide(ssrContextKey, {})
  app.mount(root)
  await settle()
}
async function click(label: string) {
  const button = find(root, item => item.tag === 'button' && content(item) === label)
  expect(button, label).toBeDefined()
  ;(button!.props.onClick as () => void)()
  await settle()
}
function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (reason: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

afterEach(() => { app?.unmount(); app = undefined; clearComparison(); vi.restoreAllMocks(); setLocale('en') })

describe('comparison selection', () => {
  it('allows three products, rejects a fourth, toggles and clears', () => {
    expect(toggleComparison('a')).toBe('added')
    expect(toggleComparison('b')).toBe('added')
    expect(toggleComparison('c')).toBe('added')
    expect(toggleComparison('d')).toBe('limit')
    expect(comparisonIds.value).toEqual(['a', 'b', 'c'])
    expect(toggleComparison('b')).toBe('removed')
    expect(comparisonIds.value).toEqual(['a', 'c'])
    clearComparison()
    expect(comparisonIds.value).toEqual([])
  })

  it('ignores responses after a request is replaced or its product is removed', () => {
    expect(isCurrentComparisonRequest('a', 2, 2, ['a', 'b'])).toBe(true)
    expect(isCurrentComparisonRequest('a', 1, 2, ['a', 'b'])).toBe(false)
    expect(isCurrentComparisonRequest('a', 2, 2, ['b'])).toBe(false)
  })

  it('filters differences using canonical values and keeps unknown data visible', () => {
    expect(hasCanonicalDifference([undefined, undefined])).toBe(false)
    expect(hasCanonicalDifference([null, undefined])).toBe(false)
    expect(hasCanonicalDifference([undefined, '"BLACK"'])).toBe(true)
    expect(hasCanonicalDifference([null, '"BLACK"'])).toBe(true)
    expect(hasCanonicalDifference(['"BLACK"', '"BLACK"'])).toBe(false)
    expect(hasCanonicalDifference([undefined, '"BLACK"'])).toBe(true)
  })

  it('swaps the other mobile selection to prevent duplicate products', () => {
    expect(selectMobilePair(['a', 'b', 'c'], ['a', 'b'], 0, 'b')).toEqual(['b', 'a'])
    expect(selectMobilePair(['a', 'b', 'c'], ['a', 'b'], 1, 'c')).toEqual(['a', 'c'])
  })
})

describe('comparison panel rendering', () => {
  it('explains that two products are needed', async () => {
    setLocale('en')
    expect(await render()).toContain('Choose at least two products to compare.')
  })

  it('loads selected products individually and renders comparison values and a mobile pair picker', async () => {
    setLocale('en')
    toggleComparison('a'); toggleComparison('b'); toggleComparison('c')
    vi.spyOn(api, 'product').mockImplementation(async id => detail(id))
    const html = await render()
    expect(api.product).toHaveBeenCalledTimes(3)
    expect(html).toContain('Shoe a')
    expect(html).toContain('Only show differences')
    expect(html).toContain('Choose two products')
    expect(html).toContain('Shoe on foot')
    expect(html).toContain('Road / track')
    expect(html).toContain('View product')
  })

  it('keeps a failed product in the comparison with a retry action', async () => {
    setLocale('en')
    toggleComparison('a'); toggleComparison('b')
    vi.spyOn(api, 'product').mockImplementation(async id => {
      if (id === 'b') throw new Error('offline')
      return detail(id)
    })
    const html = await render()
    expect(html).toContain('Could not load this product.')
    expect(html).toContain('Retry')
    expect(html).toContain('Shoe a')
  })

  it('translates panel labels into Vietnamese', async () => {
    setLocale('vi-VN')
    toggleComparison('a')
    const html = await render()
    expect(html).toContain('Chọn ít nhất hai sản phẩm để so sánh.')
  })
})

describe('comparison refresh', () => {
  it('refetches on reopen and hides old prices until fresh responses arrive', async () => {
    toggleComparison('a'); toggleComparison('b')
    const fresh = deferred<ProductDetail>()
    vi.spyOn(api, 'product').mockImplementation(async id => id === 'a' && vi.mocked(api.product).mock.calls.filter(([key]) => key === 'a').length > 1
      ? fresh.promise : id === 'b' ? { ...detail(id), pricing: { state: 'SINGLE', minimumAmount: 2400000, maximumAmount: 2400000, currency: 'VND' } } : detail(id))
    await mount(); await click('Open comparison (2)')
    expect(content(root)).toContain('1,200,000')
    await click('Close comparison'); await click('Open comparison (2)')
    expect(api.product).toHaveBeenCalledTimes(4)
    expect(content(root)).not.toContain('1,200,000')
    fresh.resolve({ ...detail('a'), pricing: { state: 'SINGLE', minimumAmount: 1800000, maximumAmount: 1800000, currency: 'VND' } })
    await settle()
    expect(content(root)).toContain('1,800,000')
  })

  it('keeps selected IDs and the other product when a refreshed product returns 404, then retries', async () => {
    toggleComparison('a'); toggleComparison('b')
    const failed = deferred<ProductDetail>()
    const retry = deferred<ProductDetail>()
    let aCalls = 0
    vi.spyOn(api, 'product').mockImplementation(id => id === 'b' ? Promise.resolve(detail('b')) : (++aCalls === 1 ? Promise.resolve(detail('a')) : aCalls === 2 ? failed.promise : retry.promise))
    await mount(); await click('Open comparison (2)'); await click('Close comparison'); await click('Open comparison (2)')
    failed.reject(new Error('404')); await settle()
    expect(comparisonIds.value).toEqual(['a', 'b'])
    expect(content(root)).toContain('Could not load this product.')
    expect(content(root)).toContain('Shoe b')
    expect(content(root)).not.toContain('Shoe a')
    expect(content(root)).toContain('Remove from comparison')
    await click('Retry')
    expect(content(root)).not.toContain('Shoe a')
    retry.resolve(detail('a', 'Fresh a')); await settle()
    expect(content(root)).toContain('Fresh a')
  })

  it('ignores late success and error after newer refreshes, including remove and re-add of the same ID', async () => {
    toggleComparison('a'); toggleComparison('b')
    const first = deferred<ProductDetail>()
    const second = deferred<ProductDetail>()
    const third = deferred<ProductDetail>()
    let aCalls = 0
    vi.spyOn(api, 'product').mockImplementation(id => id === 'b' ? Promise.resolve(detail('b')) : [first.promise, second.promise, third.promise][aCalls++])
    await mount(); await click('Open comparison (2)')
    toggleComparison('a')
    toggleComparison('a'); await settle()
    await click('Close comparison'); await click('Open comparison (2)')
    first.resolve(detail('a', 'Late a')); second.reject(new Error('late')); await settle()
    expect(content(root)).toContain('Loading…')
    expect(content(root)).not.toContain('Late a')
    expect(content(root)).not.toContain('Could not load this product.')
    third.resolve(detail('a', 'Current a')); await settle()
    expect(content(root)).toContain('Current a')
    expect(content(root)).not.toContain('Late a')
    expect(content(root)).not.toContain('Could not load this product.')
  })
})
