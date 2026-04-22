# Billing & Pro Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Google Play Billing(RevenueCat SDK)으로 일회성 Pro IAP(₩5,900)를 구현하고, Free/Pro 기능 게이팅 및 업그레이드 전체 화면을 추가한다.

**Architecture:** RevenueCat Android SDK가 Google Play Billing 복잡도를 흡수. `EntitlementManager`가 Pro 여부 + 무료 월별 AI 사용량을 관리하는 단일 진입점. `ShortcutApplication`에 싱글톤으로 노출. 서버·로그인 없음. 한도 초과 시 다이얼로그 → ProUpgradeFragment 전체 화면으로 이동.

**Tech Stack:** RevenueCat `purchases-android:7.12.0`, Google Play Billing (RevenueCat 내장), Kotlin Coroutines, Navigation Component Safe Args, Robolectric(단위 테스트)

---

## ⚠️ 사전 준비 (코드 작업 전 외부 설정)

구현 시작 전 아래 두 가지를 완료해야 한다.

**1. RevenueCat 계정 설정**
- [app.revenuecat.com](https://app.revenuecat.com) 가입 → 새 프로젝트 생성
- Android 앱 추가 → Google Play 서비스 계정 JSON 업로드
- Products → `pro_lifetime` (일회성) 생성
- Entitlements → `pro` 생성 → `pro_lifetime` 연결
- API Keys → Public SDK Key 복사 → `BillingConstants.REVENUECAT_API_KEY`에 입력

**2. Google Play Console 설정**
- Play Console → 앱 → 수익 창출 → 인앱 상품 → 관리 상품 생성
  - 상품 ID: `pro_lifetime`, 유형: 일회성 구매, 가격: ₩5,900

---

## 파일 구조

### 신규 생성
```
app/src/main/java/com/kore2/shortcutime/
├── billing/
│   ├── BillingConstants.kt          — 상수 (API 키, 상품 ID, Free 한도)
│   ├── EntitlementManager.kt        — isPro(), purchase(), restore(), 월 AI 카운터
│   └── BillingUiHelper.kt           — showLimitDialog() Fragment 확장 함수
└── ui/pro/
    └── ProUpgradeFragment.kt        — 전체 화면 업그레이드 UI

app/src/main/res/layout/
└── fragment_pro_upgrade.xml         — Pro 화면 레이아웃

app/src/test/java/com/kore2/shortcutime/billing/
└── EntitlementManagerTest.kt        — 월별 AI 카운터 단위 테스트
```

### 수정
```
app/build.gradle.kts                                           — RevenueCat 의존성
app/src/main/java/com/kore2/shortcutime/ShortcutApplication.kt — entitlementManager lazy + RC init
app/src/main/res/navigation/nav_graph.xml                      — proUpgradeFragment 노드 + 글로벌 액션
app/src/main/res/values/strings.xml                            — Pro 관련 문자열
app/src/main/res/layout/fragment_settings.xml                  — Pro 업그레이드 버튼 행 추가
app/src/main/java/com/kore2/shortcutime/ui/settings/SettingsFragment.kt  — Pro 버튼 와이어업
app/src/main/java/com/kore2/shortcutime/ui/list/FolderListFragment.kt    — 폴더 한도 게이팅
app/src/main/java/com/kore2/shortcutime/ui/detail/FolderDetailFragment.kt — 단축어/CSV 게이팅
app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorFragment.kt — AI 월 한도 게이팅
```

---

## Task 1: RevenueCat 의존성 추가

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: RevenueCat 의존성 추가**

`app/build.gradle.kts`의 `dependencies { }` 블록에 추가:

```kotlin
implementation("com.revenuecat.purchases:purchases-android:7.12.0")
```

최종 dependencies 블록:
```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.4")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.4")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.revenuecat.purchases:purchases-android:7.12.0")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

- [ ] **Step 2: Gradle Sync 확인**

Android Studio → Sync Now 클릭 또는:
```bash
./gradlew dependencies --configuration releaseRuntimeClasspath | grep revenuecat
```
Expected: `com.revenuecat.purchases:purchases-android:7.12.0` 출력

- [ ] **Step 3: 커밋**

```bash
git add app/build.gradle.kts
git commit -m "build: add RevenueCat purchases-android dependency"
```

---

## Task 2: BillingConstants + EntitlementManager

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/billing/BillingConstants.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/billing/EntitlementManager.kt`

- [ ] **Step 1: BillingConstants.kt 생성**

```kotlin
// app/src/main/java/com/kore2/shortcutime/billing/BillingConstants.kt
package com.kore2.shortcutime.billing

object BillingConstants {
    /** RevenueCat 대시보드 → API Keys → Public SDK Key */
    const val REVENUECAT_API_KEY = "YOUR_REVENUECAT_ANDROID_API_KEY"

    /** Google Play Console에 등록한 인앱 상품 ID */
    const val PRODUCT_ID = "pro_lifetime"

    /** RevenueCat Entitlement ID */
    const val ENTITLEMENT_ID = "pro"

    // ── Free tier limits ──────────────────────────────────────────────────
    const val FREE_MAX_FOLDERS = 2
    const val FREE_MAX_SHORTCUTS_PER_FOLDER = 10
    const val FREE_AI_MONTHLY_CAP = 20
}
```

- [ ] **Step 2: EntitlementManager.kt 생성**

```kotlin
// app/src/main/java/com/kore2/shortcutime/billing/EntitlementManager.kt
package com.kore2.shortcutime.billing

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.models.PurchaseParams
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestorePurchases
import java.util.Calendar

class EntitlementManager(context: Context) {

    private val prefs = context.getSharedPreferences("entitlement_prefs", Context.MODE_PRIVATE)

    // ── Pro 여부 ─────────────────────────────────────────────────────────

    /**
     * RevenueCat 캐시 기반으로 Pro 여부를 동기적으로 반환한다.
     * Purchases.configure() 이후 항상 사용 가능. 네트워크 불필요.
     */
    fun isPro(): Boolean = try {
        Purchases.sharedInstance
            .cachedCustomerInfo
            ?.entitlements
            ?.get(BillingConstants.ENTITLEMENT_ID)
            ?.isActive == true
    } catch (_: Exception) {
        false
    }

    // ── 구매 흐름 ─────────────────────────────────────────────────────────

    suspend fun purchase(activity: Activity): PurchaseResult {
        return try {
            val offerings = Purchases.sharedInstance.awaitOfferings()
            val pkg = offerings.current?.availablePackages?.firstOrNull()
                ?: return PurchaseResult.Error("구매 가능한 상품이 없습니다")
            val (_, _) = Purchases.sharedInstance.awaitPurchase(
                PurchaseParams.Builder(activity, pkg).build()
            )
            PurchaseResult.Success
        } catch (e: PurchasesException) {
            if (e.purchasesError.code == PurchasesErrorCode.PurchaseCancelledError) {
                PurchaseResult.Cancelled
            } else {
                PurchaseResult.Error(e.purchasesError.message)
            }
        } catch (e: Exception) {
            PurchaseResult.Error(e.message ?: "알 수 없는 오류")
        }
    }

    suspend fun restorePurchases(): RestoreResult {
        return try {
            val customerInfo = Purchases.sharedInstance.awaitRestorePurchases()
            if (customerInfo.entitlements[BillingConstants.ENTITLEMENT_ID]?.isActive == true) {
                RestoreResult.Restored
            } else {
                RestoreResult.NothingToRestore
            }
        } catch (e: PurchasesException) {
            RestoreResult.Error(e.purchasesError.message)
        } catch (e: Exception) {
            RestoreResult.Error(e.message ?: "복원 중 오류")
        }
    }

    // ── 무료 AI 월 사용량 ─────────────────────────────────────────────────

    fun getMonthlyAiUsage(): Int {
        val storedMonth = prefs.getString(KEY_AI_MONTH, "") ?: ""
        return if (storedMonth == currentYearMonth()) {
            prefs.getInt(KEY_AI_COUNT, 0)
        } else {
            0 // 월이 바뀌었으면 0으로 간주
        }
    }

    fun incrementMonthlyAiUsage() {
        val currentMonth = currentYearMonth()
        val storedMonth = prefs.getString(KEY_AI_MONTH, "") ?: ""
        val current = if (storedMonth == currentMonth) prefs.getInt(KEY_AI_COUNT, 0) else 0
        prefs.edit()
            .putString(KEY_AI_MONTH, currentMonth)
            .putInt(KEY_AI_COUNT, current + 1)
            .apply()
    }

    private fun currentYearMonth(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
    }

    // ── 결과 sealed classes ───────────────────────────────────────────────

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

    companion object {
        private const val KEY_AI_MONTH = "ai_usage_month"
        private const val KEY_AI_COUNT = "ai_usage_count"
    }
}
```

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/billing/
git commit -m "feat: add BillingConstants and EntitlementManager"
```

---

## Task 3: EntitlementManager 단위 테스트

**Files:**
- Create: `app/src/test/java/com/kore2/shortcutime/billing/EntitlementManagerTest.kt`

- [ ] **Step 1: 테스트 디렉터리 생성**

```bash
mkdir -p "app/src/test/java/com/kore2/shortcutime/billing"
```

- [ ] **Step 2: 실패하는 테스트 작성**

```kotlin
// app/src/test/java/com/kore2/shortcutime/billing/EntitlementManagerTest.kt
package com.kore2.shortcutime.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntitlementManagerTest {

    private lateinit var context: Context
    private lateinit var manager: EntitlementManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // 각 테스트마다 깨끗한 prefs 사용
        context.getSharedPreferences("entitlement_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        manager = EntitlementManager(context)
    }

    @Test
    fun `getMonthlyAiUsage returns 0 initially`() {
        assertEquals(0, manager.getMonthlyAiUsage())
    }

    @Test
    fun `incrementMonthlyAiUsage increments count`() {
        manager.incrementMonthlyAiUsage()
        manager.incrementMonthlyAiUsage()
        assertEquals(2, manager.getMonthlyAiUsage())
    }

    @Test
    fun `getMonthlyAiUsage resets when month changes`() {
        // 이전 달 데이터를 직접 주입
        val prefs = context.getSharedPreferences("entitlement_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ai_usage_month", "1999-0") // 과거 달
            .putInt("ai_usage_count", 15)
            .commit()
        assertEquals(0, manager.getMonthlyAiUsage())
    }

    @Test
    fun `incrementMonthlyAiUsage resets count when month changes`() {
        val prefs = context.getSharedPreferences("entitlement_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ai_usage_month", "1999-0")
            .putInt("ai_usage_count", 15)
            .commit()
        manager.incrementMonthlyAiUsage()
        assertEquals(1, manager.getMonthlyAiUsage())
    }
}
```

- [ ] **Step 3: 테스트 실행 — 실패 확인**

```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat test --tests "com.kore2.shortcutime.billing.EntitlementManagerTest"
```
Expected: BUILD FAILED (EntitlementManager 존재하므로 실제로는 PASS할 수 있음. PASS면 Step 4 진행)

- [ ] **Step 4: 테스트 통과 확인**

```bash
.\gradlew.bat test --tests "com.kore2.shortcutime.billing.EntitlementManagerTest"
```
Expected: `BUILD SUCCESSFUL`, 4 tests passed

- [ ] **Step 5: 커밋**

```bash
git add app/src/test/java/com/kore2/shortcutime/billing/EntitlementManagerTest.kt
git commit -m "test: add EntitlementManager monthly AI usage unit tests"
```

---

## Task 4: BillingUiHelper + ShortcutApplication 연결

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/billing/BillingUiHelper.kt`
- Modify: `app/src/main/java/com/kore2/shortcutime/ShortcutApplication.kt`

- [ ] **Step 1: BillingUiHelper.kt 생성**

```kotlin
// app/src/main/java/com/kore2/shortcutime/billing/BillingUiHelper.kt
package com.kore2.shortcutime.billing

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kore2.shortcutime.R

enum class LimitReason { FOLDER, SHORTCUT, AI, CSV }

/**
 * 한도 초과 시 다이얼로그를 표시한다.
 * "Pro 보기" 클릭 시 ProUpgradeFragment로 이동한다.
 */
fun Fragment.showLimitDialog(reason: LimitReason) {
    val (title, message) = when (reason) {
        LimitReason.FOLDER -> Pair(
            getString(R.string.limit_folder_title),
            getString(R.string.limit_folder_message)
        )
        LimitReason.SHORTCUT -> Pair(
            getString(R.string.limit_shortcut_title),
            getString(R.string.limit_shortcut_message)
        )
        LimitReason.AI -> Pair(
            getString(R.string.limit_ai_title),
            getString(R.string.limit_ai_message)
        )
        LimitReason.CSV -> Pair(
            getString(R.string.limit_csv_title),
            getString(R.string.limit_csv_message)
        )
    }
    AlertDialog.Builder(requireContext())
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(R.string.pro_see_upgrade) { _, _ ->
            findNavController().navigate(R.id.action_global_to_proUpgrade)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
```

- [ ] **Step 2: ShortcutApplication.kt 수정 — entitlementManager 추가 + RevenueCat 초기화**

전체 파일을 다음으로 교체:

```kotlin
// app/src/main/java/com/kore2/shortcutime/ShortcutApplication.kt
package com.kore2.shortcutime

import android.app.Application
import android.content.Context
import com.kore2.shortcutime.billing.BillingConstants
import com.kore2.shortcutime.billing.EntitlementManager
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.data.SecureKeyStore
import com.kore2.shortcutime.data.SystemClock
import com.kore2.shortcutime.llm.ExampleGenerationService
import com.kore2.shortcutime.llm.HttpClientFactory
import com.kore2.shortcutime.llm.LlmRegistry
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class ShortcutApplication : Application() {
    val repository: FolderRepository by lazy { FolderRepository(applicationContext) }
    val themeStore: KeyboardThemeStore by lazy { KeyboardThemeStore(applicationContext) }
    val entitlementManager: EntitlementManager by lazy { EntitlementManager(applicationContext) }

    val clock by lazy { SystemClock() }
    val secureKeyStore by lazy { SecureKeyStore.create(applicationContext) }
    val llmSettingsStore by lazy { LlmSettingsStore(applicationContext, clock) }
    val llmRegistry by lazy { LlmRegistry(HttpClientFactory.create(debug = BuildConfig.DEBUG)) }
    val exampleGenerationService by lazy {
        ExampleGenerationService(secureKeyStore, llmSettingsStore, llmRegistry)
    }

    override fun onCreate() {
        super.onCreate()

        // RevenueCat 초기화 — entitlementManager보다 먼저 실행되어야 함
        if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, BillingConstants.REVENUECAT_API_KEY).build()
        )

        // 크래시 진단 핸들러
        val prefs = getSharedPreferences("crash_diag", Context.MODE_PRIVATE)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            prefs.edit()
                .putString("crash_trace", throwable.stackTraceToString().take(2000))
                .apply()
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        fun from(context: Context): ShortcutApplication = context.applicationContext as ShortcutApplication
    }
}
```

- [ ] **Step 3: 빌드 확인**

```bash
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/billing/BillingUiHelper.kt
git add app/src/main/java/com/kore2/shortcutime/ShortcutApplication.kt
git commit -m "feat: wire EntitlementManager and RevenueCat init into ShortcutApplication"
```

---

## Task 5: Pro 문자열 추가

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: strings.xml 끝 `</resources>` 직전에 추가**

```xml
    <!-- ── Pro billing ──────────────────────────────────────────────── -->
    <string name="pro_upgrade_title">Pro 업그레이드</string>
    <string name="pro_hero_title">Shortcut IME Pro</string>
    <string name="pro_hero_subtitle">단 한 번의 결제로 평생 이용</string>
    <string name="pro_price">₩5,900</string>
    <string name="pro_price_label">평생 이용권 · 일회성 결제</string>
    <string name="pro_cta">지금 Pro로 업그레이드</string>
    <string name="pro_restore">구매 복원</string>
    <string name="pro_already_active">이미 Pro를 사용 중입니다 🎉</string>
    <string name="pro_badge">PRO</string>
    <string name="pro_settings_row">Pro로 업그레이드</string>
    <string name="pro_settings_row_sub">모든 기능을 제한 없이</string>
    <!-- comparison table -->
    <string name="pro_col_feature">기능</string>
    <string name="pro_col_free">Free</string>
    <string name="pro_col_pro">Pro</string>
    <string name="pro_row_folders">폴더</string>
    <string name="pro_row_shortcuts">Shortcut</string>
    <string name="pro_row_ai">AI 예문 생성</string>
    <string name="pro_row_csv">CSV 내보내기</string>
    <string name="pro_row_themes">키보드 테마</string>
    <string name="pro_row_future">향후 신기능</string>
    <string name="pro_free_folders">2개</string>
    <string name="pro_free_shortcuts">10개/폴더</string>
    <string name="pro_free_ai">월 20회</string>
    <string name="pro_free_csv">✕</string>
    <string name="pro_free_themes">3종</string>
    <string name="pro_free_future">✕</string>
    <string name="pro_paid_folders">무제한</string>
    <string name="pro_paid_shortcuts">무제한</string>
    <string name="pro_paid_ai">무제한</string>
    <string name="pro_paid_csv">✓</string>
    <string name="pro_paid_themes">10종 전체</string>
    <string name="pro_paid_future">우선 제공</string>
    <!-- toasts -->
    <string name="toast_purchase_success">Pro 업그레이드 완료! 감사합니다 🎉</string>
    <string name="toast_restore_success">구매가 복원되었습니다.</string>
    <string name="toast_restore_nothing">복원할 구매 내역이 없어요.</string>
    <string name="toast_purchase_cancelled">구매가 취소되었습니다.</string>
    <string name="toast_purchase_error">구매 중 오류: %1$s</string>
    <!-- limit dialogs -->
    <string name="limit_folder_title">폴더 한도 도달</string>
    <string name="limit_folder_message">무료 버전은 폴더를 2개까지 만들 수 있어요.\nPro로 업그레이드하면 제한 없이 사용할 수 있어요.</string>
    <string name="limit_shortcut_title">Shortcut 한도 도달</string>
    <string name="limit_shortcut_message">무료 버전은 폴더당 Shortcut을 10개까지 만들 수 있어요.\nPro로 업그레이드하면 제한 없이 사용할 수 있어요.</string>
    <string name="limit_ai_title">AI 생성 한도 도달</string>
    <string name="limit_ai_message">무료 버전은 이번 달 AI 예문을 20회까지 생성할 수 있어요.\nPro로 업그레이드하면 제한 없이 사용할 수 있어요.</string>
    <string name="limit_csv_title">Pro 전용 기능</string>
    <string name="limit_csv_message">CSV 내보내기는 Pro 전용 기능이에요.\nPro로 업그레이드하면 사용할 수 있어요.</string>
    <string name="pro_see_upgrade">Pro 보기</string>
```

- [ ] **Step 2: 커밋**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add Pro billing string resources"
```

---

## Task 6: nav_graph + fragment_pro_upgrade.xml 레이아웃

**Files:**
- Modify: `app/src/main/res/navigation/nav_graph.xml`
- Create: `app/src/main/res/layout/fragment_pro_upgrade.xml`

- [ ] **Step 1: nav_graph.xml에 proUpgradeFragment 노드 + 글로벌 액션 추가**

`</navigation>` 직전에 추가:

```xml
    <fragment
        android:id="@+id/proUpgradeFragment"
        android:name="com.kore2.shortcutime.ui.pro.ProUpgradeFragment"
        android:label="ProUpgrade"
        tools:layout="@layout/fragment_pro_upgrade"
        xmlns:tools="http://schemas.android.com/tools" />

    <!-- 어느 화면에서든 Pro 화면으로 이동 가능한 글로벌 액션 -->
    <action
        android:id="@+id/action_global_to_proUpgrade"
        app:destination="@id/proUpgradeFragment" />
```

`settingsFragment` 노드 내 기존 액션 아래에 Pro 액션 추가:

```xml
    <fragment
        android:id="@+id/settingsFragment"
        android:name="com.kore2.shortcutime.ui.settings.SettingsFragment"
        android:label="Settings"
        tools:layout="@layout/fragment_settings"
        xmlns:tools="http://schemas.android.com/tools">
        <action
            android:id="@+id/action_settings_to_llmSettings"
            app:destination="@id/llmSettingsFragment" />
        <action
            android:id="@+id/action_settings_to_proUpgrade"
            app:destination="@id/proUpgradeFragment" />
    </fragment>
```

최종 nav_graph.xml 전체:
```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/folderListFragment">

    <fragment
        android:id="@+id/folderListFragment"
        android:name="com.kore2.shortcutime.ui.list.FolderListFragment"
        android:label="FolderList"
        tools:layout="@layout/fragment_folder_list"
        xmlns:tools="http://schemas.android.com/tools">
        <action
            android:id="@+id/action_folderList_to_folderDetail"
            app:destination="@id/folderDetailFragment" />
        <action
            android:id="@+id/action_folderList_to_folderEditor"
            app:destination="@id/folderEditorFragment" />
        <action
            android:id="@+id/action_folderList_to_settings"
            app:destination="@id/settingsFragment" />
    </fragment>

    <fragment
        android:id="@+id/folderDetailFragment"
        android:name="com.kore2.shortcutime.ui.detail.FolderDetailFragment"
        android:label="FolderDetail"
        tools:layout="@layout/fragment_folder_detail"
        xmlns:tools="http://schemas.android.com/tools">
        <argument
            android:name="folderId"
            app:argType="string" />
        <action
            android:id="@+id/action_folderDetail_to_shortcutEditor"
            app:destination="@id/shortcutEditorFragment" />
        <action
            android:id="@+id/action_folderDetail_to_folderEditor"
            app:destination="@id/folderEditorFragment" />
    </fragment>

    <fragment
        android:id="@+id/folderEditorFragment"
        android:name="com.kore2.shortcutime.ui.editor.FolderEditorFragment"
        android:label="FolderEditor"
        tools:layout="@layout/fragment_folder_editor"
        xmlns:tools="http://schemas.android.com/tools">
        <argument
            android:name="folderId"
            app:argType="string"
            app:nullable="true"
            android:defaultValue="@null" />
    </fragment>

    <fragment
        android:id="@+id/shortcutEditorFragment"
        android:name="com.kore2.shortcutime.ui.editor.ShortcutEditorFragment"
        android:label="ShortcutEditor"
        tools:layout="@layout/fragment_shortcut_editor"
        xmlns:tools="http://schemas.android.com/tools">
        <argument
            android:name="folderId"
            app:argType="string" />
        <argument
            android:name="shortcutId"
            app:argType="string"
            app:nullable="true"
            android:defaultValue="@null" />
        <action
            android:id="@+id/action_shortcutEditor_self"
            app:destination="@id/shortcutEditorFragment"
            app:popUpTo="@id/shortcutEditorFragment"
            app:popUpToInclusive="true" />
        <action
            android:id="@+id/action_shortcutEditor_to_llmSettings"
            app:destination="@id/llmSettingsFragment" />
    </fragment>

    <fragment
        android:id="@+id/settingsFragment"
        android:name="com.kore2.shortcutime.ui.settings.SettingsFragment"
        android:label="Settings"
        tools:layout="@layout/fragment_settings"
        xmlns:tools="http://schemas.android.com/tools">
        <action
            android:id="@+id/action_settings_to_llmSettings"
            app:destination="@id/llmSettingsFragment" />
        <action
            android:id="@+id/action_settings_to_proUpgrade"
            app:destination="@id/proUpgradeFragment" />
    </fragment>

    <fragment
        android:id="@+id/llmSettingsFragment"
        android:name="com.kore2.shortcutime.ui.settings.llm.LlmSettingsFragment"
        android:label="LlmSettings"
        tools:layout="@layout/fragment_llm_settings"
        xmlns:tools="http://schemas.android.com/tools" />

    <fragment
        android:id="@+id/proUpgradeFragment"
        android:name="com.kore2.shortcutime.ui.pro.ProUpgradeFragment"
        android:label="ProUpgrade"
        tools:layout="@layout/fragment_pro_upgrade"
        xmlns:tools="http://schemas.android.com/tools" />

    <action
        android:id="@+id/action_global_to_proUpgrade"
        app:destination="@id/proUpgradeFragment" />

</navigation>
```

- [ ] **Step 2: fragment_pro_upgrade.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.core.widget.NestedScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_app">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/topToolbar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:layout_marginStart="16dp"
            android:layout_marginEnd="16dp"
            android:backgroundTint="@color/bg_panel"
            android:titleTextColor="@color/text_primary" />

        <!-- Hero 영역 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:paddingTop="28dp"
            android:paddingBottom="12dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="✦"
                android:textSize="36sp"
                android:textColor="@color/accent" />

            <TextView
                android:id="@+id/heroTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/pro_hero_title"
                android:textSize="22sp"
                android:textStyle="bold"
                android:textColor="@color/accent"
                android:layout_marginTop="6dp" />

            <TextView
                android:id="@+id/heroSubtitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/pro_hero_subtitle"
                android:textSize="13sp"
                android:textColor="@color/text_secondary"
                android:layout_marginTop="4dp" />
        </LinearLayout>

        <!-- 비교표 -->
        <LinearLayout
            android:id="@+id/comparisonTable"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:layout_marginStart="16dp"
            android:layout_marginEnd="16dp"
            android:layout_marginTop="8dp"
            android:background="@color/bg_panel"
            android:padding="0dp" />

        <!-- 가격 + 구매 버튼 영역 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:layout_marginStart="16dp"
            android:layout_marginEnd="16dp"
            android:layout_marginTop="16dp"
            android:gravity="center"
            android:background="@color/bg_panel"
            android:padding="16dp">

            <TextView
                android:id="@+id/priceLabel"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/pro_price_label"
                android:textSize="12sp"
                android:textColor="@color/text_secondary" />

            <TextView
                android:id="@+id/priceText"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/pro_price"
                android:textSize="28sp"
                android:textStyle="bold"
                android:textColor="@color/accent"
                android:layout_marginTop="4dp"
                android:layout_marginBottom="16dp" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/purchaseButton"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/pro_cta"
                android:textSize="15sp" />

        </LinearLayout>

        <!-- 이미 Pro인 경우 표시 -->
        <TextView
            android:id="@+id/alreadyProText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/pro_already_active"
            android:textSize="15sp"
            android:textColor="@color/accent"
            android:gravity="center"
            android:layout_marginTop="8dp"
            android:visibility="gone" />

        <!-- 복원 + 약관 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:gravity="center"
            android:padding="20dp">

            <TextView
                android:id="@+id/restoreButton"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/pro_restore"
                android:textSize="13sp"
                android:textColor="@color/text_secondary"
                android:padding="8dp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="구매 시 Google Play 이용약관에 동의합니다."
                android:textSize="11sp"
                android:textColor="@color/text_secondary"
                android:layout_marginTop="8dp"
                android:gravity="center" />
        </LinearLayout>

    </LinearLayout>
</androidx.core.widget.NestedScrollView>
```

- [ ] **Step 3: 빌드 확인**

```bash
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/res/navigation/nav_graph.xml
git add app/src/main/res/layout/fragment_pro_upgrade.xml
git commit -m "feat: add ProUpgradeFragment nav node, global action, and layout"
```

---

## Task 7: ProUpgradeFragment 구현

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/ui/pro/ProUpgradeFragment.kt`

- [ ] **Step 1: ProUpgradeFragment.kt 생성**

```kotlin
// app/src/main/java/com/kore2/shortcutime/ui/pro/ProUpgradeFragment.kt
package com.kore2.shortcutime.ui.pro

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.billing.EntitlementManager
import com.kore2.shortcutime.databinding.FragmentProUpgradeBinding
import com.kore2.shortcutime.ui.applyFabTheme
import com.kore2.shortcutime.ui.applyFilledButtonTheme
import com.kore2.shortcutime.ui.applyToolbarTheme
import kotlinx.coroutines.launch

class ProUpgradeFragment : Fragment() {

    private var _binding: FragmentProUpgradeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProUpgradeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topToolbar.title = getString(R.string.pro_upgrade_title)
        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        buildComparisonTable()

        val entitlement = ShortcutApplication.from(requireContext()).entitlementManager

        if (entitlement.isPro()) {
            showAlreadyPro()
        }

        binding.purchaseButton.setOnClickListener {
            lifecycleScope.launch { handlePurchase(entitlement) }
        }

        binding.restoreButton.setOnClickListener {
            lifecycleScope.launch { handleRestore(entitlement) }
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── 구매 흐름 ─────────────────────────────────────────────────────────

    private suspend fun handlePurchase(entitlement: EntitlementManager) {
        binding.purchaseButton.isEnabled = false
        when (val result = entitlement.purchase(requireActivity())) {
            EntitlementManager.PurchaseResult.Success -> {
                Toast.makeText(requireContext(), R.string.toast_purchase_success, Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
            EntitlementManager.PurchaseResult.Cancelled -> {
                Toast.makeText(requireContext(), R.string.toast_purchase_cancelled, Toast.LENGTH_SHORT).show()
                binding.purchaseButton.isEnabled = true
            }
            is EntitlementManager.PurchaseResult.Error -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_purchase_error, result.message),
                    Toast.LENGTH_LONG,
                ).show()
                binding.purchaseButton.isEnabled = true
            }
        }
    }

    private suspend fun handleRestore(entitlement: EntitlementManager) {
        binding.restoreButton.isEnabled = false
        when (val result = entitlement.restorePurchases()) {
            EntitlementManager.RestoreResult.Restored -> {
                Toast.makeText(requireContext(), R.string.toast_restore_success, Toast.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
            EntitlementManager.RestoreResult.NothingToRestore -> {
                Toast.makeText(requireContext(), R.string.toast_restore_nothing, Toast.LENGTH_SHORT).show()
                binding.restoreButton.isEnabled = true
            }
            is EntitlementManager.RestoreResult.Error -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.toast_purchase_error, result.message),
                    Toast.LENGTH_LONG,
                ).show()
                binding.restoreButton.isEnabled = true
            }
        }
    }

    // ── 이미 Pro ──────────────────────────────────────────────────────────

    private fun showAlreadyPro() {
        binding.purchaseButton.isEnabled = false
        binding.purchaseButton.alpha = 0.4f
        binding.alreadyProText.visibility = View.VISIBLE
    }

    // ── 비교표 동적 생성 ──────────────────────────────────────────────────

    private fun buildComparisonTable() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        val container = binding.comparisonTable
        container.removeAllViews()

        val rows = listOf(
            Triple(R.string.pro_row_folders,   R.string.pro_free_folders,   R.string.pro_paid_folders),
            Triple(R.string.pro_row_shortcuts, R.string.pro_free_shortcuts, R.string.pro_paid_shortcuts),
            Triple(R.string.pro_row_ai,        R.string.pro_free_ai,        R.string.pro_paid_ai),
            Triple(R.string.pro_row_csv,       R.string.pro_free_csv,       R.string.pro_paid_csv),
            Triple(R.string.pro_row_themes,    R.string.pro_free_themes,    R.string.pro_paid_themes),
            Triple(R.string.pro_row_future,    R.string.pro_free_future,    R.string.pro_paid_future),
        )

        // 헤더 행
        container.addView(buildRow(
            label = getString(R.string.pro_col_feature),
            freeVal = getString(R.string.pro_col_free),
            proVal = getString(R.string.pro_col_pro),
            isHeader = true,
            bgColor = theme.previewBackground,
            labelColor = theme.textSecondary,
            freeColor = theme.textSecondary,
            proColor = theme.accentColor,
        ))

        // 데이터 행
        rows.forEach { (featureRes, freeRes, proRes) ->
            container.addView(buildRow(
                label = getString(featureRes),
                freeVal = getString(freeRes),
                proVal = getString(proRes),
                isHeader = false,
                bgColor = theme.previewBackground,
                labelColor = theme.textPrimary,
                freeColor = theme.textSecondary,
                proColor = theme.accentColor,
            ))
        }
    }

    private fun buildRow(
        label: String,
        freeVal: String,
        proVal: String,
        isHeader: Boolean,
        bgColor: Int,
        labelColor: Int,
        freeColor: Int,
        proColor: Int,
    ): LinearLayout {
        val density = resources.displayMetrics.density
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bgColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        fun cell(text: String, color: Int, weight: Float, align: Int = Gravity.START): TextView {
            return TextView(requireContext()).apply {
                this.text = text
                textSize = if (isHeader) 11f else 12f
                setTextColor(color)
                if (isHeader) setTypeface(typeface, Typeface.BOLD)
                gravity = align or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
                    val padH = (12 * density).toInt()
                    val padV = (10 * density).toInt()
                    setPadding(padH, padV, padH, padV)
                }
            }
        }

        row.addView(cell(label, labelColor, 1.4f))
        row.addView(cell(freeVal, freeColor, 1f, Gravity.CENTER_HORIZONTAL))
        row.addView(cell(proVal, proColor, 1f, Gravity.CENTER_HORIZONTAL))
        return row
    }

    // ── 테마 ─────────────────────────────────────────────────────────────

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.heroTitle.setTextColor(theme.accentColor)
        binding.heroSubtitle.setTextColor(theme.textSecondary)
        binding.priceLabel.setTextColor(theme.textSecondary)
        binding.priceText.setTextColor(theme.accentColor)
        binding.comparisonTable.setBackgroundColor(theme.previewBackground)
        binding.restoreButton.setTextColor(theme.textSecondary)
        applyFilledButtonTheme(binding.purchaseButton, theme)
        buildComparisonTable()
    }
}
```

- [ ] **Step 2: 빌드 확인**

```bash
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 수동 확인**

