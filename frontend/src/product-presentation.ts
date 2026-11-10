import type { Locale } from './i18n'

export type LocalizedProductSummary = { vi: string; en: string }
export type ProductPresentation = { summary: LocalizedProductSummary }
export type ProductPresentationRevision = {
  id: string
  revisionNumber: number
  status: 'DRAFT' | 'PUBLISHED'
  entityVersion: number
  summaryVi: string | null
  summaryEn: string | null
  createdAt: string
  publishedAt: string | null
  retiredAt: string | null
}
export type ProductPresentationManagementState = {
  productId: string
  draft: ProductPresentationRevision | null
  published: ProductPresentationRevision | null
}
export type SaveProductPresentationDraft = {
  expectedDraftRevisionId: string | null
  expectedEntityVersion: number | null
  summaryVi: string | null
  summaryEn: string | null
}
export type PublishProductPresentation = {
  expectedDraftRevisionId: string
  expectedEntityVersion: number
  expectedPublishedRevisionId: string | null
}

export const presentationSummary = (value: ProductPresentation | null, language: Locale) =>
  value ? value.summary[language === 'vi-VN' ? 'vi' : 'en'] : null

export const canPublishProductPresentation = (draft: ProductPresentationRevision | null, dirty: boolean) =>
  !!draft && !dirty && !!draft.summaryVi?.trim() && !!draft.summaryEn?.trim()
