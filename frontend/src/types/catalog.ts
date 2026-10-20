/** 与后端 model.entity 对齐的业务实体类型。 */

export interface ProductEntity {
  id: number
  organizationId: number
  productCode: string
  productName: string
  category: string
  variety?: string
  originPlace?: string
  description?: string
  status: string
}

export interface ProductBatchEntity {
  id: number
  productId: number
  organizationId: number
  batchCode: string
  productionArea?: string
  plantDate?: string
  harvestDate?: string
  batchMetadata?: string
  chainStatus: string
  status: string
  createTime?: string
  updateTime?: string
}

export interface TraceItemEntity {
  id: number
  batchId: number
  traceCodeHash: string
  traceCodeEncrypted?: string
  /** 开发期明文码，用于生成二维码，不作为链上参数。 */
  traceCodePlain?: string
  traceUrl?: string
  qrStatus: string
  nfcStatus: string
  chainStatus: string
  status: string
  createTime?: string
  updateTime?: string
}

export interface TraceEventEntity {
  id: number
  itemId: number
  batchId: number
  eventType: string
  eventTime: string
  location?: string
  operatorName?: string
  canonicalPayloadHash: string
  eventHash: string
  payload?: string
  chainStatus: string
  status: string
  createTime?: string
  updateTime?: string
}

export interface NfcTagEntity {
  chainStatus?: string
  id: number
  uidHash: string
  tagHash?: string
  itemId?: number
  bindStatus: string
  boundAt?: string
  status: string
  createTime?: string
  updateTime?: string
}

/* ---- 请求体 ---- */

export interface CreateProductRequest {
  organizationId: number
  productCode: string
  productName: string
  category: string
  variety?: string
  originPlace?: string
  description?: string
}

export interface CreateBatchRequest {
  productId: number
  organizationId: number
  batchCode: string
  productionArea?: string
  plantDate?: string
  harvestDate?: string
  metadata?: Record<string, unknown>
}

export interface CloseBatchRequest {
  /** 2 = CLOSED，3 = RECALL */
  status: number
  reasonPayload?: Record<string, unknown>
}

export interface GenerateItemsRequest {
  batchId: number
  count: number
}

export interface CreateTraceEventRequest {
  eventType: string
  /** 格式 yyyy-MM-dd HH:mm:ss */
  eventTime: string
  location?: string
  operatorName?: string
  payload?: Record<string, unknown>
}

export interface BindNfcRequest {
  itemId: number
  nfcUid: string
  tagHash?: string
  payload?: Record<string, unknown>
}

export interface DisableNfcRequest {
  reasonPayload?: Record<string, unknown>
}
