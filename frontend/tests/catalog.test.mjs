import { readFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'

const source = await readFile(new URL('../src/utils/catalog.ts', import.meta.url), 'utf8')
const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } })
const { validateProduct, validateBatch, productPayload, batchPayload, batchSaveFeedback } =
  await import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`)

const product = { id: 1, organizationId: 7, productName: '测试产品', productCode: 'P-1' }
const productDraft = () => ({ organizationId: '7', productCode: ' P-2 ', productName: ' 测试产品 ', category: ' 坚果 ', variety: '', originPlace: '', description: '' })
const batchDraft = () => ({ productId: '1', organizationId: '7', batchCode: ' B-1 ', productionArea: '', plantDate: '2026-03-01', harvestDate: '2026-10-01', metadata: '' })

test('拒绝空白产品名、重复编码和无效机构编号', () => {
  const draft = productDraft()
  draft.productName = '   '
  draft.organizationId = '1.5'
  const errors = validateProduct(draft, ['p-2'])
  assert.ok(errors.productName)
  assert.ok(errors.organizationId)
  assert.ok(errors.productCode)
  for (const id of ['', '0', '-1', 'NaN', '9007199254740992']) {
    assert.ok(validateProduct({ ...productDraft(), organizationId: id }).organizationId)
  }
})
test('产品字段长度与数据库约束一致', () => {
  assert.ok(validateProduct({ ...productDraft(), productCode: 'x'.repeat(65) }).productCode)
  assert.ok(validateProduct({ ...productDraft(), productName: 'x'.repeat(129) }).productName)
  assert.ok(validateProduct({ ...productDraft(), originPlace: 'x'.repeat(256) }).originPlace)
})
test('产品请求去掉首尾空白，省略未填写的可选字段', () => {
  assert.deepEqual(productPayload(productDraft()), {
    organizationId: 7, productCode: 'P-2', productName: '测试产品', category: '坚果',
    variety: undefined, originPlace: undefined, description: undefined,
  })
})
test('批次必须关联已有产品并继承机构', () => {
  assert.ok(validateBatch(batchDraft(), []).productId)
  assert.ok(validateBatch({ ...batchDraft(), organizationId: '8' }, [product]).organizationId)
})
test('批次不接受倒置日期、无效日期或重复编码', () => {
  assert.ok(validateBatch({ ...batchDraft(), harvestDate: '2026-02-01' }, [product]).harvestDate)
  assert.ok(validateBatch({ ...batchDraft(), plantDate: '2026-02-30' }, [product]).plantDate)
  assert.ok(validateBatch(batchDraft(), [product], ['b-1']).batchCode)
})
test('扩展资料只接受 JSON 对象', () => {
  for (const metadata of ['[]', 'null', '7', 'bad json']) {
    assert.ok(validateBatch({ ...batchDraft(), metadata }, [product]).metadata)
  }
  assert.deepEqual(validateBatch({ ...batchDraft(), metadata: '{"note":"test"}' }, [product]), {})
})
test('日期可省略，批次请求保留标准日期和结构化扩展资料', () => {
  assert.deepEqual(validateBatch({ ...batchDraft(), plantDate: '', harvestDate: '' }, [product]), {})
  const payload = batchPayload({ ...batchDraft(), metadata: '{"note":"test"}' })
  assert.equal(payload.batchCode, 'B-1')
  assert.equal(payload.plantDate, '2026-03-01')
  assert.deepEqual(payload.metadata, { note: 'test' })
})
test('保存结果与上链结果分别表达', () => {
  assert.equal(batchSaveFeedback('CONFIRMED').tone, 'ok')
  assert.equal(batchSaveFeedback('FAILED').tone, 'bad')
  assert.match(batchSaveFeedback('FAILED').message, /已保存.*上链失败/)
  assert.equal(batchSaveFeedback('CHAIN_DISABLED').tone, 'off')
  assert.equal(batchSaveFeedback('PENDING').tone, 'wait')
  assert.equal(batchSaveFeedback().tone, 'wait')
})
