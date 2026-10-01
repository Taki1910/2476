<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { api, ApiError, type PosReceipt, type PosRegister, type PosShift, type PosVariant } from '../api'
import { formatDateTime, formatVnd, posErrorCopy } from '../format'
import { messageLabel, t } from '../i18n'
import { acceptScannedCandidate, candidateStateLabel, moveCandidateSelection, selectedCandidate } from '../pos-workflow'

const registers = ref<PosRegister[]>([])
const shift = ref<PosShift>()
const selectedRegister = ref('')
const lookupMode = ref<'scan' | 'search'>('scan')
const lookupText = ref('')
const variant = ref<PosVariant>()
const candidates = ref<PosVariant[]>([])
const activeCandidate = ref(-1)
const searched = ref(false)
const receipt = ref<PosReceipt>()
const loading = ref(true)
const opening = ref(false)
const lookingUp = ref(false)
const selling = ref(false)
const closing = ref(false)
const error = ref('')
const lookupError = ref('')
const saleError = ref('')
const shiftWarning = ref('')
const saleKey = ref('')
const lookupInput = ref<HTMLInputElement>()
const receiptPanel = ref<HTMLElement>()
const confirmDialog = ref<HTMLDialogElement>()

const canSell = computed(() => variant.value?.saleState === 'SELLABLE'
  && variant.value.amount !== null && !!variant.value.priceVersionId && !selling.value)
const stateHelp: Record<PosVariant['saleState'], string> = {
  SELLABLE: 'This pair can be sold from the active register location.',
  SOLD_OUT_HERE: 'This item exists, but it is sold out at this register location.',
  RETIRED: 'This size/color has been retired.',
  NOT_PUBLISHED: 'This size/color is not published for sale.',
  PRICE_UNAVAILABLE: 'This size/color has no current selling price.',
}

async function focusLookup() {
  await nextTick()
  lookupInput.value?.focus()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [current, accessible] = await Promise.all([api.currentPosShift(), api.posRegisters()])
    shift.value = current
    registers.value = accessible
    selectedRegister.value = current?.register.id ?? accessible[0]?.id ?? ''
  } catch (reason) {
    error.value = posErrorCopy(reason)
  } finally {
    loading.value = false
    if (shift.value) {
      await focusLookup()
    }
  }
}

async function openShift() {
  if (!selectedRegister.value) return
  opening.value = true
  error.value = ''
  try {
    shift.value = await api.openPosShift(selectedRegister.value)
    await focusLookup()
  } catch (reason) {
    error.value = posErrorCopy(reason)
  } finally {
    opening.value = false
  }
}

function resetLookupResult() {
  lookupError.value = ''
  saleError.value = ''
  receipt.value = undefined
  shiftWarning.value = ''
  variant.value = undefined
  candidates.value = []
  activeCandidate.value = -1
  searched.value = false
  saleKey.value = ''
}

async function setLookupMode(mode: 'scan' | 'search') {
  lookupMode.value = mode
  lookupText.value = ''
  resetLookupResult()
  await focusLookup()
}

function chooseCandidate(candidate: PosVariant) {
  variant.value = candidate
  saleKey.value = crypto.randomUUID()
}

async function resolveBarcode() {
  if (!shift.value || !lookupText.value.trim()) return
  lookingUp.value = true
  resetLookupResult()
  try {
    const resolved = await api.posBarcode(shift.value.id, lookupText.value.trim())
    chooseCandidate(acceptScannedCandidate(variant.value, resolved).candidate)
    lookupText.value = ''
  } catch (reason) {
    lookupError.value = posErrorCopy(reason)
  } finally {
    lookingUp.value = false
  }
}

async function searchProducts() {
  if (!shift.value || !lookupText.value.trim()) return
  lookingUp.value = true
  resetLookupResult()
  try {
    candidates.value = await api.searchPosVariants(shift.value.id, lookupText.value.trim())
    activeCandidate.value = candidates.value.length ? 0 : -1
    searched.value = true
  } catch (reason) {
    lookupError.value = posErrorCopy(reason)
  } finally {
    lookingUp.value = false
  }
}

function submitLookup() {
  return lookupMode.value === 'scan' ? resolveBarcode() : searchProducts()
}

function handleSearchKeydown(event: KeyboardEvent) {
  if (lookupMode.value !== 'search' || !candidates.value.length) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    activeCandidate.value = moveCandidateSelection(activeCandidate.value, candidates.value.length, event.key)
  } else {
    const selected = selectedCandidate(candidates.value, activeCandidate.value, event.key)
    if (selected) {
      event.preventDefault()
      chooseCandidate(selected)
    }
  }
}

