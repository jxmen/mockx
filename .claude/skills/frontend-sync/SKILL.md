---
name: frontend-sync
description: mockx 백엔드에 만들어진 API(컨트롤러·DTO·에러 code·E2E 테스트)를 읽고 frontend/에 API 클라이언트와 시연 화면을 구현하는 스킬. 기존 프론트 구조(src/api, src/features, MemberIdContext, lib/money의 센트 처리, code 기반 에러 매핑)를 따르고, 순수 로직은 Vitest로 테스트한 뒤 test·lint·build를 돌려 보고한다. 필요한 API가 없으면 직접 만들지 않고 스펙을 제안한다. "종목 목록 화면 만들어줘", "주문 API 프론트에 붙여줘", "이 API로 화면 구현해줘", "입금 응답 바뀐 거 프론트에 반영해줘", "프론트 맞춰줘", "화면 추가해줘"처럼 백엔드 기능을 화면으로 시연하거나 API 변경을 프론트에 반영하려는 요청이면 사용한다. 백엔드 코드 리뷰(hint-review), 백엔드 테스트 작성(test-first)에는 쓰지 않는다.
---

# frontend-sync

프론트는 백엔드 기능을 **시연**하기 위한 것이다. 면접에서 "이 API가 이렇게 동작한다"를 화면으로 보여주는 게 목적이라, 예쁜 화면보다 **API 계약을 정확히 따르고, 에러 케이스까지 눈으로 확인할 수 있는** 화면이 우선이다.

프론트 코드는 Claude가 구현한다. `src/main` 아래 백엔드 코드는 읽기만 하고 고치지 않는다.

## 1. API 계약 읽기

화면을 만들기 전에 계약을 먼저 정리한다. 추측으로 필드를 만들지 않는다.

| 볼 것 | 어디서 |
|---|---|
| 경로, 메서드, 인증 필요 여부 | `*Controller.kt`의 매핑, `@CurrentMember` 파라미터 유무 |
| 요청·응답 필드, 타입, 검증 규칙 | `*Request.kt`, `*Response.kt` (검증 어노테이션, nullable 여부) |
| 에러 `code`와 HTTP 상태 | `ApiException`을 상속한 예외들(`grep -rn ': ApiException(' src/main/kotlin`), `ApiExceptionHandler.kt` |
| 실제로 보장되는 동작 | `src/test/**/*E2ETest.kt`의 기대값 |
| 정책, 단위, 범위 | `docs/db-design-*.md`의 요청/응답·에러 응답 섹션 |

정리한 계약을 사용자에게 짧게 보여주고 진행한다.

```
POST /api/accounts/{accountId}/deposit  (X-Member-Id 필요)
요청: amount(센트, 1 ~ 1,000,000)
응답: accountId, amount(센트), balance(센트)
에러: 400 INVALID_INPUT(errors[].field=amount), 401 UNAUTHENTICATED, 404 ACCOUNT_NOT_FOUND
```

- **docs와 코드가 어긋나면 멈추고 묻는다.** 어느 쪽에 맞출지 임의로 정하지 않는다.
- 금액·가격 필드는 단위(센트인지, 소수 자리)를 반드시 확인한다. 2026-10 금액 API가 달러에서 센트로 바뀌었다. 종목 가격 단위는 `docs/db-design-stock.md`를 본다.

### 필요한 API가 없을 때

화면에 필요한데 백엔드에 없는 API(목록 조회, 단건 조회 등)는 **만들지 않는다.** 백엔드는 사용자가 직접 구현한다. 대신 스펙을 제안하고 멈춘다.

```
제안: GET /api/stocks
- 인증: 불필요
- 응답: [{ symbol, name, market, ... }]
- 에러: 없음 (빈 배열)
- 왜 필요한가: 주문 화면에서 종목을 고르려면 목록이 있어야 함
```

API가 생기기 전까지 임시로 하드코딩하거나 화면 안에서만 상태를 들고 가야 한다면, 그렇게 한다는 사실을 주석과 보고에 남긴다. (예: 입금 화면의 "원장 조회 API가 생기기 전까지 화면 안에서만 보여 준다")

