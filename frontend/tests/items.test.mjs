import { readFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'
const source = await readFile(new URL('../src/utils/items.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } })
const { validateCount, traceUrl, eventTime } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
test('批量生成只接受 1–100 的整数', () => {
  for (const value of [0, -1, 1.5, 101, NaN]) assert.equal(validateCount(value), false)
  assert.equal(validateCount(100), true)
})
test('二维码地址只使用安全的 HTTP 前端地址和编码后的溯源编号', () => {
  assert.equal(traceUrl('ABC/123', 'http://192.168.1.8:5173'), 'http://192.168.1.8:5173/t/ABC%2F123')
  assert.throws(() => traceUrl('ABC', 'javascript:alert(1)'))
  assert.throws(() => traceUrl('ABC', 'https://user:password@example.com'))
  assert.throws(() => traceUrl('', 'http://localhost'))
})
test('事件时间使用契约规定的北京时间文本并补齐秒', () => {
  assert.equal(eventTime('2026-10-20T10:30'), '2026-10-20 10:30:00')
  assert.equal(eventTime('2026-10-20T10:30:15'), '2026-10-20 10:30:15')
  assert.throws(() => eventTime(''))
})
