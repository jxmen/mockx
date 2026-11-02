import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError, type FieldErrorItem } from '../../api/client'
import { toCancelOrderErrorFeedback, toOrdersErrorMessage, toPlaceOrderErrorFeedback } from './orderError'

// 화면이 직접 만드는 문구는 구현이 정한다. 서버 detail을 그대로 쓰지 않는지 가리려고 눈에 띄는 detail을 넣는다
const SERVER_DETAIL = '서버가 보낸 detail'
const DEFAULT_400 = '요청에 실패했습니다. (HTTP 400)'
const UNAUTHENTICATED_MESSAGE = 'X-Member-Id는 1 이상의 숫자여야 합니다.'
const UNKNOWN_MESSAGE = '알 수 없는 오류가 발생했습니다.'
const NETWORK_MESSAGE = '서버에 연결할 수 없습니다. 백엔드(./gradlew bootRun)가 켜져 있는지 확인해 주세요.'

function problemError(status: number, code: string | undefined, detail?: string, errors?: FieldErrorItem[]) {
  return new ApiError(status, { status, code, detail, errors })
}

describe('toPlaceOrderErrorFeedback', () => {
  it.each(['symbol', 'side', 'price', 'quantity'] as const)(
    '400 INVALID_INPUT의 errors[].field가 %s면 그 detail을 해당 칸에 붙이고 배너는 비운다',
    (field) => {
      const error = problemError(400, 'INVALID_INPUT', '입력값이 올바르지 않습니다.', [
        { field, detail: `${field} 칸 에러` },
      ])
      expect(toPlaceOrderErrorFeedback(error)).toEqual({
        fieldErrors: { [field]: `${field} 칸 에러` },
        banner: null,
        uncertain: false,
      })
    },
  )

  it('400 INVALID_INPUT의 errors가 여러 칸이면 각 칸에 붙인다', () => {
    const error = problemError(400, 'INVALID_INPUT', '입력값이 올바르지 않습니다.', [
      { field: 'price', detail: '0보다 커야 합니다' },
      { field: 'quantity', detail: '10000 이하여야 합니다' },
    ])
    expect(toPlaceOrderErrorFeedback(error)).toEqual({
      fieldErrors: { price: '0보다 커야 합니다', quantity: '10000 이하여야 합니다' },
      banner: null,
      uncertain: false,
    })
  })

  it('400 INVALID_INPUT에 같은 field가 여러 번 있으면 첫 번째 detail을 붙인다', () => {
    const error = problemError(400, 'INVALID_INPUT', '입력값이 올바르지 않습니다.', [
      { field: 'symbol', detail: '첫 번째' },
      { field: 'symbol', detail: '두 번째' },
    ])
    expect(toPlaceOrderErrorFeedback(error).fieldErrors).toEqual({ symbol: '첫 번째' })
  })

  it('400 INVALID_INPUT에서 입력칸이 아닌 field는 무시한다', () => {
    const error = problemError(400, 'INVALID_INPUT', '입력값이 올바르지 않습니다.', [
      { field: 'accountId', detail: '무시' },
      { field: 'price', detail: '가격 에러' },
    ])
    expect(toPlaceOrderErrorFeedback(error)).toEqual({
      fieldErrors: { price: '가격 에러' },
      banner: null,
      uncertain: false,
    })
  })

  it('400 INVALID_INPUT에서 붙일 칸이 하나도 없으면 서버 detail을 배너로 보여 준다', () => {
    const error = problemError(400, 'INVALID_INPUT', SERVER_DETAIL, [{ field: 'accountId', detail: '무시' }])
    expect(toPlaceOrderErrorFeedback(error)).toEqual({ fieldErrors: {}, banner: SERVER_DETAIL, uncertain: false })
  })

  it.each([
    ['errors가 없으면(본문이 JSON이 아님)', undefined],
    ['errors가 빈 배열이면', []],
  ])('400 INVALID_INPUT에 %s 서버 detail을 배너로 보여 준다', (_, errors) => {
    const error = problemError(400, 'INVALID_INPUT', SERVER_DETAIL, errors)
    expect(toPlaceOrderErrorFeedback(error)).toEqual({ fieldErrors: {}, banner: SERVER_DETAIL, uncertain: false })
  })

  it('400 INVALID_INPUT에 붙일 칸도 detail도 없으면 기본 안내를 배너로 보여 준다', () => {
    const error = problemError(400, 'INVALID_INPUT')
    expect(toPlaceOrderErrorFeedback(error)).toEqual({
      fieldErrors: {},
      banner: '입력값이 올바르지 않습니다.',
      uncertain: false,
    })
  })

  it('404 STOCK_NOT_FOUND는 화면이 만든 문구를 종목 칸에 붙이고 배너는 비운다', () => {
    const feedback = toPlaceOrderErrorFeedback(problemError(404, 'STOCK_NOT_FOUND', SERVER_DETAIL))
    expect(feedback.fieldErrors.symbol).toBeTruthy()
    expect(feedback.fieldErrors.symbol).not.toBe(SERVER_DETAIL)
    expect(Object.keys(feedback.fieldErrors)).toEqual(['symbol'])
    expect(feedback.banner).toBeNull()
    expect(feedback.uncertain).toBe(false)
  })

  it('422 INSUFFICIENT_AVAILABLE_AMOUNT는 서버 detail(센트 금액) 대신 화면이 만든 문구를 배너로 보여 준다', () => {
    const error = problemError(
      422,
      'INSUFFICIENT_AVAILABLE_AMOUNT',
      '주문가능금액이 부족합니다. (요청: 1000000, 가능: 500)',
    )
    const feedback = toPlaceOrderErrorFeedback(error)
    expect(feedback.banner).toBeTruthy()
    expect(feedback.banner).not.toBe('주문가능금액이 부족합니다. (요청: 1000000, 가능: 500)')
    expect(feedback.fieldErrors).toEqual({})
    expect(feedback.uncertain).toBe(false)
  })

  it('401 UNAUTHENTICATED는 헤더 형식을 배너로 안내한다', () => {
    expect(toPlaceOrderErrorFeedback(problemError(401, 'UNAUTHENTICATED', 'UnAuthenticated'))).toEqual({
      fieldErrors: {},
      banner: UNAUTHENTICATED_MESSAGE,
      uncertain: false,
    })
  })

  it('404 ACCOUNT_NOT_FOUND는 화면이 만든 문구를 배너로 보여 준다', () => {
    const feedback = toPlaceOrderErrorFeedback(problemError(404, 'ACCOUNT_NOT_FOUND', SERVER_DETAIL))
    expect(feedback.banner).toBeTruthy()
    expect(feedback.banner).not.toBe(SERVER_DETAIL)
    expect(feedback.fieldErrors).toEqual({})
    expect(feedback.uncertain).toBe(false)
  })

  it('404 ACCOUNT_NOT_FOUND는 남의 계좌인지 없는 계좌인지 구분하지 않는다', () => {
    const others = toPlaceOrderErrorFeedback(problemError(404, 'ACCOUNT_NOT_FOUND', "계좌 id '2'를 찾을 수 없습니다."))
    const missing = toPlaceOrderErrorFeedback(
      problemError(404, 'ACCOUNT_NOT_FOUND', "계좌 id '999'를 찾을 수 없습니다."),
    )
    expect(others).toEqual(missing)
  })

  it('모르는 4xx code면 서버 detail을 배너로 보여 준다', () => {
    expect(toPlaceOrderErrorFeedback(problemError(409, 'SOMETHING', '충돌'))).toEqual({
      fieldErrors: {},
      banner: '충돌',
      uncertain: false,
    })
  })

  it('모르는 4xx code에 detail이 없으면 상태 코드를 보여 준다', () => {
    expect(toPlaceOrderErrorFeedback(problemError(409, 'SOMETHING'))).toEqual({
      fieldErrors: {},
      banner: '요청에 실패했습니다. (HTTP 409)',
      uncertain: false,
    })
  })

  it.each([400, 404])('본문이 Problem Details가 아닌 %d 응답이면 상태 코드를 배너로 보여 준다', (status) => {
    expect(toPlaceOrderErrorFeedback(new ApiError(status, null))).toEqual({
      fieldErrors: {},
      banner: `요청에 실패했습니다. (HTTP ${status})`,
      uncertain: false,
    })
  })

  it.each([
    ['5xx', problemError(500, undefined, '서버 오류')],
    ['code가 INVALID_INPUT인 5xx', problemError(500, 'INVALID_INPUT', '서버 오류', [{ field: 'price', detail: 'x' }])],
    ['본문이 Problem Details가 아닌 5xx', new ApiError(502, null)],
    ['네트워크 오류', new NetworkError()],
  ])('%s는 접수 여부를 알 수 없다고 배너로 안내한다', (_, error) => {
    const feedback = toPlaceOrderErrorFeedback(error)
    expect(feedback.uncertain).toBe(true)
    expect(feedback.fieldErrors).toEqual({})
    expect(feedback.banner).toBeTruthy()
    expect(feedback.banner).not.toBe('서버 오류')
  })

  it.each([
    ['일반 Error', new Error('boom')],
    ['문자열', 'boom'],
    ['undefined', undefined],
  ])('ApiError도 NetworkError도 아닌 값(%s)은 알 수 없는 오류로 안내하고 결과를 모른다고 본다', (_, error) => {
    expect(toPlaceOrderErrorFeedback(error)).toEqual({ fieldErrors: {}, banner: UNKNOWN_MESSAGE, uncertain: true })
  })
})

