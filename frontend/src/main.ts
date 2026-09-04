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
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import AccessDeniedView from './views/AccessDeniedView.vue'
import { hasPermission, loadSession, loginDestination, session } from './session'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'catalog', component: CatalogView },
    { path: '/cart', name: 'cart', component: CartView },
    { path: '/orders', name: 'orders', component: OrdersView, meta: { requiresAuth: true, permission: 'ORDER_PLACE' } },
    { path: '/products/:id', name: 'product', component: ProductView },
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
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async to => {
  if (!session.loaded) await loadSession()
  if (session.unavailable) return false
  if ((to.name === 'login' || to.name === 'register') && session.account) return loginDestination(to.query.returnTo, session.account)
  if (to.meta.requiresAuth && !session.account) return { path: '/login', query: { returnTo: to.fullPath } }
  if (session.account && !hasPermission(to.meta.permission as string | undefined)) return '/forbidden'
})

createApp(App).use(router).mount('#app')
