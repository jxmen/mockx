import { useState } from 'react'
import { stockIconUrl } from './stockIcon'

/**
 * public/stocks/{심볼}.png. 심볼(대문자) 그대로 찾는다. 배포 서버는 파일 이름의 대소문자를 구분한다. docs/design/stock.md 5장
 * 아이콘이 없는 종목은 첫 글자로 대신한다.
 */
export function StockLogo({ symbol }: { symbol: string }) {
  const [failed, setFailed] = useState(false)

  return (
    <span className="stock-logo">
      {failed ? (
        <span className="stock-logo__image stock-logo__fallback" aria-hidden="true">
          {symbol.charAt(0)}
        </span>
      ) : (
        <img
          className="stock-logo__image"
          src={stockIconUrl(symbol)}
          alt=""
          width={36}
          height={36}
          onError={() => setFailed(true)}
        />
      )}
      <UsFlag />
    </span>
  )
}

/** 미국 국기를 줄인 배지. 줄무늬 7개와 파란 칸만 그린다 */
function UsFlag() {
  return (
    <svg className="stock-logo__flag" width="18" height="13" viewBox="0 0 18 13" aria-hidden="true">
      <rect width="18" height="13" fill="#ffffff" />
      {[0, 2, 4, 6, 8, 10, 12].map((y) => (
        <rect key={y} y={y} width="18" height="1" fill="#d22f3c" />
      ))}
      <rect width="8" height="7" fill="#2b4a9b" />
    </svg>
  )
}
