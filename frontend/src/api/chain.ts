import { get } from './http'
import type { ChainBatchView, ChainHealthResult, ChainItemView, ChainSensorWindowView, ChainTransaction } from '@/types/chain'

export function listTransactions(params?: { businessType?: string; businessId?: number; chainStatus?: string; limit?: number }): Promise<ChainTransaction[]> { return get('/chain/transactions', params) }
export function getTransaction(id: number): Promise<ChainTransaction> { return get(`/chain/transactions/${id}`) }

export function chainHealth(): Promise<ChainHealthResult> {
  return get<ChainHealthResult>('/chain/health')
}

export function getChainBatch(batchCode: string): Promise<ChainBatchView> {
  return get<ChainBatchView>(`/chain/batches/${encodeURIComponent(batchCode)}`)
}

export function getChainItem(itemCode: string): Promise<ChainItemView> {
  return get<ChainItemView>(`/chain/items/${encodeURIComponent(itemCode)}`)
}

export function getChainSensorWindow(batchCode: string, index: number): Promise<ChainSensorWindowView> {
  return get<ChainSensorWindowView>(
    `/chain/batches/${encodeURIComponent(batchCode)}/sensor-windows/${index}`,
  )
}
