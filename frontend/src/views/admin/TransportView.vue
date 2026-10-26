<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import DispatchItemsPanel from '@/components/DispatchItemsPanel.vue'
import TransportPlanPanel from '@/components/TransportPlanPanel.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { salesDetail, type SalesDetail } from '@/api/sales'
import { TRANSPORT_STATUS, TRANSPORT_EVENTS, listTransport, transportDetail, createTransport, recordTransport, type TransportInput } from '@/api/transport'
import { localDateTime, newOperationKey, operationError, uncertainOperation } from '@/utils/growing'
import { eventTime } from '@/utils/items'
import { formatDateTime } from '@/types/api'

const route = useRoute(), auth = useAuth()
const codesBusy = ref(false), planOpen = ref(false), planBusy = ref(false)
const search = ref(''), term = ref(''), status = ref(''), page = ref(1), selected = ref(0)
const shipments = useAsyncResource(() => listTransport(term.value, status.value, page.value))
const detail = useAsyncResource(() => transportDetail(selected.value))
const source = useAsyncResource(() => salesDetail(Number(route.query.order)))
const dispatch = computed(() => source.data.value?.dispatches.find(item => item.id === Number(route.query.dispatch)))
const shipment = computed(() => detail.data.value?.shipment)
const active = computed(() => shipment.value && ['PLANNED', 'IN_TRANSIT'].includes(shipment.value.status))
const mode = ref<'create' | 'event' | null>(null), busy = ref(false), error = ref(''), uncertain = ref(false), notice = ref('')
const form = reactive({ code: '', carrier: '', tracking: '', driver: '', contact: '', notes: '', key: '', kind: 'DEPART' as TransportInput['kind'], at: '', location: '', performer: '', description: '', reference: '', accepted: '' as string | number, rejected: '' as string | number })
function closePlans() { if (!planBusy.value) planOpen.value = false }
watch(selected, () => { if (selected.value) void detail.load() })
onMounted(async () => {
  await shipments.load()
  const id = Number(route.query.shipment)
  if (Number.isSafeInteger(id) && id > 0) selected.value = id
  if (route.query.order && route.query.dispatch) await source.load()
})
function filter() { term.value = search.value.trim(); page.value = 1; void shipments.load() }
async function turn(next: number) { page.value = next; await shipments.load() }
async function refresh() { if (codesBusy.value) return; await shipments.load(); if (selected.value) await detail.load(); if (route.query.dispatch) await source.load() }
function open(next: 'create' | 'event') {
  if (codesBusy.value) return
  error.value = ''; uncertain.value = false; mode.value = next
  Object.assign(form, { key: newOperationKey(), code: `SHIP-${newOperationKey()}`, carrier: '', tracking: '', driver: '', contact: '', notes: '', kind: shipment.value?.status === 'PLANNED' ? 'DEPART' : 'UPDATE', at: `${localDateTime()}:${String(new Date().getSeconds()).padStart(2, '0')}`, location: '', performer: '', description: '', reference: '', accepted: '', rejected: '' })
}
function close() { if (!busy.value) mode.value = null }
async function save() {
  if (busy.value || uncertain.value) return
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'create' && dispatch.value) {
      const created = await createTransport({ dispatchId: dispatch.value.id, shipmentCode: form.code, carrier: form.carrier, trackingReference: form.tracking, driverName: form.driver, driverContact: form.contact, notes: form.notes, requestKey: form.key })
      selected.value = created.id; notice.value = '运输安排已保存，尚未登记实际发运。'
    } else {
      await recordTransport(selected.value, { kind: form.kind, occurredAt: eventTime(form.at), location: form.location, performerName: form.performer, description: form.description, reference: form.reference, requestKey: form.key, ...(form.kind === 'RECEIVE' ? { acceptedCount: Number(form.accepted), rejectedCount: Number(form.rejected) } : {}) })
      notice.value = '实际记录已留档；库存与原销售出库不重复变动。'
    }
    mode.value = null; await refresh()
  } catch (cause) { error.value = operationError(cause); uncertain.value = uncertainOperation(cause) }
  finally { busy.value = false }
}
const sourceTitle = (data: SalesDetail) => `${data.order.orderCode} · ${data.order.customerName}`
</script>

