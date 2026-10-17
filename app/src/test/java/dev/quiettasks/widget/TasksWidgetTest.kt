package dev.quiettasks.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import android.widget.ListView
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.quiettasks.R
import dev.quiettasks.TasksApplication
import dev.quiettasks.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
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
class TasksWidgetTest {
    @Test fun checkedSubtasksStayVisibleUntilTheirParentCompletes() {
        val app = ApplicationProvider.getApplicationContext<TasksApplication>()
        val list = TaskList(id = "list", name = "List")
        val parent = Task(id = "parent", listId = list.id, title = "Parent", position = 0)
        val checked = Task(id = "checked", listId = list.id, title = "Checked", parentId = parent.id, completedAt = 1, position = 1024)
        val pending = checked.copy(id = "pending", title = "Pending", completedAt = null, position = 2048)
        val snapshot = Snapshot(listOf(list), listOf(parent, checked, pending))
        assertEquals(listOf("parent", "checked", "pending"), widgetTasks(snapshot, Preferences(), ALL_TASKS_TAB).map { it.id })
        val row = widgetRows(app, snapshot, Preferences(), ALL_TASKS_TAB, false, android.graphics.Color.BLACK)[1].apply(app, null)
        assertTrue(row.findViewById<TextView>(R.id.widget_task_title).paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG != 0)
        assertEquals("Mark incomplete Checked", row.findViewById<View>(R.id.widget_check).contentDescription)
        val completed = snapshot.copy(tasks = snapshot.tasks.map { it.copy(completedAt = 1) })
        assertTrue(widgetTasks(completed, Preferences(), ALL_TASKS_TAB).isEmpty())
    }
    @Test fun widgetSubtasksHaveTheirOwnIndentBranchMarkerAndSmallerTitle() {
        val app = ApplicationProvider.getApplicationContext<TasksApplication>()
        val list = TaskList(id = "list", name = "List")
        val parent = Task(id = "parent", listId = list.id, title = "Weekend plan", position = 0)
        val child = Task(id = "child", listId = list.id, title = "Choose a trail", parentId = parent.id, position = 1024)
        val snapshot = Snapshot(listOf(list), listOf(parent, child))
        val rows = widgetRows(app, snapshot, Preferences(), ALL_TASKS_TAB, false, android.graphics.Color.WHITE)
        val root = rows[0].apply(app, null); val nested = rows[1].apply(app, null)
        fun measure(view: View) {
            view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(140, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1000, 140)
        }
        measure(root); measure(nested)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_subtask_branch).visibility)
        assertEquals(View.VISIBLE, nested.findViewById<View>(R.id.widget_subtask_branch).visibility)
        assertTrue(nested.findViewById<View>(R.id.widget_check).left > root.findViewById<View>(R.id.widget_check).left)
        assertTrue(nested.findViewById<TextView>(R.id.widget_task_title).textSize < root.findViewById<TextView>(R.id.widget_task_title).textSize)
        assertEquals("Choose a trail", nested.findViewById<TextView>(R.id.widget_task_title).text.toString())
        assertEquals("Subtask of Weekend plan: Choose a trail", nested.findViewById<TextView>(R.id.widget_task_title).contentDescription)
        val image = Bitmap.createBitmap(1000, 280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image); canvas.drawColor(android.graphics.Color.parseColor("#1E1F20"))
        root.draw(canvas); canvas.translate(0f, 140f); nested.draw(canvas)
        File("build/previews/widget-hierarchy-dark.png").apply { parentFile!!.mkdirs() }.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun rendersSelectedListAndOnlyUncompletedTasks() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<TasksApplication>()
        app.store.load()
        app.store.writableDatabase.delete("tasks", null, null)
        app.store.load()
        val list = app.store.snapshot().lists.first { !it.deleted }
        app.store.saveTask(Task(id = "widget-active", listId = list.id, title = "Pick up groceries", position = 0))
        app.store.saveTask(Task(id = "widget-done", listId = list.id, title = "Already done", completedAt = 1, position = 1))
        assertEquals(R.layout.widget_tasks_collection, TasksWidget.createViews(app, app.store.snapshot(), Preferences(), 123).first.layoutId)
        assertNotEquals(R.layout.widget_tasks, R.layout.widget_tasks_collection)
        val manager = AppWidgetManager.getInstance(app)
        val shadow = Shadows.shadowOf(manager)
        val id = shadow.createWidget(TasksWidget::class.java, R.layout.widget_tasks)
        app.getSharedPreferences("widgets", Application.MODE_PRIVATE).edit().putString(id.toString(), list.id).commit()
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.AMOLED)) {
            app.settings.update { it.copy(theme = theme, deviceColors = false) }
            TasksWidget.update(app, app.store.snapshot(), Preferences(theme = theme, deviceColors = false), intArrayOf(id))
            val view = AppWidgetHostView(app).apply {
                setAppWidget(id, AppWidgetProviderInfo())
                addView(TasksWidget.createViews(app, app.store.snapshot(), app.settings.state.value, id).first.apply(app, this))
            }.getChildAt(0)
            assertTrue(view.findViewById<TextView>(R.id.widget_list).text.startsWith(list.name))
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val width = 1000; val height = 520
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            assertEquals(View.OVER_SCROLL_NEVER, view.findViewById<ListView>(R.id.widget_rows).overScrollMode)
            assertEquals(1, view.findViewById<ListView>(R.id.widget_rows).adapter.count)
            val collection = view.findViewById<ListView>(R.id.widget_rows)
            assertTrue("At least one task is visible", collection.childCount > 0)
            assertEquals("Pick up groceries", collection.getChildAt(0).findViewById<TextView>(R.id.widget_task_title).text.toString())
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            if (theme == ThemeMode.AMOLED) assertEquals(android.graphics.Color.BLACK, image.getPixel(10, height / 2))
            if (theme == ThemeMode.DARK) assertEquals(android.graphics.Color.parseColor("#1E1F20"), image.getPixel(10, height / 2))
            val output = File("build/previews/widget-${theme.name.lowercase()}.png").apply { parentFile!!.mkdirs() }
            output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(output.length() > 3000)
        }
        repeat(40) { index -> app.store.saveTask(Task(id = "row-$index", listId = list.id, title = "Task $index", position = index + 10L)) }
        app.store.saveTask(Task(id = "widget-archived", listId = list.id, title = "Archived", archived = true))
        for (height in listOf(420, 1100)) {
            TasksWidget.update(app, app.store.snapshot(), Preferences(deviceColors = false), intArrayOf(id))
            val view = AppWidgetHostView(app).apply {
                setAppWidget(id, AppWidgetProviderInfo())
                addView(TasksWidget.createViews(app, app.store.snapshot(), app.settings.state.value, id).first.apply(app, this))
            }.getChildAt(0)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            view.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1000, height)
            val rows = view.findViewById<ListView>(R.id.widget_rows)
            assertEquals(41, rows.adapter.count)
            assertEquals("Collection fills resized body", (rows.parent as View).height, rows.height)
            assertTrue(rows.height > 0)
            rows.setSelection(40); rows.layout(0, rows.top, rows.width, rows.bottom)
            assertEquals("Task 39", (rows.adapter.getView(40, null, rows)).findViewById<TextView>(R.id.widget_task_title).text.toString())
            val bitmap = Bitmap.createBitmap(1000, height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
            File("build/previews/widget-resized-$height.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        app.getSharedPreferences("widgets", Application.MODE_PRIVATE).edit().putBoolean("picker_$id", true).commit()
        TasksWidget.update(app, app.store.snapshot(), Preferences(deviceColors = false), intArrayOf(id))
        val picker = AppWidgetHostView(app).apply { setAppWidget(id, AppWidgetProviderInfo()); addView(TasksWidget.createViews(app, app.store.snapshot(), Preferences(deviceColors = false), id).first.apply(app, this)) }.getChildAt(0)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        picker.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY)); picker.layout(0, 0, 1000, 700)
        assertTrue(picker.findViewById<TextView>(R.id.widget_list).text.startsWith("Choose list"))
        val options = picker.findViewById<ListView>(R.id.widget_rows)
        assertEquals(widgetChoices(app.store.snapshot()).size, options.adapter.count)
        assertEquals("All", options.adapter.getView(0, null, options).findViewById<TextView>(R.id.widget_option).text.toString())
        TasksWidget().onReceive(app, Intent(app, TasksWidget::class.java).setAction(TasksWidget.ROW).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("widgetAction", "select").putExtra("listId", ALL_TASKS_TAB))
        repeat(100) {
            if (app.getSharedPreferences("widgets", Application.MODE_PRIVATE).getString(id.toString(), null) != ALL_TASKS_TAB) Thread.sleep(10)
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        assertEquals(ALL_TASKS_TAB, app.getSharedPreferences("widgets", Application.MODE_PRIVATE).getString(id.toString(), null))
        assertFalse(app.getSharedPreferences("widgets", Application.MODE_PRIVATE).getBoolean("picker_$id", true))
        val other = TaskList(id = "other", name = "Other"); app.store.saveList(other)
        app.store.saveTask(Task(id = "other-task", listId = other.id, title = "Other list task", starred = true))
        assertEquals(42, widgetTasks(app.store.snapshot(), Preferences(), ALL_TASKS_TAB).size)
        assertEquals(listOf("other-task"), widgetTasks(app.store.snapshot(), Preferences(), STARRED_TAB).map { it.id })
        repeat(110) { index -> app.store.saveTask(Task(id = "large-$index", listId = list.id, title = "Large $index")) }
        val service = org.robolectric.Robolectric.buildService(WidgetRowsService::class.java).create().get()
        val factory = service.onGetViewFactory(Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); factory.onCreate()
        assertEquals(152, factory.count)
        assertNotNull(factory.getViewAt(151)); assertNull(factory.getViewAt(152))
        factory.onDestroy()
        val info = app.resources.getXml(R.xml.tasks_widget)
        while (info.eventType != org.xmlpull.v1.XmlPullParser.START_TAG && info.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) info.next()
        assertEquals("appwidget-provider", info.name)
        assertEquals(0, info.getAttributeIntValue("http://schemas.android.com/apk/res/android", "updatePeriodMillis", -1))
        info.close()
    }
}
