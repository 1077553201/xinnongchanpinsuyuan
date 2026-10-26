<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import DispatchItemsPanel from '@/components/DispatchItemsPanel.vue'
import SalesReplacementPanel from '@/components/SalesReplacementPanel.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { listOrganizations } from '@/api/production'
import { inventoryDetail, listWarehouses, warehouseStock } from '@/api/inventory'
import { SALES_STATUS, closeSalesOrder, createSalesOrder, dispatchSales, listSales, salesDetail } from '@/api/sales'
import type { SalesLine } from '@/api/sales'
import { salesAmount } from '@/utils/sales'
import { eventTime } from '@/utils/items'
import { localDateTime, newOperationKey, operationError, uncertainOperation } from '@/utils/growing'
import { formatDateTime } from '@/types/api'

const route = useRoute(), auth = useAuth()
const organizations = useAsyncResource(listOrganizations), warehouses = useAsyncResource(listWarehouses)
const search = ref(''), applied = ref(''), status = ref(''), page = ref(1), selectedId = ref(0)
const orders = useAsyncResource(() => listSales(applied.value, status.value, page.value))
const detail = useAsyncResource(() => salesDetail(selectedId.value))
const order = computed(() => detail.data.value?.order)
const canDispatch = computed(() => order.value && ['OPEN', 'PARTIAL'].includes(order.value.status))
const mode = ref<'create' | 'dispatch' | 'close' | null>(null), busy = ref(false), uncertain = ref(false), error = ref(''), notice = ref('')
const currentLine = ref<SalesLine | null>(null)
const codesDispatch = ref(0), codesBusy = ref(false), replacementOrder = ref(0), replacementBusy = ref(false)
const draft = reactive({ org: '', code: '', date: '', customer: '', contact: '', address: '', notes: '', key: '', quantity: '', at: '', performer: '', reason: '', reference: '' })
interface DraftLine { packingId: number; warehouseId: number; title: string; warehouse: string; unit: string; netWeight: number; quantity: string | number; price: string | number }
const lines = ref<DraftLine[]>([])
const pickerWarehouse = ref(''), pickerSearch = ref(''), pickerTerm = ref(''), pickerPage = ref(1), pickerPacking = ref('')
const stock = useAsyncResource(() => warehouseStock(Number(pickerWarehouse.value), pickerTerm.value, false, pickerPage.value, 20))
const eligibleWarehouses = computed(() => (warehouses.data.value ?? []).filter(item => item.organizationId === Number(draft.org)))
const amount = computed(() => salesAmount(lines.value))
const title = computed(() => mode.value === 'create' ? '建立销售订单' : mode.value === 'dispatch' ? '登记实际销售出库' : '停止订单剩余出库')
const kg = (value: number) => Number(value).toFixed(3)
const today = () => localDateTime().slice(0, 10)

