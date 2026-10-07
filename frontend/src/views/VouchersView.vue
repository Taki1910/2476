<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api, type SavedVoucher } from '../api'
import { errorCopy, formatDateTime } from '../format'
import { messageLabel, t } from '../i18n'
import { session } from '../session'
import { offerBenefit } from '../voucher'
const vouchers=ref<SavedVoucher[]>([]),loading=ref(true),loadingMore=ref(false),error=ref(''),page=ref(0),hasNext=ref(false);let disposed=false
async function load(reset=true){const generation=session.generation,owner=session.account?.accountId;reset?loading.value=true:loadingMore.value=true;error.value='';try{const next=reset?0:page.value+1;const r=await api.savedVouchers(next,20);if(!disposed&&generation===session.generation&&owner===session.account?.accountId){vouchers.value=reset?r.items:[...vouchers.value,...r.items];page.value=next;hasNext.value=r.hasNext}}catch(e){if(!disposed&&generation===session.generation)error.value=errorCopy(e)}finally{if(!disposed&&generation===session.generation){loading.value=false;loadingMore.value=false}}}
onMounted(load);onBeforeUnmount(()=>{disposed=true;vouchers.value=[]})
</script>
<template><div class="vouchers-page"><header class="offers-heading"><h1>{{t('My Vouchers')}}</h1><p>{{t('Saved offers belong to this account. Apply an available one in your cart to confirm eligibility and savings.') }}</p></header>
  <div v-if="loading" class="queue-loading" role="status"><span class="loader-mark"></span>{{t('Loading saved offers…')}}</div><section v-else-if="error" class="inline-state" role="alert"><h2>{{t('Saved offers unavailable')}}</h2><p>{{messageLabel(error)}}</p><button class="text-button" @click="load()">{{t('Try again')}}</button></section><section v-else-if="!vouchers.length" class="inline-state"><h2>{{t('No saved offers yet.')}}</h2><p>{{t('Browse current offers and save one for later.') }}</p><RouterLink class="text-button" to="/promotions">{{t('Browse offers')}}</RouterLink></section>
  <template v-else><ol class="saved-offer-list"><li v-for="voucher in vouchers" :key="voucher.claimId"><div><strong class="voucher-state" :data-state="voucher.offerAvailability">{{t(voucher.offerAvailability)}}</strong><h2>{{voucher.offer.name}}</h2><p>{{voucher.offer.customerSummary}}</p><small>{{t('Saved on')}} {{formatDateTime(voucher.claimedAt)}}</small></div><strong>{{offerBenefit(voucher.offer)}}</strong><div class="saved-offer-actions"><RouterLink v-if="voucher.termsAvailable" class="text-button" :to="`/promotions/${voucher.offer.familyId}`">{{t('View terms')}}</RouterLink><span v-else class="terms-unavailable">{{t('Current public terms unavailable')}}</span><RouterLink v-if="voucher.claimStatus==='CLAIMED'&&voucher.offerAvailability==='AVAILABLE'" class="primary-button" to="/cart">{{t('Use in cart')}}</RouterLink></div></li></ol><button v-if="hasNext" class="text-button load-more" :disabled="loadingMore" @click="load(false)">{{t(loadingMore?'Loading…':'Load more vouchers')}}</button></template>
</div></template>
