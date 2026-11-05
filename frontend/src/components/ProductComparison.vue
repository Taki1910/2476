<script setup lang="ts">
import { computed, nextTick, onMounted, onServerPrefetch, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { api, type ProductDetail } from '../api'
import { formatVnd } from '../format'
import { t } from '../i18n'
import { clearComparison, comparisonIds, hasCanonicalDifference, isCurrentComparisonRequest, selectMobilePair, toggleComparison } from '../comparison'
import { evidenceRows, evidenceValueLabel } from '../product-evidence'
import { PRODUCT_IMAGE_PLACEHOLDER, productAlt, productListingImage } from '../product-media'

type Loaded = { product?: ProductDetail; error?: true; loading: boolean }
type Value = { canonical: string | undefined; display: string; image?: string; alt?: string }
type Row = { key: string; label: string; values: (Value | undefined)[] }

const open = ref(false)
let openedBefore = false
const opener = ref<HTMLButtonElement>()
const heading = ref<HTMLElement>()
const onlyDifferences = ref(false)
const entries = ref(new Map<string, Loaded>())
const epochs = new Map<string, number>()
const mobilePair = ref<string[]>([])

async function load(id: string, retry = false) {
  const existing = entries.value.get(id)
  if (!retry && (existing?.loading || existing?.product)) return
  const epoch = (epochs.get(id) ?? 0) + 1
  epochs.set(id, epoch)
  entries.value.set(id, { loading: true })
  try {
    const product = await api.product(id)
    if (isCurrentComparisonRequest(id, epoch, epochs.get(id) ?? 0)) entries.value.set(id, { loading: true, product })
  } catch {
    if (isCurrentComparisonRequest(id, epoch, epochs.get(id) ?? 0)) entries.value.set(id, { loading: true, error: true })
  } finally {
    if (isCurrentComparisonRequest(id, epoch, epochs.get(id) ?? 0)) {
      const current = entries.value.get(id)
      if (current) entries.value.set(id, { ...current, loading: false })
    }
  }
}

function syncIds(ids: string[], previous: string[] = []) {
  for (const id of previous) if (!ids.includes(id)) {
    epochs.set(id, (epochs.get(id) ?? 0) + 1)
    entries.value.delete(id)
  }
  for (const id of ids) void load(id)
  mobilePair.value = mobilePair.value.filter(id => ids.includes(id)).slice(0, 2)
  for (const id of ids) if (!mobilePair.value.includes(id) && mobilePair.value.length < 2) mobilePair.value.push(id)
}

watch(() => [...comparisonIds.value], (ids, previous) => syncIds(ids, previous), { flush: 'sync' })
onMounted(() => syncIds([...comparisonIds.value]))
onServerPrefetch(() => Promise.all(comparisonIds.value.map(id => load(id))))

function rowValues(product: ProductDetail): Record<string, Value> {
  const colors = [...new Set(product.options.map(option => option.color))].sort()
  const sizes = [...new Set(product.options.flatMap(option => option.sizes.map(size => size.size)))].sort()
  const available = product.options.flatMap(option => option.sizes).filter(size => size.availability === 'AVAILABLE').length
  const total = product.options.reduce((count, option) => count + option.sizes.length, 0)
  const evidence = evidenceRows(product.evidence)
  const fit = product.fitSummary
  const price = product.pricing
  const image = product.media[0]?.url ?? product.heroImage ?? product.primaryImage
  return {
    name: { canonical: product.name, display: product.name },
    image: { canonical: image ?? undefined, display: image ? productAlt(product.name) : t('No information yet.'), image: image ? productListingImage(image) : PRODUCT_IMAGE_PLACEHOLDER, alt: productAlt(product.media[0]?.alt || product.name) },
    price: { canonical: `${price.state}|${price.minimumAmount}|${price.maximumAmount}|${price.currency}`, display: price.state === 'RANGE' ? `${formatVnd(price.minimumAmount)} – ${formatVnd(price.maximumAmount)}` : formatVnd(price.minimumAmount) },
    colors: { canonical: colors.length ? JSON.stringify(colors) : undefined, display: colors.length ? colors.map(value => t(value)).join(', ') : t('No information yet.') },
    sizes: { canonical: sizes.length ? JSON.stringify(sizes) : undefined, display: sizes.length ? sizes.join(', ') : t('No information yet.') },
    availability: { canonical: total ? `${available}/${total}` : undefined, display: total ? t('Available {available} of {total} size/color options', { available, total }) : t('No information yet.') },
    evidence: { canonical: evidence.length ? JSON.stringify(evidence.map(([, value]) => value)) : undefined, display: evidence.length ? evidence.map(([, value]) => t(value)).join(' · ') : t('No information yet.') },
    fit: { canonical: fit ? JSON.stringify(fit) : undefined, display: fit ? `${t(evidenceValueLabel(fit.fitTendency))} · ${t(evidenceValueLabel(fit.widthProfile))}` : t('No information yet.') },
  }
}

const rowDefinitions = [
  ['name', 'Name'], ['image', 'Product image'], ['price', 'Price'], ['colors', 'Colors'],
  ['sizes', 'Sizes'], ['availability', 'Public availability'], ['evidence', 'Product evidence'], ['fit', 'Fit summary'],
] as const
function rowsFor(ids: string[]): Row[] {
  const values = ids.map(id => {
    const product = entries.value.get(id)?.product
    return product ? rowValues(product) : undefined
  })
  return rowDefinitions.flatMap(([key, label]) => {
    const cells = values.map(item => item?.[key])
    if (onlyDifferences.value && key !== 'name' && key !== 'image' && !hasCanonicalDifference(cells.map(cell => cell?.canonical))) return []
    return [{ key, label: t(label), values: cells }]
  })
}
const desktopRows = computed(() => rowsFor(comparisonIds.value))
const mobileRows = computed(() => rowsFor(mobilePair.value))

async function show() {
  open.value = !open.value
  if (open.value) {
    if (openedBefore) for (const id of comparisonIds.value) void load(id, true)
    openedBefore = true
    await nextTick(); heading.value?.focus()
  }
  else opener.value?.focus()
}
function close() { open.value = false; nextTick(() => opener.value?.focus()) }
function productName(id: string) { return entries.value.get(id)?.product?.name ?? id }
function changeMobilePair(slot: number, event: Event) {
  mobilePair.value = selectMobilePair(comparisonIds.value, mobilePair.value, slot, (event.target as HTMLSelectElement).value)
}
</script>

<template>
  <div class="product-comparison" @keydown.esc="close">
    <button ref="opener" class="comparison-toggle" type="button" :aria-expanded="open" aria-controls="product-comparison-panel" @click="show">
      {{ t(open ? 'Close comparison' : 'Open comparison ({count})', { count: comparisonIds.length }) }}
    </button>
    <section v-show="open" id="product-comparison-panel" class="comparison-panel" aria-labelledby="comparison-heading">
      <div class="comparison-heading">
        <div><h2 id="comparison-heading" ref="heading" tabindex="-1">{{ t('Compare products') }}</h2><p>{{ t('Your comparison is kept for this visit and clears when you reload the page.') }}</p></div>
        <div class="comparison-controls">
          <label><input v-model="onlyDifferences" type="checkbox" /> {{ t('Only show differences') }}</label>
          <button class="text-button" type="button" @click="close">{{ t('Close comparison') }}</button>
          <button class="text-button" type="button" @click="clearComparison">{{ t('Clear comparison') }}</button>
        </div>
      </div>
      <p v-if="comparisonIds.length < 2" class="comparison-hint" role="status">{{ t('Choose at least two products to compare.') }}</p>
      <template v-else>
        <div v-if="comparisonIds.length === 3" class="comparison-mobile-picker">
          <h3>{{ t('Choose two products') }}</h3>
          <label>{{ t('First product') }}<select :value="mobilePair[0]" :aria-label="t('First product')" @change="changeMobilePair(0, $event)"><option v-for="id in comparisonIds" :key="id" :value="id">{{ productName(id) }}</option></select></label>
          <label>{{ t('Second product') }}<select :value="mobilePair[1]" :aria-label="t('Second product')" @change="changeMobilePair(1, $event)"><option v-for="id in comparisonIds" :key="id" :value="id">{{ productName(id) }}</option></select></label>
        </div>
        <div class="comparison-table-wrap comparison-desktop">
          <table class="comparison-table"><thead><tr><th scope="col">{{ t('Feature') }}</th><th v-for="id in comparisonIds" :key="id" scope="col"><template v-if="entries.get(id)?.loading">{{ t('Loading…') }}</template><template v-else-if="entries.get(id)?.error"><span role="alert">{{ t('Could not load this product.') }}</span><button class="text-button" type="button" @click="load(id, true)">{{ t('Retry') }}</button></template><template v-else-if="entries.get(id)?.product"><RouterLink :to="`/products/${id}`">{{ entries.get(id)?.product?.name }}<span class="sr-only"> · {{ t('View product') }}</span></RouterLink><RouterLink class="text-button" :to="`/products/${id}`">{{ t('View product') }}</RouterLink></template><button class="text-button" type="button" @click="toggleComparison(id)">{{ t('Remove from comparison') }}</button></th></tr></thead>
            <tbody><tr v-for="row in desktopRows" :key="row.key"><th scope="row">{{ row.label }}</th><td v-for="(value, index) in row.values" :key="comparisonIds[index]" :data-product-index="index"><template v-if="value"><img v-if="row.key === 'image' && value.image" :src="value.image" :alt="value.alt" width="160" height="120" loading="lazy" /><template v-else>{{ value.display }}</template></template><span v-else-if="entries.get(comparisonIds[index])?.loading" role="status">{{ t('Loading…') }}</span><span v-else-if="entries.get(comparisonIds[index])?.error" aria-hidden="true">—</span><template v-else>{{ t('No information yet.') }}</template></td></tr></tbody>
          </table>
        </div>
        <div class="comparison-table-wrap comparison-mobile">
          <table class="comparison-table"><thead><tr><th scope="col">{{ t('Feature') }}</th><th v-for="id in mobilePair" :key="id" scope="col"><template v-if="entries.get(id)?.loading">{{ t('Loading…') }}</template><template v-else-if="entries.get(id)?.error"><span role="alert">{{ t('Could not load this product.') }}</span><button class="text-button" type="button" @click="load(id, true)">{{ t('Retry') }}</button></template><template v-else><RouterLink :to="`/products/${id}`">{{ entries.get(id)?.product?.name }}<span class="sr-only"> · {{ t('View product') }}</span></RouterLink><RouterLink class="text-button" :to="`/products/${id}`">{{ t('View product') }}</RouterLink></template><button class="text-button" type="button" @click="toggleComparison(id)">{{ t('Remove from comparison') }}</button></th></tr></thead><tbody><tr v-for="row in mobileRows" :key="row.key"><th scope="row">{{ row.label }}</th><td v-for="(value, index) in row.values" :key="mobilePair[index]"><template v-if="value"><img v-if="row.key === 'image' && value.image" :src="value.image" :alt="value.alt" width="160" height="120" loading="lazy" /><template v-else>{{ value.display }}</template></template><span v-else-if="entries.get(mobilePair[index])?.loading" role="status">{{ t('Loading…') }}</span><span v-else-if="entries.get(mobilePair[index])?.error" aria-hidden="true">—</span><template v-else>{{ t('No information yet.') }}</template></td></tr></tbody></table>
        </div>
      </template>
    </section>
  </div>
</template>
