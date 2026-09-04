import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, type Order, type PaymentInitiation } from './api'
import { beginCheckout as beginCheckoutCommand, checkoutLocked, checkoutRecovery, CheckoutStorageError, clearCheckout, paymentDestination, readCheckout, recoverablePayment, submitCheckout } from './checkout'
import { addToCart, cart, setCartQuantity } from './cart'
import { cartErrorCopy } from './format'
import { session } from './session'
import { setLocale } from './i18n'

const A = 'aaaaaaaa-0000-0000-0000-000000000001'
const B = 'bbbbbbbb-0000-0000-0000-000000000002'
const items = [{ variantId: B, quantity: 2 }, { variantId: A, quantity: 1 }]
const fulfillment = { type: 'PICKUP' as const, pickupLocationId: 'location' }
const beginCheckout = (accountId: string, quoteId: string, demand: typeof items) =>
  beginCheckoutCommand(accountId, quoteId, demand, fulfillment)
let saved: Map<string, string>
beforeEach(() => {
  saved = new Map()
  vi.stubGlobal('localStorage', { getItem: (key: string) => saved.get(key) ?? null, setItem: (key: string, value: string) => saved.set(key, value), removeItem: (key: string) => saved.delete(key) })
  session.account = { accountId: 'owner', login: 'customer', roles: [], permissions: ['ORDER_PLACE'] }
  cart.items = []
})
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('durable checkout identity', () => {
  it('persists the complete canonical command before the API can run', async () => {
    const command = beginCheckout('owner', 'quote', items)
    const send = vi.spyOn(api, 'cartCheckout').mockImplementation(async (quoteId, demand, key) => {
      expect(readCheckout('owner')).toEqual(command)
      expect({ quoteId, demand, key }).toEqual({ quoteId: 'quote', demand: [...items].reverse(), key: command.key })
      return { id: 'one-order' } as Order
    })
    expect((await submitCheckout(command)).id).toBe('one-order')
    expect(send).toHaveBeenCalledOnce()
  })
  it('replays the identical key and payload after timeout and simulated reload', async () => {
    const send = vi.spyOn(api, 'cartCheckout').mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValue({ id: 'same-order' } as Order)
    const first = beginCheckout('owner', 'quote', items)
    await expect(submitCheckout(first)).rejects.toThrow()
    const restored = readCheckout('owner')!
    // A fresh view must not replace an unresolved command, even with new demand.
    expect(beginCheckout('owner', 'another-quote', [{ variantId: A, quantity: 5 }])).toEqual(restored)
    await submitCheckout(restored)
    expect(send.mock.calls[0]).toEqual(send.mock.calls[1])
    expect(restored.rejected).toBe(false)
  })
  it('blocks cart mutation while a checkout result is unknown', () => {
    const item = { productId: 'p', productName: 'Runner', variantId: A, sku: 'A-42', size: '42', color: 'White', image: null, amount: 1490000, currency: 'VND' as const }
    addToCart(item)
    beginCheckout('owner', 'quote', items)
    expect(checkoutLocked.value).toBe(true)
    setCartQuantity(A, 9)
    expect(cart.items[0].quantity).toBe(1)
    expect(addToCart({ ...item, variantId: B })).toBe('checkout-pending')
  })
  it('retains the same failed request until an explicit edit invalidates it', async () => {
    addToCart({ productId: 'p', productName: 'Runner', variantId: A, sku: 'A-42', size: '42', color: 'White', image: null, amount: 1490000, currency: 'VND' })
    const original = beginCheckout('owner', 'quote', items)
    vi.spyOn(api, 'cartCheckout').mockRejectedValue(new ApiError(409, 'INSUFFICIENT_STOCK', 'conflict', B))
    await expect(submitCheckout(original)).rejects.toThrow()
    expect(readCheckout('owner')).toEqual({ ...original, rejected: true })
    expect(checkoutLocked.value).toBe(false)
    setCartQuantity(A, 2)
    expect(readCheckout('owner')).toBeUndefined()
    expect(beginCheckout('owner', 'fresh-quote', [{ variantId: A, quantity: 2 }]).key).not.toBe(original.key)
  })
  it('never treats key conflicts or server errors as proof that no order exists', async () => {
    for (const error of [new ApiError(409, 'IDEMPOTENCY_KEY_CONFLICT', 'conflict'), new ApiError(500, 'INTERNAL_ERROR', 'error')]) {
      const command = beginCheckout('owner', 'quote', items)
      vi.spyOn(api, 'cartCheckout').mockRejectedValue(error)
      await expect(submitCheckout(command)).rejects.toThrow()
      expect(readCheckout('owner')?.rejected).toBe(false)
      expect(checkoutLocked.value).toBe(true)
    }
  })
  it('keeps command recovery scoped to the originating signed-in account', async () => {
    const command = beginCheckout('owner', 'quote', items)
    session.account = { ...session.account!, accountId: 'other' }
    expect(checkoutRecovery.value.command).toBeUndefined()
    expect(checkoutLocked.value).toBe(false)
    const send = vi.spyOn(api, 'cartCheckout')
    await expect(submitCheckout(command)).rejects.toMatchObject({ status: 401 })
    expect(send).not.toHaveBeenCalled()
    session.account = { ...session.account!, accountId: 'owner' }
    expect(checkoutRecovery.value.command?.key).toBe(command.key)
  })
  it('does not send an order when durable recovery storage fails', () => {
    vi.stubGlobal('localStorage', { getItem: () => null, setItem: () => { throw new Error('quota') } })
    const send = vi.spyOn(api, 'cartCheckout')
    expect(() => beginCheckout('owner', 'quote', items)).toThrow(CheckoutStorageError)
    expect(send).not.toHaveBeenCalled()
  })
  it('refuses to silently replace a corrupted recovery record', () => {
    saved.set('shoe-commerce:checkout:owner', '{broken')
    expect(() => beginCheckout('owner', 'new', items)).toThrow(CheckoutStorageError)
  })
  it('removes a resolved identity explicitly', () => {
    beginCheckout('owner', 'quote', items)
    clearCheckout('owner')
    expect(readCheckout('owner')).toBeUndefined()
    expect(checkoutLocked.value).toBe(false)
  })
  it('retains payment initiation identity after an uncertain response', async () => {
    const pay = vi.spyOn(api, 'pay').mockRejectedValue(new TypeError('network'))
    await expect(recoverablePayment('order')).rejects.toThrow()
    await expect(recoverablePayment('order')).rejects.toThrow()
    expect(pay.mock.calls[0]).toEqual(pay.mock.calls[1])
  })
  it('resumes an already opened payment with the same key after navigation or reload', async () => {
    const result = { attempt: { id: 'attempt', status: 'PENDING' }, paymentUrl: '/provider' } as PaymentInitiation
    const pay = vi.spyOn(api, 'pay').mockResolvedValue(result)
    expect(paymentDestination(await recoverablePayment('order'))).toBe('/provider')
    expect(saved.get('shoe-commerce:payment:owner:order')).toBe(pay.mock.calls[0][1])
    await recoverablePayment('order')
    expect(pay.mock.calls[1]).toEqual(pay.mock.calls[0])
  })
  it('starts a new payment only for the confirmed failure, preserving an uncertain retry', async () => {
    const failed = { attempt: { id: 'failed', status: 'FAILED' } } as PaymentInitiation
    const pending = { attempt: { id: 'retry', status: 'PENDING' }, paymentUrl: '/provider' } as PaymentInitiation
    const pay = vi.spyOn(api, 'pay').mockResolvedValueOnce(failed)
      .mockRejectedValueOnce(new TypeError('network')).mockResolvedValue(pending)
    await expect(recoverablePayment('order', 'failed')).rejects.toThrow('network')
    expect(pay.mock.calls[1][1]).not.toBe(pay.mock.calls[0][1])
    expect(saved.get('shoe-commerce:payment:owner:order')).toBe(pay.mock.calls[1][1])
    await recoverablePayment('order', 'failed')
    expect(pay).toHaveBeenCalledTimes(3)
    expect(pay.mock.calls[2]).toEqual(pay.mock.calls[1])
  })
  it.each(['SUCCEEDED', 'REVIEW_REQUIRED', 'EXPIRED', 'CANCELLED'] as const)('routes a %s replay to verified status without another payment', async status => {
    const pay = vi.spyOn(api, 'pay').mockResolvedValue({ attempt: { id: 'attempt', status }, paymentUrl: '/provider' } as PaymentInitiation)
    expect(paymentDestination(await recoverablePayment('order', 'attempt'))).toBe('/payment/result?attemptId=attempt')
    expect(pay).toHaveBeenCalledOnce()
  })
  it('does not start payment without a signed-in account', async () => {
    session.account = undefined
    const pay = vi.spyOn(api, 'pay')
    await expect(recoverablePayment('order')).rejects.toMatchObject({ status: 401 })
    expect(pay).not.toHaveBeenCalled()
  })
})

describe('localized line-specific failures', () => {
  it.each(['en', 'vi-VN'] as const)('identifies the affected second line in %s without raw backend details', language => {
    setLocale(language)
    const lines = [{ productId: 'p', productName: 'Court Classic', variantId: B, sku: 'CC-40', size: '40', color: 'White', image: null, amount: 100, quantity: 2, currency: 'VND' as const }]
    const message = cartErrorCopy(new ApiError(409, 'INSUFFICIENT_STOCK', 'raw SQL table secret', B), lines)
    expect(message).toContain('Court Classic')
    expect(message).toContain('CC-40')
    expect(message).toContain(language === 'en' ? 'Reduce the quantity' : 'giảm số lượng')
    expect(message).not.toContain('raw SQL')
    expect(cartErrorCopy(new ApiError(409, 'VARIANT_UNAVAILABLE', 'raw SQL', B), lines)).toContain(language === 'en' ? 'choose another size' : 'chọn kích cỡ khác')
  })
})
