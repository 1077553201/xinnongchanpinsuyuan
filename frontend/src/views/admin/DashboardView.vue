<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import StatusChip from '@/components/StatusChip.vue'
import ResourceState from '@/components/ResourceState.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { chainHealthBadge } from '@/utils/chainHealth'
import { chainHealth } from '@/api/chain'
import { listBatches, listItems, listProducts } from '@/api/catalog'
import { listAlerts } from '@/api/iot'
import { alertTypeLabel } from '@/types/iot'
import { formatDateTime, formatNumber, metricMeta, shortHash } from '@/types/api'
const products = useAsyncResource(listProducts)
const batches = useAsyncResource(listBatches)
const items = useAsyncResource(listItems)
const alerts = useAsyncResource(() => listAlerts({ limit: 6 }))
const health = useAsyncResource(chainHealth)
const chain = health.data
const badge = computed(() => chainHealthBadge(chain.value))
const resources = [products, batches, items, alerts, health]
const loading = computed(() => resources.some((resource) => resource.loading.value))
const hasErrors = computed(() => resources.some((resource) => resource.error.value))
const cards = [
  { label: '产品', resource: products, to: '/admin/products', hint: '当前产品记录' },
  { label: '批次', resource: batches, to: '/admin/products', hint: '当前批次记录' },
  { label: '单品码', resource: items, to: '/admin/items', hint: '当前单品记录' },
  { label: '最近告警', resource: alerts, to: '/admin/iot', hint: '最近记录，最多 6 条，含已处理' },
]

async function refresh() {
  await Promise.all(resources.map((resource) => resource.load()))
}
onMounted(refresh)
</script>

<template>
  <div class="dash">
    <div class="row-between wrap">
      <p class="muted tiny" role="status">
        {{ loading ? '正在读取最新记录…' : hasErrors ? '部分记录读取失败，可单独重试。' : '当前记录已更新。' }}
      </p>
      <button class="action-button" type="button" :disabled="loading" @click="refresh">刷新总览</button>
    </div>
    <section class="tiles">
      <GlassPanel v-for="card in cards" :key="card.label" class="tile">
        <p class="eyebrow">{{ card.label }}</p>
        <ResourceState
          :loading="card.resource.loading.value"
          :error="card.resource.error.value"
          @retry="card.resource.load"
        >
          <p class="num">{{ card.resource.data.value?.length ?? '—' }}</p>
        </ResourceState>
        <p class="muted tiny">{{ card.hint }}</p>
        <RouterLink :to="card.to" class="tile-link">查看 →</RouterLink>
      </GlassPanel>
    </section>

    <section class="panels">
      <GlassPanel padded="large" class="panel">
        <header class="panel-head">
          <h2 class="panel-title display">链上节点</h2>
          <StatusChip
            :tone="health.loading.value ? 'wait' : health.error.value ? 'bad' : badge.tone"
            :label="health.loading.value ? '读取中' : health.error.value ? '读取失败' : badge.label"
          />
        </header>

        <ResourceState :loading="health.loading.value" :error="health.error.value" @retry="health.load">
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
            <dt>调用账户</dt>
            <dd class="mono" :title="chain?.accountAddress || ''">
              {{ shortHash(chain?.accountAddress, 10, 6) }}
            </dd>
          </div>
          <div class="fact">
            <dt>节点地址</dt>
            <dd class="mono">{{ chain?.peers?.join(', ') || '—' }}</dd>
          </div>
        </dl>

        <p v-if="badge.tone === 'bad'" class="warn">暂时无法确认节点连接，请稍后刷新。</p>
        <p v-else-if="chain?.chainEnabled === false" class="muted tiny">节点状态已读取，写链配置尚未就绪。</p>
        </ResourceState>
      </GlassPanel>

      <GlassPanel padded="large" class="panel">
        <header class="panel-head">
          <h2 class="panel-title display">最近告警</h2>
          <RouterLink to="/admin/iot" class="panel-link">全部 →</RouterLink>
        </header>

        <ResourceState
          :loading="alerts.loading.value"
          :error="alerts.error.value"
          :empty="!alerts.data.value?.length"
          empty-text="暂无告警记录。"
          @retry="alerts.load"
        >
        <ul class="alerts">
          <li v-for="alert in alerts.data.value" :key="alert.id" class="alert">
            <StatusChip tone="bad" :label="alertTypeLabel(alert.alertType)" compact />
            <div class="alert-body">
              <p class="alert-line">
                {{ alert.deviceCode }} · {{ metricMeta(alert.metric).label }} = {{ formatNumber(alert.value) }}
                <span class="muted">{{ metricMeta(alert.metric).unit }}</span>
              </p>
              <p class="muted tiny">{{ formatDateTime(alert.occurredAt) }}</p>
              <p class="muted tiny">{{ alert.status === 'OPEN' ? '待处理' : alert.status === 'RESOLVED' || alert.status === 'CLOSED' ? '已处理' : '处理状态未知' }}</p>
            </div>
          </li>
        </ul>
        </ResourceState>
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
