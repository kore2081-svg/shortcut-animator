# Shortcut Animator Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 Activity 기반 Shortcut Animator 앱을 Single Activity + Navigation Component 구조로 전환하고, 데이터 리셋·"Starter Folder" 자동생성·Colemak 애니메이션 타이밍 버그를 고치며 usage count 뱃지 UI 를 추가한다.

**Architecture:** `HostActivity` 하나가 `NavHostFragment` 를 품고, 5 개 Fragment (FolderList / FolderDetail / FolderEditor / ShortcutEditor / Settings) 간 이동은 Jetpack Navigation safeargs 로 처리. `ShortcutApplication` 이 `FolderRepository` / `KeyboardThemeStore` 를 싱글톤으로 보유하고 Fragment/ViewModel 이 `applicationContext` 로 접근. 데이터는 기존 SharedPreferences + JSON 유지.

**Tech Stack:** Kotlin, Android SDK 35, Jetpack Navigation 2.8.x + safeargs, AndroidX Lifecycle (ViewModel + StateFlow), Coroutines, JUnit4, Robolectric (Repository 테스트).

**Spec:** `docs/superpowers/specs/2026-04-19-shortcut-animator-phase1-design.md`

---

## Deviation From Spec

스펙 §6.2 ④ 는 `UsageCountFormatter` 신설을 요구했지만, `ShortcutEntry.kt:17-18` 에 이미 `usageDisplay: String` 프로퍼티가 동일 로직(`count > 100 ? "100↑" : count.toString()`)으로 존재한다. 중복 추상화를 피하기 위해 기존 프로퍼티에 단위 테스트만 붙이고 어댑터는 `entry.usageDisplay` 를 바로 사용한다.

---

## File Structure

### 신규 생성 파일

| 경로 | 책임 |
|---|---|
| `app/src/main/java/com/kore2/shortcutime/ShortcutApplication.kt` | `Application` 하위 클래스. `FolderRepository`, `KeyboardThemeStore` 를 lazy 싱글톤으로 노출. |
| `app/src/main/java/com/kore2/shortcutime/ui/HostActivity.kt` | 유일한 Activity. `FragmentContainerView` 하나만 포함. |
| `app/src/main/java/com/kore2/shortcutime/ui/list/FolderListFragment.kt` | 폴더 목록 화면. |
| `app/src/main/java/com/kore2/shortcutime/ui/list/FolderListViewModel.kt` | 폴더 목록 상태 관리. |
| `app/src/main/java/com/kore2/shortcutime/ui/detail/FolderDetailFragment.kt` | 특정 폴더의 shortcut 리스트. |
| `app/src/main/java/com/kore2/shortcutime/ui/detail/FolderDetailViewModel.kt` | FolderDetail 상태 관리. SavedStateHandle 에서 `folderId` 수신. |
| `app/src/main/java/com/kore2/shortcutime/ui/editor/FolderEditorFragment.kt` | 폴더 생성/편집. |
| `app/src/main/java/com/kore2/shortcutime/ui/editor/FolderEditorViewModel.kt` | FolderEditor 상태. SavedStateHandle `folderId?` 수신. |
| `app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorFragment.kt` | shortcut 편집 + Colemak preview + 예문. |
| `app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorViewModel.kt` | ShortcutEditor 상태. SavedStateHandle `folderId`, `shortcutId?` 수신. |
| `app/src/main/java/com/kore2/shortcutime/ui/settings/SettingsFragment.kt` | 설정 placeholder. |
| `app/src/main/java/com/kore2/shortcutime/ui/common/NavExt.kt` | `NavController.navigateSafe(...)` 확장 함수. |
| `app/src/main/res/layout/activity_host.xml` | NavHostFragment container. |
| `app/src/main/res/layout/fragment_folder_list.xml` | `activity_main.xml` 에서 파생. |
| `app/src/main/res/layout/fragment_folder_detail.xml` | `activity_folder_detail.xml` 에서 파생. |
| `app/src/main/res/layout/fragment_folder_editor.xml` | `activity_folder_editor.xml` 에서 파생. |
| `app/src/main/res/layout/fragment_shortcut_editor.xml` | `activity_shortcut_editor.xml` 에서 파생. |
| `app/src/main/res/layout/fragment_settings.xml` | `activity_settings.xml` 에서 파생. |
| `app/src/main/res/navigation/nav_graph.xml` | 5 개 destination + action. |
| `app/src/test/java/com/kore2/shortcutime/data/FolderRepositoryTest.kt` | Robolectric: round-trip, incrementUsage, migrate. |
| `app/src/test/java/com/kore2/shortcutime/data/ShortcutEntryTest.kt` | usageDisplay 경계 테스트. |

### 수정 파일

| 경로 | 변경 내용 |
|---|---|
| `app/build.gradle.kts` | Navigation / safeargs / ViewModel / coroutines deps + safeargs plugin 적용, testImplementation 추가. |
| `build.gradle.kts` (root) | Navigation safeargs classpath. |
| `app/src/main/AndroidManifest.xml` | application:name 지정, HostActivity 로 LAUNCHER 교체, 기존 5 Activity 삭제. |
| `app/src/main/java/com/kore2/shortcutime/data/FolderRepository.kt` | `resetDataForCurrentBuildIfNeeded` 제거, 관련 상수 삭제. |
| `app/src/main/java/com/kore2/shortcutime/ime/ShortcutInputMethodService.kt:616` | `MainActivity::class.java` → `HostActivity::class.java`. |
| `app/src/main/java/com/kore2/shortcutime/ime/ColemakPreviewView.kt` | `playSequence` 타이밍 재작성 (KEY_LIT_MS / KEY_GAP_MS / FINAL_PAUSE_MS). |
| `app/src/main/java/com/kore2/shortcutime/ui/FolderAdapter.kt` | ListAdapter + DiffUtil 전환, theme store 캐싱. |
| `app/src/main/java/com/kore2/shortcutime/ui/ShortcutEntryAdapter.kt` | ListAdapter + DiffUtil, usage 뱃지 바인딩. |
| `app/src/main/java/com/kore2/shortcutime/ui/ShortcutAdapter.kt` | ListAdapter + DiffUtil, usage 뱃지 바인딩. |
| `app/src/main/res/layout/item_shortcut_entry.xml` | 우측 openArrowText 왼쪽에 usage 뱃지 TextView 추가. |
| `app/src/main/res/layout/item_shortcut.xml` | editShortcutButton 왼쪽에 usage 뱃지 TextView 추가. |
| `app/src/main/res/values/strings.xml` | 새 문자열: `phase2_feature_not_ready`, `folder_not_found`, `usage_badge_format`, 편집 제목 "Shortcut Animator" 확인. |

### 삭제 파일

| 경로 | 비고 |
|---|---|
| `app/src/main/java/com/kore2/shortcutime/ui/MainActivity.kt` | FolderListFragment 로 대체. |
| `app/src/main/java/com/kore2/shortcutime/ui/FolderDetailActivity.kt` | Fragment 로 대체. |
| `app/src/main/java/com/kore2/shortcutime/ui/FolderEditorActivity.kt` | Fragment 로 대체. |
| `app/src/main/java/com/kore2/shortcutime/ui/ShortcutEditorActivity.kt` | Fragment 로 대체. |
| `app/src/main/java/com/kore2/shortcutime/ui/SettingsActivity.kt` | Fragment 로 대체. |
| `app/src/main/res/layout/activity_main.xml` | fragment_folder_list.xml 로 대체. |
| `app/src/main/res/layout/activity_folder_detail.xml` | fragment_folder_detail.xml 로 대체. |
| `app/src/main/res/layout/activity_folder_editor.xml` | fragment_folder_editor.xml 로 대체. |
| `app/src/main/res/layout/activity_shortcut_editor.xml` | fragment_shortcut_editor.xml 로 대체. |
| `app/src/main/res/layout/activity_settings.xml` | fragment_settings.xml 로 대체. |

---

## Phase 1-a: Navigation 스켈레톤 + 폴더 흐름

### Task 1: Gradle 의존성 + safeargs 플러그인

**Files:**
- Modify: `build.gradle.kts` (프로젝트 루트)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 프로젝트 루트 `build.gradle.kts` 에 safeargs classpath 추가**

파일이 없으면 생성. 이미 있다면 buildscript 블록에 추가:

```kotlin
buildscript {
    dependencies {
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.8.4")
    }
}
```

- [ ] **Step 2: `app/build.gradle.kts` 의 `plugins` 블록에 safeargs 추가**

