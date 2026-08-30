<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, ApiError, type PickupTask } from '../api'
import { errorCopy, formatDateTime } from '../format'

const route = useRoute()
const task = ref<PickupTask>()
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const success = ref('')
const confirmDialog = ref<HTMLDialogElement>()
const heading = ref<HTMLElement>()
const orderId = computed(() => String(route.params.id))
const canPrepare = computed(() => task.value && ['NOT_CREATED', 'PENDING', 'PICKING'].includes(task.value.fulfillmentStatus))
const canHandover = computed(() => task.value?.fulfillmentStatus === 'PREPARED')

async function load() {
  loading.value = true; error.value = ''
  try { task.value = await api.pickupTask(orderId.value) }
  catch (reason) { error.value = errorCopy(reason) }
  finally { loading.value = false }
}

async function prepare() {
  if (!task.value || !canPrepare.value || busy.value) return
  busy.value = true; error.value = ''; success.value = ''
  try {
    let fulfillmentId = task.value.fulfillmentId
    if (!fulfillmentId) fulfillmentId = (await api.createPickup(task.value.orderId)).id
    await api.preparePickup(fulfillmentId)
    await load(); success.value = 'Pickup is ready for customer handover.'
    await nextTick(); heading.value?.focus()
  } catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = false }
}

function askHandover() { confirmDialog.value?.showModal() }
function closeDialog() { confirmDialog.value?.close() }
async function handover() {
  if (!task.value?.fulfillmentId || busy.value) return
  busy.value = true; error.value = ''; closeDialog()
  try {
    await api.handoverPickup(task.value.fulfillmentId, crypto.randomUUID())
    await load(); success.value = 'Handover recorded. Physical stock was issued exactly once.'
    await nextTick(); heading.value?.focus()
  } catch (reason) {
    const actionError = reason instanceof ApiError && reason.code === 'CANCELLATION_WON'
      ? 'Cancellation already won. Do not hand this pair to the customer.' : errorCopy(reason)
    await load()
    error.value = error.value ? `${actionError} Current status could not be refreshed: ${error.value}` : actionError
  } finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <div class="operations-page pickup-detail-page">
    <RouterLink class="back-link" to="/operations/pickups"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>Pickup queue</RouterLink>
    <div v-if="loading" class="queue-loading" role="status"><span class="loader-mark"></span>Loading pickup…</div>
    <section v-else-if="error && !task" class="inline-state" role="alert"><h1>Pickup unavailable</h1><p>{{ error }}</p><button class="text-button" @click="load">Try again</button></section>
    <template v-else-if="task">
      <header ref="heading" class="pickup-detail-heading" tabindex="-1">
        <p class="eyebrow">{{ task.branchName }} · {{ task.locationName }}</p>
        <h1>{{ task.sku }} <span>/ {{ task.size }}</span></h1>
        <p>Order {{ task.orderId }}</p>
      </header>
      <section class="pickup-command" aria-labelledby="command-title">
        <div class="command-state"><span>{{ task.fulfillmentStatus.replace('_', ' ') }}</span><strong id="command-title">{{ task.fulfillmentStatus === 'PREPARED' ? 'Ready for handover' : task.fulfillmentStatus === 'HANDED_OVER' ? 'Handover complete' : task.fulfillmentStatus === 'CANCELLED' ? 'No longer actionable' : 'Prepare this pickup' }}</strong></div>
        <dl>
          <div><dt>Location authority</dt><dd>{{ task.branchCode }} / {{ task.locationCode }}</dd></div>
          <div><dt>Paid order</dt><dd>{{ task.orderStatus === 'PAID' ? 'Verified paid' : 'Cancelled' }}</dd></div>
          <div><dt>Quantity</dt><dd>{{ task.quantity }}</dd></div>
          <div v-if="task.preparedAt"><dt>Prepared</dt><dd>{{ formatDateTime(task.preparedAt) }}</dd></div>
          <div v-if="task.handedOverAt"><dt>Handed over</dt><dd>{{ formatDateTime(task.handedOverAt) }}</dd></div>
          <div v-if="task.financialVoidStatus"><dt>Financial reversal</dt><dd>{{ task.financialVoidStatus.replace('_', ' ') }}</dd></div>
        </dl>
        <p v-if="success" class="success-message" role="status">{{ success }}</p>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button v-if="canPrepare" class="primary-button" type="button" :disabled="busy" @click="prepare">{{ busy ? 'Preparing…' : 'Mark ready for pickup' }}</button>
        <button v-else-if="canHandover" class="handover-button" type="button" :disabled="busy" @click="askHandover">Record customer handover</button>
        <p v-else-if="task.fulfillmentStatus === 'CANCELLED'" class="terminal-guidance">Cancellation won. Keep the pair at this location; stock is sellable again.</p>
        <p v-else-if="task.fulfillmentStatus === 'HANDED_OVER'" class="terminal-guidance">This terminal pickup cannot be cancelled in this workflow.</p>
      </section>

      <dialog ref="confirmDialog" class="terminal-dialog" aria-labelledby="handover-dialog-title" aria-describedby="handover-dialog-description" @cancel="closeDialog">
        <form method="dialog" @submit.prevent>
          <p class="eyebrow">Terminal inventory action</p>
          <h2 id="handover-dialog-title">Hand over {{ task.sku }}, size {{ task.size }}?</h2>
          <p id="handover-dialog-description">Confirm only after the customer physically receives this pair at <strong>{{ task.locationName }}</strong>. This issues quantity {{ task.quantity }} and cannot be undone as a cancellation.</p>
          <div><button class="text-button" type="button" @click="closeDialog">Not yet</button><button class="handover-button" type="button" @click="handover">Yes, pair handed over</button></div>
        </form>
      </dialog>
    </template>
  </div>
</template>
