import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { activateCart, addToCart, cart, cartCount, changedQuoteItems, removeFromCart, removePurchasedItems, restoreCart, setCartQuantity, syncCartStorage } from './cart'
import { normalizeCartDemand, type CartQuote } from './api'
import { session } from './session'

const A = 'aaaaaaaa-0000-0000-0000-000000000001'
const B = 'bbbbbbbb-0000-0000-0000-000000000002'
const item = { productId: 'product-1', productName: 'Test Runner', variantId: A, sku: 'RUN-42', size: '42', color: 'Black', image: null, amount: 120000, currency: 'VND' as const }
let values: Map<string, string>
beforeEach(() => {
  values = new Map()
  vi.stubGlobal('localStorage', { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key) })
  vi.stubGlobal('navigator', { locks: { request: async (_name: string, callback: () => Promise<void>) => callback() } })
  session.account = undefined
  cart.items = []
  cart.owner = null
  cart.storageError = false
})
afterEach(() => vi.unstubAllGlobals())

describe('multi-item display cart', () => {
  it('isolates logout, another account and refresh without resurrecting unowned legacy data', () => {
    values.set('shoe-commerce:cart', JSON.stringify([{ ...item, quantity: 3 }]))
    activateCart(null)
    expect(cart.items).toEqual([])
    activateCart('A'); addToCart(item)
    activateCart(null)
    expect(cart.items).toEqual([])
    expect(values.has('shoe-commerce:cart:v2:A')).toBe(true)
    activateCart('B')
    expect(cartCount.value).toBe(0)
    cart.owner = null; cart.items = []
    activateCart('B')
    expect(cartCount.value).toBe(0)
    activateCart('A')
    expect(cartCount.value).toBe(1)
  })
  it('merges guest quantities deterministically and consumes guest once', async () => {
    values.set('shoe-commerce:cart:v2:A', JSON.stringify({ owner: 'A', items: [{ ...item, quantity: 9 }] }))
    addToCart(item); addToCart(item)
    await activateCart('A')
    expect(cart.items[0].quantity).toBe(10)
    expect(values.has('shoe-commerce:cart:v2:guest')).toBe(false)
    cart.owner = null; cart.items = []
    await activateCart('A')
    expect(cart.items[0].quantity).toBe(10)
  })
  it('does not let a late checkout response remove another owner’s units', () => {
    activateCart('B'); addToCart(item)
    removePurchasedItems([{ variantId: A, quantity: 1 }], 'A')
    expect(cartCount.value).toBe(1)
  })
  it('synchronizes only the active owner cart from another tab', () => {
    activateCart('A')
    const saved = JSON.stringify({ owner: 'A', items: [{ ...item, quantity: 2 }] })
    syncCartStorage({ key: 'shoe-commerce:cart:v2:B', newValue: saved })
    expect(cart.items).toEqual([])
    syncCartStorage({ key: 'shoe-commerce:cart:v2:A', newValue: saved })
    expect(cart.items).toEqual([{ ...item, quantity: 2 }])
    syncCartStorage({ key: 'shoe-commerce:cart:v2:A', newValue: null })
    expect(cart.items).toEqual([])
  })
  it('does not merge twice when another tab still holds the consumed guest cart in memory', async () => {
    addToCart(item); setCartQuantity(A, 2)
    const staleGuest = cart.items.map(line => ({ ...line })) // Tab B before Tab A signs in.
    await activateCart('A') // Tab A consumes shared guest storage.
    expect(cart.items[0].quantity).toBe(2)
    cart.owner = null; cart.items = staleGuest // Tab B has not handled storage/session events yet.
    await activateCart('A')
    expect(cart.items[0].quantity).toBe(2)
  })
  it('keeps guest data for retry when the account write fails', async () => {
    addToCart(item)
    const savedGuest = values.get('shoe-commerce:cart:v2:guest')
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => { if (key.endsWith(':A')) throw new Error('quota'); values.set(key, value) },
      removeItem: (key: string) => values.delete(key),
    })
    await activateCart('A')
    expect(cart.storageError).toBe(true)
    expect(values.get('shoe-commerce:cart:v2:guest')).toBe(savedGuest)
    cart.owner = null
    vi.stubGlobal('localStorage', { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key) })
    await activateCart('A')
    expect(cart.items[0].quantity).toBe(1)
    expect(values.has('shoe-commerce:cart:v2:guest')).toBe(false)
  })
  it('does not consume guest storage when Web Locks is unavailable', async () => {
    addToCart(item)
    vi.stubGlobal('navigator', {})
    await activateCart('A')
    expect(cart.storageError).toBe(true)
    expect(values.has('shoe-commerce:cart:v2:guest')).toBe(true)
    expect(cart.items).toEqual([])
  })
  it('retries a failed guest removal without adding the same quantity again', async () => {
    addToCart(item)
    let failRemoval = true
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => { if (key.endsWith(':guest') && failRemoval) throw new Error('blocked'); values.delete(key) },
    })
    await activateCart('A')
    expect(cart.storageError).toBe(true)
    expect(cart.items[0].quantity).toBe(1)
    expect(values.has('shoe-commerce:cart:v2:guest')).toBe(true)
    failRemoval = false
    cart.owner = null
    await activateCart('A')
    expect(cart.items[0].quantity).toBe(1)
    expect(values.has('shoe-commerce:cart:v2:guest')).toBe(false)
  })
  it('isolates A to B to A and ignores delayed storage events for an inactive owner', () => {
    activateCart('A'); addToCart(item); setCartQuantity(A, 3)
    const savedA = values.get('shoe-commerce:cart:v2:A')!
    activateCart(null)
    activateCart('B'); addToCart({ ...item, variantId: B })
    syncCartStorage({ key: 'shoe-commerce:cart:v2:A', newValue: savedA })
    expect(cart.owner).toBe('B')
    expect(cart.items.map(line => line.variantId)).toEqual([B])
    activateCart(null)
    activateCart('A')
    syncCartStorage({ key: 'shoe-commerce:cart:v2:B', newValue: values.get('shoe-commerce:cart:v2:B')! })
    expect(cart.items.map(({ variantId, quantity }) => ({ variantId, quantity }))).toEqual([{ variantId: A, quantity: 3 }])
  })
  it('adds different variants and merges duplicates; badge counts total units', () => {
    expect(addToCart(item)).toBe('added')
    expect(addToCart({ ...item, variantId: B })).toBe('added')
    expect(addToCart(item)).toBe('added')
    expect(cart.items).toHaveLength(2)
    expect(cart.items[0].quantity).toBe(2)
    expect(cartCount.value).toBe(3)
  })
  it('edits and removes only the selected line and persists an array', () => {
    addToCart(item); addToCart({ ...item, variantId: B })
    setCartQuantity(A, 4)
    removeFromCart(B)
    expect(cart.items).toEqual([{ ...item, quantity: 4 }])
    expect(JSON.parse(values.get('shoe-commerce:cart:v2:guest')!).items).toEqual(cart.items)
  })
  it('rejects invalid quantities without silently changing demand', () => {
    addToCart(item)
    for (const quantity of [0, 11, 1.5, NaN, Infinity]) setCartQuantity(A, quantity)
    expect(cartCount.value).toBe(1)
    setCartQuantity(A, 10)
    expect(addToCart(item)).toBe('max-quantity')
    expect(cartCount.value).toBe(10)
  })
  it('bounds distinct variants to 50 while allowing an existing line to grow', () => {
    for (let i = 0; i < 50; i++) addToCart({ ...item, variantId: 'aaaaaaaa-0000-0000-0000-' + String(i).padStart(12, '0') })
    expect(addToCart({ ...item, variantId: B })).toBe('max-lines')
    expect(addToCart(item)).toBe('added')
    expect(cartCount.value).toBe(51)
  })
  it('migrates the old single-item storage without dropping metadata', () => {
    expect(restoreCart(JSON.stringify({ ...item, quantity: 3 }))).toEqual([{ ...item, quantity: 3 }])
  })
  it('normalizes duplicate stored lines and rejects corrupt quantities/metadata', () => {
    expect(restoreCart(JSON.stringify([{ ...item, quantity: 2 }, { ...item, quantity: 3 }]))[0].quantity).toBe(5)
    for (const raw of ['{bad', 'null', JSON.stringify({ ...item, quantity: 11 }), JSON.stringify({ variantId: A, quantity: 1 })]) expect(restoreCart(raw)).toEqual([])
  })
  it('preserves unrelated cart units when resolving an earlier command', () => {
    addToCart(item); setCartQuantity(A, 4); addToCart({ ...item, variantId: B })
    removePurchasedItems([{ variantId: A, quantity: 2 }])
    expect(cartCount.value).toBe(3)
    expect(cart.items.find(line => line.variantId === B)).toBeDefined()
  })
  it('reports storage failure without silently losing the in-memory cart', () => {
    vi.stubGlobal('localStorage', { setItem: () => { throw new Error('quota') } })
    addToCart(item)
    expect(cart.storageError).toBe(true)
    expect(cartCount.value).toBe(1)
  })
  it('detects every changed server price without overwriting cached metadata', () => {
    addToCart(item); addToCart({ ...item, variantId: B })
    const quote = { items: [{ variantId: A, unitPriceAmount: 125000 }, { variantId: B, unitPriceAmount: 130000 }] } as CartQuote
    expect(changedQuoteItems(quote, cart.items).map(line => line.variantId)).toEqual([A, B])
    expect(cart.items.every(line => line.amount === 120000)).toBe(true)
  })
})

describe('canonical demand', () => {
  it('merges and sorts UUID strings without any price/owner metadata', () => {
    const a = normalizeCartDemand([{ ...item, variantId: B, quantity: 2 }, { variantId: A.toUpperCase(), quantity: 1 }, { variantId: A, quantity: 2 }])
    expect(a).toEqual([{ variantId: A, quantity: 3 }, { variantId: B, quantity: 2 }])
    expect(normalizeCartDemand([...a].reverse())).toEqual(a)
  })
  it('rejects empty, oversized, malformed, or merged-over-limit demands', () => {
    for (const items of [[], Array(51).fill({ variantId: A, quantity: 1 }), [{ variantId: 'not-a-uuid', quantity: 1 }], [{ variantId: A, quantity: 9 }, { variantId: A, quantity: 2 }]]) expect(() => normalizeCartDemand(items)).toThrow()
  })
})
