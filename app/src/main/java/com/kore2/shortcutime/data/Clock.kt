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
