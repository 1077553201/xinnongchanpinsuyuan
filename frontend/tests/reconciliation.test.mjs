import { readFile } from 'node:fs/promises'
import test from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'
const source = await readFile(new URL('../src/utils/reconciliation.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } })
const { canRecordReconciliation, reconciliationOutcome } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
test('未找到、读取失败、冲突及读取上限分别解释，不误报一致', () => {
  const values = ['NOT_FOUND', 'UNAVAILABLE', 'CONFLICT', 'LOCAL_CONFLICT', 'SOURCE_MISSING', 'PREVIOUS_MISSING', 'READ_LIMIT']
  const labels = values.map(value => reconciliationOutcome(value).label)
  assert.equal(new Set(labels).size, values.length)
  assert.equal(reconciliationOutcome('UNKNOWN').label, '结果待核对')
})
test('只允许链上匹配且未处理中、未本地确认的记录补记', () => {
  const base = { canRecord: true, processing: false, outcome: 'MATCHED', record: { chainStatus: 'UNCERTAIN' } }
  assert.equal(canRecordReconciliation(base), true)
  for (const outcome of ['NOT_FOUND', 'CONFLICT', 'UNAVAILABLE', 'UNKNOWN']) assert.equal(canRecordReconciliation({ ...base, outcome }), false)
  assert.equal(canRecordReconciliation({ ...base, canRecord: false }), false)
  assert.equal(canRecordReconciliation({ ...base, processing: true }), false)
  assert.equal(canRecordReconciliation({ ...base, record: { chainStatus: 'CONFIRMED' } }), false)
})
