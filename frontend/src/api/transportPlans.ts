import { get, post } from './http'
import type { Shipment } from './transport'
export interface PlanAction {
  id: number; revision: number; action: 'CANCEL' | 'REPLACE'; reason: string; shipmentCode?: string; carrier?: string; driverName?: string
  trackingReference?: string; driverContact?: string; notes?: string; packageCount?: number; packageUnit?: string; actionHash: string; recordedBy: number; recordedAt: string
}
export interface PlanContext { original: Shipment; current: Shipment; history: PlanAction[]; revision: number; cancelBlockers: string[]; replaceBlockers: string[] }
export interface PlanDraft {
  action: 'CANCEL' | 'REPLACE'; reason: string
  replacement?: { dispatchId: number; shipmentCode: string; carrier: string; trackingReference: string; driverName: string; driverContact: string; notes: string; requestKey: string }
}
export interface PlanPreview {
  canConfirm: boolean; impactHash: string
  impact: { revision: number; action: string; reason: string; currentStatus: string; currentDispatchCount: number; recipient: string; deliveryAddress: string; blockers: string[]; current: { shipmentCode: string; carrier: string; driverName: string; packageCount: number; packageUnit: string }; replacement: PlanDraft['replacement'] & { packageCount?: number; packageUnit?: string } }
}
export const planContext = (id: number) => get<PlanContext>(`/production/transport/${id}/plans`)
export const previewPlan = (id: number, draft: PlanDraft) => post<PlanPreview>(`/production/transport/${id}/plans/preview`, draft)
export const confirmPlan = (id: number, draft: PlanDraft & { expectedImpactHash: string; requestKey: string }) => post<PlanContext>(`/production/transport/${id}/plans`, draft)
