<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { previewQualityProof, submitQualityProof, type QualityPreview } from '@/api/qualityEvidence'
import { useAuth } from '@/composables/useAuth'
import { operationError } from '@/utils/growing'
import QualityProofVersions from './QualityProofVersions.vue'
const props = defineProps<{ reportId: number }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const auth = useAuth(), code = ref(''), activeCode = ref(''), data = ref<QualityPreview | null>(null), busy = ref(false), loading = ref(false), approved = ref(false), target = ref(''), error = ref(''), notice = ref('')
let poll: ReturnType<typeof setInterval> | undefined
onBeforeUnmount(() => clearInterval(poll))
async function preview() {
  if (busy.value || loading.value) return
  loading.value = true; data.value = null; approved.value = false; error.value = ''; notice.value = ''
  try { data.value = await previewQualityProof(props.reportId, code.value.trim()); activeCode.value = data.value.traceCode; target.value = data.value.manifestHash }
  catch (cause) { error.value = operationError(cause) }
  finally { loading.value = false }
}
async function submit() {
  if (busy.value || !approved.value || !data.value) return
  busy.value = true; emit('busy', true); error.value = ''; notice.value = ''
  poll = setInterval(async () => { try { const progress = await previewQualityProof(props.reportId, activeCode.value); if (busy.value) data.value = progress } catch { /* 原提交保留，短暂轮询失败不代表交易失败。 */ } }, 1500)
  try { data.value = await submitQualityProof(props.reportId, activeCode.value, target.value); approved.value = false; notice.value = '本轮处理结束，请查看回执；公众查询会独立读取链上记录核验。' }
  catch (cause) { error.value = operationError(cause) + ' 可刷新回执核对，未知结果不会盲目重发。' }
  finally { clearInterval(poll); busy.value = false; emit('busy', false) }
}
const hints: Record<string,string> = { SOURCE_MISMATCH: '链上来源或摘要不一致，已停止', SOURCE_NOT_ACTIVE: '批次或包装码状态不允许新写入', HASH_MISMATCH: '链上事件内容、类型或时间不一致', CHAIN_READ_UNAVAILABLE: '暂时无法读取链上记录', RECONCILE_REQUIRED: '上次结果未知，先核对，不重复发送', CHAIN_RESULT_UNKNOWN: '提交结果尚不确定', CHAIN_RECORD_MISSING: '原已确认事件暂未查到', PREVIOUS_NOT_VERIFIED: '前一版本未实时核验一致', EVENT_LIMIT: '已达到链上事件核对上限', CHAIN_DISABLED: '本次链写入未启用', TRANSACTION_FAILED: '交易未成功，可核对后恢复' }
</script>
<template>
  <section class="quality-evidence stack"><h4>为具体包装码留存质检证据</h4><p class="muted">输入适用于本报告的完整包装码。采收样本范围不等于所有码已存证；本次只处理明确选择的一个码，不改写原生产、流转或报告资料。</p><form class="filters" @submit.prevent="preview"><label>完整包装码<input v-model.trim="code" class="field-input" maxlength="128" :disabled="busy || loading" required @input="data = null; approved = false; activeCode = ''"></label><button class="action-button" :disabled="busy || loading">{{ loading ? '正在读取…' : '预览 / 刷新回执' }}</button></form>
    <template v-if="data"><p class="notice code">本轮包装码：{{ activeCode }} · {{ data.frozen ? '已冻结' : '待冻结' }}版本 {{ data.manifest.version }}</p><p v-if="data.versionLimitReached" class="notice wait">本报告与码已达 20 版上限，当前新事实不能继续生成版本，仍可核对或恢复最近冻结版。</p><QualityProofVersions :preview="true" :versions="[{ version: data.manifest.version, manifest: data.manifest, manifestHash: data.manifestHash, createdAt: data.frozen ? data.versions[0]?.createdAt : undefined, chainStatus: data.frozen ? data.versions[0]?.chainStatus ?? 'NOT_SUBMITTED' : 'NOT_SUBMITTED' }]" /><form v-if="auth.canWrite.value" class="stack" @submit.prevent="submit"><label class="form-field">提交范围<select v-model="target" class="field-input" :disabled="busy" @change="approved = false"><option :value="data.manifestHash">当前预览版本 {{ data.manifest.version }}</option><option v-if="data.versions[0] && data.versions[0].manifestHash !== data.manifestHash" :value="data.versions[0].manifestHash">恢复最近冻结版本 {{ data.versions[0].version }}</option></select></label><p class="digest">确认摘要 {{ target }}</p><label class="approval"><input v-model="approved" type="checkbox" :disabled="busy">已核对包装码、样本范围、报告资料和本次证据版本，确认提交。摘要一致不等于检测机构真实性或本件质量保证。</label><button class="action-button primary" :disabled="busy || !approved">{{ busy ? '正在处理与核对…' : '确认提交 / 恢复所选版本' }}</button></form><p v-if="busy" role="status">正在核对来源、历史事件和本次提交，回执持续刷新。</p><h4 v-if="data.versions.length">冻结版本与回执</h4><QualityProofVersions :preview="true" :versions="data.versions" /><p v-for="version in data.versions.filter(v => v.errorCode)" :key="version.version" class="notice wait">版本 {{ version.version }}：{{ hints[version.errorCode!] || '请核对处理结果' }}</p></template><p v-if="notice" role="status" class="notice">{{ notice }}</p><p v-if="error" role="alert" class="notice bad">{{ error }}</p>
  </section>
</template>
<style scoped>
.quality-evidence { border-top: 1px solid var(--glass-hairline); padding-top: 16px; min-width: 0; }.digest,.code { overflow-wrap: anywhere; }.digest { font-size: var(--fs-xs); color: var(--slate); }.approval { display: flex; gap: 10px; align-items: start; line-height: 1.8; }.approval input { flex-shrink: 0; margin-top: 6px; }
</style>
