<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api, type CreateInventoryTransfer, type InventoryTransfer, type InventoryTransferOptions } from '../api'
import { errorCopy, formatDateTime } from '../format'
import { t } from '../i18n'
import { submitRetryableCommand, type RetryableCommand } from '../retryable-command'

const options = ref<InventoryTransferOptions>({ locations: [], stock: [] })
const transfers = ref<InventoryTransfer[]>([])
const sourceLocationId = ref('')
const destinationLocationId = ref('')
const quantities = ref<Record<string, string>>({})
const loading = ref(true)
const busy = ref('')
const error = ref('')
const success = ref('')
const createCommand = ref<RetryableCommand>()
const dispatchCommands = new Map<string, { value: RetryableCommand | undefined }>()
const receiveCommands = new Map<string, { value: RetryableCommand | undefined }>()
const statusFilter = ref<'ALL' | InventoryTransfer['status']>('ALL')
let loadVersion = 0
let disposed = false

const sourceStock = computed(() => options.value.stock.filter(row => row.locationId === sourceLocationId.value))
const destinations = computed(() => options.value.locations.filter(row => row.locationId !== sourceLocationId.value))
const statusCounts = computed(() => ({
  ALL: transfers.value.length,
  DRAFT: transfers.value.filter(transfer => transfer.status === 'DRAFT').length,
  DISPATCHED: transfers.value.filter(transfer => transfer.status === 'DISPATCHED').length,
  RECEIVED: transfers.value.filter(transfer => transfer.status === 'RECEIVED').length,
}))
const visibleTransfers = computed(() => statusFilter.value === 'ALL'
  ? transfers.value
  : transfers.value.filter(transfer => transfer.status === statusFilter.value))

function commandState(store: Map<string, { value: RetryableCommand | undefined }>, transferId: string) {
  const existing = store.get(transferId)
  if (existing) return existing
  const created = { value: undefined }
  store.set(transferId, created)
  return created
}

function statusCopy(status: InventoryTransfer['status']) {
  return status === 'DRAFT' ? 'Draft' : status === 'DISPATCHED' ? 'Dispatched / In transit' : 'Received at destination'
}

function filterStatus(event: Event) {
  statusFilter.value = (event.target as HTMLSelectElement).value as 'ALL' | InventoryTransfer['status']
}

async function load() {
  const version = ++loadVersion
  loading.value = true; error.value = ''
  try {
    const [nextOptions, nextTransfers] = await Promise.all([api.inventoryTransferOptions(), api.inventoryTransfers()])
    if (disposed || version !== loadVersion) return
    options.value = nextOptions; transfers.value = nextTransfers
    if (!sourceLocationId.value) sourceLocationId.value = nextOptions.locations[0]?.locationId ?? ''
    if (!destinationLocationId.value || destinationLocationId.value === sourceLocationId.value) destinationLocationId.value = nextOptions.locations.find(location => location.locationId !== sourceLocationId.value)?.locationId ?? ''
  } catch (reason) {
    if (!disposed && version === loadVersion) {
      options.value = { locations: [], stock: [] }; transfers.value = []; error.value = errorCopy(reason)
    }
  }
  finally { if (!disposed && version === loadVersion) loading.value = false }
}

function createPayload(): CreateInventoryTransfer {
  return {
    sourceLocationId: sourceLocationId.value,
    destinationLocationId: destinationLocationId.value,
    items: sourceStock.value.flatMap(row => {
      const quantity = Number(quantities.value[row.variantId] ?? 0)
      return Number.isSafeInteger(quantity) && quantity > 0 ? [{ variantId: row.variantId, quantity }] : []
    }),
  }
}

async function create() {
  const payload = createPayload()
  if (!payload.sourceLocationId || !payload.destinationLocationId || !payload.items.length) { error.value = t('Choose two locations and enter at least one positive whole quantity.'); return }
  const fingerprint = JSON.stringify(payload)
  busy.value = 'create'; error.value = ''; success.value = ''
  try {
    await submitRetryableCommand(createCommand, fingerprint, key => api.createInventoryTransfer(payload, key))
    quantities.value = {}; await load(); success.value = t('Transfer draft created.')
  } catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = '' }
}

async function dispatch(transferId: string) {
  const dispatchCommand = commandState(dispatchCommands, transferId)
  busy.value = `dispatch:${transferId}`; error.value = ''; success.value = ''
  try {
    await submitRetryableCommand(dispatchCommand, JSON.stringify(['DISPATCH', transferId]), key => api.dispatchInventoryTransfer(transferId, key))
    await load(); success.value = t('Transfer dispatched.')
  } catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = '' }
}

async function receive(transferId: string) {
  const receiveCommand = commandState(receiveCommands, transferId)
  busy.value = `receive:${transferId}`; error.value = ''; success.value = ''
  try {
    await submitRetryableCommand(receiveCommand, JSON.stringify(['RECEIVE', transferId]), key => api.receiveInventoryTransfer(transferId, key))
    await load(); success.value = t('Transfer received.')
  } catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = '' }
}

onMounted(load)
onUnmounted(() => { disposed = true; loadVersion++ })
</script>

