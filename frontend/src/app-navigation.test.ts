import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, expect, it } from 'vitest'
import App from './App.vue'
import { setLocale } from './i18n'
import { session } from './session'

afterEach(() => { session.account = undefined; setLocale('en') })

it('makes permitted staff destinations discoverable in one named operations menu', async () => {
  session.account = { accountId: 'staff', login: 'staff', roles: [], permissions: ['POS_SELL', 'INVENTORY_TRANSFER'] }
  setLocale('en')
  const router = createRouter({ history: createMemoryHistory(), routes: ['/', '/promotions', '/cart', '/operations/pos', '/operations/inventory/transfers']
    .map(path => ({ path, component: { template: '<p>Work</p>' } })) })
  await router.push('/operations/pos')
  await router.isReady()

  const html = await renderToString(createSSRApp(App).use(router))

  expect(html).toMatch(/<details[^>]*class="[^"]*operations-menu[^"]*"[^>]*>\s*<summary>Operations<\/summary>\s*<nav aria-label="Operations">/)
  expect(html).toContain('href="/operations/pos"')
  expect(html).toContain('href="/operations/inventory/transfers"')
  expect(html).not.toContain('href="/operations/reports"')
})
