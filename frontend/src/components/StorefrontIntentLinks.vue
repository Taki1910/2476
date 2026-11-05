<script lang="ts">
const needs = ['running', 'daily', 'trail'] as const
const colors = ['white', 'black', 'cream', 'navy'] as const

export function discoveryQuery(filters: { need: string; color: string; budget: string }) {
  const need = needs.includes(filters.need as typeof needs[number]) ? filters.need : ''
  const color = colors.includes(filters.color as typeof colors[number]) ? filters.color : ''
  const budget = Number(filters.budget)
  return [need, color, Number.isSafeInteger(budget) && budget > 0 && budget <= 1_000_000_000 ? `under ${budget}` : ''].filter(Boolean).join(' ')
}
</script>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { t } from '../i18n'

const route = useRoute()
const router = useRouter()
const need = ref('')
const color = ref('')
const budget = ref('')
const intents = [
  { label: 'Road running', query: 'running' },
  { label: 'Everyday wear', query: 'daily' },
  { label: 'Trail', query: 'trail' },
]

function syncFilters(query: unknown) {
  const value = typeof query === 'string' ? query.toLowerCase() : ''
  need.value = needs.find(option => new RegExp(`(?:^|\\s)${option}(?:$|\\s)`).test(value)) ?? ''
  color.value = colors.find(option => new RegExp(`(?:^|\\s)${option}(?:$|\\s)`).test(value)) ?? ''
  budget.value = value.match(/\bunder\s+([1-9]\d*)$/)?.[1] ?? ''
}

watch(() => route.query.q, syncFilters, { immediate: true })

function updateNeed(event: Event) { need.value = (event.target as HTMLSelectElement).value }
function updateColor(event: Event) { color.value = (event.target as HTMLSelectElement).value }
function updateBudget(event: Event) { budget.value = (event.target as HTMLInputElement).value }

async function applyFilters() {
  const query = discoveryQuery({ need: need.value, color: color.value, budget: budget.value })
  await router.push({ path: '/', query: { ...route.query, q: query || undefined }, hash: '#product-search' })
}

async function clearFilters() {
  need.value = ''; color.value = ''; budget.value = ''
  await router.push({ path: '/', query: { ...route.query, q: undefined }, hash: '#product-search' })
}
</script>

<template>
  <details class="storefront-discovery">
    <summary>{{ t('Filters & Categories') }}</summary>
    <nav class="storefront-intents" :aria-label="t('Find your next pair')">
      <h2>{{ t('Find your next pair') }}</h2>
      <RouterLink v-for="intent in intents" :key="intent.query" :to="{ path: '/', query: { q: intent.query }, hash: '#product-search' }">
        <span>{{ t(intent.label) }}</span><span aria-hidden="true">→</span>
      </RouterLink>
      <form class="storefront-intent-filters" @submit.prevent="applyFilters">
        <label>{{ t('Need') }}<select :value="need" @change="updateNeed"><option value="">{{ t('Any need') }}</option><option value="running">{{ t('Road running') }}</option><option value="daily">{{ t('Everyday wear') }}</option><option value="trail">{{ t('Trail') }}</option></select></label>
        <label>{{ t('Color') }}<select :value="color" @change="updateColor"><option value="">{{ t('Any color') }}</option><option value="white">{{ t('White') }}</option><option value="black">{{ t('Black') }}</option><option value="cream">{{ t('Cream') }}</option><option value="navy">{{ t('Navy') }}</option></select></label>
        <label>{{ t('Maximum budget (VND)') }}<input :value="budget" type="number" min="1" max="1000000000" step="1" inputmode="numeric" @input="updateBudget" /></label>
        <button type="submit">{{ t('Apply filters') }}</button>
        <button type="button" class="text-button" @click="clearFilters">{{ t('Clear filters') }}</button>
      </form>
    </nav>
  </details>
</template>
