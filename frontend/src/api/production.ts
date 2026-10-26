import { get, post } from './http'

export interface Organization { id: number; name: string; code?: string; status?: 'ACTIVE' | 'DISABLED' }
export interface FarmPlot {
  id: number
  organizationId: number
  plotCode: string
  plotName: string
  location: string
  areaMu: number
  managerName: string
  notes?: string
}
export interface PlantingCycle {
  id: number
  plotId: number
  productId: number
  cycleCode: string
  productName: string
  variety: string
  plantedOn: string
  expectedHarvestOn?: string
  managerName: string
  environmentNotes?: string
  status: 'ACTIVE' | 'ENDED'
  endedOn?: string
  endReason?: string
}
export type NewPlot = Omit<FarmPlot, 'id'>
export interface NewCycle {
  productId: number
  cycleCode: string
  variety: string
  plantedOn: string
  expectedHarvestOn?: string
  managerName: string
  environmentNotes?: string
}
export const listPlots = () => get<FarmPlot[]>('/production/plots')
export const listCycles = () => get<PlantingCycle[]>('/production/cycles')
export const listOrganizations = () => get<Organization[]>('/production/organizations')
export const listOrganizationNames = () => get<Organization[]>('/production/organizations', { includeInactive: true })
export const registerPlot = (body: NewPlot) => post<FarmPlot>('/production/plots', body)
export const startPlanting = (plotId: number, body: NewCycle) => post<PlantingCycle>(`/production/plots/${plotId}/cycles`, body)
export const endPlanting = (id: number, body: { endedOn: string; reason: string }) => post<PlantingCycle>(`/production/cycles/${id}/end`, body)
