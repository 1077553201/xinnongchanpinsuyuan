<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import ResourceState from './ResourceState.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { TRANSPORT_STATUS } from '@/api/transport'
import { planContext, previewPlan, confirmPlan, type PlanDraft, type PlanPreview } from '@/api/transportPlans'
import { newOperationKey, operationError } from '@/utils/growing'
import { formatDateTime } from '@/types/api'

const props = defineProps<{ shipmentId: number }>()
const emit = defineEmits<{ saved: []; busy: [value: boolean] }>()
const auth = useAuth(), data = useAsyncResource(() => planContext(props.shipmentId))
const action = ref<'CANCEL' | 'REPLACE'>('CANCEL'), reason = ref(''), replacement = ref<NonNullable<PlanDraft['replacement']> | null>(null)
const preview = shallowRef<PlanPreview | null>(null), pending = shallowRef<(PlanDraft & { expectedImpactHash: string; requestKey: string }) | null>(null)
const busy = ref(false), approved = ref(false), error = ref(''), notice = ref('')
const blockers = computed(() => action.value === 'CANCEL' ? data.data.value?.cancelBlockers ?? [] : data.data.value?.replaceBlockers ?? [])
function activity(value: boolean) { busy.value = value; emit('busy', value) }
async function load() {
  await data.load()
  const current = data.data.value?.current
  if (!current) return
  action.value = current.status === 'CANCELLED' ? 'REPLACE' : 'CANCEL'
  replacement.value = { dispatchId: current.dispatchId, shipmentCode: `TPL-${newOperationKey()}`, carrier: current.carrier, trackingReference: current.trackingReference, driverName: current.driverName, driverContact: current.driverContact, notes: current.notes, requestKey: newOperationKey() }
}
onMounted(() => { void load() })
async function check() {
  if (busy.value || pending.value) return
  activity(true); error.value = ''; preview.value = null
  try {
    const draft: PlanDraft = { action: action.value, reason: reason.value.trim() }
    if (action.value === 'REPLACE' && replacement.value) draft.replacement = JSON.parse(JSON.stringify(replacement.value))
    preview.value = await previewPlan(props.shipmentId, draft)
    pending.value = { ...draft, expectedImpactHash: preview.value.impactHash, requestKey: newOperationKey() }; approved.value = false
  } catch (cause) { error.value = operationError(cause) }
  finally { activity(false) }
}
async function save() {
  if (!pending.value || !approved.value || busy.value || !preview.value?.canConfirm) return
  activity(true); error.value = ''
  try {
    await confirmPlan(props.shipmentId, pending.value)
    pending.value = null; preview.value = null; approved.value = false; reason.value = ''
    notice.value = '安排版本已留档，原始资料保留；库存、订单数量与实际运输事实未变更。'
    await load(); emit('saved')
  } catch (cause) { error.value = operationError(cause) + ' 相同内容可重试；需要修改时先返回重新预览。' }
  finally { activity(false) }
}
async function cancel() { if (busy.value) return; pending.value = null; preview.value = null; approved.value = false; error.value = ''; await load() }
</script>

