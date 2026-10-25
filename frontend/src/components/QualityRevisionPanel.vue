<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { qualityRevision, linkQualityRevision, type RevisionDetail } from '@/api/qualityEvidence'
import { useAuth } from '@/composables/useAuth'
import { operationError } from '@/utils/growing'
import { formatDateTime } from '@/types/api'
const props = defineProps<{ reportId: number }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const auth = useAuth(), data = ref<RevisionDetail | null>(null), previousId = ref(''), reason = ref(''), approved = ref(false), busy = ref(false), loading = ref(false), error = ref(''), notice = ref('')
function changePrevious() { approved.value = false; if (data.value) data.value = { ...data.value, candidatePrevious: undefined } }
onMounted(() => { void load() })
async function load(preview = false) {
  if (busy.value || loading.value) return
  loading.value = true; approved.value = false; error.value = ''
  try { data.value = await qualityRevision(props.reportId, preview ? Number(previousId.value) : undefined) }
  catch (cause) { error.value = operationError(cause); data.value = null }
  finally { loading.value = false }
}
async function save() {
  if (busy.value || !approved.value || !data.value?.candidatePrevious) return
  busy.value = true; emit('busy', true); error.value = ''
  try { await linkQualityRevision(props.reportId, { previousReportId: data.value.candidatePrevious.id, expectedPreviousHash: data.value.candidatePrevious.manifestHash, expectedReplacementHash: data.value.report.manifestHash, reason: reason.value }); data.value = await qualityRevision(props.reportId); notice.value = '替代关系已保留，原报告未覆盖；新报告仍需单独核对公开，存证另行办理。'; approved.value = false }
  catch (cause) { error.value = operationError(cause) }
  finally { busy.value = false; emit('busy', false) }
}
</script>
<template>
  <section class="revision-panel stack"><h4>经营方关联的替代关系</h4><p class="muted">仅能将较新的待公开报告关联到同一采收或包装范围内已撤回的原报告。保留原文件与结论，不自动公开、上链或证明实验室正式修订。</p><button class="action-button" :disabled="busy || loading" @click="load()">刷新关系</button><p v-if="loading" role="status">正在读取…</p><template v-if="data"><p>本记录 #{{ data.report.id }} · {{ data.report.reportNo }}</p><p v-if="data.previous" class="notice">替代原报告记录 #{{ data.previous.previousReportId }} · {{ data.previous.reason }} · {{ formatDateTime(data.previous.recordedAt) }}</p><p v-if="data.next" class="notice">已有替代报告记录 #{{ data.next.replacementReportId }} · {{ data.next.reason }} · {{ formatDateTime(data.next.recordedAt) }}</p><template v-if="auth.canWrite.value && data.report.status === 'DRAFT' && !data.previous"><form class="filters" @submit.prevent="load(true)"><label>原报告记录编号<input v-model="previousId" class="field-input" type="number" min="1" step="1" required :disabled="busy || loading" @input="changePrevious"></label><button class="action-button" :disabled="busy || loading">核对原报告</button></form><form v-if="data.candidatePrevious" class="stack" @submit.prevent="save"><p class="notice">原报告：{{ data.candidatePrevious.reportNo }}（#{{ data.candidatePrevious.id }}）→ 新报告：{{ data.report.reportNo }}（#{{ data.report.id }}）</p><p v-if="data.candidatePrevious.status !== 'WITHDRAWN'" class="notice wait">请先撤回原报告，再确认替代关系。</p><p class="digest">原资料摘要 {{ data.candidatePrevious.manifestHash }}<br>新资料摘要 {{ data.report.manifestHash }}</p><label class="form-field">关联原因（仅机构内留档）<textarea v-model.trim="reason" class="field-input" rows="3" maxlength="500" required :disabled="busy" @input="approved = false"></textarea></label><label class="approval"><input v-model="approved" type="checkbox" :disabled="busy">已核对两个来源范围、原始报告与资料摘要，确认建立替代关系。</label><button class="action-button primary" :disabled="busy || !approved || data.candidatePrevious.status !== 'WITHDRAWN'">{{ busy ? '正在留档…' : '确认关联，不覆盖原报告' }}</button></form></template><p v-else-if="!data.previous && !data.next" class="muted">尚未关联替代记录；报告公开后不能补改其原报告关系。</p></template><p v-if="notice" role="status" class="notice">{{ notice }}</p><p v-if="error" role="alert" class="notice bad">{{ error }}</p></section>
</template>
<style scoped>
.revision-panel { min-width: 0; padding-top: 16px; border-top: 1px solid var(--glass-hairline); }.digest { overflow-wrap: anywhere; font-size: var(--fs-xs); color: var(--slate); }.approval { display: flex; gap: 10px; align-items: start; line-height: 1.8; }.approval input { margin-top: 6px; flex-shrink: 0; }
</style>
