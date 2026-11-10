import { beforeAll, describe, expect, it, vi } from 'vitest'
import { storefrontScrollBehavior } from './navigation'
import type { RouterOptions } from 'vue-router'

const mocks = vi.hoisted(() => {
  const beforeEach = vi.fn()
  return {
    beforeEach,
    createRouter: vi.fn((_options: RouterOptions) => ({ beforeEach })),
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
  it('loads every page on demand and resolves a renderable Vue component', async () => {
    const routes = mocks.createRouter.mock.calls[0]![0].routes
    const pages = routes.filter(route => 'component' in route && route.component)
    expect(pages).toHaveLength(24)
    await Promise.all(pages.map(async route => {
      expect(typeof route.component, route.path).toBe('function')
      const page = await (route.component as () => Promise<{ default: { ssrRender: unknown } }>)()
      expect(typeof page.default.ssrRender, route.path).toBe('function')
    }))
  })

  it('uses the tested storefront scroll behavior', () => {
    expect(mocks.createRouter.mock.calls[0]?.[0].scrollBehavior).toBe(storefrontScrollBehavior)
  })
})
