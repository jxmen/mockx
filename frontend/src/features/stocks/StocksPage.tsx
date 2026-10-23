import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { getStocks, stocksPath, type StockSummary } from '../../api/stocks'
import { pageTitle } from '../../lib/pageTitle'
import { ErrorIcon } from '../signup/FormField'
import { filterStocks } from './filter'
import { INVALID_SORT_EXAMPLES, SORT_OPTIONS, parseRawSorts, toSorts } from './sort'
import { StockLogo } from './StockLogo'
import { toStocksErrorMessage } from './stocksError'

/** 어느 요청으로 받은 결과인지 함께 둔다. 지금 요청과 다르면 아직 불러오는 중이다. */
type Result =
  | { requestKey: string; status: 'success'; stocks: StockSummary[] }
  | { requestKey: string; status: 'error'; message: string }

export function StocksPage() {
  const [option, setOption] = useState(SORT_OPTIONS[0])
  // 개발 모드에서 서버 정렬 검증(400 INVALID_INPUT)을 직접 확인하기 위한 값. 비어 있지 않으면 위 정렬 대신 보낸다
  const [rawSort, setRawSort] = useState('')
  const [reloadToken, setReloadToken] = useState(0)
  const [result, setResult] = useState<Result | null>(null)
  // 받은 목록을 화면에서 거른다. 검색어를 바꿔도 다시 요청하지 않고, 정렬을 바꿔도 검색어는 남긴다
  const [query, setQuery] = useState('')

  const sorts = useMemo(() => {
    const raw = parseRawSorts(rawSort)
    return raw.length > 0 ? raw : toSorts(option)
  }, [rawSort, option])
  const path = stocksPath(sorts)
  const requestKey = `${path}#${reloadToken}`

  // 정렬을 바꿀 때마다 다시 조회한다. 이전 요청은 취소해 늦게 온 응답이 덮어쓰지 않게 한다.
  useEffect(() => {
    const controller = new AbortController()
    getStocks(sorts, controller.signal)
      .then((data) => setResult({ requestKey, status: 'success', stocks: data.stocks }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({ requestKey, status: 'error', message: toStocksErrorMessage(error) })
        }
      })
    return () => controller.abort()
  }, [sorts, requestKey])

  const current = result !== null && result.requestKey === requestKey ? result : null
  const overridden = parseRawSorts(rawSort).length > 0
  const filtered = current?.status === 'success' ? filterStocks(current.stocks, query) : null
  const searching = query.trim() !== ''

  return (
    <main className="page page--wide">
      <title>{pageTitle('종목')}</title>
      <h1 className="page__title">종목</h1>
      <p className="page__lead">주문할 수 있는 미국 상장 종목이에요. 로그인하지 않아도 볼 수 있어요.</p>

      <div className="stock-toolbar">
        <div className="pills" role="group" aria-label="정렬">
          {SORT_OPTIONS.map((item) => (
            <button
              key={item.label}
              type="button"
              className="pill"
              aria-pressed={!overridden && item === option}
              onClick={() => setOption(item)}
            >
              {item.label}
            </button>
          ))}
        </div>
        <label className="search">
          <SearchIcon />
          <input
            type="search"
            aria-label="종목 검색"
            placeholder="티커나 종목명 검색"
            autoComplete="off"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
      </div>

      <div className="stock-meta">
        {/* 실제로는 쉼표가 %2C로 인코딩돼 나간다. 읽기 쉽게 풀어서 보여 준다 */}
        <span className="request-line">GET {decodeURIComponent(path)}</span>
        {current?.status === 'success' && filtered !== null && (
          <span role="status">
            {searching ? `${current.stocks.length}개 중 ${filtered.length}개` : `${current.stocks.length}개`}
          </span>
        )}
      </div>

      <div aria-live="polite" aria-busy={current === null}>
        {current === null ? (
          <p className="stock-empty">불러오는 중…</p>
        ) : current.status === 'error' ? (
          <div className="stack stock-state">
            <div role="alert" className="banner">
              <ErrorIcon />
              <span>{current.message}</span>
            </div>
            <button
              type="button"
              className="button button--secondary button--block"
              onClick={() => setReloadToken((prev) => prev + 1)}
            >
              다시 불러오기
            </button>
          </div>
        ) : current.stocks.length === 0 ? (
          <p className="stock-empty">등록된 종목이 없어요.</p>
        ) : filtered === null || filtered.length === 0 ? (
          <p className="stock-empty">‘{query.trim()}’와 맞는 종목이 없어요. 티커나 영문 종목명으로 찾아보세요.</p>
        ) : (
          <>
            <StockTable stocks={filtered} />
            <p className="stock-footnote">현재가와 등락률은 매칭 엔진이 체결을 시작하면 보여 줄 수 있어요.</p>
          </>
        )}
      </div>

      {import.meta.env.DEV && (
        <fieldset className="dev-box stock-dev">
          <legend>개발 모드에서만 보임</legend>
          <label className="dev-field">
            <span>sort 직접 입력 (공백으로 나누면 여러 개를 보냄. 입력하면 위 정렬 대신 이 값을 보냄)</span>
            <input
              className="header__dev-input"
              placeholder="예: symbol,desc"
              autoComplete="off"
              value={rawSort}
              onChange={(event) => setRawSort(event.target.value)}
            />
          </label>
          <div className="chips" role="group" aria-label="서버가 거절하는 sort 예시">
            {INVALID_SORT_EXAMPLES.map((example) => (
              <button
                key={example}
                type="button"
                className="button button--secondary chip"
                onClick={() => setRawSort(example)}
              >
                {example}
              </button>
            ))}
            {overridden && (
              <button type="button" className="button button--secondary chip" onClick={() => setRawSort('')}>
                지우기
              </button>
            )}
          </div>
        </fieldset>
      )}
    </main>
  )
}

function StockTable({ stocks }: { stocks: StockSummary[] }) {
  return (
    <table className="stock-table">
      <thead>
        <tr>
          <th scope="col" className="stock-table__rank">
            순서
          </th>
          <th scope="col">종목</th>
          <th scope="col" className="stock-table__symbol">
            티커
          </th>
        </tr>
      </thead>
      <tbody>
        {stocks.map((stock, index) => (
          <tr key={stock.symbol}>
            <td className="stock-table__rank">{index + 1}</td>
            <td>
              <Link to={`/stocks/${encodeURIComponent(stock.symbol)}`} className="stock-name stock-link">
                <StockLogo symbol={stock.symbol} />
                <span className="stock-name__text">
                  <span className="stock-name__name" title={stock.name}>
                    {stock.name}
                  </span>
                  {/* 좁은 화면에서만 보인다. 티커 칸과 같은 값이라 스크린 리더에는 한 번만 읽힌다 */}
                  <span className="stock-name__symbol" aria-hidden="true">
                    {stock.symbol}
                  </span>
                </span>
              </Link>
            </td>
            <td className="stock-table__symbol">{stock.symbol}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function SearchIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
      <circle cx="8" cy="8" r="5.5" />
      <path d="M12.2 12.2L16 16" />
    </svg>
  )
}
