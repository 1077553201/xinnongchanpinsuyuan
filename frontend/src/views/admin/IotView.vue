<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import StatusChip from '@/components/StatusChip.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { aggregateWindow, iotStatus, listAlerts, listDevices, listGateways, listReadings, listWindows, pushTelemetry, registerDevice, registerGateway } from '@/api/iot'
import { ApiError } from '@/api/http'
import { METRIC_META, formatDateTime, formatNumber, metricMeta, shortHash } from '@/types/api'
import { eventTime } from '@/utils/items'

const deviceFilter = ref(''), metricFilter = ref(''), gatewayFilter = ref('')
const gateways = useAsyncResource(listGateways), devices = useAsyncResource(listDevices), status = useAsyncResource(iotStatus)
const readings = useAsyncResource(() => listReadings({ deviceCode: deviceFilter.value || undefined, metric: metricFilter.value || undefined, limit: 100 }))
const windows = useAsyncResource(() => listWindows({ deviceCode: deviceFilter.value || undefined, limit: 100 }))
const alerts = useAsyncResource(() => listAlerts({ deviceCode: deviceFilter.value || undefined, limit: 100 }))
const resources = { readings, windows, alerts, devices, gateways }
type Tab = keyof typeof resources
const tab = ref<Tab>('readings')
const current = computed(() => resources[tab.value])
const tabs: { key: Tab; label: string }[] = [{ key: 'readings', label: '近期读数' }, { key: 'windows', label: '窗口摘要' }, { key: 'alerts', label: '异常告警' }, { key: 'devices', label: '设备' }, { key: 'gateways', label: '网关 / 小车' }]
const columns: Record<Tab, { key: string; label: string }[]> = {
  readings: [{ key: 'deviceCode', label: '设备' }, { key: 'gatewayCode', label: '网关' }, { key: 'metric', label: '指标' }, { key: 'value', label: '读数' }, { key: 'reportedAt', label: '采样时间' }, { key: 'batchId', label: '批次 ID' }],
  windows: [{ key: 'deviceCode', label: '设备' }, { key: 'metric', label: '指标' }, { key: 'avgValue', label: '均值' }, { key: 'sampleCount', label: '样本数' }, { key: 'windowStart', label: '窗口开始' }, { key: 'chainStatus', label: '上链状态' }, { key: 'dataHash', label: '数据摘要' }],
  alerts: [{ key: 'deviceCode', label: '设备' }, { key: 'metric', label: '指标' }, { key: 'value', label: '读数' }, { key: 'message', label: '告警内容' }, { key: 'occurredAt', label: '发生时间' }, { key: 'status', label: '处理状态' }],
  devices: [{ key: 'deviceCode', label: '设备编码' }, { key: 'deviceName', label: '名称' }, { key: 'gatewayCode', label: '网关' }, { key: 'deviceType', label: '类型' }, { key: 'lastSeenAt', label: '最近上报' }],
  gateways: [{ key: 'gatewayCode', label: '网关编码' }, { key: 'gatewayName', label: '名称' }, { key: 'farmId', label: '农场 ID' }, { key: 'status', label: '状态' }],
}
const rows = computed(() => ((current.value.data.value ?? []) as unknown as Record<string, unknown>[]).filter(row => (!gatewayFilter.value || row.gatewayCode === gatewayFilter.value)
  && (!deviceFilter.value || !row.deviceCode || row.deviceCode === deviceFilter.value)
  && (!metricFilter.value || !row.metric || row.metric === metricFilter.value)))
const trend = computed(() => {
  if (!metricFilter.value || !deviceFilter.value) return []
  return (readings.data.value ?? []).filter(r => r.metric === metricFilter.value && r.deviceCode === deviceFilter.value).slice().sort((a, b) => a.reportedAt.localeCompare(b.reportedAt))
})
const trendPoints = computed(() => {
  const values = trend.value.map(r => Number(r.value)).filter(Number.isFinite)
  const min = Math.min(...values), max = Math.max(...values)
  return values.map((v, i) => `${20 + i * 560 / Math.max(1, values.length - 1)},${160 - (v - min) * 130 / (max - min || 1)}`).join(' ')
})
const mode = ref<'gateway' | 'device' | 'telemetry' | 'aggregate' | null>(null)
const busy = ref(false), uncertain = ref(false), error = ref(''), notice = ref('')
const draft = reactive<Record<string, string>>({})
const formFields = computed(() => mode.value === 'gateway' ? [['farmId','农场 ID','number'],['gatewayCode','网关编码','text'],['gatewayName','网关名称','text']]
  : mode.value === 'device' ? [['farmId','农场 ID','number'],['gatewayCode','网关编码','text'],['deviceCode','设备编码','text'],['deviceName','设备名称','text']]
  : mode.value === 'telemetry' ? [['farmId','农场 ID','number'],['gatewayCode','网关编码','text'],['deviceCode','设备编码','text'],['value','采样值','number'],['batchCode','关联批次编码（选填）','text']]
  : [['deviceCode','设备编码','text'],['windowStart','窗口开始（北京时间）','datetime-local'],['windowEnd','窗口结束（北京时间）','datetime-local']])
