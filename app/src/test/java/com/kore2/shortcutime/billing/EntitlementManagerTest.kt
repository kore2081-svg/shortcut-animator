package com.kore2.shortcutime.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class EntitlementManagerTest {

    private lateinit var context: Context
    private lateinit var manager: EntitlementManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
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
        val prefs = context.getSharedPreferences("entitlement_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ai_usage_month", "1999-01") // past month
            .putInt("ai_usage_count", 15)
            .commit()
        assertEquals(0, manager.getMonthlyAiUsage())
    }

    @Test
    fun `incrementMonthlyAiUsage resets count when month changes`() {
        val prefs = context.getSharedPreferences("entitlement_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ai_usage_month", "1999-01") // past month
            .putInt("ai_usage_count", 15)
            .commit()
        manager.incrementMonthlyAiUsage()
        assertEquals(1, manager.getMonthlyAiUsage())
    }

    @Test
    fun `isPro returns false by default`() {
        assertFalse(manager.isPro())
    }

    @Test
    fun `isPro returns true when is_pro pref is set`() {
        val prefs = context.getSharedPreferences("entitlement_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_pro", true).commit()
        // Create new manager instance to read updated prefs
        val newManager = EntitlementManager(context)
        assertTrue(newManager.isPro())
    }
}
