import { get, post } from './http'
import type { EvidenceKind, ReconciliationRow } from './evidenceReconciliation'
export interface ReceiptCheck {
  record: ReconciliationRow
  outcome: string
  canRecord: boolean
  alreadyRecorded: boolean
  processing: boolean
  transactionId?: number
  txHash?: string
  storedChainStatus?: string
  storedReceiptStatus?: string
  verifiedAt?: string
  checkHash: string
  checkedAt: string
  receipt: { found?: boolean; txHash?: string; contractAddress?: string; operator?: string; blockNumber?: number; receiptStatus?: number; inputMatched?: boolean; inputHash?: string; events?: Record<string, string>[] }
}
const path = (kind: EvidenceKind, id: number) => `/production/evidence-reconciliation/${kind}/${id}/receipt`
export const inspectReceipt = (kind: EvidenceKind, id: number) => get<ReceiptCheck>(path(kind, id), undefined, 160000)
export const recordReceipt = (kind: EvidenceKind, id: number, expectedCheckHash: string) => post<ReceiptCheck>(path(kind, id), { expectedCheckHash }, 160000)
