<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, type Order } from '../api'
import { errorCopy, formatDateTime, formatVnd } from '../format'
import { commerceCount, messageLabel, t } from '../i18n'
import CommerceItems from '../components/CommerceItems.vue'
import { paymentDestination, recoverablePayment } from '../checkout'

const route = useRoute()
const order = ref<Order>()
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const statusRegion = ref<HTMLElement>()
const cancelDialog = ref<HTMLDialogElement>()
const orderId = computed(() => String(route.params.id))
let disposed = false

const fulfillmentLabel = { PENDING: 'Waiting for acceptance', PICKING: 'Being prepared', PREPARED: 'Ready', OUT_FOR_DELIVERY: 'Out for delivery', DELIVERED: 'Delivered', HANDED_OVER: 'Picked up', CANCELLED: 'Cancelled' }
const refundLabel = { PROCESSING: 'Refund processing', SUCCEEDED: 'Payment refunded', FAILED_RETRYABLE: 'Refund retry needed', UNKNOWN: 'Refund confirmation pending', REVIEW_REQUIRED: 'Refund under review' }
const paymentLabel = { PENDING_PAYMENT: 'Payment needed', PAID: 'Paid', CANCELLED: 'Order cancelled' }

const copy = computed(() => ({
  PENDING_PAYMENT: ['Payment needed', 'Your items are held only until the payment deadline.'],
  CANCELLED: ['Order cancelled', 'The unpaid stock hold has ended. No refund was needed.'],
  PAID_WAITING_PREPARATION: ['Paid — waiting for preparation', order.value?.fulfillmentType === 'DELIVERY'
    ? 'The store has your paid items reserved and will prepare them for delivery.'
    : 'The store has your paid items reserved and will prepare them for pickup.'],
  READY_FOR_PICKUP: ['Ready for pickup', t('Collect it at {location} and bring your order reference.', { location: `${t(order.value?.locationName ?? '')} (${order.value?.locationCode})` })],
  READY_FOR_DISPATCH: ['Ready for dispatch', 'Your complete order is prepared and waiting to leave the store.'],
  OUT_FOR_DELIVERY: ['Out for delivery', 'Your complete order has left the store. Direct cancellation is no longer available.'],
  DELIVERED: ['Delivered', 'The delivery was recorded as received.'],
  PICKED_UP: ['Picked up', 'The store recorded physical handover. Cancellation is no longer available.'],
  CANCELLATION_PROCESSING: ['Cancellation accepted', 'The order will not be handed over. We are confirming your full VNPAY refund.'],
  CANCELLED_PAYMENT_REVERSED: ['Cancelled — payment refunded', 'VNPAY confirmed the full refund.'],
  CANCELLED_REVERSAL_FAILED: ['Cancelled — refund needs retry', 'The order remains cancelled, but the last VNPAY refund attempt failed. You can retry below.'],
  CANCELLED_REVERSAL_REVIEW: ['Cancelled — refund under review', 'The order remains cancelled while the store confirms the VNPAY refund result.'],
})[order.value?.pickupStatus ?? 'PENDING_PAYMENT']?.map(value => t(value)))

async function load() {
  loading.value = true; error.value = ''
  const requestedId = orderId.value
  try { const result = await api.order(requestedId); if (requestedId === orderId.value && !disposed) order.value = result }
  catch (reason) { error.value = errorCopy(reason) }
  finally { loading.value = false }
}
async function pay() {
  if (!order.value || order.value.status !== 'PENDING_PAYMENT' || busy.value) return
  busy.value = true; error.value = ''
  const requestedId = order.value.id
  try { const result = await recoverablePayment(requestedId); if (!disposed && requestedId === orderId.value) window.location.assign(paymentDestination(result)) }
  catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = false }
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
    error.value = error.value ? t('{actionError} Current status could not be refreshed: {error}', { actionError, error: error.value }) : actionError
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
watch(orderId, () => { order.value = undefined; load() })
onMounted(load)
onBeforeUnmount(() => { disposed = true })
</script>

