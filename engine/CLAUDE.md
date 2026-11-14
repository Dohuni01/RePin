# engine/ — A(엔진 담당) 폴더

- 전체 작업 지시서: `engine/PROMPT.md` (사용자가 "N단계 진행해"라고 하면 그 단계만 하고 멈춘다)
- 수정 가능: `engine/`, `app/`, `scripts/`, 루트 gradle 파일, `gradle/libs.versions.toml`(줄 추가만)
- 수정 금지: `ui/`(0단계 뼈대 생성만 예외), `contract/`
- 화면(Compose UI)을 만들지 않는다. 확인은 `EngineProbeActivity`, logcat(태그 `AKKA`), JUnit으로 한다
- 모든 타이머와 판단은 엔진이 한다. 화면은 `AppState`를 그리기만 한다
- Pleos SDK는 인터페이스 뒤에 두고 목업 구현을 같이 만든다. 초기화에 실패하면 자동으로 목업으로 바꾸고 `debug.sdkStatus`에 표시한다
- `RealDemoEngine(context: Context)` 생성자 시그니처는 바꾸지 않는다