`app/build.gradle.kts:1-4` 블록을 교체:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
}
```

- [ ] **Step 3: `app/build.gradle.kts` 의 `dependencies` 블록에 추가**

`app/build.gradle.kts:44-50` 를 다음으로 교체:

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.4")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.4")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

또한 `android { }` 블록에 `testOptions` 를 추가 (블록 내부, buildFeatures 바로 아래):

```kotlin
testOptions {
    unitTests {
        isIncludeAndroidResources = true
    }
}
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts app/build.gradle.kts
git commit -m "feat: add navigation + safeargs + test deps"
```

---

### Task 2: ShortcutApplication 싱글톤 생성

**Files:**
- Create: `app/src/main/java/com/kore2/shortcutime/ShortcutApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml:3`

- [ ] **Step 1: `ShortcutApplication.kt` 생성**

```kotlin
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
```

- [ ] **Step 2: Manifest 에 `android:name` 등록**

`app/src/main/AndroidManifest.xml:3-10` 의 `<application>` 여는 태그에 `android:name=".ShortcutApplication"` 추가:

```xml
<application
    android:name=".ShortcutApplication"
    android:allowBackup="true"
    android:icon="@android:drawable/sym_def_app_icon"
    android:label="@string/app_name"
    android:roundIcon="@android:drawable/sym_def_app_icon"
    android:supportsRtl="true"
    android:theme="@style/Theme.ShortcutIme">
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/kore2/shortcutime/ShortcutApplication.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add ShortcutApplication singleton holder"
```

---

### Task 3: Repository reset 버그 수정 + 회귀 테스트

**Files:**
- Create: `app/src/test/java/com/kore2/shortcutime/data/FolderRepositoryTest.kt`
- Modify: `app/src/main/java/com/kore2/shortcutime/data/FolderRepository.kt`

- [ ] **Step 1: 실패 테스트 작성**

`app/src/test/java/com/kore2/shortcutime/data/FolderRepositoryTest.kt` 생성:

```kotlin
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
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.data.FolderRepositoryTest.savedFolderPersistsAcrossNewRepositoryInstances"`
Expected: FAIL — `savedFolderPersistsAcrossNewRepositoryInstances` 가 실패 (두 번째 repo 인스턴스 init 에서 `resetDataForCurrentBuildIfNeeded` 가 데이터를 지움)

- [ ] **Step 3: reset 로직 제거**

`app/src/main/java/com/kore2/shortcutime/data/FolderRepository.kt:11-13` 의 init 블록을 제거:

변경 전:
```kotlin
    init {
        resetDataForCurrentBuildIfNeeded()
    }
```

변경 후: (init 블록 전체 삭제)

- [ ] **Step 4: 관련 private 메서드와 상수 삭제**

`FolderRepository.kt:280-288` 의 `resetDataForCurrentBuildIfNeeded()` 메서드 전체를 삭제. 또한 `FolderRepository.kt:273-278` 의 companion object 내부 `KEY_RESET_VERSION` 과 `CURRENT_RESET_VERSION` 상수를 삭제.

companion object 는 다음과 같이 단순화:

```kotlin
    companion object {
        private const val KEY_FOLDERS = "folders"
        private const val KEY_SHORTCUTS = "shortcuts"
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.data.FolderRepositoryTest"`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kore2/shortcutime/data/FolderRepository.kt app/src/test/java/com/kore2/shortcutime/data/FolderRepositoryTest.kt
git commit -m "fix: remove build-triggered data reset; add repository tests"
```

---

### Task 4: HostActivity + activity_host.xml

**Files:**
- Create: `app/src/main/res/layout/activity_host.xml`
- Create: `app/src/main/java/com/kore2/shortcutime/ui/HostActivity.kt`

- [ ] **Step 1: activity_host.xml 생성**

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.fragment.app.FragmentContainerView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/navHostFragment"
    android:name="androidx.navigation.fragment.NavHostFragment"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:defaultNavHost="true"
    app:navGraph="@navigation/nav_graph" />
```

- [ ] **Step 2: HostActivity.kt 생성**

```kotlin
package com.kore2.shortcutime.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kore2.shortcutime.databinding.ActivityHostBinding

class HostActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHostBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHostBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
```

- [ ] **Step 3: Commit (빌드는 nav_graph 생성 후 Task 5 에서 검증)**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/HostActivity.kt app/src/main/res/layout/activity_host.xml
git commit -m "feat: add HostActivity shell"
```

---

### Task 5: Navigation graph (1-a 버전, Activity destination 공존)

**Files:**
- Create: `app/src/main/res/navigation/nav_graph.xml`

- [ ] **Step 1: nav_graph.xml 생성**

Phase 1-a 에서는 folderListFragment / folderDetailFragment 만 `<fragment>` 로 두고, 나머지는 `<activity>` 로 임시 연결. 1-b 에서 모두 `<fragment>` 로 교체.

```xml
<?xml version="1.0" encoding="utf-8"?>
<navigation xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/nav_graph"
    app:startDestination="@id/folderListFragment">

    <fragment
        android:id="@+id/folderListFragment"
        android:name="com.kore2.shortcutime.ui.list.FolderListFragment"
        android:label="FolderList"
        tools:layout="@layout/fragment_folder_list"
        xmlns:tools="http://schemas.android.com/tools">
        <action
            android:id="@+id/action_folderList_to_folderDetail"
            app:destination="@id/folderDetailFragment" />
        <action
            android:id="@+id/action_folderList_to_folderEditor"
            app:destination="@id/folderEditorActivity" />
        <action
            android:id="@+id/action_folderList_to_settings"
            app:destination="@id/settingsActivity" />
    </fragment>

    <fragment
        android:id="@+id/folderDetailFragment"
        android:name="com.kore2.shortcutime.ui.detail.FolderDetailFragment"
        android:label="FolderDetail"
        tools:layout="@layout/fragment_folder_detail"
        xmlns:tools="http://schemas.android.com/tools">
        <argument
            android:name="folderId"
            app:argType="string" />
        <action
            android:id="@+id/action_folderDetail_to_shortcutEditor"
            app:destination="@id/shortcutEditorActivity" />
        <action
            android:id="@+id/action_folderDetail_to_folderEditor"
            app:destination="@id/folderEditorActivity" />
    </fragment>

    <activity
        android:id="@+id/folderEditorActivity"
        android:name="com.kore2.shortcutime.ui.FolderEditorActivity"
        android:label="FolderEditor">
        <argument
            android:name="folderId"
            app:argType="string"
            app:nullable="true"
            android:defaultValue="@null" />
    </activity>

    <activity
        android:id="@+id/shortcutEditorActivity"
        android:name="com.kore2.shortcutime.ui.ShortcutEditorActivity"
        android:label="ShortcutEditor">
        <argument
            android:name="folderId"
            app:argType="string" />
        <argument
            android:name="shortcutId"
            app:argType="string"
            app:nullable="true"
            android:defaultValue="@null" />
    </activity>

    <activity
        android:id="@+id/settingsActivity"
        android:name="com.kore2.shortcutime.ui.SettingsActivity"
        android:label="Settings" />
</navigation>
```

- [ ] **Step 2: 빌드 확인 (safeargs 가 Direction 클래스를 생성해야 함)**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. 이 시점에서 FolderListFragment / FolderDetailFragment 는 아직 없으므로 app 은 실행 불가하지만 빌드는 통과해야 한다 (safeargs 는 메타데이터만 읽음).

만약 `Cannot resolve class` 에러가 나오면 그건 다음 task 들이 아직 안 만들어졌기 때문이며, 이번 task 에서는 nav_graph 의 XML 파싱만 통과하면 OK — 해당 fragment 클래스는 nav_graph 에 경로만 명시되어 있고 곧 생성할 예정. 만약 이 단계에서 빌드가 실패한다면 클래스 이름 오타/경로 확인.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/navigation/nav_graph.xml
git commit -m "feat: add navigation graph with mixed fragment/activity destinations"
```

---

### Task 6: FolderListFragment + ViewModel + Layout

**Files:**
- Create: `app/src/main/res/layout/fragment_folder_list.xml`
- Create: `app/src/main/java/com/kore2/shortcutime/ui/list/FolderListViewModel.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/ui/list/FolderListFragment.kt`

- [ ] **Step 1: fragment_folder_list.xml 생성**

기존 `activity_main.xml` 에서 `openAnimatorScreenButton` (백도어) 을 제거하고 나머지 구조 유지. root 를 CoordinatorLayout 으로 유지 (FAB 레이어 이유).

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_app">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/topToolbar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:backgroundTint="@color/bg_panel"
            android:title="@string/title_folder_list"
            android:titleTextColor="@color/text_primary" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/folderRecyclerView"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:clipToPadding="false"
            android:padding="16dp" />

        <TextView
            android:id="@+id/emptyStateText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:padding="24dp"
            android:text="@string/empty_folders"
            android:textColor="@color/text_secondary"
            android:visibility="gone" />

        <LinearLayout
            android:id="@+id/bottomTabBar"
            android:layout_width="match_parent"
            android:layout_height="64dp"
            android:background="@color/bg_panel"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingHorizontal="12dp">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/foldersButton"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginEnd="8dp"
                android:layout_weight="1"
                android:text="@string/tab_folders" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/settingsButton"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="@string/tab_settings" />
        </LinearLayout>
    </LinearLayout>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/addFolderFab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="20dp"
        android:contentDescription="@string/action_add_folder"
        android:src="@android:drawable/ic_input_add" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: FolderListViewModel.kt 생성**

```kotlin
package com.kore2.shortcutime.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderListViewModel(
    private val repository: FolderRepository,
) : ViewModel() {

    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _folders.value = repository.getAllFolders()
        }
    }

    fun deleteFolder(id: String) {
        repository.deleteFolder(id)
        refresh()
    }

    class Factory(private val app: ShortcutApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FolderListViewModel(app.repository) as T
        }
    }
}
```

- [ ] **Step 3: FolderListFragment.kt 생성**

