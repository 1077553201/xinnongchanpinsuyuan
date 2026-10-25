import { readFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'

// 使用项目现有 TypeScript 编译器执行纯状态逻辑，无需引入新测试依赖。
async function loadTypeScript(path) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  })
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
}

const { traceVerdict, productionTraceVerdict, recordAssessment, qualityAssessment } = await loadTypeScript('../src/utils/traceVerdict.ts')
const { chainHealthBadge } = await loadTypeScript('../src/utils/chainHealth.ts')
const { nfcStatusLabel } = await loadTypeScript('../src/types/api.ts')

test('NFC 绑定与停用同时显示清晰业务标签，保留未知状态边界', () => {
  assert.equal(nfcStatusLabel('BOUND:CONFIRMED'), '已绑定 NFC 标签')
  assert.equal(nfcStatusLabel('DISABLED:CONFIRMED'), 'NFC 标签已停用')
  assert.equal(nfcStatusLabel(undefined), 'NFC 状态待确认')
})

function confirmed() {
  return {
    verificationSource: 'LIVE_CHAIN', eventsComplete: true,
    latestChainStatus: 'CONFIRMED',
    itemPayloadHashMatched: true,
    batchPayloadHashMatched: true,
    records: ['BATCH_REGISTER', 'ITEM_BIND'].map((businessType) => ({
      businessType, verificationSource: 'LIVE_CHAIN', chainStatus: 'CONFIRMED', txHash: '0xtransaction',
      payloadHash: '0xdigest', payloadHashMatched: true,
    })),
  }
}

test('流转版本待确认时，生产来源独立核验且明确范围', () => {
  const value = confirmed()
  value.eventsComplete = false
  value.records.push({ businessType: 'TRACE_EVENT_APPEND', chainStatus: 'PENDING', verificationSource: 'UNAVAILABLE' })
  assert.equal(traceVerdict(value).tone, 'wait')
  assert.equal(productionTraceVerdict(value).tone, 'ok')
  assert.match(productionTraceVerdict(value).detail, /逐件流转证据在下方单独核验/)
})

test('生产来源范围仍要求实时链和完整批次、单品摘要', () => {
  for (const mutate of [value => { value.verificationSource = 'UNAVAILABLE' }, value => { value.batchPayloadHashMatched = false }, value => { value.records.shift() }]) {
    const value = confirmed()
    mutate(value)
    assert.notEqual(productionTraceVerdict(value).tone, 'ok')
  }
})

test('缺失响应、缺失摘要比较或缺失批次记录都不能通过', () => {
  for (const value of [undefined, {}, { latestChainStatus: 'CONFIRMED' }]) {
    assert.equal(traceVerdict(value).tone, 'off')
  }
  const value = confirmed()
  delete value.itemPayloadHashMatched
  assert.equal(traceVerdict(value).tone, 'off')
  const missingBatch = confirmed()
  missingBatch.records.shift()
  assert.equal(traceVerdict(missingBatch).tone, 'off')
})

for (const [status, tone] of [['FAILED', 'bad'], ['PENDING', 'wait'], ['CHAIN_DISABLED', 'off'], ['NOT_SUBMITTED', 'off'], ['UNKNOWN', 'off']]) {
  test(`最新交易为 ${status} 时不显示成功`, () => {
    assert.equal(traceVerdict({ ...confirmed(), latestChainStatus: status }).tone, tone)
  })
  test(`单品已确认但批次为 ${status} 时不显示成功`, () => {
    const value = confirmed()
    value.records[0].chainStatus = status
    assert.equal(traceVerdict(value).tone, tone)
  })
}

test('事件交易异常也阻止整体成功结论', () => {
  const value = confirmed()
  value.records.push({ businessType: 'TRACE_EVENT_APPEND', chainStatus: 'FAILED' })
  assert.equal(traceVerdict(value).tone, 'bad')
})

test('交易已确认但摘要不匹配时，结论和明细均标红', () => {
  const value = confirmed()
  value.records[0].payloadHashMatched = false
  assert.equal(traceVerdict(value).tone, 'bad')
  assert.equal(recordAssessment(value.records[0]).tone, 'bad')
})

test('缺少交易哈希或摘要时保持未知', () => {
  for (const field of ['txHash', 'payloadHash']) {
    const value = confirmed()
    value.records[0][field] = ''
    assert.equal(traceVerdict(value).tone, 'off')
    assert.equal(recordAssessment(value.records[0]).tone, 'off')
  }
})

test('完整一致结果须明确读取链上，缺少来源或事件完整性不能通过', () => {
  const result = traceVerdict(confirmed())
  assert.equal(result.tone, 'ok')
  assert.equal(result.headline, '链上摘要核验通过')
  assert.equal(traceVerdict({ ...confirmed(), verificationSource: 'UNAVAILABLE' }).tone, 'off')
  assert.equal(traceVerdict({ ...confirmed(), eventsComplete: false }).tone, 'bad')
})

test('未知质检结果不得显示合格', () => {
  for (const result of [undefined, '', 'PENDING', 'NOT_PASS', 'UNKNOWN']) {
    assert.equal(qualityAssessment(result).tone, 'off')
  }
  assert.equal(qualityAssessment('PASS').tone, 'ok')
  assert.equal(qualityAssessment('不合格').tone, 'bad')
})

test('连接健康与写链配置分别表达', () => {
  assert.equal(chainHealthBadge({ status: 'OK', chainEnabled: false }).tone, 'ok')
  assert.equal(chainHealthBadge({ status: 'FAILED', chainEnabled: true }).tone, 'bad')
  assert.equal(chainHealthBadge({ chainEnabled: true }).tone, 'off')
  assert.equal(chainHealthBadge(null).tone, 'off')
})
