import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuth } from '@/composables/useAuth'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    eyebrow?: string
  }
}

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/admin' },
  { path: '/n/:uidHash', name: 'public-nfc', component: () => import('@/views/public/NfcResolveView.vue'), props: true, meta: { title: 'NFC 身份查询' } },

  {
    path: '/t/:traceCode',
    name: 'public-trace',
    component: () => import('@/views/public/TraceView.vue'),
    props: true,
    meta: { title: '溯源查询' },
  },

  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/admin/LoginView.vue'),
    meta: { title: '登录' },
  },

  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      {
        path: '',
        name: 'admin-home',
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '工作台', eyebrow: 'Farm workspace' },
      },
      {
        path: 'production',
        name: 'admin-production',
        component: () => import('@/views/admin/ProductionView.vue'),
        meta: { title: '地块与种植', eyebrow: 'Fields & growing seasons' },
      },
      {
        path: 'farming',
        name: 'admin-farming',
        component: () => import('@/views/admin/FarmOperationsView.vue'),
        meta: { title: '环境与农事', eyebrow: 'Growing & fieldwork' },
      },
      {
        path: 'harvest',
        name: 'admin-harvest',
        component: () => import('@/views/admin/HarvestView.vue'),
        meta: { title: '采收与包装', eyebrow: 'Harvest & packing' },
      },
      {
        path: 'inventory',
        name: 'admin-inventory',
        component: () => import('@/views/admin/InventoryView.vue'),
        meta: { title: '库存台账', eyebrow: 'Stock & movements' },
      },
      {
        path: 'rejections',
        name: 'admin-rejections',
        component: () => import('@/views/admin/RejectionsView.vue'),
        meta: { title: '退回与隔离', eyebrow: 'Returns & quarantine' },
      },
      {
        path: 'transport',
        name: 'admin-transport',
        component: () => import('@/views/admin/TransportView.vue'),
        meta: { title: '运输与交付', eyebrow: 'Transport & delivery' },
      },
      {
        path: 'sales',
        name: 'admin-sales',
        component: () => import('@/views/admin/SalesView.vue'),
        meta: { title: '销售订单', eyebrow: 'Sales & dispatch' },
      },
      {
        path: 'inventory/warehouse/:warehouseId?',
        name: 'admin-warehouse-stock',
        component: () => import('@/views/admin/WarehouseStockView.vue'),
        meta: { title: '仓库货物', eyebrow: 'Warehouse stock' },
      },
      {
        path: 'monitoring',
        name: 'admin-monitoring',
        component: () => import('@/views/admin/MonitoringView.vue'),
        meta: { title: '监测与设备绑定', eyebrow: 'Growing environment' },
      },
      {
        path: 'accounts',
        name: 'admin-accounts',
        component: () => import('@/views/admin/AccountsView.vue'),
        meta: { title: '账号与权限', eyebrow: 'People & access' },
      },
      {
        path: 'products',
        name: 'admin-products',
        component: () => import('@/views/admin/CatalogView.vue'),
        meta: { title: '产品与批次', eyebrow: 'Catalog' },
      },
      {
        path: 'items',
        name: 'admin-items',
        component: () => import('@/views/admin/ItemsView.vue'),
        meta: { title: '单品码与二维码', eyebrow: 'Trace Code' },
      },
      {
        path: 'iot',
        name: 'admin-iot',
        component: () => import('@/views/admin/IotView.vue'),
        meta: { title: '传感器监控', eyebrow: 'IoT Monitor' },
      },
      {
        path: 'nfc',
        name: 'admin-nfc',
        component: () => import('@/views/admin/NfcView.vue'),
        meta: { title: 'NFC 绑定', eyebrow: 'NFC Binding' },
      },
      {
        path: 'chain',
        name: 'admin-chain',
        component: () => import('@/views/admin/ChainExplorerView.vue'),
        meta: { title: '链上交易', eyebrow: 'Chain Explorer' },
      },
      {
        path: 'evidence-reconciliation', name: 'admin-evidence-reconciliation', component: () => import('@/views/admin/EvidenceReconciliationView.vue'), meta: { title: '证据对账', eyebrow: 'Evidence reconciliation' },
      },
      {
        path: 'ai',
        name: 'admin-ai',
        component: () => import('@/views/admin/AiView.vue'),
        meta: { title: '农场 AI 助手', eyebrow: 'AI Assistant' },
      },
    ],
  },

  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})
router.beforeEach(async to => {
  if (!to.matched.some(record => record.path === '/admin')) return true
  const auth = useAuth()
  try {
    if (!await auth.restore()) return { path: '/login', query: { redirect: to.fullPath } }
  } catch { return { path: '/login', query: { unavailable: '1', redirect: to.fullPath } } }
  if (!auth.isAdmin.value && to.name !== 'admin-warehouse-stock' && !['/admin', '/admin/production', '/admin/farming', '/admin/harvest', '/admin/inventory', '/admin/sales', '/admin/transport', '/admin/rejections', '/admin/monitoring', '/admin/accounts', '/admin/ai', '/admin/evidence-reconciliation'].includes(to.path)) return '/admin/production'
  return true
})
export default router
