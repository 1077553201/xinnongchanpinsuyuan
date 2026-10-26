<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import InventoryViewTabs from '@/components/InventoryViewTabs.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { useAuth } from '@/composables/useAuth'
import { listWarehouses } from '@/api/inventory'
import { rejectionList, rejectionPreview, rejectionDetail, createRejection, appendRejection, type RejectionDetail, type RejectionAppend } from '@/api/rejections'
import { rejectionStatuses, rejectionKinds, rejectionActions } from '@/utils/rejections'
import { newOperationKey, operationError } from '@/utils/growing'
import { formatDateTime } from '@/types/api'

const route = useRoute(), auth = useAuth()
const warehouse = ref(String(route.query.warehouseId || '')), status = ref('ALL'), code = ref(''), page = ref(1)
const list = useAsyncResource(() => rejectionList(Number(warehouse.value) || undefined, status.value, code.value.trim(), page.value))
const warehouses = useAsyncResource(listWarehouses)
const selected = ref<RejectionDetail | null>(null)
const preview = ref<Awaited<ReturnType<typeof rejectionPreview>> | null>(null)
const dispatchId = ref(String(route.query.dispatchId || '')), itemId = ref(String(route.query.itemId || '')), reason = ref('')
const kind = ref(''), occurredAt = ref(''), destination = ref(''), location = ref(''), performer = ref(''), note = ref(''), reference = ref(''), target = ref(0)
const busy = ref(false), error = ref(''), notice = ref(''), approved = ref(false)
type Pending = { type: 'create'; body: Parameters<typeof createRejection>[0] } | { type: 'event'; id: number; body: RejectionAppend }
const pending = ref<Pending | null>(null)
const detailHeading = ref<HTMLElement | null>(null), confirmationHeading = ref<HTMLElement | null>(null)
watch(pending, async value => { if (value) { await nextTick(); confirmationHeading.value?.focus() } })
const locked = computed(() => busy.value || !!pending.value)
const actions = computed(() => rejectionActions(selected.value?.record.status || ''))
async function load() { await list.load() }
async function open(id: number) {
  if (locked.value) return
  busy.value = true; error.value = ''; preview.value = null
  try { selected.value = await rejectionDetail(id); kind.value = ''; note.value = ''; approved.value = false; await nextTick(); detailHeading.value?.focus() }
  catch (cause) { selected.value = null; error.value = operationError(cause) }
  finally { busy.value = false }
}
async function checkSource() {
  if (locked.value) return
  busy.value = true; error.value = ''; preview.value = null; selected.value = null
  try { preview.value = await rejectionPreview(Number(dispatchId.value), Number(itemId.value)) }
  catch (cause) { error.value = operationError(cause) }
  finally { busy.value = false }
}
function prepareCreate() {
  if (!preview.value || locked.value) return
  pending.value = { type: 'create', body: { dispatchId: preview.value.source.dispatchId, itemId: preview.value.source.itemId, reason: reason.value.trim(), expectedSourceHash: preview.value.sourceHash, requestKey: newOperationKey() } }
  approved.value = false; error.value = ''
}
function prepareEvent() {
  if (!selected.value || locked.value) return
  const body: RejectionAppend = { kind: kind.value, performerName: performer.value.trim(), note: note.value.trim(), reference: reference.value.trim() || undefined, expectedStateHash: selected.value.record.stateHash, requestKey: newOperationKey() }
  if (kind.value === 'ANNOTATION') body.targetRevision = Number(target.value)
  else body.occurredAt = occurredAt.value.replace('T', ' ') + ':00'
  if (kind.value === 'QUARANTINE_RECEIVE') { body.warehouseId = Number(destination.value); body.quarantineLocation = location.value.trim() }
  pending.value = { type: 'event', id: selected.value.record.id, body }; approved.value = false; error.value = ''
}
async function save() {
  const draft = pending.value
  if (!draft || !approved.value || busy.value) return
  busy.value = true; error.value = ''
  try {
    selected.value = draft.type === 'create' ? await createRejection(draft.body) : await appendRejection(draft.id, draft.body)
    pending.value = null; preview.value = null; approved.value = false; kind.value = ''; note.value = ''; reason.value = ''
    notice.value = '处置已留档。原拒收和普通库存未变更，未自动提交链上存证。'; await load()
  } catch (cause) { error.value = operationError(cause) + ' 相同内容可重试；需要修改时先返回刷新。' }
  finally { busy.value = false }
}
async function cancel() {
  if (busy.value) return
  const draft = pending.value; pending.value = null; approved.value = false; error.value = ''
  if (draft?.type === 'event') await open(draft.id)
  else await checkSource()
  await load()
}
onMounted(async () => { await Promise.all([load(), warehouses.load()]); if (Number(dispatchId.value) && Number(itemId.value)) await checkSource() })
</script>

