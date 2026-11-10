<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api, ApiError, type ProductPresentationReference } from '../api'
import ProductPresentationSummary from '../components/ProductPresentationSummary.vue'
import { locale, t } from '../i18n'
import {
  canPublishProductPresentation, presentationSummary, type ProductPresentation,
  type ProductPresentationManagementState, type ProductPresentationRevision,
} from '../product-presentation'

const route = useRoute()
const products = ref<ProductPresentationReference[]>([])
const productId = ref('')
const management = ref<ProductPresentationManagementState | null>(null)
const summaryVi = ref('')
const summaryEn = ref('')
const savedVi = ref('')
const savedEn = ref('')
const operation = ref<'load' | 'edit' | 'save' | 'publish' | null>(null)
const error = ref('')
const fieldError = ref('')
const success = ref('')
const conflict = ref(false)
let loadVersion = 0

const dirty = computed(() => summaryVi.value !== savedVi.value || summaryEn.value !== savedEn.value)
const canPublish = computed(() => canPublishProductPresentation(management.value?.draft ?? null, dirty.value))
const previewPresentation = computed<ProductPresentation | null>(() => summaryVi.value || summaryEn.value
  ? { summary: { vi: summaryVi.value, en: summaryEn.value } } : null)
const previewText = computed(() => presentationSummary(previewPresentation.value, locale.value))

function setEditor(revision: ProductPresentationRevision | null) {
  summaryVi.value = revision?.summaryVi ?? ''
  summaryEn.value = revision?.summaryEn ?? ''
  savedVi.value = summaryVi.value
  savedEn.value = summaryEn.value
}

function problemCopy(reason: unknown) {
  if (!(reason instanceof ApiError)) return t('The service is temporarily unavailable. Please retry.')
  if (reason.status === 403) return t('You do not have permission to manage product presentations.')
  if (reason.code === 'PRODUCT_PRESENTATION_SUMMARY_INVALID') return t('Each summary must contain no more than 500 characters.')
  if (reason.code === 'PRODUCT_PRESENTATION_LOCALES_REQUIRED') return t('Complete both languages before publishing.')
  return t('Product presentation could not be updated. Retry or reload the latest state.')
}

function preserveConflict() {
  conflict.value = true
  error.value = t('The server draft or publication changed. Your local edits are still here.')
}

async function loadState() {
  const selected = productId.value
  const version = ++loadVersion
  operation.value = 'load'; error.value = ''; fieldError.value = ''; success.value = ''; conflict.value = false
  management.value = null
  setEditor(null)
  try {
    const state = await api.productPresentationState(selected)
    if (version !== loadVersion || selected !== productId.value) return
    management.value = state
    setEditor(state.draft ?? state.published)
  } catch (reason) {
    if (version === loadVersion) error.value = problemCopy(reason)
  } finally { if (version === loadVersion) operation.value = null }
}

async function load() {
  operation.value = 'load'; error.value = ''
  try {
    products.value = await api.productPresentationProducts()
    if (!productId.value && typeof route.query.productId === 'string') productId.value = route.query.productId
    if (!products.value.some(product => product.id === productId.value)) productId.value = products.value[0]?.id ?? ''
    if (productId.value) await loadState()
  } catch (reason) { error.value = problemCopy(reason); operation.value = null }
}

async function selectProduct(event: Event) {
  productId.value = (event.target as HTMLSelectElement).value
  await loadState()
}

async function edit() {
  if (!productId.value || operation.value || management.value?.draft) return
  operation.value = 'edit'; error.value = ''; success.value = ''; conflict.value = false
  try {
    const draft = await api.saveProductPresentationDraft(productId.value, {
      expectedDraftRevisionId: null,
      expectedEntityVersion: null,
      summaryVi: null,
      summaryEn: null,
    })
    management.value = { productId: productId.value, draft, published: management.value?.published ?? null }
    setEditor(draft)
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 409) preserveConflict()
    else error.value = problemCopy(reason)
  } finally { operation.value = null }
}

async function save() {
  const draft = management.value?.draft
  if (!draft || operation.value) return
  if (summaryVi.value.length > 500 || summaryEn.value.length > 500) {
    fieldError.value = t('Each summary must contain no more than 500 characters.'); return
  }
  operation.value = 'save'; error.value = ''; fieldError.value = ''; success.value = ''; conflict.value = false
  try {
    const saved = await api.saveProductPresentationDraft(productId.value, {
      expectedDraftRevisionId: draft.id,
      expectedEntityVersion: draft.entityVersion,
      summaryVi: summaryVi.value.trim() || null,
      summaryEn: summaryEn.value.trim() || null,
    })
    management.value = { productId: productId.value, draft: saved, published: management.value?.published ?? null }
    setEditor(saved)
    success.value = t('Draft revision {revision} saved.', { revision: saved.revisionNumber })
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 409) preserveConflict()
    else fieldError.value = problemCopy(reason)
  } finally { operation.value = null }
}

