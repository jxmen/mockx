# 회원(Member) 설계

> 최종 수정: 2026-10-24. 계좌와 계좌번호 설계는 [`db-design-account.md`](db-design-account.md)를 참고한다.

## 1. 개요

- 모의 증권 서비스라 실명 확인이나 본인인증은 하지 않는다. username + password만으로 가입한다.
- 회원가입을 하면 Member와 위탁계좌(Account) 1개가 **하나의 트랜잭션**에서 함께 생성된다. 추가 계좌는 만들 수 없다.
- 회원가입 서비스(member)가 계좌 생성(account)을 직접 호출한다. 이벤트 방식은 쓰지 않는다.
- Account는 Member를 `memberId: Long`으로 ID만 참조한다(`@OneToOne`을 쓰지 않음).
- 회원 삭제와 탈퇴는 MVP에서 만들지 않는다.

## 2. 테이블 공통 규칙

| 항목 | 규칙 |
|---|---|
| 테이블 이름 | 단수형(`member`, `account`). 주문 테이블만 예외로 `orders`다. `order`가 MySQL 예약어이기 때문이다([`db-design-order.md`](db-design-order.md)) |
| 시간 컬럼 | `DATETIME(6)`, UTC로 저장한다. Kotlin 타입은 `Instant`. ISO 8601은 API 응답에서 쓰는 표기법이다 |
| 생성·수정 시각 | 엔티티 테이블에는 `created_at`, `updated_at`을 둔다. 값은 애플리케이션이 채운다(`BaseTimeEntity` + Spring Data JPA Auditing). DB `DEFAULT`, `ON UPDATE`는 쓰지 않는다 |
| 제약 이름 | 직접 지정한다(`uk_<테이블>_<컬럼>`). 409로 변환할 때 어떤 제약이 깨졌는지 구분하는 데 쓴다 |
| 스키마 관리 | Flyway 마이그레이션으로 관리하고, Hibernate는 `ddl-auto: validate`로 둔다 |

- `DATETIME(6)`의 `(6)`은 마이크로초 정밀도다. 정밀도가 없으면 초 단위에서 잘려, 저장한 값과 다시 읽은 값이 달라진다.
- `DATETIME`은 타임존 정보 없이 달력 값을 그대로 저장한다. UTC라는 것은 약속이고, `hibernate.jdbc.time_zone: UTC`가 이를 지킨다.
- 생성·수정 시각을 DB가 채우지 않는 이유: `CURRENT_TIMESTAMP`는 MySQL 세션 `time_zone`을 따르므로 UTC가 보장되지 않는다. 또 값이 빠졌을 때 DB 시각으로 조용히 채워지지 않고 NOT NULL 위반으로 드러나게 한다.
- 정밀도를 넘는 소수 초는 MySQL이 반올림한다. `Instant.now()`가 나노초 값을 줄 수 있으므로 시각을 만드는 곳(`DateTimeProvider`)에서 마이크로초로 잘라 둔다.
- `updated_at`은 마지막으로 바뀐 시각일 뿐 이력이 아니다. 돈의 변동 이력은 원장(ledger)에 남긴다.

## 3. 테이블

### member

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, auto increment | |
| username | VARCHAR(10) | NOT NULL, `uk_member_username` | 입력한 그대로 저장. collation `utf8mb4_0900_ai_ci`를 DDL에 명시 |
| nickname | VARCHAR(10) | NOT NULL | 중복 허용, 대소문자 유지 |
| password | VARCHAR(255) | NOT NULL | 평문이 아니라 BCrypt 해시(60자)를 저장한다. 알고리즘을 바꿀 때를 대비해 여유 있게 잡는다 |
| created_at | DATETIME(6) | NOT NULL | UTC |
| updated_at | DATETIME(6) | NOT NULL | UTC |

