import type { Tone } from '../types/api'
import type { TraceChainVerification, TraceVerifyRecord } from '../types/trace'

export interface TraceVerdict {
  tone: Tone
  headline: string
  detail: string
  sealChar: string
}

const unknown: TraceVerdict = {
  tone: 'off', headline: '验证信息不完整', sealChar: '待',
  detail: '暂未取得完整的交易和摘要记录，无法作出验证结论。',
}

/** Production verification has its own scope; pending circulation versions cannot invalidate its digest. */
export function productionTraceVerdict(value?: TraceChainVerification): TraceVerdict {
  if (!value) return unknown
  const records = (value.records ?? []).filter(record => ['BATCH_REGISTER', 'ITEM_BIND'].includes(record.businessType ?? ''))
  const item = records.find(record => record.businessType === 'ITEM_BIND')
  const result = traceVerdict({ ...value, records, latestChainStatus: item?.chainStatus, eventsComplete: true })
  return result.tone === 'ok'
    ? { ...result, headline: '生产来源摘要核验通过', detail: '本次核对范围为包装来源与生产证据。逐件流转证据在下方单独核验，后补事实也可能尚未提交存证。此结果不替代产品质量检测。' }
    : result
}

/** 只在后端明确完成实时链上读取、必需摘要和事件完整性核对时给出通过结论。 */
export function traceVerdict(value?: TraceChainVerification): TraceVerdict {
  if (!value) return unknown
  const records = value.records ?? []
  const statuses = [value.latestChainStatus, ...records.map((record) => record.chainStatus)]
  if (statuses.includes('FAILED')) {
    return { tone: 'bad', headline: '存在上链失败记录', sealChar: '误', detail: '部分交易未成功，请联系生产方核实。' }
  }
  if (statuses.includes('CHAIN_DISABLED')) {
    return { tone: 'off', headline: '存在未上链记录', sealChar: '未', detail: '部分记录未启用上链，尚不能确认链上存证。' }
  }
  if (statuses.includes('PENDING')) {
    return { tone: 'wait', headline: '等待上链确认', sealChar: '待', detail: '部分交易仍待确认，请稍后重新查询。' }
  }
  if (statuses.includes('NOT_SUBMITTED')) {
    return { tone: 'off', headline: '来源尚未提交存证', sealChar: '未', detail: '当前可查询已登记的包装来源；尚未提交链上存证，不能认定已完成链上核验。' }
  }
  if (value.verificationSource !== 'LIVE_CHAIN') {
    return { tone: 'off', headline: '链上核验暂不可用', sealChar: '待', detail: '未完成本次链上摘要读取，请稍后重新查询。保存的交易记录仍可供查看。' }
  }
  if (value.eventsComplete === false) {
    return { tone: 'bad', headline: '流转记录不完整', sealChar: '疑', detail: '业务明细与链上的事件数量不一致，请联系生产方核实。' }
  }
  if (value.eventsComplete !== true) return unknown
  const batch = records.find((record) => record.businessType === 'BATCH_REGISTER')
  const item = records.find((record) => record.businessType === 'ITEM_BIND')
  if (!batch || !item || statuses.some((status) => status !== 'CONFIRMED')
      || records.some((record) => record.verificationSource !== 'LIVE_CHAIN' || !record.txHash?.trim() || !record.payloadHash?.trim())) return unknown
  const matches = [value.batchPayloadHashMatched, value.itemPayloadHashMatched,
    ...records.map((record) => record.payloadHashMatched)]
  if (matches.includes(false)) {
    return {
      tone: 'bad', headline: '摘要记录不一致', sealChar: '疑',
      detail: '明细摘要与保存的交易摘要不一致，请联系生产方核实。',
    }
  }
  if (matches.some((matched) => matched !== true)) return unknown
  return {
    tone: 'ok', headline: '链上摘要核验通过', sealChar: '验',
    detail: '批次、单品和已展示事件的明细摘要与本次读取的链上摘要一致，事件数量一致。此结果不替代产品质量检测。',
  }
}

export function recordAssessment(record: TraceVerifyRecord): { tone: Tone; label: string } {
  if (record.chainStatus === 'FAILED') return { tone: 'bad', label: '上链失败' }
  if (record.chainStatus === 'CHAIN_DISABLED') return { tone: 'off', label: '未上链' }
  if (record.chainStatus === 'PENDING') return { tone: 'wait', label: '待确认' }
  if (record.verificationSource !== 'LIVE_CHAIN') return { tone: 'off', label: '未实时核验' }
  if (record.chainStatus !== 'CONFIRMED' || !record.txHash?.trim() || !record.payloadHash?.trim()
      || typeof record.payloadHashMatched !== 'boolean') return { tone: 'off', label: '待核对' }
  return record.payloadHashMatched
    ? { tone: 'ok', label: '记录一致' }
    : { tone: 'bad', label: '摘要不一致' }
}

export function qualityAssessment(result?: string): { tone: Tone; label: string } {
  const value = result?.trim().toUpperCase()
  if (value === 'PASS' || value === '合格') return { tone: 'ok', label: '合格' }
  if (value === 'FAIL' || value === '不合格') return { tone: 'bad', label: '不合格' }
  return { tone: 'off', label: '结果待确认' }
}
