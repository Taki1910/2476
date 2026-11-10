import { describe, expect, it } from 'vitest'
import { PRODUCT_IMAGE_PLACEHOLDER, productAlt, productListingImage } from './product-media'

describe('product media fallback', () => {
  it('uses only the canonical product name as fallback alt', () => {
    expect(productAlt('City Loafer')).toBe('City Loafer')
    expect(productAlt('City Loafer', 'Side profile on foot')).toBe('Side profile on foot')
    expect(productAlt('City Loafer', '  ')).toBe('City Loafer')
    expect(productAlt('Unknown Product')).toBe('Unknown Product')
    expect(productAlt('City Loafer')).not.toMatch(/leather|da |technical|mesh|suede/i)
  })

  it('uses a neutral local asset when product media is unavailable', () => {
    expect(PRODUCT_IMAGE_PLACEHOLDER).toBe('/products/placeholder.svg')
    expect(PRODUCT_IMAGE_PLACEHOLDER).not.toContain('court-classic')
  })

  it('serves the 640px WebP derivative for controlled listing artwork', () => {
    expect(productListingImage('/products/city-loafer.png')).toBe('/products/city-loafer-640.webp')
    expect(productListingImage(PRODUCT_IMAGE_PLACEHOLDER)).toBe(PRODUCT_IMAGE_PLACEHOLDER)
    expect(productListingImage('https://cdn.example/shoe.png')).toBe('https://cdn.example/shoe.png')
  })
})
