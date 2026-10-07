import { ApiError, type PublicOffer } from './api'
import { formatVnd } from './format'
import { t } from './i18n'

export function offerBenefit(offer:PublicOffer) {
  if (offer.effectType === 'FREE_SHIPPING') return t('Free shipping')
  if (offer.percentageValue != null) return t('{value}% off', { value: offer.percentageValue })
  return t('{amount} off', { amount: formatVnd(offer.fixedAmount ?? 0) })
}

export function voucherError(error:unknown) {
  const code=error instanceof ApiError?error.code:''
  return t(({
    VOUCHER_NOT_FOUND_OR_UNAVAILABLE:'This offer is unavailable. Choose another offer or continue without one.',
    VOUCHER_EXPIRED:'This offer has expired. Choose another offer or continue without one.',
    VOUCHER_ALREADY_USED:'You have already used this offer.',
    VOUCHER_MINIMUM_SPEND_NOT_MET:'Your cart has not reached this offer’s minimum spend.',
    VOUCHER_USAGE_LIMIT_REACHED:'This offer has reached its usage limit.',
    VOUCHER_STALE:'This offer changed. Review a fresh total before trying checkout again.',
    INVALID_VOUCHER_SELECTION:'Choose one saved offer or enter one valid code.',
  } as Record<string,string>)[code] ?? 'The offer could not be applied. Try another offer or continue without one.')
}
