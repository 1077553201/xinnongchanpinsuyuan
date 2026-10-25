<script setup lang="ts">
import { onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { qualityList, registerQuality, publishQuality, withdrawQuality, qualityDownloadUrl, QUALITY_RESULTS, QUALITY_STATUS, type SourceQualityReport, type QualityScope } from '@/api/sourceQuality'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { useAuth } from '@/composables/useAuth'
import { localDateTime, newOperationKey, operationError, uncertainOperation } from '@/utils/growing'
import { formatDateTime } from '@/types/api'
import ResourceState from './ResourceState.vue'
import QualityEvidencePanel from './QualityEvidencePanel.vue'
import QualityRevisionPanel from './QualityRevisionPanel.vue'
const props = defineProps<{ scope: QualityScope; sourceId: number }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const auth = useAuth(), page = ref(1), data = useAsyncResource(() => qualityList(props.scope, props.sourceId, page.value))
const mode = ref<'create' | 'publish' | 'withdraw' | null>(null), current = shallowRef<SourceQualityReport | null>(null), file = shallowRef<File | null>(null)
const busy = ref(false), uncertain = ref(false), approved = ref(false), error = ref(''), notice = ref('')
const auxBusy = ref(false), auxiliary = shallowRef<{ kind: 'evidence' | 'revision'; report: SourceQualityReport } | null>(null)
const draft = reactive({ reportNo: '', agency: '', sampledOn: '', checkedOn: '', result: 'INCONCLUSIVE', key: '', publicFile: false, privacyReviewed: false, reason: '' })
watch(() => [draft.reportNo, draft.agency, draft.sampledOn, draft.checkedOn, draft.result, draft.publicFile, draft.privacyReviewed], () => { approved.value = false })
watch(() => busy.value || auxBusy.value, value => emit('busy', value))
onMounted(() => { void data.load() })
function open(next: NonNullable<typeof mode.value>, report?: SourceQualityReport) {
  if (auxBusy.value) return
  auxiliary.value = null
  mode.value = next; current.value = report ?? null; file.value = null; approved.value = false; error.value = ''; uncertain.value = false
  Object.assign(draft, { reportNo: '', agency: '', sampledOn: localDateTime().slice(0, 10), checkedOn: localDateTime().slice(0, 10), result: 'INCONCLUSIVE', key: newOperationKey(), publicFile: false, privacyReviewed: false, reason: '' })
}
async function cancel() { if (busy.value || auxBusy.value) return; mode.value = null; uncertain.value = false; file.value = null; error.value = ''; await data.load() }
function chooseFile(event: Event) {
  const input = event.target as HTMLInputElement, selected = input.files?.[0]
  approved.value = false; file.value = null; error.value = ''
  if (!selected) return
  if (!/\.(pdf|png|jpe?g)$/i.test(selected.name) || selected.size < 1 || selected.size > 5 * 1024 * 1024) { error.value = '请选择不超过 5 MB 的 PDF、PNG 或 JPG 报告。'; input.value = ''; return }
  file.value = selected
}
async function save() {
  if (busy.value || auxBusy.value || !mode.value) return
  if (mode.value !== 'withdraw' && !approved.value) return
  if (mode.value === 'create' && !file.value) { error.value = '请先选择报告附件。'; return }
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'create') { const saved = await registerQuality(props.scope, props.sourceId, { reportNo: draft.reportNo, agencyName: draft.agency, sampledOn: draft.sampledOn, checkedOn: draft.checkedOn, declaredResult: draft.result, requestKey: draft.key }, file.value!); page.value = 1; notice.value = saved.status === 'DRAFT' ? '报告和附件已一并留存，尚未公开或提交链上存证。' : `已找到同一确认对应的原报告，当前为${QUALITY_STATUS[saved.status]}，原资料及附件保留。` }
    else if (mode.value === 'publish' && current.value) { await publishQuality(current.value.id, { expectedManifestHash: current.value.manifestHash, metadataReviewed: approved.value, publishAttachment: draft.publicFile, attachmentPrivacyReviewed: draft.privacyReviewed }); notice.value = draft.publicFile ? '报告资料已公开，附件允许消费者下载；各包装码存证请独立核对。' : '报告资料已公开，附件保持私有；各包装码存证请独立核对。' }
    else if (current.value) { await withdrawQuality(current.value.id, draft.reason); notice.value = '已撤回，原资料与附件保留；此前公开过的记录仍可查看，后续公开附件下载已关闭。' }
    mode.value = null; file.value = null; await data.load()
  } catch (cause) { error.value = operationError(cause) + ' 可保持本次内容重试，或返回列表核对是否已保存。'; uncertain.value = uncertainOperation(cause) }
  finally { busy.value = false }
}
</script>
<template>
  <section class="management quality-management stack" aria-label="来源质检报告与附件">
    <div class="section-head"><h3 class="display">{{ scope === 'HARVEST' ? '本次采收的样本报告' : '本批包装的样本报告' }}</h3><div class="actions"><button class="action-button" :disabled="busy || auxBusy || !!mode || data.loading.value" @click="data.load">刷新报告</button><button v-if="auth.canWrite.value" class="action-button primary" :disabled="busy || auxBusy || !!mode || !data.data.value" @click="open('create')">登记报告与附件</button></div></div>
    <p class="muted">报告须明确属于本次采收或本批包装。上传留存、经营方核对公开、链上存证分别记录；样本结论不等于每件货物都经过检测。</p>
    <p v-if="notice" role="status" class="notice">{{ notice }}</p>
    <ResourceState :loading="data.loading.value" :error="data.error.value" @retry="data.load">
      <template v-if="data.data.value">
        <p class="notice">{{ data.data.value.source.productName }} · {{ data.data.value.source.variety }}<br>{{ data.data.value.source.harvestCode }}<template v-if="scope === 'PACKING'"> → {{ data.data.value.source.packingCode }}</template></p>
        <p v-if="!data.data.value.rows.length" class="muted">尚未登记本来源报告，不能据此认定产品合格。</p>
        <article v-for="report in data.data.value.rows" :key="report.id" class="quality-card stack"><div class="section-head"><strong>{{ report.reportNo }} · 记录 #{{ report.id }}</strong><span>{{ QUALITY_STATUS[report.status] }}</span></div><p>{{ report.agencyName }} · {{ QUALITY_RESULTS[report.declaredResult] }}</p><p class="muted">采样 {{ report.sampledOn }} · 检测 {{ report.checkedOn }}</p><p class="file-name">{{ report.fileName }} · {{ (report.fileSize / 1024).toFixed(1) }} KB</p><p class="digest">附件 SHA-256 {{ report.fileSha256 }}</p><p class="digest">资料摘要 {{ report.manifestHash }} · 存证状态按具体包装码核对</p><p class="muted">上传留档：{{ report.recorderName }} · {{ formatDateTime(report.recordedAt) }}<template v-if="report.publishedAt"><br>核对公开：{{ report.publisherName }} · {{ formatDateTime(report.publishedAt) }}</template><br>附件当前：{{ report.status === 'WITHDRAWN' ? '已停止公开下载，私有原件保留' : report.attachmentPublic ? '允许公开下载' : '仅本机构私有' }}</p><p v-if="report.status === 'WITHDRAWN'" class="notice wait">撤回：{{ formatDateTime(report.withdrawnAt) }} · {{ report.withdrawReason }}</p><div class="actions"><button v-if="report.publishedAt" class="text-button" :disabled="busy || auxBusy || !!mode" @click="auxiliary = { kind: 'evidence', report }">质检证据存证</button><button class="text-button" :disabled="busy || auxBusy || !!mode" @click="auxiliary = { kind: 'revision', report }">查看 / 关联替代关系</button><a class="text-button" :href="qualityDownloadUrl(`/production/quality-reports/${report.id}/attachment`)" target="_blank" rel="noopener">下载留存附件</a><template v-if="auth.canWrite.value && report.status !== 'WITHDRAWN'"><button v-if="report.status === 'DRAFT'" class="text-button" :disabled="busy || auxBusy || !!mode" @click="open('publish', report)">核对公开范围</button><button class="text-button" :disabled="busy || auxBusy || !!mode" @click="open('withdraw', report)">撤回并留档</button></template></div></article>
        <div class="section-head"><p class="muted">共 {{ data.data.value.total }} 份 · 第 {{ data.data.value.page }} 页</p><div class="actions"><button class="action-button" :disabled="busy || auxBusy || !!mode || data.data.value.page <= 1" @click="page = data.data.value.page - 1; data.load()">上一页</button><button class="action-button" :disabled="busy || auxBusy || !!mode || data.data.value.page * 20 >= data.data.value.total" @click="page = data.data.value.page + 1; data.load()">下一页</button></div></div>
      </template>
    </ResourceState>
    <form v-if="mode" class="quality-form stack" @submit.prevent="save"><h4>{{ mode === 'create' ? '登记报告和附件' : mode === 'publish' ? '核对并公开报告' : '撤回报告' }}</h4><fieldset :disabled="busy || auxBusy || uncertain">
      <template v-if="mode === 'create'"><label class="form-field">报告编号<input v-model.trim="draft.reportNo" class="field-input" maxlength="64" required></label><label class="form-field">报告记载的检测机构<input v-model.trim="draft.agency" class="field-input" maxlength="128" required></label><div class="form-grid"><label class="form-field">采样日期<input v-model="draft.sampledOn" class="field-input" type="date" required></label><label class="form-field">检测日期<input v-model="draft.checkedOn" class="field-input" type="date" required></label></div><label class="form-field">报告记载的结果<select v-model="draft.result" class="field-input"><option v-for="(label,key) in QUALITY_RESULTS" :key="key" :value="key">{{ label }}</option></select></label><label class="form-field">报告附件（PDF、PNG 或 JPG，最大 5 MB）<input type="file" accept=".pdf,.png,.jpg,.jpeg" class="field-input" required @change="chooseFile"></label><p class="muted">原件默认仅本机构有权账号可下载。资料确认后保留原记录，不覆盖已登记附件；登记不代表已通过第三方审核。</p></template>
      <template v-else-if="mode === 'publish' && current"><p>{{ current.reportNo }} · {{ current.agencyName }} · {{ QUALITY_RESULTS[current.declaredResult] }}</p><p class="digest">确认资料摘要 {{ current.manifestHash }}</p><p class="notice">将公开报告编号、机构、采样/检测日期、原记载结论及摘要。请确认这些字段适合消费者查看。</p><label class="approval"><input v-model="draft.publicFile" type="checkbox">同时允许消费者下载附件原件（默认关闭）</label><label v-if="draft.publicFile" class="approval"><input v-model="draft.privacyReviewed" type="checkbox" required>已检查附件中的个人信息和其他敏感内容，确认适合公开。已下载的副本无法收回。</label></template>
      <label v-else class="form-field">撤回原因<textarea v-model.trim="draft.reason" class="field-input" maxlength="500" rows="3" required></textarea><span class="muted">撤回后保留原报告及附件；此前公开过的记录仍在公众页标注撤回。</span></label>
    </fieldset><label v-if="mode !== 'withdraw'" class="approval"><input v-model="approved" type="checkbox" :disabled="busy || auxBusy || uncertain">已核对本次来源、报告资料及{{ mode === 'create' ? '附件' : '公开范围' }}，确认办理。</label><div class="actions"><button class="action-button primary" :disabled="busy || auxBusy || (mode !== 'withdraw' && !approved) || (mode === 'create' && !file)">{{ busy ? '正在保存…' : uncertain ? '相同内容重试' : '确认保存' }}</button><button class="action-button" type="button" :disabled="busy || auxBusy" @click="cancel">返回并刷新核对</button></div></form>
    <QualityEvidencePanel v-if="auxiliary?.kind === 'evidence'" :key="`evidence-${auxiliary.report.id}`" :report-id="auxiliary.report.id" @busy="auxBusy = $event" />
    <QualityRevisionPanel v-if="auxiliary?.kind === 'revision'" :key="`revision-${auxiliary.report.id}`" :report-id="auxiliary.report.id" @busy="auxBusy = $event" />
    <p v-if="error" role="alert" class="notice bad">{{ error }}</p>
  </section>
</template>
<style scoped>
.quality-management { min-width: 0; }.quality-card,.quality-form { padding: 16px; border: 1px solid var(--glass-hairline); border-radius: var(--radius-md); min-width: 0; }.quality-card { overflow-wrap: anywhere; }.quality-card p { line-height: 1.8; }.digest,.file-name { overflow-wrap: anywhere; }.digest { font-size: var(--fs-xs); color: var(--slate); }.approval { display: flex; align-items: start; gap: 10px; line-height: 1.8; margin: 12px 0; }.approval input { flex-shrink: 0; margin-top: 6px; }.quality-form fieldset { margin: 0; }
</style>
