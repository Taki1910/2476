<script setup lang="ts">
import { computed } from 'vue'
import { locale } from '../i18n'
import { presentationSummary, type ProductPresentation } from '../product-presentation'

const props = withDefaults(defineProps<{ presentation: ProductPresentation | null; compact?: boolean }>(), {
  compact: false,
})
const summary = computed(() => presentationSummary(props.presentation, locale.value))
</script>

<template>
  <p v-if="summary" class="product-presentation-summary" :class="{ compact }">{{ summary }}</p>
</template>
