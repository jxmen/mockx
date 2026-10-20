import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '../../api/client'
import { toDepositErrorFeedback } from './depositError'

describe('toDepositErrorFeedback', () => {
  it('400 INVALID_INPUT의 errors에 amount가 있으면 그 detail을 금액 칸에 붙인다', () => {
    const error = new ApiError(400, {
      status: 400,
      code: 'INVALID_INPUT',
      detail: '입력값이 올바르지 않습니다.',
      errors: [{ field: 'amount', detail: '10000 이하여야 합니다' }],
    })
    expect(toDepositErrorFeedback(error)).toEqual({
      amountError: '10000 이하여야 합니다',
      banner: null,
      uncertain: false,
    })
  })

  it('400 INVALID_INPUT에 errors가 없으면(형식 오류) 서버 detail을 금액 칸에 붙인다', () => {
    const error = new ApiError(400, { status: 400, code: 'INVALID_INPUT', detail: '입력값이 올바르지 않습니다.' })
    expect(toDepositErrorFeedback(error)).toEqual({
      amountError: '입력값이 올바르지 않습니다.',
      banner: null,
      uncertain: false,
    })
  })

  it('401 UNAUTHENTICATED는 헤더 형식을 배너로 안내한다', () => {
    const error = new ApiError(401, { status: 401, code: 'UNAUTHENTICATED', detail: 'UnAuthenticated' })
    expect(toDepositErrorFeedback(error)).toEqual({
      amountError: null,
      banner: 'X-Member-Id는 1 이상의 숫자여야 합니다.',
      uncertain: false,
    })
  })

  it('404 ACCOUNT_NOT_FOUND는 남의 계좌인지 없는 계좌인지 구분하지 않고 배너로 알린다', () => {
    const error = new ApiError(404, {
      status: 404,
      code: 'ACCOUNT_NOT_FOUND',
      detail: "계좌 id '2'를 찾을 수 없습니다.",
    })
    expect(toDepositErrorFeedback(error)).toEqual({
      amountError: null,
      banner: '계좌를 찾을 수 없습니다. 내 계좌가 맞는지 확인해 주세요.',
      uncertain: false,
    })
  })

  it('모르는 4xx code면 서버 detail을 배너로 보여 준다', () => {
    const error = new ApiError(409, { status: 409, code: 'SOMETHING', detail: '충돌' })
    expect(toDepositErrorFeedback(error)).toEqual({ amountError: null, banner: '충돌', uncertain: false })
  })

  it('본문이 Problem Details가 아닌 4xx면 상태 코드를 보여 준다', () => {
    expect(toDepositErrorFeedback(new ApiError(400, null))).toEqual({
      amountError: null,
      banner: '요청에 실패했습니다. (HTTP 400)',
      uncertain: false,
    })
  })

  it.each([
    ['5xx', new ApiError(500, { status: 500, detail: '서버 오류' })],
    ['본문이 Problem Details가 아닌 5xx', new ApiError(502, null)],
    ['네트워크 오류', new NetworkError()],
  ])('%s는 반영 여부를 알 수 없다고 안내한다', (_, error) => {
    const feedback = toDepositErrorFeedback(error)
    expect(feedback.uncertain).toBe(true)
    expect(feedback.amountError).toBeNull()
    expect(feedback.banner).toContain('입금 결과를 확인하지 못했어요.')
  })
})
