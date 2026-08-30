<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, ApiError, type Order, type PriceQuote, type ProductDetail, type Variant } from '../api'
import { checkoutErrorCopy, errorCopy, formatExpiry, formatVnd, isExpired } from '../format'

const route = useRoute()
const product = ref<ProductDetail>()
const selected = ref<Variant>()
const quote = ref<PriceQuote>()
const loading = ref(true)
const quoteLoading = ref(false)
const error = ref('')
const notFound = ref(false)
const quoteError = ref('')
const checkoutLoading = ref(false)
const checkoutError = ref('')
const cancelLoading = ref(false)
const cancelError = ref('')
const paymentLoading = ref(false)
const paymentError = ref('')
const paymentKey = ref('')
const checkoutKey = ref('')
const order = ref<Order>()
const orderPanel = ref<HTMLElement>()
const now = ref(Date.now())
const expired = computed(() => quote.value ? isExpired(quote.value.expiresAt, now.value) : false)
const minutesRemaining = computed(() => quote.value ? Math.max(0, Math.ceil((Date.parse(quote.value.expiresAt) - now.value) / 60_000)) : 0)
const timer = window.setInterval(() => { now.value = Date.now() }, 1000)

async function loadProduct() {
  loading.value = true
  error.value = ''
  notFound.value = false
  try {
    product.value = await api.product(String(route.params.id))
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 404) notFound.value = true
    else error.value = errorCopy(reason)
  } finally {
    loading.value = false
  }
}

function choose(variant: Variant) {
  selected.value = variant
  quote.value = undefined
  quoteError.value = ''
  order.value = undefined
  checkoutError.value = ''
  cancelError.value = ''
  paymentError.value = ''
  paymentKey.value = ''
  checkoutKey.value = ''
}

async function requestQuote() {
  if (!selected.value) return
  quoteLoading.value = true
  quoteError.value = ''
  try {
    quote.value = await api.quote(selected.value.id)
    checkoutKey.value = crypto.randomUUID()
    order.value = undefined
    checkoutError.value = ''
    cancelError.value = ''
    paymentError.value = ''
    paymentKey.value = ''
    now.value = Date.now()
  } catch (reason) {
    quote.value = undefined
    quoteError.value = errorCopy(reason)
  } finally {
    quoteLoading.value = false
  }
}

async function cancelOrder() {
  if (!order.value || order.value.status !== 'PENDING_PAYMENT') return
  cancelLoading.value = true
  cancelError.value = ''
  try {
    order.value = await api.cancelOrder(order.value.id)
    await nextTick()
    orderPanel.value?.focus()
  } catch (reason) {
    cancelError.value = errorCopy(reason)
  } finally {
    cancelLoading.value = false
  }
}

async function startPayment() {
  if (!order.value || order.value.status !== 'PENDING_PAYMENT') return
  paymentLoading.value = true
  paymentError.value = ''
  if (!paymentKey.value) paymentKey.value = crypto.randomUUID()
  try {
    const result = await api.pay(order.value.id, paymentKey.value)
    window.location.assign(result.paymentUrl)
  } catch (reason) {
    paymentError.value = errorCopy(reason)
    paymentLoading.value = false
  }
}

async function checkout() {
  if (!quote.value || expired.value || !checkoutKey.value) return
  checkoutLoading.value = true
  checkoutError.value = ''
  try {
    order.value = await api.checkout(quote.value.id, checkoutKey.value)
    paymentKey.value = crypto.randomUUID()
    await nextTick()
    orderPanel.value?.focus()
  } catch (reason) {
    checkoutError.value = checkoutErrorCopy(reason)
  } finally {
    checkoutLoading.value = false
  }
}

