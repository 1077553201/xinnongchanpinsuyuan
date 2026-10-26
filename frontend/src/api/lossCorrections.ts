import { get, post } from './http'
import type { InventoryMovement, InventoryPosition } from './inventory'
export interface LossCorrection {
  id: number; revision: number; previousCount: number; correctedCount: number; balanceBefore: number; balanceAfter: number
  verifiedAt: string; performerName: string; reason: string; reference: string; recordedBy: number; recordedAt: string; correctionHash: string
}
export interface LossHistory { original: InventoryMovement; history: LossCorrection[]; effectiveCount: number }
export interface LossImpact {
  kind?: string; packingCount?: number; receivedBefore?: number; receivedAfter?: number; availableBefore?: number; availableAfter?: number; blockers?: string[]
  revision: number; previousCount: number; correctedCount: number; stockDelta: number; balanceBefore: number; balanceAfter: number
  warehouseId: number; packageUnit: string; netWeightKg: number; positions: InventoryPosition[]
  orders: { lineId: number; orderId: number; orderCode: string; status: string; warehouseId: number; orderedCount: number; dispatchedCount: number }[]
  relatedCodes: { itemId: number; traceCode: string; dispatchId?: number; receiptOutcome?: string; rejectionStatus?: string }[]
  evidence: { kind: string; id: number; itemId: number; version: number; chainStatus: string }[]
}
export interface LossPreview { original: InventoryMovement; impact: LossImpact; impactHash: string; canConfirm?: boolean }
export interface ConfirmLossCorrection { correctedCount: number; verifiedAt: string; performerName: string; reason: string; reference: string; expectedImpactHash: string; requestKey: string }
const correctionPath = (id: number, ordinary: boolean) => `/production/inventory/movements/${id}/${ordinary ? 'quantity-corrections' : 'corrections'}`
export const lossHistory = (id: number, ordinary = false) => get<LossHistory>(correctionPath(id, ordinary))
export const previewLossCorrection = (id: number, correctedCount: number, ordinary = false) => get<LossPreview>(`${correctionPath(id, ordinary)}/preview`, { correctedCount })
export const confirmLossCorrection = (id: number, body: ConfirmLossCorrection, ordinary = false) => post<LossHistory>(correctionPath(id, ordinary), body)
