<script setup lang="ts">
import type { ProductSummary, PublicOffer } from '../api'
import { t } from '../i18n'
import type { StorefrontHomepage } from '../merchandising'
import StorefrontSectionPreview from './StorefrontSectionPreview.vue'

defineProps<{ homepage: StorefrontHomepage<ProductSummary, PublicOffer> | null; loading: boolean; failed: boolean }>()
</script>

<template>
  <section v-if="loading" class="storefront-home-loading" role="status" :aria-label="t('Loading storefront highlights…')">
    <div><span></span><span></span><span></span></div><i aria-hidden="true"></i>
  </section>
  <div v-else-if="homepage?.sections.length" class="customer-merchandising">
    <StorefrontSectionPreview v-for="section in homepage.sections" :key="section.sectionId" :section="section" mode="customer" />
  </div>
  <p v-else-if="failed" class="storefront-home-notice" role="status">{{ t('Storefront highlights are temporarily unavailable. The full catalog remains available below.') }}</p>
</template>
