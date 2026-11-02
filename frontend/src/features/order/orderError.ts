import { ApiError, NetworkError } from '../../api/client'

export type PlaceOrderField = 'symbol' | 'side' | 'price' | 'quantity'

const PLACE_ORDER_FIELDS: readonly string[] = ['symbol', 'side', 'price', 'quantity'] satisfies PlaceOrderField[]

export type PlaceOrderErrorFeedback = {
  /** 입력칸 아래에 표시할 메시지 */
  fieldErrors: Partial<Record<PlaceOrderField, string>>
  /** 폼 상단 배너. 입력칸에 붙일 수 없는 에러일 때만 있다 */
  banner: string | null
  /** 접수됐는지 알 수 없다(네트워크 오류, 5xx). 주문 목록을 새로고침해 확인하게 안내한다 */
  uncertain: boolean
}

const UNKNOWN = '알 수 없는 오류가 발생했습니다.'
const UNAUTHENTICATED = 'X-Member-Id는 1 이상의 숫자여야 합니다.'
// 서버는 남의 계좌도 없는 계좌와 똑같이 응답한다. 화면도 둘을 구분하지 않는다
const ACCOUNT_NOT_FOUND = '계좌를 찾을 수 없습니다. 내 계좌가 맞는지 확인해 주세요.'
const PLACE_UNCERTAIN =
  '주문 결과를 확인하지 못했어요. 주문 목록을 새로고침해 접수됐는지 확인한 뒤 다시 시도해 주세요.'
const CANCEL_UNCERTAIN = '취소 결과를 확인하지 못했어요. 주문 목록을 다시 불러왔으니 상태를 확인해 주세요.'

function defaultMessage(error: ApiError): string {
  return error.problem?.detail ?? `요청에 실패했습니다. (HTTP ${error.status})`
}

/**
 * 주문 접수 API 에러를 폼에 표시할 형태로 바꾼다. 분기는 code로 한다. docs/db-design-order.md
 * - INVALID_INPUT → errors[].field에 맞는 입력칸 (붙일 칸이 없으면 배너)
 * - STOCK_NOT_FOUND → 종목 칸
 * - INSUFFICIENT_AVAILABLE_AMOUNT, UNAUTHENTICATED, ACCOUNT_NOT_FOUND, 그 밖의 4xx → 배너
 * - 네트워크 오류, 5xx → 접수 여부를 알 수 없다는 배너
 */
export function toPlaceOrderErrorFeedback(error: unknown): PlaceOrderErrorFeedback {
  if (error instanceof NetworkError) {
    return { fieldErrors: {}, banner: PLACE_UNCERTAIN, uncertain: true }
  }
  if (!(error instanceof ApiError)) {
    return { fieldErrors: {}, banner: UNKNOWN, uncertain: true }
  }
  if (error.status >= 500) {
    return { fieldErrors: {}, banner: PLACE_UNCERTAIN, uncertain: true }
  }

  const problem = error.problem
  switch (problem?.code) {
    case 'INVALID_INPUT': {
      const fieldErrors: Partial<Record<PlaceOrderField, string>> = {}
      for (const item of problem.errors ?? []) {
        const field = item.field as PlaceOrderField
        // 같은 칸에 에러가 여러 개면 첫 번째만 보여 준다
        if (PLACE_ORDER_FIELDS.includes(item.field) && fieldErrors[field] === undefined) {
          fieldErrors[field] = item.detail
        }
      }
      if (Object.keys(fieldErrors).length > 0) {
        return { fieldErrors, banner: null, uncertain: false }
      }
      // 본문이 JSON이 아니면 errors가 없다
      return { fieldErrors: {}, banner: problem.detail ?? '입력값이 올바르지 않습니다.', uncertain: false }
    }
    case 'STOCK_NOT_FOUND':
      return {
        fieldErrors: { symbol: '없는 종목이에요. 종목 목록에 있는 티커인지 확인해 주세요.' },
        banner: null,
        uncertain: false,
      }
    case 'INSUFFICIENT_AVAILABLE_AMOUNT':
      // 서버 detail에는 센트 금액이 들어 있어 그대로 보여 주지 않는다. 금액 비교는 폼 아래 주문 금액·주문 가능 금액으로 보여 준다
      return {
        fieldErrors: {},
        banner: '주문 금액이 주문 가능 금액(예수금 − 주문에 묶인 금액)보다 커요. 가격이나 수량을 줄이거나 먼저 입금해 주세요.',
        uncertain: false,
      }
    case 'UNAUTHENTICATED':
      return { fieldErrors: {}, banner: UNAUTHENTICATED, uncertain: false }
    case 'ACCOUNT_NOT_FOUND':
      return { fieldErrors: {}, banner: ACCOUNT_NOT_FOUND, uncertain: false }
  }
  return { fieldErrors: {}, banner: defaultMessage(error), uncertain: false }
}

