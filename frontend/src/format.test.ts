import { describe, expect, it } from 'vitest'
import { ApiError } from './api'
import { errorCopy, formatDateTime, formatVnd, isExpired, pickupDisplayState, posErrorCopy } from './format'
import { setLocale, t } from './i18n'

describe('storefront presentation rules', () => {
  it('formats exact integer đồng without decimal money', () => {
    setLocale('vi-VN')
    expect(formatVnd(125000)).toMatch(/125[.\s]000/)
    expect(formatVnd('9007199254740993')).toMatch(/9[.\s]007[.\s]199[.\s]254[.\s]740[.\s]993/)
  })

  it('switches centralized copy and locale formatting', () => {
    setLocale('vi-VN')
    expect(t('Sign in')).toBe('Đăng nhập')
    expect(formatVnd(1490000)).toMatch(/1\.490\.000/)
    setLocale('en')
    expect(t('Sign in')).toBe('Sign in')
    expect(formatVnd(1490000)).toMatch(/1,490,000/)
  })

  it('expires at the server-provided instant', () => {
    expect(isExpired('2026-08-26T02:15:00Z', Date.parse('2026-08-26T02:15:00Z'))).toBe(true)
    expect(isExpired('2026-08-26T02:15:00Z', Date.parse('2026-08-26T02:14:59Z'))).toBe(false)
  })

  it('shows a payment timestamp with its calendar date', () => {
    expect(formatDateTime('2026-08-26T02:15:00Z')).toContain('2026')
  })

  it('uses business time, concise Vietnamese dates and localized safe errors', () => {
    setLocale('vi-VN')
    expect(formatDateTime('2026-08-30T21:12:08Z')).toBe('31/08/2026, 04:12')
    expect(posErrorCopy(new ApiError(404, 'POS_VARIANT_NOT_FOUND', 'internal exact SKU detail'))).toContain('đầy đủ SKU')
    expect(errorCopy(new ApiError(500, 'INTERNAL_ERROR', 'SQL credentials stacktrace'))).toBe(t('The service is temporarily unavailable. Please retry.'))
  })

  it('maps an availability conflict to a recovery action', () => {
    setLocale('en')
    expect(errorCopy(new ApiError(409, 'VARIANT_UNAVAILABLE', 'conflict'))).toContain('Choose another size')
  })

  it('stops cash intake after a cross-channel inventory loss', () => {
    setLocale('en')
    expect(posErrorCopy(new ApiError(409, 'INSUFFICIENT_INVENTORY', 'conflict'))).toContain('before taking cash')
  })

  it('does not count cancelled orders without fulfillment as needing pickup setup', () => {
    expect(pickupDisplayState({ orderStatus: 'CANCELLED', fulfillmentStatus: 'NOT_CREATED' })).toBe('CANCELLED')
    expect(pickupDisplayState({ orderStatus: 'PAID', fulfillmentStatus: 'NOT_CREATED' })).toBe('NOT_CREATED')
  })
})
