import { get } from './http'
import type { ChainBatchView, ChainHealthResult, ChainItemView, ChainSensorWindowView } from '@/types/chain'

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
