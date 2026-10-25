<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue'
import GlassPanel from '@/components/GlassPanel.vue'
import ProductionEvidencePanel from '@/components/ProductionEvidencePanel.vue'
import CirculationVersionView from '@/components/CirculationVersionView.vue'
import PackingQualityPanel from '@/components/PackingQualityPanel.vue'
import PackingNfcIdentity from '@/components/PackingNfcIdentity.vue'
import MetricBar from '@/components/MetricBar.vue'
import StatusChip from '@/components/StatusChip.vue'
import TraceTimeline from '@/components/TraceTimeline.vue'
import VerifySeal from '@/components/VerifySeal.vue'
import { fetchTrace } from '@/api/publicTrace'
import { ApiError, apiErrorMessage } from '@/api/http'
import { formatDate, formatDateTime, nfcStatusLabel, shortHash } from '@/types/api'
import type { PublicTraceView, TraceEnvironment } from '@/types/trace'
import { qualityAssessment, recordAssessment, traceVerdict, productionTraceVerdict } from '@/utils/traceVerdict'

const props = defineProps<{ traceCode: string }>()

const trace = ref<PublicTraceView | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const notFound = ref(false)
const queriedAt = ref('')
let revision = 0
onScopeDispose(() => { revision += 1 })

async function load(code: string) {
  const current = ++revision
  loading.value = true
  error.value = null
  notFound.value = false
  trace.value = null
  try {
    const result = await fetchTrace(code)
    if (current !== revision) return
    trace.value = result
    queriedAt.value = new Date().toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
  } catch (err) {
    if (current !== revision) return
    if (err instanceof ApiError && err.code === '400' && err.message === 'trace code not found') {
      notFound.value = true
    } else {
      error.value = apiErrorMessage(err)
    }
  } finally {
    if (current === revision) loading.value = false
  }
}

watch(() => props.traceCode, (code) => load(code), { immediate: true })