<template>
  <div class="management stack">
    <GlassPanel padded="large" class="stack"><div class="section-head"><div><p class="eyebrow">从离开仓库，到明确收货结果</p><h2 class="display">运输与交付</h2></div><button class="action-button" :disabled="shipments.loading.value || codesBusy" @click="refresh">刷新运输</button></div><p class="muted">从销售订单的实际出库记录建立运输单。安排、发运和收货分别留档，每笔出库整批运输；需要分批发货时，先分别登记销售出库。</p><p class="muted">运输事实保留原始记录；尚无运输事实、逐件绑定或冻结流转证据的待发运安排，可从详情办理撤销与替代版本。</p></GlassPanel>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <GlassPanel v-if="route.query.dispatch" padded="large" class="stack"><ResourceState :loading="source.loading.value" :error="source.error.value" :empty="!dispatch" empty-text="本订单最近 200 条出库中未找到该记录，请回销售订单核对。" @retry="source.load"><template v-if="dispatch && source.data.value"><h2 class="display">所选销售出库</h2><p>{{ sourceTitle(source.data.value) }}</p><p class="mono wrap">{{ dispatch.movementCode }}</p><p>{{ dispatch.packageCount }} {{ dispatch.packageUnit }} · {{ formatDateTime(dispatch.occurredAt) }}</p><RouterLink v-if="dispatch.shipmentId" class="text-button" :to="{ path: '/admin/transport', query: { shipment: dispatch.shipmentId } }" @click="selected = dispatch.shipmentId">查看已有运输单 →</RouterLink><button v-else-if="auth.canWrite.value" class="action-button primary" @click="open('create')">为本笔出库建立运输单</button></template></ResourceState></GlassPanel>
    <GlassPanel padded="large" class="stack"><h2 class="display">运输单</h2><form class="filters" @submit.prevent="filter"><label>搜索运输或订单<input v-model="search" class="field-input" maxlength="128" placeholder="运输单、订单、客户、承运方或运单号"></label><label>运输状态<select v-model="status" class="field-input" @change="filter"><option value="">全部状态</option><option v-for="(label,key) in TRANSPORT_STATUS" :key="key" :value="key">{{ label }}</option></select></label><button class="action-button" :disabled="shipments.loading.value || codesBusy">查询</button></form><ResourceState :loading="shipments.loading.value" :error="shipments.error.value" :empty="!shipments.data.value?.rows.length" empty-text="暂无匹配的运输单，请从销售出库记录建立。" @retry="shipments.load"><div class="table-scroll"><table class="data-table"><thead><tr><th>运输 / 订单</th><th>货物 / 件数</th><th>承运 / 收货</th><th>状态 / 查看</th></tr></thead><tbody><tr v-for="item in shipments.data.value?.rows" :key="item.id"><td>{{ item.shipmentCode }}<p>{{ item.orderCode }}</p></td><td>{{ item.productName }} · {{ item.variety }}<p>{{ item.packageCount }} {{ item.packageUnit }}</p></td><td>{{ item.carrier }}<p>{{ item.recipient }}</p></td><td>{{ TRANSPORT_STATUS[item.status] }}<p><button class="text-button" :disabled="codesBusy" @click="selected = item.id">查看运输详情</button></p></td></tr></tbody></table></div></ResourceState><div v-if="shipments.data.value" class="section-head"><p class="muted">共 {{ shipments.data.value.total }} 单 · 第 {{ shipments.data.value.page }} 页</p><div class="actions"><button class="action-button" :disabled="shipments.loading.value || shipments.data.value.page <= 1" @click="turn((shipments.data.value?.page ?? 1) - 1)">上一页</button><button class="action-button" :disabled="shipments.loading.value || shipments.data.value.page * 20 >= shipments.data.value.total" @click="turn((shipments.data.value?.page ?? 1) + 1)">下一页</button></div></div></GlassPanel>
    <GlassPanel v-if="selected" padded="large" class="stack"><ResourceState :loading="detail.loading.value" :error="detail.error.value" @retry="detail.load"><template v-if="shipment"><div class="section-head"><div><p class="eyebrow">{{ TRANSPORT_STATUS[shipment.status] }}</p><h2 class="display wrap">{{ shipment.shipmentCode }}</h2><p v-if="shipment.planRevision" class="muted">当前安排版本 {{ shipment.planRevision }} · {{ formatDateTime(shipment.planRecordedAt) }}</p></div><button class="action-button" @click="planOpen = true">安排撤销 / 替代历史</button><button v-if="auth.canWrite.value && active" class="action-button primary" @click="open('event')">{{ shipment.status === 'PLANNED' ? '登记实际发运' : '追加运输 / 收货记录' }}</button></div><dl class="detail-grid"><div><dt>来源货物 / 仓库</dt><dd>{{ shipment.productName }} · {{ shipment.variety }} · {{ shipment.packingCode }}<p>{{ shipment.warehouseName }} · {{ shipment.packageCount }} {{ shipment.packageUnit }}</p></dd></div><div><dt>原销售出库</dt><dd>{{ shipment.movementCode }}<p>{{ formatDateTime(shipment.dispatchedAt) }}</p><p v-if="shipment.correctionRecordedAt">最近出库更正：{{ formatDateTime(shipment.correctionRecordedAt) }}。实际发运时间不能早于此时。</p><RouterLink class="text-button" :to="{ path: '/admin/sales', query: { order: shipment.orderId } }">查看销售与库存来源 →</RouterLink></dd></div><div><dt>承运方 / 运单或车辆</dt><dd>{{ shipment.carrier }} · {{ shipment.trackingReference || '未填写' }}<p>{{ shipment.driverName }} · {{ shipment.driverContact || '未填写联系方式' }}</p></dd></div><div><dt>收货方 / 地址</dt><dd>{{ shipment.recipient }}<p class="pre-wrap">{{ shipment.deliveryAddress }}</p></dd></div><div><dt>安排留档</dt><dd>{{ shipment.recorderName }} · {{ formatDateTime(shipment.recordedAt) }}</dd></div></dl><p v-if="shipment.notes" class="muted pre-wrap">{{ shipment.notes }}</p><h3 class="display">运输事实 · 最近 200 条</h3><p class="muted">现场办理人与系统录入人分别记录。异常描述不代表环境已恢复；收货结果由现场人员录入，尚非消费者身份核验。</p><p v-if="!detail.data.value?.events.length" class="notice">仅保存运输安排，尚未登记实际发运。</p><article v-for="event in detail.data.value?.events" :key="event.id" class="event-card stack"><div class="section-head"><strong>{{ TRANSPORT_EVENTS[event.kind] }}</strong><span>{{ formatDateTime(event.occurredAt) }}</span></div><p>{{ event.location }} · {{ event.performerName }}</p><p class="pre-wrap">{{ event.description }}</p><p v-if="event.kind === 'RECEIVE'" class="notice">实收 {{ event.acceptedCount }} {{ shipment.packageUnit }} · 拒收 {{ event.rejectedCount }} {{ shipment.packageUnit }}。拒收未自动退库或退款。</p><p class="muted">凭证：{{ event.reference || '未填写' }} · 录入：{{ event.recorderName }} · {{ formatDateTime(event.recordedAt) }}</p></article></template></ResourceState></GlassPanel>
    <p v-if="shipment?.status === 'CANCELLED'" class="notice">运输安排已撤销，请先确认替代安排，再关联包装码或登记实际发运。</p>
    <GlassPanel v-else-if="shipment" padded="large"><DispatchItemsPanel :key="`${shipment.dispatchId}-${detail.updatedAt.value?.getTime()}`" :dispatch-id="shipment.dispatchId" @busy="codesBusy = $event" /></GlassPanel>
    <AppDialog :open="planOpen && selected > 0" title="运输安排版本" :busy="planBusy" @close="closePlans"><TransportPlanPanel v-if="planOpen && selected" :key="selected" :shipment-id="selected" @busy="planBusy = $event" @saved="refresh" /></AppDialog>
    <AppDialog :open="mode !== null" :title="mode === 'create' ? '建立运输安排' : '登记实际运输事实'" :busy="busy" @close="close"><form class="management" @submit.prevent="save"><fieldset :disabled="busy || uncertain"><template v-if="mode === 'create'"><p class="notice">整笔出库关联唯一运输单，数量、收货方和地址沿用原销售事实；保存安排不表示车辆已经发运。</p><p v-if="source.data.value" class="notice pre-wrap">收货方：{{ source.data.value.order.customerName }}
