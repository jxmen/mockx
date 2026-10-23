---
name: review-diff-before-commit
description: mockx에서 커밋하기 직전에 커밋 안 된 변경(diff)을 체크리스트로 점검하는 스킬. 이미 적용된 Flyway 마이그레이션 수정, 엔티티와 스키마 불일치(ddl-auto validate), docs 작업 목록·에러 code 표와 코드의 불일치, 프론트 타입과 백엔드 DTO 불일치, 남은 디버그 코드·.only·@Disabled, 섞인 변경을 찾고, 관련 테스트를 돌린 뒤 커밋 분리안과 한국어 커밋 메시지 초안을 준다. "커밋 전에 봐줘", "커밋해도 돼?", "커밋 메시지 써줘", "빠뜨린 거 없나 봐줘", "diff 점검", "올리기 전에 체크"처럼 커밋 직전 점검을 원하면 사용한다. 코드 설계·품질 리뷰는 hint-review가 맡는다.
---

# review-diff-before-commit

커밋 직전에 "고치기 쉬운데 커밋 후엔 비싸지는" 실수를 잡는다. 코드가 잘 짜였는지는 보지 않는다(그건 `hint-review`). **규칙 위반, 빠뜨린 것, 어긋난 것**만 본다.

파일을 고치지 않는다. 커밋도 사용자가 명시적으로 요청할 때만 한다. 백엔드(`src/main/kotlin`) 문제는 위치와 이유만 짚는다.

## 1. 범위 파악

```bash
git branch --show-current
git status --short
git diff --cached --stat        # 스테이징된 것
git diff --stat                 # 스테이징 안 된 것
git ls-files --others --exclude-standard   # 새 파일
```

- 스테이징된 것이 있으면 그것이 이번 커밋 범위다. 스테이징 안 된 변경은 "이번 커밋에 빠진 것"으로 따로 본다. 스테이징된 게 없으면 변경 전체를 범위로 본다.
- 새 파일(untracked)이 빠졌는지 확인한다. 새 마이그레이션·테스트 파일을 `git add` 안 하는 실수가 흔하다.

## 2. 체크리스트

해당하는 항목만 본다. 각 항목은 **막음 / 확인 필요 / 통과**로 판정한다.

### 마이그레이션 (`src/main/resources/db/migration`)
- **이미 있던 마이그레이션 파일이 수정됐는가** → 막음. 적용된 마이그레이션을 고치면 Flyway checksum 검증이 실패한다. 새 버전 파일로 변경해야 한다. `git diff main...HEAD --name-status`와 작업 트리 변경에서 `M`, `D`, `R` 상태인 기존 `V*.sql`을 찾는다. 이번 브랜치에서 새로 만들고 아직 main에 없는 파일이면 수정해도 된다고 구분해서 알린다.
- 버전 번호가 중복되거나 건너뛰었는가.
- 마이그레이션의 테이블·컬럼과 엔티티 매핑이 맞는가. `ddl-auto: validate`라 어긋나면 앱이 뜨지 않는다. 컬럼 이름·타입·nullable을 엔티티 필드와 대조한다.

### 문서 (`docs/`)
- 이번 변경으로 완료된 기능이 docs 작업 목록에서 아직 `- [ ]`인가 → 확인 필요. 갱신은 `design-note`로 안내한다.
- 코드에 새로 생긴 에러 `code`(`ApiException` 하위 예외)가 docs의 에러 응답 표에 있는가. 반대로 docs에만 있는 code는 없는가.
- 요청 검증 규칙(범위, 길이)이 docs와 같은가.
- 어긋나면 어느 쪽이 맞는지 판단하지 않고 묻는다.

### 프론트와 백엔드 계약
- 백엔드 DTO가 바뀌었는데 `frontend/src/api/*.ts`의 타입이 그대로인가 → 확인 필요. 반영은 `frontend-sync`로 안내한다.
- 새 에러 code를 프론트 `*Error.ts`가 다루는가.

### 테스트 코드
- `.only`, `fdescribe`, `fit`, `xit`, `xdescribe`, `@Disabled`, `enabled = false`, `.config(enabled = false)`가 새로 들어갔는가 → 막음(의도한 거라면 이유를 묻는다).
- 백엔드 코드는 바뀌었는데 관련 테스트 변경이 없는가 → 확인 필요. 추가는 `test-first`로 안내한다.

### 남은 흔적
변경에서 **추가된 줄만** 본다(`git diff -U0 | grep '^+'`).
- `println`, `console.log`, `debugger`, 주석 처리된 코드 블록
- 새로 생긴 `TODO`, `FIXME` → 막지 않고 목록으로 알린다
- 비밀번호·토큰·키로 보이는 값, `.env`·로컬 설정 파일

### 섞인 변경
- 한 커밋에 서로 관련 없는 변경(예: 기능 구현 + 다른 기능 문서 + 프론트 리팩터링)이 섞였으면 커밋 분리안을 제안한다. 파일 단위로 묶어 순서까지 제시한다.

## 3. 테스트 실행

- 백엔드 변경이 있으면 관련 테스트 클래스만 먼저 돌린다. Testcontainers라 전체는 느리다.
  ```bash
  ./gradlew test --tests 'xyz.jxmen.mockx.account.*'
  ```
  마이그레이션·엔티티·공통 설정(`common/`)이 바뀌었으면 전체(`./gradlew test`)를 돌린다.
- 프론트 변경이 있으면 `frontend/`에서 `npm test && npm run lint && npm run build`.
- 실패하면 실패한 케이스와 메시지 첫 줄을 그대로 보고한다. 미구현이라 실패하는 것(test-first로 미리 쓴 테스트)이면 그렇게 구분한다. 고치지 않는다.

## 4. 보고

```
## 커밋 전 점검 — feature/buy-stock (스테이징 4파일)

🚫 막음
- src/main/resources/db/migration/V2__create_ledger.sql 수정됨 — main에 이미 있는 마이그레이션. V5로 새로 만들어야 함

⚠️ 확인 필요
- docs/db-design-stock.md 작업 목록 "Stock 엔티티 매핑" 미체크
- StockController 추가됐는데 테스트 변경 없음

✅ 통과: 엔티티·스키마 일치, 디버그 코드 없음, 테스트 12/12

**커밋 분리 제안**
1. Stock 엔티티 매핑 및 repository 추가 — Stock.kt, StockRepository.kt, V4__create_stock.sql
2. 종목 문서 갱신 — docs/db-design-stock.md

**커밋 메시지 초안**
Stock 엔티티 매핑 및 repository 추가
```

- 막음이 없으면 첫 줄에 "커밋해도 됩니다"라고 쓴다.
- 커밋 메시지는 이 저장소의 기존 스타일을 따른다(`git log --oneline -10`으로 확인). 한국어, 짧은 명사형 제목. 필요할 때만 본문을 붙인다.
- 보고 내용 중 "막음"과 "확인 필요"를 `/tmp/review-diff-<브랜치명>-<YYYYMMDD-HHMM>.md`에 "손볼 목록"으로 저장한다(브랜치명의 `/`는 `-`). `resume-task`가 이 파일을 읽는다. 손볼 것이 없으면 저장하지 않는다.

## 5. 커밋을 요청받으면

- 사용자가 "커밋해줘"라고 명시하면 초안 메시지로 커밋한다. 막음 항목이 남아 있으면 커밋하기 전에 한 번 더 알린다.
- `git add -A`로 몰아 담지 않고 범위에 든 파일을 명시해서 담는다.
- push는 따로 요청받을 때만 한다.
