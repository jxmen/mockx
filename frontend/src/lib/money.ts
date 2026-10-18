const wonFormatter = new Intl.NumberFormat('ko-KR')

/** 원 단위 금액을 천 단위 쉼표와 '원'을 붙여 표시한다. (예: 10000 → "10,000원") */
export function formatWon(amount: number): string {
  return `${wonFormatter.format(amount)}원`
}
