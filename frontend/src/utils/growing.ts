import { ApiError } from '@/api/http'

export function operationError(error: unknown): string {
  if (error instanceof ApiError && ['400', '403'].includes(error.code)) return error.message
  if (error instanceof ApiError && error.code === '409') return '编码或操作标识已存在，请关闭窗口刷新核对。'
  return '无法确认操作结果，请关闭窗口刷新核对，避免重复办理。'
}
export function uncertainOperation(error: unknown): boolean {
  return !(error instanceof ApiError && ['400', '401', '403', '409'].includes(error.code))
}
export function newOperationKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 15) | 64; bytes[8] = (bytes[8]! & 63) | 128
  const text = [...bytes].map(value => value.toString(16).padStart(2, '0')).join('')
  return `${text.slice(0, 8)}-${text.slice(8, 12)}-${text.slice(12, 16)}-${text.slice(16, 20)}-${text.slice(20)}`
}
export function localDateTime(): string {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
