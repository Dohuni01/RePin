# WORKLOG (ui/, 화면 담당 B)

최신 항목이 위에 온다. 이 앱은 실제 API를 연결하지 않는 데모 버전이며 FakeDemoEngine 기준으로 동작한다.

## 2026-11-14 — 합치기(chore/integration)
- `dev/DevScreen.kt`: SDK 스위치를 `useSdk[key] != false`로 표시 (진짜 엔진은 키가 없으면 SDK 사용). 나머지 엔진 쪽 수정은 루트 `MERGE_NOTES.md` 참고.

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
