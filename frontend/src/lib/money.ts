const dollarFormatter = new Intl.NumberFormat('en-US')

/** 달러 단위 정수 금액에 '$'와 천 단위 쉼표를 붙여 표시한다. (예: 10000 → "$10,000") */
export function formatDollar(amount: number): string {
  return `$${dollarFormatter.format(amount)}`
}
