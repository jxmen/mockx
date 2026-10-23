import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { pageTitle } from '../../lib/pageTitle'

type DemoStep = {
  title: string
  /** 화면이 없으면 null (준비 중) */
  to: string | null
  summary: ReactNode
  /** 화면에서 일부러 내 볼 만한 에러. 면접에서 서버 검증을 보여 주는 용도다 */
  tryThis: ReactNode
}

// 시연 순서. 실제로 이 순서대로 눌러야 다음 화면에 쓸 회원·예수금이 생긴다
const DEMO_STEPS: DemoStep[] = [
  {
    title: '회원가입',
    to: '/signup',
    summary: '회원과 위탁계좌를 한 트랜잭션으로 만들어요. 계좌번호는 상품코드, 순번, Luhn 검증숫자로 된 12자리예요.',
    tryThis: (
      <>
        <code>Alice</code>가 있을 때 <code>alice</code>로 가입하면 <code>409 USERNAME_DUPLICATED</code>
      </>
    ),
  },
  {
    title: '내 정보',
    to: '/me',
    summary: '로그인 대신 헤더의 X-Member-Id로 회원을 골라요. 가입하면 새 회원 번호로 바뀌어요.',
    tryThis: (
      <>
        없는 회원 번호는 <code>404 MEMBER_NOT_FOUND</code>, 숫자가 아니면 <code>401 UNAUTHENTICATED</code>
      </>
    ),
  },
  {
    title: '입금',
    to: '/deposit',
    summary: '달러로 입력하면 센트 정수로 바꿔 보내요. 서버는 예수금을 늘리고 원장에 한 줄을 남겨요.',
    tryThis: (
      <>
        개발 모드에서 다른 계좌 id로 보내면, 남의 계좌도 없는 계좌와 같은 <code>404 ACCOUNT_NOT_FOUND</code>
      </>
    ),
  },
  {
    title: '종목',
    to: '/stocks',
    summary: '인증 없이 보는 공개 API예요. 정렬 필드는 허용 목록(id, symbol)에 있는 것만 받고, 상세는 티커를 대문자로 바꿔 찾아요.',
    tryThis: (
      <>
        개발 모드에서 <code>sort=name</code>을 보내면 <code>400 INVALID_INPUT</code>. 상세에서 <code>mü</code>로 찾으면 악센트를
        무시하는 DB 콜레이션에 기대지 않고 <code>400</code>
      </>
    ),
  },
  {
    title: '지정가 주문',
    to: null,
    summary: '지정가 매수 주문을 받고, 주문 금액만큼 예수금을 묶어(hold) 다른 주문에 쓰지 못하게 해요.',
    tryThis: '주문 API를 만드는 중이에요.',
  },
]

type ProgressStatus = 'done' | 'in-progress' | 'planned'

type ProgressItem = {
  area: string
  status: ProgressStatus
  detail: ReactNode
}

const STATUS_LABEL: Record<ProgressStatus, string> = {
  done: '완료',
  'in-progress': '진행 중',
  planned: '예정',
}

// docs/ 설계 문서의 작업 목록과 src/test의 테스트 기준(2026-10-24). 기능을 끝내면 여기도 고친다
const PROGRESS: ProgressItem[] = [
  {
    area: '회원가입·계좌 개설',
    status: 'done',
    detail: (
      <>
        BCrypt 해시, 계좌 생성이 실패하면 회원도 롤백, Luhn 계좌번호 (<code>SignupE2ETest</code>)
      </>
    ),
  },
  {
    area: '입금·원장',
    status: 'done',
    detail: (
      <>
        정수 센트, 단식부기 원장, 비관적 락으로 같은 계좌 동시 입금 100건 검증 (<code>DepositConcurrencyTest</code>)
      </>
    ),
  },
  {
    area: '에러 응답',
    status: 'done',
    detail: '검증·권한 에러를 RFC 9457 Problem Details와 code 필드로 응답, 남의 리소스는 404로 숨김',
  },
  {
    area: '종목 목록·상세',
    status: 'done',
    detail: (
      <>
        공개 API, 정렬 허용 목록, 잘못된 정렬은 기본값으로 넘기지 않고 400. 상세는 티커를 영문 1~10자로 검사한 뒤 대문자로
        찾음 (<code>StockE2ETest</code>)
      </>
    ),
  },
  {
    area: '지정가 주문·예수금 hold',
    status: 'in-progress',
    detail: (
      <>
        계좌의 hold·release 도메인 로직과 테스트까지 완료 (<code>AccountTest</code>)
      </>
    ),
  },
  { area: '주문 상태 머신', status: 'planned', detail: '접수, 부분 체결, 체결, 취소 사이의 전이 규칙' },
  { area: '중복 주문 방지', status: 'planned', detail: 'Idempotency-Key 헤더로 같은 주문 요청을 한 번만 처리' },
  { area: '매칭 엔진', status: 'planned', detail: '종목별 인메모리 오더북, 가격·시간 우선 체결' },
  { area: '동시성 테스트', status: 'planned', detail: '같은 계좌에서 매수 주문 1,000건을 동시에 보내도 예수금이 맞는지' },
]

