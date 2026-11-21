# mockx Claude Code 스킬

백엔드는 직접 구현하고, Claude는 선택지 정리·리뷰 힌트·테스트·시연용 프론트를 맡도록 역할을 나눈 스킬 모음입니다.

## 결정과 구현은 내가, Claude는 보조
- **[design-note](design-note/SKILL.md)**: 설계 결정을 상황 → 선택지 → 트레이드오프 → 선택 순서로 문서화 (결정은 사람이 함)
- **[domain-primer](domain-primer/SKILL.md)**: 증권 개념을 "실제 시장 → mockx의 최소 구현 → 미룰 것" 순서로 설명
- **[hint-review](hint-review/SKILL.md)**: 내가 짠 백엔드 코드를 고치지 않고 문제 지점과 힌트만 단계적으로 제시

## Claude가 작성
- **[test-first](test-first/SKILL.md)**: 구현 전에 정상·경계·동시성 시나리오를 제안하고, 내가 고른 케이스만 실패하는 Kotest로 작성
- **[frontend-from-api](frontend-from-api/SKILL.md)**: 백엔드 API(DTO·에러 code)를 읽고 시연용 프론트와 Vitest 테스트 구현

## 작업 흐름
- **[resume-task](resume-task/SKILL.md)**: 브랜치·WIP·TODO·미완료 작업을 모아 이어서 할 지점 요약
- **[review-diff-before-commit](review-diff-before-commit/SKILL.md)**: 커밋 전 diff 점검 (Flyway 수정, 스키마 불일치, DTO 타입 불일치 등)과 커밋 분리안 제시
