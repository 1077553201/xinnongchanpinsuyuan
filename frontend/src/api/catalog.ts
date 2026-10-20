import { get, post } from './http'
import type {
  BindNfcRequest,
  CloseBatchRequest,
  CreateBatchRequest,
  CreateProductRequest,
  CreateTraceEventRequest,
  DisableNfcRequest,
  GenerateItemsRequest,
  NfcTagEntity,
  ProductBatchEntity,
  ProductEntity,
  TraceEventEntity,
  TraceItemEntity,
} from '@/types/catalog'

/* ---- 产品 ---- */

export function listProducts(): Promise<ProductEntity[]> {
  return get<ProductEntity[]>('/products')
}

export function getProduct(id: number): Promise<ProductEntity> {
  return get<ProductEntity>(`/products/${id}`)
}

export function createProduct(body: CreateProductRequest): Promise<ProductEntity> {
  return post<ProductEntity>('/products', body)
}

/* ---- 批次 ---- */

export function listBatches(): Promise<ProductBatchEntity[]> {
  return get<ProductBatchEntity[]>('/batches')
}

export function getBatch(id: number): Promise<ProductBatchEntity> {
  return get<ProductBatchEntity>(`/batches/${id}`)
}

export function createBatch(body: CreateBatchRequest): Promise<ProductBatchEntity> {
  return post<ProductBatchEntity>('/batches', body)
}

export function closeBatch(id: number, body: CloseBatchRequest): Promise<ProductBatchEntity> {
  return post<ProductBatchEntity>(`/batches/${id}/close`, body)
}

/* ---- 单品码 ---- */

export function listItems(): Promise<TraceItemEntity[]> {
  return get<TraceItemEntity[]>('/items')
}

export function generateItems(body: GenerateItemsRequest): Promise<TraceItemEntity[]> {
  return post<TraceItemEntity[]>('/items/generate', body, 180000)
}

export function getItem(id: number): Promise<TraceItemEntity> {
  return get<TraceItemEntity>(`/items/${id}`)
}

/* ---- 溯源事件 ---- */

export function listEvents(itemId: number): Promise<TraceEventEntity[]> {
  return get<TraceEventEntity[]>(`/items/${itemId}/events`)
}

export function appendEvent(itemId: number, body: CreateTraceEventRequest): Promise<TraceEventEntity> {
  return post<TraceEventEntity>(`/items/${itemId}/events`, body)
}

/* ---- NFC ---- */

export function bindNfc(body: BindNfcRequest): Promise<NfcTagEntity> {
  return post<NfcTagEntity>('/nfc/bind', body)
}

export function disableNfc(id: number, body: DisableNfcRequest): Promise<NfcTagEntity> {
  return post<NfcTagEntity>(`/nfc/${id}/disable`, body)
}
