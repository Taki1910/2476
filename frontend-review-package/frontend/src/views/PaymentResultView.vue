<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, type Order, type PaymentAttempt } from '../api'
import { errorCopy, formatDateTime, formatVnd } from '../format'
import { messageLabel, t } from '../i18n'
import CommerceItems from '../components/CommerceItems.vue'
import { paymentDestination, recoverablePayment } from '../checkout'

type Screen = 'loading' | 'pending' | 'paid' | 'failed' | 'expired' | 'review' | 'invalid' | 'error'

const route = useRoute()
const attempt = ref<PaymentAttempt>()
const order = ref<Order>()
const screen = ref<Screen>('loading')
const message = ref('')
const retrying = ref(false)
const checking = ref(false)
const pollCount = ref(0)
let timer: number | undefined
let disposed = false
const attemptId = computed(() => typeof route.query.attemptId === 'string' ? route.query.attemptId : '')
const statusTime = computed(() => {
  if (!attempt.value) return
  if (screen.value === 'paid' || screen.value === 'review') {
    return {
      label: t(screen.value === 'paid' ? 'Confirmed at' : 'Provider reported at'),
      value: screen.value === 'paid' ? order.value?.paidAt ?? attempt.value.providerPaidAt ?? attempt.value.resolvedAt ?? attempt.value.expiresAt
        : attempt.value.providerPaidAt ?? attempt.value.resolvedAt ?? attempt.value.expiresAt,
    }
  }
  return { label: t('Payment deadline'), value: attempt.value.expiresAt }
})

function resolveScreen() {
  if (!attempt.value || !order.value) return
  if (attempt.value.status === 'REVIEW_REQUIRED') screen.value = 'review'
  else if (order.value.status === 'PAID') screen.value = 'paid'
  else if (attempt.value.status === 'EXPIRED' || attempt.value.status === 'CANCELLED' || order.value.status === 'CANCELLED') screen.value = 'expired'
  else if (attempt.value.status === 'FAILED') screen.value = 'failed'
  else screen.value = 'pending'
}

async function refresh(continuePolling = true) {
  if (!attemptId.value) {
    screen.value = 'invalid'
    return
  }
  if (checking.value) return
  checking.value = true
  if (pollCount.value === 0) screen.value = 'loading'
  message.value = ''
  try {
    attempt.value = await api.paymentAttempt(attemptId.value)
    order.value = await api.order(attempt.value.orderId)
    resolveScreen()
    if (screen.value === 'pending' && continuePolling && pollCount.value < 6 && !disposed) {
      pollCount.value += 1
      timer = window.setTimeout(() => refresh(), 2_000)
    }
  } catch (reason) {
    screen.value = 'error'
    message.value = errorCopy(reason)
  } finally {
    checking.value = false
  }
}

async function retryPayment() {
  if (!order.value || order.value.status !== 'PENDING_PAYMENT' || screen.value !== 'failed' || retrying.value) return
  retrying.value = true
  message.value = ''
  try {
    const result = await recoverablePayment(order.value.id, attempt.value?.id)
    if (!disposed) window.location.assign(paymentDestination(result))
  } catch (reason) {
    message.value = errorCopy(reason)
    retrying.value = false
  }
}

function checkAgain() {
  if (checking.value) return
  if (timer) window.clearTimeout(timer)
  pollCount.value = 0
  refresh()
}

onMounted(() => refresh())
onBeforeUnmount(() => {
  disposed = true
  if (timer) window.clearTimeout(timer)
})
</script>

