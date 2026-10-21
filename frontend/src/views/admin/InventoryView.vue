<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import InventoryViewTabs from '@/components/InventoryViewTabs.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { listCycles, listPlots, listOrganizations } from '@/api/production'
import { createWarehouse, inventoryDetail, listInventory, listWarehouses, recordMovement } from '@/api/inventory'
import { formatDateTime } from '@/types/api'
import { eventTime } from '@/utils/items'
import { localDateTime, newOperationKey, operationError, uncertainOperation } from '@/utils/growing'

const auth = useAuth(), route = useRoute()
const cycles = useAsyncResource(listCycles), plots = useAsyncResource(listPlots), organizations = useAsyncResource(listOrganizations), warehouses = useAsyncResource(listWarehouses)
const cycleId = ref(typeof route.query.cycle === 'string' ? route.query.cycle : ''), selectedId = ref(0)
const cycle = computed(() => cycles.data.value?.find(item => item.id === Number(cycleId.value)))
const plotMap = computed(() => new Map((plots.data.value ?? []).map(plot => [plot.id, plot])))
const sources = useAsyncResource(() => listInventory(Number(cycleId.value)))
const detail = useAsyncResource(() => inventoryDetail(selectedId.value))
const source = computed(() => detail.data.value?.source)
const relevantWarehouses = computed(() => (warehouses.data.value ?? []).filter(item => item.organizationId === source.value?.organizationId))
const mode = ref<'warehouse' | 'IN' | 'OUT' | 'LOSS' | null>(null)
const labels = { IN: '实际入库', OUT: '实际出库', LOSS: '库存损耗' } as const
const title = computed(() => mode.value === 'warehouse' ? '登记仓库' : `登记${labels[mode.value ?? 'IN']}`)
const busy = ref(false), uncertain = ref(false), error = ref(''), notice = ref('')
const draft = reactive({ code: '', org: '', name: '', location: '', notes: '', warehouse: '', count: '', at: '', performer: '', destination: '', reason: '', reference: '', key: '' })
const position = computed(() => detail.data.value?.positions.find(item => item.warehouseId === Number(draft.warehouse)))
const warehouseContext = computed(() => relevantWarehouses.value.find(item => item.id === Number(route.query.warehouse)))
const contextPosition = computed(() => detail.data.value?.positions.find(item => item.warehouseId === warehouseContext.value?.id))
const kg = (value: number) => Number(value).toFixed(3)

onMounted(async () => {
  await Promise.all([cycles.load(), plots.load(), organizations.load(), warehouses.load()])
  if (cycle.value) {
    await sources.load()
    const requested = Number(route.query.packing)
    if (Number.isSafeInteger(requested) && requested > 0) selectedId.value = requested
  }
})
watch(cycleId, () => { selectedId.value = 0; notice.value = ''; if (cycle.value) void sources.load() })
watch(selectedId, () => { if (selectedId.value) void detail.load() })
async function refresh() { await Promise.all([warehouses.load(), sources.load()]); if (selectedId.value) await detail.load() }
function open(next: NonNullable<typeof mode.value>) {
  mode.value = next; error.value = ''; uncertain.value = false
  Object.assign(draft, { code: `${next === 'warehouse' ? 'WH' : 'STOCK'}-${newOperationKey()}`, org: String(plotMap.value.get(cycle.value?.plotId ?? 0)?.organizationId ?? organizations.data.value?.[0]?.id ?? ''), name: '', location: '', notes: '', warehouse: '', count: '', at: `${localDateTime()}:${String(new Date().getSeconds()).padStart(2, '0')}`, performer: '', destination: '', reason: '', reference: '', key: newOperationKey() })
  if (warehouseContext.value) draft.warehouse = String(warehouseContext.value.id)
}
function close() { if (!busy.value) mode.value = null }
async function save() {
  if (busy.value || uncertain.value) return
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'warehouse') {
      await createWarehouse({ organizationId: Number(draft.org), warehouseCode: draft.code, warehouseName: draft.name, location: draft.location, notes: draft.notes })
      notice.value = '仓库已登记，尚未自动接收任何包装。'; await warehouses.load()
    } else if (mode.value) {
      await recordMovement(selectedId.value, { warehouseId: Number(draft.warehouse), movementCode: draft.code, kind: mode.value, packageCount: Number(draft.count), occurredAt: eventTime(draft.at), performerName: draft.performer, destination: draft.destination, reason: draft.reason, reference: draft.reference, requestKey: draft.key })
      notice.value = `${labels[mode.value]}已登记，库存按真实流水核对；未自动建立销售单或运输记录。`
    }
    mode.value = null; if (cycle.value) await refresh()
  } catch (cause) { error.value = operationError(cause); uncertain.value = uncertainOperation(cause) }
  finally { busy.value = false }
}
</script>

