<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, ApiError, type ManagerInventoryBalance, type ManagerProductDetail, type ManagerVariantDetail } from '../api'
import { errorCopy, formatDateTime, formatVnd } from '../format'
import { statusLabel, t } from '../i18n'
import { evidenceOptions, evidenceValueLabel, type ProductEvidence } from '../product-evidence'
import { hasPermission } from '../session'
import { submitRetryableCommand, type RetryableCommand } from '../retryable-command'

const route = useRoute()
const productId = computed(() => String(route.params.id))
const product = ref<ManagerProductDetail>()
const balances = ref<ManagerInventoryBalance[]>([])
const loading = ref(true)
const busy = ref('')
const error = ref('')
const success = ref('')
const priceDraft = ref<Record<string, string>>({})
const barcodeDraft = ref<Record<string, string>>({})
const evidence = ref<ProductEvidence>(emptyEvidence())
const editingBalance = ref('')
const targetOnHand = ref('')
const adjustmentReason = ref('')
const receivingBalance = ref('')
const receiptQuantity = ref('')
const receiptNote = ref('')
const receiptCommand = ref<RetryableCommand>()
const adjustmentCommand = ref<RetryableCommand>()
const editingIdentity = ref(false)
const addingVariant = ref(false)
const identity = reactive({ name: '', category: '', collection: '', heroImage: '', primaryImage: '' })
const variantDraft = reactive({ sku: '', size: '', color: '', barcode: '' })
const pendingRetire = ref<ManagerVariantDetail>()
const retireDialog = ref<HTMLDialogElement>()
const evidenceFields = Object.keys(evidenceOptions) as (keyof typeof evidenceOptions)[]

const colorGroups = computed(() => {
  const grouped = new Map<string, ManagerVariantDetail[]>()
  for (const variant of product.value?.variants ?? []) grouped.set(variant.color, [...(grouped.get(variant.color) ?? []), variant])
  return [...grouped.entries()].map(([color, variants]) => ({ color, variants }))
})
const adjustmentRow = computed(() => balances.value.find(row => balanceKey(row) === editingBalance.value))
const receiptRow = computed(() => balances.value.find(row => balanceKey(row) === receivingBalance.value))
const adjustmentDifference = computed(() => {
  const target = Number(targetOnHand.value)
  return adjustmentRow.value && Number.isInteger(target) ? target - adjustmentRow.value.onHand : null
})
const draftCount = computed(() => product.value?.variants.filter(variant => variant.status === 'DRAFT').length ?? 0)
const retiredCount = computed(() => product.value?.variants.filter(variant => variant.status === 'RETIRED').length ?? 0)
const activePricingComplete = computed(() => {
  const active = product.value?.variants.filter(variant => variant.status !== 'RETIRED') ?? []
  return active.length > 0 && active.every(variant => variant.pricingComplete)
})

function emptyEvidence(): ProductEvidence {
  return { intendedUse: null, primarySurface: null, upperConstruction: null, cutProfile: null, primarySoleProfile: null }
}
function balanceKey(row: ManagerInventoryBalance) { return `${row.variantId}:${row.locationId}` }
function setProduct(next: ManagerProductDetail) {
  product.value = next
  Object.assign(identity, { name: next.name, category: next.category ?? '', collection: next.collection ?? '', heroImage: next.heroImage ?? '', primaryImage: next.primaryImage ?? '' })
  evidence.value = { ...emptyEvidence(), ...next.evidence }
  priceDraft.value = Object.fromEntries(next.variants.map(variant => [variant.id, variant.currentPrice?.toString() ?? '']))
  barcodeDraft.value = Object.fromEntries(next.variants.map(variant => [variant.id, variant.barcode ?? '']))
}

