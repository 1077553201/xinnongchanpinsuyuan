import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import type { CommonResponse } from '@/types/api'
import { OK_CODE } from '@/types/api'

const baseURL = (import.meta.env.VITE_API_BASE as string | undefined) ?? '/api'

export const http: AxiosInstance = axios.create({
  baseURL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
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

function toApiError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as CommonResponse | undefined
    const code = payload?.code ?? String(error.response?.status ?? 'network')
    const message = payload?.message || error.message || '网络异常，请稍后重试'
    return new ApiError(code, message)
  }
  return new ApiError('unknown', error instanceof Error ? error.message : '未知错误')
}

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await http.request<CommonResponse<T>>(config)
    const body = response.data
    if (!body || body.code !== OK_CODE) {
      throw new ApiError(body?.code ?? 'unknown', body?.message || '请求失败')
    }
    return body.data
  } catch (error) {
    throw error instanceof ApiError ? error : toApiError(error)
  }
}

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return request<T>({ method: 'GET', url, params })
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return request<T>({ method: 'POST', url, data })
}
