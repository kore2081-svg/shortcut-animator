package com.kore2.shortcutime.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("shortcut_store", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun savedFolderPersistsAcrossNewRepositoryInstances() {
        val repo1 = FolderRepository(context)
        val folder = FolderItem(title = "Test Folder")
        repo1.saveFolder(folder)

        val repo2 = FolderRepository(context)
        val loaded = repo2.getFolder(folder.id)

        assertNotNull("폴더가 새 repo 인스턴스에서 조회되어야 함", loaded)
        assertEquals("Test Folder", loaded!!.title)
    }

    @Test
    fun incrementUsageAddsOne() {
        val repo = FolderRepository(context)
        val folder = FolderItem(
            title = "F",
            shortcuts = listOf(ShortcutEntry(shortcut = "hi", expandsTo = "hello")),
        )
        repo.saveFolder(folder)
        val entry = folder.shortcuts.first()

        repo.incrementUsage(folder.id, entry.id)
        repo.incrementUsage(folder.id, entry.id)

        val updated = repo.getFolder(folder.id)!!.shortcuts.first()
        assertEquals(2, updated.usageCount)
    }

    @Test
    fun deleteFolderRemovesItsShortcuts() {
        val repo = FolderRepository(context)
        val folder = FolderItem(
            title = "A",
            shortcuts = listOf(ShortcutEntry(shortcut = "x", expandsTo = "xx")),
        )
        repo.saveFolder(folder)

        repo.deleteFolder(folder.id)

        assertEquals(0, repo.getAllFolders().size)
    }

    @Test
    fun findMatchingShortcutIgnoresCaseByDefault() {
        val repo = FolderRepository(context)
        val folder = FolderItem(
            title = "F",
            shortcuts = listOf(ShortcutEntry(shortcut = "HI", expandsTo = "hello")),
        )
        repo.saveFolder(folder)

        val match = repo.findMatchingShortcut("hi")

        assertNotNull(match)
        assertEquals("hello", match!!.entry.expandsTo)
    }
}
