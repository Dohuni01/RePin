# 엔진 ↔ 화면 계약

파일: `contract/src/main/kotlin/com/akkageugot/demo/contract/Contract.kt`

## 흐름

```
 ┌──────────── ui/ (B) ────────────┐          ┌────────── engine/ (A) ──────────┐
 │  화면: AppState를 그리기만 한다   │ UiAction │  DemoEngine                      │
 │  버튼/마이크 → dispatch()        │ ───────▶ │  시뮬레이터, 역추적, 대화, SDK    │
 │                                  │ ◀─────── │  state: StateFlow<AppState>      │
 └──────────────────────────────────┘ AppState └──────────────────────────────────┘
```

- 화면은 `EngineProvider.engine.state`를 collect해서 그린다
- 사용자 입력은 전부 `EngineProvider.engine.dispatch(UiAction)`
- 상태를 바꾸는 건 엔진뿐이다. 카드 6초, 말풍선 4초 같은 타이머도 엔진이 처리하고 해당 필드를 null/제거한다
- 엔진은 state를 최대 10Hz로 갱신한다

## 좌표와 방향
- `Vec2`: 로컬 미터. 경로 시작점 = (0,0), x = 동쪽 +, y = 북쪽 +
- `headingRad`: 0 = 동쪽, 반시계 방향 + (수학 좌표계). 화면에서 heading-up 회전할 때 쓴다
- 화면 좌표계(y 아래가 +)로 바꾸는 건 ui 책임

## 필드 메모
| 필드 | 의미 |
|---|---|
| `HeadsUp.subtitle` | 엔진이 만드는 순간 값 고정 ("빵집 · 오른쪽 · 150m 지나옴"). 화면은 그대로 표시 |
| `HeadsUp.buttons` | ANSWER = [SAVE, VISIT], REVISIT = [VISIT, DISMISS] |
| `HeadsUp.category == null` | 위치만 저장한 카드 (지도에서 못 찾음) |
| `voiceLines` | 화면에 보일 것만 최대 2개 |
| `drive.restricted` | 주행 중 제한. true면 목록/설정/QR 금지, 애니메이션 금지 |
| `debug.*` | 발표자 모드 디버그 오버레이용 |
| `settings.useSdk` 키 | `STT`, `TTS`, `NAVI`, `VEHICLE`, `UX` |
| `Candidate.score` | 0~1. 0.75 이상 "높음", 0.5 이상 "보통", 그 외 "낮음"으로 표시 |

## 구현체
- 진짜: `com.akkageugot.demo.engine.RealDemoEngine(context: Context)` — A
- 가짜: `com.akkageugot.demo.ui.fake.FakeDemoEngine(context: Context)` — B
- `:app`의 Application이 `BuildConfig.USE_FAKE_ENGINE`으로 고른다. 두 생성자 시그니처 `(Context)`는 바꾸지 않는다

## 변경 절차
`docs/WORKFLOW.md`의 "Contract 변경이 꼭 필요할 때"를 따른다.
