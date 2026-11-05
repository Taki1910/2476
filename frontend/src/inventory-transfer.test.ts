import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'
import appSource from './App.vue?raw'
import i18nSource from './i18n.ts?raw'
import mainSource from './main.ts?raw'
import viewSource from './views/InventoryTransfersView.vue?raw'

describe('inventory transfer workflow', () => {
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

  it('sends each mutation with CSRF and caller-owned idempotency identity', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
      .mockImplementation(async () => new Response(JSON.stringify({ transferId: 'transfer-1', status: 'DRAFT', items: [] })))
    vi.stubGlobal('fetch', fetchMock)

    const command = { sourceLocationId: 'source-1', destinationLocationId: 'destination-1', items: [{ variantId: 'variant-1', quantity: 2 }] }
    await api.createInventoryTransfer(command, 'create-key')
    await api.dispatchInventoryTransfer('transfer/1', 'dispatch-key')
    await api.receiveInventoryTransfer('transfer/1', 'receive-key')

    const mutations = fetchMock.mock.calls.filter(call => call[0] !== '/api/v1/auth/csrf')
    expect(mutations.map(call => call[0])).toEqual([
      '/api/v1/operations/inventory/transfers',
      '/api/v1/operations/inventory/transfers/transfer%2F1/dispatch',
      '/api/v1/operations/inventory/transfers/transfer%2F1/receive',
    ])
    expect(mutations.map(call => call[1].headers['Idempotency-Key']))
      .toEqual(['create-key', 'dispatch-key', 'receive-key'])
    expect(JSON.parse(mutations[0][1].body)).toEqual(command)
  })

  it('exposes one permission-gated DRAFT to DISPATCHED to RECEIVED workflow', () => {
    expect(mainSource).toContain("permission: 'INVENTORY_TRANSFER'")
    expect(appSource).toContain("permissions.includes('INVENTORY_TRANSFER')")
    expect(viewSource).toContain('DRAFT')
    expect(viewSource).toContain('DISPATCHED')
    expect(viewSource).toContain("submitRetryableCommand(createCommand")
    expect(viewSource).toContain("submitRetryableCommand(dispatchCommand")
    expect(viewSource).toContain("submitRetryableCommand(receiveCommand")
    expect(viewSource).toContain('line.dispatchedQuantity')
    expect(viewSource).toContain('line.receivedQuantity')
    expect(viewSource).toContain('formatDateTime(transfer.createdAt)')
    expect(viewSource).toContain('formatDateTime(transfer.dispatchedAt)')
    expect(viewSource).toContain('formatDateTime(transfer.receivedAt)')
    expect(viewSource).toContain("'Dispatched / In transit'")
    expect(i18nSource).toContain("'Dispatched / In transit': 'Đã xuất kho / Đang vận chuyển'")
  })

})
