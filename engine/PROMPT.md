# 엔진 담당(A) 작업 지시서

AI에게: 사용자가 "N단계 진행해"라고 하면 그 단계만 하고 멈춰서 결과를 보고한다. 커밋/푸시는 사용자가 요청할 때만.

## 먼저 읽을 파일
- `CLAUDE.md`(루트), `engine/CLAUDE.md`
- `contract/src/main/kotlin/com/akkageugot/demo/contract/Contract.kt`, `docs/CONTRACT.md`
- `docs/PLEOS_SDK_NOTES.md`

## 1. 프로젝트와 내 역할
현대차 Pleos Connect(AAOS 기반) 차량용 앱 공모전 데모 "아까 그곳". Pleos Connect Emulator에서 실행한다.
운전자가 주행 중 "어, 방금 오른쪽에 있던 빵집 뭐야?"라고 말하면 차가 지나온 길을 되짚어 그 가게를 찾아
음성으로 답하고 작은 카드 한 장을 띄운다. 에뮬레이터에선 차가 안 움직이므로 주행과 내비는 앱 안에서 시뮬레이션한다.
- 나(A): 엔진. 화면은 만들지 않는다 (팀원 B가 `ui/`에서 가짜 엔진으로 따로 개발 중)
- 엔진은 `AppState` 하나를 `StateFlow`로 내보내고 `UiAction`만 받는다. 타이머와 판단은 모두 엔진에서 한다
- 외부 지도/장소 API는 쓰지 않는다. 가게는 목업 데이터
- Pleos SDK는 실제로 호출하되 전부 인터페이스 뒤에 두고 목업 구현을 같이 만든다. 실패하면 자동 대체

## 2. 모듈 구조
| 모듈 | 종류 | namespace / applicationId | 담당 |
|---|---|---|---|
| `:contract` | Kotlin JVM 라이브러리 | `com.akkageugot.demo.contract` | 공통(동결). Contract.kt 이미 있음 |
| `:engine` | Android 라이브러리 | `com.akkageugot.demo.engine` | A |
| `:ui` | Android 라이브러리 | `com.akkageugot.demo.ui` | 0단계 뼈대만 A, 이후 B |
| `:app` | Android 앱 | `com.akkageugot.demo` | A |

의존: engine → contract, ui → contract, app → contract + engine + ui

## 3. 0단계 — 프로젝트 뼈대 (브랜치 `chore/skeleton`)
1. Gradle 멀티모듈 + `gradle/libs.versions.toml` + gradle wrapper. Kotlin 1.9 이상(`data object` 사용)
   - 이미 있는 파일(`CLAUDE.md`, `AGENTS.md`, `README.md`, `docs/`, `contract/`의 Contract.kt, `engine/*.md`, `ui/*.md`, `.github/`)은 지우거나 옮기지 않는다
2. `settings.gradle.kts`: `include(":contract", ":engine", ":ui", ":app")`,
   repositories에 `google()`, `mavenCentral()`, Pleos Maven(`docs/PLEOS_SDK_NOTES.md`)
3. `:contract`: `contract/build.gradle.kts`(kotlin jvm + kotlinx-coroutines-core). Contract.kt를 고치지 않고 컴파일되는지 확인
4. `:app`
   - productFlavors: `flavorDimensions += "engine"`
     - `fake { buildConfigField("boolean", "USE_FAKE_ENGINE", "true") }`
     - `real { buildConfigField("boolean", "USE_FAKE_ENGINE", "false") }`
   - `buildFeatures { buildConfig = true }`
   - `AkkaApp : Application`:
     `EngineProvider.engine = if (BuildConfig.USE_FAKE_ENGINE) FakeDemoEngine(this) else RealDemoEngine(this)` 후 `EngineProvider.engine.start()`
5. `:engine` 뼈대
   - `useLibrary("android.car")`, Pleos SDK 의존성(SpeechToText, TextToSpeech, NaviHelper, Vehicle — 좌표와 버전은 문서 확인)
   - `RealDemoEngine(context: Context)`: `emptyAppState()`만 내보내는 스텁
   - AndroidManifest: `service.AkkaService`(포그라운드), `demo.DemoCommandReceiver`(exported),
     `probe.EngineProbeActivity`(런처, 라벨 "엔진 테스트"), 권한은 실제로 쓸 것만
