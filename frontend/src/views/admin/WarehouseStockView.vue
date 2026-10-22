<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import InventoryViewTabs from '@/components/InventoryViewTabs.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { useAuth } from '@/composables/useAuth'
import { listWarehouses, warehouseStock } from '@/api/inventory'
import { formatDateTime } from '@/types/api'

const route = useRoute(), router = useRouter()
const auth = useAuth()
const warehouses = useAsyncResource(listWarehouses)
const warehouseId = computed(() => Number(route.params.warehouseId) || 0)
const selected = ref(String(warehouseId.value || ''))
const search = ref(''), appliedSearch = ref(''), includeEmpty = ref(false), page = ref(1), pageSize = ref(20)
const stock = useAsyncResource(() => warehouseStock(warehouseId.value, appliedSearch.value, includeEmpty.value, page.value, pageSize.value))
const kg = (value: number) => Number(value).toFixed(3)
const pages = computed(() => Math.max(1, Math.ceil((stock.data.value?.total ?? 0) / pageSize.value)))
function load() { if (warehouseId.value) return stock.load() }
function choose() { void router.push({ name: 'admin-warehouse-stock', params: { warehouseId: selected.value || undefined } }) }
function filter() { appliedSearch.value = search.value.trim(); page.value = 1; void load() }
async function turn(next: number) { page.value = next; await load() }
watch(warehouseId, () => { selected.value = String(warehouseId.value || ''); page.value = 1; search.value = ''; appliedSearch.value = ''; includeEmpty.value = false; void load() })
onMounted(async () => { await warehouses.load(); await load() })
</script>