```kotlin
package com.kore2.shortcutime.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.databinding.FragmentFolderListBinding
import com.kore2.shortcutime.ui.FolderAdapter
import com.kore2.shortcutime.ui.applyFabTheme
import com.kore2.shortcutime.ui.applyFilledButtonTheme
import com.kore2.shortcutime.ui.applyToolbarTheme
import kotlinx.coroutines.launch

class FolderListFragment : Fragment() {
    private var _binding: FragmentFolderListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FolderListViewModel by viewModels {
        FolderListViewModel.Factory(ShortcutApplication.from(requireContext()))
    }

    private lateinit var adapter: FolderAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFolderListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FolderAdapter(
            onFolderClick = { openFolderDetail(it) },
            onFolderEdit = { openFolderEditor(it.id) },
            onFolderDelete = { confirmDelete(it) },
        )
        binding.folderRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.folderRecyclerView.adapter = adapter

        binding.addFolderFab.setOnClickListener { openFolderEditor(null) }
        binding.settingsButton.setOnClickListener { openSettings() }
        binding.foldersButton.setOnClickListener { /* already here */ }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.folders.collect { list ->
                    adapter.submitList(list)
                    val empty = list.isEmpty()
                    binding.emptyStateText.visibility = if (empty) View.VISIBLE else View.GONE
                    if (empty) {
                        binding.emptyStateText.text = getString(R.string.empty_folders)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.bottomTabBar.setBackgroundColor(theme.keyboardBackground)
        applyFilledButtonTheme(binding.foldersButton, theme)
        applyFilledButtonTheme(binding.settingsButton, theme)
        binding.emptyStateText.setTextColor(theme.textSecondary)
        applyFabTheme(binding.addFolderFab, theme)
    }

    private fun openFolderDetail(folder: FolderItem) {
        val action = FolderListFragmentDirections
            .actionFolderListToFolderDetail(folder.id)
        findNavController().navigate(action)
    }

    private fun openFolderEditor(folderId: String?) {
        val action = FolderListFragmentDirections
            .actionFolderListToFolderEditor(folderId)
        findNavController().navigate(action)
    }

    private fun openSettings() {
        findNavController().navigate(R.id.action_folderList_to_settings)
    }

    private fun confirmDelete(folder: FolderItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_delete_folder_title))
            .setMessage(getString(R.string.dialog_delete_folder_message, folder.title))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteFolder(folder.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
```

주의: `FolderEditorActivity` 가 folderId 를 String 으로 받는 상황에서, nav_graph 의 `folderEditorActivity` destination argument 가 nullable string 이므로 safeargs 가 `actionFolderListToFolderEditor(folderId: String?)` 를 생성한다.

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/list/ app/src/main/res/layout/fragment_folder_list.xml
git commit -m "feat: add FolderListFragment + ViewModel"
```

---

### Task 7: FolderDetailFragment + ViewModel + Layout

**Files:**
- Create: `app/src/main/res/layout/fragment_folder_detail.xml`
- Create: `app/src/main/java/com/kore2/shortcutime/ui/detail/FolderDetailViewModel.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/ui/detail/FolderDetailFragment.kt`

- [ ] **Step 1: fragment_folder_detail.xml 생성**

`activity_folder_detail.xml` 과 동일한 구조:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_app">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/topToolbar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:backgroundTint="@color/bg_panel"
            android:titleTextColor="@color/text_primary" />

        <TextView
            android:id="@+id/folderHeaderText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingStart="20dp"
            android:paddingTop="14dp"
            android:paddingEnd="20dp"
            android:paddingBottom="6dp"
            android:textColor="@color/text_secondary"
            android:textSize="14sp" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/shortcutRecyclerView"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:clipToPadding="false"
            android:nestedScrollingEnabled="false"
            android:padding="16dp" />

        <TextView
            android:id="@+id/emptyStateText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:padding="24dp"
            android:text="@string/empty_shortcut_entries"
            android:textColor="@color/text_secondary"
            android:visibility="gone" />
    </LinearLayout>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/addShortcutFab"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="20dp"
        android:contentDescription="@string/action_add_shortcut_entry"
        android:src="@android:drawable/ic_input_add" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: FolderDetailViewModel.kt 생성**

```kotlin
package com.kore2.shortcutime.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.createSavedStateHandle
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderDetailViewModel(
    private val repository: FolderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val folderId: String = requireNotNull(savedStateHandle["folderId"]) {
        "folderId argument is required"
    }

    private val _state = MutableStateFlow<FolderDetailState>(FolderDetailState.Loading)
    val state: StateFlow<FolderDetailState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val folder = repository.getFolder(folderId)
            _state.value = if (folder == null) {
                FolderDetailState.NotFound
            } else {
                FolderDetailState.Loaded(folder)
            }
        }
    }

    fun deleteShortcut(shortcutId: String) {
        repository.deleteShortcut(folderId, shortcutId)
        refresh()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ShortcutApplication
                FolderDetailViewModel(app.repository, createSavedStateHandle())
            }
        }

        private val APPLICATION_KEY = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }
}

sealed class FolderDetailState {
    data object Loading : FolderDetailState()
    data object NotFound : FolderDetailState()
    data class Loaded(val folder: FolderItem) : FolderDetailState()
}
```

- [ ] **Step 3: FolderDetailFragment.kt 생성**

```kotlin
package com.kore2.shortcutime.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.FragmentFolderDetailBinding
import com.kore2.shortcutime.ui.ShortcutEntryAdapter
import com.kore2.shortcutime.ui.applyFabTheme
import com.kore2.shortcutime.ui.applyToolbarTheme
import kotlinx.coroutines.launch

class FolderDetailFragment : Fragment() {
    private var _binding: FragmentFolderDetailBinding? = null
    private val binding get() = _binding!!

    private val args: FolderDetailFragmentArgs by navArgs()

    private val viewModel: FolderDetailViewModel by viewModels {
        FolderDetailViewModel.Factory
    }

    private lateinit var adapter: ShortcutEntryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFolderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ShortcutEntryAdapter(
            onShortcutClick = { openShortcutEditor(it.id) },
            onShortcutDelete = { confirmDelete(it) },
        )
        binding.shortcutRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.shortcutRecyclerView.adapter = adapter

        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.addShortcutFab.setOnClickListener { openShortcutEditor(null) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is FolderDetailState.Loaded -> renderFolder(state)
                        FolderDetailState.NotFound -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.folder_not_found,
                                Toast.LENGTH_SHORT,
                            ).show()
                            findNavController().popBackStack()
                        }
                        FolderDetailState.Loading -> Unit
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderFolder(state: FolderDetailState.Loaded) {
        val folder = state.folder
        binding.topToolbar.title = folder.title
        binding.folderHeaderText.text =
            getString(R.string.folder_header_format, folder.title, folder.shortcuts.size)
        adapter.submitList(folder.shortcuts)
        binding.emptyStateText.visibility =
            if (folder.shortcuts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.folderHeaderText.setTextColor(theme.textSecondary)
        binding.emptyStateText.setTextColor(theme.textSecondary)
        applyFabTheme(binding.addShortcutFab, theme)
    }

    private fun openShortcutEditor(shortcutId: String?) {
        val action = FolderDetailFragmentDirections
            .actionFolderDetailToShortcutEditor(args.folderId, shortcutId)
        findNavController().navigate(action)
    }

    private fun confirmDelete(shortcut: ShortcutEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_delete_shortcut_title))
            .setMessage(getString(R.string.dialog_delete_shortcut_message, shortcut.shortcut))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteShortcut(shortcut.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
```

- [ ] **Step 4: strings.xml 에 `folder_not_found` 추가**

`app/src/main/res/values/strings.xml` 의 `</resources>` 바로 위에 추가:

```xml
<string name="folder_not_found">폴더를 찾을 수 없습니다.</string>
```

- [ ] **Step 5: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kore2/shortcutime/ui/detail/ app/src/main/res/layout/fragment_folder_detail.xml app/src/main/res/values/strings.xml
git commit -m "feat: add FolderDetailFragment + ViewModel"
```

---

### Task 8: IME 진입점을 HostActivity 로 전환 + Manifest launcher 교체

**Files:**
- Modify: `app/src/main/java/com/kore2/shortcutime/ime/ShortcutInputMethodService.kt:616`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: ShortcutInputMethodService 의 진입점 변경**

`ShortcutInputMethodService.kt:615-620` 의 `openShortcutApp()` 을 다음으로 교체:

변경 전:
```kotlin
    private fun openShortcutApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
```

변경 후:
```kotlin
    private fun openShortcutApp() {
        val intent = Intent(this, com.kore2.shortcutime.ui.HostActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }
```

그리고 파일 상단 import 에서 `import com.kore2.shortcutime.ui.MainActivity` 가 있다면 제거.

- [ ] **Step 2: Manifest 에 HostActivity 선언 추가 + MainActivity LAUNCHER 교체**

`app/src/main/AndroidManifest.xml` 의 MainActivity 블록 전체 (라인 12-19) 를 다음으로 교체:

```xml
        <activity
            android:name=".ui.HostActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
```

이 시점에서 기존 FolderDetailActivity / FolderEditorActivity / ShortcutEditorActivity / SettingsActivity 선언은 그대로 유지 (Phase 1-b 에서 제거).

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/kore2/shortcutime/ime/ShortcutInputMethodService.kt app/src/main/AndroidManifest.xml
git commit -m "feat: route IME + LAUNCHER to HostActivity"
```

---

### Task 9: MainActivity.kt 와 activity_main.xml 제거 + 수동 검증

**Files:**
- Delete: `app/src/main/java/com/kore2/shortcutime/ui/MainActivity.kt`
- Delete: `app/src/main/res/layout/activity_main.xml`

- [ ] **Step 1: MainActivity 파일 삭제**

```bash
rm "app/src/main/java/com/kore2/shortcutime/ui/MainActivity.kt"
rm "app/src/main/res/layout/activity_main.xml"
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. 만약 다른 곳에서 MainActivity 참조가 남아 있다면 컴파일 에러 — grep 으로 찾아 제거.

```bash
# sanity check
grep -r "MainActivity" app/src/main --include="*.kt" --include="*.xml"
```
Expected: 출력 없음

- [ ] **Step 3: 디바이스/에뮬레이터에서 수동 검증**

Run: `./gradlew installDebug`
그 후 디바이스에서:
- [ ] 앱 실행 → 폴더 목록 화면이 뜸
- [ ] FAB (+) → 기존 FolderEditorActivity 가 뜸 (아직 Activity 로 공존 중, 정상)
- [ ] 폴더 저장 후 돌아오기 → 목록에 반영
- [ ] 폴더 클릭 → FolderDetailFragment 가 뜨고, 다시 뒤로 → 목록
- [ ] 폴더 내부 shortcut 추가 FAB → 기존 ShortcutEditorActivity 가 뜸 (공존)
- [ ] Shortcut 저장 → FolderDetail 로 돌아와 목록에 반영
- [ ] 앱 킬 후 다시 실행 → 데이터 유지 (reset 버그 수정 검증)
- [ ] IME 활성화 후 다른 앱에서 키보드 → `+` 버튼 → HostActivity 가 뜸

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: remove MainActivity (replaced by FolderListFragment via nav graph)"
```

**Phase 1-a 완료.**

---

## Phase 1-b: 남은 전환 + 애니메이션 + Usage Badge + 마감

### Task 10: FolderEditorFragment + ViewModel + Layout

**Files:**
- Create: `app/src/main/res/layout/fragment_folder_editor.xml`
- Create: `app/src/main/java/com/kore2/shortcutime/ui/editor/FolderEditorViewModel.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/ui/editor/FolderEditorFragment.kt`
- Modify: `app/src/main/res/navigation/nav_graph.xml` (1-a 에서 `<activity>` 로 둔 것을 `<fragment>` 로 교체)

- [ ] **Step 1: fragment_folder_editor.xml 생성**

`activity_folder_editor.xml` 의 내부 레이아웃을 복제 (root 는 기존 구조 유지). 기존 파일을 Read 로 보고 동일 내용으로 `fragment_folder_editor.xml` 에 복사 저장.

(본 단계에서는 기존 `activity_folder_editor.xml` 의 내용과 **동일한 element 트리** 를 `fragment_folder_editor.xml` 에 그대로 옮긴다. Layout ID 는 유지.)

- [ ] **Step 2: FolderEditorViewModel.kt 생성**

```kotlin
package com.kore2.shortcutime.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.data.FolderRepository

class FolderEditorViewModel(
    private val repository: FolderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val folderId: String? = savedStateHandle["folderId"]

    val existing: FolderItem? = folderId?.let { repository.getFolder(it) }

    fun save(title: String, note: String): Boolean {
        if (title.isBlank()) return false
        val folder = existing?.copy(
            title = title,
            note = note,
            updatedAt = System.currentTimeMillis(),
        ) ?: FolderItem(title = title, note = note)
        repository.saveFolder(folder)
        return true
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ShortcutApplication
                FolderEditorViewModel(app.repository, createSavedStateHandle())
            }
        }

        private val APPLICATION_KEY = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }
}
```

- [ ] **Step 3: FolderEditorFragment.kt 생성**

```kotlin
package com.kore2.shortcutime.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.databinding.FragmentFolderEditorBinding
import com.kore2.shortcutime.ui.applyFilledButtonTheme
import com.kore2.shortcutime.ui.applyInputLayoutTheme
import com.kore2.shortcutime.ui.applyToolbarTheme