기기/에뮬레이터에 APK 설치 후:
- 설정 화면 없는 상태에서 NavController로 `R.id.action_global_to_proUpgrade` 직접 호출 가능한지 확인
- ProUpgradeFragment 화면이 렌더링되고 비교표가 보이는지 확인

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/pro/
git commit -m "feat: implement ProUpgradeFragment with purchase and restore flows"
```

---

## Task 8: 설정 화면 Pro 업그레이드 행 추가

**Files:**
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/settings/SettingsFragment.kt`

- [ ] **Step 1: fragment_settings.xml에 Pro 버튼 추가**

`aiSettingsButton` 아래에 추가:

```xml
    <com.google.android.material.button.MaterialButton
        android:id="@+id/proUpgradeButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginStart="16dp"
        android:layout_marginEnd="16dp"
        android:layout_marginTop="8dp"
        android:text="@string/pro_settings_row" />
```

- [ ] **Step 2: SettingsFragment.kt에 Pro 버튼 클릭 리스너 추가**

`onViewCreated`의 `aiSettingsButton` 리스너 아래에 추가:

```kotlin
binding.proUpgradeButton.setOnClickListener {
    findNavController().navigate(R.id.action_settings_to_proUpgrade)
}
```

- [ ] **Step 3: applyTheme()에 Pro 버튼 테마 적용 추가**

