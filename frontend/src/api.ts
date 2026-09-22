import type { PublishStorefrontDraft, SaveStorefrontDraft, StorefrontHomepage, StorefrontManagementState, StorefrontRevision } from './merchandising'
import type { ProductPresentation, ProductPresentationManagementState, ProductPresentationRevision, PublishProductPresentation, SaveProductPresentationDraft } from './product-presentation'

export type Account = { accountId: string; login: string; roles: string[]; permissions: string[] }
export type ProductMerchandising = { category: string | null; collection: string | null; featured: boolean; newArrival: boolean; campaignEligible: boolean; merchandisingRank: number; heroImage: string | null; primaryImage: string | null }
export type ProductSummary = ProductMerchandising & { id: string; name: string; variantCount: number; availableVariantCount: number; fromAmount: number; presentation: ProductPresentation | null }
export type Variant = { id: string; sku: string; size: string; color: string; availability: 'AVAILABLE' | 'UNAVAILABLE'; amount: number }
export type ProductPricing = { state: 'SINGLE' | 'RANGE'; minimumAmount: number; maximumAmount: number; currency: 'VND' }
export type ProductEvidenceMedia = { url: string; position: number; alt: string }
export type ProductFitRange = { size: string; minimumFootLengthMm: number; maximumFootLengthMm: number; minimumFootWidthMm: number; maximumFootWidthMm: number }
export type ProductFitGuidance = { sizeSystem: 'EU'; fitTendency: 'RUNS_SMALL' | 'TRUE_TO_SIZE' | 'RUNS_LARGE'; widthProfile: 'NARROW' | 'REGULAR' | 'WIDE'; fitAssistantSupported: boolean; ranges: ProductFitRange[] }
export type ProductDetail = ProductMerchandising & { id: string; name: string; fitSupported?: boolean; variants: Variant[]; pricing: ProductPricing; media: ProductEvidenceMedia[]; fitGuidance: ProductFitGuidance | null; presentation: ProductPresentation | null }
export type FitAnalysis = {
  status: 'SUCCESS' | 'RETAKE' | 'UNSUPPORTED_PRODUCT'
  retakeReason?: string
  footLengthMm?: number
  footWidthMm?: number
  recommendedSize?: string
  alternativeSize?: string
  analysisConfidence?: 'HIGH' | 'MEDIUM'
  analysisScore?: number
  explanation?: string
  warning?: string
  sizeSystem?: 'EU'
  fitTendency?: 'TRUE_TO_SIZE' | 'RUNS_SMALL' | 'RUNS_LARGE'
  widthProfile?: 'NARROW' | 'REGULAR' | 'WIDE'
  recommendedAvailable?: boolean
  selectedColorAvailable?: boolean | null
  availableColors: string[]
}
export type HeroProduct = ProductMerchandising & { id: string; name: string; recent30DayUnits: number; recent30DayRevenue: number; last7DayUnits: number; previous7DayUnits: number; growthUnits: number }
export type HeroCarousel = { topSeller: HeroProduct | null; trending: HeroProduct | null; newArrival: HeroProduct | null; featuredCollection: HeroProduct | null; candidates: HeroProduct[] }
export type PriceQuote = { id: string; variantId: string; priceVersionId: string; amount: number; currency: 'VND'; quotedAt: string; expiresAt: string }
export type CartDemand = { variantId: string; quantity: number }
export type CartQuoteItem = CartDemand & { productName: string; sku: string; size: string; color: string; priceVersionId: string; unitPriceAmount: number; totalAmount: number }
export type PromotionAdjustment = { name:string; layer:'ITEM'|'ORDER_AUTOMATIC'|'VOUCHER_SELECTED'|'SHIPPING'; amount:number; acquisitionMode?:'AUTOMATIC'|'CODE'|'CLAIMABLE'; maskedCode?:string; claimId?:string }
export type VoucherSelection = { type:'NONE' } | { type:'CODE'; code:string } | { type:'CLAIM'; claimId:string }
export type PublicOffer = { familyId:string; acquisitionMode:'CODE'|'CLAIMABLE'; name:string; customerSummary:string; customerTerms:string; effectType:'ORDER_PERCENT'|'ORDER_FIXED'|'FREE_SHIPPING'; percentageValue?:number; fixedAmount?:number; minimumSpendAmount?:number; validFrom:string; validTo?:string; claimable:boolean }
export type VoucherClaim = { claimId:string; familyId:string; claimStatus:'CLAIMED'|'REVOKED'; claimedAt:string; revokedAt?:string }
export type SavedVoucher = { claimId:string; claimStatus:'CLAIMED'|'REVOKED'; claimedAt:string; offer:PublicOffer; offerAvailability:string }
export type Page<T> = { items:T[]; page:number; size:number; hasNext:boolean }
export type PickupLocation = { id: string; branchId?: string; code: string; name: string }
export type GeoReference = { code: string; label: string }
export type ShippingRule = { id: string; familyId: string; revisionNumber: number; status: 'DRAFT'|'PUBLISHED'|'RETIRED'|'INVALIDATED'; originScope: 'LOCATION'|'GLOBAL'; originLocationId?: string; provinceCode: string; districtCode: string; zoneCode: 'LOCAL_CORE'|'LOCAL_OUTER'|'INTER_PROVINCE'; feeAmount: number; priority: number; validFrom: string; validTo?: string }
export type ShippingLocation = { id:string; code:string; name:string; branchId:string; branchCode:string }
export type DeliveryDetails = { receiverName: string; receiverPhone: string; provinceCode: string; districtCode: string; address: string; note?: string }
export type FulfillmentChoice = { type: 'PICKUP'; pickupLocationId: string } | { type: 'DELIVERY'; delivery: DeliveryDetails }
export type CartQuote = { id: string; quotedAt: string; expiresAt: string; currency: 'VND'; fulfillmentType: 'PICKUP' | 'DELIVERY'; originLocationId?: string; originBranchId?: string; destinationProvinceCode?: string; destinationDistrictCode?: string; shippingZoneCode?: string; merchandiseAmount: number; merchandiseDiscountAmount:number; shippingFeeAmount: number; shippingDiscountAmount:number; totalAmount: number; items: CartQuoteItem[]; adjustments:PromotionAdjustment[]; pickupLocations: PickupLocation[] }
export type OrderItem = CartDemand & { id: string; reservationId: string | null; priceVersionId: string; sku: string; size: string; color: string | null; locationId: string; unitPriceAmount: number; totalAmount: number }
export type FulfillmentStatus = 'PENDING' | 'PICKING' | 'PREPARED' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'HANDED_OVER' | 'CANCELLED'
export type Order = { id: string; orderReference: string; items: OrderItem[]; itemCount: number; reservationId: string | null; reservationExpiresAt: string | null; priceQuoteId: string | null; priceVersionId: string | null; ownerAccountId: string; responsibleBranchId: string; status: 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED'; createdAt: string; cancelledAt?: string; paidAt?: string; variantId: string | null; sku: string | null; size: string | null; locationId: string; locationCode: string; locationName: string; quantity: number; unitPriceAmount: number | null; currency: 'VND'; merchandiseAmount:number; merchandiseDiscountAmount:number; shippingDiscountAmount:number; adjustments:PromotionAdjustment[]; totalAmount: number; pickupStatus: 'PENDING_PAYMENT' | 'CANCELLED' | 'PAID_WAITING_PREPARATION' | 'READY_FOR_PICKUP' | 'READY_FOR_DISPATCH' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'PICKED_UP' | 'CANCELLATION_PROCESSING' | 'CANCELLED_PAYMENT_REVERSED' | 'CANCELLED_REVERSAL_FAILED' | 'CANCELLED_REVERSAL_REVIEW'; fulfillmentType?: 'PICKUP' | 'DELIVERY'; fulfillmentStatus?: FulfillmentStatus; financialVoidStatus?: VoidStatus; cancellationEligible: boolean; acceptedAt?: string; readyAt?: string; handedOverAt?: string; dispatchedAt?: string; deliveredAt?: string; fulfillmentCancelledAt?: string; receiverName?: string; receiverPhone?: string; deliveryAddress?: string; deliveryNote?: string; deliveryFeeAmount: number }
export type PromotionRule={id:string;familyId:string;revisionNumber:number;name:string;status:'DRAFT'|'PUBLISHED'|'RETIRED'|'INVALIDATED';layer:'ITEM'|'ORDER_AUTOMATIC'|'SHIPPING';effectType:'ITEM_PERCENT'|'ITEM_FIXED'|'ORDER_PERCENT'|'ORDER_FIXED'|'FREE_SHIPPING';percentageValue?:number;fixedAmount?:number;minimumSpendAmount?:number;buyQuantity?:number;globalUsageLimit?:number;perCustomerUsageLimit?:number;priority:number;validFrom:string;validTo?:string;productIds:string[];customerSummary?:string;customerTerms?:string;acquisitionMode:'AUTOMATIC'|'CODE'|'CLAIMABLE';normalizedCode?:string;publiclyDiscoverable:boolean}
export type PromotionDraft=Omit<PromotionRule,'id'|'status'|'familyId'|'normalizedCode'|'publiclyDiscoverable'>&{familyId?:string;normalizedCode?:string;publiclyDiscoverable?:boolean}
export type OrderPage = { items: Order[]; page: number; size: number; hasNext: boolean }
export type PaymentAttempt = { id: string; orderId: string; provider: 'VNPAY'; merchantTransactionReference: string; status: 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'EXPIRED' | 'REVIEW_REQUIRED'; amount: number; currency: 'VND'; createdAt: string; expiresAt: string; cancelledAt?: string; resolvedAt?: string; providerTransactionNo?: string; providerResponseCode?: string; providerTransactionStatus?: string; providerPaidAt?: string }
export type PaymentInitiation = { attempt: PaymentAttempt; paymentUrl: string; created: boolean }
export type VoidStatus = 'PROCESSING' | 'SUCCEEDED' | 'FAILED_RETRYABLE' | 'UNKNOWN' | 'REVIEW_REQUIRED'
export type VoidView = { id: string; orderId: string; status: VoidStatus; amount: number; currency: 'VND'; attemptId: string; generation: number; attemptStatus: string; allocationStatuses: string[]; createdAt: string; resolvedAt?: string }
export type CancellationResult = { orderId: string; fulfillmentId: string; fulfillmentStatus: 'CANCELLED'; financialVoid: VoidView }
export type PickupFulfillment = { id: string; orderId: string; branchId: string; locationId: string; type: 'PICKUP' | 'DELIVERY'; status: FulfillmentStatus; createdAt: string; pickingStartedAt?: string; preparedAt?: string; handedOverAt?: string; dispatchedAt?: string; deliveredAt?: string; cancelledAt?: string; receiverName?: string; receiverPhone?: string; deliveryAddress?: string; deliveryNote?: string; deliveryFeeAmount: number }
export type PickupItem = { sku: string; size: string; color: string | null; quantity: number }
export type PickupTask = { orderId: string; fulfillmentId?: string; branchId: string; branchCode: string; branchName: string; locationId: string; locationCode: string; locationName: string; items: PickupItem[]; itemCount: number; sku: string | null; size: string | null; quantity: number; orderStatus: 'PAID' | 'CANCELLED'; fulfillmentType: 'PICKUP' | 'DELIVERY'; fulfillmentStatus: 'NOT_CREATED' | FulfillmentStatus; createdAt?: string; pickingStartedAt?: string; preparedAt?: string; handedOverAt?: string; dispatchedAt?: string; deliveredAt?: string; cancelledAt?: string; receiverName?: string; receiverPhone?: string; deliveryAddress?: string; deliveryNote?: string; deliveryFeeAmount: number; financialVoidStatus?: VoidStatus }
export type PosRegister = { id: string; code: string; locationId: string; locationCode: string; locationName: string; branchId: string }
export type PosShift = { id: string; register: PosRegister; status: 'OPEN' | 'CLOSED'; openedAt: string; closedAt?: string; expectedCash: number; currency: 'VND' }
export type PosVariant = { id: string; productName: string; sku: string; size: string; color: string; priceVersionId: string; amount: number; currency: 'VND'; available: number; registerId: string; locationId: string }
export type PosReceipt = { orderId: string; saleId: string; tenderId: string; shiftId: string; registerId: string; registerCode: string; locationId: string; locationCode: string; locationName: string; soldAt: string; sku: string; size: string; quantity: number; unitPrice: number; total: number; currency: 'VND'; tender: 'CASH'; fulfillmentStatus: 'HANDED_OVER' }
export type ReportLocation = { branchId: string; branchCode: string; branchName: string; locationId: string; locationCode: string; locationName: string }
export type ReportScope = { asOf: string; businessTimezone: 'Asia/Ho_Chi_Minh'; defaultFromDate: string; defaultToDate: string; locations: ReportLocation[] }
export type ReportContext = { from?: string; to?: string; asOf: string; businessTimezone: 'Asia/Ho_Chi_Minh'; scope: ReportLocation }
export type MerchandiseBreakdown = { itemDiscount: string; orderDiscount: string; voucherDiscount: string; merchandiseNetBeforeReversal: string }
export type NetSalesReport = MerchandiseBreakdown & { context: ReportContext; onlineGross: string; posGross: string; grossSales: string; successfulVoids: string; netSales: string; exceptionAmount: string; exceptionCount: number; currency: 'VND'; merchandiseGross: string; shippingGross: string; shippingDiscount: string; shippingNetBeforeReversal: string; merchandiseVoids: string; shippingVoids: string; unallocatedLegacyVoids: string }
export type ProductSalesRow = MerchandiseBreakdown & { variantId: string; sku: string; size: string; onlineGross: string; posGross: string; grossSales: string; successfulVoids: string; netSales: string }
export type ProductSalesReport = MerchandiseBreakdown & { context: ReportContext; rows: ProductSalesRow[]; onlineGross: string; posGross: string; grossSales: string; successfulVoids: string; netSales: string; currency: 'VND'; unallocatedLegacyVoids: string }
export type InventoryRow = { variantId: string; productName: string; sku: string; size: string; onHand: number; reserved: number; available: number; updatedAt: string }
export type InventoryMovement = { id: string; orderId?: string; variantId: string; sku: string; type: string; onHandDelta: number; reservedDelta: number; occurredAt: string }
export type InventoryReservation = { id: string; variantId: string; sku: string; quantity: number; status: string; createdAt: string; expiresAt?: string }
export type InventoryReport = { context: ReportContext; sku?: string; rows: InventoryRow[]; movements: InventoryMovement[]; reservations: InventoryReservation[] }
export type ReconciliationEntry = { category: string; referenceId: string; orderId: string; status: string; amount: string; netEffect: string; occurredAt: string; exception: boolean }
export type ReconciliationReport = { context: ReportContext; entries: ReconciliationEntry[]; exceptionAmount: string; exceptionCount: number; currency: 'VND' }
export type Problem = { status?: number; code?: string; detail?: string; variantId?: string }
export type StaffLocation = { branchId: string; branchCode: string; branchName: string; locationId: string; locationCode: string; locationName: string }
export type StaffAccessRecord = { accountId: string; login: string; status: string; baseRole: string; inheritedPermissions: string[]; directCapabilities: string[]; assignments: StaffLocation[] }

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public variantId?: string, public retryAfterSeconds?: number) {
    super(message)
  }
}

