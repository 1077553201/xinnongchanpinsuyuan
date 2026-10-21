<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { ASSESSMENT_LABELS, handleEnvironmentCase, listEnvironmentCases } from '@/api/growing'
import type { Assessment } from '@/api/growing'
import { formatDateTime, metricMeta } from '@/types/api'
import { operationError, uncertainOperation } from '@/utils/growing'
const props = defineProps<{ cycleId: number }>()
const auth = useAuth(), cases = useAsyncResource(() => listEnvironmentCases(props.cycleId))
const selected = ref<Assessment | null>(null), busy = ref(false), uncertain = ref(false), error = ref(''), notice = ref('')
const draft = reactive({ action: '', observation: '' })
onMounted(cases.load)
function open(item: Assessment) { selected.value = item; error.value = ''; uncertain.value = false; Object.assign(draft, { action: '', observation: '' }) }
function close() { if (!busy.value) selected.value = null }
async function save() {
  if (!selected.value?.caseId || busy.value || uncertain.value) return
  busy.value = true; error.value = ''
  try {
    await handleEnvironmentCase(selected.value.caseId, draft.action, draft.observation)
    selected.value = null; notice.value = '处置情况已记录。是否恢复须查看后续采集，不会自动标为环境恢复。'; await cases.load()
  } catch (cause) { error.value = operationError(cause); uncertain.value = uncertainOperation(cause) }
  finally { busy.value = false }
}
</script>
<template>
  <GlassPanel padded="large" class="stack management">
    <div class="section-head"><h2 class="display">本轮环境异常与处置</h2><button class="action-button" :disabled="cases.loading.value" @click="cases.load">刷新异常</button></div>
    <p class="muted">按当时规则超出边界的正式样本生成异常，未处置优先，最多显示 100 条。“已记录处置”只说明有人采取措施，不代表环境已经恢复。</p>
    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <ResourceState :loading="cases.loading.value" :error="cases.error.value" :empty="!cases.data.value?.length" empty-text="暂无本批规则产生的超限记录；没有异常记录不等于数据完整或环境合格。" @retry="cases.load"><div class="table-scroll"><table class="data-table"><thead><tr><th>采集时间 / 设备</th><th>指标与超限</th><th>当时范围</th><th>处置状态</th><th>操作 / 措施</th></tr></thead><tbody><tr v-for="item in cases.data.value" :key="item.caseId"><td>{{ formatDateTime(item.reportedAt) }}<p class="mono muted">{{ item.deviceCode }}</p></td><td>{{ metricMeta(item.metric).label }} · {{ item.value }} {{ item.unit }}<p class="field-error">{{ ASSESSMENT_LABELS[item.result] }}</p></td><td>{{ item.minValue ?? '—' }} 至 {{ item.maxValue ?? '—' }} {{ metricMeta(item.metric).unit }}<p class="muted">版本 #{{ item.ruleId }}</p></td><td>{{ item.caseStatus === 'OPEN' ? '待记录处置' : '已记录处置' }}<p v-if="item.handledAt" class="muted">{{ item.handlerName }} · {{ formatDateTime(item.handledAt) }}</p></td><td><button v-if="item.caseStatus === 'OPEN' && auth.canWrite.value" class="text-button" @click="open(item)">记录处置</button><template v-else><p class="pre-wrap">{{ item.actionTaken || '等待负责人处理' }}</p><p v-if="item.observation" class="muted pre-wrap">{{ item.observation }}</p></template></td></tr></tbody></table></div></ResourceState>
    <AppDialog :open="selected !== null" title="记录异常处置" :busy="busy" @close="close"><form class="management" @submit.prevent="save"><fieldset :disabled="busy || uncertain"><p class="notice wait">{{ selected ? metricMeta(selected.metric).label : '' }} · {{ selected?.value }} {{ selected?.unit }}。请填写实际措施，不要把计划中的操作记为已执行。</p><label class="form-field">已采取的措施<textarea v-model.trim="draft.action" class="field-input" rows="3" required maxlength="2000" placeholder="例如 现场检查后开启通风，并记录检查范围"></textarea></label><label class="form-field">观察结果或后续复核安排（选填）<textarea v-model.trim="draft.observation" class="field-input" rows="3" maxlength="2000"></textarea></label></fieldset><p v-if="error" class="notice bad" role="alert">{{ error }}</p><div class="actions form-actions"><button class="action-button primary" type="submit" :disabled="busy || uncertain">{{ busy ? '正在保存…' : '确认记录处置' }}</button><button class="action-button" type="button" :disabled="busy" @click="close">关闭</button></div></form></AppDialog>
  </GlassPanel>
</template>
<style scoped>h2 { font-size: var(--fs-h2); }.pre-wrap { white-space: pre-wrap; }.form-actions, .form-field { margin-top: 20px; }</style>