<template>
  <div class="order-status-page">
    <RouterLink class="back-link" to="/orders"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>{{ t('My Orders') }}</RouterLink>
    <div v-if="loading" class="queue-loading" role="status"><span class="loader-mark"></span>{{ t('Loading authoritative order status…') }}</div>
    <section v-else-if="error && !order" class="inline-state" role="alert"><h1>{{ t('Order unavailable') }}</h1><p>{{ messageLabel(error) }}</p><button class="text-button" @click="load">{{ t('Try again') }}</button></section>
    <section v-else-if="order" ref="statusRegion" class="order-status-sheet" tabindex="-1" aria-labelledby="order-status-title">
      <h1 id="order-status-title">{{ copy?.[0] }}</h1><p class="status-lede">{{ copy?.[1] }}</p>
      <dl>
        <div><dt>{{ t('Total units') }}</dt><dd>{{ commerceCount(order.itemCount, order.quantity) }}</dd></div>
        <div><dt>{{ t('Order amount') }}</dt><dd>{{ formatVnd(order.totalAmount) }}</dd></div>
        <div><dt>{{ t('Order reference') }}</dt><dd>{{ order.orderReference }}</dd></div>
        <div><dt>{{ t(order.fulfillmentType === 'DELIVERY' ? 'Dispatch location' : 'Pickup location') }}</dt><dd>{{ t(order.locationName) }} · {{ order.locationCode }}</dd></div>
        <div><dt>{{ t('Fulfillment type') }}</dt><dd>{{ t(order.fulfillmentType === 'DELIVERY' ? 'Delivery' : 'Pickup') }}</dd></div>
        <div v-if="order.receiverName"><dt>{{ t('Receiver') }}</dt><dd>{{ order.receiverName }} · {{ order.receiverPhone }}</dd></div>
        <div v-if="order.deliveryAddress"><dt>{{ t('Delivery address') }}</dt><dd>{{ order.deliveryAddress }}</dd></div>
        <div v-if="order.deliveryNote"><dt>{{ t('Delivery note') }}</dt><dd>{{ order.deliveryNote }}</dd></div>
        <div v-if="order.fulfillmentType === 'DELIVERY'"><dt>{{ t('Delivery fee') }}</dt><dd>{{ formatVnd(order.deliveryFeeAmount) }}</dd></div>
        <div><dt>{{ t('Payment status') }}</dt><dd>{{ t(paymentLabel[order.status]) }}</dd></div>
        <div v-if="order.paidAt"><dt>{{ t('Paid') }}</dt><dd>{{ formatDateTime(order.paidAt) }}</dd></div>
        <div v-if="order.fulfillmentStatus"><dt>{{ t('Fulfillment progress') }}</dt><dd>{{ t(fulfillmentLabel[order.fulfillmentStatus]) }}</dd></div>
        <div v-if="order.acceptedAt"><dt>{{ t('Accepted') }}</dt><dd>{{ formatDateTime(order.acceptedAt) }}</dd></div>
        <div v-if="order.readyAt"><dt>{{ t('Ready') }}</dt><dd>{{ formatDateTime(order.readyAt) }}</dd></div>
        <div v-if="order.dispatchedAt"><dt>{{ t('Dispatched') }}</dt><dd>{{ formatDateTime(order.dispatchedAt) }}</dd></div>
        <div v-if="order.deliveredAt"><dt>{{ t('Delivered') }}</dt><dd>{{ formatDateTime(order.deliveredAt) }}</dd></div>
        <div v-if="order.handedOverAt"><dt>{{ t('Handed over') }}</dt><dd>{{ formatDateTime(order.handedOverAt) }}</dd></div>
        <div v-if="order.financialVoidStatus"><dt>{{ t('Payment refund') }}</dt><dd>{{ t(refundLabel[order.financialVoidStatus]) }}</dd></div>
      </dl>
      <CommerceItems :items="order.items" />
      <p v-if="error" class="form-error" role="alert">{{ messageLabel(error) }}</p>
      <div class="order-status-actions">
        <button v-if="order.status === 'PENDING_PAYMENT'" class="primary-button" type="button" :disabled="busy" @click="pay">{{ t(busy ? 'Opening VNPAY…' : 'Pay with VNPAY') }}</button>
        <button v-if="order.cancellationEligible" class="cancel-confirmed-button" type="button" :disabled="busy" @click="askCancel">{{ t(order.fulfillmentType === 'DELIVERY' ? 'Cancel before dispatch' : 'Cancel before pickup') }}</button>
        <button v-if="order.financialVoidStatus === 'FAILED_RETRYABLE'" class="primary-button" type="button" :disabled="busy" @click="retryVoid">{{ t(busy ? 'Retrying refund…' : 'Retry VNPAY refund') }}</button>
        <button class="text-button" type="button" :disabled="busy" @click="load">{{ t('Refresh status') }}</button>
        <RouterLink class="text-button" to="/">{{ t('Continue shopping') }}</RouterLink>
      </div>
    </section>
    <dialog v-if="order" ref="cancelDialog" class="terminal-dialog" aria-labelledby="cancel-dialog-title" aria-describedby="cancel-dialog-description" @cancel="closeDialog">
      <form method="dialog" @submit.prevent>
        <h2 id="cancel-dialog-title">{{ t('Cancel order {reference}?', { reference: order.orderReference }) }}</h2>
        <p id="cancel-dialog-description">{{ t(order.fulfillmentType === 'DELIVERY' ? 'This cancels every item before dispatch and requests a full VNPAY refund. Refund confirmation may take time or require review.' : 'This cancels every item before handover and requests a full VNPAY refund. Refund confirmation may take time or require review.') }}</p>
        <div><button class="text-button" type="button" @click="closeDialog">{{ t('Keep order') }}</button><button class="cancel-confirmed-button" type="button" @click="cancel">{{ t('Yes, cancel order') }}</button></div>
      </form>
    </dialog>
  </div>
</template>
