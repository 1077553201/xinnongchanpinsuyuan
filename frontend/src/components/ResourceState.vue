<script setup lang="ts">
withDefaults(defineProps<{
  loading?: boolean
  error?: string | null
  empty?: boolean
  emptyText?: string
}>(), {
  emptyText: '暂无记录。',
})

defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="resource-state" role="status" aria-live="polite">
    <span class="loading-dot" aria-hidden="true"></span>
    正在读取…
  </div>
  <div v-else-if="error" class="resource-state failed" role="alert">
    <p>{{ error }}</p>
    <button class="action-button" type="button" @click="$emit('retry')">重新加载</button>
  </div>
  <p v-else-if="empty" class="resource-state" role="status">{{ emptyText }}</p>
  <slot v-else />
</template>

<style scoped>
.resource-state {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  color: var(--slate);
  font-size: var(--fs-sm);
  overflow-wrap: anywhere;
}
.failed { color: var(--seal); }
.loading-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--grain);
}
</style>
