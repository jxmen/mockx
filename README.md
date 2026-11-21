# mockx

모의 증권 주문 시스템입니다. 계좌·예수금·원장과 지정가 매수 주문·취소를 구현했고, 요청이 동시에 몰려도 돈이 틀리지 않는지를 테스트로 확인합니다.

- 기간: 2026-10-08 ~ 진행 중
- 백엔드: Kotlin, Spring Boot, JPA, MySQL, Flyway, Kotest, Testcontainers
- 시연용 프론트: React, Vite, TypeScript

백엔드는 직접 구현했고, 시연용 프론트와 테스트 코드 작성에는 Claude Code를 썼습니다. 역할 분담은 [CLAUDE.md](CLAUDE.md)에 있습니다.

![mockx 첫 화면](docs/images/landing.png)

회원가입 → 입금 → 종목 조회 → 매수 주문(hold) → 취소(release) 시연:

![mockx 시연](docs/images/demo.gif)

## 지키는 규칙

| 규칙 | 검증 |
|---|---|
| 예수금 = 원장 금액 합계 | 같은 계좌에 입금 100건 동시 (`DepositConcurrencyTest`) |
| 회원 가입과 계좌 개설은 함께 성공하거나 함께 롤백 | 계좌 개설을 일부러 실패시켜 회원도 남지 않는지 확인 (`SignupE2ETest`) |
| 남의 계좌는 없는 계좌와 같은 404 | `DepositE2ETest` |
| 주문에 묶인 금액(hold) ≤ 예수금 | 예수금 $100 계좌에 $3 매수 100건 동시 → 33건 접수, 67건 422 (`PlaceOrderConcurrencyTest`) |
| 같은 주문을 동시에 취소해도 hold는 한 번만 풀림 | 취소 10건 동시 → 1건 성공, 9건 409 (`CancelOrderConcurrencyTest`) |
| 같은 주문 요청은 한 번만 처리 | 같은 `Idempotency-Key`로 100건 동시 → 주문 1건 (`PlaceOrderIdempotencyConcurrencyTest`) |

## 설계 결정

- **계좌 행 비관적 락**: 한 계좌에 요청이 몰리는 상황이라 낙관적 락은 충돌과 재시도가 많다. 입금·주문·취소 모두 계좌 행부터 잠가서 락 순서도 하나로 맞췄다.
- **hold는 원장에 남기지 않음**: 원장에는 예수금이 실제로 바뀐 기록만 둔다. hold는 "접수 상태 주문 금액 합계"와 맞는지로 검증한다.
- **금액은 정수 센트**: API, DB, 도메인 모두 센트 단위 `Long`으로 다룬다.
- **단식부기로 시작**: 체결 때 체결 ID로 양쪽 기록을 연결하고, 복식부기 전환은 뒤로 미뤘다.

선택지와 고른 이유는 설계 문서에 정리했습니다.

| 문서 | 내용 |
|---|---|
| [member.md](docs/design/member.md) | 회원가입, 개발용 인증(`X-Member-Id`), 에러 응답(RFC 9457) |
| [account.md](docs/design/account.md) | 계좌, Luhn 계좌번호, 입금 |
| [ledger-single-vs-double-entry.md](docs/design/ledger-single-vs-double-entry.md) | 단식부기와 복식부기 비교 |
| [stock.md](docs/design/stock.md) | 종목, 금액 단위, 티커 정규화 |
| [order.md](docs/design/order.md) | 매수 주문 접수·취소, 동시성 제어, Idempotency-Key |

## 다음 단계

- 동시 매수 1,000건으로 락 방식(비관적 락, 낙관적 락 + 재시도, Redis 분산 락)별 TPS·실패율·p99 비교
- 매칭 엔진: 종목별 오더북, 가격·시간 우선, 부분 체결
- 부하 테스트와 모니터링 (k6, Prometheus, Grafana)

## 실행

```bash
./gradlew bootRun   # 백엔드 (MySQL 컨테이너 자동 기동)
./gradlew test      # 테스트 (Docker 필요)

cd frontend
npm install
npm run dev         # http://localhost:5173, 첫 화면에 시연 순서가 있음
npm test
```
