const dollarFormatter = new Intl.NumberFormat('en-US')

/**
 * 센트 단위 정수 금액(서버 API 단위)을 '$', 천 단위 쉼표, 소수 둘째 자리까지 붙여 표시한다. (예: 187430 → "$1,874.30")
 * 부동소수 나눗셈 대신 정수 몫과 나머지로 나눠, 센트 자리가 반올림되지 않게 한다.
 */
export function formatCents(cents: number): string {
  const sign = cents < 0 ? '-' : ''
  const abs = Math.abs(cents)
  const dollars = Math.floor(abs / 100)
  const rest = abs % 100
  return `${sign}$${dollarFormatter.format(dollars)}.${String(rest).padStart(2, '0')}`
}
