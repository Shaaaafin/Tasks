package dev.quiettasks.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.quiettasks.TaskViewModel
import dev.quiettasks.TasksApplication
import dev.quiettasks.data.*
import dev.quiettasks.sync.SyncStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TasksUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = rule.activity.application as TasksApplication
    private val sample = Snapshot(listOf(TaskList(id = "my-tasks", name = "Personal", color = "purple", icon = "🌿"), TaskList(id = "shopping", name = "Shopping", color = "green", icon = "🛒")), listOf(
        Task(id = "design-1", listId = "my-tasks", title = "Plan the weekend", starred = true, position = 0, priority = TaskPriority.HIGH, tags = listOf("personal")),
        Task(id = "design-2", listId = "shopping", title = "Pick up groceries", dueDate = java.time.LocalDate.now().plusDays(1).toString(), position = 1024, tags = listOf("errands")),
        Task(id = "design-3", listId = "my-tasks", title = "Book a table for dinner", position = 2048),
        Task(id = "design-4", listId = "my-tasks", title = "Water the plants", completedAt = 1, position = 3072),
        Task(id = "design-5", listId = "my-tasks", title = "Read for 20 minutes", dueDate = java.time.LocalDate.now().toString(), recurrence = "FREQ=DAILY", position = 4096),
    ))
    private fun capture(name: String, dialog: Boolean = false) {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500)); rule.waitForIdle()
        val bitmap = rule.runOnIdle {
            val host = rule.activity.window.decorView
            val view = (if (dialog) org.robolectric.shadows.ShadowDialog.getLatestDialog()?.window?.decorView else null) ?: host
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it); if (dialog) host.draw(canvas); view.draw(canvas)
            }
        }
        val file = File("build/previews/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 5000)
        if (name == "home-amoled") assertEquals(android.graphics.Color.BLACK, bitmap.getPixel(1, bitmap.height / 2))
        if (name == "home-dark") assertEquals(android.graphics.Color.parseColor("#111315"), bitmap.getPixel(1, bitmap.height / 2))
    }
    private fun home(theme: ThemeMode) {
        val model = TaskViewModel(app)
        rule.setContent { TasksTheme(theme, false) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TasksHome(sample, Preferences(theme = theme, selectedList = ALL_TASKS_TAB), false, model, {}, {}, {}) } } }
        rule.onNodeWithText("Plan the weekend").assertIsDisplayed()
        rule.onAllNodesWithText("All").assertCountEquals(1)
        rule.onNodeWithText("No date").assertIsDisplayed()
        rule.onAllNodesWithText("Today").onFirst().assertIsDisplayed()
    }
    @Test fun lightThemeRendersTaskCardsAndCompletedSection() { home(ThemeMode.LIGHT); capture("home-light") }
    @Test fun darkThemeRendersTaskCardsAndCompletedSection() { home(ThemeMode.DARK); capture("home-dark") }
    @Test fun amoledThemeUsesPureBlackWithoutLosingContent() { home(ThemeMode.AMOLED); capture("home-amoled") }
    @Test fun amoledKeepsBlackSurfacesWithWallpaperColors() {
        var colors: androidx.compose.material3.ColorScheme? = null
        rule.setContent { TasksTheme(ThemeMode.AMOLED, true) {
            colors = MaterialTheme.colorScheme
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { }
        } }
        rule.waitForIdle()
        assertEquals(androidx.compose.ui.graphics.Color.Black, colors!!.background)
        assertEquals(androidx.compose.ui.graphics.Color.Black, colors!!.surface)
        assertEquals(androidx.compose.ui.graphics.Color.Black, colors!!.surfaceContainerLowest)
        assertEquals(androidx.compose.ui.graphics.Color.Black, colors!!.surfaceTint)
    }
    @Test fun customOrderAndTabCustomizationRender() {
        val model = TaskViewModel(app)
        rule.setContent { TasksTheme(ThemeMode.DARK, false) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TasksHome(sample, Preferences(summarySort = SortMode.MY_ORDER), false, model, {}, {}, {}) } } }
        rule.onNodeWithText("Plan the weekend").assertIsDisplayed(); capture("home-custom-dark")
        rule.onNodeWithContentDescription("Summary options").performClick(); rule.onNodeWithText("Customize tab").performClick()
        rule.onNodeWithContentDescription("purple tab color").assertIsDisplayed(); capture("tab-customization-dark", true)
    }
    @Test fun settingsAndTaskEditorRender() {
        val model = TaskViewModel(app)
        var settings by mutableStateOf(false)
        rule.setContent { TasksTheme(ThemeMode.DARK, false) {
            if (settings) SettingsScreen(Preferences(theme = ThemeMode.DARK), SyncStatus(), model, {})
            else TaskEditor(sample.tasks.first(), sample, { _, _, _ -> settings = true }, { settings = true }, {}, { _, _ -> })
        } }
        rule.onNodeWithText("Add deadline").assertIsDisplayed(); capture("task-editor-dark")
        rule.onNodeWithContentDescription("Save and go back").performClick()
        rule.onNodeWithText("Only while charging").performScrollTo().assertIsDisplayed(); capture("settings-dark")
        rule.onNodeWithText("Made with ♥\nAhnaf Hasan Shafin").performScrollTo().assertIsDisplayed(); capture("settings-footer-dark")
    }
    @Test fun subtasksAreAddedInlineAndSurviveStateRestorationOnRepeatingTasks() {
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(rule)
        var saved: List<Task>? = null
        val parent = sample.tasks.first().copy(recurrence = "FREQ=DAILY", dueDate = java.time.LocalDate.now().toString())
        restoration.setContent { TasksTheme(ThemeMode.LIGHT, false) {
            QuickAdd(parent, sample.lists, {}) { _, children -> saved = children }
        } }
        rule.onAllNodesWithContentDescription("Subtask title").assertCountEquals(0)
        rule.onNodeWithText("Add subtask").performClick()
        rule.onNode(hasSetTextAction() and hasText(parent.title)).performClick()
        rule.onNodeWithText("Add subtask").performClick()
        rule.onNodeWithContentDescription("Subtask title").assertIsFocused().performTextInput("Choose a trail")
        rule.onNodeWithContentDescription("Subtask title").performImeAction()
        rule.onAllNodesWithContentDescription("Subtask title").onLast().performTextInput("Pack a picnic")
        restoration.emulateSavedInstanceStateRestore()
        rule.onAllNodesWithContentDescription("Subtask title").assertCountEquals(2)
        rule.onNodeWithText("Choose a trail").assertExists()
        rule.onNodeWithText("Pack a picnic").assertExists()
        rule.onNodeWithText("Save and go back").assertDoesNotExist()
        capture("subtasks-inline-light", true)
        rule.onNodeWithText("Save").performScrollTo().performClick()
        assertEquals(listOf("Choose a trail", "Pack a picnic"), saved!!.map { it.title })
        assertTrue(saved!!.all { it.parentId == parent.id })
    }
    @Test fun checkedInlineSubtasksStayInTheListWithStrikethrough() {
        val parent = sample.tasks.first().copy(recurrence = "FREQ=DAILY")
        val first = Task(id = "step-one", listId = parent.listId, title = "Choose a trail", parentId = parent.id, position = 0)
        val second = first.copy(id = "step-two", title = "Pack a picnic", position = 1024)
        var saved: List<Task>? = null
        var closed = false
        rule.setContent { TasksTheme(ThemeMode.DARK, false) {
            TaskEditor(parent, sample.copy(tasks = sample.tasks + listOf(first, second)),
                { _, children, close -> saved = children; closed = close }, {}, {}, { _, _ -> })
        } }
        rule.onNodeWithContentDescription("Complete Choose a trail").performClick()
        rule.onNodeWithText("Choose a trail").assertIsDisplayed()
        rule.onNodeWithContentDescription("Mark incomplete Choose a trail").assertIsDisplayed()
        assertFalse(closed); assertTrue(saved!!.first().completed); assertFalse(saved!!.last().completed)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        rule.onAllNodesWithContentDescription("Subtask title").onFirst().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(androidx.compose.ui.text.style.TextDecoration.LineThrough, layouts.single().layoutInput.style.textDecoration)
        capture("subtasks-inline-dark")
        rule.onNodeWithContentDescription("Complete Pack a picnic").performClick()
        assertTrue(closed); assertTrue(saved!!.all { it.completed })
    }
    @Test fun tabsRemainLeftAlignedAndStationaryDuringVisiblePageSwipes() {
        home(ThemeMode.LIGHT)
        rule.onAllNodesWithText("All tasks").assertCountEquals(0)
        rule.onAllNodesWithText("Every list", substring = true).assertCountEquals(0)
        rule.onNodeWithContentDescription("All tab").assertIsSelected()
        val initial = rule.onNodeWithContentDescription("All tab").fetchSemanticsNode().boundsInRoot
        val before = rule.onNodeWithContentDescription("Starred tab").fetchSemanticsNode().boundsInRoot
        assertTrue("Tabs start at the left edge", initial.left < with(rule.density) { 12.dp.toPx() })
        rule.onNodeWithText("Plan the weekend").performTouchInput { swipeLeft() }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        rule.onNodeWithContentDescription("Starred tab").assertIsSelected()
        val bounds = rule.onNodeWithContentDescription("Starred tab").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, bounds.left, 1f)
        rule.onNodeWithText("Plan the weekend").performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(1000); rule.waitForIdle()
        rule.onNodeWithContentDescription("Personal tab").assertIsSelected()
        val next = rule.onNodeWithContentDescription("Personal tab").fetchSemanticsNode().boundsInRoot
        assertTrue("Selected tab remains visible", next.left >= 0 && next.right <= root.right)
    }

    @Test fun weeklyRepeatEditorKeepsStartTimeWeekdaysAndOccurrenceLimit() {
        var result: Triple<String, String, String?>? = null
        rule.setContent { TasksTheme(ThemeMode.DARK, false) {
            CustomRepeatDialog("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR;COUNT=8", "2026-10-19", "08:30", {}, { value, date, time -> result = Triple(value, date, time) })
        } }
        rule.onNodeWithText("Repeat schedule").assertIsDisplayed()
        rule.onNodeWithText("08:30").assertIsDisplayed()
        rule.onNodeWithContentDescription("Monday").assertIsSelected()
        rule.onNodeWithContentDescription("Friday").assertIsSelected()
        rule.onNode(hasSetTextAction() and hasContentDescription("Interval")).performTextReplacement("3")
        capture("repeat-weekly-dark", true)
        rule.onNodeWithText("Done").performScrollTo().performClick()
        assertEquals(Triple("FREQ=WEEKLY;INTERVAL=3;BYDAY=MO,FR;COUNT=8", "2026-10-19", "08:30"), result)
    }

    @Test fun laterTabsCenterWhileTheLeadingTabsStayLeftAligned() {
        val model = TaskViewModel(app)
        val lists = (1..8).map { TaskList(id = "list-$it", name = "List $it") }
        rule.setContent { TasksTheme(ThemeMode.LIGHT, false) { Surface(Modifier.fillMaxSize()) {
            TasksHome(Snapshot(lists), Preferences(selectedList = ALL_TASKS_TAB), false, model, {}, {}, {})
        } } }
        val first = rule.onNodeWithContentDescription("All tab").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left < with(rule.density) { 12.dp.toPx() })
        repeat(5) { rule.onNodeWithTag("task-pages").performTouchInput { swipeLeft() } }
        rule.mainClock.advanceTimeBy(500); rule.waitForIdle()
        rule.onNodeWithContentDescription("List 4 tab").assertIsSelected()
        val later = rule.onNodeWithContentDescription("List 4 tab").fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, later.center.x, 3f)
        capture("tabs-later-centered-light")
    }
    @Test fun liquidLiftRendersAndSettlesBackWithoutWritingAnUnchangedOrder() {
        val tasks = sample.tasks.take(2)
        var saved = false
        val drag = TaskDragState { saved = true }
        rule.setContent { TasksTheme(ThemeMode.DARK, false) {
            val list = rememberLazyListState()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.padding(32.dp)) {
                    Surface(Modifier.fillMaxWidth().taskDragVisual(tasks[0], tasks, drag, list), shape = RoundedCornerShape(16.dp), color = taskDragContainerColor(tasks[0].id, drag)) {
                        TaskRow(tasks[0], false, "Personal", {}, {}, {})
                    }
                }
            }
        } }
        rule.runOnIdle { drag.start(tasks[0], tasks, 0, 120, 50f, .3f) }
        capture("task-lift-held-dark")
        rule.runOnIdle { drag.end() }
        capture("task-lift-rest-dark")
        assertFalse(saved)
        assertFalse(File("build/previews/task-lift-held-dark.png").readBytes().contentEquals(File("build/previews/task-lift-rest-dark.png").readBytes()))
    }

    @Test fun shadedDarkUsesNonBlackCanvasWithLighterCardsAndTabs() {
        var colors: androidx.compose.material3.ColorScheme? = null
        var wallpaper by mutableStateOf(false)
        rule.setContent { TasksTheme(ThemeMode.DARK, wallpaper) {
            colors = MaterialTheme.colorScheme
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { }
        } }
        fun check() {
            rule.waitForIdle()
            val palette = colors!!
            val surfaces = listOf(palette.background, palette.surface, palette.surfaceDim, palette.surfaceBright,
                palette.surfaceContainerLowest, palette.surfaceContainerLow, palette.surfaceContainer,
                palette.surfaceContainerHigh, palette.surfaceContainerHighest, palette.surfaceVariant)
            assertTrue(surfaces.all { it != androidx.compose.ui.graphics.Color.Black })
            assertTrue(palette.surface.luminance() > palette.background.luminance())
            assertTrue(palette.surfaceContainerLow.luminance() > palette.background.luminance())
        }
        check(); rule.runOnIdle { wallpaper = true }; check()
    }
    @Test fun parentAndSubtasksLiftAndFollowTheSameDragTogether() {
        val parent = Task(id = "lift-parent", listId = "my-tasks", title = "Plan the weekend")
        val child = Task(id = "lift-child", listId = parent.listId, title = "Choose a trail", parentId = parent.id)
        val sibling = child.copy(id = "lift-sibling", title = "Pack a picnic")
        val tasks = listOf(parent, child, sibling, parent.copy(id = "other-root", title = "Water the plants"))
        var saved = false
        val drag = TaskDragState { saved = true }
        lateinit var list: LazyListState
        rule.setContent { TasksTheme(ThemeMode.DARK, false) {
            list = rememberLazyListState()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                LazyColumn(Modifier.padding(24.dp), state = list) {
                    items(tasks, key = { it.id }) { task ->
                        Surface(Modifier.fillMaxWidth().taskDragVisual(task, tasks, drag, list), color = taskDragContainerColor(task.id, drag)) {
                            TaskRow(task, task.parentId != null, null, {}, {}, {})
                        }
                    }
                }
            }
        } }
        val before = tasks.associate { it.id to rule.onNodeWithText(it.title).fetchSemanticsNode().boundsInRoot.center.y }
        val movement = with(rule.density) { 36.dp.toPx() }
        fun frame(name: String): Bitmap = rule.runOnIdle {
            val view = rule.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                view.draw(Canvas(bitmap))
                File("build/previews/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle {
            val row = list.layoutInfo.visibleItemsInfo.first { it.key == parent.id }
            drag.start(parent, tasks, row.offset, row.size, row.offset + row.size / 2f)
            drag.dragBy(movement, list.layoutInfo)
        }
        rule.mainClock.advanceTimeBy(176)
        val early = frame("family-wave-expanding-dark")
        rule.mainClock.advanceTimeBy(240)
        val late = frame("family-wave-expanded-dark")
        val lastRow = rule.onNodeWithText(sibling.title).fetchSemanticsNode().boundsInRoot
        val x = (lastRow.right - with(rule.density) { 64.dp.toPx() }).toInt()
        val y = (lastRow.bottom - with(rule.density) { 8.dp.toPx() }).toInt()
        assertTrue("The wave spreads to the last subtask after the lift is settled",
            android.graphics.Color.blue(late.getPixel(x, y)) > android.graphics.Color.blue(early.getPixel(x, y)))
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        for (task in listOf(parent, child, sibling)) {
            val displacement = rule.onNodeWithText(task.title).fetchSemanticsNode().boundsInRoot.center.y - before.getValue(task.id)
            assertTrue("${task.title} follows the lifted family", displacement > movement * .8f)
            assertEquals(movement, displacement, with(rule.density) { 8.dp.toPx() })
        }
        assertEquals(before.getValue("other-root"), rule.onNodeWithText("Water the plants").fetchSemanticsNode().boundsInRoot.center.y, 1f)
        capture("family-lift-held-dark")
        rule.runOnIdle { drag.cancel(list.layoutInfo) }
        capture("family-lift-rest-dark")
        assertFalse(saved)
    }

    @Test fun repeatedSwipesAtBothListEndsKeepTheContentStationary() {
        var overscroll: androidx.compose.foundation.OverscrollFactory? = null
        lateinit var list: LazyListState
        rule.setContent { TasksTheme(ThemeMode.DARK, false) {
            overscroll = androidx.compose.foundation.LocalOverscrollFactory.current
            list = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().testTag("edge-list"), state = list) {
                items((0..40).toList()) { index -> androidx.compose.material3.Text("Row $index", Modifier.fillMaxWidth().height(48.dp)) }
            }
        } }
        rule.waitForIdle(); assertNull(overscroll)
        val start = rule.onNodeWithText("Row 0").fetchSemanticsNode().boundsInRoot.top
        repeat(3) { rule.onNodeWithTag("edge-list").performTouchInput { swipeDown() }; rule.mainClock.advanceTimeBy(500) }
        assertEquals(start, rule.onNodeWithText("Row 0").fetchSemanticsNode().boundsInRoot.top, 1f)
        rule.onNodeWithTag("edge-list").performScrollToIndex(40)
        val end = rule.onNodeWithText("Row 40").fetchSemanticsNode().boundsInRoot.bottom
        repeat(3) { rule.onNodeWithTag("edge-list").performTouchInput { swipeUp() }; rule.mainClock.advanceTimeBy(500) }
        assertEquals(end, rule.onNodeWithText("Row 40").fetchSemanticsNode().boundsInRoot.bottom, 1f)
        assertFalse(list.canScrollForward)
    }

}
