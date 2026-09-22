<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, ApiError, type ProductDetail, type Variant } from '../api'
import { addToCart, cart } from '../cart'
import { errorCopy, formatVnd } from '../format'
import { messageLabel, t } from '../i18n'
import { PRODUCT_IMAGE_PLACEHOLDER, productAlt } from '../product-media'
import FitAssistant from '../components/FitAssistant.vue'
import ProductPresentationSummary from '../components/ProductPresentationSummary.vue'

const route = useRoute()
const product = ref<ProductDetail>()
const selected = ref<Variant>()
const loading = ref(true)
const error = ref('')
const notFound = ref(false)
const cartMessage = ref('')
const cartError = ref('')
const fitNotice = ref('')
const added = ref(false)
const selectedMediaIndex = ref(0)
let addTimer: ReturnType<typeof setTimeout> | undefined
let loadVersion = 0

const productPrice = computed(() => {
  if (!product.value) return { label: t('Price'), value: '' }
  const pricing = product.value.pricing
  return pricing.state === 'RANGE'
    ? { label: t('Price range'), value: `${formatVnd(pricing.minimumAmount)} – ${formatVnd(pricing.maximumAmount)}` }
    : { label: t('Price'), value: formatVnd(pricing.minimumAmount) }
})
const selectedMedia = computed(() => product.value?.media[selectedMediaIndex.value])
const productTaxonomy = computed(() => [product.value?.category, product.value?.collection]
  .filter((value): value is string => Boolean(value)).map(value => t(value)).join(' · '))

function fitTendencyLabel(value: NonNullable<ProductDetail['fitGuidance']>['fitTendency']) {
  return t(value === 'RUNS_SMALL' ? 'Runs small' : value === 'RUNS_LARGE' ? 'Runs large' : 'True to size')
}

function widthProfileLabel(value: NonNullable<ProductDetail['fitGuidance']>['widthProfile']) {
  return t(value === 'NARROW' ? 'Narrow' : value === 'WIDE' ? 'Wide' : 'Regular')
}

async function loadProduct() {
  const version = ++loadVersion
  const productId = String(route.params.id)
  loading.value = true; error.value = ''; notFound.value = false; cartMessage.value = ''; cartError.value = ''; fitNotice.value = ''
  try {
    const result = await api.product(productId)
    if (version !== loadVersion) return
    product.value = result
    selectedMediaIndex.value = 0
    const intended = result.variants.find(variant => variant.id === route.query.variant)
    selected.value = intended
  } catch (reason) {
    if (version !== loadVersion) return
    if (reason instanceof ApiError && reason.status === 404) notFound.value = true
    else error.value = errorCopy(reason)
  } finally { if (version === loadVersion) loading.value = false }
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
  if (added.value || !selected.value || !product.value || selected.value.availability !== 'AVAILABLE') return
  cartMessage.value = ''; cartError.value = ''
  try {
    const result = addToCart({
      productId: product.value.id, productName: product.value.name, variantId: selected.value.id,
      sku: selected.value.sku, size: selected.value.size, color: selected.value.color,
      image: product.value.media[0]?.url ?? null,
      amount: selected.value.amount, currency: 'VND',
    })
    if (result === 'added') {
      cartMessage.value = t('Added {name} · In cart: {quantity}', { name: product.value.name,
        quantity: cart.items.find(item => item.variantId === selected.value!.id.toLowerCase())?.quantity ?? 0 })
      added.value = true
      addTimer = setTimeout(() => { added.value = false }, 700)
    }
    cartError.value = result === 'checkout-pending' ? 'Resolve your previous checkout in the cart before making changes.'
      : result === 'max-lines' ? 'Your cart can contain up to 50 different variants.'
      : result === 'max-quantity' ? 'Your cart already has the maximum quantity.' : ''
  } catch (reason) { cartError.value = errorCopy(reason) }
}

watch(() => route.params.id, loadProduct)
onMounted(loadProduct)
onBeforeUnmount(() => { ++loadVersion; clearTimeout(addTimer) })
</script>

