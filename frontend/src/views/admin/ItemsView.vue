<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import QRCode from 'qrcode'
import GlassPanel from '@/components/GlassPanel.vue'
import StatusChip from '@/components/StatusChip.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import TraceTimeline from '@/components/TraceTimeline.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { appendEvent, generateItems, getItem, listBatches, listEvents, listItems, listProducts } from '@/api/catalog'
import { ApiError } from '@/api/http'
import { EVENT_TYPE_LABEL, nfcStatusLabel } from '@/types/api'
import type { TraceItemEntity } from '@/types/catalog'
import { batchSaveFeedback } from '@/utils/catalog'
import { eventTime, traceUrl, validateCount } from '@/utils/items'

const items = useAsyncResource(listItems)
const batches = useAsyncResource(listBatches)
const products = useAsyncResource(listProducts)
const selectedItem = ref<TraceItemEntity | null>(null)
const events = useAsyncResource(() => listEvents(selectedItem.value!.id))
const route = useRoute()
const query = ref(''), batchFilter = ref(String(route.query.batchId ?? '')), statusFilter = ref('')
const page = ref(1)
const selected = ref<number[]>([])
const base = ref(window.location.origin)
const labels = ref<{ id: number; code: string; url: string; image: string; product: string }[]>([])
const labelError = ref(''), labelLoading = ref(false)
const mode = ref<'generate' | 'detail' | 'labels' | null>(null)
const busy = ref(false), uncertain = ref(false), error = ref(''), notice = ref('')
const generateDraft = reactive({ batchId: '', count: 1 })
const eventDraft = reactive({ eventType: 'FARMING', eventTime: '', location: '', operatorName: '', note: '' })
const activeBatches = computed(() => (batches.data.value ?? []).filter(b => b.status === 'ACTIVE' && ['CONFIRMED', 'CHAIN_DISABLED'].includes(b.chainStatus)))
const batchMap = computed(() => new Map((batches.data.value ?? []).map(b => [b.id, b])))
const productMap = computed(() => new Map((products.data.value ?? []).map(p => [p.id, p])))
const filtered = computed(() => (items.data.value ?? []).filter(item => (!batchFilter.value || item.batchId === Number(batchFilter.value))
  && (!statusFilter.value || item.chainStatus === statusFilter.value)
  && (!query.value.trim() || [item.traceCodePlain, item.traceCodeHash, batchMap.value.get(item.batchId)?.batchCode]
    .some(value => value?.toLowerCase().includes(query.value.trim().toLowerCase())))))
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / 10)))
const rows = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10))
const eventViews = computed(() => (events.data.value ?? []).map(event => ({ ...event, payloadHash: event.canonicalPayloadHash })))
watch([query, batchFilter, statusFilter], () => { page.value = 1 })
watch(pages, count => { page.value = Math.min(page.value, count) })
watch(items.data, values => { if (values) selected.value = selected.value.filter(id => values.some(item => item.id === id)) })
async function refresh() { await Promise.all([items.load(), batches.load(), products.load()]) }
onMounted(refresh)
function openGenerate() { error.value = ''; uncertain.value = false; generateDraft.batchId = batchFilter.value; generateDraft.count = 1; mode.value = 'generate' }
async function openItem(item: TraceItemEntity) {
  selectedItem.value = item; error.value = ''; uncertain.value = false; mode.value = 'detail'
  Object.assign(eventDraft, { eventType: 'FARMING', eventTime: '', location: '', operatorName: '', note: '' })
  await events.load()
}
async function generate() {
  if (busy.value || uncertain.value) return
  if (!activeBatches.value.some(b => b.id === Number(generateDraft.batchId)) || !validateCount(generateDraft.count)) {
    error.value = '请选择可用批次，数量须为 1–100 的整数。'; return
  }
  busy.value = true; error.value = ''
  try {
    const result = await generateItems({ batchId: Number(generateDraft.batchId), count: generateDraft.count })
    const failed = result.filter(item => item.chainStatus !== 'CONFIRMED').length
    notice.value = `已生成 ${result.length} 个单品码。${failed ? `${failed} 个尚未确认上链，请核查状态。` : '上链均已确认。'}`
    mode.value = null
    await items.load()
    selected.value = result.map(item => item.id)
  } catch (cause) { mutationError(cause) } finally { busy.value = false }
}
function mutationError(cause: unknown) {
  if (cause instanceof ApiError && ['400', '409'].includes(cause.code)) error.value = '内容不符合要求或记录已变化，请核对输入和当前状态。'
  else { uncertain.value = true; error.value = '提交结果未确认，请关闭窗口并刷新列表核查，避免重复提交。' }
}
async function append() {
  if (!selectedItem.value || busy.value || uncertain.value) return
  error.value = ''
  let time: string
  try { time = eventTime(eventDraft.eventTime) } catch (cause) { error.value = (cause as Error).message; return }
  busy.value = true
  try {
    const result = await appendEvent(selectedItem.value.id, {
      eventType: eventDraft.eventType, eventTime: time, location: eventDraft.location.trim() || undefined,
      operatorName: eventDraft.operatorName.trim() || undefined,
      payload: eventDraft.note.trim() ? { note: eventDraft.note.trim() } : undefined,
    })
    notice.value = batchSaveFeedback(result.chainStatus).message
    eventDraft.note = ''; eventDraft.eventTime = ''
    await events.load()
    selectedItem.value = await getItem(selectedItem.value.id)
    await items.load()
  } catch (cause) { mutationError(cause) } finally { busy.value = false }
}
let labelRevision = 0
async function prepareLabels() {
  const revision = ++labelRevision
  labelLoading.value = true; labelError.value = ''; labels.value = []
  try {
    const result = await Promise.all((items.data.value ?? []).filter(item => selected.value.includes(item.id)).map(async item => {
      const code = item.traceCodePlain ?? ''
      const url = traceUrl(code, base.value)
      return { id: item.id, code, url, image: await QRCode.toDataURL(url, { width: 240, margin: 2, errorCorrectionLevel: 'M' }),
        product: productMap.value.get(batchMap.value.get(item.batchId)?.productId ?? 0)?.productName ?? '农产品溯源' }
    }))
    if (revision === labelRevision) labels.value = result
  } catch (cause) { if (revision === labelRevision) labelError.value = (cause as Error).message }
  finally { if (revision === labelRevision) labelLoading.value = false }
}
watch(base, () => { if (mode.value === 'labels') void prepareLabels() })
async function previewLabels() { mode.value = 'labels'; await prepareLabels() }
function print() { if (labels.value.length && !labelLoading.value && !labelError.value) window.print() }
function selectRow(id: number) { selected.value = selected.value.includes(id) ? selected.value.filter(value => value !== id) : [...selected.value, id] }
function selectPage() { const ids = rows.value.filter(item => !!item.traceCodePlain).map(item => item.id); selected.value = ids.every(id => selected.value.includes(id)) ? selected.value.filter(id => !ids.includes(id)) : [...new Set([...selected.value, ...ids])] }
</script>

