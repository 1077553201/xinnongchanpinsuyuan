<script setup lang="ts">
import { computed } from 'vue'

/**
 * 玻璃面板：整个界面的承载基元。
 * glow 用于柔光强调，按约定全场只保留一处高强度柔光。
 */
const props = defineProps<{
  strong?: boolean
  quiet?: boolean
  glow?: 'field' | 'warm' | 'seal'
  interactive?: boolean
  padded?: 'tight' | 'normal' | 'large' | false
}>()

const classes = computed(() => [
  'glass',
  {
    'glass-strong': props.strong,
    'glass-quiet': props.quiet,
    'glass-interactive': props.interactive,
    'glow-field': props.glow === 'field',
    'glow-warm': props.glow === 'warm',
    'glow-seal': props.glow === 'seal',
  },
])

const padClass = computed(() => {
  if (props.padded === false) return ''
  if (props.padded === 'tight') return 'glass-pad-tight'
  if (props.padded === 'large') return 'glass-pad-lg'
  return 'glass-pad'
})
</script>

<template>
  <div :class="[...classes, padClass]">
    <slot />
  </div>
</template>
