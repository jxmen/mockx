import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { getMyInfo, type AccountInfo } from '../../api/members'
import {
  cancelOrder,
  getOrders,
  ordersPath,
  placeOrder,
  type OrderItem,
  type OrderStatus,
  type PlaceOrderRequest,
  type PlaceOrderResponse,
} from '../../api/orders'
import { getStocks, type StockSummary } from '../../api/stocks'
import { useMemberId } from '../../auth/memberIdContext'
import { formatAccountNumber } from '../../lib/accountNumber'
import { formatCents } from '../../lib/money'
import { pageTitle } from '../../lib/pageTitle'
import { toMyInfoErrorMessage } from '../me/myInfoError'
import { ErrorIcon, FormField } from '../signup/FormField'
import { ORDER_STATUS_FILTERS, statusFilterToQuery, type OrderStatusFilter } from './orderFilter'
import {
  toCancelOrderErrorFeedback,
  toOrdersErrorMessage,
  toPlaceOrderErrorFeedback,
  type PlaceOrderField,
} from './orderError'
import { PRICE_MAX, PRICE_MIN, QUANTITY_MAX, QUANTITY_MIN, parsePriceInput, parseQuantityInput, toRawNumberInput } from './price'
import { validateSymbolInput } from './symbol'

/** 어느 요청으로 받은 결과인지 함께 둔다. 지금 요청과 다르면 다시 불러오는 중이다. */
type AccountResult =
  | { requestKey: number; status: 'ready'; account: AccountInfo | null }
  | { requestKey: number; status: 'error'; message: string }

type OrdersResult =
  | { requestKey: string; path: string; status: 'success'; orders: OrderItem[] }
  | { requestKey: string; path: string; status: 'error'; message: string }

type FieldErrors = Partial<Record<PlaceOrderField, string>>

/** 개발 모드에서 클라이언트 검증을 끄면 보낼 수 있는 side. SELL → 400(field=side), buy → 서버가 BUY로 바꿔 201 */
const RAW_SIDES = ['BUY', 'SELL', 'buy'] as const

const STATUS_LABEL: Record<OrderStatus, string> = { ACCEPTED: '접수', CANCELED: '취소' }
// 랜딩의 상태 배지(.status)를 그대로 쓴다. 접수는 파란색, 취소는 회색
const STATUS_CLASS: Record<OrderStatus, string> = { ACCEPTED: 'status--in-progress', CANCELED: 'status--planned' }

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

/** 서버 시각(ISO-8601 UTC)을 브라우저 로컬 시각으로 보여 준다 */
function formatDateTime(iso: string): string {
  return dateTimeFormatter.format(new Date(iso))
}

export function OrderPage() {
  const { memberId } = useMemberId()
  // 종목 상세의 "매수 주문하기"로 들어오면 /orders?symbol=MU. 검사하지 않고 종목 칸 초기값으로만 쓴다(검사는 입력값과 같은 규칙)
  const [searchParams] = useSearchParams()
  const initialSymbol = searchParams.get('symbol') ?? ''

  return (
    <main className="page page--wide">
      <title>{pageTitle('주문')}</title>
      <h1 className="page__title">주문</h1>
      <p className="page__lead">
        지정가 매수 주문을 넣으면 주문 금액(가격 × 수량)만큼 예수금이 묶이고(hold), 취소하면 풀려요(release). 가격은{' '}
        {formatCents(PRICE_MIN)} ~ {formatCents(PRICE_MAX)}, 수량은 {QUANTITY_MIN} ~ {QUANTITY_MAX.toLocaleString('en-US')}
        주까지 넣을 수 있어요.
      </p>

      {memberId === null ? (
        <section className="card">
          <p className="empty">상단 X-Member-Id에 회원번호를 입력하거나 먼저 회원가입을 해 주세요.</p>
        </section>
      ) : (
        // 회원을 바꾸면 입력값과 목록을 모두 버리고 새로 시작한다
        <OrderPanel key={memberId} memberId={memberId} initialSymbol={initialSymbol} />
      )}
    </main>
  )
}

