import { Link, NavLink } from 'react-router'
import { useMemberId } from '../auth/memberIdContext'
import { ThemeToggle } from './ThemeToggle'

export function Header() {
  const { memberId, setMemberId } = useMemberId()

  return (
    <header className="header">
      <div className="header__left">
        <Link to="/" className="header__brand">
          mockx
        </Link>
        <nav aria-label="주요 메뉴" className="header__nav">
          <NavLink to="/signup" className="header__link">
            회원가입
          </NavLink>
          <NavLink to="/me" className="header__link">
            내 정보
          </NavLink>
          <NavLink to="/deposit" className="header__link">
            입금
          </NavLink>
          <NavLink to="/stocks" className="header__link">
            종목
          </NavLink>
          <NavLink to="/orders" className="header__link">
            주문
          </NavLink>
        </nav>
      </div>
      <div className="header__right">
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
        <ThemeToggle />
      </div>
    </header>
  )
}