`applyTheme()` 내 기존 코드 아래에:
```kotlin
applyFilledButtonTheme(binding.proUpgradeButton, theme)
```

`applyFilledButtonTheme` import 추가:
```kotlin
import com.kore2.shortcutime.ui.applyFilledButtonTheme
```

최종 SettingsFragment.kt:
```kotlin
package com.kore2.shortcutime.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.databinding.FragmentSettingsBinding
import com.kore2.shortcutime.ui.applyFilledButtonTheme
import com.kore2.shortcutime.ui.applyToolbarTheme

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.aiSettingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_llmSettings)
        }
        binding.proUpgradeButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_proUpgrade)
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.placeholderText.setTextColor(theme.textSecondary)
        applyFilledButtonTheme(binding.aiSettingsButton, theme)
        applyFilledButtonTheme(binding.proUpgradeButton, theme)
    }
}
```

- [ ] **Step 4: 빌드 확인**

```bash
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/res/layout/fragment_settings.xml
git add app/src/main/java/com/kore2/shortcutime/ui/settings/SettingsFragment.kt
git commit -m "feat: add Pro upgrade entry point in SettingsFragment"
```

---

## Task 9: FolderListFragment 폴더 한도 게이팅

**Files:**
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/list/FolderListFragment.kt`

- [ ] **Step 1: import 추가 + addFolderFab 클릭 핸들러 수정**

`openFolderEditor(null)` 단독 호출을 다음으로 교체:

```kotlin
import com.kore2.shortcutime.billing.BillingConstants
import com.kore2.shortcutime.billing.LimitReason
import com.kore2.shortcutime.billing.showLimitDialog
```

`binding.addFolderFab.setOnClickListener { openFolderEditor(null) }` 를 다음으로 교체:

```kotlin
binding.addFolderFab.setOnClickListener {
    val em = ShortcutApplication.from(requireContext()).entitlementManager
    val folderCount = viewModel.folders.value.size
    if (!em.isPro() && folderCount >= BillingConstants.FREE_MAX_FOLDERS) {
        showLimitDialog(LimitReason.FOLDER)
    } else {
        openFolderEditor(null)
    }
}
```

- [ ] **Step 2: 빌드 확인**

```bash
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 수동 테스트**

