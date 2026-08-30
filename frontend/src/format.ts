import type { ApiError } from './api'

export const formatVnd = (amount: number | string) => new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
}).format(typeof amount === 'string' ? BigInt(amount) : amount)

export const formatExpiry = (value: string) => new Intl.DateTimeFormat(undefined, {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
}).format(new Date(value))

export const formatDateTime = (value: string) => new Intl.DateTimeFormat('en-GB', {
  dateStyle: 'medium',
  timeStyle: 'long',
}).format(new Date(value))

export const isExpired = (expiresAt: string, now = Date.now()) => new Date(expiresAt).getTime() <= now

export const errorCopy = (error: unknown) => {
  const apiError = error as ApiError
  if (apiError.status === 401) return 'Your session has ended. Sign in again to continue.'
  if (apiError.status === 403) return 'This account is not authorized for this action or location.'
  if (apiError.code === 'VARIANT_UNAVAILABLE') return 'That variant is no longer available. Choose another size and try again.'
  return apiError.message || 'Something went wrong. Try again.'
}

export const checkoutErrorCopy = (error: unknown) => {
  const apiError = error as ApiError
  if (apiError.code === 'PRICE_QUOTE_EXPIRED') return 'Your price confirmation expired. Request a fresh price, then try checkout again.'
  if (apiError.code === 'INSUFFICIENT_STOCK') return 'That pair was just reserved by someone else. Choose another available size or check again.'
  if (apiError.code === 'VARIANT_NOT_SELLABLE') return 'This size is no longer available for checkout. Return to the size list and choose another.'
  if (apiError.code === 'PRICE_QUOTE_CONSUMED') return 'This price confirmation has already been used. Request a fresh price before trying again.'
  if (apiError.code === 'IDEMPOTENCY_KEY_CONFLICT') return 'This checkout request conflicts with an earlier submission. Refresh the price and try again.'
  return errorCopy(error)
}

export const posErrorCopy = (error: unknown) => {
  const apiError = error as ApiError
  if (apiError.code === 'INSUFFICIENT_INVENTORY') return 'This pair was just sold or reserved. Look up the SKU again before taking cash.'
  if (apiError.code === 'SHIFT_CLOSED') return 'This shift is closed. Open a new shift before making another sale.'
  if (apiError.code === 'SHIFT_ALREADY_OPEN') return 'This cashier or register already has an open shift. Refresh the current shift before continuing.'
  if (apiError.code === 'POS_VARIANT_NOT_FOUND') return 'No sellable variant matches that SKU. Check the label and try again.'
  if (apiError.code === 'REGISTER_UNAVAILABLE') return 'This register is not available for your active location assignment.'
  if (apiError.code === 'IDEMPOTENCY_KEY_CONFLICT') return 'This sale retry belongs to another SKU. Look up the intended SKU again.'
  return errorCopy(error)
}
