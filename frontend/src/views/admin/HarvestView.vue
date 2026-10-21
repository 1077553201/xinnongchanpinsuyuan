<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { listCycles, listPlots } from '@/api/production'
import { harvestDetail, listHarvests, recordHarvest, recordGrade, recordPacking, reconcileHarvest } from '@/api/harvest'
import { formatDateTime } from '@/types/api'
import { eventTime } from '@/utils/items'
import { localDateTime, newOperationKey, operationError, uncertainOperation } from '@/utils/growing'

const route = useRoute(), auth = useAuth()
const cycles = useAsyncResource(listCycles), plots = useAsyncResource(listPlots)
const cycleId = ref(typeof route.query.cycle === 'string' ? route.query.cycle : '')
const selectedId = ref(0)
const cycle = computed(() => cycles.data.value?.find(item => item.id === Number(cycleId.value)))
const plotMap = computed(() => new Map((plots.data.value ?? []).map(plot => [plot.id, plot])))
const lots = useAsyncResource(() => listHarvests(Number(cycleId.value)))
const detail = useAsyncResource(() => harvestDetail(selectedId.value))
const current = computed(() => detail.data.value?.harvest)
const mode = ref<'harvest' | 'grade' | 'packing' | 'reconcile' | null>(null)
const title = computed(() => ({ harvest: '登记实际采收', grade: '登记分级或损耗', packing: '登记实际包装', reconcile: '完成分级数量核对' })[mode.value ?? 'harvest'])
const notice = ref(''), error = ref(''), busy = ref(false), uncertain = ref(false)
const draft = reactive({ code: '', at: '', quantity: '', unit: 'kg', performer: '', notes: '', kind: 'SALEABLE', gradeName: '', location: '', reason: '', gradeId: '', count: '', packageUnit: '', netWeight: '', key: '' })
const gradeOptions = computed(() => (detail.data.value?.grades ?? []).filter(grade => grade.kind === 'SALEABLE' && grade.weightKg > grade.packedKg))
const packingSource = computed(() => gradeOptions.value.find(grade => grade.id === Number(draft.gradeId)))
const kg = (value: number) => Number(value).toFixed(3)
const unitLabel = (unit: string) => ({ kg: '公斤', g: '克', jin: '市斤' })[unit] ?? unit

onMounted(async () => { await Promise.all([cycles.load(), plots.load()]); if (cycle.value) await lots.load() })
watch(cycleId, async () => { selectedId.value = 0; notice.value = ''; if (cycle.value) await lots.load() })
watch(selectedId, () => { if (selectedId.value) void detail.load() })
async function refresh() { await lots.load(); if (selectedId.value) await detail.load() }
function open(next: NonNullable<typeof mode.value>) {
  mode.value = next; error.value = ''; uncertain.value = false
  Object.assign(draft, { code: `${next === 'packing' ? 'PACK' : 'HARVEST'}-${newOperationKey()}`, at: `${localDateTime()}:${String(new Date().getSeconds()).padStart(2, '0')}`, quantity: '', unit: 'kg', performer: '', notes: '', kind: 'SALEABLE', gradeName: '', location: '', reason: '', gradeId: '', count: '', packageUnit: '', netWeight: '', key: newOperationKey() })
}
function close() { if (!busy.value) mode.value = null }
async function save() {
  if (busy.value || uncertain.value) return
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'harvest') {
      const created = await recordHarvest(Number(cycleId.value), { lotCode: draft.code, harvestedAt: eventTime(draft.at), quantity: Number(draft.quantity), unit: draft.unit, performerName: draft.performer, notes: draft.notes, requestKey: draft.key })
      selectedId.value = created.id; notice.value = '实际采收已登记，请继续核对分级与损耗。'
    } else if (mode.value === 'grade') {
      await recordGrade(selectedId.value, { kind: draft.kind, gradeName: draft.gradeName, weightKg: Number(draft.quantity), handledAt: eventTime(draft.at), performerName: draft.performer, location: draft.location, reason: draft.reason, requestKey: draft.key })
      notice.value = '分级或损耗已保存，原采收净重保持不变。'
    } else if (mode.value === 'packing') {
      await recordPacking(Number(draft.gradeId), { packingCode: draft.code, packedAt: eventTime(draft.at), packageCount: Number(draft.count), packageUnit: draft.packageUnit, netWeightKg: Number(draft.netWeight), performerName: draft.performer, location: draft.location, notes: draft.notes, requestKey: draft.key })
      notice.value = '实际包装已记录，来源余量已扣减；尚未登记入库或生成单品码。'
    } else {
      await reconcileHarvest(selectedId.value); notice.value = '分级和损耗总量已核对，可按等级登记实际包装。'
    }
    mode.value = null; await refresh()
  } catch (cause) { error.value = operationError(cause); uncertain.value = uncertainOperation(cause) }
  finally { busy.value = false }
}
</script>