class FolderEditorFragment : Fragment() {
    private var _binding: FragmentFolderEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FolderEditorViewModel by viewModels { FolderEditorViewModel.Factory }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFolderEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        viewModel.existing?.let {
            binding.topToolbar.title = getString(R.string.title_edit_folder)
            binding.titleInput.setText(it.title)
            binding.noteInput.setText(it.note)
        } ?: run {
            binding.topToolbar.title = getString(R.string.title_add_folder)
        }

        binding.saveButton.setOnClickListener { onSaveClick() }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        applyInputLayoutTheme(binding.titleInputLayout, binding.titleInput, theme)
        applyInputLayoutTheme(binding.noteInputLayout, binding.noteInput, theme)
        applyFilledButtonTheme(binding.saveButton, theme)
    }

    private fun onSaveClick() {
        val title = binding.titleInput.text?.toString().orEmpty().trim()
        val note = binding.noteInput.text?.toString().orEmpty().trim()
        if (!viewModel.save(title, note)) {
            binding.titleInputLayout.error = getString(R.string.error_folder_title_required)
            return
        }
        binding.titleInputLayout.error = null
        findNavController().popBackStack()
    }
}
```

- [ ] **Step 4: nav_graph 에서 `folderEditorActivity` 를 `folderEditorFragment` 로 교체**

`app/src/main/res/navigation/nav_graph.xml` 에서 다음 `<activity>` 블록:

```xml
    <activity
        android:id="@+id/folderEditorActivity"
        android:name="com.kore2.shortcutime.ui.FolderEditorActivity"
        android:label="FolderEditor">
        ...
    </activity>
```

을 다음으로 교체:

```xml
    <fragment
        android:id="@+id/folderEditorFragment"
        android:name="com.kore2.shortcutime.ui.editor.FolderEditorFragment"
        android:label="FolderEditor"
        tools:layout="@layout/fragment_folder_editor">
        <argument
            android:name="folderId"
            app:argType="string"
            app:nullable="true"
            android:defaultValue="@null" />
    </fragment>
```

그리고 기존 `folderListFragment` 와 `folderDetailFragment` 내의 action 에서 `app:destination="@id/folderEditorActivity"` 를 `app:destination="@id/folderEditorFragment"` 로 바꾼다. action id (`action_folderList_to_folderEditor`, `action_folderDetail_to_folderEditor`) 는 유지.

- [ ] **Step 5: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

safeargs 가 재생성되어 `FolderListFragmentDirections.actionFolderListToFolderEditor(folderId)` 는 동일 시그니처 유지.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: convert FolderEditorActivity to FolderEditorFragment"
```

---

### Task 11: ShortcutEditorFragment + ViewModel (bug fixes 포함)

**Files:**
- Create: `app/src/main/res/layout/fragment_shortcut_editor.xml`
- Create: `app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorViewModel.kt`
- Create: `app/src/main/java/com/kore2/shortcutime/ui/editor/ShortcutEditorFragment.kt`
- Modify: `app/src/main/res/navigation/nav_graph.xml`
- Modify: `app/src/main/res/values/strings.xml`

이 Task 가 가장 큼. 버그 3 개 동시 수정:
1. `ensureTargetFolderId` 제거 (Starter Folder 자동 생성 금지)
2. `updatePreview` debounce (800ms)
3. `generateExamples` fake 제거 (Phase 2 Toast)

- [ ] **Step 1: fragment_shortcut_editor.xml 생성**

기존 `activity_shortcut_editor.xml` 의 element 트리를 그대로 복사. 단, 상단 toolbar 제목 기본값을 "Shortcut Animator" 로 (이미지 목업 대응):

기존 `android:title="@string/title_add_shortcut_entry"` 를 `android:title="@string/title_folder_list"` 로 유지 (값 자체는 "Shortcut Animator"). Fragment 내 코드에서 편집/신규에 따라 overwrite.

- [ ] **Step 2: strings.xml 에 문자열 추가**

`app/src/main/res/values/strings.xml` 의 `</resources>` 바로 위에 추가:

```xml
<string name="phase2_feature_not_ready">예문 자동 생성은 다음 업데이트에서 지원돼요.</string>
<string name="usage_badge_format">사용 %1$s</string>
```

- [ ] **Step 3: ShortcutEditorViewModel.kt 생성**

```kotlin
package com.kore2.shortcutime.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.ExampleSourceType
import com.kore2.shortcutime.data.FolderRepository
import com.kore2.shortcutime.data.ShortcutEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShortcutEditorViewModel(
    private val repository: FolderRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val folderId: String = requireNotNull(savedStateHandle["folderId"]) {
        "folderId argument is required"
    }
    val shortcutId: String? = savedStateHandle["shortcutId"]

    private val _entry = MutableStateFlow<ShortcutEntry?>(null)
    val entry: StateFlow<ShortcutEntry?> = _entry.asStateFlow()

    private val _savedShortcuts = MutableStateFlow<List<ShortcutEntry>>(emptyList())
    val savedShortcuts: StateFlow<List<ShortcutEntry>> = _savedShortcuts.asStateFlow()

    private val _workingExamples = MutableStateFlow<List<ExampleItem>>(emptyList())
    val workingExamples: StateFlow<List<ExampleItem>> = _workingExamples.asStateFlow()

    private val _folderMissing = MutableStateFlow(false)
    val folderMissing: StateFlow<Boolean> = _folderMissing.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val folder = repository.getFolder(folderId)
            if (folder == null) {
                _folderMissing.value = true
                return@launch
            }
            _savedShortcuts.value = folder.shortcuts
            val current = shortcutId?.let { id -> folder.shortcuts.firstOrNull { it.id == id } }
            _entry.value = current
            _workingExamples.value = current?.examples.orEmpty()
        }
    }

    fun refreshSavedShortcuts() {
        viewModelScope.launch {
            val folder = repository.getFolder(folderId) ?: return@launch
            _savedShortcuts.value = folder.shortcuts
        }
    }

    fun addOrUpdateExample(example: ExampleItem) {
        val list = _workingExamples.value.toMutableList()
        val index = list.indexOfFirst { it.id == example.id }
        if (index >= 0) list[index] = example else list.add(example)
        _workingExamples.value = list
    }

    fun deleteExample(id: String) {
        _workingExamples.value = _workingExamples.value.filterNot { it.id == id }
    }

    fun save(
        shortcut: String,
        expandsTo: String,
        note: String,
        caseSensitive: Boolean,
        backspaceToUndo: Boolean,
    ): SaveResult {
        if (shortcut.isBlank()) return SaveResult.MissingShortcut
        if (expandsTo.isBlank()) return SaveResult.MissingExpandsTo

        val current = _entry.value
        val updated = if (current == null) {
            ShortcutEntry(
                shortcut = shortcut,
                expandsTo = expandsTo,
                examples = _workingExamples.value,
                note = note,
                caseSensitive = caseSensitive,
                backspaceToUndo = backspaceToUndo,
            )
        } else {
            current.copy(
                shortcut = shortcut,
                expandsTo = expandsTo,
                examples = _workingExamples.value,
                note = note,
                caseSensitive = caseSensitive,
                backspaceToUndo = backspaceToUndo,
                updatedAt = System.currentTimeMillis(),
            )
        }

        if (current == null) {
            repository.addShortcut(folderId, updated)
        } else {
            repository.updateShortcut(folderId, updated)
        }
        return SaveResult.Success
    }

    fun deleteShortcut(id: String) {
        repository.deleteShortcut(folderId, id)
        refreshSavedShortcuts()
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data object MissingShortcut : SaveResult()
        data object MissingExpandsTo : SaveResult()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ShortcutApplication
                ShortcutEditorViewModel(app.repository, createSavedStateHandle())
            }
        }

        private val APPLICATION_KEY = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }
}
```

