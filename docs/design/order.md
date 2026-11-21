# 주문(Order) 설계

> 최종 수정: 2026-11-08. 테이블 공통 규칙(단수형 이름, `DATETIME(6)` UTC, 제약 이름, Flyway)은 [`member.md`](member.md)를 참고한다. 예수금·hold는 [`account.md`](account.md), 원장은 [`ledger-single-vs-double-entry.md`](ledger-single-vs-double-entry.md), 종목은 [`stock.md`](stock.md)를 참고한다.

## 1. 범위

이번 단계는 **매수 주문 접수(예수금 hold), 매수 주문 취소(release), 내 주문 목록 조회, 접수의 중복 방지(Idempotency-Key)** 만 만든다. 주문은 체결되지 않고 "접수됨"으로 쌓인다.

| 이번에 한다 | 나중에 한다 |
|---|---|
| 지정가 매수 주문 접수 + 예수금 hold | 매칭 엔진, 체결, 보유 주식(잔고) |
| 매수 주문 취소 + release | 매도 주문, 부분체결, 정정 |
| 내 주문 목록 조회 | Idempotency-Key 저장소를 Redis·DB로 이전 (15장) |
| 주문 상태 2개(접수됨, 취소됨) | |
| 매수 주문 접수의 Idempotency-Key 중복 방지 (15장) | |

- 체결이 없으므로 부분체결도 없다. 취소하면 hold한 금액을 전부 푼다.
- 체결까지 한 번에 넣지 않은 이유: 매칭 엔진은 그 자체로 개념이 많다(가격·시간 우선, 부분체결, 체결가와 지정가 차액). 접수·취소만으로도 hold 정합성과 동시 매수 테스트를 먼저 검증할 수 있다.

## 2. 정책

| 항목 | 결정 |
|---|---|
| 주문 종류 | 지정가 매수만 받는다 |
| 가격 | 정수 센트([`stock.md`](stock.md) 4.1). 센트로 저장하므로 $1 이상 종목의 호가 단위($0.01)는 따로 검사하지 않아도 지켜진다 |
| 수량 | 정수 주. 소수점 주식은 받지 않는다 |
| 종목 확인 | 존재 여부만 확인한다. 현재가가 없으므로 가격이 시세와 동떨어져도 막지 않는다([`stock.md`](stock.md) 2장) |
| 종목 식별 | 요청에는 티커(`symbol`)를 받고, 주문에는 `stock.id`를 저장한다(티커가 바뀌어도 주문이 깨지지 않게) |
| hold 시점·금액 | 접수 시점에 `지정가 × 수량`을 hold한다 |
| 원장 | hold·release는 원장에 남기지 않는다(3장) |
| 테이블·클래스 이름 | 테이블은 `orders`, 엔티티 클래스는 `Order`(`order` 패키지). `order`가 MySQL 예약어라 테이블만 복수형으로 쓴다([`member.md`](member.md) 2장의 예외) |

## 3. hold·release를 원장에 남기는가

### 상황

원장은 **예수금(`balance`)이 바뀐 기록**이고, 이 불변식을 검증한다([`ledger-single-vs-double-entry.md`](ledger-single-vs-double-entry.md) 8장).

```
예수금 = 원장 amount 합계 = 마지막 balance_after
```

hold와 release는 `balance`가 아니라 `hold_amount`를 바꾼다.

```
                      balance     hold_amount   원장
입금 $5,000.00        500000      0             DEPOSIT +500000
MU 10주 $187.43 접수  500000      187430        ?
주문 취소             500000      0             ?
```

### 선택지

| 선택지 | 방식 | 장점 | 단점 |
|---|---|---|---|
| **A. 원장에 안 남김 (선택)** | hold 이력은 주문 테이블의 상태로 추적한다 | 원장 불변식이 그대로다. 원장 = "돈이 실제로 움직인 기록"으로 의미가 하나다 | 주문 상태는 UPDATE라 "언제 취소됐는지"는 컬럼으로 따로 남겨야 한다 |
| B. 원장에 `HOLD`/`RELEASE` 타입 추가 | 계좌 변동을 모두 원장 한 곳에 남긴다 | 계좌에 일어난 일이 한 테이블에 모인다 | `amount`에 0을 적으면 의미 없는 줄, hold 금액을 적으면 불변식이 깨진다. `balance_after`의 의미가 타입마다 달라진다 |
| C. 별도 hold 이력 테이블 | hold 변동을 append-only로 따로 남긴다 | hold 변동도 고치지 않는 기록으로 남는다 | 테이블이 하나 늘어난다. MVP에는 과하다 |

### mockx의 선택

- A. hold·release는 원장에 남기지 않는다. 원장에 처음 기록이 생기는 것은 **체결** 시점이다(실제 돈이 빠져나가는 때, 체결 ID와 함께).
- 대신 주문 테이블로 hold를 검증한다. 원장 불변식과 짝을 이루는 불변식이다.
  ```
  account.hold_amount = 그 계좌의 ACCEPTED 매수 주문들의 (price × quantity) 합계
  ```
- 포기한 것: hold 변동의 시간순 이력. 주문별 접수·취소 시각으로만 재구성한다.
- 바꿀 조건: 체결·부분체결이 들어와 한 주문의 hold가 여러 번 바뀌게 되면, 위 불변식 계산식과 C(hold 이력)를 다시 검토한다.

append-only와 취소는 부딪히지 않는다. append-only는 지난 기록을 고치거나 지우지 않는다는 뜻이고, 되돌릴 때는 반대 방향 기록을 하나 더 붙인다(역분개). 주문 취소는 예수금이 움직인 적이 없어서 되돌릴 원장 기록이 애초에 없다.

## 4. 주문 상태

```
            접수 (hold)
  (요청) ──────────────▶ ACCEPTED ───────────▶ CANCELED
                                  취소 (release)

  나중에 추가: ACCEPTED ─▶ PARTIALLY_FILLED ─▶ FILLED (체결 작업 시)
```

| 현재 상태 | 취소 요청 |
|---|---|
| `ACCEPTED` | 성공. `CANCELED`로 바꾸고 hold를 푼다 |
| `CANCELED` | 실패. `409 ORDER_NOT_CANCELABLE` (10장) |

- 상태 이름은 `ACCEPTED`, `CANCELED`로 한다(`OrderStatus`). 증권사·거래소마다 이름이 다르다(접수/확인/미체결 등).
- 상태는 `VARCHAR` + `EnumType.STRING`으로 저장한다. 이유는 `account.type`과 같다([`account.md`](account.md) 2장).

## 5. 테이블