<template>
  <div class="management stack rejection-page">
    <InventoryViewTabs />
    <GlassPanel padded="large" class="stack">
      <div class="section-head"><h2 class="display">退回与隔离处置</h2><button class="action-button" :disabled="locked" @click="load">刷新台账</button></div>
      <p class="muted">逐件拒收后登记退回、隔离收货和实际报损。每单对应一个完整包装；隔离保管独立统计，不增加可售库存。退款、召回及重新可售另行办理。</p>
      <ResourceState :loading="warehouses.loading.value" :error="warehouses.error.value" @retry="warehouses.load">
        <form class="filters" @submit.prevent="page = 1; load()">
          <label>隔离仓库<select v-model="warehouse" class="field-input" :disabled="locked"><option value="">全部仓库 / 尚未收货</option><option v-for="w in warehouses.data.value" :key="w.id" :value="String(w.id)">{{ w.warehouseName }}</option></select></label>
          <label>处置状态<select v-model="status" class="field-input" :disabled="locked"><option value="ALL">全部状态</option><option v-for="(label, value) in rejectionStatuses" :key="value" :value="value">{{ label }}</option></select></label>
          <label>完整包装码<input v-model="code" class="field-input" maxlength="128" :disabled="locked"></label><button class="action-button" :disabled="locked">筛选</button>
        </form>
      </ResourceState>
      <ResourceState :loading="list.loading.value" :error="list.error.value" @retry="load">
        <template v-if="list.data.value">
          <p class="notice">{{ warehouse ? '所选仓库' : '当前权限范围' }}隔离保管合计（不随状态和包装码筛选改变）：<span v-for="u in list.data.value.quarantineUnits" :key="u.packageUnit"> {{ u.packageCount }} {{ u.packageUnit }} / {{ Number(u.weightKg).toFixed(3) }} kg；</span><span v-if="!list.data.value.quarantineUnits.length">暂无隔离保管货物。</span></p>
          <p v-if="!list.data.value.rows.length" class="muted">暂无匹配记录。可从销售出库的逐件拒收结果进入，或在下方核对原拒收来源。</p>
          <article v-for="row in list.data.value.rows" :key="row.id" class="case-row"><div><strong>{{ rejectionStatuses[row.status] }} · {{ row.productName }} {{ row.variety }}</strong><p class="mono">{{ row.traceCode }}</p><p class="muted">1 {{ row.packageUnit }} · {{ row.netWeightKg }} kg · {{ row.warehouseName || '尚未隔离收货' }} · 版本 {{ row.revision }}</p></div><button class="action-button" :disabled="locked" @click="open(row.id)">查看处置 #{{ row.id }}</button></article>
          <div class="actions"><span>共 {{ list.data.value.total }} 单 · 第 {{ list.data.value.page }} 页</span><button class="action-button" :disabled="locked || list.data.value.page <= 1" @click="page = list.data.value.page - 1; load()">上一页</button><button class="action-button" :disabled="locked || list.data.value.page * 20 >= list.data.value.total" @click="page = list.data.value.page + 1; load()">下一页</button></div>
        </template>
      </ResourceState>
    </GlassPanel>
    <GlassPanel padded="large" class="stack">
      <h3 class="display">核对逐件拒收来源</h3>
      <form class="filters" @submit.prevent="checkSource"><label>原销售出库 ID<input v-model="dispatchId" type="number" min="1" required class="field-input" :disabled="locked"></label><label>单品 ID<input v-model="itemId" type="number" min="1" required class="field-input" :disabled="locked"></label><button class="action-button" :disabled="locked">核对来源</button></form>
      <template v-if="preview"><p>{{ preview.source.productName }} {{ preview.source.variety }} · {{ preview.source.packingCode }} · 1 {{ preview.source.packageUnit }} / {{ preview.source.netWeightKg }} kg</p><p class="mono">{{ preview.source.traceCode }}</p><p>原拒收发生于 {{ formatDateTime(preview.source.receiptOccurredAt) }}，核对留档于 {{ formatDateTime(preview.source.receiptRecordedAt) }}。</p>
        <button v-if="preview.existingCaseId" class="action-button" :disabled="locked" @click="open(preview.existingCaseId)">查看已有处置单 #{{ preview.existingCaseId }}</button>
        <form v-else-if="auth.canWrite.value" class="stack" @submit.prevent="prepareCreate"><label>登记原因<textarea v-model="reason" required maxlength="500" class="field-input" :disabled="locked" /></label><button class="action-button" :disabled="locked">核对并登记处置单</button></form>
      </template>
    </GlassPanel>
    <GlassPanel v-if="selected" padded="large" class="stack">
      <h3 ref="detailHeading" tabindex="-1" class="display">处置单 #{{ selected.record.id }} · {{ rejectionStatuses[selected.record.status] }}</h3>
      <p class="mono">{{ selected.source.traceCode }}</p><p>{{ selected.source.productName }} {{ selected.source.variety }} · 1 {{ selected.record.packageUnit }} / {{ selected.record.netWeightKg }} kg</p>
      <p>原拒收：{{ formatDateTime(selected.source.receiptOccurredAt) }}；核对留档：{{ formatDateTime(selected.source.receiptRecordedAt) }}。原登记 #0：{{ selected.record.reason }}</p>
      <p class="muted">登记人 #{{ selected.record.recordedBy }} · {{ formatDateTime(selected.record.recordedAt) }} · 当前版本 {{ selected.record.revision }}</p>
      <p v-if="selected.record.warehouseId">最近隔离仓库：{{ selected.record.warehouseName }} / {{ selected.record.quarantineLocation }}</p>
      <p class="mono hash">当前处置摘要：{{ selected.record.stateHash }}</p>
      <article v-for="event in selected.events" :key="event.revision" class="event-card stack"><strong>#{{ event.revision }} {{ rejectionKinds[event.kind] }}<template v-if="event.targetRevision !== undefined"> · 针对 #{{ event.targetRevision }}</template></strong><p>发生：{{ formatDateTime(event.occurredAt) }} · 留档：{{ formatDateTime(event.recordedAt) }} · 登记人 #{{ event.recordedBy }}</p><p>{{ event.performerName }} · {{ event.note }}</p><p v-if="event.warehouseName">{{ event.warehouseName }} / {{ event.quarantineLocation }}</p><p v-if="event.reference">参考：{{ event.reference }}</p><small class="mono hash">{{ event.eventHash }}</small></article>
      <form v-if="auth.canWrite.value" class="stack" @submit.prevent="prepareEvent">
        <label>办理事项<select v-model="kind" required class="field-input" :disabled="locked"><option value="">请选择</option><option v-for="action in actions" :key="action" :value="action">{{ rejectionKinds[action] }}</option></select></label>
        <template v-if="kind"><p v-if="kind === 'ANNOTATION'" class="notice">仅追加说明，不覆盖原记录，不改数量、状态和仓库；涉及实际业务变更须另行办理。</p><label v-if="kind === 'ANNOTATION'">针对历史版本<select v-model="target" class="field-input" :disabled="locked"><option :value="0">#0 原登记</option><option v-for="e in selected.events" :key="e.revision" :value="e.revision">#{{ e.revision }} {{ rejectionKinds[e.kind] }}</option></select></label><label v-else>实际发生时间<input v-model="occurredAt" type="datetime-local" required class="field-input" :disabled="locked"></label>
          <template v-if="kind === 'QUARANTINE_RECEIVE'"><label>实际隔离收货仓库<select v-model="destination" required class="field-input" :disabled="locked"><option value="">请选择同机构仓库</option><option v-for="w in warehouses.data.value" :key="w.id" :value="String(w.id)">{{ w.warehouseName }}</option></select></label><label>隔离位置<input v-model="location" required maxlength="256" class="field-input" :disabled="locked"></label></template>
          <label>实际办理人员<input v-model="performer" required maxlength="128" class="field-input" :disabled="locked"></label><label>处置或更正说明<textarea v-model="note" required maxlength="1000" class="field-input" :disabled="locked" /></label><label>参考单据编号<input v-model="reference" maxlength="128" class="field-input" :disabled="locked"></label><button class="action-button" :disabled="locked">核对本次操作</button>
        </template>
      </form>
    </GlassPanel>
    <GlassPanel v-if="pending" padded="large" class="stack confirmation"><strong ref="confirmationHeading" tabindex="-1">确认{{ pending.type === 'create' ? '登记拒收处置单' : rejectionKinds[pending.body.kind] }}</strong><p v-if="pending.type === 'create'">出库 #{{ pending.body.dispatchId }} / 单品 #{{ pending.body.itemId }} · {{ pending.body.reason }}</p><template v-else><p>处置单 #{{ pending.id }} · {{ pending.body.occurredAt || `针对历史 #${pending.body.targetRevision}` }}</p><p>{{ pending.body.performerName }} · {{ pending.body.note }}</p><p v-if="pending.body.warehouseId">隔离仓库 #{{ pending.body.warehouseId }} / {{ pending.body.quarantineLocation }}</p><p v-if="pending.body.reference">{{ pending.body.reference }}</p></template><label class="approval"><input v-model="approved" type="checkbox" :disabled="busy">我已核对原拒收、实物与办理记录，确认留档；普通库存及链上状态不会自动变更。</label><div class="actions"><button class="action-button primary" :disabled="busy || !approved" @click="save">{{ busy ? '正在保存…' : '确认保存 / 同内容重试' }}</button><button class="action-button" :disabled="busy" @click="cancel">返回并刷新核对</button></div></GlassPanel>
    <p v-if="error" class="notice bad" role="alert">{{ error }}</p><p v-if="notice" class="notice" role="status">{{ notice }}</p>
  </div>
</template>

<style scoped>
.rejection-page { min-width: 0; }.case-row { display: flex; gap: 16px; justify-content: space-between; align-items: center; padding: 16px 0; border-bottom: 1px solid var(--glass-hairline); }.case-row>div { min-width: 0; }.case-row p,.hash { overflow-wrap: anywhere; }.event-card { padding: 16px; border: 1px solid var(--glass-hairline); border-radius: var(--radius-md); }.approval { display: flex; gap: 12px; align-items: start; }.confirmation { border: 1px solid var(--field); }label { display: grid; gap: 8px; }textarea { min-height: 90px; }@media(max-width: 600px) { .case-row { align-items: stretch; flex-direction: column; } }
</style>
