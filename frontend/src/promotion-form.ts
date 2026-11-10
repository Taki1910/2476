import type { PromotionRule } from './api'

export type PromotionEditorDraft = {
  editingId?: string
  familyId?: string
  revisionNumber: number
  name: string
  acquisitionMode: PromotionRule['acquisitionMode']
  normalizedCode: string
  publiclyDiscoverable: boolean
  effectType: PromotionRule['effectType']
  value: number
  minimumSpendAmount?: number
  buyQuantity?: number
  globalUsageLimit?: number
  perCustomerUsageLimit?: number
  priority: number
  productIds: string[]
  customerSummary: string
  customerTerms: string
  validFrom: string
  validTo: string
}

const localDateTime = (value = new Date()) => {
  const offset = value.getTimezoneOffset() * 60_000
  return new Date(value.getTime() - offset).toISOString().slice(0, 16)
}

export function blankPromotionDraft(): PromotionEditorDraft {
  return {
    revisionNumber: 1, name: '', acquisitionMode: 'AUTOMATIC', normalizedCode: '', publiclyDiscoverable: false,
    effectType: 'ITEM_PERCENT', value: 10, priority: 10, productIds: [], customerSummary: '', customerTerms: '',
    validFrom: localDateTime(), validTo: '',
  }
}

export function hydratePromotionDraft(rule: PromotionRule, intent: 'edit' | 'revise'): PromotionEditorDraft {
  return {
    editingId: intent === 'edit' ? rule.id : undefined,
    familyId: rule.familyId,
    revisionNumber: intent === 'edit' ? rule.revisionNumber : rule.revisionNumber + 1,
    name: rule.name,
    acquisitionMode: rule.acquisitionMode,
    normalizedCode: rule.normalizedCode ?? '',
    publiclyDiscoverable: rule.publiclyDiscoverable,
    effectType: rule.effectType,
    value: rule.percentageValue ?? rule.fixedAmount ?? 0,
    minimumSpendAmount: rule.minimumSpendAmount,
    buyQuantity: rule.buyQuantity,
    globalUsageLimit: rule.globalUsageLimit,
    perCustomerUsageLimit: rule.acquisitionMode === 'CLAIMABLE' ? 1 : rule.perCustomerUsageLimit,
    priority: rule.priority,
    productIds: [...rule.productIds],
    customerSummary: rule.customerSummary ?? '',
    customerTerms: rule.customerTerms ?? '',
    validFrom: localDateTime(new Date(rule.validFrom)),
    validTo: rule.validTo ? localDateTime(new Date(rule.validTo)) : '',
  }
}

export function normalizePromotionCodePreview(value: string) {
  return value.normalize('NFKC').trim().toUpperCase()
}
