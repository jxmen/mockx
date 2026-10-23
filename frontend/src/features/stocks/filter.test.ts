import { describe, expect, it } from 'vitest'
import type { StockSummary } from '../../api/stocks'
import { filterStocks } from './filter'

// V4에서 넣은 순서(id 오름차순)
const stocks: StockSummary[] = [
  { symbol: 'SPY', name: 'SPDR S&P 500 ETF Trust' },
  { symbol: 'QQQM', name: 'Invesco NASDAQ 100 ETF' },
  { symbol: 'QLD', name: 'ProShares Ultra QQQ' },
  { symbol: 'TQQQ', name: 'ProShares UltraPro QQQ' },
  { symbol: 'SOXL', name: 'Direxion Daily Semiconductor Bull 3X Shares' },
  { symbol: 'SGOV', name: 'iShares 0-3 Month Treasury Bond ETF' },
  { symbol: 'MU', name: 'Micron Technology' },
  { symbol: 'SKHY', name: 'SK hynix ADR (원주는 한국거래소 000660)' },
]

const symbols = (result: StockSummary[]) => result.map((stock) => stock.symbol)

describe('filterStocks', () => {
  it.each(['', '   '])('검색어가 "%s"이면 전부 그대로 돌려준다', (query) => {
    expect(filterStocks(stocks, query)).toBe(stocks)
  })

  it.each([
    ['mu', ['MU']],
    ['MU', ['MU']],
    ['  mu  ', ['MU']],
    ['qqq', ['QQQM', 'QLD', 'TQQQ']], // 티커(QQQM, TQQQ)나 종목명(QLD의 "Ultra QQQ")에 들어 있으면 남긴다
    ['micron', ['MU']],
    ['proshares', ['QLD', 'TQQQ']],
    ['s&p', ['SPY']],
    ['etf', ['SPY', 'QQQM', 'SGOV']],
    ['000660', ['SKHY']],
  ])('"%s"로 찾으면 %j를 남긴다', (query, expected) => {
    expect(symbols(filterStocks(stocks, query))).toEqual(expected)
  })

  it('서버가 준 정렬 순서를 바꾸지 않는다', () => {
    const reversed = [...stocks].reverse()
    expect(symbols(filterStocks(reversed, 'qqq'))).toEqual(['TQQQ', 'QLD', 'QQQM'])
  })

  it('맞는 종목이 없으면 빈 배열을 돌려준다', () => {
    expect(filterStocks(stocks, 'AAPL')).toEqual([])
  })
})