6. `:ui` 뼈대 (이것만 만들고 이후 손대지 않는다. `ui/CLAUDE.md`, `ui/PROMPT.md`는 건드리지 않는다)
   - Compose 설정
   - `com.akkageugot.demo.ui.fake.FakeDemoEngine(context: Context)`: `emptyAppState()`만 내보내는 스텁
   - `com.akkageugot.demo.ui.DemoActivity`: `EngineProvider.engine.state`를 텍스트로 찍는 임시 화면
   - ui 모듈 AndroidManifest: DemoActivity에 `screenOrientation="landscape"`,
     `<meta-data android:name="distractionOptimized" android:value="true"/>`, 런처 라벨 "아까 그곳"
7. 에뮬레이터에서 `fakeDebug`, `realDebug` 둘 다 실행 확인 → 멈추고 보고 (사용자가 PR 올림)

## 4. engine 모듈 내부 구조 (`com.akkageugot.demo.engine`)
```
platform/   인터페이스 + pleos/ 구현 + mock/ 구현
  SpeechInput     start(), stop(), results: Flow<String>      pleos: SpeechToText / mock: TypedUtterance·adb 입력
  SpeechOutput    speak(text)                                  pleos: TextToSpeech / mock: Android TextToSpeech
  Navigation      setDestination, addWaypoint, events          기본: DemoNavigation / 옵션: NaviHelper 미러링
  VehicleSignals  gear, speedKmh                               AAOS CarPropertyManager + Vehicle SDK Brake / mock: 시뮬레이터
  UxRestrictions  restricted: Flow<Boolean>                    AAOS CarUxRestrictionsManager / mock: gear==D && speed>0
sim/SimulatedDrive
core/        Geo, TrailBuffer, Backtracker, IntentParser, DialogMachine, RevisitWatcher, DemoRoutePlanner  ← 순수 Kotlin
RealDemoEngine.kt
service/AkkaService
data/        ScenarioLoader, SavedPlaceRepository(filesDir JSON, kotlinx.serialization)
demo/DemoCommandReceiver
probe/EngineProbeActivity   AppState 텍스트 덤프 + 버튼(말하기, 프리셋 발화, 기어, 시나리오)
```

## 5. 시나리오 데이터 (`engine/src/main/assets/scenarios.json`)
- `route`: 서울 시내 실제 도로를 따라가는 위경도 점 목록 (약 4km, 직진 + 우회전 1 + 좌회전 1, 마지막 1km는 가게 없는 직선이라 답 카드가 도착 전에 닫힌다)
  → `Geo`가 경로 시작점 기준 equirectangular 근사로 로컬 미터 변환
- `pois`: 좌표 대신 경로 기준으로 정의하고 앱이 좌표를 계산
  `{ id, name, category, distanceAlongRouteM, side: "right"|"left", offsetM(12~35), open }`
  - 실존 브랜드가 아닌 지어낸 이름만 (달빛제과, 오후세시커피, 골목국수, 초록꽃집 등), 약 30개
  - 반드시 포함:
    (a) 오른쪽에 빵집 하나만 확실한 구간
    (b) 빵집과 카페가 붙은 구간
    (c) 왼쪽 빵집, 오른쪽 카페가 마주 보는 구간
    (d) 가게 데이터가 없는 구간 (새로 생긴 가게 상황)
- `scenarios`: 1~5번 각각 시작 지점(distanceAlongRouteM), 자동 발화 순서와 타이밍
- 같은 route로 `scripts/route.gpx` 생성 (실제 내비 연동 테스트용)

## 6. 핵심 로직 (`core/`, 순수 Kotlin, JUnit 필수)

### TrailBuffer
- 1초마다 `{t, pos, speedMps, headingRad}` 저장, 최근 10분만 유지, 기기 밖으로 보내지 않는다

### t0 고정
- `MicPressed`(또는 adb say)를 받은 순간이 t0. 발화가 끝난 시각이 아니다
- "아까 N분 전"이면 t0를 N분 앞으로. "아까"만 있으면 탐색 폭 2배