const product = computed(() => trace.value?.product)
const batch = computed(() => trace.value?.batch)
const item = computed(() => trace.value?.item)
const verification = computed(() => trace.value?.chainVerification)
const environments = computed<TraceEnvironment[]>(() => {
  const seen = new Set<string>()
  return (trace.value?.environmentSummary ?? []).filter(env => {
    const key = `${env.deviceCode}-${env.metric}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  }).slice(0, 8)
})
const reports = computed(() => trace.value?.qualityReports ?? [])
const events = computed(() => trace.value?.timeline ?? [])

const verdict = computed(() => trace.value?.productionSource ? productionTraceVerdict(verification.value) : traceVerdict(verification.value))
const sealAssessment = computed(() => ({
  tone: verdict.value.tone, label: verdict.value.headline, char: verdict.value.sealChar,
}))

const sampledCount = computed(() => trace.value?.productionEvidence
  ? trace.value.productionEvidence.environment.rows.reduce((total, row) => total + Number(row.sample_count ?? 0), 0)
  : environments.value.reduce((total, env) => total + (env.sampleCount ?? 0), 0))

const qualitySummary = computed(() => {
  if (trace.value?.productionSource) return trace.value.packingQuality?.total ? `公开记录 ${trace.value.packingQuality.total} 份` : '暂无公开报告'
  if (!reports.value.length) return '未上传'
  const assessments = reports.value.map((report) => qualityAssessment(report.result))
  if (assessments.some((result) => result.tone === 'bad')) return '存在不合格'
  return assessments.every((result) => result.tone === 'ok') ? '报告均合格' : '结果待确认'
})

const aiAvailable = computed(() => trace.value?.ai?.available === true)
</script>

<template>
  <div class="trace">
    <header class="bar glass glass-strong">
      <span class="brand">农产品可信溯源</span>
      <StatusChip
        :tone="loading ? 'wait' : error ? 'bad' : notFound ? 'off' : verdict.tone"
        :label="loading ? '查询中' : error ? '查询失败' : notFound ? '未找到记录' : verdict.headline"
        compact
      />
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
      <GlassPanel v-if="batch?.status === 'RECALL' || item?.status === 'RECALL'" class="recall-banner" role="alert">
        <strong>该产品或批次已召回</strong><p>请停止使用并联系销售方处理。摘要核验通过不代表当前产品仍适合销售或食用。</p>
      </GlassPanel>
      <GlassPanel padded="large" glow="field" class="hero">
        <div class="hero-grid">
          <div class="hero-text">
            <p class="eyebrow">溯源编号</p>
            <p class="mono code">{{ traceCode }}</p>
            <h1 class="hero-title display">{{ trace.productionSource?.productName || product?.productName || '未命名产品' }}</h1>
            <p class="hero-sub">
              <span>{{ trace.productionSource?.plotName || product?.originPlace || '产地未记录' }}</span>
              <span v-if="trace.productionSource?.variety || product?.variety" class="dot-sep">·</span>
              <span v-if="trace.productionSource?.variety || product?.variety">{{ trace.productionSource?.variety || product?.variety }}</span>
            </p>
          </div>
          <VerifySeal :assessment="sealAssessment" label="农产品可信溯源 · 记录核对" :size="104" />
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
            <span class="fact-label">{{ trace.productionEvidence ? '本版纳入的环境采样' : '近期环境采样' }}</span>
            <span class="fact-value mono">{{ sampledCount || '—' }}</span>
          </li>
        </ul>
      </GlassPanel>

      <GlassPanel v-if="trace.productionSource" padded="large" class="production-source">
        <p class="eyebrow">包装生产来源</p><h2 class="display">从这块地，到这一件包装</h2>
        <p>{{ trace.productionSource.verificationStatus === 'VERIFIED' ? '来源快照已包含在本次摘要核验一致的生产证据中。' : '来源为业务登记快照，目前尚未核验通过。' }}此处不代表完整流通过程、实物质量或设备身份已验证。</p>
        <dl class="source-facts"><div><dt>地块 / 种植周期</dt><dd>{{ trace.productionSource.plotName }} · {{ trace.productionSource.cycleCode }}</dd></div><div><dt>产品 / 品种</dt><dd>{{ trace.productionSource.productName }} · {{ trace.productionSource.variety }}</dd></div><div><dt>种植日期</dt><dd>{{ formatDate(trace.productionSource.plantedOn) }}</dd></div><div><dt>采收 / 分级</dt><dd>{{ trace.productionSource.harvestCode }} · {{ trace.productionSource.gradeName }}<br />{{ formatDateTime(trace.productionSource.harvestedAt) }}</dd></div><div><dt>实际包装</dt><dd>{{ trace.productionSource.packingCode }}<br />{{ formatDateTime(trace.productionSource.packedAt) }}</dd></div><div><dt>每码对应</dt><dd>1 {{ trace.productionSource.packageUnit }}完整包装 · 净重 {{ trace.productionSource.netWeightKg }} kg</dd></div></dl>
        <p class="source-hash">来源快照摘要：{{ trace.productionSource.sourceHash }}</p>
      </GlassPanel>
      <ProductionEvidencePanel v-if="trace.productionEvidence" :evidence="trace.productionEvidence" />
      <GlassPanel v-if="trace.packingNfc" padded="large"><PackingNfcIdentity :identity="trace.packingNfc" /></GlassPanel>
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
        <button class="action-button" type="button" @click="load(traceCode)">重新查询</button>
      </GlassPanel>

      <GlassPanel v-if="!trace.productionEvidence" padded="large" class="block">
        <header class="block-head">
          <h2 class="block-title display">本批次环境记录</h2>
          <span class="muted tiny">关联到当前批次的设备采样</span>
        </header>
        <div v-if="environments.length" class="metrics">
          <div v-for="env in environments" :key="`${env.deviceCode}-${env.metric}-${env.windowStart}`">
          <MetricBar
            :metric="env.metric || ''"
            :avg="env.avgValue"
            :min="env.minValue"
            :max="env.maxValue"
            :sample-count="env.sampleCount"
            :chain-status="env.chainStatus"
          />
          <p class="muted tiny">{{ env.deviceCode }} · {{ formatDateTime(env.windowStart) }} 起</p>
          </div>
        </div>
        <p v-if="!environments.length" class="muted empty">暂无可用的环境采样。</p>
      </GlassPanel>

      <GlassPanel padded="large" class="block">
        <header class="block-head">
          <h2 class="block-title display">{{ trace.productionSource ? '单品流转记录' : '农事历' }}</h2>
          <span class="muted tiny">{{ trace.productionSource ? (trace.circulationEvidence?.verificationStatus === 'VERIFIED' ? '当前流转摘要一致' : trace.circulation ? '流转待核验' : '尚未关联') : `${events.length} 条记录` }}</span>
        </header>
        <template v-if="trace.productionSource && trace.circulation">
          <p class="split-legend muted tiny">{{ trace.circulationEvidence?.verificationStatus === 'VERIFIED' ? '本件当前流转与最新冻结版本一致，且本次链上摘要核验一致。' : trace.circulationEvidence ? '本件当前流转尚未实时核验通过，请逐版查看状态；新事实可能还未纳入存证。' : '逐件流转业务留档 · 尚未提交链上存证。' }}上方生产证据的核验结果不包含以下流转记录。</p>
          <dl class="source-facts"><div><dt>本件销售出库</dt><dd>{{ formatDateTime(trace.circulation.dispatchedAt) }}</dd></div><div><dt>包装码关联留档</dt><dd>{{ formatDateTime(trace.circulation.linkedAt) }}</dd></div><div><dt>本件收货结果</dt><dd>{{ trace.circulation.receiptOutcome === 'ACCEPTED' ? '已逐件核对实收' : trace.circulation.receiptOutcome === 'REJECTED' ? '已逐件核对拒收' : '待逐件核对，不据运输单汇总推断' }}</dd></div><div v-if="trace.circulation.receiptRecordedAt"><dt>收货核对留档</dt><dd>{{ formatDateTime(trace.circulation.receiptRecordedAt) }}</dd></div></dl>
          <p v-if="trace.circulation.transportStatus === 'NOT_ARRANGED'" class="muted tiny">尚未安排本笔出库的运输。</p>
          <p v-else-if="trace.circulation.transportStatus === 'PLANNED'" class="muted tiny">已安排运输，尚未登记实际发运。</p>
          <ol class="circulation-events"><li v-for="(event,index) in trace.circulation.events" :key="index">{{ ({ DEPART: '实际发运', UPDATE: '运输进展留档', EXCEPTION: '运输异常留档', RECEIVE: '收货结果留档' } as Record<string,string>)[event.kind] || '运输记录' }} · {{ formatDateTime(event.occurredAt) }}</li></ol>
          <p v-if="trace.circulation.eventsTruncated" class="muted tiny">仅展示最近 200 条运输记录。</p>
          <p class="muted tiny">以上由经营方后台核对留档，尚非消费者身份核验或外部物流认证。</p>
          <template v-if="trace.circulationEvidence"><p v-if="!trace.circulationEvidence.currentMatchesLatest" class="muted tiny">当前业务记录已有新增内容，旧版核验结果仅适用于各自冻结的快照。</p><CirculationVersionView :versions="trace.circulationEvidence.versions" /></template>
        </template>
        <p v-else-if="!trace.productionSource" class="split-legend muted tiny">每条记录展示业务明细、保存的摘要及交易状态。</p>
        <TraceTimeline v-if="!trace.productionSource || events.length" :events="events" />
        <p v-else-if="!trace.circulation" class="muted tiny">尚未建立本件产品的销售、运输及签收关联。生产农事请查看上方生产证据。</p>
      </GlassPanel>

      <PackingQualityPanel v-if="trace.productionSource" :quality="trace.packingQuality" />
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
              v-bind="qualityAssessment(report.result)"
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
          本页展示业务明细与交易记录。只有本次链上读取成功且摘要核对一致时才显示通过；
          环境记录按当前批次关联展示，交易确认不等同于产品质量保证。
        </p>
        <p class="muted tiny">
          单品状态 {{ item?.status || '—' }} · 二维码 {{ item?.qrStatus || '—' }} · NFC
          {{ nfcStatusLabel(item?.nfcStatus) }}
        </p>
        <p v-if="verification?.records?.length" class="records">
          <span
            v-for="record in verification.records"
            :key="`${record.businessType}-${record.txHash}`"
            class="record"
          >
            {{ record.businessType }}
            <StatusChip v-bind="recordAssessment(record)" compact />
          </span>
        </p>
        <p class="muted tiny">查询时间 {{ queriedAt }}（北京时间）</p>
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
.recall-banner { color: var(--seal); background: var(--seal-tint); border-radius: var(--radius-lg); }
.recall-banner p { margin-top: 8px; font-size: var(--fs-sm); }

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

<style scoped>
.production-source h2 { margin: 12px 0; font-size: var(--fs-h2); }.production-source > p { line-height: 1.8; color: var(--slate); }.source-facts { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 18px; margin: 22px 0; }.source-facts dt { color: var(--slate); font-size: var(--fs-xs); margin-bottom: 6px; }.source-facts dd { margin: 0; overflow-wrap: anywhere; line-height: 1.8; }.source-hash { overflow-wrap: anywhere; font-size: 11px; }@media(max-width:600px) { .source-facts { grid-template-columns: minmax(0,1fr); } }
</style>
