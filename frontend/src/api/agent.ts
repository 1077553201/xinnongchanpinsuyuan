import { get, post } from './http'

export type AgentKind = 'HARVEST' | 'TRANSPORT_EVENT' | 'NOTE'
export interface AgentOperation { id: string; kind: AgentKind; quote: string; targetId: number | null; fields: Record<string, string | number>; issues: string[]; contextHash: string | null }
export interface AgentReport { id: number; reportDate: string; sourceText: string; mode: 'MODEL' | 'RULES'; version: number; status: 'READY' | 'RUNNING' | 'SUPERSEDED'; createdAt: string }
export interface AgentConfirmation { id: number; version: number; scopeJson: string; status: string; confirmedAt: string }
export interface AgentReceipt { id: number; confirmationId: number; operationId: string; status: 'SUCCESS' | 'FAILED'; businessId: number | null; message: string; recordedAt: string }
export interface AgentDetail { report: AgentReport; operations: AgentOperation[]; confirmations: AgentConfirmation[]; receipts: AgentReceipt[]; completed: AgentReceipt[] }
export const agentStatus = () => get<{ mode: 'RULES' | 'MODEL'; configured: boolean; timezone: string }>('/production/agent/status')
export const agentContext = (search: string) => get<{ cycles: { id: number; label: string }[]; shipments: { id: number; label: string }[]; limit: number }>(`/production/agent/context?${new URLSearchParams({ search })}`)
export const agentReports = (page: number) => get<{ rows: AgentReport[]; total: number; page: number }>(`/production/agent/reports?page=${page}`)
export const agentDetail = (id: number) => get<AgentDetail>(`/production/agent/reports/${id}`)
export const createAgentReport = (body: { sourceText: string; reportDate: string; requestKey: string }) => post<AgentReport>('/production/agent/reports', body)
export const reviseAgentReport = (id: number, version: number, operations: AgentOperation[]) => post<AgentReport>(`/production/agent/reports/${id}/revise`, { version, operations })
export const confirmAgentReport = (id: number, version: number, operationIds: string[], requestKey: string) => post<AgentDetail>(`/production/agent/reports/${id}/confirm`, { version, operationIds, requestKey })
export const resumeAgentReport = (id: number, confirmation: number) => post<AgentDetail>(`/production/agent/reports/${id}/confirmations/${confirmation}/resume`, {})