- [ ] **Step 4: ShortcutEditorFragment.kt 생성**

```kotlin
package com.kore2.shortcutime.ui.editor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.kore2.shortcutime.R
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ExampleItem
import com.kore2.shortcutime.data.ExampleSourceType
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.DialogExampleEditorBinding
import com.kore2.shortcutime.databinding.FragmentShortcutEditorBinding
import com.kore2.shortcutime.ui.ExampleAdapter
import com.kore2.shortcutime.ui.ShortcutAdapter
import com.kore2.shortcutime.ui.applyBodyTextTheme
import com.kore2.shortcutime.ui.applyFilledButtonTheme
import com.kore2.shortcutime.ui.applyInputLayoutTheme
import com.kore2.shortcutime.ui.applySwitchTheme
import com.kore2.shortcutime.ui.applyToolbarTheme
import com.kore2.shortcutime.ui.roundedRectDrawable
import kotlinx.coroutines.launch
import java.util.UUID

class ShortcutEditorFragment : Fragment() {
    private var _binding: FragmentShortcutEditorBinding? = null
    private val binding get() = _binding!!

    private val args: ShortcutEditorFragmentArgs by navArgs()

    private val viewModel: ShortcutEditorViewModel by viewModels { ShortcutEditorViewModel.Factory }

    private lateinit var exampleAdapter: ExampleAdapter
    private lateinit var savedShortcutAdapter: ShortcutAdapter

    private var selectedGenerateCount: Int = 1
    private val previewHandler = Handler(Looper.getMainLooper())
    private var previewRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentShortcutEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        exampleAdapter = ExampleAdapter(
            onEdit = { openExampleDialog(it) },
            onDelete = { viewModel.deleteExample(it.id) },
        )
        savedShortcutAdapter = ShortcutAdapter(
            onEdit = { openExistingShortcut(it.id) },
            onDelete = { confirmDeleteExisting(it) },
        )
        binding.examplesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.examplesRecyclerView.adapter = exampleAdapter
        binding.savedShortcutsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.savedShortcutsRecyclerView.adapter = savedShortcutAdapter

        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.previewTitle.text = getString(R.string.preview_title)
        binding.colemakPreview.setOnAnimationFinishedListener {
            binding.previewStatus.text = getString(R.string.preview_complete, it)
        }

        setupGenerateCountButtons()
        binding.addExampleButton.setOnClickListener { openExampleDialog(null) }
        binding.generateExamplesButton.setOnClickListener {
            Toast.makeText(
                requireContext(),
                R.string.phase2_feature_not_ready,
                Toast.LENGTH_SHORT,
            ).show()
        }
        binding.saveButton.setOnClickListener { onSaveClick() }
        binding.shortcutInput.addTextChangedListener { text ->
            schedulePreview(text?.toString().orEmpty())
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        viewModel.refreshSavedShortcuts()
    }

    override fun onDestroyView() {
        previewRunnable?.let { previewHandler.removeCallbacks(it) }
        _binding = null
        super.onDestroyView()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.folderMissing.collect { missing ->
                        if (missing) {
                            Toast.makeText(
                                requireContext(),
                                R.string.folder_not_found,
                                Toast.LENGTH_SHORT,
                            ).show()
                            findNavController().popBackStack()
                        }
                    }
                }
                launch {
                    viewModel.entry.collect { entry ->
                        entry?.let { bindEntry(it) }
                        if (entry != null) {
                            binding.topToolbar.title = getString(R.string.title_edit_shortcut)
                        } else {
                            binding.topToolbar.title = getString(R.string.title_folder_list)
                        }
                    }
                }
                launch {
                    viewModel.workingExamples.collect { examples ->
                        exampleAdapter.submitList(examples)
                        binding.exampleCountText.text =
                            getString(R.string.example_count_format, examples.size)
                    }
                }
                launch {
                    viewModel.savedShortcuts.collect { list ->
                        savedShortcutAdapter.submitList(list)
                        binding.savedShortcutsRecyclerView.visibility =
                            if (list.isEmpty()) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    private fun bindEntry(entry: ShortcutEntry) {
        if (binding.shortcutInput.text?.toString() != entry.shortcut) {
            binding.shortcutInput.setText(entry.shortcut)
        }
        if (binding.expandsToInput.text?.toString() != entry.expandsTo) {
            binding.expandsToInput.setText(entry.expandsTo)
        }
        if (binding.noteInput.text?.toString() != entry.note) {
            binding.noteInput.setText(entry.note)
        }
        binding.caseSensitiveSwitch.isChecked = entry.caseSensitive
        binding.backspaceUndoSwitch.isChecked = entry.backspaceToUndo
    }

    private fun setupGenerateCountButtons() {
        binding.generateOneButton.setOnClickListener {
            selectedGenerateCount = 1
            updateGenerateSelection()
        }
        binding.generateThreeButton.setOnClickListener {
            selectedGenerateCount = 3
            updateGenerateSelection()
        }
        binding.generateFiveButton.setOnClickListener {
            selectedGenerateCount = 5
            updateGenerateSelection()
        }
        updateGenerateSelection()
    }

    private fun updateGenerateSelection() {
        val buttons = listOf(
            binding.generateOneButton to 1,
            binding.generateThreeButton to 3,
            binding.generateFiveButton to 5,
        )
        buttons.forEach { (button, count) ->
            button.isChecked = count == selectedGenerateCount
        }
    }

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        applyInputLayoutTheme(binding.shortcutInputLayout, binding.shortcutInput, theme)
        applyInputLayoutTheme(binding.expandsToInputLayout, binding.expandsToInput, theme)
        applyInputLayoutTheme(binding.noteInputLayout, binding.noteInput, theme)
        binding.previewCard.background =
            roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.previewCard)
        binding.previewTitle.setTextColor(theme.textPrimary)
        binding.previewStatus.setTextColor(theme.textSecondary)
        binding.helperText.setTextColor(theme.textSecondary)
        binding.savedShortcutsTitle.setTextColor(theme.textPrimary)
        binding.colemakPreview.applyTheme(theme)
        applyBodyTextTheme(binding.examplesTitle, theme, emphasize = true)
        applyBodyTextTheme(binding.exampleCountText, theme)
        applyBodyTextTheme(binding.keywordSettingsTitle, theme, emphasize = true)
        applyFilledButtonTheme(binding.addExampleButton, theme)
        applyFilledButtonTheme(binding.generateOneButton, theme)
        applyFilledButtonTheme(binding.generateThreeButton, theme)
        applyFilledButtonTheme(binding.generateFiveButton, theme)
        applyFilledButtonTheme(binding.generateExamplesButton, theme)
        applyFilledButtonTheme(binding.saveButton, theme)
        applySwitchTheme(binding.backspaceUndoSwitch, theme)
        applySwitchTheme(binding.caseSensitiveSwitch, theme)
    }

    private fun schedulePreview(raw: String) {
        previewRunnable?.let { previewHandler.removeCallbacks(it) }
        val normalized = raw.trim()
        if (normalized.isBlank()) {
            binding.colemakPreview.clearAnimationState()
            binding.previewStatus.text = getString(R.string.preview_idle)
            return
        }
        binding.colemakPreview.setSequence(normalized)
        binding.previewStatus.text = getString(R.string.preview_typed, normalized)
        val next = Runnable {
            binding.colemakPreview.playSequence(normalized)
            binding.previewStatus.text = getString(R.string.preview_animating, normalized)
        }
        previewRunnable = next
        previewHandler.postDelayed(next, PREVIEW_DEBOUNCE_MS)
    }

    private fun openExampleDialog(existing: ExampleItem?) {
        val dialogBinding = DialogExampleEditorBinding.inflate(layoutInflater)
        dialogBinding.englishInput.setText(existing?.english.orEmpty())
        dialogBinding.koreanInput.setText(existing?.korean.orEmpty())
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        dialogBinding.root.setBackgroundColor(theme.appBackground)
        applyInputLayoutTheme(dialogBinding.englishInputLayout, dialogBinding.englishInput, theme)
        applyInputLayoutTheme(dialogBinding.koreanInputLayout, dialogBinding.koreanInput, theme)

        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) R.string.title_add_example else R.string.title_edit_example)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val english = dialogBinding.englishInput.text?.toString().orEmpty().trim()
                val korean = dialogBinding.koreanInput.text?.toString().orEmpty().trim()
                if (english.isBlank() && korean.isBlank()) return@setPositiveButton
                val updated = ExampleItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    english = english,
                    korean = korean,
                    sourceType = existing?.sourceType ?: ExampleSourceType.MANUAL,
                )
                viewModel.addOrUpdateExample(updated)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onSaveClick() {
        val shortcut = binding.shortcutInput.text?.toString().orEmpty().trim()
        val expandsTo = binding.expandsToInput.text?.toString().orEmpty().trim()
        val note = binding.noteInput.text?.toString().orEmpty().trim()
        val result = viewModel.save(
            shortcut = shortcut,
            expandsTo = expandsTo,
            note = note,
            caseSensitive = binding.caseSensitiveSwitch.isChecked,
            backspaceToUndo = binding.backspaceUndoSwitch.isChecked,
        )
        when (result) {
            ShortcutEditorViewModel.SaveResult.MissingShortcut -> {
                binding.shortcutInputLayout.error = getString(R.string.error_shortcut_required)
            }
            ShortcutEditorViewModel.SaveResult.MissingExpandsTo -> {
                binding.shortcutInputLayout.error = null
                binding.expandsToInputLayout.error = getString(R.string.error_expands_to_required)
            }
            ShortcutEditorViewModel.SaveResult.Success -> {
                binding.shortcutInputLayout.error = null
                binding.expandsToInputLayout.error = null
                findNavController().popBackStack()
            }
        }
    }

    private fun openExistingShortcut(shortcutId: String) {
        if (viewModel.shortcutId == shortcutId) return
        val action = ShortcutEditorFragmentDirections
            .actionShortcutEditorSelf(viewModel.folderId, shortcutId)
        findNavController().navigate(action)
    }

    private fun confirmDeleteExisting(entry: ShortcutEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_delete_shortcut_title))
            .setMessage(getString(R.string.dialog_delete_shortcut_message, entry.shortcut))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteShortcut(entry.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val PREVIEW_DEBOUNCE_MS = 800L
    }
}
```