<template>
  <div class="management stack">
    <InventoryViewTabs />
    <GlassPanel padded="large" class="stack">
      <div class="section-head"><div><p class="eyebrow">走进一个仓库，查看所有在库货物</p><h2 class="display intro-title">本仓有什么、还剩多少，一起看清。</h2></div><button class="action-button" :disabled="!warehouseId || stock.loading.value" @click="load">刷新本仓库存</button></div>
      <p class="muted">跨地块、跨种植周期查看本仓货物。默认仅显示仍有余量的包装批次，件数按包装单位分别汇总。</p>
      <ResourceState :loading="warehouses.loading.value" :error="warehouses.error.value" :empty="!warehouses.data.value?.length" empty-text="尚无仓库，请到按种植来源查看中登记仓库。" @retry="warehouses.load">
        <label class="form-field">查看仓库<select v-model="selected" class="field-input" @change="choose"><option value="">选择要查看的仓库</option><option v-for="item in warehouses.data.value" :key="item.id" :value="String(item.id)">{{ item.warehouseName }} · {{ item.warehouseCode }}</option></select></label>
        <div v-if="!warehouseId" class="warehouse-grid"><article v-for="item in warehouses.data.value" :key="item.id" class="storage-card"><h3>{{ item.warehouseName }}</h3><p class="muted">{{ item.location }}</p><p class="mono">{{ item.warehouseCode }}</p><RouterLink :to="{ name: 'admin-warehouse-stock', params: { warehouseId: item.id } }" class="text-button">查看本仓货物 →</RouterLink></article></div>
      </ResourceState>
    </GlassPanel>
    <template v-if="warehouseId">
      <GlassPanel padded="large" class="stack">
        <ResourceState :loading="stock.loading.value" :error="stock.error.value" @retry="load"><template v-if="stock.data.value">
          <div class="section-head"><div><p class="eyebrow">{{ stock.data.value.warehouse.warehouseCode }}</p><h2 class="display">{{ stock.data.value.warehouse.warehouseName }}</h2></div><p class="muted">{{ stock.data.value.warehouse.location }}</p></div>
          <div class="summary-grid"><article class="storage-card"><span>本仓当前在库批次</span><strong>{{ stock.data.value.summary.batchCount }} 批</strong></article><article class="storage-card"><span>本仓当前在库净重</span><strong>{{ kg(stock.data.value.summary.weightKg) }} kg</strong></article></div>
          <div class="unit-balances"><span v-for="item in stock.data.value.units" :key="item.packageUnit" class="unit-balance">{{ item.packageCount }} {{ item.packageUnit }} · {{ kg(item.weightKg) }} kg</span><p v-if="!stock.data.value.units.length" class="muted">本仓目前没有在库货物。</p></div>
          <p class="muted small">汇总覆盖本仓所有在库货物，不随下方搜索、分页或已清空批次开关变化。箱、袋等不同单位不合并成一个件数。</p>
        </template></ResourceState>
      </GlassPanel>
      <GlassPanel padded="large" class="stack">
        <div class="section-head"><h2 class="display">本仓货物明细</h2><RouterLink to="/admin/inventory" class="text-button">按来源办理新入库 →</RouterLink></div>
        <form class="filters" @submit.prevent="filter"><label>搜索货物与来源<input v-model="search" class="field-input" maxlength="128" placeholder="产品、品种、地块、批次或等级"></label><label>每页条数<select v-model.number="pageSize" class="field-input" @change="filter"><option :value="20">20 条</option><option :value="50">50 条</option><option :value="100">100 条</option></select></label><button class="action-button" type="submit" :disabled="stock.loading.value">查询本仓货物</button></form>
        <label class="include-empty"><input v-model="includeEmpty" type="checkbox" @change="filter">同时显示本仓已清空的历史批次</label>
        <ResourceState :loading="stock.loading.value" :error="stock.error.value" :empty="!stock.data.value?.rows.length" :empty-text="appliedSearch ? '未找到匹配货物，可调整搜索或显示已清空批次。' : includeEmpty ? '本仓尚无货物入库记录。' : '本仓目前没有在库货物，可查看已清空的历史批次。'" @retry="load">
          <div class="table-scroll"><table class="data-table"><thead><tr><th>货物 / 包装批次</th><th>等级 / 包装规格</th><th>本仓余量 / 净重</th><th>地块 / 种植与采收来源</th><th>最近办理 / 操作</th></tr></thead><tbody><tr v-for="item in stock.data.value?.rows" :key="item.id"><td>{{ item.productName }} · {{ item.variety }}<p class="mono">{{ item.packingCode }}</p></td><td>{{ item.gradeName }}<p>{{ kg(item.netWeightKg) }} kg / {{ item.packageUnit }}</p></td><td><strong>{{ item.availableCount }} {{ item.packageUnit }}</strong><p>{{ kg(item.availableWeightKg) }} kg</p><p class="muted">本仓入 {{ item.receivedCount }} · 出 {{ item.dispatchedCount }} · 损耗 {{ item.lossCount }}</p></td><td>{{ item.plotName }}<p>{{ item.cycleCode }}</p><p class="mono">{{ item.harvestCode }}</p></td><td>{{ formatDateTime(item.lastMovementAt) }}<p><RouterLink :to="{ path: '/admin/inventory', query: { cycle: item.cycleId, packing: item.id, warehouse: warehouseId } }" class="text-button">来源与出入库 →</RouterLink></p><p v-if="auth.canWrite.value && item.availableCount > 0"><RouterLink :to="{ path: '/admin/sales', query: { packing: item.id, warehouse: warehouseId } }" class="text-button">建立销售订单 →</RouterLink></p></td></tr></tbody></table></div>
        </ResourceState>
        <div v-if="stock.data.value" class="section-head"><p class="muted small">匹配 {{ stock.data.value.total }} 批 · 第 {{ stock.data.value.page }} / {{ pages }} 页</p><div class="actions"><button class="action-button" :disabled="stock.loading.value || stock.data.value.page <= 1" @click="turn((stock.data.value?.page ?? 1) - 1)">上一页</button><button class="action-button" :disabled="stock.loading.value || stock.data.value.page >= pages" @click="turn((stock.data.value?.page ?? 1) + 1)">下一页</button></div></div>
      </GlassPanel>
    </template>
  </div>
</template>

<style scoped>
h2 { font-size: var(--fs-h2); }.intro-title { margin-top: 8px; font-size: clamp(1.2rem,2vw,1.65rem); line-height: 1.5; }.small { font-size: var(--fs-sm); }.warehouse-grid,.summary-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; }.storage-card { display: grid; gap: 8px; padding: 16px; border: 1px solid var(--glass-stroke); border-radius: var(--radius-md); background: var(--glass-fill-quiet); overflow-wrap: anywhere; }.storage-card span { font-size: var(--fs-xs); color: var(--slate); }.storage-card strong { font-size: 1.4rem; }.unit-balances { display: flex; gap: 12px; flex-wrap: wrap; }.unit-balance { padding: 8px 12px; border-radius: var(--radius-pill); background: var(--field-tint); color: var(--field); font-size: var(--fs-sm); }.include-empty { display: flex; gap: 8px; align-items: center; font-size: var(--fs-sm); }.include-empty input { width: 18px; height: 18px; flex-shrink: 0; }
@media(max-width:560px){.warehouse-grid,.summary-grid{grid-template-columns:minmax(0,1fr);}}
</style>
