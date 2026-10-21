<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import ResourceState from '@/components/ResourceState.vue'
import GrowingRulesPanel from '@/components/growing/GrowingRulesPanel.vue'
import FarmTasksPanel from '@/components/growing/FarmTasksPanel.vue'
import EnvironmentCasesPanel from '@/components/growing/EnvironmentCasesPanel.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { listCycles, listPlots } from '@/api/production'
const route = useRoute()
const cycles = useAsyncResource(listCycles), plots = useAsyncResource(listPlots)
const cycleId = ref(typeof route.query.cycle === 'string' ? route.query.cycle : '')
const tab = ref<'rules' | 'tasks' | 'cases'>('tasks')
const selected = computed(() => cycles.data.value?.find(cycle => cycle.id === Number(cycleId.value)))
const plotMap = computed(() => new Map((plots.data.value ?? []).map(plot => [plot.id, plot])))
const tabs = [{ key: 'tasks', label: '农事作业' }, { key: 'rules', label: '环境要求' }, { key: 'cases', label: '异常处置' }] as const
async function refresh() { await Promise.all([cycles.load(), plots.load()]) }
onMounted(refresh)
</script>
<template>
  <div class="management stack">
    <GlassPanel padded="large" class="stack">
      <div class="section-head"><div><p class="eyebrow">每轮种植，都有据可查</p><h2 class="display">把生产安排，变成真实的作业记录。</h2></div><button class="action-button" :disabled="cycles.loading.value || plots.loading.value" @click="refresh">刷新周期</button></div>
      <p class="muted">先明确环境要求和农事安排，再登记实际执行、投入品用量与异常处置。计划不会自动变成完成记录。</p>
      <ResourceState :loading="cycles.loading.value || plots.loading.value" :error="cycles.error.value || plots.error.value" :empty="!cycles.data.value?.length" empty-text="请先到地块与种植开启本轮生产。" @retry="refresh">
        <label class="field-label">当前种植周期<select v-model="cycleId" class="field-input cycle-picker"><option value="">选择本轮或历史周期</option><option v-for="cycle in cycles.data.value" :key="cycle.id" :value="String(cycle.id)">{{ plotMap.get(cycle.plotId)?.plotName }} · {{ cycle.productName }} / {{ cycle.variety }} · {{ cycle.cycleCode }}{{ cycle.status === 'ENDED' ? '（已结束）' : '' }}</option></select></label>
      </ResourceState>
      <div v-if="selected" class="actions" role="group" aria-label="生产记录类型"><button v-for="item in tabs" :key="item.key" type="button" class="action-button" :class="{ primary: tab === item.key }" :aria-pressed="tab === item.key" @click="tab = item.key">{{ item.label }}</button></div>
    </GlassPanel>
    <template v-if="selected">
      <GrowingRulesPanel v-if="tab === 'rules'" :key="selected.id" :cycle="selected" />
      <FarmTasksPanel v-if="tab === 'tasks'" :key="selected.id" :cycle="selected" :organization-id="plotMap.get(selected.plotId)?.organizationId ?? 0" />
      <EnvironmentCasesPanel v-if="tab === 'cases'" :key="selected.id" :cycle-id="selected.id" />
    </template>
  </div>
</template>
<style scoped>h2 { margin-top: 8px; font-size: clamp(1.2rem, 2vw, 1.65rem); line-height: 1.5; }.cycle-picker { margin-top: 8px; }</style>
