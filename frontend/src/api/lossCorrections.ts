import { get, post } from './http'
import type { InventoryMovement, InventoryPosition } from './inventory'
export interface LossCorrection {
  id: number; revision: number; previousCount: number; correctedCount: number; balanceBefore: number; balanceAfter: number
  verifiedAt: string; performerName: string; reason: string; reference: string; recordedBy: number; recordedAt: string; correctionHash: string
}
export interface LossHistory { original: InventoryMovement; history: LossCorrection[]; effectiveCount: number }
export interface LossImpact {
  dispatchId?: number; lineId?: number; orderId?: number; orderCode?: string; orderStatusBefore?: string; orderStatusAfter?: string; orderedCount?: number; dispatchedBefore?: number; dispatchedAfter?: number; remainingBefore?: number; remainingAfter?: number
  kind?: string; packingCount?: number; receivedBefore?: number; receivedAfter?: number; availableBefore?: number; availableAfter?: number; blockers?: string[]
  revision: number; previousCount: number; correctedCount: number; stockDelta: number; balanceBefore: number; balanceAfter: number
  warehouseId: number; packageUnit: string; netWeightKg: number; positions: InventoryPosition[]
  orders: { lineId: number; orderId: number; orderCode: string; status: string; warehouseId: number; orderedCount: number; dispatchedCount: number }[]
  relatedCodes: { itemId: number; traceCode: string; dispatchId?: number; receiptOutcome?: string; rejectionStatus?: string }[]
  evidence: { kind: string; id: number; itemId: number; version: number; chainStatus: string }[]
}
export interface LossPreview { original: InventoryMovement; impact: LossImpact; impactHash: string; canConfirm?: boolean }
export interface ConfirmLossCorrection { correctedCount: number; verifiedAt: string; performerName: string; reason: string; reference: string; expectedImpactHash: string; requestKey: string }
const correctionPath = (id: number, ordinary: boolean, sales: boolean) => `/production/inventory/movements/${id}/${sales ? 'dispatch-corrections' : ordinary ? 'quantity-corrections' : 'corrections'}`
export const lossHistory = (id: number, ordinary = false, sales = false) => get<LossHistory>(correctionPath(id, ordinary, sales))
export const previewLossCorrection = (id: number, correctedCount: number, ordinary = false, sales = false) => get<LossPreview>(`${correctionPath(id, ordinary, sales)}/preview`, { correctedCount })
export const confirmLossCorrection = (id: number, body: ConfirmLossCorrection, ordinary = false, sales = false) => post<LossHistory>(correctionPath(id, ordinary, sales), body)
