import { get, post } from './http'
import type { PublicCirculation } from './itemCirculation'

export interface CirculationManifest {
  schema: string; version: number; previousHash?: string; traceCodeHash: string; sourceHash: string; productionManifestHash: string
  circulation: Omit<PublicCirculation, 'chainStatus'>
}
export interface CirculationVersion {
  version: number; manifest: CirculationManifest; manifestHash: string; createdAt?: string
  chainStatus: string; verificationStatus?: string; txId?: number; errorCode?: string
}
export interface CirculationPreview { manifest: CirculationManifest; manifestHash: string; frozen: boolean; versionLimitReached: boolean; versions: CirculationVersion[] }
export interface PublicCirculationEvidence { currentMatchesLatest: boolean; verificationStatus: string; versions: CirculationVersion[] }
export const previewCirculation = (id: number) => get<CirculationPreview>(`/production/items/${id}/circulation-evidence`)
export const submitCirculation = (id: number, expectedManifestHash: string) => post<CirculationPreview>(`/production/items/${id}/circulation-evidence`, { expectedManifestHash }, 160000)
