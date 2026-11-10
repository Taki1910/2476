<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import {
  api, ApiError, type MerchandisingCandidate, type MerchandisingCandidatePage,
  type MerchandisingAssistantAvailability, type MerchandisingCandidateType, type MerchandisingCopyField,
  type MerchandisingCopyLocale, type MerchandisingCopySuggestion, type MerchandisingIntentResult,
  type ProductSummary, type PublicOffer,
} from '../api'
import StorefrontSectionPreview from '../components/StorefrontSectionPreview.vue'
import { formatVnd } from '../format'
import { t } from '../i18n'
import {
  CTA_KINDS, MEDIA_OPTIONS, SECTION_TYPES, addProduct, addSection, applyAssistantCopy, applyCandidateToDraft, canPublish,
  candidateQueryForIntent, draftFingerprint, fromRevision, moveProduct, moveSection, removeProduct, removeSection, toSectionInput,
  toggleSection, validateDraft, type StorefrontCtaKind, type StorefrontManagementState,
  type StorefrontSectionDraft, type StorefrontSectionType,
} from '../merchandising'

const management = ref<StorefrontManagementState | null>(null)
const sections = ref<StorefrontSectionDraft[]>([])
const products = ref<ProductSummary[]>([])
const promotions = ref<PublicOffer[]>([])
const activeId = ref<string | null>(null)
const newType = ref<StorefrontSectionType>('HERO_BANNER')
const productQuery = ref('')
const candidateQuery = ref('')
const candidateFilter = ref<MerchandisingCandidateType>('ALL')
const candidatePage = ref<MerchandisingCandidatePage | null>(null)
const candidateLoading = ref(false)
const candidateError = ref('')
const candidateFeedback = ref('')
const candidateCollectionId = ref('')
const ignoredCandidateIds = ref(new Set<string>())
const savedFingerprint = ref('[]')
const operation = ref<'load' | 'clone' | 'save' | 'publish' | null>(null)
const error = ref('')
const success = ref('')
const conflict = ref(false)
const confirmDialog = ref<HTMLDialogElement>()
const pendingAction = ref<'reload' | 'publish'>()
const assistantAvailability = ref<MerchandisingAssistantAvailability>({ enabled: false, available: false, status: 'DISABLED' })
const assistantIntent = ref('')
const assistantIntentLoading = ref(false)
const assistantIntentError = ref('')
const assistantIntentResult = ref<MerchandisingIntentResult | null>(null)
const copyTarget = ref('VI:TITLE')
const copyInstruction = ref('Make the copy natural and clear.')
const copyLoading = ref(false)
const copyError = ref('')
const copySuggestion = ref<MerchandisingCopySuggestion | null>(null)
let intentRequestId = 0
let copyRequestId = 0

const dirty = computed(() => draftFingerprint(sections.value) !== savedFingerprint.value)
const activeSection = computed(() => sections.value.find(section => section.clientId === activeId.value) ?? null)
const filteredProducts = computed(() => {
  const query = productQuery.value.trim().toLocaleLowerCase()
  return products.value.filter(product => !query || `${product.name} ${product.category ?? ''} ${product.collection ?? ''}`.toLocaleLowerCase().includes(query))
})
const candidateFilters: { value: MerchandisingCandidateType; label: string }[] = [
  { value: 'ALL', label: 'All candidates' },
  { value: 'BEST_SELLER', label: 'Best sellers' },
  { value: 'PROMOTED', label: 'Promoted' },
  { value: 'NEEDS_REVIEW', label: 'Needs review' },
]
const copyTargets = [
  { value: 'VI:TITLE', label: 'VI title' }, { value: 'EN:TITLE', label: 'EN title' },
  { value: 'VI:SUPPORTING_TEXT', label: 'VI supporting text' }, { value: 'EN:SUPPORTING_TEXT', label: 'EN supporting text' },
  { value: 'VI:CTA_LABEL', label: 'VI CTA label' }, { value: 'EN:CTA_LABEL', label: 'EN CTA label' },
]
const copyInstructions = [
  { value: 'Make the copy natural and clear.', label: 'Make it more natural' },
  { value: 'Shorten the copy without losing meaning.', label: 'Make it shorter' },
  { value: 'Emphasize the active promotion, but only when the supplied evidence supports it.', label: 'Emphasize the promotion' },
  { value: 'Write a concise merchandising title using only the supplied evidence.', label: 'Write a title' },
  { value: 'Write the English version using only the supplied evidence.', label: 'Create an English version' },
]
const visibleCandidates = computed(() => candidatePage.value?.candidates.filter(candidate => !ignoredCandidateIds.value.has(candidate.productId)) ?? [])
const productCollections = computed(() => sections.value.filter(section => section.type === 'PRODUCT_COLLECTION'))
const canAdd = computed(() => sections.value.length < 8 && (newType.value !== 'HERO_BANNER' || !sections.value.some(section => section.type === 'HERO_BANNER')))
const confirmation = computed(() => pendingAction.value === 'publish'
  ? { title: 'Confirm publication', description: 'Publish this HOME draft? It will replace the customer-visible homepage.', action: 'Publish' }
  : { title: 'Discard local edits?', description: 'Reload the latest server draft? This will replace all unsaved local edits.', action: 'Reload latest and discard local edits' })

