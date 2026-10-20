<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import GlassPanel from '@/components/GlassPanel.vue'
import StatusChip from '@/components/StatusChip.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { ApiError } from '@/api/http'
import { closeBatch, createBatch, createProduct, getBatch, getProduct, listBatches, listProducts } from '@/api/catalog'
import { batchPayload, batchSaveFeedback, batchStatusLabel, productPayload, validateBatch, validateProduct } from '@/utils/catalog'
import type { BatchDraft, FieldErrors, ProductDraft } from '@/utils/catalog'
import type { ProductBatchEntity, ProductEntity } from '@/types/catalog'
import { formatDate, formatDateTime, type Tone } from '@/types/api'

const productResource = useAsyncResource(listProducts)
const batchResource = useAsyncResource(listBatches)
const products = computed(() => productResource.data.value ?? [])
const batches = computed(() => batchResource.data.value ?? [])
const tab = ref<'products' | 'batches'>('products')
const search = ref('')
const productFilter = ref('')
const statusFilter = ref('')
const chainFilter = ref('')
const page = ref(1)
const pageSize = 10
const activeResource = computed(() => tab.value === 'products' ? productResource : batchResource)
const refreshing = computed(() => productResource.loading.value || batchResource.loading.value)
const productMap = computed(() => new Map(products.value.map((product) => [product.id, product])))
const filteredProducts = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return products.value.filter((product) => !query || [product.productName, product.productCode, product.category, product.originPlace]
    .some((value) => value?.toLocaleLowerCase().includes(query)))
})
const filteredBatches = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return batches.value.filter((batch) => (!productFilter.value || batch.productId === Number(productFilter.value))
    && (!statusFilter.value || batch.status === statusFilter.value)
    && (!chainFilter.value || batch.chainStatus === chainFilter.value)
    && (!query || [batch.batchCode, batch.productionArea, productMap.value.get(batch.productId)?.productName]
      .some((value) => value?.toLocaleLowerCase().includes(query))))
})
const resultCount = computed(() => tab.value === 'products' ? filteredProducts.value.length : filteredBatches.value.length)
const pageCount = computed(() => Math.max(1, Math.ceil(resultCount.value / pageSize)))
const productRows = computed(() => filteredProducts.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const batchRows = computed(() => filteredBatches.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch([tab, search, productFilter, statusFilter, chainFilter], () => { page.value = 1 })
watch(pageCount, (count) => { page.value = Math.min(page.value, count) })

type Mode = 'product' | 'batch' | 'product-detail' | 'batch-detail' | 'close' | null
const mode = ref<Mode>(null)
const selectedId = ref(0)
const productDetail = useAsyncResource(() => getProduct(selectedId.value))
const batchDetail = useAsyncResource(() => getBatch(selectedId.value))
const selectedBatch = ref<ProductBatchEntity | null>(null)
const closeStatus = ref<2 | 3>(2)
const closeReason = ref('')
const saving = ref(false)
const checking = ref(false)
const uncertain = ref(false)
const saveError = ref('')
const errors = ref<FieldErrors>({})
const notice = ref<{ tone: Tone; message: string } | null>(null)
let checkPending: (() => Promise<ProductEntity | ProductBatchEntity | undefined>) | null = null
const emptyProduct = (): ProductDraft => ({ organizationId: '', productCode: '', productName: '', category: '', variety: '', originPlace: '', description: '' })
const emptyBatch = (): BatchDraft => ({ productId: '', organizationId: '', batchCode: '', productionArea: '', plantDate: '', harvestDate: '', metadata: '' })
const productDraft = reactive(emptyProduct())
const batchDraft = reactive(emptyBatch())
const productFields = [
  { key: 'organizationId', label: '机构编号', type: 'number', required: true, max: undefined },
  { key: 'productCode', label: '产品编码', type: 'text', required: true, max: 64 },
  { key: 'productName', label: '产品名称', type: 'text', required: true, max: 128 },
  { key: 'category', label: '品类', type: 'text', required: true, max: 64 },
  { key: 'variety', label: '品种', type: 'text', required: false, max: 128 },
  { key: 'originPlace', label: '产地', type: 'text', required: false, max: 255 },
] as const
const batchFields = [
  { key: 'batchCode', label: '批次编码', type: 'text', required: true, max: 64 },
  { key: 'productionArea', label: '生产地块', type: 'text', required: false, max: 255 },
  { key: 'plantDate', label: '种植日期', type: 'date', required: false, max: undefined },
  { key: 'harvestDate', label: '采收日期', type: 'date', required: false, max: undefined },
] as const
const dialogTitle = computed(() => mode.value === 'close' ? (closeStatus.value === 3 ? '召回批次' : '关闭批次') : ({
  product: '新建产品', batch: '新建批次', 'product-detail': '产品详情', 'batch-detail': '批次详情',
} as Record<string, string>)[mode.value ?? ''] ?? '')

watch(() => batchDraft.productId, (id) => {
  const product = productMap.value.get(Number(id))
  batchDraft.organizationId = product ? String(product.organizationId) : ''
})
async function refresh() { await Promise.all([productResource.load(), batchResource.load()]) }
onMounted(refresh)
function resetDialog() {
  errors.value = {}
  saveError.value = ''
  uncertain.value = false
  checkPending = null
}
function openProduct() { resetDialog(); Object.assign(productDraft, emptyProduct()); mode.value = 'product' }
function openBatch(productId?: number) {
  resetDialog()
  Object.assign(batchDraft, emptyBatch())
  if (productId) {
    batchDraft.productId = String(productId)
    batchDraft.organizationId = String(productMap.value.get(productId)?.organizationId ?? '')
  }
  mode.value = 'batch'
}
function viewBatches(id: number) { tab.value = 'batches'; search.value = ''; productFilter.value = String(id); statusFilter.value = ''; chainFilter.value = ''; mode.value = null }
async function openDetail(kind: 'product-detail' | 'batch-detail', id: number) {
  resetDialog(); selectedId.value = id; mode.value = kind
  await (kind === 'product-detail' ? productDetail.load() : batchDetail.load())
}
function openClose(batch: ProductBatchEntity, status: 2 | 3) {
  resetDialog(); selectedBatch.value = batch; closeStatus.value = status; closeReason.value = ''; mode.value = 'close'
}
async function focusError() {
  await nextTick()
  document.querySelector<HTMLElement>('dialog [aria-invalid="true"]')?.focus()
}
function success(result: ProductEntity | ProductBatchEntity) {
  notice.value = 'chainStatus' in result ? batchSaveFeedback(result.chainStatus) : { tone: 'ok', message: '产品已保存。可以继续为该产品创建生产批次。' }
  uncertain.value = false
  mode.value = null
  void refresh()
}
async function mutate(task: () => Promise<ProductEntity | ProductBatchEntity>) {
  if (saving.value || uncertain.value) return
  saving.value = true
  saveError.value = ''
  try { success(await task()) } catch (error) {
    if (error instanceof ApiError && ['400', '409', '401', '403'].includes(error.code)) {
      saveError.value = error.code === '409' ? '编号已存在，请更换编码或查看已有记录。'
        : error.code === '400' ? '提交内容不符合要求，请检查字段；批次状态也可能已变化，请刷新核对。'
        : '当前无权保存，请联系管理员。'
    } else {
      uncertain.value = true
      saveError.value = '保存结果暂未确认。请先核对记录，避免重复提交。'
    }
  } finally { saving.value = false }
}
async function submitProduct() {
  errors.value = validateProduct(productDraft, products.value.map((product) => product.productCode))
  if (Object.keys(errors.value).length) return focusError()
  const payload = productPayload(productDraft)
  checkPending = async () => (await listProducts()).find((product) => product.productCode === payload.productCode)
  await mutate(() => createProduct(payload))
}
async function submitBatch() {
  errors.value = validateBatch(batchDraft, products.value, batches.value.map((batch) => batch.batchCode))
  if (Object.keys(errors.value).length) return focusError()
  const payload = batchPayload(batchDraft)
  checkPending = async () => (await listBatches()).find((batch) => batch.batchCode === payload.batchCode)
  await mutate(() => createBatch(payload))
}
async function submitClose() {
  const batch = selectedBatch.value
  if (!batch) return
  errors.value = closeReason.value.trim() ? {} : { reason: '请填写操作原因。' }
  if (Object.keys(errors.value).length) return focusError()
  const status = closeStatus.value
  const payload = { status, reasonPayload: { reason: closeReason.value.trim() } }
  checkPending = async () => {
    const result = await getBatch(batch.id)
    return result.status === (status === 2 ? 'CLOSED' : 'RECALL') ? result : undefined
  }
  await mutate(() => closeBatch(batch.id, payload))
}
async function reconcile() {
  if (!checkPending || checking.value) return
  checking.value = true
  try {
    const result = await checkPending()
    if (result) success(result)
    else saveError.value = '暂未查到已保存的结果，请稍后再核对。当前不会重复提交。'
  } catch { saveError.value = '暂时无法核对，请检查连接后再次核对。当前不会重复提交。' }
  finally { checking.value = false }
}
function canClose(batch: ProductBatchEntity) { return batch.status === 'ACTIVE' && ['CONFIRMED', 'CHAIN_DISABLED'].includes(batch.chainStatus) }
function closeDialog() { if (!saving.value && !checking.value) mode.value = null }
function clearFilters() { search.value = ''; productFilter.value = ''; statusFilter.value = ''; chainFilter.value = '' }
</script>

<template>
  <div class="catalog stack">
    <div v-if="notice" class="save-notice" :class="`tone-${notice.tone}`" role="status">
      <p>{{ notice.message }}</p>
      <button type="button" class="text-button" aria-label="收起保存提示" @click="notice = null">收起</button>
    </div>
    <GlassPanel class="catalog-intro">
      <div>
        <p class="eyebrow">生产台账</p>
        <h2 class="display">从产品到每一批生产记录</h2>
        <p class="muted">先登记产品，再按产地和生产日期建立批次。批次保存结果与上链状态分别展示。</p>
      </div>
      <div class="row wrap">
        <button class="action-button" type="button" :disabled="refreshing" @click="refresh">刷新台账</button>
        <button class="action-button primary" type="button" @click="openProduct">新建产品</button>
      </div>
    </GlassPanel>

    <GlassPanel :padded="false" class="ledger">
      <div class="ledger-head">
        <div class="segmented" role="group" aria-label="台账类型">
          <button type="button" :aria-pressed="tab === 'products'" @click="tab = 'products'">产品台账 <span>{{ productResource.data.value?.length ?? '—' }}</span></button>
          <button type="button" :aria-pressed="tab === 'batches'" @click="tab = 'batches'">生产批次 <span>{{ batchResource.data.value?.length ?? '—' }}</span></button>
        </div>
        <button v-if="tab === 'batches'" class="action-button primary" type="button" :disabled="!products.length || !!productResource.error.value || productResource.loading.value" @click="openBatch()">新建批次</button>
      </div>
      <form class="catalog-filters" role="search" @submit.prevent>
        <label class="filter-search">搜索台账
          <input v-model="search" type="search" class="field-input" :placeholder="tab === 'products' ? '产品名称、编码、品类或产地' : '批次编码、产品名称或地块'">
        </label>
        <template v-if="tab === 'batches'">
          <label>所属产品<select v-model="productFilter" class="field-input"><option value="">全部产品</option><option v-for="product in products" :key="product.id" :value="String(product.id)">{{ product.productName }}</option></select></label>
          <label>业务状态<select v-model="statusFilter" class="field-input"><option value="">全部状态</option><option value="ACTIVE">生产中</option><option value="CLOSED">已关闭</option><option value="RECALL">已召回</option></select></label>
          <label>上链状态<select v-model="chainFilter" class="field-input"><option value="">全部状态</option><option value="CONFIRMED">上链已确认</option><option value="PENDING">等待上链</option><option value="FAILED">上链失败</option><option value="CHAIN_DISABLED">未上链</option></select></label>
        </template>
        <button type="button" class="text-button" @click="clearFilters">清除筛选</button>
      </form>
      <p v-if="tab === 'batches' && productResource.error.value" class="dependency-warning" role="alert">产品资料暂不可用，部分名称无法展示。请刷新台账后再新建批次。</p>
      <div class="ledger-body">
        <ResourceState :loading="activeResource.loading.value" :error="activeResource.error.value" :empty="!resultCount" :empty-text="search || productFilter || statusFilter || chainFilter ? '没有符合筛选条件的记录，请调整筛选。' : tab === 'products' ? '还没有产品，点击“新建产品”开始登记。' : '还没有批次，请先登记产品，再创建生产批次。'" @retry="activeResource.load">
          <div class="table-scroll" tabindex="0" :aria-label="tab === 'products' ? '产品台账，可横向滚动' : '生产批次，可横向滚动'">
            <table v-if="tab === 'products'" class="data-table">
              <caption class="sr-only">产品台账</caption>
              <thead><tr><th scope="col">产品</th><th scope="col">品类 / 品种</th><th scope="col">产地</th><th scope="col">机构</th><th scope="col">操作</th></tr></thead>
              <tbody><tr v-for="product in productRows" :key="product.id">
                <td><button class="record-link" type="button" @click="openDetail('product-detail', product.id)">{{ product.productName }}</button><p class="mono muted">{{ product.productCode }}</p></td>
                <td>{{ product.category }}<p class="muted tiny">{{ product.variety || '品种未记录' }}</p></td>
                <td>{{ product.originPlace || '—' }}</td><td class="mono">{{ product.organizationId }}</td>
                <td><div class="table-actions"><button class="text-button" type="button" @click="viewBatches(product.id)">查看批次</button><button class="text-button" type="button" @click="openBatch(product.id)">新建批次</button></div></td>
              </tr></tbody>
            </table>
            <table v-else class="data-table batch-table">
              <caption class="sr-only">生产批次</caption>
              <thead><tr><th scope="col">批次 / 产品</th><th scope="col">地块 / 日期</th><th scope="col">业务状态</th><th scope="col">上链状态</th><th scope="col">操作</th></tr></thead>
              <tbody><tr v-for="batch in batchRows" :key="batch.id">
                <td><button class="record-link mono" type="button" @click="openDetail('batch-detail', batch.id)">{{ batch.batchCode }}</button><p class="muted tiny">{{ productMap.get(batch.productId)?.productName || `产品 #${batch.productId}` }}</p></td>
                <td>{{ batch.productionArea || '地块未记录' }}<p class="muted tiny">种植 {{ formatDate(batch.plantDate) }} · 采收 {{ formatDate(batch.harvestDate) }}</p></td>
                <td><StatusChip :tone="batch.status === 'RECALL' ? 'bad' : 'off'" :label="batchStatusLabel(batch.status)" compact /></td>
                <td><StatusChip :status="batch.chainStatus" compact /></td>
                <td><div class="table-actions"><button class="text-button" type="button" @click="openDetail('batch-detail', batch.id)">详情</button><button class="text-button" type="button" :disabled="!canClose(batch)" @click="openClose(batch, 2)">关闭</button><button class="text-button danger" type="button" :disabled="!canClose(batch)" @click="openClose(batch, 3)">召回</button></div></td>
              </tr></tbody>
            </table>
          </div>
        </ResourceState>
      </div>
      <footer class="ledger-footer">
        <p class="muted tiny" role="status">{{ activeResource.loading.value ? '读取中…' : activeResource.error.value ? '记录尚未读取' : `筛选后 ${resultCount} 条 · 第 ${page} / ${pageCount} 页` }}</p>
        <div class="row"><button class="action-button" type="button" :disabled="page <= 1 || !!activeResource.error.value || activeResource.loading.value" @click="page--">上一页</button><button class="action-button" type="button" :disabled="page >= pageCount || !!activeResource.error.value || activeResource.loading.value" @click="page++">下一页</button></div>
      </footer>
    </GlassPanel>
    <p v-if="tab === 'batches'" class="muted tiny">关闭和召回仅对生产中、上链已确认或未启用上链的批次开放。待确认或失败的记录请先核查，勿重复操作。</p>

    <AppDialog :open="mode !== null" :title="dialogTitle" :busy="saving || checking" @close="closeDialog">
      <form v-if="mode === 'product'" novalidate @submit.prevent="submitProduct">
        <p class="form-intro muted">带 * 的字段必填。产品编码建立后用于关联生产记录，请保持唯一。</p>
        <fieldset :disabled="saving || uncertain" class="form-grid">
          <div v-for="field in productFields" :key="field.key" class="form-field">
            <label :for="`product-${field.key}`" class="field-label">{{ field.label }}{{ field.required ? ' *' : '' }}</label>
            <input :id="`product-${field.key}`" v-model="productDraft[field.key]" class="field-input" :type="field.type" :min="field.type === 'number' ? 1 : undefined" :step="field.type === 'number' ? 1 : undefined" :maxlength="field.max" :required="field.required" :aria-invalid="!!errors[field.key]" :aria-describedby="errors[field.key] ? `product-error-${field.key}` : undefined">
            <p v-if="errors[field.key]" :id="`product-error-${field.key}`" class="field-error">{{ errors[field.key] }}</p>
          </div>
          <div class="form-field full"><label for="product-description" class="field-label">产品说明</label><textarea id="product-description" v-model="productDraft.description" class="field-input" rows="3" maxlength="5000"></textarea></div>
          <p class="muted tiny full">机构编号请填写已确认的业务机构 ID；当前尚无机构选择页面。</p>
        </fieldset>
        <div class="form-actions"><button class="action-button primary" type="submit" :disabled="saving || uncertain">{{ saving ? '保存中…' : '保存产品' }}</button></div>
      </form>

      <form v-else-if="mode === 'batch'" novalidate @submit.prevent="submitBatch">
        <p class="form-intro muted">批次保存后会提交摘要上链。请核对所属产品、生产日期与编码。</p>
        <fieldset :disabled="saving || uncertain" class="form-grid">
          <div class="form-field full"><label for="batch-productId" class="field-label">所属产品 *</label><select id="batch-productId" v-model="batchDraft.productId" class="field-input" required :aria-invalid="!!errors.productId" :aria-describedby="errors.productId ? 'batch-error-productId' : undefined"><option value="">请选择产品</option><option v-for="product in products" :key="product.id" :value="String(product.id)">{{ product.productName }} · {{ product.productCode }}</option></select><p v-if="errors.productId" id="batch-error-productId" class="field-error">{{ errors.productId }}</p></div>
          <p class="muted tiny full">机构编号：{{ batchDraft.organizationId || '随所属产品带入' }}</p>
          <p v-if="errors.organizationId" class="field-error full">{{ errors.organizationId }}</p>
          <div v-for="field in batchFields" :key="field.key" class="form-field">
            <label :for="`batch-${field.key}`" class="field-label">{{ field.label }}{{ field.required ? ' *' : '' }}</label>
            <input :id="`batch-${field.key}`" v-model="batchDraft[field.key]" class="field-input" :type="field.type" :maxlength="field.max" :required="field.required" :aria-invalid="!!errors[field.key]" :aria-describedby="errors[field.key] ? `batch-error-${field.key}` : undefined">
            <p v-if="errors[field.key]" :id="`batch-error-${field.key}`" class="field-error">{{ errors[field.key] }}</p>
          </div>
          <details class="full"><summary>扩展资料（选填）</summary><label for="batch-metadata" class="field-label">JSON 对象</label><textarea id="batch-metadata" v-model="batchDraft.metadata" class="field-input mono" rows="4" placeholder='{"note":"生产备注"}' :aria-invalid="!!errors.metadata" :aria-describedby="errors.metadata ? 'batch-error-metadata' : undefined"></textarea><p v-if="errors.metadata" id="batch-error-metadata" class="field-error">{{ errors.metadata }}</p></details>
        </fieldset>
        <div class="form-actions"><button class="action-button primary" type="submit" :disabled="saving || uncertain">{{ saving ? '保存并提交中…' : '保存批次并提交上链' }}</button></div>
      </form>

      <form v-else-if="mode === 'close'" novalidate @submit.prevent="submitClose">
        <p class="form-intro">你将{{ closeStatus === 3 ? '召回' : '关闭' }}批次 <strong class="mono">{{ selectedBatch?.batchCode }}</strong>。此操作会改变业务状态并提交链上记录，当前没有重新开启功能。</p>
        <fieldset :disabled="saving || uncertain" class="form-grid"><div class="form-field full"><label for="close-reason" class="field-label">{{ closeStatus === 3 ? '召回' : '关闭' }}原因 *</label><textarea id="close-reason" v-model="closeReason" class="field-input" rows="3" maxlength="1000" required :aria-invalid="!!errors.reason" :aria-describedby="errors.reason ? 'close-error-reason' : undefined"></textarea><p v-if="errors.reason" id="close-error-reason" class="field-error">{{ errors.reason }}</p></div></fieldset>
        <div class="form-actions"><button class="action-button" type="button" :disabled="saving" @click="closeDialog">取消</button><button class="action-button" :class="closeStatus === 3 ? 'danger-button' : 'primary'" type="submit" :disabled="saving || uncertain">{{ saving ? '提交中…' : closeStatus === 3 ? '确认召回' : '确认关闭' }}</button></div>
      </form>

      <template v-else-if="mode === 'product-detail'">
        <ResourceState :loading="productDetail.loading.value" :error="productDetail.error.value" @retry="productDetail.load">
          <dl v-if="productDetail.data.value" class="detail-grid"><template v-for="[label, value] in [
            ['产品名称', productDetail.data.value.productName], ['产品编码', productDetail.data.value.productCode],
            ['品类', productDetail.data.value.category], ['品种', productDetail.data.value.variety],
            ['产地', productDetail.data.value.originPlace], ['机构编号', productDetail.data.value.organizationId], ['产品说明', productDetail.data.value.description],
          ]" :key="label"><div><dt>{{ label }}</dt><dd>{{ value || '—' }}</dd></div></template></dl>
          <div class="form-actions"><button class="action-button" type="button" @click="viewBatches(selectedId)">查看该产品批次</button><button class="action-button primary" type="button" @click="openBatch(selectedId)">新建批次</button></div>
        </ResourceState>
      </template>
      <template v-else-if="mode === 'batch-detail'">
        <ResourceState :loading="batchDetail.loading.value" :error="batchDetail.error.value" @retry="batchDetail.load">
          <template v-if="batchDetail.data.value">
            <dl class="detail-grid"><template v-for="[label, value] in [
              ['批次编码', batchDetail.data.value.batchCode], ['所属产品', productMap.get(batchDetail.data.value.productId)?.productName || `产品 #${batchDetail.data.value.productId}`],
              ['机构编号', batchDetail.data.value.organizationId], ['生产地块', batchDetail.data.value.productionArea],
              ['种植日期', formatDate(batchDetail.data.value.plantDate)], ['采收日期', formatDate(batchDetail.data.value.harvestDate)],
              ['业务状态', batchStatusLabel(batchDetail.data.value.status)], ['创建时间', formatDateTime(batchDetail.data.value.createTime)],
            ]" :key="label"><div><dt>{{ label }}</dt><dd>{{ value || '—' }}</dd></div></template></dl>
            <p class="detail-status">上链状态 <StatusChip :status="batchDetail.data.value.chainStatus" /></p>
            <details><summary>扩展资料</summary><pre class="metadata-preview">{{ batchDetail.data.value.batchMetadata || '未填写' }}</pre></details>
            <div class="form-actions"><button class="action-button" type="button" :disabled="!canClose(batchDetail.data.value)" @click="openClose(batchDetail.data.value, 2)">关闭批次</button><button class="action-button danger-button" type="button" :disabled="!canClose(batchDetail.data.value)" @click="openClose(batchDetail.data.value, 3)">召回批次</button></div>
          </template>
        </ResourceState>
      </template>
      <div v-if="saveError" class="mutation-error" role="alert"><p>{{ saveError }}</p><button v-if="uncertain" class="action-button" type="button" :disabled="checking" @click="reconcile">{{ checking ? '核对中…' : '核对保存结果' }}</button></div>
    </AppDialog>
  </div>
