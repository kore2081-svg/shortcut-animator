# Shortcut Animator — Phase 1 Design Spec

**Date:** 2026-04-19
**Status:** Approved for planning
**Scope:** Phase 1 only (Phase 2 LLM example generation is a separate spec)

---

## 1. 배경과 목표

### 1.1 앱 정체성

`Colemak 경로 애니메이션이 붙은 shortcut expander 키보드 + 폴더형 DB 관리 앱`.

두 실행 맥락:

1. **앱 실행 시** — 폴더/shortcut 관리 백오피스
2. **IME 실행 시** — 실제 입력 도구 (Colemak 영문 + 2벌식 한글 + shortcut expansion + 경로 시각화)

### 1.2 Phase 1 의 의도

현재 코드는 ~80% 구현되어 있지만 아래 문제로 사용 불가에 가까운 상태다.

- 매 빌드마다 저장 데이터가 초기화되는 리셋 버그
- shortcut 편집 화면이 존재하지만 폴더 흐름과 어긋나 자동 폴더 생성 버그 발생
- Colemak 애니메이션이 "흐르는 빛" 형태로 잘못 재생됨
- 외부 앱에서의 shortcut expansion 은 동작하지만 사용 횟수가 어디에도 표시되지 않음
- 5 개 Activity 구조가 Play Store 장기 유지보수에 부담

Phase 1 은 이 문제들을 해결하면서, 이후 단계(LLM 예문 생성, 추가 기능)가 얹힐 수 있는 **안정된 단일 Activity + Navigation 아키텍처**를 만드는 것이 목표다.

### 1.3 Phase 1 에서 하지 않는 것

- Phase 2 의 LLM 예문 생성 (Claude Haiku API 호출)
- Room 데이터베이스 전환 (SharedPreferences + JSON 유지)
- 다크 모드 확장
- 키보드 IME 동작 로직 자체의 재설계
- 다국어 문자열 확장

---

## 2. 아키텍처 선택

### 2.1 채택: Navigation Component + safeargs (Approach C)

대안으로 고려했던 것:
- **A** — 기존 Activity 구조 유지 + 버그 수정만
- **B** — FragmentManager 수동 관리 + BackStack
- **C** — Jetpack Navigation Component + safeargs (**채택**)

채택 이유: 사용자가 Google Play Store 론칭을 목표로 함. 장기 유지보수 비용이 러닝 커브보다 크다. deep link, BottomSheet, dialog destination 등의 향후 확장 모두 Navigation Component 가 표준 도구다.

### 2.2 단일 Activity 구조

- `HostActivity` (유일한 Activity) — `FragmentContainerView` + `NavHostFragment`
- 모든 화면은 Fragment
- 서비스: `ShortcutInputMethodService` (그대로 유지)

### 2.3 Fragment 구조

```
FolderListFragment          — 앱 런치 시 startDestination
  └ FolderDetailFragment    — 특정 폴더의 shortcut 리스트
      └ ShortcutEditorFragment  — shortcut 편집 + Colemak preview + 예문
  └ FolderEditorFragment    — 폴더 생성/편집
  └ SettingsFragment        — 테마, IME 활성화 안내 등
```

### 2.4 의존성 주입

- DI 프레임워크 없음 (Hilt/Koin 과잉)
- `ShortcutApplication : Application` 에 `FolderRepository`, `KeyboardThemeStore` 싱글톤 보유
- Fragment 는 `(requireContext().applicationContext as ShortcutApplication).repository` 로 접근
- ViewModel 은 `ShortcutApplication` 을 받는 factory 통해 생성

### 2.5 상태 관리

- Fragment 당 ViewModel 1 개
- ViewModel 은 `SavedStateHandle` 로 nav args 수신
- UI 상태는 `StateFlow<UiState>` 로 노출
- `viewModelScope.launch` 로 비동기 작업 (Phase 2 의 LLM 호출 대비)

---

## 3. 데이터 모델

### 3.1 기존 구조 유지

```
FolderItem
  - id: String (UUID)
  - title: String
  - note: String
  - shortcuts: List<ShortcutEntry>

ShortcutEntry
  - id: String (UUID)
  - shortcut: String
  - expandsTo: String
  - usageCount: Int
  - note: String
  - examples: List<ExampleItem>
  - caseSensitive: Boolean
  - backspaceToUndo: Boolean

ExampleItem
  - english: String
  - korean: String
  - sourceType: "auto" | "manual"
```

### 3.2 저장소

- `SharedPreferences` 에 JSON 문자열로 전체 폴더 리스트 저장
- Key: `"folders_v1"` (기존 유지)
- 파싱 실패 시 빈 리스트 반환, 저장 실패 시 예외 전파 (데이터 유실 은폐 방지)

