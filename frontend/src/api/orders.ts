import { apiRequest } from './client'

/** 매수·매도. 지금은 BUY만 접수한다(SELL은 400 INVALID_INPUT, field=side). docs/db-design-order.md */
export type OrderSide = 'BUY' | 'SELL'
/** 접수됨·취소됨. 체결 상태는 매칭 엔진과 함께 추가된다 */
export type OrderStatus = 'ACCEPTED' | 'CANCELED'

export type PlaceOrderRequest = {
  /** 티커. 영문 1~10자(^[a-zA-Z]{1,10}$). 서버가 대문자로 바꿔 찾는다 */
  symbol: string
  /** 대소문자는 서버가 정규화한다(buy → BUY) */
  side: OrderSide
  /** 지정가. 센트 단위 정수($1 = 100). 1 이상 1,000,000($10,000) 이하 */
  price: number
  /** 주문 수량(주). 정수, 1 이상 10,000 이하 */
  quantity: number
}

export type PlaceOrderResponse = {
  orderId: number
  /** 대문자 티커 */
  symbol: string
  side: OrderSide
  /** 지정가(센트) */
  price: number
  quantity: number
  /** 접수 직후라 항상 ACCEPTED */
  status: OrderStatus
}

export type CancelOrderResponse = PlaceOrderResponse & {
  /** 취소 시각. ISO-8601 UTC (예: "2026-10-31T12:34:56.789Z") */
  canceledAt: string
}

export type OrderItem = {
  orderId: number
  symbol: string
  /** 종목명(예: "Micron Technology") */
  stockName: string
  side: OrderSide
  /** 지정가(센트) */
  price: number
  quantity: number
  status: OrderStatus
  /** 접수 시각. ISO-8601 UTC */
  createdAt: string
  /** 취소 시각. ISO-8601 UTC. 취소되지 않았으면 null(필드는 항상 있다) */
  canceledAt: string | null
}

/** 페이지 정보 같은 필드가 붙을 수 있어 배열을 orders로 감싼다. 최신순(orderId 내림차순) */
export type GetOrdersResponse = {
  orders: OrderItem[]
}

function ordersBasePath(accountId: number | string): string {
  return `/api/accounts/${encodeURIComponent(accountId)}/orders`
}

/** 주문 목록 경로. query는 toOrdersQuery()의 결과('' 또는 '?status=...')다. 화면의 요청 줄 표시에도 쓴다 */
export function ordersPath(accountId: number | string, query: string): string {
  return `${ordersBasePath(accountId)}${query}`
}

/**
 * 지정가 매수 주문을 접수한다. 주문 금액(price × quantity)만큼 예수금이 묶인다(hold).
 * 없는 종목은 404 STOCK_NOT_FOUND, 남의 계좌·없는 계좌는 404 ACCOUNT_NOT_FOUND,
 * 주문 금액이 주문 가능 금액(예수금 − hold)을 넘으면 422 INSUFFICIENT_AVAILABLE_AMOUNT
 */
export function placeOrder(
  accountId: number | string,
  request: PlaceOrderRequest,
  memberId: string | null,
): Promise<PlaceOrderResponse> {
  return apiRequest<PlaceOrderResponse>(ordersBasePath(accountId), { method: 'POST', body: request, memberId })
}

/**
 * 주문을 취소하고 묶인 금액을 푼다(release). 본문 없이 보낸다.
 * 남의 주문·없는 주문은 404 ORDER_NOT_FOUND, 이미 취소된 주문은 409 ORDER_NOT_CANCELABLE
 */
export function cancelOrder(
  accountId: number | string,
  orderId: number | string,
  memberId: string | null,
): Promise<CancelOrderResponse> {
  return apiRequest<CancelOrderResponse>(`${ordersBasePath(accountId)}/${encodeURIComponent(orderId)}/cancel`, {
    method: 'POST',
    memberId,
  })
}

/**
 * 내 계좌의 주문 목록. query는 toOrdersQuery()의 결과를 그대로 붙인다.
 * 허용하지 않는 값이나 빈 값(?status=)은 400 INVALID_INPUT(errors[].field = side | status)
 */
export function getOrders(
  accountId: number | string,
  query: string,
  memberId: string | null,
  signal?: AbortSignal,
): Promise<GetOrdersResponse> {
  return apiRequest<GetOrdersResponse>(ordersPath(accountId, query), { memberId, signal })
}
