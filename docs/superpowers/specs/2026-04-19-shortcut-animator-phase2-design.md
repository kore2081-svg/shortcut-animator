# Shortcut Animator — Phase 2 Design (LLM Example Generation, BYOK)

**Date:** 2026-04-19
**Status:** Approved for planning
**Scope:** Phase 2 only — LLM 기반 예문 생성 + BYOK(Bring Your Own Key) 다중 공급자 지원. 로그인/결제는 Phase 3 별도 스펙.
**Depends on:** Phase 1 (2026-04-19-shortcut-animator-phase1-design.md) — 데이터 모델, Fragment 네비게이션, 예문 placeholder Toast 를 Phase 2 실제 호출로 교체.

---

## 1. 목표 및 범위

### 1.1 Phase 2 에서 하는 것

- 5개 AI 공급자 API 어댑터 구현: **Anthropic Claude, Google Gemini, OpenAI, xAI Grok, DeepSeek**
- 사용자가 각 공급자에 자기 API 키를 입력(BYOK) — 암호화 저장
- 활성 공급자 1개 선택, 공급자별 기본 모델 선택(드롭다운 ~3개)
- 단축키 편집 화면의 "예문 생성" 버튼(1/3/5개)이 실제 LLM 호출
- 생성된 예문은 `sourceType = "auto"` 로 폴더 저장
- 비용 폭주 방지: 하루 호출 한도 설정 (기본 50회/일)
- 오류별 맞춤 Snackbar + 재시도 UX
- 부분 성공(요청 N개 중 M개 반환) 시 받은 것만 저장 + 안내

### 1.2 Phase 2 에서 하지 않는 것

- 로그인 (Google/GitHub OAuth) → Phase 3
- 결제 (Toss/PG/Google Play Billing) → Phase 3
- 사용자 데이터 클라우드 동기화
- 구독/유료 티어 구분
- Room DB 전환 (SharedPreferences + EncryptedSharedPreferences 유지)
- 프롬프트 A/B 테스트
- 모델 목록 원격 동기화 (Remote Config) — 사용자가 설정 화면에서 모델 선택하는 A+Y 방식으로 유지보수 부담 해소

### 1.3 성공 기준

- 사용자가 Claude/Gemini/OpenAI/Grok/DeepSeek 중 최소 1개 실 키로 예문 5개 이상 생성 성공
- 키 입력 시 테스트 호출로 유효성 즉시 확인
- 잘못된 키 / 오프라인 / 5xx / 429 / 콘텐츠 필터 각각 맞춤 메시지 + 재시도 동작
- 일일 한도 초과 시 차단 + 설정 이동 액션
- `EncryptedSharedPreferences` 로 키 저장, `adb backup` 으로 평문 노출 없음

---

## 2. 아키텍처 결정

### 2.1 접근법: OpenAI-compatible 통합 + Claude/Gemini 네이티브

- **3개 어댑터로 5개 공급자 커버:**
  - `OpenAiCompatibleAdapter` 1개 → OpenAI + Grok + DeepSeek (BaseURL/인증만 분기)
  - `ClaudeAdapter` → Anthropic Messages API 네이티브
  - `GeminiAdapter` → Google `generateContent` API 네이티브
- Grok(x.ai), DeepSeek 은 공식적으로 OpenAI Chat Completions 스펙 호환 제공
- 향후 OpenAI-compat 공급자 추가 시 BaseURL 상수만 추가

### 2.2 레이어 분리

```
UI (Fragment)
  ↓
ViewModel (EditorEvent SharedFlow)
  ↓
ExampleGenerationService  ← 오케스트레이션 (캡, 액티브 공급자, Outcome)
  ↓
LlmRegistry → LlmAdapter (인터페이스)
  ↓
HTTP (OkHttp)
```

각 레이어 단일 책임, 테스트 경계 명확.

### 2.3 기술 스택

