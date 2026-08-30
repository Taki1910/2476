<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api, type PickupTask } from '../api'
import { errorCopy } from '../format'

const tasks = ref<PickupTask[]>([])
const loading = ref(true)
const error = ref('')
const actionable = computed(() => tasks.value.filter(task => !['HANDED_OVER', 'CANCELLED'].includes(task.fulfillmentStatus)))

async function load() {
  loading.value = true; error.value = ''
  try { tasks.value = await api.pickupQueue() }
  catch (reason) { error.value = errorCopy(reason) }
  finally { loading.value = false }
}

function label(status: PickupTask['fulfillmentStatus']) {
  return ({ NOT_CREATED: 'Needs setup', PENDING: 'Needs preparation', PICKING: 'Being picked', PREPARED: 'Ready', HANDED_OVER: 'Handed over', CANCELLED: 'Cancelled' })[status]
}

onMounted(load)
</script>

<template>
  <div class="operations-page">
    <header class="operations-heading">
      <div><p class="eyebrow">Pickup operations</p><h1>What needs action?</h1></div>
      <p>Paid pickup orders in your active location assignments. Terminal orders remain visible as evidence.</p>
    </header>

    <div v-if="loading" class="queue-loading" role="status"><span class="loader-mark"></span>Loading pickup work…</div>
    <section v-else-if="error" class="inline-state" role="alert">
      <h2>Pickup work is unavailable</h2><p>{{ error }}</p>
      <button class="text-button" type="button" @click="load">Try again</button>
    </section>
    <section v-else-if="tasks.length === 0" class="queue-empty">
      <p class="state-code">0</p><h2>No pickups in your locations.</h2>
      <p>New paid pickup orders will appear here when they match an active assignment.</p>
    </section>
    <template v-else>
      <p class="queue-summary" aria-live="polite"><strong>{{ actionable.length }}</strong> need action · {{ tasks.length }} total</p>
      <ol class="pickup-list">
        <li v-for="task in tasks" :key="task.orderId">
          <RouterLink :to="`/operations/pickups/${task.orderId}`" class="pickup-row">
            <div class="pickup-priority"><span :data-status="task.fulfillmentStatus"></span>{{ label(task.fulfillmentStatus) }}</div>
            <div class="pickup-item"><strong>{{ task.sku }}</strong><span>Size {{ task.size }} · Qty {{ task.quantity }}</span></div>
            <div class="pickup-location"><strong>{{ task.locationName }}</strong><span>{{ task.branchCode }} / {{ task.locationCode }}</span></div>
            <code>{{ task.orderId.slice(0, 8) }}</code>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" /></svg>
          </RouterLink>
        </li>
      </ol>
    </template>
  </div>
</template>
