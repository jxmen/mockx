import { describe, expect, it } from 'vitest'
import { addToAmount, parseDepositAmount, toRawAmount } from './amount'

describe('parseDepositAmount', () => {
  it.each([
    ['1', 1],
    ['10000', 10_000],
    ['10,000', 10_000],
    [' 2500 ', 2_500],
    ['007', 7],
  ])('"%s"는 %d달러로 받는다', (input, expected) => {
    expect(parseDepositAmount(input)).toEqual({ ok: true, amount: expected })
  })

  it.each([
    ['', '입금할 금액을 입력해 주세요.'],
    ['   ', '입금할 금액을 입력해 주세요.'],
    ['10.5', '센트(소수)는 입금할 수 없어요. 달러 단위 정수로 입력해 주세요.'],
    ['.5', '센트(소수)는 입금할 수 없어요. 달러 단위 정수로 입력해 주세요.'],
    ['abc', '숫자만 입력해 주세요.'],
    ['1e3', '숫자만 입력해 주세요.'],
    ['0', '$1 이상 입금할 수 있어요.'],
    ['-1', '$1 이상 입금할 수 있어요.'],
    ['10001', '1회 최대 $10,000까지 입금할 수 있어요.'],
    ['9223372036854775808', '1회 최대 $10,000까지 입금할 수 있어요.'],
  ])('"%s"는 거절한다: %s', (input, error) => {
    expect(parseDepositAmount(input)).toEqual({ ok: false, error })
  })
})

describe('toRawAmount', () => {
  it.each([
    ['10.5', 10.5],
    ['-1', -1],
    ['1,000', 1_000],
    ['abc', 'abc'],
    ['', undefined],
  ])('"%s"는 %o로 보낸다', (input, expected) => {
    expect(toRawAmount(input)).toBe(expected)
  })
})

describe('addToAmount', () => {
  it('올바른 금액에 더한다', () => {
    expect(addToAmount('1,000', 100)).toBe('1100')
  })

  it('비었거나 잘못된 값이면 0에서 시작한다', () => {
    expect(addToAmount('', 1_000)).toBe('1000')
    expect(addToAmount('abc', 100)).toBe('100')
  })
})
