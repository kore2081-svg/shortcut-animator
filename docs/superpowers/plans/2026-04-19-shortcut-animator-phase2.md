# Shortcut Animator Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phase 1 앱에 BYOK(Bring Your Own Key) 기반 LLM 예문 생성 기능을 5개 공급자(Anthropic Claude / OpenAI / Google Gemini / xAI Grok / DeepSeek)에 대해 추가한다.

**Architecture:** `OpenAiCompatibleAdapter` 하나로 OpenAI·Grok·DeepSeek 3개 공급자를 커버하고, Claude·Gemini 는 네이티브 어댑터 2개로 분리. Service 층(`ExampleGenerationService`)이 액티브 공급자 선택·일일 호출 캡·부분 성공 판정을 담당. API 키는 `EncryptedSharedPreferences` 로 저장.

**Tech Stack:** Kotlin + Coroutines + OkHttp 4.x + kotlinx.serialization + androidx.security.crypto + MockWebServer (테스트).

**Depends on:** `docs/superpowers/specs/2026-04-19-shortcut-animator-phase2-design.md`

**Conventions:**
- TDD 철저. 각 Task 안에서 먼저 실패 테스트 작성 → 구현 → 통과 확인 → 커밋.
- Gradle 빌드/테스트 실행: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew <task>` (Windows/bash).
- 테스트 실행: `./gradlew :app:testDebugUnitTest --tests <FQCN>`
- 커밋 메시지는 Phase 1 스타일: `feat(phase2): ...` / `test(phase2): ...` / `refactor(phase2): ...`.

---

## 파일 구조 맵

**신규 파일 (소스 27개):**
- `app/src/main/java/com/kore2/shortcutime/llm/ProviderId.kt` — 공급자 enum
- `app/src/main/java/com/kore2/shortcutime/llm/ModelInfo.kt` — 모델 메타
- `app/src/main/java/com/kore2/shortcutime/llm/ModelCatalog.kt` — 공급자별 모델 3종 맵
- `app/src/main/java/com/kore2/shortcutime/llm/LlmError.kt` — 에러 sealed class + LlmException
- `app/src/main/java/com/kore2/shortcutime/llm/GenerationResult.kt`
- `app/src/main/java/com/kore2/shortcutime/llm/LlmAdapter.kt` — 인터페이스
- `app/src/main/java/com/kore2/shortcutime/llm/PromptBuilder.kt`
- `app/src/main/java/com/kore2/shortcutime/llm/ParseExamples.kt` — 공용 JSON 파서
- `app/src/main/java/com/kore2/shortcutime/llm/LanguageClassifier.kt`
- `app/src/main/java/com/kore2/shortcutime/llm/HttpClientFactory.kt`
- `app/src/main/java/com/kore2/shortcutime/llm/OpenAiCompatibleAdapter.kt`
- `app/src/main/java/com/kore2/shortcutime/llm/ClaudeAdapter.kt`
- `app/src/main/java/com/kore2/shortcutime/llm/GeminiAdapter.kt`
- `app/src/main/java/com/kore2/shortcutime/llm/LlmRegistry.kt`
- `app/src/main/java/com/kore2/shortcutime/llm/ExampleGenerationService.kt`
- `app/src/main/java/com/kore2/shortcutime/data/Clock.kt` — 주입용 시계
- `app/src/main/java/com/kore2/shortcutime/data/SecureKeyStore.kt`
- `app/src/main/java/com/kore2/shortcutime/data/LlmSettings.kt`
- `app/src/main/java/com/kore2/shortcutime/data/LlmSettingsStore.kt`
- `app/src/main/java/com/kore2/shortcutime/ui/settings/llm/LlmSettingsFragment.kt`
- `app/src/main/java/com/kore2/shortcutime/ui/settings/llm/LlmSettingsViewModel.kt`
- `app/src/main/java/com/kore2/shortcutime/ui/settings/llm/ProviderRowAdapter.kt`
- `app/src/main/java/com/kore2/shortcutime/ui/settings/llm/ApiKeyDialogFragment.kt`
- `app/src/main/res/layout/fragment_llm_settings.xml`
- `app/src/main/res/layout/dialog_api_key.xml`
- `app/src/main/res/layout/item_provider_row.xml`

**신규 파일 (테스트 11개):**
- `app/src/test/java/com/kore2/shortcutime/llm/ModelCatalogTest.kt`
- `app/src/test/java/com/kore2/shortcutime/llm/PromptBuilderTest.kt`
- `app/src/test/java/com/kore2/shortcutime/llm/ParseExamplesTest.kt`
- `app/src/test/java/com/kore2/shortcutime/llm/LanguageClassifierTest.kt`
- `app/src/test/java/com/kore2/shortcutime/llm/OpenAiCompatibleAdapterTest.kt`
- `app/src/test/java/com/kore2/shortcutime/llm/ClaudeAdapterTest.kt`
- `app/src/test/java/com/kore2/shortcutime/llm/GeminiAdapterTest.kt`
- `app/src/test/java/com/kore2/shortcutime/llm/ExampleGenerationServiceTest.kt`
- `app/src/test/java/com/kore2/shortcutime/data/SecureKeyStoreTest.kt`
- `app/src/test/java/com/kore2/shortcutime/data/LlmSettingsStoreTest.kt`
- `app/src/test/java/com/kore2/shortcutime/ui/editor/ShortcutEditorViewModelPhase2Test.kt`

**수정 파일:**
- `app/build.gradle.kts` — 의존성 + plugin 추가
- `build.gradle.kts` (root) — kotlinx.serialization plugin classpath
- `app/src/main/java/com/kore2/shortcutime/ShortcutApplication.kt` — lazy 싱글톤 확장
- `app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorViewModel.kt` — EditorEvent + onGenerateExamplesClicked
- `app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorFragment.kt` — placeholder Toast 제거 + Snackbar 이벤트 처리
- `app/src/main/java/com/kore2/shortcutime/ui/settings/SettingsFragment.kt` — "AI 설정" 행 추가
- `app/src/main/res/layout/fragment_settings.xml` — "AI 설정" 버튼 추가
- `app/src/main/res/navigation/nav_graph.xml` — llmSettingsFragment destination + action
- `app/src/main/res/values/strings.xml` — Phase 2 문자열 일괄

---

## Task 1: Gradle 의존성 + kotlinx.serialization 플러그인

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts` (루트, 필요 시)

**Step 1: 루트 build.gradle.kts 에 serialization 플러그인 classpath 추가 (없으면)**

루트 `build.gradle.kts` 를 읽고 이미 `org.jetbrains.kotlin.plugin.serialization` classpath 가 있는지 확인. 없으면 `plugins { }` 블록 또는 `buildscript { dependencies { ... } }` 에 추가:

```kotlin
plugins {
    // ... 기존 ...
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
```

루트 파일에 `plugins` 블록이 없으면 `build.gradle.kts` 를 그대로 두고 모듈 수준에서만 버전 명시.

**Step 2: `app/build.gradle.kts` plugin 블록에 serialization 추가**

`plugins { }` 블록에 한 줄 추가:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")  // <-- 추가
    id("androidx.navigation.safeargs.kotlin")
}
```

**Step 3: `app/build.gradle.kts` dependencies 추가**

`dependencies { }` 블록 하단(`testImplementation` 들 위)에 추가:

```kotlin
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

**Step 4: Gradle sync + 기본 빌드 확인**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. 의존성 fetch 로 첫 실행은 느릴 수 있음.

**Step 5: 커밋**

```bash
git add app/build.gradle.kts build.gradle.kts
git commit -m "chore(phase2): add okhttp, kotlinx.serialization, security.crypto deps"
```

---

## Task 2: Provider / Model 카탈로그 데이터 타입

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/llm/ProviderId.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/llm/ModelInfo.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/llm/ModelCatalog.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/llm/ModelCatalogTest.kt`

**Step 1: 실패 테스트 작성**

`app/src/test/java/com/kore2/shortcutime/llm/ModelCatalogTest.kt`:

```kotlin
package com.kore2.shortcutime.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun `모든 5개 공급자가 카탈로그에 있다`() {
        ProviderId.values().forEach { id ->
            assertNotNull("$id 누락", ModelCatalog.modelsFor(id))
        }
        assertEquals(5, ProviderId.values().size)
    }

    @Test
    fun `각 공급자마다 최소 1개 이상의 추천 모델이 있다`() {
        ProviderId.values().forEach { id ->
            val models = ModelCatalog.modelsFor(id)
            assertTrue("$id 에 추천 모델 없음", models.any { it.isRecommended })
        }
    }

    @Test
    fun `recommendedModelId 가 modelsFor 목록 안에 포함된다`() {
        ProviderId.values().forEach { id ->
            val recommended = ModelCatalog.recommendedModelId(id)
            val ids = ModelCatalog.modelsFor(id).map { it.id }
            assertTrue("$id recommended=$recommended 가 목록에 없음", recommended in ids)
        }
    }
}
```

**Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.ModelCatalogTest"`
Expected: 컴파일 에러 — `ProviderId` / `ModelCatalog` 미정의.

**Step 3: `ProviderId.kt` 작성**

```kotlin
package com.kore2.shortcutime.llm

enum class ProviderId {
    OPENAI, CLAUDE, GEMINI, GROK, DEEPSEEK;
}
```

**Step 4: `ModelInfo.kt` 작성**

```kotlin
package com.kore2.shortcutime.llm

data class ModelInfo(
    val id: String,
    val displayName: String,
    val isRecommended: Boolean,
)
```

**Step 5: `ModelCatalog.kt` 작성**

```kotlin
package com.kore2.shortcutime.llm

import com.kore2.shortcutime.llm.ProviderId.*

object ModelCatalog {
    private val entries: Map<ProviderId, List<ModelInfo>> = mapOf(
        CLAUDE to listOf(
            ModelInfo("claude-haiku-4-5-20251001", "Claude Haiku 4.5", true),
            ModelInfo("claude-sonnet-4-6", "Claude Sonnet 4.6", false),
            ModelInfo("claude-opus-4-7", "Claude Opus 4.7", false),
        ),
        OPENAI to listOf(
            ModelInfo("gpt-4o-mini", "GPT-4o Mini", true),
            ModelInfo("gpt-4o", "GPT-4o", false),
            ModelInfo("gpt-4.1", "GPT-4.1", false),
        ),
        GEMINI to listOf(
            ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", true),
            ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro", false),
            ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", false),
        ),
        GROK to listOf(
            ModelInfo("grok-2-mini", "Grok 2 Mini", true),
            ModelInfo("grok-2", "Grok 2", false),
            ModelInfo("grok-3", "Grok 3", false),
        ),
        DEEPSEEK to listOf(
            ModelInfo("deepseek-chat", "DeepSeek V3", true),
            ModelInfo("deepseek-reasoner", "DeepSeek R1", false),
        ),
    )

    fun modelsFor(provider: ProviderId): List<ModelInfo> =
        entries.getValue(provider)

    fun recommendedModelId(provider: ProviderId): String =
        modelsFor(provider).first { it.isRecommended }.id
}
```

**Step 6: 테스트 실행 → 통과**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.ModelCatalogTest"`
Expected: 3 tests passed.

**Step 7: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/llm/ProviderId.kt \
        app/src/main/java/com/kore2/shortcutime/llm/ModelInfo.kt \
        app/src/main/java/com/kore2/shortcutime/llm/ModelCatalog.kt \
        app/src/test/java/com/kore2/shortcutime/llm/ModelCatalogTest.kt
git commit -m "feat(phase2): add ProviderId enum and ModelCatalog for 5 LLM providers"
```

---

## Task 3: Clock 인터페이스 (테스트용 시계 주입)

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/data/Clock.kt`

**Step 1: `Clock.kt` 작성**

```kotlin
package com.kore2.shortcutime.data

import java.time.LocalDate
import java.time.ZoneId

interface Clock {
    fun today(): String  // yyyy-MM-dd ISO
    fun nowMillis(): Long
}

class SystemClock(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : Clock {
    override fun today(): String = LocalDate.now(zoneId).toString()
    override fun nowMillis(): Long = System.currentTimeMillis()
}
```

**Step 2: 컴파일 확인**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

**Step 3: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/data/Clock.kt
git commit -m "feat(phase2): add Clock interface for testable time source"
```

---

## Task 4: SecureKeyStore (EncryptedSharedPreferences)

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/data/SecureKeyStore.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/data/SecureKeyStoreTest.kt`

**Step 1: 실패 테스트 작성**

`app/src/test/java/com/kore2/shortcutime/data/SecureKeyStoreTest.kt`:

```kotlin
package com.kore2.shortcutime.data

import androidx.test.core.app.ApplicationProvider
import com.kore2.shortcutime.llm.ProviderId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecureKeyStoreTest {
    private lateinit var store: SecureKeyStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = SecureKeyStore(context)
        ProviderId.values().forEach { store.clear(it) }
    }

    @After
    fun teardown() {
        ProviderId.values().forEach { store.clear(it) }
    }

    @Test
    fun `save and get round trip`() {
        store.save(ProviderId.OPENAI, "sk-test-key-123")
        assertEquals("sk-test-key-123", store.get(ProviderId.OPENAI))
    }

    @Test
    fun `get returns null for unsaved provider`() {
        assertNull(store.get(ProviderId.CLAUDE))
    }

    @Test
    fun `clear removes saved key`() {
        store.save(ProviderId.GEMINI, "gm-key")
        store.clear(ProviderId.GEMINI)
        assertNull(store.get(ProviderId.GEMINI))
    }

    @Test
    fun `different providers isolated`() {
        store.save(ProviderId.OPENAI, "a")
        store.save(ProviderId.CLAUDE, "b")
        assertEquals("a", store.get(ProviderId.OPENAI))
        assertEquals("b", store.get(ProviderId.CLAUDE))
    }

    @Test
    fun `getAllSaved returns only providers with keys`() {
        store.save(ProviderId.OPENAI, "x")
        store.save(ProviderId.GROK, "y")
        val saved = store.getAllSaved()
        assertEquals(setOf(ProviderId.OPENAI, ProviderId.GROK), saved)
    }

    @Test
    fun `empty string saves and clears like null`() {
        store.save(ProviderId.DEEPSEEK, "   ")
        assertNull(store.get(ProviderId.DEEPSEEK))
        assertTrue(ProviderId.DEEPSEEK !in store.getAllSaved())
    }
}
```

