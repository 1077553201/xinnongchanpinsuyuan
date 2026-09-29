<script setup lang="ts">
import { computed } from 'vue'
import { chainStatusLabel, chainStatusTone, type Tone } from '@/types/api'

/**
 * 链上校验印章 —— 全局唯一的记忆点。
 * 用检验章的中文篆印语言承载「链上已验证」这一核心事实：
 * 边款环绕系统名，中心一字表明状态，四态以字、色、环线三重区分。
 */

let sealCounter = 0

const props = withDefaults(
  defineProps<{
    status?: string
    size?: number
    variant?: 'seal' | 'mark'
    label?: string
    showCaption?: boolean
  }>(),
  {
    size: 112,
    variant: 'seal',
    label: '农产品可信溯源 · 链上存证',
    showCaption: true,
  },
)

const SEAL_STATES: Record<string, { char: string; tone: Tone; ring: 'solid' | 'dashed' | 'notch' }> = {
  CONFIRMED: { char: '验', tone: 'ok', ring: 'solid' },
  PENDING: { char: '待', tone: 'wait', ring: 'dashed' },
  FAILED: { char: '误', tone: 'bad', ring: 'notch' },
  CHAIN_DISABLED: { char: '未', tone: 'off', ring: 'dashed' },
}

const arcId = `verify-seal-arc-${++sealCounter}`

const state = computed(() => SEAL_STATES[props.status ?? ''] ?? SEAL_STATES.CHAIN_DISABLED)
const tone = computed<Tone>(() => (props.status ? chainStatusTone(props.status) : state.value.tone))
const caption = computed(() => (props.status ? chainStatusLabel(props.status) : '未上链'))
</script>

<template>
  <figure class="seal" :class="[`tone-${tone}`, `variant-${variant}`]">
    <svg :width="size" :height="size" viewBox="0 0 120 120" role="img"
      :aria-label="`链上校验印章：${caption}`">
      <defs>
        <path :id="arcId" d="M60,13 a47,47 0 1,1 0,94 a47,47 0 1,1 0,-94" fill="none" />
      </defs>

      <!-- 内盘：柔光底 -->
      <circle class="disc" cx="60" cy="60" r="38" />

      <!-- 外环：四态以环线形态区分 -->
      <circle v-if="variant === 'seal'" class="ring-outer" cx="60" cy="60" r="55" />
      <circle v-if="variant === 'seal'" class="ring-inner" cx="60" cy="60" r="50"
        :stroke-dasharray="state.ring === 'dashed' ? '3 5' : undefined" />
      <line v-if="variant === 'seal' && state.ring === 'notch'" class="notch" x1="60" y1="4" x2="60" y2="16" />

      <!-- 边款 -->
      <text v-if="variant === 'seal'" class="arc">
        <textPath :href="`#${arcId}`" startOffset="50%" text-anchor="middle">{{ label }}</textPath>
      </text>

      <!-- 中心状态字 -->
      <text class="glyph" x="60" y="62" text-anchor="middle" dominant-baseline="central">
        {{ state.char }}
      </text>
    </svg>
    <figcaption v-if="showCaption && variant === 'seal'" class="caption">{{ caption }}</figcaption>
  </figure>
</template>

<style scoped>
.seal {
  margin: 0;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  --seal-color: var(--state-disabled);
  --seal-soft: var(--state-disabled-bg);
}

.tone-ok {
  --seal-color: var(--state-confirmed);
  --seal-soft: rgba(30, 91, 72, 0.14);
}

.tone-wait {
  --seal-color: var(--state-pending);
  --seal-soft: rgba(169, 124, 20, 0.14);
}

.tone-bad {
  --seal-color: var(--state-failed);
  --seal-soft: rgba(190, 58, 43, 0.14);
}

.tone-off {
  --seal-color: var(--state-disabled);
  --seal-soft: rgba(107, 122, 115, 0.12);
}

.disc {
  fill: var(--seal-soft);
  stroke: color-mix(in srgb, var(--seal-color) 22%, transparent);
  stroke-width: 1;
}

.ring-outer {
  fill: none;
  stroke: var(--seal-color);
  stroke-width: 2;
  opacity: 0.9;
}

.ring-inner {
  fill: none;
  stroke: var(--seal-color);
  stroke-width: 0.9;
  opacity: 0.6;
}

.notch {
  stroke: var(--seal-color);
  stroke-width: 2;
  stroke-linecap: round;
}

.arc {
  fill: var(--seal-color);
  font-family: var(--font-body);
  font-size: 9px;
  letter-spacing: 0.18em;
  opacity: 0.82;
}

.glyph {
  fill: var(--seal-color);
  font-family: var(--font-display);
  font-size: 40px;
  font-weight: 600;
}

.variant-mark .glyph {
  font-size: 34px;
}

.caption {
  font-size: var(--fs-micro);
  letter-spacing: 0.12em;
  color: var(--seal-color);
  font-weight: 500;
}

@media (prefers-reduced-motion: no-preference) {
  .tone-wait .ring-inner {
    transform-origin: 60px 60px;
    animation: seal-breathe 4.2s var(--ease-out) infinite;
  }
}

@keyframes seal-breathe {
  0%,
  100% {
    opacity: 0.45;
  }
  50% {
    opacity: 0.85;
  }
}
</style>
