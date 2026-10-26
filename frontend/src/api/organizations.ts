import { get, post } from './http'

export const ORGANIZATION_TYPES: Record<string, string> = { FARM: '农场', COOPERATIVE: '合作社', PROCESSOR: '加工主体', WAREHOUSE: '仓储主体', LOGISTICS: '运输主体', RETAILER: '销售主体', TESTING: '检测机构', OTHER: '其他主体' }
export const ORGANIZATION_STATUS = { ACTIVE: '启用', DISABLED: '停用', DELETED: '已删除' }
export interface OrganizationInput { orgCode: string; orgName: string; orgType: string; contactName: string; contactPhone: string; address: string }
export interface OrganizationRecord extends Omit<OrganizationInput, 'contactName' | 'contactPhone' | 'address'> { contactName?: string; contactPhone?: string; address?: string; id: number; status: keyof typeof ORGANIZATION_STATUS; createTime: string; updateTime: string }
export interface OrganizationPage { rows: OrganizationRecord[]; total: number; page: number; pageSize: number }
export interface OrganizationDetail { organization: OrganizationRecord; dependencies: { label: string; total: number }[]; canDelete: boolean }
export const organizationsPage = (search: string, status: string, page: number) => get<OrganizationPage>('/organizations', { search, status, page })
export const organizationDetail = (id: number) => get<OrganizationDetail>(`/organizations/${id}`)
export const createOrganization = (input: OrganizationInput) => post<OrganizationRecord>('/organizations', input)
export const updateOrganization = (id: number, input: OrganizationInput) => post<OrganizationRecord>(`/organizations/${id}`, input)
export const changeOrganizationStatus = (id: number, status: string) => post<OrganizationRecord>(`/organizations/${id}/status`, { status })
