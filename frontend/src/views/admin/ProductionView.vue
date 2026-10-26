<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import StatusChip from '@/components/StatusChip.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { useOrganizationDirectory } from '@/composables/useOrganizationDirectory'
import OrganizationHelp from '@/components/OrganizationHelp.vue'
import { useAuth } from '@/composables/useAuth'
import { ApiError } from '@/api/http'
import { listProducts } from '@/api/catalog'
import { endPlanting, listCycles, listPlots, registerPlot, startPlanting } from '@/api/production'
import type { FarmPlot, PlantingCycle } from '@/api/production'
import { formatDate } from '@/types/api'

const plotsResource = useAsyncResource(listPlots)
const auth = useAuth()
const cyclesResource = useAsyncResource(listCycles)
const organizations = useOrganizationDirectory()
const products = useAsyncResource(listProducts)
const resources = [plotsResource, cyclesResource, organizations, products]
const plots = computed(() => plotsResource.data.value ?? [])
const cycles = computed(() => cyclesResource.data.value ?? [])
const loading = computed(() => resources.some((resource) => resource.loading.value))
const activeByPlot = computed(() => new Map(cycles.value.filter((cycle) => cycle.status === 'ACTIVE').map((cycle) => [cycle.plotId, cycle])))
const search = ref('')
const status = ref('')
const filtered = computed(() => plots.value.filter((plot) => {
  const cycle = activeByPlot.value.get(plot.id)
  const query = search.value.trim().toLocaleLowerCase()
  return (!status.value || (status.value === 'ACTIVE' ? !!cycle : !cycle))
    && (!query || [plot.plotName, plot.plotCode, plot.location, plot.managerName, cycle?.productName, cycle?.variety]
      .some((value) => value?.toLocaleLowerCase().includes(query)))
}))
const page = ref(1)
const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / 8)))
const rows = computed(() => filtered.value.slice((page.value - 1) * 8, page.value * 8))
watch([search, status], () => { page.value = 1 })
watch(pageCount, (value) => { page.value = Math.min(value, page.value) })
const selected = ref<FarmPlot | null>(null)
const selectedCycle = ref<PlantingCycle | null>(null)
const mode = ref<'plot' | 'start' | 'end' | 'detail' | null>(null)
const busy = ref(false)
const uncertain = ref(false)
const saveError = ref('')
const notice = ref('')
const confirmed = ref(false)
function today() {
  const value = new Date()
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`
}
const plotDraft = reactive({ organizationId: '', plotCode: '', plotName: '', location: '', areaMu: '', managerName: '', notes: '' })
const cycleDraft = reactive({ productId: '', cycleCode: '', variety: '', plantedOn: today(), expectedHarvestOn: '', managerName: '', environmentNotes: '' })
const endDraft = reactive({ endedOn: today(), reason: '' })
const availableProducts = computed(() => (products.data.value ?? []).filter((product) => product.organizationId === selected.value?.organizationId && product.status === 'ACTIVE' && organizations.data.value?.some(org => org.id === product.organizationId)))
const history = computed(() => cycles.value.filter((cycle) => cycle.plotId === selected.value?.id))
const title = computed(() => ({ plot: '登记地块', start: '开启本轮种植', end: '结束本轮种植', detail: selected.value?.plotName ?? '地块档案' })[mode.value ?? 'detail'])
watch(() => cycleDraft.productId, (id) => {
  const product = availableProducts.value.find((value) => value.id === Number(id))
  cycleDraft.variety = product?.variety ?? ''
})
async function refresh() { await Promise.all(resources.map((resource) => resource.load())) }
onMounted(refresh)

function open(kind: NonNullable<typeof mode.value>, plot?: FarmPlot) {
  selected.value = plot ?? null
  selectedCycle.value = plot ? activeByPlot.value.get(plot.id) ?? null : null
  saveError.value = ''
  uncertain.value = false
  confirmed.value = false
  Object.assign(plotDraft, { organizationId: '', plotCode: '', plotName: '', location: '', areaMu: '', managerName: '', notes: '' })
  Object.assign(cycleDraft, { productId: '', cycleCode: '', variety: '', plantedOn: today(), expectedHarvestOn: '', managerName: plot?.managerName ?? '', environmentNotes: '' })
  Object.assign(endDraft, { endedOn: today(), reason: '' })
  mode.value = kind
}
function close() { if (!busy.value) mode.value = null }
async function save() {
  if (busy.value || uncertain.value || !mode.value) return
  saveError.value = ''
  if (mode.value === 'start' && cycleDraft.expectedHarvestOn && cycleDraft.expectedHarvestOn < cycleDraft.plantedOn) {
    saveError.value = '预计采收日期不能早于种植日期。'
    return
  }
  if (mode.value === 'end' && !confirmed.value) { saveError.value = '请先核对结束条件。'; return }
  const kind = mode.value
  busy.value = true
  try {
    if (kind === 'plot') {
      await registerPlot({ ...plotDraft, organizationId: Number(plotDraft.organizationId), areaMu: Number(plotDraft.areaMu) })
      notice.value = '地块已登记，可以开启本轮种植。'
    } else if (kind === 'start' && selected.value) {
      await startPlanting(selected.value.id, { ...cycleDraft, productId: Number(cycleDraft.productId), expectedHarvestOn: cycleDraft.expectedHarvestOn || undefined })
      notice.value = '本轮种植已开启，产品与品种已保存到本轮档案。'
    } else if (kind === 'end' && selectedCycle.value) {
      await endPlanting(selectedCycle.value.id, { ...endDraft })
      notice.value = '本轮种植已结束，历史档案保留，地块可以开启下一轮。'
    }
    mode.value = null
    await refresh()
  } catch (error) {
    if (error instanceof ApiError && error.code === '400') saveError.value = error.message
    else if (error instanceof ApiError && error.code === '409') saveError.value = '编码已存在或地块状态已变化。请关闭窗口，刷新核对，避免重复登记。'
    else {
      uncertain.value = true
      saveError.value = '暂时无法确认保存结果。请关闭窗口并刷新核对；不要更换编码重复登记。'
    }
  } finally { busy.value = false }
}
</script>

<template>
  <div class="management production stack">
    <GlassPanel class="season-intro" padded="large">
      <div>
        <p class="eyebrow">从一块地，记录每一轮生长</p>
        <h2 class="display">地块留存，种植有始有终。</h2>
        <p class="muted">登记地块，记录本轮产品与品种。采收结束后保留档案，再开始下一轮。</p>
      </div>
      <button v-if="auth.canWrite.value" class="action-button primary" type="button" :disabled="loading || !!organizations.error.value || !organizations.data.value?.length" @click="open('plot')">＋ 登记地块</button>
    </GlassPanel>

    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <p v-if="organizations.error.value" class="notice bad" role="alert">机构读取失败，暂不能登记地块。<button class="text-button" @click="organizations.load">重试</button></p>
    <p v-else-if="organizations.data.value && !organizations.data.value.length" class="notice wait">尚无启用机构，请先创建或启用经营主体。</p>
    <OrganizationHelp :loading="organizations.loading.value" @refresh="organizations.load" />
    <p v-if="products.error.value" class="notice bad" role="alert">产品读取失败，暂不能开启种植。<button class="text-button" @click="products.load">重试</button></p>

    <GlassPanel padded="large">
      <div class="section-head">
        <h2 class="display">地块档案</h2>
        <button class="action-button" :disabled="loading" @click="refresh">刷新记录</button>
      </div>
      <div class="filters">
        <label>查找地块<input v-model="search" class="field-input" type="search" placeholder="地块、位置、产品或负责人"></label>
        <label>种植状态<select v-model="status" class="field-input"><option value="">全部状态</option><option value="ACTIVE">正在种植</option><option value="IDLE">待种植</option></select></label>
      </div>
      <ResourceState :loading="plotsResource.loading.value || cyclesResource.loading.value" :error="plotsResource.error.value || cyclesResource.error.value" :empty="!filtered.length" :empty-text="plots.length ? '没有符合筛选条件的地块。' : '还没有地块，先登记第一块生产用地。'" @retry="refresh">
        <div class="plot-grid">
          <article v-for="plot in rows" :key="plot.id" class="plot-card">
            <div class="section-head">
              <p class="eyebrow mono">{{ plot.plotCode }}</p>
              <StatusChip :tone="activeByPlot.has(plot.id) ? 'ok' : 'off'" :label="activeByPlot.has(plot.id) ? '正在种植' : '待种植'" />
            </div>
            <h3 class="display">{{ plot.plotName }}</h3>
            <p class="muted plot-location">{{ plot.location }}</p>
            <div class="growing-summary">
              <template v-if="activeByPlot.has(plot.id)">
                <strong>{{ activeByPlot.get(plot.id)?.productName }} · {{ activeByPlot.get(plot.id)?.variety }}</strong>
                <span class="muted">{{ formatDate(activeByPlot.get(plot.id)?.plantedOn) }} 开始种植</span>
              </template>
              <template v-else><strong>等待下一轮生长</strong><span class="muted">历史种植档案持续保留</span></template>
            </div>
            <p class="muted">{{ organizations.names.value.get(plot.organizationId) ?? `机构 #${plot.organizationId}` }}</p>
            <p class="plot-meta">{{ plot.areaMu }} 亩 <span>·</span> 地块负责人 {{ plot.managerName }}</p>
            <div class="actions">
              <button class="action-button" @click="open('detail', plot)">查看档案</button>
              <button v-if="auth.canWrite.value && !activeByPlot.has(plot.id)" class="action-button primary" :disabled="!!products.error.value || loading" @click="open('start', plot)">开启种植</button>
              <button v-if="auth.canWrite.value && activeByPlot.has(plot.id)" class="text-button" @click="open('end', plot)">结束本轮</button>
            </div>
          </article>
        </div>
        <div class="section-head pagination">
          <span class="muted">共 {{ filtered.length }} 块地 · 第 {{ page }} / {{ pageCount }} 页</span>
          <div class="actions"><button class="action-button" :disabled="page <= 1" @click="page--">上一页</button><button class="action-button" :disabled="page >= pageCount" @click="page++">下一页</button></div>
        </div>
      </ResourceState>
    </GlassPanel>

    <AppDialog :open="mode !== null" :title="title" :busy="busy" @close="close">
      <div v-if="mode === 'detail' && selected" class="management">
        <dl class="detail-grid">
          <div><dt>地块编码</dt><dd>{{ selected.plotCode }}</dd></div>
          <div><dt>面积</dt><dd>{{ selected.areaMu }} 亩</dd></div>
          <div><dt>位置</dt><dd>{{ selected.location }}</dd></div>
          <div><dt>地块负责人</dt><dd>{{ selected.managerName }}</dd></div>
          <div class="full"><dt>备注</dt><dd class="pre-wrap">{{ selected.notes || '未填写' }}</dd></div>
        </dl>
        <h3 class="display">种植历史 · {{ history.length }} 轮</h3>
        <p v-if="!history.length" class="muted history-empty">尚未开启种植周期。</p>
        <ol class="cycle-history">
          <li v-for="cycle in history" :key="cycle.id">
            <div class="section-head"><strong>{{ cycle.productName }} · {{ cycle.variety }}</strong><StatusChip :tone="cycle.status === 'ACTIVE' ? 'ok' : 'off'" :label="cycle.status === 'ACTIVE' ? '进行中' : '已结束'" /></div>
            <p class="mono muted">{{ cycle.cycleCode }}</p>
            <p>种植 {{ formatDate(cycle.plantedOn) }} · {{ cycle.endedOn ? `结束 ${formatDate(cycle.endedOn)}` : '尚未结束' }}</p>
            <p v-if="cycle.expectedHarvestOn">预计采收 {{ formatDate(cycle.expectedHarvestOn) }}</p>
            <p>本轮负责人 {{ cycle.managerName }}</p>
            <p v-if="cycle.environmentNotes" class="pre-wrap muted">环境要求备注：{{ cycle.environmentNotes }}</p>
            <p v-if="cycle.endReason" class="pre-wrap muted">结束说明：{{ cycle.endReason }}</p>
            <RouterLink :to="{ path: '/admin/monitoring', query: { cycle: cycle.id } }" class="text-button" @click="close">查看本轮监测与绑定 →</RouterLink>
            <RouterLink :to="{ path: '/admin/farming', query: { cycle: cycle.id } }" class="text-button" @click="close">查看本轮环境与农事 →</RouterLink>
            <RouterLink :to="{ path: '/admin/harvest', query: { cycle: cycle.id } }" class="text-button" @click="close">查看本轮采收与包装 →</RouterLink>
          </li>
        </ol>
      </div>

      <form v-else class="management" @submit.prevent="save">
        <fieldset :disabled="busy || uncertain">
          <template v-if="mode === 'plot'">
            <label class="form-field">所属机构<select v-model="plotDraft.organizationId" class="field-input" required><option value="" disabled>请选择经营主体</option><option v-for="org in organizations.data.value" :key="org.id" :value="String(org.id)">{{ org.name }} · {{ org.code || `#${org.id}` }}</option></select></label>
            <OrganizationHelp :loading="organizations.loading.value" @refresh="organizations.load" /><div class="form-grid">
              <label class="form-field">地块编码<input v-model.trim="plotDraft.plotCode" class="field-input" required maxlength="64" pattern="[A-Za-z0-9_-]{1,64}" placeholder="例如 EAST-01"><small class="muted">使用字母、数字、短横线或下划线</small></label>
              <label class="form-field">地块名称<input v-model.trim="plotDraft.plotName" class="field-input" required maxlength="128" placeholder="例如 东边一号棚"></label>
              <label class="form-field full">地块位置<input v-model.trim="plotDraft.location" class="field-input" required maxlength="255" placeholder="填写可定位到这块地的位置"></label>
              <label class="form-field">面积（亩）<input v-model="plotDraft.areaMu" class="field-input" required type="number" min="0.01" max="9999999999.99" step="0.01"></label>
              <label class="form-field">地块负责人<input v-model.trim="plotDraft.managerName" class="field-input" required maxlength="64"></label>
              <label class="form-field full">备注（选填）<textarea v-model.trim="plotDraft.notes" class="field-input" rows="3" maxlength="2000"></textarea></label>
            </div>
          </template>

          <template v-if="mode === 'start' && selected">
            <p class="notice">{{ selected.plotName }} · {{ selected.plotCode }}<br>本次登记实际开始的种植，计划中的种植请在开始后登记。</p>
            <p v-if="!availableProducts.length" class="notice wait">所属机构已停用或尚无有效产品，请核对机构与产品资料。</p>
            <div class="form-grid cycle-form">
              <label class="form-field full">种植周期编码<input v-model.trim="cycleDraft.cycleCode" class="field-input" required maxlength="64" pattern="[A-Za-z0-9_-]{1,64}" placeholder="例如 EAST-01-2026-A"></label>
              <label class="form-field">种植产品<select v-model="cycleDraft.productId" class="field-input" required><option value="" disabled>选择同机构产品</option><option v-for="product in availableProducts" :key="product.id" :value="String(product.id)">{{ product.productName }} · {{ product.productCode }}</option></select></label>
              <label class="form-field">本轮品种<input v-model.trim="cycleDraft.variety" class="field-input" required maxlength="128"></label>
              <label class="form-field">实际种植日期<input v-model="cycleDraft.plantedOn" class="field-input" required type="date" :max="today()"></label>
              <label class="form-field">预计采收日期（选填）<input v-model="cycleDraft.expectedHarvestOn" class="field-input" type="date" :min="cycleDraft.plantedOn"></label>
              <label class="form-field full">本轮负责人<input v-model.trim="cycleDraft.managerName" class="field-input" required maxlength="64"></label>
              <label class="form-field full">环境要求备注（选填）<textarea v-model.trim="cycleDraft.environmentNotes" class="field-input" rows="3" maxlength="2000" placeholder="记录本轮环境要求及依据，具体监测规则另行配置"></textarea><small class="muted">本栏用于留档，不会自动改变设备告警阈值。</small></label>
            </div>
          </template>

          <template v-if="mode === 'end' && selectedCycle">
            <p class="notice wait">结束 {{ selected?.plotName }} 的 {{ selectedCycle.productName }} · {{ selectedCycle.variety }}。地块与历史记录保留，结束后可开启下一轮。</p>
            <label class="form-field cycle-form">实际结束日期<input v-model="endDraft.endedOn" class="field-input" required type="date" :min="selectedCycle.plantedOn" :max="today()"></label>
            <label class="form-field">结束说明<textarea v-model.trim="endDraft.reason" class="field-input" required rows="3" maxlength="1000" placeholder="说明本轮结束情况与后续安排"></textarea></label>
            <label class="end-check"><input v-model="confirmed" type="checkbox" required>我已人工核对本轮采收、未完成作业及设备安排，确认可以结束。</label>
          </template>
        </fieldset>
        <p v-if="saveError" class="notice bad" role="alert">{{ saveError }}</p>
        <div class="actions form-actions">
          <button class="action-button primary" type="submit" :disabled="busy || uncertain || (mode === 'start' && !availableProducts.length)">{{ busy ? '正在保存…' : mode === 'plot' ? '确认登记地块' : mode === 'start' ? '确认开启种植' : '确认结束本轮' }}</button>
          <button class="action-button" type="button" :disabled="busy" @click="close">{{ uncertain ? '关闭并核对记录' : '取消' }}</button>
        </div>
      </form>
    </AppDialog>
  </div>
</template>

<style scoped>
.season-intro { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 24px; }
.season-intro h2 { margin: 10px 0 12px; font-size: clamp(1.3rem, 2vw, 1.8rem); line-height: 1.4; }
.season-intro p:last-child { max-width: 560px; font-size: var(--fs-sm); }
.plot-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.plot-card { min-width: 0; padding: 22px; border: 1px solid var(--glass-stroke); border-radius: var(--radius-lg); background: var(--glass-fill-quiet); box-shadow: var(--shadow-inset-top); overflow-wrap: anywhere; }
.plot-card h3 { margin: 16px 0 6px; font-size: 1.2rem; }
.plot-location, .plot-meta { font-size: var(--fs-xs); }
.growing-summary { display: grid; gap: 4px; padding: 18px 0; margin-top: 12px; border-top: 1px solid var(--glass-hairline); }
.growing-summary strong { color: var(--field); font-weight: 600; }
.growing-summary span { font-size: var(--fs-xs); }
.plot-meta { margin-bottom: 20px; color: var(--ink-70); }
.plot-meta span { margin: 0 6px; }
.pagination { margin-top: 24px; font-size: var(--fs-xs); }
.cycle-form { margin-top: 20px; }
.form-actions { margin-top: 20px; }
.end-check { display: flex; align-items: flex-start; gap: 10px; font-size: var(--fs-sm); }
.end-check input { margin-top: 5px; accent-color: var(--field); }
.cycle-history li { padding: 20px 0; border-bottom: 1px solid var(--glass-hairline); font-size: var(--fs-sm); }
.cycle-history li p { margin-top: 8px; }
.pre-wrap { white-space: pre-wrap; overflow-wrap: anywhere; }
.history-empty { margin-top: 16px; }
@media (max-width: 680px) { .plot-grid { grid-template-columns: minmax(0, 1fr); } .plot-card { padding: 18px; } }
</style>
