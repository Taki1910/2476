import {describe,expect,it} from 'vitest'
import api from './api.ts?raw'
import promotions from './views/PromotionsView.vue?raw'

describe('C3 promotion administration contract',()=>{
  it('sends replacement evidence and keeps family identity immutable',()=>{
    expect(api).toContain('expectedPublishedRevisionId')
    expect(promotions).toContain(':disabled="!!familyId"')
    expect(promotions).toContain('current?.id')
  })
  it('explains discovery and excludes unsupported voucher effects',()=>{
    expect(promotions).toContain("t('Visibility does not grant eligibility or usage.')")
    expect(promotions).toContain("['ORDER_PERCENT','ORDER_FIXED','FREE_SHIPPING']")
  })
})