<template>
  <div class="product-page">
    <RouterLink class="back-link" to="/"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>{{ t('All products') }}</RouterLink>

    <div v-if="loading" class="detail-loading" role="status" aria-live="polite" :aria-label="t('Loading…')"><div class="skeleton-block"></div><div class="skeleton-lines"><span></span><span></span><span></span></div></div>
    <section v-else-if="notFound" class="centered-state"><p class="state-code">404</p><h1>{{ t('Product not found') }}</h1><p>{{ t('It may be unpublished or no longer available in the storefront.') }}</p><RouterLink class="text-button" to="/">{{ t('Return to products') }}</RouterLink></section>
    <section v-else-if="error" class="inline-state" role="alert"><h1>{{ t('Couldn’t load this product') }}</h1><p>{{ messageLabel(error) }}</p><button class="text-button" type="button" @click="loadProduct">{{ t('Try again') }}</button></section>

    <template v-else-if="product">
      <section class="product-decision">
        <div class="product-media-region">
          <div class="product-detail-image"><img :src="selectedMedia?.url ?? PRODUCT_IMAGE_PLACEHOLDER" :alt="productAlt(product.name)" width="1456" height="1092" /></div>
          <div v-if="product.media.length > 1" class="product-media-picker" role="group" :aria-label="t('Product images')">
            <button v-for="(media, index) in product.media" :key="`${media.position}-${media.url}`" type="button" class="product-media-option" :class="{ selected: selectedMediaIndex === index }" :aria-label="t('Show image {position} of {total}', { position: index + 1, total: product.media.length })" :aria-pressed="selectedMediaIndex === index" @click="selectedMediaIndex = index"><img :src="media.url" alt="" width="160" height="120" /></button>
          </div>
        </div>
        <div class="product-decision-panel">
          <header class="product-identity">
            <h1>{{ product.name }}</h1>
            <p v-if="productTaxonomy" class="product-taxonomy">{{ productTaxonomy }}</p>
            <p class="product-option-count">{{ product.variants.length }} {{ t(product.variants.length === 1 ? 'available option' : 'size/color options') }}</p>
          </header>

          <div class="public-price" aria-live="polite"><span>{{ selected ? t('Selected price') : productPrice.label }}</span><strong>{{ selected ? formatVnd(selected.amount) : productPrice.value }}</strong></div>

          <section class="selection-panel" aria-labelledby="variant-heading">
            <div class="selection-copy"><h2 id="variant-heading">{{ t('Choose your size') }}</h2><p>{{ t('Availability can change until your order is placed.') }}</p></div>
            <div v-if="!product.variants.length" class="inline-state"><h3>{{ t('No sizes available') }}</h3><p>{{ t('This product cannot be added to a cart right now.') }}</p></div>
            <fieldset v-else class="variant-list"><legend class="sr-only">{{ t('Available sizes and colors') }}</legend><button v-for="variant in product.variants" :key="variant.id" type="button" class="variant-option" :class="{ selected: selected?.id === variant.id }" :disabled="variant.availability === 'UNAVAILABLE'" :aria-pressed="selected?.id === variant.id" @click="choose(variant)"><span class="variant-size">{{ variant.size }}</span><span class="variant-color">{{ t(variant.color) }}</span><span class="availability" :class="variant.availability.toLowerCase()">{{ t(selected?.id === variant.id ? variant.availability === 'AVAILABLE' ? 'Selected' : 'Selected · Unavailable' : variant.availability === 'AVAILABLE' ? 'Available' : 'Unavailable') }}</span></button></fieldset>

            <div class="product-add-panel">
              <div v-if="selected" class="selected-summary"><span>{{ t('Selected') }}</span><strong>{{ t('Size') }} {{ selected.size }} · {{ t(selected.color) }}</strong><small>SKU {{ selected.sku }}</small></div>
              <div v-else class="selection-required"><strong>{{ t('Select a size first') }}</strong><span>{{ t('Select an available size before adding this product to your cart.') }}</span></div>
              <div class="purchase-assurance"><p>{{ t('Review final price and availability in your cart, where you can choose pickup or delivery.') }}</p></div>
              <p v-if="cartMessage" class="success-message" role="status" aria-live="polite">{{ t(cartMessage) }}</p>
              <p v-if="cartError" class="form-error" role="alert">{{ messageLabel(cartError) }}</p>
              <p v-if="cart.storageError" class="form-error" role="alert">{{ t('Browser storage is unavailable. Your cart may not survive a refresh.') }}</p>
              <div class="product-actions"><button class="primary-button quote-button" type="button" :disabled="added || !selected || selected.availability !== 'AVAILABLE'" @click="add">{{ t(!selected ? 'Choose a size to add' : selected.availability !== 'AVAILABLE' ? 'Size unavailable' : added ? 'Added to cart.' : 'Add to cart') }}</button><RouterLink class="text-button refresh-button" to="/cart">{{ t('View cart') }}</RouterLink></div>
            </div>
          </section>
        </div>
      </section>

      <ProductPresentationSummary :presentation="product.presentation" />

      <details v-if="product.fitGuidance" class="product-fit-evidence">
        <summary>{{ t('Size and fit guide') }}</summary>
        <div class="product-fit-evidence-body">
          <dl class="product-fit-profile">
            <div><dt>{{ t('Sizing system') }}</dt><dd>{{ product.fitGuidance.sizeSystem }}</dd></div>
            <div><dt>{{ t('Fit tendency') }}</dt><dd>{{ fitTendencyLabel(product.fitGuidance.fitTendency) }}</dd></div>
            <div><dt>{{ t('Width profile') }}</dt><dd>{{ widthProfileLabel(product.fitGuidance.widthProfile) }}</dd></div>
          </dl>
          <div class="product-fit-table">
            <table>
              <thead><tr><th scope="col">{{ t('Size') }}</th><th scope="col">{{ t('Foot length') }}</th><th scope="col">{{ t('Foot width') }}</th></tr></thead>
              <tbody><tr v-for="range in product.fitGuidance.ranges" :key="range.size"><th scope="row">{{ range.size }}</th><td>{{ range.minimumFootLengthMm }}–{{ range.maximumFootLengthMm }} mm</td><td>{{ range.minimumFootWidthMm }}–{{ range.maximumFootWidthMm }} mm</td></tr></tbody>
            </table>
          </div>
          <p class="fit-evidence-disclaimer">{{ t('These measurements are sizing guidance, not a guarantee of fit.') }}</p>
        </div>
      </details>

      <FitAssistant :product-id="product.id" :fit-supported="product.fitSupported" :selected-color="selected?.color" :variants="product.variants" @select-size="selectFitSize" @select-color="selectFitColor" />
      <p v-if="fitNotice" class="form-error fit-selection-notice" role="alert">{{ t(fitNotice) }}</p>
    </template>
  </div>
</template>