<template>
  <div class="management stack">
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <GlassPanel class="stack">
      <div class="toolbar"><div><p class="eyebrow">单品身份</p><h2 class="display">每一件，都有自己的溯源编号</h2></div><div class="actions"><button class="action-button" type="button" :disabled="items.loading.value" @click="refresh">刷新列表</button><button class="action-button primary" type="button" :disabled="!activeBatches.length" @click="openGenerate">生成单品码</button></div></div>
      <p v-if="batches.error.value" class="notice bad">批次读取失败，请刷新后再生成单品码。</p>
      <p v-else-if="!batches.loading.value && !activeBatches.length" class="muted tiny">请先在产品与批次页面创建生产中、上链已确认或未启用上链的批次。</p>
      <form class="filters" role="search" @submit.prevent><label>搜索单品<input v-model="query" class="field-input" type="search" placeholder="溯源编号、摘要或批次编码"></label><label>所属批次<select v-model="batchFilter" class="field-input"><option value="">全部批次</option><option v-for="batch in batches.data.value" :key="batch.id" :value="String(batch.id)">{{ batch.batchCode }}</option></select></label><label>上链状态<select v-model="statusFilter" class="field-input"><option value="">全部状态</option><option value="CONFIRMED">上链已确认</option><option value="PENDING">等待上链</option><option value="FAILED">上链失败</option><option value="CHAIN_DISABLED">未上链</option></select></label></form>
      <div class="toolbar"><p class="muted tiny" role="status">已选择 {{ selected.length }} 个 · 共 {{ filtered.length }} 条</p><div class="actions"><button class="action-button" type="button" :disabled="!rows.length" @click="selectPage">选择 / 取消本页</button><button class="action-button" type="button" :disabled="!selected.length" @click="previewLabels">预览与打印二维码</button></div></div>
      <ResourceState :loading="items.loading.value" :error="items.error.value" :empty="!filtered.length" empty-text="暂无符合条件的单品码。可先选择生产批次生成。" @retry="items.load">
        <div class="table-scroll" tabindex="0" aria-label="单品台账，可横向滚动"><table class="data-table"><caption class="sr-only">单品码列表</caption><thead><tr><th scope="col">选择</th><th scope="col">溯源编号 / 批次</th><th scope="col">上链状态</th><th scope="col">标签 / 业务状态</th><th scope="col">操作</th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td><input type="checkbox" :checked="selected.includes(item.id)" :disabled="!item.traceCodePlain" :aria-label="`选择 ${item.traceCodePlain || item.id}`" @change="selectRow(item.id)"></td><td><button class="text-button mono" type="button" @click="openItem(item)">{{ item.traceCodePlain || `单品 #${item.id}` }}</button><p class="muted tiny">{{ batchMap.get(item.batchId)?.batchCode || `批次 #${item.batchId}` }}</p></td><td><StatusChip :status="item.chainStatus" compact /></td><td>{{ nfcStatusLabel(item.nfcStatus) }}<p class="muted tiny">{{ item.status === 'RECALL' ? '已召回' : item.status === 'ACTIVE' ? '有效' : item.status }}</p></td><td><div class="actions"><button class="text-button" type="button" @click="openItem(item)">详情与事件</button><RouterLink v-if="item.traceCodePlain" class="text-button" :to="{ name: 'public-trace', params: { traceCode: item.traceCodePlain } }">公众查询 ↗</RouterLink></div></td></tr></tbody></table></div>
      </ResourceState>
      <div class="toolbar"><p class="muted tiny">第 {{ page }} / {{ pages }} 页</p><div class="actions"><button class="action-button" type="button" :disabled="page <= 1" @click="page--">上一页</button><button class="action-button" type="button" :disabled="page >= pages" @click="page++">下一页</button></div></div>
    </GlassPanel>
    <AppDialog :open="mode !== null" :title="mode === 'generate' ? '生成单品码' : mode === 'labels' ? '二维码预览与打印' : '单品详情与溯源事件'" :busy="busy" @close="mode = null">
      <form v-if="mode === 'generate'" @submit.prevent="generate"><p class="muted tiny">每个单品码都会提交一笔存证交易，批量生成可能需要等待。请勿重复提交。</p><fieldset :disabled="busy || uncertain"><div class="form-field"><label class="field-label" for="generate-batch">生产批次 *</label><select id="generate-batch" v-model="generateDraft.batchId" class="field-input" required><option value="">请选择批次</option><option v-for="batch in activeBatches" :key="batch.id" :value="String(batch.id)">{{ batch.batchCode }}</option></select></div><div class="form-field"><label class="field-label" for="generate-count">生成数量（1–100）*</label><input id="generate-count" v-model.number="generateDraft.count" class="field-input" type="number" min="1" max="100" step="1" required></div></fieldset><button class="action-button primary" type="submit" :disabled="busy || uncertain">{{ busy ? '生成并提交中…' : '确认生成单品码' }}</button></form>
      <template v-else-if="mode === 'detail' && selectedItem">
        <p class="mono item-code">{{ selectedItem.traceCodePlain || selectedItem.traceCodeHash }}</p><p class="detail-status"><StatusChip :status="selectedItem.chainStatus" /></p>
        <h3 class="display">已记录的流转事件</h3><ResourceState :loading="events.loading.value" :error="events.error.value" @retry="events.load"><TraceTimeline :events="eventViews" /></ResourceState>
        <form class="event-form" @submit.prevent="append"><h3 class="display">追加溯源事件</h3><p class="muted tiny">事件时间按北京时间填写。提交后保留存证，不提供删除功能。</p><fieldset :disabled="busy || uncertain" class="form-grid"><div class="form-field"><label class="field-label" for="event-type">事件类型 *</label><select id="event-type" v-model="eventDraft.eventType" class="field-input"><option v-for="(label, value) in EVENT_TYPE_LABEL" :key="value" :value="value">{{ label }}</option></select></div><div class="form-field"><label class="field-label" for="event-time">事件时间 *</label><input id="event-time" v-model="eventDraft.eventTime" class="field-input" type="datetime-local" required></div><div class="form-field"><label class="field-label" for="event-location">地点</label><input id="event-location" v-model="eventDraft.location" class="field-input" maxlength="255"></div><div class="form-field"><label class="field-label" for="event-operator">操作人</label><input id="event-operator" v-model="eventDraft.operatorName" class="field-input" maxlength="128"></div><div class="form-field full"><label class="field-label" for="event-note">事件说明</label><textarea id="event-note" v-model="eventDraft.note" class="field-input" rows="3" maxlength="5000"></textarea></div></fieldset><button class="action-button primary" type="submit" :disabled="busy || uncertain">{{ busy ? '保存并提交中…' : '保存事件并提交上链' }}</button></form>
      </template>
      <template v-else-if="mode === 'labels'"><label for="qr-base" class="field-label">扫码访问地址</label><input id="qr-base" v-model="base" class="field-input" type="url"><p class="muted tiny">手机扫码时，此地址须为手机可达的前端地址。修改此处只影响标签链接，不改动已存证的业务记录。</p><p v-if="labelError" class="notice bad" role="alert">{{ labelError }}</p><p v-if="labelLoading" role="status">二维码生成中…</p><div class="label-grid"><article v-for="label in labels" :key="label.id" class="qr-label"><img :src="label.image" :alt="`${label.code} 的二维码`"><h3>{{ label.product }}</h3><p class="mono">{{ label.code }}</p><p class="muted tiny">{{ label.url }}</p></article></div><button class="action-button primary" type="button" :disabled="!labels.length || labelLoading || !!labelError" @click="print">打印 {{ labels.length }} 张标签</button></template>
      <p v-if="error && mode !== 'labels'" class="notice bad" role="alert">{{ error }}</p>
    </AppDialog>
    <Teleport to="body"><section class="print-labels" aria-hidden="true"><article v-for="label in labels" :key="label.id" class="qr-label"><img :src="label.image" alt=""><h3>{{ label.product }}</h3><p>{{ label.code }}</p></article></section></Teleport>
  </div>
</template>

<style scoped>
h2 { font-size: var(--fs-h2); margin-top: 8px; }
h3 { margin: 16px 0; font-size: var(--fs-h2); }
.item-code { overflow-wrap: anywhere; margin-bottom: 12px; }
.event-form { border-top: 1px solid var(--glass-hairline); margin-top: 20px; }
.label-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; margin: 16px 0; }
.qr-label { text-align: center; border: 1px solid var(--ink-16); border-radius: 12px; padding: 12px; overflow-wrap: anywhere; break-inside: avoid; }
.qr-label img { width: 100%; max-width: 180px; aspect-ratio: 1; }
.qr-label h3 { font-size: var(--fs-sm); margin: 6px 0; }
.qr-label p { font-size: var(--fs-xs); }
@media(max-width:480px) { .label-grid { grid-template-columns: minmax(0,1fr); } }
</style>
<style>
.print-labels { display: none; }
@media print {
  body > #app { display: none !important; }
  body { background: white !important; }
  .print-labels { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10mm; padding: 8mm; color: black; }
  .print-labels .qr-label { border: 1px solid #888; break-inside: avoid; padding: 4mm; text-align: center; }
  .print-labels img { width: 40mm; height: 40mm; }
}
</style>
