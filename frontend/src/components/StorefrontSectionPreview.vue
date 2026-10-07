<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import type { ProductSummary, PublicOffer } from '../api'
import { formatVnd } from '../format'
import { locale, t } from '../i18n'
import {
  MEDIA_OPTIONS, storefrontCtaLocation, type StorefrontCustomerSection, type StorefrontSectionDraft,
} from '../merchandising'
import { PRODUCT_IMAGE_PLACEHOLDER, productAlt, productListingImage } from '../product-media'
import { evidenceHighlights, productPositioning } from '../product-evidence'

type CustomerSection = StorefrontCustomerSection<ProductSummary, PublicOffer>
const props = withDefaults(defineProps<{
  section: StorefrontSectionDraft | CustomerSection
  products?: ProductSummary[]
  promotions?: PublicOffer[]
  mode?: 'preview' | 'customer'
  priority?: boolean
  headingLevel?: 'h1' | 'h2' | 'h3'
}>(), { products: () => [], promotions: () => [], mode: 'preview', priority: false })

function isCustomer(section: StorefrontSectionDraft | CustomerSection): section is CustomerSection { return 'copy' in section }
const customerSection = computed(() => isCustomer(props.section) ? props.section : null)
const draftSection = computed(() => customerSection.value ? null : props.section as StorefrontSectionDraft)
const title = computed(() => locale.value === 'vi-VN' ? customerSection.value?.copy.titleVi ?? draftSection.value?.titleVi : customerSection.value?.copy.titleEn ?? draftSection.value?.titleEn)
const supportingText = computed(() => locale.value === 'vi-VN' ? customerSection.value?.copy.supportingTextVi ?? draftSection.value?.supportingTextVi : customerSection.value?.copy.supportingTextEn ?? draftSection.value?.supportingTextEn)
const ctaLabel = computed(() => locale.value === 'vi-VN' ? customerSection.value?.cta.label.vi ?? draftSection.value?.ctaLabelVi : customerSection.value?.cta.label.en ?? draftSection.value?.ctaLabelEn)
const mediaUrl = computed(() => customerSection.value?.media?.url ?? MEDIA_OPTIONS.find(([key]) => key === draftSection.value?.mediaKey)?.[1])
const selectedProducts = computed(() => customerSection.value?.products ?? draftSection.value?.productIds.map(id => props.products.find(product => product.id === id)).filter(Boolean) as ProductSummary[] ?? [])
const promotion = computed(() => customerSection.value?.promotion ?? props.promotions.find(offer => offer.familyId === draftSection.value?.promotionFamilyId))
const enabled = computed(() => customerSection.value ? true : draftSection.value?.enabled)
const ctaTo = computed(() => customerSection.value ? storefrontCtaLocation(customerSection.value.cta) : '/')
const heading = computed(() => props.headingLevel ?? (props.mode === 'customer' ? 'h2' : 'h3'))
const offerPeriod = computed(() => {
  if (!promotion.value) return ''
  const formatter = new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium' })
  const from = formatter.format(new Date(promotion.value.validFrom))
  return promotion.value.validTo ? `${from} – ${formatter.format(new Date(promotion.value.validTo))}` : t('From {date}', { date: from })
})
const promotionMark = computed(() => {
  if (promotion.value?.effectType === 'ORDER_PERCENT' && promotion.value.percentageValue != null) return `${promotion.value.percentageValue}%`
  if (promotion.value?.effectType === 'ORDER_FIXED' && promotion.value.fixedAmount != null) return formatVnd(promotion.value.fixedAmount)
  return promotion.value?.effectType === 'FREE_SHIPPING' ? t('Free shipping') : ''
})
function productImage(product: ProductSummary) { return productListingImage(product.primaryImage || product.heroImage || PRODUCT_IMAGE_PLACEHOLDER) }
function productImageAlt(product: ProductSummary) { return productAlt(product.name) }
function productEvidence(product: ProductSummary) {
  const statement = productPositioning(product.evidence)
  return statement ? t(statement) : evidenceHighlights(product.evidence).map(label => t(label)).join(' · ')
}
</script>

