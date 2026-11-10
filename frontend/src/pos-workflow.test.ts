import { describe, expect, it } from 'vitest'
import { acceptScannedCandidate, candidateStateLabel, evaluateCashTender, moveCandidateSelection, posSaleKeyAfterFailure, selectedCandidate } from './pos-workflow'
import posSource from './views/PosView.vue?raw'

const candidates = [{ id: 'variant-a' }, { id: 'variant-b' }]

describe('P4A cashier workflow', () => {
  it('moves through candidates with Arrow keys and selects only on Enter', () => {
    expect(moveCandidateSelection(-1, candidates.length, 'ArrowDown')).toBe(0)
    expect(moveCandidateSelection(0, candidates.length, 'ArrowDown')).toBe(1)
    expect(moveCandidateSelection(1, candidates.length, 'ArrowDown')).toBe(0)
    expect(moveCandidateSelection(0, candidates.length, 'ArrowUp')).toBe(1)
    expect(selectedCandidate(candidates, 1, 'Enter')).toEqual(candidates[1])
    expect(selectedCandidate(candidates, 1, 'ArrowDown')).toBeUndefined()
  })

  it('keeps a repeated scan on the same candidate without creating quantity state', () => {
    expect(acceptScannedCandidate(candidates[0], { id: 'variant-a' })).toEqual({
      candidate: { id: 'variant-a' }, repeated: true,
    })
    expect(acceptScannedCandidate(candidates[0], { id: 'variant-b' })).toEqual({
      candidate: { id: 'variant-b' }, repeated: false,
    })
  })

  it.each([
    ['SELLABLE', 'Ready to sell'],
    ['SOLD_OUT_HERE', 'Sold out here'],
    ['RETIRED', 'Retired'],
    ['NOT_PUBLISHED', 'Not published'],
    ['PRICE_UNAVAILABLE', 'Price unavailable'],
  ] as const)('presents %s as a truthful cashier state', (state, label) => {
    expect(candidateStateLabel(state)).toBe(label)
  })
})

describe('P4 POS cash tender', () => {
  it('keeps the reviewed sale key after an unknown retryable failure', () => {
    expect(posSource.match(/await api\.sellPos/g)).toHaveLength(1)
    expect(posSource).toContain("const code = reason instanceof ApiError ? reason.code : ''")
    expect(posSource).not.toMatch(/CONCURRENCY_RETRY_REQUIRED[\s\S]{0,200}saleKey\.value = ''/)
    expect(posSaleKeyAfterFailure('sale-key', 'CONCURRENCY_RETRY_REQUIRED')).toBe('sale-key')
    expect(posSaleKeyAfterFailure('sale-key', 'IDEMPOTENCY_KEY_CONFLICT')).toBe('')
  })

  it('accepts exact integer VND with zero change', () => {
    expect(evaluateCashTender('1250000', 1250000)).toEqual({
      status: 'ready', received: 1250000, change: 0,
    })
  })

  it('calculates integer change for an overpayment', () => {
    expect(evaluateCashTender('1500000', 1250000)).toEqual({
      status: 'ready', received: 1500000, change: 250000,
    })
  })

  it('reports the remaining amount for insufficient cash', () => {
    expect(evaluateCashTender('1000000', 1250000)).toEqual({
      status: 'insufficient', received: 1000000, remaining: 250000,
    })
  })

  it('keeps an untouched cash field distinct from invalid input', () => {
    expect(evaluateCashTender('', 1250000)).toEqual({ status: 'empty' })
  })

  it.each(['-1', '1.5', '1e6', '1,000,000', ' 1250000 ', 'cash', '9007199254740992'])(
    'rejects malformed or unsafe cash input %j',
    (input) => expect(evaluateCashTender(input, 1250000)).toEqual({ status: 'invalid' }),
  )

  it.each([-1, 1.5, 9007199254740992])('refuses unsafe authoritative totals %j', (total) => {
    expect(evaluateCashTender('1500000', total)).toEqual({ status: 'invalid' })
  })
})