const TECH_STACK = ['Kotlin 2.3', 'Spring Boot 4.1', 'MySQL 8.4', 'Flyway', 'Kotest', 'Testcontainers', 'React']

export function HomePage() {
  return (
    <main className="page page--wide">
      <title>{pageTitle('미국 주식 모의 거래소')}</title>

      <section className="home-hero">
        <h1 className="home-hero__title">
          주문부터 체결까지,
          <br />
          증권 백엔드를 직접 만드는 모의 거래소
        </h1>
        <p className="page__lead">
          mockx는 미국 주식을 주문하고 체결하는 증권 시스템을 백엔드부터 만든 포트폴리오예요. 아래 순서대로 눌러 보면 API가
          어떻게 동작하고, 어떤 요청을 어떤 에러로 막는지 화면에서 확인할 수 있어요.
        </p>
        <div className="home-hero__actions">
          <Link className="button button--primary" to="/signup">
            회원가입부터 시작하기
          </Link>
          <a className="button button--secondary" href="#progress">
            구현 현황 보기
          </a>
        </div>
        <ul className="tech-list" aria-label="사용한 기술">
          {TECH_STACK.map((tech) => (
            <li key={tech}>{tech}</li>
          ))}
        </ul>
      </section>

      <section className="home-section" aria-labelledby="demo-title">
        <h2 id="demo-title" className="home-section__title">
          시연 순서
        </h2>
        <p className="home-section__lead">위에서부터 차례로 눌러 보세요. 단계마다 일부러 내 볼 만한 에러를 적어 두었어요.</p>
        <ol className="demo-steps">
          {DEMO_STEPS.map((step, index) => (
            <li key={step.title}>
              <DemoStepRow step={step} number={index + 1} />
            </li>
          ))}
        </ol>
      </section>

      <section id="progress" className="home-section" aria-labelledby="progress-title">
        <h2 id="progress-title" className="home-section__title">
          구현 현황
        </h2>
        <p className="home-section__lead">MVP 범위 중 어디까지 만들었는지예요. 설계 문서의 작업 목록과 테스트를 기준으로 적었어요.</p>
        <table className="progress-table">
          <thead>
            <tr>
              <th scope="col">기능</th>
              <th scope="col">상태</th>
              <th scope="col">보장하는 것</th>
            </tr>
          </thead>
          <tbody>
            {PROGRESS.map((item) => (
              <tr key={item.area}>
                <th scope="row">{item.area}</th>
                <td>
                  <span className={`status status--${item.status}`}>{STATUS_LABEL[item.status]}</span>
                </td>
                <td>{item.detail}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </main>
  )
}

function DemoStepRow({ step, number }: { step: DemoStep; number: number }) {
  const body = (
    <>
      <span className="demo-step__number">{number}</span>
      <span className="demo-step__body">
        <span className="demo-step__title">
          {step.title}
          {step.to === null && <span className="status status--planned">준비 중</span>}
        </span>
        <span className="demo-step__summary">{step.summary}</span>
        <span className="demo-step__try">{step.tryThis}</span>
      </span>
    </>
  )

  if (step.to === null) {
    return <div className="demo-step demo-step--disabled">{body}</div>
  }
  return (
    <Link className="demo-step" to={step.to}>
      {body}
      <ChevronIcon />
    </Link>
  )
}

function ChevronIcon() {
  return (
    <svg className="demo-step__chevron" width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
      <path d="M8 5l5 5-5 5" />
    </svg>
  )
}
