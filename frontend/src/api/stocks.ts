import { apiRequest } from './client'

export type StockSummary = {
  /** 티커. 대문자(예: "MU"). docs/design/stock.md 4.3 */
  symbol: string
  /** 종목명(예: "Micron Technology") */
  name: string
}

/** 페이지 정보 같은 필드가 붙을 수 있어 배열을 stocks로 감싼다. docs/design/stock.md 4.5 */
export type StocksResponse = {
  stocks: StockSummary[]
}

/** 서버가 허용하는 정렬 필드. 그 밖의 필드는 400 INVALID_INPUT */
export type StockSortField = 'id' | 'symbol'
export type SortDirection = 'asc' | 'desc'

/** 정렬 하나를 sort 쿼리 값으로 바꾼다. (예: symbol, desc → "symbol,desc") */
export function toSortParam(field: StockSortField, direction: SortDirection): string {
  return `${field},${direction}`
}

/** sort 값마다 sort=...를 붙인 경로. 비어 있으면 쿼리 없이 보내 서버 기본 정렬(id 오름차순)을 쓴다. */
export function stocksPath(sorts: string[]): string {
  const query = new URLSearchParams(sorts.map((sort) => ['sort', sort])).toString()
  return query === '' ? '/api/stocks' : `/api/stocks?${query}`
}

/**
 * 종목 목록. 공개 API라 X-Member-Id를 보내지 않는다.
 * sorts는 sort 쿼리 값들이다. 서버는 하나만 허용하고, 여러 개면 400 INVALID_INPUT (개발 모드 확인용으로 배열을 받는다)
 */
export function getStocks(sorts: string[], signal?: AbortSignal): Promise<StocksResponse> {
  return apiRequest<StocksResponse>(stocksPath(sorts), { signal })
}

/** 종목 상세. 목록의 한 항목과 같은 필드다. docs/design/stock.md 4.6 */
export type StockResponse = StockSummary

/** 심볼은 경로 변수라 인코딩한다. (예: "mü" → /api/stocks/m%C3%BC, "MU " → /api/stocks/MU%20) */
export function stockPath(symbol: string): string {
  return `/api/stocks/${encodeURIComponent(symbol)}`
}

/**
 * 종목 상세. 공개 API라 X-Member-Id를 보내지 않는다.
 * 서버가 영문 1~10자인지 검사하고(아니면 400 INVALID_INPUT) 대문자로 바꿔 찾는다(mu → MU). 없으면 404 STOCK_NOT_FOUND
 */
export function getStock(symbol: string, signal?: AbortSignal): Promise<StockResponse> {
  return apiRequest<StockResponse>(stockPath(symbol), { signal })
}