1. 앱 실행 → 폴더 2개 생성
2. 세 번째 폴더 추가 FAB 탭
3. "폴더 한도 도달" 다이얼로그 표시 확인
4. "Pro 보기" 탭 → ProUpgradeFragment 이동 확인

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/list/FolderListFragment.kt
git commit -m "feat: gate folder creation at FREE_MAX_FOLDERS limit"
```

---

## Task 10: FolderDetailFragment Shortcut 한도 + CSV 게이팅

**Files:**
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/detail/FolderDetailFragment.kt`

- [ ] **Step 1: import 추가**

```kotlin
import com.kore2.shortcutime.billing.BillingConstants
import com.kore2.shortcutime.billing.LimitReason
import com.kore2.shortcutime.billing.showLimitDialog
```

- [ ] **Step 2: addShortcutFab 클릭 핸들러 수정**

`binding.addShortcutFab.setOnClickListener { openShortcutEditor(null) }` 를 교체:

```kotlin
binding.addShortcutFab.setOnClickListener {
    val em = ShortcutApplication.from(requireContext()).entitlementManager
    val state = viewModel.state.value as? FolderDetailState.Loaded
    val shortcutCount = state?.folder?.shortcuts?.size ?: 0
    if (!em.isPro() && shortcutCount >= BillingConstants.FREE_MAX_SHORTCUTS_PER_FOLDER) {
        showLimitDialog(LimitReason.SHORTCUT)
    } else {
        openShortcutEditor(null)
    }
}
```

