import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import type { CommonResponse } from '@/types/api'
import { OK_CODE } from '@/types/api'

const baseURL = (import.meta.env.VITE_API_BASE as string | undefined) ?? '/api'

export const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
})

let csrfToken: string | null = null
export function setCsrfToken(value: string | null) { csrfToken = value }
http.interceptors.request.use(config => {
  if (csrfToken && config.method?.toUpperCase() !== 'GET') config.headers.set('X-CSRF-Token', csrfToken)
  return config
})

/** 后端业务错误，携带业务 code 与 message。 */
export class ApiError extends Error {
  readonly code: string

  constructor(code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/** 页面只展示可行动的提示，避免把数据库或 SDK 内部异常直接展示给用户。 */
export function apiErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') return '读取超时，请稍后重试。'
    if (error.code === 'network' || error.code === 'ERR_NETWORK') return '暂时无法连接服务，请检查网络后重试。'
    if (error.code === '401') return '登录已失效，请重新登录。'
    if (error.code === '403') return '当前无权读取这些记录，请联系管理员。'
    if (error.code === '404') return '查询入口暂不可用，请稍后重试。'
  }
  return '暂时无法读取记录，请稍后重试。'
}

function toApiError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as CommonResponse | undefined
    const code = payload?.code ?? String(error.response?.status ?? error.code ?? 'network')
    const message = payload?.message || error.message || '网络异常，请稍后重试'
    return new ApiError(code, message)
  }
  return new ApiError('unknown', error instanceof Error ? error.message : '未知错误')
}

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const requestCsrf = csrfToken
  try {
    const response = await http.request<CommonResponse<T>>(config)
    const body = response.data
    if (!body || body.code !== OK_CODE) {
      throw new ApiError(body?.code ?? 'unknown', body?.message || '请求失败')
    }
    return body.data
  } catch (error) {
    const apiError = error instanceof ApiError ? error : toApiError(error)
    if (apiError.code === '401' && config.url !== '/auth/login' && requestCsrf === csrfToken) window.dispatchEvent(new Event('agritrace:unauthorized'))
    throw apiError
  }
}

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>({ method: 'GET', url, params })
}

export function post<T>(url: string, data?: unknown, timeout = 15000): Promise<T> {
  return request<T>({ method: 'POST', url, data, timeout })
}

export function postMultipart<T>(url: string, data: FormData): Promise<T> {
  return request<T>({ method: 'POST', url, data, timeout: 60000, headers: { 'Content-Type': undefined } })
}
