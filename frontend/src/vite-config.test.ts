import { afterEach, describe, expect, it, vi } from 'vitest'
import config from '../vite.config'

async function apiTarget() {
  if (typeof config !== 'function') throw new Error('Expected environment-aware Vite config')
  const resolved = await config({ command: 'serve', mode: 'test' })
  return resolved.server?.proxy?.['/api']
}

afterEach(() => vi.unstubAllEnvs())

describe('local demo API proxy', () => {
  it('keeps the ordinary backend as the default', async () => {
    vi.stubEnv('DEMO_API_TARGET', undefined)
    expect(await apiTarget()).toBe('http://localhost:8080')
  })

  it('uses the isolated demo backend when explicitly configured', async () => {
    vi.stubEnv('DEMO_API_TARGET', 'http://127.0.0.1:8081')
    expect(await apiTarget()).toBe('http://127.0.0.1:8081')
  })
})