### orders

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGINT` | PK, auto increment |
| `account_id` | `BIGINT` | NOT NULL, `idx_orders_account_id` |
| `stock_id` | `BIGINT` | NOT NULL |
| `side` | `VARCHAR(10)` | NOT NULL (`OrderSide` enum 이름. 지금은 `BUY`만) |
| `price` | `BIGINT` | NOT NULL (지정가, 센트) |
| `quantity` | `BIGINT` | NOT NULL (주문 수량, 주) |
| `status` | `VARCHAR(20)` | NOT NULL (`ACCEPTED`, `CANCELED`) |
| `canceled_at` | `DATETIME(6)` | NULL (취소 시각. 취소되지 않았으면 NULL) |
| `created_at` | `DATETIME(6)` | NOT NULL (접수 시각) |
| `updated_at` | `DATETIME(6)` | NOT NULL |

- hold 금액은 저장하지 않고 `price × quantity`로 계산한다. 체결이 없는 동안은 둘이 항상 같다.
- `side`는 매수(`BUY`)·매도(`SELL`) 구분이다. 지금은 `BUY`뿐이지만 미리 둔다. 매도를 추가할 때 기존 행을 채우는 마이그레이션이 필요 없고, 목록 응답에 매수/매도를 표시할 수 있다.
- `canceled_at`을 따로 둔다. `updated_at`은 "마지막으로 바뀐 시각"이라 나중에 다른 변경이 생기면 취소 시각을 잃는다. hold 이력을 원장 대신 주문 테이블로 추적하기로 한 대가(3장)를 이 컬럼으로 메운다.
- `account_id`, `stock_id`에 FK 제약을 걸지 않는다. 기존 `ledger.account_id`와 같다.
  - 참조 무결성은 애플리케이션이 지킨다. 주문은 계좌(락)와 종목을 조회한 뒤에만 저장한다(6장).
  - FK가 없으면 InnoDB가 인덱스를 자동으로 만들지 않으므로, 인덱스는 그 컬럼으로 찾는 쿼리가 있을 때만 따로 만든다.
- `account_id` 인덱스는 내 주문 목록 조회(8장)가 쓴다.
- `stock_id`에는 인덱스를 두지 않는다. 지금은 `stock_id`로 주문을 찾는 쿼리가 없다. 목록에 티커를 붙일 때는 `stock`의 PK로 찾는다. 인덱스는 주문 INSERT마다 갱신 비용이 든다.
  - 체결이 붙어 "이 종목의 접수 상태 주문"을 찾는 쿼리가 생기면 그때 쿼리에 맞춰 추가한다. `(stock_id, status)` 같은 복합 인덱스가 될 수 있다.

## 6. 매수 주문 접수

### 요청

`POST /api/accounts/{accountId}/orders` (`X-Member-Id`, `Idempotency-Key` 헤더 필요)

계좌는 경로로, 나머지는 JSON 본문으로 받는다. `Idempotency-Key` 헤더의 규칙과 처리는 15장에 있다.

| 필드 | 규칙 |
|---|---|
| `symbol` | 영문 1~10자, 대문자로 정규화한다. 종목 상세 API와 같은 규칙이다([`stock.md`](stock.md) 4.6) |
| `side` | 필수. 대소문자 구분 없이 받아 대문자로 정규화한다. 지금은 `BUY`만 허용하고, `SELL`도 400이다 |
| `price` | 정수 센트, 1($0.01) 이상 1,000,000($10,000) 이하 |
| `quantity` | 정수, 1 이상 10,000 이하 |

- `side`를 지금부터 필수로 받는다. 매도를 추가할 때 필수 필드를 새로 넣으면 기존 클라이언트가 깨진다. 생략하면 `BUY`로 보는 기본값도 두지 않는다. 매도할 생각으로 보낸 요청이 `side`를 빠뜨려 매수로 접수될 수 있다(12장 9번).
- `SELL`은 `OrderSide` enum에 있지만 아직 처리할 흐름이 없으므로 다른 잘못된 값과 똑같이 400 `INVALID_INPUT`이다. 매도를 추가하면 허용 값이 늘어날 뿐이라 기존 계약이 깨지지 않는다.
- 상한이 있으므로 hold 금액은 최대 1,000,000 × 10,000 = 10,000,000,000센트($100,000,000)다. `Long` 범위(약 9.2 × 10^18)를 넘지 않아 곱셈 오버플로가 생기지 않는다. 상한이 없으면 곱이 넘쳐 작은 양수로 돌아올 수 있다(예: (2^62 + 1) × 4 → 4). 그러면 4센트만 hold하고 주문이 접수된다.

### 응답 (제안)

```http
HTTP/1.1 201 Created
Content-Type: application/json

{ "orderId": 1, "symbol": "MU", "side": "BUY", "price": 18743, "quantity": 10, "status": "ACCEPTED" }
```

### 처리 흐름

```
① 입력 검사 (symbol 형식, side = BUY, price·quantity 범위) → 400 INVALID_INPUT
② 종목 조회                                             → 404 STOCK_NOT_FOUND
③ 내 계좌 조회 + 계좌 행 락                               → 404 ACCOUNT_NOT_FOUND
④ hold (price × quantity ≤ 주문가능금액)                  → 422 INSUFFICIENT_AVAILABLE_AMOUNT
⑤ 주문 저장 (ACCEPTED)
   ③~⑤는 한 트랜잭션
```

- `Idempotency-Key` 처리는 ①과 ② 사이에서 키를 등록하고, ⑤가 커밋된 뒤에 결과를 저장한다. 위 트랜잭션 바깥이다(15장).

- hold와 주문 저장은 한 트랜잭션이다. 하나만 반영되면 돈이 묶인 채 주문이 없거나, 주문은 있는데 돈이 안 묶인다.
- 주문가능금액은 `balance − hold_amount`다(`Account.availableAmount()`).
- 주문가능금액 부족(④)은 `Account.hold()`가 `InsufficientAvailableAmountException`(422)을 던진다. 서비스가 미리 비교하지 않는다. 규칙이 엔티티 한 곳에만 있어서 다른 진입점(봇)도 같은 응답을 받는다. 금액이 0 이하인 경우는 호출자의 실수라 `require`로 남긴다.
- 종목 조회(②)를 계좌 락(③)보다 먼저 하면, 없는 종목 요청 때문에 계좌 락을 잡지 않는다.
- 동시성은 계좌 행 비관적 락(③)으로 제어한다. 입금과 같은 `findWithLockByIdAndMemberId`를 쓴다. 같은 계좌에 들어온 매수는 락을 얻은 순서대로 하나씩 처리된다. 이유는 9장.

### 서비스 입력·출력

- `order.PlaceOrderService`는 웹 요청 DTO(`PlaceOrderRequest`)가 아니라 `PlaceOrderCommand`를 받고, 웹 응답 DTO 대신 도메인 결과를 돌려준다. 요청 → Command, 결과 → 응답 변환은 컨트롤러가 맡는다. 회원가입·입금은 지금처럼 DTO를 그대로 받는다([`member.md`](member.md) 4장).
- 이유:
  1. `Long` 입력이 넷(`accountId`, `memberId`, `price`, `quantity`)이다. 파라미터로 늘어놓으면 순서를 바꿔 넘겨도 컴파일된다.
  2. `side` 대소문자 정규화, "지금은 `BUY`만"은 요청 형식 규칙이고 hold는 도메인 규칙이다. 서비스는 정규화·검사를 마친 `OrderSide`만 받는다.
  3. 봇 주문 스크립트(유동성 공급)가 붙으면 웹이 아닌 진입점이 생긴다.
  4. 응답 형태가 바뀌어도 서비스와 `PlacedOrder`는 그대로다. 결과 → 응답 변환은 `PlaceOrderResponse.from`이 맡고, 변환 메서드는 항상 바깥 타입(Command, 결과, 응답 DTO)이 안쪽 타입을 아는 쪽에 둔다(`PlacedOrder.of(order, symbol)`, `Order`에는 두지 않는다).
- 패키지: 주문의 웹 계층(`OrderController`, `PlaceOrderRequest`, `PlaceOrderResponse`)도 `order` 패키지에 둔다. URL은 `/api/accounts/{accountId}/orders`지만 컨트롤러는 `AccountController`와 나눈다. 그래서 `order` → `account`(계좌 조회, 예외) 한 방향만 있고 `account`는 주문을 모른다. `Money`는 `account`·`ledger`·`order`가 함께 쓰므로 `common.money`에 둔다.
- 응답의 `symbol`은 `Order`에 없다(`stock_id`만 있다). 결과 타입은 이 값을 함께 담아야 한다. 구체적인 모양은 구현하며 정한다.

## 7. 매수 주문 취소

### 요청

`POST /api/accounts/{accountId}/orders/{orderId}/cancel` (`X-Member-Id` 헤더 필요). 본문은 없다.

- 취소는 행을 지우지 않는 상태 전이라 `DELETE`가 아니라 `POST .../cancel`로 한다.

### 처리 흐름

```
① 내 계좌 조회 + 계좌 행 락 (accountId + memberId)         → 404 ACCOUNT_NOT_FOUND
② 그 계좌의 주문 조회                                      → 404 ORDER_NOT_FOUND
③ 상태 확인: ACCEPTED가 아니면                             → 409 ORDER_NOT_CANCELABLE
④ release (price × quantity)
⑤ 상태를 CANCELED로 바꾸고 canceled_at 기록
   ①~⑤는 한 트랜잭션
