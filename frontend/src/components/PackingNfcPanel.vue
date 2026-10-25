<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { packingNfc, previewPackingNfc, submitPackingNfc, type PackingNfcDetail, type NfcPreviewInput } from '@/api/packingNfc'
import { useAuth } from '@/composables/useAuth'
import { operationError } from '@/utils/growing'
import { traceUrl } from '@/utils/items'
import { chainStatusLabel, formatDateTime } from '@/types/api'

const props = defineProps<{ itemId: number; baseUrl: string }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const auth = useAuth()
const data = shallowRef<PackingNfcDetail | null>(null)
const input = shallowRef<NfcPreviewInput | null>(null)
const uid = ref(''), reason = ref(''), approved = ref(false)
const busy = ref(false), loading = ref(false), error = ref(''), notice = ref('')
const binding = computed(() => data.value?.history.find(row => row.operation === 'BIND'))
const disable = computed(() => data.value?.history.find(row => row.operation === 'DISABLE'))
const links = computed(() => {
  if (!data.value?.uidHash) return null
  try {
    const canonical = traceUrl(data.value.traceCode, props.baseUrl)
    return { canonical, nfc: new URL(`/n/${data.value.uidHash}`, new URL(canonical).origin).href }
  } catch { return null }
})
watch([uid, reason], () => { input.value = null; approved.value = false })
let poll: ReturnType<typeof setInterval> | undefined
onBeforeUnmount(() => clearInterval(poll))
async function load() {
  loading.value = true; error.value = ''; input.value = null; approved.value = false
  try { data.value = await packingNfc(props.itemId) }
  catch (cause) { error.value = operationError(cause) }
  finally { loading.value = false }
}
onMounted(load)
async function preview(operation: 'BIND' | 'DISABLE') {
  if (busy.value || loading.value) return
  input.value = null; approved.value = false; error.value = ''; loading.value = true
  const body: NfcPreviewInput = { operation }
  if (operation === 'BIND' && !binding.value) body.uid = uid.value.trim()
  if (operation === 'DISABLE' && !disable.value) body.reason = reason.value.trim()
  try { data.value = await previewPackingNfc(props.itemId, body); input.value = body }
  catch (cause) { error.value = operationError(cause) }
  finally { loading.value = false }
}
async function submit() {
  if (busy.value || !approved.value || !input.value || !data.value?.payloadHash) return
  const body = { ...input.value, expectedHash: data.value.payloadHash }
  busy.value = true; emit('busy', true); error.value = ''; notice.value = ''
  poll = setInterval(async () => {
    try { const current = await packingNfc(props.itemId); if (busy.value && data.value) data.value = { ...data.value, history: current.history } }
    catch { /* 读取失败不代表交易失败，保留当前确认。 */ }
  }, 1500)
  try {
    data.value = await submitPackingNfc(props.itemId, body)
    input.value = null; approved.value = false
    notice.value = '本轮处理结束，请查看绑定和停用各自的回执。公众入口会重新读取链上身份核验。'
  } catch (cause) { error.value = operationError(cause) + ' 请刷新回执核对；恢复会先查链，不盲目重发。' }
  finally { clearInterval(poll); busy.value = false; emit('busy', false) }
}
const hints: Record<string, string> = {
  SOURCE_MISMATCH: '包装来源与链上身份不一致', HASH_MISMATCH: '标签、绑定或停用摘要不一致',
  OTHER_TAG_BOUND: '链上已有其他标签，停止覆盖', SOURCE_NOT_ACTIVE: '当前链状态不允许新操作',
  CHAIN_READ_UNAVAILABLE: '暂时无法读取链上记录', CHAIN_RESULT_UNKNOWN: '交易结果待核对',
  RECONCILE_REQUIRED: '原交易结果未知，已停止重发', CHAIN_RECORD_MISSING: '原记录暂未查到',
  CHAIN_DISABLED: '本次未启用链写入', TRANSACTION_FAILED: '交易失败，可核对后恢复',
}
</script>