<template>
  <div class="payment-result-page">
    <RouterLink class="back-link" to="/">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>
      {{ t('Storefront') }}
    </RouterLink>

    <section class="payment-result-card" :data-state="screen" aria-labelledby="payment-result-title">
      <div class="payment-state-mark" aria-hidden="true">
        <span v-if="screen === 'loading' || screen === 'pending'" class="loader-mark"></span>
        <svg v-else-if="screen === 'paid'" viewBox="0 0 24 24"><path d="m5 12 4 4L19 6" /></svg>
        <svg v-else viewBox="0 0 24 24"><path d="M12 8v5m0 3h.01M4.8 19h14.4L12 5 4.8 19Z" /></svg>
      </div>

      <div class="payment-result-copy" role="status" aria-live="polite">
        <template v-if="screen === 'loading'">
          <h1 id="payment-result-title">{{ t('Confirming payment…') }}</h1>
          <p>{{ t('We’re checking the merchant record. Your return from VNPAY is not treated as payment proof.') }}</p>
        </template>
        <template v-else-if="screen === 'pending'">
          <h1 id="payment-result-title">{{ t(pollCount >= 6 ? 'Confirmation is still pending.' : 'Confirming payment…') }}</h1>
          <p>{{ t(pollCount >= 6 ? 'We have not received a final provider result yet. This does not mean the payment failed.' : 'VNPAY and the merchant notification can arrive at different times. Keep this page open while we check.') }}</p>
        </template>
        <template v-else-if="screen === 'paid'">
          <h1 id="payment-result-title">{{ t('Payment confirmed.') }}</h1>
          <p>{{ t('The provider result was verified by our server and this order is now paid.') }}</p>
        </template>
        <template v-else-if="screen === 'failed'">
          <h1 id="payment-result-title">{{ t('Payment wasn’t completed.') }}</h1>
          <p>{{ t('VNPAY reported an unsuccessful attempt. Your order remains payable while its reservation is valid.') }}</p>
        </template>
        <template v-else-if="screen === 'expired'">
          <h1 id="payment-result-title">{{ t('The reservation has ended.') }}</h1>
          <p>{{ t('This order is not paid and its stock hold is no longer active.') }}</p>
        </template>
        <template v-else-if="screen === 'review'">
          <h1 id="payment-result-title">{{ t('Payment needs review.') }}</h1>
          <p>{{ t('VNPAY reported payment, but the order could not be confirmed automatically. The transaction requires review.') }}</p>
        </template>
        <template v-else-if="screen === 'invalid'">
          <h1 id="payment-result-title">{{ t('No payment reference found.') }}</h1>
          <p>{{ t('This page cannot confirm payment from browser parameters. Return to the storefront or use the verified VNPAY return link.') }}</p>
        </template>
        <template v-else>
          <h1 id="payment-result-title">{{ t('We couldn’t check payment.') }}</h1>
          <p>{{ messageLabel(message) || t('The authoritative status is temporarily unavailable.') }}</p>
        </template>
      </div>

      <p v-if="screen === 'review'" class="payment-guidance">
        {{ t('Do not try to pay again. Keep the order reference below when contacting the store.') }}
      </p>

      <dl v-if="attempt && order" class="payment-facts">
        <div><dt>{{ t('Order reference') }}</dt><dd>{{ order.orderReference }}</dd></div>
        <div><dt>{{ t('Total units') }}</dt><dd>{{ order.quantity }} · {{ t('Variants') }} {{ order.itemCount }}</dd></div>
        <div><dt>{{ t('Order amount') }}</dt><dd>{{ formatVnd(attempt.amount) }}</dd></div>
        <div><dt>{{ t('Status') }}</dt><dd>{{ t(screen === 'paid' ? 'Paid' : screen === 'review' ? 'Review required' : screen === 'failed' ? 'Payment failed' : screen === 'expired' ? 'Reservation ended' : 'Pending confirmation') }}</dd></div>
        <div v-if="statusTime"><dt>{{ statusTime.label }}</dt><dd>{{ formatDateTime(statusTime.value) }}</dd></div>
      </dl>
      <CommerceItems v-if="order" :items="order.items" />

      <p v-if="message && screen !== 'error'" class="form-error" role="alert">{{ messageLabel(message) }}</p>
      <div class="payment-result-actions">
        <button v-if="screen === 'pending' && pollCount >= 6" class="primary-button" type="button" :disabled="checking" @click="checkAgain">
          {{ t(checking ? 'Checking status…' : 'Check status again') }}
        </button>
        <button v-if="screen === 'failed'" class="primary-button" type="button" :disabled="retrying" @click="retryPayment">
          {{ t(retrying ? 'Opening VNPAY…' : 'Try VNPAY again') }}
        </button>
        <button v-if="screen === 'error'" class="primary-button" type="button" :disabled="checking" @click="checkAgain">
          {{ t(checking ? 'Checking status…' : 'Try status check again') }}
        </button>
        <RouterLink v-if="order" class="text-button" :to="`/orders/${order.id}`">{{ t('Track order') }}</RouterLink>
        <RouterLink v-if="order" class="text-button" to="/orders">{{ t('My Orders') }}</RouterLink>
        <RouterLink class="text-button" to="/">{{ t('Continue shopping') }}</RouterLink>
      </div>
    </section>
  </div>
</template>
