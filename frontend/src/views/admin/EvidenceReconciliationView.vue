<script setup lang="ts">
import { computed, onMounted, onScopeDispose, reactive, ref, shallowRef } from 'vue'
import { RouterLink } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import StatusChip from '@/components/StatusChip.vue'
import TransactionReceiptPanel from '@/components/TransactionReceiptPanel.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { reconciliationList, inspectEvidence, recordReconciliation, type ReconciliationRow, type ReconciliationCheck } from '@/api/evidenceReconciliation'
import { EVIDENCE_KINDS, reconciliationOutcome, canRecordReconciliation } from '@/utils/reconciliation'
import { operationError } from '@/utils/growing'
import { formatDateTime } from '@/types/api'

const auth = useAuth()
const draft = reactive({ kind: '', status: 'ATTENTION', packingId: '', code: '', minAgeHours: 0 })
const filters = shallowRef<Record<string, unknown>>({ status: 'ATTENTION' }), page = ref(1)
const resource = useAsyncResource(() => reconciliationList({ ...filters.value, page: page.value }))
const selected = shallowRef<ReconciliationRow | null>(null), check = shallowRef<ReconciliationCheck | null>(null)
const receiptBusy = ref(false)
const checking = ref(false), recording = ref(false), approved = ref(false), error = ref(''), notice = ref('')
let revision = 0
onScopeDispose(() => { revision += 1 })
const result = computed(() => reconciliationOutcome(check.value?.outcome ?? ''))
const recordable = computed(() => check.value && !checking.value && !receiptBusy.value && auth.canWrite.value && canRecordReconciliation(check.value))
const actions: Record<string, string> = { BATCH_REGISTER: '注册包装批次', ITEM_BIND: '绑定包装码', TRACE_EVENT: '流转事件', QUALITY_TEST: '质检事件', BIND: '绑定标签', DISABLE: '停用标签' }
onMounted(resource.load)
async function search() {
  filters.value = { kind: draft.kind, status: draft.status, packingId: draft.packingId ? Number(draft.packingId) : undefined, code: draft.code.trim(), minAgeHours: draft.minAgeHours }
  page.value = 1
  await resource.load()
}
async function turn(next: number) { page.value = next; await resource.load() }
async function inspect(row: ReconciliationRow) {
  if (recording.value || receiptBusy.value) return
  const current = ++revision
  selected.value = row; check.value = null; approved.value = false; error.value = ''; notice.value = ''; checking.value = true
  try { const value = await inspectEvidence(row.kind, row.recordId); if (current === revision) check.value = value }
  catch (cause) { if (current === revision) error.value = operationError(cause) }
  finally { if (current === revision) checking.value = false }
}
function close() {
  if (recording.value || receiptBusy.value) return
  revision += 1; selected.value = null; check.value = null; checking.value = false; approved.value = false
}
async function record() {
  if (recording.value || receiptBusy.value || !approved.value || !recordable.value || !check.value) return
  const current = check.value
  recording.value = true; approved.value = false; error.value = ''; notice.value = ''
  try {
    check.value = await recordReconciliation(current.record.kind, current.record.recordId, current.checkHash)
    notice.value = '本轮核对补记已结束，请查看最新结果；本入口没有发送链交易，也不会补造原交易哈希或回执。'
    await resource.load()
  } catch (cause) { check.value = null; error.value = operationError(cause) + ' 请重新查链核对当前状态。' }
  finally { recording.value = false }
}
async function receiptRecorded() {
  approved.value = false
  if (!check.value) return
  const row = check.value.record
  checking.value = true
  try { check.value = await inspectEvidence(row.kind, row.recordId); await resource.load() }
  catch (cause) { check.value = null; error.value = operationError(cause) }
  finally { checking.value = false }
}
</script>

