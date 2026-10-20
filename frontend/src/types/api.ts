/** 后端统一响应包装：{ code, message, data }，成功 code 为 "0"。 */
export interface CommonResponse<T = unknown> {
  code: string
  message?: string
  data: T
}

export const OK_CODE = '0'

/** 链上交易状态，与后端 ChainStatus 枚举一一对应。 */
export type ChainStatus = 'PENDING' | 'CONFIRMED' | 'FAILED' | 'CHAIN_DISABLED'

export type Tone = 'ok' | 'wait' | 'bad' | 'off'

export const CHAIN_STATUS_LABEL: Record<string, string> = {
  CONFIRMED: '上链已确认',
  PENDING: '等待上链',
  FAILED: '上链失败',
  CHAIN_DISABLED: '未上链',
}

export const CHAIN_STATUS_TONE: Record<string, Tone> = {
  CONFIRMED: 'ok',
  PENDING: 'wait',
  FAILED: 'bad',
  CHAIN_DISABLED: 'off',
}

/** 溯源事件类型，与 contract/README.md 的 ChainEventType 一致。 */
export const EVENT_TYPE_LABEL: Record<string, string> = {
  SOWING: '播种',
  FARMING: '农事管理',
  HARVEST: '采收',
  PROCESSING: '加工',
  QUALITY_TEST: '质检',
  WAREHOUSE_IN: '入库',
  WAREHOUSE_OUT: '出库',
  LOGISTICS: '物流',
  STORE_ARRIVAL: '到店',
  SALE: '销售',
  RECALL: '召回',
}

export interface MetricMeta {
  label: string
  unit: string
}

/** 传感器指标，与后端 IotConstants 以及 trace.iot.alerts 的键一致。 */
export const METRIC_META: Record<string, MetricMeta> = {
  temperature: { label: '温度', unit: '°C' },
  humidity: { label: '空气湿度', unit: '%' },
  soil_moisture: { label: '土壤水分', unit: '%' },
  light: { label: '光照', unit: 'lux' },
}

export function metricMeta(metric: string): MetricMeta {
  return METRIC_META[metric] ?? { label: metric, unit: '' }
}

export function eventTypeLabel(type?: string): string {
  if (!type) return '未知环节'
  return EVENT_TYPE_LABEL[type] ?? type
}

export function chainStatusLabel(status?: string): string {
  if (!status) return '状态未知'
  return CHAIN_STATUS_LABEL[status] ?? '状态未知'
}

export function chainStatusTone(status?: string): Tone {
  if (!status) return 'off'
  return CHAIN_STATUS_TONE[status] ?? 'off'
}

/** 哈希截断展示：保留头尾，中间省略。 */
export function shortHash(value?: string | null, head = 10, tail = 6): string {
  if (!value) return '—'
  if (value.length <= head + tail + 1) return value
  return `${value.slice(0, head)}…${value.slice(-tail)}`
}

/** 后端时间格式为 yyyy-MM-dd HH:mm:ss，此处仅做展示裁剪。 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

export function formatDate(value?: string | null): string {
  if (!value) return '—'
  return value.slice(0, 10)
}

/** 数值展示：去掉无意义的小数尾零，最多保留两位。 */
export function formatNumber(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') return '—'
  const num = typeof value === 'number' ? value : Number(value)
  if (Number.isNaN(num)) return String(value)
  return String(Number(num.toFixed(2)))
}