地址：{{ source.data.value.order.deliveryAddress }}
本笔数量：{{ dispatch?.packageCount }} {{ dispatch?.packageUnit }}。原安排保留；无下游依赖的待发运安排可另行撤销或替代。</p><label class="form-field">运输单编码<input v-model.trim="form.code" class="field-input" maxlength="64" pattern="[A-Za-z0-9_-]+" required></label><label class="form-field">承运方<input v-model.trim="form.carrier" class="field-input" maxlength="128" required></label><label class="form-field">运单号或车辆标识（选填）<input v-model.trim="form.tracking" class="field-input" maxlength="128"></label><label class="form-field">承运联系人<input v-model.trim="form.driver" class="field-input" maxlength="128" required></label><label class="form-field">承运联系方式（选填）<input v-model.trim="form.contact" class="field-input" maxlength="128"></label><label class="form-field">安排说明（选填）<textarea v-model.trim="form.notes" class="field-input" maxlength="1000" rows="3"></textarea></label></template><template v-else><label class="form-field">事实类型<select v-model="form.kind" class="field-input" required><option v-if="shipment?.status === 'PLANNED'" value="DEPART">实际发运</option><template v-else><option value="UPDATE">运输进展</option><option value="EXCEPTION">运输异常</option><option value="RECEIVE">收货结果</option></template></select></label><label class="form-field">实际发生时间<input v-model="form.at" class="field-input" type="datetime-local" step="1" required></label><label class="form-field">实际地点<input v-model.trim="form.location" class="field-input" maxlength="256" required></label><label class="form-field">现场办理 / 收货人员<input v-model.trim="form.performer" class="field-input" maxlength="128" required></label><label class="form-field">实际情况说明<textarea v-model.trim="form.description" class="field-input" maxlength="1000" rows="3" required></textarea></label><label class="form-field">凭证引用（选填）<input v-model.trim="form.reference" class="field-input" maxlength="128"></label><template v-if="form.kind === 'RECEIVE'"><div class="form-grid"><label class="form-field">实收件数<input v-model="form.accepted" class="field-input" type="number" min="0" :max="shipment?.packageCount" step="1" required></label><label class="form-field">拒收件数<input v-model="form.rejected" class="field-input" type="number" min="0" :max="shipment?.packageCount" step="1" required></label></div><p class="notice wait">实收与拒收合计须为 {{ shipment?.packageCount }} {{ shipment?.packageUnit }}。保存后结束本单运输记录，不自动退库、退款或证明消费者身份。</p></template></template></fieldset><p v-if="error" role="alert" class="notice bad">{{ error }}</p><div class="actions"><button class="action-button primary" :disabled="busy || uncertain">{{ busy ? '正在保存…' : '确认保存' }}</button><button type="button" class="action-button" :disabled="busy" @click="close">关闭</button></div></form></AppDialog>
  </div>
</template>

<style scoped>
h2 { font-size: var(--fs-h2); }.wrap { overflow-wrap: anywhere; }.pre-wrap { white-space: pre-wrap; }.event-card { padding: 16px; border: 1px solid var(--ink-16); border-radius: var(--radius-md); overflow-wrap: anywhere; }
</style>
