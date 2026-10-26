<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import ResourceState from './ResourceState.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { SALES_STATUS, type NewSalesOrder } from '@/api/sales'
import { replacementContext, previewReplacement, confirmReplacement, type ReplacementPreview, type ReplacementDraft } from '@/api/salesReplacement'
import { newOperationKey, operationError } from '@/utils/growing'
import { salesAmount } from '@/utils/sales'
import { formatDateTime } from '@/types/api'

const props = defineProps<{ orderId: number }>()
const emit = defineEmits<{ busy: [value: boolean]; saved: []; openOrder: [id: number] }>()
const auth = useAuth(), context = useAsyncResource(() => replacementContext(props.orderId))
const draft = ref<NewSalesOrder | null>(null), reason = ref(''), busy = ref(false), approved = ref(false), error = ref(''), notice = ref('')
const preview = shallowRef<ReplacementPreview | null>(null)
const pending = shallowRef<(ReplacementDraft & { expectedImpactHash: string; requestKey: string }) | null>(null)
const amount = computed(() => salesAmount((draft.value?.lines ?? []).map(line => ({ quantity: line.packageCount, price: line.unitPrice }))))
function activity(value: boolean) { busy.value = value; emit('busy', value) }
function initialize() {
  const current = context.data.value
  if (!current) return
  const source = current.order
  draft.value = { organizationId: source.organizationId, orderCode: `REPL-${newOperationKey()}`, orderedOn: source.orderedOn, customerName: source.customerName, customerContact: source.customerContact || '', deliveryAddress: source.deliveryAddress, notes: source.notes || '', lines: current.lines.map(line => ({ packingId: line.packingId, warehouseId: line.warehouseId, packageCount: line.packageCount, unitPrice: line.unitPrice })), requestKey: newOperationKey() }
}
async function load() { await context.load(); initialize() }
onMounted(() => { void load() })
async function check() {
  if (!draft.value || busy.value || pending.value) return
  activity(true); error.value = ''; preview.value = null
  try {
    const frozen: ReplacementDraft = JSON.parse(JSON.stringify({ replacement: draft.value, reason: reason.value.trim() }))
    preview.value = await previewReplacement(props.orderId, frozen)
    pending.value = { ...frozen, expectedImpactHash: preview.value.impactHash, requestKey: newOperationKey() }; approved.value = false
  } catch (cause) { error.value = operationError(cause) }
  finally { activity(false) }
}
async function save() {
  if (!pending.value || busy.value || !approved.value || !preview.value?.canConfirm) return
  activity(true); error.value = ''
  try {
    const result = await confirmReplacement(props.orderId, pending.value)
    pending.value = null; preview.value = null; approved.value = false
    notice.value = '原单已停止，替代单与关联留档已保存；库存及实际交付未变更。'
    await context.load(); initialize(); emit('saved')
    if (!result.relations.length) notice.value = '操作已返回，请刷新核对原单和替代关系。'
  } catch (cause) { error.value = operationError(cause) + ' 相同内容可重试；需要修改时先返回重新预览。' }
  finally { activity(false) }
}
async function cancel() { if (busy.value) return; pending.value = null; preview.value = null; approved.value = false; error.value = ''; await load() }
</script>

