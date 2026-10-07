import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'
import appSource from './App.vue?raw'
import ProductPresentationSummary from './components/ProductPresentationSummary.vue'
import { setLocale } from './i18n'
import mainSource from './main.ts?raw'
import { canPublishProductPresentation, presentationSummary } from './product-presentation'
import viewSource from './views/ProductPresentationsView.vue?raw'

describe('Product presentation contracts', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('selects only the current authoritative locale and preserves null', () => {
    const presentation = { summary: { vi: 'Tóm tắt', en: 'Summary' } }
    expect(presentationSummary(presentation, 'vi-VN')).toBe('Tóm tắt')
    expect(presentationSummary(presentation, 'en')).toBe('Summary')
    expect(presentationSummary(null, 'vi-VN')).toBeNull()
  })

  it('renders the current locale through one null-safe shared component', async () => {
    const presentation = { summary: { vi: 'Bản VI', en: 'English summary' } }
    setLocale('en')
    expect(await renderToString(createSSRApp(ProductPresentationSummary, { presentation })))
      .toContain('English summary')
    setLocale('vi-VN')
    expect(await renderToString(createSSRApp(ProductPresentationSummary, { presentation, compact: true })))
      .toContain('product-presentation-summary compact')
    expect(await renderToString(createSSRApp(ProductPresentationSummary, { presentation: null })))
      .not.toContain('<p')
  })

  it('requires a complete saved draft before publication', () => {
    const draft = {
      id: 'draft-1', revisionNumber: 2, status: 'DRAFT' as const, entityVersion: 3,
      summaryVi: 'VI', summaryEn: 'EN', createdAt: '2026-09-20T00:00:00Z',
      publishedAt: null, retiredAt: null,
    }
    expect(canPublishProductPresentation(draft, false)).toBe(true)
    expect(canPublishProductPresentation({ ...draft, summaryEn: null }, false)).toBe(false)
    expect(canPublishProductPresentation(draft, true)).toBe(false)
    expect(canPublishProductPresentation(null, false)).toBe(false)
  })

  it('owns the route, permission, shared preview and bounded editor workflow', () => {
    expect(mainSource).toContain("path: '/operations/product-presentations'")
    expect(mainSource).toContain("permission: 'STOREFRONT_MANAGE'")
    expect(appSource).toContain('to="/operations/product-presentations"')
    expect(appSource).toContain("permissions.includes('STOREFRONT_MANAGE')")
    expect(viewSource).toContain('<ProductPresentationSummary')
    expect(viewSource).not.toContain('v-html')
    expect(viewSource).toContain('expectedDraftRevisionId: null')
    expect(viewSource).toContain('expectedEntityVersion: null')
    expect(viewSource).toContain("if (dirty.value) { error.value = t('Save your local changes before publishing.'); return }")
    expect(viewSource).toContain('expectedPublishedRevisionId: management.value?.published?.id ?? null')
    expect(viewSource).toContain('reason instanceof ApiError && reason.status === 409')
    expect(viewSource).toContain('Your local edits are still here.')
  })

  it('loads and saves the exact product draft with CSRF', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ productId: 'product/1', draft: null, published: null })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'draft-1', entityVersion: 3 })))
    vi.stubGlobal('fetch', fetchMock)

    await api.productPresentationState('product/1')
    await api.saveProductPresentationDraft('product/1', {
      expectedDraftRevisionId: 'draft-1', expectedEntityVersion: 2,
      summaryVi: 'VI', summaryEn: 'EN',
    })

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/operations/product-presentations/product%2F1')
    expect(fetchMock.mock.calls[2][0]).toBe('/api/v1/operations/product-presentations/product%2F1/draft')
    expect(fetchMock.mock.calls[2][1]).toMatchObject({
      method: 'PUT', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf' },
    })
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({
      expectedDraftRevisionId: 'draft-1', expectedEntityVersion: 2,
      summaryVi: 'VI', summaryEn: 'EN',
    })
  })

  it('publishes all expected identities and surfaces a conflict without retry', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'PRESENTATION_CONFLICT' }), {
        status: 409, headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)
    const command = {
      expectedDraftRevisionId: 'draft-1', expectedEntityVersion: 2,
      expectedPublishedRevisionId: 'published-1',
    }

    await expect(api.publishProductPresentation('product-1', command))
      .rejects.toMatchObject({ status: 409, code: 'PRESENTATION_CONFLICT' })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/product-presentations/product-1/publish')
    expect(fetchMock.mock.calls[1][1].method).toBe('POST')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual(command)
  })
})
