<script setup lang="ts">
import GlassPanel from './GlassPanel.vue'
import { QUALITY_RESULTS, qualityDownloadUrl, type PackingQuality } from '@/api/sourceQuality'
defineProps<{ quality?: PackingQuality }>()
</script>
<template>
  <GlassPanel padded="large" class="packing-quality stack">
    <div class="section-head"><h2 class="display">来源质检报告</h2><span class="muted">公开记录 {{ quality?.total ?? 0 }} 份</span></div>
    <p class="muted">报告由经营方上传并核对公开，记载的是对应来源样本的检测结论；不等于本件产品逐件检测或已验证检测机构真实性。报告资料与文件摘要尚未提交链上存证。</p>
    <p v-if="!quality?.rows.length" class="muted">暂无本包装或其原采收批次的公开报告，不能据此认定合格。</p>
    <p v-if="quality && quality.total > quality.rows.length" class="muted">仅展示最近 {{ quality.limit }} 份公开记录，包含撤回历史，不代表完整检测档案。</p>
    <article v-for="report in quality?.rows" :key="report.id" class="report-card stack"><div class="section-head"><strong>{{ report.reportNo }}</strong><span>{{ report.status === 'WITHDRAWN' ? '已撤回 · 原结论留档' : '经营方已核对公开' }}</span></div><p>{{ report.scopeType === 'HARVEST' ? '原采收批次样本报告' : '本包装批次样本报告' }} · {{ report.agencyName }}</p><p :class="{ failed: report.declaredResult === 'FAIL' }">{{ QUALITY_RESULTS[report.declaredResult] }}</p><p class="muted">采样 {{ report.sampledOn }} · 检测 {{ report.checkedOn }}</p><p class="digest">文件 SHA-256 {{ report.fileSha256 }}</p><p class="digest">资料摘要 {{ report.manifestHash }} · 未提交存证</p><p v-if="report.status === 'WITHDRAWN'" class="muted">此报告已由经营方撤回，保留原公开记录，附件停止公开下载。</p><a v-else-if="report.downloadPath" class="download" :href="qualityDownloadUrl(report.downloadPath)" target="_blank" rel="noopener">下载公开报告（{{ (report.fileSize / 1024).toFixed(1) }} KB） ↗</a><p v-else class="muted">仅公开报告资料，附件原件未开放下载。</p></article>
  </GlassPanel>
</template>
<style scoped>
.packing-quality { min-width: 0; }.section-head { display: flex; justify-content: space-between; flex-wrap: wrap; align-items: center; gap: 12px; }.packing-quality h2 { font-size: var(--fs-h2); }.packing-quality p { line-height: 1.8; }.report-card { overflow-wrap: anywhere; min-width: 0; padding: 16px; border: 1px solid var(--glass-hairline); border-radius: var(--radius-md); }.report-card strong,.digest { overflow-wrap: anywhere; }.digest { color: var(--slate); font-size: var(--fs-xs); }.download { color: var(--field); font-size: var(--fs-sm); }.failed { color: var(--seal); }
</style>
