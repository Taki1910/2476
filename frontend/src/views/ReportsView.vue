<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type InventoryReport, type NetSalesReport, type ProductSalesReport, type ReconciliationReport, type ReportScope } from '../api'
import { errorCopy, formatDateTime, formatVnd } from '../format'
import { messageLabel, statusLabel, t } from '../i18n'

const scope = ref<ReportScope>()
const net = ref<NetSalesReport>()
const products = ref<ProductSalesReport>()
const inventory = ref<InventoryReport>()
const reconciliation = ref<ReconciliationReport>()
const filters = reactive({ locationId: '', fromDate: '', toDate: '', sku: '' })
const state = reactive({ loadingScope: true, refreshing: false, error: '' })
const refreshAnnouncement = ref('')

const location = computed(() => net.value?.context.scope)
const exceptions = computed(() => reconciliation.value?.entries.filter(entry => entry.exception) ?? [])
const financialEntries = computed(() => reconciliation.value?.entries.filter(entry => !entry.exception) ?? [])
const hasReports = computed(() => Boolean(net.value && products.value && inventory.value && reconciliation.value))
const lowStock = computed(() => inventory.value?.rows.filter(row => row.available <= 2).length ?? 0)

async function refresh() {
  if (!filters.locationId || !filters.fromDate || !filters.toDate) return
  if (filters.fromDate >= filters.toDate) {
    state.error = t('From date must be before the exclusive to date.')
    return
  }
  state.refreshing = true
  state.error = ''
  try {
    const [nextNet, nextProducts, nextInventory, nextReconciliation] = await Promise.all([
      api.netSales(filters.fromDate, filters.toDate, filters.locationId),
      api.productSales(filters.fromDate, filters.toDate, filters.locationId),
      api.inventoryReport(filters.locationId, filters.sku.trim()),
      api.reconciliation(filters.fromDate, filters.toDate, filters.locationId),
    ])
    net.value = nextNet
    products.value = nextProducts
    inventory.value = nextInventory
    reconciliation.value = nextReconciliation
    refreshAnnouncement.value = t('Reports refreshed. Data is current as of {time}.', { time: formatDateTime(nextNet.context.asOf) })
  } catch (error) {
    state.error = errorCopy(error)
  } finally {
    state.refreshing = false
  }
}

async function loadScope() {
  state.loadingScope = true
  state.error = ''
  try {
    scope.value = await api.reportScope()
    filters.fromDate = scope.value.defaultFromDate
    filters.toDate = scope.value.defaultToDate
    filters.locationId = scope.value.locations[0]?.locationId ?? ''
    if (filters.locationId) await refresh()
  } catch (error) {
    state.error = errorCopy(error)
  } finally {
    state.loadingScope = false
  }
}

onMounted(loadScope)
</script>

