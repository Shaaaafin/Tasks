package dev.quiettasks.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import dev.quiettasks.MainActivity
import dev.quiettasks.R
import dev.quiettasks.TasksApplication
import dev.quiettasks.data.*
import dev.quiettasks.ui.formatDue
import kotlinx.coroutines.*

class TasksWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { refresh(context, ids) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) { refresh(context, intArrayOf(id)) }
    private fun refresh(context: Context, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { val app = context.applicationContext as TasksApplication; val snapshot = app.store.snapshot(); withContext(Dispatchers.Main) { update(context, snapshot, app.settings.state.value, ids) } }
            finally { pending?.finish() }
        }
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(COMPLETE, PICK, ROW)) { super.onReceive(context, intent); return }
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val registered = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TasksWidget::class.java))
        if (widgetId !in registered) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as TasksApplication
                val preferences = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
                when (intent.action) {
                    PICK -> preferences.edit().putBoolean("picker_$widgetId", !preferences.getBoolean("picker_$widgetId", false)).apply()
                    COMPLETE -> intent.getStringExtra("taskId")?.let(app.store::toggle)
                    ROW -> when (intent.getStringExtra("widgetAction")) {
                        "select" -> {
                            val selected = intent.getStringExtra("listId")
                            if (widgetChoices(app.store.snapshot()).any { it.first == selected }) preferences.edit().putString(widgetId.toString(), selected).putBoolean("picker_$widgetId", false).apply()
                        }
                        "complete" -> intent.getStringExtra("taskId")?.let(app.store::toggle)
                        "open" -> context.startActivity(Intent(context, MainActivity::class.java).putExtra("taskId", intent.getStringExtra("taskId")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    }
                }
                val snapshot = app.store.snapshot()
                withContext(Dispatchers.Main) { updateAll(context, snapshot, app.settings.state.value) }
            } finally { pending?.finish() }
        }
    }
    override fun onDeleted(context: Context, ids: IntArray) {
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE).edit().apply { ids.forEach { remove(it.toString()); remove("picker_$it") } }.apply()
    }
    companion object {
        const val COMPLETE = "dev.quiettasks.widget.COMPLETE"
        const val PICK = "dev.quiettasks.widget.PICK"
        const val ROW = "dev.quiettasks.widget.ROW"
        fun updateAll(context: Context, snapshot: Snapshot, prefs: Preferences) {
            val manager = AppWidgetManager.getInstance(context)
            update(context, snapshot, prefs, manager.getAppWidgetIds(ComponentName(context, TasksWidget::class.java)))
        }
        fun update(context: Context, snapshot: Snapshot, prefs: Preferences, ids: IntArray) {
            val manager = AppWidgetManager.getInstance(context)
            for (id in ids) {
                val (views, usesService) = createViews(context, snapshot, prefs, id)
                manager.updateAppWidget(id, views)
                if (usesService) manager.notifyAppWidgetViewDataChanged(id, R.id.widget_rows)
            }
        }
        internal fun createViews(context: Context, snapshot: Snapshot, prefs: Preferences, id: Int): Pair<RemoteViews, Boolean> {
            val widgetPrefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
            val dark = prefs.theme == ThemeMode.DARK || prefs.theme == ThemeMode.AMOLED || (prefs.theme == ThemeMode.SYSTEM && context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            val dynamic = prefs.deviceColors && Build.VERSION.SDK_INT >= 31
            val background = if (prefs.theme == ThemeMode.AMOLED) Color.BLACK else if (dynamic) context.getColor(if (dark) android.R.color.system_neutral1_900 else android.R.color.system_accent1_100) else Color.parseColor(if (dark) "#1E1F20" else "#D3E3FD")
            val foreground = if (dynamic) context.getColor(if (dark) android.R.color.system_accent1_50 else android.R.color.system_accent1_900) else Color.parseColor(if (dark) "#E3E3E3" else "#041E49")
            val button = if (dynamic) context.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600) else Color.parseColor(if (dark) "#A8C7FA" else "#0B57D0")
            val choices = widgetChoices(snapshot)
            val selection = widgetPrefs.getString(id.toString(), ALL_TASKS_TAB)?.takeIf { value -> choices.any { it.first == value } } ?: ALL_TASKS_TAB
            val list = snapshot.lists.firstOrNull { !it.deleted && it.id == selection } ?: snapshot.lists.firstOrNull { !it.deleted }
            val picking = widgetPrefs.getBoolean("picker_$id", false)
            val views = RemoteViews(context.packageName, R.layout.widget_tasks_collection)
            val config = Intent(context, TasksWidget::class.java).setAction(PICK).setData(android.net.Uri.parse("quiettasks://widget/$id/pick")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            views.setOnClickPendingIntent(R.id.widget_list, PendingIntent.getBroadcast(context, id, config, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id + 10000, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val create = Intent(context, MainActivity::class.java).putExtra("createListId", list?.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, id + 20000, create, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setTextViewText(R.id.widget_list, if (picking) "Choose list  ▴" else "${choices.first { it.first == selection }.second}  ▾")
            views.setTextColor(R.id.widget_list, foreground)
            views.setInt(R.id.widget_root, "setBackgroundResource", if (prefs.theme == ThemeMode.AMOLED) R.drawable.widget_background_amoled else if (dark) R.drawable.widget_background_dark else R.drawable.widget_background)
            views.setInt(R.id.widget_add, "setBackgroundResource", if (dark) R.drawable.widget_add_background_dark else R.drawable.widget_add_background)
            if (Build.VERSION.SDK_INT >= 31) {
                views.setColorStateList(R.id.widget_root, "setBackgroundTintList", ColorStateList.valueOf(background))
                views.setColorStateList(R.id.widget_add, "setBackgroundTintList", ColorStateList.valueOf(button))
            }
            views.setInt(R.id.widget_add, "setColorFilter", if (dark) Color.parseColor("#041E49") else Color.WHITE)
            val action = Intent(context, TasksWidget::class.java).setAction(ROW).setData(android.net.Uri.parse("quiettasks://widget/$id/row")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            views.setPendingIntentTemplate(R.id.widget_rows, PendingIntent.getBroadcast(context, id, action, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
            val rowCount = if (picking) choices.size else widgetTasks(snapshot, prefs, selection).size
            val usesService = Build.VERSION.SDK_INT < 31 || rowCount > 100
            if (!usesService) {
                val rows = widgetRows(context, snapshot, prefs, selection, picking, foreground)
                val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(false).setViewTypeCount(2)
                rows.forEachIndexed { index, row -> items.addItem(index.toLong(), row) }
                views.setRemoteAdapter(R.id.widget_rows, items.build())
            } else {
                val service = Intent(context, WidgetRowsService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("foreground", foreground)
                    .setData(android.net.Uri.parse("quiettasks://widget/$id/${if (picking) "lists" else selection}/$foreground"))
                views.setRemoteAdapter(R.id.widget_rows, service)
            }
            views.setEmptyView(R.id.widget_rows, R.id.widget_empty)
            views.setTextColor(R.id.widget_empty, foreground)
            return views to usesService
        }
    }
}