- Kotlin, Coroutines (`viewModelScope`, `withContext(Dispatchers.IO)`)
- OkHttp 4.x (HTTP)
- kotlinx.serialization (JSON 파싱, provider 수준 일관성)
- androidx.security.crypto 1.1.0-alpha06+ (`EncryptedSharedPreferences`)
- MockWebServer (테스트)

---

## 3. 데이터 모델 및 저장

### 3.1 공급자/모델 카탈로그 (앱 내장)

```kotlin
enum class ProviderId { OPENAI, CLAUDE, GEMINI, GROK, DEEPSEEK }

data class ModelInfo(
    val id: String,          // API 에 보낼 model string
    val displayName: String, // 사용자 표시 라벨
    val isRecommended: Boolean,
)

val MODEL_CATALOG: Map<ProviderId, List<ModelInfo>> = mapOf(
    CLAUDE to listOf(
        ModelInfo("claude-haiku-4-5-20251001",  "Claude Haiku 4.5",  true),
        ModelInfo("claude-sonnet-4-6",          "Claude Sonnet 4.6", false),
        ModelInfo("claude-opus-4-7",            "Claude Opus 4.7",   false),
    ),
    OPENAI to listOf(
        ModelInfo("gpt-4o-mini", "GPT-4o Mini", true),
        ModelInfo("gpt-4o",      "GPT-4o",      false),
        ModelInfo("gpt-4.1",     "GPT-4.1",     false),
    ),
    GEMINI to listOf(
        ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", true),
        ModelInfo("gemini-2.5-pro",   "Gemini 2.5 Pro",   false),
        ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", false),
    ),
    GROK to listOf(
        ModelInfo("grok-2-mini", "Grok 2 Mini", true),
        ModelInfo("grok-2",      "Grok 2",      false),
        ModelInfo("grok-3",      "Grok 3",      false),
    ),
    DEEPSEEK to listOf(
        ModelInfo("deepseek-chat",     "DeepSeek V3", true),
        ModelInfo("deepseek-reasoner", "DeepSeek R1", false),
    ),
)
```

모델 ID/이름은 2026-04 기준. 구모델 deprecation 시 사용자가 드롭다운에서 다른 모델 선택하면 앱 업데이트 없이 계속 사용 가능. 장기적으로 앱 업데이트로 카탈로그 갱신.

### 3.2 API 키 저장소

**`SecureKeyStore`** — `EncryptedSharedPreferences`

```kotlin
class SecureKeyStore(context: Context) {
    private val prefs: SharedPreferences  // EncryptedSharedPreferences
    fun save(provider: ProviderId, key: String)
    fun get(provider: ProviderId): String?
    fun clear(provider: ProviderId)
    fun getAllSaved(): Set<ProviderId>
}
```

- 파일명: `api_keys_encrypted`
- 암호화: AES256_SIV (키), AES256_GCM (값), MasterKey AES256
- 저장된 키 없는 공급자는 `getAllSaved()` 에 포함 안 됨

### 3.3 LLM 설정 저장소

**`LlmSettingsStore`** — 일반 `SharedPreferences` (민감 정보 아님)

```kotlin
data class LlmSettings(
    val activeProvider: ProviderId?,            // null = 미설정
    val modelByProvider: Map<ProviderId, String>, // provider → model id (디폴트: 추천 모델)
    val dailyCallCap: Int,                       // 기본 50, 범위 10–500
    val todayCallCount: Int,                     // 오늘 호출 수
    val todayResetDate: String,                  // ISO 날짜 yyyy-MM-dd
)

class LlmSettingsStore(context: Context, clock: Clock) {
    fun load(): LlmSettings                     // 로드 시 날짜 경계 리셋 자동 적용
    fun setActiveProvider(provider: ProviderId?)
    fun setModel(provider: ProviderId, modelId: String)
    fun setDailyCap(cap: Int)                    // clamp 10..500
    fun incrementCallCount()                     // 날짜 경계 체크 후 +1
}
```

- 파일명: `llm_settings`
- `Clock` 주입 → 테스트에서 날짜 경계 시뮬레이션 가능

