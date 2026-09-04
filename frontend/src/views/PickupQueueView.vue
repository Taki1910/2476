<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api, type PickupTask } from '../api'
import { errorCopy, formatDateTime, pickupDisplayState } from '../format'
import { messageLabel, t } from '../i18n'

const tasks = ref<PickupTask[]>([])
const loading = ref(true)
const error = ref('')
const filter = ref<'ALL' | 'ACTION' | PickupTask['fulfillmentStatus']>('ACTION')
const filters: { value: typeof filter.value; label: string }[] = [
  { value: 'ACTION', label: 'Needs action' }, { value: 'ALL', label: 'All' },
  { value: 'PENDING', label: 'Pending' }, { value: 'PICKING', label: 'Picking' },
  { value: 'PREPARED', label: 'Ready' }, { value: 'OUT_FOR_DELIVERY', label: 'Out for delivery' },
]
const actionable = computed(() => tasks.value.filter(task => !['HANDED_OVER', 'DELIVERED', 'CANCELLED'].includes(pickupDisplayState(task))))
const visibleTasks = computed(() => tasks.value.filter(task => filter.value === 'ALL'
  || filter.value === 'ACTION' && !['HANDED_OVER', 'DELIVERED', 'CANCELLED'].includes(pickupDisplayState(task))
  || pickupDisplayState(task) === filter.value))
const counts = computed(() => Object.fromEntries(['PENDING', 'PICKING', 'PREPARED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'HANDED_OVER'].map(status => [status, tasks.value.filter(task => pickupDisplayState(task) === status).length])))

async function load() {
  loading.value = true; error.value = ''
  try { tasks.value = await api.pickupQueue() }
  catch (reason) { error.value = errorCopy(reason) }
  finally { loading.value = false }
}

function label(status: PickupTask['fulfillmentStatus']) {
  return t(({ NOT_CREATED: 'Needs setup', PENDING: 'Needs acceptance', PICKING: 'Being prepared', PREPARED: 'Ready', OUT_FOR_DELIVERY: 'Out for delivery', DELIVERED: 'Delivered', HANDED_OVER: 'Handed over', CANCELLED: 'Cancelled' })[status])
}

onMounted(load)
</script>

<template>
  <div class="operations-page">
    <header class="operations-heading">
      <div><h1>{{ t('What needs action?') }}</h1></div>
      <p>{{ t('Paid pickup and delivery orders in your active location assignments. Terminal orders remain visible as evidence.') }}</p>
    </header>

    <div v-if="loading" class="queue-loading" role="status"><span class="loader-mark"></span>{{ t('Loading fulfillment work…') }}</div>
    <section v-else-if="error" class="inline-state" role="alert">
      <h2>{{ t('Fulfillment work is unavailable') }}</h2><p>{{ messageLabel(error) }}</p>
      <button class="text-button" type="button" @click="load">{{ t('Try again') }}</button>
    </section>
    <section v-else-if="tasks.length === 0" class="queue-empty">
      <p class="state-code">0</p><h2>{{ t('No fulfillment work in your locations.') }}</h2>
      <p>{{ t('New paid pickup and delivery orders appear here when they match an active assignment.') }}</p>
    </section>
    <template v-else>
      <dl class="queue-metrics" :aria-label="t('Fulfillment status summary')">
        <div><dt>{{ t('Need action') }}</dt><dd>{{ actionable.length }}</dd></div>
        <div><dt>{{ t('Pending') }}</dt><dd>{{ counts.PENDING ?? 0 }}</dd></div>
        <div><dt>{{ t('Picking') }}</dt><dd>{{ counts.PICKING ?? 0 }}</dd></div>
        <div><dt>{{ t('Ready') }}</dt><dd>{{ counts.PREPARED ?? 0 }}</dd></div>
        <div><dt>{{ t('Issued') }}</dt><dd>{{ (counts.HANDED_OVER ?? 0) + (counts.OUT_FOR_DELIVERY ?? 0) }}</dd></div>
      </dl>
      <p class="report-note">{{ t('Needs action includes needs setup, pending, picking and ready. The status counts overlap this total.') }}</p>
      <div class="queue-filters" role="group" :aria-label="t('Filter fulfillment queue')">
        <button v-for="item in filters" :key="item.value" type="button" :aria-pressed="filter === item.value" @click="filter = item.value">{{ t(item.label) }}</button>
      </div>
      <p class="queue-summary" aria-live="polite">{{ t('{shown} shown · {total} total', { shown: visibleTasks.length, total: tasks.length }) }}</p>
      <ol class="pickup-list">
        <li v-for="task in visibleTasks" :key="task.orderId">
          <RouterLink :to="`/operations/fulfillments/${task.orderId}`" class="pickup-row">
            <div class="pickup-priority"><span :data-status="pickupDisplayState(task)"></span>{{ label(pickupDisplayState(task)) }}</div>
            <div class="pickup-item"><strong>{{ t(task.fulfillmentType === 'DELIVERY' ? 'Delivery' : 'Pickup') }} · {{ t('{variants} variants · {quantity} units', { variants: task.itemCount, quantity: task.quantity }) }}</strong><span v-for="item in task.items" :key="item.sku">{{ item.sku }} · {{ t('Size') }} {{ item.size }}<template v-if="item.color"> · {{ t(item.color) }}</template> · {{ t('Quantity') }} {{ item.quantity }}</span><time v-if="task.createdAt" :datetime="task.createdAt">{{ formatDateTime(task.createdAt) }}</time></div>
            <div class="pickup-location"><strong>{{ t(task.locationName) }}</strong><span>{{ task.branchCode }} / {{ task.locationCode }}</span></div>
            <code>{{ task.orderId.slice(0, 8) }}</code>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" /></svg>
          </RouterLink>
        </li>
      </ol>
      <p v-if="visibleTasks.length === 0" class="inline-empty">{{ t('No fulfillment tasks match this status.') }}</p>
    </template>
  </div>
</template>
