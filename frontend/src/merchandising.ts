export const SECTION_TYPES = ['HERO_BANNER', 'PRODUCT_COLLECTION', 'PROMOTION_SPOTLIGHT'] as const
export const CTA_KINDS = ['PRODUCT_DETAIL', 'CATALOG_SEARCH', 'PROMOTION_DETAIL', 'OFFERS'] as const
export const MEDIA_OPTIONS = [
  ['COURT_CLASSIC', '/products/court-classic.png'],
  ['METRO_RUNNER', '/products/metro-runner.png'],
  ['STUDIO_LOW', '/products/studio-low.png'],
  ['TRAIL_FORM', '/products/trail-form.png'],
  ['AFTER_DARK', '/products/after-dark.png'],
  ['DAILY_CANVAS', '/products/daily-canvas.png'],
  ['COURT_HIGH', '/products/court-high.png'],
  ['PACE_KNIT', '/products/pace-knit.png'],
] as const

export type StorefrontSectionType = typeof SECTION_TYPES[number]
export type StorefrontCtaKind = typeof CTA_KINDS[number]
export type StorefrontMediaKey = typeof MEDIA_OPTIONS[number][0]

export type StorefrontSectionInput = {
  type: StorefrontSectionType
  enabled: boolean
  titleVi: string
  titleEn: string
  supportingTextVi: string | null
  supportingTextEn: string | null
  ctaLabelVi: string
  ctaLabelEn: string
  ctaKind: StorefrontCtaKind
  ctaTargetPublicId: string | null
  ctaSearchTerm: string | null
  promotionFamilyId: string | null
  mediaKey: StorefrontMediaKey | null
  productIds: string[]
}

export type StorefrontStoredSection = StorefrontSectionInput & { sectionId: string; position: number }
export type StorefrontRevision = {
  revisionId: string
  revisionNumber: number
  status: 'DRAFT' | 'PUBLISHED'
  entityVersion: number
  publishedAt: string | null
  sections: StorefrontStoredSection[]
}
export type StorefrontManagementState = { draft: StorefrontRevision | null; published: StorefrontRevision | null }
export type StorefrontLocalizedCopy = { titleVi: string; titleEn: string; supportingTextVi: string | null; supportingTextEn: string | null }
export type StorefrontLocalizedLabel = { vi: string; en: string }
export type StorefrontCustomerCta = { label: StorefrontLocalizedLabel; kind: StorefrontCtaKind; targetPublicId: string | null; searchTerm: string | null }
export type StorefrontCustomerMedia = { key: StorefrontMediaKey; url: string }
export type StorefrontCustomerSection<Product, Promotion> = {
  sectionId: string
  type: StorefrontSectionType
  position: number
  copy: StorefrontLocalizedCopy
  cta: StorefrontCustomerCta
  media: StorefrontCustomerMedia | null
  products: Product[]
  promotion: Promotion | null
}
export type StorefrontHomepage<Product, Promotion> = {
  publishedRevisionId: string | null
  publishedAt: string | null
  sections: StorefrontCustomerSection<Product, Promotion>[]
}
export type SaveStorefrontDraft = {
  expectedDraftRevisionId: string | null
  expectedEntityVersion: number | null
  sections: StorefrontSectionInput[]
}
export type PublishStorefrontDraft = {
  expectedDraftRevisionId: string
  expectedEntityVersion: number
  expectedPublishedRevisionId: string | null
}
export type CloneStorefrontPublished = { expectedPublishedRevisionId: string }
export type StorefrontSectionDraft = StorefrontSectionInput & { clientId: string }
export type AssistantCopyLocale = 'VI' | 'EN'
export type AssistantCopyField = 'TITLE' | 'SUPPORTING_TEXT' | 'CTA_LABEL'
export type AssistantIntentResult = { intent: 'BEST_SELLERS' | 'PROMOTED' | 'NEEDS_REVIEW' | 'SEARCH' | 'UNSUPPORTED'; query: string; explanation: string }

const controlledMedia = new Set<string>(MEDIA_OPTIONS.map(([key]) => key))

export function createSection(type: StorefrontSectionType, clientId: string = crypto.randomUUID()): StorefrontSectionDraft {
  const common = {
    clientId, type, enabled: true, titleVi: '', titleEn: '', supportingTextVi: null,
    supportingTextEn: null, ctaLabelVi: '', ctaLabelEn: '', ctaTargetPublicId: null,
    ctaSearchTerm: null, promotionFamilyId: null, mediaKey: null, productIds: [],
  }
  if (type === 'HERO_BANNER') return { ...common, ctaKind: 'OFFERS', mediaKey: 'COURT_CLASSIC' }
  if (type === 'PRODUCT_COLLECTION') return { ...common, ctaKind: 'CATALOG_SEARCH', ctaSearchTerm: '' }
  return { ...common, ctaKind: 'PROMOTION_DETAIL' }
}

