import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '../../api/client'
import { toStockDetailError } from './stockDetailError'

describe('toStockDetailError', () => {
  it('400 INVALID_INPUT이면 티커 형식을 안내하고, 다시 불러오기를 권하지 않는다', () => {
    const error = new ApiError(400, {
      status: 400,
      code: 'INVALID_INPUT',
      detail: '입력값이 올바르지 않습니다.',
      errors: [{ field: 'symbol', detail: '길이 초과 혹은 영문이 아닌 숫자 포함' }],
    })
    expect(toStockDetailError(error, 'mü')).toEqual({
      message: '‘mü’은(는) 티커 형식이 아니에요. 티커는 영문 1~10자예요.',
      retryable: false,
    })
  })

  it('404 STOCK_NOT_FOUND면 요청한 심볼로 없는 종목이라고 안내하고, 다시 불러오기를 권하지 않는다', () => {
    const error = new ApiError(404, { status: 404, code: 'STOCK_NOT_FOUND', detail: '없음' })
    expect(toStockDetailError(error, 'AAPL')).toEqual({
      message: '‘AAPL’ 종목을 찾을 수 없어요. mockx에서 거래할 수 있는 종목은 목록에서 확인해 주세요.',
      retryable: false,
    })
  })

  it('모르는 code의 5xx면 서버 detail을 보여 주고 다시 불러올 수 있다', () => {
    const error = new ApiError(500, { status: 500, code: 'SOMETHING', detail: '서버 오류' })
    expect(toStockDetailError(error, 'MU')).toEqual({ message: '서버 오류', retryable: true })
  })

  it.each([
    [404, false], // Problem Details가 아닌 404 (엔드포인트가 없는 백엔드 등)
    [502, true],
  ])('본문이 Problem Details가 아닌 %d이면 상태 코드를 보여 준다', (status, retryable) => {
    expect(toStockDetailError(new ApiError(status, null), 'MU')).toEqual({
      message: `요청에 실패했습니다. (HTTP ${status})`,
      retryable,
    })
  })

  it('네트워크 오류면 백엔드 실행 여부를 안내하고 다시 불러올 수 있다', () => {
    const result = toStockDetailError(new NetworkError(), 'MU')
    expect(result.message).toContain('서버에 연결할 수 없습니다.')
    expect(result.retryable).toBe(true)
  })

  it('ApiError, NetworkError가 아니면 알 수 없는 오류로 보여 준다', () => {
    expect(toStockDetailError(new TypeError('boom'), 'MU')).toEqual({
      message: '알 수 없는 오류가 발생했습니다.',
      retryable: true,
    })
  })
})
