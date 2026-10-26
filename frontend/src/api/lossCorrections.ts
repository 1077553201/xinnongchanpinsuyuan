import { get, post } from './http'
import type { InventoryMovement, InventoryPosition } from './inventory'
export interface LossCorrection {
  id: number; revision: number; previousCount: number; correctedCount: number; balanceBefore: number; balanceAfter: number
  verifiedAt: string; performerName: string; reason: string; reference: string; recordedBy: number; recordedAt: string; correctionHash: string
}
export interface LossHistory { original: InventoryMovement; history: LossCorrection[]; effectiveCount: number }
export interface LossImpact {
  revision: number; previousCount: number; correctedCount: number; stockDelta: number; balanceBefore: number; balanceAfter: number
  warehouseId: number; packageUnit: string; netWeightKg: number; positions: InventoryPosition[]
  orders: { lineId: number; orderId: number; orderCode: string; status: string; warehouseId: number; orderedCount: number; dispatchedCount: number }[]
  relatedCodes: { itemId: number; traceCode: string; dispatchId?: number; receiptOutcome?: string; rejectionStatus?: string }[]
  evidence: { kind: string; id: number; itemId: number; version: number; chainStatus: string }[]
}
export interface LossPreview { original: InventoryMovement; impact: LossImpact; impactHash: string }
export interface ConfirmLossCorrection { correctedCount: number; verifiedAt: string; performerName: string; reason: string; reference: string; expectedImpactHash: string; requestKey: string }
export const lossHistory = (id: number) => get<LossHistory>(`/production/inventory/movements/${id}/corrections`)
export const previewLossCorrection = (id: number, correctedCount: number) => get<LossPreview>(`/production/inventory/movements/${id}/corrections/preview`, { correctedCount })
export const confirmLossCorrection = (id: number, body: ConfirmLossCorrection) => post<LossHistory>(`/production/inventory/movements/${id}/corrections`, body)
