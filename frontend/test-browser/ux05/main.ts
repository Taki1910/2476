import { createFixtureTransport, type Reply } from './transport'
import { orderFixture } from './fixtures'

async function start() {
  if (!import.meta.env.DEV) throw new Error('Harness is development-only')
  if ('serviceWorker' in navigator && (navigator.serviceWorker.controller || (await navigator.serviceWorker.getRegistrations()).length)) {
    throw new Error('Service worker present: use a clean origin/context. Harness refuses to mount.')
  }
  const diagnostic = document.createElement('pre')
  diagnostic.setAttribute('aria-label', 'Fixture diagnostics')
  document.querySelector('#controls')!.append(diagnostic)
  const instance = crypto.randomUUID()
  const trace = (event: string) => { diagnostic.textContent += `${event}\n` }
  trace(`INSTANCE ${instance} timeOrigin=${performance.timeOrigin}`)
  const transport = createFixtureTransport(location.origin, trace)
  window.fetch = transport.fetch
  // Real session/cart modules must not read or overwrite this origin's saved user data.
  for (const key of ['localStorage', 'sessionStorage'] as const) {
    const values = new Map<string, string>()
    const storage: Storage = {
      get length() { return values.size }, clear: () => values.clear(),
      key: index => [...values.keys()][index] ?? null,
      getItem: name => values.get(name) ?? null,
      setItem: (name, value) => { values.set(name, String(value)) },
      removeItem: name => { values.delete(name) },
    }
    Object.defineProperty(window, key, { configurable: true, value: storage })
  }
  // Imports happen only after the fixture-only transport is installed.
  const [{ createApp, h }, { createRouter, createMemoryHistory }, { default: App }, { default: OrderStatus }, { session }] = await Promise.all([
    import('vue'), import('vue-router'), import('../../src/App.vue'), import('../../src/views/OrderStatusView.vue'), import('../../src/session'),
  ])
  await import('../../src/styles.css')
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/orders/:id', component: OrderStatus },
    { path: '/:pathMatch(.*)*', component: { render: () => h('p', 'Fixture boundary: no real navigation or authentication.') } },
  ] })
  session.loaded = true; session.unavailable = false
  session.account = { accountId: 'fixture-owner', login: 'SYNTHETIC ONLY', roles: [], permissions: ['ORDER_PLACE'] }
  let id: 'a' | 'b' = 'a'
  let delivery = true
  const controls = document.querySelector('#controls')!
  const log = document.createElement('pre'); log.setAttribute('aria-label', 'Fixture request log')
  const original = transport.fetch
  window.fetch = async (input, init) => { try { return await original(input, init) } finally { log.textContent = transport.log.join('\n') } }
  function button(label: string, action: () => void | Promise<void>) {
    const element = document.createElement('button'); element.textContent = label; element.type = 'button'
    element.onclick = () => {
      trace(`ACTIVATE ${label} selected=${id} route=${router.currentRoute.value.fullPath} account=${session.account?.accountId} generation=${session.generation}`)
      Promise.resolve(action()).then(() => trace(`ACK ${label} selected=${id} delivery=${delivery} pending=${transport.pending}`)).catch(error => { log.textContent += `\n${error}` })
    }
    controls.append(element)
  }
  function queue(reply: Reply) { transport.enqueue(id, reply) }
  for (const status of [401, 403, 404]) button(`Queue ${status} for next refresh`, () => queue({ status, body: { code: 'FIXTURE_DENIED', message: 'Synthetic denied read' } }))
  button('Queue network failure', () => queue({ networkError: true }))
  button('Queue success', () => queue({ body: orderFixture(id, delivery) }))
  button('Queue prepared order', () => queue({ body: {
    ...orderFixture(id, delivery), status: 'PAID',
    pickupStatus: delivery ? 'READY_FOR_DISPATCH' : 'READY_FOR_PICKUP',
    paidAt: '2026-10-01T10:05:00Z', acceptedAt: '2026-10-01T10:10:00Z', readyAt: '2026-10-01T10:20:00Z',
  } }))
  button('Queue delayed success', () => queue({ body: orderFixture(id, delivery), hold: true }))
  button('Release delayed responses', () => transport.release())
  for (const next of ['a', 'b'] as const) button(`Show fixture ${next.toUpperCase()}`, async () => {
    id = next; transport.enqueue(id, { body: orderFixture(id, delivery) })
    if (router.currentRoute.value.params.id === id) session.generation++
    else await router.push(`/orders/${id}`)
  })
  button('Toggle pickup/delivery (next success)', () => { delivery = !delivery; log.textContent = `Next fixture: ${delivery ? 'DELIVERY' : 'PICKUP'}` })
  button('Switch synthetic account (403)', () => {
    queue({ status: 403, body: { code: 'FIXTURE_DENIED' } })
    session.account = { accountId: 'fixture-other', login: 'SYNTHETIC OTHER', roles: [], permissions: ['ORDER_PLACE'] }
    session.generation++
  })
  controls.append(log)
  document.querySelector('#guard')!.textContent = 'FIXTURES ONLY · native connections blocked by CSP · no real login/payment/order · queue response, then use the real Refresh status / Try again button'
  transport.enqueue('a', { body: orderFixture('a', delivery) })
  await router.push('/orders/a')
  createApp(App).use(router).mount('#app')
  window.addEventListener('resize', () => trace(`RESIZE ${innerWidth}x${innerHeight} instance=${instance} route=${router.currentRoute.value.fullPath} selected=${id} generation=${session.generation} pending=${transport.pending}`))
}
start().catch(error => { document.querySelector('#guard')!.textContent = `BLOCKED: ${error.message}` })
