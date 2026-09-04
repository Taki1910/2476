<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { api, ApiError, type PosReceipt, type PosRegister, type PosShift, type PosVariant } from '../api'
import { formatDateTime, formatVnd, posErrorCopy } from '../format'
import { messageLabel, t } from '../i18n'

const registers = ref<PosRegister[]>([])
const shift = ref<PosShift>()
const selectedRegister = ref('')
const sku = ref('')
const variant = ref<PosVariant>()
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
const skuInput = ref<HTMLInputElement>()
const receiptPanel = ref<HTMLElement>()

const canSell = computed(() => variant.value && variant.value.available > 0 && !selling.value)

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
      await nextTick()
      skuInput.value?.focus()
    }
  }
}

async function openShift() {
  if (!selectedRegister.value) return
  opening.value = true
  error.value = ''
  try {
    shift.value = await api.openPosShift(selectedRegister.value)
    await nextTick()
    skuInput.value?.focus()
  } catch (reason) {
    error.value = posErrorCopy(reason)
  } finally {
    opening.value = false
  }
}

async function lookup() {
  if (!shift.value || !sku.value.trim()) return
  lookingUp.value = true
  lookupError.value = ''
  saleError.value = ''
  receipt.value = undefined
  shiftWarning.value = ''
  variant.value = undefined
  try {
    variant.value = await api.posVariant(shift.value.id, sku.value.trim())
    saleKey.value = crypto.randomUUID()
  } catch (reason) {
    lookupError.value = posErrorCopy(reason)
  } finally {
    lookingUp.value = false
  }
}

async function sell() {
  if (!shift.value || !variant.value || !saleKey.value) return
  if (!window.confirm(t('Confirm exact cash of {amount} for {sku}, size {size}? This immediately hands over one pair.', { amount: formatVnd(variant.value.amount), sku: variant.value.sku, size: variant.value.size }))) return
  selling.value = true
  saleError.value = ''
  try {
    receipt.value = await api.sellPos(shift.value.id, variant.value.id, saleKey.value)
    await nextTick()
    receiptPanel.value?.focus()
  } catch (reason) {
    saleError.value = posErrorCopy(reason)
    const code = reason instanceof ApiError ? reason.code : ''
    if (['INSUFFICIENT_INVENTORY', 'IDEMPOTENCY_KEY_CONFLICT'].includes(code)) {
      variant.value = undefined
      saleKey.value = ''
      await nextTick()
      skuInput.value?.focus()
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
    sku.value = ''
    saleKey.value = ''
    error.value = t('Shift closed. Expected cash: {amount}.', { amount: formatVnd(closed.expectedCash) })
  } catch (reason) {
    error.value = posErrorCopy(reason)
  } finally {
    closing.value = false
  }
}

async function nextSale() {
  sku.value = ''
  variant.value = undefined
  receipt.value = undefined
  saleKey.value = ''
  lookupError.value = ''
  saleError.value = ''
  shiftWarning.value = ''
  await nextTick()
  skuInput.value?.focus()
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
        <label for="register">{{ t('Register lane') }}</label>
        <select id="register" v-model="selectedRegister" :disabled="opening || registers.length === 0" required>
          <option value="" disabled>{{ t('Select a register') }}</option>
          <option v-for="register in registers" :key="register.id" :value="register.id">{{ register.code }} · {{ t(register.locationName) }}</option>
        </select>
        <p v-if="registers.length === 0" class="field-help">{{ t('No enabled register is available in your assigned locations.') }}</p>
        <button class="primary-button" type="submit" :disabled="opening || !selectedRegister">{{ t(opening ? 'Opening shift…' : 'Open shift') }}</button>
      </form>
    </section>

    <template v-else>
      <p v-if="error" class="pos-notice" role="status">{{ messageLabel(error) }}</p>
      <div class="pos-workbench">
        <section class="sale-station" aria-labelledby="sale-title">
          <div class="station-heading"><h2 id="sale-title">{{ t('Find the pair') }}</h2><span>{{ t('Quantity') }} 1</span></div>
          <template v-if="!receipt">
            <form class="sku-search" @submit.prevent="lookup">
              <label for="sku">{{ t('Scan or enter the exact SKU') }}</label>
              <div>
                <input id="sku" ref="skuInput" v-model="sku" name="sku" autocomplete="off" autocapitalize="characters" maxlength="64" :disabled="lookingUp || selling" aria-describedby="sku-help" required />
                <button type="submit" :disabled="lookingUp || selling || !sku.trim()">{{ t(lookingUp ? 'Checking…' : 'Check price & stock') }}</button>
              </div>
              <p id="sku-help" class="field-help">{{ t('Use the complete variant SKU on the label, for example DEMO-CC-39. Product names and partial SKUs are not supported.') }}</p>
            </form>
            <p v-if="lookupError" class="form-error" role="alert">{{ messageLabel(lookupError) }}</p>

            <article v-if="variant" class="sale-line" aria-live="polite">
              <div class="sale-product"><strong>{{ variant.productName }}</strong><small>{{ variant.sku }}</small><small>{{ t('Size') }} {{ variant.size }} · {{ t(variant.color) }}</small></div>
              <div><span>{{ t('Available here') }}</span><strong>{{ variant.available }}</strong><small>{{ t('Authoritative now') }}</small></div>
              <div class="sale-price"><span>{{ t('Exact cash') }}</span><strong>{{ formatVnd(variant.amount) }}</strong><small>{{ t('Server price · VND') }}</small></div>
            </article>

            <div v-if="variant" class="sale-commit">
              <p v-if="variant.available > 0"><strong>{{ t('Confirm only after receiving exact cash.') }}</strong><span>{{ t('This completes a paid order and hands over one pair immediately.') }}</span></p>
              <p v-else><strong>{{ t('Out of stock at this register.') }}</strong><span>{{ t('No cash was taken and no order was created.') }}</span></p>
              <button type="button" :disabled="!canSell" @click="sell">{{ selling ? t('Completing sale…') : t('Take {amount} & complete sale', { amount: formatVnd(variant.amount) }) }}</button>
            </div>
            <p v-if="saleError" class="form-error" role="alert">{{ messageLabel(saleError) }}</p>
          </template>

          <article v-if="receipt" ref="receiptPanel" class="pos-receipt" tabindex="-1" aria-labelledby="receipt-title">
            <div class="receipt-mark" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m5 12 4 4L19 6" /></svg></div>
            <div>
              <h2 id="receipt-title">{{ t('Sale complete.') }}</h2>
              <p>{{ t('{sku} · Size {size} has been handed over.', { sku: receipt.sku, size: receipt.size }) }}</p>
              <dl>
                <div><dt>{{ t('Total') }}</dt><dd>{{ formatVnd(receipt.total) }}</dd></div>
                <div><dt>{{ t('Tender') }}</dt><dd>{{ t('Exact cash') }}</dd></div>
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
  </div>
</template>