export function fromRevision(revision: StorefrontRevision | null): StorefrontSectionDraft[] {
  return revision?.sections
    .slice().sort((a, b) => a.position - b.position)
    .map(({ sectionId, position: _position, ...section }) => ({ ...section, productIds: [...section.productIds], clientId: sectionId })) ?? []
}

export function toSectionInput(section: StorefrontSectionDraft): StorefrontSectionInput {
  const text = (value: string | null) => value?.trim() || null
  const base = {
    type: section.type, enabled: section.enabled, titleVi: section.titleVi.trim(), titleEn: section.titleEn.trim(),
    supportingTextVi: text(section.supportingTextVi), supportingTextEn: text(section.supportingTextEn),
    ctaLabelVi: section.ctaLabelVi.trim(), ctaLabelEn: section.ctaLabelEn.trim(), ctaKind: section.ctaKind,
    ctaTargetPublicId: section.ctaTargetPublicId || null, ctaSearchTerm: text(section.ctaSearchTerm),
    promotionFamilyId: null, mediaKey: null, productIds: [] as string[],
  }
  if (section.type === 'HERO_BANNER') return { ...base, mediaKey: controlledMedia.has(section.mediaKey ?? '') ? section.mediaKey : null }
  if (section.type === 'PRODUCT_COLLECTION') return { ...base, productIds: [...section.productIds] }
  return { ...base, ctaKind: 'PROMOTION_DETAIL', ctaTargetPublicId: section.promotionFamilyId, promotionFamilyId: section.promotionFamilyId }
}

export function draftFingerprint(sections: StorefrontSectionDraft[]) {
  return JSON.stringify(sections.map(toSectionInput))
}

export function addSection(sections: StorefrontSectionDraft[], type: StorefrontSectionType, id?: string) {
  return sections.length >= 8 ? sections : [...sections, createSection(type, id)]
}

export function removeSection(sections: StorefrontSectionDraft[], clientId: string) {
  return sections.filter(section => section.clientId !== clientId)
}

export function moveSection(sections: StorefrontSectionDraft[], clientId: string, offset: -1 | 1) {
  const index = sections.findIndex(section => section.clientId === clientId)
  const destination = index + offset
  if (index < 0 || destination < 0 || destination >= sections.length) return sections
  const moved = [...sections]
  ;[moved[index], moved[destination]] = [moved[destination], moved[index]]
  return moved
}

export function toggleSection(sections: StorefrontSectionDraft[], clientId: string) {
  return sections.map(section => section.clientId === clientId ? { ...section, enabled: !section.enabled } : section)
}

export function addProduct(section: StorefrontSectionDraft, productId: string) {
  if (section.type !== 'PRODUCT_COLLECTION' || section.productIds.includes(productId) || section.productIds.length >= 8) return section
  return { ...section, productIds: [...section.productIds, productId] }
}

export type ApplyCandidateOutcome = 'APPLIED' | 'ALREADY_PRESENT' | 'COLLECTION_FULL' | 'COLLECTION_NOT_FOUND'

export function applyCandidateToDraft(sections: StorefrontSectionDraft[], collectionClientId: string, productId: string) {
  const index = sections.findIndex(section => section.clientId === collectionClientId && section.type === 'PRODUCT_COLLECTION')
  if (index < 0) return { sections, outcome: 'COLLECTION_NOT_FOUND' as const }
  const collection = sections[index]
  if (collection.productIds.includes(productId)) return { sections, outcome: 'ALREADY_PRESENT' as const }
  if (collection.productIds.length >= 8) return { sections, outcome: 'COLLECTION_FULL' as const }
  const updated = [...sections]
  updated[index] = addProduct(collection, productId)
  return { sections: updated, outcome: 'APPLIED' as const }
}

export function applyAssistantCopy(sections: StorefrontSectionDraft[], clientId: string, locale: AssistantCopyLocale, field: AssistantCopyField, value: string) {
  const key = ({
    'VI:TITLE': 'titleVi', 'EN:TITLE': 'titleEn',
    'VI:SUPPORTING_TEXT': 'supportingTextVi', 'EN:SUPPORTING_TEXT': 'supportingTextEn',
    'VI:CTA_LABEL': 'ctaLabelVi', 'EN:CTA_LABEL': 'ctaLabelEn',
  } as const)[`${locale}:${field}`]
  return sections.map(section => section.clientId === clientId ? { ...section, [key]: value } : section)
}