export type CancelOrderErrorFeedback = {
  message: string
  /** 취소됐는지 알 수 없다(네트워크 오류, 5xx) */
  uncertain: boolean
  /** 목록이 서버와 어긋났을 수 있어 다시 불러와야 한다 (409, 404 ORDER_NOT_FOUND, uncertain) */
  shouldReload: boolean
}

/**
 * 주문 취소 API 에러를 목록 위 배너 문구로 바꾼다. 분기는 code로 한다.
 * 이미 취소됐거나 없는 주문이면 화면의 목록이 서버와 어긋난 것이라 다시 불러오게 한다.
 */
export function toCancelOrderErrorFeedback(error: unknown): CancelOrderErrorFeedback {
  if (error instanceof NetworkError) {
    return { message: CANCEL_UNCERTAIN, uncertain: true, shouldReload: true }
  }
  if (!(error instanceof ApiError)) {
    return { message: UNKNOWN, uncertain: true, shouldReload: true }
  }
  if (error.status >= 500) {
    return { message: CANCEL_UNCERTAIN, uncertain: true, shouldReload: true }
  }

  switch (error.problem?.code) {
    case 'ORDER_NOT_CANCELABLE':
      return {
        message: '이미 취소된 주문이에요. 주문 목록을 다시 불러왔어요.',
        uncertain: false,
        shouldReload: true,
      }
    case 'ORDER_NOT_FOUND':
      // 서버는 다른 계좌의 주문도 없는 주문과 똑같이 응답한다. 화면도 둘을 구분하지 않는다
      return {
        message: '주문을 찾을 수 없어요. 주문 목록을 다시 불러왔어요.',
        uncertain: false,
        shouldReload: true,
      }
    case 'ACCOUNT_NOT_FOUND':
      return { message: ACCOUNT_NOT_FOUND, uncertain: false, shouldReload: false }
    case 'UNAUTHENTICATED':
      return { message: UNAUTHENTICATED, uncertain: false, shouldReload: false }
  }
  return { message: defaultMessage(error), uncertain: false, shouldReload: false }
}

/** 주문 목록 조회 실패를 화면에 보여 줄 문구로 바꾼다. 분기는 code로 한다. (me/myInfoError.ts 형식) */
export function toOrdersErrorMessage(error: unknown): string {
  if (error instanceof NetworkError) {
    return '서버에 연결할 수 없습니다. 백엔드(./gradlew bootRun)가 켜져 있는지 확인해 주세요.'
  }
  if (error instanceof ApiError) {
    switch (error.problem?.code) {
      case 'UNAUTHENTICATED':
        return UNAUTHENTICATED
      case 'ACCOUNT_NOT_FOUND':
        return ACCOUNT_NOT_FOUND
      case 'INVALID_INPUT':
        return '주문 목록 필터 값이 올바르지 않습니다. (side는 BUY·SELL, status는 ACCEPTED·CANCELED만 받아요)'
    }
    return defaultMessage(error)
  }
  return UNKNOWN
}
