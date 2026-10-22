<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import type { ChatEvidence } from '@/api/chat'
import { formatDateTime } from '@/types/api'

const props = defineProps<{ events: ChatEvidence[]; active: boolean }>()
const reads = computed(() => props.events.filter(e => e.kind === 'READ' || e.tool))
const latest = computed(() => props.events.at(-1))
const clock = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined
watch(() => props.active, active => { clearInterval(timer); if (active) timer = setInterval(() => { clock.value = Date.now() }, 1000) }, { immediate: true })
onBeforeUnmount(() => clearInterval(timer))
const seconds = computed(() => {
  const start = props.events[0]?.observedAt
  if (!start) return 0
  const at = new Date(start + (/(Z|[+-]\d{2}:\d{2})$/.test(start) ? '' : '+08:00')).getTime()
  return Number.isFinite(at) ? Math.max(0, Math.floor((clock.value - at) / 1000)) : 0
})
const labels: Record<string, string> = {
  id: '编号', plot_name: '地块', plotName: '地块', plot_code: '地块编码', cycle_code: '种植周期', cycleCode: '种植周期',
  product_name: '产品', productName: '产品', variety: '品种', status: '状态', planted_on: '种植日期',
  expected_harvest_on: '预计采收', warehouse_name: '仓库', warehouseName: '仓库', warehouse_code: '仓库编码',
  availableCount: '在库件数', availableWeightKg: '在库净重(kg)', packageUnit: '包装单位', packingCode: '包装编码', gradeName: '等级',
  orderCode: '订单', customerName: '客户', totalAmount: '订单金额(元)', orderedOn: '下单日期',
  shipmentCode: '运输单', packageCount: '件数', title: '农事', planned_on: '计划日期', assignee_name: '负责人',
  lotCode: '采收编码', quantityKg: '采收净重(kg)', harvestedAt: '采收时间', performerName: '现场人员',
  area_mu: '面积(亩)', manager_name: '负责人', metric: '监测指标', value: '读数', unit: '单位', reported_at: '采样时间',
  device_code: '设备', action_taken: '处置记录', handled_at: '处置时间'
}
const statuses: Record<string, string> = { ACTIVE: '在种', ENDED: '已结束', PLANNED: '待执行', OPEN: '待处理', DONE: '已完成', CANCELLED: '已取消', PARTIAL: '部分完成', FULFILLED: '已履约', CLOSED: '已关闭', IN_TRANSIT: '运输中', DELIVERED: '已交付', DELIVERY_ISSUE: '交付异常' }
function object(value: unknown): Record<string, unknown> { return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {} }
function rows(event: ChatEvidence): Record<string, unknown>[] {
  const data = Array.isArray(event.data) ? event.data : object(event.data).rows
  return Array.isArray(data) ? data.filter(v => v && typeof v === 'object').slice(0, 20).map(object) : []
}
function columns(event: ChatEvidence) {
  const keys = [...new Set(rows(event).flatMap(row => Object.keys(row)))].filter(key => key in labels)
  const preferred = event.tool === 'CYCLES' ? ['plot_name','product_name','variety','status','planted_on','expected_harvest_on','cycle_code','id'] : event.tool === 'STOCK' ? ['productName','variety','availableCount','packageUnit','availableWeightKg','packingCode','cycleCode','id'] : []
  return [...preferred.filter(key => keys.includes(key)), ...keys.filter(key => !preferred.includes(key))].slice(0, 8)
}
function cell(value: unknown, key: string) {
  if (value == null || value === '') return '—'
  if (key === 'status' && typeof value === 'string') return statuses[value] ?? value
  return typeof value === 'object' ? JSON.stringify(value) : String(value)
}
function conditions(event: ChatEvidence) {
  const q = event.query
  return [q?.targetId ? `来源编号 ${q.targetId}` : '', q?.search ? `关键词「${q.search}」` : '', q?.status ? `状态：${statuses[q.status] ?? q.status}` : ''].filter(Boolean).join(' · ') || '当前账号权限范围，默认查询'
}
function time(value?: string) { return value ? formatDateTime(value).split(' ').at(-1) : '' }
</script>