function problemCopy(reason: unknown) {
  if (!(reason instanceof ApiError)) return t('The service is temporarily unavailable. Please retry.')
  if (reason.status === 403) return t('You do not have permission to manage the storefront.')
  if (reason.code === 'STOREFRONT_REFERENCE_INVALID') return t('A selected product or promotion is no longer public. Refresh the available choices and review the draft.')
  if (reason.code.startsWith('STOREFRONT_')) return t('The storefront draft was rejected. Review every section and try again.')
  return t('The service is temporarily unavailable. Please retry.')
}

function assistantProblemCopy(reason: unknown) {
  if (reason instanceof ApiError && reason.status === 403) return t('You do not have permission to manage the storefront.')
  return t('AI assistance is temporarily unavailable. Your draft has not changed.')
}

async function load() {
  operation.value = 'load'; error.value = ''; success.value = ''; conflict.value = false
  try {
    const [state, catalog, offers, availability] = await Promise.all([
      api.storefrontManagement(), api.products(), loadAllPromotions(),
      api.merchandisingAssistantAvailability().catch(() => ({ enabled: true, available: false, status: 'NOT_CONFIGURED' as const })),
    ])
    management.value = state
    products.value = catalog
    promotions.value = offers
    assistantAvailability.value = availability
    sections.value = fromRevision(state.draft)
    savedFingerprint.value = draftFingerprint(sections.value)
    activeId.value = sections.value[0]?.clientId ?? null
    candidateCollectionId.value = productCollections.value[0]?.clientId ?? ''
    await loadCandidates()
    return true
  } catch (reason) { error.value = problemCopy(reason); return false }
  finally { operation.value = null }
}

async function loadCandidates(page = 0) {
  candidateLoading.value = true; candidateError.value = ''
  try {
    candidatePage.value = await api.storefrontCandidates({
      query: candidateQuery.value.trim(), type: candidateFilter.value, page, size: 12,
    })
  } catch (reason) {
    candidateError.value = problemCopy(reason)
  } finally { candidateLoading.value = false }
}

async function setCandidateFilter(filter: MerchandisingCandidateType) {
  candidateFilter.value = filter
  await loadCandidates()
}

async function interpretIntent() {
  const request = assistantIntent.value.trim()
  if (!assistantAvailability.value.available || !request || assistantIntentLoading.value) return
  const requestId = ++intentRequestId
  assistantIntentLoading.value = true; assistantIntentError.value = ''; assistantIntentResult.value = null
  try {
    const result = await api.interpretMerchandisingIntent(request)
    if (requestId !== intentRequestId) return
    assistantIntentResult.value = result
    const query = candidateQueryForIntent(result)
    if (!query) return
    candidateFilter.value = query.type
    candidateQuery.value = query.query
    await loadCandidates(0)
  } catch (reason) {
    if (requestId === intentRequestId) assistantIntentError.value = assistantProblemCopy(reason)
  } finally {
    if (requestId === intentRequestId) assistantIntentLoading.value = false
  }
}

async function loadAllPromotions() {
  const offers: PublicOffer[] = []
  for (let page = 0; ; page++) {
    const result = await api.publicOffers(page, 100)
    offers.push(...result.items)
    if (!result.hasNext) return offers
  }
}

function create() {
  if (operation.value || !canAdd.value) return
  sections.value = addSection(sections.value, newType.value)
  activeId.value = sections.value.at(-1)?.clientId ?? null
  if (newType.value === 'PRODUCT_COLLECTION') candidateCollectionId.value = activeId.value ?? ''
  error.value = ''; success.value = ''
}

function selectSection(clientId: string) {
  activeId.value = clientId
  copySuggestion.value = null
  copyError.value = ''
}

function remove(clientId: string) {
  if (operation.value) return
  sections.value = removeSection(sections.value, clientId)
  if (activeId.value === clientId) activeId.value = sections.value[0]?.clientId ?? null
}

function copySelection() {
  const [locale, field] = copyTarget.value.split(':') as [MerchandisingCopyLocale, MerchandisingCopyField]
  return { locale, field }
}

function currentCopy(section: StorefrontSectionDraft, locale: MerchandisingCopyLocale, field: MerchandisingCopyField) {
  if (field === 'TITLE') return locale === 'VI' ? section.titleVi : section.titleEn
  if (field === 'SUPPORTING_TEXT') return (locale === 'VI' ? section.supportingTextVi : section.supportingTextEn) ?? ''
  return locale === 'VI' ? section.ctaLabelVi : section.ctaLabelEn
}

function copyEvidence(section: StorefrontSectionDraft) {
  if (section.type === 'PRODUCT_COLLECTION') return section.productIds
  return section.ctaKind === 'PRODUCT_DETAIL' && section.ctaTargetPublicId ? [section.ctaTargetPublicId] : []
}

