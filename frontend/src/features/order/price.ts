import { formatCents } from '../../lib/money'
import { toRawAmount } from '../deposit/amount'

// 서버 입력 규칙(PlaceOrderRequest: price @Positive @Max(1000000), quantity @Positive @Max(10000))과 같은 범위
export const PRICE_MIN = 1 // $0.01
export const PRICE_MAX = 1_000_000 // $10,000.00
export const QUANTITY_MIN = 1
export const QUANTITY_MAX = 10_000

export type PriceParseResult = { ok: true; cents: number } | { ok: false; error: string }
export type QuantityParseResult = { ok: true; quantity: number } | { ok: false; error: string }

/** 입력칸의 쉼표와 앞뒤 공백을 지운다. "1,000" 처럼 쉼표를 넣어 입력해도 받는다. */
function normalize(input: string): string {
  return input.replace(/,/g, '').trim()
}

/**
 * 달러로 입력한 주문 가격(예: "187.43")을 센트 정수(18743)로 바꾼다. 전송 전 안내용 검증이고, 최종 판정은 서버가 한다.
 * `1.15 * 100 = 114.99999999999999`처럼 부동소수 오차가 생기므로 곱셈 없이 정수부와 소수부 문자열을 이어 붙여 센트로 읽는다.
 * (deposit/amount.ts의 parseDepositAmount와 같은 입력 규칙. ".5", "10."도 받는다)
 */
export function parsePriceInput(input: string): PriceParseResult {
  const value = normalize(input)

  if (value === '') {
    return { ok: false, error: '주문 가격을 입력해 주세요.' }
  }
  const match = /^(-?)(\d*)(?:\.(\d*))?$/.exec(value)
  if (match === null || (match[2] === '' && (match[3] ?? '') === '')) {
    return { ok: false, error: '숫자만 입력해 주세요.' }
  }
  const [, sign, dollarPart, centPart = ''] = match
  if (centPart.length > 2) {
    return { ok: false, error: '센트(소수 둘째 자리)까지만 입력할 수 있어요.' }
  }

  // "187" + "43" → 18743. 아주 긴 숫자도 Number로 읽으면 상한보다 커지므로 범위 검사에 걸린다
  const cents = Number(`${dollarPart}${centPart.padEnd(2, '0')}`)
  const price = sign === '-' ? -cents : cents
  if (price < PRICE_MIN) {
    return { ok: false, error: `${formatCents(PRICE_MIN)} 이상으로 입력해 주세요.` }
  }
  if (price > PRICE_MAX) {
    return { ok: false, error: `가격은 최대 ${formatCents(PRICE_MAX)}까지 입력할 수 있어요.` }
  }
  return { ok: true, cents: price }
}

/**
 * 주문 수량(주)을 정수로 바꾼다. 쉼표·앞뒤 공백을 지운 뒤 숫자만 있을 때 받는다("007" → 7). 소수("1.0" 포함), 부호("+1", "-1")는 거절한다.
 * 전송 전 안내용 검증이고, 최종 판정은 서버가 한다.
 */
export function parseQuantityInput(input: string): QuantityParseResult {
  const value = normalize(input)

  if (value === '') {
    return { ok: false, error: '주문 수량을 입력해 주세요.' }
  }
  if (!/^\d+$/.test(value)) {
    if (/^[+-]?(\d+\.\d*|\.\d+)$/.test(value)) {
      return { ok: false, error: '수량은 1주 단위 정수로 입력해 주세요.' }
    }
    if (/^-\d+$/.test(value)) {
      return { ok: false, error: `${QUANTITY_MIN}주 이상으로 입력해 주세요.` }
    }
    return { ok: false, error: '숫자만 입력해 주세요.' }
  }

  // 아주 긴 숫자도 Number로 읽으면 상한보다 커지므로 범위 검사에 걸린다
  const quantity = Number(value)
  if (quantity < QUANTITY_MIN) {
    return { ok: false, error: `${QUANTITY_MIN}주 이상으로 입력해 주세요.` }
  }
  if (quantity > QUANTITY_MAX) {
    return { ok: false, error: `1회 최대 ${QUANTITY_MAX.toLocaleString('en-US')}주까지 주문할 수 있어요.` }
  }
  return { ok: true, quantity }
}

/**
 * 개발 모드에서 클라이언트 검증을 끄고 보낼 값. 서버 검증(400 INVALID_INPUT)을 직접 확인하는 용도다.
 * 가격도 센트로 바꾸지 않고 입력값을 그대로 보낸다. 숫자 모양이면 숫자(소수, 음수 포함)로, 아니면 문자열 그대로, 비어 있으면 undefined(필드를 빼고 보냄).
 * 입금 화면의 toRawAmount와 같은 규칙이다.
 */
export function toRawNumberInput(input: string): number | string | undefined {
  return toRawAmount(input)
}
