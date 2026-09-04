<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, ApiError, type PickupTask } from '../api'
import { errorCopy, formatDateTime, pickupDisplayState } from '../format'
import { messageLabel, statusLabel, t } from '../i18n'
import CommerceItems from '../components/CommerceItems.vue'

const route = useRoute()
const task = ref<PickupTask>()
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const success = ref('')
const confirmDialog = ref<HTMLDialogElement>()
const heading = ref<HTMLElement>()
const pendingAction = ref<'HANDOVER' | 'DISPATCH' | 'DELIVER'>()
const orderId = computed(() => String(route.params.id))
const displayState = computed(() => task.value ? pickupDisplayState(task.value) : 'NOT_CREATED')
const isDelivery = computed(() => task.value?.fulfillmentType === 'DELIVERY')
const canAccept = computed(() => task.value && ['NOT_CREATED', 'PENDING'].includes(displayState.value))
const canReady = computed(() => task.value && displayState.value === 'PICKING')
const canHandover = computed(() => task.value && !isDelivery.value && displayState.value === 'PREPARED')
const canDispatch = computed(() => task.value && isDelivery.value && displayState.value === 'PREPARED')
const canDeliver = computed(() => task.value && isDelivery.value && displayState.value === 'OUT_FOR_DELIVERY')

async function load() {
  loading.value = true; error.value = ''
  try { task.value = await api.pickupTask(orderId.value) }
  catch (reason) { error.value = errorCopy(reason) }
  finally { loading.value = false }
}

async function accept() {
  if (!task.value || !canAccept.value || busy.value) return
  busy.value = true; error.value = ''; success.value = ''
  try {
    let fulfillmentId = task.value.fulfillmentId
    if (!fulfillmentId) fulfillmentId = (await api.createPickup(task.value.orderId)).id
    await api.acceptFulfillment(fulfillmentId)
    await load(); success.value = t('Fulfillment accepted. Start preparing every item.')
    await nextTick(); heading.value?.focus()
  } catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = false }
}

async function ready() {
  if (!task.value?.fulfillmentId || !canReady.value || busy.value) return
  busy.value = true; error.value = ''; success.value = ''
  try {
    await api.readyFulfillment(task.value.fulfillmentId)
    await load(); success.value = t(isDelivery.value ? 'Order is ready for dispatch.' : 'Pickup is ready for customer handover.')
    await nextTick(); heading.value?.focus()
  } catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = false }
}

function ask(action: 'HANDOVER' | 'DISPATCH' | 'DELIVER') {
  pendingAction.value = action
  confirmDialog.value?.showModal()
}
function closeDialog() { confirmDialog.value?.close() }
async function complete() {
  if (!task.value?.fulfillmentId || !pendingAction.value || busy.value) return
  busy.value = true; error.value = ''; closeDialog()
  const action = pendingAction.value
  try {
    if (action === 'HANDOVER') await api.handoverPickup(task.value.fulfillmentId, crypto.randomUUID())
    else if (action === 'DISPATCH') await api.dispatchFulfillment(task.value.fulfillmentId, crypto.randomUUID())
    else await api.deliverFulfillment(task.value.fulfillmentId, crypto.randomUUID())
    await load()
    success.value = t(action === 'HANDOVER' ? 'Handover recorded. Physical stock was issued exactly once.'
      : action === 'DISPATCH' ? 'Dispatch recorded. Physical stock was issued exactly once.'
        : 'Delivery completion recorded.')
    await nextTick(); heading.value?.focus()
  } catch (reason) {
    const actionError = reason instanceof ApiError && reason.code === 'CANCELLATION_WON'
      ? t('Cancellation already won. Do not issue these items.') : errorCopy(reason)
    await load()
    error.value = error.value ? t('{actionError} Current status could not be refreshed: {error}', { actionError, error: error.value }) : actionError
  } finally { busy.value = false; pendingAction.value = undefined }
}

const commandTitle = computed(() => {
  if (displayState.value === 'PREPARED') return isDelivery.value ? 'Ready for dispatch' : 'Ready for handover'
  if (displayState.value === 'OUT_FOR_DELIVERY') return 'Out for delivery'
  if (['HANDED_OVER', 'DELIVERED'].includes(displayState.value)) return 'Fulfillment complete'
  if (displayState.value === 'CANCELLED') return 'No longer actionable'
  return displayState.value === 'PICKING' ? 'Prepare the whole order' : 'Accept this request'
})
const dialogAction = computed(() => pendingAction.value ?? (canHandover.value ? 'HANDOVER' : canDispatch.value ? 'DISPATCH' : 'DELIVER'))
const dialogTitle = computed(() => dialogAction.value === 'HANDOVER' ? 'Hand over the whole order?'
  : dialogAction.value === 'DISPATCH' ? 'Dispatch the whole order?' : 'Confirm delivery completion?')
const dialogCopy = computed(() => dialogAction.value === 'DELIVER'
  ? 'Confirm only after the receiver has received the complete order.'
  : 'This issues every line from physical stock and prevents direct cancellation. Confirm only when the complete order leaves staff custody.')

onMounted(load)
</script>

