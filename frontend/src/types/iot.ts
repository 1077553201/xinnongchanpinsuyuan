/** 与后端 IoT 实体对齐的类型。 */

export interface IotGatewayEntity {
  id: number
  farmId: number
  gatewayCode: string
  gatewayName?: string
  mqttClientId?: string
  status: string
}

export interface IotDeviceEntity {
  id: number
  gatewayId?: number
  farmId: number
  gatewayCode?: string
  deviceCode: string
  deviceName?: string
  deviceType?: string
  status: string
  lastSeenAt?: string
}

export interface IotSensorReadingEntity {
  id: number
  cycleId?: number
  plotId?: number
  bindingId?: number
  resolution?: string
  resolutionReason?: string
  farmId: number
  gatewayCode: string
  deviceCode: string
  batchId?: number
  metric: string
  value: number | string
  unit?: string
  signature?: string
  reportedAt: string
  status: string
}

export interface IotDataWindowEntity {
  id: number
  cycleId?: number
  plotId?: number
  bindingId?: number
  farmId: number
  batchId?: number
  gatewayCode?: string
  deviceCode: string
  metric: string
  windowStart: string
  windowEnd: string
  minValue?: number | string
  maxValue?: number | string
  avgValue?: number | string
  sampleCount: number
  dataHash: string
  chainStatus: string
  status: string
}

export type AlertType = 'THRESHOLD_HIGH' | 'THRESHOLD_LOW' | 'DEVICE_OFFLINE'

export interface IotAlertEntity {
  id: number
  farmId: number
  gatewayCode?: string
  deviceCode: string
  metric: string
  alertType: AlertType | string
  level: string
  value?: number | string
  thresholdMin?: number | string
  thresholdMax?: number | string
  message?: string
  occurredAt: string
  status: string
}

/* ---- 请求体 ---- */

export interface TelemetryRequest {
  farmId?: number
  gatewayCode?: string
  deviceCode?: string
  metric?: string
  value?: number
  unit?: string
  /** 格式 yyyy-MM-dd HH:mm:ss，缺省时后端按当前时间处理 */
  reportedAt?: string
  signature?: string
  batchCode?: string
}

export interface RegisterGatewayRequest {
  farmId: number
  gatewayCode: string
  gatewayName?: string
  mqttClientId?: string
}

export interface RegisterDeviceRequest {
  farmId: number
  gatewayCode: string
  deviceCode: string
  deviceName?: string
  deviceType?: string
}

export interface AggregateWindowRequest {
  deviceCode: string
  metric: string
  /** 格式 yyyy-MM-dd HH:mm:ss */
  windowStart: string
  windowEnd: string
  farmId?: number
  gatewayCode?: string
}

export interface IotIngestResult {
  reading?: IotSensorReadingEntity
  alerts?: IotAlertEntity[]
  window?: IotDataWindowEntity
}

export const ALERT_TYPE_LABEL: Record<string, string> = {
  THRESHOLD_HIGH: '高于上限',
  THRESHOLD_LOW: '低于下限',
  DEVICE_OFFLINE: '设备离线',
}

export function alertTypeLabel(type?: string): string {
  if (!type) return '异常'
  return ALERT_TYPE_LABEL[type] ?? type
}
