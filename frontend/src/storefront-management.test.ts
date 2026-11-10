import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { afterEach, describe, expect, it, vi } from 'vitest'
import appSource from './App.vue?raw'
import mainSource from './main.ts?raw'
import viewSource from './views/StorefrontManagementView.vue?raw'
import mediaReadme from '../public/products/README.md?raw'
import { api, type ProductSummary, type PublicOffer } from './api'
import StorefrontSectionPreview from './components/StorefrontSectionPreview.vue'
import { setLocale, t } from './i18n'
import {
  addProduct, addSection, applyAssistantCopy, applyCandidateToDraft, canManageStorefront, canPublish, candidateQueryForIntent, createSection, moveProduct, moveSection,
  removeProduct, removeSection, toSectionInput, toggleSection, validateDraft, type StorefrontRevision,
} from './merchandising'

const product: ProductSummary = {
  id: 'product-1', name: 'Court Classic', category: 'Lifestyle', collection: null, featured: true,
  newArrival: false, campaignEligible: true, merchandisingRank: 1, heroImage: '/products/court-classic.png',
  primaryImage: '/products/court-classic.png', variantCount: 2, availableVariantCount: 1, fromAmount: 1290000,
  presentation: null, evidence: null, fitSummary: null,
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

  it('locks every editor mutation while a save is in flight', () => {
    expect(viewSource).toContain('aria-busy="!!operation"')
    expect(viewSource).toContain('<select v-model="newType" :disabled="!!operation">')
    expect(viewSource).toContain('<input v-model="activeSection.titleVi" :disabled="!!operation"')
    expect(viewSource).toContain('<textarea v-model="activeSection.supportingTextVi" :disabled="!!operation"')
    expect(viewSource).toContain(':disabled="!!operation || !canAdd"')
    expect(viewSource).toContain('if (operation.value || !canAdd.value) return')
    expect(viewSource).toMatch(/if \(operation\.value\) return\r?\n  sections\.value = removeSection/)
    expect(viewSource).toMatch(/if \(operation\.value\) return\r?\n  const validation = validateDraft/)
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

  it('encodes the protected candidate query and paging filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ candidates: [], page: 2, size: 8, totalItems: 0, totalPages: 0 })))
    vi.stubGlobal('fetch', fetchMock)

    await api.storefrontCandidates({ query: 'Court & Run', type: 'BEST_SELLER', page: 2, size: 8 })

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/operations/storefront/candidates?query=Court+%26+Run&type=BEST_SELLER&page=2&size=8')
  })

  it('binds the protected assistant availability and CSRF-backed proposal endpoints', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ enabled: true, available: true, status: 'AVAILABLE' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ primary: 'Move naturally', alternatives: [], notes: '' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ intent: 'SEARCH', query: 'trail', explanation: 'Search products' })))
    vi.stubGlobal('fetch', fetchMock)

    await api.merchandisingAssistantAvailability()
    await api.rewriteMerchandisingCopy({
      locale: 'EN', field: 'TITLE', currentText: 'Old', instruction: 'Make it natural', sectionPurpose: 'Hero',
      productIds: ['product-1'], promotionFamilyId: null,
    })
    await api.interpretMerchandisingIntent('show trail shoes')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/operations/storefront/assistant/availability')
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/operations/storefront/assistant/copy')
    expect(fetchMock.mock.calls[2][1]).toMatchObject({ method: 'POST', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf' }) })
    expect(JSON.parse(fetchMock.mock.calls[2][1].body).locale).toBe('EN')
    expect(fetchMock.mock.calls[4][0]).toBe('/api/v1/operations/storefront/assistant/intent')
    expect(JSON.parse(fetchMock.mock.calls[4][1].body)).toEqual({ request: 'show trail shoes' })
  })

  it('clones the current publication with CSRF and the published revision fence', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify(revision)))
    vi.stubGlobal('fetch', fetchMock)
    const command = { expectedPublishedRevisionId: 'published-1' }

    await expect(api.cloneStorefrontPublished(command)).resolves.toEqual(revision)

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/storefront/homepage/draft-from-published')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf' }) })
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

