export const PRODUCT_IMAGE_PLACEHOLDER = '/products/placeholder.svg'
export const productAlt = (name: string) => name
export const productListingImage = (source: string) => /^\/products\/[^/?#]+\.png$/.test(source) ? source.replace(/\.png$/, '-640.webp') : source
