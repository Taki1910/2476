<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, ApiError, type ProductDetail, type Variant } from '../api'
import { addToCart, cart } from '../cart'
import { errorCopy, formatVnd } from '../format'
import { locale, messageLabel, t } from '../i18n'
import { productAlt, productMedia } from '../product-media'
import FitAssistant from '../components/FitAssistant.vue'

const route = useRoute()
const product = ref<ProductDetail>()
const selected = ref<Variant>()
const loading = ref(true)
const error = ref('')
const notFound = ref(false)
const cartMessage = ref('')
const cartError = ref('')
const fitNotice = ref('')

async function loadProduct() {
  loading.value = true; error.value = ''; notFound.value = false; cartMessage.value = ''; cartError.value = ''; fitNotice.value = ''
  try {
    product.value = await api.product(String(route.params.id))
    const intended = product.value.variants.find(variant => variant.id === route.query.variant)
    selected.value = intended ?? product.value.variants.find(variant => variant.availability === 'AVAILABLE')
  } catch (reason) {
    if (reason instanceof ApiError && reason.status === 404) notFound.value = true
    else error.value = errorCopy(reason)
  } finally { loading.value = false }
}

function choose(variant: Variant) {
  selected.value = variant; cartMessage.value = ''; cartError.value = ''; fitNotice.value = ''
}

function selectFitSize(size: string) {
  if (!product.value) return
  const matchingColor = product.value.variants.find(variant => variant.size === size && variant.color === selected.value?.color)
  if (matchingColor) { choose(matchingColor); return }
  const availableSize = product.value.variants.find(variant => variant.size === size && variant.availability === 'AVAILABLE')
  if (availableSize && !selected.value) { choose(availableSize); return }
  fitNotice.value = 'Recommended size is unavailable in the selected color.'
}

function selectFitColor(color: string, size?: string) {
  const variant = product.value?.variants.find(candidate => candidate.color === color && candidate.size === size
    && candidate.availability === 'AVAILABLE')
  if (variant) choose(variant)
}

function add() {
  if (!selected.value || !product.value || selected.value.availability !== 'AVAILABLE') return
  cartMessage.value = ''; cartError.value = ''
  try {
    const result = addToCart({
      productId: product.value.id, productName: product.value.name, variantId: selected.value.id,
      sku: selected.value.sku, size: selected.value.size, color: selected.value.color,
      image: product.value.primaryImage ?? productMedia(product.value.name)?.src ?? null,
      amount: selected.value.amount, currency: 'VND',
    })
    cartMessage.value = result === 'added' ? 'Added to cart.' : ''
    cartError.value = result === 'checkout-pending' ? 'Resolve your previous checkout in the cart before making changes.'
      : result === 'max-lines' ? 'Your cart can contain up to 50 different variants.'
      : result === 'max-quantity' ? 'Your cart already has the maximum quantity.' : ''
  } catch (reason) { cartError.value = errorCopy(reason) }
}

watch(() => route.params.id, loadProduct)
onMounted(loadProduct)
</script>

<template>
  <div class="product-page">
    <RouterLink class="back-link" to="/"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>{{ t('All products') }}</RouterLink>

    <div v-if="loading" class="detail-loading" role="status" aria-live="polite" :aria-label="t('Loading…')"><div class="skeleton-block"></div><div class="skeleton-lines"><span></span><span></span><span></span></div></div>
    <section v-else-if="notFound" class="centered-state"><p class="state-code">404</p><h1>{{ t('Product not found') }}</h1><p>{{ t('It may be unpublished or no longer available in the storefront.') }}</p><RouterLink class="text-button" to="/">{{ t('Return to products') }}</RouterLink></section>
    <section v-else-if="error" class="inline-state" role="alert"><h1>{{ t('Couldn’t load this product') }}</h1><p>{{ messageLabel(error) }}</p><button class="text-button" type="button" @click="loadProduct">{{ t('Try again') }}</button></section>

    <template v-else-if="product">
      <section class="product-identity">
        <div class="product-detail-image"><img :src="product.primaryImage ?? productMedia(product.name)?.src" :alt="productAlt(product.name, locale) ?? product.name" width="1456" height="1092" /></div>
        <div><h1>{{ product.name }}</h1><p>{{ product.variants.length }} {{ t(product.variants.length === 1 ? 'available option' : 'size/color options') }}</p><p class="field-help">{{ t('Choose a size to see the current price and add it to your cart.') }}</p></div>
      </section>

      <FitAssistant :product-id="product.id" :fit-supported="product.fitSupported" :selected-color="selected?.color" :variants="product.variants" @select-size="selectFitSize" @select-color="selectFitColor" />
      <p v-if="fitNotice" class="form-error fit-selection-notice" role="alert">{{ t(fitNotice) }}</p>

      <section class="selection-panel" aria-labelledby="variant-heading">
        <div class="selection-copy"><h2 id="variant-heading">{{ t('Choose your size') }}</h2><p>{{ t('Availability can change while other customers check out. Exact store stock stays private.') }}</p></div>
        <div v-if="!product.variants.length" class="inline-state"><h3>{{ t('No sizes available') }}</h3><p>{{ t('This product cannot be added to a cart right now.') }}</p></div>
        <fieldset v-else class="variant-list"><legend class="sr-only">{{ t('Available sizes and colors') }}</legend><button v-for="variant in product.variants" :key="variant.id" type="button" class="variant-option" :class="{ selected: selected?.id === variant.id }" :disabled="variant.availability === 'UNAVAILABLE'" :aria-pressed="selected?.id === variant.id" @click="choose(variant)"><span class="variant-size">{{ variant.size }}</span><span class="variant-color">{{ t(variant.color) }}</span><span class="availability" :class="variant.availability.toLowerCase()">{{ t(selected?.id === variant.id ? 'Selected' : variant.availability === 'AVAILABLE' ? 'Available' : 'Unavailable') }}</span></button></fieldset>

        <div class="quote-panel product-add-panel">
          <div v-if="!selected" class="quote-placeholder"><strong>{{ t('Price waits for your size.') }}</strong><span>{{ t('Select an available variant to add it to your cart.') }}</span></div>
          <template v-else>
            <div class="selected-summary"><span>{{ t('Selected') }}</span><strong>{{ t('Size') }} {{ selected.size }} · {{ t(selected.color) }}</strong><small>SKU {{ selected.sku }}</small></div>
            <div class="public-price"><span>{{ t('Current price') }}</span><strong>{{ formatVnd(selected.amount) }}</strong></div>
            <p class="quote-note">{{ t('Price and availability are checked again by the server at checkout.') }}</p>
            <p v-if="cartMessage" class="success-message" role="status" aria-live="polite">{{ t(cartMessage) }}</p>
            <p v-if="cartError" class="form-error" role="alert">{{ messageLabel(cartError) }}</p>
            <p v-if="cart.storageError" class="form-error" role="alert">{{ t('Browser storage is unavailable. Your cart may not survive a refresh.') }}</p>
            <div class="product-actions"><button class="primary-button quote-button" type="button" :disabled="selected.availability !== 'AVAILABLE'" @click="add">{{ t('Add to cart') }}</button><RouterLink class="text-button refresh-button" to="/cart">{{ t('View cart') }}</RouterLink></div>
          </template>
        </div>
      </section>
    </template>
  </div>
</template>
