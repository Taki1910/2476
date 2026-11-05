import { describe, expect, it } from 'vitest'
import { createFixtureTransport } from './transport'

describe('UX05 strict fixture transport', () => {
  it('acknowledges the exact held request without creating a new request on release', async () => {
    const events: string[] = []
    const fixture = createFixtureTransport('http://localhost:5173', event => events.push(event))
    fixture.enqueue('a', { hold: true, body: { id: 'a' } })
    const pending = fixture.fetch('/api/v1/orders/a')
    expect(fixture.pending).toBe(1)
    expect(events).toEqual(['START #1 GET /api/v1/orders/a', 'HOLD #1 pending=1'])
    fixture.release()
    await pending
    expect(fixture.pending).toBe(0)
    expect(events.slice(2)).toEqual(['RELEASE ids=1 pending=0', 'SETTLE #1 200'])
  })
  it('returns only explicitly queued responses and fails closed otherwise', async () => {
    const fixture = createFixtureTransport('http://localhost:5173')
    fixture.enqueue('a', { status: 200, body: { id: 'a' } })
    expect(await (await fixture.fetch('/api/v1/orders/a')).json()).toEqual({ id: 'a' })
    await expect(fixture.fetch('/api/v1/orders/a')).rejects.toThrow('Unscripted')
    for (const url of ['/api/v1/auth/me', '/api/v1/orders/unknown', 'http://example.com/api/v1/orders/a']) {
      await expect(fixture.fetch(url)).rejects.toThrow('Blocked')
    }
    fixture.enqueue('a', { status: 200, body: {} })
    await expect(fixture.fetch('/api/v1/orders/a', { method: 'POST' })).rejects.toThrow('Blocked')
    expect((await fixture.fetch('/api/v1/orders/a')).status).toBe(200)
  })
  it('can release old A after B and model denied reads and network failure', async () => {
    const fixture = createFixtureTransport('http://localhost:5173')
    fixture.enqueue('a', { status: 200, body: { id: 'a' }, hold: true })
    let settled = false
    const old = fixture.fetch('/api/v1/orders/a').then(result => { settled = true; return result })
    fixture.enqueue('b', { status: 200, body: { id: 'b' } })
    expect(await (await fixture.fetch('/api/v1/orders/b')).json()).toEqual({ id: 'b' })
    expect(settled).toBe(false)
    fixture.release()
    expect(await (await old).json()).toEqual({ id: 'a' })
    for (const status of [401, 403, 404]) {
      fixture.enqueue('a', { status, body: { message: 'Fixture denial' } })
      expect((await fixture.fetch('/api/v1/orders/a')).status).toBe(status)
    }
    fixture.enqueue('b', { networkError: true })
    await expect(fixture.fetch('/api/v1/orders/b')).rejects.toThrow('Fixture network failure')
  })
})
