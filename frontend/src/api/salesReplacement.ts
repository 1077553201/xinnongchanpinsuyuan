import { get, post } from './http'
import type { NewSalesOrder, SalesLine, SalesOrder } from './sales'

export interface SalesReplacementRecord {
  id: number; originalOrderId: number; replacementOrderId: number; reason: string; impactHash: string; recordHash: string; recordedBy: number; recordedAt: string
}
export interface ReplacementContext {
  order: SalesOrder; lines: SalesLine[]; relations: SalesReplacementRecord[]; dispatchCount: number; dependenciesTruncated: boolean; blockers: string[]
  dependencies: { dispatchId: number; packageCount: number; shipmentId?: number; shipmentStatus?: string; linkedCodeCount: number; checkedCodeCount: number }[]
}
export interface ReplacementDraft { replacement: NewSalesOrder; reason: string }
export interface ReplacementPreview {
  canConfirm: boolean; impactHash: string
  impact: { original: SalesOrder; originalLines: SalesLine[]; replacement: NewSalesOrder; reason: string; proposedAmount: string; blockers: string[]; proposedLines: { packingId: number; warehouseId: number; packingCode: string; warehouseName: string; productName: string; packageUnit: string; packageCount: number; unitPrice: string; lineAmount: string; availableCount: number }[] }
}
export const replacementContext = (id: number) => get<ReplacementContext>(`/production/sales/${id}/replacement`)
export const previewReplacement = (id: number, draft: ReplacementDraft) => post<ReplacementPreview>(`/production/sales/${id}/replacement/preview`, draft)
export const confirmReplacement = (id: number, draft: ReplacementDraft & { expectedImpactHash: string; requestKey: string }) => post<ReplacementContext>(`/production/sales/${id}/replacement`, draft)