watch(() => route.params.id, loadProduct)
onMounted(loadProduct)
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<template>
  <div class="product-page">
    <RouterLink class="back-link" to="/">
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>
      All products
    </RouterLink>

    <div v-if="loading" class="detail-loading" role="status" aria-live="polite">
      <div class="skeleton-block"></div><div class="skeleton-lines"><span></span><span></span><span></span></div>
    </div>

    <section v-else-if="notFound" class="centered-state">
      <p class="state-code">404</p>
      <h1>Product not found</h1>
      <p>It may be unpublished or no longer available in the storefront.</p>
      <RouterLink class="text-button" to="/">Return to products</RouterLink>
    </section>

    <section v-else-if="error" class="inline-state" role="alert">
      <h1>Couldn’t load this product</h1>
      <p>{{ error }}</p>
      <button class="text-button" type="button" @click="loadProduct">Try again</button>
    </section>

    <template v-else-if="product">
      <section class="product-identity">
        <div class="product-type" aria-hidden="true">{{ product.name.slice(0, 2).toUpperCase() }}</div>
        <div>
          <h1>{{ product.name }}</h1>
          <p>{{ product.variants.length }} {{ product.variants.length === 1 ? 'available option' : 'size/color options' }}</p>
        </div>
      </section>

      <section class="selection-panel" aria-labelledby="variant-heading">
        <div class="selection-copy">
          <h2 id="variant-heading">Choose your size</h2>
          <p>Availability can change while other customers check out. Exact store stock stays private.</p>
        </div>

        <div v-if="product.variants.length === 0" class="inline-state">
          <h3>No sizes available</h3>
          <p>This product cannot be quoted right now.</p>
        </div>

        <fieldset v-else class="variant-list">
          <legend class="sr-only">Available sizes and colors</legend>
          <button
            v-for="variant in product.variants"
            :key="variant.id"
            type="button"
            class="variant-option"
            :class="{ selected: selected?.id === variant.id }"
            :disabled="variant.availability === 'UNAVAILABLE'"
            :aria-pressed="selected?.id === variant.id"
            @click="choose(variant)"
          >
            <span class="variant-size">{{ variant.size }}</span>
            <span class="variant-color">{{ variant.color }}</span>
            <span class="availability" :class="variant.availability.toLowerCase()">
              {{ variant.availability === 'AVAILABLE' ? 'Available' : 'Unavailable' }}
            </span>
          </button>
        </fieldset>

        <div class="quote-panel">
          <div v-if="!selected" class="quote-placeholder">
            <strong>Price waits for your size.</strong>
            <span>Select an available variant to request the current price.</span>
          </div>

          <template v-else>
            <div class="selected-summary">
              <span>Selected</span>
              <strong>Size {{ selected.size }} · {{ selected.color }}</strong>
              <small>SKU {{ selected.sku }}</small>
            </div>

            <div v-if="quote" class="quote-result" :class="{ expired }">
              <div class="quote-announcement" role="status" aria-live="polite">
                <p>{{ expired ? 'Quote expired' : 'Current price' }}</p>
                <strong>{{ formatVnd(quote.amount) }}</strong>
                <span>{{ expired ? 'Request a fresh price before continuing.' : `Price valid for about ${minutesRemaining} ${minutesRemaining === 1 ? 'minute' : 'minutes'}.` }}</span>
                <small v-if="!expired">This quote confirms the price; it does not hold stock.</small>
              </div>
              <details class="quote-details">
                <summary>Quote details</summary>
                <small>Valid until {{ formatExpiry(quote.expiresAt) }}</small>
                <small>Reference {{ quote.id }}</small>
              </details>
            </div>

            <section v-if="order" ref="orderPanel" class="order-confirmation" tabindex="-1" aria-labelledby="order-confirmation-title">
              <h3 id="order-confirmation-title">{{ order.status === 'CANCELLED' ? 'Order cancelled.' : 'Your pair is reserved.' }}</h3>
              <dl>
                <div><dt>Order status</dt><dd>{{ order.status === 'CANCELLED' ? 'Cancelled' : 'Pending payment' }}</dd></div>
                <div><dt>Size</dt><dd>{{ order.size }}</dd></div>
                <div><dt>Total</dt><dd>{{ formatVnd(order.totalAmount) }}</dd></div>
              </dl>
              <small>Order {{ order.id }}</small>
              <p v-if="order.status === 'PENDING_PAYMENT' && order.reservationExpiresAt">Payment has not started. The server holds this pair until {{ formatExpiry(order.reservationExpiresAt) }}.</p>
              <p v-else-if="order.status === 'PENDING_PAYMENT'">Payment has not started. Your stock reservation is recorded.</p>
              <p v-else>The stock hold has been released. No payment was started.</p>
              <div v-if="order.status === 'PENDING_PAYMENT'" class="payment-action">
                <div>
                  <strong>Pay with VNPAY</strong>
                  <span>You’ll continue to VNPAY. We confirm the order only after the provider notifies our server.</span>
                </div>
                <button class="payment-button" type="button" :disabled="paymentLoading || cancelLoading" @click="startPayment">
                  {{ paymentLoading ? 'Opening VNPAY…' : 'Pay ' + formatVnd(order.totalAmount) }}
                </button>
              </div>
              <p v-if="paymentError" class="form-error" role="alert">{{ paymentError }}</p>
              <p v-if="cancelError" class="form-error" role="alert">{{ cancelError }}</p>
              <button v-if="order.status === 'PENDING_PAYMENT'" class="text-button cancel-order-button" type="button" :disabled="cancelLoading || paymentLoading" @click="cancelOrder">
                {{ cancelLoading ? 'Releasing pair…' : 'Release pair & cancel order' }}
              </button>
            </section>

            <div v-else-if="quote && !expired" class="checkout-action">
              <div>
                <strong>Ready to hold this pair?</strong>
                <span>Checkout reserves one pair and creates a pending order. No payment is taken.</span>
              </div>
              <button class="checkout-button" type="button" :disabled="checkoutLoading || quoteLoading" @click="checkout">
                {{ checkoutLoading ? 'Reserving your pair…' : 'Reserve pair & create order' }}
              </button>
            </div>

            <p v-if="quoteError" class="form-error" role="alert">{{ quoteError }}</p>
            <p v-if="checkoutError" class="form-error checkout-error" role="alert">{{ checkoutError }}</p>
            <button v-if="!order" :class="quote && !expired ? 'text-button refresh-button' : 'primary-button quote-button'" type="button" :disabled="quoteLoading || checkoutLoading" @click="requestQuote">
              {{ quoteLoading ? 'Requesting price…' : quote && !expired ? 'Refresh price' : 'Get current price' }}
            </button>
          </template>
        </div>
      </section>
    </template>
  </div>
</template>
