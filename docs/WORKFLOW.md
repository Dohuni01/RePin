# GitHub 작업 방법

명령은 모두 Windows cmd 기준. 저장소 위치는 `C:\dev\akka-geugot`로 가정한다.

## 브랜치
| 브랜치 | 누가 | 용도 |
|---|---|---|
| `main` | 둘 다 | 항상 빌드되는 상태. 직접 작업·push 금지 |
| `chore/skeleton` | A | 0단계 프로젝트 뼈대 |
| `feature/engine-<이름>` | A | 엔진 단계별 작업 (engine/PROMPT.md 단계표 참고) |
| `feature/ui-<이름>` | B | 화면 단계별 작업 (ui/PROMPT.md 단계표 참고) |
| `chore/<이름>`, `docs/<이름>` | 둘 다 | 공통 설정, 문서 |
| `chore/integration` | 둘 다 | 마지막 합치기 |

## main 보호 (권장)
- GitHub 저장소 Settings > Branches > Add rule > `main`: "Require a pull request before merging", 승인 1명
- Private 저장소에서는 보호 규칙에 GitHub Pro가 필요할 수 있다 (학생은 GitHub Student Developer Pack으로 Pro 사용 가능).
  안 되면 "main 직접 push 금지"를 둘이 약속으로 지킨다

## 단계 하나의 흐름
```
cd C:\dev\akka-geugot
git switch main
git pull
git switch -c feature/engine-core
```
AI 도구에서: "engine/PROMPT.md 읽고 1단계 진행해" → 결과 확인
```
git status
git add engine app scripts gradle
git commit -m "feat(engine): 역추적 알고리즘과 테스트"
git push -u origin feature/engine-core
```
- `git status`에서 내 담당이 아닌 폴더가 바뀌어 있으면 add하지 말고 원인부터 확인
- GitHub에서 Pull request 생성 → 템플릿 체크리스트 채우기 → 상대방이 확인 후 Approve → "Squash and merge" → 브랜치 삭제
- B는 `git add ui gradle`

## 리뷰 기준 (5분이면 충분)
- Files changed 탭에서 상대 폴더를 건드리지 않았는지
- `contract/Contract.kt`가 바뀌지 않았는지
- fakeDebug / realDebug 빌드 결과 (스크린샷이나 로그)

## 작업 중에 main 변경 받아오기
```
git switch main
git pull
git switch feature/ui-navi
git merge main
```

## 충돌이 나면
- 내 폴더 충돌: 내 버전 기준으로 정리
- `gradle/libs.versions.toml`, `MERGE_NOTES.md`: 두 쪽 줄을 모두 남긴다
- `Contract.kt`나 남의 폴더 충돌: 혼자 해결하지 말고 상대방과 이야기한다

## Contract 변경이 꼭 필요할 때
1. `MERGE_NOTES.md`에 제안 (무엇을, 왜)
2. 둘이 합의
3. `chore/contract-<이름>` 브랜치에서 `Contract.kt`만 고치는 PR → 둘 다 확인 후 merge
4. 각자 자기 쪽 코드에 반영 (B는 FakeDemoEngine, A는 RealDemoEngine)

## 마지막 합치기
1. 양쪽 마지막 단계 PR까지 main에 merge
2. `git switch main && git pull && git switch -c chore/integration`
3. AI 도구에서: "docs/MERGE_PROMPT.md 읽고 진행해"
4. PR → 둘이 같이 확인 → merge

## 하지 말 것
- `local.properties`, 키, 계정 정보 커밋
- `build/` 폴더 커밋 (.gitignore에 있음)
- main에서 바로 작업
- 상대 폴더 수정 (부탁은 MERGE_NOTES.md로)
