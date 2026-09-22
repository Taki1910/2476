import { describe, expect, it } from 'vitest'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
import FitAssistant from './components/FitAssistant.vue'
import { setLocale, t } from './i18n'

const variants = [{ id: 'variant', sku: 'DEMO-CC-40', size: '40', color: 'White', availability: 'AVAILABLE' as const, amount: 1490000 }]

describe('fit assistant entry states', () => {
  it.each(['en', 'vi-VN'] as const)('offers the advisory flow for a supported product in %s', async language => {
    setLocale(language)
    const html = await renderToString(createSSRApp(FitAssistant, { productId: 'product', fitSupported: true, variants }))
    expect(html).toContain(language === 'en' ? 'Not sure about your size?' : 'Chưa chắc nên chọn kích cỡ nào?')
    expect(html).toContain(language === 'en' ? 'Optional photo guide' : 'Hướng dẫn qua ảnh, không bắt buộc')
    expect(html).toContain(t('Find my size'))
    expect(html.split(t('Find my size')).length - 1).toBe(1)
  })

  it.each(['en', 'vi-VN'] as const)('explains when a model has no profile in %s', async language => {
    setLocale(language)
    const html = await renderToString(createSSRApp(FitAssistant, { productId: 'product', fitSupported: false, variants }))
    expect(html).toContain(t('This shoe model does not have a supported fit profile yet.'))
    expect(html).not.toContain('fit-entry')
  })
})
