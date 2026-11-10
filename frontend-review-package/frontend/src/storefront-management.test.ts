import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { afterEach, describe, expect, it, vi } from 'vitest'
import appSource from './App.vue?raw'
import mainSource from './main.ts?raw'
import viewSource from './views/StorefrontManagementView.vue?raw'
import { api, type ProductSummary, type PublicOffer } from './api'
import StorefrontSectionPreview from './components/StorefrontSectionPreview.vue'
import { setLocale } from './i18n'
import {
  addProduct, addSection, canManageStorefront, canPublish, createSection, moveProduct, moveSection,
  removeProduct, removeSection, toSectionInput, toggleSection, validateDraft, type StorefrontRevision,
} from './merchandising'

const product: ProductSummary = {
  id: 'product-1', name: 'Court Classic', category: 'Lifestyle', collection: null, featured: true,
  newArrival: false, campaignEligible: true, merchandisingRank: 1, heroImage: '/products/court-classic.png',
  primaryImage: '/products/court-classic.png', variantCount: 2, availableVariantCount: 1, fromAmount: 1290000,
}
const promotion: PublicOffer = {
  familyId: 'promotion-1', acquisitionMode: 'CLAIMABLE', name: 'Member Week', customerSummary: 'Save on your order',
  customerTerms: 'Current published terms', effectType: 'ORDER_PERCENT', percentageValue: 10,
  validFrom: '2026-09-01T00:00:00Z', claimable: true,
}
const revision: StorefrontRevision = {
  revisionId: 'draft-1', revisionNumber: 3, status: 'DRAFT', entityVersion: 4, publishedAt: null, sections: [],
}

function completed(type: 'HERO_BANNER' | 'PRODUCT_COLLECTION' | 'PROMOTION_SPOTLIGHT', id: string) {
  const section = createSection(type, id)
  section.titleVi = 'Tiêu đề'; section.titleEn = 'Title'; section.ctaLabelVi = 'Xem'; section.ctaLabelEn = 'View'
  if (type === 'PRODUCT_COLLECTION') { section.productIds = [product.id]; section.ctaSearchTerm = 'shoes' }
  if (type === 'PROMOTION_SPOTLIGHT') { section.promotionFamilyId = promotion.familyId; section.ctaTargetPublicId = promotion.familyId }
  return section
}

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('storefront route permission', () => {
  it('allows the explicit capability and does not infer access from an OPERATIONS role', () => {
    expect(canManageStorefront({ permissions: ['STOREFRONT_MANAGE'] })).toBe(true)
    expect(canManageStorefront({ permissions: ['PROMOTION_MANAGE'] })).toBe(false)
    expect(mainSource).toContain("path: '/operations/storefront'")
    expect(mainSource).toContain("permission: 'STOREFRONT_MANAGE'")
    expect(appSource).toContain('v-if="canManageStorefront(session.account)"')
    expect(appSource).not.toMatch(/roles\.includes\([^)]*OPERATIONS/)
  })
})

