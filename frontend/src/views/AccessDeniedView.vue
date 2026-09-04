<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { t } from '../i18n'
</script>
<template><section class="centered-state"><p class="state-code">403</p><h1>{{ t('Access denied') }}</h1><p>{{ t('This account is not authorized for this workspace.') }}</p><RouterLink class="text-button" to="/">{{ t('Store') }}</RouterLink></section></template>
