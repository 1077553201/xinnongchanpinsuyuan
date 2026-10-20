import { get, post } from './http'

export function iotStatus(): Promise<{ mqttEnabled: boolean; mqttConnected: boolean }> { return get('/iot/status') }
import type {
  AggregateWindowRequest,
  IotAlertEntity,
  IotDataWindowEntity,
  IotDeviceEntity,
  IotGatewayEntity,
  IotIngestResult,
  IotSensorReadingEntity,
  RegisterDeviceRequest,
  RegisterGatewayRequest,
  TelemetryRequest,
} from '@/types/iot'

export function listGateways(): Promise<IotGatewayEntity[]> {
  return get<IotGatewayEntity[]>('/iot/gateways')
}

export function registerGateway(body: RegisterGatewayRequest): Promise<IotGatewayEntity> {
  return post<IotGatewayEntity>('/iot/gateways', body)
}

export function listDevices(): Promise<IotDeviceEntity[]> {
  return get<IotDeviceEntity[]>('/iot/devices')
}

export function registerDevice(body: RegisterDeviceRequest): Promise<IotDeviceEntity> {
  return post<IotDeviceEntity>('/iot/devices', body)
}

export function listReadings(params?: {
  deviceCode?: string
  metric?: string
  limit?: number
}): Promise<IotSensorReadingEntity[]> {
  return get<IotSensorReadingEntity[]>('/iot/readings', params)
}

export function listWindows(params?: {
  deviceCode?: string
  limit?: number
}): Promise<IotDataWindowEntity[]> {
  return get<IotDataWindowEntity[]>('/iot/windows', params)
}

export function listAlerts(params?: {
  deviceCode?: string
  limit?: number
}): Promise<IotAlertEntity[]> {
  return get<IotAlertEntity[]>('/iot/alerts', params)
}

/** 模拟网关上报一条遥测，payload 与真实 MQTT 格式一致。 */
export function pushTelemetry(body: TelemetryRequest): Promise<IotIngestResult> {
  return post<IotIngestResult>('/iot/telemetry/mock', body)
}

/** 手动触发指定时间段的窗口聚合。 */
export function aggregateWindow(body: AggregateWindowRequest): Promise<IotDataWindowEntity | null> {
  return post<IotDataWindowEntity | null>('/iot/windows/aggregate', body)
}
