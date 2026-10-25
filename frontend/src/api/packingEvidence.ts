import { get, post } from './http'
export interface EvidenceSection { rows: Record<string, unknown>[]; limit: number; truncated: boolean }
export interface ProductionEvidence { version: number; from: string; until: string; timezone: string; capturedAt?: string; manifestHash?: string; verificationStatus?: string; coverage: string; circulationStatus: string; rules: EvidenceSection; farming: EvidenceSection; environment: EvidenceSection }
export interface EvidenceJob { id: number; businessType: string; businessId: number; chainStatus: string; errorCode?: string; txId?: number; updatedAt: string }
export interface EvidencePreview { manifest: ProductionEvidence; manifestHash: string; frozen: boolean; createdAt?: string; jobs: EvidenceJob[] }
export const previewPackingEvidence = (id: number) => get<EvidencePreview>(`/production/packings/${id}/evidence`)
export const submitPackingEvidence = (id: number, expectedManifestHash: string, itemIds: number[]) => post<EvidencePreview>(`/production/packings/${id}/evidence`, { expectedManifestHash, itemIds }, 160000)
