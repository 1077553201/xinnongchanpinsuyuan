import { get, post } from './http'

export interface HarvestLot {
  id: number; cycleId: number; lotCode: string; harvestedAt: string; quantity: number; unit: 'kg' | 'g' | 'jin'
  weightKg: number; performerName: string; notes?: string; status: 'OPEN' | 'GRADED'
  recorderName: string; recordedAt: string; allocatedKg: number; lossKg: number; packedKg: number
}
export interface HarvestGrade {
  id: number; harvestId: number; kind: 'SALEABLE' | 'LOSS'; gradeName: string; weightKg: number
  handledAt: string; performerName: string; location: string; reason?: string; recorderName: string; recordedAt: string; packedKg: number
}
export interface PackingLot {
  id: number; gradeId: number; packingCode: string; packedAt: string; packageCount: number; packageUnit: string
  netWeightKg: number; totalWeightKg: number; performerName: string; location: string; notes?: string
  recorderName: string; recordedAt: string; gradeName: string
}
export interface HarvestDetail { harvest: HarvestLot; grades: HarvestGrade[]; packings: PackingLot[] }
export interface NewHarvest { lotCode: string; harvestedAt: string; quantity: number; unit: string; performerName: string; notes: string; requestKey: string }
export interface NewGrade { kind: string; gradeName: string; weightKg: number; handledAt: string; performerName: string; location: string; reason: string; requestKey: string }
export interface NewPacking { packingCode: string; packedAt: string; packageCount: number; packageUnit: string; netWeightKg: number; performerName: string; location: string; notes: string; requestKey: string }
export const listHarvests = (cycle: number) => get<HarvestLot[]>(`/production/cycles/${cycle}/harvests`)
export const harvestDetail = (id: number) => get<HarvestDetail>(`/production/harvests/${id}`)
export const recordHarvest = (cycle: number, body: NewHarvest) => post<HarvestLot>(`/production/cycles/${cycle}/harvests`, body)
export const recordGrade = (id: number, body: NewGrade) => post<HarvestGrade>(`/production/harvests/${id}/grades`, body)
export const reconcileHarvest = (id: number) => post<HarvestLot>(`/production/harvests/${id}/reconcile`, {})
export const recordPacking = (grade: number, body: NewPacking) => post<PackingLot>(`/production/grades/${grade}/packings`, body)
