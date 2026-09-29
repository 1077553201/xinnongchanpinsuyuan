<script setup lang="ts">
import StatusChip from './StatusChip.vue'
import VerifySeal from './VerifySeal.vue'
import { eventTypeLabel, formatDateTime, shortHash } from '@/types/api'
import type { TraceEvent } from '@/types/trace'

defineProps<{ events: TraceEvent[] }>()
</script>

<template>
  <ol class="timeline">
    <li v-for="(event, index) in events" :key="`${event.eventType}-${event.eventTime}-${index}`" class="entry">
      <div class="rail" aria-hidden="true">
        <span class="node">
          <VerifySeal :status="event.chainStatus" variant="mark" :size="30" :show-caption="false" />
        </span>
        <span class="line"></span>
      </div>

      <div class="body">
        <div class="when">
          <h3 class="title">{{ eventTypeLabel(event.eventType) }}</h3>
          <time class="mono time">{{ formatDateTime(event.eventTime) }}</time>
        </div>

        <div class="split">
          <div class="offchain">
            <p class="muted place">{{ event.location || '地点未记录' }}</p>
            <p class="muted operator">{{ event.operatorName || '操作人未记录' }}</p>
          </div>

          <div class="onchain">
            <StatusChip :status="event.chainStatus" compact />
            <span class="mono hash" :title="event.payloadHash || ''">
              {{ shortHash(event.payloadHash) }}
            </span>
          </div>
        </div>
      </div>
    </li>

    <li v-if="!events.length" class="empty muted">尚无农事记录</li>
  </ol>
</template>

<style scoped>
.timeline {
  display: flex;
  flex-direction: column;
  container-type: inline-size;
}

.entry {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: var(--space-3);
}

.rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.node {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--glass-fill-strong);
  border: 1px solid var(--glass-stroke);
  flex: none;
}

.line {
  flex: 1 1 auto;
  width: 1px;
  min-height: 18px;
  background: linear-gradient(180deg, rgba(30, 91, 72, 0.28), rgba(30, 91, 72, 0.06));
}

.entry:last-child .line {
  display: none;
}

.body {
  padding-bottom: var(--space-5);
  min-width: 0;
}

.when {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.title {
  font-family: var(--font-display);
  font-size: var(--fs-h3);
  color: var(--ink);
}

.time {
  color: var(--slate);
  white-space: nowrap;
}

.split {
  margin-top: 6px;
  display: grid;
  gap: var(--space-2);
}

.place,
.operator {
  font-size: var(--fs-sm);
}

.onchain {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.hash {
  color: var(--slate);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty {
  padding: var(--space-4) 0;
  font-size: var(--fs-sm);
}

@container (min-width: 420px) {
  .split {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: start;
  }

  .onchain {
    justify-content: flex-end;
  }
}
</style>
