<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { api, ApiError, SESSION_ENDED_EVENT, type Account } from './api'

const session = reactive<{ state: 'loading' | 'guest' | 'ready' | 'denied'; account?: Account }>({ state: 'loading' })
const login = reactive({ username: '', password: '', busy: false, error: '' })

async function loadSession() {
  session.state = 'loading'
  try {
    session.account = await api.me()
    session.state = session.account.permissions.some(permission => ['CATALOG_BROWSE', 'FULFILL_PICKUP', 'POS_SELL', 'REPORT_VIEW'].includes(permission)) ? 'ready' : 'denied'
  } catch (error) {
    session.state = error instanceof ApiError && error.status === 401 ? 'guest' : 'denied'
  }
}

async function submitLogin() {
  login.busy = true
  login.error = ''
  try {
    session.account = await api.login(login.username.trim(), login.password)
    login.password = ''
    session.state = session.account.permissions.some(permission => ['CATALOG_BROWSE', 'FULFILL_PICKUP', 'POS_SELL', 'REPORT_VIEW'].includes(permission)) ? 'ready' : 'denied'
  } catch {
    login.error = 'Sign-in failed. Check your account details and try again.'
  } finally {
    login.busy = false
  }
}

async function logout() {
  await api.logout()
  session.account = undefined
  session.state = 'guest'
}

function endSession() {
  session.account = undefined
  session.state = 'guest'
}

onMounted(() => {
  window.addEventListener(SESSION_ENDED_EVENT, endSession)
  loadSession()
})
onBeforeUnmount(() => window.removeEventListener(SESSION_ENDED_EVENT, endSession))
</script>

<template>
  <a class="skip-link" href="#main">Skip to main content</a>

  <div v-if="session.state === 'loading'" class="session-loader" role="status" aria-live="polite">
    <span class="loader-mark" aria-hidden="true"></span>
    Checking your session…
  </div>

  <main v-else-if="session.state === 'guest'" id="main" class="login-shell">
    <section class="login-intro">
      <p class="brand-wordmark">SHOE<br />COMMERCE</p>
      <h1>Find the pair.<br />Confirm the price.</h1>
      <p>Shoes ready to browse, clear availability by size, and a current price confirmed when you ask.</p>
    </section>
    <form class="login-form" @submit.prevent="submitLogin" aria-labelledby="login-title">
      <h2 id="login-title">Sign in</h2>
      <label for="username">Email or login</label>
      <input id="username" v-model="login.username" name="username" autocomplete="username" required />
      <label for="password">Password</label>
      <input id="password" v-model="login.password" name="password" type="password" autocomplete="current-password" required />
      <p v-if="login.error" class="form-error" role="alert">{{ login.error }}</p>
      <button class="primary-button" type="submit" :disabled="login.busy">
        {{ login.busy ? 'Signing in…' : 'Sign in' }}
      </button>
    </form>
  </main>

  <main v-else-if="session.state === 'denied'" id="main" class="centered-state">
    <p class="state-code">403</p>
    <h1>Commerce access required</h1>
    <p>This signed-in account cannot use the storefront, pickup operations, point of sale, or reporting.</p>
    <button class="text-button" type="button" @click="logout">Sign out and use another account</button>
  </main>

  <div v-else class="app-shell">
    <header class="site-header">
      <RouterLink class="logo" :to="session.account?.permissions.includes('CATALOG_BROWSE') ? '/' : session.account?.permissions.includes('REPORT_VIEW') ? '/operations/reports' : session.account?.permissions.includes('POS_SELL') ? '/operations/pos' : '/operations/pickups'" aria-label="Shoe Commerce home">
        <span aria-hidden="true">SC</span>
        <strong>Shoe Commerce</strong>
      </RouterLink>
      <div class="account-controls">
        <nav aria-label="Primary">
          <RouterLink v-if="session.account?.permissions.includes('CATALOG_BROWSE')" to="/">Storefront</RouterLink>
          <RouterLink v-if="session.account?.permissions.includes('FULFILL_PICKUP')" to="/operations/pickups">Pickups</RouterLink>
          <RouterLink v-if="session.account?.permissions.includes('POS_SELL')" to="/operations/pos">POS</RouterLink>
          <RouterLink v-if="session.account?.permissions.includes('REPORT_VIEW')" to="/operations/reports">Reports</RouterLink>
        </nav>
        <span>{{ session.account?.login }}</span>
        <button type="button" @click="logout">Sign out</button>
      </div>
    </header>
    <main id="main">
      <RouterView />
    </main>
  </div>
</template>
