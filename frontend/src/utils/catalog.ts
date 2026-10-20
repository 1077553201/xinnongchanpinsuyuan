import type { CreateBatchRequest, CreateProductRequest, ProductEntity } from '../types/catalog'
import type { Tone } from '../types/api'

export interface ProductDraft {
  organizationId: string; productCode: string; productName: string; category: string
  variety: string; originPlace: string; description: string
}
export interface BatchDraft {
  productId: string; organizationId: string; batchCode: string; productionArea: string
  plantDate: string; harvestDate: string; metadata: string
}
export type FieldErrors = Record<string, string>
const positiveId = (value: string) => Number.isSafeInteger(Number(value)) && Number(value) > 0

export function validateProduct(draft: ProductDraft, existingCodes: string[] = []): FieldErrors {
  const errors: FieldErrors = {}
  if (!positiveId(draft.organizationId)) errors.organizationId = '请填写有效的正整数机构编号。'
  for (const [key, label, max] of [
    ['productCode', '产品编码', 64], ['productName', '产品名称', 128], ['category', '品类', 64],
  ] as const) {
    if (!draft[key].trim()) errors[key] = `请填写${label}。`
    else if (draft[key].trim().length > max) errors[key] = `${label}最多 ${max} 个字符。`
  }
  if (existingCodes.some((code) => code.toLowerCase() === draft.productCode.trim().toLowerCase())) errors.productCode = '该产品编码已存在。'
  if (draft.variety.trim().length > 128) errors.variety = '品种最多 128 个字符。'
  if (draft.originPlace.trim().length > 255) errors.originPlace = '产地最多 255 个字符。'
  if (draft.description.trim().length > 5000) errors.description = '产品说明最多 5000 个字符。'
  return errors
}

function validDate(value: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(`${value}T00:00:00Z`))
    && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value
}
export function validateBatch(draft: BatchDraft, products: ProductEntity[], existingCodes: string[] = []): FieldErrors {
  const errors: FieldErrors = {}
  const product = products.find((product) => product.id === Number(draft.productId))
  if (!positiveId(draft.productId) || !product) errors.productId = '请选择已存在的产品。'
  if (!positiveId(draft.organizationId)) errors.organizationId = '请填写有效的正整数机构编号。'
  else if (product && Number(draft.organizationId) !== product.organizationId) errors.organizationId = '批次机构必须与所属产品一致。'
  if (!draft.batchCode.trim()) errors.batchCode = '请填写批次编码。'
  else if (draft.batchCode.trim().length > 64) errors.batchCode = '批次编码最多 64 个字符。'
  if (existingCodes.some((code) => code.toLowerCase() === draft.batchCode.trim().toLowerCase())) errors.batchCode = '该批次编码已存在。'
  if (draft.productionArea.trim().length > 255) errors.productionArea = '生产地块最多 255 个字符。'
  for (const key of ['plantDate', 'harvestDate'] as const) {
    if (draft[key] && !validDate(draft[key])) errors[key] = '请填写有效日期。'
  }
  if (draft.plantDate && draft.harvestDate && draft.harvestDate < draft.plantDate) errors.harvestDate = '采收日期不能早于种植日期。'
  if (draft.metadata.trim()) {
    try {
      const value = JSON.parse(draft.metadata)
      if (!value || typeof value !== 'object' || Array.isArray(value)) errors.metadata = '扩展资料须为 JSON 对象。'
    } catch { errors.metadata = '扩展资料不是有效的 JSON。' }
  }
  return errors
}

export function productPayload(draft: ProductDraft): CreateProductRequest {
  return {
    organizationId: Number(draft.organizationId), productCode: draft.productCode.trim(),
    productName: draft.productName.trim(), category: draft.category.trim(),
    variety: draft.variety.trim() || undefined, originPlace: draft.originPlace.trim() || undefined,
    description: draft.description.trim() || undefined,
  }
}
export function batchPayload(draft: BatchDraft): CreateBatchRequest {
  return {
    productId: Number(draft.productId), organizationId: Number(draft.organizationId),
    batchCode: draft.batchCode.trim(), productionArea: draft.productionArea.trim() || undefined,
    plantDate: draft.plantDate || undefined, harvestDate: draft.harvestDate || undefined,
    metadata: draft.metadata.trim() ? JSON.parse(draft.metadata) : undefined,
  }
}
export function batchStatusLabel(status?: string): string {
  return ({ ACTIVE: '生产中', CLOSED: '已关闭', RECALL: '已召回' } as Record<string, string>)[status ?? ''] ?? '状态未知'
}
export function batchSaveFeedback(status?: string): { tone: Tone; message: string } {
  if (status === 'CONFIRMED') return { tone: 'ok', message: '业务记录已保存，上链已确认。' }
  if (status === 'CHAIN_DISABLED') return { tone: 'off', message: '业务记录已保存，本次未启用上链。' }
  if (status === 'FAILED') return { tone: 'bad', message: '业务记录已保存，但上链失败。请核查记录，勿重复创建。' }
  return { tone: 'wait', message: '业务记录已保存，上链结果尚未确认。请稍后刷新核对。' }
}
