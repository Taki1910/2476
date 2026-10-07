import type { PosSaleState } from './api'

export function moveCandidateSelection(current: number, length: number, key: string): number {
  if (length <= 0) return -1
  if (key === 'ArrowDown') return current < 0 || current >= length - 1 ? 0 : current + 1
  if (key === 'ArrowUp') return current <= 0 ? length - 1 : current - 1
  return current
}

export function selectedCandidate<T>(candidates: T[], index: number, key: string): T | undefined {
  return key === 'Enter' && index >= 0 ? candidates[index] : undefined
}

export function acceptScannedCandidate<T extends { id: string }>(current: T | undefined, next: T) {
  return { candidate: next, repeated: current?.id === next.id }
}

const stateLabels: Record<PosSaleState, string> = {
  SELLABLE: 'Ready to sell',
  SOLD_OUT_HERE: 'Sold out here',
  RETIRED: 'Retired',
  NOT_PUBLISHED: 'Not published',
  PRICE_UNAVAILABLE: 'Price unavailable',
}

export const candidateStateLabel = (state: PosSaleState) => stateLabels[state]

export type CashTender =
  | { status: 'empty' }
  | { status: 'invalid' }
  | { status: 'insufficient'; received: number; remaining: number }
  | { status: 'ready'; received: number; change: number }

export function evaluateCashTender(input: string, total: number): CashTender {
  if (input === '') return { status: 'empty' }
  if (!/^\d+$/.test(input) || !Number.isSafeInteger(total) || total < 0) return { status: 'invalid' }

  const received = Number(input)
  if (!Number.isSafeInteger(received)) return { status: 'invalid' }
  if (received < total) return { status: 'insufficient', received, remaining: total - received }
  return { status: 'ready', received, change: received - total }
}
