<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { previewPackingEvidence, submitPackingEvidence } from '@/api/packingEvidence'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { useAuth } from '@/composables/useAuth'
import { operationError } from '@/utils/growing'
import { chainStatusLabel } from '@/types/api'
import ProductionEvidencePanel from './ProductionEvidencePanel.vue'
const props = defineProps<{ packingId: number; itemIds: number[]; disabled: boolean }>()
const emit = defineEmits<{ busy: [value: boolean]; updated: [] }>()
const resource = useAsyncResource(() => previewPackingEvidence(props.packingId)), auth = useAuth()
const busy = ref(false), approved = ref(false), error = ref(''), notice = ref('')
let poll: ReturnType<typeof setInterval> | undefined
onBeforeUnmount(() => clearInterval(poll))
watch(busy, value => emit('busy', value))
watch(() => props.itemIds.join(','), () => { approved.value = false })
async function preview() { error.value = ''; approved.value = false; await resource.load() }
async function submit() {
  if (busy.value || props.disabled || !approved.value || !resource.data.value || !props.itemIds.length) return
  busy.value = true; error.value = ''; notice.value = ''
  poll = setInterval(async () => { try { const progress = await previewPackingEvidence(props.packingId); if (busy.value) resource.data.value = progress } catch { /* 原请求保留，短暂刷新失败不改变交易状态。 */ } }, 1500)
  try { resource.data.value = await submitPackingEvidence(props.packingId, resource.data.value.manifestHash, props.itemIds); approved.value = false; notice.value = '本轮处理结束，请逐项核对回执；失败、未提交和待核对不代表成功。'; emit('updated') }
  catch (cause) { error.value = operationError(cause) + ' 可刷新回执核对，结果不确定时不会盲目重发链交易。' }
  finally { clearInterval(poll); busy.value = false }
}
function state(status: string) { return status === 'UNCERTAIN' ? '结果待核对' : chainStatusLabel(status) }
const errors: Record<string,string> = { CHAIN_READ_UNAVAILABLE: '暂时无法读取链上记录，请稍后核对', SOURCE_MISMATCH: '链上所属批次与来源不同，已停止提交', HASH_MISMATCH: '链上摘要不一致，已停止提交', RECONCILE_REQUIRED: '上次结果未知，需先核对，未重复发送', CHAIN_RECORD_MISSING: '已确认记录目前未能查到，请核查节点', CHAIN_RESULT_UNKNOWN: '交易结果尚不确定，请刷新回执', CHAIN_DISABLED: '尚未启用本次链写入', TRANSACTION_FAILED: '链交易未成功，可核对后恢复' }
</script>
<template>
  <section class="packing-evidence-control" aria-label="生产证据存证">
    <button class="action-button" :disabled="disabled || busy || resource.loading.value" @click="preview">{{ resource.loading.value ? '正在读取…' : '预览生产证据 / 刷新存证回执' }}</button>
    <p v-if="resource.error.value" role="alert" class="error">{{ resource.error.value }}</p>
    <template v-if="resource.data.value">
      <ProductionEvidencePanel :preview="true" :evidence="{ ...resource.data.value.manifest, manifestHash: resource.data.value.manifestHash, capturedAt: resource.data.value.createdAt }" />
      <p>{{ resource.data.value.frozen ? '此生产证据版本已冻结，后续补录不会覆盖本版。' : '尚未冻结。提交时会核对当前资料是否与本预览一致，变化后需要重新确认。' }}</p>
      <form v-if="auth.canWrite.value" @submit.prevent="submit"><label><input v-model="approved" type="checkbox" :disabled="disabled || busy" />已核对以上证据版本，提交当前页 {{ itemIds.length }} 个码的存证；仅上链摘要，不自动办理销售、发货或签收。</label><button class="action-button" :disabled="disabled || busy || !approved || !itemIds.length">{{ busy ? '正在提交与核对…' : '确认提交 / 恢复本页存证' }}</button></form>
      <p v-if="busy" role="status">正在逐项处理批次及包装码，完成后返回回执；重复操作会先读取链上摘要核对。</p>
      <p v-if="notice" role="status">{{ notice }}</p><p v-if="error" role="alert" class="error">{{ error }}</p>
      <div v-if="resource.data.value.jobs.length" class="job-table"><table><caption>最近 100 项存证处理</caption><thead><tr><th>对象</th><th>状态</th><th>回执 / 提示</th></tr></thead><tbody><tr v-for="job in resource.data.value.jobs" :key="job.id"><td>{{ job.businessType === 'BATCH_REGISTER' ? '包装批次' : '包装码' }} #{{ job.businessId }}</td><td>{{ state(job.chainStatus) }}</td><td>{{ job.txId ? `交易记录 #${job.txId}` : '尚无交易记录' }}<p v-if="job.errorCode">{{ errors[job.errorCode] || '请核对处理状态' }}</p></td></tr></tbody></table></div>
    </template>
  </section>
</template>
<style scoped>
.packing-evidence-control { margin: 20px 0; padding-top: 18px; border-top: 1px solid var(--glass-hairline); display: grid; gap: 14px; min-width: 0; }.packing-evidence-control form { display: grid; gap: 12px; }.packing-evidence-control label { display: flex; align-items: start; gap: 8px; line-height: 1.8; }.packing-evidence-control input { margin-top: 6px; }.error { color: var(--seal); }.job-table { overflow-x: auto; max-width: 100%; }table { width: 100%; font-size: var(--fs-xs); }caption { text-align: left; padding-bottom: 12px; }th,td { padding: 10px; border-bottom: 1px solid var(--glass-hairline); text-align: left; overflow-wrap: anywhere; }th { color: var(--slate); font-weight: 500; }
</style>