export function candidateQueryForIntent(result: AssistantIntentResult): { type: 'ALL' | 'BEST_SELLER' | 'PROMOTED' | 'NEEDS_REVIEW'; query: string } | null {
  if (result.intent === 'UNSUPPORTED') return null
  if (result.intent === 'SEARCH') return { type: 'ALL', query: result.query }
  return { type: ({ BEST_SELLERS: 'BEST_SELLER', PROMOTED: 'PROMOTED', NEEDS_REVIEW: 'NEEDS_REVIEW' } as const)[result.intent], query: '' }
}

export function removeProduct(section: StorefrontSectionDraft, productId: string) {
  return { ...section, productIds: section.productIds.filter(id => id !== productId) }
}

export function moveProduct(section: StorefrontSectionDraft, productId: string, offset: -1 | 1) {
  const index = section.productIds.indexOf(productId)
  const destination = index + offset
  if (index < 0 || destination < 0 || destination >= section.productIds.length) return section
  const productIds = [...section.productIds]
  ;[productIds[index], productIds[destination]] = [productIds[destination], productIds[index]]
  return { ...section, productIds }
}

export type DraftValidation = { key: string; position?: number }

export function validateDraft(sections: StorefrontSectionDraft[]): DraftValidation | null {
  if (!sections.length) return { key: 'Add at least one section before saving.' }
  if (sections.length > 8) return { key: 'HOME can contain at most 8 sections.' }
  if (sections.filter(section => section.type === 'HERO_BANNER').length > 1) return { key: 'HOME can contain only one hero banner.' }
  for (const [index, section] of sections.entries()) {
    const invalid = (key: string): DraftValidation => ({ key, position: index + 1 })
    if (!section.titleVi.trim() || !section.titleEn.trim() || !section.ctaLabelVi.trim() || !section.ctaLabelEn.trim()) return invalid('Section {position}: complete both titles and both CTA labels.')
    if (!!section.supportingTextVi?.trim() !== !!section.supportingTextEn?.trim()) return invalid('Section {position}: supporting text must be completed in both languages or left empty.')
    if (section.titleVi.length > 120 || section.titleEn.length > 120 || section.ctaLabelVi.length > 80 || section.ctaLabelEn.length > 80) return invalid('Section {position}: a title or CTA label is too long.')
    if ((section.supportingTextVi?.length ?? 0) > 500 || (section.supportingTextEn?.length ?? 0) > 500) return invalid('Section {position}: supporting text is too long.')
    if (section.type === 'HERO_BANNER' && !controlledMedia.has(section.mediaKey ?? '')) return invalid('Section {position}: choose a controlled media option.')
    if (section.type === 'PRODUCT_COLLECTION' && (section.productIds.length < 1 || section.productIds.length > 8)) return invalid('Section {position}: choose between 1 and 8 products.')
    if (new Set(section.productIds).size !== section.productIds.length) return invalid('Section {position}: a product can appear only once.')
    if (section.type === 'PROMOTION_SPOTLIGHT' && !section.promotionFamilyId) return invalid('Section {position}: choose a current promotion.')
    if (section.ctaKind === 'CATALOG_SEARCH' && !section.ctaSearchTerm?.trim()) return invalid('Section {position}: enter a catalog search term.')
    if (section.ctaKind === 'PRODUCT_DETAIL' && !section.ctaTargetPublicId) return invalid('Section {position}: choose a product for the CTA.')
    if (section.ctaKind === 'PROMOTION_DETAIL' && !section.ctaTargetPublicId && section.type !== 'PROMOTION_SPOTLIGHT') return invalid('Section {position}: choose a promotion for the CTA.')
  }
  return null
}

export function canPublish(draft: StorefrontRevision | null, dirty: boolean) {
  return !!draft && !dirty
}

export function canManageStorefront(account?: { permissions: string[] }) {
  return !!account?.permissions.includes('STOREFRONT_MANAGE')
}

export function storefrontCtaLocation(cta: StorefrontCustomerCta) {
  if (cta.kind === 'PRODUCT_DETAIL') return `/products/${encodeURIComponent(cta.targetPublicId ?? '')}`
  if (cta.kind === 'PROMOTION_DETAIL') return `/promotions/${encodeURIComponent(cta.targetPublicId ?? '')}`
  if (cta.kind === 'CATALOG_SEARCH') return { path: '/', query: { q: cta.searchTerm ?? '' }, hash: '#product-search' }
  return '/promotions'
}
