import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createSSRApp, type Component } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createMemoryHistory, createRouter } from 'vue-router'
import { api, type Order, type PickupTask, type PaymentAttempt } from './api'
import { commerceCount, setLocale, t } from './i18n'
import CommerceItems from './components/CommerceItems.vue'
import OrdersView from './views/OrdersView.vue'
import OrderStatusView from './views/OrderStatusView.vue'
import PaymentResultView from './views/PaymentResultView.vue'
import PickupQueueView from './views/PickupQueueView.vue'
import PickupDetailView from './views/PickupDetailView.vue'

// Run the views' normal initial loads during SSR for component-level checks.
// These are unit fixtures, not browser or transaction acceptance evidence.
vi.mock('vue', async importOriginal => {
  const vue = await importOriginal<typeof import('vue')>()
  return { ...vue, onMounted: vue.onServerPrefetch }
})

const order: Order = {
  id: 'aaaaaaaa-0000-0000-0000-000000000010', orderReference: 'SC-AAAAAAAA',
  itemCount: 3, quantity: 4, totalAmount: 3390000, currency: 'VND', status: 'PAID',
  reservationId: null, reservationExpiresAt: null, priceQuoteId: null, priceVersionId: null,
  ownerAccountId: 'owner', responsibleBranchId: 'branch', createdAt: '2026-08-31T10:00:00Z',
  variantId: null, sku: null, size: null, locationId: 'location', locationCode: 'FLOOR', locationName: 'Demo Sales Floor',
  unitPriceAmount: null, pickupStatus: 'PAID_WAITING_PREPARATION', fulfillmentType: 'PICKUP',
  deliveryFeeAmount: 0, cancellationEligible: true,
  items: [
    { id: '1', reservationId: 'r1', priceVersionId: 'p1', variantId: 'v1', sku: 'COURT-39', size: '39', color: 'White', locationId: 'location', quantity: 1, unitPriceAmount: 1490000, totalAmount: 1490000 },
    { id: '2', reservationId: 'r2', priceVersionId: 'p2', variantId: 'v2', sku: 'RUNNER-42', size: '42', color: 'Black', locationId: 'location', quantity: 2, unitPriceAmount: 900000, totalAmount: 1800000 },
    { id: '3', reservationId: 'r3', priceVersionId: 'p3', variantId: 'v3', sku: 'TRAIL-40', size: '40', color: 'Beige', locationId: 'location', quantity: 1, unitPriceAmount: 100000, totalAmount: 100000 },
  ],
}
const task: PickupTask = {
  orderId: order.id, branchId: 'branch', branchCode: 'BRANCH', branchName: 'Demo Branch A',
  locationId: 'location', locationCode: 'FLOOR', locationName: 'Demo Sales Floor',
  sku: null, size: null, itemCount: 3, quantity: 4, items: order.items,
  orderStatus: 'PAID', fulfillmentType: 'PICKUP', fulfillmentStatus: 'PREPARED',
  fulfillmentId: 'pickup', deliveryFeeAmount: 0,
}

beforeEach(() => {
  vi.stubGlobal('localStorage', { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn() })
  vi.stubGlobal('window', { setTimeout: vi.fn(), setInterval: vi.fn(), clearTimeout: vi.fn(), clearInterval: vi.fn() })
  vi.spyOn(api, 'orders').mockResolvedValue({ items: [order], page: 0, size: 20, hasNext: false })
  vi.spyOn(api, 'order').mockResolvedValue(order)
  vi.spyOn(api, 'pickupQueue').mockResolvedValue([task])
  vi.spyOn(api, 'pickupTask').mockResolvedValue(task)
  vi.spyOn(api, 'paymentAttempt').mockResolvedValue({ id: 'attempt', orderId: order.id, status: 'SUCCEEDED', amount: order.totalAmount, expiresAt: '2026-08-31T10:15:00Z' } as PaymentAttempt)
})
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

