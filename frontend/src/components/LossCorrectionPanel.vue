<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import ResourceState from './ResourceState.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { lossHistory, previewLossCorrection, confirmLossCorrection, type LossPreview, type ConfirmLossCorrection } from '@/api/lossCorrections'
import { localDateTime, newOperationKey, operationError } from '@/utils/growing'
import { chainStatusLabel, formatDateTime } from '@/types/api'
import { rejectionStatuses } from '@/utils/rejections'
const props = defineProps<{ movementId: number; ordinary?: boolean }>()
const emit = defineEmits<{ saved: []; busy: [value: boolean] }>()
const auth = useAuth(), history = useAsyncResource(() => lossHistory(props.movementId, props.ordinary))
const kindLabel = computed(() => history.data.value?.original.kind === 'IN' ? '入库' : history.data.value?.original.kind === 'OUT' ? '出库' : '损耗')
const count = ref(''), verifiedAt = ref(localDateTime()), performer = ref(''), reason = ref(''), reference = ref('')
const preview = shallowRef<LossPreview | null>(null), pending = shallowRef<ConfirmLossCorrection | null>(null)
const busy = ref(false), approved = ref(false), error = ref(''), notice = ref('')
const orderLabels: Record<string, string> = { OPEN: '待出库', PARTIAL: '部分出库', FULFILLED: '已全部出库', CLOSED: '已停止', CANCELLED: '已取消' }
const evidenceLabels: Record<string, string> = { PRODUCTION: '生产证据', CIRCULATION: '流转证据', QUALITY: '质检证据' }
function activity(value: boolean) { busy.value = value; emit('busy', value) }
onMounted(() => { void history.load() })
async function check() {
  if (busy.value || pending.value) return
  activity(true); error.value = ''; preview.value = null
  try {
    preview.value = await previewLossCorrection(props.movementId, Number(count.value), props.ordinary)
    pending.value = { correctedCount: Number(count.value), verifiedAt: verifiedAt.value.replace('T', ' ') + ':00', performerName: performer.value.trim(), reason: reason.value.trim(), reference: reference.value.trim(), expectedImpactHash: preview.value.impactHash, requestKey: newOperationKey() }
    approved.value = false
  } catch (cause) { error.value = operationError(cause) }
  finally { activity(false) }
}
async function save() {
  if (!pending.value || busy.value || !approved.value || preview.value?.canConfirm === false) return
  activity(true); error.value = ''
  try {
    await confirmLossCorrection(props.movementId, pending.value, props.ordinary)
    pending.value = null; preview.value = null; approved.value = false; count.value = ''; reason.value = ''
    notice.value = '调整单已留档，原始库存记录保留。订单、包装码与证据未改写。'; await history.load(); emit('saved')
  } catch (cause) { error.value = operationError(cause) + ' 相同内容可重试；内容需要变化时请返回重新预览。' }
  finally { activity(false) }
}
async function cancel() { if (busy.value) return; pending.value = null; preview.value = null; approved.value = false; error.value = ''; await history.load() }
</script>

