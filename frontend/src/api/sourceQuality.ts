import { get, post, postMultipart } from './http'

export type QualityScope = 'HARVEST' | 'PACKING'
export const QUALITY_RESULTS = { PASS: '报告记载合格', FAIL: '报告记载不合格', INCONCLUSIVE: '无法判定 / 未明确结论' } as const
export const QUALITY_STATUS = { DRAFT: '待核对公开', PUBLISHED: '已公开', WITHDRAWN: '已撤回' } as const
export interface QualitySource { harvestId: number; packingId?: number; harvestCode: string; packingCode?: string; cycleCode: string; productName: string; variety: string; harvestedAt: string; packedAt?: string }
export interface SourceQualityReport {
  id: number; scopeType: QualityScope; reportNo: string; agencyName: string; sampledOn: string; checkedOn: string
  declaredResult: keyof typeof QUALITY_RESULTS; status: keyof typeof QUALITY_STATUS; fileName: string; fileSize: number; fileType: string
  fileSha256: string; manifestHash: string; attachmentPublic: boolean; recorderName: string; recordedAt: string
  publisherName?: string; publishedAt?: string; withdrawnAt?: string; withdrawReason?: string
}
export interface QualityRevisionLink { previousReportId: number; replacementReportId: number; previousReportNo: string; replacementReportNo: string; previousHash: string; replacementHash: string }
export interface PublicQualityReport extends Pick<SourceQualityReport, 'id' | 'scopeType' | 'reportNo' | 'agencyName' | 'sampledOn' | 'checkedOn' | 'declaredResult' | 'status' | 'fileSize' | 'fileType' | 'fileSha256' | 'manifestHash' | 'publishedAt' | 'withdrawnAt'> { downloadPath?: string; chainStatus: string; revisionLinks?: QualityRevisionLink[]; evidence?: import('./qualityEvidence').PublicQualityProof }
export interface PackingQuality { rows: PublicQualityReport[]; total: number; limit: number }
export interface SourceQualityList { source: QualitySource; rows: SourceQualityReport[]; total: number; page: number; pageSize: number }
const sources = { HARVEST: 'harvests', PACKING: 'packings' }
export const qualityList = (kind: QualityScope, id: number, page: number) => get<SourceQualityList>(`/production/${sources[kind]}/${id}/quality-reports`, { page })
export async function registerQuality(kind: QualityScope, id: number, report: { reportNo: string; agencyName: string; sampledOn: string; checkedOn: string; declaredResult: string; requestKey: string }, attachment: File) {
  const body = new FormData()
  body.append('report', new Blob([JSON.stringify(report)], { type: 'application/json' }))
  body.append('attachment', attachment)
  return postMultipart<SourceQualityReport>(`/production/${sources[kind]}/${id}/quality-reports`, body)
}
export const publishQuality = (id: number, body: { expectedManifestHash: string; metadataReviewed: boolean; publishAttachment: boolean; attachmentPrivacyReviewed: boolean }) => post<SourceQualityReport>(`/production/quality-reports/${id}/publish`, body)
export const withdrawQuality = (id: number, reason: string) => post<SourceQualityReport>(`/production/quality-reports/${id}/withdraw`, { reason })
export const qualityDownloadUrl = (path: string) => ((import.meta.env.VITE_API_BASE as string | undefined) ?? '/api').replace(/\/$/, '') + path
