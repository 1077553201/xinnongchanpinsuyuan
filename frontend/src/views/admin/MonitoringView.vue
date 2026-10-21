<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { ApiError, get, post } from '@/api/http'
import { listCycles, listOrganizations, listPlots } from '@/api/production'
import type { IotDeviceEntity, IotSensorReadingEntity, IotDataWindowEntity } from '@/types/iot'
import { formatDateTime, metricMeta, shortHash } from '@/types/api'

interface Device extends IotDeviceEntity { organizationId?: number }
interface Binding { id: number; deviceId: number; cycleId: number; plotId: number; deviceCode: string; deviceName?: string; plotName: string; cycleCode: string; startedAt: string; endedAt?: string; endReason?: string }
const auth = useAuth()
const route = useRoute()
const cycles = useAsyncResource(listCycles)
const plots = useAsyncResource(listPlots)
const devices = useAsyncResource(() => get<Device[]>('/production/devices'))
const bindings = useAsyncResource(() => get<Binding[]>('/production/bindings'))
const organizations = useAsyncResource(listOrganizations)
const cycleId = ref(typeof route.query.cycle === 'string' ? route.query.cycle : '')
const selectedCycle = computed(() => cycles.data.value?.find(cycle => cycle.id === Number(cycleId.value)))
const selectedPlot = computed(() => plots.data.value?.find(plot => plot.id === selectedCycle.value?.plotId))
const plotMap = computed(() => new Map((plots.data.value ?? []).map(plot => [plot.id, plot])))
const currentBindings = computed(() => (bindings.data.value ?? []).filter(binding => !cycleId.value || binding.cycleId === Number(cycleId.value)))
const readings = useAsyncResource(() => get<IotSensorReadingEntity[]>(`/production/cycles/${Number(cycleId.value)}/readings`))
const windows = useAsyncResource(() => get<IotDataWindowEntity[]>(`/production/cycles/${Number(cycleId.value)}/windows`))
const unassigned = computed(() => (devices.data.value ?? []).filter(device => !device.organizationId))
const eligible = computed(() => (devices.data.value ?? []).filter(device => device.organizationId === selectedPlot.value?.organizationId
  && !(bindings.data.value ?? []).some(binding => binding.deviceId === device.id && !binding.endedAt)))
const loading = computed(() => [cycles, plots, devices, bindings, organizations].some(resource => resource.loading.value))
const mode = ref<'bind' | 'assign' | 'end' | null>(null)
const selectedBinding = ref<Binding | null>(null)
const draft = reactive({ deviceId: '', organizationId: '', reason: '' })
const busy = ref(false), uncertain = ref(false), error = ref(''), notice = ref('')
const title = computed(() => mode.value === 'bind' ? '绑定本轮固定监测' : mode.value === 'assign' ? '确认设备所属机构' : '结束设备绑定')
async function loadData() { if (selectedCycle.value) await Promise.all([readings.load(), windows.load()]) }
async function refresh() { await Promise.all([cycles.load(), plots.load(), devices.load(), bindings.load(), organizations.load()]); await loadData() }
watch(cycleId, loadData)
onMounted(refresh)
function open(value: NonNullable<typeof mode.value>, binding?: Binding) { mode.value = value; selectedBinding.value = binding ?? null; error.value = ''; uncertain.value = false; Object.assign(draft, { deviceId: '', organizationId: '', reason: '' }) }
function close() { if (!busy.value) mode.value = null }
async function save() {
  if (busy.value || uncertain.value) return
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'assign') await post(`/production/devices/${Number(draft.deviceId)}/assign`, { organizationId: Number(draft.organizationId) })
    else if (mode.value === 'bind') await post('/production/bindings', { deviceId: Number(draft.deviceId), cycleId: Number(cycleId.value) })
    else if (selectedBinding.value) await post(`/production/bindings/${selectedBinding.value.id}/end`, { reason: draft.reason })
    notice.value = mode.value === 'end' ? '绑定已结束，历史采集仍按原时间归属。' : mode.value === 'assign' ? '设备所属机构已确认，可以安排固定监测。' : '绑定已生效，从当前时间开始记录本轮来源。'
    mode.value = null; await refresh()
  } catch (cause) {
    if (cause instanceof ApiError && ['400', '403'].includes(cause.code)) error.value = cause.message
    else if (cause instanceof ApiError && cause.code === '409') error.value = '设备已有绑定或所属机构，请关闭窗口刷新核对。'
    else { uncertain.value = true; error.value = '暂时无法确认结果，请关闭窗口刷新核对，不要重复提交。' }
  } finally { busy.value = false }
}
</script>

