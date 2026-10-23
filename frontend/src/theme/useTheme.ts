import { useCallback, useEffect, useState } from 'react'
import { DARK_QUERY, THEME_STORAGE_KEY, parseStoredTheme, resolveTheme, toStoredTheme, type Theme } from './theme'

function readSystemTheme(): Theme {
  return window.matchMedia(DARK_QUERY).matches ? 'dark' : 'light'
}

function readStored(): Theme | null {
  try {
    return parseStoredTheme(localStorage.getItem(THEME_STORAGE_KEY))
  } catch {
    return null
  }
}

function writeStored(theme: Theme | null) {
  try {
    if (theme === null) {
      localStorage.removeItem(THEME_STORAGE_KEY)
    } else {
      localStorage.setItem(THEME_STORAGE_KEY, theme)
    }
  } catch {
    // 저장소를 쓸 수 없는 환경(사생활 보호 모드 등)이면 이번 탭에서만 유지한다
  }
}

/**
 * 라이트/다크 테마. 처음에는 브라우저(OS) 설정을 따르고, toggle로 바꾼 값은 localStorage에 남긴다.
 * 첫 화면 깜빡임을 막으려고 index.html 스크립트가 렌더링 전에 같은 규칙으로 data-theme을 먼저 정한다.
 */
export function useTheme() {
  const [system, setSystem] = useState<Theme>(readSystemTheme)
  const [stored, setStored] = useState<Theme | null>(readStored)
  const theme = resolveTheme(stored, system)

  // 직접 고른 값이 없으면 OS 설정을 바꾸는 즉시 따라간다
  useEffect(() => {
    const media = window.matchMedia(DARK_QUERY)
    const onChange = (event: MediaQueryListEvent) => setSystem(event.matches ? 'dark' : 'light')
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = theme
  }, [theme])

  const toggle = useCallback(() => {
    const next = toStoredTheme(theme === 'dark' ? 'light' : 'dark', system)
    setStored(next)
    writeStored(next)
  }, [theme, system])

  return { theme, toggle }
}
