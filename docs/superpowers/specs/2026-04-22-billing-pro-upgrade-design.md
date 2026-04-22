# Billing & Pro Upgrade 구현 설계

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Google Play Billing API(RevenueCat SDK)를 사용해 일회성 Pro IAP를 구현하고, Free/Pro 기능 게이팅 및 업그레이드 화면을 추가한다.

**Architecture:** RevenueCat Android SDK로 Google Play Billing을 추상화. 서버·로그인 없음. `EntitlementManager` 싱글톤이 Pro 여부를 앱 전역에 제공. 기존 `ShortcutApplication`에 RevenueCat 초기화 추가.

**Tech Stack:** RevenueCat Android SDK (`purchases-android:7.x`), Google Play Billing (RevenueCat 내장), Kotlin Coroutines, Navigation Component Safe Args

---

## 핵심 결정사항

| 항목 | 결정 | 이유 |
|------|------|------|
| 로그인/계정 | ❌ 없음 | Google Play가 구매자 계정 관리, 재설치 시 복원 자동 |
| 백엔드 서버 | ❌ 없음 | 일회성 IAP는 클라이언트 검증으로 충분한 규모 |
| PG사 연동 | ❌ 없음 | Google Play 정책상 디지털 콘텐츠는 Play Billing 의무 |
| 결제 SDK | RevenueCat | Billing 엣지케이스 자동 처리, 무료 대시보드, 코드 단순화 |
| 수익화 모델 | Freemium + 일회성 Pro ₩5,900 | BYOK 구조상 구독 이중과금 부담 없애고 진입 장벽 낮춤 |
| 페이월 UI | 전체 화면 비교표 | 가치 명확하게 전달, 설정 + 한도초과 두 경로에서 진입 |

---

## Free vs Pro 기능 구분

| 기능 | Free | Pro |
|------|------|-----|
| 폴더 수 | 최대 2개 | 무제한 |
| 폴더당 Shortcut 수 | 최대 10개 | 무제한 |
| AI 예문 생성 | 월 20회 | 무제한 |
| CSV 내보내기 | ❌ | ✅ |
| 키보드 테마 | 3종 (인덱스 0–2) | 10종 전체 |
| 향후 신기능 | ❌ | 우선 제공 |

---

## 파일 구조

### 신규 생성

```
app/src/main/java/com/kore2/shortcutime/
├── billing/
│   ├── EntitlementManager.kt      # Pro 여부 단일 진입점 (RevenueCat 래핑)
│   └── BillingConstants.kt        # 상수 (Product ID, Entitlement ID)
├── ui/
│   └── pro/
│       └── ProUpgradeFragment.kt  # 전체 화면 업그레이드 화면
app/src/main/res/
├── layout/
│   └── fragment_pro_upgrade.xml   # Pro 업그레이드 화면 레이아웃
└── navigation/
    └── nav_graph.xml              # ProUpgradeFragment 노드 추가 (수정)
```

### 수정

```
app/src/main/java/com/kore2/shortcutime/
├── ShortcutApplication.kt                  # RevenueCat 초기화 추가
├── ui/
│   ├── FolderListFragment.kt               # 폴더 추가 전 한도 확인
│   ├── detail/FolderDetailFragment.kt      # shortcut 추가 전 한도 확인, CSV PRO 게이팅
│   ├── editor/ShortcutEditorFragment.kt    # AI 생성 월 한도 확인
│   └── settings/SettingsFragment.kt        # "Pro로 업그레이드" 행 추가
app/src/main/res/values/strings.xml         # Pro 관련 문자열 추가
app/build.gradle.kts                        # RevenueCat 의존성 추가
```

---

## 컴포넌트 설계

### BillingConstants.kt

```kotlin
object BillingConstants {
    const val REVENUECAT_API_KEY = "appl_xxxx"   // RevenueCat 대시보드에서 발급
    const val PRODUCT_ID = "pro_lifetime"         // Google Play Console에 등록할 상품 ID
    const val ENTITLEMENT_ID = "pro"              // RevenueCat 엔타이틀먼트 ID

    // Free tier limits
    const val FREE_MAX_FOLDERS = 2
    const val FREE_MAX_SHORTCUTS_PER_FOLDER = 10
    const val FREE_AI_MONTHLY_CAP = 20
    const val FREE_MAX_THEME_INDEX = 2            // 인덱스 0~2 (3종)
}
```

### EntitlementManager.kt

