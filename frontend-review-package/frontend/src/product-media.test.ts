import { describe, expect, it } from 'vitest'
import { productAlt, productMedia } from './product-media'

describe('S1.4 product media', () => {
  it.each([
    ['Basket Retro', '/products/basket-retro.png'],
    ['Urban Hiker', '/products/urban-hiker.png'],
    ['Trail Edge', '/products/trail-edge.png'],
    ['City Loafer', '/products/city-loafer.png'],
  ])('uses distinct catalog artwork for %s', (name, src) => {
    expect(productMedia(name)?.src).toBe(src)
    expect(productAlt(name, 'en')).toBeTruthy()
    expect(productAlt(name, 'vi-VN')).toBeTruthy()
  })
})
