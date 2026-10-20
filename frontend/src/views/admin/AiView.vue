<script setup lang="ts">
import GlassPanel from '@/components/GlassPanel.vue'
import StatusChip from '@/components/StatusChip.vue'
</script>
<template>
  <div class="stack"><GlassPanel padded="large" class="ai-intro"><p class="eyebrow">农场助手</p><h2 class="display">让记录帮助下一次判断</h2><StatusChip tone="off" label="AI 服务未启用" /><p>当前可以通过产品台账、设备监控和溯源记录查看业务事实。AI 问答尚未接入，因此暂时不能提问。</p><p class="muted tiny">启用后，助手将基于可访问的记录提供解释和建议，并与已核验事实分开呈现。</p></GlassPanel><GlassPanel padded="large" class="ai-intro"><h2 class="display">计划提供的帮助</h2><ul><li>汇总批次生产和流转记录</li><li>解释设备趋势与异常告警</li><li>回答消费者关于当前产品记录的问题</li></ul><p class="muted tiny">当前不生成模拟回答，也不会通过 AI 修改业务、写链或控制设备。</p></GlassPanel></div>
</template>
<style scoped>.ai-intro { display: grid; gap: 18px; border-radius: var(--radius-xl); }.ai-intro h2 { font-size: var(--fs-h2); }.ai-intro ul { display: grid; gap: 10px; list-style: disc; padding-left: 20px; }</style>
