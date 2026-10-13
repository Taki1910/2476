import {describe,expect,it} from 'vitest'
import app from './App.vue?raw'
import promotions from './views/PromotionsView.vue?raw'

describe('C2 promotion administration contract',()=>{
  it('shows administration only through PROMOTION_MANAGE',()=>{
    expect(app).toContain("permissions.includes('PROMOTION_MANAGE')")
    expect(app).not.toMatch(/roles\.includes\([^)]*OPERATIONS/)
  })
  it('allows draft edits while published rows only expose lifecycle actions',()=>{
    expect(promotions).toMatch(/v-if="r\.status==='DRAFT'"[^>]*@click="edit\(r\)"/)
    expect(promotions).toContain('await api.editPromotion(editingId.value,draft)')
    expect(promotions).toMatch(/v-if="r\.status==='PUBLISHED'"[^>]*@click="revise\(r\)"/)
    expect(promotions).not.toMatch(/v-if="r\.status==='PUBLISHED'"[^>]*@click="edit\(r\)"/)
  })
  it('contains no customer claiming or wallet controls',()=>{
    expect(promotions).not.toMatch(/claim voucher|wallet/i)
  })
})