<template>
  <div class="operations-page pickup-detail-page">
    <RouterLink class="back-link" to="/operations/fulfillments"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>{{ t('Fulfillment queue') }}</RouterLink>
    <div v-if="loading" class="queue-loading" role="status"><span class="loader-mark"></span>{{ t('Loading fulfillment…') }}</div>
    <section v-else-if="error && !task" class="inline-state" role="alert"><h1>{{ t('Fulfillment unavailable') }}</h1><p>{{ messageLabel(error) }}</p><button class="text-button" @click="load">{{ t('Try again') }}</button></section>
    <template v-else-if="task">
      <header ref="heading" class="pickup-detail-heading" tabindex="-1">
        <h1>{{ t(task.fulfillmentType === 'DELIVERY' ? 'Delivery order' : 'Pickup order') }} <span>SC-{{ task.orderId.slice(0, 8).toUpperCase() }}</span></h1>
        <p>{{ t(task.branchName) }} · {{ t(task.locationName) }} · {{ task.orderId }}</p>
      </header>
      <section class="pickup-command" aria-labelledby="command-title">
        <div class="command-state"><span>{{ statusLabel(displayState) }}</span><strong id="command-title">{{ t(commandTitle) }}</strong></div>
        <dl>
          <div><dt>{{ t('Fulfillment type') }}</dt><dd>{{ t(task.fulfillmentType === 'DELIVERY' ? 'Delivery' : 'Pickup') }}</dd></div>
          <div><dt>{{ t('Location authority') }}</dt><dd>{{ task.branchCode }} / {{ task.locationCode }}</dd></div>
          <div><dt>{{ t('Paid order') }}</dt><dd>{{ t(task.orderStatus === 'PAID' ? 'Verified paid' : 'Cancelled') }}</dd></div>
          <div><dt>{{ t('Quantity') }}</dt><dd>{{ task.quantity }}</dd></div>
          <div v-if="task.receiverName"><dt>{{ t('Receiver') }}</dt><dd>{{ task.receiverName }} · {{ task.receiverPhone }}</dd></div>
          <div v-if="task.deliveryAddress"><dt>{{ t('Delivery address') }}</dt><dd>{{ task.deliveryAddress }}</dd></div>
          <div v-if="task.deliveryNote"><dt>{{ t('Delivery note') }}</dt><dd>{{ task.deliveryNote }}</dd></div>
          <div v-if="task.pickingStartedAt"><dt>{{ t('Accepted') }}</dt><dd>{{ formatDateTime(task.pickingStartedAt) }}</dd></div>
          <div v-if="task.preparedAt"><dt>{{ t('Ready') }}</dt><dd>{{ formatDateTime(task.preparedAt) }}</dd></div>
          <div v-if="task.dispatchedAt"><dt>{{ t('Dispatched') }}</dt><dd>{{ formatDateTime(task.dispatchedAt) }}</dd></div>
          <div v-if="task.deliveredAt"><dt>{{ t('Delivered') }}</dt><dd>{{ formatDateTime(task.deliveredAt) }}</dd></div>
          <div v-if="task.handedOverAt"><dt>{{ t('Handed over') }}</dt><dd>{{ formatDateTime(task.handedOverAt) }}</dd></div>
          <div v-if="task.financialVoidStatus"><dt>{{ t('Financial reversal') }}</dt><dd>{{ statusLabel(task.financialVoidStatus) }}</dd></div>
        </dl>
        <CommerceItems :items="task.items" />
        <p v-if="success" class="success-message" role="status">{{ success }}</p>
        <p v-if="error" class="form-error" role="alert">{{ messageLabel(error) }}</p>
        <button v-if="canAccept" class="primary-button" type="button" :disabled="busy" @click="accept">{{ t(busy ? 'Accepting…' : 'Accept & start preparing') }}</button>
        <button v-else-if="canReady" class="primary-button" type="button" :disabled="busy" @click="ready">{{ t(busy ? 'Saving…' : isDelivery ? 'Mark ready for dispatch' : 'Mark ready for pickup') }}</button>
        <button v-else-if="canHandover" class="handover-button" type="button" :disabled="busy" @click="ask('HANDOVER')">{{ t('Record customer handover') }}</button>
        <button v-else-if="canDispatch" class="handover-button" type="button" :disabled="busy" @click="ask('DISPATCH')">{{ t('Dispatch complete order') }}</button>
        <button v-else-if="canDeliver" class="handover-button" type="button" :disabled="busy" @click="ask('DELIVER')">{{ t('Mark delivered') }}</button>
        <p v-else-if="displayState === 'CANCELLED'" class="terminal-guidance">{{ t('Cancellation won. Keep all items at this location; stock is sellable again.') }}</p>
        <p v-else-if="['HANDED_OVER', 'DELIVERED'].includes(displayState)" class="terminal-guidance">{{ t('This terminal fulfillment cannot be cancelled in this workflow.') }}</p>
      </section>

      <dialog ref="confirmDialog" class="terminal-dialog" aria-labelledby="fulfillment-dialog-title" aria-describedby="fulfillment-dialog-description" @cancel="closeDialog">
        <form method="dialog" @submit.prevent>
          <h2 id="fulfillment-dialog-title">{{ t(dialogTitle) }}</h2>
          <p id="fulfillment-dialog-description">{{ t(dialogCopy) }}</p>
          <div><button class="text-button" type="button" @click="closeDialog">{{ t('Not yet') }}</button><button class="handover-button" type="button" @click="complete">{{ t('Confirm complete order') }}</button></div>
        </form>
      </dialog>
    </template>
  </div>
</template>
