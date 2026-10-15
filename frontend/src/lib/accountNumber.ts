/** 숫자 12자리 계좌번호를 4-4-4로 끊어 표시한다. 형식이 다르면 받은 그대로 돌려준다. */
export function formatAccountNumber(raw: string): string {
  if (!/^\d{12}$/.test(raw)) {
    return raw
  }
  return `${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8)}`
}
