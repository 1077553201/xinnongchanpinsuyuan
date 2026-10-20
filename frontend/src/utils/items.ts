export function validateCount(value: number): boolean {
  return Number.isSafeInteger(value) && value >= 1 && value <= 100
}
export function traceUrl(code: string, base: string): string {
  if (!code.trim()) throw new Error('缺少溯源编号，无法生成二维码。')
  const url = new URL(base)
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) throw new Error('请填写有效的 HTTP 或 HTTPS 前端访问地址。')
  return new URL(`/t/${encodeURIComponent(code.trim())}`, url.origin).href
}
export function eventTime(value: string): string {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value)) throw new Error('请填写完整的事件时间。')
  return value.replace('T', ' ') + (value.length === 16 ? ':00' : '')
}
