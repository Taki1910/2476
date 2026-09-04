<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { api, normalizeCartDemand, type CartQuote, type FulfillmentChoice, type Order } from '../api'
import { cart, cartCount, changedQuoteItems, removeFromCart, removePurchasedItems, setCartQuantity } from '../cart'
import { beginCheckout, checkoutLocked, checkoutRecovery, clearCheckout, invalidateRejectedCheckout, paymentDestination, readCheckout, recoverablePayment, submitCheckout } from '../checkout'
import CommerceItems from '../components/CommerceItems.vue'
import { cartErrorCopy, errorCopy, formatExpiry, formatVnd, isExpired } from '../format'
import { locale, messageLabel, t } from '../i18n'
import { productAlt, productMedia } from '../product-media'
import { session } from '../session'

const router = useRouter()
const quote = ref<CartQuote>()
const quoteLoading = ref(false)
const checkoutLoading = ref(false)
const failure = ref<unknown>()
const paymentLoading = ref(false)
const paymentError = ref('')
const order = ref<Order>()
const now = ref(Date.now())
const reviewPanel = ref<HTMLElement>()
const orderPanel = ref<HTMLElement>()
const fulfillmentType = ref<'PICKUP' | 'DELIVERY'>('PICKUP')
const pickupLocationId = ref('')
const receiverName = ref('')
const receiverPhone = ref('')
const deliveryAddress = ref('')
const deliveryNote = ref('')
let timer: number | undefined
let disposed = false

const expired = computed(() => quote.value ? isExpired(quote.value.expiresAt, now.value) : false)
const changes = computed(() => quote.value ? changedQuoteItems(quote.value, cart.items) : [])
const error = computed(() => failure.value ? cartErrorCopy(failure.value, cart.items) : '')
const affectedVariant = computed(() => (failure.value as { variantId?: string } | undefined)?.variantId)
const busy = computed(() => quoteLoading.value || checkoutLoading.value)
const demandIdentity = computed(() => JSON.stringify(cart.items.map(({ variantId, quantity }) => ({ variantId, quantity }))))
const command = computed(() => checkoutRecovery.value.command)
const unresolved = computed(() => !!command.value && !command.value.rejected)
const fulfillment = computed<FulfillmentChoice | undefined>(() => {
  if (fulfillmentType.value === 'PICKUP') {
    return pickupLocationId.value ? { type: 'PICKUP', pickupLocationId: pickupLocationId.value } : undefined
  }
  if (!receiverName.value.trim() || !receiverPhone.value.trim() || !deliveryAddress.value.trim()) return
  return { type: 'DELIVERY', delivery: { receiverName: receiverName.value.trim(), receiverPhone: receiverPhone.value.trim(),
    address: deliveryAddress.value.trim(), ...(deliveryNote.value.trim() ? { note: deliveryNote.value.trim() } : {}) } }
})

function editQuantity(variantId: string, event: Event) {
  try { setCartQuantity(variantId, Number((event.target as HTMLSelectElement).value)) }
  catch (reason) { failure.value = reason }
}
function remove(variantId: string) {
  try { removeFromCart(variantId) }
  catch (reason) { failure.value = reason }
}
function fieldValue(event: Event) { return (event.target as HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement).value }

async function requestQuote() {
  if (busy.value || checkoutLocked.value || !cart.items.length) return
  if (!session.account) { await router.push({ path: '/login', query: { returnTo: '/cart' } }); return }
  quoteLoading.value = true; failure.value = undefined; quote.value = undefined
  const identity = demandIdentity.value
  const accountId = session.account.accountId
  try {
    invalidateRejectedCheckout()
    const result = await api.cartQuote(normalizeCartDemand(cart.items))
    if (disposed || identity !== demandIdentity.value || accountId !== session.account?.accountId) return
    quote.value = result; now.value = Date.now()
    if (!pickupLocationId.value || !result.pickupLocations.some(location => location.id === pickupLocationId.value)) {
      pickupLocationId.value = result.pickupLocations[0]?.id ?? ''
    }
    await nextTick(); reviewPanel.value?.focus()
  } catch (reason) { if (!disposed && identity === demandIdentity.value && accountId === session.account?.accountId) failure.value = reason }
  finally { quoteLoading.value = false }
}

