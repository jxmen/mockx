import type { StockSummary } from '../../api/stocks'

/**
 * 검색어가 티커나 종목명에 들어 있는 종목만 남긴다. 대소문자는 구분하지 않고, 서버가 준 정렬 순서는 그대로 둔다.
 * 목록 API가 전 종목을 한 번에 주므로(페이지네이션 없음) 화면에서 거른다. 페이지네이션이 생기면 서버 검색이 필요하다.
 */
export function filterStocks(stocks: StockSummary[], query: string): StockSummary[] {
  const keyword = query.trim().toLowerCase()
  if (keyword === '') {
    return stocks
  }
  return stocks.filter(
    (stock) => stock.symbol.toLowerCase().includes(keyword) || stock.name.toLowerCase().includes(keyword),
  )
}
