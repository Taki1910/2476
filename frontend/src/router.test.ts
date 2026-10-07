import { beforeAll, describe, expect, it, vi } from 'vitest'
import { storefrontScrollBehavior } from './navigation'

const mocks = vi.hoisted(() => {
  const beforeEach = vi.fn()
  return {
    beforeEach,
    createRouter: vi.fn((_options: { scrollBehavior?: unknown }) => ({ beforeEach })),
    createWebHistory: vi.fn(),
    mount: vi.fn(),
  }
})

vi.mock('vue', async importOriginal => ({
  ...await importOriginal<typeof import('vue')>(),
  createApp: () => {
    const app = { use: () => app, mount: mocks.mount }
    return app
  },
}))
vi.mock('vue-router', async importOriginal => ({
  ...await importOriginal<typeof import('vue-router')>(),
  createRouter: mocks.createRouter,
  createWebHistory: mocks.createWebHistory,
}))

beforeAll(async () => { await import('./main') })

describe('application router', () => {
  it('uses the tested storefront scroll behavior', () => {
    expect(mocks.createRouter.mock.calls[0]?.[0].scrollBehavior).toBe(storefrontScrollBehavior)
  })
})