- [ ] **Step 5: nav_graph 에서 shortcutEditorActivity → shortcutEditorFragment 교체**

`app/src/main/res/navigation/nav_graph.xml` 의 `<activity android:id="@+id/shortcutEditorActivity" .../>` 를 다음으로 교체:

```xml
    <fragment
        android:id="@+id/shortcutEditorFragment"
        android:name="com.kore2.shortcutime.ui.editor.ShortcutEditorFragment"
        android:label="ShortcutEditor"
        tools:layout="@layout/fragment_shortcut_editor">
        <argument
            android:name="folderId"
            app:argType="string" />
        <argument
            android:name="shortcutId"
            app:argType="string"
            app:nullable="true"
            android:defaultValue="@null" />
        <action
            android:id="@+id/action_shortcutEditor_self"
            app:destination="@id/shortcutEditorFragment" />
    </fragment>
```

그리고 `folderDetailFragment` 의 action 에서 `app:destination="@id/shortcutEditorActivity"` 를 `app:destination="@id/shortcutEditorFragment"` 로 변경.

- [ ] **Step 6: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: convert ShortcutEditor to Fragment; fix ensureTargetFolderId + preview debounce + stub generate"
```

---

### Task 12: SettingsFragment

**Files:**
- Create: `app/src/main/res/layout/fragment_settings.xml`
- Create: `app/src/main/java/com/kore2/shortcutime/ui/settings/SettingsFragment.kt`
- Modify: `app/src/main/res/navigation/nav_graph.xml`

- [ ] **Step 1: fragment_settings.xml 생성**

기존 `activity_settings.xml` 의 element 트리를 복사:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_app"
    android:orientation="vertical">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/topToolbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:backgroundTint="@color/bg_panel"
        android:title="@string/tab_settings"
        android:titleTextColor="@color/text_primary" />

    <TextView
        android:id="@+id/placeholderText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="20dp"
        android:text="@string/settings_placeholder"
        android:textColor="@color/text_secondary" />
</LinearLayout>
```

- [ ] **Step 2: SettingsFragment.kt 생성**

```kotlin
package com.kore2.shortcutime.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.databinding.FragmentSettingsBinding
import com.kore2.shortcutime.ui.applyToolbarTheme

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topToolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.topToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyTheme() {
        val theme = ShortcutApplication.from(requireContext()).themeStore.currentTheme()
        binding.root.setBackgroundColor(theme.appBackground)
        applyToolbarTheme(binding.topToolbar, theme)
        binding.placeholderText.setTextColor(theme.textSecondary)
    }
}
```

- [ ] **Step 3: nav_graph 에서 settingsActivity → settingsFragment 교체**

`app/src/main/res/navigation/nav_graph.xml` 의 `<activity android:id="@+id/settingsActivity" .../>` 를 다음으로 교체:

```xml
    <fragment
        android:id="@+id/settingsFragment"
        android:name="com.kore2.shortcutime.ui.settings.SettingsFragment"
        android:label="Settings"
        tools:layout="@layout/fragment_settings" />
```

그리고 `folderListFragment` 의 action 에서 `app:destination="@id/settingsActivity"` 를 `app:destination="@id/settingsFragment"` 로 변경.

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: convert SettingsActivity to SettingsFragment"
```

---

### Task 13: 기존 Activity + 레이아웃 삭제 + Manifest 정리

**Files:**
- Delete: `app/src/main/java/com/kore2/shortcutime/ui/FolderDetailActivity.kt`
- Delete: `app/src/main/java/com/kore2/shortcutime/ui/FolderEditorActivity.kt`
- Delete: `app/src/main/java/com/kore2/shortcutime/ui/ShortcutEditorActivity.kt`
- Delete: `app/src/main/java/com/kore2/shortcutime/ui/SettingsActivity.kt`
- Delete: `app/src/main/res/layout/activity_folder_detail.xml`
- Delete: `app/src/main/res/layout/activity_folder_editor.xml`
- Delete: `app/src/main/res/layout/activity_shortcut_editor.xml`
- Delete: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Activity 파일 삭제**

```bash
rm "app/src/main/java/com/kore2/shortcutime/ui/FolderDetailActivity.kt"
rm "app/src/main/java/com/kore2/shortcutime/ui/FolderEditorActivity.kt"
rm "app/src/main/java/com/kore2/shortcutime/ui/ShortcutEditorActivity.kt"
rm "app/src/main/java/com/kore2/shortcutime/ui/SettingsActivity.kt"
rm "app/src/main/res/layout/activity_folder_detail.xml"
rm "app/src/main/res/layout/activity_folder_editor.xml"
rm "app/src/main/res/layout/activity_shortcut_editor.xml"
rm "app/src/main/res/layout/activity_settings.xml"
```

- [ ] **Step 2: Manifest 에서 4 개 Activity 선언 삭제**

`app/src/main/AndroidManifest.xml` 에서 다음 4 블록을 제거:

```xml
        <activity android:name=".ui.FolderDetailActivity" android:exported="false" />
        <activity android:name=".ui.FolderEditorActivity" android:exported="false" />
        <activity android:name=".ui.ShortcutEditorActivity" android:exported="false" />
        <activity android:name=".ui.SettingsActivity" android:exported="false" />
```

최종 Manifest 는 HostActivity + ShortcutInputMethodService 만 남는다.

- [ ] **Step 3: sanity check — 다른 곳에서 삭제된 Activity 참조 없는지 확인**

```bash
grep -r "FolderDetailActivity\|FolderEditorActivity\|ShortcutEditorActivity\|SettingsActivity" app/src/main --include="*.kt" --include="*.xml"
```
Expected: 출력 없음. 있으면 그 참조를 수정.

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: remove legacy Activities replaced by Fragments"
```

---

### Task 14: Colemak 애니메이션 타이밍 재작성

**Files:**
- Modify: `app/src/main/java/com/kore2/shortcutime/ime/ColemakPreviewView.kt`

- [ ] **Step 1: `playSequence` 전체 재작성**

`ColemakPreviewView.kt:58-108` 의 `playSequence` 함수를 다음으로 교체:

```kotlin
    fun playSequence(sequence: String) {
        animationToken += 1
        animationHandler.removeCallbacksAndMessages(null)

        displayedSequence = sequence.lowercase().filter { it in COLEMAK_KEYS }.toList()
        activeIndex = -1
        showCompleted = false
        showTrail = false
        invalidate()

        if (displayedSequence.isEmpty()) {
            onAnimationFinished?.invoke("")
            return
        }

        val token = animationToken
        val cycleMs = KEY_LIT_MS + KEY_GAP_MS

        displayedSequence.indices.forEach { index ->
            val litAt = index * cycleMs
            val offAt = litAt + KEY_LIT_MS
            animationHandler.postDelayed({
                if (token != animationToken) return@postDelayed
                activeIndex = index
                showCompleted = false
                showTrail = false
                invalidate()
            }, litAt)
            animationHandler.postDelayed({
                if (token != animationToken) return@postDelayed
                activeIndex = -1
                invalidate()
            }, offAt)
        }

        val finalAt = displayedSequence.size * cycleMs - KEY_GAP_MS + FINAL_PAUSE_MS
        animationHandler.postDelayed({
            if (token != animationToken) return@postDelayed
            activeIndex = -1
            showCompleted = true
            showTrail = displayedSequence.size > 1
            invalidate()
            onAnimationFinished?.invoke(displayedSequence.joinToString(""))
        }, finalAt)
    }
```

