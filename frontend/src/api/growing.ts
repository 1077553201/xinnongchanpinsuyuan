import { get, post } from './http'

export interface GrowingRule {
  id: number; cycleId: number; metric: string; minValue?: number; maxValue?: number; unit: string
  expectedIntervalMinutes: number; basis: string; notes?: string; effectiveFrom: string; effectiveTo?: string; createdBy: number
}
export interface Assessment {
  readingId: number; cycleId: number; ruleId?: number; metric: string; result: string; reason: string
  value: number; unit?: string; deviceCode: string; reportedAt: string; minValue?: number; maxValue?: number; ruleUnit?: string
  caseId?: number; caseStatus?: string; actionTaken?: string; observation?: string; handledAt?: string; handlerName?: string
}
export interface EnvironmentOverview {
  rules: GrowingRule[]; assessments: Assessment[]; openCases: number
  coverage: { ruleId: number; metric: string; latestReportedAt?: string; status: string }[]
}
export interface ProductionPerson { id: number; organizationId: number; username: string; displayName: string }
export interface FarmTask {
  id: number; cycleId: number; taskCode: string; taskType: string; title: string; plannedOn: string; assignedTo: number
  assigneeName: string; instructions?: string; status: 'PLANNED' | 'COMPLETED' | 'CANCELLED'; createdBy: number
  cancelReason?: string; cancelledAt?: string
}
export interface FarmExecution {
  id: number; taskId: number; performedBy: number; performerName: string; performedAt: string; inputName?: string
  quantity?: number; unit?: string; notes: string; evidenceReference?: string; recordedBy: number; recordedName: string; recordedAt: string
}
export interface RuleRequest { metric: string; minValue?: number; maxValue?: number; expectedIntervalMinutes: number; basis: string; notes?: string; requestKey: string }
export interface TaskRequest { taskCode: string; taskType: string; title: string; plannedOn: string; assignedTo: number; instructions?: string }
export interface ExecutionRequest { performedAt: string; inputName?: string; quantity?: number; unit?: string; notes: string; evidenceReference?: string; requestKey: string }
export const environmentOverview = (id: number) => get<EnvironmentOverview>(`/production/cycles/${id}/environment`)
export const configureRule = (id: number, body: RuleRequest) => post<GrowingRule>(`/production/cycles/${id}/environment/rules`, body)
export const retireRule = (id: number, reason: string) => post(`/production/environment/rules/${id}/retire`, { reason })
export const listEnvironmentCases = (id: number) => get<Assessment[]>(`/production/cycles/${id}/environment/cases`)
export const handleEnvironmentCase = (id: number, actionTaken: string, observation: string) => post<Assessment>(`/production/environment/cases/${id}/handle`, { actionTaken, observation })
export const listProductionPeople = () => get<ProductionPerson[]>('/production/people')
export const listFarmTasks = (id: number) => get<FarmTask[]>(`/production/cycles/${id}/tasks`)
export const createFarmTask = (id: number, body: TaskRequest) => post<FarmTask>(`/production/cycles/${id}/tasks`, body)
export const cancelFarmTask = (id: number, reason: string) => post<FarmTask>(`/production/tasks/${id}/cancel`, { reason })
export const getFarmExecution = async (id: number): Promise<FarmExecution | null> => (await get<FarmExecution | undefined>(`/production/tasks/${id}/execution`)) ?? null
export const completeFarmTask = (id: number, body: ExecutionRequest) => post<FarmExecution>(`/production/tasks/${id}/complete`, body)

export const TASK_TYPES: Record<string, string> = { IRRIGATION: '灌溉', FERTILIZATION: '施肥', PEST_CONTROL: '植保 / 用药', PRUNING: '修剪', WEEDING: '除草', INSPECTION: '巡检', OTHER: '其他农事' }
export const ASSESSMENT_LABELS: Record<string, string> = { IN_RANGE: '本条在范围内', BELOW: '低于下限', ABOVE: '高于上限', NO_RULE: '当时无适用规则', UNIT_MISMATCH: '单位待核实' }
export const COVERAGE_LABELS: Record<string, string> = { CURRENT: '近期有采集', WAITING: '等待首次采集', NO_DATA: '超过预期间隔仍无数据', STALE: '采集间隔已超时', NOT_ACTIVE: '历史种植周期' }