<template>
  <section class="operations-page inventory-transfer-page">
    <header class="page-heading"><p class="eyebrow">{{ t('Inventory operations') }}</p><h1>{{ t('Inventory transfers') }}</h1><p>{{ t('Create a draft, dispatch exact stock from the source, then receive it at the destination.') }}</p></header>
    <p v-if="loading" class="inline-state" role="status">{{ t('Loading…') }}</p>
    <p v-if="success" class="success-message" role="status">{{ success }}</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>

    <form v-if="!loading" class="catalog-create-form" @submit.prevent="create">
      <h2>{{ t('Create transfer draft') }}</h2>
      <div class="catalog-form-grid">
        <label>{{ t('Source location') }}<select v-model="sourceLocationId" @change="destinationLocationId = destinations[0]?.locationId ?? ''"><option v-for="location in options.locations" :key="location.locationId" :value="location.locationId">{{ location.branchCode }} · {{ location.locationCode }} — {{ location.locationName }}</option></select></label>
        <label>{{ t('Destination location') }}<select v-model="destinationLocationId"><option v-for="location in destinations" :key="location.locationId" :value="location.locationId">{{ location.branchCode }} · {{ location.locationCode }} — {{ location.locationName }}</option></select></label>
      </div>
      <div class="catalog-table-wrap" tabindex="0" :aria-label="t('Transfer stock table')">
        <table class="catalog-inventory-table"><thead><tr><th>{{ t('Product') }}</th><th>{{ t('SKU') }}</th><th class="number">{{ t('Available') }}</th><th>{{ t('Transfer quantity') }}</th></tr></thead><tbody>
          <tr v-for="row in sourceStock" :key="row.variantId"><th scope="row">{{ row.productName }}</th><td>{{ row.sku }}</td><td class="number">{{ row.available }}</td><td><input v-model="quantities[row.variantId]" inputmode="numeric" type="number" min="0" step="1" :max="row.available" :aria-label="`${t('Transfer quantity')} ${row.sku}`"></td></tr>
          <tr v-if="!sourceStock.length"><td colspan="4">{{ t('No stock rows are available at this source.') }}</td></tr>
        </tbody></table>
      </div>
      <button type="submit" :disabled="!!busy || options.locations.length < 2">{{ t(busy === 'create' ? 'Creating…' : 'Create draft') }}</button>
    </form>

    <section class="catalog-work-section" aria-labelledby="transfer-history-title"><div class="catalog-section-heading"><div><h2 id="transfer-history-title">{{ t('Transfer history') }}</h2><p>{{ t('Only the next valid lifecycle action is available.') }}</p></div><button class="text-button" type="button" :disabled="!!busy || loading" @click="load">{{ t('Refresh') }}</button></div>
      <div class="transfer-list-controls"><label>{{ t('Filter by status') }}<select :value="statusFilter" @change="filterStatus"><option value="ALL">{{ t('All') }} ({{ statusCounts.ALL }})</option><option value="DRAFT">{{ t('Draft') }} ({{ statusCounts.DRAFT }})</option><option value="DISPATCHED">{{ t('Dispatched / In transit') }} ({{ statusCounts.DISPATCHED }})</option><option value="RECEIVED">{{ t('Received at destination') }} ({{ statusCounts.RECEIVED }})</option></select></label></div>
      <div class="catalog-table-wrap" tabindex="0" :aria-label="t('Transfer history table')"><table class="catalog-inventory-table"><thead><tr><th>{{ t('Route') }}</th><th>{{ t('Items') }}</th><th>{{ t('Requested quantity') }}</th><th>{{ t('Dispatched quantity') }}</th><th>{{ t('Received quantity') }}</th><th>{{ t('Status') }}</th><th>{{ t('Timeline') }}</th><th>{{ t('Action') }}</th></tr></thead><tbody>
        <tr v-for="transfer in visibleTransfers" :key="transfer.transferId"><th scope="row">{{ transfer.sourceLocationCode }} → {{ transfer.destinationLocationCode }}<small>{{ transfer.transferId }}</small></th><td><span v-for="line in transfer.items" :key="line.variantId">{{ line.sku }}<br></span></td><td><span v-for="line in transfer.items" :key="line.variantId">{{ line.requestedQuantity }}<br></span></td><td><span v-for="line in transfer.items" :key="line.variantId">{{ line.dispatchedQuantity }}<br></span></td><td><span v-for="line in transfer.items" :key="line.variantId">{{ line.receivedQuantity }}<br></span></td><td>{{ t(statusCopy(transfer.status)) }}</td><td><small>{{ t('Created at') }}: {{ formatDateTime(transfer.createdAt) }}<template v-if="transfer.dispatchedAt"><br>{{ t('Dispatched at') }}: {{ formatDateTime(transfer.dispatchedAt) }}</template><template v-if="transfer.receivedAt"><br>{{ t('Received at') }}: {{ formatDateTime(transfer.receivedAt) }}</template></small></td><td><button v-if="transfer.status === 'DRAFT'" type="button" :disabled="!!busy" @click="dispatch(transfer.transferId)">{{ t(busy === `dispatch:${transfer.transferId}` ? 'Dispatching…' : 'Dispatch') }}</button><button v-else-if="transfer.status === 'DISPATCHED'" type="button" :disabled="!!busy" @click="receive(transfer.transferId)">{{ t(busy === `receive:${transfer.transferId}` ? 'Receiving…' : 'Receive') }}</button><span v-else>{{ t('Complete') }}</span></td></tr>
        <tr v-if="!visibleTransfers.length"><td colspan="8">{{ t(transfers.length ? 'No transfers match this status.' : 'No transfers yet.') }}</td></tr>
      </tbody></table></div>
    </section>
  </section>
</template>