### 3.4 기존 데이터 모델 변경

`ExampleItem.sourceType` 은 Phase 1 에 이미 정의됨. Phase 2 는 LLM 생성 예문 저장 시 `ExampleSourceType.AUTO` 값 사용. 기존 수동 입력(`MANUAL`) 과 목록에서 뱃지로 구분 표시.

---

## 4. 어댑터 레이어

### 4.1 공통 타입

```kotlin
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

// Kotlin Result<T> 는 Throwable 만 담으므로 LlmError 를 감싸는 예외 타입.
class LlmException(val error: LlmError) : Exception(error.toString())

data class GenerationResult(
    val examples: List<String>,
    val requestedCount: Int,
)

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

어댑터 실패 경로는 항상 `Result.failure(LlmException(LlmError.X))`. 상위 레이어는 `exceptionOrNull() as? LlmException` 로 타입 복원.

### 4.2 `OpenAiCompatibleAdapter`

- 생성자: `baseUrl: String`, `providerId: ProviderId`
- 엔드포인트:
  - `POST {baseUrl}/v1/chat/completions` (생성)
  - `GET {baseUrl}/v1/models` (키 검증)
- 인증: `Authorization: Bearer {key}`
- 요청 body:
  ```json
  {
    "model": "{model}",
    "messages": [{"role": "user", "content": "{PROMPT}"}],
    "max_tokens": 512,
    "response_format": {"type": "json_object"}
  }
  ```
- 응답 파싱: `choices[0].message.content` → JSON 파싱 → `examples` 배열
- 에러 매핑: 섹션 4.6 참조
- BaseURL 상수:
  - `OPENAI_BASE_URL = "https://api.openai.com"`
  - `GROK_BASE_URL = "https://api.x.ai"`
  - `DEEPSEEK_BASE_URL = "https://api.deepseek.com"`

### 4.3 `ClaudeAdapter`

- 엔드포인트: `POST https://api.anthropic.com/v1/messages`
- 인증: `x-api-key: {key}` + `anthropic-version: 2023-06-01`
- 요청 body:
  ```json
  {
    "model": "{model}",
    "max_tokens": 512,
    "messages": [{"role": "user", "content": "{PROMPT}"}]
  }
  ```
- 응답 파싱: `content[0].text` → JSON 파싱 (Claude 는 `response_format` 대신 프롬프트로 JSON 강제)
- 키 검증: `max_tokens: 1` 의 ping 요청
- `stop_reason` 매핑:
  - `max_tokens` → `Truncated`
  - `refusal` → `ContentFiltered`
  - `end_turn` → 정상

### 4.4 `GeminiAdapter`

- 엔드포인트: `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}`
- 키는 쿼리 파라미터로 전달 (Gemini 표준)
- 요청 body:
  ```json
  {
    "contents": [{"parts": [{"text": "{PROMPT}"}]}],
    "generationConfig": {
      "responseMimeType": "application/json",
      "maxOutputTokens": 512
    }
  }
  ```
- 응답 파싱: `candidates[0].content.parts[0].text` → JSON 파싱
- 키 검증: `GET /v1beta/models?key={apiKey}`
- `finishReason` 매핑:
  - `MAX_TOKENS` → `Truncated`
  - `SAFETY`, `RECITATION`, `BLOCKLIST` → `ContentFiltered`
  - `STOP` → 정상
- `candidates` 빈 배열 → `ContentFiltered` (prompt-level safety block)

### 4.5 공통 프롬프트

```
You generate example sentences demonstrating a text-expansion shortcut.

Shortcut: "{shortcut}"
Expands to: "{expansion}"

Write {count} natural example sentences that naturally use the shortcut's expansion in realistic contexts.
Detect the language of the expansion automatically and write examples in THE SAME LANGUAGE.

Return ONLY a JSON object of this exact shape with no prose, no markdown:
{"examples": ["sentence 1", "sentence 2", ...]}
```

