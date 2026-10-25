<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import QRCode from 'qrcode'
import { packingCodes, issuePackingCodes } from '@/api/packingCodes'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import ResourceState from './ResourceState.vue'
import { newOperationKey, operationError } from '@/utils/growing'
import { traceUrl } from '@/utils/items'
import { ApiError } from '@/api/http'
import PackingEvidencePanel from './PackingEvidencePanel.vue'
import { chainStatusLabel } from '@/types/api'

const props = defineProps<{ packingId: number }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const auth = useAuth(), page = ref(1), resource = useAsyncResource(() => packingCodes(props.packingId, page.value))
const count = ref(1), busy = ref(false), error = ref(''), notice = ref(''), confirmed = ref(false), base = ref(window.location.origin)
const labels = ref<{ code: string; url: string; qr: string; status: string }[]>([]), labelError = ref(''), labelLoading = ref(false)
const remaining = computed(() => resource.data.value?.remaining ?? 0)
const evidenceBusy = ref(false), blocked = computed(() => busy.value || evidenceBusy.value)
let key = newOperationKey(), submittedCount: number | undefined, generation = 0
watch(blocked, value => emit('busy', value))
async function load() { await resource.load(); await prepare() }
async function refreshStatus() { resource.data.value = await packingCodes(props.packingId, page.value); await prepare() }
async function prepare() {
  const revision = ++generation
  labelLoading.value = true; labelError.value = ''; labels.value = []
  try {
    const result = await Promise.all((resource.data.value?.rows ?? []).map(async item => {
      const url = traceUrl(item.traceCodePlain, base.value)
      return { code: item.traceCodePlain, url, status: chainStatusLabel(item.chainStatus), qr: await QRCode.toDataURL(url, { width: 180, margin: 2, errorCorrectionLevel: 'M' }) }
    }))
    if (revision === generation) labels.value = result
  } catch (cause) { if (revision === generation) labelError.value = operationError(cause) }
  finally { if (revision === generation) labelLoading.value = false }
}
async function generate() {
  if (blocked.value || !confirmed.value || !Number.isSafeInteger(count.value) || count.value < 1 || count.value > 100) return
  if (submittedCount !== undefined && count.value !== submittedCount) { error.value = '上次结果尚未确认，请先用原数量重试，核对后再办理新的一次赋码。'; return }
  busy.value = true; error.value = ''; submittedCount = count.value
  try {
    const result = await issuePackingCodes(props.packingId, count.value, key)
    notice.value = `本次已生成 ${result.items.length} 个码，一码对应一件完整包装，尚未提交链上存证。`
    key = newOperationKey(); submittedCount = undefined; confirmed.value = false; page.value = 1; await load()
  } catch (cause) { if (cause instanceof ApiError && ['400','403','409'].includes(cause.code)) { submittedCount = undefined; key = newOperationKey() }; error.value = operationError(cause) + ' 如遇网络中断，请保持原数量重试，同一操作不会重复生成。' }
  finally { busy.value = false }
}
async function turn(next: number) { page.value = next; await load() }
function printLabels() { window.print() }
onMounted(load)
</script>

