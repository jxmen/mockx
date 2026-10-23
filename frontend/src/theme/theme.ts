export type Theme = 'light' | 'dark'

// index.html의 첫 화면 스크립트도 같은 키를 읽는다. 바꾸면 둘 다 바꾼다
export const THEME_STORAGE_KEY = 'mockx.theme'
export const DARK_QUERY = '(prefers-color-scheme: dark)'

/** 저장된 값이 light, dark가 아니면(없거나 손으로 바꾼 값) 시스템 설정을 따른다 */
export function parseStoredTheme(value: string | null): Theme | null {
  return value === 'light' || value === 'dark' ? value : null
}

/** 사용자가 고른 테마가 있으면 그것을, 없으면 브라우저(OS) 설정을 쓴다 */
export function resolveTheme(stored: Theme | null, system: Theme): Theme {
  return stored ?? system
}

/**
 * 전환한 테마를 저장할 값. 시스템 설정과 같아지면 저장을 지워(null) 다시 시스템 설정을 따르게 한다.
 * 그래서 한 번 바꿨다가 되돌리면 이후 OS 설정 변경도 그대로 반영된다.
 */
export function toStoredTheme(next: Theme, system: Theme): Theme | null {
  return next === system ? null : next
}