JSON 모드: 5개 공급자 모두 지원. 파싱 실패율 감소.

### 4.6 HTTP → `LlmError` 매핑

| HTTP / 예외 | `LlmError` |
|---|---|
| IOException (DNS/소켓) | `Network` |
| SocketTimeoutException, 408, 504 | `Timeout` |
| 401, 403 | `InvalidKey` |
| 404 | `Unknown("endpoint not found")` |
| 429 | `RateLimited` |
| 500, 502, 503 | `ServerError` |
| 기타 4xx | `Unknown("http {code}: {body snippet}")` |

**공급자별 body 규칙:**

- OpenAI-compat `{"error":{"type":"invalid_api_key"}}` → `InvalidKey`
- OpenAI-compat `{"error":{"type":"insufficient_quota"}}` → `InvalidKey` (사용자 관점에선 키 문제)
- OpenAI-compat `{"error":{"type":"rate_limit_exceeded"}}` → `RateLimited`
- OpenAI-compat `finish_reason=length` → `Truncated`, `content_filter` → `ContentFiltered`
- Claude `{"type":"error","error":{"type":"authentication_error"}}` → `InvalidKey`
- Claude `{"type":"error","error":{"type":"overloaded_error"}}` → `ServerError`
- Claude `{"type":"error","error":{"type":"rate_limit_error"}}` → `RateLimited`
- Gemini `400 + error.status=INVALID_ARGUMENT` (키 문제 케이스) → `InvalidKey`

### 4.7 JSON 파싱 공통 함수

```kotlin
fun parseExamples(rawText: String, requestedCount: Int): Result<GenerationResult>
```

1. `rawText.trim()` 후 markdown 코드펜스 (```json ... ```) 제거
2. JSON 파싱 시도 → `examples` 키가 `List<String>` 이면 빈/null 요소 필터 후 성공
3. 실패 시 fallback: 정규식 `"([^"]+)"` 으로 따옴표 사이 문자열 추출
4. 두 단계 모두 실패 → `ParseFailure`
5. 파싱 성공 시 반환 크기가 `requestedCount` 와 달라도 성공 (Partial 판정은 Service 층)

### 4.8 HTTP 레이어 설정

- OkHttp 타임아웃: connect 10s / read 30s / write 10s
- 전체 호출 `withTimeout(60_000)` 안전망
- 자동 재시도 **없음** — BYOK 비용 투명성 원칙
- API 키 로그 마스킹: `"sk-***{last4}"`

---

## 5. 오케스트레이션 서비스

### 5.1 `LlmRegistry`

```kotlin
object LlmRegistry {
    fun adapterFor(provider: ProviderId): LlmAdapter = when (provider) {
        OPENAI    -> OpenAiCompatibleAdapter(OPENAI_BASE_URL, OPENAI)
        GROK      -> OpenAiCompatibleAdapter(GROK_BASE_URL, GROK)
        DEEPSEEK  -> OpenAiCompatibleAdapter(DEEPSEEK_BASE_URL, DEEPSEEK)
        CLAUDE    -> ClaudeAdapter()
        GEMINI    -> GeminiAdapter()
    }
}
```

`ShortcutApplication` 에서 `by lazy` 싱글톤 노출.

### 5.2 `ExampleGenerationService`

```kotlin
class ExampleGenerationService(
    private val keyStore: SecureKeyStore,
    private val settingsStore: LlmSettingsStore,
    private val registry: LlmRegistry,
    private val clock: Clock,
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

    suspend fun generate(
        shortcut: String,
        expansion: String,
        count: Int,
    ): Outcome
}
```

### 5.3 실행 흐름