<template>
  <div class="packing-codes">
    <ResourceState :loading="resource.loading.value" :error="resource.error.value" @retry="load">
      <template v-if="resource.data.value">
        <p class="muted">{{ resource.data.value.source.plotName }} → {{ resource.data.value.source.cycleCode }} → {{ resource.data.value.source.harvestCode }} → {{ resource.data.value.source.packingCode }}</p>
        <p class="unit-rule"><strong>一码 = 1 {{ resource.data.value.packing.packageUnit }}完整包装</strong> · 单件净重 {{ resource.data.value.packing.netWeightKg }} kg</p>
        <p>实际包装 {{ resource.data.value.packing.packageCount }} 件 · 已赋码 {{ resource.data.value.generatedCount }} 件 · 尚可赋码 {{ remaining }} 件</p>
        <p class="muted">赋码只建立来源查询身份，不扣库存，不代表已入库、销售或质检合格。生成后可独立预览并确认提交生产证据；NFC 与逐件流转关联后续接入。</p>
        <form v-if="auth.canWrite.value && (remaining > 0 || submittedCount !== undefined)" class="issue-form" @submit.prevent="generate">
          <label>本次赋码件数<input v-model.number="count" type="number" min="1" max="100" step="1" required :disabled="blocked || submittedCount !== undefined" /></label>
          <label class="confirm-rule"><input v-model="confirmed" type="checkbox" :disabled="blocked" />已核对上述包装规格，按一件完整包装一码办理</label>
          <button class="action-button" :disabled="blocked || !confirmed">{{ busy ? '正在生成…' : submittedCount !== undefined ? '按原操作重试' : '确认生成包装码' }}</button>
        </form>
        <p v-if="notice" role="status" class="notice">{{ notice }}</p><p v-if="error" role="alert" class="error">{{ error }}</p>
        <PackingEvidencePanel v-if="resource.data.value.generatedCount" :packing-id="packingId" :item-ids="resource.data.value.rows.map(item => item.id)" :disabled="busy" @busy="evidenceBusy = $event" @updated="refreshStatus" />
        <div class="label-tools"><label>标签访问地址<input v-model="base" type="url" aria-label="标签访问地址" /></label><button class="action-button" :disabled="blocked || labelLoading" @click="prepare">更新二维码预览</button><button class="action-button" :disabled="!labels.length || labelLoading || !!labelError" @click="printLabels">打印本页标签</button></div>
        <p class="muted">手机访问时填写手机可达的前端地址；修改预览地址不改变已保存的码与来源。</p>
        <p v-if="labelError" role="alert" class="error">{{ labelError }}</p><p v-if="labelLoading" role="status">正在生成二维码预览…</p>
        <div class="code-grid"><article v-for="label in labels" :key="label.code" class="code-label"><img :src="label.qr" :alt="`包装码 ${label.code} 的二维码`" width="140" height="140" /><p>{{ label.code }}</p><p>{{ label.status }}</p><a :href="label.url" target="_blank" rel="noopener noreferrer">打开来源查询 ↗</a><input :value="label.url" readonly :aria-label="`${label.code}查询链接`" /></article></div>
        <p v-if="!resource.data.value.total" class="muted">本批尚未赋码。</p>
        <div class="pagination"><button class="action-button" :disabled="blocked || resource.data.value.page <= 1" @click="turn(resource.data.value.page - 1)">上一页</button><span>第 {{ resource.data.value.page }} 页 · 共 {{ resource.data.value.total }} 个码</span><button class="action-button" :disabled="blocked || resource.data.value.page * 20 >= resource.data.value.total" @click="turn(resource.data.value.page + 1)">下一页</button></div>
        <Teleport to="body"><div class="packing-code-labels"><article v-for="label in labels" :key="label.code"><img :src="label.qr" alt="包装来源二维码" /><strong>{{ resource.data.value.source.productName }} · {{ resource.data.value.source.variety }}</strong><p>1 {{ resource.data.value.packing.packageUnit }} · {{ resource.data.value.packing.netWeightKg }} kg</p><p>{{ label.code }}</p><small>来源查询 · 存证状态请扫码核对</small></article></div></Teleport>
      </template>
    </ResourceState>
  </div>
</template>

<style scoped>
.packing-codes { display: grid; gap: 14px; line-height: 1.8; font-size: var(--fs-sm); }.packing-codes p { overflow-wrap: anywhere; margin-bottom: 12px; }.unit-rule { padding: 12px; background: var(--field-tint); border-radius: 12px; }.issue-form,.label-tools { display: flex; gap: 12px; flex-wrap: wrap; align-items: end; margin: 18px 0; }label { display: grid; gap: 6px; flex: 1 1 180px; }.confirm-rule { display: flex; align-items: start; flex-basis: 100%; }.confirm-rule input { width: auto; flex-shrink: 0; margin-top: 7px; }input { width: 100%; min-width: 0; padding: 10px; background: var(--glass-fill); border: 1px solid var(--glass-stroke); border-radius: 10px; }.code-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(210px,1fr)); gap: 12px; }.code-label { border: 1px solid var(--glass-stroke); border-radius: 14px; padding: 14px; text-align: center; min-width: 0; }.code-label img { max-width: 100%; height: auto; }.code-label p { font-size: 11px; }.code-label a { color: var(--field); }.code-label input { margin-top: 10px; font-size: 11px; }.pagination { display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; align-items: center; }.error { color: var(--seal); }.notice { color: var(--field); }.packing-code-labels { display: none; }@media(max-width:480px) { .code-grid { grid-template-columns: minmax(0,1fr); } }
</style>
<style>
@media print {
  html:has(.packing-code-labels),body:has(.packing-code-labels) { background: white !important; background-image: none !important; min-height: 0 !important; height: auto !important; margin: 0; }
  body:has(.packing-code-labels) > *:not(.packing-code-labels) { display: none !important; }
  .packing-code-labels { display: grid !important; position: static; width: 100%; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 10mm; background: white; color: black; }
  .packing-code-labels article { padding: 5mm; border: 1px solid #bbb; text-align: center; break-inside: avoid; font-size: 10pt; overflow-wrap: anywhere; }.packing-code-labels img { width: 35mm; display: block; margin: 0 auto; }.packing-code-labels p { margin: 2mm 0; }
}
</style>