<template>
  <div class="management stack">
    <GlassPanel padded="large" class="stack">
      <div class="section-head"><div><p class="eyebrow">让每次采集有明确来源</p><h2 class="display">监测跟随本轮种植，历史留在原处。</h2></div><button class="action-button" :disabled="loading" @click="refresh">刷新记录</button></div>
      <p class="muted">固定设备按绑定生效时间确定地块与种植周期。小车跨地巡检将通过独立任务关联，本阶段不按固定设备方式处理。</p>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
      <ResourceState :loading="cycles.loading.value || plots.loading.value" :error="cycles.error.value || plots.error.value" :empty="!cycles.data.value?.length" empty-text="请先登记地块并开启种植周期。" @retry="refresh">
        <label class="form-field">查看种植周期<select v-model="cycleId" class="field-input"><option value="">选择本轮或历史种植</option><option v-for="cycle in cycles.data.value" :key="cycle.id" :value="String(cycle.id)">{{ plotMap.get(cycle.plotId)?.plotName }} · {{ cycle.productName }} / {{ cycle.variety }} · {{ cycle.cycleCode }}{{ cycle.status === 'ENDED' ? '（已结束）' : '' }}</option></select></label>
        <div v-if="selectedCycle" class="actions"><span>{{ selectedPlot?.plotName }} · {{ selectedCycle.productName }} · {{ selectedCycle.variety }}</span><button v-if="auth.canWrite.value && selectedCycle.status === 'ACTIVE'" class="action-button primary" :disabled="loading || !!devices.error.value || !!bindings.error.value" @click="open('bind')">绑定固定设备</button></div>
      </ResourceState>
    </GlassPanel>
    <GlassPanel v-if="auth.isAdmin.value" padded="large" class="stack">
      <div class="section-head"><h2 class="display">设备所属机构</h2><button class="action-button" :disabled="loading || !!devices.error.value || !!organizations.error.value || !unassigned.length" @click="open('assign')">分配未归属设备</button></div>
      <p class="muted">{{ devices.data.value ? `${unassigned.length} 台设备尚未确认所属机构。` : '' }}旧设备与自动发现设备不推测机构，确认后才能绑定。设备注册仍在管理员的“传感器监控”中办理。</p>
      <ResourceState :loading="devices.loading.value" :error="devices.error.value || organizations.error.value" @retry="refresh"><p class="muted">所属机构确认后暂不支持跨机构转移，避免改变历史来源。</p></ResourceState>
    </GlassPanel>
    <GlassPanel padded="large">
      <h2 class="display">{{ selectedCycle ? '本轮绑定历史' : '全部绑定历史' }}</h2>
      <ResourceState :loading="bindings.loading.value" :error="bindings.error.value" :empty="!currentBindings.length" empty-text="暂无绑定记录。选择正在种植的周期后，可安排固定设备监测。" @retry="refresh">
        <div class="table-scroll"><table class="data-table"><thead><tr><th>设备</th><th>地块 / 周期</th><th>生效时间</th><th>结束时间 / 说明</th><th>操作</th></tr></thead><tbody><tr v-for="binding in currentBindings" :key="binding.id"><td>{{ binding.deviceName || binding.deviceCode }}<p class="mono muted">{{ binding.deviceCode }}</p></td><td>{{ binding.plotName }}<p class="mono muted">{{ binding.cycleCode }}</p></td><td>{{ formatDateTime(binding.startedAt) }}</td><td>{{ binding.endedAt ? formatDateTime(binding.endedAt) : '正在监测' }}<p v-if="binding.endReason" class="muted">{{ binding.endReason }}</p></td><td><button v-if="!binding.endedAt && auth.canWrite.value" class="text-button" @click="open('end', binding)">解除绑定</button><span v-else class="muted">—</span></td></tr></tbody></table></div>
      </ResourceState>
    </GlassPanel>
    <template v-if="selectedCycle">
      <GlassPanel padded="large"><div class="section-head"><h2 class="display">本轮环境采集</h2><span class="muted">最近 100 条，含迟到数据</span></div><ResourceState :loading="readings.loading.value" :error="readings.error.value" :empty="!readings.data.value?.length" empty-text="暂无可归到本轮的采集记录。绑定前或来源不明确的数据不会补算到本轮。" @retry="readings.load"><div class="table-scroll"><table class="data-table"><thead><tr><th>采集时间</th><th>设备</th><th>指标</th><th>数值</th><th>归属依据</th></tr></thead><tbody><tr v-for="reading in readings.data.value" :key="reading.id"><td>{{ formatDateTime(reading.reportedAt) }}</td><td class="mono">{{ reading.deviceCode }}</td><td>{{ metricMeta(reading.metric).label }}</td><td>{{ reading.value }} {{ reading.unit }}</td><td>采集时的历史绑定</td></tr></tbody></table></div></ResourceState></GlassPanel>
      <GlassPanel padded="large" class="stack"><div class="section-head"><h2 class="display">环境摘要窗口</h2><span class="muted">最近 50 个窗口</span></div><p class="muted">同一设备换地或换季后分别汇总，不混入旧周期。尚未关联采收批次的窗口只保存链下摘要，不代表已经上链验证。</p><ResourceState :loading="windows.loading.value" :error="windows.error.value" :empty="!windows.data.value?.length" empty-text="暂无本轮环境摘要。" @retry="windows.load"><div class="table-scroll"><table class="data-table"><thead><tr><th>窗口</th><th>指标 / 设备</th><th>范围 / 均值</th><th>样本数</th><th>摘要 / 存证状态</th></tr></thead><tbody><tr v-for="window in windows.data.value" :key="window.id"><td>{{ formatDateTime(window.windowStart) }}<p class="muted">至 {{ formatDateTime(window.windowEnd) }}</p></td><td>{{ metricMeta(window.metric).label }}<p class="mono muted">{{ window.deviceCode }}</p></td><td>{{ window.minValue }} — {{ window.maxValue }}<p class="muted">均值 {{ window.avgValue }}</p></td><td>{{ window.sampleCount }}</td><td class="mono" :title="window.dataHash">{{ shortHash(window.dataHash) }}<p>{{ window.chainStatus === 'CONFIRMED' ? '交易已确认，尚需核验' : window.chainStatus === 'CHAIN_DISABLED' ? '尚未存证' : window.chainStatus === 'FAILED' ? '存证失败' : '等待存证结果' }}</p></td></tr></tbody></table></div></ResourceState></GlassPanel>
    </template>
    <RouterLink to="/admin/production" class="text-button">返回地块与种植 →</RouterLink>
    <AppDialog :open="mode !== null" :title="title" :busy="busy" @close="close"><form class="management" @submit.prevent="save"><fieldset :disabled="busy || uncertain">
      <template v-if="mode === 'assign'"><p class="notice">请核对设备实际所属经营主体。确认后暂不支持跨机构转移。</p><label class="form-field">未归属设备<select v-model="draft.deviceId" class="field-input" required><option value="" disabled>请选择</option><option v-for="device in unassigned" :key="device.id" :value="String(device.id)">{{ device.deviceName || device.deviceCode }} · {{ device.deviceCode }}</option></select></label><label class="form-field">所属机构<select v-model="draft.organizationId" class="field-input" required><option value="" disabled>请选择</option><option v-for="org in organizations.data.value" :key="org.id" :value="String(org.id)">{{ org.name }}</option></select></label></template>
      <template v-else-if="mode === 'bind'"><p class="notice">{{ selectedPlot?.plotName }} · {{ selectedCycle?.cycleCode }}。确认后立即生效，不回填过去的采集来源。</p><label class="form-field">同机构可用设备<select v-model="draft.deviceId" class="field-input" required><option value="" disabled>请选择固定传感器</option><option v-for="device in eligible" :key="device.id" :value="String(device.id)">{{ device.deviceName || device.deviceCode }} · {{ device.deviceCode }}</option></select></label><p v-if="!eligible.length" class="notice wait">暂无可用设备，请先确认所属机构，或解除设备的旧绑定。</p></template>
      <template v-else><p class="notice wait">确认结束 {{ selectedBinding?.deviceCode }} 对本轮的监测？历史绑定保留，解除后新采集不再归到本轮。</p><label class="form-field">解除说明<textarea v-model.trim="draft.reason" class="field-input" rows="3" required maxlength="1000" placeholder="例如 转移到另一块地，或本轮结束"></textarea></label></template>
    </fieldset><p v-if="error" class="notice bad" role="alert">{{ error }}</p><div class="actions form-actions"><button class="action-button primary" type="submit" :disabled="busy || uncertain || (mode === 'bind' && !eligible.length)">{{ busy ? '正在保存…' : '确认执行' }}</button><button class="action-button" type="button" :disabled="busy" @click="close">关闭</button></div></form></AppDialog>
  </div>
</template>
<style scoped>h2 { font-size: var(--fs-h2); line-height: 1.5; }.table-scroll { margin-top: 16px; }.form-actions { margin-top: 20px; }.form-field { margin-top: 16px; }</style>
