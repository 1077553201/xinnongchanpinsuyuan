<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { dispatchItems, linkDispatchItems, receiveDispatchItems, type ReceiptOutcome } from '@/api/itemCirculation'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { useAuth } from '@/composables/useAuth'
import { newOperationKey, operationError } from '@/utils/growing'
import { formatDateTime } from '@/types/api'
import ResourceState from './ResourceState.vue'
import CirculationEvidencePanel from './CirculationEvidencePanel.vue'

const props = defineProps<{ dispatchId: number }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const auth = useAuth(), mode = ref('AVAILABLE'), search = ref(''), term = ref(''), page = ref(1)
const data = useAsyncResource(() => dispatchItems(props.dispatchId, term.value, mode.value, page.value))
const selected = ref<number[]>([]), approved = ref(false), busy = ref(false), error = ref(''), notice = ref('')
const proofBusy = ref(false), proof = ref<{ id: number; code: string } | null>(null)
const pending = ref<{ itemIds: number[]; codes: string[]; outcome?: ReceiptOutcome; key: string } | null>(null)
const locked = computed(() => busy.value || proofBusy.value || pending.value !== null)
watch(() => busy.value || proofBusy.value, value => emit('busy', value))
const resultLabel = (outcome?: ReceiptOutcome) => outcome === 'ACCEPTED' ? '实收' : outcome === 'REJECTED' ? '拒收' : '待逐件核对'
onMounted(() => { void data.load() })
async function load() { selected.value = []; await data.load() }
function filter() { term.value = search.value.trim(); page.value = 1; void load() }
function confirm(outcome?: ReceiptOutcome) {
  if (!selected.value.length || locked.value) return
  pending.value = { itemIds: [...selected.value], codes: (data.data.value?.rows ?? []).filter(row => selected.value.includes(row.itemId)).map(row => row.traceCode), outcome, key: newOperationKey() }
  approved.value = false; error.value = ''; notice.value = ''
}
async function save() {
  const draft = pending.value
  if (!draft || busy.value || !approved.value) return
  busy.value = true; error.value = ''
  try {
    if (draft.outcome) await receiveDispatchItems(props.dispatchId, draft.itemIds, draft.outcome, draft.key)
    else await linkDispatchItems(props.dispatchId, draft.itemIds, draft.key)
    notice.value = draft.outcome ? '逐件收货结果已留档，未自动退库或退款。' : '所选码已关联本笔实际出库，库存没有重复扣减。'
    pending.value = null; approved.value = false; await load()
  } catch (cause) { error.value = operationError(cause) + ' 可使用相同确认内容重试；重新选择前请刷新核对记录。' }
  finally { busy.value = false }
}
function cancel() { pending.value = null; approved.value = false; error.value = ''; void load() }
</script>