```

- 남의 주문은 없는 주문과 같은 404다. 계좌와 같은 방식이다([`member.md`](member.md) 5장).
- 계좌 락(①)을 먼저 잡고, 주문은 락을 잡은 뒤에 읽는다(②). 입금·매수 접수와 같은 `findWithLockByIdAndMemberId`를 쓴다.
- **같은 주문에 취소 요청이 동시에 두 번 오면** 두 번째 요청은 ①에서 기다린다. 첫 번째가 커밋한 뒤 주문을 읽으므로 `CANCELED`를 보고 409로 끝난다.
- ① 전에 주문을 읽으면 안 된다. 락을 잡은 뒤 다시 읽어도 예전 값이 보일 수 있다(9장 "락을 잡은 뒤에 읽는 값").
- 없는 주문을 취소하는 요청도 계좌 락을 잡는다. 매수 접수는 종목 조회를 락보다 먼저 하지만(6장), 취소는 상태 확인을 락 안에서 해야 해서 순서를 바꿀 수 없다.
- 취소된 주문은 지우지 않는다. 상태만 바꾼다.

## 8. 내 주문 목록 조회

프론트에서 취소를 시연하려면 취소할 주문을 고를 목록이 필요해서 이번 범위에 넣는다.

### 요청

`GET /api/accounts/{accountId}/orders` (`X-Member-Id` 헤더 필요)

쿼리 파라미터는 모두 선택이다. 값 여러 개는 쉼표로 잇는다.

| 파라미터 | 허용 값 | 예 |
|---|---|---|
| `side` | `BUY`, `SELL` | `?side=BUY` |
| `status` | `ACCEPTED`, `CANCELED` | `?status=ACCEPTED,CANCELED` |

- 파라미터가 없으면 그 조건으로 거르지 않는다.
- 같은 파라미터 안의 값들은 OR, `side`와 `status`는 AND로 합친다. `?side=BUY&status=CANCELED`는 취소된 매수 주문만 돌려준다.
- `SELL`은 아직 접수할 수 없지만 조회 조건으로는 허용한다. 매도를 추가해도 계약이 바뀌지 않는다. 지금은 빈 배열이 나온다.
- 값은 enum 이름과 대소문자까지 같아야 한다. 조회 필터는 Spring 기본 enum 변환(대소문자 구분)을 따른다. 본문을 Jackson으로 읽는 주문 접수(`buy` → `BUY` 정규화, 6장)와 다르다.

| 입력 | 응답 | 이유 |
|---|---|---|
| 허용하지 않는 값 (`?status=FILLED`, `?side=foo`) | 400 `INVALID_INPUT` | 무시하고 전체를 돌려주면 오타 난 필터가 "필터 없음"처럼 동작해 버그가 숨는다 |
| 대소문자가 다른 값 (`?side=buy`, `?status=Accepted`) | 400 `INVALID_INPUT` | 위와 같다 |
| 빈 값 (`?status=`, `?side=`, `?status=ACCEPTED,`) | 400 `INVALID_INPUT` | "아무것도 고르지 않음"이 "전체"와 같은 뜻이 되지 않게 한다 |

### 응답

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "orders": [
    {
      "orderId": 2, "symbol": "SPY", "stockName": "SPDR S&P 500 ETF Trust", "side": "BUY",
      "price": 60012, "quantity": 1, "status": "CANCELED",
      "createdAt": "2026-10-24T03:12:45.123456Z", "canceledAt": "2026-10-24T03:15:20.987654Z"
    },
    {
      "orderId": 1, "symbol": "MU", "stockName": "Micron Technology", "side": "BUY",
      "price": 18743, "quantity": 10, "status": "ACCEPTED",
      "createdAt": "2026-10-24T03:10:02.654321Z", "canceledAt": null
    }
  ]
}
```

| 항목 | 결정 |
|---|---|
| 응답 형태 | 종목 목록(`{ "stocks": [...] }`)처럼 배열을 객체로 감싼다([`stock.md`](stock.md) 4.5). 나중에 페이지네이션 정보를 계약을 깨지 않고 추가할 수 있다 |
| 필드 | `orderId`, `symbol`, `stockName`, `side`, `price`(정수 센트), `quantity`, `status`, `createdAt`, `canceledAt` |
| `canceledAt` | 취소되지 않았으면 `null`. 필드를 빼지 않고 `null`로 응답한다 |
| 대상 | 그 계좌의 주문. 필터가 없으면 모든 상태(`ACCEPTED`, `CANCELED`) |
| 정렬 | 최신순(`id` 내림차순) |
| 페이지네이션 | 없음. 주문이 많아지면 추가한다(11장) |
| 주문이 없을 때 | 200, `orders`는 빈 배열 |
| 남의 계좌, 없는 계좌 | 404 `ACCOUNT_NOT_FOUND` |

- 주문에는 `stock_id`만 있으므로 `symbol`·`stockName`을 응답하려면 종목 정보를 함께 읽어야 한다. 주문마다 종목을 따로 조회하면 주문 수만큼 쿼리가 나간다(N+1).
- 그래서 주문과 종목을 한 쿼리로 조인해서 읽는다(JPQL `SELECT NEW ... FROM Order o JOIN Stock s ON o.stockId = s.id`). 엔티티 사이에 연관관계 매핑이 없으므로 `ON`으로 조인한다. `GetOrdersE2ETest`가 주문이 1건일 때와 종목이 다른 주문 5건일 때 쿼리 수가 같은지 확인한다.

## 9. 동시성 제어

### 상황

예수금 $100.00(10000센트), hold 0인 계좌에 $80.00(8000센트) 매수 두 건이 동시에 들어온다. 락이 없으면 이렇게 된다.

```
                        요청 A                    요청 B
① 계좌 읽기             주문가능 10000            주문가능 10000
② 금액 확인             8000 ≤ 10000 → 통과       8000 ≤ 10000 → 통과
③ hold + 주문 저장      hold_amount = 8000        hold_amount = 8000
④ 커밋
─────────────────────────────────────────────────────────────────
결과: ACCEPTED 주문 2건(합계 16000), hold_amount 8000
```

- B가 A의 `hold_amount`를 덮어써서 hold 하나가 사라진다(lost update). 3장 불변식이 깨진다.
- 주문은 새 행 INSERT라 주문끼리는 같은 행을 두고 다투지 않는다. 다투는 행은 **계좌**(`hold_amount`)다. "계좌를 읽고 → 확인하고 → 고친다"는 흐름이 겹치는 게 문제다.
- 같은 주문을 동시에 두 번 취소하는 경우도 구조가 같다. 둘 다 `ACCEPTED`를 보고 release를 두 번 하면, 같은 계좌의 다른 주문 hold까지 풀린다.

### 선택지

| | A. 계좌 행 비관적 락 (선택) | B. 낙관적 락 (`account.version`) |
|---|---|---|
| 방식 | 계좌를 `SELECT ... FOR UPDATE`로 읽는다. 다른 트랜잭션은 커밋될 때까지 기다린다 | `UPDATE ... WHERE id = ? AND version = ?`로 쓴다. 0행이면 충돌 예외, 트랜잭션 롤백 |
| 위 예시에서 B | A가 커밋한 뒤 hold 8000을 읽는다 → `422 INSUFFICIENT_AVAILABLE_AMOUNT` | 충돌 예외. 재시도하거나 실패 응답을 준다 |
| 같은 계좌에 요청이 몰리면 | 줄을 서서 하나씩 처리된다 | 대부분 충돌로 실패하고 재시도가 몰린다 |
| 충돌이 드물면 | 락을 잡는 비용만 든다 | 기다림 없이 끝나 가볍다 |
| 추가 작업 | 없다. 입금이 이미 쓰는 조회를 그대로 쓴다 | `version` 컬럼 마이그레이션, 충돌 예외 처리·재시도, 충돌 응답 `code` |

