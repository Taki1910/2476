<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, type PublicOffer } from '../api'
import { errorCopy, formatDateTime, formatVnd } from '../format'
import { messageLabel, t } from '../i18n'
import { session } from '../session'
import { offerBenefit, voucherError } from '../voucher'
const route=useRoute(),offer=ref<PublicOffer>(),loading=ref(true),claiming=ref(false),saved=ref(false),error=ref('');let disposed=false
const id=computed(()=>String(route.params.familyId))
async function load(){const requested=id.value;loading.value=true;error.value='';try{const r=await api.publicOffer(requested);if(!disposed&&requested===id.value)offer.value=r}catch(e){if(!disposed)error.value=errorCopy(e)}finally{if(!disposed)loading.value=false}}
async function claim(){if(!offer.value||claiming.value)return;claiming.value=true;error.value='';const generation=session.generation,account=session.account?.accountId;try{const claim=await api.claimVoucher(offer.value.familyId);if(!disposed&&generation===session.generation&&account===session.account?.accountId){saved.value=claim.claimStatus==='CLAIMED';if(!saved.value)error.value=t('This saved offer is no longer available.')}}catch(e){if(!disposed&&generation===session.generation)error.value=voucherError(e)}finally{claiming.value=false}}
watch(id,()=>{offer.value=undefined;saved.value=false;load()});onMounted(load);onBeforeUnmount(()=>{disposed=true})
</script>
<template><div class="offer-detail-page"><RouterLink class="back-link" to="/promotions">← {{t('All offers')}}</RouterLink>
  <div v-if="loading" class="queue-loading" role="status"><span class="loader-mark"></span>{{t('Loading offer…')}}</div>
  <section v-else-if="error&&!offer" class="inline-state" role="alert"><h1>{{t('Offer unavailable')}}</h1><p>{{messageLabel(error)}}</p></section>
  <article v-else-if="offer" class="offer-detail"><header><p class="eyebrow">{{t(offer.acquisitionMode==='CLAIMABLE'?'Save to use later':'Code offer')}}</p><h1>{{offer.name}}</h1><strong>{{offerBenefit(offer)}}</strong><p>{{offer.customerSummary}}</p></header><dl><div v-if="offer.minimumSpendAmount"><dt>{{t('Minimum spend')}}</dt><dd>{{formatVnd(offer.minimumSpendAmount)}}</dd></div><div><dt>{{t('Valid from')}}</dt><dd>{{formatDateTime(offer.validFrom)}}</dd></div><div v-if="offer.validTo"><dt>{{t('Ends')}}</dt><dd>{{formatDateTime(offer.validTo)}}</dd></div></dl><section><h2>{{t('Terms')}}</h2><p class="offer-terms">{{offer.customerTerms}}</p></section>
    <p v-if="saved" class="saved-confirmation" role="status">{{t('Saved to My Vouchers.')}}</p><p v-if="error" class="form-error" role="alert">{{messageLabel(error)}}</p><div class="product-actions"><button v-if="offer.acquisitionMode==='CLAIMABLE'&&session.account&&!saved" class="primary-button" :disabled="claiming" @click="claim">{{t(claiming?'Saving…':'Save offer')}}</button><RouterLink v-else-if="offer.acquisitionMode==='CLAIMABLE'&&!session.account" class="primary-button" :to="{path:'/login',query:{returnTo:route.fullPath}}">{{t('Sign in to save')}}</RouterLink><RouterLink :class="saved?'primary-button':'text-button'" to="/cart">{{t(saved?'Use in cart':'Go to cart')}}</RouterLink></div>
  </article></div></template>
