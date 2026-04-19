package com.kore2.shortcutime

import android.app.Application
import android.content.Context
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.KeyboardThemeStore

class ShortcutApplication : Application() {
    val repository: FolderRepository by lazy { FolderRepository(applicationContext) }
    val themeStore: KeyboardThemeStore by lazy { KeyboardThemeStore(applicationContext) }

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
        fun from(context: android.content.Context): ShortcutApplication {
            return context.applicationContext as ShortcutApplication
        }
    }
}
