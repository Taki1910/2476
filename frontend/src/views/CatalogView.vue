<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api, type InterpretedQuery, type ProductSummary, type PublicOffer } from '../api'
import StorefrontHomepage from '../components/StorefrontHomepage.vue'
import StorefrontIntentLinks from '../components/StorefrontIntentLinks.vue'
import ProductPresentationSummary from '../components/ProductPresentationSummary.vue'
import { errorCopy, formatVnd } from '../format'
import { messageLabel, t } from '../i18n'
import type { StorefrontHomepage as StorefrontHomepageModel } from '../merchandising'
import { catalogScrollTicket, completeCatalogScrollRestore, prepareCatalogScrollRestore } from '../navigation'
import { PRODUCT_IMAGE_PLACEHOLDER, productAlt, productListingImage } from '../product-media'
import { evidenceHighlights, evidenceValueLabel, productPositioning } from '../product-evidence'
import { comparisonIds, toggleComparison } from '../comparison'
import { campaignDestination, storeCampaigns } from '../campaigns'

const route = useRoute()
const router = useRouter()
const products = ref<ProductSummary[]>([])
const suggestions = ref<ProductSummary[]>([])
const interpreted = ref<InterpretedQuery | null>(null)
const query = ref(typeof route.query.q === 'string' ? route.query.q : '')
const submittedQuery = ref(query.value.trim())
const loading = ref(true)
const error = ref('')
const autocompleteItems = ref<ProductSummary[]>([])
const autocompleteKind = ref<'results' | 'suggestions'>('results')
const autocompleteOpen = ref(false)
const autocompleteLoading = ref(false)
const autocompleteError = ref(false)
const activeOption = ref(-1)
const homepage = ref<StorefrontHomepageModel<ProductSummary, PublicOffer> | null>(null)
const merchandisingLoading = ref(true)
const merchandisingFailed = ref(false)
const catalogHeading = ref<HTMLElement>()
const hasPublished = computed(() => !!homepage.value?.sections.length)
const hasPublishedCollection = computed(() => homepage.value?.sections.some(section => section.type === 'PRODUCT_COLLECTION') ?? false)
const showAll = computed(() => route.query.view === 'all')
const comparisonMessage = ref('')
const activeCampaign = ref(0)
const featuredCampaign = computed(() => storeCampaigns[activeCampaign.value]!)
function moveCampaign(direction: number) {
  activeCampaign.value = (activeCampaign.value + direction + storeCampaigns.length) % storeCampaigns.length
}
const showCatalogProducts = computed(() => !!submittedQuery.value || showAll.value || (!merchandisingLoading.value && !hasPublishedCollection.value))
const visibleProducts = computed(() => submittedQuery.value || showAll.value ? products.value : products.value.slice(0, 6))
const recognizedIntent = computed(() => {
  if (!interpreted.value) return []
  const colorLabels: Record<string, string> = { WHITE: 'White', BLACK: 'Black', CREAM: 'Cream', NAVY: 'Navy' }
  const categoryLabels: Record<string, string> = { RUNNING: 'Running', EVERYDAY: 'Everyday', TRAIL: 'Trail', COURT: 'Court', TRAINING: 'Training', SLIP_ON: 'Slip-on' }
  return [
    ...interpreted.value.colors.map(value => t(colorLabels[value] ?? value)),
    ...interpreted.value.categories.map(value => t(categoryLabels[value] ?? value)),
    ...(interpreted.value.maximumPrice ? [t('Up to {price}', { price: formatVnd(interpreted.value.maximumPrice) })] : []),
    ...(interpreted.value.skuLookup ? [t('Exact product code')] : []),
  ]
})
let loadVersion = 0
let autocompleteVersion = 0
let autocompleteTimer: ReturnType<typeof setTimeout> | undefined
// Trial UI parameters; endpoint latency and payload still need live certification.
const AUTOCOMPLETE_DELAY_MS = 250
const AUTOCOMPLETE_MIN_LENGTH = 2
const AUTOCOMPLETE_LIMIT = 6
let homepageLoad = Promise.resolve()
prepareCatalogScrollRestore()
const ticket = catalogScrollTicket()

