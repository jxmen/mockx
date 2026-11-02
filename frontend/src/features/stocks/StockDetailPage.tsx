import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getStock, stockPath, type StockResponse } from '../../api/stocks'
import { pageTitle } from '../../lib/pageTitle'
import { ErrorIcon } from '../signup/FormField'
import { toStockDetailError, type StockDetailError } from './stockDetailError'
import { StockLogo } from './StockLogo'
import { SYMBOL_EXAMPLES, isNormalizedByServer } from './symbol'

/** 어느 요청으로 받은 결과인지 함께 둔다. 지금 요청과 다르면 아직 불러오는 중이다. */
type Result =
  | { requestKey: string; status: 'success'; stock: StockResponse }
  | { requestKey: string; status: 'error'; error: StockDetailError }

export function StockDetailPage() {
  // 라우터가 디코딩한 값이다. /stocks/m%C3%BC → "mü". 화면에서 바꾸지 않고 서버에 그대로 보낸다(형식 검사·대문자 변환은 서버 몫)
  const symbol = useParams().symbol ?? ''
  const [reloadToken, setReloadToken] = useState(0)
  const [result, setResult] = useState<Result | null>(null)

  const path = stockPath(symbol)
  const requestKey = `${path}#${reloadToken}`

  // 심볼이 바뀔 때마다 다시 조회한다. 이전 요청은 취소해 늦게 온 응답이 덮어쓰지 않게 한다.
  useEffect(() => {
    const controller = new AbortController()
    getStock(symbol, controller.signal)
      .then((stock) => setResult({ requestKey, status: 'success', stock }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({ requestKey, status: 'error', error: toStockDetailError(error, symbol) })
        }
      })
    return () => controller.abort()
  }, [symbol, requestKey])

  const current = result !== null && result.requestKey === requestKey ? result : null

  return (
    <main className="page page--wide">
      <title>{pageTitle(current?.status === 'success' ? current.stock.symbol : '종목')}</title>
      <Link to="/stocks" className="back-link">
        ← 종목 목록
      </Link>

      <div className="stock-meta">
        {/* 목록 화면과 달리 인코딩된 그대로 보여 준다. mü, 끝 공백처럼 눈으로 구분하기 어려운 심볼을 확인하기 위해서다 */}
        <span className="request-line">GET {path}</span>
      </div>

      <div aria-live="polite" aria-busy={current === null}>
        {current === null ? (
          <p className="stock-empty">불러오는 중…</p>
        ) : current.status === 'error' ? (
          <div className="stack stock-state">
            <div role="alert" className="banner">
              <ErrorIcon />
              <span>{current.error.message}</span>
            </div>
            {current.error.retryable && (
              <button
                type="button"
                className="button button--secondary button--block"
                onClick={() => setReloadToken((prev) => prev + 1)}
              >
                다시 불러오기
              </button>
            )}
          </div>
        ) : (
          <StockDetail stock={current.stock} requested={symbol} />
        )}
      </div>

      {import.meta.env.DEV && (
        <fieldset className="dev-box stock-dev">
          <legend>개발 모드에서만 보임</legend>
          <p className="dev-note">
            서버의 심볼 검사(영문 1~10자, 아니면 400)와 대문자 변환을 확인해요. 주소창의 심볼을 직접 바꿔도 돼요.
          </p>
          <div className="chips" role="group" aria-label="심볼 예시">
            {SYMBOL_EXAMPLES.map((example) => (
              <Link
                key={example.symbol}
                to={`/stocks/${encodeURIComponent(example.symbol)}`}
                className="button button--secondary chip"
                aria-current={example.symbol === symbol ? 'page' : undefined}
              >
                {example.symbol} <span className="chip__hint">{example.expected}</span>
              </Link>
            ))}
          </div>
        </fieldset>
      )}
    </main>
  )
}

function StockDetail({ stock, requested }: { stock: StockResponse; requested: string }) {
  return (
    <div className="stack stock-state">
      <div className="stock-hero">
        <StockLogo symbol={stock.symbol} />
        <div className="stock-name__text">
          <h1 className="stock-hero__name">{stock.name}</h1>
          <span className="stock-hero__symbol">{stock.symbol}</span>
        </div>
      </div>

      {isNormalizedByServer(requested, stock.symbol) && (
        <div role="status" className="notice">
          ‘{requested}’로 요청했고, 서버가 대문자 ‘{stock.symbol}’로 바꿔 찾았어요.
        </div>
      )}

      <dl className="summary">
        <div>
          <dt>티커</dt>
          <dd>{stock.symbol}</dd>
        </div>
        <div>
          <dt>상장 국가</dt>
          <dd>미국 (USD)</dd>
        </div>
        <div className="summary__wide">
          <dt>종목명</dt>
          <dd>{stock.name}</dd>
        </div>
      </dl>

      {/* 서버가 정규화한 티커를 넘긴다. mu로 들어와도 주문 화면에는 MU가 채워진다 */}
      <Link
        to={`/orders?symbol=${encodeURIComponent(stock.symbol)}`}
        className="button button--primary button--block"
      >
        매수 주문하기
      </Link>

      <p className="stock-footnote">현재가와 호가는 매칭 엔진이 체결을 시작하면 이 화면에 붙일게요.</p>
    </div>
  )
}
