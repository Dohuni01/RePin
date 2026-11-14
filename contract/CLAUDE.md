# contract/ — 공통 계약 (동결)

- 이 폴더의 `Contract.kt`는 엔진(A)과 화면(B)의 계약이다. 0단계 이후 수정 금지
- AI는 이 파일을 읽기만 한다. 사용자가 고치라고 해도 먼저 `docs/WORKFLOW.md`의 "Contract 변경이 꼭 필요할 때" 절차를 안내한다
- 0단계에서는 이 폴더에 `build.gradle.kts`(Kotlin JVM 라이브러리, kotlinx-coroutines-core 의존)만 추가한다
