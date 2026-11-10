<script setup lang="ts">
import type { PickupItem } from '../api'
import { formatVnd } from '../format'
import { t } from '../i18n'

defineProps<{ items: (PickupItem & { unitPriceAmount?: number; totalAmount?: number })[] }>()
</script>

<template>
  <ul class="commerce-items" :aria-label="t('Order items')">
    <li v-for="item in items" :key="item.sku" class="commerce-item">
      <div><strong>{{ item.sku }}</strong><p>{{ t('Size') }} {{ item.size }}<template v-if="item.color"> · {{ t(item.color) }}</template> · {{ t('Quantity') }} {{ item.quantity }}</p></div>
      <div v-if="item.unitPriceAmount !== undefined && item.totalAmount !== undefined" class="commerce-item-price"><span>{{ t('Unit price') }} {{ formatVnd(item.unitPriceAmount) }}</span><strong>{{ t('Subtotal') }} {{ formatVnd(item.totalAmount) }}</strong></div>
    </li>
  </ul>
</template>
