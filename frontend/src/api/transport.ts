import { get, post } from './http'

export const TRANSPORT_STATUS = { PLANNED: '待发运', IN_TRANSIT: '运输中', DELIVERED: '已全部接收', DELIVERY_ISSUE: '收货有拒收' } as const
export const TRANSPORT_EVENTS = { DEPART: '实际发运', UPDATE: '运输进展', EXCEPTION: '运输异常', RECEIVE: '收货结果' } as const
export interface Shipment {
  id: number; dispatchId: number; orderId: number; orderCode: string; packingId: number; warehouseId: number
  shipmentCode: string; carrier: string; trackingReference: string; driverName: string; driverContact: string
  recipient: string; deliveryAddress: string; notes: string; status: keyof typeof TRANSPORT_STATUS
  productName: string; variety: string; packingCode: string; warehouseName: string; packageUnit: string; packageCount: number
  movementCode: string; dispatchedAt: string; recorderName: string; recordedAt: string
}
export interface TransportEvent {
  id: number; kind: keyof typeof TRANSPORT_EVENTS; occurredAt: string; location: string; performerName: string; description: string
  reference: string; acceptedCount?: number; rejectedCount?: number; recorderName: string; recordedAt: string
}
export interface TransportInput {
  kind: keyof typeof TRANSPORT_EVENTS; occurredAt: string; location: string; performerName: string; description: string
  reference: string; acceptedCount?: number; rejectedCount?: number; requestKey: string
}
export const listTransport = (search: string, status: string, page: number) => get<{ rows: Shipment[]; total: number; page: number; pageSize: number }>(`/production/transport?${new URLSearchParams({ search, status, page: String(page), pageSize: '20' })}`)
export const transportDetail = (id: number) => get<{ shipment: Shipment; events: TransportEvent[] }>(`/production/transport/${id}`)
export const createTransport = (body: { dispatchId: number; shipmentCode: string; carrier: string; trackingReference: string; driverName: string; driverContact: string; notes: string; requestKey: string }) => post<Shipment>('/production/transport', body)
export const recordTransport = (id: number, body: TransportInput) => post<TransportEvent>(`/production/transport/${id}/events`, body)
