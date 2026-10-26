<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { RouterLink } from 'vue-router'
import GlassPanel from './GlassPanel.vue'
import ResourceState from './ResourceState.vue'
import { useAsyncResource } from '@/composables/useAsyncResource'
import { rejectionList } from '@/api/rejections'
const props = defineProps<{ warehouseId: number }>()
const data = useAsyncResource(() => rejectionList(props.warehouseId, 'QUARANTINED'))
onMounted(() => { void data.load() })
watch(() => props.warehouseId, () => { void data.load() })
</script>
<template>
  <GlassPanel padded="large" class="stack">
    <div class="section-head"><h2 class="display">本仓隔离退回货物</h2><button class="action-button" :disabled="data.loading.value" @click="data.load">刷新隔离保管</button></div>
    <p class="muted">以下为逐件拒收后实际隔离收货的保管记录，与普通库存分开统计，不能直接销售出库。报损后不再计入隔离保管。</p>
    <ResourceState :loading="data.loading.value" :error="data.error.value" @retry="data.load"><template v-if="data.data.value"><p v-if="!data.data.value.quarantineUnits.length">本仓暂无隔离保管货物。</p><p v-for="u in data.data.value.quarantineUnits" :key="u.packageUnit">隔离保管 {{ u.packageCount }} {{ u.packageUnit }} · {{ Number(u.weightKg).toFixed(3) }} kg</p></template></ResourceState>
    <RouterLink :to="{ path: '/admin/rejections', query: { warehouseId } }" class="text-button">查看本仓退回与隔离明细 →</RouterLink>
  </GlassPanel>
</template>
