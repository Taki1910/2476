<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api, type ProductSummary } from '../api'
import { errorCopy, formatVnd } from '../format'
import { locale, messageLabel, t } from '../i18n'
import { productAlt, productMedia } from '../product-media'

const route = useRoute()
const router = useRouter()
const products = ref<ProductSummary[]>([])
const query = ref(typeof route.query.q === 'string' ? route.query.q : '')
const loading = ref(true)
const error = ref('')
let loadVersion = 0

async function loadProducts(value = query.value) {
  const version = ++loadVersion
  loading.value = true; error.value = ''
  try { const result = await api.products(value.trim()); if (version === loadVersion) products.value = result }
  catch (reason) { if (version === loadVersion) error.value = errorCopy(reason) }
  finally { if (version === loadVersion) loading.value = false }
}

async function submitSearch() {
  const value = query.value.trim()
  await router.replace(value ? { path: '/', query: { q: value } } : { path: '/' })
}

async function clearSearch() {
  query.value = ''
  await submitSearch()
}

function productImage(product: ProductSummary) {
  return product.primaryImage || productMedia(product.name)?.src || '/products/court-classic.png'
}

function productImageAlt(product: ProductSummary) {
  return productAlt(product.name, locale.value) || product.name
}

watch(() => route.query.q, value => {
  const next = typeof value === 'string' ? value : ''
  if (next !== query.value) query.value = next
  loadProducts(next)
})
onMounted(() => loadProducts())
</script>

<template>
  <section class="catalog-hero">
    <div><h1>{{ t('Choose the shoe.') }}<br /><em>{{ t('Then the size.') }}</em></h1></div>
    <p>{{ t('Explore the full collection. Search by product name, SKU, color, or category, then choose the right size.') }}</p>
  </section>

  <section class="catalog-section" aria-labelledby="catalog-heading">
    <form class="store-search" role="search" @submit.prevent="submitSearch">
      <label for="product-search">{{ t('Search the store') }}</label>
      <div>
        <input id="product-search" v-model="query" type="search" :placeholder="t('Search products, SKU, color…')" autocomplete="off" />
        <button class="primary-button" type="submit" :disabled="loading">{{ t('Search') }}</button>
        <button v-if="query" class="text-button" type="button" @click="clearSearch">{{ t('Clear search') }}</button>
      </div>
    </form>

    <div class="section-heading">
      <h2 id="catalog-heading">{{ query ? t('Search results') : t('Shop the collection') }}</h2>
      <p v-if="!loading && !error">{{ products.length }} {{ t(products.length === 1 ? 'product' : 'products') }}</p>
    </div>
    <div v-if="loading" class="product-grid" role="status" aria-live="polite" :aria-label="t('Loading…')"><div v-for="item in 4" :key="item" class="product-card skeleton-card"><span></span></div></div>
    <div v-else-if="error" class="inline-state" role="alert"><h3>{{ t('Catalog unavailable') }}</h3><p>{{ messageLabel(error) }}</p><button class="text-button" type="button" @click="loadProducts()">{{ t('Try again') }}</button></div>
    <div v-else-if="!products.length" class="inline-state"><h3>{{ t(query ? 'No products match that search.' : 'No shoes available yet') }}</h3><p v-if="query">{{ t('Try a product name, SKU, color, or category.') }}</p><button v-if="query" class="text-button" type="button" @click="clearSearch">{{ t('Clear search') }}</button></div>
    <ul v-else class="product-grid">
      <li v-for="(product, index) in products" :key="product.id" :class="{ featured: index === 0 && !query }">
        <RouterLink class="product-card" :to="`/products/${product.id}`" :aria-label="`${t('View product')}: ${product.name}`">
          <div class="product-image"><img :src="productImage(product)" :alt="productImageAlt(product)" width="1456" height="1092" :loading="index < 2 ? 'eager' : 'lazy'" /></div>
          <div class="product-card-copy">
            <h3>{{ product.name }}</h3><p>{{ product.category ?? product.collection ?? t('Available by size') }}</p>
            <div><strong>{{ t('From') }} {{ formatVnd(product.fromAmount) }}</strong><span>{{ product.availableVariantCount }} {{ t('available sizes') }}</span></div>
          </div>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>
