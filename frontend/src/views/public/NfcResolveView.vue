<script setup lang="ts">
import { ref, shallowRef, watch, onScopeDispose } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import PackingNfcIdentity from '@/components/PackingNfcIdentity.vue'
import { resolvePackingNfc, type NfcIdentity } from '@/api/packingNfc'
import { operationError } from '@/utils/growing'
const props = defineProps<{ uidHash: string }>()
const router = useRouter(), data = shallowRef<NfcIdentity | null>(null), loading = ref(false), error = ref('')
let revision = 0
onScopeDispose(() => { revision += 1 })
async function load() {
  const current = ++revision
  loading.value = true; data.value = null; error.value = ''
  try {
    const result = await resolvePackingNfc(props.uidHash)
    if (current !== revision) return
    data.value = result
    if (result.active && result.verificationStatus === 'VERIFIED' && result.verificationSource === 'LIVE_CHAIN') await router.replace({ name: 'public-trace', params: { traceCode: result.traceCode } })
  } catch (cause) { if (current === revision) error.value = operationError(cause) }
  finally { if (current === revision) loading.value = false }
}
watch(() => props.uidHash, load, { immediate: true })
</script>
<template>
  <main class="nfc-resolve"><GlassPanel padded="large" class="stack"><p class="eyebrow">标签来源查询</p><h1>核对 NFC 关联身份</h1><p v-if="loading" role="status">正在核对标签、产品来源及链上状态…</p><p v-if="error" role="alert" class="notice bad">{{ error }}</p><PackingNfcIdentity v-if="data" :identity="data" /><RouterLink v-if="data" :to="{ name: 'public-trace', params: { traceCode: data.traceCode } }">查看原关联产品的历史资料 →</RouterLink><button class="action-button" :disabled="loading" @click="load">重新核对</button></GlassPanel></main>
</template>
<style scoped>
.nfc-resolve { width: 100%; min-height: 100dvh; padding: clamp(16px,4vw,48px); }.nfc-resolve h1 { font-size: var(--fs-h1); overflow-wrap: anywhere; }
</style>
