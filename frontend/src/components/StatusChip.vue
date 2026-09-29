<script setup lang="ts">
import { computed } from 'vue'
import { chainStatusLabel, chainStatusTone, type Tone } from '@/types/api'

const props = defineProps<{
  status?: string
  label?: string
  tone?: Tone
  compact?: boolean
}>()

const resolvedTone = computed<Tone>(() => props.tone ?? chainStatusTone(props.status))
const resolvedLabel = computed(() => props.label ?? chainStatusLabel(props.status))
</script>

<template>
  <span class="chip" :class="[`tone-${resolvedTone}`, { compact }]">
    <span class="dot" aria-hidden="true"></span>
    <span>{{ resolvedLabel }}</span>
  </span>
</template>

<style scoped>
.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px 3px 8px;
  border-radius: var(--radius-pill);
  font-size: var(--fs-xs);
  line-height: 1.5;
  white-space: nowrap;
  border: 1px solid transparent;
}

.compact {
  padding: 2px 8px 2px 7px;
  font-size: var(--fs-micro);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex: none;
}

.tone-ok {
  color: var(--state-confirmed);
  background: var(--state-confirmed-bg);
  border-color: rgba(30, 91, 72, 0.18);
}

.tone-wait {
  color: var(--state-pending);
  background: var(--state-pending-bg);
  border-color: rgba(169, 124, 20, 0.2);
}

.tone-bad {
  color: var(--state-failed);
  background: var(--state-failed-bg);
  border-color: rgba(190, 58, 43, 0.2);
}

.tone-off {
  color: var(--state-disabled);
  background: var(--state-disabled-bg);
  border-color: rgba(107, 122, 115, 0.2);
}
</style>