**Step 2: 테스트 실행 → 실패**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.data.SecureKeyStoreTest"`
Expected: 컴파일 에러 `SecureKeyStore` 미정의.

**Step 3: `SecureKeyStore.kt` 구현**

```kotlin
package com.kore2.shortcutime.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kore2.shortcutime.llm.ProviderId

class SecureKeyStore(context: Context) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(provider: ProviderId, key: String) {
        val trimmed = key.trim()
        val editor = prefs.edit()
        if (trimmed.isEmpty()) editor.remove(provider.name) else editor.putString(provider.name, trimmed)
        editor.apply()
    }

    fun get(provider: ProviderId): String? = prefs.getString(provider.name, null)

    fun clear(provider: ProviderId) {
        prefs.edit().remove(provider.name).apply()
    }

    fun getAllSaved(): Set<ProviderId> =
        ProviderId.values().filter { !get(it).isNullOrBlank() }.toSet()

    companion object {
        private const val FILE_NAME = "api_keys_encrypted"
    }
}
```

**Step 4: 테스트 실행 → 통과**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.data.SecureKeyStoreTest"`
Expected: 6 tests passed.

> ⚠️ Robolectric 이 `androidx.security.crypto` 를 처음 로드할 때 시간이 걸릴 수 있음. 빌드 로그에 Keystore 초기화 관련 메시지가 정상.

**Step 5: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/data/SecureKeyStore.kt \
        app/src/test/java/com/kore2/shortcutime/data/SecureKeyStoreTest.kt
git commit -m "feat(phase2): add SecureKeyStore with EncryptedSharedPreferences"
```

---

## Task 5: LlmSettings + LlmSettingsStore

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/data/LlmSettings.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/data/LlmSettingsStore.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/data/LlmSettingsStoreTest.kt`

**Step 1: 실패 테스트 작성**

`app/src/test/java/com/kore2/shortcutime/data/LlmSettingsStoreTest.kt`:

```kotlin
package com.kore2.shortcutime.data

import androidx.test.core.app.ApplicationProvider
import com.kore2.shortcutime.llm.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LlmSettingsStoreTest {
    private lateinit var clock: FakeClock
    private lateinit var store: LlmSettingsStore

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("llm_settings", 0).edit().clear().apply()
        clock = FakeClock(today = "2026-04-19")
        store = LlmSettingsStore(context, clock)
    }

    @Test
    fun `default settings when nothing saved`() {
        val s = store.load()
        assertNull(s.activeProvider)
        assertEquals(50, s.dailyCallCap)
        assertEquals(0, s.todayCallCount)
        assertEquals("2026-04-19", s.todayResetDate)
    }

    @Test
    fun `setActiveProvider persists`() {
        store.setActiveProvider(ProviderId.CLAUDE)
        assertEquals(ProviderId.CLAUDE, store.load().activeProvider)
        store.setActiveProvider(null)
        assertNull(store.load().activeProvider)
    }

    @Test
    fun `setModel stores per-provider mapping`() {
        store.setModel(ProviderId.OPENAI, "gpt-4o")
        store.setModel(ProviderId.GEMINI, "gemini-2.5-pro")
        val s = store.load()
        assertEquals("gpt-4o", s.modelByProvider[ProviderId.OPENAI])
        assertEquals("gemini-2.5-pro", s.modelByProvider[ProviderId.GEMINI])
    }

    @Test
    fun `setDailyCap clamps to 10-500 range`() {
        store.setDailyCap(5)
        assertEquals(10, store.load().dailyCallCap)
        store.setDailyCap(1000)
        assertEquals(500, store.load().dailyCallCap)
        store.setDailyCap(75)
        assertEquals(75, store.load().dailyCallCap)
    }

    @Test
    fun `incrementCallCount on same day adds 1`() {
        store.incrementCallCount()
        store.incrementCallCount()
        assertEquals(2, store.load().todayCallCount)
    }

    @Test
    fun `load resets counter when date changed`() {
        store.incrementCallCount()
        store.incrementCallCount()
        assertEquals(2, store.load().todayCallCount)

        clock.today = "2026-04-20"
        val reloaded = store.load()
        assertEquals(0, reloaded.todayCallCount)
        assertEquals("2026-04-20", reloaded.todayResetDate)
    }
}

class FakeClock(var today: String, var millis: Long = 0) : Clock {
    override fun today(): String = today
    override fun nowMillis(): Long = millis
}
```

**Step 2: 테스트 실행 → 실패**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.data.LlmSettingsStoreTest"`
Expected: 컴파일 에러.

**Step 3: `LlmSettings.kt` 작성**

```kotlin
package com.kore2.shortcutime.data

import com.kore2.shortcutime.llm.ProviderId

data class LlmSettings(
    val activeProvider: ProviderId?,
    val modelByProvider: Map<ProviderId, String>,
    val dailyCallCap: Int,
    val todayCallCount: Int,
    val todayResetDate: String,
)
```

**Step 4: `LlmSettingsStore.kt` 작성**

```kotlin
package com.kore2.shortcutime.data

import android.content.Context
import android.content.SharedPreferences
import com.kore2.shortcutime.llm.ProviderId

class LlmSettingsStore(
    context: Context,
    private val clock: Clock,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): LlmSettings {
        val today = clock.today()
        val savedDate = prefs.getString(KEY_RESET_DATE, null)
        if (savedDate != today) {
            prefs.edit()
                .putString(KEY_RESET_DATE, today)
                .putInt(KEY_CALL_COUNT, 0)
                .apply()
        }
        val active = prefs.getString(KEY_ACTIVE_PROVIDER, null)?.let { runCatching { ProviderId.valueOf(it) }.getOrNull() }
        val models = ProviderId.values().mapNotNull { p ->
            prefs.getString(modelKey(p), null)?.let { p to it }
        }.toMap()
        return LlmSettings(
            activeProvider = active,
            modelByProvider = models,
            dailyCallCap = prefs.getInt(KEY_DAILY_CAP, DEFAULT_CAP),
            todayCallCount = prefs.getInt(KEY_CALL_COUNT, 0),
            todayResetDate = today,
        )
    }

    fun setActiveProvider(provider: ProviderId?) {
        prefs.edit().apply {
            if (provider == null) remove(KEY_ACTIVE_PROVIDER) else putString(KEY_ACTIVE_PROVIDER, provider.name)
        }.apply()
    }

    fun setModel(provider: ProviderId, modelId: String) {
        prefs.edit().putString(modelKey(provider), modelId).apply()
    }

    fun setDailyCap(cap: Int) {
        val clamped = cap.coerceIn(MIN_CAP, MAX_CAP)
        prefs.edit().putInt(KEY_DAILY_CAP, clamped).apply()
    }

    fun incrementCallCount() {
        load()  // 날짜 경계 리셋을 먼저 적용
        val current = prefs.getInt(KEY_CALL_COUNT, 0)
        prefs.edit().putInt(KEY_CALL_COUNT, current + 1).apply()
    }

    private fun modelKey(provider: ProviderId) = "model_${provider.name}"

    companion object {
        private const val FILE_NAME = "llm_settings"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_DAILY_CAP = "daily_cap"
        private const val KEY_CALL_COUNT = "call_count"
        private const val KEY_RESET_DATE = "reset_date"
        const val DEFAULT_CAP = 50
        const val MIN_CAP = 10
        const val MAX_CAP = 500
    }
}
```

**Step 5: 테스트 실행 → 통과**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.data.LlmSettingsStoreTest"`
Expected: 6 tests passed.

**Step 6: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/data/LlmSettings.kt \
        app/src/main/java/com/kore2/shortcutime/data/LlmSettingsStore.kt \
        app/src/test/java/com/kore2/shortcutime/data/LlmSettingsStoreTest.kt
git commit -m "feat(phase2): add LlmSettingsStore with daily cap and date boundary reset"
```

---

## Task 6: LLM 에러 타입 + 어댑터 인터페이스 + GenerationResult

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/llm/LlmError.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/llm/GenerationResult.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/llm/LlmAdapter.kt`

**Step 1: `LlmError.kt` 작성**

```kotlin
package com.kore2.shortcutime.llm

sealed class LlmError {
    object Network : LlmError()
    object Timeout : LlmError()
    object InvalidKey : LlmError()
    object RateLimited : LlmError()
    object ServerError : LlmError()
    object Truncated : LlmError()
    object ParseFailure : LlmError()
    object ContentFiltered : LlmError()
    data class Unknown(val message: String) : LlmError()
}

class LlmException(val error: LlmError) : Exception(error.toString())
```

**Step 2: `GenerationResult.kt` 작성**

```kotlin
package com.kore2.shortcutime.llm

data class GenerationResult(
    val examples: List<String>,
    val requestedCount: Int,
)
```

**Step 3: `LlmAdapter.kt` 작성**

```kotlin
package com.kore2.shortcutime.llm

interface LlmAdapter {
    val providerId: ProviderId
    suspend fun validateKey(apiKey: String): Result<Unit>
    suspend fun generateExamples(
        apiKey: String,
        model: String,
        shortcut: String,
        expansion: String,
        count: Int,
    ): Result<GenerationResult>
}
```

**Step 4: 컴파일 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

**Step 5: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/llm/LlmError.kt \
        app/src/main/java/com/kore2/shortcutime/llm/GenerationResult.kt \
        app/src/main/java/com/kore2/shortcutime/llm/LlmAdapter.kt
git commit -m "feat(phase2): add LlmError sealed class and LlmAdapter interface"
```

---

## Task 7: PromptBuilder + LanguageClassifier

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/llm/PromptBuilder.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/llm/LanguageClassifier.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/llm/PromptBuilderTest.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/llm/LanguageClassifierTest.kt`

**Step 1: 실패 테스트 작성 — PromptBuilder**

`app/src/test/java/com/kore2/shortcutime/llm/PromptBuilderTest.kt`:

```kotlin
package com.kore2.shortcutime.llm

import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    @Test
    fun `prompt contains shortcut expansion count and JSON instruction`() {
        val prompt = PromptBuilder.build(shortcut = "btw", expansion = "by the way", count = 3)
        assertTrue(prompt.contains("\"btw\""))
        assertTrue(prompt.contains("\"by the way\""))
        assertTrue(prompt.contains("3 natural example sentences"))
        assertTrue(prompt.contains("{\"examples\":"))
        assertTrue(prompt.contains("THE SAME LANGUAGE"))
    }

    @Test
    fun `prompt escapes quotes in shortcut`() {
        val prompt = PromptBuilder.build("say \"hi\"", "hello", 1)
        assertTrue(prompt.contains("\\\"hi\\\""))
    }
}
```

**Step 2: 실패 테스트 작성 — LanguageClassifier**

`app/src/test/java/com/kore2/shortcutime/llm/LanguageClassifierTest.kt`:

```kotlin
package com.kore2.shortcutime.llm

import com.kore2.shortcutime.llm.LanguageClassifier.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageClassifierTest {
    @Test
    fun `pure English is classified ENGLISH`() {
        assertEquals(Language.ENGLISH, LanguageClassifier.classify("by the way"))
    }

    @Test
    fun `pure Korean is classified KOREAN`() {
        assertEquals(Language.KOREAN, LanguageClassifier.classify("그런데 말이야"))
    }

    @Test
    fun `mixed with any hangul is classified KOREAN`() {
        assertEquals(Language.KOREAN, LanguageClassifier.classify("BTW 그건 그렇고"))
    }

    @Test
    fun `jamo (초성) is classified KOREAN`() {
        assertEquals(Language.KOREAN, LanguageClassifier.classify("ㅋㅋㅋ"))
    }

    @Test
    fun `empty string defaults to ENGLISH`() {
        assertEquals(Language.ENGLISH, LanguageClassifier.classify(""))
    }
}
```

**Step 3: 두 테스트 실행 → 실패**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.PromptBuilderTest" --tests "com.kore2.shortcutime.llm.LanguageClassifierTest"`
Expected: 컴파일 에러.

**Step 4: `PromptBuilder.kt` 구현**

```kotlin
package com.kore2.shortcutime.llm

object PromptBuilder {
    fun build(shortcut: String, expansion: String, count: Int): String {
        val s = shortcut.replace("\\", "\\\\").replace("\"", "\\\"")
        val e = expansion.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            You generate example sentences demonstrating a text-expansion shortcut.

            Shortcut: "$s"
            Expands to: "$e"

            Write $count natural example sentences that naturally use the shortcut's expansion in realistic contexts.
            Detect the language of the expansion automatically and write examples in THE SAME LANGUAGE.

            Return ONLY a JSON object of this exact shape with no prose, no markdown:
            {"examples": ["sentence 1", "sentence 2", ...]}
        """.trimIndent()
    }
}
```

**Step 5: `LanguageClassifier.kt` 구현**