</template>

<style scoped>
.catalog { min-width: 0; }
.catalog-intro { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 20px; border-radius: var(--radius-lg); }
.catalog-intro h2 { margin: 8px 0; font-size: var(--fs-h2); }
.catalog-intro p { font-size: var(--fs-sm); max-width: 520px; }
.save-notice { display: flex; justify-content: space-between; gap: 16px; padding: 16px; border-radius: 14px; background: var(--field-tint); font-size: var(--fs-sm); }
.save-notice.tone-bad { background: var(--seal-tint); color: var(--seal); }
.save-notice.tone-off, .save-notice.tone-wait { background: var(--grain-tint); }
.ledger { overflow: hidden; border-radius: var(--radius-lg); }
.ledger-head, .ledger-footer { padding: 16px 24px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; }
.segmented { display: flex; gap: 6px; flex-wrap: wrap; }
.segmented button { min-height: 42px; border: 0; border-radius: 12px; background: transparent; padding: 8px 12px; font-size: var(--fs-sm); }
.segmented button[aria-pressed="true"] { background: var(--field); color: white; }
.segmented span { margin-left: 8px; font-family: var(--font-mono); }
.catalog-filters { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; padding: 0 24px 20px; border-bottom: 1px solid var(--glass-hairline); }
.catalog-filters label { display: grid; gap: 6px; font-size: var(--fs-xs); min-width: 120px; }
.filter-search { flex: 1 1 220px; }
.ledger-body { padding: 0 24px; }
.table-scroll { overflow-x: auto; }
.data-table { min-width: 680px; font-size: var(--fs-sm); text-align: left; }
.batch-table { min-width: 800px; }
.data-table th { padding: 14px 12px; font-size: var(--fs-xs); color: var(--slate); font-weight: 500; white-space: nowrap; }
.data-table td { padding: 16px 12px; border-top: 1px solid var(--glass-hairline); max-width: 240px; overflow-wrap: anywhere; vertical-align: top; }
.data-table td p { margin-top: 5px; }
.record-link, .text-button { padding: 4px 0; border: 0; background: transparent; color: var(--field); font: inherit; font-size: var(--fs-sm); text-align: left; }
.record-link { font-weight: 600; }
.record-link:hover, .text-button:hover:not(:disabled) { text-decoration: underline; }
.text-button:disabled { color: var(--slate); opacity: 0.6; cursor: not-allowed; }
.table-actions { display: flex; flex-wrap: wrap; gap: 12px; }
.danger { color: var(--seal); }
.primary { background: var(--field); color: white; border-color: var(--field); }
.primary:hover:not(:disabled) { background: var(--field-soft); }
.danger-button { color: var(--seal); border-color: var(--seal); background: var(--seal-tint); }
.form-intro { margin-bottom: 20px; font-size: var(--fs-sm); }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; border: 0; padding: 0; margin: 0; min-width: 0; }
.form-field { display: grid; gap: 6px; align-content: start; }
.full { grid-column: 1 / -1; }
.form-actions { margin-top: 24px; display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 12px; }
.mutation-error, .dependency-warning { padding: 14px; margin-top: 16px; color: var(--seal); background: var(--seal-tint); border-radius: 12px; font-size: var(--fs-sm); }
.mutation-error button { margin-top: 12px; }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; margin: 0; }
.detail-grid dt { font-size: var(--fs-xs); color: var(--slate); }
.detail-grid dd { margin: 5px 0 0; overflow-wrap: anywhere; white-space: pre-wrap; font-size: var(--fs-sm); }
.detail-status { margin: 20px 0; }
.metadata-preview { padding: 12px; white-space: pre-wrap; overflow-wrap: anywhere; font-size: var(--fs-xs); }
summary { cursor: pointer; font-size: var(--fs-sm); padding: 8px 0; }
textarea { resize: vertical; }
@media (max-width: 560px) {
  .form-grid, .detail-grid { grid-template-columns: minmax(0, 1fr); }
  .catalog-filters, .ledger-head, .ledger-footer { padding-left: 16px; padding-right: 16px; }
  .ledger-body { padding: 0 12px; }
  .catalog-filters label { min-width: 0; flex: 1 1 130px; }
  .catalog-filters .filter-search { flex-basis: 100%; }
}
</style>
