import { describe, expect, it } from 'vitest'
import { addToAmount, centsToInput, parseDepositAmount, toRawAmount } from './amount'

describe('parseDepositAmount', () => {
  it.each([
    ['0.01', 1],
    ['1', 100],
    ['18.74', 1_874],
    ['18.7', 1_870],
    ['10.', 1_000],
    ['.5', 50],
    ['10000', 1_000_000],
    ['10,000.00', 1_000_000],
    [' 2500 ', 250_000],
    ['007', 700],
  ])('"%s"는 %d센트로 받는다', (input, expected) => {
    expect(parseDepositAmount(input)).toEqual({ ok: true, amount: expected })
  })

  // Number(x) * 100으로 바꾸면 정수가 아니게 되는 값들 (예: 1.15 * 100 = 114.99999999999999)
  it.each([
    ['0.29', 29],
    ['1.13', 113],
    ['1.15', 115],
    ['4.35', 435],
  ])('"%s"는 부동소수 오차 없이 %d센트로 바꾼다', (input, expected) => {
    expect(parseDepositAmount(input)).toEqual({ ok: true, amount: expected })
  })

  it.each([
    ['', '입금할 금액을 입력해 주세요.'],
    ['   ', '입금할 금액을 입력해 주세요.'],
    ['10.555', '센트(소수 둘째 자리)까지만 입력할 수 있어요.'],
    ['.', '숫자만 입력해 주세요.'],
    ['abc', '숫자만 입력해 주세요.'],
    ['1e3', '숫자만 입력해 주세요.'],
    ['1.2.3', '숫자만 입력해 주세요.'],
    ['0', '$0.01 이상 입금할 수 있어요.'],
    ['0.00', '$0.01 이상 입금할 수 있어요.'],
    ['-1', '$0.01 이상 입금할 수 있어요.'],
    ['10000.01', '1회 최대 $10,000.00까지 입금할 수 있어요.'],
    ['9223372036854775808', '1회 최대 $10,000.00까지 입금할 수 있어요.'],
  ])('"%s"는 거절한다: %s', (input, error) => {
    expect(parseDepositAmount(input)).toEqual({ ok: false, error })
  })
})

describe('centsToInput', () => {
  it.each([
    [1, '0.01'],
    [1_050, '10.50'],
    [100_000, '1000'],
    [1_000_000, '10000'],
  ])('%d센트는 "%s"로 입력칸에 넣는다', (cents, expected) => {
    expect(centsToInput(cents)).toBe(expected)
  })
})

describe('toRawAmount', () => {
  it.each([
    ['10.5', 10.5],
    ['-1', -1],
    ['1,000', 1_000],
    ['abc', 'abc'],
    ['', undefined],
  ])('"%s"는 센트로 바꾸지 않고 %o로 보낸다', (input, expected) => {
    expect(toRawAmount(input)).toBe(expected)
  })
})

describe('addToAmount', () => {
  it('올바른 금액에 센트 단위 delta를 더한다', () => {
    expect(addToAmount('1,000', 10_000)).toBe('1100')
    expect(addToAmount('0.50', 10_000)).toBe('100.50')
  })

  it('비었거나 잘못된 값이면 0에서 시작한다', () => {
    expect(addToAmount('', 100_000)).toBe('1000')
    expect(addToAmount('abc', 10_000)).toBe('100')
  })
})