watch(selectedId, () => { if (selectedId.value) void detail.load() })
watch(pickerWarehouse, () => { pickerPacking.value = ''; pickerPage.value = 1; pickerSearch.value = ''; pickerTerm.value = ''; if (pickerWarehouse.value) void stock.load() })
onMounted(async () => {
  await Promise.all([orders.load(), organizations.load(), warehouses.load()])
  const id = Number(route.query.order)
  if (Number.isSafeInteger(id) && id > 0) selectedId.value = id
  if (auth.canWrite.value && route.query.packing && route.query.warehouse) await openCreate(true)
})
function closeReplacement() { if (!replacementBusy.value) replacementOrder.value = 0 }
function openRelatedOrder(id: number) { if (!replacementBusy.value) { replacementOrder.value = 0; if (selectedId.value === id) void detail.load(); else selectedId.value = id } }
async function refresh() { await orders.load(); if (selectedId.value) await detail.load() }
function filter() { page.value = 1; applied.value = search.value.trim(); void orders.load() }
async function turn(next: number) { page.value = next; await orders.load() }
function reset() { error.value = ''; uncertain.value = false; Object.assign(draft, { key: newOperationKey(), quantity: '', at: `${localDateTime()}:${String(new Date().getSeconds()).padStart(2, '0')}`, performer: '', reason: '', reference: '' }) }
async function openCreate(prefill = false) {
  reset(); mode.value = 'create'; lines.value = []; pickerWarehouse.value = ''; pickerPacking.value = ''; pickerTerm.value = ''; pickerSearch.value = ''
  Object.assign(draft, { org: String(organizations.data.value?.[0]?.id ?? ''), code: `ORDER-${newOperationKey()}`, date: today(), customer: '', contact: '', address: '', notes: '' })
  if (prefill) {
    busy.value = true
    try {
      const result = await inventoryDetail(Number(route.query.packing))
      const warehouse = warehouses.data.value?.find(item => item.id === Number(route.query.warehouse) && item.organizationId === result.source.organizationId)
      if (!warehouse) throw new Error('invalid warehouse')
      draft.org = String(warehouse.organizationId); pickerWarehouse.value = String(warehouse.id)
      lines.value.push({ packingId: result.source.id, warehouseId: warehouse.id, title: `${result.source.productName} · ${result.source.packingCode}`, warehouse: warehouse.warehouseName, unit: result.source.packageUnit, netWeight: result.source.netWeightKg, quantity: '', price: '' })
    } catch (cause) { error.value = operationError(cause) }
    finally { busy.value = false }
  }
}
function changeOrg() { pickerWarehouse.value = ''; pickerPacking.value = '' }
function searchStock() { pickerTerm.value = pickerSearch.value.trim(); pickerPage.value = 1; if (pickerWarehouse.value) void stock.load() }
function addLine() {
  const item = stock.data.value?.rows.find(row => row.id === Number(pickerPacking.value))
  const warehouse = warehouses.data.value?.find(row => row.id === Number(pickerWarehouse.value))
  if (!item || !warehouse) return
  if (lines.value.some(line => line.packingId === item.id && line.warehouseId === warehouse.id)) { error.value = '这批货物与仓库已在明细中，请直接修改件数。'; return }
  if (lines.value.length >= 50) { error.value = '一张订单最多 50 条明细。'; return }
  lines.value.push({ packingId: item.id, warehouseId: warehouse.id, title: `${item.productName} · ${item.packingCode}`, warehouse: warehouse.warehouseName, unit: item.packageUnit, netWeight: item.netWeightKg, quantity: '', price: '' }); pickerPacking.value = ''; error.value = ''
}
function openDispatch(line: SalesLine) { reset(); currentLine.value = line; mode.value = 'dispatch' }
function openClose() { reset(); mode.value = 'close' }
function close() { if (!busy.value) mode.value = null }
async function save() {
  if (busy.value || uncertain.value) return
  if (mode.value === 'create' && amount.value === null) { error.value = '请添加货物并填写整数件数和最多两位小数的单价。'; return }
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'create') {
      const created = await createSalesOrder({ organizationId: Number(draft.org), orderCode: draft.code, orderedOn: draft.date, customerName: draft.customer, customerContact: draft.contact, deliveryAddress: draft.address, notes: draft.notes, requestKey: draft.key, lines: lines.value.map(line => ({ packingId: line.packingId, warehouseId: line.warehouseId, packageCount: Number(line.quantity), unitPrice: String(line.price) })) })
      selectedId.value = created.id; notice.value = '订单已建立，尚未扣减或预占库存。'
    } else if (mode.value === 'dispatch' && currentLine.value) {
      await dispatchSales(currentLine.value.id, { packageCount: Number(draft.quantity), occurredAt: eventTime(draft.at), performerName: draft.performer, reason: draft.reason, reference: draft.reference, requestKey: draft.key }); notice.value = '实际销售出库与库存流水已同时保存；尚不代表运输交付或收款完成。'
    } else { await closeSalesOrder(selectedId.value, draft.reason); notice.value = '未出库部分已停止，已出库记录与库存流水保留。' }
    mode.value = null; await refresh()
  } catch (cause) { error.value = operationError(cause); uncertain.value = uncertainOperation(cause) }
  finally { busy.value = false }
}
</script>

