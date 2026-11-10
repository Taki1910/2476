<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, ApiError, type ProductSummary, type PublicOffer } from '../api'
import StorefrontSectionPreview from '../components/StorefrontSectionPreview.vue'
import { t } from '../i18n'
import {
  CTA_KINDS, MEDIA_OPTIONS, SECTION_TYPES, addProduct, addSection, canPublish, draftFingerprint,
  fromRevision, moveProduct, moveSection, removeProduct, removeSection, toSectionInput, toggleSection,
  validateDraft, type StorefrontCtaKind, type StorefrontManagementState, type StorefrontSectionDraft,
  type StorefrontSectionType,
} from '../merchandising'

const management = ref<StorefrontManagementState | null>(null)
const sections = ref<StorefrontSectionDraft[]>([])
const products = ref<ProductSummary[]>([])
const promotions = ref<PublicOffer[]>([])
const activeId = ref<string | null>(null)
const newType = ref<StorefrontSectionType>('HERO_BANNER')
const productQuery = ref('')
const savedFingerprint = ref('[]')
const operation = ref<'load' | 'save' | 'publish' | null>(null)
const error = ref('')
const success = ref('')
const conflict = ref(false)

const dirty = computed(() => draftFingerprint(sections.value) !== savedFingerprint.value)
const activeSection = computed(() => sections.value.find(section => section.clientId === activeId.value) ?? null)
const filteredProducts = computed(() => {
  const query = productQuery.value.trim().toLocaleLowerCase()
  return products.value.filter(product => !query || `${product.name} ${product.category ?? ''} ${product.collection ?? ''}`.toLocaleLowerCase().includes(query))
})
const canAdd = computed(() => sections.value.length < 8 && (newType.value !== 'HERO_BANNER' || !sections.value.some(section => section.type === 'HERO_BANNER')))

function problemCopy(reason: unknown) {
  if (!(reason instanceof ApiError)) return t('The service is temporarily unavailable. Please retry.')
  if (reason.status === 403) return t('You do not have permission to manage the storefront.')
  if (reason.code === 'STOREFRONT_REFERENCE_INVALID') return t('A selected product or promotion is no longer public. Refresh the available choices and review the draft.')
  if (reason.code.startsWith('STOREFRONT_')) return t('The storefront draft was rejected. Review every section and try again.')
  return t('The service is temporarily unavailable. Please retry.')
}

async function load() {
  operation.value = 'load'; error.value = ''; success.value = ''; conflict.value = false
  try {
    const [state, catalog, offers] = await Promise.all([api.storefrontManagement(), api.products(), loadAllPromotions()])
    management.value = state
    products.value = catalog
    promotions.value = offers
    sections.value = fromRevision(state.draft)
    savedFingerprint.value = draftFingerprint(sections.value)
    activeId.value = sections.value[0]?.clientId ?? null
    return true
  } catch (reason) { error.value = problemCopy(reason); return false }
  finally { operation.value = null }
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
  if (!canAdd.value) return
  sections.value = addSection(sections.value, newType.value)
  activeId.value = sections.value.at(-1)?.clientId ?? null
  error.value = ''; success.value = ''
}

function remove(clientId: string) {
  sections.value = removeSection(sections.value, clientId)
  if (activeId.value === clientId) activeId.value = sections.value[0]?.clientId ?? null
}

function move(clientId: string, offset: -1 | 1) { sections.value = moveSection(sections.value, clientId, offset) }
function toggle(clientId: string) { sections.value = toggleSection(sections.value, clientId) }

