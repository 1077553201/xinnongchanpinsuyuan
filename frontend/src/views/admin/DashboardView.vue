<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import StatusChip from '@/components/StatusChip.vue'
import { chainHealth } from '@/api/chain'
import { listBatches, listItems, listProducts } from '@/api/catalog'
import { listAlerts } from '@/api/iot'
import { alertTypeLabel, type IotAlertEntity } from '@/types/iot'
import { formatDateTime, formatNumber, metricMeta, shortHash } from '@/types/api'
import type { ChainHealthResult } from '@/types/chain'

const products = ref<number | null>(null)
const batches = ref<number | null>(null)
const items = ref<number | null>(null)
const alerts = ref<IotAlertEntity[]>([])
const chain = ref<ChainHealthResult | null>(null)
const loading = ref(true)

onMounted(async () => {
  const [p, b, i, a, h] = await Promise.allSettled([
    listProducts(),
    listBatches(),
    listItems(),
    listAlerts({ limit: 6 }),
    chainHealth(),
  ])

  if (p.status === 'fulfilled') products.value = p.value.length
  if (b.status === 'fulfilled') batches.value = b.value.length
  if (i.status === 'fulfilled') items.value = i.value.length
  if (a.status === 'fulfilled') alerts.value = a.value
  if (h.status === 'fulfilled') chain.value = h.value

  loading.value = false
})

function metric(value: number | null): string {
  if (loading.value) return '···'
  return value === null ? '—' : String(value)
}
</script>

<template>
  <div class="dash">
    <section class="tiles">
      <GlassPanel class="tile" interactive>
        <p class="eyebrow">产品</p>
        <p class="num">{{ metric(products) }}</p>
        <RouterLink to="/admin/products" class="tile-link">管理 →</RouterLink>
      </GlassPanel>

      <GlassPanel class="tile" interactive>
        <p class="eyebrow">批次</p>
        <p class="num">{{ metric(batches) }}</p>
        <RouterLink to="/admin/products" class="tile-link">管理 →</RouterLink>
      </GlassPanel>

      <GlassPanel class="tile" interactive>
        <p class="eyebrow">单品码</p>
        <p class="num">{{ metric(items) }}</p>
        <RouterLink to="/admin/items" class="tile-link">管理 →</RouterLink>
      </GlassPanel>

      <GlassPanel class="tile" interactive>
        <p class="eyebrow">待处理告警</p>
        <p class="num" :class="{ alarm: alerts.length > 0 }">{{ alerts.length }}</p>
        <RouterLink to="/admin/iot" class="tile-link">查看 →</RouterLink>
      </GlassPanel>
    </section>

    <section class="panels">
      <GlassPanel padded="large" class="panel">
        <header class="panel-head">
          <h2 class="panel-title display">链上节点</h2>
          <StatusChip
            :tone="chain?.chainEnabled ? 'ok' : 'off'"
            :label="chain?.chainEnabled ? '连接正常' : '未连接'"
          />
        </header>

        <dl class="facts">
          <div class="fact">
            <dt>合约地址</dt>
            <dd class="mono" :title="chain?.contractAddress || ''">
              {{ shortHash(chain?.contractAddress, 12, 8) }}
            </dd>
          </div>
          <div class="fact">
            <dt>群组</dt>
            <dd class="mono">{{ chain?.groupId || '—' }}</dd>
          </div>
          <div class="fact">
            <dt>最新区块</dt>
            <dd class="mono">{{ chain?.latestBlock ?? '—' }}</dd>
          </div>
          <div class="fact">
            <dt>写权限</dt>
            <dd>{{ chain?.writerStatus === true ? '已授权' : chain?.writerStatus === false ? '未授权' : '—' }}</dd>
          </div>
          <div class="fact">
            <dt>出块账户</dt>
            <dd class="mono" :title="chain?.accountAddress || ''">
              {{ shortHash(chain?.accountAddress, 10, 6) }}
            </dd>
          </div>
          <div class="fact">
            <dt>节点地址</dt>
            <dd class="mono">{{ chain?.peers?.join(', ') || '—' }}</dd>
          </div>
        </dl>

        <p v-if="chain?.errorMessage" class="warn">{{ chain.errorMessage }}</p>
      </GlassPanel>

      <GlassPanel padded="large" class="panel">
        <header class="panel-head">
          <h2 class="panel-title display">最近告警</h2>
          <RouterLink to="/admin/iot" class="panel-link">全部 →</RouterLink>
        </header>

        <ul v-if="alerts.length" class="alerts">
          <li v-for="alert in alerts" :key="alert.id" class="alert">
            <StatusChip tone="bad" :label="alertTypeLabel(alert.alertType)" compact />
            <div class="alert-body">
              <p class="alert-line">
                {{ alert.deviceCode }} · {{ metricMeta(alert.metric).label }} = {{ formatNumber(alert.value) }}
                <span class="muted">{{ metricMeta(alert.metric).unit }}</span>
              </p>
              <p class="muted tiny">{{ formatDateTime(alert.occurredAt) }}</p>
            </div>
          </li>
        </ul>
        <p v-else class="muted empty">
          {{ loading ? '加载中…' : '没有未处理的告警。' }}
        </p>
      </GlassPanel>
    </section>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-3);
}

.tile {
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.num {
  font-family: var(--font-mono);
  font-size: 1.75rem;
  font-weight: 500;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.num.alarm {
  color: var(--seal);
}

.tile-link {
  font-size: var(--fs-xs);
  color: var(--field);
}

.panels {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
}

.panel {
  border-radius: var(--radius-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.panel-title {
  font-size: var(--fs-h2);
  font-weight: 600;
}

.panel-link {
  font-size: var(--fs-xs);
  color: var(--field);
}

.facts {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.fact dt {
  font-size: var(--fs-micro);
  letter-spacing: 0.1em;
  color: var(--slate);
  text-transform: uppercase;
}

.fact dd {
  margin: 4px 0 0;
  font-size: var(--fs-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.alerts {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.alert {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: start;
}

.alert-line {
  font-size: var(--fs-sm);
}

.tiny {
  font-size: var(--fs-micro);
}

.warn {
  margin: 0;
  font-size: var(--fs-xs);
  color: var(--seal);
  word-break: break-all;
}

.empty {
  font-size: var(--fs-sm);
  padding: var(--space-3) 0;
}

@media (max-width: 1080px) {
  .tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .panels {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 560px) {
  .facts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