async function loadProducts(value = query.value) {
  const version = ++loadVersion
  const submitted = value.trim()
  loading.value = true; error.value = ''; submittedQuery.value = submitted; interpreted.value = null
  try {
    if (submitted) {
      const result = await api.discovery(submitted)
      if (version === loadVersion) { products.value = result.results; suggestions.value = result.suggestions; interpreted.value = result.interpreted }
    } else {
      const result = await api.products()
      if (version === loadVersion) { products.value = result; suggestions.value = []; interpreted.value = null }
    }
  }
  catch (reason) { if (version === loadVersion) error.value = errorCopy(reason) }
  finally { if (version === loadVersion) loading.value = false }
}

async function loadHomepage() {
  merchandisingLoading.value = true; merchandisingFailed.value = false
  try { homepage.value = await api.storefrontHomepage() }
  catch { homepage.value = null; merchandisingFailed.value = true }
  finally { merchandisingLoading.value = false }
}

async function submitSearch() {
  closeAutocomplete()
  const value = query.value.trim()
  await router.replace({ path: '/', query: { ...route.query, q: value || undefined } })
}

async function clearSearch() {
  query.value = ''
  await submitSearch()
}

async function updateQuery(event: Event) {
  query.value = (event.target as HTMLInputElement).value
  scheduleAutocomplete()
  if (!query.value.trim() && route.query.q) {
    products.value = []; suggestions.value = []
    loading.value = true
    ++loadVersion
    await router.replace({ query: { ...route.query, q: undefined } })
  }
}

function closeAutocomplete() {
  ++autocompleteVersion
  clearTimeout(autocompleteTimer)
  autocompleteOpen.value = false
  autocompleteLoading.value = false
  autocompleteError.value = false
  autocompleteItems.value = []
  activeOption.value = -1
}

function scheduleAutocomplete() {
  closeAutocomplete()
  const value = query.value.trim()
  if (value.length < AUTOCOMPLETE_MIN_LENGTH) return
  autocompleteOpen.value = true
  const version = autocompleteVersion
  autocompleteTimer = setTimeout(async () => {
    autocompleteLoading.value = true
    try {
      const response = await api.discovery(value)
      if (version !== autocompleteVersion) return
      autocompleteKind.value = response.results.length ? 'results' : 'suggestions'
      autocompleteItems.value = (response.results.length ? response.results : response.suggestions).slice(0, AUTOCOMPLETE_LIMIT)
    } catch {
      if (version !== autocompleteVersion) return
      autocompleteError.value = true
    } finally {
      if (version === autocompleteVersion) autocompleteLoading.value = false
    }
  }, AUTOCOMPLETE_DELAY_MS)
}

function selectAutocomplete(product: ProductSummary) {
  closeAutocomplete()
  void router.push({ path: `/products/${product.id}`, query: { q: query.value.trim() } })
}

function autocompleteKeydown(event: KeyboardEvent) {
  if (!autocompleteOpen.value) return
  if (event.key === 'Escape') { event.preventDefault(); closeAutocomplete(); return }
  if (!autocompleteItems.value.length) return
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const count = autocompleteItems.value.length
    activeOption.value = activeOption.value < 0
      ? (event.key === 'ArrowDown' ? 0 : count - 1)
      : (activeOption.value + (event.key === 'ArrowDown' ? 1 : -1) + count) % count
  } else if (event.key === 'Enter' && activeOption.value >= 0) {
    event.preventDefault()
    selectAutocomplete(autocompleteItems.value[activeOption.value]!)
  }
}

onUnmounted(closeAutocomplete)

function productImage(product: ProductSummary) {
  return productListingImage(product.primaryImage || product.heroImage || PRODUCT_IMAGE_PLACEHOLDER)
}

