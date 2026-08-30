<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api, type ProductSummary } from '../api'
import { errorCopy } from '../format'

const products = ref<ProductSummary[]>([])
const loading = ref(true)
const error = ref('')

async function loadProducts() {
  loading.value = true
  error.value = ''
  try {
    products.value = await api.products()
  } catch (reason) {
    error.value = errorCopy(reason)
  } finally {
    loading.value = false
  }
}

onMounted(loadProducts)
</script>

<template>
  <section class="catalog-hero">
    <h1>Choose the shoe.<br /><em>Then the size.</em></h1>
    <p>Only styles you can shop appear here. Availability is current; choose a size to confirm today’s price.</p>
  </section>

  <section class="catalog-section" aria-labelledby="catalog-heading">
    <div class="section-heading">
      <h2 id="catalog-heading">Shoes available now</h2>
      <p v-if="!loading && !error">{{ products.length }} {{ products.length === 1 ? 'product' : 'products' }}</p>
    </div>

    <div v-if="loading" class="product-list" role="status" aria-live="polite" aria-label="Loading products">
      <div v-for="item in 3" :key="item" class="product-row skeleton-row"><span></span><span></span></div>
    </div>

    <div v-else-if="error" class="inline-state" role="alert">
      <h3>Catalog unavailable</h3>
      <p>{{ error }}</p>
      <button class="text-button" type="button" @click="loadProducts">Try again</button>
    </div>

    <div v-else-if="products.length === 0" class="inline-state">
      <h3>No shoes available yet</h3>
      <p>Check back after more stocked styles are ready to shop.</p>
    </div>

    <ul v-else class="product-list">
      <li v-for="product in products" :key="product.id">
        <RouterLink class="product-row" :to="`/products/${product.id}`">
          <span class="product-name">{{ product.name }}</span>
          <span class="product-facts">
            {{ product.availableVariantCount }} of {{ product.variantCount }} sizes available
          </span>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>