async function render(view: Component, path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:pathMatch(.*)*', component: view }] })
  await router.push(path)
  return renderToString(createSSRApp(view).use(router))
}

describe.each(['en', 'vi-VN'] as const)('multi-item component rendering in %s', language => {
  beforeEach(() => setLocale(language))
  it('renders every line with snapshot quantities, color, unit price and subtotal', async () => {
    const html = await renderToString(createSSRApp(CommerceItems, { items: order.items }))
    for (const item of order.items) {
      expect(html).toContain(item.sku)
      expect(html).toContain(t(item.color!))
      expect(html).toContain(`${t('Quantity')} ${item.quantity}`)
    }
    expect(html).toContain(t('Subtotal'))
    expect(html).toContain(language === 'en' ? '1,800,000' : '1.800.000')
  })
  it('keeps history compact, with complete counts and the human order reference', async () => {
    const html = await render(OrdersView, '/orders')
    expect(html).toContain(order.orderReference)
    expect(html).toContain(commerceCount(3, 4))
    expect(html).not.toContain('commerce-item-price')
    expect(html).toContain(`/orders/${order.id}`)
  })
  it('renders every order detail line, not nullable legacy first-item fields', async () => {
    const html = await render(OrderStatusView, `/orders/${order.id}`)
    for (const item of order.items) expect(html).toContain(item.sku)
    expect(html).toContain(order.orderReference)
    expect(html).toContain(t('Cancel order {reference}?', { reference: order.orderReference }))
    expect(html).not.toContain('null')
  })
  it('provides payment recovery from a pending order after navigation or refresh', async () => {
    vi.mocked(api.order).mockResolvedValue({ ...order, status: 'PENDING_PAYMENT', pickupStatus: 'PENDING_PAYMENT', cancellationEligible: false })
    const html = await render(OrderStatusView, `/orders/${order.id}`)
    expect(html).toContain(t('Pay with VNPAY'))
    expect(html).toContain(t('Your items are held only until the payment deadline.'))
  })
  it('uses delivery-specific copy while a paid delivery waits for preparation', async () => {
    vi.mocked(api.order).mockResolvedValue({ ...order, fulfillmentType: 'DELIVERY' })
    const html = await render(OrderStatusView, `/orders/${order.id}`)
    expect(html).toContain(t('The store has your paid items reserved and will prepare them for delivery.'))
    expect(html).not.toContain(t('The store has your paid items reserved and will prepare them for pickup.'))
  })
  it('shows every line after verified payment', async () => {
    const html = await render(PaymentResultView, '/payment/result?attemptId=attempt')
    expect(html).toContain(t('Payment confirmed.'))
    for (const item of order.items) expect(html).toContain(item.sku)
  })
  it.each(['PAID', 'CANCELLED'] as const)('does not offer another payment from an old failed attempt when the order is %s', async status => {
    vi.mocked(api.order).mockResolvedValue({ ...order, status })
    vi.mocked(api.paymentAttempt).mockResolvedValue({ id: 'old-attempt', orderId: order.id, status: 'FAILED', amount: order.totalAmount, expiresAt: '2026-08-31T10:15:00Z' } as PaymentAttempt)
    const html = await render(PaymentResultView, '/payment/result?attemptId=old-attempt')
    expect(html).toContain(t(status === 'PAID' ? 'Payment confirmed.' : 'The reservation has ended.'))
    expect(html).not.toContain(t('Try VNPAY again'))
  })
  it('keeps one pickup task for the order and lists all its lines', async () => {
    const queue = await render(PickupQueueView, '/operations/fulfillments')
    expect(queue.match(/class="pickup-row"/g)).toHaveLength(1)
    const detail = await render(PickupDetailView, `/operations/fulfillments/${order.id}`)
    for (const item of order.items) { expect(queue).toContain(item.sku); expect(detail).toContain(item.sku) }
    expect(detail).toContain(t('Hand over the whole order?'))
  })
})
