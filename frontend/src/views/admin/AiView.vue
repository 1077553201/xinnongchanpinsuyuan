<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { agentStatus, agentContext, agentReports, agentDetail, createAgentReport, reviseAgentReport, confirmAgentReport, resumeAgentReport, type AgentOperation } from '@/api/agent'
import { localDateTime, newOperationKey, operationError } from '@/utils/growing'
import { formatDateTime } from '@/types/api'

const auth = useAuth(), route = useRoute()
const status = useAsyncResource(agentStatus), page = ref(1), id = ref(0), source = ref(''), date = ref(localDateTime().slice(0, 10))
const reports = useAsyncResource(() => agentReports(page.value)), detail = useAsyncResource(() => agentDetail(id.value))
const query = ref(''), applied = ref(''), context = useAsyncResource(() => agentContext(applied.value))
const operations = ref<AgentOperation[]>([]), selected = ref<string[]>([]), dirty = ref(false), busy = ref(false), error = ref(''), notice = ref(''), dialog = ref(false), key = ref('')
const report = computed(() => detail.data.value?.report)
const running = computed(() => detail.data.value?.confirmations.find(c => c.status === 'RUNNING'))
const success = (operation: string) => detail.data.value?.completed.find(r => r.operationId === operation)
const ready = (op: AgentOperation) => op.kind !== 'NOTE' && !op.issues.length && !!op.contextHash && !success(op.id)
const editable = computed(() => auth.canWrite.value && report.value?.status === 'READY' && !busy.value)
const chosen = computed(() => operations.value.filter(op => selected.value.includes(op.id)))
const names = { HARVEST: '登记实际采收', TRANSPORT_EVENT: '登记运输事实', NOTE: '待办 / 暂不执行' }
const sources = (op: AgentOperation) => op.kind === 'HARVEST' ? context.data.value?.cycles ?? [] : context.data.value?.shipments ?? []
interface Field { key: string; label: string; type?: string; options?: Record<string, string>; max?: number }
const harvestFields: Field[] = [
  { key: 'lotCode', label: '采收批次编码', max: 64 }, { key: 'harvestedAt', label: '实际采收时间', type: 'datetime-local' },
  { key: 'quantity', label: '实际采收数量', type: 'decimal' }, { key: 'unit', label: '数量单位', options: { kg: '千克', g: '克', jin: '斤（500克）' } },
  { key: 'performerName', label: '实际采收人员', max: 128 }, { key: 'notes', label: '采收说明（选填）', type: 'textarea', max: 2000 },
]
const transportFields: Field[] = [
  { key: 'kind', label: '运输事实类型', options: { DEPART: '实际发运', UPDATE: '运输进展', EXCEPTION: '运输异常', RECEIVE: '收货结果' } },
  { key: 'occurredAt', label: '实际发生时间', type: 'datetime-local' }, { key: 'location', label: '实际地点', max: 256 },
  { key: 'performerName', label: '现场办理 / 收货人员', max: 128 }, { key: 'description', label: '实际情况说明', type: 'textarea', max: 1000 },
  { key: 'reference', label: '凭证引用（选填）', max: 128 }, { key: 'acceptedCount', label: '实收件数', type: 'number' }, { key: 'rejectedCount', label: '拒收件数', type: 'number' },
]
const fields = (op: AgentOperation) => (op.kind === 'HARVEST' ? harvestFields : transportFields).filter(f => !['acceptedCount', 'rejectedCount'].includes(f.key) || op.fields.kind === 'RECEIVE')
function value(op: AgentOperation, field: Field) { const raw = String(op.fields[field.key] ?? ''); return field.type === 'datetime-local' ? raw.replace(' ', 'T') : raw }
function change(op: AgentOperation, field: Field, event: Event) {
  const text = (event.target as HTMLInputElement).value
  if (text === '') delete op.fields[field.key]
  else op.fields[field.key] = field.type === 'number' ? Number(text) : field.type === 'datetime-local' ? text.replace('T', ' ') + (text.length === 16 ? ':00' : '') : text
  if (field.key === 'kind' && text !== 'RECEIVE') { delete op.fields.acceptedCount; delete op.fields.rejectedCount }
  dirty.value = true; selected.value = []
}
function kindChanged(op: AgentOperation) { op.fields = {}; op.targetId = null; dirty.value = true; selected.value = [] }
async function load(next = id.value) {
  if (busy.value) return
  id.value = next; await detail.load(); operations.value = JSON.parse(JSON.stringify(detail.data.value?.operations ?? [])); selected.value = []; dirty.value = false
}
async function refresh() { await reports.load(); if (id.value) await load() }
onMounted(async () => { await Promise.all([status.load(), context.load(), reports.load()]); const queryId = Number(route.query.report); if (Number.isSafeInteger(queryId) && queryId > 0) await load(queryId) })
async function create() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { const created = await createAgentReport({ sourceText: source.value, reportDate: date.value, requestKey: newOperationKey() }); id.value = created.id; notice.value = '事项已整理为草稿，尚未执行业务；相同日期和原文会回到原日报。' }
  catch (cause) { error.value = operationError(cause) + ' 如连接中断，请先刷新历史核对原日报。' }
  finally { busy.value = false }
  await refresh()
}
async function save() {
  if (!report.value || busy.value) return
  busy.value = true; error.value = ''
  try { await reviseAgentReport(id.value, report.value.version, operations.value); notice.value = '新版本已保存并重新核对来源，请勾选本次要办理的事项。' }
  catch (cause) { error.value = operationError(cause); return }
  finally { busy.value = false }
  await refresh()
}
function openConfirmation() { if (dirty.value || !chosen.value.length || !chosen.value.every(ready)) return; key.value = newOperationKey(); error.value = ''; dialog.value = true }
async function execute(resume = false) {
  if (!report.value || busy.value) return
  busy.value = true; error.value = ''
  try {
    if (resume && running.value) await resumeAgentReport(id.value, running.value.id)
    else await confirmAgentReport(id.value, report.value.version, [...selected.value], key.value)
    dialog.value = false; notice.value = '本次确认已处理，请逐项核对实际回执；失败事项没有按成功落账。'
  } catch (cause) { dialog.value = false; error.value = operationError(cause) + ' 请刷新日报，若仍在执行可恢复原确认，避免重复另建。' }
  finally { busy.value = false }
  await refresh()
}
async function searchSources() { applied.value = query.value.trim(); await context.load() }
async function turn(next: number) { page.value = next; await reports.load() }
function link(op: AgentOperation) { return op.kind === 'HARVEST' ? { path: '/admin/harvest', query: { cycle: op.targetId } } : { path: '/admin/transport', query: { shipment: op.targetId } } }
</script>

