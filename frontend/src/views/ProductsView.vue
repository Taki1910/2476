<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { api, type ManagerProductListItem } from '../api'
import { errorCopy, formatVnd } from '../format'
import { statusLabel, t } from '../i18n'

const products = ref<ManagerProductListItem[]>([])
const router = useRouter()
const query = ref('')
const loading = ref(true)
const creating = ref(false)
const showCreate = ref(false)
const error = ref('')
const draft = reactive({ name: '', category: '', collection: '', heroImage: '', primaryImage: '' })

async function load() {
  loading.value = true
  error.value = ''
  try { products.value = await api.managerCatalog(query.value.trim()) }
  catch (reason) { error.value = errorCopy(reason) }
  finally { loading.value = false }
}

function price(product: ManagerProductListItem) {
  if (product.minCurrentPrice == null || product.maxCurrentPrice == null) return t('Incomplete')
  return product.minCurrentPrice === product.maxCurrentPrice
    ? formatVnd(product.minCurrentPrice)
    : `${formatVnd(product.minCurrentPrice)} – ${formatVnd(product.maxCurrentPrice)}`
}

async function createProduct() {
  if (!draft.name.trim()) { error.value = t('Enter a product name.'); return }
  creating.value = true; error.value = ''
  try {
    const created = await api.createManagerProduct({
      name: draft.name.trim(), category: draft.category.trim() || null,
      collection: draft.collection.trim() || null, heroImage: draft.heroImage.trim() || null,
      primaryImage: draft.primaryImage.trim() || null,
    })
    await router.push(`/operations/products/${created.id}`)
  } catch (reason) { error.value = errorCopy(reason) }
  finally { creating.value = false }
}

onMounted(load)
</script>

<template>
  <section class="operations-page product-management catalog-index">
    <header class="catalog-management-heading">
      <div><h1>{{ t('Products') }}</h1><p>{{ t('Draft and published catalog facts, pricing completeness, and authorized inventory evidence.') }}</p></div>
      <div class="catalog-heading-actions"><strong>{{ products.length }} {{ t('products') }}</strong><button class="primary-button" type="button" :disabled="creating" @click="showCreate = !showCreate">{{ t(showCreate ? 'Close' : 'Create product') }}</button></div>
    </header>

    <form v-if="showCreate" class="catalog-identity-form catalog-create-form" @submit.prevent="createProduct">
      <div class="catalog-section-heading"><div><h2>{{ t('Create product') }}</h2><p>{{ t('Start with identity facts, then continue variants, pricing, inventory, content, and publication on the product workspace.') }}</p></div></div>
      <div class="catalog-form-grid">
        <label><span>{{ t('Product name') }}</span><input v-model.trim="draft.name" required maxlength="160" :disabled="creating"></label>
        <label><span>{{ t('Category') }}</span><input v-model.trim="draft.category" maxlength="32" :disabled="creating"></label>
        <label><span>{{ t('Collection') }}</span><input v-model.trim="draft.collection" maxlength="64" :disabled="creating"></label>
        <label><span>{{ t('Hero image reference') }}</span><input v-model.trim="draft.heroImage" maxlength="255" :disabled="creating"></label>
        <label><span>{{ t('Primary image reference') }}</span><input v-model.trim="draft.primaryImage" maxlength="255" :disabled="creating"></label>
      </div>
      <button class="primary-button" type="submit" :disabled="creating">{{ t(creating ? 'Creating…' : 'Create and continue') }}</button>
    </form>

    <form class="catalog-management-search" role="search" @submit.prevent="load">
      <label for="catalog-search">{{ t('Search name, SKU, color, or category') }}</label>
      <div><input id="catalog-search" v-model.trim="query" type="search" maxlength="80" autocomplete="off"><button class="primary-button" type="submit" :disabled="loading">{{ t('Search') }}</button></div>
    </form>

    <p v-if="loading" class="inline-state" role="status">{{ t('Loading…') }}</p>
    <div v-else-if="error" class="inline-state" role="alert"><p>{{ error }}</p><button class="text-button" type="button" @click="load">{{ t('Try again') }}</button></div>
    <div v-else-if="!products.length" class="inline-state"><h2>{{ t('No products found') }}</h2><p>{{ t('Try another name, SKU, color, or category.') }}</p></div>
    <div v-else class="catalog-table-wrap" role="region" tabindex="0" :aria-label="t('Managed products')">
      <table class="catalog-management-table">
        <thead><tr><th>{{ t('Product') }}</th><th>{{ t('Publication') }}</th><th>{{ t('Variants') }}</th><th>{{ t('Current price') }}</th><th>{{ t('Availability') }}</th><th><span class="sr-only">{{ t('Open') }}</span></th></tr></thead>
        <tbody>
          <tr v-for="product in products" :key="product.id">
            <th scope="row"><RouterLink :to="`/operations/products/${product.id}`">{{ product.name }}</RouterLink><small>{{ product.category ?? t('Uncategorized') }}<template v-if="product.collection"> · {{ product.collection }}</template></small><span v-if="product.hasDuplicateOptions" class="warning-copy">{{ t('Duplicate color and size data') }}</span></th>
            <td><span class="state-label" :data-state="product.publication">{{ t(statusLabel(product.publication)) }}</span><small>{{ product.publishedVariantCount }} {{ t('published') }} · {{ product.draftVariantCount }} {{ t('draft') }} · {{ product.retiredVariantCount }} {{ t('retired') }}</small></td>
            <td class="number">{{ product.variantCount }}</td>
            <td class="number"><strong>{{ price(product) }}</strong><small v-if="!product.pricingComplete">{{ t('Price missing') }}</small></td>
            <td><template v-if="product.inventory"><strong v-if="product.inventory.state === 'AVAILABLE'">{{ product.inventory.available }} {{ t('available') }}</strong><strong v-else-if="product.inventory.state === 'SOLD_OUT'">{{ t('Sold out') }}</strong><strong v-else>{{ t('No inventory evidence') }}</strong><small v-if="product.inventory.state !== 'NO_INVENTORY_EVIDENCE'">{{ product.inventory.onHand }} {{ t('on hand') }} · {{ product.inventory.reserved }} {{ t('reserved') }}</small></template><span v-else>{{ t('Not authorized') }}</span></td>
            <td><RouterLink class="text-button" :to="`/operations/products/${product.id}`">{{ t('Open') }}</RouterLink></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