<template>
  <article class="reports-page">
    <header class="reports-heading">
      <div>
        <p class="eyebrow">{{ t('Operations / branch-scoped reporting') }}</p>
        <h1>{{ t('Sales & stock proof.') }}</h1>
      </div>
      <p>{{ t('Trace net sales back to successful payment, accepted cash, successful void allocations, and location-owned inventory evidence.') }}</p>
    </header>

    <div v-if="state.loadingScope" class="report-loading" role="status" aria-live="polite">
      <span class="loader-mark" aria-hidden="true"></span>
      {{ t('Loading reporting scope…') }}
    </div>

    <section v-else-if="state.error && !scope" class="inline-state" role="alert">
      <h2>{{ t('Session service unavailable') }}</h2><p>{{ messageLabel(state.error) }}</p>
      <button class="text-button" type="button" @click="loadScope">{{ t('Retry') }}</button>
    </section>
    <section v-else-if="!scope?.locations.length" class="report-empty">
      <p class="eyebrow">{{ t('No assigned scope') }}</p>
      <h2>{{ t('No reporting location is available.') }}</h2>
      <p>{{ t('Ask an administrator to add an active branch and location assignment, then retry.') }}</p>
      <button class="primary-button" type="button" @click="loadScope">{{ t('Retry scope') }}</button>
    </section>

    <template v-else>
      <form class="report-filters" :aria-label="t('Report filters')" @submit.prevent="refresh">
        <div class="report-field report-location-field">
          <label for="report-location">{{ t('Location') }}</label>
          <select id="report-location" v-model="filters.locationId" required>
            <option v-for="item in scope.locations" :key="item.locationId" :value="item.locationId">
              {{ item.branchCode }} / {{ item.locationCode }} — {{ t(item.locationName) }}
            </option>
          </select>
        </div>
        <div class="report-field">
          <label for="report-from">{{ t('Start date') }} <span>{{ t('(included)') }}</span></label>
          <input id="report-from" v-model="filters.fromDate" type="date" required>
        </div>
        <div class="report-field">
          <label for="report-to">{{ t('End date') }} <span>{{ t('(not included)') }}</span></label>
          <input id="report-to" v-model="filters.toDate" type="date" required>
        </div>
        <div class="report-field">
          <label for="report-sku">{{ t('Inventory SKU') }} <span>{{ t('(optional)') }}</span></label>
          <input id="report-sku" v-model="filters.sku" maxlength="64" autocomplete="off" :placeholder="t('All SKUs')">
        </div>
        <button type="submit" :disabled="state.refreshing">
          {{ t(state.refreshing ? 'Refreshing…' : 'Refresh reports') }}
        </button>
      </form>

      <p v-if="state.error" class="report-error" role="alert">
        {{ messageLabel(state.error) }}
        <button type="button" @click="refresh">{{ t('Retry') }}</button>
      </p>
      <p class="sr-only" role="status" aria-live="polite">{{ refreshAnnouncement }}</p>

      <div v-if="hasReports" :aria-busy="state.refreshing">
        <aside class="report-scope" :aria-label="t('Report scope and freshness')">
          <p><strong>{{ t(location?.branchName ?? '') }}</strong> / {{ t(location?.locationName ?? '') }}</p>
          <p><span>{{ t('Business time') }}</span> {{ net?.context.businessTimezone }} · [{{ formatDateTime(net!.context.from!) }}, {{ formatDateTime(net!.context.to!) }})</p>
          <p><span>{{ t('As of') }}</span> {{ formatDateTime(net!.context.asOf) }}</p>
        </aside>

        <dl class="report-overview" :aria-label="t('Reporting summary')">
          <div><dt>{{ t('Net sales') }}</dt><dd>{{ formatVnd(net!.netSales) }}</dd></div>
          <div><dt>{{ t('Online / POS') }}</dt><dd><span class="money">{{ formatVnd(net!.onlineGross) }}</span> / <span class="money">{{ formatVnd(net!.posGross) }}</span></dd></div>
          <div><dt>{{ t('Products sold') }}</dt><dd>{{ products!.rows.length }} SKU</dd></div>
          <div><dt>{{ t('Low / zero stock') }}</dt><dd>{{ lowStock }}</dd></div>
          <div :data-alert="net!.exceptionCount > 0"><dt>{{ t('Reconciliation') }}</dt><dd>{{ net!.exceptionCount }}</dd></div>
        </dl>

        <nav class="report-index" :aria-label="t('Report sections')">
          <a href="#net-sales">01 {{ t('Net sales') }}</a><a href="#product-sales">02 {{ t('Product sales') }}</a><a href="#inventory">03 {{ t('Inventory') }}</a><a href="#reconciliation">04 {{ t('Reconciliation') }}</a>
        </nav>

        <section id="net-sales" class="report-section">
          <header class="report-section-heading">
            <p>01</p>
            <div>
              <p class="eyebrow">{{ t('Net sales') }} / VND</p>
              <h2>{{ t('Money equation') }}</h2>
            </div>
          </header>
          <div class="sales-equation" :aria-label="t('Online sales plus point of sale cash minus successful voids equals net sales')">
            <div><span>{{ t('Online successful') }}</span><strong>{{ formatVnd(net!.onlineGross) }}</strong></div>
            <b aria-hidden="true">+</b>
            <div><span>{{ t('POS cash') }}</span><strong>{{ formatVnd(net!.posGross) }}</strong></div>
            <b aria-hidden="true">−</b>
            <div><span>{{ t('Successful voids') }}</span><strong>{{ formatVnd(net!.successfulVoids) }}</strong></div>
            <b aria-hidden="true">=</b>
            <div class="equation-net"><span>{{ t('Net sales') }}</span><strong>{{ formatVnd(net!.netSales) }}</strong></div>
          </div>
          <p class="report-note">{{ t('Gross {gross}. Unresolved financial exceptions: {count}, worth {amount}, excluded from the equation.', { gross: formatVnd(net!.grossSales), count: net!.exceptionCount, amount: formatVnd(net!.exceptionAmount) }) }}</p>
          <p class="report-note">{{ t('Sales channels are not cash counts. Online payments do not add to register cash.') }}</p>
          <details class="cash-explanation"><summary>{{ t('Cash reconciliation') }}</summary><p>{{ t('Expected cash is accepted cash within a cashier shift. Actual counted cash and the difference are not recorded by this workflow.') }}</p></details>
        </section>

        <section id="product-sales" class="report-section">
          <header class="report-section-heading">
            <p>02</p>
            <div>
              <p class="eyebrow">{{ t('Product sales / historical snapshots') }}</p>
              <h2>{{ t('By SKU and size') }}</h2>
            </div>
          </header>
          <div v-if="products!.rows.length" class="report-table-wrap" tabindex="0" :aria-label="t('Product sales table')">
            <table class="responsive-report-table">
              <thead><tr><th>SKU</th><th>{{ t('Size') }}</th><th class="number">{{ t('Online') }}</th><th class="number">POS</th><th class="number">{{ t('Gross') }}</th><th class="number">{{ t('Voids') }}</th><th class="number">{{ t('Net') }}</th></tr></thead>
              <tbody>
                <tr v-for="row in products!.rows" :key="row.variantId">
                  <th scope="row" data-label="SKU">{{ row.sku }}</th><td :data-label="t('Size')">{{ row.size }}</td><td class="number" :data-label="t('Online')">{{ formatVnd(row.onlineGross) }}</td><td class="number" data-label="POS">{{ formatVnd(row.posGross) }}</td><td class="number" :data-label="t('Gross')">{{ formatVnd(row.grossSales) }}</td><td class="number" :data-label="t('Voids')">{{ formatVnd(row.successfulVoids) }}</td><td class="number report-total" :data-label="t('Net')">{{ formatVnd(row.netSales) }}</td>
                </tr>
              </tbody>
              <tfoot><tr><th colspan="4" scope="row">{{ t('All reported products') }}</th><td class="number" :data-label="t('Gross')">{{ formatVnd(products!.grossSales) }}</td><td class="number" :data-label="t('Voids')">{{ formatVnd(products!.successfulVoids) }}</td><td class="number" :data-label="t('Net')">{{ formatVnd(products!.netSales) }}</td></tr></tfoot>
            </table>
          </div>
          <p v-else class="inline-empty">{{ t('No successful sales or voids fall inside this interval.') }}</p>
        </section>

        <section id="inventory" class="report-section">
          <header class="report-section-heading">
            <p>03</p>
            <div>
              <p class="eyebrow">{{ t('Inventory / current at as-of') }}</p>
              <h2>{{ t('Balance and evidence') }}</h2>
            </div>
          </header>
          <div v-if="inventory!.rows.length" class="report-table-wrap" tabindex="0" :aria-label="t('Inventory balance table')">
            <table class="responsive-report-table">
              <thead><tr><th>{{ t('Product') }}</th><th>SKU</th><th>{{ t('Size') }}</th><th class="number">{{ t('On hand') }}</th><th class="number">{{ t('Reserved') }}</th><th class="number">{{ t('Available') }}</th><th>{{ t('Updated') }}</th></tr></thead>
              <tbody><tr v-for="row in inventory!.rows" :key="row.variantId"><th scope="row" :data-label="t('Product')">{{ row.productName }}</th><td data-label="SKU">{{ row.sku }}</td><td :data-label="t('Size')">{{ row.size }}</td><td class="number" :data-label="t('On hand')">{{ row.onHand }}</td><td class="number" :data-label="t('Reserved')">{{ row.reserved }}</td><td class="number report-total" :data-label="t('Available')">{{ row.available }}</td><td :data-label="t('Updated')">{{ formatDateTime(row.updatedAt) }}</td></tr></tbody>
            </table>
          </div>
          <p v-else class="inline-empty">{{ t('No inventory balance matches this location and SKU filter.') }}</p>
          <div class="evidence-grid">
            <section>
              <h3>{{ t('Recent stock movements') }} <span>{{ inventory!.movements.length }}</span></h3>
              <ol v-if="inventory!.movements.length" class="evidence-list">
                <li v-for="movement in inventory!.movements.slice(0, 12)" :key="movement.id">
                  <div><strong>{{ statusLabel(movement.type) }}</strong><span>{{ movement.sku }}</span></div>
                  <p>{{ t('On hand') }} {{ movement.onHandDelta > 0 ? '+' : '' }}{{ movement.onHandDelta }} · {{ t('Reserved') }} {{ movement.reservedDelta > 0 ? '+' : '' }}{{ movement.reservedDelta }}</p>
                  <time :datetime="movement.occurredAt">{{ formatDateTime(movement.occurredAt) }}</time>
                </li>
              </ol>
              <p v-else class="inline-empty">{{ t('No stock movement evidence for this selection.') }}</p>
            </section>
            <section>
              <h3>{{ t('Contributing reservations') }} <span>{{ inventory!.reservations.length }}</span></h3>
              <ol v-if="inventory!.reservations.length" class="evidence-list">
                <li v-for="reservation in inventory!.reservations" :key="reservation.id">
                  <div><strong>{{ statusLabel(reservation.status) }}</strong><span>{{ reservation.sku }}</span></div>
                  <p>{{ t('Quantity') }} {{ reservation.quantity }}</p>
                  <time :datetime="reservation.createdAt">{{ formatDateTime(reservation.createdAt) }}</time>
                </li>
              </ol>
              <p v-else class="inline-empty">{{ t('No active, adopted, or committed reservation contributes evidence.') }}</p>
            </section>
          </div>
        </section>

        <section id="reconciliation" class="report-section">
          <header class="report-section-heading">
            <p>04</p>
            <div>
              <p class="eyebrow">{{ t('Financial Reconciliation') }}</p><h2>{{ t('Included facts & exceptions') }}</h2>
            </div>
          </header>
          <section class="exception-ledger" :data-empty="exceptions.length === 0">
            <h3>{{ t('Needs attention') }} <span>{{ exceptions.length }}</span></h3>
            <p v-if="!exceptions.length">{{ t('No UNKNOWN, RELEASED, or REVIEW_REQUIRED entries in this interval.') }}</p>
            <p v-else>{{ t('Exception evidence stays visible and does not count as a successful sale or void.') }}</p>
            <ol v-if="exceptions.length" class="reconciliation-list">
              <li v-for="entry in exceptions" :key="entry.referenceId">
                <div><strong>{{ statusLabel(entry.status) }}</strong><span>{{ statusLabel(entry.category) }}</span></div>
                <p>{{ formatVnd(entry.amount) }} · {{ t('Excluded from net') }}</p>
                <code>{{ entry.orderId }}</code>
                <details class="exception-details">
                  <summary>{{ t('Details and next action') }}</summary>
                  <dl>
                    <div><dt>{{ t('Evidence reference') }}</dt><dd>{{ entry.referenceId }}</dd></div>
                    <div><dt>{{ t('Order reference') }}</dt><dd>{{ entry.orderId }}</dd></div>
                    <div><dt>{{ t('Occurred') }}</dt><dd><time :datetime="entry.occurredAt">{{ formatDateTime(entry.occurredAt) }}</time> · Asia/Ho_Chi_Minh</dd></div>
                    <div><dt>{{ t('Net effect') }}</dt><dd class="money">{{ formatVnd(entry.netEffect) }}</dd></div>
                  </dl>
                  <p><strong>{{ t('Status explanation') }}:</strong> {{ t(entry.category === 'PAYMENT_REVIEW' ? 'The provider reported payment, but the order was not confirmed. This amount is not recognized as a completed sale.' : 'The reversal outcome is unresolved or its allocation was released. It is not a successful void.') }}</p>
                  <p>{{ t('This report is read-only. A specific provider reason and a resolution action are not exposed. Keep both references for the authorized financial review.') }}</p>
                </details>
              </li>
            </ol>
          </section>
          <div v-if="financialEntries.length" class="report-table-wrap" tabindex="0" :aria-label="t('Financial reconciliation table')">
            <table class="responsive-report-table">
              <thead><tr><th>{{ t('Source') }}</th><th>{{ t('Status') }}</th><th>{{ t('Order') }}</th><th>{{ t('Occurred') }}</th><th class="number">{{ t('Amount') }}</th><th class="number">{{ t('Net effect') }}</th></tr></thead>
              <tbody><tr v-for="entry in financialEntries" :key="entry.referenceId"><th scope="row" :data-label="t('Source')">{{ statusLabel(entry.category) }}</th><td :data-label="t('Status')">{{ statusLabel(entry.status) }}</td><td :data-label="t('Order')"><code>{{ entry.orderId }}</code></td><td :data-label="t('Occurred')">{{ formatDateTime(entry.occurredAt) }}</td><td class="number" :data-label="t('Amount')">{{ formatVnd(entry.amount) }}</td><td class="number report-total" :data-label="t('Net effect')">{{ formatVnd(entry.netEffect) }}</td></tr></tbody>
            </table>
          </div>
          <p v-else class="inline-empty">{{ t('No successful payment, cash tender, or void facts fall inside this interval.') }}</p>
        </section>
      </div>
    </template>
  </article>
</template>