<template>
  <section class="storefront-section" :class="[`storefront-section--${section.type.toLowerCase()}`, `storefront-section--${mode}`]" :data-enabled="enabled">
    <p v-if="!enabled" class="storefront-preview-disabled">{{ t('Disabled in publication') }}</p>

    <template v-if="section.type === 'HERO_BANNER'">
      <div class="storefront-section-copy">
        <component :is="heading">{{ title || t('Untitled section') }}</component>
        <p v-if="supportingText">{{ supportingText }}</p>
        <RouterLink v-if="mode === 'customer'" class="storefront-section-cta" :to="ctaTo">{{ ctaLabel }}</RouterLink>
        <span v-else class="storefront-section-cta">{{ ctaLabel || t('CTA label') }}</span>
      </div>
      <div v-if="mediaUrl" class="storefront-hero-media"><img :src="mediaUrl" :alt="title || t('Controlled product artwork')" width="1456" height="1092" :loading="mode === 'preview' || priority ? 'eager' : 'lazy'"></div>
    </template>

    <template v-else-if="section.type === 'PRODUCT_COLLECTION'">
      <header class="storefront-section-copy">
        <component :is="heading">{{ title || t('Untitled section') }}</component>
        <p v-if="supportingText">{{ supportingText }}</p>
      </header>
      <ul v-if="selectedProducts.length" class="storefront-section-products">
        <li v-for="(product, index) in selectedProducts" :key="product.id" :data-lead="index === 0">
          <RouterLink v-if="mode === 'customer'" :to="`/products/${product.id}`" :aria-label="`${t('View product')}: ${product.name}`">
            <div><img :src="productImage(product)" :alt="productImageAlt(product)" width="640" height="480" loading="lazy"></div>
            <span><strong>{{ product.name }}</strong><small>{{ product.category ?? product.collection ?? t('Available by size') }}</small><small v-if="productEvidence(product)" class="storefront-product-evidence">{{ productEvidence(product) }}</small><b>{{ t('From') }} {{ formatVnd(product.fromAmount) }}</b></span>
          </RouterLink>
          <template v-else>
            <img :src="productImage(product)" :alt="productImageAlt(product)">
            <strong>{{ product.name }}</strong><span>{{ formatVnd(product.fromAmount) }}</span>
          </template>
        </li>
      </ul>
      <p v-else class="storefront-preview-empty">{{ t('Selected products will appear here.') }}</p>
      <RouterLink v-if="mode === 'customer'" class="storefront-section-cta storefront-collection-cta" :to="ctaTo">{{ ctaLabel }}</RouterLink>
      <span v-else class="storefront-section-cta">{{ ctaLabel || t('CTA label') }}</span>
    </template>

    <template v-else>
      <div class="storefront-section-copy">
        <p class="storefront-offer-mark">{{ t('Current offer') }}</p>
        <component :is="heading">{{ title || t('Untitled section') }}</component>
        <p v-if="supportingText">{{ supportingText }}</p>
        <div v-if="promotion" class="storefront-offer-evidence">
          <strong>{{ promotion.name }}</strong>
          <p>{{ promotion.customerSummary }}</p>
          <p>{{ promotion.customerTerms }}</p>
          <time>{{ offerPeriod }}</time>
        </div>
        <p v-else class="storefront-preview-empty">{{ t('Choose a current promotion to preview it.') }}</p>
        <RouterLink v-if="mode === 'customer'" class="storefront-section-cta" :to="ctaTo">{{ ctaLabel }}</RouterLink>
        <span v-else class="storefront-section-cta">{{ ctaLabel || t('CTA label') }}</span>
      </div>
      <div v-if="mode === 'customer' && promotion && promotionMark" class="storefront-campaign-art" aria-hidden="true"><span>{{ title }}</span><strong>{{ promotionMark }}</strong></div>
    </template>
  </section>
</template>