## 2. 구현 — 기존 구조를 따른다

새 패턴을 만들기 전에 가장 비슷한 기존 기능(`features/deposit`이 가장 완성도 높은 예)을 먼저 읽고 같은 방식으로 쓴다.

**API 클라이언트** — `src/api/{도메인}.ts`
- `apiRequest<T>()`(`client.ts`)만 쓴다. `fetch`를 직접 부르지 않는다.
- 요청·응답 타입을 DTO와 같은 이름·필드로 정의하고, 단위와 범위를 JSDoc으로 적는다.
- 인증이 필요한 API는 `memberId`를 인자로 받는다. 조회 API는 `signal`을 받는다.
- 경로 변수는 `encodeURIComponent`로 감싼다.

**화면** — `src/features/{기능}/`
- `{기능}Page.tsx`: 화면. 회원은 `useMemberId()`로 얻고, `memberId === null`이면 안내 문구를 보인다.
- `{기능}Error.ts`: 에러 → 화면 표시 변환 순수 함수. **`code`로 분기**하고 메시지(`detail`) 문자열로 분기하지 않는다. 필드 에러는 `errors[].field`로 해당 입력칸에 붙인다.
- 입력 파싱·검증이 있으면 별도 순수 함수 파일로 뺀다. (예: `deposit/amount.ts`)
- `App.tsx`에 라우트를, `components/Header.tsx`에 네비게이션을 추가한다.
- 스타일은 `index.css`의 기존 클래스(`page`, `card` 등)를 재사용한다. UI 라이브러리 등 새 의존성은 사용자에게 묻고 추가한다.

**돈**
- 금액은 끝까지 정수 센트로 다룬다. 표시는 `lib/money.ts`의 `formatCents`, 입력은 정수 연산으로 센트 변환한다. `/ 100`, `* 100` 부동소수 계산을 쓰지 않는다.

**에러 상황 구분**
- 네트워크 오류(`NetworkError`)와 5xx는 "실패"가 아니라 **"결과를 모름"** 이다. 돈이 움직이는 요청(입금, 주문)이면 재시도 전에 상태를 다시 확인하라고 안내한다. (`depositError.ts`의 `uncertain` 참고)
- 남의 리소스와 없는 리소스를 서버가 같은 404로 응답하면 화면도 구분하지 않는다.

**시연용 개발 모드 기능**
- 서버 검증·권한 에러를 화면에서 일부러 낼 수 있게 하는 토글(클라이언트 검증 끄기, 다른 id로 보내기 등)은 이 프로젝트에서 쓸모가 크다. 의미 있는 에러 케이스가 있으면 제안하고, `import.meta.env.DEV`일 때만 보이게 한다.

## 3. 테스트와 검증

- 순수 함수(`*Error.ts`, 파싱·변환)는 Vitest 테스트를 같은 폴더에 `*.test.ts`로 쓴다. 에러 매핑은 계약에서 정리한 `code`마다 한 케이스씩, 네트워크 오류·5xx·Problem Details가 아닌 응답도 넣는다.
- 컴포넌트 렌더링 테스트는 쓰지 않는다. (현재 테스트 환경에 DOM 라이브러리가 없다)
- 끝나면 `frontend/`에서 차례로 돌린다.
  ```bash
  npm test && npm run lint && npm run build
  ```
  실패하면 고쳐서 다시 돌린다. 마지막 결과를 그대로 보고한다.
- 브라우저나 개발 서버를 띄우지 않는다. 화면 확인은 사용자가 `npm run dev`로 직접 한다.

## 4. 마무리

- `frontend/README.md`의 구조·개발 모드 전용 기능 섹션을 갱신한다.
- 보고:
  - 반영한 API 계약 (1장 형식)
  - 바꾼 파일 목록
  - 테스트·lint·build 결과
  - 화면에서 확인해 볼 시나리오 2~4개 (정상 1개 + 에러 케이스, 개발 모드 토글로 재현하는 법)
  - 제안한 API 스펙이나 임시 처리한 부분이 있으면 다시 나열
