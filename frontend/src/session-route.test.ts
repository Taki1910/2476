import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, type Account } from './api'
import { refreshSessionRoute, routeAccessDestination } from './session'

const customer: Account = { accountId: 'customer', login: 'customer', roles: [], permissions: ['ORDER_PLACE'] }
const cashier: Account = { accountId: 'cashier', login: 'cashier', roles: [], permissions: ['POS_SELL'] }
const route = (path: string, meta = {}, name = 'route', query = {}) => ({ fullPath: path, meta, name, query })

describe('session route access', () => {
  afterEach(() => vi.restoreAllMocks())

  it('keeps guest-safe and currently authorized routes in place', () => {
    expect(routeAccessDestination(route('/products/one'), undefined)).toBeUndefined()
    expect(routeAccessDestination(route('/orders', { requiresAuth: true, permission: 'ORDER_PLACE' }), customer)).toBeUndefined()
  })

  it('sends a guest away from protected content with an exact return target', () => {
    expect(routeAccessDestination(route('/orders?page=2', { requiresAuth: true, permission: 'ORDER_PLACE' }), undefined))
      .toEqual({ path: '/login', query: { returnTo: '/orders?page=2' } })
  })

  it('sends an authenticated permission mismatch to forbidden', () => {
    expect(routeAccessDestination(route('/orders', { requiresAuth: true, permission: 'ORDER_PLACE' }), cashier)).toBe('/forbidden')
    expect(routeAccessDestination(route('/operations/people', { requiresAuth: true, permissions: ['STAFF_MANAGE_SCOPED', 'IDENTITY_MANAGE'] }), cashier)).toBe('/forbidden')
  })

  it('moves an authenticated account off login using its safe return target', () => {
    expect(routeAccessDestination(route('/login?returnTo=/orders', {}, 'login', { returnTo: '/orders' }), customer)).toBe('/orders')
  })

  it('refreshes cross-tab state while preserving safe and authorized routes', async () => {
    vi.spyOn(api, 'me').mockRejectedValueOnce(new ApiError(401, 'UNAUTHENTICATED', ''))
    await expect(refreshSessionRoute(route('/products/one'))).resolves.toBeUndefined()
    vi.mocked(api.me).mockResolvedValueOnce(customer)
    await expect(refreshSessionRoute(route('/orders', { requiresAuth: true, permission: 'ORDER_PLACE' }))).resolves.toBeUndefined()
  })

  it('refreshes cross-tab state before removing unauthorized content', async () => {
    vi.spyOn(api, 'me').mockRejectedValueOnce(new ApiError(401, 'UNAUTHENTICATED', ''))
    await expect(refreshSessionRoute(route('/orders?page=2', { requiresAuth: true, permission: 'ORDER_PLACE' })))
      .resolves.toEqual({ path: '/login', query: { returnTo: '/orders?page=2' } })
    vi.mocked(api.me).mockResolvedValueOnce(cashier)
    await expect(refreshSessionRoute(route('/orders', { requiresAuth: true, permission: 'ORDER_PLACE' }))).resolves.toBe('/forbidden')
  })

  it('does not let an older refresh redirect while the newest session check is pending', async () => {
    let rejectOld!: (reason: unknown) => void
    let resolveNew!: (account: Account) => void
    vi.spyOn(api, 'me')
      .mockImplementationOnce(() => new Promise((_, reject) => { rejectOld = reject }))
      .mockImplementationOnce(() => new Promise(resolve => { resolveNew = resolve }))
    const protectedRoute = route('/orders', { requiresAuth: true, permission: 'ORDER_PLACE' })

    const oldRefresh = refreshSessionRoute(protectedRoute)
    const newRefresh = refreshSessionRoute(protectedRoute)
    rejectOld(new ApiError(401, 'UNAUTHENTICATED', ''))

    await expect(oldRefresh).resolves.toBeUndefined()
    resolveNew(customer)
    await expect(newRefresh).resolves.toBeUndefined()
  })
})
