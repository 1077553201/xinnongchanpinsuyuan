<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import GlassPanel from './GlassPanel.vue'
import ResourceState from './ResourceState.vue'
import StatusChip from './StatusChip.vue'
import AppDialog from './AppDialog.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { getTransaction, listTransactions } from '@/api/chain'
import { BUSINESS_LABELS, businessPath } from '@/utils/transactions'
import { formatDateTime, shortHash } from '@/types/api'

const businessType = ref(''), businessId = ref(''), chainStatus = ref(''), limit = ref('100')
const error = ref(''), open = ref(false), selectedId = ref(0)
const transactions = useAsyncResource(() => listTransactions({ businessType: businessType.value || undefined,
  businessId: businessId.value ? Number(businessId.value) : undefined, chainStatus: chainStatus.value || undefined, limit: Number(limit.value) }))
const detail = useAsyncResource(() => getTransaction(selectedId.value))
async function load() {
  if (businessId.value && (!Number.isSafeInteger(Number(businessId.value)) || Number(businessId.value) < 1)) { error.value = '业务 ID 须为正整数。'; return }
  error.value = ''; await transactions.load()
}
async function inspect(id: number) { selectedId.value = id; open.value = true; await detail.load() }
onMounted(load)
</script>
<template>
  <div class="management">
    <GlassPanel class="stack"><div class="toolbar"><h2 class="display">业务存证交易</h2><button class="action-button" type="button" :disabled="transactions.loading.value" @click="load">刷新交易</button></div>
      <form class="filters" @submit.prevent="load"><label>业务类型<select v-model="businessType" class="field-input"><option value="">全部类型</option><option v-for="(label,key) in BUSINESS_LABELS" :key="key" :value="key">{{ label }}</option></select></label><label>业务 ID<input v-model="businessId" type="number" min="1" step="1" class="field-input" placeholder="可选"></label><label>上链状态<select v-model="chainStatus" class="field-input"><option value="">全部状态</option><option value="CONFIRMED">上链已确认</option><option value="PENDING">等待上链</option><option value="FAILED">上链失败</option><option value="CHAIN_DISABLED">未上链</option></select></label><label>最多显示<select v-model="limit" class="field-input"><option value="50">50 条</option><option value="100">100 条</option><option value="200">200 条</option><option value="500">500 条</option></select></label><button class="action-button primary" type="submit" :disabled="transactions.loading.value">查询交易</button></form>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
      <ResourceState :loading="transactions.loading.value" :error="transactions.error.value" :empty="!transactions.data.value?.length" empty-text="当前条件下暂无交易。" @retry="load"><div class="table-scroll" tabindex="0" aria-label="交易列表，可横向滚动"><table class="data-table"><caption class="sr-only">最近业务存证交易</caption><thead><tr><th scope="col">业务 / ID</th><th scope="col">状态</th><th scope="col">交易哈希</th><th scope="col">区块</th><th scope="col">提交时间</th><th scope="col">操作</th></tr></thead><tbody><tr v-for="tx in transactions.data.value" :key="tx.id"><td>{{ BUSINESS_LABELS[tx.businessType] || tx.businessType }}<p class="mono muted">#{{ tx.businessId }}</p></td><td><StatusChip :status="tx.chainStatus" compact /></td><td class="mono" :title="tx.txHash">{{ shortHash(tx.txHash) }}</td><td class="mono">{{ tx.blockNumber ?? '—' }}</td><td>{{ formatDateTime(tx.submittedAt) }}</td><td><div class="actions"><button class="text-button" type="button" @click="inspect(tx.id)">交易详情</button><RouterLink class="text-button" :to="businessPath(tx.businessType,tx.businessId)">关联业务</RouterLink></div></td></tr></tbody></table></div></ResourceState>
      <p class="muted tiny">返回最近最多 {{ limit }} 条匹配记录；不代表全库交易总数。当前不提供自动重试或重复发送交易。</p>
    </GlassPanel>
    <AppDialog :open="open" title="存证交易详情" @close="open = false"><ResourceState :loading="detail.loading.value" :error="detail.error.value" @retry="detail.load"><template v-if="detail.data.value"><StatusChip :status="detail.data.value.chainStatus" /><dl class="detail-grid"><div v-for="[label,value] in [
      ['业务',BUSINESS_LABELS[detail.data.value.businessType] || detail.data.value.businessType],['业务 ID',detail.data.value.businessId],
      ['交易哈希',detail.data.value.txHash],['区块高度',detail.data.value.blockNumber],['回执状态',detail.data.value.receiptStatus],['合约地址',detail.data.value.contractAddress],
      ['摘要',detail.data.value.payloadHash],['调用账户',detail.data.value.chainOperator],['提交时间',formatDateTime(detail.data.value.submittedAt)],['确认时间',formatDateTime(detail.data.value.confirmedAt)],
    ]" :key="label"><dt>{{ label }}</dt><dd>{{ value ?? '—' }}</dd></div></dl><p v-if="detail.data.value.chainStatus === 'FAILED'" class="notice bad">交易执行失败。请核查节点、调用权限及关联业务，勿直接重复发送。</p></template></ResourceState></AppDialog>
  </div>
</template>
<style scoped>h2 { font-size: var(--fs-h2); }</style>
