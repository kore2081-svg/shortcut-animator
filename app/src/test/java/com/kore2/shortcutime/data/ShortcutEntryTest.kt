package com.kore2.shortcutime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutEntryTest {

    @Test
    fun usageDisplayShowsZero() {
        val entry = entry(0)
        assertEquals("0", entry.usageDisplay)
    }

    @Test
    fun usageDisplayShowsOneDigit() {
        assertEquals("1", entry(1).usageDisplay)
        assertEquals("99", entry(99).usageDisplay)
    }

    @Test
    fun usageDisplayShowsHundredExactly() {
        assertEquals("100", entry(100).usageDisplay)
    }

    @Test
    fun usageDisplayCapsAt101() {
        assertEquals("100↑", entry(101).usageDisplay)
    }

    @Test
    fun usageDisplayCapsAtLargeNumbers() {
        assertEquals("100↑", entry(999).usageDisplay)
        assertEquals("100↑", entry(10_000).usageDisplay)
    }

    private fun entry(count: Int) = ShortcutEntry(
        shortcut = "x",
        expandsTo = "y",
        usageCount = count,
    )
}
