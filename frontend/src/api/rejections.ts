import { get, post } from './http'

export interface RejectionSource {
  dispatchId: number; itemId: number; traceCode: string; packingCode: string; productName: string; variety: string
  packageUnit: string; netWeightKg: number; receiptOccurredAt: string; receiptRecordedAt: string
}
export interface RejectionCase {
  id: number; dispatchId: number; itemId: number; traceCode: string; packingCode: string; productName: string; variety: string
  status: string; revision: number; stateHash: string; packageUnit: string; netWeightKg: number
  warehouseId?: number; warehouseName?: string; quarantineLocation?: string; reason: string; recordedAt: string; recordedBy: number
}
export interface RejectionEvent {
  revision: number; kind: string; occurredAt: string; recordedAt: string; recordedBy: number; performerName: string; note: string
  reference?: string; warehouseName?: string; quarantineLocation?: string; targetRevision?: number; eventHash: string
}
export interface RejectionDetail { record: RejectionCase; source: RejectionSource; events: RejectionEvent[] }
export interface RejectionList {
  rows: RejectionCase[]; total: number; page: number; limit: number
  quarantineUnits: { packageUnit: string; packageCount: number; weightKg: number }[]
}
export interface RejectionAppend {
  kind: string; occurredAt?: string; warehouseId?: number; quarantineLocation?: string; targetRevision?: number
  performerName: string; note: string; reference?: string; expectedStateHash: string; requestKey: string
}
export const rejectionList = (warehouseId?: number, status = 'ALL', code = '', page = 1) => get<RejectionList>('/production/rejections', { warehouseId, status, code, page })
export const rejectionPreview = (dispatchId: number, itemId: number) => get<{ source: RejectionSource; sourceHash: string; existingCaseId?: number }>('/production/rejections/preview', { dispatchId, itemId })
export const rejectionDetail = (id: number) => get<RejectionDetail>(`/production/rejections/${id}`)
export const createRejection = (body: { dispatchId: number; itemId: number; expectedSourceHash: string; reason: string; requestKey: string }) => post<RejectionDetail>('/production/rejections', body)
export const appendRejection = (id: number, body: RejectionAppend) => post<RejectionDetail>(`/production/rejections/${id}/events`, body)