- username의 UNIQUE 제약은 **동시 가입을 막는 최종 방어선**이다. 조회 후 insert(`existsBy...`)만으로는 경쟁 조건을 막을 수 없다.
- username은 정규화하지 않고 **입력한 그대로 저장**한다(`Alice`는 `Alice`로 저장해서 표시). 대소문자 중복 판정은 DB에 맡긴다.
  - `_ci`(case-insensitive) collation에서는 `Alice`와 `alice`가 같은 값이다. 따라서 UNIQUE 제약이 대소문자만 다른 가입을 막고, `WHERE username = 'ALICE'` 조회도 `Alice`를 찾는다.
  - MySQL 8 기본 collation이 `utf8mb4_0900_ai_ci`이지만, 서버나 테이블 설정이 바뀌면 보호가 사라진다. 그래서 컬럼 DDL에 `COLLATE`를 명시한다.
  - `_ai`(accent-insensitive)도 함께 적용되지만, username은 ASCII만 허용하므로 영향이 없다.
  - 주의: 애플리케이션 코드의 비교(`==`, `Map` 키, 이후 Redis 키)는 대소문자를 구분한다. username을 메모리에서 비교하거나 키로 쓸 일이 생기면 DB와 기준이 어긋나지 않게 해야 한다.
- password 확인 값(passwordConfirm)은 저장하지 않는다.

## 4. 회원가입

### 요청

`POST /api/members`

| 필드 | 설명 |
|---|---|
| username | 로그인 ID |
| nickname | 화면 표시용 이름 |
| password | 비밀번호 |
| passwordConfirm | 비밀번호 확인. 저장하지 않고 서버에서 일치 여부만 검증한다 |

- 정의되지 않은 필드(예: `"role": "ADMIN"`)는 무시한다(Spring Boot 기본 동작).

