<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { api, ApiError, type PosReceipt, type PosRegister, type PosShift, type PosVariant } from '../api'
import { formatDateTime, formatVnd, posErrorCopy } from '../format'

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
  if (!window.confirm(`Confirm exact cash of ${formatVnd(variant.value.amount)} for ${variant.value.sku}, size ${variant.value.size}? This immediately hands over one pair.`)) return
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
    shiftWarning.value = 'Sale recorded. The shift total could not refresh; refresh the page before closing the shift.'
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
    error.value = `Shift closed. Expected cash: ${formatVnd(closed.expectedCash)}.`
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
        <h1>Sell one pair.</h1>
        <p>Server price, location stock, exact cash. One transaction at a time.</p>
      </div>
      <dl v-if="shift" class="shift-strip">
        <div><dt>Register</dt><dd>{{ shift.register.code }}</dd></div>
        <div><dt>Location name</dt><dd>{{ shift.register.locationName }}</dd></div>
        <div><dt>Expected cash</dt><dd>{{ formatVnd(shift.expectedCash) }}</dd></div>
      </dl>
    </header>

    <div v-if="loading" class="queue-loading" role="status" aria-live="polite"><span class="loader-mark"></span>Loading the register…</div>

    <section v-else-if="!shift" class="shift-start" aria-labelledby="shift-title">
      <div>
        <h2 id="shift-title">Open a cashier shift</h2>
        <p>Your active location assignment determines which registers you may use.</p>
      </div>
      <form @submit.prevent="openShift">
        <label for="register">Register</label>
        <select id="register" v-model="selectedRegister" :disabled="opening || registers.length === 0" required>
          <option value="" disabled>Select a register</option>
          <option v-for="register in registers" :key="register.id" :value="register.id">{{ register.code }} · {{ register.locationName }}</option>
        </select>
        <p v-if="registers.length === 0" class="field-help">No enabled register is available in your assigned locations.</p>
        <button class="primary-button" type="submit" :disabled="opening || !selectedRegister">{{ opening ? 'Opening shift…' : 'Open shift' }}</button>
      </form>
    </section>

    <template v-else>
      <p v-if="error" class="pos-notice" role="status">{{ error }}</p>
      <div class="pos-workbench">
        <section class="sale-station" aria-labelledby="sale-title">
          <div class="station-heading"><h2 id="sale-title">Find the pair</h2><span>Quantity 1</span></div>
          <template v-if="!receipt">
            <form class="sku-search" @submit.prevent="lookup">
              <label for="sku">Scan or enter SKU</label>
              <div>
                <input id="sku" ref="skuInput" v-model="sku" name="sku" autocomplete="off" autocapitalize="characters" maxlength="64" :disabled="lookingUp || selling" required />
                <button type="submit" :disabled="lookingUp || selling || !sku.trim()">{{ lookingUp ? 'Checking…' : 'Check price & stock' }}</button>
              </div>
            </form>
            <p v-if="lookupError" class="form-error" role="alert">{{ lookupError }}</p>

            <article v-if="variant" class="sale-line" aria-live="polite">
              <div class="sale-product"><span>{{ variant.productName }}</span><strong>{{ variant.sku }}</strong><small>Size {{ variant.size }} · {{ variant.color }}</small></div>
              <div><span>Available here</span><strong>{{ variant.available }}</strong><small>Authoritative now</small></div>
              <div class="sale-price"><span>Exact cash</span><strong>{{ formatVnd(variant.amount) }}</strong><small>Server price · VND</small></div>
            </article>

            <div v-if="variant" class="sale-commit">
              <p v-if="variant.available > 0"><strong>Confirm only after receiving exact cash.</strong><span>This completes a paid order and hands over one pair immediately.</span></p>
              <p v-else><strong>Out of stock at this register.</strong><span>No cash was taken and no order was created.</span></p>
              <button type="button" :disabled="!canSell" @click="sell">{{ selling ? 'Completing sale…' : `Take ${formatVnd(variant.amount)} & complete sale` }}</button>
            </div>
            <p v-if="saleError" class="form-error" role="alert">{{ saleError }}</p>
          </template>

          <article v-if="receipt" ref="receiptPanel" class="pos-receipt" tabindex="-1" aria-labelledby="receipt-title">
            <div class="receipt-mark" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="m5 12 4 4L19 6" /></svg></div>
            <div>
              <h2 id="receipt-title">Sale complete.</h2>
              <p>{{ receipt.sku }} · Size {{ receipt.size }} has been handed over.</p>
              <dl>
                <div><dt>Total</dt><dd>{{ formatVnd(receipt.total) }}</dd></div>
                <div><dt>Tender</dt><dd>Exact cash</dd></div>
                <div><dt>Sold</dt><dd>{{ formatDateTime(receipt.soldAt) }}</dd></div>
                <div><dt>Order</dt><dd>{{ receipt.orderId }}</dd></div>
              </dl>
              <p v-if="shiftWarning" class="receipt-warning" role="status">{{ shiftWarning }}</p>
              <button class="primary-button" type="button" @click="nextSale">Start next sale</button>
            </div>
          </article>
        </section>

        <aside class="shift-rail" aria-labelledby="shift-rail-title">
          <h2 id="shift-rail-title">Current shift</h2>
          <dl>
            <div><dt>Opened</dt><dd>{{ formatDateTime(shift.openedAt) }}</dd></div>
            <div><dt>Register</dt><dd>{{ shift.register.code }}</dd></div>
            <div><dt>Location code</dt><dd>{{ shift.register.locationCode }}</dd></div>
            <div><dt>Expected cash</dt><dd>{{ formatVnd(shift.expectedCash) }}</dd></div>
          </dl>
          <details class="close-shift">
            <summary>Close cashier shift</summary>
            <p>Finish the active sale first. Closing prevents any new sale on this shift.</p>
            <button type="button" :disabled="closing || selling" @click="closeShift">{{ closing ? 'Closing shift…' : 'Confirm close shift' }}</button>
          </details>
        </aside>
      </div>
    </template>

    <section v-if="error && !shift" class="inline-state" role="status"><h3>Register status</h3><p>{{ error }}</p><button class="text-button" type="button" @click="load">Refresh</button></section>
  </div>
</template>