- B에서 충돌로 롤백되면 그 트랜잭션의 주문 INSERT도 함께 사라진다. "주문은 있는데 hold는 안 된" 상태는 어느 쪽이든 남지 않는다.

### mockx의 선택

- A. 매수 접수·취소 모두 입금과 같은 계좌 행 비관적 락을 쓴다. `version` 컬럼은 추가하지 않는다.
- 이유:
  - MVP에서 검증할 상황이 "같은 계좌에 동시 매수 1,000건"이다. 한 행에 요청이 몰리는 상황이라, 낙관적 락이면 대부분 충돌 예외로 끝나고 재시도 로직을 따로 만들어야 한다.
  - 기다린 요청은 최신 hold를 보고 판단하므로, 사용자는 "주문가능금액 부족(422)"처럼 뜻이 분명한 응답을 받는다. 낙관적 락의 충돌은 "다시 시도하세요" 같은 응답을 따로 정해야 한다.
  - 계좌 잔액·hold를 바꾸는 트랜잭션(입금, 매수 접수, 매수 취소)이 모두 같은 방식으로 같은 계좌 행을 잠근다.
- 포기한 것: 같은 계좌의 주문은 한 번에 하나씩만 처리된다. 기다리는 요청도 DB 커넥션을 쥐고 있으므로, 한 계좌에 요청이 아주 많으면 대기 시간이 길어지고 커넥션 풀이 모자랄 수 있다. 기다리다 `innodb_lock_wait_timeout`(기본 50초)을 넘으면 그 요청은 실패한다.
- 바꿀 조건: 한 계좌의 락 대기가 병목으로 측정되거나, 체결이 붙어 계좌를 고치는 일이 크게 늘면 다시 본다. 그때 검토할 대안은 조건부 원자적 UPDATE(`hold_amount = hold_amount + ?`를 주문가능금액 조건과 함께 한 문장으로 실행)다. 계좌번호 순번 발급이 이미 이 방식을 쓴다([`account.md`](account.md) 3장).

### 락을 잡은 뒤에 읽는 값

취소의 상태 확인(7장 ③)은 락을 잡은 뒤의 **최신 값**으로 해야 한다. 락은 다른 트랜잭션이 못 바꾸게 막을 뿐, 락 전에 읽어 둔 값을 새로 고쳐 주지 않는다.

주문을 락보다 먼저 읽는 흐름(주문 조회 → 계좌 락 → 상태 확인)이면 이렇게 된다. hold $300, `ACCEPTED` 주문 3건(각 $100), 주문 #1에 취소 T1·T2가 동시에 온다.

