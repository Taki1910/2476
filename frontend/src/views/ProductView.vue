<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api, ApiError, type ProductDetail, type SizeOption, type Variant } from '../api'
import { addToCart, cart } from '../cart'
import { errorCopy, formatVnd } from '../format'
import { messageLabel, t } from '../i18n'
import { PRODUCT_IMAGE_PLACEHOLDER, productAlt } from '../product-media'
import { evidenceHighlights, productPositioning } from '../product-evidence'
import FitAssistant from '../components/FitAssistant.vue'
import ProductPresentationSummary from '../components/ProductPresentationSummary.vue'

const route = useRoute()
const router = useRouter()
const product = ref<ProductDetail>()
const selected = ref<Variant>()
const selectedColor = ref('')
const requestedSize = ref('')
const loading = ref(true)
const error = ref('')
const notFound = ref(false)
const cartMessage = ref('')
const cartError = ref('')
const fitNotice = ref('')
const added = ref(false)
const selectedMediaIndex = ref(0)
const fitGuide = ref<HTMLDetailsElement>()
const fitGuideSummary = ref<HTMLElement>()
let addTimer: ReturnType<typeof setTimeout> | undefined
let toastTimer: ReturnType<typeof setTimeout> | undefined
let loadVersion = 0

const productPrice = computed(() => {
  if (!product.value) return { label: t('Price'), value: '' }
  const pricing = product.value.pricing
  return pricing.state === 'RANGE'
    ? { label: t('Price range'), value: `${formatVnd(pricing.minimumAmount)} – ${formatVnd(pricing.maximumAmount)}` }
    : { label: t('Price'), value: formatVnd(pricing.minimumAmount) }
})
const selectedMedia = computed(() => product.value?.media[selectedMediaIndex.value])
const selectedColorOption = computed(() => product.value?.options.find(option => option.color === selectedColor.value))
const requestedOption = computed(() => selectedColorOption.value?.sizes.find(option => option.size === requestedSize.value))
const productTaxonomy = computed(() => [product.value?.category, product.value?.collection]
  .filter((value): value is string => Boolean(value)).map(value => t(value)).join(' · '))
const productEvidence = computed(() => {
  const statement = productPositioning(product.value?.evidence)
  return statement ? t(statement) : evidenceHighlights(product.value?.evidence).map(value => t(value)).join(' · ')
})
const fitSummary = computed(() => product.value?.fitSummary
  ? `${fitTendencyLabel(product.value.fitSummary.fitTendency)} · ${widthProfileLabel(product.value.fitSummary.widthProfile)}`
  : '')
const catalogDestination = computed(() => {
  const query = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  if (query) return { path: '/', query: { q: query }, hash: '#catalog-heading' }
  if (route.query.view === 'all') return { path: '/', query: { view: 'all' }, hash: '#catalog-heading' }
  return { path: '/', query: { view: 'all' }, hash: '#catalog-heading' }
})
const backLabel = computed(() => typeof route.query.q === 'string' && route.query.q.trim() ? 'Back to results' : 'All products')

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
    applyRouteVariant()
  } catch (reason) {
    if (version !== loadVersion) return
    if (reason instanceof ApiError && reason.status === 404) notFound.value = true
    else error.value = errorCopy(reason)
  } finally { if (version === loadVersion) loading.value = false }
}

function applyRouteVariant() {
  const intended = product.value?.variants.find(variant => variant.id === route.query.variant)
  selectedColor.value = intended?.color ?? ''
  requestedSize.value = intended?.size ?? ''
  selected.value = intended?.availability === 'AVAILABLE' ? intended : undefined
  resetSelectionFeedback()
}

async function openFitGuide() {
  if (!fitGuide.value) return
  fitGuide.value.open = true
  await nextTick()
  fitGuideSummary.value?.focus()
}

function resetSelectionFeedback() {
  cartMessage.value = ''; cartError.value = ''; fitNotice.value = ''
}

function resolve(option: SizeOption | undefined) {
  selected.value = option?.availability === 'AVAILABLE' && option.variantId
    ? product.value?.variants.find(variant => variant.id === option.variantId)
    : undefined
}

function chooseColor(color: string) {
  selectedColor.value = color
  selected.value = undefined
  resetSelectionFeedback()
  resolve(product.value?.options.find(option => option.color === color)?.sizes
    .find(option => option.size === requestedSize.value))
}

