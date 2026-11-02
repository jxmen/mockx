export type SymbolValidationResult = { ok: true; symbol: string } | { ok: false; error: string }

/**
 * 주문할 종목 티커를 검사하고 대문자로 바꾼다. 서버 규칙(PlaceOrderRequest.symbol: ^[a-zA-Z]{1,10}$)과 같다. 전송 전 안내용이다.
 * 검사는 대문자로 바꾸기 전에, i 플래그 없이 한다. "ſpy"(롱 s), "ıbm"(점 없는 i)은 toUpperCase하면 SPY, IBM이 되고,
 * "Key"(켈빈 기호)는 i·u 플래그를 함께 쓰면 k와 같게 취급돼 통과해 버린다.
 */
export function validateSymbolInput(input: string): SymbolValidationResult {
  const value = input.trim()

  if (value === '') {
    return { ok: false, error: '종목 티커를 입력해 주세요.' }
  }
  if (!/^[A-Za-z]+$/.test(value)) {
    return { ok: false, error: '티커는 영문(A~Z)만 입력할 수 있어요.' }
  }
  if (value.length > 10) {
    return { ok: false, error: '티커는 영문 10자까지예요.' }
  }
  return { ok: true, symbol: value.toUpperCase() }
}
