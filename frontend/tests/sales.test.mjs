import { readFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'
const source = await readFile(new URL('../src/utils/sales.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } })
const { salesAmount } = await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)
test('金额按分精确累加，保留零价与两位小数', () => {
  assert.equal(salesAmount([{ quantity: '3', price: '0.10' }, { quantity: '2', price: '0.20' }]), '0.70')
  assert.equal(salesAmount([{ quantity: '2', price: '0' }]), '0.00')
})
test('大额多明细计算不丢整数精度', () => {
  assert.equal(salesAmount(Array.from({ length: 50 }, () => ({ quantity: '1000000', price: '999999999.99' }))), '49999999999500000.00')
})
test('兼容 Vue 数字输入自动转换的数值类型', () => {
  assert.equal(salesAmount([{ quantity: 3, price: 0.1 }, { quantity: 2, price: 2.5 }]), '5.30')
})
test('不对小数件数、过细价格及缺失字段自动舍入', () => {
  for (const line of [{ quantity: '1.5', price: '1' }, { quantity: '1', price: '0.001' }, { quantity: '0', price: '1' }, { quantity: '', price: '' }, { quantity: '1', price: '-1' }]) assert.equal(salesAmount([line]), null)
  assert.equal(salesAmount([]), null)
})
