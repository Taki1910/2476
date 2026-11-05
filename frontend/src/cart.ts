import { computed, reactive } from 'vue'
import { normalizeCartDemand, type CartDemand, type CartQuote } from './api'
import { checkoutLocked, invalidateRejectedCheckout } from './checkout'

export const MAX_CART_QUANTITY = 10
export const MAX_CART_LINES = 50
const storageKey = (owner: string | null) => `shoe-commerce:cart:v2:${owner ?? 'guest'}`

// Display metadata only. Cached amounts are never sent as checkout authority.
export type CartItem = {
  productId: string; productName: string; variantId: string; sku: string; size: string
  color: string; image: string | null; amount: number; currency: 'VND'; quantity: number
}

export function restoreCart(raw: string | null): CartItem[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    const lines: CartItem[] = Array.isArray(parsed) ? parsed : [parsed] // Phase 15A migration.
    if (lines.length > MAX_CART_LINES) return []
    const valid = lines.filter(item => item && ['productId', 'productName', 'variantId', 'sku', 'size', 'color'].every(key => typeof item[key as keyof CartItem] === 'string')
      && (item.image === null || typeof item.image === 'string') && item.currency === 'VND'
      && Number.isSafeInteger(item.amount) && item.amount >= 0)
    if (!valid.length) return []
    return normalizeCartDemand(valid).map(demand => ({ ...valid.find(line => line.variantId.toLowerCase() === demand.variantId)!, ...demand }))
  } catch { return [] }
}

function storedCart(owner: string | null) {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey(owner)) ?? 'null')
    return saved?.owner === owner ? restoreCart(JSON.stringify(saved.items)) : []
  }
  catch { return [] }
}
export const cart = reactive<{ items: CartItem[]; owner: string | null; storageError: boolean }>({ items: [], owner: null, storageError: false })

export async function activateCart(owner: string | null, consumeGuest = true): Promise<void> {
  const previousOwner = cart.owner
  const items = storedCart(owner)
  cart.owner = owner
  cart.items = items
  try {
    localStorage.removeItem('shoe-commerce:cart') // Unowned legacy data cannot be attributed safely.
  } catch { cart.storageError = true }
  if (owner === null || previousOwner !== null || !consumeGuest) return
  if (!navigator.locks?.request) { cart.storageError = true; return }
  await navigator.locks.request('shoe-commerce:guest-cart-merge', async () => {
    try {
      if (cart.owner !== owner) return
      const guestRaw = localStorage.getItem(storageKey(null))
      if (!guestRaw) return
      const guest = storedCart(null)
      if (!guest.length) return
      const accountRaw = localStorage.getItem(storageKey(owner))
      const saved = accountRaw ? JSON.parse(accountRaw) : null
      if (saved?.mergedGuest !== guestRaw) {
        const merged = storedCart(owner)
        for (const line of guest) {
          const existing = merged.find(item => item.variantId === line.variantId)
          if (existing) existing.quantity = Math.min(MAX_CART_QUANTITY, existing.quantity + line.quantity)
          else if (merged.length < MAX_CART_LINES) merged.push({ ...line })
        }
        localStorage.setItem(storageKey(owner), JSON.stringify({ owner, items: merged, mergedGuest: guestRaw }))
        if (cart.owner === owner) cart.items = merged
      }
      localStorage.removeItem(storageKey(null))
      cart.storageError = false
    } catch { cart.storageError = true }
  }).catch(() => { cart.storageError = true })
}
// Navigation badge counts total units, not distinct variants.
export const cartCount = computed(() => cart.items.reduce((total, item) => total + item.quantity, 0))

function persist() {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey(cart.owner)) ?? 'null')
    localStorage.setItem(storageKey(cart.owner), JSON.stringify({ owner: cart.owner, items: cart.items, ...(cart.owner === null ? { revision: crypto.randomUUID() } : saved?.owner === cart.owner && saved.mergedGuest ? { mergedGuest: saved.mergedGuest } : {}) }))
    cart.storageError = false
  } catch { cart.storageError = true }
}

export function syncCartStorage(event: Pick<StorageEvent, 'key' | 'newValue'>) {
  if (event.key !== storageKey(cart.owner)) return
  if (event.newValue === null) {
    cart.items = []
    cart.storageError = false
    return
  }
  try {
    const saved = JSON.parse(event.newValue)
    if (saved?.owner !== cart.owner || !Array.isArray(saved.items)) throw new Error()
    const items = restoreCart(JSON.stringify(saved.items))
    if (saved.items.length && !items.length) throw new Error()
    cart.items = items
    cart.storageError = false
  } catch { cart.storageError = true }
}

export function addToCart(item: Omit<CartItem, 'quantity'>): 'added' | 'max-quantity' | 'max-lines' | 'checkout-pending' {
  if (checkoutLocked.value) return 'checkout-pending'
  const variantId = item.variantId.toLowerCase()
  const existing = cart.items.find(line => line.variantId === variantId)
  if (existing?.quantity === MAX_CART_QUANTITY) return 'max-quantity'
  if (!existing && cart.items.length === MAX_CART_LINES) return 'max-lines'
  invalidateRejectedCheckout()
  if (existing) existing.quantity++
  else cart.items.push({ ...item, variantId, quantity: 1 })
  persist()
  return 'added'
}

export function setCartQuantity(variantId: string, quantity: number) {
  if (checkoutLocked.value || !Number.isInteger(quantity) || quantity < 1 || quantity > MAX_CART_QUANTITY) return
  const item = cart.items.find(line => line.variantId === variantId)
  if (!item || item.quantity === quantity) return
  invalidateRejectedCheckout()
  item.quantity = quantity
  persist()
}

export function removeFromCart(variantId: string) {
  if (checkoutLocked.value) return
  invalidateRejectedCheckout()
  cart.items = cart.items.filter(line => line.variantId !== variantId)
  persist()
}

export function removePurchasedItems(items: CartDemand[], owner = cart.owner) {
  if (owner !== cart.owner) return
  cart.items = cart.items.flatMap(item => {
    const quantity = item.quantity - (items.find(line => line.variantId === item.variantId)?.quantity ?? 0)
    return quantity > 0 ? [{ ...item, quantity }] : []
  })
  persist()
}

export function changedQuoteItems(quote: CartQuote, items: CartItem[]) {
  return quote.items.filter(line => items.find(item => item.variantId === line.variantId)?.amount !== line.unitPriceAmount)
}
