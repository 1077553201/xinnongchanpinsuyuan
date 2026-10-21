<script setup lang="ts">
import { useTheme, type Theme } from '@/composables/useTheme'
const { theme, applyTheme } = useTheme()
function change(event: Event) { applyTheme((event.target as HTMLSelectElement).value as Theme) }
</script>

<template>
  <label class="theme-switcher">
    <span>外观</span>
    <select :value="theme" aria-label="界面外观" @change="change">
      <option value="morning">晨光绿</option>
      <option value="soft-glass">柔光玻璃</option>
    </select>
  </label>
</template>

<style scoped>
.theme-switcher { display: flex; align-items: center; gap: 8px; font-size: var(--fs-xs); color: var(--slate); }
select { min-height: 38px; max-width: 140px; padding: 6px 10px; border: 1px solid var(--ink-16); border-radius: var(--radius-pill); background: var(--glass-fill-strong); color: var(--ink); cursor: pointer; }
@media (max-width: 480px) { .theme-switcher > span { display: none; } }
</style>
