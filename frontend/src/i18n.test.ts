import { beforeEach, describe, expect, it, vi } from 'vitest'
import { commerceCount, locale, messageLabel, setLocale, t } from './i18n'
import { loginDestination, safeReturnTo } from './session'

describe('locale and return-route rules', () => {
  beforeEach(() => vi.stubGlobal('localStorage', { getItem: vi.fn(), setItem: vi.fn() }))

  it('persists an explicit language choice', () => {
    setLocale('vi-VN')
    expect(locale.value).toBe('vi-VN')
    expect(t('Net sales')).toBe('Doanh thu thuần')
    expect(t('Receiver name, phone, and delivery address are required.')).toBe('Bắt buộc nhập tên, số điện thoại người nhận và địa chỉ giao hàng.')
    expect(t('Choose a valid province, district, and ward.')).toBe('Vui lòng chọn tỉnh/thành phố, quận/huyện và phường/xã hợp lệ.')
    expect(t('Choose a valid province / city.')).toBe('Vui lòng chọn tỉnh/thành phố hợp lệ.')
    expect(t('Choose a valid district.')).toBe('Vui lòng chọn quận/huyện hợp lệ.')
    expect(t('Enter a valid street and house address.')).toBe('Vui lòng nhập số nhà và tên đường hợp lệ.')
    expect(t('Enter a shorter delivery note.')).toBe('Vui lòng rút gọn ghi chú giao hàng.')
    expect(localStorage.setItem).toHaveBeenCalledWith('shoe-commerce:locale', 'vi-VN')
  })

  it('keeps language controls working when persistence is blocked', () => {
    vi.stubGlobal('localStorage', { setItem: () => { throw new Error('denied') } })
    expect(() => setLocale('vi-VN')).not.toThrow()
    expect(t('Checkout recovery unavailable')).toBe('Chưa thể khôi phục lần đặt hàng')
  })

  it('can initialize the UI that explains unavailable checkout storage', async () => {
    vi.stubGlobal('localStorage', { getItem: () => { throw new Error('denied') } })
    vi.resetModules()
    const fresh = await import('./i18n')
    expect(['en', 'vi-VN']).toContain(fresh.locale.value)
  })

  it('changes existing error copy with the selected language', () => {
    const key = 'No sellable variant matches that SKU. Check the label and try again.'
    setLocale('vi-VN')
    const displayed = t(key)
    setLocale('en')
    expect(messageLabel(displayed)).toBe(key)
    setLocale('vi-VN')
    expect(messageLabel(key)).toBe(displayed)
  })

  it('uses natural singular and plural commerce counts', () => {
    setLocale('en')
    expect(commerceCount(1, 1)).toBe('1 selection · 1 pair')
    expect(commerceCount(2, 3)).toBe('2 selections · 3 pairs')
    setLocale('vi-VN')
    expect(commerceCount(2, 3)).toBe('2 lựa chọn · 3 đôi')
  })

  it('accepts only local return destinations', () => {
    expect(safeReturnTo('/products/one?variant=two')).toBe('/products/one?variant=two')
    expect(safeReturnTo('//attacker.example')).toBe('/')
    expect(safeReturnTo('https://attacker.example')).toBe('/')
  })

  it('distinguishes a register lane from account registration and translates cash actions', () => {
    setLocale('vi-VN')
    expect(t('Register')).toBe('Đăng ký')
    expect(t('Register lane')).toBe('Quầy thanh toán')
    expect(t('Take {amount} & complete sale', { amount: '100 ₫' })).toBe('Nhận 100 ₫ và hoàn tất giao dịch')
  })

  it('writes storefront discovery copy independently in Vietnamese', () => {
    setLocale('vi-VN')
    expect(t('Describe the shoe you’re looking for')).toBe('Mô tả đôi giày bạn đang tìm')
    expect(t('White running shoes under 2 million')).toBe('Giày trắng chạy bộ dưới 2 triệu')
    expect(t('No exact matches for “{query}”.', { query: 'giày tím' }))
      .toBe('Chưa thấy mẫu khớp “giày tím”.')
    expect(t('Results for “{query}”', { query: 'giày trắng' })).toBe('Kết quả cho “giày trắng”')
    expect(t('Matched: {criteria}', { criteria: 'Trắng · Chạy bộ' })).toBe('Đã nhận diện: Trắng · Chạy bộ')
    expect(t('Try a broader product name, color, use, or price.')).toBe('Hãy thử tên sản phẩm, màu sắc, nhu cầu hoặc mức giá rộng hơn.')
    expect(t('Not sure about size?')).toBe('Chưa chắc về kích cỡ?')
    expect(t('You might like')).toBe('Có thể bạn sẽ thích')
  })

  it('uses the role workspace unless an explicit local destination was supplied', () => {
    const cashier = { accountId: 'cashier', login: 'cashier', roles: [], permissions: ['POS_SELL'] }
    const manager = { ...cashier, permissions: ['FULFILL_ORDER', 'REPORT_VIEW'] }
    expect(loginDestination(undefined, cashier)).toBe('/operations/pos')
    expect(loginDestination(undefined, manager)).toBe('/operations/fulfillments')
    expect(loginDestination('/products/one?variant=two&checkout=1', cashier)).toBe('/products/one?variant=two&checkout=1')
    expect(loginDestination('https://attacker.example', cashier)).toBe('/operations/pos')
  })
})
