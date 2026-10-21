import { readonly, ref } from 'vue'

export type Theme = 'morning' | 'soft-glass'
const storageKey = 'agritrace.appearance'
const theme = ref<Theme>('morning')

export function applyTheme(value: Theme) {
  theme.value = value
  document.documentElement.dataset.theme = value
  try { localStorage.setItem(storageKey, value) } catch { /* 隐私模式下仍支持本次会话切换。 */ }
}

export function initializeTheme() {
  let stored: string | null = null
  try { stored = localStorage.getItem(storageKey) } catch { /* 使用默认外观。 */ }
  applyTheme(stored === 'soft-glass' ? 'soft-glass' : 'morning')
  window.addEventListener('storage', (event) => {
    if (event.key !== storageKey) return
    const value = event.newValue === 'soft-glass' ? 'soft-glass' : 'morning'
    theme.value = value
    document.documentElement.dataset.theme = value
  })
}

export function useTheme() { return { theme: readonly(theme), applyTheme } }
