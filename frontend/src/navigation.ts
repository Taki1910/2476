import type { RouterScrollBehavior } from 'vue-router'

let catalogTicket = 0
let catalogReady = Promise.resolve()
let releaseCatalog = () => {}

export function prepareCatalogScrollRestore() {
  catalogTicket++
  catalogReady = new Promise<void>(resolve => { releaseCatalog = resolve })
}

export function catalogScrollTicket() { return catalogTicket }

export function completeCatalogScrollRestore(ticket: number) {
  if (ticket === catalogTicket) releaseCatalog()
}

export const storefrontScrollBehavior: RouterScrollBehavior = (to, from, savedPosition) => {
  if (to.name === 'catalog' && from.name === 'catalog' && to.query.view === 'all'
    && from.query.view !== 'all' && to.query.q === from.query.q) return false
  if (savedPosition && to.name === 'catalog') return catalogReady.then(() => savedPosition)
  return savedPosition ?? (to.hash ? { el: to.hash, top: 16 } : { top: 0 })
}
