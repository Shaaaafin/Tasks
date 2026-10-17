package dev.quiettasks.ui

import android.os.Looper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.quiettasks.MainActivity
import dev.quiettasks.TasksApplication
import dev.quiettasks.data.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val app get() = rule.activity.application as TasksApplication
    private fun settle(condition: () -> Boolean) {
        rule.waitUntil(10000) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100)); condition() }
        rule.waitForIdle()
    }
    private fun verifySubtaskCompletion() {
        app.store.writableDatabase.delete("tasks", null, null)
        val list = app.store.snapshot().lists.first { !it.deleted }
        val parent = Task(id = "family", listId = list.id, title = "Weekend plan")
        val a = Task(id = "step-a", listId = list.id, title = "Choose a trail", parentId = parent.id, position = 0)
        val b = a.copy(id = "step-b", title = "Pack a picnic", position = 1024)
        app.store.saveTaskFamily(parent, listOf(a, b))
        settle { app.store.state.value.tasks.size == 3 }
        rule.onNodeWithContentDescription("Complete Choose a trail").performClick()
        settle { app.store.state.value.tasks.first { it.id == a.id }.completed }
        rule.onNodeWithText("Choose a trail").assertIsDisplayed()
        rule.onNodeWithContentDescription("Mark Choose a trail incomplete").assertIsDisplayed()
        rule.onAllNodesWithText("Completed", substring = true).assertCountEquals(0)
        rule.onNodeWithContentDescription("Complete Pack a picnic").performClick()
        settle { app.store.state.value.tasks.all { it.completed } }
        rule.onNodeWithText("Completed (1)").assertIsDisplayed()
        rule.onNodeWithText("Choose a trail").assertDoesNotExist()
        rule.onNodeWithText("Completed (1)").performClick()
        rule.onNodeWithText("Weekend plan").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Mark incomplete Choose a trail").assertIsDisplayed()
        rule.onNodeWithContentDescription("Mark incomplete Pack a picnic").assertIsDisplayed()
        rule.onNodeWithContentDescription("Save and go back").performClick()
        rule.waitUntil(10000) { rule.onAllNodesWithContentDescription("Create task").fetchSemanticsNodes().isNotEmpty() }
        app.store.writableDatabase.delete("tasks", null, null)
        runBlocking { app.store.load() }
    }
    @Test fun addingStarringCompletingAndChangingThemeWorkOffline() {
        Class.forName("androidx.test.espresso.IdlingPolicies").getMethod("setMasterPolicyTimeout", java.lang.Long.TYPE, java.util.concurrent.TimeUnit::class.java)
            .invoke(null, 15L, java.util.concurrent.TimeUnit.SECONDS)
        app.store.writableDatabase.delete("tasks", null, null)
        runBlocking { app.store.load() }
        settle { app.store.state.value.lists.isNotEmpty() }
        verifySubtaskCompletion()
        rule.onNodeWithContentDescription("Create task").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("A new offline task")
        rule.onNodeWithText("Priority: None").performClick(); rule.onNodeWithText("High").performClick()
        rule.mainClock.advanceTimeBy(500)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
        rule.onNodeWithText("Add tags").performClick()
        rule.onNode(hasSetTextAction() and hasContentDescription("Add tags")).performTextInput("work, errands")
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Save").assertIsEnabled().performClick()
        settle { app.store.state.value.tasks.any { it.title == "A new offline task" } }
        assertEquals(TaskPriority.HIGH, app.store.state.value.tasks.single().priority)
        assertEquals(listOf("work", "errands"), app.store.state.value.tasks.single().tags)
        rule.onNodeWithContentDescription("Create task").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Second task")
        rule.onNodeWithText("Add subtask").assertIsEnabled().performClick()
        rule.onNodeWithContentDescription("Subtask title").assertIsDisplayed()
        rule.onNode(hasSetTextAction() and isFocused()).performTextInput("First subtask")
        rule.onNodeWithText("Save").performClick()
        settle { app.store.state.value.tasks.size == 3 }
        rule.waitUntil(10000) { rule.onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("First subtask").assertIsDisplayed()
        rule.onNodeWithContentDescription("Save and go back").assertDoesNotExist()
        rule.onNodeWithContentDescription("Sort tasks").performClick(); rule.onNodeWithText("My order").performClick()
        settle { app.settings.state.value.summarySort == SortMode.MY_ORDER }
        val distance = with(rule.density) { 160.dp.toPx() }
        rule.onNodeWithText("A new offline task").performTouchInput {
            down(center); advanceEventTime(650); moveBy(Offset(0f, 1f)); advanceEventTime(32); moveBy(Offset(0f, distance)); up()
        }
        rule.onNodeWithTag("task-menu").assertDoesNotExist()
        settle { app.store.state.value.tasks.first { it.title == "Second task" }.position < app.store.state.value.tasks.first { it.title == "A new offline task" }.position }
        for (title in listOf("Second subtask")) {
            rule.onNodeWithText("Second task").performClick()
            rule.onNodeWithText("Subtasks").assertIsDisplayed()
            rule.onNodeWithText("Add subtask").assertIsDisplayed().performClick()
            rule.onNode(hasSetTextAction() and hasContentDescription("Subtask title") and isFocused()).performTextInput(title)
            rule.onNodeWithText("Save").assertDoesNotExist()
            rule.onNodeWithContentDescription("Save and go back").performClick()
            settle { app.store.state.value.tasks.any { it.title == title && it.parentId != null } }
            rule.waitUntil(10000) { rule.onAllNodesWithContentDescription("Create task").fetchSemanticsNodes().isNotEmpty() }
        }
        val subtaskDistance = with(rule.density) { 90.dp.toPx() }
        rule.onNodeWithText("Second subtask").performTouchInput {
            down(center); advanceEventTime(650); moveBy(Offset(0f, -1f)); advanceEventTime(32); moveBy(Offset(0f, -subtaskDistance)); up()
        }
        settle { app.store.state.value.tasks.first { it.title == "Second subtask" }.position < app.store.state.value.tasks.first { it.title == "First subtask" }.position }
        assertEquals(app.store.state.value.tasks.first { it.title == "Second task" }.id, app.store.state.value.tasks.first { it.title == "Second subtask" }.parentId)
        rule.onNodeWithContentDescription("Filter by tag").performClick(); rule.onAllNodesWithText("#work").onLast().performClick()
        rule.onNodeWithText("Second task").assertDoesNotExist()
        rule.onNodeWithContentDescription("Filter by tag: work").performClick(); rule.onNodeWithText("All tags").performClick()
        rule.onNodeWithContentDescription("Summary options").performClick(); rule.onNodeWithText("Customize tab").performClick()
        rule.onNodeWithContentDescription("purple tab color").performClick()
        rule.onNode(hasSetTextAction() and hasContentDescription("Your emoji")).performTextInput("📋")
        rule.onNodeWithText("Done").performClick()
        settle { app.settings.state.value.allTabColor == "purple" && app.settings.state.value.allTabIcon == "📋" }
        rule.onNodeWithContentDescription("Create task").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("Daily repeat")
        rule.onNodeWithContentDescription("Repeat task").performClick(); rule.onNodeWithText("Daily").performClick()
        rule.onNodeWithText("Repeat schedule").assertIsDisplayed()
        rule.onNode(hasSetTextAction() and hasContentDescription("Occurrences")).performTextReplacement("3")
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Save").performClick()
        settle { app.store.state.value.tasks.any { it.title == "Daily repeat" && it.recurrence == "FREQ=DAILY;INTERVAL=1;COUNT=3" && it.dueDate != null } }
        rule.onNodeWithContentDescription("Star A new offline task").performClick()
        settle { app.store.state.value.tasks.any { it.title == "A new offline task" && it.starred } }
        rule.onNodeWithContentDescription("Complete A new offline task").performClick()
        settle { app.store.state.value.tasks.any { it.title == "A new offline task" && it.completed } }
        rule.onNodeWithText("Completed (1)").assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Long press").performClick(); rule.onNodeWithText("Show task menu").performClick()
        settle { app.settings.state.value.longPressAction == LongPressAction.MENU }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Second task").performTouchInput { longClick() }
        rule.onNodeWithTag("task-menu").assertIsDisplayed()
        rule.onNodeWithText("Edit").assertIsDisplayed()
        rule.onNodeWithText("Archive").performClick()
        settle { app.store.state.value.tasks.any { it.title == "Second task" && it.archived } }
        rule.onNodeWithText("Second task").assertDoesNotExist()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithContentDescription("Show All tab").performClick()
        settle { !app.settings.state.value.showAllTab }
        rule.onNodeWithContentDescription("Show Starred tab").performClick()
        settle { !app.settings.state.value.showStarredTab }
        rule.onNodeWithText("Archived tasks").performClick()
        rule.onNodeWithContentDescription("Restore Second task").performClick()
        settle { app.store.state.value.tasks.none { it.archived } }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithContentDescription("All tab").assertDoesNotExist()
        rule.onNodeWithContentDescription("Starred tab").assertDoesNotExist()
        rule.onNodeWithText("Second task").performTouchInput { longClick() }
        rule.onNodeWithText("Delete").performClick()
        rule.onNodeWithText("Delete").performClick()
        settle { app.store.state.value.tasks.any { it.title == "Second task" && it.deleted } }
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Theme").performClick(); rule.onNodeWithText("Dark (shaded)").performClick()
        settle { app.settings.state.value.theme == ThemeMode.DARK }
        rule.onNodeWithText("Theme").performClick(); rule.onNodeWithText("AMOLED (pure black)").performClick()
        settle { app.settings.state.value.theme == ThemeMode.AMOLED }
    }
}
