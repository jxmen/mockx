import { ApiError, NetworkError } from '../../api/client'

export type StockDetailError = {
  message: string
  /** 다시 불러오면 결과가 달라질 수 있는가. 형식 오류(400)와 없는 종목(404)은 다시 불러와도 같다 */
  retryable: boolean
}

/**
 * 종목 상세 조회 실패를 화면에 보여 줄 문구로 바꾼다. 분기는 code로 한다. docs/design/stock.md 4.6
 * - INVALID_INPUT → 심볼이 영문 1~10자가 아님
 * - STOCK_NOT_FOUND → 형식은 맞지만 없는 종목
 * - 조회라서 돈이 움직이지 않는다. 네트워크 오류, 5xx는 다시 불러오면 된다
 */
export function toStockDetailError(error: unknown, symbol: string): StockDetailError {
  if (error instanceof NetworkError) {
    return {
      message: '서버에 연결할 수 없습니다. 백엔드(./gradlew bootRun)가 켜져 있는지 확인해 주세요.',
      retryable: true,
    }
  }
  if (error instanceof ApiError) {
    switch (error.problem?.code) {
      case 'INVALID_INPUT':
        return { message: `‘${symbol}’은(는) 티커 형식이 아니에요. 티커는 영문 1~10자예요.`, retryable: false }
      case 'STOCK_NOT_FOUND':
        return {
          message: `‘${symbol}’ 종목을 찾을 수 없어요. mockx에서 거래할 수 있는 종목은 목록에서 확인해 주세요.`,
          retryable: false,
        }
    }
    return {
      message: error.problem?.detail ?? `요청에 실패했습니다. (HTTP ${error.status})`,
      retryable: error.status >= 500,
    }
  }
  return { message: '알 수 없는 오류가 발생했습니다.', retryable: true }
}
