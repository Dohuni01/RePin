# RePin — 무심코 지나친 그곳, 다시 핀 꽂아 드려요

> 현대차그룹 **Pleos Connect** 차량용 앱 공모전 데모
> 운전 중 "어, 방금 오른쪽에 있던 빵집 뭐야?"라고 물으면, 차가 지나온 길을 되짚어 그 가게를 찾아
> 음성으로 답하고 내비 위에 작은 카드 한 장만 잠깐 띄웁니다. 후보 확인·저장은 정차했을 때만 합니다.

---

## 앱 이름 및 한 줄 소개

- **앱 이름**: RePin
- **한 줄 소개**: 운전 중 지나친 가게를 음성 한마디로 되짚어 다시 핀 꽂아 주는 차량용 AI 어시스턴트

## APK 파일 위치

- **경로**: [`apk/RePin_RePin_release.apk`](apk/RePin_RePin_release.apk)
- 실행 가능한 **release(서명됨)** 버전입니다. 가짜 엔진(`fake`) flavor로 빌드되어, 실제 차량/서버 없이
  에뮬레이터만으로 전체 시나리오가 동작합니다.

## 데모 영상

- **경로**: [`videos/scenario1.mp4`](videos/scenario1.mp4)
- Pleos Connect Emulator에서 "빵집 저장" 시나리오를 실행한 화면 녹화 (설정 → 주행 → 가게 발견·저장 → 정차).

## 실행 방법

### 방법 1 — 제출 APK 바로 설치 (권장)

Pleos Connect Emulator(AVD)를 실행한 뒤, 터미널에서:

```
adb install -r apk\RePin_RePin_release.apk
adb shell am start -n com.akkageugot.demo/com.akkageugot.demo.ui.DemoActivity
```

앱을 켜면 **RePin 스플래시 화면**이 잠깐 뜬 뒤 정차 홈(설정·시나리오 화면)으로 들어갑니다.
설정 화면에서 시나리오를 고르면 주행 내비가 시작되고, 지나친 가게를 되짚어 카드로 보여 줍니다.

### 방법 2 — 소스에서 직접 빌드

```
gradlew.bat :app:assembleFakeRelease
```

결과물: `app\build\outputs\apk\fake\release\app-fake-release.apk`

- Build Variant
  - `fakeRelease` / `fakeDebug`: 가짜 엔진. 하드웨어·서버 없이 데모가 완결됩니다. (제출본)
  - `realRelease` / `realDebug`: 실제 엔진(Pleos SDK 연동). Pleos 플랫폼 권한·서버가 갖춰진 환경에서 동작합니다.

> 소스 빌드 시 release 서명은 `local.properties`의 `RELEASE_*` 값과 `keystore/` 키스토어로 처리됩니다.
> 이 두 가지는 비밀값이라 저장소에 포함하지 않습니다. 서명 설정이 없으면 `fakeDebug`로 빌드해도 동일하게 실행됩니다.

## 테스트 환경 및 필요한 설정

- **IDE**: Android Studio + Pleos Connect Emulator 시스템 이미지
- **JDK**: 17
- **Android SDK**: compileSdk 36 / minSdk 34 / targetSdk 34
- **엔진**: 제출본은 `fake` flavor라 별도 서버·계정·키 설정이 필요 없습니다.
- **권한**: 앱이 실제로 호출하는 범위만 선언했습니다 — 마이크(STT), Pleos STT/TTS,
  NaviHelper 경로 탐색, 인터넷. (`app` 매니페스트 / `docs/PLEOS_SDK_NOTES.md` 참고)
- **음성 입력을 직접 테스트**하려면 에뮬레이터 확장 컨트롤에서 가상 마이크가 PC 오디오 입력을
  쓰도록 켭니다. 데모 시나리오는 마이크 없이도 화면 버튼으로 재생됩니다.

## 개발에 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격

| 항목 | 값 |
|---|---|
| Pleos Connect Emulator | **v2.1.0** (`RELEASE.release_connect_v2.1.0`) |
| AVD / device | `crp_avd` (Connect on x86_64 emulator) |
| Android 버전 | Android 14 (API 34) |
| 화면 해상도 | 2560 × 1440 px |
| 화면 밀도 | 160 dpi (mdpi) → 2560 × 1440 dp |
| 화면 방향 | 가로(landscape), 차량 앱 영역 2칸 |

---

## 프로젝트 구조

```
contract/   엔진 ↔ 화면 계약 (공통, 동결)
engine/     시뮬레이터, 역추적, 대화, Pleos SDK, 저장, adb 명령
ui/         데모 내비, 카드, 정차 화면, 설정, 가짜 엔진
app/        앱 진입점, fake/real 엔진 선택
scripts/    발표용 .bat, route.gpx
docs/       작업 방법, 계약 설명, SDK 메모
apk/        제출용 release APK
```

## 참고 문서

- Pleos SDK 메모: `docs/PLEOS_SDK_NOTES.md`
- 엔진 ↔ 화면 계약: `docs/CONTRACT.md`
- 작업 흐름: `docs/WORKFLOW.md`