function OrderPanel({ memberId, initialSymbol }: { memberId: string; initialSymbol: string }) {
  const [accountToken, setAccountToken] = useState(0)
  const [accountResult, setAccountResult] = useState<AccountResult | null>(null)
  const [ordersToken, setOrdersToken] = useState(0)

  // 계좌 id·예수금·hold는 내 정보 API로 얻는다. 첫 번째 계좌를 쓴다(입금 화면과 같다)
  useEffect(() => {
    const controller = new AbortController()
    getMyInfo(memberId, controller.signal)
      .then((data) => setAccountResult({ requestKey: accountToken, status: 'ready', account: data.accounts[0] ?? null }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setAccountResult({ requestKey: accountToken, status: 'error', message: toMyInfoErrorMessage(error) })
        }
      })
    return () => controller.abort()
  }, [memberId, accountToken])

  // 다시 불러오는 동안에도 이전 계좌 정보와 입력값을 그대로 둔다
  const reloadAccount = () => setAccountToken((prev) => prev + 1)
  const reloadOrders = () => setOrdersToken((prev) => prev + 1)
  // 주문 접수·취소 뒤에는 hold가 바뀌므로 계좌 요약과 목록을 함께 다시 불러온다
  const reloadAll = () => {
    reloadAccount()
    reloadOrders()
  }

  const accountRefreshing = accountResult === null || accountResult.requestKey !== accountToken

  if (accountResult === null) {
    return (
      <section className="card" aria-busy="true">
        <p className="empty">불러오는 중…</p>
      </section>
    )
  }
  if (accountResult.status === 'error') {
    return (
      <section className="card">
        <div className="stack">
          <div role="alert" className="banner">
            <ErrorIcon />
            <span>{accountResult.message}</span>
          </div>
          <button
            type="button"
            className="button button--secondary button--block"
            onClick={reloadAccount}
            disabled={accountRefreshing}
          >
            {accountRefreshing ? '불러오는 중…' : '다시 불러오기'}
          </button>
        </div>
      </section>
    )
  }
  if (accountResult.account === null) {
    return (
      <section className="card">
        <p className="empty">이 회원에게는 계좌가 없습니다.</p>
      </section>
    )
  }

  const account = accountResult.account
  return (
    <>
      <section className="card" aria-live="polite" aria-busy={accountRefreshing}>
        <div className="stack">
          <AccountSummary account={account} refreshing={accountRefreshing} onReload={reloadAccount} />
          {/* 다른 종목으로 들어오면(?symbol이 바뀌면) 폼을 새로 시작한다 */}
          <OrderForm
            key={initialSymbol}
            memberId={memberId}
            account={account}
            initialSymbol={initialSymbol}
            onPlaced={reloadAll}
          />
        </div>
      </section>

      <OrderList
        memberId={memberId}
        accountId={account.id}
        reloadToken={ordersToken}
        onReload={reloadOrders}
        onChanged={reloadAll}
      />
    </>
  )
}

type AccountSummaryProps = { account: AccountInfo; refreshing: boolean; onReload: () => void }

function AccountSummary({ account, refreshing, onReload }: AccountSummaryProps) {
  return (
    <dl className="summary">
      <div className="summary__wide summary__head">
        <div>
          <dt>위탁계좌</dt>
          <dd className="mono summary__account">{formatAccountNumber(account.number)}</dd>
        </div>
        <button
          type="button"
          className="button button--secondary button--small"
          onClick={onReload}
          disabled={refreshing}
          aria-busy={refreshing}
        >
          {refreshing ? '불러오는 중…' : '새로고침'}
        </button>
      </div>
      <div>
        <dt>예수금</dt>
        <dd className="mono summary__amount">{formatCents(account.balance)}</dd>
      </div>
      <div>
        <dt>주문에 묶인 금액 (hold)</dt>
        <dd className="mono summary__amount">{formatCents(account.holdAmount)}</dd>
      </div>
      <div className="summary__wide">
        <dt>주문 가능 금액 (예수금 − hold)</dt>
        <dd className="mono summary__amount">{formatCents(account.balance - account.holdAmount)}</dd>
      </div>
    </dl>
  )
}

