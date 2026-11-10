export type Reply = { status?: number; body?: unknown; hold?: boolean; networkError?: boolean }

// No native fetch reference or passthrough: every application request is scripted.
export function createFixtureTransport(origin: string, observe: (event: string) => void = () => {}) {
  const queues = new Map<string, Reply[]>()
  const held: Array<{ requestId: number; resolve: () => void }> = []
  let sequence = 0
  const log: string[] = []
  const fetch: typeof globalThis.fetch = async (input, init) => {
    const request = input instanceof Request ? input : undefined
    const url = new URL(request?.url ?? String(input), origin)
    const method = (init?.method ?? request?.method ?? 'GET').toUpperCase()
    const id = /^\/api\/v1\/orders\/(a|b)$/.exec(url.pathname)?.[1]
    const requestId = ++sequence
    observe(`START #${requestId} ${method} ${url.pathname}`)
    log.push(`${method} ${url.href}`)
    if (url.origin !== origin || url.search || url.hash || method !== 'GET' || !id) throw new Error('Blocked fixture request')
    const reply = queues.get(id)?.shift()
    if (!reply) throw new Error('Unscripted fixture request')
    if (reply.hold) await new Promise<void>(resolve => {
      held.push({ requestId, resolve }); observe(`HOLD #${requestId} pending=${held.map(item => item.requestId).join(',')}`)
    })
    observe(`SETTLE #${requestId} ${reply.networkError ? 'NETWORK_ERROR' : reply.status ?? 200}`)
    if (reply.networkError) throw new TypeError('Fixture network failure')
    return Response.json(reply.body ?? {}, { status: reply.status ?? 200 })
  }
  return {
    fetch, log,
    enqueue(id: 'a' | 'b', reply: Reply) { queues.set(id, [...(queues.get(id) ?? []), reply]) },
    release() {
      const released = held.splice(0)
      observe(`RELEASE ids=${released.map(item => item.requestId).join(',')} pending=0`)
      released.forEach(item => item.resolve())
    },
    get pending() { return held.length },
  }
}