```kotlin
package com.kore2.shortcutime.llm

object LanguageClassifier {
    enum class Language { KOREAN, ENGLISH }

    fun classify(text: String): Language {
        val hasHangul = text.any { c ->
            c in '\uAC00'..'\uD7A3' ||  // Hangul Syllables
            c in '\u1100'..'\u11FF' ||  // Hangul Jamo
            c in '\u3130'..'\u318F'     // Hangul Compatibility Jamo
        }
        return if (hasHangul) Language.KOREAN else Language.ENGLISH
    }
}
```

**Step 6: 테스트 실행 → 통과**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.PromptBuilderTest" --tests "com.kore2.shortcutime.llm.LanguageClassifierTest"`
Expected: 7 tests passed.

**Step 7: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/llm/PromptBuilder.kt \
        app/src/main/java/com/kore2/shortcutime/llm/LanguageClassifier.kt \
        app/src/test/java/com/kore2/shortcutime/llm/PromptBuilderTest.kt \
        app/src/test/java/com/kore2/shortcutime/llm/LanguageClassifierTest.kt
git commit -m "feat(phase2): add PromptBuilder and LanguageClassifier utilities"
```

---

## Task 8: ParseExamples 공용 함수

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/llm/ParseExamples.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/llm/ParseExamplesTest.kt`

**Step 1: 실패 테스트 작성**

`app/src/test/java/com/kore2/shortcutime/llm/ParseExamplesTest.kt`:

```kotlin
package com.kore2.shortcutime.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseExamplesTest {
    @Test
    fun `clean JSON object with examples array`() {
        val result = ParseExamples.parse("""{"examples":["one","two","three"]}""", 3)
        assertTrue(result.isSuccess)
        assertEquals(listOf("one", "two", "three"), result.getOrThrow().examples)
        assertEquals(3, result.getOrThrow().requestedCount)
    }

    @Test
    fun `JSON wrapped in markdown code fences`() {
        val raw = "```json\n{\"examples\":[\"a\",\"b\"]}\n```"
        val result = ParseExamples.parse(raw, 2)
        assertTrue(result.isSuccess)
        assertEquals(listOf("a", "b"), result.getOrThrow().examples)
    }

    @Test
    fun `fewer examples than requested is still success`() {
        val result = ParseExamples.parse("""{"examples":["x"]}""", 3)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().examples.size)
        assertEquals(3, result.getOrThrow().requestedCount)
    }

    @Test
    fun `null and blank entries filtered out`() {
        val result = ParseExamples.parse("""{"examples":["one","","  ","two"]}""", 4)
        assertTrue(result.isSuccess)
        assertEquals(listOf("one", "two"), result.getOrThrow().examples)
    }

    @Test
    fun `empty examples array returns success with zero items`() {
        val result = ParseExamples.parse("""{"examples":[]}""", 3)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().examples.size)
    }

    @Test
    fun `malformed JSON falls back to regex extraction`() {
        val raw = """sure! here are some: "first example" "second example" "third example""""
        val result = ParseExamples.parse(raw, 3)
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow().examples.size)
    }

    @Test
    fun `completely unusable text returns ParseFailure`() {
        val result = ParseExamples.parse("totally unparseable no quotes either", 3)
        assertTrue(result.isFailure)
        val err = (result.exceptionOrNull() as LlmException).error
        assertTrue(err is LlmError.ParseFailure)
    }
}
```

**Step 2: 테스트 실행 → 실패**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.ParseExamplesTest"`
Expected: 컴파일 에러.

**Step 3: `ParseExamples.kt` 구현**

```kotlin
package com.kore2.shortcutime.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object ParseExamples {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val fencePattern = Regex("^```(?:json)?\\s*|\\s*```$", RegexOption.MULTILINE)
    private val quotedStringPattern = Regex("\"([^\"\\\\]+)\"")

    @Serializable
    private data class Payload(val examples: List<String> = emptyList())

    fun parse(rawText: String, requestedCount: Int): Result<GenerationResult> {
        val stripped = rawText.trim().replace(fencePattern, "").trim()

        // 1차: JSON 파싱
        val jsonAttempt = runCatching {
            json.decodeFromString(Payload.serializer(), stripped)
        }
        if (jsonAttempt.isSuccess) {
            val examples = jsonAttempt.getOrThrow().examples
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            return Result.success(GenerationResult(examples, requestedCount))
        }

        // 2차: 정규식으로 따옴표 문자열 추출
        val regexMatches = quotedStringPattern.findAll(stripped)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() && it.length > 2 }
            .toList()
        if (regexMatches.isNotEmpty()) {
            return Result.success(GenerationResult(regexMatches, requestedCount))
        }

        return Result.failure(LlmException(LlmError.ParseFailure))
    }
}
```

**Step 4: 테스트 실행 → 통과**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.ParseExamplesTest"`
Expected: 7 tests passed.

**Step 5: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/llm/ParseExamples.kt \
        app/src/test/java/com/kore2/shortcutime/llm/ParseExamplesTest.kt
git commit -m "feat(phase2): add ParseExamples with JSON-primary and regex-fallback"
```

---

## Task 9: HttpClientFactory

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/llm/HttpClientFactory.kt`

**Step 1: `HttpClientFactory.kt` 작성**

```kotlin
package com.kore2.shortcutime.llm

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object HttpClientFactory {
    fun create(debug: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
        if (debug) {
            builder.addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC  // 본문 로깅 금지 (키 유출 방지)
            })
        }
        return builder.build()
    }
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

**Step 3: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/llm/HttpClientFactory.kt
git commit -m "feat(phase2): add HttpClientFactory with safe timeout defaults"
```

---

## Task 10: OpenAiCompatibleAdapter (OpenAI + Grok + DeepSeek 3곳 커버)

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/llm/OpenAiCompatibleAdapter.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/llm/OpenAiCompatibleAdapterTest.kt`

**Step 1: 실패 테스트 작성**

`app/src/test/java/com/kore2/shortcutime/llm/OpenAiCompatibleAdapterTest.kt`:

```kotlin
package com.kore2.shortcutime.llm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: OpenAiCompatibleAdapter

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        adapter = OpenAiCompatibleAdapter(
            baseUrl = server.url("/").toString().trimEnd('/'),
            providerId = ProviderId.OPENAI,
            httpClient = HttpClientFactory.create(debug = false),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `validateKey 200 success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))
        assertTrue(adapter.validateKey("sk-ok").isSuccess)
    }

    @Test
    fun `validateKey 401 maps to InvalidKey`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad"}}"""))
        val r = adapter.validateKey("sk-bad")
        assertTrue(r.isFailure)
        val err = (r.exceptionOrNull() as LlmException).error
        assertTrue(err is LlmError.InvalidKey)
    }

    @Test
    fun `generateExamples 200 success`() = runBlocking {
        val body = """
            {"choices":[{"message":{"content":"{\"examples\":[\"a\",\"b\",\"c\"]}"},"finish_reason":"stop"}]}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "btw", "by the way", 3)
        assertTrue(r.isSuccess)
        assertEquals(listOf("a", "b", "c"), r.getOrThrow().examples)
    }

    @Test
    fun `generateExamples 429 maps to RateLimited`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"type":"rate_limit_exceeded"}}"""))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 1)
        assertTrue(r.isFailure)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.RateLimited)
    }

    @Test
    fun `generateExamples 500 maps to ServerError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ServerError)
    }

    @Test
    fun `finish_reason length maps to Truncated`() = runBlocking {
        val body = """{"choices":[{"message":{"content":"{\"examples\":[\"a\"]}"},"finish_reason":"length"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 3)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.Truncated)
    }

    @Test
    fun `finish_reason content_filter maps to ContentFiltered`() = runBlocking {
        val body = """{"choices":[{"message":{"content":"{\"examples\":[]}"},"finish_reason":"content_filter"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ContentFiltered)
    }

    @Test
    fun `error body invalid_api_key maps to InvalidKey even on 400`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"type":"invalid_api_key","message":"x"}}"""))
        val r = adapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.InvalidKey)
    }

    @Test
    fun `timeout maps to Timeout`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val fastClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val fastAdapter = OpenAiCompatibleAdapter(
            baseUrl = server.url("/").toString().trimEnd('/'),
            providerId = ProviderId.OPENAI,
            httpClient = fastClient,
        )
        val r = fastAdapter.generateExamples("sk-ok", "gpt-4o-mini", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.Timeout)
    }
}
```

**Step 2: 테스트 실행 → 실패**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.OpenAiCompatibleAdapterTest"`
Expected: 컴파일 에러.

**Step 3: `OpenAiCompatibleAdapter.kt` 구현**

```kotlin
package com.kore2.shortcutime.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

class OpenAiCompatibleAdapter(
    private val baseUrl: String,
    override val providerId: ProviderId,
    private val httpClient: OkHttpClient,
) : LlmAdapter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun validateKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/models")
            .get()
            .header("Authorization", "Bearer $apiKey")
            .build()
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw LlmException(mapHttpError(resp.code, resp.body?.string().orEmpty()))
                }
            }
        }.mapExceptionToLlm()
    }

    override suspend fun generateExamples(
        apiKey: String,
        model: String,
        shortcut: String,
        expansion: String,
        count: Int,
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        val prompt = PromptBuilder.build(shortcut, expansion, count)
        val payload = """
            {"model":"$model","max_tokens":512,
             "response_format":{"type":"json_object"},
             "messages":[{"role":"user","content":${json.encodeToString(kotlinx.serialization.builtins.serializer(), prompt)}}]}
        """.trimIndent()

        val req = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .post(payload.toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $apiKey")
            .build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmException(mapHttpError(resp.code, body))
                val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), body)
                val first = parsed.choices.firstOrNull()
                    ?: throw LlmException(LlmError.ParseFailure)
                when (first.finish_reason) {
                    "length" -> throw LlmException(LlmError.Truncated)
                    "content_filter" -> throw LlmException(LlmError.ContentFiltered)
                }
                val content = first.message.content
                val result = ParseExamples.parse(content, count)
                result.getOrThrow()
            }
        }.mapExceptionToLlm()
    }

    private fun mapHttpError(code: Int, body: String): LlmError {
        // body 의 error.type 먼저 확인
        if (body.contains("\"invalid_api_key\"") || body.contains("\"insufficient_quota\"")) {
            return LlmError.InvalidKey
        }
        if (body.contains("\"rate_limit_exceeded\"")) return LlmError.RateLimited
        return when (code) {
            401, 403 -> LlmError.InvalidKey
            408, 504 -> LlmError.Timeout
            429 -> LlmError.RateLimited
            500, 502, 503 -> LlmError.ServerError
            404 -> LlmError.Unknown("endpoint not found")
            else -> LlmError.Unknown("http $code: ${body.take(200)}")
        }
    }

    private fun <T> Result<T>.mapExceptionToLlm(): Result<T> = recoverCatching { t ->
        throw when (t) {
            is LlmException -> t
            is SocketTimeoutException -> LlmException(LlmError.Timeout)
            is IOException -> LlmException(LlmError.Network)
            else -> LlmException(LlmError.Unknown(t.message ?: t::class.java.simpleName))
        }
    }

    @Serializable
    private data class ChatCompletionResponse(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(
        val message: Message,
        val finish_reason: String? = null,
    )

    @Serializable
    private data class Message(val content: String = "")

    companion object {
        const val OPENAI_BASE_URL = "https://api.openai.com"
        const val GROK_BASE_URL = "https://api.x.ai"
        const val DEEPSEEK_BASE_URL = "https://api.deepseek.com"
    }
}
```

**Step 4: 테스트 실행 → 통과**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.OpenAiCompatibleAdapterTest"`
Expected: 9 tests passed.

**Step 5: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/llm/OpenAiCompatibleAdapter.kt \
        app/src/test/java/com/kore2/shortcutime/llm/OpenAiCompatibleAdapterTest.kt
git commit -m "feat(phase2): add OpenAiCompatibleAdapter for OpenAI/Grok/DeepSeek"
```

---

## Task 11: ClaudeAdapter

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/llm/ClaudeAdapter.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/llm/ClaudeAdapterTest.kt`

**Step 1: 실패 테스트 작성**

`app/src/test/java/com/kore2/shortcutime/llm/ClaudeAdapterTest.kt`:

```kotlin
package com.kore2.shortcutime.llm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClaudeAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: ClaudeAdapter

    @Before fun setup() {
        server = MockWebServer(); server.start()
        adapter = ClaudeAdapter(
            baseUrl = server.url("/").toString().trimEnd('/'),
            httpClient = HttpClientFactory.create(),
        )
    }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `validateKey 200 success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"x","stop_reason":"end_turn","content":[{"type":"text","text":"ok"}]}"""))
        assertTrue(adapter.validateKey("anthropic-key").isSuccess)
    }

    @Test
    fun `validateKey 401 maps to InvalidKey`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"error","error":{"type":"authentication_error"}}"""))
        val r = adapter.validateKey("bad")
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.InvalidKey)
    }

    @Test
    fun `generateExamples 200 success`() = runBlocking {
        val content = """{\"examples\":[\"a\",\"b\"]}"""
        val body = """{"content":[{"type":"text","text":"$content"}],"stop_reason":"end_turn"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "claude-haiku-4-5-20251001", "s", "e", 2)
        assertTrue(r.isSuccess)
        assertEquals(listOf("a", "b"), r.getOrThrow().examples)
    }

    @Test
    fun `stop_reason max_tokens maps to Truncated`() = runBlocking {
        val body = """{"content":[{"type":"text","text":"{\"examples\":[\"a\"]}"}],"stop_reason":"max_tokens"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "m", "s", "e", 3)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.Truncated)
    }

    @Test
    fun `stop_reason refusal maps to ContentFiltered`() = runBlocking {
        val body = """{"content":[{"type":"text","text":"{\"examples\":[]}"}],"stop_reason":"refusal"}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "m", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ContentFiltered)
    }

    @Test
    fun `overloaded_error maps to ServerError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(529).setBody("""{"type":"error","error":{"type":"overloaded_error"}}"""))
        val r = adapter.generateExamples("k", "m", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ServerError)
    }

    @Test
    fun `rate_limit_error maps to RateLimited`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"type":"error","error":{"type":"rate_limit_error"}}"""))
        val r = adapter.generateExamples("k", "m", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.RateLimited)
    }
}
```

**Step 2: 테스트 실행 → 실패**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.ClaudeAdapterTest"`
Expected: 컴파일 에러.

