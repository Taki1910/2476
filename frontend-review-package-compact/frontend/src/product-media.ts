export type ProductMedia = { src: string; alt: string; altVi: string }

const media: Record<string, ProductMedia> = {
  'Court Classic': { src: '/products/court-classic.png', alt: 'White Court Classic low-top sneaker with black details', altVi: 'Giày Court Classic cổ thấp màu trắng, phối chi tiết đen' },
  'Metro Runner': { src: '/products/metro-runner.png', alt: 'Silver and black Metro Runner mesh running shoe', altVi: 'Giày chạy Metro Runner vải lưới màu bạc và đen' },
  'Studio Low': { src: '/products/studio-low.png', alt: 'Ivory Studio Low suede sneaker with gum sole', altVi: 'Giày Studio Low da lộn màu ngà, đế gum' },
  'Trail Form': { src: '/products/trail-form.png', alt: 'Moss green Trail Form shoe with rugged sand sole', altVi: 'Giày Trail Form xanh rêu với đế địa hình màu cát' },
  'After Dark': { src: '/products/after-dark.png', alt: 'Black After Dark sneaker with pink stitching', altVi: 'Giày After Dark màu đen với đường chỉ hồng' },
  'Daily Canvas': { src: '/products/daily-canvas.png', alt: 'Natural canvas Daily Canvas sneaker with navy heel', altVi: 'Giày Daily Canvas vải màu tự nhiên, gót xanh navy' },
  'Court High': { src: '/products/court-high.png', alt: 'White Court High sneaker with deep red collar', altVi: 'Giày Court High cổ cao màu trắng, viền đỏ đậm' },
  'Pace Knit': { src: '/products/pace-knit.png', alt: 'Stone grey Pace Knit running shoe with lime accents', altVi: 'Giày chạy Pace Knit màu xám đá, phối xanh lime' },
  'Basket Retro': { src: '/products/basket-retro.png', alt: 'White and green Basket Retro court low-top sneaker', altVi: 'Giày Court cổ thấp Basket Retro màu trắng phối xanh lá' },
  'Urban Hiker': { src: '/products/urban-hiker.png', alt: 'Black and olive Urban Hiker technical shoe with rugged outsole', altVi: 'Giày Urban Hiker kỹ thuật màu đen và xanh olive với đế địa hình' },
  'Trail Edge': { src: '/products/trail-edge.png', alt: 'Navy and orange Trail Edge shoe with rugged tread', altVi: 'Giày Trail Edge xanh navy phối cam với đế địa hình' },
  'City Loafer': { src: '/products/city-loafer.png', alt: 'Brown leather City Loafer slip-on shoe', altVi: 'Giày lười City Loafer bằng da nâu' },
}

export const productMedia = (name: string) => media[name]
export const productAlt = (name: string, locale: string) => locale === 'vi-VN' ? media[name]?.altVi : media[name]?.alt
