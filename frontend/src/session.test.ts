import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './api'
import { clearPrivateSession, loadSession, session, SESSION_CHANGE_CHANNEL, SESSION_CHANGE_SOURCE, signIn, signOut } from './session'

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('backend-authoritative session', () => {
  it('ignores a stale session error after a newer session check succeeds', async () => {
    let rejectOld!: (error: Error) => void
    const account = { accountId: 'new', login: 'new', roles: [], permissions: [] }
    vi.spyOn(api, 'me').mockImplementationOnce(() => new Promise((_, reject) => { rejectOld = reject }))
      .mockResolvedValueOnce(account)
    const oldCheck = loadSession()
    clearPrivateSession()
    await loadSession()
    rejectOld(new TypeError('old network failure'))
    await oldCheck
    expect(session).toMatchObject({ loaded: true, unavailable: false, account })
  })

  it('treats 401 as guest but fails closed on unavailable session service, then recovers', async () => {
    const me = vi.spyOn(api, 'me').mockRejectedValueOnce(new ApiError(401, 'UNAUTHENTICATED', ''))
    await loadSession()
    expect(session).toMatchObject({ loaded: true, unavailable: false, account: undefined })
    me.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await loadSession()
    expect(session).toMatchObject({ loaded: false, unavailable: true, account: undefined })
    const account = { accountId: 'one', login: 'one', roles: [], permissions: ['POS_SELL'] }
    me.mockResolvedValueOnce(account)
    await loadSession()
    expect(session).toMatchObject({ loaded: true, unavailable: false, account })
  })

  it('notifies other tabs only after successful login/logout, without identity or credentials', async () => {
    const postMessage = vi.fn()
    const close = vi.fn()
    const channels: string[] = []
    vi.stubGlobal('BroadcastChannel', class {
      constructor(name: string) { channels.push(name) }
      postMessage = postMessage
      close = close
    })
    vi.spyOn(api, 'login').mockResolvedValue({ accountId: 'one', login: 'one', roles: [], permissions: [] })
    vi.spyOn(api, 'logout').mockResolvedValue(undefined)
    await signIn('test-account', 'test-only-value')
    const generation = session.generation
    expect(await signOut()).toBe('/')
    expect(session.account).toBeUndefined()
    expect(session.generation).toBe(generation + 1)
    expect(channels).toEqual([SESSION_CHANGE_CHANNEL, SESSION_CHANGE_CHANNEL])
    expect(postMessage.mock.calls).toEqual([[{ source: SESSION_CHANGE_SOURCE }], [{ source: SESSION_CHANGE_SOURCE }]])
    expect(close).toHaveBeenCalledTimes(2)
    vi.mocked(api.login).mockRejectedValueOnce(new Error('rejected'))
    await expect(signIn('test-account', 'test-only-value')).rejects.toThrow('rejected')
    expect(postMessage).toHaveBeenCalledTimes(2)
  })
})
