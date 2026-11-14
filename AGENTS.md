<!-- 이 파일은 CLAUDE.md와 같은 내용입니다 (Cursor, Codex 등 다른 AI 도구용). 수정할 땐 둘 다 고치세요. -->

# 아까 그곳 — AI 작업 공통 규칙

이 저장소는 현대차 Pleos Connect 차량용 앱 공모전 데모 "아까 그곳"이다.
운전 중 "방금 오른쪽에 있던 빵집 뭐야?"라고 물으면 차가 지나온 길을 되짚어 그 가게를 찾아
음성으로 답하고 작은 카드 한 장만 띄운다. 두 명이 모듈을 나눠 만든다.

## 작업 전에 반드시
1. `git branch --show-current`로 브랜치를 확인하고 역할을 정한다
   - `chore/skeleton`, `feature/engine-*` → A(엔진) 규칙: `engine/CLAUDE.md`, `engine/PROMPT.md`
   - `feature/ui-*` → B(화면) 규칙: `ui/CLAUDE.md`, `ui/PROMPT.md`
   - `chore/integration` → 합치기: `docs/MERGE_PROMPT.md`
   - `main` → 코드를 고치지 말고, 사용자에게 브랜치부터 만들라고 안내한다
2. `contract/src/main/kotlin/com/akkageugot/demo/contract/Contract.kt`와 `docs/CONTRACT.md`를 읽는다

## 폴더 소유
| 폴더 | 담당 | 내용 |
|---|---|---|
| `contract/` | 공통 (동결) | 엔진과 화면의 계약 |
| `engine/` | A | 시뮬레이터, 역추적, 대화, Pleos SDK, 저장, adb 명령 |
| `app/` | A | Application, fake/real 엔진 선택 |
| `scripts/` | A | 발표용 .bat, route.gpx |
| `ui/` | B | 데모 내비, 카드, 정차 화면, 설정, 가짜 엔진 |
| 루트 gradle 파일, `gradle/libs.versions.toml` | 공통 | 줄 추가만. 기존 줄 수정은 상대 동의 |
| `docs/`, `MERGE_NOTES.md` | 공통 | |

- 남의 폴더는 읽기만 한다. 고칠 게 있으면 `MERGE_NOTES.md`에 적는다
- 예외: `chore/skeleton`(A의 0단계)에서는 `ui/` 뼈대 파일을 만든다. `ui/*.md`는 건드리지 않는다
- `Contract.kt`는 수정하지 않는다. 변경이 필요하면 `MERGE_NOTES.md`에 제안만 적는다

## 공통 원칙
- 엔진만 상태를 바꾼다. 화면은 `AppState`를 그리고 `UiAction`을 보내기만 한다
- Pleos SDK 시그니처를 추측하지 않는다. `docs/PLEOS_SDK_NOTES.md`와 https://document.pleos.ai 를 확인하고, 불확실하면 TODO + 목업으로 둔다
- 개발 환경은 Windows + cmd. PowerShell 명령을 안내하지 않는다. `.bat` 파일에는 한글을 쓰지 않는다 (코드페이지 깨짐)
- 단계 하나가 끝나면 멈추고 사용자에게 확인받는다. git 커밋/푸시는 사용자가 요청할 때만 한다
- 키, 계정 같은 비밀값은 커밋하지 않는다 (`local.properties` 또는 `%USERPROFILE%\.gradle\gradle.properties`)
- 커밋 메시지: `feat(engine): ...`, `feat(ui): ...`, `fix(...)`, `chore: ...`, `docs: ...` (설명은 한국어 OK)