- [ ] **Step 2: companion object 에 타이밍 상수 추가**

`ColemakPreviewView.kt:230-232` 의 `companion object` 블록을 다음으로 교체:

```kotlin
    companion object {
        private const val COLEMAK_KEYS = "qwfpgjluy;arstdhneio'zxcvbkm,./"
        private const val KEY_LIT_MS = 700L
        private const val KEY_GAP_MS = 150L
        private const val FINAL_PAUSE_MS = 400L
    }
```

- [ ] **Step 3: 기존 호출자 중 `stepDurationMs` 를 지정하던 곳 확인**

```bash
grep -rn "playSequence" app/src/main --include="*.kt"
```

`ShortcutEditorActivity` 는 이미 삭제됨. `ShortcutEditorFragment.schedulePreview` 는 `playSequence(normalized)` 로 호출 — 새 시그니처와 호환. `ShortcutInputMethodService` 에서 호출하는 경우가 있으면 인자 없이 호출로 맞춘다.

```bash
grep -n "playSequence" app/src/main/java/com/kore2/shortcutime/ime/ShortcutInputMethodService.kt
```

만약 `.playSequence("...", 1000L)` 같은 호출이 남아 있으면, 두 번째 인자를 제거.

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kore2/shortcutime/ime/ColemakPreviewView.kt app/src/main/java/com/kore2/shortcutime/ime/ShortcutInputMethodService.kt
git commit -m "fix: Colemak animation now flashes keys independently with final arrow"
```

---

### Task 15: ShortcutEntry.usageDisplay 단위 테스트

**Files:**
- Create: `app/src/test/java/com/kore2/shortcutime/data/ShortcutEntryTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
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
```

- [ ] **Step 2: 테스트 실행**

Run: `./gradlew :app:testDebugUnitTest --tests "com.kore2.shortcutime.data.ShortcutEntryTest"`
Expected: PASS (5 tests). 기존 `usageDisplay` 로직이 올바르면 통과.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/kore2/shortcutime/data/ShortcutEntryTest.kt
git commit -m "test: verify ShortcutEntry.usageDisplay boundaries"
```

---

### Task 16: Usage badge — item_shortcut_entry.xml + ShortcutEntryAdapter

**Files:**
- Modify: `app/src/main/res/layout/item_shortcut_entry.xml`
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/ShortcutEntryAdapter.kt`

- [ ] **Step 1: item_shortcut_entry.xml 에 usage 뱃지 TextView 추가**

`item_shortcut_entry.xml` 의 `openArrowText` TextView **바로 위** (같은 LinearLayout 내부) 에 새 TextView 를 추가. 최종 파일:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/shortcutCardRoot"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="14dp"
    android:background="@drawable/panel_bg"
    android:minHeight="92dp"
    android:orientation="horizontal"
    android:paddingHorizontal="16dp"
    android:paddingVertical="16dp">

    <TextView
        android:id="@+id/orderText"
        android:layout_width="40dp"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:text="1."
        android:textColor="@color/text_primary"
        android:textSize="22sp"
        android:textStyle="bold" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/shortcutText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:maxLines="1"
            android:textColor="@color/text_primary"
            android:textSize="17sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/expandsToText"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:maxLines="2"
            android:textColor="@color/text_secondary"
            android:textSize="14sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/usageBadge"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical"
        android:paddingHorizontal="8dp"
        android:paddingVertical="4dp"
        android:textColor="@color/text_secondary"
        android:textSize="14sp" />

    <TextView
        android:id="@+id/openArrowText"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:clickable="true"
        android:focusable="true"
        android:gravity="center"
        android:paddingStart="14dp"
        android:text=">"
        android:textSize="34sp"
        android:textStyle="bold" />
</LinearLayout>
```

- [ ] **Step 2: ShortcutEntryAdapter.bind 에서 usage badge 바인딩**

`app/src/main/java/com/kore2/shortcutime/ui/ShortcutEntryAdapter.kt:32-47` 의 `bind()` 를 다음으로 교체:

```kotlin
        fun bind(item: ShortcutEntry, position: Int) {
            val theme = KeyboardThemeStore(binding.root.context).currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.root)
            binding.orderText.text = "${position + 1}."
            binding.orderText.setTextColor(theme.textPrimary)
            binding.shortcutText.text = item.shortcut
            binding.shortcutText.setTextColor(theme.textPrimary)
            binding.expandsToText.text = item.expandsTo
            binding.expandsToText.setTextColor(theme.textSecondary)
            binding.usageBadge.text = binding.root.context.getString(
                com.kore2.shortcutime.R.string.usage_badge_format,
                item.usageDisplay,
            )
            binding.usageBadge.setTextColor(theme.textSecondary)
            binding.openArrowText.setTextColor(theme.accentColor)
            binding.openArrowText.setOnClickListener { onShortcutClick(item) }
            binding.root.setOnLongClickListener {
                onShortcutDelete(item)
                true
            }
        }
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: show usage badge on folder detail shortcut list"
```

---

### Task 17: Usage badge — item_shortcut.xml + ShortcutAdapter

**Files:**
- Modify: `app/src/main/res/layout/item_shortcut.xml`
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/ShortcutAdapter.kt`

- [ ] **Step 1: item_shortcut.xml 에 usage 뱃지 TextView 추가**

`editShortcutButton` 바로 왼쪽 (같은 inner LinearLayout 내부 맨 앞) 에 usage 뱃지 TextView 삽입:

기존 inner LinearLayout 블록 (라인 34-59) 를 다음으로 교체:

```xml
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/usageBadge"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingHorizontal="8dp"
            android:paddingVertical="4dp"
            android:textColor="@color/text_secondary"
            android:textSize="14sp" />

        <TextView
            android:id="@+id/editShortcutButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingStart="12dp"
            android:paddingEnd="8dp"
            android:text="@string/action_edit"
            android:textColor="@color/text_secondary"
            android:textSize="14sp" />

        <TextView
            android:id="@+id/deleteShortcutButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:paddingStart="8dp"
            android:paddingEnd="4dp"
            android:text="@string/action_delete"
            android:textColor="@color/text_secondary"
            android:textSize="14sp" />
    </LinearLayout>
```

- [ ] **Step 2: ShortcutAdapter.bind 에 usage badge 바인딩 추가**

`app/src/main/java/com/kore2/shortcutime/ui/ShortcutAdapter.kt:36-47` 의 `bind()` 를 다음으로 교체:

```kotlin
        fun bind(item: ShortcutEntry) {
            val theme = KeyboardThemeStore(binding.root.context).currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.root)
            binding.shortcutLabel.text = item.shortcut
            binding.shortcutLabel.setTextColor(theme.accentColor)
            binding.expandsToLabel.text = item.expandsTo
            binding.expandsToLabel.setTextColor(theme.textPrimary)
            binding.usageBadge.text = binding.root.context.getString(
                com.kore2.shortcutime.R.string.usage_badge_format,
                item.usageDisplay,
            )
            binding.usageBadge.setTextColor(theme.textSecondary)
            binding.editShortcutButton.setTextColor(theme.textSecondary)
            binding.deleteShortcutButton.setTextColor(theme.textSecondary)
            binding.editShortcutButton.setOnClickListener { onEdit(item) }
            binding.deleteShortcutButton.setOnClickListener { onDelete(item) }
        }
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: show usage badge on saved shortcuts list in editor"
```

---

### Task 18: Adapter 효율화 — ListAdapter + DiffUtil + theme 캐싱

**Files:**
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/FolderAdapter.kt`
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/ShortcutEntryAdapter.kt`
- Modify: `app/src/main/java/com/kore2/shortcutime/ui/ShortcutAdapter.kt`

- [ ] **Step 1: FolderAdapter 재작성**

`app/src/main/java/com/kore2/shortcutime/ui/FolderAdapter.kt` 를 다음으로 전체 교체:

```kotlin
package com.kore2.shortcutime.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.FolderItem
import com.kore2.shortcutime.databinding.ItemFolderBinding

class FolderAdapter(
    private val onFolderClick: (FolderItem) -> Unit,
    private val onFolderEdit: (FolderItem) -> Unit,
    private val onFolderDelete: (FolderItem) -> Unit,
) : ListAdapter<FolderItem, FolderAdapter.FolderViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FolderViewHolder(
        private val binding: ItemFolderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val themeStore =
            ShortcutApplication.from(binding.root.context).themeStore

        fun bind(item: FolderItem) {
            val theme = themeStore.currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.root)
            val openAction = { onFolderClick(item) }
            binding.folderIcon.setTextColor(theme.textSecondary)
            binding.folderTitle.text = item.title
            binding.folderTitle.setTextColor(theme.textPrimary)
            binding.folderCount.text = binding.root.context.getString(
                com.kore2.shortcutime.R.string.folder_count_format,
                item.shortcuts.size,
            )
            binding.folderNote.text = item.note.ifBlank {
                binding.root.context.getString(com.kore2.shortcutime.R.string.folder_note_placeholder)
            }
            binding.folderCount.setTextColor(theme.textSecondary)
            binding.folderNote.setTextColor(theme.textSecondary)
            binding.openButton.setTextColor(theme.accentColor)
            binding.root.setOnClickListener { openAction() }
            binding.folderContentArea.setOnClickListener { openAction() }
            binding.openButton.setOnClickListener { openAction() }
            binding.root.setOnLongClickListener {
                onFolderEdit(item)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<FolderItem>() {
            override fun areItemsTheSame(old: FolderItem, new: FolderItem) = old.id == new.id
            override fun areContentsTheSame(old: FolderItem, new: FolderItem) = old == new
        }
    }
}
```

- [ ] **Step 2: ShortcutEntryAdapter 재작성 (ListAdapter + DiffUtil)**

```kotlin
package com.kore2.shortcutime.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.ItemShortcutEntryBinding

