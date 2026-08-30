import { describe, expect, it } from 'vitest'
import { ApiError } from './api'
import { errorCopy, formatDateTime, formatVnd, isExpired, posErrorCopy } from './format'

describe('storefront presentation rules', () => {
  it('formats exact integer đồng without decimal money', () => {
    expect(formatVnd(125000)).toMatch(/125[.\s]000/)
    expect(formatVnd('9007199254740993')).toMatch(/9[.\s]007[.\s]199[.\s]254[.\s]740[.\s]993/)
  })

  it('expires at the server-provided instant', () => {
    expect(isExpired('2026-08-26T02:15:00Z', Date.parse('2026-08-26T02:15:00Z'))).toBe(true)
    expect(isExpired('2026-08-26T02:15:00Z', Date.parse('2026-08-26T02:14:59Z'))).toBe(false)
  })

  it('shows a payment timestamp with its calendar date', () => {
    expect(formatDateTime('2026-08-26T02:15:00Z')).toContain('2026')
  })

  it('maps an availability conflict to a recovery action', () => {
    expect(errorCopy(new ApiError(409, 'VARIANT_UNAVAILABLE', 'conflict'))).toContain('Choose another size')
  })

  it('stops cash intake after a cross-channel inventory loss', () => {
    expect(posErrorCopy(new ApiError(409, 'INSUFFICIENT_INVENTORY', 'conflict'))).toContain('before taking cash')
  })
})