### Backtracker
- 탐색 구간: t0 기준 뒤쪽 `clamp(속도 × 8초, 30m, 250m)`, 앞쪽 `속도 × 1초` → `debug.corridor`로 내보낸다
- 구간 궤적 선분에서 수직 거리 60m 이내 가게가 후보. d = 최단 거리, tPass = 가장 가까웠던 시각
- 좌우: 그 시점 진행 방향 h = (hx, hy), 차→가게 (dx, dy), `cross = hx*dy - hy*dx` → > 0 LEFT, < 0 RIGHT
- `score = 0.35*(1 - d/60) + 0.25*방향 + 0.25*업종 + 0.15*시간`
  - 방향: 일치 1, 불일치 0, 말 안 함 → RIGHT 0.6 / LEFT 0.4 (우측통행 가중)
  - 업종: 일치 1, 불일치 0, 말 안 함 0.5
  - 시간: `(t0 - tPass)`가 2~8초면 1, 0초와 15초 쪽으로 선형 감소해 0
- 확신도: HIGH(1위 ≥ 0.75 && 2위와 차이 ≥ 0.15), MEDIUM(1위 ≥ 0.5), LOW(위치만 저장). 상위 3개 반환
- 테스트: (a)~(d) 상황 + 속도별 탐색 구간 길이

### IntentParser (규칙 기반. 나중에 LLM SDK로 바꿀 수 있게 인터페이스 유지)
- type: ASK(뭐야/뭐였지/뭐지) | SAVE(저장) | VISIT(들르/들렀다/가자/거기로) | REJECT(아니/그 옆/다른 거) | MEMO
- category: 빵집/베이커리/제과→BAKERY, 카페/커피→CAFE, 식당/밥집/국수→RESTAURANT, 꽃집→FLOWER, 편의점→CONVENIENCE
- side(오른쪽/왼쪽/없음), timeOffsetSec. ASK와 SAVE가 같이 오면("방금 빵집 저장해줘") 바로 저장
- 결과 JSON → `debug.lastIntentJson`

### DialogMachine
IDLE → LISTENING → THINKING → ANSWERED(headsUp 표시, 6초 대기)
- SAVE 또는 [저장] → "저장했어요. 도착하면 보여드릴게요." → CONFIRMED 저장
- VISIT 또는 [들르기] → `Navigation.addWaypoint` → "경유지로 넣었어요. N분 늘어나요."
- REJECT → 다음 후보로 headsUp 교체. 두 번 거절되면 "도착하면 같이 골라요." → PENDING 저장
- 6초 무응답 → 음성 없이 PENDING으로 자동 저장 (절대 잃어버리지 않기)
- 응답 문장
  - HIGH: "달빛제과요. 저장할까요, 들렀다 갈까요?"
  - MEDIUM: "아마 달빛제과 같아요. 옆에 오후세시커피도 있었어요."
  - LOW: "지도에선 못 찾았어요. 위치는 저장해 둘게요." → LOCATION_ONLY, headsUp.category = null
- 발화와 응답은 `voiceLines`에 넣고 4초 뒤 제거 (최대 2개)
- `subtitle`은 "업종 · 방향 · Nm 지나옴" 형식. 만드는 순간 값 고정

### RevisitWatcher
- CONFIRMED 장소가 진행 방향 300m 앞, 경로에서 60m 이내면 REVISIT headsUp + TTS
  "지난번 저장한 달빛제과가 300m 앞 오른쪽이에요. 들를까요?" — 같은 곳은 하루 1번

### DemoRoutePlanner (데모 내비 경로 계산)
- `addWaypoint(poi)`: 현재 위치 → 가게 앞 도로 지점 → 원래 경로 합류점(가게 지나 200m)으로 새 route (단순 직선 연결 허용).
  `map.waypoint` 설정, `etaDelayMin` 계산
- 가게 20m 이내 도착 시 waypoint 해제, 원래 경로로 복귀
- `setDestination(savedPlace)`: 현재 위치 → 선택한 가게로 단순 경로

## 7. RealDemoEngine
- 주행 틱 100ms(위치 보간, state 최대 10Hz), TrailBuffer는 1초 간격
- SimulatedDrive: `targetSpeedKmh`로 주행, 회전 지점 50m 전부터 20km/h로 감속 후 다시 가속, `timeScale` 배속,
  route가 바뀌면 새 경로를 따라감, 목적지 도착 시 gear = P
- `drive.restricted`: UX SDK를 쓰면 CarUxRestrictions 값, 아니면 `gear == D && speed > 0`
- `nextTurn`, `distanceToNextTurnM`, `remainingM`, `etaText`, `etaDelayMin` 계산
- Navigation 기본 구현 = DemoNavigation(DemoRoutePlanner). `settings.mirrorToRealNavi = true`면
  NaviHelper.requestRoute / addWaypoint도 같이 호출하고 콜백 결과를 logcat과 `debug.sdkStatus["NAVI"]`에 표시