describe('local draft transformations', () => {
  it('adds, removes, reorders, and toggles bounded sections', () => {
    let sections = addSection([], 'HERO_BANNER', 'hero')
    sections = addSection(sections, 'PRODUCT_COLLECTION', 'collection')
    expect(sections.map(section => section.clientId)).toEqual(['hero', 'collection'])
    sections = moveSection(sections, 'collection', -1)
    expect(sections.map(section => section.clientId)).toEqual(['collection', 'hero'])
    sections = toggleSection(sections, 'hero')
    expect(sections[1].enabled).toBe(false)
    expect(removeSection(sections, 'collection').map(section => section.clientId)).toEqual(['hero'])
  })

  it('keeps curated product membership unique, ordered, and limited to eight', () => {
    let section = createSection('PRODUCT_COLLECTION', 'collection')
    for (let index = 1; index <= 9; index++) section = addProduct(section, `product-${index}`)
    section = addProduct(section, 'product-1')
    expect(section.productIds).toHaveLength(8)
    section = moveProduct(section, 'product-3', -1)
    expect(section.productIds.slice(0, 3)).toEqual(['product-1', 'product-3', 'product-2'])
    expect(removeProduct(section, 'product-3').productIds).not.toContain('product-3')
  })

  it('produces exact discriminated payloads and rejects non-controlled media', () => {
    const hero = completed('HERO_BANNER', 'hero')
    const collection = completed('PRODUCT_COLLECTION', 'collection')
    const spotlight = completed('PROMOTION_SPOTLIGHT', 'spotlight')
    expect(toSectionInput(hero)).toMatchObject({ type: 'HERO_BANNER', mediaKey: 'COURT_CLASSIC', productIds: [], promotionFamilyId: null })
    expect(toSectionInput(collection)).toMatchObject({ type: 'PRODUCT_COLLECTION', mediaKey: null, productIds: ['product-1'], promotionFamilyId: null })
    expect(toSectionInput(spotlight)).toMatchObject({ type: 'PROMOTION_SPOTLIGHT', ctaKind: 'PROMOTION_DETAIL', ctaTargetPublicId: 'promotion-1', promotionFamilyId: 'promotion-1', mediaKey: null, productIds: [] })
    const unsafe = { ...hero, mediaKey: 'https://example.invalid/image.png' as never }
    expect(toSectionInput(unsafe).mediaKey).toBeNull()
    expect(validateDraft([unsafe])).toMatchObject({ key: expect.stringContaining('controlled media') })
  })

  it('blocks invalid saves and publication with unsaved local changes', () => {
    expect(validateDraft([])).toEqual({ key: 'Add at least one section before saving.' })
    expect(validateDraft([completed('PRODUCT_COLLECTION', 'collection')])).toBeNull()
    expect(canPublish(revision, true)).toBe(false)
    expect(canPublish(revision, false)).toBe(true)
    expect(canPublish(null, false)).toBe(false)
    expect(viewSource).toContain("if (dirty.value) { error.value = t('Save your local changes before publishing.'); return }")
  })
})

describe('S1.1 storefront management API bindings', () => {
  it('loads the management state', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ draft: null, published: null })))
    vi.stubGlobal('fetch', fetchMock)
    await expect(api.storefrontManagement()).resolves.toEqual({ draft: null, published: null })
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/operations/storefront/homepage')
  })

  it('saves the full draft with CSRF and expected version evidence', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify(revision)))
    vi.stubGlobal('fetch', fetchMock)
    const command = { expectedDraftRevisionId: 'draft-1', expectedEntityVersion: 3, sections: [toSectionInput(completed('HERO_BANNER', 'hero'))] }
    await expect(api.saveStorefrontDraft(command)).resolves.toEqual(revision)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/storefront/homepage/draft')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'PUT', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf' }) })
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(command)
  })

  it('publishes with all three expected identifiers', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...revision, status: 'PUBLISHED' })))
    vi.stubGlobal('fetch', fetchMock)
    const command = { expectedDraftRevisionId: 'draft-1', expectedEntityVersion: 4, expectedPublishedRevisionId: 'published-1' }
    await api.publishStorefrontDraft(command)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/storefront/homepage/publish')
    expect(fetchMock.mock.calls[1][1].method).toBe('POST')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(command)
  })

  it('surfaces a 409 exactly once so the editor can preserve local changes', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'STOREFRONT_DRAFT_CONFLICT', detail: 'stale' }), { status: 409 }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(api.saveStorefrontDraft({ expectedDraftRevisionId: 'draft-1', expectedEntityVersion: 2, sections: [] }))
      .rejects.toMatchObject({ status: 409, code: 'STOREFRONT_DRAFT_CONFLICT' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(viewSource).toContain('Your local edits are still here.')
    expect(viewSource).toContain('Reload latest and discard local edits')
  })
})

describe('reusable storefront section preview', () => {
  it.each([
    ['HERO_BANNER', 'hero', 'Title'],
    ['PRODUCT_COLLECTION', 'collection', 'Court Classic'],
    ['PROMOTION_SPOTLIGHT', 'spotlight', 'Member Week'],
  ] as const)('renders one focused %s preview', async (type, id, evidence) => {
    setLocale('en')
    const html = await renderToString(createSSRApp(StorefrontSectionPreview, {
      section: completed(type, id), products: [product], promotions: [promotion],
    }))
    expect(html).toContain(evidence)
    expect(html).toContain(`storefront-section--${type.toLowerCase()}`)
    expect(html).toContain('storefront-section--preview')
  })
})
