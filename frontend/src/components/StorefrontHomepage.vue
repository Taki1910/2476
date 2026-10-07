<script setup lang="ts">
import { computed } from 'vue'
import type { ProductSummary, PublicOffer } from '../api'
import { t } from '../i18n'
import type { StorefrontHomepage } from '../merchandising'
import StorefrontSectionPreview from './StorefrontSectionPreview.vue'

const props = defineProps<{ homepage: StorefrontHomepage<ProductSummary, PublicOffer> | null; loading: boolean; failed: boolean }>()
const discoveryIndex = computed(() => {
  const index = props.homepage?.sections.findIndex(section => section.type === 'HERO_BANNER') ?? -1
  return index < 0 ? 0 : index
})
</script>

<template>
  <section v-if="loading" class="storefront-home-loading" role="status" :aria-label="t('Loading storefront highlights…')">
    <div><span></span><span></span><span></span></div><i aria-hidden="true"></i>
  </section>
  <div v-else-if="homepage?.sections.length" class="customer-merchandising">
    <template v-for="(section, index) in homepage.sections" :key="section.sectionId">
      <StorefrontSectionPreview :section="section" mode="customer" :heading-level="index === 0 ? 'h1' : 'h2'" :priority="index === 0" />
      <slot v-if="index === discoveryIndex" name="discovery" />
    </template>
  </div>
  <p v-else-if="failed" class="storefront-home-notice" role="status">{{ t('Storefront highlights are temporarily unavailable. The full catalog remains available below.') }}</p>
</template>
