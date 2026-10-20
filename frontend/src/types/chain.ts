/** 与后端链层 VO 对齐的类型。 */

export interface ChainTransaction {
  id: number
  businessType: string
  businessId: number
  chainStatus: string
  txHash?: string
  blockNumber?: number
  receiptStatus?: string
  contractAddress?: string
  eventName?: string
  payloadHash?: string
  chainOperator?: string
  errorMessage?: string
  submittedAt?: string
  confirmedAt?: string
}

export interface ChainHealthResult {
  groupId?: string
  peers?: string[]
  contractAddress?: string
  chainEnabled?: boolean
  accountAddress?: string
  writerStatus?: boolean
  latestBlock?: number | string
  status?: string
  errorMessage?: string
}

export interface ChainTxResult {
  chainStatus?: string
  success?: boolean
  txHash?: string
  blockNumber?: number | string
  receiptStatus?: string
  contractAddress?: string
  eventName?: string
  payloadHash?: string
  chainOperator?: string
  errorMessage?: string
}

export interface ChainBatchView {
  batchIdHash?: string
  exists?: boolean
  productIdHash?: string
  payloadHash?: string
  status?: number
  startedAt?: number
  closedAt?: number
  closeReasonHash?: string
  operator?: string
  sensorWindowCount?: number
}

export interface ChainItemView {
  itemCodeHash?: string
  exists?: boolean
  batchIdHash?: string
  payloadHash?: string
  status?: number
  nfcUidHash?: string
  createdAt?: number
  updatedAt?: number
  statusReasonHash?: string
  operator?: string
  traceEventCount?: number
}

export interface ChainSensorWindowView {
  batchIdHash?: string
  index?: number
  windowIdHash?: string
  deviceCodeHash?: string
  payloadHash?: string
  windowStart?: number
  windowEnd?: number
  recordedAt?: number
  operator?: string
}
