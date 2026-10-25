export const EVIDENCE_KINDS: Record<string, string> = { PRODUCTION: '生产来源', CIRCULATION: '逐件流转', QUALITY: '质检版本', NFC: 'NFC 身份' }
export const RECONCILIATION_OUTCOMES: Record<string, { label: string; detail: string }> = {
  MATCHED: { label: '已找到，摘要一致', detail: '本次读取到对应链记录；这证明冻结记录一致，不代替实物或检测真实性验收。' },
  NOT_FOUND: { label: '本次未查到', detail: '未查到不等于原交易失败；应保留原意图，不能仅因等待时间长而重发。' },
  UNAVAILABLE: { label: '节点暂不可读', detail: '本次无法完成链上读取，请稍后重新核对，不推断交易结果。' },
  CONFLICT: { label: '链上摘要或归属冲突', detail: '停止补记和重发，检查来源、事件类型、时间及原始版本。' },
  LOCAL_CONFLICT: { label: '本地冻结资料不一致', detail: '原资料、摘要或交易意图不完整，请先核对留档，不能覆盖原版本。' },
  SOURCE_MISSING: { label: '前置来源未查到', detail: '本次未找到批次或单品来源，先核对生产证据，不跳过来源补记。' },
  PREVIOUS_MISSING: { label: '前序版本未核验齐全', detail: '当前事件存在，但前序链证据未齐全，不将当前版本标为完整核验。' },
  READ_LIMIT: { label: '超过本次读取上限', detail: '本次最多核对 200 条单品事件；超过上限不判为不存在或一致。' },
}
export function reconciliationOutcome(value: string) {
  return RECONCILIATION_OUTCOMES[value] ?? { label: '结果待核对', detail: '未识别核对结果，保持原记录，不确认或重发。' }
}
export function canRecordReconciliation(value: { canRecord: boolean; processing: boolean; outcome: string; record: { chainStatus: string } }) {
  return value.canRecord && !value.processing && value.outcome === 'MATCHED' && value.record.chainStatus !== 'CONFIRMED'
}
