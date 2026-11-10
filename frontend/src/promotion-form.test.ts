import { describe, expect, it } from 'vitest'
import type { PromotionRule } from './api'
import { blankPromotionDraft, hydratePromotionDraft, normalizePromotionCodePreview } from './promotion-form'

const rule: PromotionRule = {
  id: 'revision-1', familyId: 'family-1', revisionNumber: 4, name: 'Member weekend', status: 'DRAFT',
  layer: 'ORDER_AUTOMATIC', effectType: 'ORDER_FIXED', fixedAmount: 120000, minimumSpendAmount: 900000,
  globalUsageLimit: 30, perCustomerUsageLimit: 1, priority: 8,
  validFrom: '2026-10-01T00:00:00Z', validTo: '2026-10-03T00:00:00Z', productIds: [],
  customerSummary: 'Save on the weekend', customerTerms: 'One use per customer.', acquisitionMode: 'CLAIMABLE',
  publiclyDiscoverable: true,
}

describe('P3 promotion editor state', () => {
  it('hydrates every persisted field and starts a revision without stale editor data', () => {
    const editing = hydratePromotionDraft(rule, 'edit')
    expect(editing).toMatchObject({ familyId: 'family-1', revisionNumber: 4, editingId: 'revision-1', value: 120000, minimumSpendAmount: 900000, globalUsageLimit: 30, perCustomerUsageLimit: 1, customerTerms: 'One use per customer.' })

    const revision = hydratePromotionDraft(rule, 'revise')
    expect(revision).toMatchObject({ familyId: 'family-1', revisionNumber: 5, editingId: undefined })

    expect(blankPromotionDraft()).toEqual(blankPromotionDraft())
    expect(blankPromotionDraft()).not.toMatchObject({ familyId: 'family-1', value: 120000, minimumSpendAmount: 900000, validTo: '2026-10-03T00:00:00Z' })
  })

  it('normalizes the manager code preview with NFKC, trim, and uppercase', () => {
    expect(normalizePromotionCodePreview('  sＡle_20  ')).toBe('SALE_20')
  })
})
