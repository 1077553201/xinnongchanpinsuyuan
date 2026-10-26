import { readFile } from 'node:fs/promises'
import test from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'
const source = await readFile(new URL('../src/utils/rejections.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } })
const { rejectionActions } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
test('拒收处置必须经过隔离收货才能报损，终态仅允许追加说明', () => {
  assert.deepEqual(rejectionActions('OPEN'), ['RETURN_DISPATCH', 'QUARANTINE_RECEIVE', 'ANNOTATION'])
  assert.deepEqual(rejectionActions('RETURNING'), ['QUARANTINE_RECEIVE', 'ANNOTATION'])
  assert.deepEqual(rejectionActions('QUARANTINED'), ['DISPOSE', 'ANNOTATION'])
  assert.deepEqual(rejectionActions('DISPOSED'), ['ANNOTATION'])
  assert.deepEqual(rejectionActions('UNKNOWN'), [])
})