### 3.3 Phase 1 에서 고칠 것

- `FolderRepository.init { resetDataForCurrentBuildIfNeeded() }` **삭제** — 매 빌드마다 데이터 리셋하는 치명 버그
- `migrateLegacyShortcutsIfNeeded()` 는 Play Store 업그레이드 경로를 위해 **유지**

---

## 4. Colemak 애니메이션 사양

### 4.1 요구사항 (APP_UNDERSTANDING.md 123–143 행)

- **순차 점등** (동시 아님)
- 각 글자는 **밝아졌다가 꺼져야** 함
- 마지막에만 **전체 경로 화살표** 표시

### 4.2 타이밍 상수

```
KEY_LIT_MS      = 700   // 각 키가 밝은 시간
KEY_GAP_MS      = 150   // 키 사이 완전 off 간격
FINAL_PAUSE_MS  = 400   // 마지막 키 소등 후 화살표 출현까지
EDITOR_DEBOUNCE_MS = 800 // Editor 에서 타이핑 debounce
```

### 4.3 타임라인 예시 (`mike`)

```
t=0     : m on
t=700   : m off,  모두 off
t=850   : i on
t=1550  : i off,  모두 off
t=1700  : k on
t=2400  : k off,  모두 off
t=2550  : e on
t=3250  : e off,  모두 off
t=3650  : 화살표 m→i→k→e 표시
```

### 4.4 구현 메모

- `Handler.postDelayed` + `Runnable` token 기반
- 재시작 시 `removeCallbacksAndMessages(token)` 후 새 token 으로 재생
- ShortcutEditor 의 shortcut 입력창은 `TextWatcher` + `EDITOR_DEBOUNCE_MS` debounce — 중간에는 `setSequence()` 로 정적 표시, 입력이 멈춘 뒤 `playSequence()` 호출
- IME 의 ON 모드: 입력 문자마다 즉시 `setSequence(accumulated)` — 애니메이션 X, 정적 하이라이트만
- IME 의 LINK 모드: 저장된 shortcut 에 매칭될 때만 강조
- IME 의 OFF 모드: preview view 접기

---

## 5. Phase 1-a 범위 — Navigation 스켈레톤 + 폴더 흐름

### 5.1 목표

앱을 단일 Activity + Fragment + Navigation 구조로 전환하되, 중간에 빌드가 깨지지 않도록 폴더 흐름(List → Detail)만 먼저 Fragment 화하고, 나머지 Activity 들은 일시적으로 공존시킨다.

### 5.2 작업 그룹

**① 인프라**
- `build.gradle(:app)` 에 `androidx.navigation:navigation-fragment-ktx`, `navigation-ui-ktx` 추가
- `navigation-safe-args-gradle-plugin` 적용
- `ShortcutApplication : Application` 생성, AndroidManifest 등록
- `FolderRepository`, `KeyboardThemeStore` 를 Application 싱글톤화

**② HostActivity + nav_graph.xml**
- `HostActivity` 생성 — `FragmentContainerView` 만 품는 최소 Activity
- `res/navigation/nav_graph.xml` 신설
- startDestination = `folderListFragment`
- 이 시점엔 `folderDetailFragment` 까지만 `<fragment>`, 나머지 3 개는 `<activity>` 목적지로 연결 (공존)

**③ Fragment 전환 (2 개)**
- `FolderListFragment` + `FolderListViewModel`
- `FolderDetailFragment` + `FolderDetailViewModel`
- 각 Fragment 는 기존 Activity 의 레이아웃 XML 을 최소 수정해 재사용

**④ Repository 버그 수정**
- `FolderRepository.init { resetDataForCurrentBuildIfNeeded() }` 제거
- `resetDataForCurrentBuildIfNeeded()` 메서드 삭제
- `migrateLegacyShortcutsIfNeeded()` 유지

**⑤ IME 진입점 수정**
- `ShortcutInputMethodService.openShortcutApp()` 의 `Intent(this, MainActivity::class.java)` → `Intent(this, HostActivity::class.java)`

**⑥ Manifest 정리**
- `MainActivity` 선언 제거
- `HostActivity` 를 LAUNCHER 로 등록
- 나머지 3 Activity 선언은 유지 (1-b 에서 제거)

### 5.3 완료 기준

1. 앱 실행 시 HostActivity 가 뜨고 FolderListFragment 가 표시
2. 폴더 추가 / 삭제 / 수정 (기존 Activity 로 이동) 후 목록에 반영
3. 폴더 클릭 → FolderDetailFragment 진입
4. IME 의 `+` 버튼 → HostActivity 가 뜸
5. 앱 재실행 시 저장 데이터 유지 (reset 버그 없음)
6. 기존 shortcut 편집 / 설정 화면은 일시적으로 `<activity>` 목적지로 동작
7. `./gradlew assembleDebug` 성공
8. IME 가 정상 활성화되고 타이핑 동작

