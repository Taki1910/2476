import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, SESSION_ENDED_EVENT } from './api'

describe('API session handling', () => {
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

  it('submits a POS sale identity without client-authored price or quantity', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ orderId: 'order-1', total: 125000 }), { status: 201 }))
    vi.stubGlobal('fetch', fetchMock)

    await api.sellPos('shift-1', 'variant-1', 'sale-key')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/pos/sales')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'sale-key', 'X-CSRF-TOKEN': 'csrf' },
    })
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ shiftId: 'shift-1', variantId: 'variant-1' })
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

  it('loads the data-driven hero read model', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ candidates: [] }), { status: 200 }))
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
    expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ items: [{ variantId, quantity: 3 }] })
    expect(fetchMock.mock.calls[1][1].headers['X-CSRF-TOKEN']).toBe('csrf')
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

  it('preserves a line-specific ProblemDetail without exposing it as raw UI copy', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ code: 'INSUFFICIENT_STOCK', variantId: 'variant-b', detail: 'internal detail' }), { status: 409 })))
    await expect(api.product('p')).rejects.toMatchObject({ code: 'INSUFFICIENT_STOCK', variantId: 'variant-b' })
  })
})
