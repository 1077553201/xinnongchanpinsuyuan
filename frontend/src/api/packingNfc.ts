import { get, post } from './http'

export interface NfcOperation {
  operation: 'BIND' | 'DISABLE'
  payloadHash: string
  chainStatus: string
  txId?: number
  errorCode?: string
  recordedAt?: string
  privateReason?: string
}
export interface PackingNfcDetail {
  itemId: number
  packingId: number
  traceCode: string
  uidHash?: string
  history: NfcOperation[]
  payload?: Record<string, unknown>
  payloadHash?: string
  operation?: 'BIND' | 'DISABLE'
}
export interface NfcIdentity {
  uidHash: string
  traceCode: string
  active: boolean
  disableRequested: boolean
  verificationStatus: string
  verificationSource?: string
  bindingChainStatus: string
  bindingHash: string
  disableChainStatus?: string
  disableHash?: string
}
export interface NfcPreviewInput { operation: 'BIND' | 'DISABLE'; uid?: string; reason?: string }
export const packingNfc = (id: number) => get<PackingNfcDetail>(`/production/packing-items/${id}/nfc`)
export const previewPackingNfc = (id: number, body: NfcPreviewInput) => post<PackingNfcDetail>(`/production/packing-items/${id}/nfc/preview`, body)
export const submitPackingNfc = (id: number, body: NfcPreviewInput & { expectedHash: string }) => post<PackingNfcDetail>(`/production/packing-items/${id}/nfc`, body, 160000)
export const resolvePackingNfc = (hash: string) => get<NfcIdentity>(`/public/trace/nfc/${encodeURIComponent(hash)}`)