describe('candidate Apply to draft', () => {
  it('changes only the selected local product collection and can be removed manually', () => {
    const hero = completed('HERO_BANNER', 'hero')
    const first = completed('PRODUCT_COLLECTION', 'first')
    const second = completed('PRODUCT_COLLECTION', 'second')
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    const result = applyCandidateToDraft([hero, first, second], 'second', 'product-2')

    expect(result.outcome).toBe('APPLIED')
    expect(result.sections[0]).toBe(hero)
    expect(result.sections[1]).toBe(first)
    expect(result.sections[2].productIds).toEqual(['product-1', 'product-2'])
    expect(removeProduct(result.sections[2], 'product-2').productIds).toEqual(['product-1'])
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('leaves duplicate, full, and missing collections unchanged', () => {
    const collection = completed('PRODUCT_COLLECTION', 'collection')
    expect(applyCandidateToDraft([collection], 'collection', 'product-1')).toEqual({ sections: [collection], outcome: 'ALREADY_PRESENT' })
    const full = { ...collection, productIds: Array.from({ length: 8 }, (_, index) => `product-${index}`) }
    expect(applyCandidateToDraft([full], 'collection', 'product-9')).toEqual({ sections: [full], outcome: 'COLLECTION_FULL' })
    expect(applyCandidateToDraft([collection], 'missing', 'product-2')).toEqual({ sections: [collection], outcome: 'COLLECTION_NOT_FOUND' })
  })
})

describe('P5A operational merchandising UX', () => {
  it('keeps discovery protected, searchable, filterable, and evidence-led', () => {
    expect(viewSource).toContain('api.storefrontCandidates')
    expect(viewSource).toContain("'BEST_SELLER'")
    expect(viewSource).toContain("'PROMOTED'")
    expect(viewSource).toContain("'NEEDS_REVIEW'")
    expect(viewSource).toContain('aria-pressed="candidateFilter === filter.value"')
    expect(viewSource).toContain('candidate.sales.units30d')
    expect(viewSource).toContain('candidate.sales.grossSalesValue30d')
    expect(viewSource).toContain('candidate.promotions')
    expect(viewSource).toContain('promotion.state')
    expect(viewSource).toContain('candidate.warnings')
    expect(viewSource).toContain('candidate.publishedPlacements')
    expect(viewSource).toContain('candidate.draftPlacements')
  })

  it('applies locally, supports ignore and manual removal, and keeps publish separate', () => {
    expect(viewSource).toContain('applyCandidateToDraft')
    expect(viewSource).toContain('applyRecommendation(candidate)')
    expect(viewSource).toContain('ignoreRecommendation(candidate.productId)')
    expect(viewSource).toContain('candidateCollectionId')
    expect(viewSource).toContain('removeCollectionProduct')
    expect(viewSource).toContain('api.publishStorefrontDraft')
    expect(viewSource).not.toContain('api.applyRecommendation')
  })

  it('offers fenced clone-from-live and named, image-backed selectors without exposing UUID work', () => {
    expect(viewSource).toContain('api.cloneStorefrontPublished')
    expect(viewSource).toContain("reason.code === 'STOREFRONT_DRAFT_CONFLICT'")
    expect(viewSource).toContain("product.primaryImage || product.heroImage || '/products/placeholder.svg'")
    expect(viewSource).toContain('product.category || product.collection')
    expect(viewSource).toContain('offer.name')
    expect(viewSource).not.toContain('{{ product.id }}')
    expect(viewSource).not.toContain('{{ offer.familyId }}')
  })

  it('ships natural VI/EN labels, announced feedback, and the verified media count', () => {
    setLocale('vi-VN')
    expect(t('Best sellers')).toBe('Bán chạy')
    expect(t('Promoted')).toBe('Đang có ưu đãi')
    expect(t('Needs review')).toBe('Cần kiểm tra')
    expect(t('Add to draft')).toBe('Thêm vào bản nháp')
    expect(t('Scheduled')).toBe('Đã lên lịch')
    expect(viewSource).toContain('aria-live="polite"')
    expect(viewSource).toContain('aria-label="')
    expect(mediaReadme).toContain('These thirteen PNG files')
  })
})

describe('P5B manager AI assistance', () => {
  it('applies accepted copy to exactly one language and field', () => {
    const hero = completed('HERO_BANNER', 'hero')

    const changed = applyAssistantCopy([hero], 'hero', 'VI', 'TITLE', 'Bước nhẹ mỗi ngày')

    expect(changed[0]).toMatchObject({ titleVi: 'Bước nhẹ mỗi ngày', titleEn: 'Title', ctaLabelVi: 'Xem' })
    expect(changed[0]).not.toBe(hero)
    expect(applyAssistantCopy([hero], 'missing', 'EN', 'CTA_LABEL', 'Shop now')).toEqual([hero])
  })

  it('maps structured intents into the existing deterministic candidate query', () => {
    expect(candidateQueryForIntent({ intent: 'BEST_SELLERS', query: '', explanation: '' })).toEqual({ type: 'BEST_SELLER', query: '' })
    expect(candidateQueryForIntent({ intent: 'PROMOTED', query: '', explanation: '' })).toEqual({ type: 'PROMOTED', query: '' })
    expect(candidateQueryForIntent({ intent: 'NEEDS_REVIEW', query: '', explanation: '' })).toEqual({ type: 'NEEDS_REVIEW', query: '' })
    expect(candidateQueryForIntent({ intent: 'SEARCH', query: 'giày chạy bộ', explanation: '' })).toEqual({ type: 'ALL', query: 'giày chạy bộ' })
    expect(candidateQueryForIntent({ intent: 'UNSUPPORTED', query: '', explanation: 'Không hỗ trợ' })).toBeNull()
  })

  it('keeps availability, loading, preview, retry, cancel, and explicit apply visible in the manager workflow', () => {
    expect(viewSource).toContain('api.merchandisingAssistantAvailability')
    expect(viewSource).toContain('api.interpretMerchandisingIntent')
    expect(viewSource).toContain('api.rewriteMerchandisingCopy')
    expect(viewSource).toContain('applyAssistantCopy')
    expect(viewSource).toContain("copyLoading ? 'Drafting…' : 'Suggest copy'")
    expect(viewSource).toContain('Use this suggestion')
    expect(viewSource).toContain("t('Try again')")
    expect(viewSource).toContain('@click="cancelCopySuggestion"')
    expect(viewSource).toContain('aria-live="polite"')
    expect(viewSource).not.toContain('api.publishAssistant')
  })

  it('leaves manual discovery and the local draft usable when AI is disabled or fails', () => {
    expect(viewSource).toContain(':disabled="!assistantAvailability.available || assistantIntentLoading"')
    expect(viewSource).toContain('AI assistance is unavailable. Manual search and filters still work.')
    expect(viewSource).toContain('Your draft has not changed.')
    expect(viewSource).toContain('candidateFilters')
    expect(viewSource).toContain('api.storefrontCandidates')
    expect(viewSource).toContain('api.saveStorefrontDraft')
    expect(viewSource).toContain('api.publishStorefrontDraft')
  })

  it('uses native keyboard submission and mobile stacking rather than a chatbot shell', () => {
    expect(viewSource).toContain('<form class="storefront-ai-intent" @submit.prevent="interpretIntent">')
    expect(viewSource).toContain('for="storefront-manager-intent"')
    expect(viewSource).not.toMatch(/chatbot|conversation history|message thread/i)
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
