<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'

const route = useRoute()

const navItems = [
  { to: '/admin', label: '总览' },
  { to: '/admin/products', label: '产品与批次' },
  { to: '/admin/items', label: '单品码' },
  { to: '/admin/iot', label: '传感器监控' },
  { to: '/admin/nfc', label: 'NFC 绑定' },
  { to: '/admin/chain', label: '链上交易' },
  { to: '/admin/ai', label: 'AI 助手' },
]

const title = computed(() => (route.meta.title as string | undefined) ?? '')
const eyebrow = computed(() => (route.meta.eyebrow as string | undefined) ?? '')

function isActive(to: string): boolean {
  return to === '/admin' ? route.path === '/admin' : route.path.startsWith(to)
}
</script>

<template>
  <div class="console">
    <aside class="sidebar glass glass-strong">
      <RouterLink to="/admin" class="brand">
        <span class="brand-mark" aria-hidden="true">溯</span>
        <span class="brand-text">
          <strong>农产品可信溯源</strong>
          <em>AgriTrace Console</em>
        </span>
      </RouterLink>

      <nav class="nav" aria-label="后台导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-link"
          :class="{ active: isActive(item.to) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="sidebar-foot">
        <RouterLink to="/t/DEMO-0001" class="public-link">
          查看公众查询页
          <span aria-hidden="true">↗</span>
        </RouterLink>
        <p class="muted tiny">设计稿阶段 · 数据接口已就绪</p>
      </div>
    </aside>

    <div class="main">
      <header class="topbar glass glass-strong">
        <div class="titles">
          <p class="eyebrow">{{ eyebrow }}</p>
          <h1 class="page-title">{{ title }}</h1>
        </div>
        <RouterLink to="/login" class="ghost-btn">登录</RouterLink>
      </header>

      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.console {
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  gap: var(--space-4);
  padding: var(--space-4);
  max-width: var(--console-max);
  margin: 0 auto;
  width: 100%;
  flex: 1 1 auto;
  align-items: start;
}

.sidebar {
  position: sticky;
  top: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: var(--space-5) var(--space-4);
  border-radius: var(--radius-xl);
  min-height: calc(100vh - var(--space-8));
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 var(--space-2);
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: var(--field);
  color: var(--mist);
  font-family: var(--font-display);
  font-size: 1.125rem;
  box-shadow: 0 8px 22px -12px rgba(30, 91, 72, 0.9);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.brand-text strong {
  font-family: var(--font-display);
  font-size: 0.9375rem;
  font-weight: 600;
}

.brand-text em {
  font-style: normal;
  font-size: var(--fs-micro);
  letter-spacing: 0.1em;
  color: var(--slate);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-link {
  padding: 9px var(--space-3);
  border-radius: var(--radius-sm);
  font-size: var(--fs-sm);
  color: var(--ink-70);
  transition: background var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out);
}

.nav-link:hover {
  background: rgba(255, 255, 255, 0.5);
  color: var(--ink);
}

.nav-link.active {
  background: rgba(255, 255, 255, 0.72);
  color: var(--field);
  font-weight: 500;
  box-shadow: var(--shadow-inset-top);
}

.sidebar-foot {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 var(--space-2);
}

.public-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-sm);
  color: var(--field);
}

.tiny {
  font-size: var(--fs-micro);
}

.main {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-width: 0;
}

.topbar {
  position: sticky;
  top: var(--space-4);
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-5);
  border-radius: var(--radius-lg);
}

.page-title {
  font-family: var(--font-display);
  font-size: var(--fs-title);
  font-weight: 600;
}

.ghost-btn {
  padding: 7px var(--space-4);
  border-radius: var(--radius-pill);
  border: 1px solid var(--glass-stroke);
  background: rgba(255, 255, 255, 0.42);
  font-size: var(--fs-sm);
  color: var(--ink-70);
  transition: background var(--dur-fast) var(--ease-out);
}

.ghost-btn:hover {
  background: rgba(255, 255, 255, 0.7);
}

.content {
  min-width: 0;
}

@media (max-width: 900px) {
  .console {
    grid-template-columns: minmax(0, 1fr);
    padding: var(--space-3);
  }

  .sidebar {
    position: static;
    min-height: 0;
    border-radius: var(--radius-lg);
    padding: var(--space-4);
    gap: var(--space-4);
  }

  .nav {
    flex-direction: row;
    overflow-x: auto;
    gap: 6px;
    padding-bottom: 2px;
  }

  .nav-link {
    white-space: nowrap;
  }

  .sidebar-foot {
    display: none;
  }

  .topbar {
    position: static;
  }
}
</style>