**Step 3: `ClaudeAdapter.kt` 구현**

```kotlin
package com.kore2.shortcutime.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException

class ClaudeAdapter(
    private val baseUrl: String = PRODUCTION_BASE_URL,
    private val httpClient: OkHttpClient = HttpClientFactory.create(),
) : LlmAdapter {

    override val providerId: ProviderId = ProviderId.CLAUDE
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun validateKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val body = """{"model":"claude-haiku-4-5-20251001","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
        val req = requestBuilder(apiKey).url("$baseUrl/v1/messages").post(body.toRequestBody(jsonMedia)).build()
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw LlmException(mapHttpError(resp.code, resp.body?.string().orEmpty()))
            }
        }.mapExceptionToLlm()
    }

    override suspend fun generateExamples(
        apiKey: String,
        model: String,
        shortcut: String,
        expansion: String,
        count: Int,
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        val prompt = PromptBuilder.build(shortcut, expansion, count)
        val body = """
            {"model":"$model","max_tokens":512,
             "messages":[{"role":"user","content":${json.encodeToString(String.serializer(), prompt)}}]}
        """.trimIndent()
        val req = requestBuilder(apiKey).url("$baseUrl/v1/messages").post(body.toRequestBody(jsonMedia)).build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmException(mapHttpError(resp.code, raw))
                val parsed = json.decodeFromString(MessageResponse.serializer(), raw)
                when (parsed.stop_reason) {
                    "max_tokens" -> throw LlmException(LlmError.Truncated)
                    "refusal" -> throw LlmException(LlmError.ContentFiltered)
                }
                val text = parsed.content.firstOrNull { it.type == "text" }?.text
                    ?: throw LlmException(LlmError.ParseFailure)
                ParseExamples.parse(text, count).getOrThrow()
            }
        }.mapExceptionToLlm()
    }

    private fun requestBuilder(apiKey: String): Request.Builder =
        Request.Builder()
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")

    private fun mapHttpError(code: Int, body: String): LlmError {
        if (body.contains("\"authentication_error\"")) return LlmError.InvalidKey
        if (body.contains("\"rate_limit_error\"")) return LlmError.RateLimited
        if (body.contains("\"overloaded_error\"")) return LlmError.ServerError
        return when (code) {
            401, 403 -> LlmError.InvalidKey
            408, 504 -> LlmError.Timeout
            429 -> LlmError.RateLimited
            500, 502, 503, 529 -> LlmError.ServerError
            404 -> LlmError.Unknown("endpoint not found")
            else -> LlmError.Unknown("http $code: ${body.take(200)}")
        }
    }

    private fun <T> Result<T>.mapExceptionToLlm(): Result<T> = recoverCatching { t ->
        throw when (t) {
            is LlmException -> t
            is SocketTimeoutException -> LlmException(LlmError.Timeout)
            is IOException -> LlmException(LlmError.Network)
            else -> LlmException(LlmError.Unknown(t.message ?: t::class.java.simpleName))
        }
    }

    @Serializable
    private data class MessageResponse(
        val content: List<ContentBlock> = emptyList(),
        val stop_reason: String? = null,
    )

    @Serializable
    private data class ContentBlock(val type: String = "", val text: String = "")

    companion object {
        const val PRODUCTION_BASE_URL = "https://api.anthropic.com"
    }
}
```

**Step 4: 테스트 실행 → 통과**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.ClaudeAdapterTest"`
Expected: 7 tests passed.

**Step 5: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/llm/ClaudeAdapter.kt \
        app/src/test/java/com/kore2/shortcutime/llm/ClaudeAdapterTest.kt
git commit -m "feat(phase2): add ClaudeAdapter with Anthropic Messages API"
```

---

## Task 12: GeminiAdapter

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/llm/GeminiAdapter.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/llm/GeminiAdapterTest.kt`

**Step 1: 실패 테스트 작성**

`app/src/test/java/com/kore2/shortcutime/llm/GeminiAdapterTest.kt`:

```kotlin
package com.kore2.shortcutime.llm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: GeminiAdapter

    @Before fun setup() {
        server = MockWebServer(); server.start()
        adapter = GeminiAdapter(
            baseUrl = server.url("/").toString().trimEnd('/'),
            httpClient = HttpClientFactory.create(),
        )
    }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `validateKey 200 success`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"models":[]}"""))
        assertTrue(adapter.validateKey("g-key").isSuccess)
    }

    @Test
    fun `validateKey 400 INVALID_ARGUMENT maps to InvalidKey`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"status":"INVALID_ARGUMENT","message":"API key not valid"}}"""))
        val r = adapter.validateKey("bad")
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.InvalidKey)
    }

    @Test
    fun `generateExamples 200 success`() = runBlocking {
        val inner = """{\"examples\":[\"x\",\"y\"]}"""
        val body = """{"candidates":[{"content":{"parts":[{"text":"$inner"}]},"finishReason":"STOP"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "gemini-2.5-flash", "s", "e", 2)
        assertTrue(r.isSuccess)
        assertEquals(listOf("x", "y"), r.getOrThrow().examples)
    }

    @Test
    fun `finishReason SAFETY maps to ContentFiltered`() = runBlocking {
        val body = """{"candidates":[{"content":{"parts":[{"text":"{\"examples\":[]}"}]},"finishReason":"SAFETY"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "m", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ContentFiltered)
    }

    @Test
    fun `finishReason MAX_TOKENS maps to Truncated`() = runBlocking {
        val body = """{"candidates":[{"content":{"parts":[{"text":"{\"examples\":[\"a\"]}"}]},"finishReason":"MAX_TOKENS"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "m", "s", "e", 3)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.Truncated)
    }

    @Test
    fun `empty candidates maps to ContentFiltered`() = runBlocking {
        val body = """{"candidates":[]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
        val r = adapter.generateExamples("k", "m", "s", "e", 1)
        assertTrue((r.exceptionOrNull() as LlmException).error is LlmError.ContentFiltered)
    }
}
```

**Step 2: 테스트 실행 → 실패**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.GeminiAdapterTest"`
Expected: 컴파일 에러.

**Step 3: `GeminiAdapter.kt` 구현**

```kotlin
package com.kore2.shortcutime.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder

class GeminiAdapter(
    private val baseUrl: String = PRODUCTION_BASE_URL,
    private val httpClient: OkHttpClient = HttpClientFactory.create(),
) : LlmAdapter {

    override val providerId: ProviderId = ProviderId.GEMINI
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun validateKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        val key = URLEncoder.encode(apiKey, "UTF-8")
        val req = Request.Builder().url("$baseUrl/v1beta/models?key=$key").get().build()
        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw LlmException(mapHttpError(resp.code, resp.body?.string().orEmpty()))
            }
        }.mapExceptionToLlm()
    }

    override suspend fun generateExamples(
        apiKey: String,
        model: String,
        shortcut: String,
        expansion: String,
        count: Int,
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        val prompt = PromptBuilder.build(shortcut, expansion, count)
        val body = """
            {"contents":[{"parts":[{"text":${json.encodeToString(String.serializer(), prompt)}}]}],
             "generationConfig":{"responseMimeType":"application/json","maxOutputTokens":512}}
        """.trimIndent()
        val key = URLEncoder.encode(apiKey, "UTF-8")
        val req = Request.Builder()
            .url("$baseUrl/v1beta/models/$model:generateContent?key=$key")
            .post(body.toRequestBody(jsonMedia))
            .build()

        runCatching {
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw LlmException(mapHttpError(resp.code, raw))
                val parsed = json.decodeFromString(GenerateContentResponse.serializer(), raw)
                val candidate = parsed.candidates.firstOrNull()
                    ?: throw LlmException(LlmError.ContentFiltered)
                when (candidate.finishReason) {
                    "MAX_TOKENS" -> throw LlmException(LlmError.Truncated)
                    "SAFETY", "RECITATION", "BLOCKLIST" -> throw LlmException(LlmError.ContentFiltered)
                }
                val text = candidate.content?.parts?.firstOrNull()?.text
                    ?: throw LlmException(LlmError.ParseFailure)
                ParseExamples.parse(text, count).getOrThrow()
            }
        }.mapExceptionToLlm()
    }

    private fun mapHttpError(code: Int, body: String): LlmError {
        if (body.contains("\"INVALID_ARGUMENT\"") && body.contains("API key", ignoreCase = true)) {
            return LlmError.InvalidKey
        }
        return when (code) {
            400 -> if (body.contains("\"INVALID_ARGUMENT\"")) LlmError.InvalidKey else LlmError.Unknown("http 400: ${body.take(200)}")
            401, 403 -> LlmError.InvalidKey
            408, 504 -> LlmError.Timeout
            429 -> LlmError.RateLimited
            500, 502, 503 -> LlmError.ServerError
            404 -> LlmError.Unknown("endpoint not found")
            else -> LlmError.Unknown("http $code: ${body.take(200)}")
        }
    }

    private fun <T> Result<T>.mapExceptionToLlm(): Result<T> = recoverCatching { t ->
        throw when (t) {
            is LlmException -> t
            is SocketTimeoutException -> LlmException(LlmError.Timeout)
            is IOException -> LlmException(LlmError.Network)
            else -> LlmException(LlmError.Unknown(t.message ?: t::class.java.simpleName))
        }
    }

    @Serializable
    private data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())

    @Serializable
    private data class Candidate(
        val content: Content? = null,
        val finishReason: String? = null,
    )

    @Serializable
    private data class Content(val parts: List<Part> = emptyList())

    @Serializable
    private data class Part(val text: String = "")

    companion object {
        const val PRODUCTION_BASE_URL = "https://generativelanguage.googleapis.com"
    }
}
```

**Step 4: 테스트 실행 → 통과**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.GeminiAdapterTest"`
Expected: 6 tests passed.

**Step 5: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/llm/GeminiAdapter.kt \
        app/src/test/java/com/kore2/shortcutime/llm/GeminiAdapterTest.kt
git commit -m "feat(phase2): add GeminiAdapter with generateContent API"
```

---

## Task 13: LlmRegistry

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/llm/LlmRegistry.kt`

**Step 1: `LlmRegistry.kt` 작성**

```kotlin
package com.kore2.shortcutime.llm

import okhttp3.OkHttpClient

class LlmRegistry(private val httpClient: OkHttpClient) {
    fun adapterFor(provider: ProviderId): LlmAdapter = when (provider) {
        ProviderId.OPENAI -> OpenAiCompatibleAdapter(OpenAiCompatibleAdapter.OPENAI_BASE_URL, ProviderId.OPENAI, httpClient)
        ProviderId.GROK -> OpenAiCompatibleAdapter(OpenAiCompatibleAdapter.GROK_BASE_URL, ProviderId.GROK, httpClient)
        ProviderId.DEEPSEEK -> OpenAiCompatibleAdapter(OpenAiCompatibleAdapter.DEEPSEEK_BASE_URL, ProviderId.DEEPSEEK, httpClient)
        ProviderId.CLAUDE -> ClaudeAdapter(httpClient = httpClient)
        ProviderId.GEMINI -> GeminiAdapter(httpClient = httpClient)
    }
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

**Step 3: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/llm/LlmRegistry.kt
git commit -m "feat(phase2): add LlmRegistry for provider->adapter mapping"
```

---

## Task 14: ExampleGenerationService

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/llm/ExampleGenerationService.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/llm/ExampleGenerationServiceTest.kt`

**Step 1: 실패 테스트 작성**

`app/src/test/java/com/kore2/shortcutime/llm/ExampleGenerationServiceTest.kt`:

```kotlin
package com.kore2.shortcutime.llm

