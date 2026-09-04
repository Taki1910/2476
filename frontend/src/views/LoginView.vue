<script setup lang="ts">
import { reactive } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { messageLabel, t } from '../i18n'
import { ApiError } from '../api'
import { errorCopy } from '../format'
import { loginDestination, safeReturnTo, signIn } from '../session'

const route = useRoute()
const router = useRouter()
const form = reactive({ login: typeof route.query.login === 'string' ? route.query.login : '', password: '', busy: false, error: '' })

async function submit() {
  form.busy = true
  form.error = ''
  try {
    const account = await signIn(form.login.trim(), form.password)
    form.password = ''
    await router.replace(loginDestination(route.query.returnTo, account))
  } catch (error) {
    form.error = error instanceof ApiError && error.status === 401 ? t('Sign-in failed. Check your account details and try again.') : errorCopy(error)
  } finally { form.busy = false }
}
</script>

<template>
  <div class="login-shell">
    <section class="login-intro">
      <p class="brand-wordmark">SHOE<br />COMMERCE</p>
      <h1>{{ t('Sign in') }}</h1>
      <p>{{ t('You need to sign in to continue.') }}</p>
    </section>
    <form class="login-form" aria-labelledby="login-title" @submit.prevent="submit">
      <h2 id="login-title">{{ t('Sign in') }}</h2>
      <p v-if="route.query.reason === 'expired'" role="status">{{ t('Your session has ended. Sign in again to continue.') }}</p>
      <label for="username">{{ t('Email or login') }}</label>
      <input id="username" v-model="form.login" name="username" autocomplete="username" maxlength="254" required :aria-describedby="form.error ? 'login-error' : undefined" />
      <label for="password">{{ t('Password') }}</label>
      <input id="password" v-model="form.password" name="password" type="password" autocomplete="current-password" required :aria-describedby="form.error ? 'login-error' : undefined" />
      <p v-if="form.error" id="login-error" class="form-error" role="alert">{{ messageLabel(form.error) }}</p>
      <button class="primary-button" type="submit" :disabled="form.busy">{{ t(form.busy ? 'Signing in…' : 'Sign in') }}</button>
      <p class="auth-alternate">{{ t('New to Shoe Commerce?') }} <RouterLink :to="{ path: '/register', query: { returnTo: safeReturnTo(route.query.returnTo) } }">{{ t('Register') }}</RouterLink></p>
    </form>
  </div>
</template>
