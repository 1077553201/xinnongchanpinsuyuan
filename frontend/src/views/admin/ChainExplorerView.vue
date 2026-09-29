<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import GlassPanel from '@/components/GlassPanel.vue'
import StatusChip from '@/components/StatusChip.vue'
import { chainHealth } from '@/api/chain'
import type { ChainHealthResult } from '@/types/chain'

const chain = ref<ChainHealthResult | null>(null)
const error = ref<string | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    chain.value = await chainHealth()
  } catch (err) {
    error.value = err instanceof Error ? err.message : '链状态获取失败'
  } finally {
    loading.value = false
  }
})

const statusTone = computed(() => (chain.value?.chainEnabled ? 'ok' : 'off'))
</script>

<template>
  <div class="explorer">
    <GlassPanel padded="large" class="panel">
      <header class="head">
        <h2 class="title display">FISCO BCOS 连接状态</h2>
        <StatusChip :tone="statusTone" :label="chain?.status || (loading ? '读取中' : '未知')" />
      </header>

      <dl class="grid">
        <div class="cell">
          <dt>合约地址</dt>
          <dd class="mono">{{ chain?.contractAddress || '—' }}</dd>
        </div>
        <div class="cell">
          <dt>群组 ID</dt>
          <dd class="mono">{{ chain?.groupId || '—' }}</dd>
        </div>
        <div class="cell">
          <dt>节点</dt>
          <dd class="mono">{{ chain?.peers?.join(', ') || '—' }}</dd>
        </div>
        <div class="cell">
          <dt>最新区块高度</dt>
          <dd class="mono">{{ chain?.latestBlock ?? '—' }}</dd>
        </div>
        <div class="cell">
          <dt>出块账户</dt>
          <dd class="mono">{{ chain?.accountAddress || '—' }}</dd>
        </div>
        <div class="cell">
          <dt>writer 权限</dt>
          <dd>{{ chain?.writerStatus === true ? '已授权' : chain?.writerStatus === false ? '未授权' : '—' }}</dd>
        </div>
      </dl>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-else-if="chain?.errorMessage" class="error">{{ chain.errorMessage }}</p>
    </GlassPanel>

    <GlassPanel padded="large" class="panel">
      <h2 class="title display">链上存证口径</h2>
      <p class="lede">
        链上只保存可验证摘要与索引，不保存业务明文。以下字段会以 <span class="mono">bytes32</span> 形式写入
        <span class="mono">AgriTrace</span> 合约，业务明细仍留在 MySQL。
      </p>

      <ul class="fields">
        <li v-for="field in [
          { name: 'batchIdHash', desc: '批次业务标识哈希' },
          { name: 'itemCodeHash', desc: '单品追溯码哈希' },
          { name: 'eventIdHash / payloadHash', desc: '溯源事件标识与明细摘要' },
          { name: 'windowIdHash / deviceCodeHash', desc: '传感器窗口与设备编码摘要' },
          { name: 'nfcUidHash', desc: 'NFC UID 摘要' },
          { name: 'operator / timestamp / txHash', desc: '操作者、时间与交易追踪字段' },
        ]" :key="field.name" class="field">
          <span class="mono chip-name">{{ field.name }}</span>
          <span class="muted">{{ field.desc }}</span>
        </li>
      </ul>
    </GlassPanel>

    <GlassPanel padded="large" class="panel gap">
      <h2 class="title display">交易列表待补</h2>
      <p class="lede">
        后端已把每笔链上动作写入 <span class="mono">chain_transaction</span> 表，但目前
        <strong>没有查询接口</strong>，所以这一页暂时无法列出交易列表。
      </p>
      <p class="lede">
        要完成「链上交易查看」，需要在后端补一个接口，例如
        <span class="mono">GET /api/chain/transactions?businessType=&amp;businessId=&amp;limit=</span>，
        返回 <span class="mono">business_type / business_id / chain_status / tx_hash / block_number /
        receipt_status / payload_hash / submitted_at / confirmed_at</span>。
      </p>
    </GlassPanel>
  </div>
</template>

<style scoped>
.explorer {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.panel {
  border-radius: var(--radius-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.title {
  font-size: var(--fs-h2);
  font-weight: 600;
}

.lede {
  font-size: var(--fs-sm);
  color: var(--ink-70);
  max-width: 68ch;
}

.grid {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
}

.cell dt {
  font-size: var(--fs-micro);
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--slate);
}

.cell dd {
  margin: 5px 0 0;
  font-size: var(--fs-sm);
  word-break: break-all;
}

.fields {
  display: grid;
  gap: 8px;
}

.field {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: var(--space-3);
  font-size: var(--fs-sm);
  padding: 7px 0;
  border-top: 1px solid var(--glass-hairline);
}

.chip-name {
  color: var(--field);
}

.error {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--seal);
  word-break: break-all;
}

.gap {
  border-style: dashed;
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .field {
    grid-template-columns: minmax(0, 1fr);
    gap: 2px;
  }
}
</style>
