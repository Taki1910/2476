<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api, type ProductSummary, type PublicOffer } from '../api'
import StorefrontHomepage from '../components/StorefrontHomepage.vue'
import ProductPresentationSummary from '../components/ProductPresentationSummary.vue'
import { errorCopy, formatVnd } from '../format'
import { messageLabel, t } from '../i18n'
import type { StorefrontHomepage as StorefrontHomepageModel } from '../merchandising'
import { PRODUCT_IMAGE_PLACEHOLDER, productAlt } from '../product-media'

const route = useRoute()
const router = useRouter()
const products = ref<ProductSummary[]>([])
const suggestions = ref<ProductSummary[]>([])
const query = ref(typeof route.query.q === 'string' ? route.query.q : '')
const submittedQuery = ref(query.value.trim())
const loading = ref(true)
const error = ref('')
const homepage = ref<StorefrontHomepageModel<ProductSummary, PublicOffer> | null>(null)
const merchandisingLoading = ref(true)
const merchandisingFailed = ref(false)
const hasPublished = computed(() => !!homepage.value?.sections.length)
let loadVersion = 0

async function loadProducts(value = query.value) {
  const version = ++loadVersion
  const submitted = value.trim()
  loading.value = true; error.value = ''
  try {
    if (submitted) {
      const result = await api.discovery(submitted)
      if (version === loadVersion) { products.value = result.results; suggestions.value = result.suggestions }
    } else {
      const result = await api.products()
      if (version === loadVersion) { products.value = result; suggestions.value = [] }
    }
    if (version === loadVersion) submittedQuery.value = submitted
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
  return product.primaryImage || product.heroImage || PRODUCT_IMAGE_PLACEHOLDER
}

function productImageAlt(product: ProductSummary) {
  return productAlt(product.name)
}

watch(() => route.query.q, value => {
  const next = typeof value === 'string' ? value : ''
  if (next !== query.value) query.value = next
  loadProducts(next)
})
onMounted(() => { void loadHomepage(); void loadProducts() })
</script>

<template>
  <StorefrontHomepage :homepage="homepage" :loading="merchandisingLoading" :failed="merchandisingFailed" />

  <section v-if="!merchandisingLoading && !hasPublished" class="catalog-hero">
    <div><h1>{{ t('Choose the shoe.') }}<br /><em>{{ t('Then the size.') }}</em></h1></div>
    <p>{{ t('Explore the full collection. Search by name, color, category, use, or price, then choose the right size.') }}</p>
  </section>

  <section class="catalog-section" aria-labelledby="catalog-heading">
    <form class="store-search" role="search" @submit.prevent="submitSearch">
      <label for="product-search">{{ t('Describe the shoe you’re looking for') }}</label>
      <div>
        <input id="product-search" :value="query" type="search" maxlength="80" :placeholder="t('White running shoes under 2 million')" autocomplete="off" @input="updateQuery" />
        <button class="primary-button" type="submit" :disabled="loading">{{ t('Search') }}</button>
        <button v-if="query" class="text-button" type="button" @click="clearSearch">{{ t('Clear search') }}</button>
      </div>
    </form>

    <div class="section-heading">
      <h2 id="catalog-heading">{{ submittedQuery ? t('Search results') : t('Shop the collection') }}</h2>
      <p v-if="!loading && !error">{{ products.length }} {{ t(products.length === 1 ? 'product' : 'products') }}</p>
    </div>
    <div v-if="loading" class="product-grid" role="status" aria-live="polite" :aria-label="t('Loading…')"><div v-for="item in 4" :key="item" class="product-card skeleton-card"><span></span></div></div>
    <div v-else-if="error" class="inline-state" role="alert"><h3>{{ t('Catalog unavailable') }}</h3><p>{{ messageLabel(error) }}</p><button class="text-button" type="button" @click="loadProducts()">{{ t('Try again') }}</button></div>
    <div v-else-if="submittedQuery && !products.length" class="discovery-recovery">
      <div class="inline-state" aria-live="polite"><h3>{{ t('No exact matches for “{query}”.', { query: submittedQuery }) }}</h3><p>{{ t('Try another search or browse the suggestions below.') }}</p><button class="text-button" type="button" @click="clearSearch">{{ t('Clear search') }}</button></div>
      <template v-if="suggestions.length"><div class="section-heading suggestion-heading"><h3>{{ t('You might like') }}</h3><p>{{ suggestions.length }} {{ t(suggestions.length === 1 ? 'product' : 'products') }}</p></div><ul class="product-grid suggestion-grid"><li v-for="(product, index) in suggestions" :key="product.id"><RouterLink class="product-card" :to="`/products/${product.id}`" :aria-label="`${t('View product')}: ${product.name}`"><div class="product-image"><img :src="productImage(product)" :alt="productImageAlt(product)" width="1456" height="1092" :loading="index < 2 ? 'eager' : 'lazy'" /></div><div class="product-card-copy"><h3>{{ product.name }}</h3><p class="product-card-taxonomy">{{ product.category ?? product.collection ?? t('Available by size') }}</p><ProductPresentationSummary :presentation="product.presentation" compact /><div><strong>{{ t('From') }} {{ formatVnd(product.fromAmount) }}</strong><span>{{ product.availableVariantCount }} {{ t('available sizes') }}</span></div></div></RouterLink></li></ul></template>
    </div>
    <div v-else-if="!products.length" class="inline-state"><h3>{{ t('No shoes available yet') }}</h3></div>
    <ul v-else class="product-grid">
      <li v-for="(product, index) in products" :key="product.id" :class="{ featured: index === 0 && !submittedQuery && !hasPublished && products.length >= 3 }">
        <RouterLink class="product-card" :to="`/products/${product.id}`" :aria-label="`${t('View product')}: ${product.name}`">
          <div class="product-image"><img :src="productImage(product)" :alt="productImageAlt(product)" width="1456" height="1092" :loading="index < 2 ? 'eager' : 'lazy'" /></div>
          <div class="product-card-copy">
            <h3>{{ product.name }}</h3><p class="product-card-taxonomy">{{ product.category ?? product.collection ?? t('Available by size') }}</p>
            <ProductPresentationSummary :presentation="product.presentation" compact />
            <div><strong>{{ t('From') }} {{ formatVnd(product.fromAmount) }}</strong><span>{{ product.availableVariantCount }} {{ t('available sizes') }}</span></div>
          </div>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>