```
generate(shortcut, expansion, count):
  settings = settingsStore.load()
  if settings.activeProvider == null: return NoActiveProvider
  key = keyStore.get(settings.activeProvider)
  if key == null: return NoKey
  counterMutex.withLock {
    if settings.todayCallCount >= settings.dailyCallCap: return DailyCapExceeded
    settingsStore.incrementCallCount()  // 성공/실패 무관 +1
  }
  model = settings.modelByProvider[settings.activeProvider] ?: recommendedDefault
  result = registry.adapterFor(settings.activeProvider)
             .generateExamples(key, model, shortcut, expansion, count)
  return when:
    result.success & examples.size == count  → Success
    result.success & 0 < examples.size < count → Partial
    result.success & examples.size == 0      → Failure(ParseFailure)
    result.failure                            → Failure(error)
```

### 5.4 주요 결정

- **카운터 증가 시점:** 호출 시도 시 (성공/실패 무관). 사용자 재시도 스팸 방지.
- **날짜 경계:** `load()` 시점에 `todayResetDate != today()` 이면 카운터 0 리셋 + 날짜 갱신.
- **동시성:** `Mutex` 로 카운터 r/w 직렬화.
- **저장 책임 분리:** Service 는 예문 저장하지 않음. ViewModel → Repository 경로.

---

## 6. UI 구조

### 6.1 Fragment 구조

- 기존 `SettingsFragment` 확장: "AI 설정" 행 추가 → `LlmSettingsFragment` 로 navigate
- **새로 추가: `LlmSettingsFragment`** — Phase 2 핵심 화면
- **새로 추가: `ApiKeyDialog`** — DialogFragment, provider 행 탭 시 표시

### 6.2 `LlmSettingsFragment` 레이아웃

```
┌─ Toolbar: "AI 설정" (back)
├─ Section: API 키
│   [Claude]       [저장됨 ✓]  →
│   [OpenAI]       [미설정]
│   [Gemini]       [저장됨 ✓]  →
│   [Grok]         [미설정]
│   [DeepSeek]     [미설정]
├─ Section: 활성 공급자 (저장된 키가 있어야 선택 가능)
│   ○ Claude
│   ● Gemini
├─ Section: 모델 (활성 공급자별)
│   [Gemini 2.5 Flash (추천) ▼]
├─ Section: 하루 호출 한도
│   Slider: 10 ─────●──── 500   (기본 50)
│   "오늘 사용: 12 / 50"
```

저장된 공급자가 0개일 때: 활성 공급자 섹션에 "먼저 API 키를 저장하세요" 안내.

### 6.3 `ApiKeyDialog`

```
┌─ 제목: "{Provider} API 키"
├─ "API 키 발급받기 ↗" 링크 → 브라우저로 공급자 콘솔 이동
├─ TextInputLayout (inputType=textVisiblePassword, 눈 토글)
├─ 상태 라인: "검증 중..." / "저장됨 (2026-04-19)" / 에러 메시지
└─ 버튼: [취소]  [삭제]  [테스트 & 저장]
```

**저장 흐름:**
1. "테스트 & 저장" 탭 → 버튼 비활성 + 스피너
2. `adapter.validateKey(key)` 호출
3. 성공 → `keyStore.save()` → dismiss + Snackbar "저장되었습니다"
4. 실패 → 상태 라인에 오류 유형별 메시지 (섹션 6.5 Snackbar 표 재사용)

**삭제 흐름:**
- 확인 다이얼로그 1회 → `keyStore.clear()` → dismiss
- 삭제된 공급자가 active 였으면 `activeProvider = null` 로 초기화

### 6.4 공급자 키 발급 URL

```
OPENAI    → https://platform.openai.com/api-keys
CLAUDE    → https://console.anthropic.com/settings/keys
GEMINI    → https://aistudio.google.com/app/apikey
GROK      → https://console.x.ai/
DEEPSEEK  → https://platform.deepseek.com/api_keys
```

### 6.5 Editor 통합 및 Snackbar 매핑

`ShortcutEditorViewModel` 기존 Phase 1 placeholder Toast 제거. `onGenerateExamplesClicked(count)` 추가:

