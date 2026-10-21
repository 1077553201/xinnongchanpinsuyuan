<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import ConsoleNavigation from '@/components/ConsoleNavigation.vue'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { useAuth } from '@/composables/useAuth'

const route = useRoute()
const router = useRouter()
const auth = useAuth()
const signingOut = ref(false)
const drawer = ref<HTMLDialogElement>()
const drawerOpen = ref(false)
let previousOverflow = ''
let desktop: MediaQueryList | undefined

function openNavigation() {
  if (!drawer.value || drawer.value.open) return
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  drawer.value.showModal()
  drawerOpen.value = true
}
function closeNavigation() {
  if (!drawer.value?.open) return
  drawer.value.close()
  restoreNavigation()
}
function restoreNavigation() {
  if (!drawerOpen.value || drawer.value?.open) return
  drawerOpen.value = false
  document.body.style.overflow = previousOverflow
}
function dismissBackdrop(event: MouseEvent) {
  if (event.target !== drawer.value) return
  const bounds = drawer.value.getBoundingClientRect()
  if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) closeNavigation()
}
function resized() { if (desktop?.matches) closeNavigation() }
onMounted(() => {
  desktop = window.matchMedia('(min-width: 901px)')
  desktop.addEventListener('change', resized)
})
onBeforeUnmount(() => {
  desktop?.removeEventListener('change', resized)
  closeNavigation()
  restoreNavigation()
})
watch(() => route.fullPath, closeNavigation)
watch(() => auth.user.value, value => { if (!value) void router.replace({ path: '/login', query: { redirect: route.fullPath } }) })
async function signOut() {
  signingOut.value = true
  try { await auth.logout() } catch { /* 本地身份已清除，重新登录会建立新会话。 */ }
  finally { signingOut.value = false; void router.replace('/login') }
}
const allNavItems = [
  { to: '/admin', label: '工作台' },
  { to: '/admin/production', label: '地块与种植' },
  { to: '/admin/farming', label: '环境与农事' },
  { to: '/admin/harvest', label: '采收与包装' },
  { to: '/admin/inventory', label: '库存台账' },
  { to: '/admin/monitoring', label: '监测与设备绑定' },
  { to: '/admin/products', label: '产品与批次' },
  { to: '/admin/items', label: '单品码' },
  { to: '/admin/iot', label: '传感器监控' },
  { to: '/admin/nfc', label: 'NFC 绑定' },
  { to: '/admin/chain', label: '链上交易' },
  { to: '/admin/ai', label: 'AI 助手' },
  { to: '/admin/accounts', label: '账号与权限' },
]
const navItems = computed(() => allNavItems.filter(item => auth.isAdmin.value || ['/admin', '/admin/production', '/admin/farming', '/admin/harvest', '/admin/inventory', '/admin/monitoring', '/admin/ai', '/admin/accounts'].includes(item.to)))
const title = computed(() => (route.meta.title as string | undefined) ?? '')
const eyebrow = computed(() => (route.meta.eyebrow as string | undefined) ?? '')
</script>

<template>
  <div class="console">
    <aside class="sidebar glass glass-strong"><ConsoleNavigation :items="navItems" /></aside>
    <div class="main">
      <header class="topbar glass glass-strong">
        <div class="titles"><p class="eyebrow">{{ eyebrow }}</p><h1 class="page-title">{{ title }}</h1></div>
        <div class="topbar-tools">
          <ThemeSwitcher />
          <span class="muted account-name">{{ auth.user.value?.displayName }}</span>
          <button type="button" class="ghost-btn sign-out" :disabled="signingOut" @click="signOut">退出</button>
          <button type="button" class="ghost-btn menu-button" aria-label="打开导航菜单" aria-controls="mobile-navigation" :aria-expanded="drawerOpen" @click="openNavigation">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
          </button>
        </div>
      </header>
      <main class="content"><RouterView /></main>
    </div>
    <dialog id="mobile-navigation" ref="drawer" class="navigation-drawer" aria-label="后台导航菜单" @close="restoreNavigation" @click="dismissBackdrop">
      <div class="drawer-head"><ThemeSwitcher /><button type="button" class="ghost-btn" autofocus aria-label="关闭导航菜单" @click="closeNavigation">关闭</button></div>
      <ConsoleNavigation :items="navItems" />
      <div class="drawer-account"><span class="account-name">{{ auth.user.value?.displayName }}</span><button type="button" class="ghost-btn" :disabled="signingOut" @click="signOut">退出登录</button></div>
    </dialog>
  </div>
