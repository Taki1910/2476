import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, SESSION_ENDED_EVENT } from './api'

describe('API session handling', () => {
  it.each(['900', 'abc', '0', '-1', '1.5', '9007199254740992', null])('validates login Retry-After %s', async header => {
    const headers: Record<string, string> = header === null ? {} : { 'Retry-After': header }
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'AUTH_RATE_LIMITED' }), { status: 429, headers })))
    await expect(api.login('account', 'password')).rejects.toMatchObject({
      status: 429, code: 'AUTH_RATE_LIMITED', retryAfterSeconds: header === '900' ? 900 : undefined,
    })
  })
  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('announces an expired session from any API request', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'UNAUTHORIZED' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' },
    })))
    vi.stubGlobal('window', new EventTarget())
    const listener = vi.fn()
    window.addEventListener(SESSION_ENDED_EVENT, listener, { once: true })

    await expect(api.products()).rejects.toMatchObject({ status: 401 })
    expect(listener).toHaveBeenCalledOnce()
  })

  it('submits checkout intent without client-authored money', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'order-1' }), { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.checkout('quote-1', 'checkout-key')

    const [, init] = fetchMock.mock.calls[1]
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/orders/checkout')
    expect(init.headers).toMatchObject({ 'Idempotency-Key': 'checkout-key', 'X-CSRF-TOKEN': 'csrf' })
    expect(JSON.parse(init.body)).toEqual({ quoteId: 'quote-1' })
  })

  it('encodes backend-authoritative storefront search terms', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.products('Metro Runner / 42')

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/storefront/products?q=Metro%20Runner%20%2F%2042', expect.anything())
  })

  it('encodes deterministic discovery queries without changing the browse endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      query: 'giày trắng / chạy bộ', results: [], suggestions: [],
      interpreted: { colors: ['WHITE'], categories: ['RUNNING'], maximumPrice: null, skuLookup: false },
    }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.discovery('giày trắng / chạy bộ')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/storefront/discovery?q=gi%C3%A0y%20tr%E1%BA%AFng%20%2F%20ch%E1%BA%A1y%20b%E1%BB%99',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('uploads fitting photos as multipart without inventing a content type', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'SUCCESS', availableColors: [] }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    const image = new File(['controlled'], 'fit.png', { type: 'image/png' })

    await api.fitAnalysis('product-1', image, 'White')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/storefront/products/product-1/fit-analysis?selectedColor=White')
    expect(fetchMock.mock.calls[1][1].headers).toEqual({ 'X-CSRF-TOKEN': 'csrf' })
    expect(fetchMock.mock.calls[1][1].body).toBeInstanceOf(FormData)
    expect(fetchMock.mock.calls[1][1].body.get('image')).toBe(image)
  })

  it('sends quantity only when the cart contains more than one unit', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'order-1', quantity: 3 }), { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.checkout('quote-1', 'checkout-key', 3)

    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ quoteId: 'quote-1', quantity: 3 })
  })

  it('loads a bounded owned-orders page', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ items: [], page: 1, size: 20, hasNext: false }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.orders(1)

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/orders?page=1&size=20', expect.objectContaining({ credentials: 'include' }))
  })

  it('cancels an owned pending order with CSRF protection', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'order-1', status: 'CANCELLED' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.cancelOrder('order-1')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/orders/order-1/cancel')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', headers: { 'X-CSRF-TOKEN': 'csrf' } })
  })

  it('starts VNPAY from customer intent without client-authored money', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ attempt: { id: 'attempt-1' }, paymentUrl: 'https://sandbox.vnpayment.vn/pay' }), { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.pay('order-1', 'payment-key')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/orders/order-1/payments')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'POST',
      headers: { 'Idempotency-Key': 'payment-key', 'X-CSRF-TOKEN': 'csrf' },
    })
    expect(fetchMock.mock.calls[1][1].body).toBeUndefined()
  })

  it('submits confirmed cancellation identity without client-authored reversal amount', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ orderId: 'order-1', fulfillmentStatus: 'CANCELLED' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.cancelConfirmed('order-1', 'cancel-key')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/orders/order-1/cancel')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', headers: { 'Idempotency-Key': 'cancel-key', 'X-CSRF-TOKEN': 'csrf' } })
    expect(fetchMock.mock.calls[1][1].body).toBeUndefined()
  })

  it('protects terminal handover with CSRF and an idempotency key', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'pickup-1', status: 'HANDED_OVER' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.handoverPickup('pickup-1', 'handover-key')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/pickup-fulfillments/pickup-1/handover')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', headers: { 'Idempotency-Key': 'handover-key', 'X-CSRF-TOKEN': 'csrf' } })
    expect(fetchMock.mock.calls[1][1].body).toBeUndefined()
  })

  it('uses exact barcode and operational search endpoints', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('{}', { status: 200 }))
      .mockResolvedValueOnce(new Response('[]', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.posBarcode('shift/1', ' 000Ab-9 ')
    await api.searchPosVariants('shift/1', 'Court Black 41')

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/operations/pos/variants/barcode?shiftId=shift%2F1&barcode=%20000Ab-9%20')
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/pos/variants/search?shiftId=shift%2F1&q=Court%20Black%2041')
  })

  it('submits reviewed POS price identity without client-authored money or quantity', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ orderId: 'order-1', total: 125000 }), { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.sellPos('shift-1', 'variant-1', 'price-version-1', 'sale-key')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/pos/sales')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'sale-key', 'X-CSRF-TOKEN': 'csrf' },
    })
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      shiftId: 'shift-1', variantId: 'variant-1', expectedPriceVersionId: 'price-version-1',
    })
  })

  it('loads read-only reports with encoded scope and exclusive dates', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ netSales: '250000' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.netSales('2026-08-28', '2026-08-29', 'location/1')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/operations/reports/net-sales?fromDate=2026-08-28&toDate=2026-08-29&locationId=location%2F1',
      expect.objectContaining({ credentials: 'include' }),
    )
    expect(fetchMock.mock.calls[0][1].method).toBeUndefined()
  })

  it('loads the customer-safe hero product summaries', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ products: [] }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.hero()

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/storefront/hero', expect.objectContaining({ credentials: 'include' }))
  })

  it('bounds API waiting time without changing checkout identity', async () => {
    const signal = new AbortController().signal
    const timeout = vi.spyOn(AbortSignal, 'timeout').mockReturnValue(signal)
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    await api.products()
    expect(timeout).toHaveBeenCalledWith(20_000)
    expect(fetchMock.mock.calls[0][1].signal).toBe(signal)
  })

  it('quotes all normalized lines with CSRF and no cached prices', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'cart-quote' })))
    vi.stubGlobal('fetch', fetchMock)
    const variantId = 'aaaaaaaa-0000-0000-0000-000000000001'
    await api.cartQuote([{ variantId, quantity: 1 }, { variantId, quantity: 2 }])
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/storefront/cart-quotes')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ items: [{ variantId, quantity: 3 }], fulfillment: { type: 'PICKUP' }, voucherSelection: { type: 'NONE' } })
    expect(fetchMock.mock.calls[1][1].headers['X-CSRF-TOKEN']).toBe('csrf')
  })

  it('sends stable destination codes without client-authored shipping money', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({})))
    vi.stubGlobal('fetch', fetchMock)
    await api.cartQuote([{ variantId: 'aaaaaaaa-0000-0000-0000-000000000001', quantity: 1 }],
      { type: 'DELIVERY', destinationProvinceCode: '79', destinationDistrictCode: '760', destinationWardCode: 'DEMO-760-01' })
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({
      items: [{ variantId: 'aaaaaaaa-0000-0000-0000-000000000001', quantity: 1 }],
      fulfillment: { type: 'DELIVERY', destinationProvinceCode: '79', destinationDistrictCode: '760', destinationWardCode: 'DEMO-760-01' },
      voucherSelection: { type: 'NONE' },
    })
  })

  it('loads wards within the selected province and district', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.wards('79', '760')

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/reference/provinces/79/districts/760/wards',
      expect.objectContaining({ credentials: 'include' }),
    )
  })

  it('submits one whole-cart command with stable ordering and the supplied key', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'one-order' })))
    vi.stubGlobal('fetch', fetchMock)
    const a = { variantId: 'aaaaaaaa-0000-0000-0000-000000000001', quantity: 3 }
    const b = { variantId: 'bbbbbbbb-0000-0000-0000-000000000002', quantity: 1 }
    const fulfillment = { type: 'PICKUP' as const, pickupLocationId: 'location' }
    await api.cartCheckout('quote', [b, a], 'same-key', fulfillment)
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/orders/cart-checkout')
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ quoteId: 'quote', items: [a, b], fulfillment })
    expect(fetchMock.mock.calls[1][1].headers['Idempotency-Key']).toBe('same-key')
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('uses explicit authoritative voucher selections and never sends voucher money', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }))).mockResolvedValueOnce(new Response(JSON.stringify({ id: 'quote' })))
    vi.stubGlobal('fetch', fetchMock)
    const variantId = 'aaaaaaaa-0000-0000-0000-000000000001'
    await api.cartQuote([{ variantId, quantity: 1 }], undefined, { type: 'CODE', code: 'SAVE-10' })
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ items: [{ variantId, quantity: 1 }], fulfillment: { type: 'PICKUP' }, voucherSelection: { type: 'CODE', code: 'SAVE-10' } })
  })

  it('preserves a line-specific ProblemDetail without exposing it as raw UI copy', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'INSUFFICIENT_STOCK', variantId: 'variant-b', detail: 'internal detail' }), { status: 409 })))
    await expect(api.product('p')).rejects.toMatchObject({ code: 'INSUFFICIENT_STOCK', variantId: 'variant-b' })
  })

  it('preserves semantic field identifiers from ProblemDetail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 'DELIVERY_ADDRESS_INVALID', detail: 'private detail', fieldErrors: { addressLine: 'INVALID' },
    }), { status: 400 })))

    await expect(api.product('p')).rejects.toMatchObject({
      code: 'DELIVERY_ADDRESS_INVALID', fieldErrors: { addressLine: 'INVALID' },
    })
  })
})
