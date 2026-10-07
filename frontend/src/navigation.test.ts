import { describe, expect, it } from 'vitest'
import {
  catalogScrollTicket, completeCatalogScrollRestore, prepareCatalogScrollRestore,
  storefrontScrollBehavior,
} from './navigation'

describe('storefront scroll restoration', () => {
  const route = (hash = '') => ({ hash }) as never

  it('restores browser history, honors hashes, and resets new navigation', () => {
    expect(storefrontScrollBehavior(route(), route(), { left: 12, top: 480 })).toEqual({ left: 12, top: 480 })
    expect(storefrontScrollBehavior(route('#product-search'), route(), null)).toEqual({ el: '#product-search', top: 16 })
    expect(storefrontScrollBehavior(route(), route(), null)).toEqual({ top: 0 })
  })

  it('waits for the async catalog to render before restoring history position', async () => {
    prepareCatalogScrollRestore()
    const ticket = catalogScrollTicket()
    const pending = storefrontScrollBehavior({ name: 'catalog', hash: '' } as never, route(), { left: 0, top: 480 })
    let settled = false
    Promise.resolve(pending).then(() => { settled = true })
    await Promise.resolve()
    expect(settled).toBe(false)

    completeCatalogScrollRestore(ticket)

    await expect(pending).resolves.toEqual({ left: 0, top: 480 })
  })
})