<template>
  <div class="management reconciliation stack">
    <GlassPanel class="stack">
      <div class="section-head"><div><p class="eyebrow">证据回执核对</p><h2 class="display">让每笔待确认操作有据可查</h2></div><button class="action-button" :disabled="resource.loading.value || recording" @click="resource.load">刷新清单</button></div>
      <p class="muted">集中查看已冻结的生产、流转、质检与 NFC 操作。清单不自动查链或重发；选择一项再核对，只有找到且一致时才可确认补记。旧模块和从未冻结的业务不在此清单。</p>
      <form class="filters" @submit.prevent="search">
        <label>证据类型<select v-model="draft.kind" class="field-input"><option value="">全部类型</option><option v-for="(label,key) in EVIDENCE_KINDS" :key="key" :value="key">{{ label }}</option></select></label>
        <label>本地证据状态<select v-model="draft.status" class="field-input"><option value="ATTENTION">需要关注（含原提交回执未确认）</option><option value="ALL">全部状态</option><option value="NOT_SUBMITTED">尚未提交</option><option value="PENDING">处理中</option><option value="UNCERTAIN">结果待核对</option><option value="FAILED">失败</option><option value="CHAIN_DISABLED">链写入未启用</option><option value="CONFIRMED">本地已确认</option></select></label>
        <label>完整包装码<input v-model.trim="draft.code" class="field-input" maxlength="128" placeholder="按完整码精确查询"></label>
        <label>包装记录编号<input v-model="draft.packingId" class="field-input" type="number" min="1" step="1"></label>
        <label>距提交 / 来源冻结至少（小时）<input v-model.number="draft.minAgeHours" class="field-input" type="number" min="0" max="87600" step="1" required></label>
        <button class="action-button primary" :disabled="resource.loading.value || recording">查询清单</button>
      </form>
      <p class="mobile-table-hint muted">左右滑动表格，可查看状态、时间和“查链核对”按钮。</p>
      <ResourceState :loading="resource.loading.value" :error="resource.error.value" :empty="!resource.data.value?.rows.length" empty-text="此范围内没有已冻结的匹配操作；不代表所有业务都已上链。" @retry="resource.load">
        <div class="table-scroll" tabindex="0" aria-label="证据对账清单，可横向滚动"><table class="data-table"><thead><tr><th>证据 / 操作</th><th>来源 / 身份</th><th>本地状态 / 原提交</th><th>时间</th><th>核对</th></tr></thead><tbody>
          <tr v-for="row in resource.data.value?.rows" :key="`${row.kind}-${row.recordId}`"><td>{{ EVIDENCE_KINDS[row.kind] }} #{{ row.recordId }}<p>{{ actions[row.action] || row.action }} · 版本 {{ row.version }}</p><p v-if="row.reportNo">{{ row.reportNo }}</p></td><td>{{ row.plotName }}<p>{{ row.packingCode }}（#{{ row.packingId }}）</p><p class="code">{{ row.traceCode || '包装批次操作' }}</p></td><td><StatusChip v-if="row.chainStatus !== 'UNCERTAIN'" :status="row.chainStatus" compact /><span v-else>结果待核对</span><p>{{ row.txId ? `原交易记录 #${row.txId}` : '尚无交易意图' }} · {{ row.transactionStatus || '—' }}</p><p v-if="row.errorCode" class="muted">{{ row.errorCode }}</p></td><td>来源冻结 {{ formatDateTime(row.recordedAt) }}<p>最近提交 {{ formatDateTime(row.submittedAt) }}</p></td><td><button class="text-button" :disabled="recording" @click="inspect(row)">查链核对</button></td></tr>
        </tbody></table></div>
      </ResourceState>
      <div v-if="resource.data.value" class="section-head"><p class="muted">共 {{ resource.data.value.total }} 项 · 第 {{ resource.data.value.page }} 页 · 清单时点 {{ formatDateTime(resource.data.value.checkedAt) }}</p><div class="actions"><button class="action-button" :disabled="resource.loading.value || recording || resource.data.value.page <= 1" @click="turn(resource.data.value.page - 1)">上一页</button><button class="action-button" :disabled="resource.loading.value || recording || resource.data.value.page * resource.data.value.limit >= resource.data.value.total" @click="turn(resource.data.value.page + 1)">下一页</button></div></div>
    </GlassPanel>
    <AppDialog :open="!!selected" title="查链核对与确认补记" :busy="recording || receiptBusy" @close="close">
      <div class="stack"><p v-if="selected">{{ EVIDENCE_KINDS[selected.kind] }} #{{ selected.recordId }} · {{ selected.traceCode || selected.packingCode }}</p><p v-if="checking" role="status">正在只读核对来源、冻结摘要与链上记录，最多读取 200 条单品事件…</p>
        <template v-if="check"><h3>{{ result.label }}</h3><p>{{ result.detail }}</p><p class="muted">核对时点 {{ formatDateTime(check.checkedAt) }}</p><p class="digest">冻结摘要 {{ check.record.frozenHash }}</p><p>原交易记录 {{ check.record.txId ? `#${check.record.txId}` : '无' }} · 原提交状态 {{ check.transactionStatus || '无' }} · 回执状态 {{ check.receiptStatus ?? '未取得' }}</p>
          <p v-if="check.processing" class="notice wait">原操作仍在处理，暂不能补记，请稍后核对。</p>
          <p v-if="check.outcome === 'MATCHED' && check.record.chainStatus === 'CONFIRMED'" class="notice">本地证据回执已确认，无需重复补记。</p>
          <p v-if="check.record.chainStatus === 'CONFIRMED' && ['PENDING','UNCERTAIN'].includes(check.transactionStatus || '')" class="notice wait">业务证据可独立核验，但原提交的回执仍待追查；不据此补造交易哈希、区块或确认回执。</p>
          <p v-if="check.disposition === 'UNKNOWN_TRANSACTION'" class="notice wait">原交易结果仍未知，保留原意图；仅可继续查链，不能因等待时间长而重发。</p>
          <p v-if="check.disposition === 'BUSINESS_REVIEW'" class="notice wait">请返回来源业务核对状态和提交范围，明确确认后再办理；此集中入口不会发送交易。</p>
          <form v-if="recordable" class="stack" @submit.prevent="record"><label class="approval"><input v-model="approved" type="checkbox" :disabled="recording">已核对具体业务、冻结摘要及本次查链结果，仅补记已存在且一致的链记录，不发送新交易。</label><button class="action-button primary" :disabled="recording || !approved">{{ recording ? '正在重新核对与补记…' : '确认补记已有链记录' }}</button></form>
          <div class="actions"><button class="action-button" :disabled="recording || receiptBusy || checking" @click="inspect(check.record)">重新只读核对</button><RouterLink v-if="!recording && !receiptBusy" :to="{ path: '/admin/harvest', query: { cycle: check.record.cycleId } }">查看来源采收与包装 →</RouterLink><RouterLink v-if="check.record.kind === 'CIRCULATION' && !recording && !receiptBusy" to="/admin/sales">前往销售出库与逐件流转 →</RouterLink></div>
          <TransactionReceiptPanel :key="`${check.record.kind}-${check.record.recordId}`" :record="check.record" :disabled="recording || checking" @busy="receiptBusy = $event" @recorded="receiptRecorded" />
        </template>
        <p v-if="notice" role="status" class="notice">{{ notice }}</p><p v-if="error" role="alert" class="notice bad">{{ error }}</p><button v-if="error && selected" class="action-button" :disabled="recording || receiptBusy || checking" @click="inspect(selected)">重新只读核对</button>
      </div>
    </AppDialog>
  </div>
</template>
<style scoped>
.reconciliation { min-width: 0; }.reconciliation h2 { font-size: var(--fs-h2); }.code,.digest { overflow-wrap: anywhere; }.digest { font-size: var(--fs-xs); color: var(--slate); }.data-table p { margin-top: 8px; }.approval { display: flex; align-items: start; gap: 10px; line-height: 1.8; }.approval input { flex-shrink: 0; margin-top: 6px; }.reconciliation .filters label { min-width: 0; }
.mobile-table-hint { display: none; }@media(max-width:600px) { .mobile-table-hint { display: block; font-size: var(--fs-xs); } }
</style>
