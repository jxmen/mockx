# mockx

핀테크(증권) 백엔드 취업용 포트폴리오 MVP. 모의 증권 주문·체결 시스템(Mock Exchange)을 1~2주 안에 만들고 점진적으로 확장한다.

## 협업 원칙 (가장 중요)

### 백엔드 — 사용자가 직접 구현, Claude는 힌트만

학습과 면접 대비(꼬리 질문 대응)가 목적이므로 백엔드 코드는 사용자가 직접 작성한다.

- 완성 코드를 주지 않는다. 개념 설명, 고려할 엣지 케이스, 찾아볼 키워드·API 이름, 설계 리뷰, 테스트 시나리오 제안 위주로 답한다.
- 코드는 시그니처·의사코드 수준까지만 보여준다. 사용자가 명시적으로 코드를 요청할 때만 구현 코드를 준다.
- `src/main/kotlin` 아래 파일을 Claude가 임의로 수정하지 않는다.
- 사용자가 짠 코드 리뷰는 적극적으로 하되, 수정본을 통째로 주지 말고 문제 지점과 이유를 짚는다.
- 예외: 빌드 설정(`build.gradle.kts`), `compose.yaml`, `application.yml` 같은 보일러플레이트는 요청하면 바로 작성해도 된다.

### 테스트 — Claude가 작성, 케이스 선정은 사용자 검토

- 테스트 코드(`src/test`)는 Claude가 작성해도 된다. 기대값 계산, 파라미터 테스트 보일러플레이트, 테스트 실행을 맡는다.
- 어떤 케이스(경계값·엣지 케이스)를 테스트할지는 사용자가 정하거나, Claude가 목록을 제안하고 사용자가 검토한다. 케이스 설계는 면접 대비 영역이다.
- 흐름: 시나리오 합의 → Claude가 실패하는 테스트 작성 → 사용자가 `src/main` 구현으로 통과시킨다.
- 작성 후 테스트를 실행하고, 실패한 케이스는 원인(미구현인지, 우연히 통과했는지 등)과 함께 알린다.

### 프론트엔드 — Claude가 구현

- `frontend/` 폴더에 React + Vite + TypeScript로 구현한다. 현재 회원가입·내 정보·입금 화면이 있다. 실행 방법은 `frontend/README.md`를 따른다.
- 프론트는 백엔드 기능을 시연하기 위한 용도다. 백엔드 API 계약에 맞춰 만들고, 필요한 API가 없으면 직접 만들지 말고 사용자에게 필요한 스펙을 제안한다.

### CLAUDE.md·스킬 — 바뀌면 Claude가 알아서 갱신

- 대화 중에 이 파일에 적힌 내용(협업 원칙, 기술 스택, 명령어, 컨벤션, 패키지·테스트 구성 등)이 새로 정해지거나 바뀌면, 묻지 말고 바로 CLAUDE.md를 고친다. (예: 서비스 입력을 Command로 받기로 함, 테스트를 컨트롤러 단위·E2E로 나누기로 함, 의존성 추가)
- 같은 내용을 담은 `.claude/skills/*/SKILL.md`(예: `hint-review`의 컨벤션 체크리스트, `test-first`의 테스트 구분)도 함께 맞춘다. 한쪽만 바뀌면 리뷰·테스트 작성이 낡은 기준을 따른다.
- 고친 뒤에는 어느 파일의 어떤 줄을 바꿨는지 답변에 짧게 알린다.
- 결정 자체는 사용자가 한다. 사용자가 정하지 않은 것을 Claude가 컨벤션으로 적지 않는다.

## 기술 스택

- Kotlin 2.3 / Java 21 / Spring Boot 4.1 (webmvc, data-jpa)
- MySQL 8.4 — 로컬은 `compose.yaml` + `spring-boot-docker-compose`로 자동 기동
- 스키마: Flyway(`src/main/resources/db/migration`), Hibernate는 `ddl-auto: validate`
- 비밀번호: `spring-security-crypto`의 BCrypt만 쓴다. Spring Security 자동 설정은 켜지 않는다
- 테스트: Kotest 6(`DescribeSpec`, JUnit5 플랫폼에서 실행) + Testcontainers(MySQL), 목 객체는 mockk
- 프론트: React + Vite + TypeScript, 테스트는 Vitest
- Redis, Kafka는 MVP 이후 하나씩 도입 예정 (사용자는 Redis/Kafka 경험이 거의 없음)
- 패키지: `xyz.jxmen.mockx`, 배포 예정 도메인: `mockx.jxmen.xyz`

## MVP 범위

- 시장은 미국 상장 종목(NASDAQ, NYSE Arca), 통화는 USD
- 계좌·예수금·원장(ledger)
- 지정가 주문 + 주문 상태 머신
- 예수금 hold / release
- 종목별 인메모리 매칭 엔진 (내부 매칭 방식의 모의 거래소, 봇 주문 스크립트로 유동성 공급 — 확정 전)
- Idempotency-Key 기반 중복 주문 방지
- 동시 매수 1,000건 동시성 테스트

