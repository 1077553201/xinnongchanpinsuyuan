<script setup lang="ts">
import { ref, useId } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

defineProps<{ items: { to: string; label: string }[] }>()
const route = useRoute()
const router = useRouter()
const traceCode = ref('')
const traceError = ref('')
const traceId = useId()

function openTrace() {
  const code = traceCode.value.trim()
  if (!code) { traceError.value = '请填写溯源编号。'; return }
  traceError.value = ''
  void router.push({ name: 'public-trace', params: { traceCode: code } })
}
</script>

<template>
  <div class="navigation-content">
    <RouterLink to="/admin" class="brand">
      <span class="brand-mark" aria-hidden="true">溯</span>
      <span class="brand-text"><strong>农产管理与溯源</strong><em>AgriTrace Console</em></span>
    </RouterLink>
    <nav class="nav" aria-label="后台导航">
      <RouterLink v-for="item in items" :key="item.to" :to="item.to" class="nav-link"
        :class="{ active: item.to === '/admin' ? route.path === '/admin' : route.path.startsWith(item.to) }">
        {{ item.label }}
      </RouterLink>
    </nav>
    <form class="trace-search" @submit.prevent="openTrace">
      <label class="field-label" :for="traceId">查询单品记录</label>
      <input :id="traceId" v-model="traceCode" class="field-input" placeholder="输入溯源编号"
        maxlength="256" :aria-invalid="!!traceError" :aria-describedby="traceError ? `${traceId}-error` : undefined" @input="traceError = ''">
      <p v-if="traceError" :id="`${traceId}-error`" class="field-error" role="alert">{{ traceError }}</p>
      <button class="action-button" type="submit">打开公众查询页 ↗</button>
    </form>
  </div>
</template>

<style scoped>
.navigation-content { display: flex; flex-direction: column; flex: 1 0 auto; gap: 24px; min-height: 0; }
.brand { display: flex; align-items: center; gap: 10px; padding: 0 8px; }
.brand-mark { display: grid; place-items: center; flex: 0 0 36px; height: 36px; border-radius: 12px; background: var(--field); color: var(--mist); font-family: var(--font-display); font-size: 1.125rem; box-shadow: 0 8px 22px -12px rgba(30,91,72,.9); }
.brand-text { display: flex; flex-direction: column; line-height: 1.3; }
.brand-text strong { font-family: var(--font-display); font-size: .9375rem; font-weight: 600; }
.brand-text em { font-style: normal; font-size: var(--fs-micro); letter-spacing: .1em; color: var(--slate); }
.nav { display: flex; flex-direction: column; gap: 4px; }
.nav-link { display: flex; align-items: center; min-height: 44px; padding: 10px 12px; border-radius: var(--radius-sm); font-size: var(--fs-sm); color: var(--ink-70); transition: background var(--dur-fast) var(--ease-out), color var(--dur-fast) var(--ease-out); }
.nav-link:hover { background: var(--glass-fill); color: var(--ink); }
.nav-link.active { background: var(--glass-fill-strong); color: var(--field); font-weight: 500; box-shadow: var(--shadow-inset-top); }
.trace-search { display: grid; gap: 8px; margin-top: auto; padding: 0 8px; }
</style>
