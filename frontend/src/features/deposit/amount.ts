import { formatDollar } from '../../lib/money'

// 서버 입력 규칙(DepositRequest: @Positive, @Max(10000))과 같은 범위
export const DEPOSIT_MIN = 1
export const DEPOSIT_MAX = 10_000

export type AmountParseResult = { ok: true; amount: number } | { ok: false; error: string }

/** 입력칸의 쉼표와 앞뒤 공백을 지운다. "1,000" 처럼 쉼표를 넣어 입력해도 받는다. */
function normalize(input: string): string {
  return input.replace(/,/g, '').trim()
}

/** 전송 전 안내용 검증. 최종 판정은 서버가 한다. */
export function parseDepositAmount(input: string): AmountParseResult {
  const value = normalize(input)

  if (value === '') {
    return { ok: false, error: '입금할 금액을 입력해 주세요.' }
  }
  if (/^-?\d*\.\d*$/.test(value)) {
    return { ok: false, error: '센트(소수)는 입금할 수 없어요. 달러 단위 정수로 입력해 주세요.' }
  }
  if (!/^-?\d+$/.test(value)) {
    return { ok: false, error: '숫자만 입력해 주세요.' }
  }

  // 아주 긴 숫자도 Number로 바꾸면 상한보다 커지므로 범위 검사에 걸린다
  const amount = Number(value)
  if (amount < DEPOSIT_MIN) {
    return { ok: false, error: `${formatDollar(DEPOSIT_MIN)} 이상 입금할 수 있어요.` }
  }
  if (amount > DEPOSIT_MAX) {
    return { ok: false, error: `1회 최대 ${formatDollar(DEPOSIT_MAX)}까지 입금할 수 있어요.` }
  }
  return { ok: true, amount }
}

/**
 * 개발 모드에서 클라이언트 검증을 끄고 보낼 값. 서버 검증(400 INVALID_INPUT)을 직접 확인하는 용도다.
 * 숫자 모양이면 숫자(소수, 음수 포함)로, 아니면 문자열 그대로, 비어 있으면 필드를 빼고 보낸다.
 */
export function toRawAmount(input: string): number | string | undefined {
  const value = normalize(input)
  if (value === '') {
    return undefined
  }
  return /^-?\d+(\.\d+)?$/.test(value) ? Number(value) : value
}

/** 빠른 입력 버튼. 지금 입력값이 올바른 금액이면 거기에 더하고, 아니면 0에서 시작한다. */
export function addToAmount(input: string, delta: number): string {
  const parsed = parseDepositAmount(input)
  const base = parsed.ok ? parsed.amount : 0
  return String(base + delta)
}
