<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import GlassPanel from '@/components/GlassPanel.vue'
import MetricBar from '@/components/MetricBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import TraceTimeline from '@/components/TraceTimeline.vue'
import VerifySeal from '@/components/VerifySeal.vue'
import { fetchTrace } from '@/api/publicTrace'
import { chainStatusTone, formatDate, formatDateTime, shortHash, type Tone } from '@/types/api'
import type { PublicTraceView, TraceEnvironment } from '@/types/trace'

const props = defineProps<{ traceCode: string }>()

const trace = ref<PublicTraceView | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const notFound = ref(false)

async function load(code: string) {
  loading.value = true
  error.value = null
  notFound.value = false
  trace.value = null
  try {
    trace.value = await fetchTrace(code)
  } catch (err) {
    const message = err instanceof Error ? err.message : '查询失败'
    if (message.toLowerCase().includes('not found')) {
      notFound.value = true
    } else {
      error.value = message
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => load(props.traceCode))
watch(() => props.traceCode, (code) => load(code))

const product = computed(() => trace.value?.product)
const batch = computed(() => trace.value?.batch)
const item = computed(() => trace.value?.item)
const verification = computed(() => trace.value?.chainVerification)
const environments = computed<TraceEnvironment[]>(() => (trace.value?.environmentSummary ?? []).slice(0, 8))
const reports = computed(() => trace.value?.qualityReports ?? [])
const events = computed(() => trace.value?.timeline ?? [])

/** 结论先行：把链上校验结果压缩成一句可读的判断。 */
const verdict = computed<{ tone: Tone; headline: string; detail: string }>(() => {
  const v = verification.value
  if (!v) {
    return { tone: 'off', headline: '未取得链上记录', detail: '该单品暂时没有可核对的链上摘要。' }
  }
  if (v.latestChainStatus === 'FAILED') {
    return { tone: 'bad', headline: '上链失败', detail: '存在写入失败的交易，请联系生产方核实。' }
  }
  if (v.latestChainStatus === 'PENDING') {
    return { tone: 'wait', headline: '等待链上确认', detail: '记录已提交，仍在等待区块确认。' }
  }
  const matched = v.itemPayloadHashMatched !== false && v.batchPayloadHashMatched !== false
  if (!matched) {
    return { tone: 'bad', headline: '哈希不一致', detail: '链下明细与链上摘要不匹配，请谨慎对待。' }
  }
  return {
    tone: 'ok',
    headline: '链上校验通过',
    detail: '批次与单品的链下明细重新计算后，与链上摘要一致。',
  }
})

const sampledCount = computed(() =>
  environments.value.reduce((total, env) => total + (env.sampleCount ?? 0), 0),
)

const qualitySummary = computed(() => {
  if (!reports.value.length) return '未上传'
  const failed = reports.value.some((r) => (r.result ?? '').toUpperCase().includes('FAIL'))
  return failed ? '存在不合格' : '全部合格'
})

const aiAvailable = computed(() => trace.value?.ai?.available === true)
</script>

<template>
  <div class="trace">
    <header class="bar glass glass-strong">
      <span class="brand">农产品可信溯源</span>
      <StatusChip :tone="verdict.tone" :label="verdict.headline" compact />
    </header>

    <!-- 载入 -->
    <template v-if="loading">
      <GlassPanel padded="large" class="hero">
        <div class="skeleton sk-eyebrow"></div>
        <div class="skeleton sk-title"></div>
        <div class="skeleton sk-sub"></div>
      </GlassPanel>
      <GlassPanel padded="large">
        <div class="skeleton sk-block"></div>
      </GlassPanel>
    </template>

    <!-- 查不到 -->
    <GlassPanel v-else-if="notFound" padded="large" class="hero">
      <VerifySeal status="CHAIN_DISABLED" :show-caption="false" />
      <h1 class="hero-title display">没有查到这个溯源编号</h1>
      <p class="lede">
        编号 <span class="mono">{{ traceCode }}</span> 不在系统中。请核对二维码是否完整，
        或联系销售方确认标签是否被替换。
      </p>
    </GlassPanel>

    <!-- 出错 -->
    <GlassPanel v-else-if="error" padded="large" class="hero">
      <h1 class="hero-title display">查询暂时不可用</h1>
      <p class="lede">{{ error }}</p>
      <button class="retry" type="button" @click="load(traceCode)">重新查询</button>
    </GlassPanel>

    <!-- 正文 -->
    <template v-else-if="trace">
      <GlassPanel padded="large" glow="field" class="hero">
        <div class="hero-grid">
          <div class="hero-text">
            <p class="eyebrow">溯源编号</p>
            <p class="mono code">{{ traceCode }}</p>
            <h1 class="hero-title display">{{ product?.productName || '未命名产品' }}</h1>
            <p class="hero-sub">
              <span>{{ product?.originPlace || '产地未记录' }}</span>
              <span v-if="product?.variety" class="dot-sep">·</span>
              <span v-if="product?.variety">{{ product.variety }}</span>
            </p>
          </div>
          <VerifySeal :status="verification?.latestChainStatus" :size="104" />
        </div>

        <ul class="facts">
          <li>
            <span class="fact-label">批次</span>
            <span class="fact-value mono">{{ batch?.batchCode || '—' }}</span>
          </li>
          <li>
            <span class="fact-label">采收</span>
            <span class="fact-value mono">{{ formatDate(batch?.harvestDate) }}</span>
          </li>
          <li>
            <span class="fact-label">质检</span>
            <span class="fact-value">{{ qualitySummary }}</span>
          </li>
          <li>
            <span class="fact-label">环境采样</span>
            <span class="fact-value mono">{{ sampledCount || '—' }}</span>
          </li>
        </ul>
      </GlassPanel>

      <GlassPanel :class="`verdict tone-${verdict.tone}`">
        <div class="verdict-main">
          <p class="verdict-headline">{{ verdict.headline }}</p>
          <p class="verdict-detail">{{ verdict.detail }}</p>
        </div>
        <dl class="verdict-meta">
          <div>
            <dt>合约</dt>
            <dd class="mono" :title="verification?.contractAddress || ''">
              {{ shortHash(verification?.contractAddress, 10, 6) }}
            </dd>
          </div>
          <div>
            <dt>最近交易</dt>
            <dd class="mono" :title="verification?.latestTxHash || ''">
              {{ shortHash(verification?.latestTxHash, 10, 6) }}
            </dd>
          </div>
        </dl>
      </GlassPanel>

      <GlassPanel padded="large" class="block">
        <header class="block-head">
          <h2 class="block-title display">生长环境</h2>
          <span class="muted tiny">来自网关与田间巡检设备</span>
        </header>
        <div v-if="environments.length" class="metrics">
          <MetricBar
            v-for="env in environments"
            :key="`${env.deviceCode}-${env.metric}-${env.windowStart}`"
            :metric="env.metric || ''"
            :avg="env.avgValue"
            :min="env.minValue"
            :max="env.maxValue"
            :sample-count="env.sampleCount"
            :chain-status="env.chainStatus"
          />
        </div>
        <p v-else class="muted empty">生产阶段暂无可用的环境采样。</p>
      </GlassPanel>

      <GlassPanel padded="large" class="block">
        <header class="block-head">
          <h2 class="block-title display">农事历</h2>
          <span class="muted tiny">{{ events.length }} 条记录</span>
        </header>
        <p class="split-legend muted tiny">每条记录：左侧链下业务明细，右侧链上证据</p>
        <TraceTimeline :events="events" />
      </GlassPanel>

      <GlassPanel v-if="reports.length" padded="large" class="block">
        <header class="block-head">
          <h2 class="block-title display">质检报告</h2>
        </header>
        <ul class="reports">
          <li v-for="report in reports" :key="report.reportNo" class="report">
            <div class="report-main">
              <p class="report-no">报告 {{ report.reportNo }}</p>
              <p class="muted tiny">
                {{ report.agencyName || '检测机构未记录' }} · {{ formatDate(report.checkedAt) }}
              </p>
            </div>
            <StatusChip
              :tone="(report.result || '').toUpperCase().includes('FAIL') ? 'bad' : 'ok'"
              :label="report.result || '未知'"
              compact
            />
          </li>
        </ul>
      </GlassPanel>

      <GlassPanel padded="large" class="block ai" :class="{ off: !aiAvailable }">
        <header class="block-head">
          <h2 class="block-title display">问一问这批产品</h2>
          <StatusChip :tone="aiAvailable ? 'ok' : 'off'" :label="aiAvailable ? '可用' : '尚未接入'" compact />
        </header>
        <p class="lede">
          {{
            aiAvailable
              ? '可以就产地、生长环境、流转过程与食用建议提问。'
              : 'AI 助手还没有接入，所以这里暂时不能提问。它上线后只回答本页已存证的事实，健康功效与医疗建议类问题会被拒绝。'
          }}
        </p>
        <div class="ai-input" aria-hidden="true">
          <span class="muted">输入你关心的问题…</span>
          <span class="send" :class="{ disabled: !aiAvailable }">提问</span>
        </div>
        <p class="boundary muted tiny">
          事实与建议会分开呈现：链上已验证的内容标注为事实，其余为模型解释。
        </p>
      </GlassPanel>

      <footer class="foot">
        <p class="muted tiny">
          本页事实来源：FISCO BCOS 链上摘要 + 链下业务明细。未被链上记录的字段属于链下数据，
          不等同于链上存证。
        </p>
        <p class="muted tiny">
          单品状态 {{ item?.status || '—' }} · 二维码 {{ item?.qrStatus || '—' }} · NFC
          {{ item?.nfcStatus || '—' }}
        </p>
        <p v-if="verification?.records?.length" class="records">
          <span
            v-for="record in verification.records"
            :key="`${record.businessType}-${record.txHash}`"
            class="record"
          >
            {{ record.businessType }}
            <StatusChip :tone="chainStatusTone(record.chainStatus)" :label="record.payloadHashMatched ? '一致' : '需核对'" compact />
          </span>
        </p>
        <p class="muted tiny">生成时间 {{ formatDateTime(new Date().toISOString()) }}</p>
      </footer>
    </template>
  </div>
</template>

<style scoped>
.trace {
  flex: 1 1 auto;
  width: 100%;
  max-width: var(--content-max);
  margin: 0 auto;
  padding: var(--space-4) var(--space-4) var(--space-7);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  /* 公共页限宽 520px，栅格必须按容器宽度自适应，而不是按视口宽度 */
  container-type: inline-size;
}

.bar {
  position: sticky;
  top: var(--space-3);
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 9px var(--space-4);
  border-radius: var(--radius-pill);
}

.brand {
  font-family: var(--font-display);
  font-size: var(--fs-sm);
  font-weight: 600;
  letter-spacing: 0.02em;
}

/* ---- hero ---- */

.hero {
  border-radius: var(--radius-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-4);
  align-items: start;
}

.hero-text {
  min-width: 0;
}

.code {
  margin-top: 5px;
  color: var(--slate);
  word-break: break-all;
}

.hero-title {
  margin-top: var(--space-3);
  font-size: var(--fs-hero);
  font-weight: 600;
}

.hero-sub {
  margin-top: 6px;
  color: var(--ink-70);
  font-size: var(--fs-sm);
}

.dot-sep {
  margin: 0 6px;
  color: var(--slate);
}

.facts {
  display: grid;
  /* 公共页最窄也在 400px 左右，2 列可保证批次号等长值不折行 */
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3) var(--space-4);
  border-top: 1px solid var(--glass-hairline);
  padding-top: var(--space-4);
}

.fact-label {
  display: block;
  font-size: var(--fs-micro);
  letter-spacing: 0.12em;
  color: var(--slate);
}

.fact-value {
  display: block;
  margin-top: 3px;
  font-size: var(--fs-sm);
}

/* ---- verdict ---- */

.verdict {
  --verdict-color: var(--state-disabled);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.verdict.tone-ok {
  --verdict-color: var(--state-confirmed);
}

.verdict.tone-wait {
  --verdict-color: var(--state-pending);
}

.verdict.tone-bad {
  --verdict-color: var(--state-failed);
}

.verdict.tone-off {
  --verdict-color: var(--state-disabled);
}

.verdict-headline {
  font-family: var(--font-display);
  font-size: var(--fs-h2);
  font-weight: 600;
  color: var(--verdict-color);
}

.verdict-detail {
  margin-top: 4px;
  font-size: var(--fs-sm);
  color: var(--ink-70);
}

.verdict-meta {
  margin: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  border-top: 1px solid var(--glass-hairline);
  padding-top: var(--space-3);
}

.verdict-meta dt {
  font-size: var(--fs-micro);
  letter-spacing: 0.1em;
  color: var(--slate);
  text-transform: uppercase;
}

.verdict-meta dd {
  margin: 3px 0 0;
  font-size: var(--fs-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- blocks ---- */

.block {
  border-radius: var(--radius-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.block-title {
  font-size: var(--fs-h2);
  font-weight: 600;
}

.tiny {
  font-size: var(--fs-micro);
}

.metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4) var(--space-5);
}

.split-legend {
  margin-top: calc(var(--space-2) * -1);
}

.reports {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.report {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  border-top: 1px solid var(--glass-hairline);
  padding-top: var(--space-3);
}

.report:first-child {
  border-top: 0;
  padding-top: 0;
}

.report-no {
  font-size: var(--fs-sm);
}

.lede {
  font-size: var(--fs-sm);
  color: var(--ink-70);
}

/* ---- ai ---- */

.ai-input {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 11px var(--space-3) 11px var(--space-4);
  border-radius: var(--radius-pill);
  border: 1px solid var(--glass-hairline);
  background: rgba(255, 255, 255, 0.42);
  font-size: var(--fs-sm);
}

.send {
  padding: 6px var(--space-4);
  border-radius: var(--radius-pill);
  background: var(--field);
  color: var(--mist);
  font-size: var(--fs-xs);
}

.send.disabled {
  background: var(--slate-tint);
  color: var(--slate);
}

.boundary {
  border-top: 1px solid var(--glass-hairline);
  padding-top: var(--space-3);
}

/* ---- foot ---- */

.foot {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0 var(--space-2);
}

.records {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin: 4px 0;
}

.record {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-xs);
  color: var(--ink-70);
}

/* ---- states ---- */

.empty {
  font-size: var(--fs-sm);
  padding: var(--space-2) 0;
}

.retry {
  align-self: flex-start;
  margin-top: var(--space-2);
  padding: 9px var(--space-5);
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--field);
  color: var(--mist);
  font-size: var(--fs-sm);
}

.sk-eyebrow,
.sk-title,
.sk-sub,
.sk-block {
  height: 14px;
  margin-bottom: 12px;
}

.sk-title {
  height: 34px;
  width: 70%;
}

.sk-sub {
  width: 46%;
}

.sk-block {
  height: 96px;
}
</style>