<template>
  <section v-if="events.length" class="run-trace" aria-label="执行轨迹">
    <details class="run-steps" :open="active">
      <summary><span class="run-dot" :class="{ working: active }"></span><strong>{{ active ? latest?.label : '执行轨迹' }}</strong><span>{{ reads.length }} 次查询{{ active ? ` · 已用 ${seconds} 秒` : ' · 已结束' }}</span><span aria-hidden="true">⌄</span></summary>
      <ol>
        <li v-for="(event,index) in events" :key="index" :class="{ failed: event.state === 'FAILED' || event.success === false }">
          <div class="step-heading"><span>{{ event.state === 'RUNNING' ? '进行中' : event.state === 'FAILED' || event.success === false ? '未完成' : '完成' }}</span><strong>{{ event.label }}</strong><time>{{ time(event.observedAt) }}</time><small v-if="event.durationMs !== undefined">{{ (event.durationMs / 1000).toFixed(1) }} 秒</small></div>
          <p v-if="event.query">{{ conditions(event) }}</p><p v-if="event.note">{{ event.note }}</p>
        </li>
      </ol>
    </details>
    <div v-if="reads.length" class="query-results" aria-label="查询记录与结果">
      <details v-for="(event,index) in reads" :key="index" class="query-result">
        <summary><strong>{{ event.label }}</strong><span>{{ event.state === 'RUNNING' ? '查询中…' : event.note || (event.success ? '查询完成' : '未取得记录') }}</span><span aria-hidden="true">⌄</span></summary>
        <div class="result-body"><p class="query-condition">{{ conditions(event) }} · {{ event.observedAt ? formatDateTime(event.observedAt) : '旧记录未保存查询时间' }}</p>
          <div v-if="rows(event).length && columns(event).length" class="record-table" role="region" :aria-label="`${event.label}返回明细`" tabindex="0"><table><thead><tr><th v-for="key in columns(event)" :key="key">{{ labels[key] }}</th></tr></thead><tbody><tr v-for="(row,rowIndex) in rows(event)" :key="rowIndex"><td v-for="key in columns(event)" :key="key">{{ cell(row[key],key) }}</td></tr></tbody></table></div>
          <p v-else>{{ event.state === 'RUNNING' ? '正在读取，结果返回后会显示在这里。' : event.data !== undefined && event.success ? '这次查询未返回符合条件的明细。' : '这条历史记录未保存可展示的明细，或查询未完成。可重新发起查询。' }}</p>
          <p v-if="event.limit" class="result-limit">本次明细最多 {{ event.limit }} 条，请按具体来源继续查询更多记录。</p>
          <RouterLink v-if="event.success && event.link?.startsWith('/admin/')" :to="event.link">打开{{ event.label }}核对 ↗</RouterLink>
        </div>
      </details>
    </div>
  </section>
</template>

<style scoped>
.run-trace { margin: 14px 0 18px; font-size: var(--fs-xs); min-width: 0; }
summary { cursor: pointer; list-style: none; } summary::-webkit-details-marker { display: none; }
.run-steps > summary { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; color: var(--slate); padding: 8px 0; }
.run-steps strong { font-weight: 500; color: var(--ink); }.run-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--field); flex-shrink: 0; }.working { animation: pulse 1.6s ease-in-out infinite; }
ol { list-style: none; margin: 8px 0 16px 3px; padding: 0 0 0 16px; border-left: 1px solid var(--glass-stroke); }
li { padding: 6px 0 10px; line-height: 1.7; }.step-heading { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }.step-heading > span { color: var(--field); }.step-heading time,.step-heading small,li p { color: var(--slate); }.step-heading time { margin-left: auto; }.failed .step-heading > span { color: var(--seal); }
.query-results { display: grid; gap: 8px; }.query-result { border: 1px solid var(--glass-stroke); border-radius: 12px; background: var(--glass-fill); min-width: 0; }.query-result summary { display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px 12px; padding: 12px 14px; line-height: 1.7; }.query-result summary strong { color: var(--field); font-weight: 500; }.query-result summary > span:nth-child(2) { flex: 1 1 180px; color: var(--slate); }.result-body { padding: 0 14px 14px; line-height: 1.8; min-width: 0; overflow-wrap: anywhere; }.query-condition,.result-limit { color: var(--slate); margin-bottom: 8px; }.result-limit { margin-top: 8px; }.result-body a { color: var(--field); display: inline-block; margin-top: 8px; text-decoration: underline; text-underline-offset: 3px; }.record-table { overflow-x: auto; max-width: 100%; border-radius: 8px; border: 1px solid var(--glass-hairline); }table { width: 100%; border-collapse: collapse; font-size: var(--fs-xs); }th,td { text-align: left; padding: 9px 10px; border-bottom: 1px solid var(--glass-hairline); min-width: 72px; max-width: 220px; overflow-wrap: anywhere; vertical-align: top; }th { font-weight: 500; color: var(--slate); background: var(--field-tint); }tbody tr:last-child td { border-bottom: 0; }
summary:focus-visible,.record-table:focus-visible { outline: 2px solid var(--field); outline-offset: 3px; }@keyframes pulse { 50% { opacity: .35; } }@media(prefers-reduced-motion:reduce) { .working { animation: none; } }
</style>
