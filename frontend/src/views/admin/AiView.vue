<script setup lang="ts">
import GlassPanel from '@/components/GlassPanel.vue'
import StatusChip from '@/components/StatusChip.vue'
</script>
<template>
  <div class="stack">
    <GlassPanel padded="large" class="ai-intro">
      <p class="eyebrow">农场业务助理</p>
      <h2 class="display">说完一天的事，整理成可核对的操作。</h2>
      <StatusChip tone="off" label="Agent 尚未接入" />
      <p>未来可以把一天的种植、农事、采收与发货情况发给助理。它先查询业务资料、整理操作清单，补问不明确的内容，再由你确认执行。</p>
      <p class="muted tiny">目前请通过地块与种植等页面办理业务。对话服务与确认执行流程尚未启用。</p>
    </GlassPanel>
    <GlassPanel padded="large" class="ai-intro">
      <h2 class="display">计划中的处理流程</h2>
      <ol><li>理解日报，区分已经发生的事情与后续计划</li><li>匹配地块、种植周期、人员与批次，核对缺失信息</li><li>列出待确认操作，支持逐项修改或排除</li><li>确认后调用业务服务，逐项返回实际结果</li></ol>
      <p class="muted tiny">后续接入查询复盘、环境异常解释与巡检任务协同。业务保存、链上存证和设备完成分别报告，不以聊天回答代替执行结果。</p>
    </GlassPanel>
  </div>
</template>
<style scoped>.ai-intro { display: grid; gap: 18px; border-radius: var(--radius-xl); }.ai-intro h2 { font-size: var(--fs-h2); line-height: 1.5; }.ai-intro ol { display: grid; gap: 10px; list-style: decimal; padding-left: 24px; }</style>