- [ ] **Step 3: exportCsvFab 클릭 핸들러에 Pro 게이팅 추가**

기존 `binding.exportCsvFab.setOnClickListener { ... }` 블록의 맨 앞에 체크 추가:

```kotlin
binding.exportCsvFab.setOnClickListener {
    val em = ShortcutApplication.from(requireContext()).entitlementManager
    if (!em.isPro()) {
        showLimitDialog(LimitReason.CSV)
        return@setOnClickListener
    }
    // 기존 CSV 로직 (아래 그대로 유지)
    val state = viewModel.state.value as? FolderDetailState.Loaded ?: return@setOnClickListener
    val safeName = state.folder.title
        .replace(Regex("[^\\w가-힣\\s-]"), "")
        .trim()
        .ifBlank { "shortcuts" }
    createCsvLauncher.launch("${safeName}_shortcuts.csv")
}
```

- [ ] **Step 4: 빌드 확인**

```bash
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 수동 테스트**

1. 폴더에 Shortcut 10개 생성
2. 열한 번째 추가 FAB 탭 → "Shortcut 한도 도달" 다이얼로그 확인
3. CSV 내보내기 FAB 탭 → "Pro 전용 기능" 다이얼로그 확인

- [ ] **Step 6: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/detail/FolderDetailFragment.kt
git commit -m "feat: gate shortcut creation and CSV export behind Pro entitlement"
```

