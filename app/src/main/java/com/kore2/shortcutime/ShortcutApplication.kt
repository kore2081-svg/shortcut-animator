package com.kore2.shortcutime

import android.app.Application
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.KeyboardThemeStore

class ShortcutApplication : Application() {
    val repository: FolderRepository by lazy { FolderRepository(applicationContext) }
    val themeStore: KeyboardThemeStore by lazy { KeyboardThemeStore(applicationContext) }

    companion object {
        fun from(context: android.content.Context): ShortcutApplication {
            return context.applicationContext as ShortcutApplication
        }
    }
}