async function refresh() { await Promise.all([gateways.load(), devices.load(), readings.load(), windows.load(), alerts.load(), status.load()]) }
onMounted(refresh)
watch([deviceFilter, metricFilter], () => { void Promise.all([readings.load(), windows.load(), alerts.load()]) })
function open(kind: typeof mode.value) { Object.keys(draft).forEach(key => delete draft[key]); Object.assign(draft, { metric: 'temperature', deviceCode: deviceFilter.value }); mode.value = kind; error.value = ''; uncertain.value = false }
function cell(row: Record<string, unknown>, key: string): string {
  const value = row[key]
  if (value === null || value === undefined || value === '') return '—'
  if (key === 'metric') return metricMeta(String(value)).label
  if (key === 'dataHash') return shortHash(String(value))
  if (['reportedAt','occurredAt','lastSeenAt','windowStart'].includes(key)) return formatDateTime(String(value))
  if (key === 'status') return ({ OPEN: '待处理', CLOSED: '已处理', RESOLVED: '已处理', ACTIVE: '已登记' } as Record<string,string>)[String(value)] ?? String(value)
  if (key === 'value' || key === 'avgValue') return `${formatNumber(value as string)} ${metricMeta(String(row.metric)).unit}`
  return String(value)
}
async function save() {
  if (busy.value || uncertain.value) return
  error.value = ''
  const kind = mode.value
  const farmId = Number(draft.farmId)
  if (kind !== 'aggregate' && (!Number.isSafeInteger(farmId) || farmId < 1)) { error.value = '农场 ID 须为正整数。'; return }
  busy.value = true
  try {
    if (kind === 'gateway') await registerGateway({ farmId, gatewayCode: draft.gatewayCode.trim(), gatewayName: draft.gatewayName?.trim() })
    else if (kind === 'device') await registerDevice({ farmId, gatewayCode: draft.gatewayCode.trim(), deviceCode: draft.deviceCode.trim(), deviceName: draft.deviceName?.trim() })
    else if (kind === 'telemetry') {
      if (!Number.isFinite(Number(draft.value)) || draft.value === undefined || String(draft.value).trim() === '') throw new Error('采样值须为有效数字。')
      await pushTelemetry({ farmId, gatewayCode: draft.gatewayCode.trim(), deviceCode: draft.deviceCode.trim(), metric: draft.metric, value: Number(draft.value), unit: metricMeta(draft.metric).unit, batchCode: draft.batchCode?.trim() || undefined })
    } else {
      const start = eventTime(draft.windowStart), end = eventTime(draft.windowEnd)
      if (end <= start) throw new Error('窗口结束须晚于开始。')
      const result = await aggregateWindow({ deviceCode: draft.deviceCode.trim(), metric: draft.metric, windowStart: start, windowEnd: end })
      if (!result) { notice.value = '该时间范围没有可聚合的读数。'; mode.value = null; return }
    }
    notice.value = kind === 'telemetry' ? '模拟采样已保存，请在读数、窗口和告警中核对结果。' : '操作已完成，请核对下方记录与上链状态。'
    mode.value = null; await refresh()
  } catch (cause) {
    if (!(cause instanceof ApiError)) error.value = (cause as Error).message
    else if (['400','409'].includes(cause.code)) error.value = '编码重复或输入不符合要求，请核对设备、网关及批次。'
    else { uncertain.value = true; error.value = '操作结果未确认，请关闭窗口并刷新核对，不要重复提交。' }
  } finally { busy.value = false }
}
</script>