async function saveIdentity() {
  if (!product.value || !identity.name.trim()) { error.value = t('Enter a product name.'); return }
  busy.value = 'identity'; error.value = ''; success.value = ''
  try {
    const saved = await api.updateManagerProduct(product.value.id, {
      expectedEntityVersion: product.value.entityVersion, name: identity.name.trim(),
      category: identity.category.trim() || null, collection: identity.collection.trim() || null,
      heroImage: identity.heroImage.trim() || null, primaryImage: identity.primaryImage.trim() || null,
    })
    product.value = { ...product.value, ...saved }
    editingIdentity.value = false
    success.value = t('Product identity saved.')
  } catch (reason) {
    if (reason instanceof ApiError && reason.code === 'CATALOG_PRODUCT_CHANGED') {
      await load(); error.value = t('This product changed. The latest identity is shown; review it before saving again.')
    } else error.value = errorCopy(reason)
  } finally { busy.value = '' }
}

async function addVariant() {
  const numericSize = Number(variantDraft.size)
  if (!variantDraft.sku.trim() || !variantDraft.color.trim() || !/^(?:\d+(?:\.\d{0,2})?|\.\d{1,2})$/.test(variantDraft.size.trim()) || numericSize <= 0 || numericSize > 999.99) {
    error.value = t('Enter a SKU, color, and positive EU size with at most two decimal places.'); return
  }
  busy.value = 'variant'; error.value = ''; success.value = ''
  try {
    await api.createManagerVariant(productId.value, { sku: variantDraft.sku.trim(), size: variantDraft.size.trim(), color: variantDraft.color.trim(), barcode: variantDraft.barcode.trim() || null })
    Object.assign(variantDraft, { sku: '', size: '', color: '', barcode: '' })
    addingVariant.value = false; await load(); success.value = t('Draft variant created.')
  } catch (reason) {
    if (reason instanceof ApiError && reason.code === 'CATALOG_SKU_ALREADY_EXISTS') error.value = t('This SKU is already in use. Enter a globally unique SKU.')
    else if (reason instanceof ApiError && reason.code === 'CATALOG_OPTION_ALREADY_EXISTS') error.value = t('An active variant already uses this color and EU size.')
    else if (reason instanceof ApiError && reason.code === 'CATALOG_BARCODE_ALREADY_EXISTS') error.value = t('This barcode is already assigned to another variant.')
    else error.value = errorCopy(reason)
  } finally { busy.value = '' }
}

async function saveBarcode(variant: ManagerVariantDetail) {
  if (!variant.barcodeEditable) return
  busy.value = `barcode:${variant.id}`; error.value = ''; success.value = ''
  try {
    await api.updateManagerBarcode(variant.id, variant.entityVersion, barcodeDraft.value[variant.id]?.trim() || null)
    await load(); success.value = t('Variant barcode saved.')
  } catch (reason) {
    if (reason instanceof ApiError && reason.code === 'CATALOG_BARCODE_ALREADY_EXISTS') error.value = t('This barcode is already assigned to another variant.')
    else if (reason instanceof ApiError && reason.code === 'CATALOG_BARCODE_IMMUTABLE') { await load(); error.value = t('Barcode is locked because this variant has already been published.') }
    else if (reason instanceof ApiError && reason.code === 'CATALOG_VARIANT_CHANGED') { await load(); error.value = t('This variant changed. Review the latest state and try again.') }
    else error.value = errorCopy(reason)
  } finally { busy.value = '' }
}

async function load() {
  loading.value = true; error.value = ''; success.value = ''
  try {
    setProduct(await api.managerProduct(productId.value))
    balances.value = hasPermission('INVENTORY_VIEW') ? (await api.managerInventory(productId.value)).balances : []
  } catch (reason) { error.value = errorCopy(reason) }
  finally { loading.value = false }
}

async function saveEvidence() {
  if (!product.value) return
  busy.value = 'evidence'; error.value = ''; success.value = ''
  try {
    const saved = await api.replaceProductEvidence(product.value.id, product.value.entityVersion, { ...evidence.value })
    product.value = { ...product.value, evidence: saved.evidence, entityVersion: saved.entityVersion }
    evidence.value = { ...emptyEvidence(), ...saved.evidence }
    success.value = t('Product evidence saved.')
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 409) {
      await load(); error.value = t('This product changed. The latest evidence is shown; review it before saving again.')
    } else error.value = errorCopy(reason)
  } finally { busy.value = '' }
}

