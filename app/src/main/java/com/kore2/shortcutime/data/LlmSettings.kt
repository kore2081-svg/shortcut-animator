package com.kore2.shortcutime.data

import com.kore2.shortcutime.llm.ProviderId

data class LlmSettings(
    val activeProvider: ProviderId?,
    val modelByProvider: Map<ProviderId, String>,
    val dailyCallCap: Int,
    val todayCallCount: Int,
    val todayResetDate: String,
)