function askSale() {
  if (!canSell.value) return
  confirmDialog.value?.showModal()
}
function closeDialog() { confirmDialog.value?.close() }
async function sell() {
  if (!shift.value || !variant.value?.priceVersionId || !saleKey.value) return
  closeDialog()
  selling.value = true
  saleError.value = ''
  try {
    receipt.value = await api.sellPos(shift.value.id, variant.value.id, variant.value.priceVersionId, saleKey.value)
    await nextTick()
    receiptPanel.value?.focus()
  } catch (reason) {
    saleError.value = posErrorCopy(reason)
    const code = reason instanceof ApiError ? reason.code : ''
    if (['POS_VARIANT_NOT_FOUND', 'POS_SOLD_OUT_HERE', 'POS_PRICE_CHANGED', 'POS_VARIANT_RETIRED', 'POS_VARIANT_NOT_PUBLISHED',
      'POS_PRICE_UNAVAILABLE', 'IDEMPOTENCY_KEY_CONFLICT'].includes(code)) {
      variant.value = undefined
      candidates.value = []
      activeCandidate.value = -1
      searched.value = false
      saleKey.value = ''
      await focusLookup()
    } else if (['SHIFT_CLOSED', 'REGISTER_UNAVAILABLE'].includes(code)) {
      error.value = saleError.value
      shift.value = undefined
      variant.value = undefined
      saleKey.value = ''
    }
    selling.value = false
    return
  }
  selling.value = false
  try {
    shift.value = await api.currentPosShift()
  } catch {
    shiftWarning.value = t('Sale recorded. The shift total could not refresh; refresh the page before closing the shift.')
  }
}

async function closeShift() {
  if (!shift.value) return
  closing.value = true
  error.value = ''
  try {
    const closed = await api.closePosShift(shift.value.id)
    shift.value = undefined
    variant.value = undefined
    receipt.value = undefined
    lookupText.value = ''
    saleKey.value = ''
    candidates.value = []
    error.value = t('Shift closed. Expected cash: {amount}.', { amount: formatVnd(closed.expectedCash) })
  } catch (reason) {
    error.value = posErrorCopy(reason)
  } finally {
    closing.value = false
  }
}

async function nextSale() {
  lookupText.value = ''
  variant.value = undefined
  candidates.value = []
  activeCandidate.value = -1
  receipt.value = undefined
  saleKey.value = ''
  lookupError.value = ''
  saleError.value = ''
  shiftWarning.value = ''
  await focusLookup()
}

onMounted(load)
</script>

