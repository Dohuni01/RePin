# 아까 그곳

현대차그룹 Pleos Connect 차량용 앱 공모전 데모.

운전 중 "어, 방금 오른쪽에 있던 빵집 뭐야?"라고 물으면, 차가 지나온 길을 되짚어 그 가게를 찾아
음성으로 답하고 내비 위에 작은 카드 한 장만 잠깐 띄운다. 후보 확인은 정차했을 때만 한다.

## 구조

```
contract/   엔진 ↔ 화면 계약 (공통, 동결)
engine/     A: 시뮬레이터, 역추적, 대화, Pleos SDK, 저장, adb 명령
ui/         B: 데모 내비, 카드, 정차 화면, 설정, 가짜 엔진
app/        앱 진입점, fake/real 엔진 선택          (0단계에서 생성)
scripts/    발표용 .bat, route.gpx                   (A가 생성)
docs/       작업 방법, 계약 설명, SDK 메모, 합치기 지시
```

## 실행

1. Android Studio에서 저장소 루트 폴더를 열고 Gradle Sync (Gradle JDK는 17~21)
2. Build Variant (Build > Select Build Variant > `:app`)
   - `realDebug` (기본): 진짜 엔진 + 화면. Pleos SDK가 안 되면 목업으로 자동 대체
   - `fakeDebug`: 가짜 엔진 + 화면. 화면 개발용
3. Pleos Connect Emulator(API 34 이상, 가로)에서 Run. 런처의 "아까 그곳"이 데모 화면,
   "엔진 테스트"는 debug 빌드에만 있는 엔진 확인 화면
4. 처음엔 정차(P) 상태. 화면 오른쪽 톱니 > 시나리오 1~5, 또는 cmd에서
   `scripts\scenario1.bat` ~ `scenario5.bat`, `drive.bat` / `park.bat`, `say1.bat` ~ `say6.bat` (realDebug 전용)

## 작업 방법

- GitHub 진행 방법: `docs/WORKFLOW.md`
- AI 도구는 루트 `CLAUDE.md`(Cursor, Codex 등은 `AGENTS.md`)를 먼저 읽는다
- A: "engine/PROMPT.md 읽고 N단계 진행해"
- B: "ui/PROMPT.md 읽고 N단계 진행해"
- 마지막 합치기: "docs/MERGE_PROMPT.md 읽고 진행해"