<template>
  <div class="management stack">
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <GlassPanel class="stack"><div class="toolbar"><div><p class="eyebrow">生产环境</p><h2 class="display">设备、采样与异常</h2></div><button class="action-button" type="button" :disabled="current.loading.value" @click="refresh">刷新监控</button></div>
      <ResourceState :loading="status.loading.value" :error="status.error.value" @retry="status.load"><StatusChip :tone="status.data.value?.mqttConnected ? 'ok' : 'off'" :label="status.data.value?.mqttConnected ? 'MQTT 已连接' : status.data.value?.mqttEnabled ? 'MQTT 未连接' : 'MQTT 订阅未启用'" /></ResourceState>
      <p class="muted tiny">固定网关与巡检小车共用采样通道，按网关编码区分。下方显示最近最多 100 条读数、窗口或告警，筛选仅作用于当前结果；设备身份尚未校验，模拟采样不代表实际硬件测量。</p>
      <div class="actions"><button class="action-button" type="button" @click="open('gateway')">登记网关</button><button class="action-button" type="button" @click="open('device')">登记设备</button><button class="action-button" type="button" @click="open('telemetry')">模拟采样</button><button class="action-button" type="button" @click="open('aggregate')">手动聚合窗口</button></div>
      <div class="actions" role="group" aria-label="监控记录类型"><button v-for="entry in tabs" :key="entry.key" class="action-button" :class="{ primary: tab === entry.key }" type="button" :aria-pressed="tab === entry.key" @click="tab = entry.key">{{ entry.label }}</button></div>
      <form class="filters" @submit.prevent><label>网关 / 小车<select v-model="gatewayFilter" class="field-input"><option value="">全部网关</option><option v-for="gateway in gateways.data.value" :key="gateway.id" :value="gateway.gatewayCode">{{ gateway.gatewayName || gateway.gatewayCode }}</option></select></label><label>设备<select v-model="deviceFilter" class="field-input"><option value="">全部设备</option><option v-for="device in devices.data.value" :key="device.id" :value="device.deviceCode">{{ device.deviceName || device.deviceCode }}</option></select></label><label>指标<select v-model="metricFilter" class="field-input"><option value="">全部指标</option><option v-for="(value, key) in METRIC_META" :key="key" :value="key">{{ value.label }}</option></select></label></form>
      <figure v-if="trend.length > 1 && tab === 'readings'" class="trend"><figcaption>{{ metricMeta(metricFilter).label }}变化 · {{ trend.length }} 次采样（{{ metricMeta(metricFilter).unit }}）</figcaption><svg viewBox="0 0 600 190" role="img" aria-label="所选设备按采样时间排列的近期读数变化"><polyline :points="trendPoints" fill="none" stroke="currentColor" stroke-width="3" /></svg><p class="muted tiny">{{ formatDateTime(trend[0].reportedAt) }} → {{ formatDateTime(trend[trend.length - 1].reportedAt) }}；精确数值见下表。</p></figure>
      <ResourceState :loading="current.loading.value" :error="current.error.value" :empty="!rows.length" empty-text="当前筛选下暂无记录。" @retry="current.load"><div class="table-scroll" tabindex="0" aria-label="传感器监控记录，可横向滚动"><table class="data-table"><caption class="sr-only">{{ tabs.find(entry => entry.key === tab)?.label }}</caption><thead><tr><th v-for="column in columns[tab]" :key="column.key" scope="col">{{ column.label }}</th></tr></thead><tbody><tr v-for="row in rows" :key="String(row.id)"><td v-for="column in columns[tab]" :key="column.key"><StatusChip v-if="column.key === 'chainStatus'" :status="String(row.chainStatus)" compact /><span v-else :title="column.key === 'dataHash' ? String(row.dataHash) : undefined">{{ cell(row, column.key) }}</span></td></tr></tbody></table></div></ResourceState>
      <p class="muted tiny" role="status">{{ rows.length }} 条记录 · 最近更新 {{ current.updatedAt.value?.toLocaleTimeString('zh-CN', { hour12: false }) || '尚未读取' }}</p>
    </GlassPanel>
    <AppDialog :open="mode !== null" :title="mode === 'gateway' ? '登记网关' : mode === 'device' ? '登记设备' : mode === 'telemetry' ? '模拟采样上报' : '手动聚合窗口'" :busy="busy" @close="mode = null"><form @submit.prevent="save"><p v-if="mode === 'telemetry'" class="notice wait">此操作将保存模拟数据，并可能触发告警和摘要上链。请使用测试设备与测试批次。</p><fieldset :disabled="busy || uncertain"><div v-for="field in formFields" :key="field[0]" class="form-field"><label :for="`iot-${field[0]}`" class="field-label">{{ field[1] }}</label><input :id="`iot-${field[0]}`" v-model="draft[field[0]]" class="field-input" :type="field[2]" :step="field[0] === 'value' ? 'any' : field[2] === 'number' ? 1 : undefined" :required="!['gatewayName','deviceName','batchCode'].includes(field[0])" maxlength="128"></div><div v-if="mode === 'telemetry' || mode === 'aggregate'" class="form-field"><label class="field-label" for="iot-metric">指标</label><select id="iot-metric" v-model="draft.metric" class="field-input"><option v-for="(value,key) in METRIC_META" :key="key" :value="key">{{ value.label }}</option></select></div></fieldset><button class="action-button primary" type="submit" :disabled="busy || uncertain">{{ busy ? '提交中…' : '确认保存' }}</button><p v-if="error" class="notice bad" role="alert">{{ error }}</p></form></AppDialog>
  </div>
</template>
<style scoped>h2 { font-size: var(--fs-h2); margin-top: 8px; }.trend { margin: 0; padding: 16px; border-radius: 14px; background: var(--field-tint); color: var(--field); }.trend svg { display: block; width: 100%; max-height: 200px; }.trend figcaption { font-size: var(--fs-sm); }</style>
