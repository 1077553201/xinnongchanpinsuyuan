import { get, post } from './http'
import type { SourceQualityReport, QualityRevisionLink } from './sourceQuality'

export interface QualityFacts {
  reportId: number; reportManifestHash: string; status: string; attachmentPublic: boolean
  report: { scopeType: string; reportNo: string; agencyName: string; declaredResult: string; fileSha256: string; sampledOn: string; checkedOn: string }
  publishedAt?: string; withdrawnAt?: string; revisionLinks: QualityRevisionLink[]
}
export interface QualityManifest { schema: string; version: number; previousHash?: string; traceCodeHash: string; sourceHash: string; productionManifestHash: string; reportFacts: QualityFacts }
export interface QualityProofVersion { version: number; manifest: QualityManifest; manifestHash: string; createdAt?: string; chainStatus: string; verificationStatus?: string; txId?: number; errorCode?: string }
export interface QualityPreview { manifest: QualityManifest; manifestHash: string; traceCode: string; frozen: boolean; versionLimitReached: boolean; versions: QualityProofVersion[] }
export interface PublicQualityProof { verificationStatus: string; currentMatchesLatest: boolean; versions: QualityProofVersion[] }
export interface PrivateRevision { previousReportId: number; replacementReportId: number; reason: string; recordedAt?: string }
export interface RevisionDetail { report: SourceQualityReport; previous?: PrivateRevision; next?: PrivateRevision; candidatePrevious?: SourceQualityReport }
export const previewQualityProof = (id: number, traceCode: string) => get<QualityPreview>(`/production/quality-reports/${id}/evidence`, { traceCode })
export const submitQualityProof = (id: number, traceCode: string, expectedManifestHash: string) => post<QualityPreview>(`/production/quality-reports/${id}/evidence`, { traceCode, expectedManifestHash }, 160000)
export const qualityRevision = (id: number, previousReportId?: number) => get<RevisionDetail>(`/production/quality-reports/${id}/replacement`, previousReportId ? { previousReportId } : undefined)
export const linkQualityRevision = (id: number, body: { previousReportId: number; expectedPreviousHash: string; expectedReplacementHash: string; reason: string }) => post<PrivateRevision>(`/production/quality-reports/${id}/replacement`, body)