<template>
  <div class="management stack">
    <GlassPanel padded="large" class="stack"><p class="eyebrow">日报整理 · 逐项核对 · 确认办理</p><h2 class="display">把一天的事，变成可确认的操作。</h2><ResourceState :loading="status.loading.value" :error="status.error.value" @retry="status.load"><p class="notice">{{ status.data.value?.mode === 'RULES' ? '当前为本地规则整理，尚未启用大模型自然语言理解。规则只识别部分采收表达，其余保留为待办。' : status.data.value?.configured ? '模型整理已配置；生成内容仍需人工核对。' : '模型配置不完整，请补齐后再整理。' }}</p></ResourceState><p class="muted">首版可办理实际采收和运输事实。包装、入库、销售、设备操作等保留待办，不自动执行；先补齐具体时间、人员和来源，再选择确认。所有时间按北京时间（UTC+8）核对。</p>
      <form v-if="auth.canWrite.value" class="stack" @submit.prevent="create"><label class="form-field">日报日期<input v-model="date" class="field-input" type="date" :max="localDateTime().slice(0, 10)" required :disabled="busy"></label><label class="form-field">当天发生的事<textarea v-model="source" class="field-input" rows="6" maxlength="12000" required :disabled="busy" placeholder="例如：今天东棚采收了80斤草莓，下午发现运输外箱损坏，明早准备送货。请写清已经发生与尚未执行的计划。"></textarea></label><div class="actions"><button class="action-button primary" :disabled="busy">{{ busy ? '正在处理…' : '整理为操作草稿' }}</button><button type="button" class="action-button" :disabled="busy" @click="refresh">刷新与核对历史</button></div></form><p v-else class="muted">只读账号可查看自己已有的日报，不可整理、修改或执行。</p>
    </GlassPanel>
    <p v-if="error && !dialog" class="notice bad" role="alert">{{ error }}</p><p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <GlassPanel padded="large" class="stack"><h2 class="display">我的日报</h2><ResourceState :loading="reports.loading.value" :error="reports.error.value" :empty="!reports.data.value?.rows.length" empty-text="还没有提交日报。" @retry="reports.load"><div class="report-grid"><button v-for="item in reports.data.value?.rows" :key="item.id" class="report-card" :disabled="busy" @click="load(item.id)"><strong>{{ item.reportDate }} · #{{ item.id }} · v{{ item.version }}</strong><span>{{ item.sourceText }}</span><span class="muted">{{ item.status === 'RUNNING' ? '有待核对的执行确认' : '可查看草稿与回执' }}</span></button></div></ResourceState><div v-if="reports.data.value" class="section-head"><p class="muted">共 {{ reports.data.value.total }} 篇 · 第 {{ reports.data.value.page }} 页</p><div class="actions"><button class="action-button" :disabled="busy || reports.loading.value || reports.data.value.page <= 1" @click="turn((reports.data.value?.page ?? 1) - 1)">上一页</button><button class="action-button" :disabled="busy || reports.loading.value || reports.data.value.page * 20 >= reports.data.value.total" @click="turn((reports.data.value?.page ?? 1) + 1)">下一页</button></div></div></GlassPanel>
    <GlassPanel v-if="id" padded="large" class="stack"><ResourceState :loading="detail.loading.value" :error="detail.error.value" @retry="load()"><template v-if="report"><div class="section-head"><div><p class="eyebrow">{{ report.mode === 'RULES' ? '本地规则草稿' : '模型建议草稿' }} · 北京时间</p><h2 class="display">{{ report.reportDate }} 日报 #{{ report.id }} · 版本 {{ report.version }}</h2></div><button class="action-button" :disabled="busy" @click="load()">重新读取</button></div><details><summary>查看保留的原始日报</summary><p class="pre-wrap source-text">{{ report.sourceText }}</p></details><p v-if="dirty" class="notice wait">有未保存修改，请先保存新版本，再重新勾选确认事项。</p><p v-if="report.status === 'RUNNING'" class="notice wait">原确认尚未结束。恢复时只核对原确认中的事项，已落账结果不会重复办理。</p><button v-if="auth.canWrite.value && running" class="action-button primary" :disabled="busy" @click="execute(true)">恢复原确认并核对结果</button>
      <form v-if="editable" class="filters" @submit.prevent="searchSources"><label>查找可关联的来源<input v-model="query" class="field-input" maxlength="128" placeholder="种植周期编码、产品或运输单编码"></label><button class="action-button" :disabled="context.loading.value">查找来源</button><p class="muted">每类候选最多50项，请用编码缩小范围。</p></form><ResourceState :loading="context.loading.value" :error="context.error.value" @retry="context.load" />
      <article v-for="(op,index) in operations" :key="op.id" class="operation-card stack"><div class="section-head"><strong>事项 {{ index + 1 }} · {{ names[op.kind] }}</strong><label v-if="editable && !dirty && ready(op)" class="selection"><input v-model="selected" type="checkbox" :value="op.id"> 本次办理</label><span v-if="success(op.id)" class="notice">已成功 · 业务 #{{ success(op.id)?.businessId }}</span></div><blockquote class="quote">{{ op.quote }}</blockquote><fieldset :disabled="!editable || !!success(op.id)"><label class="form-field">办理类型<select v-model="op.kind" class="field-input" @change="kindChanged(op)"><option value="NOTE">待办 / 暂不执行</option><option value="HARVEST">登记实际采收</option><option value="TRANSPORT_EVENT">登记运输事实</option></select></label><template v-if="op.kind !== 'NOTE'"><label class="form-field">{{ op.kind === 'HARVEST' ? '实际种植周期' : '实际运输单' }}<select v-model="op.targetId" class="field-input" @change="dirty = true; selected = []"><option :value="null">请选择明确来源</option><option v-if="op.targetId && !sources(op).some(item => item.id === op.targetId)" :value="op.targetId">已选来源 #{{ op.targetId }}（可搜索编码核对）</option><option v-for="item in sources(op)" :key="item.id" :value="item.id">{{ item.label }}</option></select></label><div class="form-grid"><label v-for="field in fields(op)" :key="field.key" class="form-field" :class="{ full: field.type === 'textarea' }">{{ field.label }}<select v-if="field.options" class="field-input" :value="op.fields[field.key] ?? ''" @change="change(op,field,$event)"><option value="">请选择</option><option v-for="(label,keyName) in field.options" :key="keyName" :value="keyName">{{ label }}</option></select><textarea v-else-if="field.type === 'textarea'" class="field-input" :value="value(op,field)" rows="3" :maxlength="field.max" @input="change(op,field,$event)"></textarea><input v-else class="field-input" :type="field.type === 'decimal' ? 'number' : field.type ?? 'text'" :value="value(op,field)" :step="field.type === 'datetime-local' || field.type === 'number' ? '1' : '0.001'" :maxlength="field.max" :min="field.type === 'number' ? 0 : undefined" @input="change(op,field,$event)"></label></div></template></fieldset><ul v-if="op.issues.length && !success(op.id)" class="issues"><li v-for="issue in op.issues" :key="issue">{{ issue }}</li></ul><p v-else-if="!success(op.id)" class="muted">字段已齐备；仍须核对真实时间、数量、人员与原文，保存后不会自动执行。</p><RouterLink v-if="success(op.id)" class="text-button" :to="link(op)">打开实际业务记录 →</RouterLink></article>
      <div v-if="editable" class="actions"><button class="action-button" :disabled="busy || !dirty" @click="save">保存新版本并核对缺项</button><button class="action-button primary" :disabled="busy || dirty || !selected.length" @click="openConfirmation">核对并确认选中的 {{ selected.length }} 项</button></div>
      <h3 class="display">确认留档 · 最近100次</h3><p v-if="!detail.data.value?.confirmations.length" class="muted">尚未确认执行。</p><p v-for="item in detail.data.value?.confirmations" :key="item.id" class="muted">确认 #{{ item.id }} · 版本 {{ item.version }} · {{ formatDateTime(item.confirmedAt) }} · {{ item.status === 'RUNNING' ? '待核对' : '本次已处理，结果见回执' }}</p><h3 class="display">执行回执 · 最近200条</h3><p v-if="!detail.data.value?.receipts.length" class="muted">尚未执行业务。整理和保存草稿均不改变库存、采收或运输状态。</p><article v-for="receipt in detail.data.value?.receipts" :key="receipt.id" class="receipt" :class="{ failed: receipt.status === 'FAILED' }"><strong>{{ receipt.status === 'SUCCESS' ? '成功' : '未完成' }} · 确认 #{{ receipt.confirmationId }}<template v-if="receipt.businessId"> · 业务 #{{ receipt.businessId }}</template></strong><p>{{ receipt.message }}</p><p class="muted">事项 {{ operations.findIndex(op => op.id === receipt.operationId) + 1 }} · {{ formatDateTime(receipt.recordedAt) }}</p></article>
    </template></ResourceState></GlassPanel>
    <AppDialog :open="dialog" title="核对本次办理内容" :busy="busy" @close="dialog = false"><div class="management stack"><p class="notice">确认日报 #{{ id }} 的版本 {{ report?.version }}，只执行下面 {{ chosen.length }} 项。各项单独保存，部分失败不会撤销其他成功事项。</p><article v-for="op in chosen" :key="op.id" class="operation-card stack"><strong>{{ names[op.kind] }} · 来源 #{{ op.targetId }}</strong><blockquote class="quote">{{ op.quote }}</blockquote><dl class="confirmation-fields"><template v-for="field in fields(op)" :key="field.key"><dt>{{ field.label }}</dt><dd>{{ field.options?.[String(op.fields[field.key])] ?? op.fields[field.key] ?? '未填写' }}</dd></template></dl></article><p class="muted">采收会新增真实采收台账，运输事实会更新运输过程或收货结果。此确认不执行设备指令或新业务上链。</p><p v-if="error" class="notice bad" role="alert">{{ error }}</p><div class="actions"><button class="action-button primary" :disabled="busy" @click="execute()">{{ busy ? '正在办理…' : '已核对，确认执行这几项' }}</button><button class="action-button" :disabled="busy" @click="dialog = false">返回修改</button></div></div></AppDialog>
  </div>
