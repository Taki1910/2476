<script setup lang="ts">
import { reactive } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { errorCopy } from '../format'
import { messageLabel, t } from '../i18n'
import { loginDestination, safeReturnTo, signIn } from '../session'

const route = useRoute()
const router = useRouter()
const form = reactive({ login: '', password: '', confirmation: '', busy: false, error: '' })

async function submit() {
  form.error = ''
  if (form.password !== form.confirmation) { form.error = t('Passwords do not match.'); return }
  form.busy = true
  try {
    await api.register(form.login.trim(), form.password)
    const account = await signIn(form.login.trim(), form.password)
    form.password = form.confirmation = ''
    await router.replace(loginDestination(route.query.returnTo, account))
  } catch (error) { form.error = errorCopy(error) }
  finally { form.busy = false }
}
</script>

<template>
  <div class="login-shell">
    <section class="login-intro">
      <p class="brand-wordmark">SHOE<br />COMMERCE</p>
      <h1>{{ t('Create account') }}</h1>
      <p>{{ t('Choose the shoe.') }} {{ t('Then the size.') }}</p>
    </section>
    <form class="login-form" aria-labelledby="register-title" @submit.prevent="submit">
      <h2 id="register-title">{{ t('Register') }}</h2>
      <label for="register-login">{{ t('Email or login') }}</label>
      <input id="register-login" v-model="form.login" autocomplete="username" minlength="3" maxlength="254" pattern="[A-Za-z0-9._@+\-]{3,254}" required aria-describedby="register-hint" />
      <small id="register-hint">{{ t('3–254 characters: letters, numbers, . _ @ + −') }}</small>
      <label for="register-password">{{ t('Password') }}</label>
      <input id="register-password" v-model="form.password" type="password" autocomplete="new-password" minlength="12" maxlength="72" required />
      <label for="register-confirmation">{{ t('Confirm password') }}</label>
      <input id="register-confirmation" v-model="form.confirmation" type="password" autocomplete="new-password" minlength="12" maxlength="72" required />
      <p v-if="form.error" class="form-error" role="alert">{{ messageLabel(form.error) }}</p>
      <button class="primary-button" type="submit" :disabled="form.busy">{{ t(form.busy ? 'Creating account…' : 'Create account') }}</button>
      <p class="auth-alternate">{{ t('Already have an account?') }} <RouterLink :to="{ path: '/login', query: { returnTo: safeReturnTo(route.query.returnTo) } }">{{ t('Sign in') }}</RouterLink></p>
    </form>
  </div>
</template>