<template>
  <div class="management stack">
    <InventoryViewTabs />
    <GlassPanel padded="large" class="stack">
      <div class="section-head"><div><p class="eyebrow">每一件入出库，都保留来路与去向</p><h2 class="display intro-title">包装落到账上，余量核对到仓。</h2></div><div class="actions"><button class="action-button" :disabled="!cycle || sources.loading.value" @click="refresh">刷新库存</button><button v-if="auth.canWrite.value" class="action-button" :disabled="organizations.loading.value || !organizations.data.value?.length" @click="open('warehouse')">登记仓库</button></div></div>
      <p class="muted">按完整包装件数办理，净重沿用来源规格。包装完成不等于已入库；出库不自动表示销售成交或运输交付。</p>
      <ResourceState :loading="cycles.loading.value || plots.loading.value" :error="cycles.error.value || plots.error.value" :empty="!cycles.data.value?.length" empty-text="请先登记种植与采收包装来源。" @retry="cycles.load(); plots.load()"><label class="form-field">来源种植周期<select v-model="cycleId" class="field-input"><option value="">选择本轮或历史种植</option><option v-for="item in cycles.data.value" :key="item.id" :value="String(item.id)">{{ plotMap.get(item.plotId)?.plotName }} · {{ item.productName }} / {{ item.variety }} · {{ item.cycleCode }}{{ item.status === 'ENDED' ? '（已结束）' : '' }}</option></select></label></ResourceState>
      <ResourceState :loading="warehouses.loading.value || organizations.loading.value" :error="warehouses.error.value || organizations.error.value" :empty="!warehouses.data.value?.length" empty-text="尚无可用仓库，请先登记同机构仓库。" @retry="warehouses.load(); organizations.load()"><details><summary>仓库档案 · {{ warehouses.data.value?.length }} 个</summary><div class="warehouse-grid"><article v-for="warehouse in warehouses.data.value" :key="warehouse.id" class="storage-card"><h3>{{ warehouse.warehouseName }}</h3><p class="mono">{{ warehouse.warehouseCode }}</p><p class="muted">{{ warehouse.location }}</p><p class="muted">{{ organizations.data.value?.find(org => org.id === warehouse.organizationId)?.name ?? `机构 #${warehouse.organizationId}` }}</p><p v-if="warehouse.notes" class="pre-wrap">{{ warehouse.notes }}</p><RouterLink :to="{ name: 'admin-warehouse-stock', params: { warehouseId: warehouse.id } }" class="text-button">查看本仓货物 →</RouterLink></article></div></details></ResourceState>
    </GlassPanel>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <GlassPanel v-if="cycle" padded="large" class="stack"><h2 class="display">本轮包装库存</h2><p class="muted small">最近最多 200 批，尚未全部入库的优先显示。累计数量覆盖全部流水，记录属于原始种植来源，结束种植后仍可办理。</p><ResourceState :loading="sources.loading.value" :error="sources.error.value" :empty="!sources.data.value?.length" empty-text="本轮尚无实际包装，请先到采收与包装完成来源登记。" @retry="sources.load"><div class="table-scroll"><table class="data-table"><thead><tr><th>包装批次 / 采收来源</th><th>包装规格</th><th>未入库 / 在库</th><th>累计出库 / 损耗</th><th>操作</th></tr></thead><tbody><tr v-for="item in sources.data.value" :key="item.id" :class="{ 'selected-row': item.id === selectedId }"><td>{{ item.packingCode }}<p class="muted">{{ item.harvestCode }} · {{ item.gradeName }}</p></td><td>{{ item.packageCount }} {{ item.packageUnit }}<p>{{ kg(item.netWeightKg) }} kg / {{ item.packageUnit }}</p></td><td>未入库 {{ item.packageCount - item.receivedCount }} {{ item.packageUnit }}<p>在库 {{ item.availableCount }} {{ item.packageUnit }}</p></td><td>出库 {{ item.dispatchedCount }} {{ item.packageUnit }}<p>库存损耗 {{ item.lossCount }} {{ item.packageUnit }}</p></td><td><button class="text-button" :aria-pressed="item.id === selectedId" @click="selectedId = item.id">查看库存与办理</button></td></tr></tbody></table></div></ResourceState></GlassPanel>
    <GlassPanel v-if="cycle && selectedId" padded="large" class="stack"><ResourceState :loading="detail.loading.value" :error="detail.error.value" @retry="detail.load"><template v-if="source && source.cycleId === cycle.id"><p v-if="warehouseContext" class="notice">当前从 {{ warehouseContext.warehouseName }} 进入，本仓余量 {{ contextPosition?.availableCount ?? 0 }} {{ source.packageUnit }}。下方汇总涵盖本批全部仓库，办理时已预选本仓。<RouterLink :to="{ name: 'admin-warehouse-stock', params: { warehouseId: warehouseContext.id } }" class="text-button">返回本仓货物 →</RouterLink></p>
      <div class="section-head"><div><p class="eyebrow">包装来源与库存台账</p><h2 class="display source-title">{{ source.packingCode }}</h2></div><div v-if="auth.canWrite.value" class="actions"><button class="action-button primary" :disabled="!relevantWarehouses.length || source.receivedCount >= source.packageCount" @click="open('IN')">登记入库</button><button class="action-button" :disabled="source.availableCount <= 0" @click="open('OUT')">登记出库</button><button class="action-button" :disabled="source.availableCount <= 0" @click="open('LOSS')">登记库存损耗</button></div></div>
      <dl class="detail-grid"><div><dt>地块 / 产品与品种</dt><dd>{{ source.plotName }} · {{ source.productName }} / {{ source.variety }}</dd></div><div><dt>采收 / 等级来源</dt><dd>{{ source.harvestCode }} · {{ source.gradeName }}（#{{ source.gradeId }}）</dd></div><div><dt>实际包装 / 固定规格</dt><dd>{{ formatDateTime(source.packedAt) }} · {{ kg(source.netWeightKg) }} kg / {{ source.packageUnit }}</dd></div><div><dt>来源档案</dt><dd><RouterLink :to="{ path: '/admin/harvest', query: { cycle: source.cycleId } }" class="text-button">查看本轮采收与包装 →</RouterLink></dd></div></dl>
      <div class="balance-grid"><article class="storage-card"><span>包装后未入库</span><strong>{{ source.packageCount - source.receivedCount }} {{ source.packageUnit }}</strong><p>{{ kg((source.packageCount - source.receivedCount) * source.netWeightKg) }} kg</p></article><article class="storage-card"><span>当前在库</span><strong>{{ source.availableCount }} {{ source.packageUnit }}</strong><p>{{ kg(source.availableCount * source.netWeightKg) }} kg</p></article><article class="storage-card"><span>累计实际出库</span><strong>{{ source.dispatchedCount }} {{ source.packageUnit }}</strong><p>{{ kg(source.dispatchedCount * source.netWeightKg) }} kg</p></article><article class="storage-card"><span>累计库存损耗</span><strong>{{ source.lossCount }} {{ source.packageUnit }}</strong><p>{{ kg(source.lossCount * source.netWeightKg) }} kg</p></article></div>
      <h3 class="display">按仓库存</h3><p v-if="!detail.data.value?.positions.length" class="muted">本批尚无仓储流水。</p><div v-else class="warehouse-grid"><article v-for="item in detail.data.value?.positions" :key="item.warehouseId" class="storage-card"><h3>{{ item.warehouseName }}</h3><p class="muted">{{ item.location }}</p><strong>在库 {{ item.availableCount }} {{ source.packageUnit }}</strong><p>{{ kg(item.availableCount * source.netWeightKg) }} kg</p><p class="muted small">累计入库 {{ item.receivedCount }} · 出库 {{ item.dispatchedCount }} · 损耗 {{ item.lossCount }}</p></article></div>
      <h3 class="display">实际库存流水 · 最近 200 条</h3><p class="muted small">时间按本仓本批顺序登记，不能通过倒填流水改变历史余量。损耗与普通出库分别记录；这里只管理完整包装，拆箱、退货、调拨和销售单关联后续接入。</p><div v-if="detail.data.value?.movements.length" class="table-scroll"><table class="data-table"><thead><tr><th>单据 / 仓库</th><th>类型 / 数量</th><th>实际时间 / 人员</th><th>去向 / 原因 / 引用</th><th>录入留档</th></tr></thead><tbody><tr v-for="item in detail.data.value?.movements" :key="item.id"><td>{{ item.movementCode }}<p>{{ item.warehouseName }}</p></td><td>{{ labels[item.kind] }} · {{ item.kind === 'IN' ? '+' : '−' }}{{ item.packageCount }} {{ item.packageUnit }}<p>{{ kg(item.weightKg) }} kg</p></td><td>{{ formatDateTime(item.occurredAt) }}<p>{{ item.performerName }}</p></td><td class="pre-wrap">{{ item.destination || '—' }}<p>{{ item.reason }}</p><p class="muted">{{ item.reference || '无外部凭证引用' }}</p></td><td>{{ item.recorderName }}<p>{{ formatDateTime(item.recordedAt) }}</p></td></tr></tbody></table></div>
    </template><p v-else-if="source" class="notice wait">当前包装不属于所选周期，请从本轮包装库存重新选择。</p></ResourceState></GlassPanel>
    <AppDialog :open="mode !== null" :title="title" :busy="busy" @close="close"><form class="management" @submit.prevent="save"><fieldset :disabled="busy || uncertain">
      <template v-if="mode === 'warehouse'"><label class="form-field">所属机构<select v-model="draft.org" class="field-input" required><option value="">选择机构</option><option v-for="org in organizations.data.value" :key="org.id" :value="String(org.id)">{{ org.name }}</option></select></label><label class="form-field">仓库编码<input v-model.trim="draft.code" class="field-input" pattern="[A-Za-z0-9_-]+" maxlength="64" required></label><label class="form-field">仓库名称<input v-model.trim="draft.name" class="field-input" maxlength="128" required></label><label class="form-field">实际位置<input v-model.trim="draft.location" class="field-input" maxlength="256" required></label><label class="form-field">仓库说明（选填）<textarea v-model.trim="draft.notes" class="field-input" rows="3" maxlength="1000"></textarea></label></template>
      <template v-else><p class="notice">{{ source?.packingCode }} · {{ kg(source?.netWeightKg ?? 0) }} kg / {{ source?.packageUnit }}。只登记实际已发生的办理。</p><label class="form-field">库存单据编码<input v-model.trim="draft.code" class="field-input" pattern="[A-Za-z0-9_-]+" maxlength="64" required></label><label class="form-field">办理仓库<select v-model="draft.warehouse" class="field-input" required><option value="">选择同机构仓库</option><option v-for="warehouse in relevantWarehouses" :key="warehouse.id" :value="String(warehouse.id)">{{ warehouse.warehouseName }} · {{ warehouse.warehouseCode }}</option></select></label><p v-if="draft.warehouse" class="notice">{{ mode === 'IN' ? `本批尚可入库 ${(source?.packageCount ?? 0) - (source?.receivedCount ?? 0)}` : `该仓本批在库 ${position?.availableCount ?? 0}` }} {{ source?.packageUnit }}</p><label class="form-field">实际完整包装件数（{{ source?.packageUnit }}）<input v-model="draft.count" class="field-input" type="number" min="1" max="1000000" step="1" required></label><p class="muted small">对应净重 {{ kg(Number(draft.count) * (source?.netWeightKg ?? 0)) }} kg，最终余量以保存时校验为准。</p><label class="form-field">实际办理时间<input v-model="draft.at" class="field-input" type="datetime-local" step="1" required></label><label class="form-field">实际办理人员<input v-model.trim="draft.performer" class="field-input" maxlength="128" required placeholder="现场人员与登录录入人分别留档"></label><label v-if="mode === 'OUT'" class="form-field">实际去向或领用用途<input v-model.trim="draft.destination" class="field-input" maxlength="256" required placeholder="填写实际接收方或用途，尚不等于销售单"></label><label class="form-field">{{ mode === 'LOSS' ? '库存损耗原因' : '实际办理说明' }}<textarea v-model.trim="draft.reason" class="field-input" rows="3" maxlength="1000" required></textarea></label><label class="form-field">外部凭证引用（选填）<input v-model.trim="draft.reference" class="field-input" maxlength="128"></label></template>
    </fieldset><p v-if="error" class="notice bad" role="alert">{{ error }}</p><div class="actions form-actions"><button type="submit" class="action-button primary" :disabled="busy || uncertain">{{ busy ? '正在保存…' : '确认保存' }}</button><button type="button" class="action-button" :disabled="busy" @click="close">关闭</button></div></form></AppDialog>
  </div>
</template>

<style scoped>
h2 { font-size: var(--fs-h2); }.intro-title { margin-top: 8px; font-size: clamp(1.2rem,2vw,1.65rem); line-height: 1.5; }.source-title { margin-top: 8px; overflow-wrap: anywhere; }.small { font-size: var(--fs-sm); }.pre-wrap { white-space: pre-wrap; }.selected-row { background: var(--field-tint); }.form-actions { margin-top: 20px; }.warehouse-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; margin-top: 12px; }.balance-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 12px; }.storage-card { display: grid; gap: 8px; padding: 16px; border: 1px solid var(--glass-stroke); border-radius: var(--radius-md); background: var(--glass-fill-quiet); overflow-wrap: anywhere; }.storage-card span { color: var(--slate); font-size: var(--fs-xs); }.balance-grid strong { font-size: 1.2rem; }.storage-card p { font-size: var(--fs-sm); }summary { cursor: pointer; color: var(--field); }
@media(max-width:1100px){.balance-grid{grid-template-columns:repeat(2,minmax(0,1fr));}}@media(max-width:560px){.warehouse-grid{grid-template-columns:minmax(0,1fr);}}@media(max-width:380px){.balance-grid{grid-template-columns:minmax(0,1fr);}}
</style>