<template>
  <div class="management stack">
    <GlassPanel padded="large" class="stack"><div class="section-head"><div><p class="eyebrow">从订单安排，到每一次真实出库</p><h2 class="display intro-title">记清卖给谁，也记清从哪里发。</h2></div><div class="actions"><button class="action-button" :disabled="orders.loading.value" @click="refresh">刷新销售</button><button v-if="auth.canWrite.value" class="action-button primary" :disabled="!organizations.data.value?.length || warehouses.loading.value" @click="openCreate()">建立销售订单</button></div></div><p class="muted">订单记录客户、货物与约定金额；下单不预占库存。实际办理出库后才减少所选仓库余量，出库完成不等于消费者签收。</p><ResourceState :loading="organizations.loading.value || warehouses.loading.value" :error="organizations.error.value || warehouses.error.value" @retry="organizations.load(); warehouses.load()" /></GlassPanel>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <GlassPanel padded="large" class="stack"><h2 class="display">销售订单</h2><form class="filters" @submit.prevent="filter"><label>搜索订单或客户<input v-model="search" class="field-input" maxlength="128" placeholder="订单编码、客户或收货地址"></label><label>履约状态<select v-model="status" class="field-input" @change="filter"><option value="">全部状态</option><option v-for="(label,key) in SALES_STATUS" :key="key" :value="key">{{ label }}</option></select></label><button class="action-button" :disabled="orders.loading.value" type="submit">查询订单</button></form><ResourceState :loading="orders.loading.value" :error="orders.error.value" :empty="!orders.data.value?.rows.length" empty-text="暂无匹配的销售订单。" @retry="orders.load"><div class="table-scroll"><table class="data-table"><thead><tr><th>订单 / 下单日期</th><th>客户</th><th>约定总额（人民币）</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in orders.data.value?.rows" :key="item.id"><td>{{ item.orderCode }}<p class="muted">{{ item.orderedOn }}</p></td><td>{{ item.customerName }}</td><td>¥ {{ item.totalAmount }}</td><td>{{ SALES_STATUS[item.status] }}</td><td><button class="text-button" @click="selectedId = item.id">查看订单与出库</button></td></tr></tbody></table></div></ResourceState><div v-if="orders.data.value" class="section-head"><p class="muted small">共 {{ orders.data.value.total }} 单 · 第 {{ orders.data.value.page }} 页</p><div class="actions"><button class="action-button" :disabled="orders.loading.value || orders.data.value.page <= 1" @click="turn((orders.data.value?.page ?? 1) - 1)">上一页</button><button class="action-button" :disabled="orders.loading.value || orders.data.value.page * 20 >= orders.data.value.total" @click="turn((orders.data.value?.page ?? 1) + 1)">下一页</button></div></div></GlassPanel>
    <GlassPanel v-if="selectedId" padded="large" class="stack"><ResourceState :loading="detail.loading.value" :error="detail.error.value" @retry="detail.load"><template v-if="order"><div class="section-head"><div><p class="eyebrow">{{ SALES_STATUS[order.status] }}</p><h2 class="display source-title">{{ order.orderCode }}</h2></div><div class="actions"><button class="action-button" @click="replacementOrder = order.id">更正影响 / 替代历史</button><button v-if="auth.canWrite.value && canDispatch" class="action-button" @click="openClose">停止剩余出库</button></div></div><dl class="detail-grid"><div><dt>客户 / 联系方式</dt><dd>{{ order.customerName }} · {{ order.customerContact || '未填写' }}</dd></div><div><dt>收货地址</dt><dd class="pre-wrap">{{ order.deliveryAddress }}</dd></div><div><dt>下单日期 / 约定总额（人民币）</dt><dd>{{ order.orderedOn }} · ¥ {{ order.totalAmount }}</dd></div><div><dt>录入留档</dt><dd>{{ order.recorderName }} · {{ formatDateTime(order.recordedAt) }}</dd></div></dl><p v-if="order.notes" class="pre-wrap muted">{{ order.notes }}</p><p v-if="order.closeReason" class="notice">停止说明：{{ order.closeReason }} · {{ formatDateTime(order.closedAt) }}。已出库部分不自动退回。</p>
      <div class="table-scroll"><table class="data-table"><thead><tr><th>货物 / 来源仓库</th><th>订购 / 已出库</th><th>单价 / 明细金额（人民币）</th><th>来源 / 办理</th></tr></thead><tbody><tr v-for="line in detail.data.value?.lines" :key="line.id"><td>{{ line.productName }} · {{ line.variety }}<p class="mono">{{ line.packingCode }}</p><p>{{ line.warehouseName }} · {{ kg(line.netWeightKg) }} kg / {{ line.packageUnit }}</p></td><td>订购 {{ line.packageCount }} {{ line.packageUnit }}<p>已出库 {{ line.dispatchedCount }} {{ line.packageUnit }}</p><p>{{ canDispatch ? '尚未出库' : order.status === 'FULFILLED' ? '剩余' : '已停止余量' }} {{ line.packageCount - line.dispatchedCount }} {{ line.packageUnit }}</p></td><td>¥ {{ line.unitPrice }} / {{ line.packageUnit }}<p>¥ {{ line.lineAmount }}</p></td><td><RouterLink :to="{ path: '/admin/inventory', query: { cycle: line.cycleId, packing: line.packingId, warehouse: line.warehouseId } }" class="text-button">查看来源与库存 →</RouterLink><p><button v-if="auth.canWrite.value && canDispatch && line.dispatchedCount < line.packageCount" class="text-button" @click="openDispatch(line)">登记实际销售出库</button></p></td></tr></tbody></table></div>
      <h3 class="display">销售出库记录 · 最近 200 条</h3><p class="muted small">每笔关联唯一库存出库流水，按实际办理分别留档，不重复扣库存。</p><p v-if="!detail.data.value?.dispatches.length" class="muted">暂无实际销售出库。</p><div v-else class="table-scroll"><table class="data-table"><thead><tr><th>库存单据 / 明细</th><th>实际出库</th><th>实际时间 / 人员</th><th>说明 / 录入</th></tr></thead><tbody><tr v-for="item in detail.data.value?.dispatches" :key="item.id"><td>{{ item.movementCode }}<p>订单明细 #{{ item.lineId }} · 库存流水 #{{ item.movementId }}</p><RouterLink class="text-button" :to="{ path: '/admin/transport', query: item.shipmentId ? { shipment: item.shipmentId } : { order: order.id, dispatch: item.id } }">{{ item.shipmentId ? '查看运输与交付 →' : '登记运输安排 →' }}</RouterLink><p><button class="text-button" @click="codesDispatch = item.id">核对包装码 / 逐件收货</button></p></td><td>{{ item.packageCount }} {{ item.packageUnit }} · {{ kg(item.weightKg) }} kg</td><td>{{ formatDateTime(item.occurredAt) }}<p>{{ item.performerName }}</p></td><td class="pre-wrap">{{ item.reason }}<p>{{ item.reference || '无外部凭证引用' }}</p><p class="muted">{{ item.recorderName }} · {{ formatDateTime(item.recordedAt) }}</p></td></tr></tbody></table></div>
    </template></ResourceState></GlassPanel>
    <AppDialog :open="replacementOrder > 0" title="订单更正与替代" :busy="replacementBusy" @close="closeReplacement"><SalesReplacementPanel v-if="replacementOrder" :key="replacementOrder" :order-id="replacementOrder" @busy="replacementBusy = $event" @saved="refresh" @open-order="openRelatedOrder" /></AppDialog>
    <AppDialog :open="codesDispatch > 0" title="本笔出库的包装码" :busy="codesBusy" @close="codesDispatch = 0"><DispatchItemsPanel v-if="codesDispatch" :key="codesDispatch" :dispatch-id="codesDispatch" @busy="codesBusy = $event" /></AppDialog>
    <AppDialog :open="mode !== null" :title="title" :busy="busy" @close="close"><form class="management" @submit.prevent="save"><fieldset :disabled="busy || uncertain">
      <template v-if="mode === 'create'"><label class="form-field">所属机构<select v-model="draft.org" class="field-input" required :disabled="lines.length > 0" @change="changeOrg"><option value="">选择机构</option><option v-for="item in organizations.data.value" :key="item.id" :value="String(item.id)">{{ item.name }}</option></select></label><label class="form-field">订单编码<input v-model.trim="draft.code" class="field-input" maxlength="64" pattern="[A-Za-z0-9_-]+" required></label><label class="form-field">实际下单日期<input v-model="draft.date" class="field-input" type="date" :max="today()" required></label><label class="form-field">客户名称<input v-model.trim="draft.customer" class="field-input" maxlength="128" required></label><label class="form-field">联系方式（选填）<input v-model.trim="draft.contact" class="field-input" maxlength="128"></label><label class="form-field">收货地址<textarea v-model.trim="draft.address" class="field-input" rows="2" maxlength="500" required></textarea></label>
        <section class="picker stack"><h3 class="display">选择货物并加入明细</h3><label class="form-field">来源仓库<select v-model="pickerWarehouse" class="field-input"><option value="">选择同机构仓库</option><option v-for="item in eligibleWarehouses" :key="item.id" :value="String(item.id)">{{ item.warehouseName }} · {{ item.warehouseCode }}</option></select></label><template v-if="pickerWarehouse"><label class="form-field">搜索在库货物<input v-model="pickerSearch" class="field-input" maxlength="128" placeholder="产品、批次、等级或地块"></label><button type="button" class="action-button" :disabled="stock.loading.value" @click="searchStock">查询在库货物</button><ResourceState :loading="stock.loading.value" :error="stock.error.value" :empty="!stock.data.value?.rows.length" empty-text="本仓没有匹配的在库货物。" @retry="stock.load"><label class="form-field">货物来源<select v-model="pickerPacking" class="field-input"><option value="">选择包装批次</option><option v-for="item in stock.data.value?.rows" :key="item.id" :value="String(item.id)">{{ item.productName }} · {{ item.packingCode }} · 本仓 {{ item.availableCount }} {{ item.packageUnit }}</option></select></label><button type="button" class="action-button" :disabled="!pickerPacking" @click="addLine">加入订单明细</button><div class="actions"><button type="button" class="text-button" :disabled="(stock.data.value?.page ?? 1) <= 1" @click="pickerPage = (stock.data.value?.page ?? 1) - 1; stock.load()">上一页货物</button><button type="button" class="text-button" :disabled="(stock.data.value?.page ?? 1) * 20 >= (stock.data.value?.total ?? 0)" @click="pickerPage = (stock.data.value?.page ?? 1) + 1; stock.load()">下一页货物</button></div></ResourceState></template></section>
        <article v-for="(line,index) in lines" :key="`${line.packingId}-${line.warehouseId}`" class="line-card"><p>{{ line.title }}</p><p class="muted small">{{ line.warehouse }} · {{ kg(line.netWeight) }} kg / {{ line.unit }}</p><div class="form-grid"><label class="form-field">订购件数（{{ line.unit }}）<input v-model="line.quantity" class="field-input" type="number" min="1" max="1000000" step="1" required></label><label class="form-field">单价（人民币元 / {{ line.unit }}）<input v-model="line.price" class="field-input" type="number" min="0" max="999999999.99" step="0.01" required></label></div><button class="text-button" type="button" @click="lines.splice(index,1)">移除此明细</button></article><p class="notice">{{ lines.length }} 条明细 · 约定总额 ¥ {{ amount ?? '请补齐数量与单价' }}（人民币）。确认后不预占、不扣库存，实际出库时重新校验。</p><label class="form-field">订单说明（选填）<textarea v-model.trim="draft.notes" class="field-input" rows="2" maxlength="2000"></textarea></label>
      </template>
      <template v-else-if="mode === 'dispatch' && currentLine"><p class="notice">{{ currentLine.packingCode }} · 固定从 {{ currentLine.warehouseName }} 出库。本条尚未出库 {{ currentLine.packageCount - currentLine.dispatchedCount }} {{ currentLine.packageUnit }}；最终数量以保存时核对为准。</p><label class="form-field">实际出库件数（{{ currentLine.packageUnit }}）<input v-model="draft.quantity" class="field-input" type="number" min="1" max="1000000" step="1" required></label><label class="form-field">实际出库时间<input v-model="draft.at" class="field-input" type="datetime-local" step="1" required></label><label class="form-field">实际办理人员<input v-model.trim="draft.performer" class="field-input" maxlength="128" required></label><label class="form-field">实际出库说明<textarea v-model.trim="draft.reason" class="field-input" rows="3" maxlength="1000" required></textarea></label><label class="form-field">外部凭证引用（选填）<input v-model.trim="draft.reference" class="field-input" maxlength="128"></label><p class="notice wait">确认后同时登记销售出库和库存流水；请勿再去库存页面为同一次办理重复登记普通出库。</p></template>
      <template v-else><p class="notice wait">只停止尚未出库的部分；已出库数量和库存流水保留，不自动退货或退回库存。</p><label class="form-field">停止原因<textarea v-model.trim="draft.reason" class="field-input" rows="3" maxlength="1000" required></textarea></label></template>
    </fieldset><p v-if="error" class="notice bad" role="alert">{{ error }}</p><div class="actions form-actions"><button class="action-button primary" type="submit" :disabled="busy || uncertain">{{ busy ? '正在保存…' : '确认保存' }}</button><button class="action-button" type="button" :disabled="busy" @click="close">关闭</button></div></form></AppDialog>
  </div>
</template>

<style scoped>
h2 { font-size: var(--fs-h2); }.intro-title { margin-top: 8px; font-size: clamp(1.2rem,2vw,1.65rem); line-height: 1.5; }.source-title { margin-top: 8px; overflow-wrap: anywhere; }.small { font-size: var(--fs-sm); }.pre-wrap { white-space: pre-wrap; }.form-actions { margin-top: 20px; }.line-card,.picker { padding: 16px; margin: 16px 0; border: 1px solid var(--ink-16); border-radius: var(--radius-md); overflow-wrap: anywhere; }.line-card .form-grid { margin-top: 12px; }
</style>
