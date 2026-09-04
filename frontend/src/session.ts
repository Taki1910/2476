import { reactive } from 'vue'
import { api, ApiError, type Account } from './api'

export const SESSION_CHANGE_CHANNEL = 'shoe-commerce:session-change'
export const SESSION_CHANGE_SOURCE = crypto.randomUUID()
export const session = reactive<{ loaded: boolean; unavailable: boolean; generation: number; account?: Account }>({ loaded: false, unavailable: false, generation: 0 })

export function clearPrivateSession() {
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
    if (generation === session.generation) session.account = account
  }
  catch (error) {
    if (generation !== session.generation) return
    session.account = undefined
    session.unavailable = !(error instanceof ApiError && error.status === 401)
  }
  finally { if (generation === session.generation) session.loaded = !session.unavailable }
}

export async function signIn(login: string, password: string) {
  const account = await api.login(login, password)
  clearPrivateSession()
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
  return '/operations/reports'
}

export function loginDestination(value: unknown, account: Account) {
  return safeReturnTo(value, homeFor(account))
}