<template>
  <section class="management stack plan-panel">
    <h3 class="display">安排撤销与替代 · 原档案保留</h3>
    <p class="muted">同一笔销售出库保留一个运输档案。撤销与替代分别确认，原编码、人员和计划数量留档；不代表实际退库、退款或发运。已有运输事实、逐件绑定或冻结流转证据时不能办理。</p>
    <ResourceState :loading="data.loading.value" :error="data.error.value" @retry="load">
      <template v-if="data.data.value">
        <p class="notice">运输档案 #{{ shipmentId }} · 当前 {{ TRANSPORT_STATUS[data.data.value.current.status] }} · 计划版本 {{ data.data.value.current.planRevision || 0 }} · 已有 {{ data.data.value.revision }} 条办理历史</p>
        <article class="history-card stack"><strong>原始安排 · {{ data.data.value.original.shipmentCode }}</strong><p>{{ data.data.value.original.carrier }} · {{ data.data.value.original.driverName }} · {{ data.data.value.original.driverContact }}</p><p>原计划 {{ data.data.value.original.packageCount }} {{ data.data.value.original.packageUnit }} · {{ formatDateTime(data.data.value.original.recordedAt) }}</p></article>
        <p>当前计划：{{ data.data.value.current.shipmentCode }} · {{ data.data.value.current.carrier }} · {{ data.data.value.current.packageCount }} {{ data.data.value.current.packageUnit }}</p>
        <article v-for="row in data.data.value.history" :key="row.id" class="history-card stack"><strong>#{{ row.revision }} {{ row.action === 'CANCEL' ? '撤销安排' : '替代安排' }}</strong><p>{{ row.reason }}</p><template v-if="row.action === 'REPLACE'"><p>{{ row.shipmentCode }} · {{ row.carrier }} · {{ row.driverName }} · {{ row.driverContact }}</p><p>{{ row.packageCount }} {{ row.packageUnit }} · {{ row.trackingReference }} · {{ row.notes }}</p></template><p class="muted">登记人 #{{ row.recordedBy }} · {{ formatDateTime(row.recordedAt) }}</p><small class="mono hash">{{ row.actionHash }}</small></article>
        <div v-if="blockers.length" class="notice wait"><strong>当前不能{{ action === 'CANCEL' ? '撤销' : '替代' }}</strong><p v-for="message in blockers" :key="message">{{ message }}</p></div>
        <form v-if="auth.canWrite.value && !blockers.length" class="stack" @submit.prevent="check"><fieldset class="stack" :disabled="busy || !!pending">
          <h4>{{ action === 'CANCEL' ? '撤销当前待发运安排' : '确认新的替代安排' }}</h4>
          <p v-if="action === 'CANCEL'" class="muted">撤销后不能继续为此安排记录发运或绑定包装码。仅在其他依赖也满足时，销售出库才可另行更正；不会自动恢复库存。</p>
          <template v-else-if="replacement"><label>新运输编码<input v-model.trim="replacement.shipmentCode" class="field-input" required pattern="[A-Za-z0-9_-]+" maxlength="64"></label><label>承运方<input v-model.trim="replacement.carrier" class="field-input" required maxlength="128"></label><label>运单或车辆信息<input v-model.trim="replacement.trackingReference" class="field-input" maxlength="128"></label><label>运输联系人<input v-model.trim="replacement.driverName" class="field-input" required maxlength="128"></label><label>联系方式<input v-model.trim="replacement.driverContact" class="field-input" maxlength="128"></label><label>安排说明<textarea v-model.trim="replacement.notes" class="field-input" rows="2" maxlength="1000" /></label><p class="muted">沿用原销售出库和收货资料，新计划数量以预览时有效出库为准，不能替换来源或复用历史编码。</p></template>
          <label>办理原因<textarea v-model.trim="reason" class="field-input" rows="3" required maxlength="800" /></label><button class="action-button">预览安排影响</button>
        </fieldset></form>
      </template>
    </ResourceState>
    <section v-if="preview && pending" class="confirmation stack">
      <h4>{{ preview.canConfirm ? `确认${pending.action === 'CANCEL' ? '撤销' : '替代'}安排` : '当前不能执行' }}</h4><p>当前 {{ preview.impact.current.shipmentCode }} · {{ preview.impact.current.packageCount }} {{ preview.impact.current.packageUnit }}；当前有效销售出库 {{ preview.impact.currentDispatchCount }} {{ preview.impact.current.packageUnit }}。</p><p v-if="pending.action === 'REPLACE'">新编码 {{ preview.impact.replacement?.shipmentCode }} · {{ preview.impact.replacement?.carrier }} · {{ preview.impact.replacement?.driverName }} · 新计划 {{ preview.impact.replacement?.packageCount }} {{ preview.impact.replacement?.packageUnit }}</p><p v-if="pending.action === 'REPLACE'">{{ pending.replacement?.trackingReference }} · {{ pending.replacement?.driverContact }} · {{ pending.replacement?.notes }}</p><p>收货资料保持：{{ preview.impact.recipient }} · {{ preview.impact.deliveryAddress }}</p><p class="pre-wrap">{{ pending.reason }}</p><div v-if="preview.impact.blockers.length" class="notice bad"><p v-for="message in preview.impact.blockers" :key="message">{{ message }}</p></div><label v-if="preview.canConfirm" class="approval"><input v-model="approved" type="checkbox" :disabled="busy">我已核对安排、实物尚未发运和下游依赖，确认本次留档，不据此退库或退款。</label><div class="actions"><button class="action-button primary" :disabled="busy || !approved || !preview.canConfirm" @click="save">{{ busy ? '正在留档…' : '确认保存 / 同内容重试' }}</button><button class="action-button" :disabled="busy" @click="cancel">返回重新核对</button></div>
    </section>
    <p v-if="error" class="notice bad" role="alert">{{ error }}</p><p v-if="notice" class="notice" role="status">{{ notice }}</p>
  </section>
</template>

<style scoped>
.plan-panel { min-width: 0; }.history-card,.confirmation { padding: 16px; border: 1px solid var(--glass-hairline); border-radius: var(--radius-md); }.confirmation { background: var(--field-tint); }label { display: grid; gap: 8px; }.approval { display: flex; gap: 10px; align-items: start; }p,.hash { overflow-wrap: anywhere; }.pre-wrap { white-space: pre-wrap; }
</style>
