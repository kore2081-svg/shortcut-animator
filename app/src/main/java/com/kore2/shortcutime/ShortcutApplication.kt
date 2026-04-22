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

        // RevenueCat must be initialized before entitlementManager is first accessed
        if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, BillingConstants.REVENUECAT_API_KEY).build()
        )

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