describe('toCancelOrderErrorFeedback', () => {
  it('409 ORDER_NOT_CANCELABLE은 화면이 만든 문구를 보여 주고 목록을 다시 불러오게 한다', () => {
    const feedback = toCancelOrderErrorFeedback(problemError(409, 'ORDER_NOT_CANCELABLE', SERVER_DETAIL))
    expect(feedback.message).toBeTruthy()
    expect(feedback.message).not.toBe(SERVER_DETAIL)
    expect(feedback.uncertain).toBe(false)
    expect(feedback.shouldReload).toBe(true)
  })

  it('404 ORDER_NOT_FOUND는 화면이 만든 문구를 보여 주고 목록을 다시 불러오게 한다', () => {
    const feedback = toCancelOrderErrorFeedback(problemError(404, 'ORDER_NOT_FOUND', SERVER_DETAIL))
    expect(feedback.message).toBeTruthy()
    expect(feedback.message).not.toBe(SERVER_DETAIL)
    expect(feedback.uncertain).toBe(false)
    expect(feedback.shouldReload).toBe(true)
  })

  it('ORDER_NOT_CANCELABLE과 ORDER_NOT_FOUND는 다른 문구로 안내한다', () => {
    const notCancelable = toCancelOrderErrorFeedback(problemError(409, 'ORDER_NOT_CANCELABLE', SERVER_DETAIL))
    const notFound = toCancelOrderErrorFeedback(problemError(404, 'ORDER_NOT_FOUND', SERVER_DETAIL))
    expect(notCancelable.message).not.toBe(notFound.message)
  })

  it('404 ORDER_NOT_FOUND는 남의 주문인지 없는 주문인지 구분하지 않는다', () => {
    const others = toCancelOrderErrorFeedback(problemError(404, 'ORDER_NOT_FOUND', "주문 id '3'을 찾을 수 없습니다."))
    const missing = toCancelOrderErrorFeedback(
      problemError(404, 'ORDER_NOT_FOUND', "주문 id '999'를 찾을 수 없습니다."),
    )
    expect(others).toEqual(missing)
  })

  it('404 ACCOUNT_NOT_FOUND는 화면이 만든 문구를 보여 주고 목록은 다시 불러오지 않는다', () => {
    const feedback = toCancelOrderErrorFeedback(problemError(404, 'ACCOUNT_NOT_FOUND', SERVER_DETAIL))
    expect(feedback.message).toBeTruthy()
    expect(feedback.message).not.toBe(SERVER_DETAIL)
    expect(feedback.uncertain).toBe(false)
    expect(feedback.shouldReload).toBe(false)
  })

  it('404 ACCOUNT_NOT_FOUND는 남의 계좌인지 없는 계좌인지 구분하지 않는다', () => {
    const others = toCancelOrderErrorFeedback(problemError(404, 'ACCOUNT_NOT_FOUND', "계좌 id '2'를 찾을 수 없습니다."))
    const missing = toCancelOrderErrorFeedback(
      problemError(404, 'ACCOUNT_NOT_FOUND', "계좌 id '999'를 찾을 수 없습니다."),
    )
    expect(others).toEqual(missing)
  })

  it('401 UNAUTHENTICATED는 헤더 형식을 안내하고 목록은 다시 불러오지 않는다', () => {
    expect(toCancelOrderErrorFeedback(problemError(401, 'UNAUTHENTICATED', 'UnAuthenticated'))).toEqual({
      message: UNAUTHENTICATED_MESSAGE,
      uncertain: false,
      shouldReload: false,
    })
  })

  it('400 INVALID_INPUT(경로 변수가 숫자 아님)은 그 밖의 4xx처럼 서버 detail을 보여 준다', () => {
    expect(toCancelOrderErrorFeedback(problemError(400, 'INVALID_INPUT', SERVER_DETAIL))).toEqual({
      message: SERVER_DETAIL,
      uncertain: false,
      shouldReload: false,
    })
  })

  it('모르는 4xx code면 서버 detail을 보여 준다', () => {
    expect(toCancelOrderErrorFeedback(problemError(409, 'SOMETHING', '충돌'))).toEqual({
      message: '충돌',
      uncertain: false,
      shouldReload: false,
    })
  })

  it('모르는 4xx code에 detail이 없으면 상태 코드를 보여 준다', () => {
    expect(toCancelOrderErrorFeedback(problemError(400, 'SOMETHING'))).toEqual({
      message: DEFAULT_400,
      uncertain: false,
      shouldReload: false,
    })
  })

  it('본문이 Problem Details가 아닌 4xx면 상태 코드를 보여 준다', () => {
    expect(toCancelOrderErrorFeedback(new ApiError(400, null))).toEqual({
      message: DEFAULT_400,
      uncertain: false,
      shouldReload: false,
    })
  })

  it.each([
    ['5xx', problemError(500, undefined, '서버 오류')],
    ['code가 ORDER_NOT_CANCELABLE인 5xx', problemError(500, 'ORDER_NOT_CANCELABLE', '서버 오류')],
    ['본문이 Problem Details가 아닌 5xx', new ApiError(502, null)],
    ['네트워크 오류', new NetworkError()],
  ])('%s는 취소 여부를 알 수 없다고 안내하고 목록을 다시 불러오게 한다', (_, error) => {
    const feedback = toCancelOrderErrorFeedback(error)
    expect(feedback.uncertain).toBe(true)
    expect(feedback.shouldReload).toBe(true)
    expect(feedback.message).toBeTruthy()
    expect(feedback.message).not.toBe('서버 오류')
  })

  it.each([
    ['일반 Error', new Error('boom')],
    ['문자열', 'boom'],
    ['undefined', undefined],
  ])('ApiError도 NetworkError도 아닌 값(%s)은 알 수 없는 오류로 안내하고 목록을 다시 불러오게 한다', (_, error) => {
    expect(toCancelOrderErrorFeedback(error)).toEqual({
      message: UNKNOWN_MESSAGE,
      uncertain: true,
      shouldReload: true,
    })
  })
})