import androidx.test.core.app.ApplicationProvider
import com.kore2.shortcutime.data.FakeClock
import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.data.SecureKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleGenerationServiceTest {
    private lateinit var keyStore: SecureKeyStore
    private lateinit var settingsStore: LlmSettingsStore
    private lateinit var fakeAdapter: FakeAdapter
    private lateinit var registry: FakeRegistry
    private lateinit var clock: FakeClock
    private lateinit var service: ExampleGenerationService

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("llm_settings", 0).edit().clear().apply()
        clock = FakeClock(today = "2026-04-19")
        keyStore = SecureKeyStore(ctx)
        ProviderId.values().forEach { keyStore.clear(it) }
        settingsStore = LlmSettingsStore(ctx, clock)
        fakeAdapter = FakeAdapter(ProviderId.OPENAI)
        registry = FakeRegistry(fakeAdapter)
        service = ExampleGenerationService(keyStore, settingsStore, registry)
    }

    @Test
    fun `NoActiveProvider when none set`() = runBlocking {
        val o = service.generate("btw", "by the way", 3)
        assertTrue(o is ExampleGenerationService.Outcome.NoActiveProvider)
    }

    @Test
    fun `NoKey when active provider has no key`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        val o = service.generate("btw", "by the way", 3)
        assertTrue(o is ExampleGenerationService.Outcome.NoKey)
    }

    @Test
    fun `Success when adapter returns exact count`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        fakeAdapter.result = Result.success(GenerationResult(listOf("a", "b", "c"), 3))
        val o = service.generate("s", "e", 3)
        assertTrue(o is ExampleGenerationService.Outcome.Success)
        assertEquals(listOf("a", "b", "c"), (o as ExampleGenerationService.Outcome.Success).examples)
        assertEquals(1, settingsStore.load().todayCallCount)
    }

    @Test
    fun `Partial when fewer returned`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        fakeAdapter.result = Result.success(GenerationResult(listOf("a"), 3))
        val o = service.generate("s", "e", 3) as ExampleGenerationService.Outcome.Partial
        assertEquals(listOf("a"), o.examples)
        assertEquals(3, o.requested)
    }

    @Test
    fun `Failure when adapter fails`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        fakeAdapter.result = Result.failure(LlmException(LlmError.RateLimited))
        val o = service.generate("s", "e", 3)
        assertTrue(o is ExampleGenerationService.Outcome.Failure)
        assertTrue((o as ExampleGenerationService.Outcome.Failure).error is LlmError.RateLimited)
        assertEquals(1, settingsStore.load().todayCallCount)
    }

    @Test
    fun `zero examples with success returns ParseFailure Outcome`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        fakeAdapter.result = Result.success(GenerationResult(emptyList(), 3))
        val o = service.generate("s", "e", 3) as ExampleGenerationService.Outcome.Failure
        assertTrue(o.error is LlmError.ParseFailure)
    }

    @Test
    fun `DailyCapExceeded does not call adapter`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        settingsStore.setDailyCap(2)
        fakeAdapter.result = Result.success(GenerationResult(listOf("a"), 1))
        service.generate("s", "e", 1)
        service.generate("s", "e", 1)
        val o = service.generate("s", "e", 1)
        assertTrue(o is ExampleGenerationService.Outcome.DailyCapExceeded)
        assertEquals(2, fakeAdapter.callCount)
    }

    @Test
    fun `date boundary resets counter`() = runBlocking {
        settingsStore.setActiveProvider(ProviderId.OPENAI)
        keyStore.save(ProviderId.OPENAI, "sk")
        settingsStore.setDailyCap(1)
        fakeAdapter.result = Result.success(GenerationResult(listOf("a"), 1))
        service.generate("s", "e", 1)
        val blocked = service.generate("s", "e", 1)
        assertTrue(blocked is ExampleGenerationService.Outcome.DailyCapExceeded)
        clock.today = "2026-04-20"
        val retry = service.generate("s", "e", 1)
        assertTrue(retry is ExampleGenerationService.Outcome.Success)
    }

    class FakeAdapter(override val providerId: ProviderId) : LlmAdapter {
        var result: Result<GenerationResult> = Result.success(GenerationResult(emptyList(), 0))
        var callCount = 0
        override suspend fun validateKey(apiKey: String): Result<Unit> = Result.success(Unit)
        override suspend fun generateExamples(
            apiKey: String, model: String, shortcut: String, expansion: String, count: Int,
        ): Result<GenerationResult> {
            callCount++
            return result
        }
    }

    class FakeRegistry(private val adapter: LlmAdapter) : AdapterRegistry {
        override fun adapterFor(provider: ProviderId): LlmAdapter = adapter
    }
}
```

**Step 2: 테스트 실행 → 실패**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.ExampleGenerationServiceTest"`
Expected: 컴파일 에러.

**Step 3: `ExampleGenerationService.kt` 작성 (AdapterRegistry 인터페이스 포함)**

```kotlin
package com.kore2.shortcutime.llm

import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.data.SecureKeyStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AdapterRegistry {
    fun adapterFor(provider: ProviderId): LlmAdapter
}

class ExampleGenerationService(
    private val keyStore: SecureKeyStore,
    private val settingsStore: LlmSettingsStore,
    private val registry: AdapterRegistry,
) {
    sealed class Outcome {
        data class Success(val examples: List<String>) : Outcome()
        data class Partial(val examples: List<String>, val requested: Int) : Outcome()
        data class Failure(val error: LlmError) : Outcome()
        object NoActiveProvider : Outcome()
        object NoKey : Outcome()
        object DailyCapExceeded : Outcome()
    }

    private val counterMutex = Mutex()

    suspend fun generate(shortcut: String, expansion: String, count: Int): Outcome {
        val snapshot = settingsStore.load()
        val provider = snapshot.activeProvider ?: return Outcome.NoActiveProvider
        val key = keyStore.get(provider) ?: return Outcome.NoKey

        counterMutex.withLock {
            val current = settingsStore.load()
            if (current.todayCallCount >= current.dailyCallCap) return Outcome.DailyCapExceeded
            settingsStore.incrementCallCount()
        }

        val model = settingsStore.load().modelByProvider[provider]
            ?: ModelCatalog.recommendedModelId(provider)

        val adapter = registry.adapterFor(provider)
        val result = adapter.generateExamples(key, model, shortcut, expansion, count)
        return result.fold(
            onSuccess = { gen ->
                when {
                    gen.examples.isEmpty() -> Outcome.Failure(LlmError.ParseFailure)
                    gen.examples.size >= count -> Outcome.Success(gen.examples.take(count))
                    else -> Outcome.Partial(gen.examples, count)
                }
            },
            onFailure = { t ->
                val err = (t as? LlmException)?.error ?: LlmError.Unknown(t.message.orEmpty())
                Outcome.Failure(err)
            },
        )
    }
}
```

**Step 4: `LlmRegistry` 가 `AdapterRegistry` 를 구현하도록 수정**

파일 `app/src/main/java/com/kore2/shortcutime/llm/LlmRegistry.kt` 1번째 줄 바로 아래 `class LlmRegistry(...)` 선언을 `class LlmRegistry(...)` 에서 `class LlmRegistry(private val httpClient: OkHttpClient) : AdapterRegistry {` 로 변경, 그리고 `fun adapterFor` 앞에 `override` 추가:

```kotlin
package com.kore2.shortcutime.llm

import okhttp3.OkHttpClient

class LlmRegistry(private val httpClient: OkHttpClient) : AdapterRegistry {
    override fun adapterFor(provider: ProviderId): LlmAdapter = when (provider) {
        ProviderId.OPENAI -> OpenAiCompatibleAdapter(OpenAiCompatibleAdapter.OPENAI_BASE_URL, ProviderId.OPENAI, httpClient)
        ProviderId.GROK -> OpenAiCompatibleAdapter(OpenAiCompatibleAdapter.GROK_BASE_URL, ProviderId.GROK, httpClient)
        ProviderId.DEEPSEEK -> OpenAiCompatibleAdapter(OpenAiCompatibleAdapter.DEEPSEEK_BASE_URL, ProviderId.DEEPSEEK, httpClient)
        ProviderId.CLAUDE -> ClaudeAdapter(httpClient = httpClient)
        ProviderId.GEMINI -> GeminiAdapter(httpClient = httpClient)
    }
}
```

**Step 5: 테스트 실행 → 통과**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.llm.ExampleGenerationServiceTest"`
Expected: 8 tests passed.

**Step 6: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/llm/ExampleGenerationService.kt \
        app/src/main/java/com/kore2/shortcutime/llm/LlmRegistry.kt \
        app/src/test/java/com/kore2/shortcutime/llm/ExampleGenerationServiceTest.kt
git commit -m "feat(phase2): add ExampleGenerationService with daily cap and partial handling"
```

---

## Task 15: ShortcutApplication 에 lazy 싱글톤 확장

**Files:**
- Modify: `app/src/main/java/com/kore2/shortcutime/ShortcutApplication.kt`

**Step 1: `ShortcutApplication.kt` 전체를 다음으로 교체**

```kotlin
package com.kore2.shortcutime

import android.app.Application
import android.content.Context
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.KeyboardThemeStore
import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.data.SecureKeyStore
import com.kore2.shortcutime.data.SystemClock
import com.kore2.shortcutime.llm.ExampleGenerationService
import com.kore2.shortcutime.llm.HttpClientFactory
import com.kore2.shortcutime.llm.LlmRegistry

class ShortcutApplication : Application() {
    val repository: FolderRepository by lazy { FolderRepository(applicationContext) }
    val themeStore: KeyboardThemeStore by lazy { KeyboardThemeStore(applicationContext) }

    val clock by lazy { SystemClock() }
    val secureKeyStore by lazy { SecureKeyStore(applicationContext) }
    val llmSettingsStore by lazy { LlmSettingsStore(applicationContext, clock) }
    val llmRegistry by lazy { LlmRegistry(HttpClientFactory.create(debug = BuildConfig.DEBUG)) }
    val exampleGenerationService by lazy {
        ExampleGenerationService(secureKeyStore, llmSettingsStore, llmRegistry)
    }

    override fun onCreate() {
        super.onCreate()
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

**Step 2: 빌드 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

**Step 3: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/ShortcutApplication.kt
git commit -m "feat(phase2): wire SecureKeyStore, LlmSettings, service into Application"
```

---

## Task 16: Phase 2 문자열 리소스

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

**Step 1: `strings.xml` 에 다음 항목들 추가 (`</resources>` 바로 위에)**

```xml
    <!-- Phase 2 LLM settings -->
    <string name="settings_ai_row">AI 설정</string>
    <string name="llm_settings_title">AI 설정</string>
    <string name="llm_api_keys_title">API 키</string>
    <string name="llm_active_provider_title">활성 공급자</string>
    <string name="llm_model_title">모델</string>
    <string name="llm_daily_cap_title">하루 호출 한도</string>
    <string name="llm_daily_cap_format">%1$d 회 / 일</string>
    <string name="llm_today_usage_format">오늘 사용: %1$d / %2$d</string>
    <string name="llm_no_keys_hint">먼저 API 키를 저장하세요</string>
    <string name="llm_provider_openai">OpenAI</string>
    <string name="llm_provider_claude">Anthropic Claude</string>
    <string name="llm_provider_gemini">Google Gemini</string>
    <string name="llm_provider_grok">xAI Grok</string>
    <string name="llm_provider_deepseek">DeepSeek</string>
    <string name="llm_key_status_saved">저장됨</string>
    <string name="llm_key_status_missing">미설정</string>
    <string name="llm_recommended_badge">추천</string>

    <string name="api_key_dialog_title_format">%1$s API 키</string>
    <string name="api_key_get_link">API 키 발급받기 ↗</string>
    <string name="api_key_input_hint">API 키 입력</string>
    <string name="api_key_status_validating">검증 중…</string>
    <string name="api_key_status_saved_format">저장됨 (%1$s)</string>
    <string name="api_key_action_test_save">테스트 &amp; 저장</string>
    <string name="api_key_action_delete">삭제</string>
    <string name="api_key_confirm_delete_title">API 키 삭제</string>
    <string name="api_key_confirm_delete_message">%1$s 의 API 키를 삭제할까요?</string>

    <string name="snack_saved">저장되었습니다</string>
    <string name="snack_key_invalid">키가 올바르지 않습니다</string>
    <string name="snack_network">인터넷 연결 확인</string>
    <string name="snack_timeout">응답 지연. 재시도</string>
    <string name="snack_server_error">공급자 서버 오류. 잠시 후 재시도</string>
    <string name="snack_unknown_format">오류: %1$s</string>

    <string name="snack_no_provider">먼저 AI 공급자를 설정하세요</string>
    <string name="snack_no_key">활성 공급자의 API 키가 없습니다</string>
    <string name="snack_daily_cap_format">오늘 호출 한도(%1$d회) 도달</string>
    <string name="snack_generate_success_format">예문 %1$d개 추가됨</string>
    <string name="snack_generate_partial_format">%1$d개 중 %2$d개 생성됨</string>
    <string name="snack_generate_key_invalid">API 키 오류. 설정에서 확인</string>
    <string name="snack_generate_rate_limited">요청 한도 초과. 잠시 후 재시도</string>
    <string name="snack_generate_content_filtered">콘텐츠 정책에 걸림. 다른 예문 요청</string>
    <string name="snack_generate_parse_failure">응답 형식 오류. 재시도</string>
    <string name="snack_generate_truncated">응답 잘림. 재시도</string>
    <string name="snack_action_retry">재시도</string>
    <string name="snack_action_settings">설정</string>
    <string name="snack_action_change_cap">한도 변경</string>
```

**Step 2: 이미 있는 `phase2_feature_not_ready` 문자열은 그대로 두기** (아직 Task 19 전에는 제거하지 않음. Task 19에서 최종 제거).

**Step 3: 빌드 확인**

Run: `./gradlew :app:processDebugResources`
Expected: `BUILD SUCCESSFUL`.

**Step 4: 커밋**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(phase2): add Phase 2 string resources"
```

---

## Task 17: ShortcutEditorViewModel — EditorEvent + onGenerateExamplesClicked

**Files:**
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorViewModel.kt`
- Create: `app/src/test/java/com/kore2/shortcutime/ui/editor/ShortcutEditorViewModelPhase2Test.kt`

**Step 1: ViewModel factory 에 service 인자 추가 + 함수 구현**

현재 `ShortcutEditorViewModel` 은 `FolderRepository` 만 받음. `ExampleGenerationService` 추가하고 `EditorEvent` sealed class + `onGenerateExamplesClicked` 추가. 파일 전체 교체:

```kotlin
package com.kore2.shortcutime.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.ExampleSourceType
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.llm.ExampleGenerationService
import com.kore2.shortcutime.llm.LanguageClassifier
import com.kore2.shortcutime.llm.LlmError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ShortcutEditorViewModel(
    private val repository: FolderRepository,
    private val generationService: ExampleGenerationService,
    val folderId: String,
    val shortcutId: String?,
) : ViewModel() {

    private val _entry = MutableStateFlow<ShortcutEntry?>(null)
    val entry: StateFlow<ShortcutEntry?> = _entry.asStateFlow()

    private val _savedShortcuts = MutableStateFlow<List<ShortcutEntry>>(emptyList())
    val savedShortcuts: StateFlow<List<ShortcutEntry>> = _savedShortcuts.asStateFlow()

    private val _workingExamples = MutableStateFlow<List<ExampleItem>>(emptyList())
    val workingExamples: StateFlow<List<ExampleItem>> = _workingExamples.asStateFlow()

    private val _folderMissing = MutableStateFlow(false)
    val folderMissing: StateFlow<Boolean> = _folderMissing.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EditorEvent> = _events.asSharedFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val folder = repository.getFolder(folderId)
            if (folder == null) { _folderMissing.value = true; return@launch }
            _savedShortcuts.value = folder.shortcuts
            val current = shortcutId?.let { id -> folder.shortcuts.firstOrNull { it.id == id } }
            _entry.value = current
            _workingExamples.value = current?.examples.orEmpty()
        }
    }

