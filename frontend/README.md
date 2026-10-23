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

## 랜딩(`/`)

면접관용 시연 순서와 구현 현황을 보여 준다. 구현 현황은 `features/home/HomePage.tsx`의 `PROGRESS`에 손으로 적은 값이라, 기능을 끝내면 `docs/` 작업 목록과 함께 고친다.

## 화면 스타일

토스증권 화면을 기준으로 한 라이트/다크 테마다. 색은 `src/index.css`의 `:root`(라이트)와 `:root[data-theme='dark']`(다크) 토큰으로만 바꾼다. 글꼴은 Pretendard(CDN)를 쓴다.

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
  components/   공통 컴포넌트 (헤더, 테마 전환 버튼)
  features/     화면 단위 기능 (home, signup, me, deposit, stocks)
  lib/          순수 함수 (계좌번호 표시 등)
  theme/        라이트/다크 테마. 처음엔 브라우저(OS) 설정을 따르고, 헤더 버튼으로 바꾼 값은 localStorage(`mockx.theme`)에 남긴다
public/
  stocks/       종목 아이콘. 파일 이름은 심볼 대문자 그대로(`MU.png`). 배포 서버는 대소문자를 구분한다
```

## 개발 모드 전용 기능

- 헤더의 `X-Member-Id` 입력칸: 로그인 대신 회원을 전환한다. 가입에 성공하면 새 회원 ID로 바뀐다.
- 회원가입 폼의 "클라이언트 검증 끄기": 서버 검증(400 `INVALID_INPUT`)을 화면에서 직접 확인할 때 쓴다.

- 입금 화면의 "클라이언트 검증 끄기": 소수, 음수, 문자도 그대로 보내 서버 검증(400 `INVALID_INPUT`)을 확인한다.
- 입금 화면의 "다른 계좌 id로 보내기": 남의 계좌·없는 계좌로 보내 소유자 검증(404 `ACCOUNT_NOT_FOUND`)을 확인한다.
- 종목 화면의 "sort 직접 입력": 허용하지 않은 필드·잘못된 방향·여러 개 정렬을 보내 정렬 검증(400 `INVALID_INPUT`)을 확인한다. 공백으로 나누면 `sort=`를 여러 번 보낸다.
- 종목 상세 화면(`/stocks/:symbol`)의 심볼 예시: `mu`(200, 대문자로 찾음), `AAPL`·10자(404 `STOCK_NOT_FOUND`), 11자·숫자·`mü`·`ſpy`(400 `INVALID_INPUT`)로 이동한다. 상세 화면은 심볼을 바꾸지 않고 서버에 그대로 보내므로 주소창에 직접 입력해도 된다.

모두 `import.meta.env.DEV`일 때만 보인다.
