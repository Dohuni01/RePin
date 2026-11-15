# 본선용 real 엔진 APK (보관)

> 이 폴더와 브랜치(`finals/real-release`)는 **본선 대비 보관용**입니다. main 제출본에는 포함되지 않습니다.

## 파일

- `RePin_RePin_real_release.apk` — `real` flavor release(서명) 빌드

## fake 제출본과의 차이

| | 예선 제출본 (main) | 본선용 (이 브랜치) |
|---|---|---|
| flavor | `fake` | `real` |
| 엔진 | `FakeDemoEngine` (시뮬레이션) | `RealDemoEngine` + Pleos SDK |
| 동작 조건 | 서버·하드웨어 없이 단독 실행 | Pleos 플랫폼 권한·서버 필요 |

## 다시 빌드하려면

```
gradlew.bat :app:assembleRealRelease
```

결과물: `app\build\outputs\apk\real\release\app-real-release.apk`

> release 서명은 `local.properties`의 `RELEASE_*` 값과 `keystore/repin-release.jks`로 처리됩니다(git 제외).

## 본선 전 확인 필요

- `RealDemoEngine` 및 `engine/.../platform/pleos/` 의 Pleos SDK 연동부에 남은 TODO·목업 점검
- 실제 Pleos Connect 환경에서 STT/TTS/NaviHelper 동작 검증
