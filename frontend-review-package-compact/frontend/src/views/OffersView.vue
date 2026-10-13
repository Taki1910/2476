<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api, type PublicOffer } from '../api'
import { errorCopy, formatDateTime, formatVnd } from '../format'
import { messageLabel, t } from '../i18n'
import { offerBenefit } from '../voucher'
const offers=ref<PublicOffer[]>([]),loading=ref(true),error=ref('');let disposed=false
async function load(){loading.value=true;error.value='';try{const r=await api.publicOffers();if(!disposed)offers.value=r.items}catch(e){if(!disposed)error.value=errorCopy(e)}finally{if(!disposed)loading.value=false}}
onMounted(load);onBeforeUnmount(()=>{disposed=true})
</script>
<template><div class="offers-page">
  <header class="offers-heading"><p class="eyebrow">{{t('Current offers')}}</p><h1>{{t('Offers for your next pair.')}}</h1><p>{{t('Browse public offers. Your cart total is confirmed only after you apply one.') }}</p></header>
  <div v-if="loading" class="queue-loading" role="status" aria-live="polite"><span class="loader-mark"></span>{{t('Loading offers…')}}</div>
  <section v-else-if="error" class="inline-state" role="alert"><h2>{{t('Offers unavailable')}}</h2><p>{{messageLabel(error)}}</p><button class="text-button" @click="load">{{t('Try again')}}</button></section>
  <section v-else-if="!offers.length" class="inline-state"><h2>{{t('No public offers right now.')}}</h2><p>{{t('Check back later or continue shopping.') }}</p><RouterLink class="text-button" to="/">{{t('Shop shoes')}}</RouterLink></section>
  <ol v-else class="offer-list"><li v-for="(offer,index) in offers" :key="offer.familyId"><RouterLink :to="`/promotions/${offer.familyId}`" class="offer-row"><span class="offer-index">{{String(index+1).padStart(2,'0')}}</span><div><p class="eyebrow">{{t(offer.acquisitionMode==='CLAIMABLE'?'Save to use later':'Code offer')}}</p><h2>{{offer.name}}</h2><p>{{offer.customerSummary}}</p><small v-if="offer.minimumSpendAmount">{{t('Minimum spend')}} · {{formatVnd(offer.minimumSpendAmount)}}</small><small v-if="offer.validTo">{{t('Ends')}} · {{formatDateTime(offer.validTo)}}</small></div><strong>{{offerBenefit(offer)}}</strong><span aria-hidden="true">→</span></RouterLink></li></ol>
</div></template>
