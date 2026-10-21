import { get, post } from './http'
import type { PackingLot } from './harvest'

export interface Warehouse {
  id: number; organizationId: number; warehouseCode: string; warehouseName: string; location: string; notes?: string
}
export interface InventorySource extends PackingLot {
  organizationId: number; cycleId: number; cycleCode: string; plotName: string; productName: string; variety: string; harvestCode: string
  receivedCount: number; dispatchedCount: number; lossCount: number; availableCount: number
}
export interface InventoryPosition {
  warehouseId: number; warehouseCode: string; warehouseName: string; location: string
  receivedCount: number; dispatchedCount: number; lossCount: number; availableCount: number
}
export interface InventoryMovement {
  id: number; packingId: number; warehouseId: number; warehouseName: string; movementCode: string; kind: 'IN' | 'OUT' | 'LOSS'
  packageCount: number; packageUnit: string; weightKg: number; occurredAt: string; performerName: string
  destination?: string; reason: string; reference?: string; recorderName: string; recordedAt: string
}
export interface InventoryDetail { source: InventorySource; positions: InventoryPosition[]; movements: InventoryMovement[] }
export interface NewWarehouse { organizationId: number; warehouseCode: string; warehouseName: string; location: string; notes: string }
export interface NewMovement { warehouseId: number; movementCode: string; kind: string; packageCount: number; occurredAt: string; performerName: string; destination: string; reason: string; reference: string; requestKey: string }
export const listWarehouses = () => get<Warehouse[]>('/production/warehouses')
export const createWarehouse = (body: NewWarehouse) => post<Warehouse>('/production/warehouses', body)
export const listInventory = (cycle: number) => get<InventorySource[]>(`/production/cycles/${cycle}/inventory`)
export const inventoryDetail = (id: number) => get<InventoryDetail>(`/production/inventory/packings/${id}`)
export const recordMovement = (id: number, body: NewMovement) => post<InventoryMovement>(`/production/inventory/packings/${id}/movements`, body)