async function requestCopy() {
  const section = activeSection.value
  if (!section || !assistantAvailability.value.available || copyLoading.value) return
  const requestId = ++copyRequestId
  const { locale, field } = copySelection()
  copyLoading.value = true; copyError.value = ''; copySuggestion.value = null
  try {
    const suggestion = await api.rewriteMerchandisingCopy({
      locale, field, currentText: currentCopy(section, locale, field), instruction: copyInstruction.value,
      sectionPurpose: `${section.type}: ${section.titleVi || section.titleEn}`,
      productIds: copyEvidence(section).slice(0, 8),
      promotionFamilyId: section.promotionFamilyId ?? (section.ctaKind === 'PROMOTION_DETAIL' ? section.ctaTargetPublicId : null),
    })
    if (requestId === copyRequestId && activeId.value === section.clientId) copySuggestion.value = suggestion
  } catch (reason) {
    if (requestId === copyRequestId) copyError.value = assistantProblemCopy(reason)
  } finally {
    if (requestId === copyRequestId) copyLoading.value = false
  }
}

function useCopySuggestion(value: string) {
  const section = activeSection.value
  if (!section) return
  const { locale, field } = copySelection()
  sections.value = applyAssistantCopy(sections.value, section.clientId, locale, field, value)
  copySuggestion.value = null
}

function cancelCopySuggestion() {
  copySuggestion.value = null
  copyError.value = ''
}

function move(clientId: string, offset: -1 | 1) { if (operation.value) return; sections.value = moveSection(sections.value, clientId, offset) }
function toggle(clientId: string) { if (operation.value) return; sections.value = toggleSection(sections.value, clientId) }

function replaceSection(next: StorefrontSectionDraft) {
  if (operation.value) return
  sections.value = sections.value.map(section => section.clientId === next.clientId ? next : section)
}

function changeCta(section: StorefrontSectionDraft, event: Event) {
  const ctaKind = (event.target as HTMLSelectElement).value as StorefrontCtaKind
  replaceSection({ ...section, ctaKind, ctaTargetPublicId: null, ctaSearchTerm: ctaKind === 'CATALOG_SEARCH' ? '' : null })
}

function changePromotion(section: StorefrontSectionDraft, event: Event) {
  const promotionFamilyId = (event.target as HTMLSelectElement).value || null
  replaceSection({ ...section, promotionFamilyId, ctaTargetPublicId: promotionFamilyId })
}

function addCollectionProduct(section: StorefrontSectionDraft, productId: string) { replaceSection(addProduct(section, productId)) }
function removeCollectionProduct(section: StorefrontSectionDraft, productId: string) { replaceSection(removeProduct(section, productId)) }
function moveCollectionProduct(section: StorefrontSectionDraft, productId: string, offset: -1 | 1) { replaceSection(moveProduct(section, productId, offset)) }

function targetCollectionId() {
  return productCollections.value.some(section => section.clientId === candidateCollectionId.value)
    ? candidateCollectionId.value : productCollections.value[0]?.clientId ?? ''
}

function applyRecommendation(candidate: MerchandisingCandidate) {
  const target = targetCollectionId()
  const result = applyCandidateToDraft(sections.value, target, candidate.productId)
  sections.value = result.sections
  if (result.outcome === 'APPLIED') {
    activeId.value = target
    candidateCollectionId.value = target
  }
  candidateFeedback.value = t({
    APPLIED: 'Added {product} to the local draft.',
    ALREADY_PRESENT: '{product} is already in this collection.',
    COLLECTION_FULL: 'This collection already has 8 products.',
    COLLECTION_NOT_FOUND: 'Add a product collection before applying a recommendation.',
  }[result.outcome], { product: candidate.name })
}

function ignoreRecommendation(productId: string) {
  ignoredCandidateIds.value = new Set(ignoredCandidateIds.value).add(productId)
  candidateFeedback.value = t('Recommendation hidden for this editing session only.')
}

function collectionContains(productId: string) {
  return productCollections.value.find(section => section.clientId === targetCollectionId())?.productIds.includes(productId) ?? false
}

async function clonePublished() {
  const published = management.value?.published
  if (operation.value || management.value?.draft || !published) return
  operation.value = 'clone'; error.value = ''; success.value = ''; conflict.value = false
  try {
    const draft = await api.cloneStorefrontPublished({ expectedPublishedRevisionId: published.revisionId })
    management.value = { draft, published }
    sections.value = fromRevision(draft)
    savedFingerprint.value = draftFingerprint(sections.value)
    activeId.value = sections.value[0]?.clientId ?? null
    candidateCollectionId.value = productCollections.value[0]?.clientId ?? ''
    success.value = t('Draft revision {revision} cloned from the current publication.', { revision: draft.revisionNumber })
    await loadCandidates(candidatePage.value?.page ?? 0)
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 409 && reason.code === 'STOREFRONT_DRAFT_CONFLICT') {
      conflict.value = true
      error.value = t('The publication changed or another draft was created. Reload the latest state before cloning.')
    } else error.value = problemCopy(reason)
  } finally { operation.value = null }
}