type StocksState = { status: 'loading' } | { status: 'ready'; stocks: StockSummary[] } | { status: 'error' }

type OrderFormProps = {
  memberId: string
  account: AccountInfo
  /** 종목 칸 초기값. 종목 상세에서 들어오면 그 티커, 아니면 '' */
  initialSymbol: string
  onPlaced: () => void
}

function OrderForm({ memberId, account, initialSymbol, onPlaced }: OrderFormProps) {
  const [symbolInput, setSymbolInput] = useState(initialSymbol)
  const [priceInput, setPriceInput] = useState('')
  const [quantityInput, setQuantityInput] = useState('')
  const [touched, setTouched] = useState<Partial<Record<PlaceOrderField, boolean>>>({})
  const [serverErrors, setServerErrors] = useState<FieldErrors>({})
  const [banner, setBanner] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [lastOrder, setLastOrder] = useState<PlaceOrderResponse | null>(null)
  const [stocksState, setStocksState] = useState<StocksState>({ status: 'loading' })
  // 개발 모드에서 서버 검증(400 INVALID_INPUT)을 직접 확인하기 위한 값
  const [skipClientValidation, setSkipClientValidation] = useState(false)
  const [rawSide, setRawSide] = useState<(typeof RAW_SIDES)[number]>('BUY')

  const symbolRef = useRef<HTMLInputElement>(null)
  const priceRef = useRef<HTMLInputElement>(null)
  const quantityRef = useRef<HTMLInputElement>(null)

  // 종목이 채워진 채로 들어오면 바로 가격을 입력할 수 있게 한다
  useEffect(() => {
    if (initialSymbol !== '') {
      priceRef.current?.focus()
    }
  }, [initialSymbol])

  // 종목 후보(datalist). 못 불러와도 티커를 직접 입력해 주문할 수 있다
  useEffect(() => {
    const controller = new AbortController()
    getStocks([], controller.signal)
      .then((data) => setStocksState({ status: 'ready', stocks: data.stocks }))
      .catch(() => {
        if (!controller.signal.aborted) {
          setStocksState({ status: 'error' })
        }
      })
    return () => controller.abort()
  }, [])

  const symbol = validateSymbolInput(symbolInput)
  const price = parsePriceInput(priceInput)
  const quantity = parseQuantityInput(quantityInput)
  const available = account.balance - account.holdAmount
  // 최대 1,000,000센트 × 10,000주 = 10^10이라 정수 곱셈이 정확하다(Number.MAX_SAFE_INTEGER ≈ 9 × 10^15)
  const orderAmount = price.ok && quantity.ok ? price.cents * quantity.quantity : null
  const exceedsAvailable = orderAmount !== null && orderAmount > available

  function clientError(field: PlaceOrderField, result: { ok: true } | { ok: false; error: string }) {
    if (skipClientValidation || !touched[field] || result.ok) {
      return undefined
    }
    return result.error
  }

  const symbolError = serverErrors.symbol ?? clientError('symbol', symbol)
  const priceError = serverErrors.price ?? clientError('price', price)
  const quantityError = serverErrors.quantity ?? clientError('quantity', quantity)
  // side 칸은 개발 모드에서 검증을 껐을 때만 보인다. 칸이 없을 때 온 side 에러는 배너로 보여 준다
  const sideBanner = !skipClientValidation && serverErrors.side !== undefined ? serverErrors.side : null

  const matchedStock =
    symbol.ok && stocksState.status === 'ready'
      ? (stocksState.stocks.find((stock) => stock.symbol === symbol.symbol) ?? null)
      : null
  const symbolHelper =
    stocksState.status === 'error'
      ? '종목 목록을 불러오지 못했어요. 티커를 직접 입력해 주세요.'
      : !symbol.ok
        ? '영문 1~10자 티커. 소문자로 넣어도 대문자로 보내요.'
        : matchedStock !== null
          ? `${matchedStock.symbol} · ${matchedStock.name}`
          : stocksState.status === 'ready'
            ? `종목 목록에 없는 티커예요. 보내면 서버가 404 STOCK_NOT_FOUND로 거절합니다.`
            : `${symbol.symbol}로 주문합니다.`

  function changeField(field: PlaceOrderField, setter: (value: string) => void, value: string) {
    setter(value)
    setServerErrors((prev) => (prev[field] === undefined ? prev : { ...prev, [field]: undefined }))
    setLastOrder(null)
  }

  function touch(field: PlaceOrderField) {
    setTouched((prev) => (prev[field] ? prev : { ...prev, [field]: true }))
  }

  function focusField(field: PlaceOrderField | undefined) {
    const ref = field === 'symbol' ? symbolRef : field === 'price' ? priceRef : field === 'quantity' ? quantityRef : null
    ref?.current?.focus()
  }

  function buildRequest(): PlaceOrderRequest | null {
    if (skipClientValidation) {
      // 개발용: 형식이 틀린 값(문자열 가격, 소문자 side, 빈 값)도 그대로 보낸다. 빈 값은 JSON에서 필드가 빠진다
      return {
        symbol: symbolInput,
        side: rawSide,
        price: toRawNumberInput(priceInput),
        quantity: toRawNumberInput(quantityInput),
      } as unknown as PlaceOrderRequest
    }
    if (!symbol.ok || !price.ok || !quantity.ok) {
      return null
    }
    // 지금은 매수만 접수한다. 화면에서 side를 고르지 않는다
    return { symbol: symbol.symbol, side: 'BUY', price: price.cents, quantity: quantity.quantity }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // 응답을 기다리는 동안 다시 누르면 같은 주문이 두 번 나가 hold도 두 번 잡힌다.
    // 서버에 Idempotency-Key가 아직 없으므로 화면에서라도 막는다
    if (pending) {
      return
    }
    setTouched({ symbol: true, price: true, quantity: true })
    setBanner(null)
    setLastOrder(null)

    const request = buildRequest()
    if (request === null) {
      focusField(!symbol.ok ? 'symbol' : !price.ok ? 'price' : 'quantity')
      return
    }

    setPending(true)
    try {
      const response = await placeOrder(account.id, request, memberId)
      setLastOrder(response)
      setSymbolInput('')
      setPriceInput('')
      setQuantityInput('')
      setTouched({})
      setServerErrors({})
      onPlaced()
    } catch (error) {
      const feedback = toPlaceOrderErrorFeedback(error)
      setServerErrors(feedback.fieldErrors)
      setBanner(feedback.banner)
      focusField((['symbol', 'price', 'quantity'] as const).find((field) => feedback.fieldErrors[field] !== undefined))
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      {banner !== null && (
        <div role="alert" className="banner">
          <ErrorIcon />
          <span>{banner}</span>
        </div>
      )}
      {sideBanner !== null && (
        <div role="alert" className="banner">
          <ErrorIcon />
          <span>주문 구분(side): {sideBanner}</span>
        </div>
      )}
      {lastOrder !== null && (
        <div role="status" className="notice">
          <CheckIcon />
          <span>
            주문 #{lastOrder.orderId} 접수: {lastOrder.symbol} {lastOrder.quantity.toLocaleString('en-US')}주 ×{' '}
            {formatCents(lastOrder.price)}. 주문 금액 {formatCents(lastOrder.price * lastOrder.quantity)}이 묶였어요.
          </span>
        </div>
      )}

      <p className="order-side">
        구분 <span className="order-side__value">매수 · 지정가</span>
      </p>

      <FormField
        id="order-symbol"
        name="symbol"
        label="종목"
        helper={symbolHelper}
        error={symbolError}
        placeholder="예: MU"
        autoComplete="off"
        autoCapitalize="characters"
        spellCheck={false}
        list="order-symbol-options"
        value={symbolInput}
        inputRef={symbolRef}
        onChange={(event) => changeField('symbol', setSymbolInput, event.target.value)}
        onBlur={() => touch('symbol')}
      />
      <datalist id="order-symbol-options">
        {stocksState.status === 'ready' &&
          stocksState.stocks.map((stock) => (
            <option key={stock.symbol} value={stock.symbol}>
              {stock.name}
            </option>
          ))}
      </datalist>

      <FormField
        id="order-price"
        name="price"
        label="가격 (USD, 지정가)"
        helper={`${formatCents(PRICE_MIN)} ~ ${formatCents(PRICE_MAX)}, 달러로 입력해 주세요. 센트(소수 둘째 자리)까지 넣을 수 있어요.`}
        error={priceError}
        placeholder="예: 187.43"
        inputMode="decimal"
        autoComplete="off"
        value={priceInput}
        inputRef={priceRef}
        onChange={(event) => changeField('price', setPriceInput, event.target.value)}
        onBlur={() => touch('price')}
        trailing={<span className="field__unit mono">$</span>}
      />

      <FormField
        id="order-quantity"
        name="quantity"
        label="수량 (주)"
        helper={`${QUANTITY_MIN} ~ ${QUANTITY_MAX.toLocaleString('en-US')}주, 정수로 입력해 주세요.`}
        error={quantityError}
        placeholder="예: 10"
        inputMode="numeric"
        autoComplete="off"
        value={quantityInput}
        inputRef={quantityRef}
        onChange={(event) => changeField('quantity', setQuantityInput, event.target.value)}
        onBlur={() => touch('quantity')}
      />

      <dl className="summary">
        <div className="summary__wide">
          <dt>주문 금액 (가격 × 수량)</dt>
          <dd className="mono summary__amount">
            {price.ok && quantity.ok
              ? `${formatCents(price.cents)} × ${quantity.quantity.toLocaleString('en-US')}주 = ${formatCents(price.cents * quantity.quantity)}`
              : '–'}
          </dd>
        </div>
        <div className="summary__wide">
          <dt>주문 가능 금액</dt>
          <dd className="mono summary__amount">{formatCents(available)}</dd>
        </div>
      </dl>
      {exceedsAvailable && (
        // 화면에서 막지 않고 서버의 422를 그대로 보여 준다
        <p className="field__warning" role="status">
          주문 가능 금액을 넘어요. 그대로 보내면 서버가 422 INSUFFICIENT_AVAILABLE_AMOUNT로 거절합니다.
        </p>
      )}

      <button type="submit" className="button button--primary button--block" disabled={pending} aria-busy={pending}>
        {pending ? '주문 중…' : '매수 주문하기'}
      </button>

      {import.meta.env.DEV && (
        <fieldset className="dev-box">
          <legend>개발 모드에서만 보임</legend>
          <label className="dev-toggle">
            <input
              type="checkbox"
              checked={skipClientValidation}
              onChange={(event) => setSkipClientValidation(event.target.checked)}
            />
            <span>
              클라이언트 검증 끄기 (가격을 센트로 바꾸지 않고 입력값 그대로 보내 서버 검증 확인. 소수, 음수, 문자, 빈 값도 보냄)
            </span>
          </label>
          {skipClientValidation && (
            <label className="dev-field">
              <span>side (SELL → 400 field=side, buy → 서버가 BUY로 바꿔 201)</span>
              <select
                className="header__dev-input"
                value={rawSide}
                onChange={(event) => setRawSide(event.target.value as (typeof RAW_SIDES)[number])}
              >
                {RAW_SIDES.map((side) => (
                  <option key={side} value={side}>
                    {side}
                  </option>
                ))}
              </select>
              {serverErrors.side !== undefined && <span className="field__hint--error">{serverErrors.side}</span>}
            </label>
          )}
        </fieldset>
      )}
    </form>
  )
}

type CancelMessage = { kind: 'error' | 'ok'; text: string }

type OrderListProps = {
  memberId: string
  accountId: number
  reloadToken: number
  onReload: () => void
  onChanged: () => void
}

function OrderList({ memberId, accountId, reloadToken, onReload, onChanged }: OrderListProps) {
  const [statusFilter, setStatusFilter] = useState<OrderStatusFilter>('ALL')
  const [result, setResult] = useState<OrdersResult | null>(null)
  const [cancelingIds, setCancelingIds] = useState<ReadonlySet<number>>(new Set())
  const [cancelMessage, setCancelMessage] = useState<CancelMessage | null>(null)

  const query = statusFilterToQuery(statusFilter)
  const path = ordersPath(accountId, query)
  const requestKey = `${path}#${reloadToken}`

  // 필터를 바꾸거나 다시 불러올 때마다 조회한다. 이전 요청은 취소해 늦게 온 응답이 덮어쓰지 않게 한다.
  useEffect(() => {
    const controller = new AbortController()
    getOrders(accountId, query, memberId, controller.signal)
      .then((data) => setResult({ requestKey, path, status: 'success', orders: data.orders }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({ requestKey, path, status: 'error', message: toOrdersErrorMessage(error) })
        }
      })
    return () => controller.abort()
  }, [accountId, query, memberId, path, requestKey])

  // 같은 필터로 다시 불러오는 동안에는 이전 목록을 그대로 보여 준다. 필터가 바뀌면 불러오는 중으로 보인다
  const shown = result !== null && result.path === path ? result : null
  const loading = result === null || result.requestKey !== requestKey

  async function handleCancel(orderId: number) {
    // 같은 행을 응답 전에 다시 누르면 두 번째 요청은 409가 된다. 화면에서 막는다
    if (cancelingIds.has(orderId)) {
      return
    }
    setCancelingIds((prev) => new Set(prev).add(orderId))
    setCancelMessage(null)
    try {
      const response = await cancelOrder(accountId, orderId, memberId)
      setCancelMessage({
        kind: 'ok',
        text: `주문 #${response.orderId} 취소: 묶였던 ${formatCents(response.price * response.quantity)}이 풀렸어요.`,
      })
      onChanged()
    } catch (error) {
      const feedback = toCancelOrderErrorFeedback(error)
      setCancelMessage({ kind: 'error', text: feedback.message })
      // 이미 취소됐거나 결과를 모르면 hold도 바뀌었을 수 있어 계좌 요약까지 다시 불러온다
      if (feedback.shouldReload) {
        onChanged()
      }
    } finally {
      setCancelingIds((prev) => {
        const next = new Set(prev)
        next.delete(orderId)
        return next
      })
    }
  }

  return (
    <section className="card history" aria-labelledby="order-list-title">
      <div className="order-list__head">
        <h2 id="order-list-title" className="history__title">
          내 주문
        </h2>
        <button
          type="button"
          className="button button--secondary button--small"
          onClick={onReload}
          disabled={loading}
          aria-busy={loading}
        >
          {loading ? '불러오는 중…' : '새로고침'}
        </button>
      </div>

      <div className="pills" role="group" aria-label="주문 상태">
        {ORDER_STATUS_FILTERS.map((item) => (
          <button
            key={item.value}
            type="button"
            className="pill"
            aria-pressed={item.value === statusFilter}
            onClick={() => setStatusFilter(item.value)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="stock-meta">
        <span className="request-line">GET {path}</span>
        {shown?.status === 'success' && <span role="status">{shown.orders.length}건</span>}
      </div>

      {cancelMessage !== null &&
        (cancelMessage.kind === 'error' ? (
          <div role="alert" className="banner order-list__message">
            <ErrorIcon />
            <span>{cancelMessage.text}</span>
          </div>
        ) : (
          <div role="status" className="notice order-list__message">
            <CheckIcon />
            <span>{cancelMessage.text}</span>
          </div>
        ))}

      <div aria-live="polite" aria-busy={loading}>
        {shown === null ? (
          <p className="stock-empty">불러오는 중…</p>
        ) : shown.status === 'error' ? (
          <div className="stack stock-state">
            <div role="alert" className="banner">
              <ErrorIcon />
              <span>{shown.message}</span>
            </div>
            <button type="button" className="button button--secondary button--block" onClick={onReload} disabled={loading}>
              다시 불러오기
            </button>
          </div>
        ) : shown.orders.length === 0 ? (
          <p className="stock-empty">{statusFilter === 'ALL' ? '주문이 없습니다.' : '조건에 맞는 주문이 없습니다.'}</p>
        ) : (
          <OrderTable orders={shown.orders} cancelingIds={cancelingIds} onCancel={handleCancel} />
        )}
      </div>
    </section>
  )
}

type OrderTableProps = {
  orders: OrderItem[]
  cancelingIds: ReadonlySet<number>
  onCancel: (orderId: number) => void
}

function OrderTable({ orders, cancelingIds, onCancel }: OrderTableProps) {
  return (
    // 칸이 많아 좁은 화면에서는 표만 가로로 스크롤한다
    <div className="order-table-wrap">
      <table className="history__table order-table">
        <thead>
          <tr>
            <th scope="col" className="num">
              주문번호
            </th>
            <th scope="col">종목</th>
            <th scope="col">구분</th>
            <th scope="col" className="num">
              가격
            </th>
            <th scope="col" className="num">
              수량
            </th>
            <th scope="col" className="num">
              주문 금액
            </th>
            <th scope="col">상태</th>
            <th scope="col">접수 시각</th>
            <th scope="col">취소 시각</th>
            <th scope="col">
              <span className="visually-hidden">취소</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order) => {
            const canceling = cancelingIds.has(order.orderId)
            return (
              <tr key={order.orderId}>
                <td className="mono num">#{order.orderId}</td>
                <td>
                  <span className="order-table__symbol">{order.symbol}</span>
                  <span className="order-table__name" title={order.stockName}>
                    {order.stockName}
                  </span>
                </td>
                <td>{order.side === 'BUY' ? '매수' : '매도'}</td>
                <td className="mono num">{formatCents(order.price)}</td>
                <td className="mono num">{order.quantity.toLocaleString('en-US')}</td>
                <td className="mono num">{formatCents(order.price * order.quantity)}</td>
                <td>
                  <span className={`status ${STATUS_CLASS[order.status]}`}>{STATUS_LABEL[order.status]}</span>
                </td>
                <td className="mono">{formatDateTime(order.createdAt)}</td>
                <td className="mono">{order.canceledAt === null ? '–' : formatDateTime(order.canceledAt)}</td>
                <td>
                  {order.status === 'ACCEPTED' && (
                    <button
                      type="button"
                      className="button button--secondary button--small"
                      onClick={() => onCancel(order.orderId)}
                      disabled={canceling}
                      aria-busy={canceling}
                      aria-label={`주문 #${order.orderId} 취소`}
                    >
                      {canceling ? '취소 중…' : '취소'}
                    </button>
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

function CheckIcon() {
  return (
    <svg className="icon" aria-hidden="true" width="16" height="16" viewBox="0 0 16 16">
      <circle cx="8" cy="8" r="6.5" />
      <path d="M5 8.2l2 2 4-4.4" />
    </svg>
  )
}