async function savePrice(variant: ManagerVariantDetail) {
  const amount = Number(priceDraft.value[variant.id])
  if (!Number.isSafeInteger(amount) || amount <= 0) { error.value = t('Enter a positive whole VND amount.'); return }
  busy.value = `price:${variant.id}`; error.value = ''; success.value = ''
  try { await api.setVariantPrice(variant.id, amount); await load(); success.value = t('Current price updated.') }
  catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = '' }
}

async function publish(variant: ManagerVariantDetail) {
  busy.value = `publish:${variant.id}`; error.value = ''; success.value = ''
  try { await api.publishVariant(variant.id, variant.entityVersion); await load(); success.value = t('Variant published.') }
  catch (reason) {
    if (reason instanceof ApiError && reason.code === 'CATALOG_PRICE_REQUIRED') error.value = t('Set a current price before publishing.')
    else if (reason instanceof ApiError && reason.code === 'CATALOG_ON_HAND_STOCK_REQUIRED') error.value = t('Positive on-hand stock is required before publishing.')
    else if (reason instanceof ApiError && reason.code === 'CATALOG_VARIANT_CHANGED') { await load(); error.value = t('This variant changed. Review the latest state and try again.') }
    else error.value = errorCopy(reason)
  } finally { busy.value = '' }
}

async function requestRetire(variant: ManagerVariantDetail) {
  pendingRetire.value = variant
  await nextTick(); retireDialog.value?.showModal()
}
function closeRetire() { retireDialog.value?.close(); pendingRetire.value = undefined }
async function confirmRetire() {
  const variant = pendingRetire.value
  if (!variant) return
  busy.value = `retire:${variant.id}`; error.value = ''; success.value = ''
  try { await api.retireVariant(variant.id, variant.entityVersion); closeRetire(); await load(); success.value = t('Variant retired. Existing orders and history were preserved.') }
  catch (reason) {
    closeRetire()
    if (reason instanceof ApiError && reason.code === 'CATALOG_VARIANT_CHANGED') { await load(); error.value = t('This variant changed. Review the latest state and try again.') }
    else error.value = errorCopy(reason)
  } finally { busy.value = '' }
}
async function restore(variant: ManagerVariantDetail) {
  busy.value = `restore:${variant.id}`; error.value = ''; success.value = ''
  try { await api.restoreVariant(variant.id, variant.entityVersion); await load(); success.value = t('Variant restored to Draft. Review readiness before publishing again.') }
  catch (reason) {
    if (reason instanceof ApiError && reason.code === 'CATALOG_OPTION_ALREADY_EXISTS') error.value = t('Restore is blocked because an active variant now uses this color and EU size.')
    else if (reason instanceof ApiError && reason.code === 'CATALOG_VARIANT_CHANGED') { await load(); error.value = t('This variant changed. Review the latest state and try again.') }
    else error.value = errorCopy(reason)
  } finally { busy.value = '' }
}

function startAdjustment(row: ManagerInventoryBalance) {
  receivingBalance.value = ''
  editingBalance.value = balanceKey(row)
  targetOnHand.value = String(row.onHand)
  adjustmentReason.value = ''
  adjustmentCommand.value = undefined
  error.value = ''; success.value = ''
}

function startReceipt(row: ManagerInventoryBalance) {
  editingBalance.value = ''
  receivingBalance.value = balanceKey(row)
  receiptQuantity.value = ''
  receiptNote.value = ''
  receiptCommand.value = undefined
  error.value = ''; success.value = ''
}

