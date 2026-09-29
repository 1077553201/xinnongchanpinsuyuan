<script setup lang="ts">
import { RouterView } from 'vue-router'
import AmbientField from '@/components/AmbientField.vue'
</script>

<template>
  <div class="app-shell">
    <AmbientField />
    <RouterView />
  </div>
</template>

<style scoped>
.app-shell {
  position: relative;
  z-index: 1;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
</style>
