import { ref } from 'vue'

export const comparisonIds = ref<string[]>([])

export function toggleComparison(productId: string): 'added' | 'removed' | 'limit' {
  const index = comparisonIds.value.indexOf(productId)
  if (index >= 0) {
    comparisonIds.value.splice(index, 1)
    return 'removed'
  }
  if (comparisonIds.value.length >= 3) return 'limit'
  comparisonIds.value.push(productId)
  return 'added'
}

export function clearComparison() { comparisonIds.value = [] }

export function isCurrentComparisonRequest(productId: string, requestVersion: number, currentVersion: number, selectedIds = comparisonIds.value) {
  return requestVersion === currentVersion && selectedIds.includes(productId)
}

export function hasCanonicalDifference(values: (string | null | undefined)[]) {
  const known = values.filter((value): value is string => value != null)
  return known.length > 0 && (known.length !== values.length || known.some(value => value !== known[0]))
}

export function selectMobilePair(ids: string[], pair: string[], slot: number, selectedId: string) {
  const next = [...pair]
  const duplicate = next.indexOf(selectedId)
  if (duplicate >= 0 && duplicate !== slot) next[duplicate] = pair[slot]
  next[slot] = selectedId
  return next.filter(id => ids.includes(id))
}