<template>
  <div class="management stack">
    <GlassPanel padded="large" class="stack">
      <div class="section-head"><div><p class="eyebrow">从这一轮种植，接住每一次收获</p><h2 class="display intro-title">先称清采收，再核对分级与包装。</h2></div><button class="action-button" :disabled="lots.loading.value || !cycle" @click="refresh">刷新采收</button></div>
      <p class="muted">采收、分级损耗和包装分别留档，重量统一核对到公斤。包装记录保留来源，后续再衔接入库、销售与赋码。</p>
      <ResourceState :loading="cycles.loading.value || plots.loading.value" :error="cycles.error.value || plots.error.value" :empty="!cycles.data.value?.length" empty-text="请先登记地块并开启种植。" @retry="cycles.load(); plots.load()">
        <label class="form-field">来源种植周期<select v-model="cycleId" class="field-input"><option value="">选择本轮或历史种植</option><option v-for="item in cycles.data.value" :key="item.id" :value="String(item.id)">{{ plotMap.get(item.plotId)?.plotName }} · {{ item.productName }} / {{ item.variety }} · {{ item.cycleCode }}{{ item.status === 'ENDED' ? '（已结束）' : '' }}</option></select></label>
      </ResourceState>
      <p v-if="cycle?.status === 'ENDED'" class="notice">本轮种植已结束，不能新增采收；已完成分级的历史采收仍可继续记录实际包装。</p>
    </GlassPanel>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <GlassPanel v-if="cycle" padded="large" class="stack">
      <div class="section-head"><h2 class="display">分次采收批次</h2><button v-if="auth.canWrite.value && cycle.status === 'ACTIVE'" class="action-button primary" @click="open('harvest')">登记实际采收</button></div>
      <p class="muted small">最近最多 200 批，未完成分级核对的优先显示。每批重量为扣除容器后的实际净重，不代表质量合格。</p>
      <ResourceState :loading="lots.loading.value" :error="lots.error.value" :empty="!lots.data.value?.length" empty-text="本轮尚无实际采收记录。" @retry="lots.load">
        <div class="table-scroll"><table class="data-table"><thead><tr><th>采收批次 / 时间</th><th>采收净重</th><th>待分级核对</th><th>损耗 / 已包装</th><th>状态 / 操作</th></tr></thead><tbody><tr v-for="lot in lots.data.value" :key="lot.id" :class="{ 'selected-row': selectedId === lot.id }"><td>{{ lot.lotCode }}<p class="muted">{{ formatDateTime(lot.harvestedAt) }}</p></td><td>{{ kg(lot.weightKg) }} kg<p class="muted">原录入 {{ lot.quantity }} {{ unitLabel(lot.unit) }}</p></td><td>{{ kg(lot.weightKg - lot.allocatedKg) }} kg</td><td>损耗 {{ kg(lot.lossKg) }} kg<p>已包装 {{ kg(lot.packedKg) }} kg</p></td><td>{{ lot.status === 'OPEN' ? '待分级核对' : '分级已核对' }}<p><button class="text-button" :aria-pressed="selectedId === lot.id" @click="selectedId = lot.id">查看来源与办理</button></p></td></tr></tbody></table></div>
      </ResourceState>
    </GlassPanel>
    <GlassPanel v-if="cycle && selectedId" padded="large" class="stack">
      <ResourceState :loading="detail.loading.value" :error="detail.error.value" @retry="detail.load">
        <template v-if="current">
          <div class="section-head"><div><p class="eyebrow">采收来源档案</p><h2 class="display source-title">{{ current.lotCode }}</h2></div><div v-if="auth.canWrite.value" class="actions"><template v-if="current.status === 'OPEN'"><button class="action-button" @click="open('grade')">登记分级或损耗</button><button class="action-button primary" @click="open('reconcile')">完成分级核对</button></template><button v-else class="action-button primary" :disabled="!gradeOptions.length" @click="open('packing')">登记实际包装</button></div></div>
          <dl class="detail-grid"><div><dt>来源地块 / 品种</dt><dd>{{ plotMap.get(cycle.plotId)?.plotName }} · {{ cycle.productName }} / {{ cycle.variety }}</dd></div><div><dt>实际采收 / 采收人员</dt><dd>{{ formatDateTime(current.harvestedAt) }} · {{ current.performerName }}</dd></div><div><dt>录入人 / 录入时间</dt><dd>{{ current.recorderName }} · {{ formatDateTime(current.recordedAt) }}</dd></div><div><dt>采收说明</dt><dd class="pre-wrap">{{ current.notes || '未填写' }}</dd></div></dl>
          <div class="balance-grid"><div><span>实际采收净重</span><strong>{{ kg(current.weightKg) }} kg</strong></div><div><span>待分级核对</span><strong>{{ kg(current.weightKg - current.allocatedKg) }} kg</strong></div><div><span>可用等级未包装</span><strong>{{ kg(current.allocatedKg - current.lossKg - current.packedKg) }} kg</strong></div><div><span>损耗 / 已包装</span><strong>{{ kg(current.lossKg) }} / {{ kg(current.packedKg) }} kg</strong></div></div>
          <h3 class="display">分级与损耗 · 最多 200 条</h3>
          <p v-if="!detail.data.value?.grades.length" class="muted">尚无分级或损耗记录。请记录实际称重，不要由剩余数量推测等级。</p>
          <div v-else class="table-scroll"><table class="data-table"><thead><tr><th>类型 / 等级 / 重量</th><th>待包装 / 位置</th><th>实际时间 / 人员</th><th>原因 / 录入</th></tr></thead><tbody><tr v-for="grade in detail.data.value?.grades" :key="grade.id"><td>{{ grade.kind === 'LOSS' ? '损耗' : '可用等级' }} · {{ grade.gradeName }}<p>{{ kg(grade.weightKg) }} kg</p></td><td>{{ grade.kind === 'LOSS' ? '不可用于包装' : `${kg(grade.weightKg - grade.packedKg)} kg` }}<p>{{ grade.location || '—' }}</p></td><td>{{ formatDateTime(grade.handledAt) }}<p>{{ grade.performerName }}</p></td><td class="pre-wrap">{{ grade.reason || '未填写' }}<p class="muted">{{ grade.recorderName }} · {{ formatDateTime(grade.recordedAt) }}</p></td></tr></tbody></table></div>
          <h3 class="display">实际包装 · 最近 200 批</h3><p class="muted small">一批包装仅来自一个采收等级；箱、袋等数量按实际单件净重核算。包装保存不等于入库、销售、赋码或已上链。</p>
          <p v-if="!detail.data.value?.packings.length" class="muted">尚无实际包装记录。</p>
          <div v-else class="table-scroll"><table class="data-table"><thead><tr><th>包装批次 / 来源等级</th><th>件数 × 单件净重</th><th>实际时间 / 人员</th><th>存放位置 / 说明</th></tr></thead><tbody><tr v-for="pack in detail.data.value?.packings" :key="pack.id"><td>{{ pack.packingCode }}<p><RouterLink :to="{ path: '/admin/inventory', query: { cycle: cycle.id, packing: pack.id } }" class="text-button">查看库存与办理 →</RouterLink></p><p>{{ pack.gradeName }} · 来源 #{{ pack.gradeId }}</p></td><td>{{ pack.packageCount }} {{ pack.packageUnit }} × {{ kg(pack.netWeightKg) }} kg<p>合计 {{ kg(pack.totalWeightKg) }} kg</p></td><td>{{ formatDateTime(pack.packedAt) }} · {{ pack.performerName }}<p class="muted">录入 {{ pack.recorderName }} · {{ formatDateTime(pack.recordedAt) }}</p></td><td class="pre-wrap">{{ pack.location }}<p>{{ pack.notes || '未填写说明' }}</p></td></tr></tbody></table></div>
        </template>
      </ResourceState>
    </GlassPanel>
    <AppDialog :open="mode !== null" :title="title" :busy="busy" @close="close"><form class="management" @submit.prevent="save"><fieldset :disabled="busy || uncertain">
      <template v-if="mode === 'reconcile'"><p>采收净重 {{ kg(current?.weightKg ?? 0) }} kg；分级与损耗合计 {{ kg(current?.allocatedKg ?? 0) }} kg。</p><p class="notice wait">确认实际称重及损耗已全部登记。只有差额为零才能完成核对；确认后不能追加分级或修改原记录。</p></template>
      <template v-else>
        <label v-if="mode !== 'grade'" class="form-field">{{ mode === 'harvest' ? '采收批次编码' : '包装批次编码' }}<input v-model.trim="draft.code" class="field-input" required maxlength="64" pattern="[A-Za-z0-9_-]+"></label>
        <template v-if="mode === 'grade'"><label class="form-field">分配类型<select v-model="draft.kind" class="field-input"><option value="SALEABLE">可用等级</option><option value="LOSS">实际损耗</option></select></label><label class="form-field">等级或损耗名称<input v-model.trim="draft.gradeName" class="field-input" required maxlength="128" placeholder="按实际分级或损耗情况填写"></label></template>
        <template v-if="mode === 'packing'"><label class="form-field">来源等级<select v-model="draft.gradeId" class="field-input" required><option value="">选择本批的可用等级</option><option v-for="grade in gradeOptions" :key="grade.id" :value="String(grade.id)">{{ grade.gradeName }} · #{{ grade.id }} · 余量 {{ kg(grade.weightKg - grade.packedKg) }} kg</option></select></label><p v-if="packingSource" class="notice">仅从等级 #{{ packingSource.id }} 扣减，来源存放位置：{{ packingSource.location }}</p><div class="form-grid"><label class="form-field">实际包装件数<input v-model="draft.count" class="field-input" type="number" min="1" max="1000000" step="1" required></label><label class="form-field">包装单位<input v-model.trim="draft.packageUnit" class="field-input" maxlength="32" required placeholder="例如 箱、袋"></label></div><label class="form-field">实际单件净重（公斤）<input v-model="draft.netWeight" class="field-input" type="number" min="0.001" max="999999999.999" step="0.001" required></label><p class="notice">合计 {{ kg(Number(draft.count) * Number(draft.netWeight)) }} kg，系统会重新核对来源余量；未包装余量继续保留。</p></template>
        <template v-else><div class="form-grid"><label class="form-field">{{ mode === 'harvest' ? '实际采收净重' : '实际分配重量（公斤）' }}<input v-model="draft.quantity" class="field-input" type="number" min="0.001" step="0.001" required></label><label v-if="mode === 'harvest'" class="form-field">称重单位<select v-model="draft.unit" class="field-input"><option value="kg">公斤（kg）</option><option value="g">克（g）</option><option value="jin">市斤（500 克）</option></select></label></div><p v-if="mode === 'harvest'" class="muted small">统一换算到公斤，精度 1 克；不足 1 克的结果会被拒绝，不自动舍入。采收后仍需登记分级与损耗。</p></template>
        <label class="form-field">实际办理时间<input v-model="draft.at" class="field-input" type="datetime-local" step="1" required></label><label class="form-field">实际办理人员<input v-model.trim="draft.performer" class="field-input" maxlength="128" required placeholder="填写实际现场人员，录入人另行留档"></label>
        <label v-if="mode !== 'harvest' && !(mode === 'grade' && draft.kind === 'LOSS')" class="form-field">实际存放位置<input v-model.trim="draft.location" class="field-input" maxlength="256" required></label>
        <label v-if="mode === 'grade'" class="form-field">{{ draft.kind === 'LOSS' ? '损耗原因（必填）' : '分级说明（选填）' }}<textarea v-model.trim="draft.reason" class="field-input" rows="3" maxlength="1000" :required="draft.kind === 'LOSS'"></textarea></label><label v-else class="form-field">实际情况说明（选填）<textarea v-model.trim="draft.notes" class="field-input" rows="3" maxlength="2000"></textarea></label>
      </template>
    </fieldset><p v-if="error" class="notice bad" role="alert">{{ error }}</p><div class="actions form-actions"><button class="action-button primary" type="submit" :disabled="busy || uncertain">{{ busy ? '正在保存…' : mode === 'reconcile' ? '确认数量核对完成' : '确认保存' }}</button><button class="action-button" type="button" :disabled="busy" @click="close">关闭</button></div></form></AppDialog>
  </div>
</template>

<style scoped>
h2 { font-size: var(--fs-h2); }.intro-title { margin-top: 8px; font-size: clamp(1.2rem,2vw,1.65rem); line-height: 1.5; }.source-title { margin-top: 8px; overflow-wrap: anywhere; }.small { font-size: var(--fs-sm); }.pre-wrap { white-space: pre-wrap; }.form-actions { margin-top: 20px; }.selected-row { background: var(--field-tint); }.balance-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 12px; }.balance-grid > div { padding: 16px; background: var(--glass-fill-quiet); border: 1px solid var(--glass-stroke); border-radius: var(--radius-md); overflow-wrap: anywhere; }.balance-grid span { display: block; color: var(--slate); font-size: var(--fs-xs); }.balance-grid strong { display: block; margin-top: 8px; font-size: 1.125rem; font-variant-numeric: tabular-nums; }
@media(max-width:1100px){.balance-grid{grid-template-columns:repeat(2,minmax(0,1fr));}}@media(max-width:380px){.balance-grid{grid-template-columns:minmax(0,1fr);}}
</style>
