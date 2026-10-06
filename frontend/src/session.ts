import { reactive } from 'vue'
import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { api, ApiError, type Account } from './api'
import { activateCart } from './cart'

export const SESSION_CHANGE_CHANNEL = 'shoe-commerce:session-change'
export const SESSION_CHANGE_SOURCE = crypto.randomUUID()
export const session = reactive<{ loaded: boolean; unavailable: boolean; generation: number; account?: Account }>({ loaded: false, unavailable: false, generation: 0 })

export function clearPrivateSession() {
  activateCart(null)
  session.account = undefined
  session.generation++
}

function notifySessionChange() {
  const channel = new BroadcastChannel(SESSION_CHANGE_CHANNEL)
  channel.postMessage({ source: SESSION_CHANGE_SOURCE })
  channel.close()
}

export async function loadSession() {
  const generation = session.generation
  session.unavailable = false
  try {
    const account = await api.me()
    if (generation === session.generation) {
      activateCart(account.accountId)
      session.account = account
    }
  }
  catch (error) {
    if (generation !== session.generation) return
    session.account = undefined
    session.unavailable = !(error instanceof ApiError && error.status === 401)
    activateCart(null)
  }
  finally { if (generation === session.generation) session.loaded = !session.unavailable }
}

export async function signIn(login: string, password: string) {
  const account = await api.login(login, password)
  session.generation++
  activateCart(account.accountId)
  session.account = account
  session.unavailable = false
  session.loaded = true
  notifySessionChange()
  return session.account
}

export async function signOut() {
  await api.logout()
  clearPrivateSession()
  notifySessionChange()
  return '/'
}

export function hasPermission(permission?: string) {
  return !permission || !!session.account?.permissions.includes(permission)
}

export function safeReturnTo(value: unknown, fallback = '/') {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : fallback
}

export function homeFor(account: Account) {
  if (account.permissions.includes('CATALOG_BROWSE')) return '/'
  if (account.permissions.includes('FULFILL_ORDER')) return '/operations/fulfillments'
  if (account.permissions.includes('POS_SELL')) return '/operations/pos'
  if (account.permissions.some(permission => ['STAFF_MANAGE_SCOPED', 'IDENTITY_MANAGE'].includes(permission))) return '/operations/people'
  if (account.permissions.includes('STOREFRONT_MANAGE')) return '/operations/storefront'
  return '/operations/reports'
}

export function loginDestination(value: unknown, account: Account) {
  return safeReturnTo(value, homeFor(account))
}

type SessionRoute = Pick<RouteLocationNormalized, 'name' | 'fullPath' | 'query' | 'meta'>

export function routeAccessDestination(
  to: SessionRoute,
  account: Account | undefined = session.account,
): RouteLocationRaw | undefined {
  if ((to.name === 'login' || to.name === 'register') && account) return loginDestination(to.query.returnTo, account)
  if (to.meta.requiresAuth && !account) return { path: '/login', query: { returnTo: to.fullPath } }
  const anyPermission = to.meta.permissions as string[] | undefined
  if (account && ((to.meta.permission && !account.permissions.includes(to.meta.permission as string))
    || anyPermission && !anyPermission.some(permission => account.permissions.includes(permission)))) return '/forbidden'
}

export async function refreshSessionRoute(to: SessionRoute) {
  clearPrivateSession()
  const generation = session.generation
  await loadSession()
  return session.unavailable || generation !== session.generation ? undefined : routeAccessDestination(to)
}