export const SESSION_ENDED_EVENT = 'shoe-commerce:session-ended'

async function parse<T>(response: Response): Promise<T> {
  if (response.ok) return response.status === 204 ? undefined as T : response.json() as Promise<T>
  const problem = await response.json().catch(() => ({} as Problem)) as Problem
  const retry = response.headers.get('Retry-After') ?? ''
  const retryAfterSeconds = response.status === 429 && /^\d+$/.test(retry) && Number.isSafeInteger(Number(retry)) && Number(retry) > 0 ? Number(retry) : undefined
  throw new ApiError(response.status, problem.code ?? `HTTP_${response.status}`, problem.detail ?? 'The request could not be completed.', problem.variantId, retryAfterSeconds)
}

// Demand identity only; amounts and availability are exclusively server-owned.
export function normalizeCartDemand(items: CartDemand[]): CartDemand[] {
  if (!items.length || items.length > 50) throw new Error('Invalid cart size')
  const merged = new Map<string, number>()
  for (const item of items) {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(item.variantId)
      || !Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 10) throw new Error('Invalid cart line')
    const variantId = item.variantId.toLowerCase()
    const quantity = (merged.get(variantId) ?? 0) + item.quantity
    if (quantity > 10) throw new Error('Invalid cart quantity')
    merged.set(variantId, quantity)
  }
  return [...merged].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0).map(([variantId, quantity]) => ({ variantId, quantity }))
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1${path}`, { credentials: 'include', signal: AbortSignal.timeout(20_000), ...init })
  if (response.status === 401 && path !== '/auth/login') window.dispatchEvent(new Event(SESSION_ENDED_EVENT))
  return parse<T>(response)
}

async function csrf(): Promise<{ headerName: string; token: string }> {
  return request('/auth/csrf')
}

export const api = {
  me: () => request<Account>('/auth/me'),
  products: (query = '') => request<ProductSummary[]>(`/storefront/products${query ? `?q=${encodeURIComponent(query)}` : ''}`),
  product: (id: string) => request<ProductDetail>(`/storefront/products/${encodeURIComponent(id)}`),
  async fitAnalysis(productId: string, image: File, selectedColor?: string) {
    const token = await csrf()
    const form = new FormData()
    form.append('image', image)
    const query = selectedColor ? `?selectedColor=${encodeURIComponent(selectedColor)}` : ''
    return request<FitAnalysis>(`/storefront/products/${encodeURIComponent(productId)}/fit-analysis${query}`, {
      method: 'POST', headers: { [token.headerName]: token.token }, body: form,
    })
  },
  hero: () => request<HeroCarousel>('/storefront/hero'),
  storefrontHomepage: () => request<StorefrontHomepage<ProductSummary, PublicOffer>>('/storefront/homepage'),
  async login(username: string, password: string) {
    const token = await csrf()
    const body = new URLSearchParams({ username, password })
    return request<Account>('/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', [token.headerName]: token.token }, body })
  },
  async register(login: string, password: string) {
    const token = await csrf()
    return request<{ accountId: string; login: string }>('/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify({ login, password }) })
  },
  async logout() {
    const token = await csrf()
    return request<void>('/auth/logout', { method: 'POST', headers: { [token.headerName]: token.token } })
  },
  async quote(variantId: string) {
    const token = await csrf()
    return request<PriceQuote>('/storefront/price-quotes', { method: 'POST', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify({ variantId }) })
  },
  async checkout(quoteId: string, idempotencyKey: string, quantity = 1) {
    const token = await csrf()
    return request<Order>('/orders/checkout', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey, [token.headerName]: token.token }, body: JSON.stringify(quantity === 1 ? { quoteId } : { quoteId, quantity }) })
  },
  provinces: () => request<GeoReference[]>('/reference/provinces'),
  districts: (provinceCode: string) => request<GeoReference[]>(`/reference/provinces/${encodeURIComponent(provinceCode)}/districts`),
  shippingRules: () => request<ShippingRule[]>('/operations/shipping-rules'),
  shippingLocations: () => request<ShippingLocation[]>('/operations/shipping-rules/locations'),
  promotions:()=>request<PromotionRule[]>('/operations/promotions'),
  async createPromotion(rule:PromotionDraft){const token=await csrf();return request<PromotionRule>('/operations/promotions',{method:'POST',headers:{'Content-Type':'application/json',[token.headerName]:token.token},body:JSON.stringify(rule)})},
  async editPromotion(id:string,rule:PromotionDraft){const token=await csrf();return request<PromotionRule>(`/operations/promotions/${encodeURIComponent(id)}`,{method:'PUT',headers:{'Content-Type':'application/json',[token.headerName]:token.token},body:JSON.stringify(rule)})},
  async transitionPromotion(id:string,action:'publish'|'retire'|'invalidate',expectedPublishedRevisionId?:string){const token=await csrf();return request<PromotionRule>(`/operations/promotions/${encodeURIComponent(id)}/${action}`,{method:'POST',headers:{'Content-Type':'application/json',[token.headerName]:token.token},body:action==='publish'?JSON.stringify({expectedPublishedRevisionId}):undefined})},
  async createShippingRule(rule: Omit<ShippingRule,'id'|'status'>) { const token=await csrf(); return request<ShippingRule>('/operations/shipping-rules',{method:'POST',headers:{'Content-Type':'application/json',[token.headerName]:token.token},body:JSON.stringify(rule)}) },
  async transitionShippingRule(id:string, action:'publish'|'retire'|'invalidate') { const token=await csrf(); return request<ShippingRule>(`/operations/shipping-rules/${encodeURIComponent(id)}/${action}`,{method:'POST',headers:{[token.headerName]:token.token}}) },
  publicOffers: (page=0,size=20)=>request<Page<PublicOffer>>(`/storefront/promotions?page=${page}&size=${size}`),
  publicOffer: (familyId:string)=>request<PublicOffer>(`/storefront/promotions/${encodeURIComponent(familyId)}`),
  storefrontManagement: () => request<StorefrontManagementState>('/operations/storefront/homepage'),
  async saveStorefrontDraft(command: SaveStorefrontDraft) {
    const token = await csrf()
    return request<StorefrontRevision>('/operations/storefront/homepage/draft', { method: 'PUT', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify(command) })
  },
  async publishStorefrontDraft(command: PublishStorefrontDraft) {
    const token = await csrf()
    return request<StorefrontRevision>('/operations/storefront/homepage/publish', { method: 'POST', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify(command) })
  },
  productPresentationState: (productId: string) => request<ProductPresentationManagementState>(`/operations/product-presentations/${encodeURIComponent(productId)}`),
  async saveProductPresentationDraft(productId: string, command: SaveProductPresentationDraft) {
    const token = await csrf()
    return request<ProductPresentationRevision>(`/operations/product-presentations/${encodeURIComponent(productId)}/draft`, { method: 'PUT', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify(command) })
  },
  async publishProductPresentation(productId: string, command: PublishProductPresentation) {
    const token = await csrf()
    return request<ProductPresentationRevision>(`/operations/product-presentations/${encodeURIComponent(productId)}/publish`, { method: 'POST', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify(command) })
  },
  savedVouchers: (page=0,size=20)=>request<Page<SavedVoucher>>(`/me/voucher-claims?page=${page}&size=${size}`),
  async claimVoucher(familyId:string){const token=await csrf();return request<VoucherClaim>(`/me/voucher-claims/${encodeURIComponent(familyId)}`,{method:'PUT',headers:{[token.headerName]:token.token}})},
  async cartQuote(items: CartDemand[], fulfillment?: { type: 'PICKUP' } | { type: 'DELIVERY'; destinationProvinceCode: string; destinationDistrictCode: string }, voucherSelection:VoucherSelection={type:'NONE'}) {
    const body = JSON.stringify({ items: normalizeCartDemand(items), fulfillment: fulfillment ?? { type: 'PICKUP' }, voucherSelection })
    const token = await csrf()
    return request<CartQuote>('/storefront/cart-quotes', { method: 'POST', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body })
  },
  async cartCheckout(quoteId: string, items: CartDemand[], idempotencyKey: string, fulfillment: FulfillmentChoice) {
    const body = JSON.stringify({ quoteId, items: normalizeCartDemand(items), fulfillment })
    const token = await csrf()
    return request<Order>('/orders/cart-checkout', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey, [token.headerName]: token.token }, body })
  },
  async cancelOrder(orderId: string) {
    const token = await csrf()
    return request<Order>(`/orders/${encodeURIComponent(orderId)}/cancel`, { method: 'POST', headers: { [token.headerName]: token.token } })
  },
  async cancelConfirmed(orderId: string, idempotencyKey: string) {
    const token = await csrf()
    return request<CancellationResult>(`/orders/${encodeURIComponent(orderId)}/cancel`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey, [token.headerName]: token.token } })
  },
  async retryVoid(orderId: string, idempotencyKey: string) {
    const token = await csrf()
    return request<VoidView>(`/orders/${encodeURIComponent(orderId)}/void/retry`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey, [token.headerName]: token.token } })
  },
  order: (orderId: string) => request<Order>(`/orders/${encodeURIComponent(orderId)}`),
  orders: (page = 0, size = 20) => request<OrderPage>(`/orders?page=${page}&size=${size}`),
  paymentAttempt: (attemptId: string) => request<PaymentAttempt>(`/payment-attempts/${encodeURIComponent(attemptId)}`),
  async pay(orderId: string, idempotencyKey: string) {
    const token = await csrf()
    return request<PaymentInitiation>(`/orders/${encodeURIComponent(orderId)}/payments`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey, [token.headerName]: token.token },
    })
  },
  pickupQueue: () => request<PickupTask[]>('/operations/fulfillments'),
  pickupTask: (orderId: string) => request<PickupTask>(`/operations/fulfillments/${encodeURIComponent(orderId)}`),
  posRegisters: () => request<PosRegister[]>('/operations/pos/registers'),
  currentPosShift: () => request<PosShift | undefined>('/operations/pos/shifts/current'),
  posVariant: (shiftId: string, sku: string) => request<PosVariant>(`/operations/pos/variants?shiftId=${encodeURIComponent(shiftId)}&sku=${encodeURIComponent(sku)}`),
  posReceipt: (orderId: string) => request<PosReceipt>(`/operations/pos/sales/${encodeURIComponent(orderId)}`),
  reportScope: () => request<ReportScope>('/operations/reports/scope'),
  netSales: (fromDate: string, toDate: string, locationId: string) => request<NetSalesReport>(`/operations/reports/net-sales?${new URLSearchParams({ fromDate, toDate, locationId })}`),
  productSales: (fromDate: string, toDate: string, locationId: string) => request<ProductSalesReport>(`/operations/reports/product-sales?${new URLSearchParams({ fromDate, toDate, locationId })}`),
  inventoryReport: (locationId: string, sku = '') => request<InventoryReport>(`/operations/reports/inventory?${new URLSearchParams({ locationId, ...(sku ? { sku } : {}) })}`),
  reconciliation: (fromDate: string, toDate: string, locationId: string) => request<ReconciliationReport>(`/operations/reports/reconciliation?${new URLSearchParams({ fromDate, toDate, locationId })}`),
  staffAccess: () => request<StaffAccessRecord[]>('/operations/staff-access'),
  globalStaffAccess: () => request<StaffAccessRecord[]>('/admin/identity/staff'),
  staffLocations: () => request<StaffLocation[]>('/operations/staff-access/locations'),
  staffCapabilities: () => request<{ code: string }[]>('/operations/staff-access/capabilities'),
  async setStaffCapability(accountId: string, permission: string, active: boolean) {
    const token = await csrf()
    return request<boolean>(`/operations/staff-access/${encodeURIComponent(accountId)}/capabilities/${encodeURIComponent(permission)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify({ active }) })
  },
  async setStaffAssignment(accountId: string, location: StaffLocation, active: boolean) {
    const token = await csrf()
    return request<boolean>(`/operations/staff-access/${encodeURIComponent(accountId)}/assignments`, { method: 'PUT', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify({ branchId: location.branchId, locationId: location.locationId, active }) })
  },
  async setAccountEnabled(accountId: string, active: boolean) {
    const token = await csrf()
    return request<boolean>(`/admin/identity/accounts/${encodeURIComponent(accountId)}/enabled`, { method: 'PUT', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify({ active }) })
  },
  async openPosShift(registerId: string) {
    const token = await csrf()
    return request<PosShift>('/operations/pos/shifts', { method: 'POST', headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify({ registerId }) })
  },
  async closePosShift(shiftId: string) {
    const token = await csrf()
    return request<PosShift>(`/operations/pos/shifts/${encodeURIComponent(shiftId)}/close`, { method: 'POST', headers: { [token.headerName]: token.token } })
  },
  async sellPos(shiftId: string, variantId: string, idempotencyKey: string) {
    const token = await csrf()
    return request<PosReceipt>('/operations/pos/sales', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey, [token.headerName]: token.token }, body: JSON.stringify({ shiftId, variantId }) })
  },
  async createPickup(orderId: string) {
    const token = await csrf()
    return request<PickupFulfillment>(`/orders/${encodeURIComponent(orderId)}/pickup-fulfillment`, { method: 'POST', headers: { [token.headerName]: token.token } })
  },
  async preparePickup(fulfillmentId: string) {
    const token = await csrf()
    return request<PickupFulfillment>(`/pickup-fulfillments/${encodeURIComponent(fulfillmentId)}/prepare`, { method: 'POST', headers: { [token.headerName]: token.token } })
  },
  async acceptFulfillment(fulfillmentId: string) {
    const token = await csrf()
    return request<PickupFulfillment>(`/fulfillments/${encodeURIComponent(fulfillmentId)}/accept`, { method: 'POST', headers: { [token.headerName]: token.token } })
  },
  async readyFulfillment(fulfillmentId: string) {
    const token = await csrf()
    return request<PickupFulfillment>(`/fulfillments/${encodeURIComponent(fulfillmentId)}/ready`, { method: 'POST', headers: { [token.headerName]: token.token } })
  },
  async handoverPickup(fulfillmentId: string, idempotencyKey: string) {
    const token = await csrf()
    return request<PickupFulfillment>(`/pickup-fulfillments/${encodeURIComponent(fulfillmentId)}/handover`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey, [token.headerName]: token.token } })
  },
  async dispatchFulfillment(fulfillmentId: string, idempotencyKey: string) {
    const token = await csrf()
    return request<PickupFulfillment>(`/fulfillments/${encodeURIComponent(fulfillmentId)}/dispatch`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey, [token.headerName]: token.token } })
  },
  async deliverFulfillment(fulfillmentId: string, idempotencyKey: string) {
    const token = await csrf()
    return request<PickupFulfillment>(`/fulfillments/${encodeURIComponent(fulfillmentId)}/delivered`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey, [token.headerName]: token.token } })
  },
}
