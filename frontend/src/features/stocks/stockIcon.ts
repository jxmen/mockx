/**
 * 종목 아이콘 경로. public/stocks/{심볼}.png를 심볼(대문자) 그대로 찾는다. docs/db-design-stock.md 5장
 * 배포 서버는 파일 이름의 대소문자를 구분하므로 심볼을 바꾸지 않는다.
 */
export function stockIconUrl(symbol: string): string {
  return `${import.meta.env.BASE_URL}stocks/${encodeURIComponent(symbol)}.png`
}
