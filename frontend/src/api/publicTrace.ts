import { get } from './http'
import type { PublicTraceView } from '@/types/trace'

/** 公众扫码 / NFC 查询入口，无需鉴权。 */
export function fetchTrace(traceCode: string): Promise<PublicTraceView> {
  return get<PublicTraceView>(`/public/trace/${encodeURIComponent(traceCode)}`)
}