    fun refreshSavedShortcuts() {
        viewModelScope.launch {
            val folder = repository.getFolder(folderId) ?: return@launch
            _savedShortcuts.value = folder.shortcuts
        }
    }

    fun addOrUpdateExample(example: ExampleItem) {
        val list = _workingExamples.value.toMutableList()
        val index = list.indexOfFirst { it.id == example.id }
        if (index >= 0) list[index] = example else list.add(example)
        _workingExamples.value = list
    }

    fun deleteExample(id: String) {
        _workingExamples.value = _workingExamples.value.filterNot { it.id == id }
    }

    fun onGenerateExamplesClicked(shortcut: String, expansion: String, count: Int) {
        if (shortcut.isBlank() || expansion.isBlank()) {
            _events.tryEmit(EditorEvent.GenerateError(LlmError.Unknown("shortcut/expansion empty")))
            return
        }
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                val outcome = generationService.generate(shortcut, expansion, count)
                when (outcome) {
                    is ExampleGenerationService.Outcome.Success -> {
                        outcome.examples.forEach { addGeneratedExample(it) }
                        _events.emit(EditorEvent.GenerateSuccess(outcome.examples.size))
                    }
                    is ExampleGenerationService.Outcome.Partial -> {
                        outcome.examples.forEach { addGeneratedExample(it) }
                        _events.emit(EditorEvent.GeneratePartial(outcome.examples.size, outcome.requested))
                    }
                    is ExampleGenerationService.Outcome.Failure -> _events.emit(EditorEvent.GenerateError(outcome.error))
                    ExampleGenerationService.Outcome.NoActiveProvider -> _events.emit(EditorEvent.NoActiveProvider)
                    ExampleGenerationService.Outcome.NoKey -> _events.emit(EditorEvent.NoKey)
                    ExampleGenerationService.Outcome.DailyCapExceeded -> _events.emit(EditorEvent.DailyCapExceeded)
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun addGeneratedExample(text: String) {
        val item = when (LanguageClassifier.classify(text)) {
            LanguageClassifier.Language.KOREAN -> ExampleItem(
                id = UUID.randomUUID().toString(),
                korean = text,
                english = "",
                sourceType = ExampleSourceType.AUTO,
            )
            LanguageClassifier.Language.ENGLISH -> ExampleItem(
                id = UUID.randomUUID().toString(),
                english = text,
                korean = "",
                sourceType = ExampleSourceType.AUTO,
            )
        }
        addOrUpdateExample(item)
    }

    fun save(
        shortcut: String, expandsTo: String, note: String,
        caseSensitive: Boolean, backspaceToUndo: Boolean,
    ): SaveResult {
        if (shortcut.isBlank()) return SaveResult.MissingShortcut
        if (expandsTo.isBlank()) return SaveResult.MissingExpandsTo
        val current = _entry.value
        val updated = if (current == null) {
            ShortcutEntry(
                shortcut = shortcut, expandsTo = expandsTo,
                examples = _workingExamples.value, note = note,
                caseSensitive = caseSensitive, backspaceToUndo = backspaceToUndo,
            )
        } else {
            current.copy(
                shortcut = shortcut, expandsTo = expandsTo,
                examples = _workingExamples.value, note = note,
                caseSensitive = caseSensitive, backspaceToUndo = backspaceToUndo,
                updatedAt = System.currentTimeMillis(),
            )
        }
        if (current == null) repository.addShortcut(folderId, updated) else repository.updateShortcut(folderId, updated)
        return SaveResult.Success
    }

    fun deleteShortcut(id: String) {
        repository.deleteShortcut(folderId, id)
        refreshSavedShortcuts()
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data object MissingShortcut : SaveResult()
        data object MissingExpandsTo : SaveResult()
    }

    sealed class EditorEvent {
        data class GenerateSuccess(val addedCount: Int) : EditorEvent()
        data class GeneratePartial(val got: Int, val requested: Int) : EditorEvent()
        data class GenerateError(val error: LlmError) : EditorEvent()
        data object NoActiveProvider : EditorEvent()
        data object NoKey : EditorEvent()
        data object DailyCapExceeded : EditorEvent()
    }

    companion object {
        fun factory(folderId: String, shortcutId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ShortcutApplication
                ShortcutEditorViewModel(app.repository, app.exampleGenerationService, folderId, shortcutId)
            }
        }
        private val APPLICATION_KEY = ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }
}
```

**Step 2: ViewModel 테스트 작성**

`app/src/test/java/com/kore2/shortcutime/ui/editor/ShortcutEditorViewModelPhase2Test.kt`:

```kotlin
package com.kore2.shortcutime.ui.editor

import androidx.test.core.app.ApplicationProvider
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.FakeClock
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.data.SecureKeyStore
import com.kore2.shortcutime.llm.ExampleGenerationService
import com.kore2.shortcutime.llm.GenerationResult
import com.kore2.shortcutime.llm.LlmAdapter
import com.kore2.shortcutime.llm.ProviderId
import com.kore2.shortcutime.llm.AdapterRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShortcutEditorViewModelPhase2Test {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setMainDispatcher() = Dispatchers.setMain(dispatcher)
    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun `generate success emits event and stores AUTO sourceType`() = runTest(dispatcher) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("llm_settings", 0).edit().clear().apply()
        val repo = FolderRepository(ctx)
        val folder = FolderItem(title = "T")
        repo.addFolder(folder)

        val clock = FakeClock("2026-04-19")
        val keyStore = SecureKeyStore(ctx).apply { save(ProviderId.OPENAI, "sk") }
        val settings = LlmSettingsStore(ctx, clock).apply { setActiveProvider(ProviderId.OPENAI) }
        val registry = object : AdapterRegistry {
            override fun adapterFor(provider: ProviderId): LlmAdapter = object : LlmAdapter {
                override val providerId = ProviderId.OPENAI
                override suspend fun validateKey(apiKey: String) = Result.success(Unit)
                override suspend fun generateExamples(
                    apiKey: String, model: String, shortcut: String, expansion: String, count: Int,
                ) = Result.success(GenerationResult(listOf("BTW, I'll be late.", "안녕하세요"), count))
            }
        }
        val service = ExampleGenerationService(keyStore, settings, registry)
        val vm = ShortcutEditorViewModel(repo, service, folder.id, null)

        val emitted = mutableListOf<ShortcutEditorViewModel.EditorEvent>()
        val job = launch { vm.events.take(1).toList(emitted) }
        vm.onGenerateExamplesClicked("btw", "by the way", 2)
        advanceUntilIdle()

        assertTrue(emitted.first() is ShortcutEditorViewModel.EditorEvent.GenerateSuccess)
        val examples = vm.workingExamples.first()
        assertEquals(2, examples.size)
        assertTrue(examples.any { it.english.contains("BTW") })
        assertTrue(examples.any { it.korean.contains("안녕") })
        examples.forEach { assertEquals(com.kore2.shortcutime.data.ExampleSourceType.AUTO, it.sourceType) }

        job.cancel()
    }
}
```

**Step 3: 테스트 실행 → 통과**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.ui.editor.ShortcutEditorViewModelPhase2Test"`
Expected: 1 test passed.

**Step 4: 기존 테스트 회귀 확인 — 전체 유닛 테스트 실행**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 전체 통과.

**Step 5: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorViewModel.kt \
        app/src/test/java/com/kore2/shortcutime/ui/editor/ShortcutEditorViewModelPhase2Test.kt
git commit -m "feat(phase2): wire ExampleGenerationService into ShortcutEditorViewModel"
```

---

## Task 18: LlmSettingsFragment 레이아웃 + item_provider_row + dialog_api_key

**Files:**
- Create: `app/src/main/res/layout/fragment_llm_settings.xml`
- Create: `app/src/main/res/layout/item_provider_row.xml`
- Create: `app/src/main/res/layout/dialog_api_key.xml`

**Step 1: `fragment_llm_settings.xml` 작성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_app">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/topToolbar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:title="@string/llm_settings_title"
            android:titleTextColor="@color/text_primary" />

        <TextView
            android:id="@+id/apiKeysTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="18dp"
            android:text="@string/llm_api_keys_title"
            android:textColor="@color/text_primary"
            android:textStyle="bold" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/providersRecyclerView"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:nestedScrollingEnabled="false" />

        <TextView
            android:id="@+id/activeProviderTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="@string/llm_active_provider_title"
            android:textColor="@color/text_primary"
            android:textStyle="bold" />

        <RadioGroup
            android:id="@+id/activeProviderGroup"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:orientation="vertical" />

        <TextView
            android:id="@+id/noKeysHint"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/llm_no_keys_hint"
            android:textColor="@color/text_secondary"
            android:visibility="gone" />

        <TextView
            android:id="@+id/modelTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="@string/llm_model_title"
            android:textColor="@color/text_primary"
            android:textStyle="bold" />

        <Spinner
            android:id="@+id/modelSpinner"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp" />

        <TextView
            android:id="@+id/dailyCapTitle"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:text="@string/llm_daily_cap_title"
            android:textColor="@color/text_primary"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/dailyCapValue"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textColor="@color/text_primary" />

        <SeekBar
            android:id="@+id/dailyCapSeekBar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:max="490" />

        <TextView
            android:id="@+id/todayUsage"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textColor="@color/text_secondary" />
    </LinearLayout>
</ScrollView>
```

**Step 2: `item_provider_row.xml` 작성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/selectableItemBackground"
    android:clickable="true"
    android:focusable="true"
    android:gravity="center_vertical"
    android:minHeight="56dp"
    android:orientation="horizontal"
    android:paddingHorizontal="4dp">

    <TextView
        android:id="@+id/providerName"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:textColor="@color/text_primary"
        android:textSize="16sp" />

    <TextView
        android:id="@+id/providerStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="8dp"
        android:textColor="@color/text_secondary"
        android:textSize="14sp" />
</LinearLayout>
```

**Step 3: `dialog_api_key.xml` 작성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="20dp">

    <TextView
        android:id="@+id/getKeyLink"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="?attr/selectableItemBackground"
        android:clickable="true"
        android:focusable="true"
        android:paddingVertical="8dp"
        android:text="@string/api_key_get_link"
        android:textColor="@color/text_primary" />

    <com.google.android.material.textfield.TextInputLayout
        android:id="@+id/keyInputLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:hint="@string/api_key_input_hint"
        app:endIconMode="password_toggle"
        xmlns:app="http://schemas.android.com/apk/res-auto">

        <com.google.android.material.textfield.TextInputEditText
            android:id="@+id/keyInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:inputType="textVisiblePassword" />
    </com.google.android.material.textfield.TextInputLayout>

    <TextView
        android:id="@+id/statusLine"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:textColor="@color/text_secondary" />
</LinearLayout>
```

**Step 4: 빌드 확인**

Run: `./gradlew :app:processDebugResources`
Expected: `BUILD SUCCESSFUL`.

**Step 5: 커밋**

```bash
git add app/src/main/res/layout/fragment_llm_settings.xml \
        app/src/main/res/layout/item_provider_row.xml \
        app/src/main/res/layout/dialog_api_key.xml
git commit -m "feat(phase2): add LLM settings and API key dialog layouts"
```

---

## Task 19: LlmSettingsViewModel

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/ui/settings/llm/LlmSettingsViewModel.kt`

**Step 1: `LlmSettingsViewModel.kt` 작성**

```kotlin
package com.kore2.shortcutime.ui.settings.llm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.LlmSettings
import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.data.SecureKeyStore
import com.kore2.shortcutime.llm.ModelCatalog
import com.kore2.shortcutime.llm.ProviderId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LlmSettingsViewModel(
    private val keyStore: SecureKeyStore,
    private val settingsStore: LlmSettingsStore,
) : ViewModel() {

    data class State(
        val savedProviders: Set<ProviderId>,
        val settings: LlmSettings,
    )

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<State> = _state.asStateFlow()

    fun refresh() { _state.value = buildState() }

    fun setActiveProvider(provider: ProviderId?) {
        settingsStore.setActiveProvider(provider)
        refresh()
    }

    fun setModel(provider: ProviderId, modelId: String) {
        settingsStore.setModel(provider, modelId)
        refresh()
    }

    fun setDailyCap(cap: Int) {
        settingsStore.setDailyCap(cap)
        refresh()
    }

    fun saveApiKey(provider: ProviderId, key: String) {
        keyStore.save(provider, key)
        refresh()
    }

    fun deleteApiKey(provider: ProviderId) {
        keyStore.clear(provider)
        val s = settingsStore.load()
        if (s.activeProvider == provider) settingsStore.setActiveProvider(null)
        refresh()
    }

    fun modelsFor(provider: ProviderId) = ModelCatalog.modelsFor(provider)
    fun currentModelFor(provider: ProviderId): String =
        settingsStore.load().modelByProvider[provider] ?: ModelCatalog.recommendedModelId(provider)

    private fun buildState() = State(
        savedProviders = keyStore.getAllSaved(),
        settings = settingsStore.load(),
    )

    companion object {
        val factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ShortcutApplication
                LlmSettingsViewModel(app.secureKeyStore, app.llmSettingsStore)
            }
        }
        private val APPLICATION_KEY = ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

