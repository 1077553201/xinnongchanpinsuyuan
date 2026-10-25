<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { inspectReceipt, recordReceipt, type ReceiptCheck } from '@/api/transactionReceipt'
import type { ReconciliationRow } from '@/api/evidenceReconciliation'
import { useAuth } from '@/composables/useAuth'
import { receiptOutcome, canRecordReceipt } from '@/utils/receiptVerification'
import { operationError } from '@/utils/growing'
import { formatDateTime, chainStatusLabel } from '@/types/api'
const props = defineProps<{ record: ReconciliationRow; disabled?: boolean }>()
const emit = defineEmits<{ busy: [value: boolean]; recorded: [] }>()
const auth = useAuth(), data = shallowRef<ReceiptCheck | null>(null)
const busy = ref(false), approved = ref(false), error = ref(''), notice = ref('')
const outcome = computed(() => receiptOutcome(data.value?.outcome || ''))
const recordable = computed(() => data.value && auth.canWrite.value && !props.disabled && canRecordReceipt(data.value))
async function inspect() {
  if (busy.value || props.disabled) return
  busy.value = true; emit('busy', true); approved.value = false; data.value = null; error.value = ''; notice.value = ''
  try { data.value = await inspectReceipt(props.record.kind, props.record.recordId) }
  catch (cause) { error.value = operationError(cause) }
  finally { busy.value = false; emit('busy', false) }
}
async function save() {
  if (busy.value || !approved.value || !recordable.value || !data.value) return
  busy.value = true; emit('busy', true); approved.value = false; error.value = ''; notice.value = ''
  let recorded = false
  try {
    data.value = await recordReceipt(props.record.kind, props.record.recordId, data.value.checkHash)
    recorded = true; notice.value = '原回执核对结果已留档；没有发送链交易，业务证据状态仍需分别核对。'
  } catch (cause) { data.value = null; error.value = operationError(cause) + ' 请重新核对原回执。' }
  finally { busy.value = false; emit('busy', false) }
  if (recorded) emit('recorded')
}
</script>
<template>
  <section class="receipt-panel stack" aria-label="原始交易回执核查">
    <h3>原始交易回执</h3><p class="muted">按原提交已经保存的交易哈希查询，不手填或猜测哈希。业务证据存在与原交易成功分别核对。</p>
    <button class="action-button" :disabled="busy || disabled" @click="inspect">{{ busy ? '正在核对原回执…' : '只读核对原始交易回执' }}</button>
    <p v-if="busy" role="status">正在读取实际回执，核对目标合约、原调用及事件；不会重发交易。</p>
    <template v-if="data"><h4>{{ outcome.label }}</h4><p>{{ outcome.detail }}</p>
      <p class="digest">原交易记录 {{ data.transactionId ? `#${data.transactionId}` : '无' }} · 原存状态 {{ chainStatusLabel(data.storedChainStatus) }}<br>交易哈希 {{ data.txHash || '缺失，保持原记录' }}</p>
      <template v-if="data.receipt.found"><p>回执状态 {{ data.receipt.receiptStatus }} · 区块 {{ data.receipt.blockNumber }} · {{ data.receipt.inputMatched ? '原调用内容一致' : '原调用内容未核对通过' }}</p><p class="digest">目标合约 {{ data.receipt.contractAddress }}<br>原提交人 {{ data.receipt.operator }}<br>调用内容摘要 {{ data.receipt.inputHash }}</p><p class="muted">读取到目标事件 {{ data.receipt.events?.length ?? 0 }} 条；失败回执不据此推测业务已执行。</p></template>
      <p class="muted">本次核对 {{ formatDateTime(data.checkedAt) }}<template v-if="data.verifiedAt"><br>已核对留档 {{ formatDateTime(data.verifiedAt) }}</template> · 这些是本地核对时间，不是区块产生时间。</p>
      <p v-if="data.processing" class="notice wait">原业务仍在处理，请稍后核对，暂不能留档。</p><p v-if="data.alreadyRecorded" class="notice">与已留档核对结果一致，无需重复保存。</p>
      <form v-if="recordable" class="stack" @submit.prevent="save"><label class="approval"><input v-model="approved" type="checkbox" :disabled="busy">已核对原交易及本次实际回执，仅保存核对结果，不重发、不改变原业务事实。</label><button class="action-button primary" :disabled="busy || !approved">确认留档原回执结果</button></form>
    </template><p v-if="notice" role="status" class="notice">{{ notice }}</p><p v-if="error" role="alert" class="notice bad">{{ error }}</p>
  </section>
</template>
<style scoped>
.receipt-panel { min-width: 0; border-top: 1px solid var(--glass-hairline); padding-top: 18px; margin-top: 12px; line-height: 1.8; }.digest { overflow-wrap: anywhere; font-size: var(--fs-xs); color: var(--slate); }.approval { display: flex; gap: 10px; align-items: start; }.approval input { flex-shrink: 0; margin-top: 6px; }
</style>