### 5.4 파일 영향

- 신규: ~12 (ShortcutApplication, HostActivity, 2 Fragment + 2 ViewModel, nav_graph.xml, item layout 등)
- 수정: 5 (build.gradle, Manifest, FolderRepository, ShortcutInputMethodService, FolderAdapter)
- 삭제: 4 (MainActivity.kt, activity_main.xml, 일부 사용처 없는 리소스)

---

## 6. Phase 1-b 범위 — 나머지 Fragment 전환 + 마감

### 6.1 목표

Phase 1-a 에서 `<activity>` 로 남겨두었던 3 개를 모두 Fragment 로 전환하고, Colemak 애니메이션 타이밍 수정과 usage count 뱃지 UI 를 붙여 이미지 목업대로 마감한다.

### 6.2 작업 그룹

**① 남은 Activity → Fragment 전환 (3 개)**

| 기존 | 전환 후 |
|---|---|
| `FolderEditorActivity` | `FolderEditorFragment` + ViewModel |
| `ShortcutEditorActivity` | `ShortcutEditorFragment` + ViewModel |
| `SettingsActivity` | `SettingsFragment` |

- `nav_graph.xml` 의 `<activity>` 목적지를 `<fragment>` 로 교체
- `AndroidManifest.xml` 에서 3 개 Activity 선언 제거

**② ShortcutEditorFragment 버그 수정**

- `ensureTargetFolderId()` 제거. args 로 받은 folderId 가 유효하지 않으면 Toast + popBackStack. "Starter Folder" 자동 생성 금지.
- `updatePreview()` 에 `EDITOR_DEBOUNCE_MS` debounce. 중간에는 `setSequence()`, 입력 멈춘 뒤 `playSequence()`.
- `generateExamples()` 의 하드코딩 fake 템플릿 제거. Phase 2 placeholder 로 대체 — 버튼 누르면 "Phase 2 에서 구현 예정" Toast, 데이터 저장 없음.

**③ Colemak 애니메이션 타이밍 수정**

- `ColemakPreviewView.playSequence()` 를 §4.2 의 상수로 재작성
- 키 사이 완전 off 간격 도입 — "흐르는 빛" → "독립 깜빡임"
- 화살표는 마지막 키 소등 + FINAL_PAUSE_MS 후에만 표시

**④ Usage count 뱃지 UI 구현**

- `UsageCountFormatter.format(count: Int): String` → `count > 100 ? "100↑" : count.toString()`
- `SavedShortcutsAdapter` (ShortcutEditor 리스트) 항목 우측에 뱃지
- `FolderShortcutAdapter` (FolderDetail 리스트) 항목 우측에 동일 뱃지
- 스타일: 작은 pill, `text_secondary` 색, 14sp
- Fragment `onResume()` 에서 리스트 refresh (IME 사용 후 앱 복귀 시 갱신)

**⑤ Adapter 효율화**

- `FolderAdapter.onBindViewHolder` 에서 매번 `KeyboardThemeStore` 생성 → ViewHolder 생성 시 1 회로 이동
- `notifyDataSetChanged()` → `ListAdapter` + `DiffUtil.ItemCallback` 교체 (Folder/FolderShortcut/SavedShortcuts adapter 공통 적용)

**⑥ Layout polish**

- Shortcut 편집 화면 상단 제목 "Shortcut Animator" (현재는 `title_add_shortcut_entry`)
- helperText 를 이미지 목업 문구로 조정
- Preview card 에 뚜렷한 surface 테두리
- Saved shortcuts 행에 Edit / Delete 아이콘 버튼 보강
- 예문 생성 버튼 1/3/5 의 checkable 스타일 선택/비선택 명확화

### 6.3 완료 기준

1. `AndroidManifest.xml` 에 `HostActivity` + `ShortcutInputMethodService` 만 남음
2. 폴더 → 새 폴더 → 진입 → shortcut 편집 → 저장 → 복귀 전 구간이 Fragment 전환으로 동작
3. `mike` 입력 후 800ms 대기 → `m → off → i → off → k → off → e → off → 화살표` 순서
4. 외부 앱에서 shortcut + space 로 expansion → 앱 복귀 후 usage count +1
5. 101 회 사용 시 뱃지 "100↑"
6. Starter Folder 자동 생성 없음
7. 같은 항목 여러 번 눌러도 스택 중복 없음
8. 예문 생성 버튼 → "Phase 2 예정" Toast 만
9. 레이아웃이 이미지 목업과 시각적으로 부합

### 6.4 파일 영향