async function checkout() {
  if (busy.value || !session.account || checkoutRecovery.value.storageError) return
  if (!command.value && (!quote.value || expired.value || !cart.items.length || !fulfillment.value)) return
  checkoutLoading.value = true; failure.value = undefined
  try {
    const submitted = command.value ?? beginCheckout(session.account.accountId, quote.value!.id, cart.items, fulfillment.value!)
    const result = await submitCheckout(submitted)
    // A response from a prior screen may finish cleanup, but must not reveal
    // another account's order or redirect the customer's current navigation.
    if (readCheckout(submitted.accountId)?.key === submitted.key) {
      removePurchasedItems(submitted.items)
      clearCheckout(submitted.accountId)
    }
    if (disposed || session.account?.accountId !== submitted.accountId) return
    order.value = result; quote.value = undefined
    await nextTick(); orderPanel.value?.focus()
  } catch (reason) { if (!disposed) failure.value = reason }
  finally { checkoutLoading.value = false }
}

async function startPayment() {
  if (!order.value || order.value.status !== 'PENDING_PAYMENT' || paymentLoading.value) return
  paymentLoading.value = true; paymentError.value = ''
  try {
    const result = await recoverablePayment(order.value.id)
    if (!disposed) window.location.assign(paymentDestination(result))
  } catch (reason) { paymentError.value = errorCopy(reason); paymentLoading.value = false }
}

watch(demandIdentity, () => {
  quote.value = undefined; failure.value = undefined
})
onMounted(() => { timer = window.setInterval(() => { now.value = Date.now() }, 1000) })
onBeforeUnmount(() => { disposed = true; if (timer) window.clearInterval(timer) })
</script>

