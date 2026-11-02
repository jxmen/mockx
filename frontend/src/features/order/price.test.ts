import { describe, expect, it } from 'vitest'
import { formatCents } from '../../lib/money'
import {
  PRICE_MAX,
  PRICE_MIN,
  QUANTITY_MAX,
  QUANTITY_MIN,
  parsePriceInput,
  parseQuantityInput,
  toRawNumberInput,
} from './price'

// 서버 입력 규칙(PlaceOrderRequest: price @Positive @Max(1000000) 센트, quantity @Positive @Max(10000))
describe('주문 입력 범위 상수', () => {
  it('가격은 1 ~ 1,000,000센트, 수량은 1 ~ 10,000으로 서버 규칙과 같다', () => {
    expect(PRICE_MIN).toBe(1)
    expect(PRICE_MAX).toBe(1_000_000)
    expect(QUANTITY_MIN).toBe(1)
    expect(QUANTITY_MAX).toBe(10_000)
  })
})

describe('parsePriceInput', () => {
  it.each([
    ['0.01', 1], // 하한
    ['1', 100],
    ['187.43', 18_743],
    ['187.4', 18_740],
    ['1,000.5', 100_050],
    [' 12 ', 1_200],
    ['9999.99', 999_999],
    ['10000', 1_000_000], // 상한
    ['10,000.00', 1_000_000],
  ])('"%s"는 %d센트로 받는다', (input, expected) => {
    expect(parsePriceInput(input)).toEqual({ ok: true, cents: expected })
  })

  // Number(x) * 100으로 바꾸면 정수가 아니게 되는 값들 (예: 1.15 * 100 = 114.99999999999999)
  it.each([
    ['0.29', 29],
    ['1.13', 113],
    ['1.15', 115],
    ['4.35', 435],
    ['0.07', 7],
    ['0.57', 57],
    ['1.1', 110],
    ['8.2', 820],
    ['19.99', 1_999],
  ])('"%s"는 부동소수 오차 없이 %d센트로 바꾼다', (input, expected) => {
    const result = parsePriceInput(input)
    expect(result).toEqual({ ok: true, cents: expected })
    if (result.ok) {
      expect(Number.isInteger(result.cents)).toBe(true)
    }
  })

  it.each([
    ['', '빈 값'],
    ['   ', '공백만'],
    ['abc', '문자'],
    ['1e3', '지수 표기'],
    ['1.2.3', '점이 두 개'],
    ['.', '점만'],
    ['$10', '달러 기호'],
    ['1.005', '소수 셋째 자리(부동소수 오차가 나는 값)'],
    ['187.431', '소수 셋째 자리'],
    ['0.001', '소수 셋째 자리'],
  ])('"%s"(%s)는 거절한다', (input) => {
    const result = parsePriceInput(input)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).not.toBe('')
    }
  })

  it.each([
    ['0'],
    ['0.00'],
    ['-1'],
    ['-0.01'],
  ])('"%s"는 하한 아래라 거절하고 문구에 하한 금액을 보여 준다', (input) => {
    const result = parsePriceInput(input)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toContain(formatCents(PRICE_MIN))
    }
  })

  it.each([
    ['10000.01'], // 1,000,001센트
    ['10,001'],
    ['9223372036854775808'],
  ])('"%s"는 상한을 넘어 거절하고 문구에 상한 금액을 보여 준다', (input) => {
    const result = parsePriceInput(input)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).toContain(formatCents(PRICE_MAX))
    }
  })
})

describe('parseQuantityInput', () => {
  it.each([
    ['1', 1], // 하한
    ['25', 25],
    [' 5 ', 5],
    ['1,000', 1_000],
    ['10000', 10_000], // 상한
    ['10,000', 10_000],
  ])('"%s"는 수량 %d로 받는다', (input, expected) => {
    expect(parseQuantityInput(input)).toEqual({ ok: true, quantity: expected })
  })

  it.each([
    ['', '빈 값'],
    ['   ', '공백만'],
    ['abc', '문자'],
    ['1e3', '지수 표기'],
    ['1.5', '소수'],
    ['0.5', '소수'],
    ['0', '0'],
    ['-1', '음수'],
    ['10001', '상한 바로 위'],
    ['10,001', '상한 바로 위(쉼표)'],
    ['9223372036854775808', 'Long 범위 밖'],
  ])('"%s"(%s)는 거절한다', (input) => {
    const result = parseQuantityInput(input)
    expect(result.ok).toBe(false)
    if (!result.ok) {
      expect(result.error).not.toBe('')
    }
  })
})

describe('toRawNumberInput', () => {
  it.each([
    ['187.43', 187.43], // 달러 → 센트 변환을 하지 않는다
    ['10.5', 10.5],
    ['100', 100],
    ['-1', -1],
    ['0', 0],
    ['1,000', 1_000],
    [' 12 ', 12],
    ['abc', 'abc'],
    ['1.2.3', '1.2.3'],
    ['', undefined],
    ['   ', undefined],
  ])('"%s"는 변환 없이 %o로 보낸다', (input, expected) => {
    expect(toRawNumberInput(input)).toBe(expected)
  })
})
