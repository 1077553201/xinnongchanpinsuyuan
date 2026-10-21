<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import AppDialog from '@/components/AppDialog.vue'
import { useAuth } from '@/composables/useAuth'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { ASSESSMENT_LABELS, COVERAGE_LABELS, configureRule, environmentOverview, retireRule } from '@/api/growing'
import type { GrowingRule } from '@/api/growing'
import type { PlantingCycle } from '@/api/production'
import { METRIC_META, formatDateTime, metricMeta } from '@/types/api'
import { newOperationKey, operationError, uncertainOperation } from '@/utils/growing'
const props = defineProps<{ cycle: PlantingCycle }>()
const auth = useAuth(), resource = useAsyncResource(() => environmentOverview(props.cycle.id))
const currentRules = computed(() => (resource.data.value?.rules ?? []).filter(rule => !rule.effectiveTo))
const mode = ref<'rule' | 'retire' | null>(null), selected = ref<GrowingRule | null>(null)
const busy = ref(false), uncertain = ref(false), error = ref(''), notice = ref('')
const draft = reactive({ metric: 'temperature', min: '', max: '', interval: '', basis: '', notes: '', reason: '', requestKey: '' })
onMounted(resource.load)
function open(rule?: GrowingRule) {
  mode.value = rule ? 'retire' : 'rule'; selected.value = rule ?? null; error.value = ''; uncertain.value = false
  Object.assign(draft, { metric: 'temperature', min: '', max: '', interval: '', basis: '', notes: '', reason: '', requestKey: newOperationKey() })
}
function close() { if (!busy.value) mode.value = null }
async function save() {
  if (busy.value || uncertain.value) return
  if (mode.value === 'rule' && draft.min === '' && draft.max === '') { error.value = '至少填写一个范围边界。'; return }
  busy.value = true; error.value = ''
  try {
    if (mode.value === 'retire' && selected.value) { await retireRule(selected.value.id, draft.reason); notice.value = '规则已停止沿用，旧版本及历史判断保留。' }
    else {
      await configureRule(props.cycle.id, { metric: draft.metric, minValue: draft.min === '' ? undefined : Number(draft.min), maxValue: draft.max === '' ? undefined : Number(draft.max), expectedIntervalMinutes: Number(draft.interval), basis: draft.basis, notes: draft.notes, requestKey: draft.requestKey })
      notice.value = '新规则已生效；同指标旧版本保留，历史样本不会按新范围重新判断。'
    }
    mode.value = null; await resource.load()
  } catch (cause) { error.value = operationError(cause); uncertain.value = uncertainOperation(cause) }
  finally { busy.value = false }
}
</script>
<template>
  <div class="stack management">
    <GlassPanel padded="large" class="stack">
      <div class="section-head"><h2 class="display">本轮环境要求</h2><div class="actions"><button class="action-button" :disabled="resource.loading.value" @click="resource.load">刷新要求</button><button v-if="auth.canWrite.value && cycle.status === 'ACTIVE'" class="action-button primary" @click="open()">配置环境规则</button></div></div>
      <p class="muted">范围由生产负责人根据实际依据填写，不预设品种适宜值。规则从确认时生效，保存后只能创建新版本或停止沿用。</p>
      <p v-if="notice" class="notice" role="status">{{ notice }}</p>
      <ResourceState :loading="resource.loading.value" :error="resource.error.value" :empty="!resource.data.value?.rules.length" empty-text="本轮尚无环境规则，不能据此判断环境是否符合要求。" @retry="resource.load">
        <div v-if="currentRules.length" class="rule-grid"><article v-for="rule in currentRules" :key="rule.id" class="rule-card"><p class="eyebrow">{{ metricMeta(rule.metric).label }}</p><h3>{{ rule.minValue ?? '不设下限' }} — {{ rule.maxValue ?? '不设上限' }} <small>{{ metricMeta(rule.metric).unit }}</small></h3><p>预期采集间隔 {{ rule.expectedIntervalMinutes }} 分钟</p><p class="muted">{{ COVERAGE_LABELS[resource.data.value?.coverage.find(item => item.ruleId === rule.id)?.status ?? ''] || '未取得采集情况' }}</p><p class="muted">依据：{{ rule.basis }}</p><button v-if="auth.canWrite.value && cycle.status === 'ACTIVE'" class="text-button" @click="open(rule)">停止沿用</button></article></div>
        <h3 class="display">版本留档 · 最近 200 个版本</h3>
        <div class="table-scroll"><table class="data-table"><thead><tr><th>指标 / 版本</th><th>范围 / 采集预期</th><th>生效区间</th><th>依据与备注</th></tr></thead><tbody><tr v-for="rule in resource.data.value?.rules" :key="rule.id"><td>{{ metricMeta(rule.metric).label }} · #{{ rule.id }}</td><td>{{ rule.minValue ?? '—' }} 至 {{ rule.maxValue ?? '—' }} {{ metricMeta(rule.metric).unit }}<p class="muted">{{ rule.expectedIntervalMinutes }} 分钟</p></td><td>{{ formatDateTime(rule.effectiveFrom) }}<p class="muted">至 {{ rule.effectiveTo ? formatDateTime(rule.effectiveTo) : '后续新版本或本轮结束' }}</p></td><td class="pre-wrap">{{ rule.basis }}<p class="muted">{{ rule.notes }}</p></td></tr></tbody></table></div>
      </ResourceState>
    </GlassPanel>
    <GlassPanel padded="large" class="stack"><h2 class="display">样本判断 · 最近 100 条</h2><p class="muted">按采集时的规则和单位判断。没有规则或单位不符时不显示合格；单条在范围内也不代表整轮种植或产品质量合格。</p><ResourceState :loading="resource.loading.value" :error="resource.error.value" :empty="!resource.data.value?.assessments.length" empty-text="暂无本批接入后的样本判断，旧样本不会自动补算。" @retry="resource.load"><div class="table-scroll"><table class="data-table"><thead><tr><th>采集时间 / 设备</th><th>指标 / 数值</th><th>当时规则</th><th>判断</th></tr></thead><tbody><tr v-for="item in resource.data.value?.assessments" :key="item.readingId"><td>{{ formatDateTime(item.reportedAt) }}<p class="mono muted">{{ item.deviceCode }}</p></td><td>{{ metricMeta(item.metric).label }} · {{ item.value }} {{ item.unit }}</td><td>{{ item.ruleId ? `版本 #${item.ruleId}` : '无适用版本' }}</td><td>{{ ASSESSMENT_LABELS[item.result] ?? '待核实' }}<p class="muted">{{ item.reason }}</p></td></tr></tbody></table></div></ResourceState></GlassPanel>
    <AppDialog :open="mode !== null" :title="mode === 'rule' ? '配置新的环境规则' : '停止沿用规则'" :busy="busy" @close="close"><form class="management" @submit.prevent="save"><fieldset :disabled="busy || uncertain">
      <template v-if="mode === 'rule'"><label class="form-field">监测指标<select v-model="draft.metric" class="field-input"><option v-for="metric in ['temperature', 'humidity', 'soil_moisture', 'light']" :key="metric" :value="metric">{{ METRIC_META[metric]?.label ?? metric }}</option></select></label><div class="form-grid"><label class="form-field">下限（{{ metricMeta(draft.metric).unit }}，选填）<input v-model="draft.min" type="number" step="any" class="field-input"></label><label class="form-field">上限（{{ metricMeta(draft.metric).unit }}，选填）<input v-model="draft.max" type="number" step="any" class="field-input"></label></div><label class="form-field">预期采集间隔（分钟）<input v-model="draft.interval" class="field-input" type="number" min="1" max="1440" step="1" required></label><label class="form-field">范围依据<textarea v-model.trim="draft.basis" class="field-input" rows="3" maxlength="1000" required placeholder="填写采用的生产规范、技术方案或负责人确认依据"></textarea></label><label class="form-field">备注（选填）<textarea v-model.trim="draft.notes" class="field-input" rows="2" maxlength="2000"></textarea></label><p class="notice wait">确认后立即生效，同指标当前版本会结束。历史样本仍按原版本判断。</p></template>
      <label v-else class="form-field">停止说明<textarea v-model.trim="draft.reason" class="field-input" rows="3" maxlength="1000" required></textarea></label>
    </fieldset><p v-if="error" class="notice bad" role="alert">{{ error }}</p><div class="actions form-actions"><button class="action-button primary" :disabled="busy || uncertain" type="submit">{{ busy ? '正在保存…' : '确认保存' }}</button><button class="action-button" type="button" :disabled="busy" @click="close">关闭</button></div></form></AppDialog>
  </div>
</template>
<style scoped>h2 { font-size: var(--fs-h2); }.rule-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 16px; }.rule-card { display: grid; gap: 10px; padding: 20px; border: 1px solid var(--glass-stroke); border-radius: var(--radius-lg); background: var(--glass-fill-quiet); overflow-wrap: anywhere; }.rule-card h3 { font-size: 1.25rem; line-height: 1.5; }.rule-card p { font-size: var(--fs-sm); }.rule-card small { font-size: var(--fs-xs); }.pre-wrap { white-space: pre-wrap; }.form-actions { margin-top: 20px; }@media(max-width:560px){.rule-grid{grid-template-columns:minmax(0,1fr);}}</style>
