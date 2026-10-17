import { NavLink } from 'react-router'
import { useMemberId } from '../auth/memberIdContext'

export function Header() {
  const { memberId, setMemberId } = useMemberId()

  return (
    <header className="header">
      <div className="header__left">
        <span className="header__brand">mockx</span>
        <nav aria-label="주요 메뉴" className="header__nav">
          <NavLink to="/signup" className="header__link">
            회원가입
          </NavLink>
          <NavLink to="/me" className="header__link">
            내 정보
          </NavLink>
        </nav>
      </div>
      {import.meta.env.DEV && (
        <div className="header__dev">
          <label htmlFor="dev-member-id" className="header__dev-label">
            X-Member-Id
          </label>
          <input
            id="dev-member-id"
            className="header__dev-input"
            inputMode="numeric"
            placeholder="없음"
            value={memberId ?? ''}
            onChange={(event) => setMemberId(event.target.value)}
          />
          <span className="header__dev-badge">local 전용</span>
        </div>
      )}
    </header>
  )
}