- 신규: ~8 (3 Fragment + 2 ViewModel, UsageCountFormatter, 3 fragment layout)
- 수정: ~10 (nav_graph, ColemakPreviewView, 3 adapter + 대응 item layout, Manifest, strings.xml)
- 삭제: 6 (3 Activity.kt + 3 activity_*.xml)

---

## 7. 에러 처리 + 테스트 접근

### 7.1 에러 처리 원칙

| 레이어 | 책임 | 사용자 노출 |
|---|---|---|
| Repository | SharedPreferences I/O, JSON | X — 로그 + 기본값 |
| ViewModel | 비즈니스 검증, 상태 관리 | `StateFlow<UiState>` Error |
| Fragment | 해석, 표시 | Toast / Snackbar / 다이얼로그 |
| IME | 입력 스트림 | 조용히 실패 (키보드 자체 생존 우선) |

### 7.2 구체 시나리오

- **JSON 파싱 실패** — 읽기 시 catch 후 빈 리스트. 쓰기 실패 시 예외 전파.
- **잘못된 ID 로 Fragment 진입** — `getFolder(id)` null → Toast + `popBackStack()`
- **빈 입력 저장** — `TextInputLayout.error` 인라인 표시 (Toast 아님), `trim()` 후 검증
- **IME 예외** — `onStartInputView` 내부 try/catch, repository 실패 시 expansion 만 skip
- **Navigation race** — `currentDestination?.id` 체크 후 navigate, 또는 `navigateSafe` 확장함수
- **Phase 2 LLM 실패** (설계만) — Snackbar + 재시도, 부분 성공 허용

### 7.3 자동 테스트 범위 (Phase 1)

| 대상 | 도구 |
|---|---|
| `UsageCountFormatter` | JUnit4 |
| `FolderRepository` round-trip, incrementUsage, migrate | Robolectric |
| `HangulComposer` 기본 조합 | JUnit4 |
| Colemak 매핑 일관성 | JUnit4 |
| `findMatchingShortcut` 경계 | JUnit4 |

Fragment UI 및 Colemak 애니메이션 타이밍은 **수동 테스트**.

### 7.4 수동 테스트 체크리스트

**Phase 1-a:**
- [ ] 앱 실행 → 폴더 목록
- [ ] 폴더 추가 → 목록 갱신
- [ ] 폴더 진입 → 빈 shortcut 리스트
- [ ] 뒤로 → 폴더 목록
- [ ] 앱 재실행 → 데이터 유지
- [ ] IME `+` → HostActivity 진입

**Phase 1-b:**
- [ ] Shortcut Fragment 진입 → 저장 → 목록 반영
- [ ] 외부 앱 shortcut + space → expansion
- [ ] 앱 복귀 → usage count +1
- [ ] 101 회 사용 → "100↑"
- [ ] `mike` 입력 → 독립 점등 + 화살표 (800ms 후)
- [ ] 한글 모드 → 2 벌식 조합
- [ ] 예문 생성 버튼 → "Phase 2 예정" Toast
- [ ] Starter Folder 생성 안 됨
- [ ] 설정 화면 진입/이탈

### 7.5 빌드 검증

- `./gradlew assembleDebug` 통과
- `./gradlew test` 통과
- `./gradlew lint` — 기존 경고 수준 유지
- 물리 디바이스에서 IME 활성화 + 체크리스트

### 7.6 환경 주의

- 에뮬레이터보다 실제 디바이스 권장 (IME 활성화 UX 차이)
- usage count 검증은 외부 앱 입력으로만 가능 (같은 앱 내에서는 IME 뜨지 않음)

---

## 8. Phase 2 예고 (참고용, 본 스펙 범위 외)

- Claude Haiku API 호출로 예문 생성 (1/3/5 개)
- 네트워크 오류, timeout, rate limit 처리
- API 키 저장 위치 — `EncryptedSharedPreferences`
- 예문 저장 시 `sourceType = "auto"` 로 표시
- 별도 스펙 문서에서 상세화

---

## 9. 요약 체크리스트

**Phase 1 완료 시 달성되는 상태:**

- [x] Play Store 유지보수를 감당할 단일 Activity + Navigation 아키텍처
- [x] 데이터 리셋 버그 해결
- [x] Starter Folder 자동 생성 버그 해결
- [x] Colemak 애니메이션이 이미지 목업대로 동작
- [x] Usage count 뱃지 표시 (100↑ 캡 포함)
- [x] IME 의 `+` 버튼이 앱 진입점으로 정상 동작
- [x] Fragment 기반 폴더/편집/설정 흐름
- [x] 자동 단위 테스트 + 수동 체크리스트

**Phase 2 로 넘기는 것:**

- LLM 기반 실제 예문 생성 (현재는 placeholder Toast)
