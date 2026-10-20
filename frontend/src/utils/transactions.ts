export const BUSINESS_LABELS: Record<string, string> = {
  BATCH_REGISTER: '批次注册', BATCH_CLOSE: '批次关闭 / 召回', ITEM_BIND: '单品码绑定', TRACE_EVENT_APPEND: '溯源事件',
  NFC_BIND: 'NFC 绑定', NFC_DISABLE: 'NFC 停用', SENSOR_WINDOW: '传感器窗口', SENSOR_WINDOW_APPEND: '传感器窗口',
}
export function businessPath(type: string, id: number): string {
  if (type.startsWith('BATCH')) return '/admin/products'
  if (type.startsWith('NFC')) return '/admin/nfc'
  if (type.includes('SENSOR') || type.includes('IOT')) return '/admin/iot'
  if (type === 'ITEM_BIND') return `/admin/items?itemId=${id}`
  return '/admin/items'
}