```kotlin
class EntitlementManager(private val context: Context) {
    /** Pro 여부 동기적으로 반환 (캐시 기반). 앱 초기화 후 사용. */
    fun isPro(): Boolean

    /** Google Play 구매 흐름 시작. Activity 필요. */
    suspend fun purchase(activity: Activity): PurchaseResult

    /** 기존 구매 복원 (재설치 후). */
    suspend fun restorePurchases(): RestoreResult

    sealed class PurchaseResult {
        object Success : PurchaseResult()
        object Cancelled : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
    }

    sealed class RestoreResult {
        object Restored : RestoreResult()
        object NothingToRestore : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }
}
```

`ShortcutApplication`에서 싱글톤으로 노출:
```kotlin
val entitlementManager: EntitlementManager by lazy { EntitlementManager(this) }
```

### ProUpgradeFragment 화면 구성

```
[← 뒤로] [Pro 업그레이드]         ← MaterialToolbar

         ✦
  Shortcut IME Pro
  단 한 번의 결제로 평생 이용

┌─────────────────────────────────┐
│ 기능            Free      Pro   │
│ 폴더            2개       무제한 │
│ Shortcut        10개/폴더 무제한 │
│ AI 예문 생성    월 20회   무제한 │
│ CSV 내보내기    ✕         ✓     │
│ 키보드 테마     3종       10종   │
│ 향후 신기능     ✕         우선   │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│    평생 이용권 · 일회성 결제     │
│           ₩5,900                │
│  [  지금 Pro로 업그레이드  ]    │  ← 버튼 클릭 → RevenueCat 구매 흐름
└─────────────────────────────────┘

           구매 복원               ← 탭 → restorePurchases()
    개인정보처리방침 · 이용약관
```

### 기능 게이팅 위치

**FolderListFragment — 폴더 추가 시:**
```kotlin
binding.addFolderFab.setOnClickListener {
    val entitlements = ShortcutApplication.from(requireContext()).entitlementManager
    val folderCount = viewModel.folders.value.size
    if (!entitlements.isPro() && folderCount >= BillingConstants.FREE_MAX_FOLDERS) {
        showLimitDialog(reason = LimitReason.FOLDER)
    } else {
        // 기존 폴더 추가 플로우
    }
}
```

**FolderDetailFragment — shortcut 추가 시 + CSV FAB:**
```kotlin
// shortcut 추가
binding.addShortcutFab.setOnClickListener {
    val shortcutCount = (viewModel.state.value as? Loaded)?.folder?.shortcuts?.size ?: 0
    if (!entitlementManager.isPro() && shortcutCount >= BillingConstants.FREE_MAX_SHORTCUTS_PER_FOLDER) {
        showLimitDialog(LimitReason.SHORTCUT)
    } else { openShortcutEditor(null) }
}

// CSV 내보내기
binding.exportCsvFab.setOnClickListener {
    if (!entitlementManager.isPro()) {
        showLimitDialog(LimitReason.CSV)
    } else { /* 기존 CSV 로직 */ }
}
```

**ShortcutEditorFragment — AI 생성 시:**
```kotlin
binding.generateExamplesButton.setOnClickListener {
    if (!entitlementManager.isPro() && viewModel.monthlyAiUsage() >= BillingConstants.FREE_AI_MONTHLY_CAP) {
        showLimitDialog(LimitReason.AI)
    } else { /* 기존 생성 로직 */ }
}
```

### LimitReachedDialog (재사용 다이얼로그)

```kotlin
enum class LimitReason { FOLDER, SHORTCUT, AI, CSV, THEME }

fun Fragment.showLimitDialog(reason: LimitReason) {
    AlertDialog.Builder(requireContext())
        .setTitle(reason.titleRes())
        .setMessage(reason.messageRes())
        .setPositiveButton(R.string.pro_see_upgrade) { _, _ ->
            findNavController().navigate(R.id.action_to_proUpgrade)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
```

---

## RevenueCat 초기화 (ShortcutApplication)

```kotlin
override fun onCreate() {
    super.onCreate()
    Purchases.logLevel = LogLevel.DEBUG  // 릴리즈 빌드에서는 제거
    Purchases.configure(
        PurchasesConfiguration.Builder(this, BillingConstants.REVENUECAT_API_KEY).build()
    )
}
```

---

## Google Play Console 설정 (코드 외 작업)

1. Play Console → 앱 → 수익 창출 → 인앱 상품 → 관리 상품 생성
   - 상품 ID: `pro_lifetime`
   - 유형: 일회성 구매
   - 가격: ₩5,900
