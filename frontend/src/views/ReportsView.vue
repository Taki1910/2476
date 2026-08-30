<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type InventoryReport, type NetSalesReport, type ProductSalesReport, type ReconciliationReport, type ReportScope } from '../api'
import { errorCopy, formatDateTime, formatVnd } from '../format'

const scope = ref<ReportScope>()
const net = ref<NetSalesReport>()
const products = ref<ProductSalesReport>()
const inventory = ref<InventoryReport>()
const reconciliation = ref<ReconciliationReport>()
const filters = reactive({ locationId: '', fromDate: '', toDate: '', sku: '' })
const state = reactive({ loadingScope: true, refreshing: false, error: '' })
const refreshAnnouncement = ref('')

const location = computed(() => scope.value?.locations.find(item => item.locationId === filters.locationId))
const exceptions = computed(() => reconciliation.value?.entries.filter(entry => entry.exception) ?? [])
const financialEntries = computed(() => reconciliation.value?.entries.filter(entry => !entry.exception) ?? [])
const hasReports = computed(() => Boolean(net.value && products.value && inventory.value && reconciliation.value))

async function refresh() {
  if (!filters.locationId || !filters.fromDate || !filters.toDate) return
  if (filters.fromDate >= filters.toDate) {
    state.error = 'From date must be before the exclusive to date.'
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
    refreshAnnouncement.value = `Reports refreshed. Data is current as of ${formatDateTime(nextNet.context.asOf)}.`
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
        <p class="eyebrow">Operations / branch-scoped reporting</p>
        <h1>Sales &amp;<br><em>stock proof.</em></h1>
      </div>
      <p>Trace net sales back to successful payment, accepted cash, successful void allocations, and location-owned inventory evidence.</p>
    </header>

    <div v-if="state.loadingScope" class="report-loading" role="status" aria-live="polite">
      <span class="loader-mark" aria-hidden="true"></span>
      Loading reporting scope…
    </div>

    <section v-else-if="!scope?.locations.length" class="report-empty">
      <p class="eyebrow">No assigned scope</p>
      <h2>No reporting location is available.</h2>
      <p>Ask an administrator to add an active branch and location assignment, then retry.</p>
      <button class="primary-button" type="button" @click="loadScope">Retry scope</button>
    </section>

    <template v-else>
      <form class="report-filters" aria-label="Report filters" @submit.prevent="refresh">
        <div class="report-field report-location-field">
          <label for="report-location">Location</label>
          <select id="report-location" v-model="filters.locationId" required>
            <option v-for="item in scope.locations" :key="item.locationId" :value="item.locationId">
              {{ item.branchCode }} / {{ item.locationCode }} — {{ item.locationName }}
            </option>
          </select>
        </div>
        <div class="report-field">
          <label for="report-from">Start date <span>(included)</span></label>
          <input id="report-from" v-model="filters.fromDate" type="date" required>
        </div>
        <div class="report-field">
          <label for="report-to">End date <span>(not included)</span></label>
          <input id="report-to" v-model="filters.toDate" type="date" required>
        </div>
        <div class="report-field">
          <label for="report-sku">Inventory SKU <span>(optional)</span></label>
          <input id="report-sku" v-model="filters.sku" maxlength="64" autocomplete="off" placeholder="All SKUs">
        </div>
        <button type="submit" :disabled="state.refreshing">
          {{ state.refreshing ? 'Refreshing…' : 'Refresh reports' }}
        </button>
      </form>

      <p v-if="state.error" class="report-error" role="alert">
        {{ state.error }}
        <button type="button" @click="refresh">Retry</button>
      </p>
      <p class="sr-only" role="status" aria-live="polite">{{ refreshAnnouncement }}</p>

      <div v-if="hasReports" :aria-busy="state.refreshing">
        <aside class="report-scope" aria-label="Report scope and freshness">
          <p><strong>{{ location?.branchName }}</strong> / {{ location?.locationName }}</p>
          <p><span>Business time</span> {{ net?.context.businessTimezone }} · [{{ filters.fromDate }}, {{ filters.toDate }})</p>
          <p><span>As of</span> {{ formatDateTime(net!.context.asOf) }}</p>
        </aside>

        <nav class="report-index" aria-label="Report sections">
          <a href="#net-sales">01 Net sales</a>
          <a href="#product-sales">02 Product sales</a>
          <a href="#inventory">03 Inventory</a>
          <a href="#reconciliation">04 Reconciliation</a>
        </nav>

        <section id="net-sales" class="report-section">
          <header class="report-section-heading">
            <p>01</p>
            <div>
              <p class="eyebrow">Net Sales / VND</p>
              <h2>Money equation</h2>
            </div>
          </header>
          <div class="sales-equation" aria-label="Online sales plus point of sale cash minus successful voids equals net sales">
            <div><span>Online successful</span><strong>{{ formatVnd(net!.onlineGross) }}</strong></div>
            <b aria-hidden="true">+</b>
            <div><span>POS cash</span><strong>{{ formatVnd(net!.posGross) }}</strong></div>
            <b aria-hidden="true">−</b>
            <div><span>Successful voids</span><strong>{{ formatVnd(net!.successfulVoids) }}</strong></div>
            <b aria-hidden="true">=</b>
            <div class="equation-net"><span>Net sales</span><strong>{{ formatVnd(net!.netSales) }}</strong></div>
          </div>
          <p class="report-note">Gross {{ formatVnd(net!.grossSales) }}. {{ net!.exceptionCount }} unresolved financial exception{{ net!.exceptionCount === 1 ? '' : 's' }} worth {{ formatVnd(net!.exceptionAmount) }} are excluded from the equation.</p>
        </section>

        <section id="product-sales" class="report-section">
          <header class="report-section-heading">
            <p>02</p>
            <div>
              <p class="eyebrow">Product Sales / historical snapshots</p>
              <h2>By SKU and size</h2>
            </div>
          </header>
          <div v-if="products!.rows.length" class="report-table-wrap" tabindex="0" aria-label="Product sales table">
            <table class="responsive-report-table">
              <thead><tr><th>SKU</th><th>Size</th><th class="number">Online</th><th class="number">POS</th><th class="number">Gross</th><th class="number">Voids</th><th class="number">Net</th></tr></thead>
              <tbody>
                <tr v-for="row in products!.rows" :key="row.variantId">
                  <th scope="row" data-label="SKU">{{ row.sku }}</th><td data-label="Size">{{ row.size }}</td><td class="number" data-label="Online">{{ formatVnd(row.onlineGross) }}</td><td class="number" data-label="POS">{{ formatVnd(row.posGross) }}</td><td class="number" data-label="Gross">{{ formatVnd(row.grossSales) }}</td><td class="number" data-label="Voids">{{ formatVnd(row.successfulVoids) }}</td><td class="number report-total" data-label="Net">{{ formatVnd(row.netSales) }}</td>
                </tr>
              </tbody>
              <tfoot><tr><th colspan="4" scope="row">All reported products</th><td class="number" data-label="Gross">{{ formatVnd(products!.grossSales) }}</td><td class="number" data-label="Voids">{{ formatVnd(products!.successfulVoids) }}</td><td class="number" data-label="Net">{{ formatVnd(products!.netSales) }}</td></tr></tfoot>
            </table>
          </div>
          <p v-else class="inline-empty">No successful sales or voids fall inside this interval.</p>
        </section>

        <section id="inventory" class="report-section">
          <header class="report-section-heading">
            <p>03</p>
            <div>
              <p class="eyebrow">Inventory / current at as-of</p>
              <h2>Balance and evidence</h2>
            </div>
          </header>
          <div v-if="inventory!.rows.length" class="report-table-wrap" tabindex="0" aria-label="Inventory balance table">
            <table class="responsive-report-table">
              <thead><tr><th>Product</th><th>SKU</th><th>Size</th><th class="number">On hand</th><th class="number">Reserved</th><th class="number">Available</th><th>Updated</th></tr></thead>
              <tbody><tr v-for="row in inventory!.rows" :key="row.variantId"><th scope="row" data-label="Product">{{ row.productName }}</th><td data-label="SKU">{{ row.sku }}</td><td data-label="Size">{{ row.size }}</td><td class="number" data-label="On hand">{{ row.onHand }}</td><td class="number" data-label="Reserved">{{ row.reserved }}</td><td class="number report-total" data-label="Available">{{ row.available }}</td><td data-label="Updated">{{ formatDateTime(row.updatedAt) }}</td></tr></tbody>
            </table>
          </div>
          <p v-else class="inline-empty">No inventory balance matches this location and SKU filter.</p>
          <div class="evidence-grid">
            <section>
              <h3>Recent stock movements <span>{{ inventory!.movements.length }}</span></h3>
              <ol v-if="inventory!.movements.length" class="evidence-list">
                <li v-for="movement in inventory!.movements.slice(0, 12)" :key="movement.id">
                  <div><strong>{{ movement.type }}</strong><span>{{ movement.sku }}</span></div>
                  <p>on hand {{ movement.onHandDelta > 0 ? '+' : '' }}{{ movement.onHandDelta }} · reserved {{ movement.reservedDelta > 0 ? '+' : '' }}{{ movement.reservedDelta }}</p>
                  <time :datetime="movement.occurredAt">{{ formatDateTime(movement.occurredAt) }}</time>
                </li>
              </ol>
              <p v-else class="inline-empty">No stock movement evidence for this selection.</p>
            </section>
            <section>
              <h3>Contributing reservations <span>{{ inventory!.reservations.length }}</span></h3>
              <ol v-if="inventory!.reservations.length" class="evidence-list">
                <li v-for="reservation in inventory!.reservations" :key="reservation.id">
                  <div><strong>{{ reservation.status }}</strong><span>{{ reservation.sku }}</span></div>
                  <p>Quantity {{ reservation.quantity }}</p>
                  <time :datetime="reservation.createdAt">{{ formatDateTime(reservation.createdAt) }}</time>
                </li>
              </ol>
              <p v-else class="inline-empty">No active, adopted, or committed reservation contributes evidence.</p>
            </section>
          </div>
        </section>

        <section id="reconciliation" class="report-section">
          <header class="report-section-heading">
            <p>04</p>
            <div>
              <p class="eyebrow">Financial Reconciliation</p>
              <h2>Included facts &amp; exceptions</h2>
            </div>
          </header>
          <section class="exception-ledger" :data-empty="exceptions.length === 0">
            <h3>Needs attention <span>{{ exceptions.length }}</span></h3>
            <p v-if="!exceptions.length">No UNKNOWN, RELEASED, or REVIEW_REQUIRED entries in this interval.</p>
            <p v-else>Review each provider outcome before treating it as a completed reversal. These entries remain excluded from net sales.</p>
            <ol v-if="exceptions.length" class="reconciliation-list">
              <li v-for="entry in exceptions" :key="entry.referenceId">
                <div><strong>{{ entry.status }}</strong><span>{{ entry.category }}</span></div>
                <p>{{ formatVnd(entry.amount) }} · excluded from net</p>
                <code>{{ entry.orderId }}</code>
              </li>
            </ol>
          </section>
          <div v-if="financialEntries.length" class="report-table-wrap" tabindex="0" aria-label="Financial reconciliation table">
            <table class="responsive-report-table">
              <thead><tr><th>Source</th><th>Status</th><th>Order</th><th>Occurred</th><th class="number">Amount</th><th class="number">Net effect</th></tr></thead>
              <tbody><tr v-for="entry in financialEntries" :key="entry.referenceId"><th scope="row" data-label="Source">{{ entry.category }}</th><td data-label="Status">{{ entry.status }}</td><td data-label="Order"><code>{{ entry.orderId }}</code></td><td data-label="Occurred">{{ formatDateTime(entry.occurredAt) }}</td><td class="number" data-label="Amount">{{ formatVnd(entry.amount) }}</td><td class="number report-total" data-label="Net effect">{{ formatVnd(entry.netEffect) }}</td></tr></tbody>
            </table>
          </div>
          <p v-else class="inline-empty">No successful payment, cash tender, or void facts fall inside this interval.</p>
        </section>
      </div>
    </template>
  </article>
</template>
