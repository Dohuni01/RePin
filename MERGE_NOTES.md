# MERGE_NOTES

상대에게 부탁할 것, Contract 변경 제안, 합칠 때 처리할 것을 적는다.
충돌이 나면 두 쪽 줄을 모두 남긴다.

| 날짜 | 작성(A/B) | 대상(폴더·파일) | 내용 | 상태(요청/반영/보류) |
|---|---|---|---|---|
| 2026-11-14 | 합치기 | 요청 목록 | 양쪽 요청 없음 (빈 표) | 필요 없음 |
| 2026-11-14 | 합치기 | engine/RealDemoEngine.kt | 정차 화면에서 후보 선택 시 status도 CONFIRMED/LOCATION_ONLY로 갱신 (배지가 안 바뀌던 문제) | 반영 |
| 2026-11-14 | 합치기 | engine/RealDemoEngine.kt | 위치만 저장 카드: [저장]/[들르기]가 아무 동작 안 하고 카드가 6초 뒤에도 안 사라지던 문제 수정 | 반영 |
| 2026-11-14 | 합치기 | engine/RealDemoEngine.kt | NavigateTo 뒤 목적지 핀이 원래 목적지에 남던 문제 → 현재 경로 끝을 destination으로 | 반영 |
| 2026-11-14 | 합치기 | ui/dev/DevScreen.kt | SDK 스위치: 진짜 엔진은 useSdk 키가 없으면 SDK 사용 → 키 없음을 켜짐으로 표시 | 반영 |
| 2026-11-14 | 합치기 | engine/src/debug/AndroidManifest.xml, app/build.gradle.kts, README.md | 엔진 테스트 런처 debug 전용, 기본 Build Variant realDebug, 실행 방법 갱신 | 반영 |