async function receive() {
  const row = receiptRow.value
  const quantity = Number(receiptQuantity.value)
  if (!row || !Number.isSafeInteger(quantity) || quantity <= 0) {
    error.value = t('Enter a positive whole receipt quantity.'); return
  }
  const note = receiptNote.value.trim()
  const fingerprint = JSON.stringify([row.variantId, row.locationId, quantity, note])
  busy.value = `receipt:${receivingBalance.value}`; error.value = ''; success.value = ''
  try {
    const result = await submitRetryableCommand(receiptCommand, fingerprint,
      key => api.receiveManagerInventory(row.variantId, row.locationId, quantity, note, key))
    receivingBalance.value = ''; await load(); success.value = t('Stock received. On hand is now {count}.').replace('{count}', String(result.afterOnHand))
  } catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = '' }
}

async function adjust() {
  const row = adjustmentRow.value
  const target = Number(targetOnHand.value)
  if (!row || !Number.isSafeInteger(target) || target < row.reserved || !adjustmentReason.value.trim()) {
    error.value = t('Enter a whole on-hand target that is not below reserved stock, and give a reason.'); return
  }
  const reason = adjustmentReason.value.trim()
  const fingerprint = JSON.stringify([row.variantId, row.locationId, target, reason, row.balanceVersion])
  busy.value = `inventory:${editingBalance.value}`; error.value = ''; success.value = ''
  try {
    await submitRetryableCommand(adjustmentCommand, fingerprint,
      key => api.adjustManagerInventory(row.variantId, row.locationId, target, reason, row.balanceVersion, key))
    editingBalance.value = ''; await load(); success.value = t('On-hand inventory adjusted.')
  } catch (reason) {
    if (reason instanceof ApiError && reason.code === 'INVENTORY_BALANCE_CHANGED') {
      editingBalance.value = ''; adjustmentCommand.value = undefined; await load(); error.value = t('Inventory changed before this adjustment. The latest balance is shown; review and try again.')
    } else error.value = errorCopy(reason)
  } finally { busy.value = '' }
}

onMounted(load)
</script>