<template>
  <section class="packing-nfc stack" aria-label="包装码 NFC 身份">
    <div class="section-head"><h3>本件包装的 NFC 身份</h3><button class="action-button" :disabled="busy || loading" @click="load">刷新 NFC 回执</button></div>
    <p class="code">{{ data?.traceCode || `包装码记录 #${itemId}` }}</p>
    <p class="muted">一件完整包装保留一次 UID 绑定和停用历史。普通 UID 和可复制的网址不代表防克隆；此页面登记关联，不读取或写入实体标签。</p>
    <template v-if="data">
      <p v-if="data.uidHash" class="digest">标签 UID 摘要 {{ data.uidHash }}</p>
      <template v-if="auth.canWrite.value">
        <form v-if="!binding" class="stack" @submit.prevent="preview('BIND')">
          <label>标签十六进制 UID<input v-model="uid" class="field-input" maxlength="64" placeholder="4、7 或 10 字节，可带冒号或空格" required :disabled="busy || loading"></label>
          <button class="action-button" :disabled="busy || loading">预览绑定范围</button>
        </form>
        <button v-else class="action-button" :disabled="busy || loading" @click="preview('BIND')">核对 / 恢复原绑定</button>
        <form v-if="binding?.chainStatus === 'CONFIRMED' && !disable" class="stack" @submit.prevent="preview('DISABLE')">
          <label>停用原因（仅机构内留档）<textarea v-model.trim="reason" class="field-input" rows="3" maxlength="500" required :disabled="busy || loading"></textarea></label>
          <button class="action-button" :disabled="busy || loading">预览停用范围</button>
        </form>
        <button v-if="disable" class="action-button" :disabled="busy || loading" @click="preview('DISABLE')">核对 / 恢复原停用</button>
      </template>
      <form v-if="input && data.payloadHash" class="stack" @submit.prevent="submit">
        <h4>{{ input.operation === 'BIND' ? '确认一次绑定' : '确认停用' }}</h4>
        <p class="digest">确认摘要 {{ data.payloadHash }}</p>
        <p class="digest">本次标签摘要 {{ data.payload?.uidHash }}<br>生产证据摘要 {{ data.payload?.productionManifestHash }}</p>
        <p v-if="input.operation === 'DISABLE'" class="notice wait">停用确认留档后，NFC 入口即停止自动进入产品页；链上结果另行核对。没有恢复或换绑入口。</p>
        <label class="approval"><input v-model="approved" type="checkbox" :disabled="busy">已核对本件包装、标签及操作摘要，确认办理{{ input.operation === 'BIND' ? '绑定' : '停用' }}。</label>
        <button class="action-button primary" :disabled="busy || !approved">{{ busy ? '正在核对与处理…' : '确认提交 / 恢复' }}</button>
      </form>
      <p v-if="busy" role="status">正在核对身份和交易回执；未知结果不会自动重发。</p>
      <article v-for="row in data.history" :key="row.operation" class="history stack">
        <h4>{{ row.operation === 'BIND' ? '原始绑定' : '停用留档' }} · {{ row.chainStatus === 'UNCERTAIN' ? '结果待核对' : chainStatusLabel(row.chainStatus) }}</h4>
        <p class="digest">操作摘要 {{ row.payloadHash }}</p><p class="muted">留档 {{ formatDateTime(row.recordedAt) }} · {{ row.txId ? `交易记录 #${row.txId}` : '尚无交易记录' }}</p>
        <p v-if="row.privateReason">内部停用原因：{{ row.privateReason }}</p><p v-if="row.errorCode" class="notice wait">{{ hints[row.errorCode] || '请核对处理结果' }}</p>
      </article>
      <div v-if="links" class="stack"><label>二维码对应的产品地址<input class="field-input" :value="links.canonical" readonly></label><label>NFC 标签应写入的查询地址<input class="field-input" :value="links.nfc" readonly></label><a :href="links.nfc" target="_blank" rel="noopener">检查 NFC 入口 ↗</a><p class="muted">使用标签写入工具将第二个地址写入 NDEF URL，并在实物贴标后用手机核对。入口通过身份与状态核验后，进入上方同一产品地址；写入网址本身不证明物理 UID。</p></div>
      <p v-if="data.uidHash && !links" class="notice bad">请先填写手机可以访问的有效前端地址。</p>
    </template>
    <p v-if="notice" role="status" class="notice">{{ notice }}</p><p v-if="error" role="alert" class="notice bad">{{ error }}</p>
  </section>
</template>
<style scoped>
.packing-nfc { min-width: 0; border-top: 1px solid var(--glass-hairline); padding-top: 20px; }.packing-nfc label { display: grid; gap: 8px; }.code,.digest { overflow-wrap: anywhere; }.digest { font-size: var(--fs-xs); color: var(--slate); }.approval { display: flex !important; align-items: start; gap: 10px; line-height: 1.8; }.approval input { width: auto; flex-shrink: 0; margin-top: 6px; }.history { padding: 14px; border: 1px solid var(--glass-hairline); border-radius: var(--radius-md); overflow-wrap: anywhere; }
</style>
