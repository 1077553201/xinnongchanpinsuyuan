<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { useAuth } from '@/composables/useAuth'
defineProps<{ loading?: boolean }>()
defineEmits<{ refresh: [] }>()
const auth = useAuth()
</script>

<template>
  <div class="organization-help muted">
    <RouterLink v-if="auth.isAdmin.value" to="/admin/organizations" target="_blank" rel="noopener" class="text-button">管理机构 ↗</RouterLink>
    <span v-else>机构资料由管理员维护。</span>
    <button type="button" class="text-button" :disabled="loading" @click="$emit('refresh')">{{ loading ? '正在刷新机构…' : '刷新机构选项' }}</button>
  </div>
</template>

<style scoped>
.organization-help { display: flex; flex-wrap: wrap; gap: 8px 16px; font-size: var(--fs-sm); }
.organization-help button { border: 0; background: transparent; padding: 0; min-height: 32px; color: var(--field); font: inherit; cursor: pointer; }
.organization-help a { display: inline-flex; align-items: center; min-height: 32px; }
.organization-help button:disabled { opacity: .55; cursor: default; }
.organization-help button:focus-visible { outline: 2px solid var(--field); outline-offset: 3px; border-radius: 4px; }
</style>
