<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type StaffAccessRecord, type StaffLocation } from '../api'
import { errorCopy } from '../format'
import { messageLabel, t } from '../i18n'
import { session } from '../session'

const staff = ref<StaffAccessRecord[]>([])
const locations = ref<StaffLocation[]>([])
const capabilities = ref<string[]>([])
const query = ref('')
const locationFilter = ref('')
const statusFilter = ref('')
const selected = ref<StaffAccessRecord>()
const loading = ref(true)
const busy = ref('')
const error = ref('')
const globalAdmin = computed(() => session.account?.permissions.includes('IDENTITY_MANAGE'))
const visible = computed(() => staff.value.filter(item =>
  item.login.includes(query.value.trim().toLowerCase())
  && (!locationFilter.value || item.assignments.some(scope => scope.locationId === locationFilter.value))
  && (!statusFilter.value || item.status === statusFilter.value)))

async function load() {
  loading.value = true; error.value = ''
  try {
    staff.value = await (globalAdmin.value ? api.globalStaffAccess() : api.staffAccess())
    if (!globalAdmin.value) {
      const [nextLocations, nextCapabilities] = await Promise.all([api.staffLocations(), api.staffCapabilities()])
      locations.value = nextLocations; capabilities.value = nextCapabilities.map(item => item.code)
    }
    if (selected.value) selected.value = staff.value.find(item => item.accountId === selected.value?.accountId)
  } catch (reason) { error.value = errorCopy(reason) }
  finally { loading.value = false }
}

async function change(key: string, action: () => Promise<unknown>) {
  busy.value = key; error.value = ''
  try { await action(); await load() }
  catch (reason) { error.value = errorCopy(reason) }
  finally { busy.value = '' }
}

onMounted(load)
</script>

<template>
  <article class="people-page">
    <header class="people-heading">
      <h1>{{ t('People & Access') }}</h1>
      <p>{{ t(globalAdmin ? 'Manage global staff account status.' : 'Manage operational access only inside your assigned locations.') }}</p>
    </header>
    <form class="people-filters" role="search" @submit.prevent>
      <label>{{ t('Search staff') }}<input v-model.trim="query" type="search" autocomplete="off" maxlength="254"></label>
      <label v-if="!globalAdmin">{{ t('Location filter') }}<select v-model="locationFilter"><option value="">{{ t('All manageable locations') }}</option><option v-for="scope in locations" :key="scope.locationId" :value="scope.locationId">{{ scope.branchCode }} / {{ scope.locationCode }}</option></select></label>
      <label>{{ t('Status filter') }}<select v-model="statusFilter"><option value="">{{ t('All statuses') }}</option><option value="ENABLED">{{ t('Enabled') }}</option><option value="DISABLED">{{ t('Disabled') }}</option></select></label>
    </form>
    <p v-if="error" class="form-error" role="alert">{{ messageLabel(error) }} <button type="button" @click="load">{{ t('Retry') }}</button></p>
    <div v-if="loading" class="queue-loading" role="status"><span class="loader-mark"></span>{{ t('Loading staff access…') }}</div>
    <p v-else-if="!visible.length" class="inline-empty">{{ t('No staff match these filters.') }}</p>
    <div v-else class="people-layout">
      <div class="people-table-wrap">
        <table class="people-table">
          <thead><tr><th>{{ t('Staff account') }}</th><th>{{ t('Base role') }}</th><th>{{ t('Location scope') }}</th><th>{{ t('Operational capabilities') }}</th><th>{{ t('Status') }}</th></tr></thead>
          <tbody><tr v-for="person in visible" :key="person.accountId" :data-selected="selected?.accountId === person.accountId" @click="selected = person"><th scope="row"><button type="button" @click.stop="selected = person">{{ person.login }}</button></th><td>{{ person.baseRole }}</td><td>{{ person.assignments.map(scope => scope.locationCode).join(', ') || '—' }}</td><td>{{ person.directCapabilities.join(', ') || '—' }}</td><td>{{ t(person.status === 'ENABLED' ? 'Enabled' : 'Disabled') }}</td></tr></tbody>
        </table>
      </div>
      <aside v-if="selected" class="people-detail" aria-labelledby="staff-detail-title">
        <button class="text-button people-close" type="button" @click="selected = undefined">{{ t('Close') }}</button>
        <h2 id="staff-detail-title">{{ selected.login }}</h2>
        <p><strong>{{ t('Base role') }}:</strong> {{ selected.baseRole }}</p>
        <template v-if="globalAdmin">
          <button type="button" :disabled="!!busy" @click="change('status', () => api.setAccountEnabled(selected!.accountId, selected!.status !== 'ENABLED'))">{{ t(selected.status === 'ENABLED' ? 'Disable account' : 'Enable account') }}</button>
        </template>
        <template v-else>
          <section><h3>{{ t('Assignments') }}</h3><label v-for="scope in locations" :key="scope.locationId"><input type="checkbox" :checked="selected.assignments.some(item => item.locationId === scope.locationId)" :disabled="!!busy" @change="change(`scope:${scope.locationId}`, () => api.setStaffAssignment(selected!.accountId, scope, !selected!.assignments.some(item => item.locationId === scope.locationId)))">{{ scope.branchCode }} / {{ scope.locationCode }}</label></section>
          <section><h3>{{ t('Operational capabilities') }}</h3><label v-for="capability in capabilities" :key="capability"><input type="checkbox" :checked="selected.directCapabilities.includes(capability)" :disabled="!!busy" @change="change(`cap:${capability}`, () => api.setStaffCapability(selected!.accountId, capability, !selected!.directCapabilities.includes(capability)))">{{ capability }}</label></section>
          <section><h3>{{ t('Inherited from role') }}</h3><p>{{ selected.inheritedPermissions.join(', ') || '—' }}</p><small>{{ t('Inherited permissions cannot be revoked individually here.') }}</small></section>
        </template>
      </aside>
    </div>
  </article>
</template>