<template>
  <section class="item-circulation management stack" aria-label="逐件出库与收货">
    <div class="section-head"><h3 class="display">逐件出库与收货</h3><button class="action-button" :disabled="locked || data.loading.value" @click="load">刷新包装码</button></div>
    <p class="muted">核对实物标签后，将具体包装码关联到本笔出库。记录确认后保留原始关联；换货、退货和更正流程另行办理。</p>
    <form class="filters" @submit.prevent="filter"><label>查找包装码<input v-model.trim="search" class="field-input" maxlength="128" placeholder="扫描或输入完整包装码" :disabled="locked"></label><label>查看范围<select v-model="mode" class="field-input" :disabled="locked" @change="page = 1; load()"><option value="AVAILABLE">同来源尚未关联的码</option><option value="ASSIGNED">本笔出库已关联的码</option></select></label><button class="action-button" :disabled="locked || data.loading.value">查询</button></form>
    <ResourceState :loading="data.loading.value" :error="data.error.value" @retry="load">
      <template v-if="data.data.value">
        <p class="notice">{{ data.data.value.packingCode }} · 本笔实际出库 {{ data.data.value.dispatch.packageCount }} {{ data.data.value.dispatch.packageUnit }}，已关联 {{ data.data.value.linkedCount }} 件。逐件实收 {{ data.data.value.acceptedCount }} 件 / 拒收 {{ data.data.value.rejectedCount }} 件。</p>
        <p v-if="data.data.value.receipt" class="muted">运输单收货汇总：实收 {{ data.data.value.receipt.acceptedCount }} 件 / 拒收 {{ data.data.value.receipt.rejectedCount }} 件（{{ formatDateTime(data.data.value.receipt.occurredAt) }}）。请按实物分别勾选，下方结果不能超过此汇总。</p>
        <p v-else-if="mode === 'ASSIGNED'" class="muted">运输单尚未登记收货汇总，暂不能确认逐件实收或拒收。</p>
        <p v-if="!data.data.value.rows.length" class="muted">暂无匹配的包装码。尚未赋码时，请先到采收包装页面为完整包装赋码。</p>
        <div v-else class="code-list"><div v-for="row in data.data.value.rows" :key="row.itemId" class="code-row"><label class="code-choice"><input v-if="auth.canWrite.value" v-model="selected" type="checkbox" :value="row.itemId" :disabled="locked || !!row.receiptOutcome"><span><span class="mono">{{ row.traceCode }}</span><small>{{ row.dispatchId ? resultLabel(row.receiptOutcome) : '尚未关联销售出库' }}<template v-if="row.linkedAt"> · 关联留档 {{ formatDateTime(row.linkedAt) }}</template><template v-if="row.receiptAt"> · 收货核对留档 {{ formatDateTime(row.receiptAt) }}</template></small></span></label><RouterLink v-if="row.receiptOutcome === 'REJECTED' && !locked" :to="{ path: '/admin/rejections', query: { dispatchId, itemId: row.itemId } }" class="text-button">办理 / 查看拒收处置 →</RouterLink><button v-if="row.dispatchId === dispatchId" class="text-button" type="button" :disabled="locked" @click.prevent="proof = { id: row.itemId, code: row.traceCode }">查看 / 提交流转存证</button></div></div>
        <div class="section-head"><p class="muted">共 {{ data.data.value.total }} 个 · 第 {{ data.data.value.page }} 页 · 已选 {{ selected.length }} 个</p><div class="actions"><button class="action-button" :disabled="locked || data.data.value.page <= 1" @click="page = data.data.value.page - 1; load()">上一页</button><button class="action-button" :disabled="locked || data.data.value.page * 20 >= data.data.value.total" @click="page = data.data.value.page + 1; load()">下一页</button></div></div>
        <div v-if="auth.canWrite.value" class="actions"><button v-if="mode === 'AVAILABLE'" class="action-button primary" :disabled="locked || !selected.length" @click="confirm()">关联所选码到本笔出库</button><template v-else><button class="action-button" :disabled="locked || !selected.length || !data.data.value.receipt" @click="confirm('ACCEPTED')">核对所选码为实收</button><button class="action-button" :disabled="locked || !selected.length || !data.data.value.receipt" @click="confirm('REJECTED')">核对所选码为拒收</button></template></div>
      </template>
    </ResourceState>
    <form v-if="pending" class="confirmation stack" @submit.prevent="save"><strong>{{ pending.outcome ? `确认逐件${resultLabel(pending.outcome)}` : '确认关联实际销售出库' }} · {{ pending.itemIds.length }} 件</strong><p v-for="code in pending.codes" :key="code" class="mono code">{{ code }}</p><label class="approval"><input v-model="approved" type="checkbox" :disabled="busy">我已核对实物标签与本笔出库、收货记录，以上内容准确。</label><div class="actions"><button class="action-button primary" :disabled="busy || !approved">{{ busy ? '正在留档…' : '确认保存 / 同内容重试' }}</button><button type="button" class="action-button" :disabled="busy" @click="cancel">返回并刷新核对</button></div></form>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p><p v-if="error" class="notice bad" role="alert">{{ error }}</p>
    <CirculationEvidencePanel v-if="proof" :key="proof.id" :item-id="proof.id" :trace-code="proof.code" @busy="proofBusy = $event" />
    <p class="muted small">逐件流转业务留档与链上存证分别办理；请在公众查询页核对各版实时状态。后台收货录入不等于消费者身份验证。</p>
  </section>
</template>

<style scoped>
.item-circulation { min-width: 0; }.code-list { display: grid; gap: 8px; }.code-choice { display: flex; gap: 12px; align-items: start; min-width: 0; }.code-row { display: grid; align-items: start; gap: 12px; padding: 12px; border: 1px solid var(--glass-hairline); border-radius: var(--radius-md); }.code-row input,.approval input { flex-shrink: 0; margin-top: 4px; }.code-row span,.code { min-width: 0; overflow-wrap: anywhere; }.code-row small { display: block; color: var(--slate); margin-top: 6px; line-height: 1.8; }.confirmation { padding: 16px; border: 1px solid var(--glass-hairline); border-radius: var(--radius-md); }.approval { display: flex; gap: 10px; align-items: start; line-height: 1.8; }
</style>
