<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { api, type PublicOffer, type VoucherClaimState } from '../api'
import { errorCopy, formatDateTime, formatVnd } from '../format'
import { messageLabel, t } from '../i18n'
import { session } from '../session'
import { offerBenefit, voucherError } from '../voucher'
const route=useRoute(),offer=ref<PublicOffer>(),claimState=ref<VoucherClaimState>(),loading=ref(true),claiming=ref(false),copied=ref(false),error=ref('');let disposed=false
const id=computed(()=>String(route.params.familyId))
const isCustomer=computed(()=>session.account?.roles.includes('CUSTOMER')??false)
const saved=computed(()=>claimState.value?.claimed&&claimState.value.claim?.claimStatus==='CLAIMED')
async function load(){const requested=id.value;loading.value=true;error.value='';try{const [offerResult,stateResult]=await Promise.all([api.publicOffer(requested),isCustomer.value?api.voucherClaimState(requested):Promise.resolve(undefined)]);if(!disposed&&requested===id.value){offer.value=offerResult;claimState.value=stateResult}}catch(e){if(!disposed)error.value=errorCopy(e)}finally{if(!disposed)loading.value=false}}
async function claim(){if(!offer.value||claiming.value||!isCustomer.value)return;claiming.value=true;error.value='';const generation=session.generation,account=session.account?.accountId;try{await api.claimVoucher(offer.value.familyId);const state=await api.voucherClaimState(offer.value.familyId);if(!disposed&&generation===session.generation&&account===session.account?.accountId)claimState.value=state}catch(e){if(!disposed&&generation===session.generation)error.value=voucherError(e)}finally{claiming.value=false}}
async function copyCode(){if(!offer.value?.code)return;try{await navigator.clipboard.writeText(offer.value.code);copied.value=true;error.value='';setTimeout(()=>copied.value=false,1800)}catch{error.value=t('Copy failed. Select the code and copy it manually.')}}
watch(id,()=>{offer.value=undefined;claimState.value=undefined;load()});onMounted(load);onBeforeUnmount(()=>{disposed=true})
</script>
<template><div class="offer-detail-page"><RouterLink class="back-link" to="/promotions">← {{t('All offers')}}</RouterLink>
  <div v-if="loading" class="queue-loading" role="status"><span class="loader-mark"></span>{{t('Loading offer…')}}</div>
  <section v-else-if="error&&!offer" class="inline-state" role="alert"><h1>{{t('Offer unavailable')}}</h1><p>{{messageLabel(error)}}</p></section>
  <article v-else-if="offer" class="offer-detail"><header><h1>{{offer.name}}</h1><strong>{{offerBenefit(offer)}}</strong><p>{{offer.customerSummary}}</p></header><dl><div v-if="offer.minimumSpendAmount"><dt>{{t('Minimum spend')}}</dt><dd>{{formatVnd(offer.minimumSpendAmount)}}</dd></div><div><dt>{{t('Valid from')}}</dt><dd>{{formatDateTime(offer.validFrom)}}</dd></div><div v-if="offer.validTo"><dt>{{t('Ends')}}</dt><dd>{{formatDateTime(offer.validTo)}}</dd></div></dl><section><h2>{{t('Terms')}}</h2><p class="offer-terms">{{offer.customerTerms}}</p></section>
    <section v-if="offer.code" class="public-voucher-code"><h2>{{t('Offer code')}}</h2><p><code>{{offer.code}}</code><button class="text-button" type="button" @click="copyCode">{{t(copied?'Copied':'Copy code')}}</button></p></section>
    <p v-if="saved" class="saved-confirmation" role="status">{{ t(claimState?.offerAvailability === 'AVAILABLE' ? 'Saved to My Vouchers.' : 'Saved to My Vouchers, but it is currently {status}.', { status: t(claimState?.offerAvailability ?? 'UNAVAILABLE') }) }}</p><p v-if="error" class="form-error" role="alert">{{messageLabel(error)}}</p><div class="product-actions"><button v-if="offer.acquisitionMode==='CLAIMABLE'&&isCustomer&&!saved" class="primary-button" :disabled="claiming" @click="claim">{{t(claiming?'Saving…':'Save offer')}}</button><RouterLink v-else-if="offer.acquisitionMode==='CLAIMABLE'&&!session.account" class="primary-button" :to="{path:'/login',query:{returnTo:route.fullPath}}">{{t('Sign in to save')}}</RouterLink><RouterLink :class="saved&&claimState?.offerAvailability==='AVAILABLE'?'primary-button':'text-button'" to="/cart">{{t(saved&&claimState?.offerAvailability==='AVAILABLE'?'Use in cart':'Go to cart')}}</RouterLink></div>
  </article></div></template>
