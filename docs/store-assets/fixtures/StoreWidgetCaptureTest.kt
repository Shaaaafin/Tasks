package dev.quiettasks.widget

import android.app.Application
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import dev.quiettasks.R
import dev.quiettasks.TasksApplication
import dev.quiettasks.data.*
import kotlinx.coroutines.runBlocking
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
class StoreWidgetCaptureTest {
    @Test fun captureStoreWidgetSamples() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<TasksApplication>()
        app.store.load()
        app.store.writableDatabase.delete("tasks", null, null)
        app.store.load()
        val personal = app.store.snapshot().lists.first { !it.deleted }.copy(name = "Personal")
        val shopping = TaskList(id = "store-shopping", name = "Shopping")
        app.store.saveList(personal)
        app.store.saveList(shopping)
        app.store.saveTaskFamily(
            Task(id = "store-weekend", listId = personal.id, title = "Plan the weekend", position = 0),
            listOf(
                Task(id = "store-trail", listId = personal.id, title = "Choose a trail", parentId = "store-weekend", completedAt = 1, position = 1024),
                Task(id = "store-picnic", listId = personal.id, title = "Pack a picnic", parentId = "store-weekend", position = 2048)
            )
        )
        app.store.saveTask(Task(id = "store-read", listId = personal.id, title = "Read for 20 minutes", position = 3072))
        app.store.saveTask(Task(id = "store-groceries", listId = shopping.id, title = "Pick up groceries", position = 4096))
        val manager = AppWidgetManager.getInstance(app)
        val id = Shadows.shadowOf(manager).createWidget(TasksWidget::class.java, R.layout.widget_tasks)
        val widgetPreferences = app.getSharedPreferences("widgets", Application.MODE_PRIVATE)
        widgetPreferences.edit().putString(id.toString(), ALL_TASKS_TAB).commit()

        fun capture(name: String, height: Int = 1100) {
            val preferences = app.settings.state.value
            TasksWidget.update(app, app.store.snapshot(), preferences, intArrayOf(id))
            val host = AppWidgetHostView(app).apply { setAppWidget(id, AppWidgetProviderInfo()) }
            val view = TasksWidget.createViews(app, app.store.snapshot(), preferences, id).first.apply(app, host)
            host.addView(view)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val width = 1000
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/previews/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }

        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            app.settings.update { it.copy(theme = theme, deviceColors = false) }
            capture("store-widget-${theme.name.lowercase()}")
        }
        widgetPreferences.edit().putBoolean("picker_$id", true).commit()
        capture("store-widget-picker-dark", height = 480)
    }
}
