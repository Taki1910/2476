import { computed, reactive } from 'vue'
import { normalizeCartDemand, type CartDemand, type CartQuote } from './api'
import { checkoutLocked, invalidateRejectedCheckout } from './checkout'

export const MAX_CART_QUANTITY = 10
export const MAX_CART_LINES = 50
const STORAGE_KEY = 'shoe-commerce:cart'

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

function storedCart() {
  try { return restoreCart(localStorage.getItem(STORAGE_KEY)) }
  catch { return [] }
}
export const cart = reactive({ items: storedCart(), storageError: false })
// Navigation badge counts total units, not distinct variants.
export const cartCount = computed(() => cart.items.reduce((total, item) => total + item.quantity, 0))

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(cart.items))
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

export function removePurchasedItems(items: CartDemand[]) {
  // A different signed-in account may have edited the shared, non-authoritative
  // cart while this account's checkout was unresolved. Remove only bought units.
  cart.items = cart.items.flatMap(item => {
    const quantity = item.quantity - (items.find(line => line.variantId === item.variantId)?.quantity ?? 0)
    return quantity > 0 ? [{ ...item, quantity }] : []
  })
  persist()
}

export function changedQuoteItems(quote: CartQuote, items: CartItem[]) {
  return quote.items.filter(line => items.find(item => item.variantId === line.variantId)?.amount !== line.unitPriceAmount)
}