```kotlin
sealed class EditorEvent {
    object Generating : EditorEvent()
    data class GenerateSuccess(val addedCount: Int) : EditorEvent()
    data class GeneratePartial(val got: Int, val requested: Int) : EditorEvent()
    data class GenerateError(val error: LlmError) : EditorEvent()
    object NoActiveProvider : EditorEvent()
    object NoKey : EditorEvent()
    object DailyCapExceeded : EditorEvent()
}
```

Fragment 에서 `SharedFlow<EditorEvent>` 수신 후 Snackbar 표시:

| EditorEvent | 메시지 | Action |
|---|---|---|
| `Generating` | (버튼 비활성 + ProgressBar) | — |
| `NoActiveProvider` | "먼저 AI 공급자를 설정하세요" | "설정" → LlmSettingsFragment |
| `NoKey` | "활성 공급자의 API 키가 없습니다" | "설정" → LlmSettingsFragment |
| `DailyCapExceeded` | "오늘 호출 한도(N회) 도달" | "한도 변경" → LlmSettingsFragment |
| `GenerateSuccess(n)` | "예문 N개 추가됨" | — |
| `GeneratePartial(g,r)` | "{r}개 중 {g}개 생성됨" | — |
| `GenerateError(Network)` | "인터넷 연결 확인" | "재시도" |
| `GenerateError(Timeout)` | "응답 지연. 재시도" | "재시도" |
| `GenerateError(InvalidKey)` | "API 키 오류. 설정에서 확인" | "설정" |
| `GenerateError(RateLimited)` | "요청 한도 초과. 잠시 후 재시도" | "재시도" |
| `GenerateError(ServerError)` | "공급자 서버 오류. 잠시 후 재시도" | "재시도" |
| `GenerateError(ContentFiltered)` | "콘텐츠 정책에 걸림. 다른 예문 요청" | "재시도" |
| `GenerateError(ParseFailure)` | "응답 형식 오류. 재시도" | "재시도" |
| `GenerateError(Truncated)` | "응답 잘림. 재시도" | "재시도" |
| `GenerateError(Unknown(m))` | "오류: {m}" | "재시도" |

재시도 Action 탭 → 같은 `count` 로 재호출 (카운터 포함).

### 6.6 접근성

- 모든 row 에 `contentDescription`
- 키 입력 `inputType="textVisiblePassword"` (복붙 지원)
- 눈 토글 시 `announceForAccessibility`

### 6.7 네비게이션 추가

`nav_graph.xml`:
- `llmSettingsFragment` destination 신규 추가
- `settingsFragment → llmSettingsFragment` action 추가
- `llmSettingsFragment` 자체 self-loop 없음 (단일 방향)

---

## 7. 에러 처리 및 안전장치

### 7.1 비용 보호

- 기본 50회/일 호출 한도, 슬라이더로 10–500 사이 조정
- 카운터 자정 리셋 (로컬 시간대 기준 ISO `yyyy-MM-dd` 비교)
- 한도 도달 시 생성 버튼 클릭 → `DailyCapExceeded` Snackbar (어댑터 호출 안 함)

### 7.2 보안

- API 키 `EncryptedSharedPreferences` 저장
- 로그에서 키는 `sk-***{last4}` 형태로 마스킹
- 릴리스 빌드: `BuildConfig.DEBUG` 분기로 요청/응답 body 로그 억제
- 키 검증용 네트워크 호출은 최소 payload (`max_tokens: 1` 또는 `GET /models`)

### 7.3 자동 재시도 안 함

BYOK 사용자 비용 투명성을 위해 HTTP/API 레벨 자동 재시도 없음. 사용자가 Snackbar "재시도" 버튼으로 의도적 재시도만 허용.

---

## 8. 테스트 전략

### 8.1 자동 단위 테스트

