import { get, post } from './http'

export const SALES_STATUS = { OPEN: '待出库', PARTIAL: '部分出库', FULFILLED: '已全部出库', CANCELLED: '已取消', CLOSED: '已停止剩余出库' } as const
export interface SalesOrder {
  id: number; organizationId: number; orderCode: string; orderedOn: string; customerName: string; customerContact?: string
  deliveryAddress: string; notes?: string; totalAmount: string; status: keyof typeof SALES_STATUS
  recorderName: string; recordedAt: string; closeReason?: string; closedAt?: string
}
export interface SalesLine {
  id: number; orderId: number; packingId: number; warehouseId: number; packingCode: string; warehouseName: string
  productName: string; variety: string; packageUnit: string; netWeightKg: number; packageCount: number; unitPrice: string
  lineAmount: string; dispatchedCount: number; cycleId: number
}
export interface SalesDispatch {
  shipmentId?: number
  id: number; lineId: number; movementId: number; movementCode: string; packageCount: number; packageUnit: string
  weightKg: number; occurredAt: string; performerName: string; recorderName: string; recordedAt: string; reason: string; reference?: string
}
export interface SalesDetail { order: SalesOrder; lines: SalesLine[]; dispatches: SalesDispatch[] }
export interface NewSalesOrder {
  organizationId: number; orderCode: string; orderedOn: string; customerName: string; customerContact: string; deliveryAddress: string; notes: string
  lines: { packingId: number; warehouseId: number; packageCount: number; unitPrice: string }[]; requestKey: string
}
export const listSales = (search: string, status: string, page: number) => get<{ rows: SalesOrder[]; total: number; page: number; pageSize: number }>(`/production/sales?${new URLSearchParams({ search, status, page: String(page), pageSize: '20' })}`)
export const salesDetail = (id: number) => get<SalesDetail>(`/production/sales/${id}`)
export const createSalesOrder = (body: NewSalesOrder) => post<SalesOrder>('/production/sales', body)
export const closeSalesOrder = (id: number, reason: string) => post<SalesOrder>(`/production/sales/${id}/close`, { reason })
export const dispatchSales = (line: number, body: { packageCount: number; occurredAt: string; performerName: string; reason: string; reference: string; requestKey: string }) => post<SalesDispatch>(`/production/sales/lines/${line}/dispatch`, body)
