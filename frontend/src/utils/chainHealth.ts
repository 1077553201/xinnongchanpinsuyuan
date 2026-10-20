import type { Tone } from '../types/api'
import type { ChainHealthResult } from '../types/chain'

export function chainHealthBadge(health: ChainHealthResult | null): { tone: Tone; label: string } {
  if (!health) return { tone: 'off', label: '状态未知' }
  if (health.status === 'FAILED' || health.errorMessage) return { tone: 'bad', label: '连接异常' }
  // chainEnabled 表示写链配置是否齐备，不能代替节点健康检查结果。
  if (health.status === 'OK') return { tone: 'ok', label: '连接正常' }
  return { tone: 'off', label: '状态未知' }
}
