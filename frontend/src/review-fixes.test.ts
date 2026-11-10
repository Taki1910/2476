import { describe, expect, it } from 'vitest'
import appSource from './App.vue?raw'
import catalogSource from './views/CatalogView.vue?raw'
import productSource from './views/ProductView.vue?raw'
import posSource from './views/PosView.vue?raw'
import promotionsSource from './views/PromotionsView.vue?raw'
import shippingSource from './views/ShippingRulesView.vue?raw'
import storefrontSource from './views/StorefrontManagementView.vue?raw'

describe('safe frontend review fixes', () => {
  it('subscribes and unsubscribes the active cart to cross-tab storage changes', () => {
    expect(appSource).toContain("window.addEventListener('storage', syncCartStorage)")
    expect(appSource).toContain("window.removeEventListener('storage', syncCartStorage)")
  })
  it.each([
    ['POS', posSource],
    ['storefront management', storefrontSource],
    ['shipping rules', shippingSource],
    ['promotion management', promotionsSource],
  ])('uses the accessible dialog pattern for %s confirmation', (_name, source) => {
    expect(source).not.toMatch(/window\.confirm|[^\w.]confirm\(/)
    expect(source).toContain('<dialog')
    expect(source).toContain('showModal()')
    expect(source).toContain('aria-labelledby')
    expect(source).toContain('aria-describedby')
    expect(source).toContain('@cancel=')
  })

  it('keeps the featured catalog lead out of sparse results', () => {
    expect(catalogSource).toContain('!hasPublished && visibleProducts.length >= 3')
  })

  it('keeps storefront navigation and product selection customer-facing', () => {
    expect(appSource).not.toContain('to="/#product-search"')
    expect(productSource).not.toContain('SKU {{ selected.sku }}')
    expect(productSource).toContain('class="color-option"')
    expect(productSource).toContain('class="variant-option size-option"')
  })

  it('acknowledges cart changes without replacing the reactive unit count', () => {
    expect(appSource).toContain('watch(cartCount')
    expect(appSource).toContain("class=\"cart-count\"")
    expect(appSource).toContain('acknowledged: cartAcknowledged')
  })

  it('keeps confirmation actions behind explicit dialog buttons', () => {
    expect(posSource).toContain('@click="askSale"')
    expect(posSource).toContain('@click="sell"')
    expect(storefrontSource).toContain('@click="confirmAction"')
    expect(shippingSource).toContain('@click="confirmTransition"')
    expect(promotionsSource).toContain('@click="confirmTransition"')
  })

  it('uses a cashier-facing POS sale status instead of candidate terminology', () => {
    expect(posSource).toContain("t('Sale status')")
    expect(posSource).not.toContain("t('Candidate state')")
  })

  it.each([
    ['shipping rules', shippingSource],
    ['promotion management', promotionsSource],
  ])('confirms retirement after the conditional dialog is mounted for %s', (_name, source) => {
    expect(source).toContain("action==='retire'")
    expect(source).toContain('await nextTick()')
    expect(source).toMatch(/await nextTick\(\)[\s\S]*showModal\(\)/)
  })
})