---

## Task 11: ShortcutEditorFragment AI 월 한도 게이팅

**Files:**
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorFragment.kt`

- [ ] **Step 1: import 추가**

```kotlin
import com.kore2.shortcutime.billing.BillingConstants
import com.kore2.shortcutime.billing.LimitReason
import com.kore2.shortcutime.billing.showLimitDialog
```

- [ ] **Step 2: generateExamplesButton 클릭 핸들러에 한도 확인 추가**

기존:
```kotlin
binding.generateExamplesButton.setOnClickListener {
    val shortcut = binding.shortcutInput.text?.toString().orEmpty().trim()
    val expansion = binding.expandsToInput.text?.toString().orEmpty().trim()
    viewModel.onGenerateExamplesClicked(shortcut, expansion, selectedGenerateCount)
}
```

교체:
```kotlin
binding.generateExamplesButton.setOnClickListener {
    val em = ShortcutApplication.from(requireContext()).entitlementManager
    if (!em.isPro() && em.getMonthlyAiUsage() >= BillingConstants.FREE_AI_MONTHLY_CAP) {
        showLimitDialog(LimitReason.AI)
        return@setOnClickListener
    }
    val shortcut = binding.shortcutInput.text?.toString().orEmpty().trim()
    val expansion = binding.expandsToInput.text?.toString().orEmpty().trim()
    viewModel.onGenerateExamplesClicked(shortcut, expansion, selectedGenerateCount)
}
```

- [ ] **Step 3: GenerateSuccess 이벤트 처리 시 사용량 증가**

`showEventSnackbar()` 내 `is ShortcutEditorViewModel.EditorEvent.GenerateSuccess` 케이스를 찾아서 수정:

```kotlin
is ShortcutEditorViewModel.EditorEvent.GenerateSuccess -> {
    // 성공 시에만 월 사용량 증가
    val em = ShortcutApplication.from(requireContext()).entitlementManager
    if (!em.isPro()) em.incrementMonthlyAiUsage()
    msg = getString(R.string.snack_generate_success_format, event.addedCount)
    actionLabel = null
    actionHandler = null
}
```

- [ ] **Step 4: 빌드 확인**

```bash
.\gradlew.bat assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 수동 테스트**