- `SecureKeyStoreTest`: save/get/clear 라운드트립, 공급자별 격리
- `LlmSettingsStoreTest`: 날짜 경계 리셋, activeProvider null 복구, cap clamp (10..500)
- `ParseExamplesTest`: 정상 JSON, 크기 불일치, 빈 배열, 잘못된 JSON + 정규식 fallback, markdown 코드펜스
- `OpenAiCompatibleAdapterTest` (MockWebServer): 200/401/429/500/timeout, `finish_reason=length/content_filter`, error body `invalid_api_key`/`rate_limit_exceeded`, 3개 BaseURL 파라미터화
- `ClaudeAdapterTest`: 동일 패턴 + `stop_reason=max_tokens/refusal`, `authentication_error`/`overloaded_error`/`rate_limit_error`
- `GeminiAdapterTest`: 동일 패턴 + `finishReason=SAFETY/MAX_TOKENS`, 빈 `candidates`, `400 INVALID_ARGUMENT`
- `ExampleGenerationServiceTest` (가짜 Adapter + 가짜 Clock): `NoActiveProvider`, `NoKey`, `DailyCapExceeded` (어댑터 미호출 확인), 날짜 경계 리셋, Success/Partial/Failure 판정, 카운터 동시성 (2 병렬 호출)

### 8.2 테스트 제외

- 실 네트워크 호출 (CI 비용/flakiness)
- UI 인스트루먼트 테스트 (Phase 1 과 동일, 수동 체크리스트로 대체)
- LLM 응답 품질 평가

### 8.3 수동 테스트 체크리스트 (릴리스 전)

최소 2개 공급자 실 키로:

- [ ] 5개 provider row 모두 표시
- [ ] "발급받기" 링크가 브라우저로 콘솔 열림
- [ ] 잘못된 키 → "키가 올바르지 않습니다"
- [ ] 올바른 키 → "저장됨" Snackbar + row "저장됨 ✓"
- [ ] 삭제 후 active 였던 provider 면 activeProvider = null
- [ ] 에디터 count 선택 + 생성 → 예문 목록 추가, `sourceType == AUTO`
- [ ] 같은 단축키 연달아 생성 → 누적
- [ ] Wifi off 시 → "인터넷 연결 확인" + 재시도 동작
- [ ] 50회 한도 초과 → 차단 Snackbar + "한도 변경" 액션
- [ ] 슬라이더로 한도 변경 → 즉시 반영
- [ ] 다음날 → 카운터 0 리셋 (또는 날짜 수동 조작)
- [ ] 영어 expansion → 영어 예문 / 한글 expansion → 한글 예문
- [ ] active provider 없이 생성 → "공급자 설정" Snackbar + 네비
- [ ] 앱 재시작 후 키/설정 유지

### 8.4 보안 검증

- [ ] `adb backup` 시 `api_keys_encrypted` 평문 아님
- [ ] Logcat 에 API 키 원문 안 찍힘 (마스킹 확인)
- [ ] 릴리스 빌드 네트워크 로그 최소화 확인

---

## 9. 의존성 추가

`app/build.gradle.kts`:
```
implementation("androidx.security:security-crypto:1.1.0-alpha06")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

이미 Phase 1 에 포함된 것은 스킵.

---

## 10. 요약 체크리스트

**Phase 2 완료 시 달성되는 상태:**

- [x] 5개 공급자 API 어댑터 동작 (3개 클래스, 5개 BaseURL/키 인증 패턴)
- [x] 사용자 BYOK 입력 + 검증 + 암호화 저장
- [x] 활성 공급자 + 공급자별 모델 선택 UI
- [x] Editor 에서 예문 1/3/5 개 실 LLM 생성
- [x] `sourceType = AUTO` 로 예문 저장
- [x] 일일 호출 한도 (기본 50, 10–500 조정 가능)
- [x] 오류 유형별 Snackbar + 재시도/설정 이동 액션
- [x] 부분 성공 받은 것만 저장 + 안내
- [x] 자동 단위 테스트 + 수동 체크리스트

**Phase 3 로 넘기는 것:**

- 로그인 (Google/GitHub OAuth)
- 결제 (Google Play Billing / 외부 PG)
- 클라우드 동기화 / 구독 / 유료 티어

---

**End of Phase 2 Design.**
