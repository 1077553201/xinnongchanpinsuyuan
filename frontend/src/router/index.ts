import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    eyebrow?: string
  }
}

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/admin' },

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
        meta: { title: '总览', eyebrow: 'Dashboard' },
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
        component: () => import('@/views/admin/StagePlaceholder.vue'),
        meta: { title: '传感器监控', eyebrow: 'IoT Monitor' },
      },
      {
        path: 'nfc',
        name: 'admin-nfc',
        component: () => import('@/views/admin/StagePlaceholder.vue'),
        meta: { title: 'NFC 绑定', eyebrow: 'NFC Binding' },
      },
      {
        path: 'chain',
        name: 'admin-chain',
        component: () => import('@/views/admin/ChainExplorerView.vue'),
        meta: { title: '链上交易', eyebrow: 'Chain Explorer' },
      },
      {
        path: 'ai',
        name: 'admin-ai',
        component: () => import('@/views/admin/StagePlaceholder.vue'),
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

export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})
