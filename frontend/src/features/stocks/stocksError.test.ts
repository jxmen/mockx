import { describe, expect, it } from 'vitest'
import { ApiError, NetworkError } from '../../api/client'
import { toStocksErrorMessage } from './stocksError'

describe('toStocksErrorMessage', () => {
  it('400 INVALID_INPUT의 errors에 sort가 있으면 그 detail을 보여 준다', () => {
    const error = new ApiError(400, {
      status: 400,
      code: 'INVALID_INPUT',
      detail: '입력값이 올바르지 않습니다.',
      errors: [{ field: 'sort', detail: '허용된 정렬 키값이 아닙니다 : name' }],
    })
    expect(toStocksErrorMessage(error)).toBe('정렬 조건이 올바르지 않습니다. 허용된 정렬 키값이 아닙니다 : name')
  })

  it('400 INVALID_INPUT에 sort 에러가 없으면 서버 detail을 보여 준다', () => {
    const error = new ApiError(400, { status: 400, code: 'INVALID_INPUT', detail: '입력값이 올바르지 않습니다.' })
    expect(toStocksErrorMessage(error)).toBe('정렬 조건이 올바르지 않습니다. 입력값이 올바르지 않습니다.')
  })

  it('모르는 code면 서버 detail을 보여 준다', () => {
    const error = new ApiError(500, { status: 500, code: 'SOMETHING', detail: '서버 오류' })
    expect(toStocksErrorMessage(error)).toBe('서버 오류')
  })

  it.each([400, 502])('본문이 Problem Details가 아닌 %d이면 상태 코드를 보여 준다', (status) => {
    expect(toStocksErrorMessage(new ApiError(status, null))).toBe(`요청에 실패했습니다. (HTTP ${status})`)
  })

  it('네트워크 오류면 백엔드 실행 여부를 안내한다', () => {
    expect(toStocksErrorMessage(new NetworkError())).toContain('서버에 연결할 수 없습니다.')
  })

  it('ApiError, NetworkError가 아니면 알 수 없는 오류로 보여 준다', () => {
    expect(toStocksErrorMessage(new TypeError('boom'))).toBe('알 수 없는 오류가 발생했습니다.')
  })
})