async function save() {
  if (operation.value) return
  const validation = validateDraft(sections.value)
  if (validation) { error.value = t(validation.key, validation.position ? { position: validation.position } : {}); success.value = ''; return }
  operation.value = 'save'; error.value = ''; success.value = ''; conflict.value = false
  try {
    const draft = await api.saveStorefrontDraft({
      expectedDraftRevisionId: management.value?.draft?.revisionId ?? null,
      expectedEntityVersion: management.value?.draft?.entityVersion ?? null,
      sections: sections.value.map(toSectionInput),
    })
    management.value = { draft, published: management.value?.published ?? null }
    sections.value = fromRevision(draft)
    savedFingerprint.value = draftFingerprint(sections.value)
    activeId.value = sections.value[0]?.clientId ?? null
    success.value = t('Draft revision {revision} saved.', { revision: draft.revisionNumber })
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 409 && reason.code === 'STOREFRONT_DRAFT_CONFLICT') {
      conflict.value = true
      error.value = t('The server draft changed while you were editing. Your local edits are still here.')
    } else error.value = problemCopy(reason)
  } finally { operation.value = null }
}

function reloadLatest() {
  if (operation.value) return
  pendingAction.value = 'reload'
  confirmDialog.value?.showModal()
}

async function publish() {
  if (operation.value) return
  if (dirty.value) { error.value = t('Save your local changes before publishing.'); return }
  const draft = management.value?.draft
  if (!canPublish(draft ?? null, dirty.value)) { error.value = t('Save a valid draft before publishing.'); return }
  pendingAction.value = 'publish'
  confirmDialog.value?.showModal()
}

async function confirmAction() {
  const action = pendingAction.value
  pendingAction.value = undefined
  confirmDialog.value?.close()
  if (action === 'reload') { await load(); return }
  if (action !== 'publish') return
  const draft = management.value?.draft
  if (!draft) { error.value = t('Save a valid draft before publishing.'); return }
  operation.value = 'publish'; error.value = ''; success.value = ''; conflict.value = false
  try {
    const published = await api.publishStorefrontDraft({
      expectedDraftRevisionId: draft!.revisionId,
      expectedEntityVersion: draft!.entityVersion,
      expectedPublishedRevisionId: management.value?.published?.revisionId ?? null,
    })
    management.value = { draft: null, published }
    sections.value = []
    savedFingerprint.value = '[]'
    activeId.value = null
    await load()
    success.value = t('Published revision {revision}.', { revision: published.revisionNumber })
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 409 && reason.code === 'STOREFRONT_DRAFT_CONFLICT') {
      conflict.value = true
      error.value = t('The server draft or publication changed. Reload the latest state before publishing.')
    } else error.value = problemCopy(reason)
  } finally { operation.value = null }
}

function closeDialog() {
  pendingAction.value = undefined
  confirmDialog.value?.close()
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => { window.addEventListener('beforeunload', beforeUnload); void load() })
onBeforeUnmount(() => window.removeEventListener('beforeunload', beforeUnload))
</script>

