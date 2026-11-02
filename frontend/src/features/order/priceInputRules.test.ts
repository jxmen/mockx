import { describe, expect, it } from 'vitest'
import { parsePriceInput, parseQuantityInput } from './price'

// QA 테스트(price.test.ts) 밖에서 메인이 정한 입력 규칙. 입금 화면(parseDepositAmount)과 같게 받는다
describe('parsePriceInput 추가 규칙', () => {
  it.each([
    ['.5', 50],
    ['10.', 1_000],
    ['007.10', 710],
  ])('"%s"는 입금 입력과 같이 %d센트로 받는다', (input, expected) => {
    expect(parsePriceInput(input)).toEqual({ ok: true, cents: expected })
  })
})

describe('parseQuantityInput 추가 규칙', () => {
  it('앞자리 0은 지우고 받는다("007" → 7)', () => {
    expect(parseQuantityInput('007')).toEqual({ ok: true, quantity: 7 })
  })

  it.each([['1.0'], ['+1'], ['1.'], ['.5']])('"%s"는 정수 숫자만 있는 값이 아니라 거절한다', (input) => {
    const result = parseQuantityInput(input)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).not.toBe('')
    }
  })
})
