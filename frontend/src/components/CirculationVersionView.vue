<script setup lang="ts">
import type { CirculationVersion } from '@/api/circulationEvidence'
import { formatDateTime, chainStatusLabel } from '@/types/api'
defineProps<{ versions: CirculationVersion[]; preview?: boolean }>()
const kinds: Record<string,string> = { DEPART: '实际发运', UPDATE: '运输进展', EXCEPTION: '运输异常', RECEIVE: '收货结果' }
</script>
<template>
  <div class="circulation-versions">
    <details v-for="version in versions" :key="version.version">
      <summary>流转证据版本 {{ version.version }} · {{ preview ? (version.chainStatus === 'UNCERTAIN' ? '结果待核对' : chainStatusLabel(version.chainStatus)) : version.verificationStatus === 'VERIFIED' ? '本版链上摘要一致' : '本版尚未实时核验通过' }}</summary>
      <p class="muted">{{ version.createdAt ? `证据冻结：${formatDateTime(version.createdAt)}。本事件时间为证据留档时间，实际发生时间列在下方。` : '尚未冻结，此处仅为待确认预览。' }}</p>
      <dl><div><dt>本版销售出库</dt><dd>{{ formatDateTime(version.manifest.circulation.dispatchedAt) }}</dd></div><div><dt>本版逐件收货</dt><dd>{{ version.manifest.circulation.receiptOutcome === 'ACCEPTED' ? '实收' : version.manifest.circulation.receiptOutcome === 'REJECTED' ? '拒收' : '尚未逐件核对' }}</dd></div></dl>
      <p v-for="(event,index) in version.manifest.circulation.events" :key="index">{{ kinds[event.kind] || '运输记录' }} · {{ formatDateTime(event.occurredAt) }}</p>
      <p v-if="version.manifest.circulation.eventsTruncated" class="muted">本版仅包含最近 200 条运输记录。</p>
      <p class="digest">本版摘要 {{ version.manifestHash }}</p><p v-if="version.manifest.previousHash" class="digest">前版摘要 {{ version.manifest.previousHash }}</p>
      <p v-if="preview && version.txId" class="muted">交易记录 #{{ version.txId }}</p>
    </details>
  </div>
</template>
<style scoped>
.circulation-versions { min-width: 0; display: grid; gap: 12px; }.circulation-versions details { min-width: 0; padding: 14px; border: 1px solid var(--glass-hairline); border-radius: var(--radius-md); }.circulation-versions summary { cursor: pointer; line-height: 1.8; }.circulation-versions p { margin-top: 12px; line-height: 1.8; font-size: var(--fs-sm); }.circulation-versions dl { display: grid; gap: 12px; margin: 14px 0; }.circulation-versions dt { font-size: var(--fs-xs); color: var(--slate); }.circulation-versions dd { margin: 5px 0 0; }.digest { overflow-wrap: anywhere; font-size: var(--fs-xs) !important; color: var(--slate); }
</style>
