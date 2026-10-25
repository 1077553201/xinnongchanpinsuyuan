<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { previewCirculation, submitCirculation, type CirculationPreview } from '@/api/circulationEvidence'
import { useAuth } from '@/composables/useAuth'
import { operationError } from '@/utils/growing'
import { formatDateTime } from '@/types/api'
import CirculationVersionView from './CirculationVersionView.vue'
const props = defineProps<{ itemId: number; traceCode: string }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const auth = useAuth(), data = ref<CirculationPreview | null>(null), busy = ref(false), loading = ref(false), approved = ref(false), error = ref(''), notice = ref(''), target = ref('')
let poll: ReturnType<typeof setInterval> | undefined
onBeforeUnmount(() => clearInterval(poll))
onMounted(() => { void refresh() })
async function refresh() {
  if (busy.value) return
  loading.value = true; approved.value = false; error.value = ''; data.value = null
  try { data.value = await previewCirculation(props.itemId); target.value = data.value.manifestHash }
  catch (cause) { error.value = operationError(cause) }
  finally { loading.value = false }
}
function choose(hash: string) { target.value = hash; approved.value = false; notice.value = '' }
async function submit() {
  if (busy.value || !approved.value || !target.value) return
  busy.value = true; emit('busy', true); error.value = ''; notice.value = ''
  poll = setInterval(async () => { try { const progress = await previewCirculation(props.itemId); if (busy.value) data.value = progress } catch { /* 提交结果由原请求及最终回执决定。 */ } }, 1500)
  try { data.value = await submitCirculation(props.itemId, target.value); notice.value = '本轮处理结束，请核对回执；只有公众查询实时核验一致才显示本版已核验。'; approved.value = false }
  catch (cause) { error.value = operationError(cause) + ' 可刷新回执核对，未知结果不会盲目重发。' }
  finally { clearInterval(poll); busy.value = false; emit('busy', false) }
}
const hints: Record<string,string> = { CHAIN_READ_UNAVAILABLE: '暂时无法读取链上记录', SOURCE_MISMATCH: '生产来源或包装码归属不一致，已停止', SOURCE_NOT_ACTIVE: '链上批次或单品已停用，已停止', HASH_MISMATCH: '链上事件与本版摘要或时间不一致', RECONCILE_REQUIRED: '上次结果未知，请先核对，未重复发送', CHAIN_RECORD_MISSING: '已确认记录暂未查到', CHAIN_RESULT_UNKNOWN: '提交结果尚不确定', PREVIOUS_NOT_VERIFIED: '前版未在链上核验一致，不能跳过', CHAIN_DISABLED: '尚未启用本次链写入', EVENT_LIMIT: '已达到链上事件读取上限', TRANSACTION_FAILED: '交易未成功，可核对后重试' }
</script>
<template>
  <section class="flow-evidence stack" aria-label="逐件流转存证">
    <div class="section-head"><h3 class="display">逐件流转存证</h3><button class="action-button" :disabled="busy || loading" @click="refresh">刷新预览与回执</button></div>
    <p class="code mono">{{ traceCode }}</p><p class="muted">独立保留每版出库、运输和逐件收货快照；后续记录生成新版本，不改写生产证据或原流转版本。</p>
    <p v-if="loading" role="status">正在读取证据…</p>
    <template v-if="data">
      <p v-if="data.versionLimitReached" class="notice wait">本码已达 50 版上限，当前新事实不能继续生成版本。下方展示上一冻结快照，仍可核对或恢复原版回执。</p>
      <p class="notice">当前{{ data.frozen ? '已冻结' : '待冻结' }}版本 {{ data.manifest.version }}：出库 {{ formatDateTime(data.manifest.circulation.dispatchedAt) }} · 本版运输 {{ data.manifest.circulation.events.length }} 条 · 收货 {{ data.manifest.circulation.receiptOutcome === 'ACCEPTED' ? '实收' : data.manifest.circulation.receiptOutcome === 'REJECTED' ? '拒收' : '待逐件核对' }}。</p>
      <CirculationVersionView :preview="true" :versions="[{ version: data.manifest.version, manifest: data.manifest, manifestHash: data.manifestHash, createdAt: data.frozen ? data.versions[0]?.createdAt : undefined, chainStatus: data.frozen ? data.versions[0]?.chainStatus ?? 'NOT_SUBMITTED' : 'NOT_SUBMITTED' }]" />
      <form v-if="auth.canWrite.value" class="stack" @submit.prevent="submit"><label class="form-field">本轮提交范围<select :value="target" class="field-input" :disabled="busy" @change="choose(($event.target as HTMLSelectElement).value)"><option :value="data.manifestHash">当前预览版本 {{ data.manifest.version }}</option><option v-if="data.versions[0] && data.versions[0].manifestHash !== data.manifestHash" :value="data.versions[0].manifestHash">核对 / 恢复上一冻结版本 {{ data.versions[0].version }}</option></select></label><p class="digest">确认摘要 {{ target }}</p><label class="approval"><input v-model="approved" type="checkbox" :disabled="busy">已核对本轮所选证据版本及摘要，确认提交；不会自动办理出库或收货。</label><button class="action-button primary" :disabled="busy || !approved">{{ busy ? '正在提交与核对…' : '确认提交 / 恢复所选版本' }}</button></form>
      <p v-if="busy" role="status">正在先核对生产来源及历史事件，再处理本版交易；回执会持续刷新。</p>
      <h4 v-if="data.versions.length">冻结版本与回执</h4><CirculationVersionView :preview="true" :versions="data.versions" />
      <p v-for="version in data.versions.filter(v => v.errorCode)" :key="version.version" class="notice wait">版本 {{ version.version }}：{{ hints[version.errorCode!] || '请核对处理结果' }}</p>
    </template>
    <p v-if="notice" role="status" class="notice">{{ notice }}</p><p v-if="error" role="alert" class="notice bad">{{ error }}</p>
  </section>
</template>
<style scoped>
.flow-evidence { min-width: 0; border-top: 1px solid var(--glass-hairline); padding-top: 18px; }.code,.digest { overflow-wrap: anywhere; }.digest { font-size: var(--fs-xs); color: var(--slate); }.approval { display: flex; gap: 10px; align-items: start; line-height: 1.8; }.approval input { flex-shrink: 0; margin-top: 6px; }
</style>
