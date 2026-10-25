/**
 * 公众查询聚合结构，与后端 org.nongchanpin.model.vo.PublicTraceView 对齐。
 * 后端开启了 non_null 序列化策略，未取到值的字段不会出现在响应里，因此全部可选。
 */

export interface TraceProduct {
  productCode?: string
  productName?: string
  category?: string
  variety?: string
  originPlace?: string
  description?: string
}

export interface TraceBatch {
  batchCode?: string
  productionArea?: string
  plantDate?: string
  harvestDate?: string
  chainStatus?: string
  status?: string
}

export interface TraceItem {
  traceCodeHash?: string
  traceUrl?: string
  qrStatus?: string
  nfcStatus?: string
  chainStatus?: string
  status?: string
}

export interface TraceNfc {
  uidHash?: string
  bindStatus?: string
  status?: string
}

export interface TraceEvent {
  eventType?: string
  eventTime?: string
  location?: string
  operatorName?: string
  payloadHash?: string
  chainStatus?: string
}

export interface TraceQuality {
  reportNo?: string
  agencyName?: string
  result?: string
  reportHash?: string
  checkedAt?: string
}

export interface TraceEnvironment {
  deviceCode?: string
  metric?: string
  windowStart?: string
  windowEnd?: string
  minValue?: string
  maxValue?: string
  avgValue?: string
  sampleCount?: number
  dataHash?: string
  chainStatus?: string
}

export interface TraceVerifyRecord {
  verificationSource?: string
  chainPayloadHash?: string
  businessType?: string
  chainStatus?: string
  txHash?: string
  payloadHash?: string
  payloadHashMatched?: boolean
}

export interface TraceChainVerification {
  verificationSource?: string
  eventsComplete?: boolean
  contractAddress?: string
  latestTxHash?: string
  latestChainStatus?: string
  itemPayloadHashMatched?: boolean
  batchPayloadHashMatched?: boolean
  records?: TraceVerifyRecord[]
}

export interface TraceAiStatus {
  available?: boolean
  provider?: string
}

export interface PublicTraceView {
  packingNfc?: import('@/api/packingNfc').NfcIdentity
  packingQuality?: import('@/api/sourceQuality').PackingQuality
  circulationEvidence?: import('@/api/circulationEvidence').PublicCirculationEvidence
  circulation?: import('@/api/itemCirculation').PublicCirculation
  productionEvidence?: import('@/api/packingEvidence').ProductionEvidence
  productionSource?: import('@/api/packingCodes').PackingSource
  product?: TraceProduct
  batch?: TraceBatch
  item?: TraceItem
  nfc?: TraceNfc
  timeline?: TraceEvent[]
  qualityReports?: TraceQuality[]
  environmentSummary?: TraceEnvironment[]
  chainVerification?: TraceChainVerification
  ai?: TraceAiStatus
}
