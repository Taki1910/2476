import { describe, expect, it } from 'vitest'
import api from './api.ts?raw'
import promotions from './views/PromotionsView.vue?raw'
import offers from './views/OffersView.vue?raw'
import detail from './views/OfferDetailView.vue?raw'
import vouchers from './views/VouchersView.vue?raw'
import voucherCopy from './voucher.ts?raw'

describe('P3 promotion and customer offer contracts', () => {
  it('uses family-centric manager reads, promotion-authorized products, and server preview', () => {
    expect(promotions).toContain('api.promotionCampaigns()')
    expect(promotions).toContain('api.promotionProducts()')
    expect(promotions).toContain('api.previewPromotion(')
    expect(promotions).not.toContain('api.products()')
    expect(promotions).not.toContain('api.managerCatalog(')
    expect(api).toContain('/operations/promotions/product-references')
    expect(promotions).toContain('campaignStatusLabel(campaign.state)')
    expect(promotions).not.toContain('t(campaign.state)')
  })

  it('loads more public offers and never presents code acquisition', () => {
    expect(offers).toContain('api.publicOffers(next,20)')
    expect(offers).toContain('Load more offers')
    expect(offers).not.toContain('Code offer')
    expect(detail).not.toContain('normalizedCode')
  })

  it('preloads owned state and only exposes claim actions to CUSTOMER', () => {
    expect(detail).toContain("roles.includes('CUSTOMER')")
    expect(detail).toContain('api.voucherClaimState(requested)')
    expect(detail).toContain('v-if="isCustomer&&!saved"')
    expect(detail).toContain('Saved to My Vouchers, but it is currently {status}.')
    expect(detail).not.toContain('Saved offer status:')
  })

  it('keeps unavailable wallet evidence without promising a live terms route', () => {
    expect(vouchers).toContain('voucher.termsAvailable')
    expect(vouchers).toContain('Current public terms unavailable')
    expect(vouchers).toContain('Load more vouchers')
    expect(voucherCopy).not.toContain('VOUCHER_PRODUCT_NOT_ELIGIBLE')
  })
})
