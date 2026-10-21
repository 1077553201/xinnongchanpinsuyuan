import { computed, readonly, shallowRef } from 'vue'
import { ApiError, get, post, setCsrfToken } from '@/api/http'

export interface User { id: number; organizationId: number; username: string; displayName: string; role: 'ADMIN' | 'MANAGER' | 'VIEWER' }
interface Session { user: User; csrfToken: string }
const user = shallowRef<User | null>(null)
let pending: Promise<boolean> | null = null
let revision = 0
function accept(session: Session) { user.value = session.user; setCsrfToken(session.csrfToken) }
function clear() { revision++; user.value = null; setCsrfToken(null) }
window.addEventListener('agritrace:unauthorized', clear)

export function useAuth() {
  async function restore() {
    const current = revision
    if (!pending) pending = get<Session>('/auth/me').then(session => { if (current !== revision) return !!user.value; accept(session); return true }).catch(error => {
      if (error instanceof ApiError && error.code === '401') { clear(); return false }
      throw error
    }).finally(() => { pending = null })
    return pending
  }
  async function login(username: string, password: string) { revision++; accept(await post<Session>('/auth/login', { username, password })) }
  async function logout() { try { await post('/auth/logout') } finally { clear() } }
  return { user: readonly(user), isAdmin: computed(() => user.value?.role === 'ADMIN'), canWrite: computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'MANAGER'), restore, login, logout, clear }
}