</template>

<style scoped>
h2 { font-size: var(--fs-h2); line-height: 1.5; }.pre-wrap { white-space: pre-wrap; }.source-text { margin-top: 16px; overflow-wrap: anywhere; }.operation-card,.report-card,.receipt { min-width: 0; padding: 18px; border: 1px solid var(--ink-16); border-radius: var(--radius-md); overflow-wrap: anywhere; }.report-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(min(100%,270px),1fr)); gap: 12px; }.report-card { display: grid; gap: 10px; background: var(--glass-fill); text-align: left; color: var(--ink); }.report-card span { white-space: normal; }.report-card:hover:not(:disabled) { border-color: var(--field); }.quote { margin: 0; border-left: 3px solid var(--field); padding: 10px 14px; white-space: pre-wrap; background: var(--field-tint); }.issues { list-style: disc; padding-left: 22px; color: var(--seal); font-size: var(--fs-sm); }.selection { display: flex; gap: 8px; align-items: center; min-height: 44px; padding: 8px 4px; cursor: pointer; }.selection input { width: 18px; height: 18px; margin: 0; }.confirmation-fields { display: grid; grid-template-columns: 1fr 2fr; gap: 10px; font-size: var(--fs-sm); }.confirmation-fields dt { color: var(--slate); }.confirmation-fields dd { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }.failed { background: var(--seal-tint); }
@media(max-width:560px) { .confirmation-fields { grid-template-columns: minmax(0,1fr); } }
</style>