2. RevenueCat 대시보드 → 새 프로젝트 → Android 앱 연결
   - Google Play 서비스 계정 JSON 업로드
   - Entitlement `pro` 생성, Product `pro_lifetime` 연결
3. `BillingConstants.REVENUECAT_API_KEY`에 발급된 키 입력

---

## 개인정보처리방침 추가 문구

```
본 앱은 인앱 결제 처리를 위해 RevenueCat Inc.의 서비스를 사용합니다.
구매 관련 정보(구매 토큰, 기기 식별자)가 RevenueCat 서버로 전송될 수 있습니다.
RevenueCat 개인정보처리방침: https://www.revenuecat.com/privacy
```

---

## 문자열 추가 목록 (strings.xml)

```xml
<string name="pro_upgrade_title">Pro 업그레이드</string>
<string name="pro_hero_title">Shortcut IME Pro</string>
<string name="pro_hero_subtitle">단 한 번의 결제로 평생 이용</string>
<string name="pro_price">₩5,900</string>
<string name="pro_price_label">평생 이용권 · 일회성 결제</string>
<string name="pro_cta">지금 Pro로 업그레이드</string>
<string name="pro_restore">구매 복원</string>
<string name="pro_feature_folders">폴더</string>
<string name="pro_feature_shortcuts">Shortcut</string>
<string name="pro_feature_ai">AI 예문 생성</string>
<string name="pro_feature_csv">CSV 내보내기</string>
<string name="pro_feature_themes">키보드 테마</string>
<string name="pro_feature_future">향후 신기능</string>
<string name="pro_free_folders">2개</string>
<string name="pro_free_shortcuts">10개/폴더</string>
<string name="pro_free_ai">월 20회</string>
<string name="pro_free_csv">사용 불가</string>
<string name="pro_free_themes">3종</string>
<string name="pro_free_future">제공 안 됨</string>
<string name="pro_paid_unlimited">무제한</string>
<string name="pro_paid_csv">사용 가능</string>
<string name="pro_paid_themes">10종 전체</string>
<string name="pro_paid_future">우선 제공</string>
<string name="pro_badge">PRO</string>
<string name="pro_settings_row">Pro로 업그레이드</string>
<string name="pro_settings_row_sub">모든 기능을 제한 없이</string>
<string name="limit_folder_title">폴더 한도 도달</string>
<string name="limit_folder_message">무료 버전은 폴더를 2개까지 만들 수 있어요. Pro로 업그레이드하면 제한 없이 사용할 수 있어요.</string>
<string name="limit_shortcut_title">Shortcut 한도 도달</string>
<string name="limit_shortcut_message">무료 버전은 폴더당 Shortcut을 10개까지 만들 수 있어요.</string>
<string name="limit_ai_title">AI 생성 한도 도달</string>
<string name="limit_ai_message">무료 버전은 이번 달 AI 예문을 20회까지 생성할 수 있어요.</string>
<string name="limit_csv_title">Pro 기능입니다</string>
<string name="limit_csv_message">CSV 내보내기는 Pro 전용 기능이에요.</string>
<string name="pro_see_upgrade">Pro 보기</string>
<string name="toast_purchase_success">Pro 업그레이드 완료! 감사합니다 🎉</string>
<string name="toast_restore_success">구매가 복원되었습니다.</string>
<string name="toast_restore_nothing">복원할 구매 내역이 없어요.</string>
<string name="toast_purchase_cancelled">구매가 취소되었습니다.</string>
<string name="toast_purchase_error">구매 중 오류가 발생했습니다: %1$s</string>
```

---

## 네비게이션 추가 (nav_graph.xml)

- `ProUpgradeFragment` 노드 추가
- `SettingsFragment` → `ProUpgradeFragment` 액션 추가
- `FolderListFragment` → `ProUpgradeFragment` 액션 추가
- `FolderDetailFragment` → `ProUpgradeFragment` 액션 추가
- `ShortcutEditorFragment` → `ProUpgradeFragment` 액션 추가 (기존 LlmSettings 액션과 공존)

---

## 테스트 계획

- RevenueCat 테스트 환경에서 구매 시뮬레이션 (Google Play Sandbox 계정)
- Free 한도 도달 → 다이얼로그 → Pro 화면 진입 흐름
- 구매 완료 → 즉시 Pro 기능 활성화 확인
- 앱 재설치 후 "구매 복원" → Pro 복원 확인
- 구매 취소 → 적절한 토스트 표시 확인