async function publish() {
  if (dirty.value) { error.value = t('Save your local changes before publishing.'); return }
  const draft = management.value?.draft
  if (!canPublish.value || !draft || operation.value) {
    error.value = t('Complete both languages and save the draft before publishing.'); return
  }
  operation.value = 'publish'; error.value = ''; fieldError.value = ''; success.value = ''; conflict.value = false
  try {
    const published = await api.publishProductPresentation(productId.value, {
      expectedDraftRevisionId: draft.id,
      expectedEntityVersion: draft.entityVersion,
      expectedPublishedRevisionId: management.value?.published?.id ?? null,
    })
    management.value = { productId: productId.value, draft: null, published }
    setEditor(published)
    success.value = t('Published revision {revision}.', { revision: published.revisionNumber })
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 409) preserveConflict()
    else fieldError.value = problemCopy(reason)
  } finally { operation.value = null }
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (!dirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onMounted(() => { window.addEventListener('beforeunload', beforeUnload); void load() })
onBeforeUnmount(() => { ++loadVersion; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <div class="storefront-management-page presentation-management-page">
    <header class="storefront-management-heading">
      <h1>{{ t('Product presentations') }}</h1>
      <p>{{ t('Edit conservative VI/EN merchandising evidence, preview the customer copy, then save and publish an exact revision.') }}</p>
    </header>

    <div class="presentation-product-selector">
      <label for="presentation-product">{{ t('Product') }}</label>
      <select id="presentation-product" :value="productId" :disabled="!!operation || !products.length" @change="selectProduct">
        <option v-if="!products.length" value="">{{ t('No products available') }}</option>
        <option v-for="product in products" :key="product.id" :value="product.id">{{ product.name }}</option>
      </select>
      <button class="text-button" type="button" :disabled="!!operation" @click="load">{{ t('Refresh') }}</button>
    </div>

    <section v-if="productId" class="storefront-status presentation-status" aria-labelledby="presentation-status-title" :aria-busy="!!operation">
      <h2 id="presentation-status-title">{{ t('Publication status') }}</h2>
      <dl>
        <div><dt>{{ t('Published revision') }}</dt><dd>{{ management?.published ? `#${management.published.revisionNumber}` : t('None') }}</dd></div>
        <div><dt>{{ t('Draft revision') }}</dt><dd>{{ management?.draft ? `#${management.draft.revisionNumber} · v${management.draft.entityVersion}` : t('None') }}</dd></div>
        <div><dt>{{ t('Editor state') }}</dt><dd :data-dirty="dirty">{{ t(dirty ? 'Unsaved changes' : 'Saved') }}</dd></div>
      </dl>
      <div class="storefront-commit-actions">
        <button v-if="management && !management.draft" class="secondary-button" type="button" :disabled="!!operation" @click="edit">{{ t(operation === 'edit' ? 'Opening draft…' : 'Edit') }}</button>
        <button v-else class="secondary-button" type="button" :disabled="!!operation || !dirty" @click="save">{{ t(operation === 'save' ? 'Saving…' : 'Save Draft') }}</button>
        <button class="primary-button" type="button" :disabled="!!operation || !canPublish" @click="publish">{{ t(operation === 'publish' ? 'Publishing…' : 'Publish') }}</button>
      </div>
    </section>

    <p v-if="error" class="form-error storefront-message" role="alert">{{ error }}</p>
    <div v-if="conflict" class="storefront-conflict" role="alert">
      <strong>{{ t('Conflict detected') }}</strong>
      <p>{{ t('Reloading gets the latest server state and replaces your unsaved local edits.') }}</p>
      <button type="button" :disabled="!!operation" @click="loadState">{{ t('Reload latest and discard local edits') }}</button>
    </div>
    <p v-if="success" class="storefront-success" role="status">{{ success }}</p>
    <section v-if="operation === 'load'" class="queue-loading" role="status" aria-live="polite"><span class="loader-mark"></span>{{ t('Loading product presentation…') }}</section>
    <section v-else-if="!productId" class="inline-state"><h2>{{ t('No products available') }}</h2></section>

    <div v-else-if="management" class="presentation-workbench">
      <section class="presentation-editor" aria-labelledby="presentation-editor-title">
        <div class="storefront-section-heading">
          <h2 id="presentation-editor-title">{{ t('VI/EN summaries') }}</h2>
          <p>{{ management.draft ? t('Draft is editable') : t('Open a draft to edit') }}</p>
        </div>
        <form class="storefront-section-form presentation-form" @submit.prevent="save">
          <div class="storefront-bilingual-grid">
            <label>{{ t('Vietnamese summary') }}
              <textarea v-model="summaryVi" :disabled="!!operation || !management.draft" maxlength="500" autocapitalize="sentences" @input="fieldError = ''; success = ''"></textarea>
              <small>{{ summaryVi.length }}/500</small>
            </label>
            <label>{{ t('English summary') }}
              <textarea v-model="summaryEn" :disabled="!!operation || !management.draft" maxlength="500" autocapitalize="sentences" @input="fieldError = ''; success = ''"></textarea>
              <small>{{ summaryEn.length }}/500</small>
            </label>
          </div>
          <p v-if="fieldError" class="form-error" role="alert">{{ fieldError }}</p>
          <p v-if="management.draft && (!summaryVi.trim() || !summaryEn.trim())" class="presentation-publish-note">{{ t('Incomplete drafts can be saved. Complete both languages before publishing.') }}</p>
        </form>
      </section>

      <aside class="storefront-preview presentation-preview" aria-labelledby="presentation-preview-title">
        <div class="storefront-preview-heading">
          <h2 id="presentation-preview-title">{{ t('Customer preview') }}</h2>
          <span>{{ t('Preview language follows the app language.') }}</span>
        </div>
        <div class="presentation-preview-body">
          <ProductPresentationSummary :presentation="previewPresentation" />
          <p v-if="!previewText" class="storefront-preview-empty">{{ t('No summary in this draft yet.') }}</p>
        </div>
      </aside>
    </div>
  </div>
</template>