## 설계 문서

`docs/`의 문서가 구현과 테스트 기대값의 기준이다. 문서와 코드가 어긋나면 임의로 한쪽에 맞추지 말고 사용자에게 묻는다.

- `db-design-member.md`: 회원가입, 인증(`X-Member-Id`), 에러 응답, 테이블 공통 규칙
- `db-design-account.md`: 계좌, 계좌번호, 입금
- `ledger-single-vs-double-entry.md`: 원장 방식 결정(단식부기)과 `ledger` 테이블
- `db-design-stock.md`: 종목 (작성 중, 금액 단위 등 결정 보류 항목 있음)
- `db-design-order.md`: 매수 주문 접수·취소, hold·release (결정 보류 항목 있음)

## 명령어

```bash
./gradlew bootRun   # 로컬 실행 (MySQL 컨테이너 자동 기동)
./gradlew test      # 테스트 (Testcontainers 사용, Docker 필요)
./gradlew build

cd frontend && npm run dev    # 프론트 개발 서버 (http://localhost:5173, /api는 8080으로 프록시)
cd frontend && npm test       # 프론트 테스트 (Vitest)
```

## 컨벤션

- 답변, 코드 주석, 커밋 메시지는 한국어로 작성한다.
- 테스트는 Kotest `DescribeSpec`으로 쓰고, `describe`는 대상(API·메서드), `context`는 조건, `it`은 기대 결과를 한국어 문장으로 쓴다. (예: `context("남의 계좌면") { it("없는 계좌와 같은 404 ACCOUNT_NOT_FOUND를 응답한다") }`)
- 테스트는 검증 대상에 따라 나눈다. (예: `OrderControllerTest` / `PlaceOrderE2ETest`)
  - 컨트롤러 단위 테스트(`{이름}ControllerTest`): `@WebMvcTest` + 서비스는 mockk 빈(`@TestConfiguration`에서 `mockk()`). 입력 검증(범위·형식·누락·null), 인증 헤더(401), 경로 변수 형식, 요청 → Command 변환을 본다. "처리되지 않는다"는 `verify(exactly = 0)`로 확인한다.
  - E2E 테스트(`{동작}E2ETest`): Testcontainers MySQL로 전체를 띄운다. 정상 흐름, 돈·상태 정합성(예수금·hold·원장·주문), 서비스가 내는 4xx(404·409·422), 조회 순서, 소유권처럼 DB가 있어야 확인되는 것만 둔다.
  - 동시성 테스트(`{동작}ConcurrencyTest`): E2E와 같은 환경에서 동시 요청 후 불변식을 확인한다.
  - 도메인 단위 테스트(`{엔티티}Test`): Spring 없이 엔티티·값 객체의 규칙을 본다.
- `withData` 항목은 container로 등록돼 `beforeEach`가 돌지 않는다. 테이블·목 초기화가 필요하면 각 항목 첫 줄에서 직접 부른다.
- 패키지는 도메인별(`member`, `account`, `ledger`, `order`, `stock`)로 나누고, 그 안은 web/application/domain 같은 계층 하위 패키지 없이 평평하게 둔다. 여러 도메인이 함께 쓰는 것은 `common` 아래에 둔다. (예: `common.money.Money`, `common.web.CurrentMember`) 도메인 안에서 기능이 커지면 계층이 아니라 기능 단위로 뗀다. (예: `order/matching`)
- 서비스 클래스는 유스케이스 하나당 하나씩 만들고 `{동작}Service`로 이름 짓는다. (예: `SignupService`, `PlaceOrderService`) 포트·어댑터는 나누지 않으므로 `UseCase` 접미사는 쓰지 않는다.
- 사용자가 직접 호출하지 않고 다른 서비스가 쓰는 부품은 `-er` 형태로 짓는다. (예: `AccountOpener`)
- 서비스 입력은 기본적으로 웹 요청 DTO를 그대로 받는다. 다음 중 하나에 해당하면 `{동작}Command`로 받고, 서비스는 웹 응답 DTO 대신 도메인 결과를 돌려준다. 요청·응답 DTO 변환은 컨트롤러가 맡는다. (예: `PlaceOrderService`)
  - 진입점이 여러 개다 (예: 봇 주문 스크립트)
  - 입력 필드가 많고 같은 타입이 반복돼 순서를 바꿔 넘겨도 컴파일된다
  - 웹 형식 규칙(대소문자 정규화, 아직 지원하지 않는 값 거절)과 도메인 규칙이 갈린다
- 에러 응답(4xx, 5xx)은 RFC 9457 Problem Details 형식을 쓰고, 확장 필드 `code`는 대문자 스네이크 케이스로 쓴다. (예: `INVALID_INPUT`, `USERNAME_DUPLICATED`)