class ShortcutEntryAdapter(
    private val onShortcutClick: (ShortcutEntry) -> Unit,
    private val onShortcutDelete: (ShortcutEntry) -> Unit,
) : ListAdapter<ShortcutEntry, ShortcutEntryAdapter.ShortcutViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val binding = ItemShortcutEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShortcutViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun submitList(list: List<ShortcutEntry>?) {
        super.submitList(list?.sortedBy { it.shortcut.lowercase() })
    }

    inner class ShortcutViewHolder(
        private val binding: ItemShortcutEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val themeStore = ShortcutApplication.from(binding.root.context).themeStore

        fun bind(item: ShortcutEntry, position: Int) {
            val theme = themeStore.currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.root)
            binding.orderText.text = "${position + 1}."
            binding.orderText.setTextColor(theme.textPrimary)
            binding.shortcutText.text = item.shortcut
            binding.shortcutText.setTextColor(theme.textPrimary)
            binding.expandsToText.text = item.expandsTo
            binding.expandsToText.setTextColor(theme.textSecondary)
            binding.usageBadge.text = binding.root.context.getString(
                com.kore2.shortcutime.R.string.usage_badge_format,
                item.usageDisplay,
            )
            binding.usageBadge.setTextColor(theme.textSecondary)
            binding.openArrowText.setTextColor(theme.accentColor)
            binding.openArrowText.setOnClickListener { onShortcutClick(item) }
            binding.root.setOnLongClickListener {
                onShortcutDelete(item)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ShortcutEntry>() {
            override fun areItemsTheSame(old: ShortcutEntry, new: ShortcutEntry) = old.id == new.id
            override fun areContentsTheSame(old: ShortcutEntry, new: ShortcutEntry) = old == new
        }
    }
}
```

- [ ] **Step 3: ShortcutAdapter 재작성 (ListAdapter + DiffUtil)**

```kotlin
package com.kore2.shortcutime.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kore2.shortcutime.ShortcutApplication
import com.kore2.shortcutime.data.ShortcutEntry
import com.kore2.shortcutime.databinding.ItemShortcutBinding

class ShortcutAdapter(
    private val onEdit: (ShortcutEntry) -> Unit,
    private val onDelete: (ShortcutEntry) -> Unit,
) : ListAdapter<ShortcutEntry, ShortcutAdapter.ShortcutViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val binding = ItemShortcutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ShortcutViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun submitList(list: List<ShortcutEntry>?) {
        super.submitList(list?.sortedBy { it.shortcut.lowercase() })
    }

    inner class ShortcutViewHolder(
        private val binding: ItemShortcutBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val themeStore = ShortcutApplication.from(binding.root.context).themeStore

        fun bind(item: ShortcutEntry) {
            val theme = themeStore.currentTheme()
            binding.root.background = roundedRectDrawable(theme.previewBackground, theme.strokeColor, 18f, binding.root)
            binding.shortcutLabel.text = item.shortcut
            binding.shortcutLabel.setTextColor(theme.accentColor)
            binding.expandsToLabel.text = item.expandsTo
            binding.expandsToLabel.setTextColor(theme.textPrimary)
            binding.usageBadge.text = binding.root.context.getString(
                com.kore2.shortcutime.R.string.usage_badge_format,
                item.usageDisplay,
            )
            binding.usageBadge.setTextColor(theme.textSecondary)
            binding.editShortcutButton.setTextColor(theme.textSecondary)
            binding.deleteShortcutButton.setTextColor(theme.textSecondary)
            binding.editShortcutButton.setOnClickListener { onEdit(item) }
            binding.deleteShortcutButton.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ShortcutEntry>() {
            override fun areItemsTheSame(old: ShortcutEntry, new: ShortcutEntry) = old.id == new.id
            override fun areContentsTheSame(old: ShortcutEntry, new: ShortcutEntry) = old == new
        }
    }
}
```

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: adapters use ListAdapter + DiffUtil + cached theme store"
```

---

### Task 19: Layout polish — 이미지 목업 매칭

**Files:**
- Modify: `app/src/main/res/layout/fragment_shortcut_editor.xml`

- [ ] **Step 1: 상단 toolbar 제목을 "Shortcut Animator" (title_folder_list) 로 고정**

`fragment_shortcut_editor.xml` 의 toolbar `android:title` 을 `@string/title_folder_list` 로 (Fragment 코드에서 편집 모드면 덮어씀).

(이 파일은 Task 11 에서 생성되었고 이미 `@string/title_add_shortcut_entry` 를 쓰고 있을 수 있음. 해당 라인을 바꿔준다.)

변경 전:
```xml
android:title="@string/title_add_shortcut_entry"
```

변경 후:
```xml
android:title="@string/title_folder_list"
```

- [ ] **Step 2: Preview card 에 테두리 배경 적용**

`fragment_shortcut_editor.xml` 의 `previewCard` LinearLayout 에 `android:background="@drawable/panel_bg"` 속성을 추가 (기존 `android:padding="16dp"` 옆):

```xml
<LinearLayout
    android:id="@+id/previewCard"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="22dp"
    android:background="@drawable/panel_bg"
    android:orientation="vertical"
    android:padding="16dp">
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_shortcut_editor.xml
git commit -m "style: shortcut editor title fixed + preview card bordered"
```

---

### Task 20: 전체 수동 검증

**Files:**
- 없음 (디바이스 테스트만)

- [ ] **Step 1: 설치**

Run: `./gradlew installDebug`

- [ ] **Step 2: Phase 1 수동 체크리스트 전부 수행**

- [ ] 앱 실행 → 폴더 목록 표시
- [ ] FAB (+) → 폴더 편집 Fragment → 저장 → 목록 갱신
- [ ] 폴더 클릭 → FolderDetail Fragment → 빈 상태 표시
- [ ] FAB → ShortcutEditor Fragment 진입 (**자동 "Starter Folder" 생성 없음**)
- [ ] shortcut 에 `mike` 입력 → 800ms 후 Colemak 프리뷰가 **m → off → i → off → k → off → e → off → 화살표** 순서로 재생
- [ ] expands to 채우고 저장 → FolderDetail 로 돌아오고 목록에 새 shortcut 표시
- [ ] 방금 저장한 shortcut 의 `usage 0` 뱃지 표시
- [ ] 다른 앱 (예: 메시지 앱) 입력창에서 IME 활성화 → 저장한 shortcut 타이핑 + space → expansion 발동
- [ ] 앱으로 돌아와 FolderDetail 을 다시 보면 `usage 1` 뱃지
- [ ] 101 회까지 반복 사용 → `usage 100↑` 표시 (간단히 테스트하려면 `repository.incrementUsage` 를 101 번 호출하는 임시 debug 코드로도 가능하지만 기본은 실제 사용으로 검증)
- [ ] `예문 자동 생성` 버튼 → "예문 자동 생성은 다음 업데이트에서 지원돼요." Toast, 저장 없음
- [ ] 앱 킬 후 재실행 → 데이터 유지
- [ ] 설정 버튼 → SettingsFragment 진입 → 뒤로 → 목록
- [ ] IME 의 `+` 버튼 → HostActivity 진입 → 폴더 목록

- [ ] **Step 3: 모든 체크리스트 통과 확인 후 최종 commit**

(이 시점에서 코드 변경은 없어야 함. 버그를 발견하면 해당 task 로 돌아가 수정)

```bash
./gradlew assembleDebug && ./gradlew :app:testDebugUnitTest
```

- [ ] **Step 4: 최종 커밋 (필요 시)**

```bash
git log --oneline
# 커밋 이력 확인. 추가 변경이 없으면 별도 commit 없음.
```

**Phase 1 완료.**

---

## Self-Review Checklist

구현자가 Phase 1 을 마치면 아래를 직접 체크:

- [ ] 스펙 §1.2 — 리셋 버그 수정 확인: Task 3
- [ ] 스펙 §1.2 — Starter Folder 버그 수정 확인: Task 11 (ensureTargetFolderId 제거)
- [ ] 스펙 §1.2 — 애니메이션 흐르는 빛 수정 확인: Task 14
- [ ] 스펙 §1.2 — usage count 표시: Task 16, 17
- [ ] 스펙 §1.2 — 단일 Activity 아키텍처: Task 4, 8, 13
- [ ] 스펙 §2 — Navigation Component + safeargs: Task 1, 5
- [ ] 스펙 §3.3 — migrateLegacyShortcutsIfNeeded 유지: Task 3 (삭제하지 않음)
- [ ] 스펙 §4 — 애니메이션 타이밍 상수: Task 14
- [ ] 스펙 §5 — Phase 1-a 완료 기준 9 개 충족: Task 9 수동 검증
- [ ] 스펙 §6 — Phase 1-b 완료 기준 9 개 충족: Task 20 수동 검증
- [ ] 스펙 §7.3 — 자동 테스트 5 개 중 Repository + usageDisplay 2 개 포함 (HangulComposer / Colemak 매핑 / findMatchingShortcut 경계는 Phase 1 핵심이 아니라 옵션)

---

## Out of Scope — Phase 2 로 이관

- Claude Haiku API 를 이용한 실제 예문 생성 (`generateExamplesButton` 동작)
- `EncryptedSharedPreferences` 기반 API 키 보관
- 네트워크 오류 처리, 재시도, rate limit 대응
- Room 전환
- 다크 모드
