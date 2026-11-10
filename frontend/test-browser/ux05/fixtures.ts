import type { Order } from '../../src/api'

export function orderFixture(id: 'a' | 'b', delivery = false): Order {
  return {
    id, orderReference: `FIXTURE-${id.toUpperCase()}`, ownerAccountId: 'fixture-owner',
    responsibleBranchId: 'fixture-branch', status: 'PENDING_PAYMENT', pickupStatus: 'PENDING_PAYMENT',
    createdAt: '2026-10-01T10:00:00Z', itemCount: 1, quantity: 1, currency: 'VND',
    merchandiseAmount: 100000, merchandiseDiscountAmount: 0, shippingDiscountAmount: 0,
    totalAmount: 100000, adjustments: [], reservationId: null, reservationExpiresAt: null,
    priceQuoteId: null, priceVersionId: null, variantId: null, sku: null, size: null, unitPriceAmount: null,
    locationId: 'fixture-location', locationName: `Fixture location ${id.toUpperCase()}`, locationCode: `LOC-${id.toUpperCase()}`,
    fulfillmentType: delivery ? 'DELIVERY' : 'PICKUP', deliveryFeeAmount: 0, cancellationEligible: true,
    receiverName: `Synthetic receiver ${id.toUpperCase()}`, receiverPhone: id === 'a' ? '0900000001' : '0900000002',
    deliveryAddress: delivery ? `Synthetic address ${id.toUpperCase()}` : undefined,
    items: [{ id: 'line', reservationId: 'reservation', priceVersionId: 'price', variantId: 'variant',
      sku: `FIXTURE-SKU-${id}`, size: '40', color: 'White', locationId: 'fixture-location',
      quantity: 1, unitPriceAmount: 100000, totalAmount: 100000 }],
  }
}
