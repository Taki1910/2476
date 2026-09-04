import { ApiError, type PickupTask } from './api'
import { locale, t } from './i18n'
import { CheckoutStorageError } from './checkout'
import type { CartItem } from './cart'

export const formatVnd = (amount: number | string) => new Intl.NumberFormat(locale.value, {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
}).format(typeof amount === 'string' ? BigInt(amount) : amount)

export const formatExpiry = (value: string) => new Intl.DateTimeFormat(locale.value, {
  timeZone: 'Asia/Ho_Chi_Minh',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
}).format(new Date(value))

export const formatDateTime = (value: string) => `${new Intl.DateTimeFormat(locale.value, {
  timeZone: 'Asia/Ho_Chi_Minh',
  day: '2-digit', month: '2-digit', year: 'numeric',
}).format(new Date(value))}, ${formatExpiry(value)}`

export const isExpired = (expiresAt: string, now = Date.now()) => new Date(expiresAt).getTime() <= now

// A cancelled order needs no preparation even if no fulfillment was ever created.
export const pickupDisplayState = (task: Pick<PickupTask, 'orderStatus' | 'fulfillmentStatus'>) =>
  task.orderStatus === 'CANCELLED' ? 'CANCELLED' : task.fulfillmentStatus

export const errorCopy = (error: unknown) => {
  if (error instanceof CheckoutStorageError) return t('Browser storage is unavailable. Enable it before checkout so a retry cannot create another order.')
  const apiError = error as ApiError
  if (!(error instanceof ApiError) || apiError.status >= 500) return t('The service is temporarily unavailable. Please retry.')
  if (apiError.status === 401) return t('Your session has ended. Sign in again to continue.')
  if (apiError.status === 403) return t('This account is not authorized for this action or location.')
  if (apiError.code === 'FIT_IMAGE_TOO_LARGE') return t('The image must be 5 MB or smaller.')
  if (apiError.code === 'FIT_IMAGE_FORMAT_UNSUPPORTED') return t('Only PNG or JPEG images up to 5 MB are accepted.')
  if (['FIT_IMAGE_INVALID', 'FIT_IMAGE_DIMENSIONS_INVALID'].includes(apiError.code)) return t('The image could not be used.')
  if (apiError.code === 'FIT_ANALYSIS_BUSY') return t('Fit analysis is busy. Please try again shortly.')
  if (apiError.code === 'VARIANT_UNAVAILABLE') return t('That variant is no longer available. Choose another size and try again.')
  if (apiError.code === 'IDENTITY_ALREADY_EXISTS') return t('An account with this login already exists.')
  if (apiError.status === 404) return t('This record is no longer available. Refresh and try again.')
  if (apiError.status === 400) return t('Check the entered details and try again.')
  if (apiError.status === 409) return t('The state changed. Refresh before trying this action again.')
  return t('Something went wrong. Try again.')
}

export const cartErrorCopy = (error: unknown, items: CartItem[]) => {
  if (!(error instanceof ApiError)) return errorCopy(error)
  const item = items.find(line => line.variantId === error.variantId)
  const line = item ? `${item.productName} · ${t('Size')} ${item.size} · ${item.sku}` : t('A cart item')
  if (['INSUFFICIENT_STOCK', 'INSUFFICIENT_INVENTORY'].includes(error.code)) return t('{line} no longer has the requested quantity. Reduce the quantity or remove it, then check again.', { line })
  if (['VARIANT_NOT_SELLABLE', 'VARIANT_UNAVAILABLE'].includes(error.code)) return t('{line} is no longer available. Remove it or choose another size, then check again.', { line })
  if (error.code === 'NO_COMMON_PICKUP_LOCATION') return t('No single pickup location can supply this whole cart. Adjust the items or quantities and check again.')
  if (error.code === 'CART_AMOUNT_LIMIT') return t('This cart exceeds the payment limit of {amount}. Reduce the items or quantities and check again.', { amount: formatVnd(9999999999) })
  if (['CART_QUOTE_EXPIRED', 'PRICE_QUOTE_EXPIRED'].includes(error.code)) return t('Your price confirmation expired. Request a fresh price, then try checkout again.')
  if (['IDEMPOTENCY_KEY_CONFLICT', 'PRICE_QUOTE_CONSUMED', 'CART_QUOTE_CONSUMED'].includes(error.code)) return t('This purchase needs verification. Open My Orders or retry the same checkout. Do not start a new purchase.')
  return errorCopy(error)
}

export const checkoutErrorCopy = (error: unknown) => {
  const apiError = error as ApiError
  if (apiError.code === 'PRICE_QUOTE_EXPIRED') return t('Your price confirmation expired. Request a fresh price, then try checkout again.')
  if (apiError.code === 'INSUFFICIENT_STOCK') return t('That pair was just reserved by someone else. Choose another available size or check again.')
  if (apiError.code === 'VARIANT_NOT_SELLABLE') return t('This size is no longer available for checkout. Return to the size list and choose another.')
  if (apiError.code === 'PRICE_QUOTE_CONSUMED') return t('This price confirmation has already been used. Request a fresh price before trying again.')
  if (apiError.code === 'IDEMPOTENCY_KEY_CONFLICT') return t('This checkout request conflicts with an earlier submission. Refresh the price and try again.')
  return errorCopy(error)
}

export const posErrorCopy = (error: unknown) => {
  const apiError = error as ApiError
  if (apiError.code === 'INSUFFICIENT_INVENTORY') return t('This pair was just sold or reserved. Look up the SKU again before taking cash.')
  if (apiError.code === 'SHIFT_CLOSED') return t('This shift is closed. Open a new shift before making another sale.')
  if (apiError.code === 'SHIFT_ALREADY_OPEN') return t('This cashier or register already has an open shift. Refresh the current shift before continuing.')
  if (apiError.code === 'POS_VARIANT_NOT_FOUND') return t('No sellable variant matches that SKU. Check the label and try again.')
  if (apiError.code === 'REGISTER_UNAVAILABLE') return t('This register is not available for your active location assignment.')
  if (apiError.code === 'IDEMPOTENCY_KEY_CONFLICT') return t('This sale retry belongs to another SKU. Look up the intended SKU again.')
  return errorCopy(error)
}
