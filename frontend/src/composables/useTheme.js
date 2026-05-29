import { ref } from 'vue'

export const THEME_KEY = 'rag-ui-theme'

export const themes = [
  { id: 'aurora', label: '极光', dark: true },
  { id: 'moonlight', label: '月影', dark: true },
  { id: 'studio', label: '画室', dark: false },
  { id: 'sand', label: '晨曦', dark: false },
]

export function getStoredThemeId() {
  try {
    return localStorage.getItem(THEME_KEY) || 'aurora'
  } catch {
    return 'aurora'
  }
}

const currentId = ref(getStoredThemeId())

export function applyTheme(id) {
  const t = themes.find((x) => x.id === id) || themes[0]
  document.documentElement.setAttribute('data-theme', t.id)
  document.documentElement.classList.toggle('dark', !!t.dark)
  try {
    localStorage.setItem(THEME_KEY, t.id)
  } catch {
    /* ignore */
  }
}

export function initTheme() {
  const id = getStoredThemeId()
  applyTheme(id)
  currentId.value = id
}

export function useTheme() {
  function setTheme(id) {
    applyTheme(id)
    currentId.value = id
  }

  return {
    themes,
    currentId,
    setTheme,
  }
}
