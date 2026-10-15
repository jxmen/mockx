import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { MemberIdContext } from './memberIdContext'

// 로그인이 생기기 전까지 쓰는 개발용 인증 스텁(X-Member-Id). 세션 로그인으로 바꾸면 이 파일을 대체한다.
const STORAGE_KEY = 'mockx.memberId'

function readStored(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function writeStored(memberId: string | null) {
  try {
    if (memberId === null) {
      localStorage.removeItem(STORAGE_KEY)
    } else {
      localStorage.setItem(STORAGE_KEY, memberId)
    }
  } catch {
    // 저장소를 쓸 수 없는 환경(사생활 보호 모드 등)이면 메모리에만 둔다
  }
}

export function MemberIdProvider({ children }: { children: ReactNode }) {
  const [memberId, setState] = useState<string | null>(readStored)

  const setMemberId = useCallback((next: string | null) => {
    const normalized = next === null || next.trim() === '' ? null : next.trim()
    setState(normalized)
    writeStored(normalized)
  }, [])

  const value = useMemo(() => ({ memberId, setMemberId }), [memberId, setMemberId])
  return <MemberIdContext.Provider value={value}>{children}</MemberIdContext.Provider>
}
