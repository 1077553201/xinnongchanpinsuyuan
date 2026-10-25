import { get, post } from './http'

export type EvidenceKind = 'PRODUCTION' | 'CIRCULATION' | 'QUALITY' | 'NFC'
export interface ReconciliationRow {
  kind: EvidenceKind
  recordId: number
  packingId: number
  itemId?: number
  reportId?: number
  cycleId: number
  organizationId: number
  action: string
  version: number
  frozenHash: string
  chainStatus: string
  transactionStatus?: string
  receiptStatus?: string
  txId?: number
  errorCode?: string
  traceCode?: string
  plotName: string
  packingCode: string
  reportNo?: string
  recordedAt: string
  submittedAt?: string
}
export interface ReconciliationList {
  rows: ReconciliationRow[]
  total: number
  page: number
  limit: number
  checkedAt: string
}
export interface ReconciliationCheck {
  record: ReconciliationRow
  outcome: string
  disposition: string
  processing: boolean
  canRecord: boolean
  checkHash: string
  checkedAt: string
  transactionStatus?: string
  receiptStatus?: string
}
const base = '/production/evidence-reconciliation'
export const reconciliationList = (filters: Record<string, unknown>) => get<ReconciliationList>(base, filters)
export const inspectEvidence = (kind: EvidenceKind, id: number) => get<ReconciliationCheck>(`${base}/${kind}/${id}`, undefined, 160000)
export const recordReconciliation = (kind: EvidenceKind, id: number, expectedCheckHash: string) => post<ReconciliationCheck>(`${base}/${kind}/${id}/reconcile`, { expectedCheckHash }, 160000)
