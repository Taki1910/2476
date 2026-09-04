import { computed, ref } from 'vue'
import { api, ApiError, normalizeCartDemand, type CartDemand, type FulfillmentChoice, type PaymentInitiation } from './api'
import { session } from './session'

export type CheckoutCommand = { accountId: string; key: string; quoteId: string; items: CartDemand[]; fulfillment: FulfillmentChoice; rejected: boolean }
const revision = ref(0)
const storageKey = (accountId: string) => `shoe-commerce:checkout:${accountId}`
export class CheckoutStorageError extends Error {}

export function readCheckout(accountId: string): CheckoutCommand | undefined {
  try {
    const saved = localStorage.getItem(storageKey(accountId))
    if (!saved) return
    const command = JSON.parse(saved) as CheckoutCommand
    // A rejected pre-fulfillment command only needs to remain readable long enough to clear it.
    if (!command.fulfillment && command.rejected) command.fulfillment = { type: 'PICKUP', pickupLocationId: '' }
    if (command.accountId !== accountId || !command.key || !command.quoteId || typeof command.rejected !== 'boolean'
      || !command.fulfillment || !['PICKUP', 'DELIVERY'].includes(command.fulfillment.type)) throw new Error()
    command.items = normalizeCartDemand(command.items)
    return command
  } catch { throw new CheckoutStorageError('Checkout recovery storage is unavailable.') }
}

function save(command: CheckoutCommand) {
  try { localStorage.setItem(storageKey(command.accountId), JSON.stringify(command)); revision.value++ }
  catch { throw new CheckoutStorageError('Checkout recovery storage is unavailable.') }
}

export const checkoutRecovery = computed(() => {
  revision.value
  if (!session.account) return {}
  try { return { command: readCheckout(session.account.accountId) } }
  catch { return { storageError: true } }
})
export const checkoutLocked = computed(() => !!checkoutRecovery.value.storageError
  || !!checkoutRecovery.value.command && !checkoutRecovery.value.command.rejected)

export function clearCheckout(accountId: string) {
  try { localStorage.removeItem(storageKey(accountId)); revision.value++ }
  catch { throw new CheckoutStorageError('Checkout recovery storage is unavailable.') }
}

export function invalidateRejectedCheckout() {
  const command = checkoutRecovery.value.command
  if (command?.rejected) clearCheckout(command.accountId)
}

export function beginCheckout(accountId: string, quoteId: string, items: CartDemand[], fulfillment: FulfillmentChoice): CheckoutCommand {
  const existing = readCheckout(accountId)
  if (existing) return existing // Never replace an unresolved purchase with a fresh key.
  const command = { accountId, quoteId, items: normalizeCartDemand(items), fulfillment, key: crypto.randomUUID(), rejected: false }
  save(command) // Durable identity BEFORE the HTTP request, including navigation/refresh.
  return command
}

export function checkoutDefinitelyRejected(error: unknown) {
  return error instanceof ApiError && error.status >= 400 && error.status < 500 && [
    'PRICE_QUOTE_EXPIRED', 'CART_QUOTE_EXPIRED', 'INSUFFICIENT_STOCK', 'INSUFFICIENT_INVENTORY',
    'VARIANT_NOT_SELLABLE', 'VARIANT_UNAVAILABLE', 'NO_COMMON_PICKUP_LOCATION', 'INVALID_CHECKOUT_QUANTITY',
    'INVALID_CART', 'CART_QUOTE_MISMATCH', 'CART_AMOUNT_LIMIT', 'INVALID_FULFILLMENT', 'PICKUP_LOCATION_UNAVAILABLE',
  ].includes(error.code)
}

export async function submitCheckout(command: CheckoutCommand) {
  if (session.account?.accountId !== command.accountId) throw new ApiError(401, 'UNAUTHORIZED', 'Sign in again.')
  save({ ...command, rejected: false })
  try { return await api.cartCheckout(command.quoteId, command.items, command.key, command.fulfillment) }
  catch (error) {
    if (checkoutDefinitelyRejected(error)) save({ ...command, rejected: true })
    throw error
  }
}

// Keep the key after opening the provider, so My Orders can resume that attempt.
// Only an explicit retry of the same server-confirmed failure starts a new one.
export async function recoverablePayment(orderId: string, failedAttemptId?: string) {
  const accountId = session.account?.accountId
  if (!accountId) throw new ApiError(401, 'UNAUTHORIZED', 'Sign in again.')
  const key = `shoe-commerce:payment:${accountId}:${orderId}`
  let identity: string
  try {
    identity = localStorage.getItem(key) ?? crypto.randomUUID()
    localStorage.setItem(key, identity)
  } catch { throw new CheckoutStorageError('Checkout recovery storage is unavailable.') }
  let result = await api.pay(orderId, identity)
  if (result.attempt.id === failedAttemptId && result.attempt.status === 'FAILED') {
    if (session.account?.accountId !== accountId) throw new ApiError(401, 'UNAUTHORIZED', 'Sign in again.')
    identity = crypto.randomUUID()
    try { localStorage.setItem(key, identity) }
    catch { throw new CheckoutStorageError('Checkout recovery storage is unavailable.') }
    result = await api.pay(orderId, identity)
  }
  return result
}

export const paymentDestination = (result: PaymentInitiation) => result.attempt.status === 'PENDING'
  ? result.paymentUrl : `/payment/result?attemptId=${encodeURIComponent(result.attempt.id)}`
