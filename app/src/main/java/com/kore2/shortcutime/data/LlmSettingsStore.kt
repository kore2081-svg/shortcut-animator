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
