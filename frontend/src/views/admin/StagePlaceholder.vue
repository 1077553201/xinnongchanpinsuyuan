<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import GlassPanel from '@/components/GlassPanel.vue'
import StatusChip from '@/components/StatusChip.vue'

const route = useRoute()

const title = computed(() => (route.meta.title as string | undefined) ?? '')
const eyebrow = computed(() => (route.meta.eyebrow as string | undefined) ?? '')

interface Plan {
  summary: string
  bullets: string[]
}

const PLANS: Record<string, Plan> = {
  'admin-products': {
    summary: '产品主数据与生产批次台账，批次创建后会直接触发链上 registerBatch。',
    bullets: [
      '产品列表：编码、名称、品类、品种、产地',
      '新建产品（POST /api/products）',
      '批次列表与新建批次（POST /api/batches，自动上链）',
      '关闭 / 召回批次（POST /api/batches/{id}/close，状态 2 / 3）',
      '批次链上状态列与重试入口',
    ],
  },
  'admin-items': {
    summary: '单品码批量生成与二维码打印，是扫码溯源链路的起点。',
    bullets: [
      '按批次批量生成单品码（POST /api/items/generate，单批 1–100）',
      '单品列表与所属批次筛选',
      '二维码预览与批量打印（traceUrl 直接编码）',
      '单品状态流转：CREATED / ACTIVE / SOLD / RECALL / VOID',
      '单品绑定的链上交易哈希查看',
    ],
  },
  'admin-iot': {
    summary: '固定网关与田间巡检小车共用的传感器监控台，两者只是分组不同。',
    bullets: [
      '网关与设备列表（含小车，按 gatewayCode 区分）',
      '实时读数表格（GET /api/iot/readings）',
      '窗口摘要与上链状态（GET /api/iot/windows）',
      '告警列表与阈值说明（GET /api/iot/alerts）',
      '模拟上报与手动聚合，用于联调（telemetry/mock、windows/aggregate）',
    ],
  },
  'admin-nfc': {
    summary: 'NFC 标签与单品的绑定关系维护，属于防伪链路的入口。',
    bullets: [
      '标签绑定（POST /api/nfc/bind，上链 bindNfcTag）',
      '停用标签（POST /api/nfc/{id}/disable，上链 updateNfcStatus）',
      '同一标签不可绑定多个有效单品、同一单品只允许一个有效标签',
      '异常状态展示：已绑定 / 未绑定 / 标签不匹配 / 疑似异常',
      '换绑流程与动态签名防克隆（第五阶段深化）',
    ],
  },
  'admin-ai': {
    summary: '农场管理助手。AI 只做摘要、解释与建议，不写链、不改业务数据、不下发设备指令。',
    bullets: [
      '会话界面（农场助手 / 消费者助手）',
      '后端需先补 AiProvider 接口与 disabled 实现（当前后端为空）',
      '会话落库到 ai_session / ai_message',
      '回答必须区分「链上已验证事实」与「AI 建议」',
      '健康功效、医疗建议类问题需要限制',
    ],
  },
}

const plan = computed<Plan>(() => {
  const key = String(route.name ?? '')
  return PLANS[key] ?? { summary: '该页面的骨架尚未定义。', bullets: [] }
})
</script>

<template>
  <GlassPanel padded="large" class="stage">
    <header class="head">
      <p class="eyebrow">{{ eyebrow }}</p>
      <h2 class="title display">{{ title }}</h2>
      <StatusChip label="骨架已就绪" tone="wait" />
    </header>

    <p class="summary">{{ plan.summary }}</p>

    <ul v-if="plan.bullets.length" class="bullets">
      <li v-for="bullet in plan.bullets" :key="bullet">
        <span class="marker" aria-hidden="true"></span>
        <span>{{ bullet }}</span>
      </li>
    </ul>

    <footer class="foot">
      <p class="muted">
        本页目前只有版式骨架。确认整体设计后，我会按上面的清单逐项实现。
      </p>
      <RouterLink to="/admin" class="link">← 返回总览</RouterLink>
    </footer>
  </GlassPanel>
</template>

<style scoped>
.stage {
  border-radius: var(--radius-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.title {
  font-size: var(--fs-title);
  font-weight: 600;
}

.summary {
  color: var(--ink-70);
  max-width: 62ch;
}

.bullets {
  display: grid;
  gap: 10px;
  border-top: 1px solid var(--glass-hairline);
  padding-top: var(--space-4);
}

.bullets li {
  display: grid;
  grid-template-columns: 16px minmax(0, 1fr);
  gap: 10px;
  font-size: var(--fs-sm);
  color: var(--ink-70);
}

.marker {
  margin-top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 2px;
  background: var(--field);
  opacity: 0.55;
}

.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
  border-top: 1px solid var(--glass-hairline);
  padding-top: var(--space-4);
  font-size: var(--fs-sm);
}

.link {
  color: var(--field);
}
</style>