<template>
  <section class="stack loss-correction management">
    <h3 class="display">{{ kindLabel }}数量更正 · 保留原单据</h3>
    <p v-if="ordinary" class="muted">本轮仅支持同仓最近的普通入库/出库单。已有销售订单、逐件出库关联或后续同仓流水时，只能查看影响与阻止原因；履约更正须另行办理。核实实物后填写该单正确总件数，不用更正代替退货放行或盘盈。</p>
    <p v-else class="muted">用于原损耗数量录错后的核实调整。减少损耗会增加当前可售余额，必须确认实物仍属于本仓普通库存；拒收退回和隔离货物不能通过此入口变成可售。入出库请从对应流水入口办理，退款另行处理。</p>
    <ResourceState :loading="history.loading.value" :error="history.error.value" @retry="history.load">
      <template v-if="history.data.value">
        <p class="notice">{{ history.data.value.original.movementCode }} · {{ history.data.value.original.warehouseName }} · 原始{{ kindLabel }} {{ history.data.value.original.packageCount }} {{ history.data.value.original.packageUnit }}；当前有效 {{ history.data.value.effectiveCount }} {{ history.data.value.original.packageUnit }}。</p>
        <p>原发生时间 {{ formatDateTime(history.data.value.original.occurredAt) }} · {{ history.data.value.original.performerName }} · {{ history.data.value.original.reason }}</p>
        <article v-for="row in history.data.value.history" :key="row.id" class="history-card stack"><strong>调整 #{{ row.id }} · 第 {{ row.revision }} 版 · {{ row.previousCount }} → {{ row.correctedCount }} {{ history.data.value.original.packageUnit }}</strong><p>当次本仓余量 {{ row.balanceBefore }} → {{ row.balanceAfter }} · {{ row.performerName }} · 核实 {{ formatDateTime(row.verifiedAt) }}</p><p class="pre-wrap">{{ row.reason }}<template v-if="row.reference"> · 参考 {{ row.reference }}</template></p><p class="muted">登记人 #{{ row.recordedBy }} · {{ formatDateTime(row.recordedAt) }}</p><small class="mono hash">{{ row.correctionHash }}</small></article>
        <form v-if="auth.canWrite.value" class="stack" @submit.prevent="check"><fieldset :disabled="busy || !!pending" class="stack">
          <label>核实后的本单{{ kindLabel }}总件数（不是差额）<input v-model="count" type="number" min="0" max="1000000" step="1" required class="field-input"></label>
          <label>实际核实时间<input v-model="verifiedAt" type="datetime-local" required class="field-input"></label><label>实际核实人员<input v-model="performer" required maxlength="128" class="field-input"></label><label>更正原因与核实依据<textarea v-model="reason" required maxlength="1000" rows="3" class="field-input" /></label><label>参考单据（选填）<input v-model="reference" maxlength="128" class="field-input"></label><button class="action-button">预览数量与下游影响</button></fieldset></form>
      </template>
    </ResourceState>
    <section v-if="preview && pending" class="stack confirmation">
      <h4>{{ preview.canConfirm === false ? '影响预览 · 当前不可执行' : `确认第 ${preview.impact.revision} 版调整` }}</h4><p>有效{{ kindLabel }} {{ preview.impact.previousCount }} → {{ preview.impact.correctedCount }} {{ preview.impact.packageUnit }}；本仓可售余量 {{ preview.impact.balanceBefore }} → {{ preview.impact.balanceAfter }} {{ preview.impact.packageUnit }}，净重变化 {{ (preview.impact.stockDelta * preview.impact.netWeightKg).toFixed(3) }} kg。</p>
      <p v-if="ordinary" class="notice">本来源有效累计入库 {{ preview.impact.receivedBefore }} → {{ preview.impact.receivedAfter }} {{ preview.impact.packageUnit }}，实际包装上限 {{ preview.impact.packingCount }}。本来源全部仓库余量 {{ preview.impact.availableBefore }} → {{ preview.impact.availableAfter }}。</p>
      <div v-if="preview.canConfirm === false" class="notice bad" role="alert"><strong>当前不能执行数量更正</strong><p v-for="message in preview.impact.blockers" :key="message">{{ message }}</p></div>
      <p>{{ pending.performerName }} · {{ pending.verifiedAt }} · {{ pending.reason }}<template v-if="pending.reference"> · {{ pending.reference }}</template></p>
      <p class="muted">下列为同包装来源关联资料，用于核对本次调整影响；不代表某个码的出入库、收货或质量结论。调整不改写订单、逐件收货、隔离处置或证据版本，不自动上链；这里列出已保存的证据处理状态，实时核验另行查询。</p>
      <details><summary>关联订单明细 {{ preview.impact.orders.length }} 条</summary><p v-for="row in preview.impact.orders" :key="row.lineId">{{ row.orderCode }} / 明细 #{{ row.lineId }} · {{ orderLabels[row.status] || row.status }} · 原订 {{ row.orderedCount }} / 已出 {{ row.dispatchedCount }} · 仓库 #{{ row.warehouseId }}</p><p v-if="!preview.impact.orders.length">暂无关联订单。</p></details>
      <details><summary>关联包装码 {{ preview.impact.relatedCodes.length }} 个</summary><p v-for="row in preview.impact.relatedCodes" :key="row.itemId" class="mono hash">{{ row.traceCode }} · {{ row.receiptOutcome === 'REJECTED' ? '拒收' : row.receiptOutcome === 'ACCEPTED' ? '实收' : row.dispatchId ? '已关联出库' : '未关联出库' }}<template v-if="row.rejectionStatus"> · {{ rejectionStatuses[row.rejectionStatus] || row.rejectionStatus }}</template></p><p v-if="!preview.impact.relatedCodes.length">尚未赋码。</p></details>
      <details><summary>既有生产、流转、质检记录 {{ preview.impact.evidence.length }} 条</summary><p v-for="row in preview.impact.evidence" :key="`${row.kind}-${row.id}`">{{ evidenceLabels[row.kind] || row.kind }} #{{ row.id }} · 业务对象 #{{ row.itemId }} · {{ chainStatusLabel(row.chainStatus) }}</p><p v-if="!preview.impact.evidence.length">暂无冻结证据。</p></details>
      <label v-if="preview.canConfirm !== false" class="approval"><input v-model="approved" type="checkbox" :disabled="busy">我已核对原单、实物和下游关联，确认调整后余量准确，并知悉这不代表退货放行或质量合格。</label><div class="actions"><button class="action-button primary" :disabled="busy || !approved || preview.canConfirm === false" @click="save">{{ busy ? '正在留档…' : '确认调整 / 同内容重试' }}</button><button class="action-button" :disabled="busy" @click="cancel">返回并刷新核对</button></div>
    </section>
    <p v-if="error" class="notice bad" role="alert">{{ error }}</p><p v-if="notice" class="notice" role="status">{{ notice }}</p>
  </section>
</template>

<style scoped>
.loss-correction { min-width: 0; }.history-card,.confirmation { padding: 16px; border: 1px solid var(--glass-hairline); border-radius: var(--radius-md); }.confirmation { background: var(--field-tint); }label { display: grid; gap: 8px; }.approval { display: flex; align-items: start; gap: 10px; }p,.hash { overflow-wrap: anywhere; }summary { cursor: pointer; color: var(--field); }.pre-wrap { white-space: pre-wrap; }
</style>