function chooseSize(option: SizeOption) {
  requestedSize.value = option.size
  resetSelectionFeedback()
  resolve(option)
}

function selectFitSize(size: string) {
  if (!product.value) return
  requestedSize.value = size
  resolve(selectedColorOption.value?.sizes.find(option => option.size === size))
  fitNotice.value = selected.value || !selectedColor.value ? '' : 'Recommended size is unavailable in the selected color.'
}

function selectFitColor(color: string, size?: string) {
  if (size) requestedSize.value = size
  chooseColor(color)
}

function add() {
  if (added.value || !selected.value || !product.value || selected.value.availability !== 'AVAILABLE') return false
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
      clearTimeout(addTimer); clearTimeout(toastTimer)
      addTimer = setTimeout(() => { added.value = false }, 700)
      toastTimer = setTimeout(() => { cartMessage.value = '' }, 3500)
    }
    cartError.value = result === 'checkout-pending' ? 'Resolve your previous checkout in the cart before making changes.'
      : result === 'max-lines' ? 'Your cart can contain up to 50 size/color selections.'
      : result === 'max-quantity' ? 'Your cart already has the maximum quantity.' : ''
    return result === 'added'
  } catch (reason) { cartError.value = errorCopy(reason); return false }
}

function buyNow() {
  if (add()) void router.push('/cart')
}

watch(() => route.params.id, loadProduct)
watch(() => route.query.variant, applyRouteVariant)
onMounted(loadProduct)
onBeforeUnmount(() => { ++loadVersion; clearTimeout(addTimer); clearTimeout(toastTimer) })
</script>

