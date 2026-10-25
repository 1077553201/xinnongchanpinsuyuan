<script setup lang="ts">
import type { NfcIdentity } from '@/api/packingNfc'
defineProps<{ identity: NfcIdentity }>()
</script>
<template>
  <section class="nfc-identity stack" aria-label="NFC 标签身份核验">
    <h2>NFC 标签身份</h2>
    <p>{{ identity.disableRequested ? '此标签已登记停用，停止自动进入产品查询。' : identity.active ? '标签关联与当前产品身份实时核验一致。' : '标签关联尚未核验为可用，请核对标签状态。' }}</p>
    <p>{{ identity.verificationStatus === 'VERIFIED' ? '本次链上记录与所登记摘要一致。' : '本次未能实时核验通过，不能据此认定绑定或停用已上链。' }}</p>
    <p class="digest">UID 摘要 {{ identity.uidHash }}<br>绑定摘要 {{ identity.bindingHash }}</p>
    <p v-if="identity.disableHash" class="digest">停用摘要 {{ identity.disableHash }}</p>
    <p class="muted">此核验只证明登记关联及链上状态一致。普通 UID、二维码与静态 NFC 网址均可复制，不验证实体标签防克隆或实物质量。停用标签不删除产品历史。</p>
  </section>
</template>
<style scoped>
.nfc-identity { min-width: 0; overflow-wrap: anywhere; line-height: 1.8; }.nfc-identity h2 { font-size: var(--fs-h2); }.digest { font-size: var(--fs-xs); color: var(--slate); }
</style>
