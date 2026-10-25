<script setup lang="ts">
import { computed, reactive } from 'vue'
import GlassPanel from './GlassPanel.vue'
import type { ProductionEvidence } from '@/api/packingEvidence'
import { formatDateTime, metricMeta } from '@/types/api'
import { TASK_TYPES } from '@/api/growing'

const props = defineProps<{ evidence: ProductionEvidence; preview?: boolean }>()
const visible = reactive<Record<string, number>>({ rules: 20, farming: 20, environment: 20 })
const sections = computed(() => [
  { key: 'rules', title: '种植环境要求', value: props.evidence.rules, columns: ['metric','min_value','max_value','unit','expected_interval_minutes','effective_from','effective_to'] },
  { key: 'farming', title: '已登记的农事执行', value: props.evidence.farming, columns: ['task_type','performed_at','input_name','quantity','unit'] },
  { key: 'environment', title: '原周期环境窗口摘要', value: props.evidence.environment, columns: ['metric','window_start','window_end','min_value','max_value','avg_value','sample_count'] }
])
const labels: Record<string,string> = { metric: '指标', min_value: '最低 / 下限', max_value: '最高 / 上限', avg_value: '平均', unit: '单位', expected_interval_minutes: '预期采样间隔(分)', effective_from: '规则生效', effective_to: '规则结束', task_type: '农事类型', performed_at: '实际执行时间', input_name: '投入品', quantity: '数量', window_start: '窗口起点', window_end: '窗口终点', sample_count: '样本数' }
function value(key: string, raw: unknown) { if (raw == null) return '—'; if (key === 'task_type') return TASK_TYPES[String(raw)] || String(raw); if (key === 'metric') { const meta = metricMeta(String(raw)); return `${meta.label}${meta.unit ? ` (${meta.unit})` : ''}` }; if (/(?:_at|_from|_to|_start|_end)$/.test(key)) return formatDateTime(String(raw)); return String(raw) }
</script>
<template>
  <GlassPanel padded="large" class="production-evidence">
    <header><div><p class="eyebrow">生产证据 · 版本 {{ evidence.version }}</p><h2 class="display">查看采收之前留下的记录</h2></div><span :class="['evidence-state', { verified: evidence.verificationStatus === 'VERIFIED' }]">{{ preview ? '证据预览 · 核验请打开产品页' : evidence.verificationStatus === 'VERIFIED' ? '链上摘要一致' : '尚未核验通过' }}</span></header>
    <p>时间范围：{{ formatDateTime(evidence.from) }} 至 {{ formatDateTime(evidence.until) }}（北京时间）</p>
    <p v-if="evidence.capturedAt">本次证据冻结：{{ formatDateTime(evidence.capturedAt) }}</p>
    <p class="muted">{{ evidence.coverage }}</p>
    <details v-for="section in sections" :key="section.key" class="evidence-section">
      <summary>{{ section.title }} <span>本版 {{ section.value.rows.length }} 条{{ section.value.truncated ? ' · 已达展示上限' : '' }} ⌄</span></summary>
      <div v-if="section.value.rows.length" class="evidence-table" tabindex="0" role="region" :aria-label="section.title"><table><thead><tr><th v-for="column in section.columns" :key="column">{{ labels[column] }}</th></tr></thead><tbody><tr v-for="(row,index) in section.value.rows.slice(0,visible[section.key])" :key="index"><td v-for="column in section.columns" :key="column">{{ value(column,row[column]) }}</td></tr></tbody></table></div>
      <p v-else class="empty-evidence">本版没有纳入此类记录，不能据此认定未发生农事或环境合格。</p>
      <button v-if="section.value.rows.length > (visible[section.key] ?? 20)" class="action-button" @click="visible[section.key] = (visible[section.key] ?? 20) + 20">继续查看本版记录</button>
      <p v-if="section.value.truncated" class="muted">本版最多纳入 {{ section.value.limit }} 条，不代表完整历史。</p>
    </details>
    <p class="muted">本版生产证据不包含逐件销售、运输和收货。具体包装码的关联情况请查看产品查询页的单品流转记录；摘要一致也不代替实物质量或设备身份验证。</p>
    <p v-if="evidence.manifestHash" class="manifest-hash">证据摘要 {{ evidence.manifestHash }}</p>
  </GlassPanel>
</template>
<style scoped>
.production-evidence { min-width: 0; }.production-evidence header { display: flex; align-items: start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }.production-evidence h2 { font-size: var(--fs-h2); margin: 8px 0 18px; }.production-evidence > p { font-size: var(--fs-sm); line-height: 1.9; margin: 8px 0; overflow-wrap: anywhere; }.evidence-state { color: var(--slate); border: 1px solid var(--glass-stroke); border-radius: 20px; padding: 6px 12px; font-size: var(--fs-xs); }.verified { color: var(--field); background: var(--field-tint); }.evidence-section { border-top: 1px solid var(--glass-hairline); padding: 16px 0; margin-top: 10px; min-width: 0; }.evidence-section summary { cursor: pointer; display: flex; gap: 8px; justify-content: space-between; flex-wrap: wrap; font-size: var(--fs-sm); }.evidence-section summary span,.empty-evidence { color: var(--slate); font-size: var(--fs-xs); }.evidence-table { overflow-x: auto; max-width: 100%; margin-top: 16px; }.evidence-table table { font-size: var(--fs-xs); width: 100%; border-collapse: collapse; }.evidence-table th,.evidence-table td { padding: 10px; min-width: 90px; text-align: left; border-bottom: 1px solid var(--glass-hairline); white-space: nowrap; }.empty-evidence { padding-top: 12px; line-height: 1.8; }.manifest-hash { font-size: 11px !important; }.evidence-section .action-button { margin-top: 12px; }
</style>