<template>
  <div class="product-page">
    <RouterLink class="back-link" :to="catalogDestination"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19 12H5m6 6-6-6 6-6" /></svg>{{ t(backLabel) }}</RouterLink>

    <div v-if="loading" class="detail-loading" role="status" aria-live="polite" :aria-label="t('Loading…')"><div class="skeleton-block"></div><div class="skeleton-lines"><span></span><span></span><span></span></div></div>
    <section v-else-if="notFound" class="centered-state"><p class="state-code">404</p><h1>{{ t('Product not found') }}</h1><p>{{ t('It may be unpublished or no longer available in the storefront.') }}</p><RouterLink class="text-button" to="/">{{ t('Return to products') }}</RouterLink></section>
    <section v-else-if="error" class="inline-state" role="alert"><h1>{{ t('Couldn’t load this product') }}</h1><p>{{ messageLabel(error) }}</p><button class="text-button" type="button" @click="loadProduct">{{ t('Try again') }}</button></section>

    <template v-else-if="product">
      <section class="product-decision">
        <div class="product-media-region">
          <figure class="product-detail-figure">
            <div class="product-detail-image"><img :src="selectedMedia?.url ?? PRODUCT_IMAGE_PLACEHOLDER" :alt="productAlt(product.name)" width="1456" height="1092" /></div>
            <figcaption>{{ t('Illustrative product view. Your color and size selection below is what applies to the cart.') }}</figcaption>
          </figure>
          <div v-if="product.media.length > 1" class="product-media-picker" role="group" :aria-label="t('Product images')">
            <button v-for="(media, index) in product.media" :key="`${media.position}-${media.url}`" type="button" class="product-media-option" :class="{ selected: selectedMediaIndex === index }" :aria-label="t('Show image {position} of {total}', { position: index + 1, total: product.media.length })" :aria-pressed="selectedMediaIndex === index" @click="selectedMediaIndex = index"><img :src="media.url" alt="" width="160" height="120" /></button>
          </div>
        </div>
        <div class="product-decision-panel">
          <header class="product-identity">
            <h1>{{ product.name }}</h1>
            <p v-if="productTaxonomy" class="product-taxonomy">{{ productTaxonomy }}</p>
            <p class="product-option-count">{{ product.variants.length }} {{ t(product.variants.length === 1 ? 'available option' : 'size/color options') }}</p>
            <div v-if="productEvidence || fitSummary" class="product-decision-evidence">
              <p v-if="productEvidence">{{ productEvidence }}</p>
              <p v-if="fitSummary"><span>{{ t('Fit') }}</span><strong>{{ fitSummary }}</strong></p>
            </div>
          </header>

          <div class="public-price" aria-live="polite"><span>{{ selected ? t('Selected price') : productPrice.label }}</span><strong>{{ selected ? formatVnd(selected.amount) : productPrice.value }}</strong></div>

          <section class="selection-panel" aria-labelledby="variant-heading">
            <div class="selection-copy"><h2 id="variant-heading">{{ t('Choose a color') }}</h2><p>{{ t('Availability can change until your order is placed.') }}</p></div>
            <div v-if="!product.options.length" class="inline-state"><h3>{{ t('No sizes available') }}</h3><p>{{ t('This product cannot be added to a cart right now.') }}</p></div>
            <template v-else>
              <fieldset class="color-list"><legend>{{ t('Color') }}</legend><button v-for="option in product.options" :key="option.color" type="button" class="color-option" :class="{ selected: selectedColor === option.color }" :aria-pressed="selectedColor === option.color" @click="chooseColor(option.color)"><span>{{ t(option.color) }}</span></button></fieldset>
              <fieldset v-if="selectedColorOption" class="variant-list"><legend>{{ t('Choose your size') }}</legend><p v-if="product.fitGuidance" class="size-help"><span>{{ t('Not sure about size?') }}</span><button class="text-button" type="button" @click="openFitGuide">{{ t('View the size and fit guide') }}</button></p><button v-for="option in selectedColorOption.sizes" :key="option.size" type="button" class="variant-option size-option" :class="{ selected: requestedSize === option.size }" :aria-pressed="requestedSize === option.size" :disabled="option.availability === 'UNAVAILABLE'" @click="chooseSize(option)"><span class="variant-size">{{ option.size }}</span><span class="availability" :class="option.availability.toLowerCase()">{{ t(requestedSize === option.size && option.availability === 'UNAVAILABLE' ? 'Requested · Unavailable' : requestedSize === option.size ? 'Selected' : option.availability === 'AVAILABLE' ? 'Available' : 'Unavailable') }}</span></button></fieldset>
              <div v-else class="selection-required"><strong>{{ t('Choose a color first') }}</strong><span>{{ t('Choose a color before selecting a size.') }}</span></div>
              <div v-if="requestedOption?.availability === 'UNAVAILABLE' && requestedOption.availableAlternativeColors.length" class="option-recovery"><strong>{{ t('Available in another color:') }}</strong><div><button v-for="color in requestedOption.availableAlternativeColors" :key="color" class="text-button" type="button" @click="chooseColor(color)">{{ t('Available in {color}', { color: t(color) }) }}</button></div></div>
            </template>

            <div class="product-add-panel">
              <div v-if="selected" class="selected-summary"><span>{{ t('Selected') }}</span><strong>{{ t('Size') }} {{ selected.size }} · {{ t(selected.color) }}</strong></div>
              <div class="purchase-assurance"><p>{{ t('Review final price and availability in your cart, where you can choose pickup or delivery.') }}</p></div>
              <div v-if="cartMessage" class="cart-toast" role="status" aria-live="polite"><span>{{ t(cartMessage) }}</span><RouterLink to="/cart">{{ t('View cart') }}</RouterLink></div>
              <p v-if="cartError" class="form-error" role="alert">{{ messageLabel(cartError) }}</p>
              <p v-if="cart.storageError" class="form-error" role="alert">{{ t('Browser storage is unavailable. Your cart may not survive a refresh.') }}</p>
              <div class="product-actions"><button class="primary-button quote-button" type="button" :disabled="added || !selected" @click="add">{{ t(!selectedColor ? 'Choose a color first' : !selected ? requestedSize ? 'Size unavailable' : 'Choose a size to add' : added ? 'Added to cart.' : 'Add to cart') }}</button><button class="text-button" type="button" :disabled="added || !selected" @click="buyNow">{{ t('Buy now') }}</button><RouterLink class="text-button refresh-button" to="/cart">{{ t('View cart') }}</RouterLink></div>
            </div>
          </section>
        </div>
      </section>

      <ProductPresentationSummary :presentation="product.presentation" />

      <details v-if="product.fitGuidance" id="size-and-fit" ref="fitGuide" class="product-fit-evidence">
        <summary ref="fitGuideSummary">{{ t('Size and fit guide') }}</summary>
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

      <FitAssistant :product-id="product.id" :fit-supported="product.fitSupported" :fit-guidance="product.fitGuidance" :selected-color="selectedColor || undefined" :variants="product.variants" @select-size="selectFitSize" @select-color="selectFitColor" />
      <p v-if="fitNotice" class="form-error fit-selection-notice" role="alert">{{ t(fitNotice) }}</p>
    </template>
  </div>
</template>