1. `EntitlementManager` SharedPreferences에 `ai_usage_count = 20`, `ai_usage_month = 현재년도-월` 직접 주입 (adb 또는 테스트 코드)
2. 자동 생성 버튼 탭 → "AI 생성 한도 도달" 다이얼로그 확인
3. 성공 시 카운트가 올바르게 증가하는지 SharedPreferences 확인

- [ ] **Step 6: 전체 테스트 실행**

```bash
.\gradlew.bat test
```
Expected: `BUILD SUCCESSFUL`, `EntitlementManagerTest` 4 tests PASSED

- [ ] **Step 7: 커밋 + 푸시**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorFragment.kt
git commit -m "feat: gate AI generation at FREE_AI_MONTHLY_CAP and track monthly usage"
git push origin main
```

---

## 최종 확인 체크리스트

- [ ] RevenueCat 대시보드에서 `REVENUECAT_API_KEY` 값이 `BillingConstants.kt`에 입력됨
- [ ] Google Play Console에 `pro_lifetime` 상품 등록됨
- [ ] RevenueCat에 `pro` entitlement + `pro_lifetime` product 연결됨
- [ ] Google Play Sandbox 계정으로 구매 흐름 end-to-end 테스트
- [ ] 구매 후 앱 재시작 시 `isPro() == true` 확인
- [ ] 앱 재설치 후 "구매 복원" → Pro 복원 확인
- [ ] 개인정보처리방침에 RevenueCat 제3자 처리 문구 추가
