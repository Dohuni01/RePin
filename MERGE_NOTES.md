# MERGE_NOTES

상대에게 부탁할 것, Contract 변경 제안, 합칠 때 처리할 것을 적는다.
충돌이 나면 두 쪽 줄을 모두 남긴다.

| 날짜 | 작성(A/B) | 대상(폴더·파일) | 내용 | 상태(요청/반영/보류) |
|---|---|---|---|---|
| 2026-11-15 | 공통 | contract/Contract.kt | `UiAction`에 `StopScenario`, `AskNearby` 2개 추가(additive). 지도 "시나리오 중단" / "옆에 뭐 있어?" 버튼용. main 단독 작업이라 동결 예외로 바로 반영. 양쪽 엔진(Fake/Real) 모두 처리 구현함 | 반영 |
| 2026-11-15 | B | engine/RealDemoEngine.kt `askNearby()` | "옆에 뭐 있어?"가 한참 뒤(200~300m 전) 가게를 추천하는 버그. 원인 2개: (1) 기준점을 `현재 - lookback`으로 뒤로 물려서 바로 옆 대신 더 뒤 가게가 뽑힘, (2) 거리 제한이 없어 빈 구간에서 먼 가게가 뽑힘. 제안: 기준을 **현재 위치(distanceAlongM)** 그대로 쓰고, 가장 가까운 가게가 **약 90m를 넘으면** 추천하지 말고 "옆에 안내할 가게가 없어요"로 응답. Fake 쪽은 `FakeWorld.nearbyBuilding`(현재 위치 기준 + 90m 캡)으로 수정함 | 요청 |
| 2026-11-15 | B | engine/RealDemoEngine.kt `stopScenario()` / 첫 실행 기어 | 발표 흐름 통일: 첫 실행은 **정차(P)**로 시작해 설정/홈 화면이 먼저 나오고, "시나리오 중단"은 **기어를 P로** 돌려 설정 화면으로 복귀. Fake(FakeDemoEngine)는 초기 `gear=P`, `stopScenario()`에서 `gear=P`로 수정함. 실엔진도 동일하게 맞출지 검토 요청 | 요청 |
