import { get, post } from './http'
import type { PackingLot } from './harvest'
export interface PackingCode { id: number; traceCodePlain: string; traceUrl: string; chainStatus: string }
export interface PackingSource { packingCode: string; packageUnit: string; netWeightKg: number; packedAt: string; gradeName: string; harvestCode: string; harvestedAt: string; cycleCode: string; productName: string; variety: string; plantedOn: string; plotName: string; sourceHash?: string; verificationStatus?: string }
export interface PackingCodes { packing: PackingLot; source: PackingSource; generatedCount: number; remaining: number; rows: PackingCode[]; page: number; total: number; unitRule: string }
export const packingCodes = (id: number, page = 1) => get<PackingCodes>(`/production/packings/${id}/codes`, { page })
export const issuePackingCodes = (id: number, count: number, requestKey: string) => post<{ issueId: number; items: PackingCode[] }>(`/production/packings/${id}/codes`, { count, requestKey })
