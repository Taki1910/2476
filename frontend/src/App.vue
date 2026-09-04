<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { SESSION_ENDED_EVENT } from './api'
import { locale, messageLabel, setLocale, t } from './i18n'
import { errorCopy } from './format'
import { clearPrivateSession, homeFor, loadSession, session, SESSION_CHANGE_CHANNEL, SESSION_CHANGE_SOURCE, signOut } from './session'
import { cartCount } from './cart'

const route = useRoute()
const router = useRouter()
let sessionChanges: BroadcastChannel | undefined
const logoutBusy = ref(false)
const logoutError = ref('')
async function reloadSession() {
  clearPrivateSession()
  await loadSession()
  if (!session.unavailable) await router.replace('/')
}
async function logout() {
  logoutBusy.value = true; logoutError.value = ''
  try { await router.replace(await signOut()) }
  catch (error) { logoutError.value = errorCopy(error) }
  finally { logoutBusy.value = false }
}
function endSession() {
  if (!session.account) return
  clearPrivateSession()
  router.replace({ path: '/login', query: { returnTo: route.fullPath, reason: 'expired' } })
}
onMounted(() => {
  window.addEventListener(SESSION_ENDED_EVENT, endSession)
  sessionChanges = new BroadcastChannel(SESSION_CHANGE_CHANNEL)
  sessionChanges.onmessage = event => { if (event.data?.source !== SESSION_CHANGE_SOURCE) reloadSession() }
})
onBeforeUnmount(() => {
  window.removeEventListener(SESSION_ENDED_EVENT, endSession)
  sessionChanges?.close()
})
</script>

<template>
  <a class="skip-link" href="#main">{{ t('Skip to main content') }}</a>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="logo" :to="session.account ? homeFor(session.account) : '/'" :aria-label="t('Shoe Commerce home')">
        <span aria-hidden="true">SC</span><strong>Shoe Commerce</strong>
      </RouterLink>
      <div class="account-controls">
        <nav :aria-label="t('Store')">
          <RouterLink to="/">{{ t('Store') }}</RouterLink>
          <RouterLink to="/#product-search">{{ t('Search') }}</RouterLink>
          <RouterLink to="/cart">{{ t('Cart') }}<span v-if="cartCount" class="cart-count" :aria-label="t('Cart items')">{{ cartCount }}</span></RouterLink>
          <RouterLink v-if="session.account?.permissions.includes('ORDER_PLACE')" to="/orders">{{ t('My Orders') }}</RouterLink>
          <RouterLink v-if="session.account?.permissions.includes('FULFILL_ORDER')" to="/operations/fulfillments">{{ t('Fulfillment') }}</RouterLink>
          <RouterLink v-if="session.account?.permissions.includes('POS_SELL')" to="/operations/pos">{{ t('POS') }}</RouterLink>
          <RouterLink v-if="session.account?.permissions.includes('REPORT_VIEW')" to="/operations/reports">{{ t('Reports') }}</RouterLink>
        </nav>
        <div class="locale-switch" :aria-label="t('Choose language')">
          <button type="button" :aria-pressed="locale === 'vi-VN'" @click="setLocale('vi-VN')">VI</button><span aria-hidden="true">/</span><button type="button" :aria-pressed="locale === 'en'" @click="setLocale('en')">EN</button>
        </div>
        <span v-if="session.account">{{ session.account.login }}</span>
        <button v-if="session.account" type="button" :disabled="logoutBusy" @click="logout">{{ t(logoutBusy ? 'Signing out…' : 'Sign out') }}</button>
        <div v-else class="guest-actions"><RouterLink to="/login">{{ t('Sign in') }}</RouterLink><RouterLink to="/register">{{ t('Register') }}</RouterLink></div>
      </div>
    </header>
    <main id="main">
      <p v-if="logoutError" class="form-error" role="alert">{{ messageLabel(logoutError) }}</p>
      <section v-if="session.unavailable" class="inline-state" role="alert">
        <h1>{{ t('Session service unavailable') }}</h1>
        <p>{{ t('We could not check your session. Retry when the server is ready.') }}</p>
        <button class="text-button" type="button" @click="reloadSession">{{ t('Retry') }}</button>
      </section>
      <RouterView v-else :key="`${session.generation}:${session.account?.accountId ?? 'guest'}`" />
    </main>
  </div>
</template>
