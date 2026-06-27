import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './style.css'

import Login from './views/Login.vue'
import Dashboard from './views/Dashboard.vue'
import Invoices from './views/Invoices.vue'
import InvoiceAdd from './views/InvoiceAdd.vue'
import InvoiceDetail from './views/InvoiceDetail.vue'
import Vouchers from './views/Vouchers.vue'
import VoucherAdd from './views/VoucherAdd.vue'
import VoucherDetail from './views/VoucherDetail.vue'
import Accounts from './views/Accounts.vue'
import Reports from './views/Reports.vue'
import Settings from './views/Settings.vue'
import BalanceSheet from './views/BalanceSheet.vue'
import IncomeStatement from './views/IncomeStatement.vue'
import CashFlow from './views/CashFlow.vue'

const routes = [
  { path: '/login', component: Login, meta: { guest: true } },
  { path: '/register', component: Login, meta: { guest: true } },
  { path: '/', component: Dashboard },
  { path: '/invoices', component: Invoices },
  { path: '/invoices/add', component: InvoiceAdd },
  { path: '/invoices/:id', component: InvoiceDetail },
  { path: '/vouchers', component: Vouchers },
  { path: '/vouchers/add', component: VoucherAdd },
  { path: '/vouchers/:id', component: VoucherDetail },
  { path: '/accounts', component: Accounts },
  { path: '/reports', component: Reports },
  { path: '/reports/balance-sheet', component: BalanceSheet },
  { path: '/reports/income-statement', component: IncomeStatement },
  { path: '/reports/cash-flow', component: CashFlow },
  { path: '/settings', component: Settings },
]

const router = createRouter({ history: createWebHistory(), routes })

// Auth guard: only check local presence of token. Actual token expiry is
// handled transparently by apiFetch() (see api.js) which refreshes on 401.
const auth = JSON.parse(localStorage.getItem('fin_user') || 'null')
router.beforeEach((to) => {
  const user = JSON.parse(localStorage.getItem('fin_user') || 'null')
  const token = localStorage.getItem('fin_token') || ''
  if ((!user || !token) && !to.meta.guest) return '/login'
  if (user && to.meta.guest) return '/'
})

const app = createApp(App)
app.use(router)
app.provide('auth', auth)
app.mount('#app')