</template>

<style scoped>
.console { --shell-gap: 16px; display: grid; grid-template-columns: 236px minmax(0,1fr); align-items: start; gap: var(--shell-gap); padding: var(--shell-gap); width: 100%; min-height: 100dvh; flex: 1 1 auto; }
.sidebar { position: sticky; top: var(--shell-gap); display: flex; flex-direction: column; height: calc(100dvh - 2 * var(--shell-gap)); overflow-y: auto; overscroll-behavior: contain; padding: 24px 16px; border-radius: var(--radius-xl); }
.main { display: flex; flex-direction: column; gap: var(--shell-gap); min-width: 0; min-height: calc(100dvh - 2 * var(--shell-gap)); }
.topbar { position: sticky; top: var(--shell-gap); z-index: 5; display: flex; align-items: center; justify-content: space-between; gap: 24px; min-height: 88px; padding: 12px 24px; border-radius: var(--radius-lg); }
.titles { min-width: 0; }
.eyebrow { line-height: 1.5; }
.page-title { font-family: var(--font-display); font-size: var(--fs-title); font-weight: 600; line-height: 1.4; }
.topbar-tools { display: flex; align-items: center; justify-content: flex-end; gap: 12px; flex-shrink: 0; }
.account-name { font-size: var(--fs-xs); max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ghost-btn { display: inline-flex; align-items: center; justify-content: center; min-height: 44px; padding: 8px 16px; border-radius: var(--radius-pill); border: 1px solid var(--glass-stroke); background: var(--glass-fill); font-size: var(--fs-sm); color: var(--ink-70); white-space: nowrap; }
.ghost-btn:hover { background: var(--glass-fill-strong); }
.menu-button { display: none; }
.content { min-width: 0; flex: 1; }
.navigation-drawer { position: fixed; inset: 0 0 0 auto; width: min(360px, calc(100% - 24px)); max-width: none; height: 100dvh; max-height: none; margin: 0 0 0 auto; padding: 20px; padding-top: max(20px, env(safe-area-inset-top)); padding-bottom: max(20px, env(safe-area-inset-bottom)); overflow-y: auto; overscroll-behavior: contain; border: 1px solid var(--glass-stroke); border-radius: 24px 0 0 24px; background: var(--dawn-top); color: var(--ink); box-shadow: var(--shadow-lift); }
.navigation-drawer[open] { display: flex; flex-direction: column; gap: 24px; animation: drawer-enter var(--dur-base) var(--ease-out); }
.navigation-drawer::backdrop { background: rgba(14,33,27,.38); }
.drawer-head, .drawer-account { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.drawer-account { border-top: 1px solid var(--glass-hairline); padding-top: 16px; }
@keyframes drawer-enter { from { transform: translateX(100%); } to { transform: translateX(0); } }
@media (max-width: 900px) {
  .console { --shell-gap: 12px; grid-template-columns: minmax(0,1fr); }
  .sidebar { display: none; }
  .topbar { gap: 12px; min-height: 80px; padding: 12px 16px; }
  .page-title { font-size: 1.25rem; }
  .topbar .eyebrow { font-size: 9px; letter-spacing: .1em; }
  .topbar-tools { gap: 8px; }
  .topbar .account-name, .sign-out { display: none; }
  .topbar-tools :deep(.theme-switcher) { display: none; }
  .menu-button { display: inline-flex; width: 44px; padding: 0; }
}
@media (max-width: 480px) {
  .console { --shell-gap: 10px; }
  .topbar { padding: 12px; }
  .page-title { font-size: 1.125rem; }
  .topbar .eyebrow { max-width: 145px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}
</style>
