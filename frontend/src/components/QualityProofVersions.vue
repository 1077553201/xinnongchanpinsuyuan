<script setup lang="ts">
import type { QualityProofVersion } from '@/api/qualityEvidence'
import { QUALITY_RESULTS, QUALITY_STATUS } from '@/api/sourceQuality'
import { formatDateTime, chainStatusLabel } from '@/types/api'
defineProps<{ versions: QualityProofVersion[]; preview?: boolean }>()
</script>
<template>
  <div class="quality-proof-versions"><details v-for="row in versions" :key="row.version"><summary>质检证据版本 {{ row.version }} · {{ preview ? (row.chainStatus === 'UNCERTAIN' ? '结果待核对' : chainStatusLabel(row.chainStatus)) : row.verificationStatus === 'VERIFIED' ? '本版摘要一致' : '本版尚未实时核验通过' }}</summary><p>{{ row.manifest.reportFacts.report.reportNo }} · {{ row.manifest.reportFacts.report.agencyName }}</p><p>{{ QUALITY_RESULTS[row.manifest.reportFacts.report.declaredResult as keyof typeof QUALITY_RESULTS] }} · 本版公开状态：{{ QUALITY_STATUS[row.manifest.reportFacts.status as keyof typeof QUALITY_STATUS] }}</p><p v-if="row.createdAt" class="muted">本版证据留档 {{ formatDateTime(row.createdAt) }}；此时间不是实验室检测时间。</p><p class="muted">采样 {{ row.manifest.reportFacts.report.sampledOn }} · 检测 {{ row.manifest.reportFacts.report.checkedOn }} · 本版当时{{ row.manifest.reportFacts.attachmentPublic ? '允许' : '不允许' }}公开下载附件。</p><p v-for="link in row.manifest.reportFacts.revisionLinks" :key="link.replacementReportId" class="muted">经营方关联替代：{{ link.previousReportNo }} → {{ link.replacementReportNo }}</p><p class="digest">文件 SHA-256 {{ row.manifest.reportFacts.report.fileSha256 }}</p><p class="digest">本版摘要 {{ row.manifestHash }}</p><p v-if="row.manifest.previousHash" class="digest">前版摘要 {{ row.manifest.previousHash }}</p><p v-if="preview && row.txId" class="muted">交易记录 #{{ row.txId }}</p></details></div>
</template>
<style scoped>
.quality-proof-versions { display: grid; gap: 12px; min-width: 0; }.quality-proof-versions details { min-width: 0; border: 1px solid var(--glass-hairline); padding: 14px; border-radius: var(--radius-md); overflow-wrap: anywhere; }.quality-proof-versions summary { cursor: pointer; line-height: 1.8; }.quality-proof-versions p { font-size: var(--fs-sm); margin-top: 12px; line-height: 1.8; }.digest { font-size: var(--fs-xs) !important; color: var(--slate); }
</style>
