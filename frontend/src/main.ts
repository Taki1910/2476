import '@fontsource-variable/archivo'
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import CatalogView from './views/CatalogView.vue'
import CartView from './views/CartView.vue'
import OrdersView from './views/OrdersView.vue'
import ProductView from './views/ProductView.vue'
import PaymentResultView from './views/PaymentResultView.vue'
import PickupQueueView from './views/PickupQueueView.vue'
import PickupDetailView from './views/PickupDetailView.vue'
import OrderStatusView from './views/OrderStatusView.vue'
import PosView from './views/PosView.vue'
import ReportsView from './views/ReportsView.vue'
import PeopleAccessView from './views/PeopleAccessView.vue'
import ShippingRulesView from './views/ShippingRulesView.vue'
import PromotionsView from './views/PromotionsView.vue'
import StorefrontManagementView from './views/StorefrontManagementView.vue'
import ProductPresentationsView from './views/ProductPresentationsView.vue'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import AccessDeniedView from './views/AccessDeniedView.vue'
import OffersView from './views/OffersView.vue'
import OfferDetailView from './views/OfferDetailView.vue'
import VouchersView from './views/VouchersView.vue'
import { hasPermission, loadSession, loginDestination, session } from './session'
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
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: to => to.hash ? { el: to.hash, top: 16 } : { top: 0 },
})

router.beforeEach(async to => {
  if (!session.loaded) await loadSession()
  if (session.unavailable) return false
  if ((to.name === 'login' || to.name === 'register') && session.account) return loginDestination(to.query.returnTo, session.account)
  if (to.meta.requiresAuth && !session.account) return { path: '/login', query: { returnTo: to.fullPath } }
  const anyPermission = to.meta.permissions as string[] | undefined
  if (session.account && (!hasPermission(to.meta.permission as string | undefined) || anyPermission && !anyPermission.some(hasPermission))) return '/forbidden'
})

createApp(App).use(router).mount('#app')
