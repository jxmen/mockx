import { describe, expect, it } from 'vitest'
import { validateSymbolInput } from './symbol'

describe('validateSymbolInput', () => {
  it.each([
    ['MU', 'MU'],
    ['mu', 'MU'],
    ['sKhY', 'SKHY'],
    [' spy ', 'SPY'],
    ['A', 'A'], // 1자
    ['ABCDEFGHIJ', 'ABCDEFGHIJ'], // 10자
    ['abcdefghij', 'ABCDEFGHIJ'],
    ['  ABCDEFGHIJ  ', 'ABCDEFGHIJ'], // 앞뒤 공백을 지우면 10자
  ])('"%s"는 받고 %s로 대문자로 바꿔 돌려준다', (input, expected) => {
    expect(validateSymbolInput(input)).toEqual({ ok: true, symbol: expected })
  })

  it.each([
    ['', '빈 값'],
    ['   ', '공백만'],
    ['ABCDEFGHIJK', '11자'],
    ['abcdefghijk', '11자(소문자)'],
    ['MU1', '숫자 포함'],
    ['123', '숫자만'],
    ['BRK.B', '점 포함'],
    ['S PY', '가운데 공백'],
    ['A/B', '경로 구분자'],
    ['삼성', '한글'],
    ['mü', '악센트 문자'],
    ['ſpy', '롱 s(대문자로 바꾸면 SPY가 됨)'],
    ['ıbm', '점 없는 i(대문자로 바꾸면 IBM이 됨)'],
    ['Key', '켈빈 기호(i 플래그 + u 플래그면 k와 같게 취급됨)'],
  ])('"%s"(%s)는 거절한다', (input) => {
    const result = validateSymbolInput(input)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).not.toBe('')
    }
  })
})