function replaceSection(next: StorefrontSectionDraft) {
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

async function reloadLatest() {
  if (!window.confirm(t('Reload the latest server draft? This will replace all unsaved local edits.'))) return
  await load()
}

async function publish() {
  if (operation.value) return
  if (dirty.value) { error.value = t('Save your local changes before publishing.'); return }
  const draft = management.value?.draft
  if (!canPublish(draft ?? null, dirty.value)) { error.value = t('Save a valid draft before publishing.'); return }
  if (!window.confirm(t('Publish this HOME draft? It will become customer-visible when S1.3 connects the customer homepage.'))) return
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
      <div><p class="eyebrow">{{ t('HOME merchandising') }}</p><h1>{{ t('Storefront Management') }}</h1></div>
      <p>{{ t('Arrange the complete local draft, review its preview, then save and publish explicitly.') }}</p>
    </header>

    <section class="storefront-status" aria-labelledby="storefront-status-title">
      <h2 id="storefront-status-title">{{ t('Publication status') }}</h2>
      <dl>
        <div><dt>{{ t('Published revision') }}</dt><dd>{{ management?.published ? `#${management.published.revisionNumber}` : t('None') }}</dd></div>
        <div><dt>{{ t('Draft revision') }}</dt><dd>{{ management?.draft ? `#${management.draft.revisionNumber} · v${management.draft.entityVersion}` : t('None') }}</dd></div>
        <div><dt>{{ t('Last publication') }}</dt><dd>{{ management?.published?.publishedAt ? new Date(management.published.publishedAt).toLocaleString() : t('Not published yet') }}</dd></div>
        <div><dt>{{ t('Editor state') }}</dt><dd :data-dirty="dirty">{{ t(dirty ? 'Unsaved changes' : 'Saved') }}</dd></div>
      </dl>
      <div class="storefront-commit-actions">
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
    <section v-if="operation === 'load'" class="inline-state" aria-live="polite"><p>{{ t('Loading storefront management…') }}</p></section>
    <section v-else-if="!management" class="inline-state">
      <h2>{{ t('Storefront management is unavailable') }}</h2><button type="button" @click="load">{{ t('Retry') }}</button>
    </section>

    <div v-else class="storefront-workbench">
      <section class="storefront-editor" aria-labelledby="storefront-editor-title">
        <div class="storefront-section-heading">
          <div><p class="eyebrow">{{ t('Local draft') }}</p><h2 id="storefront-editor-title">{{ t('Ordered sections') }}</h2></div>
          <p>{{ t('{count} of 8 sections', { count: sections.length }) }}</p>
        </div>

        <div class="storefront-add-row">
          <label>{{ t('Section type') }}
            <select v-model="newType">
              <option v-for="type in SECTION_TYPES" :key="type" :value="type">{{ t(type) }}</option>
            </select>
          </label>
          <button type="button" :disabled="!canAdd" @click="create">{{ t('Add section') }}</button>
          <small v-if="!canAdd">{{ t(sections.length >= 8 ? 'The 8-section limit is reached.' : 'HOME can contain only one hero banner.') }}</small>
        </div>

        <div v-if="!sections.length" class="storefront-empty">
          <h3>{{ t('No sections in this local draft') }}</h3>
          <p>{{ t('Choose one of the three approved section types to begin.') }}</p>
        </div>

        <ol v-else class="storefront-section-list">
          <li v-for="(section, index) in sections" :key="section.clientId" :data-active="activeId === section.clientId">
            <button class="storefront-section-select" type="button" @click="activeId = section.clientId">
              <span>{{ String(index + 1).padStart(2, '0') }}</span>
              <strong>{{ t(section.type) }}</strong>
              <small>{{ section.titleVi || section.titleEn || t('Untitled section') }}</small>
              <b>{{ t(section.enabled ? 'Enabled' : 'Disabled') }}</b>
            </button>
            <div class="storefront-row-actions">
              <button type="button" :aria-label="t('Edit section {position}', { position: index + 1 })" @click="activeId = section.clientId">{{ t('Edit') }}</button>
              <button type="button" :aria-label="t('Move section {position} up', { position: index + 1 })" :disabled="index === 0" @click="move(section.clientId, -1)">↑ {{ t('Up') }}</button>
              <button type="button" :aria-label="t('Move section {position} down', { position: index + 1 })" :disabled="index === sections.length - 1" @click="move(section.clientId, 1)">↓ {{ t('Down') }}</button>
              <button type="button" @click="toggle(section.clientId)">{{ t(section.enabled ? 'Disable' : 'Enable') }}</button>
              <button class="danger-button" type="button" @click="remove(section.clientId)">{{ t('Remove') }}</button>
            </div>
          </li>
        </ol>

        <form v-if="activeSection" class="storefront-section-form" @submit.prevent>
          <header><div><p class="eyebrow">{{ t(activeSection.type) }}</p><h2>{{ t('Edit section') }}</h2></div><label class="check-row"><input v-model="activeSection.enabled" type="checkbox">{{ t('Enabled in publication') }}</label></header>
          <div class="storefront-bilingual-grid">
            <label>{{ t('VI title') }}<input v-model="activeSection.titleVi" required maxlength="120"></label>
            <label>{{ t('EN title') }}<input v-model="activeSection.titleEn" required maxlength="120"></label>
            <label>{{ t('VI supporting text') }}<textarea v-model="activeSection.supportingTextVi" maxlength="500"></textarea></label>
            <label>{{ t('EN supporting text') }}<textarea v-model="activeSection.supportingTextEn" maxlength="500"></textarea></label>
            <label>{{ t('VI CTA label') }}<input v-model="activeSection.ctaLabelVi" required maxlength="80"></label>
            <label>{{ t('EN CTA label') }}<input v-model="activeSection.ctaLabelEn" required maxlength="80"></label>
          </div>

          <fieldset v-if="activeSection.type === 'HERO_BANNER'" class="storefront-media-options">
            <legend>{{ t('Controlled media') }}</legend>
            <label v-for="([key, url], index) in MEDIA_OPTIONS" :key="key" :data-selected="activeSection.mediaKey === key">
              <input v-model="activeSection.mediaKey" type="radio" :value="key"><img :src="url" alt=""><span>{{ index + 1 }} · {{ t(key) }}</span>
            </label>
          </fieldset>

          <fieldset v-if="activeSection.type === 'PRODUCT_COLLECTION'" class="storefront-product-selector">
            <legend>{{ t('Curated products') }} · {{ activeSection.productIds.length }}/8</legend>
            <label>{{ t('Search valid products') }}<input v-model="productQuery" type="search" autocomplete="off"></label>
            <ul v-if="filteredProducts.length" class="storefront-product-results">
              <li v-for="product in filteredProducts" :key="product.id"><span><strong>{{ product.name }}</strong><small>{{ product.category || product.collection || '—' }}</small></span><button type="button" :disabled="activeSection.productIds.includes(product.id) || activeSection.productIds.length >= 8" @click="addCollectionProduct(activeSection, product.id)">{{ t(activeSection.productIds.includes(product.id) ? 'Selected' : 'Add') }}</button></li>
            </ul>
            <p v-else class="storefront-selector-empty">{{ t('No products match this search.') }}</p>
            <ol v-if="activeSection.productIds.length" class="storefront-selected-products">
              <li v-for="(productId, index) in activeSection.productIds" :key="productId"><strong>{{ products.find(product => product.id === productId)?.name || t('Unavailable product') }}</strong><span><button type="button" :disabled="index === 0" @click="moveCollectionProduct(activeSection, productId, -1)">↑ {{ t('Up') }}</button><button type="button" :disabled="index === activeSection.productIds.length - 1" @click="moveCollectionProduct(activeSection, productId, 1)">↓ {{ t('Down') }}</button><button type="button" @click="removeCollectionProduct(activeSection, productId)">{{ t('Remove') }}</button></span></li>
            </ol>
          </fieldset>

          <label v-if="activeSection.type === 'PROMOTION_SPOTLIGHT'">{{ t('Current promotion') }}
            <select :value="activeSection.promotionFamilyId || ''" required @change="changePromotion(activeSection, $event)"><option value="" disabled>{{ t('Choose a promotion') }}</option><option v-for="offer in promotions" :key="offer.familyId" :value="offer.familyId">{{ offer.name }} · {{ offer.acquisitionMode }}</option></select>
            <small v-if="!promotions.length">{{ t('No current public promotions are available.') }}</small>
          </label>

          <fieldset class="storefront-cta-fields">
            <legend>{{ t('Call to action') }}</legend>
            <label>{{ t('CTA type') }}
              <select v-if="activeSection.type !== 'PROMOTION_SPOTLIGHT'" :value="activeSection.ctaKind" @change="changeCta(activeSection, $event)"><option v-for="kind in CTA_KINDS" :key="kind" :value="kind">{{ t(kind) }}</option></select>
              <input v-else :value="t('PROMOTION_DETAIL')" disabled>
            </label>
            <label v-if="activeSection.ctaKind === 'PRODUCT_DETAIL'">{{ t('CTA product') }}<select v-model="activeSection.ctaTargetPublicId" required><option :value="null" disabled>{{ t('Choose a product') }}</option><option v-for="product in products" :key="product.id" :value="product.id">{{ product.name }}</option></select></label>
            <label v-else-if="activeSection.ctaKind === 'PROMOTION_DETAIL' && activeSection.type !== 'PROMOTION_SPOTLIGHT'">{{ t('CTA promotion') }}<select v-model="activeSection.ctaTargetPublicId" required><option :value="null" disabled>{{ t('Choose a promotion') }}</option><option v-for="offer in promotions" :key="offer.familyId" :value="offer.familyId">{{ offer.name }}</option></select></label>
            <label v-else-if="activeSection.ctaKind === 'CATALOG_SEARCH'">{{ t('Catalog search term') }}<input v-model="activeSection.ctaSearchTerm" required maxlength="80"></label>
            <p v-else>{{ t('This CTA opens the public offers list.') }}</p>
          </fieldset>
        </form>
      </section>

      <aside class="storefront-preview" aria-labelledby="storefront-preview-title">
        <div class="storefront-preview-heading"><div><p class="eyebrow">{{ t('Local only') }}</p><h2 id="storefront-preview-title">{{ t('Draft preview') }}</h2></div><span>{{ t('Preview language follows the app language.') }}</span></div>
        <div v-if="sections.length" class="storefront-preview-canvas">
          <StorefrontSectionPreview v-for="section in sections" :key="section.clientId" :section="section" :products="products" :promotions="promotions" />
        </div>
        <div v-else class="storefront-empty"><p>{{ t('Add a section to build the local preview.') }}</p></div>
      </aside>
    </div>
  </div>
</template>