<template>
  <div class="storefront-management-page">
    <header class="storefront-management-heading">
      <div><h1>{{ t('Storefront Management') }}</h1></div>
      <p>{{ t('Arrange the complete local draft, review its preview, then save and publish explicitly.') }}</p>
    </header>

    <section class="storefront-status" aria-labelledby="storefront-status-title" :aria-busy="!!operation">
      <h2 id="storefront-status-title">{{ t('Publication status') }}</h2>
      <dl>
        <div><dt>{{ t('Published revision') }}</dt><dd>{{ management?.published ? `#${management.published.revisionNumber}` : t('None') }}</dd></div>
        <div><dt>{{ t('Draft revision') }}</dt><dd>{{ management?.draft ? `#${management.draft.revisionNumber} · v${management.draft.entityVersion}` : t('None') }}</dd></div>
        <div><dt>{{ t('Last publication') }}</dt><dd>{{ management?.published?.publishedAt ? new Date(management.published.publishedAt).toLocaleString() : t('Not published yet') }}</dd></div>
        <div><dt>{{ t('Editor state') }}</dt><dd :data-dirty="dirty">{{ t(dirty ? 'Unsaved changes' : 'Saved') }}</dd></div>
      </dl>
      <div class="storefront-commit-actions">
        <button v-if="!management?.draft && management?.published" class="secondary-button" type="button" :disabled="!!operation" @click="clonePublished">{{ t(operation === 'clone' ? 'Cloning…' : 'Create draft from live page') }}</button>
        <button class="secondary-button" type="button" :disabled="!!operation" @click="save">{{ t(operation === 'save' ? 'Saving…' : 'Save Draft') }}</button>
        <button class="primary-button" type="button" :disabled="!!operation || !management?.draft || dirty" @click="publish">{{ t(operation === 'publish' ? 'Publishing…' : 'Publish') }}</button>
      </div>
    </section>

    <p v-if="error" class="form-error storefront-message" role="alert">{{ error }}</p>
    <div v-if="conflict" class="storefront-conflict" role="alert">
      <strong>{{ t('Conflict detected') }}</strong>
      <p>{{ t('Reloading gets the latest server draft and replaces your unsaved local edits.') }}</p>
      <button type="button" @click="reloadLatest">{{ t('Reload latest and discard local edits') }}</button>
    </div>
    <p v-if="success" class="storefront-success" role="status">{{ success }}</p>
    <section v-if="operation === 'load'" class="queue-loading" role="status" aria-live="polite"><span class="loader-mark"></span>{{ t('Loading storefront management…') }}</section>
    <section v-else-if="!management" class="inline-state">
      <h2>{{ t('Storefront management is unavailable') }}</h2><button type="button" @click="load">{{ t('Retry') }}</button>
    </section>

    <section v-if="management" class="storefront-recommendations" aria-labelledby="storefront-recommendations-title">
      <header class="storefront-recommendations-heading">
        <div>
          <h2 id="storefront-recommendations-title">{{ t('Product discovery') }}</h2>
          <p>{{ t('Use product evidence to choose what belongs on the homepage. Recommendations change only this local draft.') }}</p>
        </div>
        <label>{{ t('Apply to collection') }}
          <select v-model="candidateCollectionId" :disabled="!!operation || !productCollections.length">
            <option value="" disabled>{{ t('Choose a product collection') }}</option>
            <option v-for="(section, index) in productCollections" :key="section.clientId" :value="section.clientId">{{ index + 1 }} · {{ section.titleVi || section.titleEn || t('Untitled section') }}</option>
          </select>
        </label>
      </header>

      <form class="storefront-ai-intent" @submit.prevent="interpretIntent">
        <div>
          <span class="field-help">{{ t('AI-assisted intent') }}</span>
          <label for="storefront-manager-intent">{{ t('Describe the products you want to review') }}</label>
          <p>{{ t('The assistant only translates your request into the existing evidence filters.') }}</p>
        </div>
        <input id="storefront-manager-intent" v-model="assistantIntent" type="text" autocomplete="off" maxlength="300" :disabled="!assistantAvailability.available || assistantIntentLoading" :placeholder="t('For example: show best sellers or trail shoes')">
        <button type="submit" :disabled="!assistantAvailability.available || assistantIntentLoading || !assistantIntent.trim()">{{ t(assistantIntentLoading ? 'Interpreting…' : 'Interpret request') }}</button>
        <p v-if="!assistantAvailability.available" class="storefront-ai-unavailable">{{ t('AI assistance is unavailable. Manual search and filters still work.') }}</p>
        <p v-if="assistantIntentError" class="form-error" role="alert">{{ assistantIntentError }}</p>
        <p v-else-if="assistantIntentResult" class="storefront-ai-result" aria-live="polite">
          <strong>{{ t(assistantIntentResult.intent) }}</strong> · {{ assistantIntentResult.explanation }}
        </p>
      </form>

      <form class="storefront-candidate-search" role="search" @submit.prevent="loadCandidates(0)">
        <label>{{ t('Search products by name, category, or collection') }}
          <input v-model="candidateQuery" type="search" autocomplete="off" maxlength="80">
        </label>
        <button type="submit" :disabled="candidateLoading">{{ t(candidateLoading ? 'Searching…' : 'Search') }}</button>
      </form>

      <div class="storefront-candidate-filters" role="group" :aria-label="t('Recommendation filters')">
        <button v-for="filter in candidateFilters" :key="filter.value" type="button" :aria-pressed="candidateFilter === filter.value" :disabled="candidateLoading" @click="setCandidateFilter(filter.value)">{{ t(filter.label) }}</button>
      </div>

      <p class="storefront-candidate-feedback" aria-live="polite">{{ candidateFeedback }}</p>
      <p v-if="candidateError" class="form-error" role="alert">{{ candidateError }} <button type="button" @click="loadCandidates(candidatePage?.page ?? 0)">{{ t('Retry') }}</button></p>
      <div v-else-if="candidateLoading" class="queue-loading" role="status"><span class="loader-mark"></span>{{ t('Loading product evidence…') }}</div>
      <div v-else-if="!visibleCandidates.length" class="storefront-empty">
        <h3>{{ t('No candidates match these filters') }}</h3>
        <p>{{ t('Try another search or recommendation filter.') }}</p>
      </div>

      <ol v-else class="storefront-candidate-list">
        <li v-for="candidate in visibleCandidates" :key="candidate.productId">
          <div class="storefront-candidate-media">
            <img v-if="candidate.imageUrl" :src="candidate.imageUrl" :alt="candidate.name" loading="lazy">
            <span v-else>{{ t('No product image') }}</span>
          </div>
          <div class="storefront-candidate-body">
            <header>
              <div><h3>{{ candidate.name }}</h3><p>{{ [candidate.category, candidate.collection].filter(Boolean).join(' · ') || '—' }}</p></div>
              <span :data-sellability="candidate.sellability">{{ t(candidate.sellability) }}</span>
            </header>
            <dl class="storefront-candidate-facts">
              <div><dt>{{ t('Available') }}</dt><dd>{{ candidate.aggregateAvailable }}</dd></div>
              <div><dt>{{ t('30-day sales') }}</dt><dd>{{ candidate.sales.units30d }} {{ t('pairs') }}</dd></div>
              <div><dt>{{ t('Gross sales before discounts') }}</dt><dd>{{ formatVnd(candidate.sales.grossSalesValue30d) }}</dd></div>
              <div><dt>{{ t('Colors') }}</dt><dd>{{ candidate.colors.join(', ') || '—' }}</dd></div>
            </dl>
            <div class="storefront-candidate-evidence">
              <section>
                <h4>{{ t('Product promotions') }}</h4>
                <p v-if="!candidate.promotions.length">{{ t('None') }}</p>
                <ul v-else><li v-for="promotion in candidate.promotions" :key="promotion.revisionId"><strong>{{ promotion.name }}</strong><span :data-promotion-state="promotion.state">{{ t(promotion.state === 'ACTIVE' ? 'Active' : 'Scheduled') }}</span><small>{{ promotion.percentageValue != null ? `${promotion.percentageValue}%` : promotion.fixedAmount != null ? formatVnd(promotion.fixedAmount) : t(promotion.effectType) }}</small></li></ul>
              </section>
              <section>
                <h4>{{ t('Current placement') }}</h4>
                <p v-if="!candidate.publishedPlacements.length && !candidate.draftPlacements.length">{{ t('Not placed on HOME') }}</p>
                <ul v-else>
                  <li v-for="placement in candidate.publishedPlacements" :key="`published-${placement.sectionId}`"><strong>{{ t('Published') }}</strong><span>{{ placement.titleVi || placement.titleEn }}</span></li>
                  <li v-for="placement in candidate.draftPlacements" :key="`draft-${placement.sectionId}`"><strong>{{ t('Draft') }}</strong><span>{{ placement.titleVi || placement.titleEn }}</span></li>
                </ul>
              </section>
              <section>
                <h4>{{ t('Readiness warnings') }}</h4>
                <p v-if="!candidate.warnings.length">{{ t('None') }}</p>
                <ul v-else><li v-for="warning in candidate.warnings" :key="warning.code"><strong>{{ t(warning.code) }}</strong><span>{{ t(warning.classification) }}</span></li></ul>
              </section>
            </div>
            <p v-if="!candidate.sales.complete" class="storefront-candidate-caution">{{ t('Bestseller evidence is incomplete because a partial item void cannot be converted into exact units.') }}</p>
          </div>
          <div class="storefront-candidate-actions">
            <button class="primary-button" type="button" :disabled="!!operation || candidate.sellability !== 'SELLABLE' || !targetCollectionId() || collectionContains(candidate.productId)" @click="applyRecommendation(candidate)">{{ t(collectionContains(candidate.productId) ? 'Already in draft' : 'Add to draft') }}</button>
            <button class="text-button" type="button" @click="ignoreRecommendation(candidate.productId)">{{ t('Ignore for now') }}</button>
          </div>
        </li>
      </ol>

      <nav v-if="candidatePage && candidatePage.totalPages > 1" class="storefront-candidate-pagination" :aria-label="t('Candidate pages')">
        <button type="button" :disabled="candidateLoading || candidatePage.page === 0" @click="loadCandidates(candidatePage.page - 1)">{{ t('Previous') }}</button>
        <span>{{ t('Page {page} of {pages}', { page: candidatePage.page + 1, pages: candidatePage.totalPages }) }}</span>
        <button type="button" :disabled="candidateLoading || candidatePage.page + 1 >= candidatePage.totalPages" @click="loadCandidates(candidatePage.page + 1)">{{ t('Next') }}</button>
      </nav>
    </section>

    <div v-if="management" class="storefront-workbench">
      <section class="storefront-editor" aria-labelledby="storefront-editor-title">
        <div class="storefront-section-heading">
          <div><h2 id="storefront-editor-title">{{ t('Ordered sections') }}</h2></div>
          <p>{{ t('{count} of 8 sections', { count: sections.length }) }}</p>
        </div>

        <div class="storefront-add-row">
          <label>{{ t('Section type') }}
            <select v-model="newType" :disabled="!!operation">
              <option v-for="type in SECTION_TYPES" :key="type" :value="type">{{ t(type) }}</option>
            </select>
          </label>
          <button type="button" :disabled="!!operation || !canAdd" @click="create">{{ t('Add section') }}</button>
          <small v-if="!canAdd">{{ t(sections.length >= 8 ? 'The 8-section limit is reached.' : 'HOME can contain only one hero banner.') }}</small>
        </div>

        <div v-if="!sections.length" class="storefront-empty">
          <h3>{{ t('No sections in this local draft') }}</h3>
          <p>{{ t('Choose one of the three approved section types to begin.') }}</p>
        </div>

        <ol v-else class="storefront-section-list">
          <li v-for="(section, index) in sections" :key="section.clientId" :data-active="activeId === section.clientId">
            <button class="storefront-section-select" type="button" @click="selectSection(section.clientId)">
              <span>{{ String(index + 1).padStart(2, '0') }}</span>
              <strong>{{ t(section.type) }}</strong>
              <small>{{ section.titleVi || section.titleEn || t('Untitled section') }}</small>
              <b>{{ t(section.enabled ? 'Enabled' : 'Disabled') }}</b>
            </button>
            <div class="storefront-row-actions">
              <button type="button" :aria-label="t('Edit section {position}', { position: index + 1 })" @click="selectSection(section.clientId)">{{ t('Edit') }}</button>
              <button type="button" :aria-label="t('Move section {position} up', { position: index + 1 })" :disabled="!!operation || index === 0" @click="move(section.clientId, -1)">↑ {{ t('Up') }}</button>
              <button type="button" :aria-label="t('Move section {position} down', { position: index + 1 })" :disabled="!!operation || index === sections.length - 1" @click="move(section.clientId, 1)">↓ {{ t('Down') }}</button>
              <button type="button" :disabled="!!operation" @click="toggle(section.clientId)">{{ t(section.enabled ? 'Disable' : 'Enable') }}</button>
              <button class="danger-button" type="button" :disabled="!!operation" @click="remove(section.clientId)">{{ t('Remove') }}</button>
            </div>
          </li>
        </ol>

        <form v-if="activeSection" class="storefront-section-form" @submit.prevent>
          <header><div><span class="field-help">{{ t(activeSection.type) }}</span><h2>{{ t('Edit section') }}</h2></div><label class="check-row"><input v-model="activeSection.enabled" type="checkbox" :disabled="!!operation">{{ t('Enabled in publication') }}</label></header>
          <div class="storefront-bilingual-grid">
            <label>{{ t('VI title') }}<input v-model="activeSection.titleVi" :disabled="!!operation" required maxlength="120"></label>
            <label>{{ t('EN title') }}<input v-model="activeSection.titleEn" :disabled="!!operation" required maxlength="120"></label>
            <label>{{ t('VI supporting text') }}<textarea v-model="activeSection.supportingTextVi" :disabled="!!operation" maxlength="500"></textarea></label>
            <label>{{ t('EN supporting text') }}<textarea v-model="activeSection.supportingTextEn" :disabled="!!operation" maxlength="500"></textarea></label>
            <label>{{ t('VI CTA label') }}<input v-model="activeSection.ctaLabelVi" :disabled="!!operation" required maxlength="80"></label>
            <label>{{ t('EN CTA label') }}<input v-model="activeSection.ctaLabelEn" :disabled="!!operation" required maxlength="80"></label>
          </div>

          <fieldset class="storefront-ai-copy" :disabled="!!operation || !assistantAvailability.available || copyLoading">
            <legend>{{ t('AI copy assistant') }}</legend>
            <p>{{ t('Suggestions use selected product and promotion evidence. Nothing changes until you choose Use this suggestion.') }}</p>
            <div class="storefront-ai-copy-controls">
              <label>{{ t('Target field') }}
                <select v-model="copyTarget" @change="cancelCopySuggestion"><option v-for="target in copyTargets" :key="target.value" :value="target.value">{{ t(target.label) }}</option></select>
              </label>
              <label>{{ t('Copy task') }}
                <select v-model="copyInstruction" @change="cancelCopySuggestion"><option v-for="instruction in copyInstructions" :key="instruction.value" :value="instruction.value">{{ t(instruction.label) }}</option></select>
              </label>
              <button type="button" @click="requestCopy">{{ t(copyLoading ? 'Drafting…' : 'Suggest copy') }}</button>
            </div>
            <p v-if="!assistantAvailability.available" class="storefront-ai-unavailable">{{ t('AI assistance is unavailable. You can continue editing every field manually.') }}</p>
            <p v-if="copyError" class="form-error" role="alert">{{ copyError }}</p>
            <div v-else-if="copySuggestion" class="storefront-ai-copy-preview" aria-live="polite">
              <span class="field-help">{{ t('Draft suggestion') }}</span>
              <p>{{ copySuggestion.primary }}</p>
              <button type="button" @click="useCopySuggestion(copySuggestion.primary)">{{ t('Use this suggestion') }}</button>
              <ul v-if="copySuggestion.alternatives.length">
                <li v-for="alternative in copySuggestion.alternatives" :key="alternative"><span>{{ alternative }}</span><button type="button" @click="useCopySuggestion(alternative)">{{ t('Use this suggestion') }}</button></li>
              </ul>
              <small v-if="copySuggestion.notes">{{ copySuggestion.notes }}</small>
              <div class="storefront-ai-copy-actions"><button type="button" @click="requestCopy">{{ t('Try again') }}</button><button class="text-button" type="button" @click="cancelCopySuggestion">{{ t('Cancel') }}</button></div>
            </div>
          </fieldset>

          <fieldset v-if="activeSection.type === 'HERO_BANNER'" class="storefront-media-options">
            <legend>{{ t('Controlled media') }}</legend>
            <label v-for="([key, url], index) in MEDIA_OPTIONS" :key="key" :data-selected="activeSection.mediaKey === key">
              <input v-model="activeSection.mediaKey" type="radio" :value="key" :disabled="!!operation"><img :src="url" alt=""><span>{{ index + 1 }} · {{ t(key) }}</span>
            </label>
          </fieldset>

          <fieldset v-if="activeSection.type === 'PRODUCT_COLLECTION'" class="storefront-product-selector">
            <legend>{{ t('Curated products') }} · {{ activeSection.productIds.length }}/8</legend>
            <label>{{ t('Search valid products') }}<input v-model="productQuery" type="search" autocomplete="off"></label>
            <ul v-if="filteredProducts.length" class="storefront-product-results">
              <li v-for="product in filteredProducts" :key="product.id"><img :src="product.primaryImage || product.heroImage || '/products/placeholder.svg'" :alt="product.name" loading="lazy"><span><strong>{{ product.name }}</strong><small>{{ product.category || product.collection || '—' }} · {{ product.availableVariantCount ? t('Available') : t('Unavailable') }}</small></span><button type="button" :disabled="!!operation || activeSection.productIds.includes(product.id) || activeSection.productIds.length >= 8" @click="addCollectionProduct(activeSection, product.id)">{{ t(activeSection.productIds.includes(product.id) ? 'Selected' : 'Add') }}</button></li>
            </ul>
            <p v-else class="storefront-selector-empty">{{ t('No products match this search.') }}</p>
            <ol v-if="activeSection.productIds.length" class="storefront-selected-products">
              <li v-for="(productId, index) in activeSection.productIds" :key="productId"><strong>{{ products.find(product => product.id === productId)?.name || t('Unavailable product') }}</strong><span><button type="button" :disabled="!!operation || index === 0" @click="moveCollectionProduct(activeSection, productId, -1)">↑ {{ t('Up') }}</button><button type="button" :disabled="!!operation || index === activeSection.productIds.length - 1" @click="moveCollectionProduct(activeSection, productId, 1)">↓ {{ t('Down') }}</button><button type="button" :disabled="!!operation" @click="removeCollectionProduct(activeSection, productId)">{{ t('Remove') }}</button></span></li>
            </ol>
          </fieldset>

          <label v-if="activeSection.type === 'PROMOTION_SPOTLIGHT'">{{ t('Current promotion') }}
            <select :value="activeSection.promotionFamilyId || ''" :disabled="!!operation" required @change="changePromotion(activeSection, $event)"><option value="" disabled>{{ t('Choose a promotion') }}</option><option v-for="offer in promotions" :key="offer.familyId" :value="offer.familyId">{{ offer.name }} · {{ t('Active') }} · {{ offer.acquisitionMode }}</option></select>
            <small v-if="!promotions.length">{{ t('No current public promotions are available.') }}</small>
          </label>

          <fieldset class="storefront-cta-fields">
            <legend>{{ t('Call to action') }}</legend>
            <label>{{ t('CTA type') }}
               <select v-if="activeSection.type !== 'PROMOTION_SPOTLIGHT'" :value="activeSection.ctaKind" :disabled="!!operation" @change="changeCta(activeSection, $event)"><option v-for="kind in CTA_KINDS" :key="kind" :value="kind">{{ t(kind) }}</option></select>
              <input v-else :value="t('PROMOTION_DETAIL')" disabled>
            </label>
            <label v-if="activeSection.ctaKind === 'PRODUCT_DETAIL'">{{ t('CTA product') }}<select v-model="activeSection.ctaTargetPublicId" :disabled="!!operation" required><option :value="null" disabled>{{ t('Choose a product') }}</option><option v-for="product in products" :key="product.id" :value="product.id">{{ product.name }} · {{ product.category || product.collection || '—' }}</option></select></label>
            <label v-else-if="activeSection.ctaKind === 'PROMOTION_DETAIL' && activeSection.type !== 'PROMOTION_SPOTLIGHT'">{{ t('CTA promotion') }}<select v-model="activeSection.ctaTargetPublicId" :disabled="!!operation" required><option :value="null" disabled>{{ t('Choose a promotion') }}</option><option v-for="offer in promotions" :key="offer.familyId" :value="offer.familyId">{{ offer.name }} · {{ t('Active') }}</option></select></label>
            <label v-else-if="activeSection.ctaKind === 'CATALOG_SEARCH'">{{ t('Catalog search term') }}<input v-model="activeSection.ctaSearchTerm" :disabled="!!operation" required maxlength="80"></label>
            <p v-else>{{ t('This CTA opens the public offers list.') }}</p>
          </fieldset>
        </form>
      </section>

      <aside class="storefront-preview" aria-labelledby="storefront-preview-title">
        <div class="storefront-preview-heading"><div><span class="field-help">{{ t('Local only') }}</span><h2 id="storefront-preview-title">{{ t('Draft preview') }}</h2></div><span>{{ t('Preview language follows the app language.') }}</span></div>
        <div v-if="sections.length" class="storefront-preview-canvas">
          <StorefrontSectionPreview v-for="section in sections" :key="section.clientId" :section="section" :products="products" :promotions="promotions" />
        </div>
        <div v-else class="storefront-empty"><p>{{ t('Add a section to build the local preview.') }}</p></div>
      </aside>
    </div>

    <dialog ref="confirmDialog" class="terminal-dialog" aria-labelledby="storefront-dialog-title" aria-describedby="storefront-dialog-description" @cancel="closeDialog">
      <form method="dialog" @submit.prevent>
        <h2 id="storefront-dialog-title">{{ t(confirmation.title) }}</h2>
        <p id="storefront-dialog-description">{{ t(confirmation.description) }}</p>
        <div><button class="text-button" type="button" @click="closeDialog">{{ t('Keep editing') }}</button><button class="primary-button" type="button" @click="confirmAction">{{ t(confirmation.action) }}</button></div>
      </form>
    </dialog>
  </div>
</template>
