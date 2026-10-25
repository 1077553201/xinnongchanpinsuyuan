import { get, post } from './http'
import type { SalesDispatch } from './sales'

export type ReceiptOutcome = 'ACCEPTED' | 'REJECTED'
export interface CirculationItem { itemId: number; traceCode: string; dispatchId?: number; receiptOutcome?: ReceiptOutcome; linkedAt?: string; receiptAt?: string }
export interface DispatchItems {
  dispatch: SalesDispatch; packingCode: string; linkedCount: number; acceptedCount: number; rejectedCount: number
  receipt?: { acceptedCount: number; rejectedCount: number; occurredAt: string }
  rows: CirculationItem[]; total: number; page: number; pageSize: number
}
export interface PublicCirculation {
  chainStatus: 'NOT_SUBMITTED'; dispatchedAt: string; linkedAt: string; receiptOutcome?: ReceiptOutcome; receiptRecordedAt?: string
  transportStatus: string; eventsTruncated: boolean; events: { kind: string; occurredAt: string }[]
}
export const dispatchItems = (id: number, search: string, mode: string, page: number) => get<DispatchItems>(`/production/sales/dispatches/${id}/items`, { search, mode, page })
export const linkDispatchItems = (id: number, itemIds: number[], requestKey: string) => post<{ savedCount: number }>(`/production/sales/dispatches/${id}/items`, { itemIds, requestKey })
export const receiveDispatchItems = (id: number, itemIds: number[], outcome: ReceiptOutcome, requestKey: string) => post<{ savedCount: number }>(`/production/sales/dispatches/${id}/items/receipt`, { itemIds, outcome, requestKey })
