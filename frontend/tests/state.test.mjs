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

const { traceVerdict, recordAssessment, qualityAssessment } = await loadTypeScript('../src/utils/traceVerdict.ts')
const { chainHealthBadge } = await loadTypeScript('../src/utils/chainHealth.ts')

function confirmed() {
  return {
    latestChainStatus: 'CONFIRMED',
    itemPayloadHashMatched: true,
    batchPayloadHashMatched: true,
    records: ['BATCH_REGISTER', 'ITEM_BIND'].map((businessType) => ({
      businessType, chainStatus: 'CONFIRMED', txHash: '0xtransaction',
      payloadHash: '0xdigest', payloadHashMatched: true,
    })),
  }
}

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

for (const [status, tone] of [['FAILED', 'bad'], ['PENDING', 'wait'], ['CHAIN_DISABLED', 'off'], ['UNKNOWN', 'off']]) {
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

test('完整一致结果只说明保存记录一致，不能声称实时链上核验', () => {
  const result = traceVerdict(confirmed())
  assert.equal(result.tone, 'ok')
  assert.equal(result.headline, '摘要记录一致')
  assert.match(result.detail, /未实时读取/)
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
