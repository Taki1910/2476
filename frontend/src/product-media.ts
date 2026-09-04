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
}

export const productMedia = (name: string) => media[name]
export const productAlt = (name: string, locale: string) => locale === 'vi-VN' ? media[name]?.altVi : media[name]?.alt