### 응답

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "memberId": 1,
  "username": "Alice",
  "nickname": "앨리스",
  "accountNumber": "100000000016"
}
```

- `Location` 헤더는 주지 않는다. 가리킬 회원 단건 조회 API(`GET /api/members/{id}`)가 없다. 가입한 본인의 정보는 `GET /api/members/me`로 조회한다.
- 본문에는 위 4개 필드만 넣는다. password와 `createdAt`은 넣지 않는다.
- `username`은 입력한 대소문자 그대로 응답한다.
- `accountNumber`는 하이픈 없이 숫자 12자리로 준다. 하이픈은 프론트가 표시할 때 붙인다.

### 입력 규칙

| 필드 | 길이 | 허용 문자 | 공백 | 대소문자 |
|---|---|---|---|---|
| username | 2~10자 | 영문, 숫자, `_`, `-` | 불허(400) | 그대로 저장. `Alice`가 있으면 `alice`는 중복(409) |
| nickname | 2~10자 | 한글 완성형(가-힣), 영문, 숫자 | 불허(400) | 그대로 유지 |
| password | 4~20자 | 출력 가능한 ASCII(`0x20`~`0x7E`, 공백 포함) | 유지(trim하지 않음) | 그대로 유지 |

- `__`처럼 특수문자로만 된 username과, 공백으로만 된 password(`"    "`)는 허용한다.
- 특수문자, 이모지, 전각 문자, 한자, 한글 자모 단독은 각 필드의 허용 문자에 포함되지 않으므로 모두 거절한다.
- 허용 문자는 차단 목록(blocklist)이 아니라 **허용 목록(allowlist)**으로 정의한다. 이모지를 정확히 정의하기 어렵기 때문이다. 일부 이모지(`❤`, `✅`)는 BMP 안에 있고, 여러 code point를 결합한 이모지도 있다.
- 허용 문자에 surrogate pair가 없다. 그래서 Kotlin `String.length`와 MySQL `VARCHAR` 길이가 같은 기준으로 계산된다.
- password는 ASCII만 허용하므로 최대 20바이트다. BCrypt의 72바이트 입력 제한에 걸리지 않는다.
- username과 nickname의 앞뒤 공백은 **프론트에서 trim해서 보낸다.** 서버는 trim하지 않고, 공백이 있으면 400을 준다.
- NFD로 분해된 한글은 `가-힣` 범위에 걸려 400이 된다. 프론트에서 `normalize('NFC')` 후 전송한다.

### 검증 방식: Spring Validation (Bean Validation)

입력 형식은 값 객체가 아니라 **요청 DTO에 Bean Validation 애너테이션**을 붙여 검증한다.

- 의존성은 `spring-boot-starter-validation`이다. 컨트롤러 파라미터에 `@Valid @RequestBody`를 붙인다.
- 길이는 `@Size`, 허용 문자는 `@Pattern`(정규식)으로 표현한다. 허용 문자에 surrogate pair가 없으므로 `@Size`의 UTF-16 길이와 글자 수가 같다.
- `build.gradle.kts`에 `-Xannotation-default-target=param-property`가 설정돼 있어 생성자 프로퍼티에 `@field:`를 붙이지 않아도 필드에 적용된다.
- 검증에 실패하면 `MethodArgumentNotValidException`이 발생한다. 이 예외를 400 `INVALID_INPUT`으로 변환하고, `FieldError`마다 `field`와 메시지를 응답의 `errors` 배열에 넣는다.
- 형식 검증은 DTO에서 하고, 대소문자 중복 판정은 DB collation에서 한다. 애플리케이션은 username을 변환하지 않는다.

구현할 때 고민할 것:
- 요청 DTO 필드는 non-null `String`으로 둔다. 필드가 빠지거나 `null`인 요청은 검증 전에 Jackson 역직렬화에서 실패하므로(`HttpMessageNotReadableException`), 원인 예외에서 필드 이름을 꺼내 400 `INVALID_INPUT`의 `errors`에 담는다. 첫 번째로 실패한 필드 하나만 담긴다(nullable + `@NotNull`이면 전부 담을 수 있지만 택하지 않음).
- password와 passwordConfirm 일치 검사는 필드 하나로는 표현할 수 없다. 클래스 수준 커스텀 제약을 만들지, 서비스에서 검사할지 정한다.
- 값 객체를 쓰지 않으므로 형식 규칙이 DTO에만 있다. DTO를 거치지 않고 Member를 만드는 경로에서는 형식이 검증되지 않는다(트레이드오프).

### 처리 흐름

```
1. 입력값 형식 검증 (요청 DTO의 Bean Validation, @Valid)
2. password와 passwordConfirm 일치 여부 검증
3. 비밀번호 해시 (BCryptPasswordEncoder, 의존성은 spring-security-crypto만 추가)
[트랜잭션 시작]
4. member 생성 후 저장 (username 중복이면 409)
5. 계좌번호 발급 (순번 테이블) 후 account 저장
[커밋] — 4~5 중 하나라도 실패하면 전체 롤백
```

### 클래스 구성

- `member.SignupService`: 회원가입 유스케이스. 트랜잭션 경계를 가진다.
- `account.AccountOpener`: 계좌번호 발급 → `Account.create` → 저장을 맡는다. 사용자가 직접 호출하는 기능이 아니라 가입 과정의 부품이다. `SignupService`는 `AccountRepository`나 `AccountNumberRepository`를 직접 쓰지 않고 이것만 호출한다.
- 입력: 지금은 웹 요청 DTO(`SignupRequest`)를 서비스에 그대로 넘긴다. 다른 진입점(관리자 API, 배치 등)이 생기거나 웹 검증과 서비스 입력이 달라지면 `SignupCommand`로 분리한다.

## 5. 인증

### MVP: `X-Member-Id` 헤더

로그인 없이 요청 헤더로 회원 ID를 직접 전달한다. 개발 편의용 스텁이다.

```bash
curl -X POST localhost:8080/api/accounts/1/deposit \
  -H "X-Member-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"amount":100}'
