import { describe, expect, it, vi } from 'vitest'
import { ApiError } from './api'
import { sameCommand, submitRetryableCommand, type RetryableCommand } from './retryable-command'

describe('retryable command identity', () => {
  it('reuses one key for the exact payload and replaces it when the payload changes', () => {
    const randomUUID = vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('11111111-1111-4111-8111-111111111111')
      .mockReturnValueOnce('22222222-2222-4222-8222-222222222222')

    const first = sameCommand(undefined, 'variant|location|5|reason|3')
    const retry = sameCommand(first, 'variant|location|5|reason|3')
    const changed = sameCommand(retry, 'variant|location|6|reason|3')

    expect(retry).toBe(first)
    expect(retry.key).toBe('11111111-1111-4111-8111-111111111111')
    expect(changed.key).toBe('22222222-2222-4222-8222-222222222222')
    expect(randomUUID).toHaveBeenCalledTimes(2)
  })

  it('waits for an explicit retry, then resends the exact payload with the same key', async () => {
    vi.spyOn(crypto, 'randomUUID')
      .mockReturnValueOnce('11111111-1111-4111-8111-111111111111')
      .mockReturnValueOnce('22222222-2222-4222-8222-222222222222')
    const state: { value: RetryableCommand | undefined } = { value: undefined }
    const sent: { key: string; payload: string }[] = []
    const contention = new ApiError(503, 'CONCURRENCY_RETRY_REQUIRED', 'safe', undefined, undefined, {}, true, true)
    const send = vi.fn(async (key: string, payload: string) => {
      sent.push({ key, payload })
      if (sent.length === 1) throw contention
      return 'ok'
    })
    const submit = (fingerprint: string, payload: string) =>
      submitRetryableCommand(state, fingerprint, key => send(key, payload))

    await expect(submit('same-payload', 'exact-body')).rejects.toBe(contention)
    expect(send).toHaveBeenCalledOnce()
    await expect(submit('same-payload', 'exact-body')).resolves.toBe('ok')
    expect(sent[1]).toEqual(sent[0])
    expect(state.value).toBeUndefined()

    await submit('changed-payload', 'changed-body')
    expect(sent[2].key).toBe('22222222-2222-4222-8222-222222222222')
    expect(sent[2].payload).toBe('changed-body')
  })
})
