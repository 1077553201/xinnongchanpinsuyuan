<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <nav class="inventory-views" aria-label="库存查看方式">
    <RouterLink to="/admin/rejections" class="action-button" :class="{ selected: $route.name === 'admin-rejections' }" :aria-current="$route.name === 'admin-rejections' ? 'page' : undefined">退回与隔离</RouterLink>
    <RouterLink to="/admin/inventory/warehouse" class="action-button" :class="{ selected: $route.name === 'admin-warehouse-stock' }" :aria-current="$route.name === 'admin-warehouse-stock' ? 'page' : undefined">按仓库查看</RouterLink>
    <RouterLink to="/admin/inventory" class="action-button" :class="{ selected: $route.name === 'admin-inventory' }" :aria-current="$route.name === 'admin-inventory' ? 'page' : undefined">按种植来源查看</RouterLink>
  </nav>
</template>

<style scoped>
.inventory-views { position: relative; display: flex; flex-wrap: wrap; gap: 12px; }
.selected { background: var(--field); color: white; border-color: var(--field); }
.inventory-views .selected:hover { background: var(--field-soft); color: white; }
</style>