```

- 컨트롤러는 헤더를 직접 읽지 않고 `@CurrentMember memberId: Long`으로 받는다. 값은 `HandlerMethodArgumentResolver`가 채우고, `WebMvcConfigurer.addArgumentResolvers()`로 등록한다.
- 헤더가 없거나 숫자가 아니면 401을 준다.
- 아무 숫자나 넣으면 그 회원이 되므로 prod가 아닐 때(`@Profile("!prod")`)만 동작하게 한다. `local`이 아니라 `!prod`로 거는 이유는 프로파일 없이 띄우는 `bootRun`과 E2E 테스트에서도 별도 설정 없이 쓰기 위해서다.
- 리졸버 등록은 `WebConfig`와 분리한 전용 `@Configuration` 클래스에 두고, 클래스에 `@Profile`을 붙인다. `@Profile`은 빈 등록 조건이라 `addArgumentResolvers()` 같은 콜백 메서드에 달면 무시된다.
- prod에서는 헤더 리졸버를 빼는 것만으로는 부족하다. `@CurrentMember`를 처리할 리졸버가 없으면 Spring이 단순 타입 파라미터를 `@RequestParam`처럼 해석해서(`RequestParamMethodArgumentResolver`의 기본 처리) `?memberId=1` 쿼리로 다른 회원이 될 수 있다. 그래서 prod(`@Profile("prod")`)에는 `@CurrentMember`를 받으면 항상 401을 던지는 리졸버를 등록한다(fail-closed). 세션 로그인이 생기면 이 리졸버를 세션용으로 교체한다.
- 헤더에는 **Account ID가 아니라 Member ID**를 담는다. 인증 대상은 회원이고, 계좌는 회원이 소유한 리소스다.

### 이후: 세션 로그인 (배포 전 필수)

- 리졸버만 세션용으로 교체한다(`request.getSession(false)`에서 memberId를 꺼냄). 컨트롤러와 서비스 코드는 바뀌지 않는다.
- 브라우저 쿠키에는 의미 없는 세션 ID(`JSESSIONID`)만 저장된다. memberId 같은 실제 데이터는 서버의 세션 저장소에 있다. 처음에는 Tomcat 메모리를 쓰고, Redis를 도입할 때 Spring Session + Redis로 옮긴다.
- 고려할 것: 로그인 성공 시 `changeSessionId()`(세션 고정 공격 방지), 쿠키의 `HttpOnly`, `Secure`, `SameSite` 설정

### 소유자 검증 (IDOR 방지)

- 계좌에 접근할 때는 서비스 계층에서 **소유자가 일치하는지 반드시 검증**한다. 인증 방식과 관계없이 필요하다.
- 남의 계좌에 접근하면 **404**를 준다. 존재하지 않는 계좌와 같은 응답이므로 계좌의 존재가 노출되지 않는다.
- 응답은 같게 주고 **서버 로그에는 소유자 불일치를 WARN으로 남긴다**(memberId, 계좌번호). 디버깅할 때는 로그로 구분한다.

## 6. 에러 응답

RFC 9457 Problem Details 형식을 쓴다. Spring `ProblemDetail`과 `@RestControllerAdvice`로 구현한다.

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 사용 중인 username입니다.",
  "instance": "/api/members",
  "code": "USERNAME_DUPLICATED",
  "field": "username"
}
```

- `type`, `title`, `status`, `detail`, `instance`는 표준 필드다. `code`와 `field`는 확장 필드다(`ProblemDetail.setProperty()`).
- 프론트는 `detail`을 사용자에게 보여 주고, 분기는 `code`로 한다.
- `field`에는 **요청 JSON의 필드 이름**을 넣는다. DB 컬럼 이름은 내부 구현이므로 노출하지 않는다. 특정 필드와 관련 없는 에러에는 넣지 않는다.

