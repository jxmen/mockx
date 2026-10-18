# 계좌(Account) 설계

> 최종 수정: 2026-10-14. 회원가입 흐름과 테이블 공통 규칙(단수형 이름, `DATETIME(6)` UTC, 제약 이름, Flyway)은 [`db-design-member.md`](db-design-member.md)를 참고한다.

## 1. 정책

| 항목 | 결정 |
|---|---|
| 계좌 개설 시점 | 회원가입 시 회원과 함께 1개 생성 (한 트랜잭션) |
| 추가 계좌 | 만들 수 없음 (회원당 계좌 1개) |
| 삭제 | MVP에서 만들지 않음 |
| 연관관계 | Member를 `memberId: Long`으로 ID만 참조 |

## 2. 테이블

### account

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `id` | `BIGINT` | PK, auto increment (내부 식별자) |
| `member_id` | `BIGINT` | NOT NULL, `uk_account_member_id` |
| `number` | `CHAR(12)` | NOT NULL, `uk_account_number` (계좌번호, 외부 노출 식별자) |
| `balance` | `BIGINT` | NOT NULL, DEFAULT 0 (예수금) |
| `hold_amount` | `BIGINT` | NOT NULL, DEFAULT 0 (주문으로 묶인 금액) |
| `type` | `VARCHAR(20)` | NOT NULL (계좌 상품 구분, `AccountType` enum 이름. 예: `Brokerage`) |
| `created_at` | `DATETIME(6)` | NOT NULL |
| `updated_at` | `DATETIME(6)` | NOT NULL |

- `member_id` UNIQUE 제약은 회원당 계좌 1개 정책을 DB가 보장하게 하는 장치다. 회원 ID로 계좌를 찾을 때 쓸 인덱스 역할도 한다. 나중에 1인 N계좌로 확장하면 UNIQUE를 제거한다.
- 다른 테이블이 계좌를 참조할 때(FK)는 `id`를 쓴다. 외부(API)에는 계좌번호(`number`)만 노출한다.
- `type`은 계좌번호 앞 3자리(상품코드)로도 알 수 있지만, 타입별 조회를 `LIKE '100%'`로 하지 않도록 컬럼을 따로 둔다.
  - `@Enumerated(EnumType.STRING)`으로 enum 이름을 저장한다. `ORDINAL`은 상수 순서가 바뀌면 기존 데이터의 의미가 달라지므로 쓰지 않는다.
  - enum 상수 이름을 바꾸면 저장된 값도 마이그레이션해야 한다. 저장값을 이름과 분리해야 할 때는 `AttributeConverter`로 상품코드(`prefixNum`)를 저장하는 방식으로 바꾼다.
  - MySQL 네이티브 `ENUM` 타입은 쓰지 않는다. 값을 추가할 때마다 `ALTER TABLE`이 필요하다. Hibernate 6.2+는 MySQL에서 `EnumType.STRING`을 `ENUM` 타입으로 기대하므로, `VARCHAR` 컬럼과 validate 결과가 어긋나면 `@JdbcTypeCode(SqlTypes.VARCHAR)`로 맞춘다.

### account_number_sequence

| 컬럼 | 타입 | 제약 |
|---|---|---|
| `product_code` | `CHAR(3)` | PK |
| `next_value` | `BIGINT` | NOT NULL |

- 상품코드별 초기 행(`'100', 0`)은 마이그레이션에서 함께 insert한다.

## 3. 계좌번호 규칙

```
| 상품코드 3자리 | 순번 8자리 | 검증숫자 1자리 |
|      100      | 00000001  |       7       |
```

- 상품코드: `AccountType`별 고정값 (위탁계좌 = `100`)
- 순번: `account_number_sequence`에서 발급한다. 8자리를 넘으면 예외를 던진다.
- 검증숫자: Luhn 알고리즘
- 저장할 때는 숫자 12자리만 저장하고, 하이픈(`1000-0000-0017`)은 표시할 때만 붙인다.

**순번 발급 방식**

```sql
UPDATE account_number_sequence
   SET next_value = LAST_INSERT_ID(next_value + 1)
 WHERE product_code = ?;
SELECT LAST_INSERT_ID();
```

- 동시성 제어: 원자적 UPDATE가 순번 행에 X락을 잡아 발급을 직렬화한다. (SELECT FOR UPDATE, 낙관적 락은 쓰지 않음)
- 구현: JPA 엔티티 없이 `JdbcTemplate`으로 SQL만 실행한다. 순번 발급 외에는 이 테이블을 쓰지 않고, 벌크 UPDATE와 1차 캐시가 어긋나는 문제도 피할 수 있다.
- 두 쿼리는 같은 커넥션에서 실행돼야 한다. 그래서 반드시 트랜잭션 안에서 호출한다.
- 회원가입 트랜잭션 안에서 발급하므로, 가입이 롤백되면 순번도 롤백된다(번호가 비지 않음). 대신 순번 행에 잡힌 락이 커밋까지 유지되므로, 동시에 들어온 가입 요청은 순서대로 처리된다.

## 4. 진행 상황

- [x] `Luhn` 검증숫자 계산 + 테스트
- [x] `AccountNumber.issue()` (0 패딩, 8자리 초과 검사) + 테스트
- [x] `Account` 도메인 로직 (deposit, hold, release) + 테스트
- [x] `AccountNumberRepository` 순번 테이블 구현체
- [x] `account.type` 컬럼 추가 (`VARCHAR(20)`, `EnumType.STRING`)
- [x] `Account` 엔티티 매핑 (Money 매핑 방식 결정: `@Embedded` + `@AttributeOverride` 또는 `AttributeConverter`)

### 테스트 시나리오 (순번 발급기)

- 순번을 연속으로 발급하면 1씩 증가하고 상품코드가 앞에 붙는다
- 상품코드 행이 없으면 예외가 난다
- 동시에 100건을 발급해도 모두 서로 다른 번호가 나온다

## 5. 나중으로 미룬 것

| 항목 | 시점 |
|---|---|
| 원장(ledger) 테이블 설계 | 입금 API 작업 시 |
| 동시성 제어 (`version` 컬럼 또는 비관적 락) | 동시 매수 테스트 작업 시, V2 마이그레이션으로 추가 |
| CHECK 제약 (`balance >= 0`, `hold_amount <= balance`) | 필요 시 검토 |

## 6. 알려진 한계

- `X-Member-Id` 스텁 단계에서는 헤더 값만 바꾸면 다른 회원이 될 수 있다. 배포 전 세션 로그인으로 교체한다.
- 계좌번호가 순번이라 다음 번호를 추측할 수 있다. 소유자 검증(남의 계좌는 404)으로 막는다.
