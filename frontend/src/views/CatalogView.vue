<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
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

const route = useRoute()
const router = useRouter()
const products = ref<ProductSummary[]>([])
const suggestions = ref<ProductSummary[]>([])
const interpreted = ref<InterpretedQuery | null>(null)
const query = ref(typeof route.query.q === 'string' ? route.query.q : '')
const submittedQuery = ref(query.value.trim())
const loading = ref(true)
const error = ref('')
const homepage = ref<StorefrontHomepageModel<ProductSummary, PublicOffer> | null>(null)
const merchandisingLoading = ref(true)
const merchandisingFailed = ref(false)
const catalogHeading = ref<HTMLElement>()
const hasPublished = computed(() => !!homepage.value?.sections.length)
const hasPublishedCollection = computed(() => homepage.value?.sections.some(section => section.type === 'PRODUCT_COLLECTION') ?? false)
const showAll = computed(() => route.query.view === 'all')
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
  const value = query.value.trim()
  await router.replace({ path: '/', query: { ...route.query, q: value || undefined } })
}

async function clearSearch() {
  query.value = ''
  await submitSearch()
}

async function updateQuery(event: Event) {
  query.value = (event.target as HTMLInputElement).value
  if (!query.value.trim() && route.query.q) {
    products.value = []; suggestions.value = []
    loading.value = true
    ++loadVersion
    await router.replace({ query: { ...route.query, q: undefined } })
  }
}

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

watch([() => route.query.q, () => route.query.view], async ([value, view], [previousQuery, previousView]) => {
  prepareCatalogScrollRestore()
  const ticket = catalogScrollTicket()
  query.value = typeof route.query.q === 'string' ? route.query.q : ''
  await Promise.all([homepageLoad, value !== previousQuery ? loadProducts(query.value) : Promise.resolve()])
  await nextTick()
  if (view === 'all' && previousView !== 'all') catalogHeading.value?.focus()
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

  <section v-if="!merchandisingLoading && !hasPublished" class="catalog-hero">
    <div><h1>{{ t('Find the shoe') }}<br /><em>{{ t('for your day.') }}</em></h1></div>
    <div><p>{{ t('Search by style, color, or use. Choose your size when you are ready.') }}</p><a class="storefront-section-cta" href="#catalog-heading">{{ t('Explore shoes') }}</a></div>
  </section>
  <StorefrontIntentLinks v-if="!merchandisingLoading && !hasPublished" />

  <section class="catalog-section" aria-labelledby="catalog-heading">
    <form class="store-search" role="search" @submit.prevent="submitSearch">
      <label for="product-search">{{ t('Search by name, color, use, or price') }}</label>
      <div>
        <input id="product-search" :value="query" type="search" maxlength="80" :placeholder="t('White running shoes under 2 million')" autocomplete="off" @input="updateQuery" />
        <button class="primary-button" type="submit" :disabled="loading">{{ t('Search') }}</button>
        <button v-if="query" class="text-button" type="button" @click="clearSearch">{{ t('Clear search') }}</button>
      </div>
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
        <template v-if="suggestions.length"><div class="section-heading suggestion-heading"><h3>{{ t('You might like') }}</h3><p>{{ suggestions.length }} {{ t(suggestions.length === 1 ? 'product' : 'products') }}</p></div><ul class="product-grid suggestion-grid"><li v-for="product in suggestions" :key="product.id"><RouterLink class="product-card" :to="productDestination(product.id)" :aria-label="`${t('View product')}: ${product.name}`"><div class="product-image"><img :src="productImage(product)" :alt="productImageAlt(product)" width="640" height="480" loading="lazy" /></div><div class="product-card-copy"><h3>{{ product.name }}</h3><div><strong>{{ t('From') }} {{ formatVnd(product.fromAmount) }}</strong><span>{{ product.availableVariantCount }} {{ t('available sizes') }}</span></div><p class="product-card-taxonomy">{{ product.category ?? product.collection ?? t('Available by size') }}</p><p v-if="productEvidence(product)" class="product-card-evidence">{{ productEvidence(product) }}</p><ProductPresentationSummary :presentation="product.presentation" compact /></div></RouterLink></li></ul></template>
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
        </li>
      </ul>
    </template>
    <div v-if="!submittedQuery && !showAll && !merchandisingLoading && (hasPublishedCollection || products.length > 6)" class="catalog-preview-actions">
      <RouterLink class="primary-button" :to="{ path: '/', query: { ...route.query, view: 'all' }, hash: '#catalog-heading' }">{{ t('View all products') }}</RouterLink>
    </div>
  </section>
</template>
