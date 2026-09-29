<script setup lang="ts">
import { computed } from 'vue'
import StatusChip from './StatusChip.vue'
import { formatNumber, metricMeta } from '@/types/api'

const props = defineProps<{
  metric: string
  avg?: string | number | null
  min?: string | number | null
  max?: string | number | null
  sampleCount?: number
  chainStatus?: string
}>()

const meta = computed(() => metricMeta(props.metric))

/** 均值在窗口取值区间中的位置，用于把「数据落在哪」画出来。 */
const markerLeft = computed(() => {
  const min = toNumber(props.min)
  const max = toNumber(props.max)
  const avg = toNumber(props.avg)
  if (min === null || max === null || avg === null || max <= min) return 50
  const ratio = ((avg - min) / (max - min)) * 100
  return Math.min(100, Math.max(0, ratio))
})

function toNumber(value?: string | number | null): number | null {
  if (value === null || value === undefined || value === '') return null
  const num = typeof value === 'number' ? value : Number(value)
  return Number.isNaN(num) ? null : num
}
</script>

<template>
  <div class="metric">
    <div class="head">
      <span class="label">{{ meta.label }}</span>
      <StatusChip :status="chainStatus" compact />
    </div>

    <p class="value">
      <span class="number">{{ formatNumber(avg) }}</span>
      <span v-if="meta.unit" class="unit">{{ meta.unit }}</span>
    </p>

    <div class="track" role="img"
      :aria-label="`${meta.label}窗口区间 ${formatNumber(min)} 至 ${formatNumber(max)}，均值 ${formatNumber(avg)}`">
      <div class="band"></div>
      <span class="marker" :style="{ left: `${markerLeft}%` }"></span>
    </div>

    <div class="foot">
      <span class="mono">{{ formatNumber(min) }}</span>
      <span v-if="sampleCount" class="muted samples">{{ sampleCount }} 次采样</span>
      <span class="mono">{{ formatNumber(max) }}</span>
    </div>
  </div>
</template>

<style scoped>
.metric {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.label {
  font-size: var(--fs-sm);
  color: var(--slate);
}

.value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.number {
  font-family: var(--font-mono);
  font-size: 1.5rem;
  font-weight: 500;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}

.unit {
  font-size: var(--fs-xs);
  color: var(--slate);
}

.track {
  position: relative;
  height: 6px;
  margin: 4px 0 2px;
}

.band {
  position: absolute;
  inset: 0;
  border-radius: var(--radius-pill);
  background: linear-gradient(90deg, var(--grain-tint), rgba(30, 91, 72, 0.16));
}

.marker {
  position: absolute;
  top: 50%;
  width: 9px;
  height: 9px;
  margin-left: -4.5px;
  border-radius: 50%;
  background: var(--field);
  border: 2px solid rgba(255, 255, 255, 0.9);
  transform: translateY(-50%);
  box-shadow: 0 0 0 3px rgba(30, 91, 72, 0.12);
}

.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--slate);
}

.samples {
  font-size: var(--fs-micro);
}
</style>