**Step 3: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/settings/llm/LlmSettingsViewModel.kt
git commit -m "feat(phase2): add LlmSettingsViewModel"
```

---

## Task 20: ProviderRowAdapter

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/ui/settings/llm/ProviderRowAdapter.kt`

**Step 1: `ProviderRowAdapter.kt` 작성**

```kotlin
package com.kore2.shortcutime.ui.settings.llm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.R
import com.kore2.shortcutime.databinding.ItemProviderRowBinding
import com.kore2.shortcutime.llm.ProviderId

class ProviderRowAdapter(
    private val onClick: (ProviderId) -> Unit,
) : RecyclerView.Adapter<ProviderRowAdapter.VH>() {

    data class Row(val providerId: ProviderId, val saved: Boolean)

    private var rows: List<Row> = emptyList()

    fun submit(rows: List<Row>) {
        this.rows = rows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProviderRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(rows[position])

    override fun getItemCount(): Int = rows.size

    inner class VH(private val binding: ItemProviderRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: Row) {
            val ctx = binding.root.context
            binding.providerName.text = ctx.getString(providerLabel(row.providerId))
            binding.providerStatus.text = ctx.getString(
                if (row.saved) R.string.llm_key_status_saved else R.string.llm_key_status_missing
            )
            binding.root.setOnClickListener { onClick(row.providerId) }
        }
    }

    private fun providerLabel(id: ProviderId): Int = when (id) {
        ProviderId.OPENAI -> R.string.llm_provider_openai
        ProviderId.CLAUDE -> R.string.llm_provider_claude
        ProviderId.GEMINI -> R.string.llm_provider_gemini
        ProviderId.GROK -> R.string.llm_provider_grok
        ProviderId.DEEPSEEK -> R.string.llm_provider_deepseek
    }
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

**Step 3: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/settings/llm/ProviderRowAdapter.kt
git commit -m "feat(phase2): add ProviderRowAdapter for LLM settings list"
```

---

## Task 21: ApiKeyDialogFragment

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/ui/settings/llm/ApiKeyDialogFragment.kt`

**Step 1: `ApiKeyDialogFragment.kt` 작성**

```kotlin
package com.kore2.shortcutime.ui.settings.llm

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.databinding.DialogApiKeyBinding
import com.kore2.shortcutime.llm.LlmError
import com.kore2.shortcutime.llm.LlmException
import com.kore2.shortcutime.llm.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApiKeyDialogFragment : DialogFragment() {

    private val vm: LlmSettingsViewModel by activityViewModels { LlmSettingsViewModel.factory }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val provider = ProviderId.valueOf(requireArguments().getString(ARG_PROVIDER)!!)
        val binding = DialogApiKeyBinding.inflate(LayoutInflater.from(requireContext()))
        val providerLabel = getString(providerLabelRes(provider))
        binding.getKeyLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(providerKeyUrl(provider))))
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.api_key_dialog_title_format, providerLabel))
            .setView(binding.root)
            .setPositiveButton(R.string.api_key_action_test_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.api_key_action_delete, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            positive.setOnClickListener {
                val key = binding.keyInput.text?.toString().orEmpty().trim()
                if (key.isEmpty()) {
                    binding.statusLine.text = getString(R.string.snack_key_invalid)
                    return@setOnClickListener
                }
                positive.isEnabled = false
                binding.statusLine.text = getString(R.string.api_key_status_validating)
                lifecycleScope.launch {
                    val app = ShortcutApplication.from(requireContext())
                    val adapter = app.llmRegistry.adapterFor(provider)
                    val result = withContext(Dispatchers.IO) { adapter.validateKey(key) }
                    positive.isEnabled = true
                    if (result.isSuccess) {
                        vm.saveApiKey(provider, key)
                        dialog.dismiss()
                    } else {
                        val err = (result.exceptionOrNull() as? LlmException)?.error
                        binding.statusLine.text = errorMessage(err)
                    }
                }
            }
            neutral.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.api_key_confirm_delete_title)
                    .setMessage(getString(R.string.api_key_confirm_delete_message, providerLabel))
                    .setPositiveButton(R.string.api_key_action_delete) { _, _ ->
                        vm.deleteApiKey(provider)
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
        return dialog
    }

    private fun errorMessage(err: LlmError?): String = when (err) {
        LlmError.InvalidKey -> getString(R.string.snack_key_invalid)
        LlmError.Network -> getString(R.string.snack_network)
        LlmError.Timeout -> getString(R.string.snack_timeout)
        LlmError.ServerError -> getString(R.string.snack_server_error)
        is LlmError.Unknown -> getString(R.string.snack_unknown_format, err.message)
        else -> getString(R.string.snack_unknown_format, err?.toString().orEmpty())
    }

    private fun providerLabelRes(id: ProviderId): Int = when (id) {
        ProviderId.OPENAI -> R.string.llm_provider_openai
        ProviderId.CLAUDE -> R.string.llm_provider_claude
        ProviderId.GEMINI -> R.string.llm_provider_gemini
        ProviderId.GROK -> R.string.llm_provider_grok
        ProviderId.DEEPSEEK -> R.string.llm_provider_deepseek
    }

    private fun providerKeyUrl(id: ProviderId): String = when (id) {
        ProviderId.OPENAI -> "https://platform.openai.com/api-keys"
        ProviderId.CLAUDE -> "https://console.anthropic.com/settings/keys"
        ProviderId.GEMINI -> "https://aistudio.google.com/app/apikey"
        ProviderId.GROK -> "https://console.x.ai/"
        ProviderId.DEEPSEEK -> "https://platform.deepseek.com/api_keys"
    }

    companion object {
        private const val ARG_PROVIDER = "arg_provider"

        fun newInstance(provider: ProviderId) = ApiKeyDialogFragment().apply {
            arguments = Bundle().apply { putString(ARG_PROVIDER, provider.name) }
        }
    }
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

**Step 3: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/settings/llm/ApiKeyDialogFragment.kt
git commit -m "feat(phase2): add ApiKeyDialogFragment with validate-and-save flow"
```

---

## Task 22: LlmSettingsFragment

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/ui/settings/llm/LlmSettingsFragment.kt`

**Step 1: `LlmSettingsFragment.kt` 작성**

```kotlin
package com.kore2.shortcutime.ui.settings.llm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kore2.shortcutime.R
import com.kore2.shortcutime.data.LlmSettingsStore
import com.kore2.shortcutime.databinding.FragmentLlmSettingsBinding
import com.kore2.shortcutime.llm.ProviderId
import kotlinx.coroutines.launch

class LlmSettingsFragment : Fragment() {
    private var _binding: FragmentLlmSettingsBinding? = null
    private val binding get() = _binding!!

    private val vm: LlmSettingsViewModel by viewModels { LlmSettingsViewModel.factory }
    private lateinit var rowAdapter: ProviderRowAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLlmSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        rowAdapter = ProviderRowAdapter { provider ->
            ApiKeyDialogFragment.newInstance(provider).show(parentFragmentManager, "api_key_dialog")
        }
        binding.providersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.providersRecyclerView.adapter = rowAdapter

        binding.dailyCapSeekBar.max = LlmSettingsStore.MAX_CAP - LlmSettingsStore.MIN_CAP
        binding.dailyCapSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) vm.setDailyCap(progress + LlmSettingsStore.MIN_CAP)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { render(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: LlmSettingsViewModel.State) {
        rowAdapter.submit(
            ProviderId.values().map { ProviderRowAdapter.Row(it, saved = it in state.savedProviders) }
        )
        renderActiveProviderGroup(state)
        renderModelSpinner(state)
        val cap = state.settings.dailyCallCap
        binding.dailyCapValue.text = getString(R.string.llm_daily_cap_format, cap)
        binding.dailyCapSeekBar.progress = cap - LlmSettingsStore.MIN_CAP
        binding.todayUsage.text = getString(
            R.string.llm_today_usage_format,
            state.settings.todayCallCount, cap,
        )
    }

    private fun renderActiveProviderGroup(state: LlmSettingsViewModel.State) {
        binding.activeProviderGroup.removeAllViews()
        val saved = state.savedProviders.toList().sortedBy { it.ordinal }
        if (saved.isEmpty()) {
            binding.noKeysHint.visibility = View.VISIBLE
            binding.activeProviderGroup.visibility = View.GONE
            return
        }
        binding.noKeysHint.visibility = View.GONE
        binding.activeProviderGroup.visibility = View.VISIBLE
        saved.forEach { provider ->
            val rb = RadioButton(requireContext()).apply {
                text = getString(providerLabelRes(provider))
                isChecked = state.settings.activeProvider == provider
                setOnClickListener { vm.setActiveProvider(provider) }
            }
            binding.activeProviderGroup.addView(rb)
        }
    }

    private fun renderModelSpinner(state: LlmSettingsViewModel.State) {
        val active = state.settings.activeProvider
        if (active == null) {
            binding.modelSpinner.adapter = null
            return
        }
        val models = vm.modelsFor(active)
        val labels = models.map { model ->
            if (model.isRecommended) "${model.displayName} (${getString(R.string.llm_recommended_badge)})"
            else model.displayName
        }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.modelSpinner.adapter = adapter
        val currentId = vm.currentModelFor(active)
        val idx = models.indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
        binding.modelSpinner.setSelection(idx)
        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = models[position]
                if (selected.id != currentId) vm.setModel(active, selected.id)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun providerLabelRes(id: ProviderId): Int = when (id) {
        ProviderId.OPENAI -> R.string.llm_provider_openai
        ProviderId.CLAUDE -> R.string.llm_provider_claude
        ProviderId.GEMINI -> R.string.llm_provider_gemini
        ProviderId.GROK -> R.string.llm_provider_grok
        ProviderId.DEEPSEEK -> R.string.llm_provider_deepseek
    }
}
```

**Step 2: 컴파일 확인**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

**Step 3: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/settings/llm/LlmSettingsFragment.kt
git commit -m "feat(phase2): add LlmSettingsFragment with providers list, active radio, model spinner, cap slider"
```

---

## Task 23: Navigation + SettingsFragment 진입점 연결

**Files:**
- Modify: `app/src/main/res/navigation/nav_graph.xml`
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/settings/SettingsFragment.kt`

**Step 1: `nav_graph.xml` 에서 `settingsFragment` 블록 수정 — action 추가 + 새 destination**

`<fragment android:id="@+id/settingsFragment" ...` 블록을 다음과 같이 교체:

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
    </fragment>

    <fragment
        android:id="@+id/llmSettingsFragment"
        android:name="com.kore2.shortcutime.ui.settings.llm.LlmSettingsFragment"
        android:label="LlmSettings"
        tools:layout="@layout/fragment_llm_settings"
        xmlns:tools="http://schemas.android.com/tools" />
```

**Step 2: `fragment_settings.xml` 에 "AI 설정" 버튼 추가**

기존 `fragment_settings.xml` 을 읽고 `placeholderText` 아래에 다음 버튼을 추가 (`LinearLayout` 자식으로):

```xml
    <com.google.android.material.button.MaterialButton
        android:id="@+id/aiSettingsButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="@string/settings_ai_row" />
```

**Step 3: `SettingsFragment.kt` 에 버튼 클릭 리스너 추가**

`onViewCreated` 안에, 기존 toolbar 설정 아래에 추가:

```kotlin
        binding.aiSettingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_llmSettings)
        }
```

필요한 import: `import com.kore2.shortcutime.R` (이미 있을 수 있음).

**Step 4: 빌드 확인**

Run: `./gradlew :app:compileDebugKotlin :app:processDebugResources`
Expected: `BUILD SUCCESSFUL`.

**Step 5: 커밋**

```bash
git add app/src/main/res/navigation/nav_graph.xml \
        app/src/main/res/layout/fragment_settings.xml \
        app/src/main/java/com/kore2/shortcutime/ui/settings/SettingsFragment.kt
git commit -m "feat(phase2): add LlmSettings destination and Settings entry button"
```

---

## Task 24: ShortcutEditorFragment — placeholder Toast 교체 + Snackbar 와이어링

**Files:**
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorFragment.kt`

**Step 1: `generateExamplesButton.setOnClickListener` 블록 교체**

현재 파일 라인 90-96 의 placeholder Toast 를 아래 코드로 교체:

```kotlin
        binding.generateExamplesButton.setOnClickListener {
            val shortcut = binding.shortcutInput.text?.toString().orEmpty().trim()
            val expansion = binding.expandsToInput.text?.toString().orEmpty().trim()
            viewModel.onGenerateExamplesClicked(shortcut, expansion, selectedGenerateCount)
        }
```

**Step 2: `observeViewModel()` 안에 `isGenerating` + `events` 수집 launch 블록 추가**

기존 `observeViewModel()` 의 `repeatOnLifecycle` 안쪽에 두 개의 `launch { ... }` 추가:

```kotlin
                launch {
                    viewModel.isGenerating.collect { generating ->
                        binding.generateExamplesButton.isEnabled = !generating
                        binding.generateExamplesButton.text = if (generating) {
                            getString(R.string.preview_animating, "")
                        } else {
                            getString(R.string.action_generate_examples)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { showEventSnackbar(it) }
                }
```

