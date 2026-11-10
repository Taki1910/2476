import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './api'
import { submitManualReconciliation, type ManualReconciliationState } from './payment-reconciliation'
import { setLocale, t } from './i18n'
import reportsSource from './views/ReportsView.vue?raw'

describe('manual VNPAY reconciliation', () => {
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); setLocale('en') })

  it('sends one explicit request and does not automatically retry provider failure', async () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-1111-4111-8111-111111111111')
    const state: ManualReconciliationState = {}
    const unavailable = new ApiError(503, 'PAYMENT_PROVIDER_UNAVAILABLE', 'safe')
    const submit = vi.fn().mockRejectedValue(unavailable)

    await expect(submitManualReconciliation(state, 'attempt-a', submit)).rejects.toBe(unavailable)

    expect(submit).toHaveBeenCalledOnce()
    expect(state.command).toBeUndefined()
  })

  it('reuses the same key only for an explicit exact-target contention retry', async () => {
    vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('11111111-1111-4111-8111-111111111111')
      .mockReturnValueOnce('22222222-2222-4222-8222-222222222222')
    const state: ManualReconciliationState = {}
    const contention = new ApiError(503, 'CONCURRENCY_RETRY_REQUIRED', 'safe', undefined, undefined, {}, true, true)
    const submit = vi.fn().mockRejectedValueOnce(contention).mockResolvedValueOnce({ outcome: 'RESOLVED_PAID' })

    await expect(submitManualReconciliation(state, 'attempt-a', submit)).rejects.toBe(contention)
    await expect(submitManualReconciliation(state, 'attempt-a', submit)).resolves.toMatchObject({ outcome: 'RESOLVED_PAID' })

    expect(submit.mock.calls[1][0]).toBe(submit.mock.calls[0][0])
    expect(state.command).toBeUndefined()
  })

  it('posts only server-owned attempt identity plus idempotency key', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ attemptId: 'attempt/a', outcome: 'STILL_UNRESOLVED' })))
    vi.stubGlobal('fetch', fetchMock)

    await api.reconcilePaymentAttempt('attempt/a', 'query-key')

    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/operations/payment-attempts/attempt%2Fa/reconcile')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'POST', headers: expect.objectContaining({
      'Idempotency-Key': 'query-key', 'X-CSRF-TOKEN': 'csrf',
    }) })
    expect(fetchMock.mock.calls[1][1].body).toBeUndefined()
  })

  it('keeps the operation action permission- and state-gated with safe VI/EN copy', () => {
    expect(reportsSource).toContain("permissions.includes('PAYMENT_RECONCILE')")
    expect(reportsSource).toContain("entry.category === 'PAYMENT_REVIEW'")
    expect(reportsSource).toContain("entry.status === 'REVIEW_REQUIRED'")
    expect(t('Reconcile with VNPAY')).toBe('Reconcile with VNPAY')
    setLocale('vi-VN')
    expect(t('Reconcile with VNPAY')).toBe('Đối soát với VNPAY')
    expect(t('Still unresolved')).toBe('Vẫn chưa xác định')
    expect(t('Provider unavailable')).toBe('Nhà cung cấp tạm thời không khả dụng')
    expect(t('Result mismatch')).toBe('Kết quả không khớp')
  })
})
