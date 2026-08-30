<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, type Order } from '../api'
import { errorCopy, formatDateTime, formatVnd } from '../format'

const route = useRoute()
const order = ref<Order>()
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const statusRegion = ref<HTMLElement>()
const cancelDialog = ref<HTMLDialogElement>()
const orderId = computed(() => String(route.params.id))

const fulfillmentLabel = { PENDING: 'Waiting for preparation', PICKING: 'Being prepared', PREPARED: 'Ready at store', HANDED_OVER: 'Picked up', CANCELLED: 'Cancelled' }
const refundLabel = { PROCESSING: 'Refund processing', SUCCEEDED: 'Payment refunded', FAILED_RETRYABLE: 'Refund retry needed', UNKNOWN: 'Refund confirmation pending', REVIEW_REQUIRED: 'Refund under review' }

const copy = computed(() => ({
  PENDING_PAYMENT: ['Payment needed', 'Your pair is held only until the payment deadline.'],
  CANCELLED: ['Order cancelled', 'The unpaid stock hold has ended. No refund was needed.'],
  PAID_WAITING_PREPARATION: ['Paid — waiting for preparation', 'The store has your paid pair reserved and will prepare it for pickup.'],
  READY_FOR_PICKUP: ['Ready for pickup', `Collect it at ${order.value?.locationName} (${order.value?.locationCode}) and bring your order reference.`],
  PICKED_UP: ['Picked up', 'The store recorded physical handover. Cancellation is no longer available.'],
  CANCELLATION_PROCESSING: ['Cancellation accepted', 'The order will not be handed over. We are confirming your full VNPAY refund.'],
  CANCELLED_PAYMENT_REVERSED: ['Cancelled — payment refunded', 'VNPAY confirmed the full refund.'],
  CANCELLED_REVERSAL_FAILED: ['Cancelled — refund needs retry', 'The order remains cancelled, but the last VNPAY refund attempt failed. You can retry below.'],
  CANCELLED_REVERSAL_REVIEW: ['Cancelled — refund under review', 'The order remains cancelled while the store confirms the VNPAY refund result.'],
})[order.value?.pickupStatus ?? 'PENDING_PAYMENT'])

async function load() {
  loading.value = true; error.value = ''
  try { order.value = await api.order(orderId.value) }
  catch (reason) { error.value = errorCopy(reason) }
  finally { loading.value = false }
}
function askCancel() { cancelDialog.value?.showModal() }
function closeDialog() { cancelDialog.value?.close() }
async function cancel() {
  if (!order.value || busy.value) return
  busy.value = true; error.value = ''; closeDialog()
  try { await api.cancelConfirmed(order.value.id, crypto.randomUUID()); await load(); await nextTick(); statusRegion.value?.focus() }
  catch (reason) {
    const actionError = errorCopy(reason)
    await load()
    error.value = error.value ? `${actionError} Current status could not be refreshed: ${error.value}` : actionError
  }
  finally { busy.value = false }
}
async function retryVoid() {
  if (!order.value || busy.value) return
  busy.value = true; error.value = ''
  try { await api.retryVoid(order.value.id, crypto.randomUUID()); await load(); await nextTick(); statusRegion.value?.focus() }
  catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="order-status-page">
    <RouterLink class="back-link" to="/"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>Storefront</RouterLink>
    <div v-if="loading" class="queue-loading" role="status"><span class="loader-mark"></span>Loading authoritative order status…</div>
    <section v-else-if="error && !order" class="inline-state" role="alert"><h1>Order unavailable</h1><p>{{ error }}</p><button class="text-button" @click="load">Try again</button></section>
    <section v-else-if="order" ref="statusRegion" class="order-status-sheet" tabindex="-1" aria-labelledby="order-status-title">
      <p class="eyebrow">Pickup order</p><h1 id="order-status-title">{{ copy?.[0] }}</h1><p class="status-lede">{{ copy?.[1] }}</p>
      <dl>
        <div><dt>Pair</dt><dd>{{ order.sku }} · Size {{ order.size }} · Qty {{ order.quantity }}</dd></div>
        <div><dt>Paid total</dt><dd>{{ formatVnd(order.totalAmount) }}</dd></div>
        <div><dt>Order reference</dt><dd>{{ order.id }}</dd></div>
        <div><dt>Pickup location</dt><dd>{{ order.locationName }} · {{ order.locationCode }}</dd></div>
        <div v-if="order.paidAt"><dt>Paid</dt><dd>{{ formatDateTime(order.paidAt) }}</dd></div>
        <div v-if="order.fulfillmentStatus"><dt>Pickup progress</dt><dd>{{ fulfillmentLabel[order.fulfillmentStatus] }}</dd></div>
        <div v-if="order.financialVoidStatus"><dt>Payment refund</dt><dd>{{ refundLabel[order.financialVoidStatus] }}</dd></div>
      </dl>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <div class="order-status-actions">
        <button v-if="order.cancellationEligible" class="cancel-confirmed-button" type="button" :disabled="busy" @click="askCancel">Cancel before pickup</button>
        <button v-if="order.financialVoidStatus === 'FAILED_RETRYABLE'" class="primary-button" type="button" :disabled="busy" @click="retryVoid">{{ busy ? 'Retrying refund…' : 'Retry VNPAY refund' }}</button>
        <button class="text-button" type="button" :disabled="busy" @click="load">Refresh status</button>
      </div>
    </section>
    <dialog v-if="order" ref="cancelDialog" class="terminal-dialog" aria-labelledby="cancel-dialog-title" aria-describedby="cancel-dialog-description" @cancel="closeDialog">
      <form method="dialog" @submit.prevent>
        <p class="eyebrow">Confirmed cancellation</p><h2 id="cancel-dialog-title">Cancel {{ order.sku }}, size {{ order.size }}?</h2>
        <p id="cancel-dialog-description">This is allowed only before handover. The store will stop preparing this pair and request a full VNPAY refund. Refund confirmation may take time or require review.</p>
        <div><button class="text-button" type="button" @click="closeDialog">Keep order</button><button class="cancel-confirmed-button" type="button" @click="cancel">Yes, cancel order</button></div>
      </form>
    </dialog>
  </div>
</template>
