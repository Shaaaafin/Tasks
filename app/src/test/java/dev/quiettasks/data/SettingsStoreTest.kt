package dev.quiettasks.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsStoreTest {
    @Test fun upgradeStartsOnSummaryAndKeepsSyncAndIndependentTabPreferences() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear()
            .putString("selectedList", "old-list").putString("theme", "DARK").putString("syncMode", "MANUAL")
            .putString("serverUrl", "https://example.com/dav/").putString("username", "alex").commit()
        val store = SettingsStore(context)
        assertEquals(ALL_TASKS_TAB, store.state.value.selectedList)
        assertEquals(SyncMode.MANUAL, store.state.value.syncMode); assertEquals(ThemeMode.DARK, store.state.value.theme)
        store.update { it.copy(theme = ThemeMode.AMOLED, allTabColor = "purple", allTabIcon = "📋", starredTabColor = "orange", summarySort = SortMode.MY_ORDER,
            listSorts = mapOf("a" to SortMode.PRIORITY, "b" to SortMode.TITLE), selectedList = "a", showAllTab = false, showStarredTab = false, longPressAction = LongPressAction.MENU) }
        val restored = SettingsStore(context).state.value
        assertEquals(store.state.value, restored)
        assertEquals("https://example.com/dav/", restored.serverUrl); assertEquals("alex", restored.username)
        assertEquals(SortMode.PRIORITY, restored.listSorts["a"]); assertEquals(SortMode.TITLE, restored.listSorts["b"])
    }
}
