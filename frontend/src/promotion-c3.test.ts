import {describe,expect,it} from 'vitest'
import api from './api.ts?raw'
import promotions from './views/PromotionsView.vue?raw'

describe('C3 promotion administration contract',()=>{
  it('sends replacement evidence and keeps family identity immutable',()=>{
    expect(api).toContain('expectedPublishedRevisionId')
    expect(promotions).toContain(':disabled="!!editor.familyId"')
    expect(promotions).toContain('pending.current?.id')
  })
  it('explains discovery and excludes unsupported voucher effects',()=>{
    expect(promotions).toContain('Code offers never appear in generic public discovery.')
    expect(promotions).toContain('Claimable offers are permanently limited to one claim and one use per customer family.')
    expect(promotions).toContain("['ORDER_PERCENT','ORDER_FIXED','FREE_SHIPPING']")
  })
})