function productImageAlt(product: ProductSummary) {
  return productAlt(product.name)
}

function productEvidence(product: ProductSummary) {
  const statement = productPositioning(product.evidence)
  if (statement) return t(statement)
  const highlights = evidenceHighlights(product.evidence)
  const labels = highlights.length ? highlights : product.fitSummary
    ? [evidenceValueLabel(product.fitSummary.fitTendency), evidenceValueLabel(product.fitSummary.widthProfile)]
    : []
  return labels.map(label => t(label)).join(' · ')
}

function productDestination(productId: string) {
  if (submittedQuery.value) return { path: `/products/${productId}`, query: { q: submittedQuery.value } }
  if (showAll.value) return { path: `/products/${productId}`, query: { view: 'all' } }
  return { path: `/products/${productId}` }
}

function compare(productId: string) {
  const result = toggleComparison(productId)
  comparisonMessage.value = result === 'limit' ? 'You can compare up to three products.' : ''
}

watch([() => route.query.q, () => route.query.view], async ([value], [previousQuery]) => {
  closeAutocomplete()
  prepareCatalogScrollRestore()
  const ticket = catalogScrollTicket()
  query.value = typeof route.query.q === 'string' ? route.query.q : ''
  await Promise.all([homepageLoad, value !== previousQuery ? loadProducts(query.value) : Promise.resolve()])
  await nextTick()
  completeCatalogScrollRestore(ticket)
})
onMounted(async () => {
  homepageLoad = loadHomepage()
  await Promise.all([homepageLoad, loadProducts()])
  await nextTick()
  completeCatalogScrollRestore(ticket)
})
</script>

