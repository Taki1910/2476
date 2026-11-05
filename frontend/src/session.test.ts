import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError } from './api'
import { clearPrivateSession, loadSession, refreshSessionRoute, session, SESSION_CHANGE_CHANNEL, SESSION_CHANGE_SOURCE, signIn, signOut } from './session'
import { activateCart, addToCart, cart, syncCartStorage } from './cart'

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('backend-authoritative session', () => {
  it('does not let an older login response replace a newer account and cart owner', async () => {
    const values = new Map<string, string>()
    vi.stubGlobal('localStorage', { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key) })
    vi.stubGlobal('BroadcastChannel', class { postMessage() {} close() {} })
    let resolveOld!: (account: { accountId: string; login: string; roles: string[]; permissions: string[] }) => void
    vi.spyOn(api, 'login').mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
      .mockResolvedValueOnce({ accountId: 'B', login: 'B', roles: [], permissions: [] })
    cart.owner = null; cart.items = []; session.account = undefined
    addToCart({ productId: 'shoe', productName: 'Shoe', variantId: 'aaaaaaaa-0000-0000-0000-000000000001', sku: 'SHOE', size: '40', color: 'Black', image: null, amount: 1200000, currency: 'VND' })
    const oldLogin = signIn('A', 'password')
    expect(session.account).toBeUndefined()
    expect(cart.owner).toBeNull()
    await signIn('B', 'password')
    resolveOld({ accountId: 'A', login: 'A', roles: [], permissions: [] })
    await expect(oldLogin).rejects.toThrow('Sign-in superseded')
    expect(session.account).toMatchObject({ accountId: 'B' })
    expect(cart.owner).toBe('B')
    activateCart(null)
  })
  it.each(['storage-first', 'session-first'])('keeps the server account owner when %s cross-tab signals arrive', async order => {
    const values = new Map<string, string>()
    vi.stubGlobal('localStorage', { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key) })
    cart.owner = null; cart.items = []; session.account = undefined
    const a = { accountId: 'A', login: 'A', roles: [], permissions: [] }
    const b = { accountId: 'B', login: 'B', roles: [], permissions: [] }
    vi.spyOn(api, 'me').mockResolvedValueOnce(a).mockResolvedValueOnce(b)
    await loadSession()
    const savedA = JSON.stringify({ owner: 'A', items: [{ productId: 'shoe', productName: 'Shoe', variantId: 'aaaaaaaa-0000-0000-0000-000000000001', sku: 'SHOE', size: '40', color: 'Black', image: null, amount: 1200000, currency: 'VND', quantity: 2 }] })
    values.set('shoe-commerce:cart:v2:A', savedA)
    syncCartStorage({ key: 'shoe-commerce:cart:v2:A', newValue: savedA })
    const refresh = () => refreshSessionRoute({ name: 'cart', fullPath: '/cart', query: {}, meta: {} } as Parameters<typeof refreshSessionRoute>[0])
    if (order === 'storage-first') syncCartStorage({ key: 'shoe-commerce:cart:v2:A', newValue: null })
    await refresh() // Simulates receipt of the session BroadcastChannel message.
    if (order === 'session-first') syncCartStorage({ key: 'shoe-commerce:cart:v2:A', newValue: null })
    expect(session.account).toMatchObject({ accountId: 'B' })
    expect(cart.owner).toBe('B')
    expect(cart.items).toEqual([])
  })
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
  it('does not treat /auth/me as a barrier while login is pending', async () => {
    let resolveLogin!: (account: { accountId: string; login: string; roles: string[]; permissions: string[] }) => void
    vi.spyOn(api, 'login').mockImplementation(() => new Promise(resolve => { resolveLogin = resolve }))
    const me = vi.spyOn(api, 'me').mockResolvedValue({ accountId: 'old', login: 'old', roles: [], permissions: [] })
    const pending = signIn('new', 'password')
    await loadSession()
    expect(me).not.toHaveBeenCalled()
    expect(session.account).toBeUndefined()
    expect(session.loaded).toBe(false)
    resolveLogin({ accountId: 'new', login: 'new', roles: [], permissions: [] })
    await expect(pending).rejects.toThrow('Sign-in superseded')
    expect(session.account).toBeUndefined()
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