<template>
  <div class="cart-page">
    <div class="page-topline"><RouterLink class="back-link" to="/"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>{{ t('Continue shopping') }}</RouterLink></div>

    <section v-if="checkoutRecovery.storageError" class="inline-state" role="alert">
      <h1>{{ t('Checkout recovery unavailable') }}</h1><p>{{ t('Browser storage is unavailable. Enable it before checkout so a retry cannot create another order.') }}</p><RouterLink class="text-button" to="/orders">{{ t('My Orders') }}</RouterLink>
    </section>
    <section v-else-if="!cart.items.length && !order && !command" class="cart-empty centered-state">
      <p class="state-code">0</p><h1>{{ t('Your cart is empty.') }}</h1><p>{{ t('Choose a shoe and size to start your order.') }}</p><RouterLink class="primary-button" to="/">{{ t('Shop shoes') }}</RouterLink>
    </section>

    <section v-if="!order && (cart.items.length || command)" class="cart-layout" aria-labelledby="cart-title">
      <div class="cart-main">
        <h1 id="cart-title">{{ t('Review your cart.') }}</h1>
        <p class="cart-lede">{{ t('Several shoes. One order and one payment. Review the server-confirmed prices before creating your order.') }}</p>
        <p v-if="cart.storageError" class="form-error" role="alert">{{ t('Browser storage is unavailable. Your cart may not survive a refresh.') }}</p>
        <div v-if="command" class="terminal-guidance" role="status">
          <p>{{ t(unresolved ? 'Your last checkout may already have created an order. Retry the same request to recover it before changing your cart.' : 'The server rejected checkout. Retry the same request, or edit the cart and request a new quote.') }}</p>
          <p>{{ t('The saved request contains {lines} variants and {units} units.', { lines: command.items.length, units: command.items.reduce((sum, item) => sum + item.quantity, 0) }) }}</p>
          <RouterLink class="text-button" to="/orders">{{ t('My Orders') }}</RouterLink>
        </div>
        <article v-for="item in cart.items" :key="item.variantId" class="cart-line" :aria-labelledby="'cart-line-' + item.variantId">
          <div class="cart-line-image"><img v-if="item.image ?? productMedia(item.productName)?.src" :src="item.image ?? productMedia(item.productName)?.src" :alt="productAlt(item.productName, locale) ?? item.productName" width="480" height="360" loading="lazy" /></div>
          <div class="cart-line-copy">
            <h2 :id="'cart-line-' + item.variantId"><RouterLink :to="{ path: '/products/' + item.productId, query: { variant: item.variantId } }">{{ item.productName }}</RouterLink></h2>
            <p>{{ t('Size') }} {{ item.size }} · {{ t(item.color) }} · SKU {{ item.sku }}</p>
            <p class="field-help">{{ t('Price when added') }}: {{ formatVnd(item.amount) }}</p>
            <p v-for="changed in changes.filter(line => line.variantId === item.variantId)" :key="changed.variantId" class="cart-change" role="status">{{ t('{name} · Size {size} is now {amount}. Review the updated total before continuing.', { name: item.productName, size: item.size, amount: formatVnd(changed.unitPriceAmount) }) }}</p>
            <p v-if="affectedVariant === item.variantId && error" :id="'cart-error-' + item.variantId" class="cart-change" role="alert">{{ error }}</p>
          </div>
          <div class="cart-line-controls">
            <label :for="'cart-quantity-' + item.variantId">{{ t('Quantity') }}<span class="sr-only"> · {{ item.productName }} · {{ item.sku }}</span></label>
            <select :id="'cart-quantity-' + item.variantId" :value="item.quantity" :disabled="busy || checkoutLocked" :aria-describedby="affectedVariant === item.variantId ? 'cart-error-' + item.variantId : undefined" @change="editQuantity(item.variantId, $event)"><option v-for="quantity in 10" :key="quantity" :value="quantity">{{ quantity }}</option></select>
            <button class="text-button" type="button" :disabled="busy || checkoutLocked" :aria-label="t('Remove {name}, size {size}', { name: item.productName, size: item.size })" @click="remove(item.variantId)">{{ t('Remove') }}</button>
          </div>
        </article>
      </div>

      <aside class="cart-summary" aria-labelledby="summary-title">
        <h2 id="summary-title">{{ t('Checkout') }}</h2>
        <dl><div><dt>{{ t('Variants') }}</dt><dd>{{ cart.items.length }}</dd></div><div><dt>{{ t('Total units') }}</dt><dd>{{ cartCount }}</dd></div></dl>
        <p class="field-help">{{ t('Cart count means total units. Prices shown when added are not a quote. The server confirms every line and the full total.') }}</p>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <p v-if="quote" class="cart-compact-total"><span>{{ t('Order total') }}</span><strong>{{ formatVnd(quote.totalAmount) }}</strong></p>
        <div v-if="quote" ref="reviewPanel" class="cart-quote" tabindex="-1" aria-labelledby="quote-heading">
          <h3 id="quote-heading">{{ t(expired ? 'Quote expired' : 'Review confirmed prices') }}</h3>
          <ul class="cart-quote-lines"><li v-for="line in quote.items" :key="line.variantId"><span>{{ line.productName }} · {{ t('Size') }} {{ line.size }} · {{ t(line.color) }}</span><small>{{ t('Unit price') }} {{ formatVnd(line.unitPriceAmount) }} · {{ t('Quantity') }} {{ line.quantity }}</small><b>{{ t('Subtotal') }} {{ formatVnd(line.totalAmount) }}</b></li></ul>
          <span>{{ t('Order total') }}</span><strong>{{ formatVnd(quote.totalAmount) }}</strong>
          <small>{{ t('Valid until') }} {{ formatExpiry(quote.expiresAt) }}</small>
          <p>{{ t(expired ? 'Request a fresh quote before continuing.' : 'These prices are held until the quote expires. Stock is confirmed when you create the order.') }}</p>
          <fieldset class="fulfillment-choice">
            <legend>{{ t('How should we fulfill this order?') }}</legend>
            <div class="fulfillment-options">
              <label><input type="radio" value="PICKUP" :checked="fulfillmentType === 'PICKUP'" @change="fulfillmentType = 'PICKUP'" /> <span><strong>{{ t('Pick up in store') }}</strong><small>{{ t('Choose a location that can fulfill every item.') }}</small></span></label>
              <label><input type="radio" value="DELIVERY" :checked="fulfillmentType === 'DELIVERY'" @change="fulfillmentType = 'DELIVERY'" /> <span><strong>{{ t('Deliver to an address') }}</strong><small>{{ t('Delivery fee') }}: {{ formatVnd(0) }}</small></span></label>
            </div>
            <label v-if="fulfillmentType === 'PICKUP'" class="field-stack" for="pickup-location">{{ t('Pickup location') }}
              <select id="pickup-location" :value="pickupLocationId" required @change="pickupLocationId = fieldValue($event)">
                <option v-for="location in quote.pickupLocations" :key="location.id" :value="location.id">{{ t(location.name) }} · {{ location.code }}</option>
              </select>
            </label>
            <div v-else class="delivery-fields">
              <p class="delivery-required-note">{{ t('Receiver name, phone, and delivery address are required.') }}</p>
              <label class="field-stack" for="receiver-name">{{ t('Receiver name') }}<input id="receiver-name" :value="receiverName" autocomplete="name" maxlength="120" required @input="receiverName = fieldValue($event)" /></label>
              <label class="field-stack" for="receiver-phone">{{ t('Receiver phone') }}<input id="receiver-phone" :value="receiverPhone" type="tel" autocomplete="tel" maxlength="32" required @input="receiverPhone = fieldValue($event)" /></label>
              <label class="field-stack delivery-address" for="delivery-address">{{ t('Delivery address') }}<textarea id="delivery-address" :value="deliveryAddress" autocomplete="street-address" maxlength="500" required @input="deliveryAddress = fieldValue($event)"></textarea></label>
              <label class="field-stack delivery-address" for="delivery-note">{{ t('Delivery note (optional)') }}<textarea id="delivery-note" :value="deliveryNote" maxlength="500" @input="deliveryNote = fieldValue($event)"></textarea></label>
            </div>
          </fieldset>
        </div>
        <button v-if="command" class="checkout-button cart-primary-action" type="button" :disabled="busy || !!checkoutRecovery.storageError" @click="checkout">{{ t(checkoutLoading ? 'Recovering order…' : 'Retry saved checkout') }}</button>
        <button v-if="!unresolved && (!quote || expired || command?.rejected)" class="primary-button cart-primary-action" type="button" :disabled="busy || checkoutLocked || !cart.items.length" @click="requestQuote">{{ t(quoteLoading ? 'Checking price…' : session.account ? 'Check price & availability' : 'Sign in to checkout') }}</button>
        <button v-if="quote && !expired && !command" class="checkout-button cart-primary-action" type="button" :disabled="busy || checkoutLocked || !fulfillment" @click="checkout">{{ t(checkoutLoading ? 'Creating order…' : 'Confirm total & create order') }}</button>
      </aside>
    </section>

    <section v-if="order" ref="orderPanel" class="order-confirmation cart-order-confirmation" tabindex="-1" aria-labelledby="order-confirmation-title">
      <h1 id="order-confirmation-title">{{ t(order.status === 'PENDING_PAYMENT' ? 'Your order is ready for payment.' : 'Your order was recovered.') }}</h1>
      <p>{{ t(order.fulfillmentType === 'DELIVERY' ? 'Keep this page or open My Orders. Delivery preparation starts after verified payment.' : 'Keep this page or open My Orders. The store will prepare your pickup after verified payment.') }}</p>
      <CommerceItems :items="order.items" />
      <dl><div><dt>{{ t('Order reference') }}</dt><dd>{{ order.orderReference }}</dd></div><div><dt>{{ t('Total units') }}</dt><dd>{{ order.quantity }}</dd></div><div><dt>{{ t('Total') }}</dt><dd>{{ formatVnd(order.totalAmount) }}</dd></div></dl>
      <p v-if="order.status === 'PENDING_PAYMENT' && order.reservationExpiresAt">{{ t('Your items are held until {time}.', { time: formatExpiry(order.reservationExpiresAt) }) }}</p>
      <div class="product-actions"><button v-if="order.status === 'PENDING_PAYMENT'" class="primary-button" type="button" :disabled="paymentLoading" @click="startPayment">{{ t(paymentLoading ? 'Opening VNPAY…' : 'Pay with VNPAY') }}</button><RouterLink class="text-button" :to="'/orders/' + order.id">{{ t('Track order') }}</RouterLink><RouterLink class="text-button" to="/orders">{{ t('My Orders') }}</RouterLink></div>
      <p v-if="paymentError" class="form-error" role="alert">{{ messageLabel(paymentError) }}</p>
    </section>
  </div>
</template>
