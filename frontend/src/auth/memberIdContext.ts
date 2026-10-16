import { createContext, useContext } from 'react'

export type MemberIdContextValue = {
  /** X-Member-Id 헤더에 담을 회원 ID. 없으면 null */
  memberId: string | null
  setMemberId: (memberId: string | null) => void
}

export const MemberIdContext = createContext<MemberIdContextValue | null>(null)

export function useMemberId(): MemberIdContextValue {
  const value = useContext(MemberIdContext)
  if (value === null) {
    throw new Error('MemberIdProvider 안에서만 쓸 수 있습니다.')
  }
  return value
}
