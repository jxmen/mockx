import { formatCents } from '../../lib/money'

// 서버 입력 규칙(DepositRequest: @Positive, @Max(1000000))과 같은 범위. 단위는 센트다
export const DEPOSIT_MIN = 1 // $0.01
export const DEPOSIT_MAX = 1_000_000 // $10,000.00

export type AmountParseResult = { ok: true; amount: number } | { ok: false; error: string }

/** 입력칸의 쉼표와 앞뒤 공백을 지운다. "1,000" 처럼 쉼표를 넣어 입력해도 받는다. */
function normalize(input: string): string {
  return input.replace(/,/g, '').trim()
}

/**
 * 달러로 입력한 값(예: "18.74")을 센트 정수(1874)로 바꾼다. 전송 전 안내용 검증이고, 최종 판정은 서버가 한다.
 * `18.74 * 100`은 부동소수 오차가 생길 수 있어서(`1.005 * 100 = 100.49999…`) 정수부와 소수부를 문자열로 나눠 계산한다.
 */
export function parseDepositAmount(input: string): AmountParseResult {
  const value = normalize(input)

  if (value === '') {
    return { ok: false, error: '입금할 금액을 입력해 주세요.' }
  }
  const match = /^(-?)(\d*)(?:\.(\d*))?$/.exec(value)
  if (match === null || (match[2] === '' && (match[3] ?? '') === '')) {
    return { ok: false, error: '숫자만 입력해 주세요.' }
  }
  const [, sign, dollarPart, centPart = ''] = match
  if (centPart.length > 2) {
    return { ok: false, error: '센트(소수 둘째 자리)까지만 입력할 수 있어요.' }
  }

  // 아주 긴 숫자도 Number로 바꾸면 상한보다 커지므로 범위 검사에 걸린다
  const cents = Number(dollarPart || '0') * 100 + Number(centPart.padEnd(2, '0'))
  const amount = sign === '-' ? -cents : cents
  if (amount < DEPOSIT_MIN) {
    return { ok: false, error: `${formatCents(DEPOSIT_MIN)} 이상 입금할 수 있어요.` }
  }
  if (amount > DEPOSIT_MAX) {
    return { ok: false, error: `1회 최대 ${formatCents(DEPOSIT_MAX)}까지 입금할 수 있어요.` }
  }
  return { ok: true, amount }
}

/** 센트 금액을 입력칸에 넣을 달러 문자열로 바꾼다. 센트가 0이면 소수부를 뺀다. (예: 1050 → "10.50", 100000 → "1000") */
export function centsToInput(cents: number): string {
  const dollars = Math.floor(cents / 100)
  const rest = cents % 100
  return rest === 0 ? String(dollars) : `${dollars}.${String(rest).padStart(2, '0')}`
}

/**
 * 개발 모드에서 클라이언트 검증을 끄고 보낼 값. 서버 검증(400 INVALID_INPUT)을 직접 확인하는 용도다.
 * 센트로 바꾸지 않고 입력값을 그대로 보낸다. 숫자 모양이면 숫자(소수, 음수 포함)로, 아니면 문자열 그대로, 비어 있으면 필드를 빼고 보낸다.
 */
export function toRawAmount(input: string): number | string | undefined {
  const value = normalize(input)
  if (value === '') {
    return undefined
  }
  return /^-?\d+(\.\d+)?$/.test(value) ? Number(value) : value
}

/** 빠른 입력 버튼. 지금 입력값이 올바른 금액이면 거기에 더하고, 아니면 0에서 시작한다. delta는 센트다. */
export function addToAmount(input: string, delta: number): string {
  const parsed = parseDepositAmount(input)
  const base = parsed.ok ? parsed.amount : 0
  return centsToInput(base + delta)
}
