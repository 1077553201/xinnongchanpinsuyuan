export function receiptOutcome(value: string) {
  const labels: Record<string, [string, string]> = {
    SUCCESS_MATCHED: ['原回执执行成功，核对一致', '交易哈希、目标合约、原调用及事件与冻结操作一致；这不是实物或报告真实性保证。'],
    FAILED_MATCHED: ['原回执执行失败，调用归属一致', '实际回执表明这次调用失败。只留存原交易结果，不自动重发或改变经营记录。'],
    NO_INTENT: ['尚无原交易意图', '此操作尚无可核对的原提交记录，不创建新交易。'],
    MISSING_HASH: ['原提交缺少交易哈希', '不能读取指定交易的原回执，继续保留未知；不从业务证据推造交易哈希或收据。'],
    INVALID_HASH: ['原交易哈希格式不正确', '请核对原始提交记录，不能以其他哈希替换。'],
    NOT_FOUND: ['本次未查到原回执', '未查到不等于失败，请保留原意图并稍后只读核对。'],
    UNAVAILABLE: ['暂时无法读取原回执', '本次读取未完成，不推断原交易结果，也不会重发。'],
    LOCAL_CONFLICT: ['本地意图或冻结资料不一致', '先检查原交易、目标合约及冻结操作，保持原记录。'],
    CONFLICT: ['回执身份或原调用不一致', '停止留档，检查交易哈希、目标合约、调用内容和原提交人。'],
    EVENT_CONFLICT: ['成功回执的事件未核对通过', '不能仅凭成功状态码确认此业务；事件归属、摘要、时间或数量不一致。'],
    RECORDED_CONFLICT: ['本次回执与已核对留档不同', '停止覆盖原留档，检查节点、链配置及原始核对记录。'],
  }
  const result = labels[value] ?? ['结果待核对', '未识别的结果保持原记录，不确认或重发。']
  return { label: result[0], detail: result[1] }
}
export function canRecordReceipt(value: { outcome: string; canRecord: boolean; alreadyRecorded: boolean; processing: boolean }) {
  return value.canRecord && !value.alreadyRecorded && !value.processing && ['SUCCESS_MATCHED', 'FAILED_MATCHED'].includes(value.outcome)
}
