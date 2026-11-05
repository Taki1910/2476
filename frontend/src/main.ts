import '@fontsource-variable/archivo'
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
const CatalogView = () => import('./views/CatalogView.vue')
const CartView = () => import('./views/CartView.vue')
const OrdersView = () => import('./views/OrdersView.vue')
const ProductView = () => import('./views/ProductView.vue')
const PaymentResultView = () => import('./views/PaymentResultView.vue')
const PickupQueueView = () => import('./views/PickupQueueView.vue')
const PickupDetailView = () => import('./views/PickupDetailView.vue')
const OrderStatusView = () => import('./views/OrderStatusView.vue')
const PosView = () => import('./views/PosView.vue')
const ReportsView = () => import('./views/ReportsView.vue')
const PeopleAccessView = () => import('./views/PeopleAccessView.vue')
const ShippingRulesView = () => import('./views/ShippingRulesView.vue')
const PromotionsView = () => import('./views/PromotionsView.vue')
const StorefrontManagementView = () => import('./views/StorefrontManagementView.vue')
const ProductPresentationsView = () => import('./views/ProductPresentationsView.vue')
const ProductsView = () => import('./views/ProductsView.vue')
const ProductManagementDetailView = () => import('./views/ProductManagementDetailView.vue')
const InventoryTransfersView = () => import('./views/InventoryTransfersView.vue')
const LoginView = () => import('./views/LoginView.vue')
const RegisterView = () => import('./views/RegisterView.vue')
const AccessDeniedView = () => import('./views/AccessDeniedView.vue')
const OffersView = () => import('./views/OffersView.vue')
const OfferDetailView = () => import('./views/OfferDetailView.vue')
const VouchersView = () => import('./views/VouchersView.vue')
import { loadSession, routeAccessDestination, session } from './session'
import { storefrontScrollBehavior } from './navigation'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'catalog', component: CatalogView },
    { path: '/cart', name: 'cart', component: CartView },
    { path: '/orders', name: 'orders', component: OrdersView, meta: { requiresAuth: true, permission: 'ORDER_PLACE' } },
    { path: '/products/:id', name: 'product', component: ProductView },
    { path: '/promotions', name: 'offers', component: OffersView },
    { path: '/promotions/:familyId', name: 'offer-detail', component: OfferDetailView },
    { path: '/account/vouchers', name: 'my-vouchers', component: VouchersView, meta: { requiresAuth: true, permission: 'ORDER_PLACE' } },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/forbidden', name: 'forbidden', component: AccessDeniedView },
    { path: '/payment/result', name: 'payment-result', component: PaymentResultView, meta: { requiresAuth: true } },
    { path: '/orders/:id', name: 'order-status', component: OrderStatusView, meta: { requiresAuth: true, permission: 'ORDER_PLACE' } },
    { path: '/operations/fulfillments', name: 'fulfillment-queue', component: PickupQueueView, meta: { requiresAuth: true, permission: 'FULFILL_ORDER' } },
    { path: '/operations/fulfillments/:id', name: 'fulfillment-detail', component: PickupDetailView, meta: { requiresAuth: true, permission: 'FULFILL_ORDER' } },
    { path: '/operations/pickups', redirect: '/operations/fulfillments' },
    { path: '/operations/pickups/:id', redirect: to => `/operations/fulfillments/${to.params.id}` },
    { path: '/operations/pos', name: 'pos', component: PosView, meta: { requiresAuth: true, permission: 'POS_SELL' } },
    { path: '/operations/reports', name: 'reports', component: ReportsView, meta: { requiresAuth: true, permission: 'REPORT_VIEW' } },
    { path: '/operations/people', name: 'people-access', component: PeopleAccessView, meta: { requiresAuth: true, permissions: ['STAFF_MANAGE_SCOPED', 'IDENTITY_MANAGE'] } },
    { path: '/operations/shipping', name: 'shipping-rules', component: ShippingRulesView, meta: { requiresAuth: true, permission: 'SHIPPING_RATE_MANAGE' } },
    { path: '/operations/promotions', name: 'promotions', component: PromotionsView, meta: { requiresAuth: true, permission: 'PROMOTION_MANAGE' } },
    { path: '/operations/storefront', name: 'storefront-management', component: StorefrontManagementView, meta: { requiresAuth: true, permission: 'STOREFRONT_MANAGE' } },
    { path: '/operations/product-presentations', name: 'product-presentations', component: ProductPresentationsView, meta: { requiresAuth: true, permission: 'STOREFRONT_MANAGE' } },
    { path: '/operations/products', name: 'products', component: ProductsView, meta: { requiresAuth: true, permission: 'CATALOG_MANAGE' } },
    { path: '/operations/products/:id', name: 'product-management', component: ProductManagementDetailView, meta: { requiresAuth: true, permission: 'CATALOG_MANAGE' } },
    { path: '/operations/inventory/transfers', name: 'inventory-transfers', component: InventoryTransfersView, meta: { requiresAuth: true, permission: 'INVENTORY_TRANSFER' } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: storefrontScrollBehavior,
})

router.beforeEach(async to => {
  if (!session.loaded) await loadSession()
  if (session.unavailable) return false
  return routeAccessDestination(to)
})

createApp(App).use(router).mount('#app')
