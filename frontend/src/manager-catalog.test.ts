import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'
import mainSource from './main.ts?raw'
import listSource from './views/ProductsView.vue?raw'
import detailSource from './views/ProductManagementDetailView.vue?raw'
import presentationSource from './views/ProductPresentationsView.vue?raw'

describe('P2A manager catalog contracts', () => {
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

  it('uses dedicated manager reads rather than the public storefront catalog', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify([])))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'product-1', variants: [] })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ productId: 'product-1', balances: [] })))
    vi.stubGlobal('fetch', fetchMock)

    await api.managerCatalog('draft sku')
    await api.managerProduct('product-1')
    await api.managerInventory('product-1')

    expect(fetchMock.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/operations/catalog/products?q=draft%20sku',
      '/api/v1/operations/catalog/products/product-1',
      '/api/v1/operations/inventory/products/product-1',
    ])
  })

  it('sends the absolute on-hand target, expected balance version, reason, and idempotency key', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ created: true })))
    vi.stubGlobal('fetch', fetchMock)

    await api.adjustManagerInventory('variant/1', 'location/1', 12, 'Cycle count', 4, 'adjust-key')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/inventory/variants/variant%2F1/locations/location%2F1')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'PUT', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'adjust-key', 'X-CSRF-TOKEN': 'csrf' },
    })
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      onHand: 12, reason: 'Cycle count', expectedBalanceVersion: 4,
    })
  })

  it('sends a positive receipt quantity separately from inventory adjustment', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ afterOnHand: 15, reserved: 2, available: 13, created: true }), { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.receiveManagerInventory('variant/1', 'location/1', 3, 'PO-123', 'receipt-key')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/inventory/variants/variant%2F1/locations/location%2F1/receipts')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'receipt-key', 'X-CSRF-TOKEN': 'csrf' },
    })
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ quantity: 3, note: 'PO-123' })
  })

  it('updates a Draft barcode with optimistic concurrency and catalog authorization', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.updateManagerBarcode('variant/1', 3, ' 000123 ')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/catalog/variants/variant%2F1/barcode')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'PUT', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf' },
    })
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      expectedEntityVersion: 3, barcode: ' 000123 ',
    })
  })

  it('uses explicit optimistic commands for product and variant lifecycle changes', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'product-1', entityVersion: 0 })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'product-1', entityVersion: 1 })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'variant-1' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.createManagerProduct({ name: 'Runner', category: null, collection: null, heroImage: null, primaryImage: null })
    await api.updateManagerProduct('product-1', { expectedEntityVersion: 0, name: 'Runner 2', category: null, collection: null, heroImage: null, primaryImage: null })
    await api.createManagerVariant('product-1', { sku: 'RUN-41-BLK', size: '041.00', color: ' Black ' })
    await api.publishVariant('variant-1', 2)
    await api.retireVariant('variant-1', 3)
    await api.restoreVariant('variant-1', 4)

    const writes = fetchMock.mock.calls.filter(call => String(call[0]).includes('/catalog/'))
    expect(writes.map(call => call[0])).toEqual([
      '/api/v1/catalog/products',
      '/api/v1/catalog/products/product-1',
      '/api/v1/catalog/products/product-1/variants',
      '/api/v1/catalog/variants/variant-1/publish',
      '/api/v1/catalog/variants/variant-1/retire',
      '/api/v1/catalog/variants/variant-1/restore',
    ])
    expect(JSON.parse(fetchMock.mock.calls[7][1].body)).toEqual({ expectedEntityVersion: 2 })
    expect(JSON.parse(fetchMock.mock.calls[9][1].body)).toEqual({ expectedEntityVersion: 3 })
    expect(JSON.parse(fetchMock.mock.calls[11][1].body)).toEqual({ expectedEntityVersion: 4 })
  })

  it('keeps permission gates and inventory semantics explicit in the manager UI', () => {
    expect(mainSource).toContain("path: '/operations/products/:id'")
    expect(mainSource).toContain("permission: 'CATALOG_MANAGE'")
    expect(listSource).toContain('api.managerCatalog')
    expect(listSource).not.toContain('api.products(')
    expect(detailSource).toContain("hasPermission('PRICE_MANAGE')")
    expect(detailSource).toContain("hasPermission('INVENTORY_VIEW')")
    expect(detailSource).toContain("hasPermission('INVENTORY_ADJUST')")
    expect(detailSource).toContain("hasPermission('STOREFRONT_MANAGE')")
    expect(listSource).toContain('api.createManagerProduct')
    expect(detailSource).toContain('api.updateManagerProduct')
    expect(detailSource).toContain('api.createManagerVariant')
    expect(detailSource).toContain('api.retireVariant')
    expect(detailSource).toContain('api.restoreVariant')
    expect(detailSource).toContain('Variant will stop accepting new orders')
    expect(detailSource).toContain('SKU, color, and EU size cannot be changed after creation.')
    expect(detailSource).toContain('On-hand stock required')
    expect(detailSource).toContain('New on-hand target')
    expect(detailSource).toContain('Receipt quantity')
    expect(detailSource).toContain('api.receiveManagerInventory')
    expect(detailSource).toContain('quantity <= 0')
    expect(detailSource.match(/submitRetryableCommand\(/g)).toHaveLength(2)
    expect(detailSource).toContain('key => api.receiveManagerInventory')
    expect(detailSource).toContain('key => api.adjustManagerInventory')
    expect(detailSource).not.toContain('crypto.randomUUID()')
    expect(detailSource).toContain('Reserved read-only')
    expect(detailSource).toContain("reason.code === 'INVENTORY_BALANCE_CHANGED'")
    expect(detailSource).not.toContain('v-model="row.reserved"')
    expect(detailSource.match(/class="catalog-table-wrap" role="region" tabindex="0"/g)).toHaveLength(2)
    expect(presentationSource).toContain('api.productPresentationProducts()')
    expect(presentationSource).not.toContain('await api.products()')
  })
})
