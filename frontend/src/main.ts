import '@fontsource-variable/archivo'
import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import CatalogView from './views/CatalogView.vue'
import ProductView from './views/ProductView.vue'
import PaymentResultView from './views/PaymentResultView.vue'
import PickupQueueView from './views/PickupQueueView.vue'
import PickupDetailView from './views/PickupDetailView.vue'
import OrderStatusView from './views/OrderStatusView.vue'
import PosView from './views/PosView.vue'
import ReportsView from './views/ReportsView.vue'
import './styles.css'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'catalog', component: CatalogView },
    { path: '/products/:id', name: 'product', component: ProductView },
    { path: '/payment/result', name: 'payment-result', component: PaymentResultView },
    { path: '/orders/:id', name: 'order-status', component: OrderStatusView },
    { path: '/operations/pickups', name: 'pickup-queue', component: PickupQueueView },
    { path: '/operations/pickups/:id', name: 'pickup-detail', component: PickupDetailView },
    { path: '/operations/pos', name: 'pos', component: PosView },
    { path: '/operations/reports', name: 'reports', component: ReportsView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

createApp(App).use(router).mount('#app')
