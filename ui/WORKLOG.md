# WORKLOG (ui/, 화면 담당 B)

최신 항목이 위에 온다. 2026-11-15부터 앱 이름은 RePin이다.

## 2026-11-15 — 앱 아이콘(되돌아 찍는 핀)
- 사용자가 시안 2번 "되돌아 찍는 핀"을 골랐다: 산호색 그라데이션 배경(#FF8A6B → #E2555E), 핀을 감싸고 뒤로 도는 흰 화살표, 남색(#2B2D5C) 핀.
- minSdk 34라 적응형 아이콘(벡터)만 둔다. 비트맵 mipmap 없음. 단색(테마 아이콘)은 전경을 그대로 쓴다.
- 파일:
  - `app/src/main/res/drawable/ic_launcher_background.xml`
  - `app/src/main/res/drawable/ic_launcher_foreground.xml`
  - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml`
  - `app/src/main/AndroidManifest.xml`: `android:icon`, `android:roundIcon` 추가

## 2026-11-15 — "옆에 뭐 있어?" 버튼 복원 + 오른쪽/왼쪽 버튼
- 실시간 테스트용으로 주행 지도의 보라색 "옆에 뭐 있어?" 버튼을 다시 넣었다(사용자 요청). 자리는 시나리오 중단 버튼 아래, 홈 버튼 위.
- 그 아래에 [왼쪽] [오른쪽] 알약 버튼(`navi/NaviTextPillButton`, 아이콘 없음). 누르면 말로 대답한 것과 같은 경로(`MicPressed` → `TypedUtterance("오른쪽")` → `MicReleased`, adb say와 같은 순서)로 보낸다.
- `core/DialogMachine.kt`: 되묻는 중이 아닐 때 "오른쪽", "왼쪽이요"처럼 좌우만 말하면(공백 빼고 6자 이하) 그쪽 "옆에 뭐 있어?"로 바로 답한다. 그래서 [오른쪽]만 눌러도 오른쪽 건물을 알려 준다. 되묻는 중이면 기존대로 대답으로 처리한다. "오른쪽 차선 막혔네" 같은 긴 말은 무시.
- 안내 문구 띄어쓰기·조사 정리: "왼쪽 에 꽃과바람가 있어요." → "왼쪽에 꽃과바람이 있어요." (`BuildingText.nearbyOne/nearbyPair/nameAnswer`, 받침에 맞춰 이/가, 이요/요). 다시 지나감 안내("지난번 저장한 …이/가 …m 앞 오른쪽이에요")도 같은 조사 함수를 쓴다.

## 2026-11-15 — "옆에 뭐 있어?" 좌우 되묻기, 시연 시나리오 다시 짜기 (feature/side-and-building)
- "옆에 뭐 있어?"(말하기든 지도 버튼이든)는 먼저 "오른쪽이요, 왼쪽이요?"라고 묻고, 대답한 쪽의 건물(가게)을 알려 준다. 기준점은 처음 물어본 순간이다. 처음부터 "오른쪽에 뭐 있어?", "왼쪽에 뭐가 있어?"라고 하면 묻지 않고 바로 답한다. 대답이 없거나 못 알아들으면 가장 가까운 건물로 답한다. 그쪽 80m 안에 가게가 없으면 "오른쪽에는 가게가 안 보여요."라고 말한다.
- `core/IntentParser.kt`: `IntentType.NEARBY` 추가 ("뭐 있어", "뭐가 있어", "뭐 있지", "뭐 있나", 띄어쓰기 무시). 거절/들르기/저장/질문이 우선.
- `core/DialogMachine.kt`: `DialogEvent.NearbyPressed`(지도 버튼), `DialogEffect.AnswerNearby(side)`, "옆에 뭐 있어?" 되묻기 대기 상태 추가. `isAskingSide`는 두 되묻기를 모두 포함.
- `RealDemoEngine.kt`: 처음 물어본 순간의 기준점(`nearbyRefAlongM`)을 기억하고 `answerNearby(side)`가 그쪽 가게만 보고 건물을 찾는다 (`NEARBY_MAX_GAP_M` = 80m).
- 시나리오 단계별 되묻기: `ScenarioStepJson.askSide`(기본 false). true인 자동 발화만 좌우를 되묻는다.
- `DemoCommandReceiver.PRESETS` 7~11 추가: "방금 지나친 카페 뭐야?", "오른쪽", "옆에 뭐 있어?", "왼쪽", "오른쪽에 뭐 있어?". `scripts\say7.bat`~`say11.bat`, `scenario6.bat` 추가.
- 시연 시나리오 (설정 버튼, `scenarioN.bat`):
  1. 빵집 저장: "방금 오른쪽에 있던 빵집 뭐야?" → 달빛제과 → "저장해"
  2. 3분 전·들르기: "아까 3분 전에 지나친 카페 뭐였지?" → "아니 그 옆에" → "들렀다 가자"
  3. 좌우 되묻기: "방금 지나친 카페 뭐야?" → 양쪽에 카페가 있어 되묻기 → "오른쪽" → 볕드는카페(볕들빌딩) → "저장해"
  4. 건물 안내: "옆에 뭐 있어?" → 되묻기 → "왼쪽" → 구름스퀘어 3곳 → "오른쪽에 뭐 있어?"는 바로 답
  5. 위치만 저장: 지도에 없는 곳에서 "방금 거 뭐야?" → 위치만 저장
  6. 다시 지나감: 1번 다음에 실행하면 저장한 빵집을 다시 지나며 알려 준다
- `ui/dev/DevScreen.kt`: 시나리오 버튼 이름을 위 6개로. 테스트 추가 (파서, 되묻기, 시나리오 데이터, 볕들빌딩 앞 카페 역추적).
- `DemoScreen.kt`: 주행 지도의 "옆에 뭐 있어?" 버튼 삭제(사용자 요청). 말하기로 "옆에 뭐 있어?"라고 물으면 그대로 동작한다.
- 시나리오 2(3분 전·들르기)가 끝날 때쯤에야 진행되던 문제: 시작 때 60m부터 달린 3분치 기록을 미리 채우고 2700m에서 그 속도로 출발해, 시작 후 몇 초 안에 질문한다(사용자 요청). `prefillFromM`, `SimulatedDrive.startAt(initialSpeedMps)`.
- 나머지 시나리오도 시작 후 5초 안에 첫 장면: 예전 출발 지점을 `prefillFromM`으로 두고 첫 트리거 35m 앞에서 출발한다(1: 250→365, 3: 300→465, 4: 1100→1220, 5: 2300→2465). 6은 출발 직후 알림이 떠서 그대로. 되짚기 결과는 예전과 같다(`sim/TrailPrefill.kt`, 테스트 `sim/ScenarioTimelineTest.kt`). 에뮬레이터(50km/h) 시작→첫 질문: 1 2.8초, 2 2.2초, 3 1.7초, 4 1.7초, 5 2.8초, 6 알림 0.5초.
- 답 카드와 도착(정차 홈)이 겹치던 문제: 경로 끝에 가게 없는 동쪽 직선 1000m를 덧붙여 3000m→4000m (`scenarios.json`, `scripts/route.gpx`). 80km/h에서도 마지막 질문 뒤 실제 8초 안에 도착하지 않는다. 가게 위치는 그대로.
- 시나리오를 시작할 때마다 "다시 지나감" 알림 기록을 새로 센다(리허설로 1번을 다시 돌리면 6번 알림이 안 뜨던 문제).

## 2026-11-15 — 좌우 되묻기, 건물 안내 (feature/side-and-building)
- 좌우 되묻기: "방금 거기 뭐야?"처럼 오른쪽/왼쪽을 말하지 않았고, 찾은 가게(종류를 말했으면 그 종류)가 양쪽에 다 있으면 "오른쪽이었어요, 왼쪽이었어요?"라고 되묻는다. 한쪽에만 있으면 바로 답한다. 대답은 처음 질문한 순간(t0) 기준으로 다시 찾는다. 대답이 없거나 못 알아들으면 좌우 없이 찾는다. 시나리오 자동 발화는 단계에 askSide가 있을 때만 묻는다.
- `core/IntentParser.kt`: `parseSide()` 추가. 오른쪽/우측/오른편, 왼쪽/좌측/왼편을 읽는다.
- 건물 안내: 찾은 가게가 여러 가게가 든 건물에 있으면 "오른쪽 볕들빌딩이에요. 볕드는카페, 스마일마트가 있어요."처럼 건물 이름과 가게 목록으로 답한다. 카드 제목은 건물 이름, 부제는 "카페·편의점 2곳 · 오른쪽 · 150m 지나옴". 이때 저장하면 PENDING으로 저장되고 정차 후 가게를 고른다. 종류를 말했으면 그 가게로 답하고 "볕들빌딩에 있어요."를 덧붙인다.
- `assets/scenarios.json`: 건물 가게 7곳에 `building` 추가 (볕들빌딩, 구름스퀘어, 샛별타워). `data/ScenarioLoader.kt`가 읽어 `ScenarioData.buildingOf()`로 묶는다.
- `core/Buildings.kt` (새 파일): `Building`, 안내 문구 `BuildingText` (받침에 따라 이에요/예요, 이/가).
- `core/DialogMachine.kt`: 되묻기 대기 상태, 건물 답, 건물 답 뒤 저장 처리 추가.
- `RealDemoEngine.kt`: 건물 정보와 되묻기 여부 연결. "옆에 뭐 있어?"도 건물 데이터가 있으면 건물 이름과 가게 목록으로 답한다.
- 테스트 `core/SideAskAndBuildingTest.kt` 추가. 계약(Contract.kt) 변경 없음.
- PR 양식을 두 칸(무엇을 했나요, 확인 방법)으로 정리.

## 2026-11-15 — 주행 화면 홈 버튼, real 엔진 저장 삭제·시나리오 중단 정차 (feature/home-and-fixes)
- `DemoScreen.kt`: 주행 중 내비 화면 오른쪽 아래에 집(홈) 버튼 추가. 누르면 차는 계속 달리고 홈 화면만 띄운다. 홈의 [지도]를 누르면 달리던 내비로 돌아온다(사용자 요청). 주행이 끝나 정차하면 원래 흐름대로 설정 화면. 정차 중 지도 보기의 집 버튼은 그대로.
- `engine/RealDemoEngine.kt`: `DeleteSaved`(하나 삭제)와 `ClearAllSaved`(확인 창 문구대로 오늘 저장한 곳 삭제)를 처리하고 저장소에 반영. 전에는 `else -> Unit`으로 무시돼 real에서 삭제가 안 됐음.
- `engine/RealDemoEngine.kt`: `stopScenario()`가 차를 그 자리에서 기어 P로 세운다(전에는 계속 주행).
- "옆에 뭐 있어?"는 그대로 둠(조금 뒤를 찾는 것이 의도, 거리 제한은 따로 추가 예정).

## 2026-11-15 — 정차(P) 화면을 전체 화면 "정차 홈"으로 (design 브랜치)
- 사용자 요청: P 기어로 주차하면 시안의 정차 홈 화면으로 바뀌게. 아래 막대는 [말하기] [지도] [저장] [설정] (말하기는 맨 왼쪽, 사용자 요청. 시안의 "거울 앨범" 탭은 지도 버튼으로, "지나온 길" 탭은 삭제).
- `parked/ParkedHome.kt` (새 파일): 노을 배경 + 건물 실루엣, 머리글(RePin, 오늘 거울에 비친 곳 N개, 통계 상자), 최근 장소 큰 카드(거울, 이름, 상태, [안내 시작] [폰으로]), 작은 카드 2장(누르면 큰 카드로), "오늘 지나온 길" 띠(점 + 시각 + 지금). 정차 중에도 말하기 가능하도록 말풍선과 거울 카드를 홈 위에 띄움.
  - 통계는 엔진 값으로 계산: 지나온 길 = `drive.distanceAlongRouteM`, 지나친 가게 = 이미 지나온 구간 길가 60m 안의 POI 수. 계약 변경 없음.
- `parked/ParkedPanel.kt`: [저장] 탭의 저장 목록으로 바뀜 (뒤로 버튼, "저장한 곳" 머리글). 후보 선택, 메모, 안내, 폰으로 기능은 그대로. 띠는 홈으로 옮김.
- `DemoScreen.kt`: 정차 시 좌우 반반 대신 화면 전체를 정차 홈으로. [지도]를 누르면 내비 전체 화면, 오른쪽 아래 집 버튼으로 홈 복귀. 기어 D가 되면 홈 상태와 QR 초기화.
- `common/Icons.kt`: MAP, STAR, HOME 아이콘 추가.
- 에뮬레이터 확인 후 수정: 큰 카드 거울이 세로로 늘어나 동그랗게 보이던 문제 → 가로 2.4:1 비율 고정. 달린 거리가 0이면 통계 상자 숨김.

## 2026-11-15 — RePin "백미러" 디자인 적용 (design 브랜치)
- 앱 이름을 "아까 그곳"에서 **RePin**으로 변경 (`ui/`, `app/` AndroidManifest 라벨, README 제목).
- `common/Theme.kt`: 노을(기본) / 밤 두 테마로 색 전체 교체. 산호색 주 버튼(accent), 보라 경로(accent2), 노을 하늘 그라데이션(sky*), 거울 금속 테두리(chrome*), 앱 말풍선 남색(appBubble) 추가. 기본 테마를 노을로(`AkkaTheme(day = true)`).
- `dev/DevScreen.kt`: 테마 기본값을 노을로, 토글 이름 "노을 테마 (끄면 밤)".
- `navi/MapScene.kt`: 지도 색(연보라 땅, 흰 길, 보라 경로)과 지평선 노을빛 안개(`MapPalette.horizon`).
- `overlay/Mirror.kt` (새 파일): 금속 테두리 거울(`MirrorFrame`)과 거울 속 장면(`MirrorReflection`: 노을 하늘, 멀어지는 길, 지나친 쪽 길가의 가게 그림). 좌우는 카드 부제의 "오른쪽/왼쪽" 글자로 판단(`sideFromText`).
- `overlay/Overlays.kt`: 답변 카드를 화면 위쪽 가운데의 백미러 카드로 교체 (거울 + 이름/버튼 막대). 말풍선은 사용자 흰 유리, 앱 남색.
- `navi/NaviChrome.kt`: TBT 칩을 두 줄로 작게(거울 카드와 안 겹치게), 말하기 버튼 산호 그라데이션 + 흰 테, 아이콘 버튼 보라 아이콘. 모두 그림자 추가.
- `DemoScreen.kt`: 카드 위치를 TopCenter로, 넓은 화면은 폭 640dp, 좁은 화면(정차 반쪽)은 TBT 칩 아래. 정차 화면 구분선을 금속 그라데이션으로.
- `parked/ParkedPanel.kt`: 정차 화면을 "거울 앨범"으로 (노을 배경, RePin 머리글, 오늘 지나온 길 띠, 항목마다 거울 썸네일 + 가게 이름 + 지나친 쪽). 기능(후보 선택, 메모, 안내, 폰으로)은 그대로.
- 엔진/계약(Contract.kt)은 건드리지 않음. 주행 중 애니메이션 없음.

## 2026-11-14 — 실행용 프로젝트 Desktop\ui_run 생성
- A의 0단계 뼈대 없이 돌려 보려고 `Desktop\ui_run` 을 만들었다 (contract + ui + 최소 app, 가짜 엔진만).
- 이 폴더의 .kt 파일을 복사해 넣었으므로, 여기서 고치면 ui_run 쪽에도 같이 반영해야 한다.

## 2026-11-14 — 위치만 저장 카드에도 [저장, 들르기]
- `fake/FakeDemoEngine.kt`: 시나리오 4(위치만 저장) 카드도 CONTRACT.md대로 ANSWER = [SAVE, VISIT] 두 버튼을 붙임 (사용자 결정). [저장]은 이미 저장된 위치라 안내만, [들르기]는 저장한 위치를 경유지로 넣음(`locationPoi`).
- `Previews.kt`: "4 카드 위치만" 미리보기에 두 버튼 추가.

## 2026-11-14 — 리뷰 반영
- `DemoScreen.kt`: 정차 화면(목록, 설정, QR) 조건을 `gear == P && !drive.restricted`로 변경 (명세 4절의 restricted 기준).
- `DemoScreen.kt`, `dev/DevScreen.kt`: 디버그 오버레이는 발표자 모드가 켜져 있을 때만 보이게 함 (발표자 모드를 끄면 제품 화면만 남도록). 설정 문구에 "(발표자 모드에서만)" 표기.
- `DemoScreen.kt`, `presenter/Presenter.kt`: 왼쪽 아래 영역(말풍선, 창밖 띠, 하단 정보)이 말하기 버튼과 겹치지 않도록 오른쪽 여백을 두고, 창밖 띠 폭을 화면에 맞춰 줄어들게 함 (정차 반쪽 화면 대응).
- `fake/FakeDemoEngine.kt`: `addWaypoint`가 실패하면 경유지 핀과 "+3분"을 남기지 않도록 정리(`setWaypoint`/`clearWaypoint`). [안내]에서 경로로 갈 수 없으면 데모 경로를 처음부터 다시 달림. [들르기] 실패 시 안내 문구 추가.
- `navi/NaviChrome.kt`: 도착 시 남은 거리를 "10m" 대신 "0m"으로 표시. 말하기 버튼을 누른 채 화면이 바뀌어도 MicReleased를 보내도록 try/finally.
- `parked/ParkedPanel.kt`, `fake/FakeWorld.kt`, `Previews.kt`: "오늘 저장한 곳"이 오늘 저장분만 세고 보여 줌. 샘플 3개는 새벽에 실행해도 오늘 날짜로 들어가게 함. 미리보기는 현재 시각으로 샘플 생성.
- 그대로 둔 것: 위치만 저장 카드의 버튼 없음 → 같은 날 위 항목에서 해결.
