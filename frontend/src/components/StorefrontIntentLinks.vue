<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { t } from '../i18n'

const intents = [
  { label: 'Road running', query: 'running' },
  { label: 'Everyday wear', query: 'daily' },
  { label: 'Trail', query: 'trail' },
]
</script>

<template>
  <nav class="storefront-intents" :aria-label="t('Find your next pair')">
    <h2>{{ t('Find your next pair') }}</h2>
    <RouterLink v-for="intent in intents" :key="intent.query" :to="{ path: '/', query: { q: intent.query }, hash: '#product-search' }">
      <span>{{ t(intent.label) }}</span><span aria-hidden="true">→</span>
    </RouterLink>
  </nav>
</template>
