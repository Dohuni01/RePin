# Pleos SDK 메모

2026년 11월 공식 문서 기준으로 정리. 버전과 정확한 시그니처는 항상 https://document.pleos.ai 에서 다시 확인한다.

## 모듈
- Pleos Connect SDK = Vehicle, NaviHelper, ADAS, Gleo AI(SpeechToText, TextToSpeech, LLM), Fused Location
- ADAS SDK, Fused Location SDK는 문서상 "준비 중" → 이 데모에서는 쓰지 않는다

## 공통 정책
- Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
  (settings.gradle.kts의 `dependencyResolutionManagement.repositories`에 추가)
- 권한은 Pleos 앱마켓과 사전 협의·승인된 범위에서만 쓸 수 있다. 승인 안 된 권한을 선언하거나 호출하면 앱 심사 탈락 → 쓰는 것만 선언
- 같은 기능이 AAOS 표준 API에 있으면 표준을 쓴다. 속도, 기어, 외기온도 같은 표준 데이터는 AAOS Car API(`CarPropertyManager`), 제조사 특화 기능만 Pleos SDK
- Vehicle SDK 지원 범위는 차종마다 다르다 → 도메인별 `checkXXXCapability`로 확인

## SpeechToText
- 의존성: `ai.pleos.playground:SpeechToText:3.2.1`
- API: `initialize`, `release`, `registerApp`(서버 인식용 앱 등록), `addListener`, `removeListener`,
  `request`(실시간 인식 요청), `stop`, `sendAudio`, `completeAudioSend`, `setLocale`
- SDK가 마이크 제어와 입출력을 대신 처리한다

## TextToSpeech / LLM
- TextToSpeech: 온디바이스 + 서버 하이브리드 합성
- LLM SDK: On-Device / Server 모드 지원 (이 데모는 규칙 기반 파서를 쓰고, 나중에 교체할 자리만 둔다)

## NaviHelper
- `initialize`, `release`, `addListener`, `removeListener`
- `requestRoute`, `cancelRoute`, `requestReRoute`, `addWaypoint`, `removeWaypoint`, `changeRouteOption`
- 조회: `getBookmarkInfo`, `getRecentDestinationInfo`, `getRouteStateInfo`, `getCurrentLocationInfo`,
  `getDestinationInfo`, `getWaypointInfo`, `getTBTInfo`, `getChargerOperatorInfo`
- `RouteInfo(latitude, longitude, displayLatitude, displayLongitude, poiName, poiId, poiSubId, address, routeOption)`
- 조회 API는 값을 바로 반환하지 않고 `NaviHelperEventListener` 콜백으로 결과를 준다
- `requestRoute()` 호출 성공 ≠ 경로 탐색 완료. `onRoutingCompleted` / `onError` 콜백으로 확인
- 즐겨찾기 "추가" API는 없다 → 저장 목록은 앱이 직접 관리

## Vehicle
- 사용법: `Vehicle(context).initialize()` → `vehicle.getBrake()` 같은 `getXxx()`로 도메인 객체 → 끝나면 `release()`
- 도메인: Brake, CarInfo, Display, Door, DrivingMode, EvBattery, HVAC, Light, Odometer, Safety, Seat,
  SideMirror, Steeringwheel, Tire, TurnSignal, Window, Wiper
- 권한 예: `pleos.car.permission.CAR_DRIVE`(DrivingMode, Brake, Odometer), `CAR_CLIMATE`(HVAC), `CAR_SEATS`(Seat),
  `CAR_SAFETY`(Safety, Tire), `CAR_ENERGY`(EvBattery), `CAR_DOORS`(Door), `CAR_WINDOWS`(Window, Wiper)
- 이 데모는 Brake(주차 브레이크)만 사용

## 주행 제한과 화면
- 플랫폼이 주는 `CarUxRestrictions` 상태에 따라 화면 제한을 적용한다
- DO(Distraction Optimized)를 선언하지 않은 앱은 주차 중에만 쓸 수 있고 주행 중엔 못 쓴다
  → 주행 중 쓰는 Activity에 `<meta-data android:name="distractionOptimized" android:value="true"/>`
- 앱 영역은 창 1~2개. `screenOrientation` 미지정이면 portrait로 창 하나, landscape는 앱 영역 두 칸을 쓴다
- 분할, 창 이동, 주행 상태 변화로 포그라운드/백그라운드 전환이 잦다 → 생명주기를 견고하게
- 주행 중 검색·설정 같은 기능은 비활성화
- 주행 중 움직이는 이미지·영상·애니메이션 금지, 큰 터치 영역, 고대비, 최소 텍스트
- 알림은 짧고 중요한 것만, 운전자에게 필요할 때만
- 내비 앱 가이드: 핵심 주행 정보 항상 표시, 주행 중 전체 화면 팝업 금지
- QR 로그인 같은 일부 기능은 지역별로 주행 중 제한

## 에뮬레이터
- Android Studio에 Pleos Connect Emulator를 설치하고 AVD를 설정한다
- 음성 입력을 직접 테스트하려면 확장 컨트롤에서 가상 마이크가 PC 오디오 입력을 쓰도록 켠다
- 차가 실제로 움직이지 않으므로 주행과 내비는 앱 안에서 시뮬레이션한다