<template>
  <div class="pos-page">
    <header class="pos-heading">
      <div>
        <h1>{{ t('Sell one pair.') }}</h1>
        <p>{{ t('Server price, location stock, exact cash. One transaction at a time.') }}</p>
      </div>
      <dl v-if="shift" class="shift-strip">
        <div><dt>{{ t('Register lane') }}</dt><dd>{{ shift.register.code }}</dd></div>
        <div><dt>{{ t('Location name') }}</dt><dd>{{ t(shift.register.locationName) }}</dd></div>
        <div><dt>{{ t('Expected cash') }}</dt><dd>{{ formatVnd(shift.expectedCash) }}</dd></div>
      </dl>
    </header>

    <div v-if="loading" class="queue-loading" role="status" aria-live="polite"><span class="loader-mark"></span>{{ t('Loading the register…') }}</div>

    <section v-else-if="!shift" class="shift-start" aria-labelledby="shift-title">
      <div>
        <h2 id="shift-title">{{ t('Open a cashier shift') }}</h2>
        <p>{{ t('Your active location assignment determines which registers you may use.') }}</p>
      </div>
      <form @submit.prevent="openShift">
        <p v-if="registers[0]" class="field-help"><strong>{{ t('Work location') }}:</strong> {{ registers[0].locationCode }} · {{ t(registers[0].locationName) }}</p>
        <label for="register">{{ t('Register lane') }}</label>
        <select id="register" v-model="selectedRegister" :disabled="opening || registers.length === 0" required>
          <option value="" disabled>{{ t('Select a register') }}</option>
          <option v-for="register in registers" :key="register.id" :value="register.id">{{ register.code }} · {{ t(register.locationName) }}</option>
        </select>
        <p v-if="registers.length === 0" class="field-help">{{ t('No enabled register is available in your assigned locations.') }}</p>
        <p v-else class="field-help">{{ t('Only active registers at your assigned locations are shown.') }}</p>
        <button class="primary-button" type="submit" :disabled="opening || !selectedRegister">{{ t(opening ? 'Opening shift…' : 'Open shift') }}</button>
      </form>
    </section>

    <template v-else>
      <p v-if="error" class="pos-notice" role="status">{{ messageLabel(error) }}</p>
      <div class="pos-workbench">
        <section class="sale-station" aria-labelledby="sale-title">
          <div class="station-heading"><h2 id="sale-title">{{ t('Find the pair') }}</h2><span>{{ t('Quantity') }} 1</span></div>
          <template v-if="!receipt">
            <div class="lookup-modes" role="group" :aria-label="t('Lookup mode')">
              <button type="button" :aria-pressed="lookupMode === 'scan'" @click="setLookupMode('scan')">{{ t('Scan barcode') }}</button>
              <button type="button" :aria-pressed="lookupMode === 'search'" @click="setLookupMode('search')">{{ t('Search product') }}</button>
            </div>
            <form class="sku-search" @submit.prevent="submitLookup">
              <label for="pos-lookup">{{ t(lookupMode === 'scan' ? 'Barcode' : 'Product name, SKU, color, or EU size') }}</label>
              <div>
                <input id="pos-lookup" ref="lookupInput" v-model="lookupText" name="lookup" autocomplete="off" :maxlength="lookupMode === 'scan' ? 128 : 80" :disabled="lookingUp || selling" aria-describedby="lookup-help" required @input="resetLookupResult" @keydown="handleSearchKeydown" />
                <button type="submit" :disabled="lookingUp || selling || !lookupText.trim()">{{ t(lookingUp ? 'Checking…' : lookupMode === 'scan' ? 'Resolve barcode' : 'Search') }}</button>
              </div>
              <p id="lookup-help" class="field-help">{{ t(lookupMode === 'scan' ? 'Scan a barcode and press Enter. Sale never starts automatically.' : 'Search by product name, SKU, color, EU size, or combined text.') }}</p>
            </form>
            <p v-if="lookupError" class="form-error" role="alert">{{ messageLabel(lookupError) }}</p>

            <section v-if="lookupMode === 'search' && candidates.length" class="pos-candidates" aria-labelledby="candidate-title">
              <h3 id="candidate-title">{{ t('Search results') }}</h3>
              <div class="candidate-list">
                <button v-for="(candidate, index) in candidates" :key="candidate.id" type="button"
                  :aria-pressed="variant?.id === candidate.id" :data-active="activeCandidate === index"
                  @focus="activeCandidate = index" @click="chooseCandidate(candidate)">
                  <span><strong>{{ candidate.productName }}</strong><small>{{ candidate.sku }} · {{ t('Size') }} {{ candidate.size }} · {{ t(candidate.color) }}</small></span>
                  <span><strong>{{ t(candidateStateLabel(candidate.saleState)) }}</strong><small>{{ candidate.locationCode }}</small></span>
                </button>
              </div>
            </section>
            <p v-else-if="lookupMode === 'search' && searched && !lookingUp && !lookupError" class="field-help">{{ t('No matching products.') }}</p>

            <article v-if="variant" class="sale-line" aria-live="polite">
              <div class="sale-product"><strong>{{ variant.productName }}</strong><small>{{ variant.sku }}</small><small>{{ t('Size') }} {{ variant.size }} · {{ t(variant.color) }}</small></div>
              <div><span>{{ t('Sale status') }}</span><strong>{{ t(candidateStateLabel(variant.saleState)) }}</strong><small>{{ t(stateHelp[variant.saleState]) }}</small></div>
              <div class="sale-price"><span>{{ t('Exact cash') }}</span><strong>{{ variant.amount === null ? '—' : formatVnd(variant.amount) }}</strong><small>{{ t('Server price · VND') }}</small></div>
            </article>

            <div v-if="variant" class="sale-commit">
              <p v-if="canSell"><strong>{{ t('Confirm only after receiving exact cash.') }}</strong><span>{{ t('This completes a paid order and hands over one pair immediately.') }}</span></p>
              <p v-else><strong>{{ t(candidateStateLabel(variant.saleState)) }}</strong><span>{{ t(stateHelp[variant.saleState]) }} {{ t('No cash was taken and no order was created.') }}</span></p>
              <button type="button" :disabled="!canSell" @click="askSale">{{ selling ? t('Completing sale…') : t('Take {amount} & complete sale', { amount: variant.amount === null ? '—' : formatVnd(variant.amount) }) }}</button>
            </div>
            <p v-if="saleError" class="form-error" role="alert">{{ messageLabel(saleError) }}</p>
          </template>

          <article v-if="receipt" ref="receiptPanel" class="pos-receipt" tabindex="-1" aria-labelledby="receipt-title">
            <div class="receipt-mark" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m5 12 4 4L19 6" /></svg></div>
            <div>
              <h2 id="receipt-title">{{ t('Sale complete.') }}</h2>
              <p>{{ t('{sku} · Size {size} · {color} has been handed over.', { sku: receipt.sku, size: receipt.size, color: receipt.color }) }}</p>
              <dl>
                <div><dt>{{ t('Register lane') }}</dt><dd>{{ receipt.registerCode }}</dd></div>
                <div><dt>{{ t('Location name') }}</dt><dd>{{ receipt.locationCode }} · {{ t(receipt.locationName) }}</dd></div>
                <div><dt>{{ t('SKU') }}</dt><dd>{{ receipt.sku }}</dd></div>
                <div><dt>{{ t('Color') }}</dt><dd>{{ t(receipt.color) }}</dd></div>
                <div><dt>{{ t('EU size') }}</dt><dd>{{ receipt.size }}</dd></div>
                <div><dt>{{ t('Quantity') }}</dt><dd>{{ receipt.quantity }}</dd></div>
                <div><dt>{{ t('Unit price') }}</dt><dd>{{ formatVnd(receipt.unitPrice) }}</dd></div>
                <div><dt>{{ t('Total') }}</dt><dd>{{ formatVnd(receipt.total) }}</dd></div>
                <div><dt>{{ t('Tender') }}</dt><dd>{{ t('Cash payment') }}</dd></div>
                <div><dt>{{ t('Sold') }}</dt><dd>{{ formatDateTime(receipt.soldAt) }}</dd></div>
                <div><dt>{{ t('Order') }}</dt><dd>{{ receipt.orderId }}</dd></div>
              </dl>
              <p v-if="shiftWarning" class="receipt-warning" role="status">{{ messageLabel(shiftWarning) }}</p>
              <button class="primary-button" type="button" @click="nextSale">{{ t('Start next sale') }}</button>
            </div>
          </article>
        </section>

        <aside class="shift-rail" aria-labelledby="shift-rail-title">
          <h2 id="shift-rail-title">{{ t('Current shift') }}</h2>
          <dl>
            <div><dt>{{ t('Opened') }}</dt><dd>{{ formatDateTime(shift.openedAt) }}</dd></div>
            <div><dt>{{ t('Register lane') }}</dt><dd>{{ shift.register.code }}</dd></div>
            <div><dt>{{ t('Location code') }}</dt><dd>{{ shift.register.locationCode }}</dd></div>
            <div><dt>{{ t('Expected cash') }}</dt><dd>{{ formatVnd(shift.expectedCash) }}</dd></div>
          </dl>
          <p class="field-help">{{ t('Expected cash is accepted cash within a cashier shift. Actual counted cash and the difference are not recorded by this workflow.') }}</p>
          <details class="close-shift">
            <summary>{{ t('Close cashier shift') }}</summary>
            <p>{{ t('Finish the active sale first. Closing prevents any new sale on this shift.') }}</p>
            <button type="button" :disabled="closing || selling" @click="closeShift">{{ t(closing ? 'Closing shift…' : 'Confirm close shift') }}</button>
          </details>
        </aside>
      </div>
    </template>

    <section v-if="error && !shift" class="inline-state" role="status"><h3>{{ t('Register status') }}</h3><p>{{ messageLabel(error) }}</p><button class="text-button" type="button" @click="load">{{ t('Refresh') }}</button></section>

    <dialog v-if="variant" ref="confirmDialog" class="terminal-dialog" aria-labelledby="pos-dialog-title" aria-describedby="pos-dialog-description" @cancel="closeDialog">
      <form method="dialog" @submit.prevent>
        <h2 id="pos-dialog-title">{{ t('Confirm cash sale') }}</h2>
        <p id="pos-dialog-description">{{ t('Confirm exact cash of {amount} for {sku}, size {size}? This immediately hands over one pair.', { amount: variant.amount === null ? '—' : formatVnd(variant.amount), sku: variant.sku, size: variant.size }) }}</p>
        <div><button class="text-button" type="button" @click="closeDialog">{{ t('Cancel') }}</button><button class="primary-button" type="button" @click="sell">{{ t('Complete sale') }}</button></div>
      </form>
    </dialog>
  </div>
</template>
