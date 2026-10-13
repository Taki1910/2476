import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import catalogSource from './views/CatalogView.vue?raw'
import { api, type ProductSummary, type PublicOffer } from './api'
import StorefrontHomepage from './components/StorefrontHomepage.vue'
import { setLocale } from './i18n'
import {
  storefrontCtaLocation, type StorefrontCustomerCta, type StorefrontCustomerSection, type StorefrontHomepage as Homepage,
} from './merchandising'

const product: ProductSummary = {
  id: 'product-1', name: 'Court Classic', category: 'Lifestyle', collection: null, featured: true,
  newArrival: false, campaignEligible: true, merchandisingRank: 1, heroImage: '/products/court-classic.png',
  primaryImage: '/products/court-classic.png', variantCount: 2, availableVariantCount: 1, fromAmount: 1290000,
}
const promotion: PublicOffer = {
  familyId: 'promotion-1', acquisitionMode: 'CLAIMABLE', name: 'Member Week', customerSummary: 'Save on your order',
  customerTerms: 'Public terms apply.', effectType: 'ORDER_PERCENT', percentageValue: 10,
  validFrom: '2026-09-01T00:00:00Z', validTo: '2026-09-30T00:00:00Z', claimable: true,
}
type CustomerSection = StorefrontCustomerSection<ProductSummary, PublicOffer>

function cta(kind: StorefrontCustomerCta['kind'], targetPublicId: string | null = null, searchTerm: string | null = null): StorefrontCustomerCta {
  return { label: { vi: 'Xem ngay', en: 'View now' }, kind, targetPublicId, searchTerm }
}

function section(type: CustomerSection['type'], sectionId: string, position: number): CustomerSection {
  return {
    sectionId, type, position,
    copy: { titleVi: `${sectionId} VI`, titleEn: `${sectionId} EN`, supportingTextVi: 'Nội dung quản lý', supportingTextEn: 'Managed copy' },
    cta: type === 'HERO_BANNER' ? cta('PRODUCT_DETAIL', product.id) : type === 'PRODUCT_COLLECTION' ? cta('CATALOG_SEARCH', null, 'court') : cta('PROMOTION_DETAIL', promotion.familyId),
    media: type === 'HERO_BANNER' ? { key: 'COURT_CLASSIC', url: '/products/court-classic.png' } : null,
    products: type === 'PRODUCT_COLLECTION' ? [product] : [],
    promotion: type === 'PROMOTION_SPOTLIGHT' ? promotion : null,
  }
}

function homepage(sections: CustomerSection[]): Homepage<ProductSummary, PublicOffer> {
  return { publishedRevisionId: 'published-1', publishedAt: '2026-09-11T00:00:00Z', sections }
}

async function render(props: { homepage: Homepage<ProductSummary, PublicOffer> | null; loading: boolean; failed: boolean }) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/products/:id', component: { template: '<div />' } },
    { path: '/promotions', component: { template: '<div />' } },
    { path: '/promotions/:familyId', component: { template: '<div />' } },
  ] })
  return renderToString(createSSRApp(StorefrontHomepage, props).use(router))
}

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); setLocale('en') })

describe('published customer homepage', () => {
  it('loads the S1.1 public read model', async () => {
    const response = homepage([])
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(response)))
    vi.stubGlobal('fetch', fetchMock)
    await expect(api.storefrontHomepage()).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/storefront/homepage', expect.objectContaining({ credentials: 'include' }))
  })

  it('renders all section types in the exact server order', async () => {
    setLocale('en')
    const html = await render({ homepage: homepage([
      section('PROMOTION_SPOTLIGHT', 'Promotion', 7),
      section('HERO_BANNER', 'Hero', 1),
      section('PRODUCT_COLLECTION', 'Collection', 3),
    ]), loading: false, failed: false })
    expect(html).toContain('storefront-section--hero_banner')
    expect(html).toContain('storefront-section--product_collection')
    expect(html).toContain('storefront-section--promotion_spotlight')
    expect(html.indexOf('Promotion EN')).toBeLessThan(html.indexOf('Hero EN'))
    expect(html.indexOf('Hero EN')).toBeLessThan(html.indexOf('Collection EN'))
  })

  it('renders no fake merchandising for an empty publication and a restrained failure notice on error', async () => {
    expect(await render({ homepage: homepage([]), loading: false, failed: false })).not.toContain('customer-merchandising')
    const failed = await render({ homepage: null, loading: false, failed: true })
    expect(failed).toContain('Storefront highlights are temporarily unavailable')
    expect(catalogSource).toContain('<section v-if="!merchandisingLoading && !hasPublished" class="catalog-hero">')
    expect(catalogSource).toContain('<section class="catalog-section"')
    expect(catalogSource).toContain("catch { homepage.value = null; merchandisingFailed.value = true }")
  })

  it('switches managed content between EN and VI without another request', async () => {
    const model = homepage([section('HERO_BANNER', 'Managed headline', 0)])
    setLocale('en')
    expect(await render({ homepage: model, loading: false, failed: false })).toContain('Managed headline EN')
    setLocale('vi-VN')
    const vi = await render({ homepage: model, loading: false, failed: false })
    expect(vi).toContain('Managed headline VI')
    expect(vi).not.toContain('Managed headline EN')
  })
})

describe('storefront CTA routing and catalog regression', () => {
  it('maps only the four approved CTA categories to existing flows', () => {
    expect(storefrontCtaLocation(cta('PRODUCT_DETAIL', 'product/1'))).toBe('/products/product%2F1')
    expect(storefrontCtaLocation(cta('PROMOTION_DETAIL', 'offer/1'))).toBe('/promotions/offer%2F1')
    expect(storefrontCtaLocation(cta('CATALOG_SEARCH', null, 'court classic'))).toEqual({ path: '/', query: { q: 'court classic' }, hash: '#product-search' })
    expect(storefrontCtaLocation(cta('OFFERS'))).toBe('/promotions')
  })

  it('keeps the existing search, clear, route synchronization, and full catalog while suppressing duplicate lead hierarchy', () => {
    expect(catalogSource).toContain('id="product-search"')
    expect(catalogSource).toContain('async function clearSearch()')
    expect(catalogSource).toContain('watch(() => route.query.q')
    expect(catalogSource).toContain("api.products(value.trim())")
    expect(catalogSource).toContain("featured: index === 0 && !query && !hasPublished")
  })
})
