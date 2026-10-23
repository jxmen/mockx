/**
 * 요청한 심볼과 서버가 돌려준 심볼이 다른가. 서버는 대문자로 바꿔 찾으므로 mu로 요청하면 MU가 온다. docs/db-design-stock.md 4.3
 * 화면에서 "서버가 대문자로 바꿔 찾았다"를 보여 줄 때 쓴다.
 */
export function isNormalizedByServer(requested: string, returned: string): boolean {
  return requested !== returned
}

export type SymbolExample = {
  symbol: string
  /** 서버가 응답할 결과 */
  expected: string
}

/** 개발 모드에서 서버의 심볼 처리를 확인하는 예시. StockE2ETest의 케이스와 같다 */
export const SYMBOL_EXAMPLES: SymbolExample[] = [
  { symbol: 'mu', expected: '200 · 대문자로 찾음' },
  { symbol: 'AAPL', expected: '404 · 없는 종목' },
  { symbol: 'ABCDEFGHIJ', expected: '404 · 10자 경계' },
  { symbol: 'ABCDEFGHIJK', expected: '400 · 11자' },
  { symbol: 'MU1', expected: '400 · 숫자' },
  { symbol: 'mü', expected: '400 · 악센트' },
  { symbol: 'ſpy', expected: '400 · 대문자면 SPY' },
]
