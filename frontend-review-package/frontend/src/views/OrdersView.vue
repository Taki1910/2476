<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api, type Order } from '../api'
import { errorCopy, formatDateTime, formatVnd } from '../format'
import { commerceCount, messageLabel, t } from '../i18n'

const orders = ref<Order[]>([])
const page = ref(0)
const hasNext = ref(false)
const loading = ref(true)
const error = ref('')

function status(order: Order) {
  return ({
    PENDING_PAYMENT: 'Payment needed', CANCELLED: 'Order cancelled', PAID_WAITING_PREPARATION: 'Paid — waiting for preparation',
    READY_FOR_PICKUP: 'Ready for pickup', READY_FOR_DISPATCH: 'Ready for dispatch',
    OUT_FOR_DELIVERY: 'Out for delivery', DELIVERED: 'Delivered', PICKED_UP: 'Picked up', CANCELLATION_PROCESSING: 'Cancellation accepted',
    CANCELLED_PAYMENT_REVERSED: 'Cancelled — payment refunded', CANCELLED_REVERSAL_FAILED: 'Cancelled — refund needs retry',
    CANCELLED_REVERSAL_REVIEW: 'Cancelled — refund under review',
  } as Record<string, string>)[order.pickupStatus] ?? order.status
}

async function load(nextPage = page.value) {
  loading.value = true; error.value = ''
  try {
    const result = await api.orders(nextPage)
    orders.value = result.items
    page.value = result.page
    hasNext.value = result.hasNext
  } catch (reason) { error.value = errorCopy(reason) }
  finally { loading.value = false }
}

onMounted(() => load())
</script>

<template>
  <div class="orders-page">
    <div class="page-topline"><RouterLink class="back-link" to="/"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>{{ t('Storefront') }}</RouterLink></div>
    <section class="orders-heading"><div><h1>{{ t('My Orders') }}</h1><p>{{ t('Every order you own, with its payment and fulfillment status in one place.') }}</p></div><RouterLink class="primary-button" to="/">{{ t('Shop shoes') }}</RouterLink></section>
    <div v-if="loading" class="queue-loading" role="status" aria-live="polite"><span class="loader-mark"></span>{{ t('Loading your orders…') }}</div>
    <section v-else-if="error" class="inline-state" role="alert"><h2>{{ t('Orders unavailable') }}</h2><p>{{ messageLabel(error) }}</p><button class="text-button" type="button" @click="load()">{{ t('Try again') }}</button></section>
    <section v-else-if="!orders.length" class="orders-empty"><h2>{{ t('No orders yet.') }}</h2><p>{{ t('Your paid and pending orders will appear here so you never need to remember an order URL.') }}</p><RouterLink class="text-button" to="/">{{ t('Browse the collection') }}</RouterLink></section>
    <section v-else class="orders-list" aria-labelledby="orders-list-title"><h2 id="orders-list-title" class="sr-only">{{ t('Orders') }}</h2><article v-for="order in orders" :key="order.id" class="order-row"><div><p class="eyebrow">{{ t('Order reference') }}</p><h3>{{ order.orderReference }}</h3><p>{{ commerceCount(order.itemCount, order.quantity) }}</p></div><dl><div><dt>{{ t('Status') }}</dt><dd>{{ t(status(order)) }}</dd></div><div><dt>{{ t('Date') }}</dt><dd><time :datetime="order.createdAt">{{ formatDateTime(order.createdAt) }}</time></dd></div><div><dt>{{ t('Amount') }}</dt><dd>{{ formatVnd(order.totalAmount) }}</dd></div></dl><RouterLink class="text-button order-row-action" :to="`/orders/${order.id}`">{{ t('View order') }}<span aria-hidden="true">→</span></RouterLink></article><div v-if="page > 0 || hasNext" class="orders-pagination"><button class="text-button" type="button" :disabled="loading || page === 0" @click="load(page - 1)">{{ t('Previous') }}</button><span>{{ page + 1 }}</span><button class="text-button" type="button" :disabled="loading || !hasNext" @click="load(page + 1)">{{ t('Next') }}</button></div></section>
  </div>
</template>
