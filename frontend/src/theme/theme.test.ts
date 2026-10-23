import { describe, expect, it } from 'vitest'
import { parseStoredTheme, resolveTheme, toStoredTheme } from './theme'

describe('parseStoredTheme', () => {
  it.each([
    ['light', 'light'],
    ['dark', 'dark'],
    [null, null],
    ['', null],
    ['DARK', null],
    ['system', null],
  ])('저장값 %o는 %o로 읽는다', (value, expected) => {
    expect(parseStoredTheme(value)).toBe(expected)
  })
})

describe('resolveTheme', () => {
  it.each([
    [null, 'light', 'light'],
    [null, 'dark', 'dark'],
    ['dark', 'light', 'dark'],
    ['light', 'dark', 'light'],
  ] as const)('저장값이 %o이고 시스템이 %s이면 %s를 쓴다', (stored, system, expected) => {
    expect(resolveTheme(stored, system)).toBe(expected)
  })
})

describe('toStoredTheme', () => {
  it('시스템과 다른 테마로 바꾸면 저장한다', () => {
    expect(toStoredTheme('dark', 'light')).toBe('dark')
    expect(toStoredTheme('light', 'dark')).toBe('light')
  })

  it('시스템과 같은 테마로 되돌리면 저장을 지워 다시 시스템을 따른다', () => {
    expect(toStoredTheme('light', 'light')).toBeNull()
    expect(toStoredTheme('dark', 'dark')).toBeNull()
  })
})
