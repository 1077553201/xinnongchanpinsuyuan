import { readFile } from 'node:fs/promises'
import test from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'
const source = await readFile(new URL('../src/utils/receiptVerification.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } })
const { canRecordReceipt, receiptOutcome } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
test('缺少哈希、回执未找到、读取失败及归属冲突分别解释', () => {
  const values = ['MISSING_HASH', 'NOT_FOUND', 'UNAVAILABLE', 'LOCAL_CONFLICT', 'CONFLICT', 'EVENT_CONFLICT', 'RECORDED_CONFLICT']
  assert.equal(new Set(values.map(v => receiptOutcome(v).label)).size, values.length)
  assert.equal(receiptOutcome('UNKNOWN').label, '结果待核对')
})
test('只有实际匹配的成功或失败回执可显式留档，已保存及处理中不可重复', () => {
  const base = { outcome: 'SUCCESS_MATCHED', canRecord: true, processing: false, alreadyRecorded: false }
  assert.equal(canRecordReceipt(base), true)
  assert.equal(canRecordReceipt({ ...base, outcome: 'FAILED_MATCHED' }), true)
  for (const outcome of ['MISSING_HASH', 'NOT_FOUND', 'UNAVAILABLE', 'CONFLICT', 'EVENT_CONFLICT']) assert.equal(canRecordReceipt({ ...base, outcome }), false)
  assert.equal(canRecordReceipt({ ...base, processing: true }), false)
  assert.equal(canRecordReceipt({ ...base, alreadyRecorded: true }), false)
  assert.equal(canRecordReceipt({ ...base, canRecord: false }), false)
})