<template>
  <StorefrontHomepage :homepage="homepage" :loading="merchandisingLoading" :failed="merchandisingFailed">
    <template #discovery><StorefrontIntentLinks /></template>
  </StorefrontHomepage>

  <section v-if="!route.query.q" class="store-campaigns" :class="`campaign-${featuredCampaign.art}`" :aria-label="t('Featured campaign')">
    <div class="campaign-featured">
      <RouterLink class="campaign-hero-link" :to="campaignDestination(featuredCampaign)" :aria-label="`${t('Explore')} ${t(featuredCampaign.name)}`">
        <img :src="featuredCampaign.image" :alt="t(featuredCampaign.name)" width="1672" height="968" :fetchpriority="activeCampaign === 0 ? 'high' : 'auto'" />
      </RouterLink>
      <div class="campaign-controls" :aria-label="t('Campaign controls')">
        <button class="campaign-arrow" type="button" :aria-label="t('Previous campaign')" @click="moveCampaign(-1)">←</button>
        <div class="campaign-indicators" :aria-label="t('Choose a campaign')">
          <button v-for="(campaign, index) in storeCampaigns" :key="campaign.id" type="button" :aria-label="t(campaign.name)" :aria-pressed="activeCampaign === index" @click="activeCampaign = index"></button>
        </div>
        <button class="campaign-arrow" type="button" :aria-label="t('Next campaign')" @click="moveCampaign(1)">→</button>
      </div>
    </div>

    <div class="campaign-collections">
      <div class="campaign-section-heading"><h2>{{ t('Explore Collections') }}</h2></div>
      <ul class="campaign-card-grid">
        <li v-for="campaign in storeCampaigns" :key="campaign.id">
          <RouterLink class="campaign-card" :to="campaignDestination(campaign)">
            <span class="campaign-card-index" aria-hidden="true">0{{ storeCampaigns.indexOf(campaign) + 1 }}</span>
            <span class="campaign-card-name">{{ t(campaign.name) }}<span aria-hidden="true"> ↗</span></span>
          </RouterLink>
        </li>
      </ul>
      <RouterLink class="campaign-shop-all" :to="{ path: '/', query: { view: 'all' }, hash: '#catalog-heading' }">{{ t('Shop all products') }} <span aria-hidden="true">→</span></RouterLink>
    </div>
  </section>

  <section v-if="!merchandisingLoading && !hasPublished" class="catalog-hero">
    <div><h1>{{ t('Find the shoe') }}<br /><em>{{ t('for your day.') }}</em></h1></div>
    <div><p>{{ t('Search by style, color, or use. Choose your size when you are ready.') }}</p><a class="storefront-section-cta" href="#catalog-heading">{{ t('Explore shoes') }}</a></div>
  </section>
  <StorefrontIntentLinks v-if="!merchandisingLoading && !hasPublished" />

  <section class="catalog-section" aria-labelledby="catalog-heading">
    <form class="store-search" role="search" @submit.prevent="submitSearch">
      <label for="product-search">{{ t('Search by name, color, use, or price') }}</label>
      <div>
        <div class="store-search-combobox">
          <input id="product-search" :value="query" type="search" maxlength="80" role="combobox" aria-autocomplete="list" :aria-controls="autocompleteOpen && autocompleteItems.length ? 'product-search-options' : undefined" :aria-expanded="autocompleteOpen && autocompleteItems.length > 0" :aria-activedescendant="activeOption >= 0 && autocompleteItems[activeOption] ? `product-search-option-${activeOption}` : undefined" :placeholder="t('White running shoes under 2 million')" autocomplete="off" @input="updateQuery" @keydown="autocompleteKeydown" @blur="closeAutocomplete" />
          <div v-if="autocompleteOpen" class="store-search-options">
            <p v-if="autocompleteLoading" role="status">{{ t('Searching products…') }}</p>
            <p v-else-if="autocompleteError" role="alert">{{ t('Suggestions unavailable. Keep typing or search.') }} <button type="button" @pointerdown.prevent @click="scheduleAutocomplete">{{ t('Try again') }}</button></p>
            <p v-else-if="!autocompleteItems.length" role="status">{{ t('No suggestions yet. Press Enter to search.') }}</p>
            <template v-else>
              <p class="store-search-options-heading">{{ t(autocompleteKind === 'results' ? 'Matching products' : 'Similar products') }}</p>
              <ul id="product-search-options" role="listbox" :aria-label="t('Search suggestions')"><li v-for="(product, index) in autocompleteItems" :id="`product-search-option-${index}`" :key="product.id" role="option" :aria-selected="activeOption === index" @pointerdown.prevent @click="selectAutocomplete(product)">{{ product.name }}</li></ul>
            </template>
          </div>
        </div>
        <button class="primary-button" type="submit" :disabled="loading">{{ t('Search') }}</button>
        <button v-if="query" class="text-button" type="button" @click="clearSearch">{{ t('Clear search') }}</button>
      </div>
      <span class="sr-only" aria-live="polite">{{ autocompleteOpen && !autocompleteLoading && !autocompleteError && autocompleteItems.length ? t('{count} search suggestions', { count: autocompleteItems.length }) : '' }}</span>
    </form>

    <div class="section-heading">
      <h2 id="catalog-heading" ref="catalogHeading" tabindex="-1">{{ submittedQuery ? t('Results for “{query}”', { query: submittedQuery }) : t('Shop the collection') }}</h2>
      <div v-if="!loading && !error" class="catalog-result-context" aria-live="polite"><p>{{ products.length }} {{ t(products.length === 1 ? 'product' : 'products') }}</p><p v-if="recognizedIntent.length" class="recognized-intent">{{ t('Matched: {criteria}', { criteria: recognizedIntent.join(' · ') }) }}</p></div>
    </div>
    <template v-if="showCatalogProducts">
      <div v-if="loading" class="product-grid" role="status" aria-live="polite" :aria-label="t('Loading…')"><div v-for="item in 4" :key="item" class="product-card skeleton-card"><span></span></div></div>
      <div v-else-if="error" class="inline-state" role="alert"><h3>{{ t('Catalog unavailable') }}</h3><p>{{ messageLabel(error) }}</p><button class="text-button" type="button" @click="loadProducts()">{{ t('Try again') }}</button></div>
      <div v-else-if="submittedQuery && !products.length" class="discovery-recovery">
        <div class="inline-state" aria-live="polite"><h3>{{ t('No exact matches') }}</h3><p>{{ t('Try a broader product name, color, use, or price.') }}</p><div class="discovery-recovery-actions"><button class="text-button" type="button" @click="clearSearch">{{ t('Clear search') }}</button><RouterLink class="text-button" :to="{ path: '/', query: { view: 'all' }, hash: '#catalog-heading' }">{{ t('View all products') }}</RouterLink></div></div>
        <template v-if="suggestions.length"><div class="section-heading suggestion-heading"><h3>{{ t('You might like') }}</h3><p>{{ suggestions.length }} {{ t(suggestions.length === 1 ? 'product' : 'products') }}</p></div><ul class="product-grid suggestion-grid"><li v-for="product in suggestions" :key="product.id"><RouterLink class="product-card" :to="productDestination(product.id)" :aria-label="`${t('View product')}: ${product.name}`"><div class="product-image"><img :src="productImage(product)" :alt="productImageAlt(product)" width="640" height="480" loading="lazy" /></div><div class="product-card-copy"><h3>{{ product.name }}</h3><div><strong>{{ t('From') }} {{ formatVnd(product.fromAmount) }}</strong><span>{{ product.availableVariantCount }} {{ t('available sizes') }}</span></div><p class="product-card-taxonomy">{{ product.category ?? product.collection ?? t('Available by size') }}</p><p v-if="productEvidence(product)" class="product-card-evidence">{{ productEvidence(product) }}</p><ProductPresentationSummary :presentation="product.presentation" compact /></div></RouterLink><button class="comparison-product-button" type="button" :aria-pressed="comparisonIds.includes(product.id)" @click="compare(product.id)">{{ t(comparisonIds.includes(product.id) ? 'Remove from comparison' : 'Compare') }}</button></li></ul></template>
        <StorefrontIntentLinks v-else />
      </div>
      <div v-else-if="!products.length" class="inline-state"><h3>{{ t('No shoes available yet') }}</h3></div>
      <ul v-else class="product-grid">
        <li v-for="(product, index) in visibleProducts" :key="product.id" :class="{ featured: index === 0 && !submittedQuery && !hasPublished && visibleProducts.length >= 3 }">
          <RouterLink class="product-card" :to="productDestination(product.id)" :aria-label="`${t('View product')}: ${product.name}`">
            <div class="product-image"><img :src="productImage(product)" :alt="productImageAlt(product)" width="640" height="480" loading="lazy" /></div>
            <div class="product-card-copy">
              <h3>{{ product.name }}</h3><div><strong>{{ t('From') }} {{ formatVnd(product.fromAmount) }}</strong><span>{{ product.availableVariantCount }} {{ t('available sizes') }}</span></div><p class="product-card-taxonomy">{{ product.category ?? product.collection ?? t('Available by size') }}</p><p v-if="productEvidence(product)" class="product-card-evidence">{{ productEvidence(product) }}</p>
              <ProductPresentationSummary :presentation="product.presentation" compact />
            </div>
          </RouterLink>
          <button class="comparison-product-button" type="button" :aria-pressed="comparisonIds.includes(product.id)" @click="compare(product.id)">{{ t(comparisonIds.includes(product.id) ? 'Remove from comparison' : 'Compare') }}</button>
        </li>
      </ul>
      <p v-if="comparisonMessage" class="comparison-limit-message" role="status">{{ t(comparisonMessage) }}</p>
    </template>
    <div v-if="!submittedQuery && !showAll && !merchandisingLoading && (hasPublishedCollection || products.length > 6)" class="catalog-preview-actions">
      <RouterLink class="primary-button" :to="{ path: '/', query: { ...route.query, view: 'all' } }">{{ t('View all products') }}</RouterLink>
    </div>
  </section>
</template>
