# mockx frontend

백엔드 기능 시연용 화면. React + Vite + TypeScript.

## 실행

```bash
# 1. 백엔드 실행 (저장소 루트)
./gradlew bootRun

# 2. 프론트 실행 (frontend/)
npm install
npm run dev   # http://localhost:5173
```

`/api` 요청은 Vite dev 서버가 `http://localhost:8080`으로 프록시한다(`vite.config.ts`). 그래서 백엔드에 CORS 설정이 필요 없다.

## 명령어

```bash
npm run dev     # 개발 서버
npm test        # 단위 테스트 (Vitest)
npm run lint    # oxlint
npm run build   # 타입 검사 + 빌드
```

## 구조

```
src/
  api/          fetch 래퍼(Problem Details 파싱), API 함수
  auth/         X-Member-Id 개발용 인증 스텁 (localStorage 저장)
  components/   공통 컴포넌트 (헤더)
  features/     화면 단위 기능 (signup, me, deposit)
  lib/          순수 함수 (계좌번호 표시 등)
```

## 개발 모드 전용 기능

- 헤더의 `X-Member-Id` 입력칸: 로그인 대신 회원을 전환한다. 가입에 성공하면 새 회원 ID로 바뀐다.
- 회원가입 폼의 "클라이언트 검증 끄기": 서버 검증(400 `INVALID_INPUT`)을 화면에서 직접 확인할 때 쓴다.

- 입금 화면의 "클라이언트 검증 끄기": 소수, 음수, 문자도 그대로 보내 서버 검증(400 `INVALID_INPUT`)을 확인한다.
- 입금 화면의 "다른 계좌 id로 보내기": 남의 계좌·없는 계좌로 보내 소유자 검증(404 `ACCOUNT_NOT_FOUND`)을 확인한다.

모두 `import.meta.env.DEV`일 때만 보인다.