| 상황 | 상태 코드 | code | field |
|---|---|---|---|
| 입력 형식 오류, 필드 누락, `null` | 400 | `INVALID_INPUT` | 없음. 대신 `errors` 배열 |
| JSON 문법 오류 | 400 | `INVALID_INPUT` | 없음 (`errors`도 없음) |
| password와 passwordConfirm 불일치 | 400 | `PASSWORD_NOT_MATCHED` | `passwordConfirm` |
| `X-Member-Id` 헤더 없음, 형식 오류 | 401 | `UNAUTHENTICATED` | - |
| `X-Member-Id`에 해당하는 회원이 없음 (`GET /api/members/me`) | 404 | `MEMBER_NOT_FOUND` | - |
| 계좌가 없거나 남의 계좌 | 404 | `ACCOUNT_NOT_FOUND` | - |
| username 중복 (대소문자만 다른 경우 포함) | 409 | `USERNAME_DUPLICATED` | `username` |

`INVALID_INPUT`은 틀린 필드를 모두 `errors` 배열로 준다. 폼에서 필드별로 에러를 표시하기 위해서다.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "입력값이 올바르지 않습니다.",
  "instance": "/api/members",
  "code": "INVALID_INPUT",
  "errors": [
    { "field": "username", "detail": "2~10자여야 합니다." },
    { "field": "nickname", "detail": "2~10자여야 합니다." }
  ]
}
```

- 형식 오류와 비밀번호 불일치가 함께 있으면 `INVALID_INPUT`을 응답한다. 형식 검증이 먼저다.
- 필드 누락과 `null`은 Jackson 역직렬화 단계에서 실패하므로, 빠진 필드 하나만 `errors`에 담길 수 있다.

구현할 때 고민할 것:
- 400은 `MethodArgumentNotValidException`(검증 실패)과 `HttpMessageNotReadableException`(JSON 형식 오류, 필드 누락)에서 온다. `IllegalArgumentException`을 전역에서 400으로 바꾸지 않는다. 내부 버그까지 400이 되기 때문이다.
- 409는 `DataIntegrityViolationException`으로 온다. 제약 이름(`uk_member_username`)으로 어떤 UNIQUE 제약이 깨졌는지 구분해야 한다.

## 7. 범위 밖

| 항목 | 이유 |
|---|---|
| 회원 삭제, 탈퇴 | MVP 이후 |
| 이메일 인증, 휴대폰 본인인증(CI/DI), ARS | 모의 서비스. 본인인증은 사업자 계약이 필요하다 |
| 소셜 로그인 | OAuth 설정 비용에 비해 학습 가치가 낮다 |
| 비밀번호 초기화 | 복구 채널(이메일, 휴대폰)이 없다. 잊으면 새로 가입한다 |
| 비밀번호 변경 | 시간이 남으면 추가한다 |
| 한글 자판 입력을 QWERTY로 변환(`ㅂㅈㄷㄱ` → `qwer`) | 서버는 거절하고, 프론트는 "한글 입력 상태" 안내만 한다 |

실제 서비스라면 비밀번호 초기화는 이렇게 만든다(면접 대비):
- 추측 불가능한 랜덤 토큰, 짧은 만료 시간, 1회용
- 토큰도 DB에는 해시로 저장
- 계정 존재 여부를 응답으로 노출하지 않기(user enumeration)
- 재설정 후 기존 세션 전부 무효화

## 8. 테스트

### 입력 검증 (예정)

값 객체 테스트(`UsernameTest` 등)는 Bean Validation으로 바꾸면서 삭제했다. 아래 케이스는 요청 DTO 검증 테스트로 옮긴다. `Validator`를 직접 쓰는 단위 테스트로 할지, MockMvc 컨트롤러 테스트로 할지는 정해야 한다.

- username: 길이 경계(1, 2, 10, 11자), `__` 허용, 공백 거절, 한글·특수문자·이모지·전각 문자·유니코드 숫자(`٣`) 거절
- nickname: 길이 경계, 공백 거절, 대소문자 유지, 자모 단독·특수문자·이모지·한자·NFD 거절
- password: 길이 경계(3, 4, 20, 21자), 공백으로만 된 값 허용, 앞뒤 공백 유지, 한글·이모지·전각 문자·탭·개행 거절
- 필드가 빠진 요청은 400 `INVALID_INPUT`
- username은 입력한 대소문자 그대로 저장된다 (도메인 테스트)

### 회원가입 서비스 / API (`SignupE2ETest`)

- [x] 가입하면 Member와 위탁계좌 1개가 생성되고, 비밀번호는 해시로 저장된다
- [x] password와 passwordConfirm이 다르면 400 `PASSWORD_NOT_MATCHED`
- [x] 이미 있는 username이면 409. `Alice`가 있을 때 `alice`로 가입해도 409 (collation 검증이므로 Testcontainers MySQL로 테스트)
- [x] nickname 중복은 허용. 대소문자만 달라도 입력한 그대로 저장된다
- [x] 계좌 생성이 실패하면 Member도 롤백 (계좌번호 순번도 증가하지 않음)
- [x] 같은 username으로 동시에 가입하면 1건만 성공하고, 나머지는 모두 409(`DuplicateUsernameException`). 대소문자만 다른 username으로 동시에 가입해도 1건만 성공 (`SignupConcurrencyTest`)

### 인증

- [x] `X-Member-Id` 헤더가 없거나 양의 정수가 아니면 401 (`MemberMeE2ETest`, `DepositE2ETest`)
- [x] `X-Member-Id`에 해당하는 회원이 없으면 404 `MEMBER_NOT_FOUND` (`MemberMeE2ETest`)
- [ ] prod 프로파일에서는 인증 수단이 없으므로 항상 401 (`MemberMeProdE2ETest`)
  - 가입한 회원의 올바른 `X-Member-Id` 헤더로 요청해도 401
  - `?memberId=` 쿼리로 요청해도 401 (리졸버가 없을 때 `@RequestParam`처럼 해석되는 우회 차단)
  - 헤더도 쿼리도 없으면 400이 아니라 401
- [x] 남의 계좌에 접근하면 404이고, 없는 계좌에 접근한 경우와 응답이 같다 (`DepositE2ETest`)

## 9. 작업 목록

- [x] `spring-boot-starter-validation` 의존성 추가
- [x] Flyway 설정
  - [x] `spring-boot-starter-flyway`, `flyway-mysql` 의존성 추가
  - [x] `application.yml`에 `ddl-auto: validate`, `hibernate.jdbc.time_zone: UTC` 추가
- [x] `V1__create_member_and_account.sql`: `member`, `account`, `account_number_sequence` 테이블, 제약 이름 지정, 순번 테이블 초기 행 insert
- [x] 계좌번호 순번 발급기 (`AccountNumberRepository` 순번 테이블 구현체)
- [x] `BaseTimeEntity` (`@MappedSuperclass`, `@CreatedDate`, `@LastModifiedDate`) + JPA Auditing 설정
- [x] `Member` 엔티티 + Repository 테스트
- [x] `Account` 엔티티 매핑 (Money 매핑 방식 결정)
- [x] 회원가입 요청 DTO + 입력 검증 테스트
- [x] 회원가입 서비스 + API (`spring-security-crypto` 추가)
- [x] `ProblemDetail` 기반 에러 처리 (`@RestControllerAdvice`)
- [x] `@CurrentMember` + `X-Member-Id` 리졸버 (`!prod` 프로파일)
- [x] 리졸버 등록을 프로파일 전용 `@Configuration`으로 분리 (`LocalAuthWebConfig`, `@Profile`을 클래스에)
- [x] 내 정보 조회 API (`GET /api/members/me`) + E2E 테스트
- [ ] prod 프로파일에서 `@CurrentMember`를 항상 401로 막는 리졸버 등록
