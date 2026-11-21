import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError, type FieldErrorItem } from '../../api/client'
import { shouldReuseIdempotencyKey } from './idempotencyKey'
import { toPlaceOrderErrorFeedback } from './orderError'

function problemError(status: number, code: string | undefined, detail?: string, errors?: FieldErrorItem[]) {
  return new ApiError(status, { status, code, detail, errors })
}

// 결과를 모르는 실패: 같은 키로 다시 보내야 두 번 접수되지 않는다
const UNCERTAIN_ERRORS: [string, unknown][] = [
  ['네트워크 오류', new NetworkError()],
  ['500 (code 없음)', problemError(500, undefined, '서버 오류')],
  ['code가 INVALID_INPUT인 500', problemError(500, 'INVALID_INPUT', '서버 오류', [{ field: 'price', detail: 'x' }])],
  ['본문이 Problem Details가 아닌 502', new ApiError(502, null)],
  ['본문이 Problem Details가 아닌 503', new ApiError(503, null)],
  ['409 IDEMPOTENCY_KEY_IN_PROGRESS', problemError(409, 'IDEMPOTENCY_KEY_IN_PROGRESS', "키 'abc'의 요청이 처리 중입니다.")],
  ['일반 Error (예: 201 응답 JSON 파싱 실패)', new Error('boom')],
  ['문자열', 'boom'],
  ['undefined', undefined],
]

// 확정된 실패: 키를 버리고 다음 주문은 새 키로 보낸다
const SETTLED_ERRORS: [string, unknown][] = [
  ['400 INVALID_INPUT', problemError(400, 'INVALID_INPUT', '입력값이 올바르지 않습니다.', [{ field: 'price', detail: 'x' }])],
  ['본문이 Problem Details가 아닌 400', new ApiError(400, null)],
  ['401 UNAUTHENTICATED', problemError(401, 'UNAUTHENTICATED', 'UnAuthenticated')],
  ['404 STOCK_NOT_FOUND', problemError(404, 'STOCK_NOT_FOUND', '종목을 찾을 수 없습니다.')],
  ['404 ACCOUNT_NOT_FOUND', problemError(404, 'ACCOUNT_NOT_FOUND', '계좌를 찾을 수 없습니다.')],
  ['422 INSUFFICIENT_AVAILABLE_AMOUNT', problemError(422, 'INSUFFICIENT_AVAILABLE_AMOUNT', '주문가능금액이 부족합니다.')],
  ['422 IDEMPOTENCY_KEY_REUSED', problemError(422, 'IDEMPOTENCY_KEY_REUSED', '같은 키로 다른 요청을 보냈습니다.')],
  ['code 없는 409', problemError(409, undefined, '충돌')],
  ['다른 code의 409', problemError(409, 'SOMETHING', '충돌')],
  ['본문이 Problem Details가 아닌 409', new ApiError(409, null)],
]

describe('shouldReuseIdempotencyKey', () => {
  it.each(UNCERTAIN_ERRORS)('%s면 결과를 모르므로 같은 키를 다시 쓴다', (_, error) => {
    expect(shouldReuseIdempotencyKey(error)).toBe(true)
  })

  it.each(SETTLED_ERRORS)('%s면 확정된 결과이므로 키를 버린다', (_, error) => {
    expect(shouldReuseIdempotencyKey(error)).toBe(false)
  })

  it.each([...UNCERTAIN_ERRORS, ...SETTLED_ERRORS])(
    '%s에서 같은 키 재사용 여부는 toPlaceOrderErrorFeedback의 uncertain과 같다',
    (_, error) => {
      expect(shouldReuseIdempotencyKey(error)).toBe(toPlaceOrderErrorFeedback(error).uncertain)
    },
  )
})
