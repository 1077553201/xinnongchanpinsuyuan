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

/** 当前接口只比较数据库中的交易摘要，不提供实时链上核验结果。 */
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
  const batch = records.find((record) => record.businessType === 'BATCH_REGISTER')
  const item = records.find((record) => record.businessType === 'ITEM_BIND')
  if (!batch || !item || statuses.some((status) => status !== 'CONFIRMED')
      || records.some((record) => !record.txHash?.trim() || !record.payloadHash?.trim())) return unknown
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
    tone: 'ok', headline: '摘要记录一致', sealChar: '录',
    detail: '批次与单品明细摘要与保存的交易记录一致，交易状态已确认。本次查询未实时读取链上摘要。',
  }
}

export function recordAssessment(record: TraceVerifyRecord): { tone: Tone; label: string } {
  if (record.chainStatus === 'FAILED') return { tone: 'bad', label: '上链失败' }
  if (record.chainStatus === 'CHAIN_DISABLED') return { tone: 'off', label: '未上链' }
  if (record.chainStatus === 'PENDING') return { tone: 'wait', label: '待确认' }
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