<template>
  <section class="operations-page product-management catalog-detail">
    <nav class="catalog-breadcrumb" :aria-label="t('Breadcrumb')"><RouterLink to="/operations/products">{{ t('Products') }}</RouterLink><span aria-hidden="true">/</span><span>{{ product?.name ?? t('Product') }}</span></nav>
    <p v-if="loading" class="inline-state" role="status">{{ t('Loading…') }}</p>
    <div v-else-if="error && !product" class="inline-state" role="alert"><p>{{ error }}</p><button class="text-button" type="button" @click="load">{{ t('Try again') }}</button></div>
    <template v-else-if="product">
      <header class="catalog-detail-heading">
        <div><h1>{{ product.name }}</h1><p>{{ product.category ?? t('Uncategorized') }}<template v-if="product.collection"> · {{ product.collection }}</template></p></div>
        <div class="catalog-heading-actions"><RouterLink v-if="hasPermission('STOREFRONT_MANAGE')" class="text-button" :to="{ path: '/operations/product-presentations', query: { productId: product.id } }">{{ t('Manage product copy') }}</RouterLink><button class="text-button" type="button" :disabled="!!busy" @click="load">{{ t('Refresh') }}</button></div>
      </header>
      <dl class="catalog-fact-strip">
        <div><dt>{{ t('Variants') }}</dt><dd>{{ product.variantCount }}</dd></div>
        <div><dt>{{ t('Published') }}</dt><dd>{{ product.publishedVariantCount }}</dd></div>
        <div><dt>{{ t('Draft') }}</dt><dd>{{ draftCount }}</dd></div>
        <div><dt>{{ t('Retired') }}</dt><dd>{{ retiredCount }}</dd></div>
        <div><dt>{{ t('Evidence version') }}</dt><dd>v{{ product.entityVersion }}</dd></div>
      </dl>
      <p v-if="product.hasDuplicateOptions" class="catalog-warning" role="status"><strong>{{ t('Duplicate option warning') }}</strong> {{ t('Resolve active duplicate color and EU size evidence before continuing lifecycle work.') }}</p>
      <p v-if="success" class="success-message" role="status">{{ success }}</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>

      <section class="catalog-work-section" aria-labelledby="catalog-progress-title">
        <div class="catalog-section-heading"><div><h2 id="catalog-progress-title">{{ t('Setup progress') }}</h2><p>{{ t('Incomplete work is saved. Continue each area when its permission and evidence are available.') }}</p></div></div>
        <dl class="catalog-progress-list">
          <div><dt>{{ t('Identity') }}</dt><dd class="ready-copy">{{ t('Complete') }}</dd></div>
          <div><dt>{{ t('Variants') }}</dt><dd>{{ product.variantCount ? t('Complete') : t('Incomplete') }}</dd></div>
          <div><dt>{{ t('Pricing') }}</dt><dd v-if="hasPermission('PRICE_MANAGE')">{{ activePricingComplete ? t('Complete') : t('Incomplete') }}</dd><dd v-else>{{ t('Requires PRICE_MANAGE') }}</dd></div>
          <div><dt>{{ t('Inventory') }}</dt><dd v-if="hasPermission('INVENTORY_VIEW')">{{ balances.some(row => row.onHand > 0) ? t('Evidence available') : t('Incomplete') }}</dd><dd v-else>{{ t('Requires INVENTORY_VIEW') }}</dd></div>
          <div><dt>{{ t('Content') }}</dt><dd>{{ product.evidence ? t('Evidence available') : t('Incomplete') }}</dd></div>
          <div><dt>{{ t('Publication') }}</dt><dd>{{ product.publishedVariantCount ? t('In progress') : t('Not started') }}</dd></div>
        </dl>
      </section>

      <section class="catalog-work-section" aria-labelledby="catalog-identity-title">
        <div class="catalog-section-heading"><div><h2 id="catalog-identity-title">{{ t('Product identity') }}</h2><p>{{ t('Current operational identity. Historical orders do not snapshot Product name.') }}</p></div><button class="text-button" type="button" :disabled="!!busy" @click="editingIdentity = !editingIdentity">{{ t(editingIdentity ? 'Cancel editing' : 'Edit identity') }}</button></div>
        <dl v-if="!editingIdentity" class="catalog-identity-facts"><div><dt>{{ t('Product name') }}</dt><dd>{{ product.name }}</dd></div><div><dt>{{ t('Category') }}</dt><dd>{{ product.category ?? '—' }}</dd></div><div><dt>{{ t('Collection') }}</dt><dd>{{ product.collection ?? '—' }}</dd></div><div><dt>{{ t('Hero image reference') }}</dt><dd>{{ product.heroImage ?? '—' }}</dd></div><div><dt>{{ t('Primary image reference') }}</dt><dd>{{ product.primaryImage ?? '—' }}</dd></div></dl>
        <form v-else class="catalog-identity-form" @submit.prevent="saveIdentity"><div class="catalog-form-grid"><label><span>{{ t('Product name') }}</span><input v-model.trim="identity.name" required maxlength="160" :disabled="!!busy"></label><label><span>{{ t('Category') }}</span><input v-model.trim="identity.category" maxlength="32" :disabled="!!busy"></label><label><span>{{ t('Collection') }}</span><input v-model.trim="identity.collection" maxlength="64" :disabled="!!busy"></label><label><span>{{ t('Hero image reference') }}</span><input v-model.trim="identity.heroImage" maxlength="255" :disabled="!!busy"></label><label><span>{{ t('Primary image reference') }}</span><input v-model.trim="identity.primaryImage" maxlength="255" :disabled="!!busy"></label></div><button class="primary-button" type="submit" :disabled="!!busy">{{ t(busy === 'identity' ? 'Saving…' : 'Save identity') }}</button></form>
      </section>

      <section class="catalog-work-section" aria-labelledby="catalog-evidence-title">
        <div class="catalog-section-heading"><div><h2 id="catalog-evidence-title">{{ t('Product evidence') }}</h2><p>{{ t('Controlled facts only; unknown values stay blank.') }}</p></div></div>
        <form class="catalog-evidence-form" @submit.prevent="saveEvidence">
          <label v-for="field in evidenceFields" :key="field"><span>{{ t(field === 'intendedUse' ? 'Intended use' : field === 'primarySurface' ? 'Primary surface' : field === 'upperConstruction' ? 'Upper construction' : field === 'cutProfile' ? 'Cut profile' : 'Primary sole profile') }}</span><select v-model="evidence[field]" :disabled="!!busy"><option :value="null">{{ t('Unknown / not provided') }}</option><option v-for="option in evidenceOptions[field]" :key="option" :value="option">{{ t(evidenceValueLabel(option)) }}</option></select></label>
          <button class="primary-button" type="submit" :disabled="!!busy">{{ t(busy === 'evidence' ? 'Saving…' : 'Save product evidence') }}</button>
        </form>
      </section>

      <section class="catalog-work-section" aria-labelledby="catalog-variants-title">
        <div class="catalog-section-heading"><div><h2 id="catalog-variants-title">{{ t('Variants') }}</h2><p>{{ t('Exact stored color and EU size facts, including retired history.') }}</p></div><button class="text-button" type="button" :disabled="!!busy" @click="addingVariant = !addingVariant">{{ t(addingVariant ? 'Cancel' : 'Add variant') }}</button></div>
        <form v-if="addingVariant" class="catalog-add-variant" @submit.prevent="addVariant"><p>{{ t('SKU, color, and EU size cannot be changed after creation. Barcode remains editable until first publication.') }}</p><div><label><span>{{ t('SKU') }}</span><input v-model.trim="variantDraft.sku" required maxlength="64" pattern="[A-Za-z0-9_-]{2,64}" :disabled="!!busy"></label><label><span>{{ t('Barcode (optional)') }}</span><input v-model="variantDraft.barcode" maxlength="128" autocomplete="off" :disabled="!!busy"></label><label><span>{{ t('Color') }}</span><input v-model.trim="variantDraft.color" required maxlength="64" :disabled="!!busy"></label><label><span>{{ t('EU size') }}</span><input v-model.trim="variantDraft.size" required inputmode="decimal" maxlength="32" :disabled="!!busy"></label><button class="primary-button" type="submit" :disabled="!!busy">{{ t(busy === 'variant' ? 'Creating…' : 'Create Draft variant') }}</button></div></form>
        <p v-if="!colorGroups.length" class="inline-state">{{ t('No variants yet. Add a Draft variant to continue setup.') }}</p>
        <section v-for="group in colorGroups" :key="group.color" class="variant-color-group">
          <h3>{{ group.color }} <span>{{ group.variants.length }}</span></h3>
          <div class="catalog-table-wrap" role="region" tabindex="0" :aria-label="`${group.color} ${t('Variants')}`">
            <table class="catalog-variant-table"><thead><tr><th>{{ t('SKU') }}</th><th>{{ t('Barcode') }}</th><th>{{ t('Size') }}</th><th>{{ t('Publication') }}</th><th>{{ t('Current price') }}</th><th>{{ t('Publish readiness') }}</th><th>{{ t('Actions') }}</th></tr></thead>
              <tbody><tr v-for="variant in group.variants" :key="variant.id" :data-duplicate="variant.duplicateOptionCombination">
                <th scope="row">{{ variant.sku }}<small v-if="variant.duplicateOptionCombination">{{ t('Duplicate option') }}</small></th><td><form v-if="variant.barcodeEditable" class="inline-price-form" @submit.prevent="saveBarcode(variant)"><label><span class="sr-only">{{ t('Barcode') }}</span><input v-model="barcodeDraft[variant.id]" maxlength="128" autocomplete="off" :disabled="!!busy"></label><button type="submit" :disabled="!!busy">{{ t(busy === `barcode:${variant.id}` ? 'Saving…' : 'Save barcode') }}</button></form><template v-else><strong>{{ variant.barcode ?? '—' }}</strong><small>{{ t('Locked after first publication') }}</small></template></td><td>{{ variant.size }}</td><td><span class="state-label" :data-state="variant.status">{{ t(statusLabel(variant.status)) }}</span></td>
                <td><strong>{{ variant.currentPrice == null ? t('Missing') : formatVnd(variant.currentPrice) }}</strong><form v-if="hasPermission('PRICE_MANAGE')" class="inline-price-form" @submit.prevent="savePrice(variant)"><label><span class="sr-only">{{ t('New current price') }}</span><input v-model="priceDraft[variant.id]" inputmode="numeric" type="number" min="1" step="1" :disabled="!!busy"></label><button type="submit" :disabled="!!busy">{{ t('Set price') }}</button></form><small v-else>{{ t('Requires PRICE_MANAGE') }}</small></td>
                <td><template v-if="variant.status === 'PUBLISHED'"><span>{{ t('Published') }}</span><small v-if="variant.inventory && variant.inventory.available <= 0">{{ t('Sold out in assigned locations') }}</small></template><span v-else-if="variant.status === 'RETIRED'">{{ t('Restore to Draft before readiness review') }}</span><strong v-else-if="variant.publishReady" class="ready-copy">{{ t('Ready') }}</strong><ul v-else class="readiness-list"><li v-for="code in variant.publishReadinessCodes" :key="code">{{ t(code === 'CATALOG_PRICE_REQUIRED' ? 'Current price required' : 'On-hand stock required') }}</li></ul></td>
                <td class="catalog-variant-actions"><button v-if="variant.status === 'DRAFT'" class="text-button" type="button" :disabled="!!busy || !variant.publishReady" @click="publish(variant)">{{ t(busy === `publish:${variant.id}` ? 'Publishing…' : 'Publish') }}</button><button v-if="variant.status !== 'RETIRED'" class="text-button" type="button" :disabled="!!busy" @click="requestRetire(variant)">{{ t('Retire') }}</button><button v-else class="text-button" type="button" :disabled="!!busy" @click="restore(variant)">{{ t(busy === `restore:${variant.id}` ? 'Restoring…' : 'Restore to Draft') }}</button></td>
              </tr></tbody>
            </table>
          </div>
        </section>
      </section>

      <section v-if="hasPermission('INVENTORY_VIEW')" class="catalog-work-section" aria-labelledby="catalog-inventory-title">
        <div class="catalog-section-heading"><div><h2 id="catalog-inventory-title">{{ t('Inventory by assigned location') }}</h2><p>{{ t('On hand and reserved are separate. Available equals on hand minus reserved.') }}</p></div></div>
        <p v-if="!balances.length" class="inline-state">{{ t('No enabled location assignment or inventory rows are available.') }}</p>
        <div v-else class="catalog-table-wrap" role="region" tabindex="0" :aria-label="t('Inventory by assigned location')"><table class="catalog-inventory-table"><thead><tr><th>{{ t('Variant') }}</th><th>{{ t('Location') }}</th><th>{{ t('On hand') }}</th><th>{{ t('Reserved') }}</th><th>{{ t('Available') }}</th><th>{{ t('Updated') }}</th><th v-if="hasPermission('INVENTORY_ADJUST')">{{ t('Action') }}</th></tr></thead>
          <tbody><template v-for="row in balances" :key="balanceKey(row)"><tr><th scope="row">{{ row.sku }}<small>{{ row.color }} · {{ row.size }}</small></th><td>{{ row.branchCode }} / {{ row.locationCode }}<small>{{ row.locationName }}</small></td><td class="number">{{ row.onHand }}</td><td class="number">{{ row.reserved }}</td><td class="number"><strong>{{ row.available }}</strong></td><td>{{ row.updatedAt ? formatDateTime(row.updatedAt) : t('No balance yet') }}</td><td v-if="hasPermission('INVENTORY_ADJUST')"><div class="catalog-variant-actions"><button class="text-button" type="button" :disabled="!!busy" @click="startReceipt(row)">{{ t('Receive stock') }}</button><button class="text-button" type="button" :disabled="!!busy" @click="startAdjustment(row)">{{ t('Adjust on hand') }}</button></div></td></tr>
            <tr v-if="receivingBalance === balanceKey(row)" class="inventory-adjustment-row"><td :colspan="hasPermission('INVENTORY_ADJUST') ? 7 : 6"><form @submit.prevent="receive"><dl><div><dt>{{ t('Current on hand') }}</dt><dd>{{ row.onHand }}</dd></div><div><dt>{{ t('Reserved read-only') }}</dt><dd>{{ row.reserved }}</dd></div><div><dt>{{ t('Available after receipt') }}</dt><dd>{{ Number.isSafeInteger(Number(receiptQuantity)) && Number(receiptQuantity) > 0 ? row.available + Number(receiptQuantity) : '—' }}</dd></div></dl><label>{{ t('Receipt quantity') }}<input v-model="receiptQuantity" type="number" inputmode="numeric" min="1" step="1" required :disabled="!!busy"></label><label>{{ t('Receipt note / reference') }}<input v-model="receiptNote" maxlength="256" :disabled="!!busy"></label><div><button class="primary-button" type="submit" :disabled="!!busy">{{ t('Receive stock') }}</button><button class="text-button" type="button" :disabled="!!busy" @click="receivingBalance = ''">{{ t('Cancel') }}</button></div></form></td></tr>
            <tr v-if="editingBalance === balanceKey(row)" class="inventory-adjustment-row"><td :colspan="hasPermission('INVENTORY_ADJUST') ? 7 : 6"><form @submit.prevent="adjust"><dl><div><dt>{{ t('Current on hand') }}</dt><dd>{{ row.onHand }}</dd></div><div><dt>{{ t('Reserved read-only') }}</dt><dd>{{ row.reserved }}</dd></div><div><dt>{{ t('Difference') }}</dt><dd>{{ adjustmentDifference == null ? '—' : adjustmentDifference > 0 ? `+${adjustmentDifference}` : adjustmentDifference }}</dd></div></dl><label>{{ t('New on-hand target') }}<input v-model="targetOnHand" type="number" inputmode="numeric" min="0" step="1" :disabled="!!busy"></label><label>{{ t('Adjustment reason') }}<input v-model.trim="adjustmentReason" maxlength="256" :disabled="!!busy"></label><div><button class="primary-button" type="submit" :disabled="!!busy">{{ t('Apply on-hand target') }}</button><button class="text-button" type="button" :disabled="!!busy" @click="editingBalance = ''">{{ t('Cancel') }}</button></div></form></td></tr></template></tbody>
        </table></div>
      </section>
      <section v-else class="catalog-work-section" aria-labelledby="catalog-inventory-title"><div class="catalog-section-heading"><div><h2 id="catalog-inventory-title">{{ t('Inventory by assigned location') }}</h2><p>{{ t('Requires INVENTORY_VIEW. Inventory remains server-owned and is not hidden by catalog permissions.') }}</p></div></div></section>

      <dialog ref="retireDialog" class="terminal-dialog" aria-labelledby="retire-variant-title" aria-describedby="retire-variant-description" @cancel="closeRetire"><form method="dialog" @submit.prevent><h2 id="retire-variant-title">{{ t('Retire variant') }}</h2><p id="retire-variant-description">{{ t('Variant will stop accepting new orders. Existing orders and historical data will not be deleted.') }}</p><p v-if="pendingRetire"><strong>{{ pendingRetire.sku }}</strong> · {{ pendingRetire.color }} · EU {{ pendingRetire.size }}</p><div><button class="text-button" type="button" :disabled="!!busy" @click="closeRetire">{{ t('Cancel') }}</button><button class="primary-button" type="button" :disabled="!!busy" @click="confirmRetire">{{ t(busy.startsWith('retire:') ? 'Retiring…' : 'Retire variant') }}</button></div></form></dialog>
    </template>
  </section>
</template>