describe('toOrdersErrorMessage', () => {
  it('네트워크 오류면 백엔드 실행 여부를 안내한다', () => {
    expect(toOrdersErrorMessage(new NetworkError())).toBe(NETWORK_MESSAGE)
  })

  it('401 UNAUTHENTICATED는 헤더 형식을 안내한다', () => {
    expect(toOrdersErrorMessage(problemError(401, 'UNAUTHENTICATED', 'UnAuthenticated'))).toBe(UNAUTHENTICATED_MESSAGE)
  })

  it('404 ACCOUNT_NOT_FOUND는 서버 detail 대신 화면이 만든 문구를 보여 준다', () => {
    const message = toOrdersErrorMessage(problemError(404, 'ACCOUNT_NOT_FOUND', SERVER_DETAIL))
    expect(message).toBeTruthy()
    expect(message).not.toBe(SERVER_DETAIL)
  })

  it('404 ACCOUNT_NOT_FOUND는 남의 계좌인지 없는 계좌인지 구분하지 않는다', () => {
    expect(toOrdersErrorMessage(problemError(404, 'ACCOUNT_NOT_FOUND', "계좌 id '2'를 찾을 수 없습니다."))).toBe(
      toOrdersErrorMessage(problemError(404, 'ACCOUNT_NOT_FOUND', "계좌 id '999'를 찾을 수 없습니다.")),
    )
  })

  it('400 INVALID_INPUT(필터 값 오류)은 서버 detail 대신 화면이 만든 문구를 보여 준다', () => {
    const error = problemError(400, 'INVALID_INPUT', SERVER_DETAIL, [{ field: 'status', detail: '허용하지 않는 값' }])
    const message = toOrdersErrorMessage(error)
    expect(message).toBeTruthy()
    expect(message).not.toBe(SERVER_DETAIL)
    expect(message).not.toBe(DEFAULT_400)
  })

  it('모르는 code면 서버 detail을 보여 준다', () => {
    expect(toOrdersErrorMessage(problemError(409, 'SOMETHING', '충돌'))).toBe('충돌')
  })

  it('5xx에 detail이 있으면 서버 detail을 보여 준다', () => {
    expect(toOrdersErrorMessage(problemError(500, undefined, '서버 오류'))).toBe('서버 오류')
  })

  it('모르는 code에 detail이 없으면 상태 코드를 보여 준다', () => {
    expect(toOrdersErrorMessage(problemError(409, 'SOMETHING'))).toBe('요청에 실패했습니다. (HTTP 409)')
  })

  it.each([400, 502])('본문이 Problem Details가 아닌 %d 응답이면 상태 코드를 보여 준다', (status) => {
    expect(toOrdersErrorMessage(new ApiError(status, null))).toBe(`요청에 실패했습니다. (HTTP ${status})`)
  })

  it.each([
    ['일반 Error', new Error('boom')],
    ['문자열', 'boom'],
    ['undefined', undefined],
  ])('ApiError도 NetworkError도 아닌 값(%s)은 알 수 없는 오류로 안내한다', (_, error) => {
    expect(toOrdersErrorMessage(error)).toBe(UNKNOWN_MESSAGE)
  })
})