- 음성: MicPressed → t0 고정 → SpeechInput.start (Pleos STT, 실패 시 목업: TypedUtterance/adb 입력 대기)
  → MicReleased → stop → 결과로 DialogMachine 진행. 응답은 SpeechOutput (Pleos TTS, 실패 시 Android TextToSpeech)
- `settings.useSdk`로 컴포넌트별 SDK/목업 전환, 선택은 저장해서 재시작해도 유지
- 저장 목록은 SavedPlaceRepository에 영속화
- 1초마다 logcat 태그 `AKKA`로 상태 요약 출력

## 8. 발표용 adb 명령 (DemoCommandReceiver)
- 액션 `com.akkageugot.demo.DEMO`, 명시적 컴포넌트로 호출:
  `-n com.akkageugot.demo/com.akkageugot.demo.engine.demo.DemoCommandReceiver`
- 한글은 adb로 보내지 않고 번호로 받는다 (Windows cmd 인코딩 문제)
  - `--es cmd say --ei preset N`
    1 "방금 오른쪽에 있던 빵집 뭐야?" · 2 "저장해" · 3 "들렀다 가자" · 4 "아니 그 옆에" ·
    5 "아까 3분 전에 지나친 카페 뭐였지?" · 6 "방금 거 뭐야?"
  - `--es cmd scenario --ei n 1~5`
  - `--es cmd gear --es value P|D`
  - `--es cmd speed --ei kmh 50`
- `say`는 MicPressed → TypedUtterance → MicReleased와 똑같은 경로로 처리 (t0 = 명령 받은 순간)
- `scripts/` 폴더에 `.bat` 생성 (한글 금지): `say1.bat`~`say11.bat`, `scenario1.bat`~`scenario6.bat`, `park.bat`, `drive.bat`
  ```
  adb shell am broadcast -n com.akkageugot.demo/com.akkageugot.demo.engine.demo.DemoCommandReceiver -a com.akkageugot.demo.DEMO --es cmd say --ei preset 1
  ```

## 9. 시나리오 (RunScenario n)
시작 시 목적지 = 경로 끝점 → 해당 지점 직전에서 출발 → 가게를 지나고 3초 뒤 준비된 발화 자동 입력
1. 확실한 빵집 → 질문 → "저장해" → 목적지 도착 → P
2. 확실한 빵집 → 질문 → "들렀다 가자" → 경유지
3. 빵집 + 카페 → 질문 → "아니 그 옆에" → 2순위
4. 데이터 없는 구간 → 질문 → 위치만 저장
5. 1번에서 저장한 곳 재통과 → REVISIT

## 10. 단계표 (각 단계는 main에서 새 브랜치)
| 단계 | 브랜치 | 내용 |
|---|---|---|
| 0 | `chore/skeleton` | 프로젝트 뼈대 (3절) |
| 1 | `feature/engine-core` | `core/` 전체 + JUnit |
| 2 | `feature/engine-sim` | SimulatedDrive + RealDemoEngine 틱 루프 + EngineProbeActivity |
| 3 | `feature/engine-dialog` | DialogMachine 연결 + 목업 음성 + adb 명령 + `.bat` |
| 4 | `feature/engine-voice` | Pleos STT/TTS 연결 (실패 시 목업 자동 대체) |
| 5 | `feature/engine-navi` | DemoNavigation / DemoRoutePlanner + NaviHelper 미러링 |
| 6 | `feature/engine-scenario` | RevisitWatcher + 시나리오 1~5 자동 재생 + 저장 영속화 |

## 11. 완료 기준
- [ ] realDebug + EngineProbeActivity에서 `scenario1.bat`~`scenario6.bat`이 끝까지 돈다 (logcat으로 확인)
- [ ] Backtracker 테스트 통과, 속도에 따라 corridor 길이가 바뀐다
- [ ] 어떤 SDK가 실패해도 목업으로 대체되어 끝까지 돈다
- [ ] `mirrorToRealNavi = true`일 때 NaviHelper 콜백이 로그에 찍힌다
- [ ] `Contract.kt`는 0단계 이후 한 줄도 안 바뀌었다
- [ ] `ui/`는 0단계 뼈대 이후 건드리지 않았다
