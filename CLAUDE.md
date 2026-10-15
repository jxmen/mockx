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

- 백엔드 API(OpenAPI 스펙)가 어느 정도 안정된 뒤 `frontend/` 폴더에 React + Vite + TypeScript로 구현한다.
- 프론트는 백엔드 기능을 시연하기 위한 용도다. 백엔드 API 계약에 맞춰 만들고, 필요한 API가 없으면 직접 만들지 말고 사용자에게 필요한 스펙을 제안한다.

## 기술 스택

- Kotlin 2.3 / Java 21 / Spring Boot 4.1 (webmvc, data-jpa)
- MySQL 8.4 — 로컬은 `compose.yaml` + `spring-boot-docker-compose`로 자동 기동
- 테스트: JUnit5 + Testcontainers(MySQL)
- Redis, Kafka는 MVP 이후 하나씩 도입 예정 (사용자는 Redis/Kafka 경험이 거의 없음)
- 패키지: `xyz.jxmen.mockx`, 배포 예정 도메인: `mockx.jxmen.xyz`

## MVP 범위

- 계좌·예수금·원장(ledger)
- 지정가 주문 + 주문 상태 머신
- 예수금 hold / release
- 종목별 인메모리 매칭 엔진 (내부 매칭 방식의 모의 거래소, 봇 주문 스크립트로 유동성 공급 — 확정 전)
- Idempotency-Key 기반 중복 주문 방지
- 동시 매수 1,000건 동시성 테스트

## 명령어

```bash
./gradlew bootRun   # 로컬 실행 (MySQL 컨테이너 자동 기동)
./gradlew test      # 테스트 (Testcontainers 사용, Docker 필요)
./gradlew build
```

## 컨벤션

- 답변, 코드 주석, 커밋 메시지는 한국어로 작성한다.
- 테스트 메서드명은 백틱 한국어 문장으로 쓴다. (예: `` `hello 요청 시 인사 메시지를 반환한다` ``)
- 서비스 클래스는 유스케이스 하나당 하나씩 만들고 `{동작}Service`로 이름 짓는다. (예: `SignupService`, `PlaceOrderService`) 포트·어댑터는 나누지 않으므로 `UseCase` 접미사는 쓰지 않는다.
- 사용자가 직접 호출하지 않고 다른 서비스가 쓰는 부품은 `-er` 형태로 짓는다. (예: `AccountOpener`)
- 서비스 입력은 당분간 웹 요청 DTO를 그대로 받는다. 진입점이 여러 개가 되면 `{동작}Command`로 분리한다.
- 에러 응답(4xx, 5xx)은 RFC 9457 Problem Details 형식을 쓰고, 확장 필드 `code`는 대문자 스네이크 케이스로 쓴다. (예: `INVALID_INPUT`, `USERNAME_DUPLICATED`)
