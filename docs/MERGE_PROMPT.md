# 합치기 작업 지시서 (브랜치 `chore/integration`)

AI에게: 양쪽 단계별 PR은 이미 main에 merge된 상태다. 여기서는 진짜 엔진과 실제 화면을 처음 같이 돌려서 맞춘다.
이 작업에서만 `engine/`, `app/`, `ui/`를 모두 수정할 수 있다. `Contract.kt`는 여전히 수정 금지.

1. `CLAUDE.md`, `docs/CONTRACT.md`, `MERGE_NOTES.md`, `engine/PROMPT.md`, `ui/PROMPT.md`를 읽는다
2. `MERGE_NOTES.md`의 요청을 "반영됨 / 미반영 / 필요 없음"으로 정리해서 보고한다
3. Build Variant `realDebug`로 에뮬레이터 실행
4. `scripts\scenario1.bat`~`scenario6.bat`을 순서대로 실행하며 확인
   - 데모 내비에서 차가 움직이고 TBT, 남은 거리, 도착 시각이 맞게 바뀌는지
   - 카드와 말풍선이 뜨고 6초 / 4초 뒤 사라지는지
   - 들르기 → 경유지 핀, 경로 변경, "+N분"
   - 도착 → 정차 분할 화면, 저장 목록과 후보 카드
   - 재방문 알림
   - 발표자 모드 디버그 오버레이(궤적, t0, 탐색 구간, 점수)가 실제 값으로 그려지는지
5. fake 엔진과 real 엔진의 차이(비어 있는 필드, 타이밍, 좌표 방향 등)를 목록으로 만들고 하나씩 고친다.
   엔진 쪽 수정을 우선하고, UI 수정이 필요하면 이유를 적고 고친다
6. 정리
   - EngineProbeActivity 런처는 debug 빌드에서만 남기기
   - 기본 Build Variant를 `realDebug`로
   - `README.md` 실행 방법 갱신
7. 결과 보고: 고친 것 목록, 남은 문제, 발표 전 확인할 것