> 참고: `action_generate_examples` 는 Phase 1에 이미 정의된 문자열. 생성 중 임시 텍스트는 단순 처리 — 더 좋은 디자인은 Phase 2.1 에서 다듬기.

**Step 3: `showEventSnackbar` 멤버 함수 추가 (클래스 어디에나)**

```kotlin
    private fun showEventSnackbar(event: ShortcutEditorViewModel.EditorEvent) {
        val root = binding.root
        val msg: String
        val actionLabel: Int?
        val actionHandler: (() -> Unit)?
        when (event) {
            is ShortcutEditorViewModel.EditorEvent.GenerateSuccess -> {
                msg = getString(R.string.snack_generate_success_format, event.addedCount); actionLabel = null; actionHandler = null
            }
            is ShortcutEditorViewModel.EditorEvent.GeneratePartial -> {
                msg = getString(R.string.snack_generate_partial_format, event.requested, event.got); actionLabel = null; actionHandler = null
            }
            ShortcutEditorViewModel.EditorEvent.NoActiveProvider -> {
                msg = getString(R.string.snack_no_provider)
                actionLabel = R.string.snack_action_settings
                actionHandler = { navigateToLlmSettings() }
            }
            ShortcutEditorViewModel.EditorEvent.NoKey -> {
                msg = getString(R.string.snack_no_key)
                actionLabel = R.string.snack_action_settings
                actionHandler = { navigateToLlmSettings() }
            }
            ShortcutEditorViewModel.EditorEvent.DailyCapExceeded -> {
                val cap = ShortcutApplication.from(requireContext()).llmSettingsStore.load().dailyCallCap
                msg = getString(R.string.snack_daily_cap_format, cap)
                actionLabel = R.string.snack_action_change_cap
                actionHandler = { navigateToLlmSettings() }
            }
            is ShortcutEditorViewModel.EditorEvent.GenerateError -> {
                msg = when (val err = event.error) {
                    com.kore2.shortcutime.llm.LlmError.Network -> getString(R.string.snack_network)
                    com.kore2.shortcutime.llm.LlmError.Timeout -> getString(R.string.snack_timeout)
                    com.kore2.shortcutime.llm.LlmError.InvalidKey -> getString(R.string.snack_generate_key_invalid)
                    com.kore2.shortcutime.llm.LlmError.RateLimited -> getString(R.string.snack_generate_rate_limited)
                    com.kore2.shortcutime.llm.LlmError.ServerError -> getString(R.string.snack_server_error)
                    com.kore2.shortcutime.llm.LlmError.ContentFiltered -> getString(R.string.snack_generate_content_filtered)
                    com.kore2.shortcutime.llm.LlmError.ParseFailure -> getString(R.string.snack_generate_parse_failure)
                    com.kore2.shortcutime.llm.LlmError.Truncated -> getString(R.string.snack_generate_truncated)
                    is com.kore2.shortcutime.llm.LlmError.Unknown -> getString(R.string.snack_unknown_format, err.message)
                }
                actionLabel = when (event.error) {
                    com.kore2.shortcutime.llm.LlmError.InvalidKey -> R.string.snack_action_settings
                    else -> R.string.snack_action_retry
                }
                actionHandler = {
                    if (event.error is com.kore2.shortcutime.llm.LlmError.InvalidKey) navigateToLlmSettings()
                    else retryLastGeneration()
                }
            }
        }
        val snack = com.google.android.material.snackbar.Snackbar.make(root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
        if (actionLabel != null && actionHandler != null) snack.setAction(actionLabel) { actionHandler() }
        snack.show()
    }

    private fun navigateToLlmSettings() {
        findNavController().navigate(
            com.kore2.shortcutime.ui.editor.ShortcutEditorFragmentDirections
                // Settings 화면을 거쳐 LLM 설정에 도달. 우리 graph 에선 editor → settings action 이 없으므로
                // 간단히 editor 에서 folder list 로 pop 후 settings → llmSettings 로 유도하는 대신,
                // 직접 nav_graph 에 editor → llmSettings 지름길을 추가해도 됨. 여기서는 folder list 루트로 popUpTo 하고
                // HostActivity 의 다음 네비 의도를 간단히 firmal — 실전에서는 별도 action 추가를 권장.
                .actionShortcutEditorSelf(viewModel.folderId, viewModel.shortcutId)
        )
        // TODO(phase2.1): nav_graph 에 shortcutEditor → llmSettings action 추가해 한번에 이동
    }

    private fun retryLastGeneration() {
        val shortcut = binding.shortcutInput.text?.toString().orEmpty().trim()
        val expansion = binding.expandsToInput.text?.toString().orEmpty().trim()
        viewModel.onGenerateExamplesClicked(shortcut, expansion, selectedGenerateCount)
    }
```

> ⚠️ `navigateToLlmSettings` 는 단순화 버전. 완성도를 위해 Step 4 에서 `nav_graph.xml` 에 지름길 action 을 추가하고 해당 호출로 교체.

**Step 4: nav_graph 지름길 action 추가 + navigate 호출 교체**

`app/src/main/res/navigation/nav_graph.xml` 에서 `shortcutEditorFragment` 블록 안에 기존 `action_shortcutEditor_self` 바로 아래에 추가:

```xml
        <action
            android:id="@+id/action_shortcutEditor_to_llmSettings"
            app:destination="@id/llmSettingsFragment" />
```

그리고 Step 3 의 `navigateToLlmSettings` 본문을 교체:

```kotlin
    private fun navigateToLlmSettings() {
        findNavController().navigate(R.id.action_shortcutEditor_to_llmSettings)
    }
```

**Step 5: 불필요한 문자열 `phase2_feature_not_ready` 제거**

`app/src/main/res/values/strings.xml` 에서 `phase2_feature_not_ready` 한 줄 삭제.

**Step 6: 빌드 확인**

Run: `./gradlew :app:compileDebugKotlin :app:processDebugResources`
Expected: `BUILD SUCCESSFUL`. 만약 `phase2_feature_not_ready` 참조가 남아 있으면 삭제.

**Step 7: 전체 유닛 테스트 실행**

Run: `./gradlew :app:testDebugUnitTest`
Expected: 모든 테스트 통과.

**Step 8: 커밋**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorFragment.kt \
        app/src/main/res/navigation/nav_graph.xml \
        app/src/main/res/values/strings.xml
git commit -m "feat(phase2): replace placeholder Toast with real generation Snackbar flow"
```

---

## Task 25: 디버그 APK 빌드 + 수동 체크리스트 실행

**Files:** 없음 (수동 테스트)

**Step 1: 디버그 APK 빌드**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

**Step 2: APK 설치 (기기 연결되어 있으면 `adb install -r`, 아니면 수동)**

Run: `"/c/Users/kore2/AppData/Local/Android/Sdk/platform-tools/adb.exe" devices`
- 기기 있음: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- 기기 없음: APK 를 카카오톡·Drive 등으로 기기에 전송 후 수동 설치

**Step 3: 최소 2개 공급자 실 키로 수동 체크리스트 실행**

아래 항목들 순서대로 확인:

- [ ] 설정 → AI 설정 진입. 5개 공급자 행 모두 표시됨
- [ ] 각 공급자 "발급받기 ↗" 탭 → 브라우저가 해당 콘솔 URL 로 열림
- [ ] 아무 공급자 탭 → 잘못된 키 입력 후 "테스트 & 저장" → "키가 올바르지 않습니다" 표시
- [ ] 올바른 키 입력 후 "테스트 & 저장" → "저장되었습니다" Snackbar + 행에 "저장됨" 뱃지
- [ ] 활성 공급자 라디오그룹에 저장된 공급자만 노출, 선택 반영
- [ ] 모델 Spinner 에 3개 모델 표시, 추천 뱃지 확인, 변경 후 영속되는지 (앱 재시작 검증)
- [ ] 한도 슬라이더 움직여 값 표시 즉시 변경되는지
- [ ] 단축키 편집 화면 → 1/3/5 선택 + 생성 → 예문 목록에 추가, 재시작 후에도 `sourceType == AUTO`
- [ ] 영문 expansion → 영문 예문만 생성, `english` 필드에 저장
- [ ] 한글 expansion → 한글 예문만 생성, `korean` 필드에 저장
- [ ] Wifi 끄고 생성 → "인터넷 연결 확인" Snackbar + 재시도 버튼 동작
- [ ] 한도 낮게 (예: 2) 설정 후 3회 연속 생성 → 3번째에 "호출 한도 도달" Snackbar
- [ ] "한도 변경" 액션 → LLM 설정 화면 이동
- [ ] 활성 공급자 삭제 → activeProvider 가 null 로 초기화, 편집 화면에서 생성 시 "공급자 설정" Snackbar
- [ ] 앱 재시작 후 저장된 키 / 활성 공급자 / 모델 / 한도 모두 유지

**Step 4: 보안 검증**

- [ ] Logcat 에 API 키 원문이 없는지 확인 (`adb logcat | grep -i "sk-"`). 마스킹된 `sk-***xxxx` 패턴만 있거나 아예 없어야 함
- [ ] `adb backup` 기능을 쓸 수 있는 환경이면 `api_keys_encrypted` 파일이 평문이 아닌지 확인

**Step 5: 문제 발견 시 각 이슈별 작은 fix 커밋**

문제 발견하면 원인 특정 → 최소 수정 → 해당 파일만 git add → 커밋 메시지 `fix(phase2): ...`.

**Step 6: 최종 태그**

모든 체크리스트 통과 시:

```bash
git tag phase2-complete
git push origin main --tags
```

---

## Self-Review 결과

### 1. Spec Coverage (누락 없는지 확인)

| Spec 항목 | Task |
|---|---|
| §1.1 5 공급자 어댑터 | Task 10, 11, 12 |
| §1.1 BYOK 암호화 저장 | Task 4 |
| §1.1 활성 공급자 + 모델 선택 | Task 19, 22 |
| §1.1 Editor 실 생성 | Task 17, 24 |
| §1.1 sourceType AUTO | Task 17 (addGeneratedExample) |
| §1.1 일일 호출 한도 | Task 5, 14 |
| §1.1 오류 맞춤 Snackbar | Task 24 |
| §1.1 부분 성공 처리 | Task 14 |
| §3.1 Model 카탈로그 | Task 2 |
| §3.2 SecureKeyStore | Task 4 |
| §3.3 LlmSettingsStore | Task 5 |
| §4.1 LlmError + LlmException + interface | Task 6 |
| §4.2 OpenAiCompatibleAdapter | Task 10 |
| §4.3 ClaudeAdapter | Task 11 |
| §4.4 GeminiAdapter | Task 12 |
| §4.5 공통 프롬프트 | Task 7 |
| §4.6 HTTP 매핑 | Task 10, 11, 12 |
| §4.7 parseExamples | Task 8 |
| §4.8 HTTP 타임아웃 / 재시도 없음 | Task 9 |
| §5.1 LlmRegistry | Task 13 |
| §5.2 Service Outcome | Task 14 |
| §5.3 Service flow | Task 14 |
| §6.1 Fragment 구조 | Task 22 |
| §6.2 LlmSettingsFragment 레이아웃 | Task 18 |
| §6.3 ApiKeyDialog | Task 21 |
| §6.4 발급 URL | Task 21 |
| §6.5 Editor 통합 + Snackbar 매핑 | Task 17, 24 |
| §6.6 접근성 | Task 18 (inputType=textVisiblePassword, endIconMode=password_toggle) |
| §6.7 네비게이션 | Task 23, 24 |
| §7.1 비용 보호 | Task 5, 14 |
| §7.2 보안 (EncryptedSharedPrefs, 로깅 억제) | Task 4, 9, 15 |
| §7.3 자동 재시도 없음 | Task 9 |
| §8.1 자동 테스트 | Task 2, 4, 5, 7, 8, 10, 11, 12, 14, 17 |
| §8.3 수동 체크리스트 | Task 25 |
| §8.4 보안 검증 | Task 25 |
| §9 의존성 | Task 1 |

모든 spec 요구사항에 대응되는 task 존재.

### 2. 타입 일관성

- `LlmAdapter.providerId: ProviderId` — Task 6 정의, Task 10/11/12 override
- `AdapterRegistry.adapterFor(ProviderId): LlmAdapter` — Task 14 정의, Task 13에서 LlmRegistry implements
- `LlmSettings.modelByProvider: Map<ProviderId, String>` — Task 5 정의, Task 19/22에서 참조
- `ExampleGenerationService.Outcome` sealed class — Task 14 정의, Task 17 VM 매핑
- `ShortcutEditorViewModel.EditorEvent` sealed class — Task 17 정의, Task 24 Fragment 매핑 (전 타입 누락 없이 커버)
- `LlmError` 9개 타입 (Network, Timeout, InvalidKey, RateLimited, ServerError, Truncated, ParseFailure, ContentFiltered, Unknown) — Task 6 정의, Task 10/11/12/17/24에서 when 매칭 완전 커버

일관성 문제 없음.

### 3. Placeholder 스캔

- "TBD" / "TODO" 텍스트 1건 (Task 24 Step 3 의 주석 `TODO(phase2.1)`) — 해당 TODO 는 Step 4 에서 즉시 해결되므로 Step 3 실행 중에만 잠시 존재. 허용.
- "implement later" / "fill in details" 없음.
- "handle edge cases" 같은 애매한 표현 없음.

### 4. 스코프 체크

Phase 2 는 LLM BYOK 예문 생성이라는 단일 subsystem. 25 Task 모두 이 한 목적에 봉사. decomposition 추가 불필요.

---

## 실행 옵션

Plan complete and saved to `docs/superpowers/plans/2026-04-19-shortcut-animator-phase2.md`. Two execution options:

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