<template>
  <section class="management replacement-panel stack">
    <h3 class="display">订单更正与替代</h3>
    <p class="muted">未出库订单可以停止原单并建立替代单，保留原客户、金额及更正关系。已出库、运输或逐件交付的事实须另走履约更正；本操作不退库、不退款、不预占库存。</p>
    <ResourceState :loading="context.loading.value" :error="context.error.value" @retry="load">
      <template v-if="context.data.value">
        <p class="notice">原单 {{ context.data.value.order.orderCode }} · {{ SALES_STATUS[context.data.value.order.status] }} · ¥ {{ context.data.value.order.totalAmount }} · 实际出库 {{ context.data.value.dispatchCount }} 笔</p>
        <p>{{ context.data.value.order.customerName }} · {{ context.data.value.order.customerContact || '未填联系方式' }} · {{ context.data.value.order.deliveryAddress }}</p>
        <article v-for="row in context.data.value.relations" :key="row.id" class="history-card stack"><strong>更正关系 #{{ row.id }} · 原单 #{{ row.originalOrderId }} → 替代单 #{{ row.replacementOrderId }}</strong><p>{{ row.reason }}</p><p class="muted">登记人 #{{ row.recordedBy }} · {{ formatDateTime(row.recordedAt) }}</p><small class="mono hash">{{ row.recordHash }}</small><div class="actions"><button class="action-button" :disabled="busy" @click="emit('openOrder', row.originalOrderId)">查看原单</button><button class="action-button" :disabled="busy" @click="emit('openOrder', row.replacementOrderId)">查看替代单</button></div></article>
        <div v-if="context.data.value.blockers.length" class="notice wait"><strong>当前不能停止并替代</strong><p v-for="message in context.data.value.blockers" :key="message">{{ message }}</p></div>
        <details v-if="context.data.value.dispatchCount"><summary>查看实际出库与交付依赖</summary><p v-for="row in context.data.value.dependencies" :key="row.dispatchId">出库 #{{ row.dispatchId }} · {{ row.packageCount }} 件 · {{ row.shipmentId ? `运输单 #${row.shipmentId}` : '尚未登记运输单' }} · 逐件关联 {{ row.linkedCodeCount }} / 已核对 {{ row.checkedCodeCount }}</p><p v-if="context.data.value.dependenciesTruncated" class="muted">仅展示前 201 笔；阻止判断覆盖全部出库记录。</p></details>
        <form v-if="auth.canWrite.value && !context.data.value.blockers.length && draft" class="stack" @submit.prevent="check"><fieldset class="stack" :disabled="busy || !!pending">
          <p class="muted">本轮保留原包装与仓库，可更正客户、日期、数量、单价和说明。需要换货或更换来源时另行办理。</p>
          <label>替代订单编码<input v-model="draft.orderCode" class="field-input" required pattern="[A-Za-z0-9_-]+" maxlength="64"></label><label>实际下单日期<input v-model="draft.orderedOn" class="field-input" type="date" required></label>
          <label>客户名称<input v-model="draft.customerName" class="field-input" required maxlength="128"></label><label>联系方式<input v-model="draft.customerContact" class="field-input" maxlength="128"></label><label>交付地址<input v-model="draft.deliveryAddress" class="field-input" required maxlength="500"></label>
          <article v-for="(line,index) in draft.lines" :key="`${line.packingId}-${line.warehouseId}`" class="history-card stack"><strong>{{ context.data.value.lines[index]?.productName }} · {{ context.data.value.lines[index]?.packingCode }}</strong><p>{{ context.data.value.lines[index]?.warehouseName }}</p><label>订购总件数<input v-model.number="line.packageCount" class="field-input" type="number" min="1" max="1000000" step="1" required></label><label>人民币单价（元 / 包装）<input v-model="line.unitPrice" class="field-input" type="number" min="0" max="999999999.99" step="0.01" required></label></article>
          <p class="notice">替代单约定金额 ¥ {{ amount ?? '请核对数量与单价' }}；下单不预占库存。</p><label>订单说明<textarea v-model="draft.notes" class="field-input" rows="2" maxlength="2000" /></label><label>更正原因<textarea v-model="reason" class="field-input" rows="3" maxlength="800" required /></label><button class="action-button">预览停止与替代影响</button>
        </fieldset></form>
      </template>
    </ResourceState>
    <section v-if="preview && pending" class="confirmation stack">
      <h4>{{ preview.canConfirm ? '确认停止原单并建立替代单' : '当前不能执行替代' }}</h4><p>{{ preview.impact.original.orderCode }} → {{ preview.impact.replacement.orderCode }} · 原金额 ¥ {{ preview.impact.original.totalAmount }} → 新金额 ¥ {{ preview.impact.proposedAmount }}</p><p>原客户：{{ preview.impact.original.customerName }} / {{ preview.impact.original.customerContact || '未填联系方式' }} / {{ preview.impact.original.deliveryAddress }}</p><p>替代客户：{{ pending.replacement.customerName }} / {{ pending.replacement.customerContact || '未填联系方式' }} / {{ pending.replacement.deliveryAddress }} · 日期 {{ pending.replacement.orderedOn }}</p><p class="pre-wrap">{{ pending.reason }}</p>
      <div v-if="preview.impact.blockers.length" class="notice bad" role="alert"><p v-for="message in preview.impact.blockers" :key="message">{{ message }}</p></div>
      <article v-for="line in preview.impact.proposedLines" :key="`${line.packingId}-${line.warehouseId}`" class="history-card"><strong>{{ line.productName }} · {{ line.packingCode }} / {{ line.warehouseName }}</strong><p>新订 {{ line.packageCount }} {{ line.packageUnit }} × ¥ {{ line.unitPrice }} = ¥ {{ line.lineAmount }}</p><p class="muted">原订 {{ preview.impact.originalLines.find(old => old.packingId === line.packingId && old.warehouseId === line.warehouseId)?.packageCount }} {{ line.packageUnit }}；当前库存参考 {{ line.availableCount }} {{ line.packageUnit }}，本次不预占、不扣减，实际出库另行核对。</p></article>
      <label v-if="preview.canConfirm" class="approval"><input v-model="approved" type="checkbox" :disabled="busy">我已核对原单、替代内容和更正原因，确认停止原单后仅由替代单继续安排履约。</label><div class="actions"><button class="action-button primary" :disabled="busy || !approved || !preview.canConfirm" @click="save">{{ busy ? '正在留档…' : '确认替代 / 同内容重试' }}</button><button class="action-button" :disabled="busy" @click="cancel">返回重新核对</button></div>
    </section>
    <p v-if="error" class="notice bad" role="alert">{{ error }}</p><p v-if="notice" class="notice" role="status">{{ notice }}</p>
  </section>
</template>

<style scoped>
.replacement-panel { min-width: 0; }.history-card,.confirmation { border: 1px solid var(--glass-hairline); border-radius: var(--radius-md); padding: 16px; }.confirmation { background: var(--field-tint); }label { display: grid; gap: 8px; }.approval { display: flex; align-items: start; gap: 10px; }p,.hash { overflow-wrap: anywhere; }.pre-wrap { white-space: pre-wrap; }summary { cursor: pointer; color: var(--field); }
</style>