| 시점 | T1 (취소 #1) | T2 (취소 #1) | DB |
|---|---|---|---|
| ① | 주문 #1 조회 → `ACCEPTED` | | hold $300 |
| ② | | 주문 #1 조회 → `ACCEPTED` | |
| ③ | 계좌 락 획득 | 계좌 락 요청 → 대기 | |
| ④ | release $100, `CANCELED`, 커밋 | | hold $200 |
| ⑤ | | 계좌 락 획득, ②의 `ACCEPTED`로 확인 → 통과 | |
| ⑥ | | release $100, 커밋 | **hold $100** |

남은 `ACCEPTED` 주문은 $200인데 hold는 $100이다. ⑤에서 주문을 다시 조회해도 예전 값이 보일 수 있다. 이유는 두 가지다.

1. **JPA 영속성 컨텍스트**: 같은 트랜잭션에서 이미 읽은 엔티티는 다시 조회해도 DB를 다시 읽지 않고 처음 읽은 객체를 돌려준다.
2. **MySQL 8.4 기본 격리수준 REPEATABLE READ**: 일반 `SELECT`는 트랜잭션에서 처음 일반 `SELECT`를 한 시점의 스냅샷을 읽는다. 락을 기다리는 동안 다른 트랜잭션이 커밋한 취소가 보이지 않는다. `FOR UPDATE` 같은 잠금 읽기는 최신 값을 읽는다.

그래서 계좌 락을 먼저 잡고, 그 안에서 주문을 읽는다(12장 8번). 이 트랜잭션의 첫 조회가 계좌 잠금 읽기라서, 주문 조회는 앞 트랜잭션이 커밋한 뒤에 처음 DB로 나가고 그때 스냅샷이 만들어진다. 동시 취소 테스트(`CancelOrderConcurrencyTest`)로 확인했다.

- 지켜야 할 조건: 취소 트랜잭션에서 계좌 락보다 먼저 일반 조회를 하지 않는다. 하나라도 있으면 그 시점에 스냅샷이 만들어져 위 표와 같은 문제가 다시 생긴다.
- 주문 행을 따로 잠그지 않는 이유: 계좌 행 락 하나로 같은 계좌의 hold를 바꾸는 작업이 모두 줄을 선다. 주문 행 락을 먼저 잡는 흐름(주문 → 계좌)을 만들면, 계좌 → 주문 순서로 잠그는 흐름과 섞여 데드락이 날 수 있다. hold를 바꾸는 트랜잭션은 모두 계좌 행부터 잠근다.

## 10. 에러 응답

| 상황 | 상태 코드 | code |
|---|---|---|
| `symbol` 형식 오류, `side` 누락·`BUY` 아님(`SELL` 포함), `price` 0 이하·1,000,000 초과, `quantity` 0 이하·10,000 초과, 누락·소수 | 400 | `INVALID_INPUT` |
| 경로의 `accountId`, `orderId`가 숫자 아님 | 400 | `INVALID_INPUT` |
| 목록 조회 필터(`side`, `status`)가 허용하지 않는 값·대소문자 다름·빈 값 (8장) | 400 | `INVALID_INPUT` |
| 주문 접수의 `Idempotency-Key` 헤더 없음, 빈 값, 255자 초과 (15장) | 400 | `INVALID_INPUT` |
| `X-Member-Id` 헤더 없음, 형식 오류 | 401 | `UNAUTHENTICATED` |
| 형식은 맞지만 없는 종목 | 404 | `STOCK_NOT_FOUND` |
| 계좌가 없거나 남의 계좌 | 404 | `ACCOUNT_NOT_FOUND` |
| 주문이 없거나 그 계좌의 주문이 아님 | 404 | `ORDER_NOT_FOUND` |
| 주문가능금액 부족 | 422 | `INSUFFICIENT_AVAILABLE_AMOUNT` |
| 이미 취소된 주문을 취소 | 409 | `ORDER_NOT_CANCELABLE` |
| 같은 `Idempotency-Key`의 앞 요청이 아직 처리 중 (15장) | 409 | `IDEMPOTENCY_KEY_IN_PROGRESS` |
| 같은 `Idempotency-Key`인데 요청 내용이 다름 (15장) | 422 | `IDEMPOTENCY_KEY_REUSED` |

## 11. 범위 밖

| 항목 | 이유 |
|---|---|
| 체결, 매칭 엔진, 보유 주식 | 다음 단계. 1장 |
| 매도 주문 | 보유 주식이 없으면 팔 것이 없다 |
| 정정(가격·수량 변경) | 실제로는 "취소 후 재주문"과 비슷하다. 취소가 먼저다 |
| 시장가 주문 | 현재가가 없다. 시장가는 체결 가격을 모르므로 hold 금액도 정할 수 없다 |
| 장 운영시간, 거래정지 | 모의 거래소는 항상 열려 있다 |
| 수수료 | 미국 주식 매수 수수료는 증권사마다 다르다. 넣으면 hold 금액에 수수료를 포함해야 한다 |
| 주문 목록 페이지네이션 | 주문이 많아지면 추가한다 |

## 12. 결정 보류

1. ~~API 경로~~ → 결정: 접수 `POST /api/accounts/{accountId}/orders`, 취소 `POST /api/accounts/{accountId}/orders/{orderId}/cancel`. 입금 API(`/api/accounts/{accountId}/deposit`)와 모양을 맞췄다. 취소는 행을 지우지 않으므로 `DELETE`를 쓰지 않는다.
   - `/buy`·`/sell`로 나누지 않는다. 주문은 접수 뒤에도 취소·목록 조회가 따라오는 리소스이고, 매수와 매도는 같은 `orders` 테이블·같은 id를 쓰며 `side` 하나만 다르다. 접수만 `/buy`로 두면 같은 주문을 만들 때는 `/buy`, 취소할 때는 `/orders/{orderId}`로 부르게 된다. 입금(`/deposit`)은 한 번 하면 끝나고 id로 다시 다루지 않는 동작이라 동사형을 썼다.
   - 단수 `/order`가 아니라 복수 `/orders`다. `/orders`는 주문 컬렉션이고 `POST`는 컬렉션에 하나를 추가한다는 뜻이다. `GET /orders`(목록), `/orders/{orderId}`(하나)와 이름이 같아지고, 기존 `/accounts`, `/stocks`, `/members`와도 맞는다.
2. ~~주문가능금액 부족 응답~~ → 결정: 422 `INSUFFICIENT_AVAILABLE_AMOUNT`. 요청 형식은 맞지만 지금 계좌 상태로는 처리할 수 없다는 뜻이고, 409(username 중복)와 섞이지 않는다.
3. ~~이미 취소된 주문 취소~~ → 결정: 409 `ORDER_NOT_CANCELABLE`. 나중에 "이미 체결된 주문 취소"도 같은 코드로 받을 수 있다.
4. ~~`price`·`quantity` 상한~~ → 결정: `price` 1,000,000센트($10,000), `quantity` 10,000주. 둘 다 상한이 있어 `price × quantity`가 넘치지 않는다.
5. ~~`side`, `canceled_at` 컬럼~~ → 결정: 둘 다 둔다(5장).
6. ~~동시성 제어~~ → 결정: 입금과 같은 계좌 행 비관적 락. `version` 컬럼은 두지 않는다(9장).
7. ~~내 주문 목록 조회 API~~ → 결정: 이번 범위에 넣는다. 응답 형태·정렬은 8장대로 한다.
8. ~~취소 흐름의 조회 순서~~ → 결정: 계좌 락을 먼저 잡고(입금처럼 `accountId` + `memberId`로), 그 안에서 주문을 읽는다(7장, 9장). 주문을 먼저 읽고 주문 행을 `FOR UPDATE`로 다시 읽는 방식은 락 순서가 둘이 되어 쓰지 않는다.
9. ~~요청 본문의 `side`~~ → 결정: 필수 필드로 받고 지금은 `BUY`만 허용한다. `SELL`·잘못된 값·누락은 400 `INVALID_INPUT`이다. 값은 `symbol`처럼 대문자로 정규화한다(`buy` → `BUY`). 별도 code(예: 422 `UNSUPPORTED_ORDER_SIDE`)는 두지 않는다. 매도를 추가하면 사라질 code라서다(6장).
10. ~~주문 접수 중복 방지~~ → 결정: `Idempotency-Key` 필수 헤더, 범위는 `(memberId, 키)`, 처리 중이면 409, 요청 내용이 다르면 422, 201만 저장, TTL 24시간 인메모리(Caffeine). 자세한 내용은 15장.
11. ~~409·422의 `code` 이름~~ → 결정: 409 `IDEMPOTENCY_KEY_IN_PROGRESS`, 422 `IDEMPOTENCY_KEY_REUSED`.
12. ~~키 처리 위치~~ → 결정: 별도 빈을 컨트롤러가 명시적으로 호출한다. 컨트롤러가 검증을 마치고 Command를 만든 뒤, 그 빈에 `memberId`·키·지문과 `PlaceOrderService` 호출을 넘긴다. 필터·인터셉터·AOP를 쓰지 않은 이유는 15장 "키 처리 위치".
13. ~~재응답의 모양~~ → 결정: 첫 응답과 같은 201·같은 본문에 `Idempotent-Replayed: true` 헤더를 붙인다. 처음 처리한 응답에는 붙이지 않는다. 클라이언트·테스트가 "새로 접수됨"과 "이미 접수된 것을 다시 받음"을 구분할 수 있다. 헤더 이름은 Stripe와 같다.
14. ~~요청 지문의 기준~~ → 결정: 정규화한 값(`accountId`, `symbol`, `side`, `price`, `quantity`)으로 만든다. `"aapl"`과 `"AAPL"`은 같은 주문이라서다. JSON 필드 순서·공백 차이에도 영향을 받지 않는다.
15. ~~지문의 형태~~ → 결정: 해싱하지 않는다. 정규화를 마친 `PlaceOrderCommand`를 그대로 저장하고 `equals`로 비교한다. 이를 위해 Command 변환 단계에서 `symbol`을 대문자로 정규화한다(15장 "저장 형식").
16. ~~캐시 키 형식~~ → 결정: `(memberId, 키)`를 담은 data class `IdempotencyKey`. 문자열로 이어붙이지 않는다.
17. ~~캐시 값의 모양~~ → 결정: 상태별 타입 `Pending(지문)` / `Completed(지문, 결과)`.
18. ~~저장할 결과~~ → 결정: 서비스가 돌려준 `PlacedOrder` 그대로. 상태 코드는 저장하지 않는다.

## 13. 테스트

시나리오는 `test-first`로 합의한 뒤 채운다. 반드시 들어갈 검증:

- 접수 후 `hold_amount`가 `price × quantity`만큼 늘고 `balance`와 원장은 그대로다
- 취소 후 `hold_amount`가 원래대로 돌아오고 `balance`와 원장은 그대로다
- 어느 시점이든 `hold_amount` = `ACCEPTED` 주문들의 `price × quantity` 합계 (3장 불변식)
- 같은 계좌에 동시 매수: 성공한 주문의 금액 합계 = `hold_amount` ≤ `balance`이고, 나머지는 422다 (9장)
- 같은 주문에 동시 취소 2건: 1건만 성공하고 나머지는 409다. hold는 한 번만 풀린다 (9장)
- 같은 `Idempotency-Key`로 다시 보내면 첫 응답과 같은 주문이 `Idempotent-Replayed: true` 헤더와 함께 돌아오고, 주문은 1건, `hold_amount`는 한 번만 늘어난다 (15장)
- 같은 `Idempotency-Key`로 동시에 보내면 주문은 1건만 생기고, 나머지는 409 또는 같은 주문 응답이다 (15장)
- 실패(422 등)한 키로 다시 보내면 처음부터 다시 처리된다 (15장)
- 다른 회원이 같은 키를 써도 서로 영향이 없다 (15장)

### Idempotency-Key 테스트

2026-11-08에 합의했다. 규칙 하나마다 테스트 하나를 두는 핵심 케이스부터 시작한다.

| 파일 | 시나리오 | 기대 결과 |
|---|---|---|
| `PlaceOrderIdempotencyE2ETest` | 같은 키·같은 내용을 다시 보냄 | 첫 응답과 같은 201 본문 + `Idempotent-Replayed: true`. 첫 응답에는 헤더 없음. 주문 1건, hold 1번, 예수금·원장 그대로 |
| | 같은 키로 표기만 다른 같은 주문(`symbol`·`side` 대소문자, 필드 순서) | 재응답 201, 같은 `orderId`, 주문 1건 (12장 14·15번) |
| | 같은 키로 가격만 다른 주문 | 422 `IDEMPOTENCY_KEY_REUSED`, 주문·hold는 첫 주문 그대로 |
| | 주문가능금액 부족(422)으로 실패한 키로 입금 후 다시 보냄 | 재응답 헤더 없이 201로 새로 접수 |
| | 다른 회원이 같은 키 사용 | 둘 다 재응답 헤더 없이 201, 계좌마다 주문·hold가 따로 생김 |
| `PlaceOrderIdempotencyConcurrencyTest` | 같은 키로 같은 주문 100건 동시 요청 | 새로 처리된 201은 1건, 나머지는 409 `IDEMPOTENCY_KEY_IN_PROGRESS` 또는 재응답 201. 모든 201의 `orderId`가 같음. 주문 1건, hold 1번 |

- 키는 요청마다 `newIdempotencyKey()`(UUIDv7)로 새로 만든다. 캐시는 테이블을 비워도 남고 `memberId`는 1부터 다시 시작해서, 고정 키를 쓰면 앞 테스트의 응답이 재응답으로 돌아온다.
- 재전송 테스트의 예수금은 주문을 두 번 받을 수 있게 채운다. 한 번만 받을 만큼이면 키 처리가 없어도 두 번째 요청이 422로 막혀 "주문 1건"이 우연히 통과한다. 동시성 테스트도 같은 이유로 100건을 모두 받을 만큼 채운다.
- 409와 재응답의 비율은 실행마다 다르므로, 동시성 테스트는 "새로 처리된 201이 1건"과 "주문 1건, hold 1번"으로 확인한다.

나중에 추가할 시나리오 (구현이 핵심 케이스를 통과한 뒤):

| 시나리오 | 기대 결과 |
|---|---|
| 주문가능금액을 다 쓴 뒤 같은 키로 재전송 | 422가 아니라 재응답 201 (키 확인이 hold보다 먼저) |
| 주문 취소 후 같은 키로 재전송 | 재응답 201, 본문 `status`는 첫 응답 그대로 `ACCEPTED`. 주문은 `CANCELED` 그대로 |
| 같은 키로 다른 계좌 경로 | 404가 아니라 422 `IDEMPOTENCY_KEY_REUSED` (지문에 `accountId`) |
| 같은 회원, 다른 키, 같은 내용 | 주문 2건, hold 2번 |
| 404 `STOCK_NOT_FOUND`로 실패한 키로 재전송 | 409가 아니라 다시 404 |
| 422로 실패한 키로 다른 내용 전송 | `REUSED`가 아니라 새 요청으로 처리돼 201 |
| 400(본문 오류) 뒤 같은 키로 올바른 본문 | 재응답 헤더 없이 201 (400은 키 등록 전에 끝남) |
| 앞 요청이 처리 중일 때 같은 키 (테스트가 계좌 행 락으로 첫 요청을 붙잡음) | 409 `IDEMPOTENCY_KEY_IN_PROGRESS`, 내용이 달라도 409 |
| 같은 키 동시 요청이 모두 금액 부족 | 201 없이 422·409만. 입금 후 같은 키 → 201 |
| 키 없음·빈 값·255자 초과 (컨트롤러 테스트) | 400 `INVALID_INPUT`, 서비스 미호출 |

## 14. 작업 목록

- [x] API 경로 (12장 1번)
- [x] 주문가능금액 부족 응답, 재취소 응답, 가격·수량 상한, `side`·`canceled_at`, 목록 조회 범위·응답 형태 (12장 2~5·7번)
- [x] 동시성 제어 방식: 계좌 행 비관적 락 (12장 6번)
- [x] 취소 흐름 조회 순서: 계좌 락 먼저 (12장 8번)
- [x] 요청 본문 `side`: 필수, 지금은 `BUY`만, 대문자 정규화 (12장 9번)
- [x] FK 제약 없음, `stock_id` 인덱스는 체결 때 쿼리에 맞춰 추가 (5장)
- [x] Flyway 마이그레이션: `orders` 테이블 (`V5__create_orders.sql`)
- [x] `Order` 엔티티: 매수 접수(`createBuy`), 취소 전이(`cancel`), hold 금액 계산(`holdAmount`) + 테스트(`OrderTest`)
- [x] 매수 주문 접수 API + E2E 테스트 (`PlaceOrderE2ETest`, 입력 검증은 `OrderControllerTest`)
- [x] 매수 주문 취소 API + E2E 테스트 (`CancelOrderE2ETest`, 입력 검증은 `CancelOrderControllerTest`)
- [x] 내 주문 목록 조회 API + E2E 테스트 (`GetOrdersE2ETest`, 입력 검증은 `GetOrdersControllerTest`)
- [x] 동시 매수 테스트 (같은 계좌에 동시 주문해도 `hold_amount ≤ balance`) (`PlaceOrderConcurrencyTest`)
- [x] 동시 취소 테스트 (같은 주문에 동시 취소해도 hold는 한 번만 풀림) (`CancelOrderConcurrencyTest`)
- [x] Idempotency-Key 정책: 필수, `(memberId, 키)`, 409·422, 201만 저장, TTL 24시간 (12장 10번)
- [x] 409·422 `code` 이름, 재응답 헤더, 지문 기준 (12장 11·13·14번)
- [x] 키 처리 위치: 컨트롤러가 호출하는 별도 빈 (12장 12번)
- [x] 저장 형식: 캐시 키, 지문, 캐시 값, 저장할 결과 (12장 15~18번)
- [x] 캐시 키 `common.idempotency.IdempotencyKey`
- [x] `PlaceOrderRequest.toCommand`에서 `symbol` 대문자 정규화 (지문 비교의 전제, 12장 15번)
- [x] Caffeine 의존성 추가
- [ ] 키 형식 검증 (없음·빈 값·255자 초과 → 400) + 컨트롤러 테스트
- [x] 멱등성 E2E 테스트 작성: 재전송, 표기만 다름, 다른 내용, 실패 후 재전송, 다른 회원 (`PlaceOrderIdempotencyE2ETest`, 13장)
- [x] 같은 키 동시 요청 테스트 작성 (`PlaceOrderIdempotencyConcurrencyTest`, 13장)
- [x] 키 등록·완료·삭제 구현 (`IdempotentExecutor`, 위 두 테스트 통과)
- [x] 주문 접수를 호출하는 기존 테스트 6개에 키 헤더 추가 (`PlaceOrderE2ETest`, `OrderControllerTest`, `PlaceOrderConcurrencyTest`, `CancelOrderE2ETest`, `CancelOrderConcurrencyTest`, `GetOrdersE2ETest`)
- [x] `OrderControllerTest` 컨텍스트에 멱등성 빈 등록 (`OrderServiceMockConfig`)
- [x] 프론트 주문 화면: 키 생성·재시도 시 재사용·확정 응답 후 폐기 (15장 "클라이언트")

## 15. 중복 주문 방지 (Idempotency-Key)

### 상황

매수 주문을 보냈는데 응답이 오다 끊겼다. 클라이언트는 주문이 접수됐는지 모른다.

```
클라이언트                          서버
  │ POST /orders (AAPL 10주) ─────▶ │ hold + 주문 저장, 커밋 ✅
  │        ✕ 응답이 끊김 ◀───────── │
  │ (타임아웃: 접수됐는지 모름)      │
  │ POST /orders (AAPL 10주) ─────▶ │ 새 주문으로 다시 접수 ❌
```

- 재시도하면 주문 2건, hold 2번이 된다. 재시도하지 않으면 주문이 안 됐을 수도 있다.
- 계좌 락(9장)으로는 막지 못한다. 두 번째 요청은 락 입장에서 정상적인 새 주문이다.
- 버튼 두 번 클릭, 봇 스크립트의 자동 재시도도 같은 문제다.

### 개념

클라이언트가 **주문 의도 하나**마다 키를 하나 만들어 헤더로 보낸다. 서버는 처리한 키와 결과를 기억해 두고, 같은 키가 다시 오면 처리하지 않고 저장한 결과를 돌려준다.

```
  │ POST /orders, Idempotency-Key: abc ─▶ │ 처리, (회원, abc) → 결과 저장
  │        ✕ 응답이 끊김 ◀─────────────── │
  │ POST /orders, Idempotency-Key: abc ─▶ │ 이미 처리한 키 → 저장한 결과를 돌려줌
  │ ◀────────────────── 201 (같은 주문) ── │ 주문 1건, hold 1번
```

키는 HTTP 요청 하나가 아니라 의도 하나를 가리킨다. 재시도할 때 새 키를 보내면 서버는 다른 주문으로 볼 수밖에 없다.

### 정책

| 항목 | 결정 |
|---|---|
| 적용 대상 | 매수 주문 접수만. 취소는 이미 멱등에 가깝다(두 번째 취소는 409로 끝나고 hold는 한 번만 풀린다, 7장) |
| 헤더 | `Idempotency-Key`, **필수** |
| 형식 | 1~255자 문자열. 없거나 빈 값이거나 255자를 넘으면 400 `INVALID_INPUT`. UUID 형식은 강제하지 않는다 |
| 키 범위 | `(memberId, 키)`. 다른 회원의 같은 키와 섞이지 않는다 |
| 요청 지문 | 키와 함께 정규화한 `accountId`, `symbol`, `side`, `price`, `quantity`로 만든 지문을 저장한다. 같은 키인데 지문이 다르면 422 `IDEMPOTENCY_KEY_REUSED` |
| 앞 요청이 처리 중 | 409 `IDEMPOTENCY_KEY_IN_PROGRESS` |
| 재응답 | 저장한 201과 같은 본문에 `Idempotent-Replayed: true` 헤더를 붙인다. 처음 처리한 응답에는 붙이지 않는다 |
| 저장하는 결과 | 201(접수 성공)만. 4xx·예외로 끝나면 키를 지운다 |
| 보관 기간 | 24시간 (TTL) |
| 저장소 | 인메모리 캐시(Caffeine), `expireAfterWrite`로 만료된 키를 지운다 |

- **필수로 받는 이유**: 주문은 돈이 걸린 요청이다. 선택이면 키를 빠뜨린 클라이언트는 중복 방지 없이 주문하게 된다. 클라이언트가 우리 프론트와 봇 스크립트뿐이라 둘 다 맞추기 쉽다. 포기한 것: 기존 주문 접수 테스트의 요청에 모두 헤더를 붙여야 한다.
- **키 범위에 회원을 넣는 이유**: 키만으로 찾으면 다른 회원이 같은 키를 보냈을 때(클라이언트 버그, 남의 키를 알아낸 경우) 그 회원에게 남의 주문 응답이 돌아간다.
- **길이 상한을 두는 이유**: 키는 메모리에 24시간 남는다. 아주 긴 키를 계속 보내 메모리를 채우는 것을 막는다.
- **상태 코드**: IETF 표준 초안 `draft-ietf-httpapi-idempotency-key-header`의 구분(키 없음 400, 처리 중 409, 같은 키·다른 내용 422)과 같다.
- **201만 저장하는 이유**: 실패한 요청(422 주문가능금액 부족, 404)은 hold도 주문도 만들지 않았으므로 같은 키로 다시 처리해도 안전하다. 클라이언트는 확정된 결과(4xx)를 받으면 키를 버리므로 같은 키로 다시 오는 경우도 드물다.
  - 포기한 것: 같은 키에 첫 번째는 422, 입금 후 두 번째는 201이 나올 수 있다. 응답은 달라지지만 돈·주문의 정합성은 깨지지 않는다.
  - 다른 선택: Stripe는 비즈니스 로직까지 간 4xx도 저장해 같은 키에 항상 같은 응답을 준다. 응답 전체를 저장하고 다시 만들어야 해서 구현이 커진다.
  - 400(입력 형식 오류)은 키를 등록하기 전에 끝나므로 애초에 저장되지 않는다.
- **24시간인 이유**: TTL은 클라이언트가 재시도할 수 있는 가장 긴 시간보다 길기만 하면 된다. 재시도는 보통 몇 초에서 몇 분 안에 끝난다. Stripe도 24시간이다.
- **TTL 삭제를 라이브러리에 맡기는 이유**: 직접 하면 저장 시각을 함께 넣고, 읽을 때 만료 여부를 확인하고(lazy), 스케줄러로 주기적으로 쓸어내야 한다. 학습 가치가 낮다.

### 처리 흐름

```
① 입력 검사 (키 형식, 본문)                         → 400 INVALID_INPUT
② (memberId, 키)로 PENDING 등록 — 원자적으로
   ├ 이미 PENDING                                   → 409 IDEMPOTENCY_KEY_IN_PROGRESS
   ├ 이미 COMPLETED, 지문 다름                       → 422 IDEMPOTENCY_KEY_REUSED
   ├ 이미 COMPLETED, 지문 같음                       → 저장한 201 + Idempotent-Replayed: true
   └ 없었음 → 등록하고 ③으로
③ 주문 접수 (6장 ②~⑤, 한 트랜잭션)
④ 커밋 성공 → COMPLETED + 결과로 바꾼다
   실패(4xx·예외) → 키를 지운다
```

```
  (없음) ──등록──▶ PENDING ──커밋 성공──▶ COMPLETED (24시간 뒤 만료)
                     │
                     └──실패──▶ (삭제)
```

- **②는 원자적이어야 한다.** "있는지 확인(`get`)"과 "넣기(`put`)"를 따로 하면, 동시에 온 같은 키 요청 두 개가 둘 다 "없음"을 보고 둘 다 ③으로 간다. 9장의 lost update와 같은 구조다. 키워드: `ConcurrentMap.putIfAbsent`(Caffeine은 `asMap()`으로 얻는다).
- **실패하면 반드시 지운다.** 지우지 않으면 그 키는 24시간 동안 PENDING으로 남아 재시도가 모두 409가 된다. 예외가 어디서 나든 지워지게 한다(`try/finally` 등).
- **④는 트랜잭션 커밋 뒤다.** 커밋 전에 COMPLETED로 저장했는데 커밋이 실패하면, 접수되지 않은 주문의 201이 저장된다. 그래서 키 처리는 `@Transactional`이 붙은 `PlaceOrderService`의 바깥에 둔다(아래 "키 처리 위치").
- 409를 받은 클라이언트는 같은 키로 잠시 뒤 다시 보내면 된다. 앞 요청이 끝났으면 저장한 201을 받는다.

### 저장 형식

캐시 한 칸은 키 `(memberId, 헤더 키)`와 값 `상태 + 지문 (+ 결과)`이다. 회원 7이 계좌 1에 키 `abcd`로 주문하면 다음과 같다.

```
요청
  POST /api/accounts/1/orders
  X-Member-Id: 7
  Idempotency-Key: abcd
  { "quantity": 10, "symbol": "mu", "side": "buy", "price": 18743 }

캐시
  IdempotencyKey(memberId = 7, key = "abcd")
    ② 등록 → Pending(fingerprint = PlaceOrderCommand(accountId = 1, memberId = 7, symbol = "MU", side = BUY, price = 18743, quantity = 10))
    ④ 성공 → Completed(fingerprint = (위와 같음),
                       result = PlacedOrder(id = 1, symbol = "MU", side = BUY, price = 18743, quantity = 10, status = ACCEPTED))
       실패 → (삭제)
```

| 항목 | 결정 | 이유 |
|---|---|---|
| 캐시 키 | `common.idempotency.IdempotencyKey(memberId, key)` data class. 문자열로 이어붙이지 않는다 | Caffeine은 JVM 안의 맵이라 `equals`·`hashCode`가 맞는 객체면 키가 된다. 문자열로 이어붙이면 키 값에 구분자(`:`)가 들어오는 경우를 따져야 한다 |
| 지문 | 정규화를 마친 `PlaceOrderCommand`를 그대로 넣고 `equals`로 비교한다. 해싱하지 않는다 | 필드가 `Long`·`String`·enum뿐이라 작고 정확히 비교된다. 디버깅할 때 원래 값이 보인다 |
| 값 | 상태별 타입 `Pending(지문)` / `Completed(지문, 결과)` | 한 클래스에 `status`와 nullable `result`를 두면 "COMPLETED인데 결과가 없음" 같은 불가능한 상태가 생긴다. 지문은 등록할 때 이미 있으므로 `Pending`에도 넣고, 완료할 때 그대로 옮긴다 |
| 결과 | 서비스가 돌려준 `PlacedOrder` 그대로. 상태 코드는 저장하지 않는다 | 재응답도 컨트롤러가 같은 변환(`PlaceOrderResponse.from`)을 거치므로 본문이 같다. 201만 저장하므로 상태 코드는 항상 201이다. 빈이 웹 응답 타입을 몰라도 된다 |

같은 키로 다시 온 요청은 새로 만든 지문과 저장한 지문을 비교한다.

| 다시 온 본문 | 지문 | 결과 |
|---|---|---|
| `{"symbol":"MU","side":"BUY","price":18743,"quantity":10}` | 같음 | 재응답 201 |
| `{"price":18743,"quantity":10,"side":"Buy","symbol":"Mu"}` | 같음 (정규화 후) | 재응답 201 |
| `{"symbol":"MU","side":"BUY","price":18000,"quantity":10}` | `price` 다름 | 422 `IDEMPOTENCY_KEY_REUSED` |

- 지문의 `memberId`는 키에도 있다. 같은 키를 찾았다면 `memberId`도 같으므로 비교 결과에 영향이 없다.
- 빈은 주문을 모르므로 지문·결과를 주문 타입으로 받지 않는다(제네릭 등). 시그니처는 구현하며 정한다.
- **지문은 Command가 정규화를 마쳤다는 전제다.** `side`는 Jackson이 `BUY`로 바꾼다(enum 대소문자 무시 설정). `symbol`은 지금 `PlaceOrderRequest.toCommand`가 받은 그대로(`"mu"`) 넘기므로, Command를 지문으로 쓰면 `"mu"`와 `"MU"`가 다른 지문이 된다. 그래서 변환 단계에서 대문자로 정규화한다(6장). 지금 주문 접수가 동작하는 이유는 두 가지다. 종목 조회가 대소문자를 무시하는 콜레이션으로 찾고, 응답의 `symbol`을 종목 값으로 채운다(`PlacedOrder.of`).
- 해싱을 다시 볼 조건:
  - 지문 재료가 본문 원문처럼 크고 모양이 정해지지 않을 때. 해시로 고정 길이로 줄인다.
  - Redis 같은 외부 저장소로 옮길 때. 키와 값을 문자열로 직렬화해야 한다. 키는 `idempotency:order:{memberId}:{key}`처럼 용도 접두사를 붙이는 관례를 따르고, 형식은 옮길 때 정한다.

### 키 처리 위치

컨트롤러가 검증을 마치고 Command를 만든 뒤, 별도 빈을 명시적으로 호출한다.

```
OrderController.placeOrder(accountId, memberId, Idempotency-Key 헤더, 본문)
  ① 검증 (@Valid, SELL 거절)
  ② command = request.toCommand(...)
  ③ 결과 = <멱등성 빈>.execute(memberId, 키, 지문(command)) { placeOrderService.placeOrder(command) }
                                                               └ 트랜잭션은 이 안에서 시작·커밋
  ④ 201 + 본문. 재응답이면 Idempotent-Replayed: true
```

- 빈은 주문을 모르는 범용 부품으로 만든다. 결과에 "재응답인지"를 함께 돌려줘서 컨트롤러가 헤더를 붙인다. 저장소를 Redis로 옮길 때는 이 빈 안만 바뀐다.
- 빈 이름은 `IdempotentExecutor`다. 컨트롤러가 호출하는 부품이라 `-er` 형태로 지었다.
- `PlaceOrderService` 안에 넣지 않는다. 같은 클래스에서 `@Transactional` 메서드를 내부 호출하면 프록시를 거치지 않아 트랜잭션이 시작되지 않는다(self-invocation). 별도 빈이어야 "커밋 뒤에 완료로 바꾸기"가 호출 구조로 보장된다.
- 봇 주문 스크립트처럼 웹이 아닌 진입점도 같은 빈을 호출하면 된다.

검토했다가 쓰지 않은 위치:

| 위치 | 쓰지 않은 이유 |
|---|---|
| 서블릿 필터 | Spring MVC 바깥이라 본문이 바이트 스트림뿐이다. 정규화한 값으로 지문을 만들 수 없고(12장 14번), 입력 검증(400)보다 먼저 돌아서 400이면 등록한 키를 다시 지워야 한다. 저장한 201을 돌려주려면 응답 본문을 가로채야 한다 |
| `HandlerInterceptor` | `preHandle`은 본문 파싱·`@Valid`·`@CurrentMember` 해석 전이라 필터와 같은 문제가 있다. `@RestController`는 `postHandle` 시점에 응답 본문이 이미 쓰여 있어 고치기 어렵다 |
| AOP (`@Idempotent` 애노테이션) | 메서드 인자(Command)로 지문은 만들 수 있다. 하지만 ① 키·`memberId`를 받으려면 `RequestContextHolder`로 헤더를 읽어 웹에 묶이거나 Command에 키를 넣어야 한다. ② 메서드에 투명하게 같은 값을 돌려주므로 컨트롤러가 재응답인지 몰라 `Idempotent-Replayed` 헤더를 붙일 수 없다. ③ `@Transactional`과 같은 메서드에 붙으면 두 어드바이스의 순서를 `@Order`로 직접 정해야 한다. 지정하지 않으면 순서가 정의되지 않는다 |

- 필터·인터셉터는 로깅·인증·CORS처럼 모든 요청에 똑같이 적용하는 횡단 관심사에 맞는다. 멱등성 키는 엔드포인트 하나에만 붙고 도메인 값(Command)을 알아야 한다.
- 바꿀 조건: 입금 등 멱등성 키를 붙일 엔드포인트가 여러 개가 되면 AOP를 다시 본다. 그때도 애스펙트는 이 빈을 부르는 얇은 껍데기가 된다.

### 클라이언트

키를 언제 만들고 언제 버리는지만 정하면 된다.

| 시점 | 키 |
|---|---|
| 주문 내용을 정하고 처음 "주문"을 누름 | 새로 만든다 (`crypto.randomUUID()`) |
| 네트워크 오류·타임아웃·5xx·409로 재시도 | 같은 키를 다시 쓴다 |
| 응답 전에 버튼을 또 누름 | 같은 키 (또는 버튼 비활성화) |
| 확정된 결과를 받음 (201, 4xx) | 버린다. 다음 주문은 새 키 |
| 가격·수량·종목을 바꿈 | 버린다. 다른 의도다 |

- mockx 프론트는 키를 주문 화면의 상태(메모리)에 둔다. 앱이 꺼졌다 켜져도 재시도하려면 로컬 저장소에 둬야 하지만, 시연용이라 필요 없다.
- 봇 주문 스크립트도 같은 규칙을 따른다.

### 알려진 한계

| 한계 | 결과 | 해결 방향 |
|---|---|---|
| 서버 재시작 시 키가 사라진다 | 재시작 전에 접수된 주문을 재시도하면 중복 접수된다 | Redis 또는 DB로 이전 |
| 서버가 여러 대면 키를 공유하지 못한다 | 재시도가 다른 서버로 가면 중복 접수된다 | Redis (`SET key value NX EX 86400`) |
| 주문 커밋과 키 저장(④)이 원자적이지 않다 | 커밋 직후 ④ 전에 서버가 죽으면 키가 없어 재시도가 중복된다 | 주문 INSERT와 같은 트랜잭션에서 키를 DB에 저장(유니크 제약). Redis로 옮겨도 이 틈은 남는다 |
| 저장 개수 상한이 없다 | 24시간 동안 주문이 아주 많으면 메모리가 늘어난다 | 개수 상한(`maximumSize`)은 상한을 넘으면 24시간 전에 키가 지워져 보장이 약해진다. 측정 뒤 정한다 |

- 바꿀 조건: 서버를 2대 이상 띄우거나 Redis를 도입할 때 저장소를 옮긴다. 키 처리 흐름(②~④)은 그대로이고 저장소만 바뀌게 만들어 두면 옮기기 쉽다.
