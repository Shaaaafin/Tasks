package dev.quiettasks.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.quiettasks.R
import dev.quiettasks.TasksApplication
import dev.quiettasks.data.*
import dev.quiettasks.ui.formatDue

internal fun widgetChoices(snapshot: Snapshot): List<Pair<String, String>> =
    listOf(ALL_TASKS_TAB to "All", STARRED_TAB to "Starred") + snapshot.lists.filter { !it.deleted }.map { it.id to it.name }

internal fun widgetTasks(snapshot: Snapshot, prefs: Preferences, selection: String): List<Task> {
    val active = activeTaskRows(snapshot.tasks).filter { task -> snapshot.lists.any { !it.deleted && it.id == task.listId } &&
        (selection == ALL_TASKS_TAB || selection == STARRED_TAB && task.starred || task.listId == selection) }
    val sort = if (selection == ALL_TASKS_TAB) prefs.summarySort else prefs.listSorts[selection] ?: prefs.sort
    return if (selection == ALL_TASKS_TAB && sort == SortMode.DATE) {
        val roots = active.filter { child -> active.none { it.id == child.parentId } }
        taskDateGroups(roots).flatMap { group ->
            val ids = group.tasks.mapTo(HashSet()) { it.id }
            taskHierarchy(group.tasks + active.filter { it.parentId in ids })
        }
    } else taskHierarchy(sortTasks(active, sort))
}

internal fun widgetRows(context: Context, snapshot: Snapshot, prefs: Preferences, selection: String, picking: Boolean, foreground: Int): List<RemoteViews> {
    return if (picking) widgetChoices(snapshot).map { widgetChoiceRow(context, it, selection, foreground) }
        else widgetTasks(snapshot, prefs, selection).map { widgetTaskRow(context, snapshot, it, selection, foreground) }
}

private fun widgetChoiceRow(context: Context, choice: Pair<String, String>, selection: String, foreground: Int) =
    RemoteViews(context.packageName, R.layout.widget_list_option).apply {
        setTextViewText(R.id.widget_option, (if (choice.first == selection) "✓  " else "") + choice.second)
        setTextColor(R.id.widget_option, foreground)
        setOnClickFillInIntent(R.id.widget_option, Intent().putExtra("widgetAction", "select").putExtra("listId", choice.first))
    }

private fun widgetTaskRow(context: Context, snapshot: Snapshot, task: Task, selection: String, foreground: Int) =
    RemoteViews(context.packageName, R.layout.widget_task_row_hierarchy).apply {
        val nested = task.parentId != null
        val parent = snapshot.tasks.firstOrNull { it.id == task.parentId && !it.deleted }
        val indent = if (nested) (16 * context.resources.displayMetrics.density).toInt() else 0
        setViewPadding(R.id.widget_task_row, indent, 0, 0, 0)
        setViewVisibility(R.id.widget_subtask_branch, if (nested) View.VISIBLE else View.GONE)
        setInt(R.id.widget_subtask_branch, "setColorFilter", foreground)
        setTextViewText(R.id.widget_task_title, task.title)
        setTextViewTextSize(R.id.widget_task_title, android.util.TypedValue.COMPLEX_UNIT_SP, if (nested) 14f else 16f)
        setContentDescription(R.id.widget_task_title, if (parent != null) "Subtask of ${parent.title}: ${task.title}" else if (nested) "Subtask: ${task.title}" else task.title)
        setTextColor(R.id.widget_task_title, foreground)
        setInt(R.id.widget_task_title, "setPaintFlags", android.graphics.Paint.ANTI_ALIAS_FLAG or if (task.completed) android.graphics.Paint.STRIKE_THRU_TEXT_FLAG else 0)
        setImageViewResource(R.id.widget_check, if (task.completed) R.drawable.ic_check else R.drawable.ic_circle)
        val due = task.deadline ?: task.dueDate
        val subtitle = listOfNotNull(due?.let { formatDue(it, if (task.deadline == null) task.dueTime else null) },
            if (selection in listOf(ALL_TASKS_TAB, STARRED_TAB)) snapshot.lists.firstOrNull { it.id == task.listId }?.name else null).joinToString(" · ")
        setTextViewText(R.id.widget_task_date, subtitle)
        setTextColor(R.id.widget_task_date, foreground)
        setViewVisibility(R.id.widget_task_date, if (subtitle.isEmpty()) View.GONE else View.VISIBLE)
        setInt(R.id.widget_check, "setColorFilter", foreground)
        setContentDescription(R.id.widget_check, if (task.completed) "Mark incomplete ${task.title}" else "Complete ${task.title}")
        setOnClickFillInIntent(R.id.widget_check, Intent().putExtra("widgetAction", "complete").putExtra("taskId", task.id))
        setOnClickFillInIntent(R.id.widget_task_text, Intent().putExtra("widgetAction", "open").putExtra("taskId", task.id))
    }

/** Loads large collections on demand rather than sending every row in one Binder transaction. */
class WidgetRowsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private var snapshot = Snapshot()
        private var prefs = Preferences()
        private var selection = ALL_TASKS_TAB
        private var picking = false
        private var tasks = emptyList<Task>()
        private var choices = emptyList<Pair<String, String>>()
        override fun onCreate() { onDataSetChanged() }
        override fun onDataSetChanged() {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val preferences = getSharedPreferences("widgets", MODE_PRIVATE)
            val app = applicationContext as TasksApplication
            snapshot = app.store.snapshot(); prefs = app.settings.state.value
            choices = widgetChoices(snapshot)
            selection = preferences.getString(id.toString(), ALL_TASKS_TAB)?.takeIf { value -> choices.any { it.first == value } } ?: ALL_TASKS_TAB
            picking = preferences.getBoolean("picker_$id", false)
            tasks = if (picking) emptyList() else widgetTasks(snapshot, prefs, selection)
        }
        override fun onDestroy() { tasks = emptyList(); choices = emptyList(); snapshot = Snapshot() }
        override fun getCount() = if (picking) choices.size else tasks.size
        override fun getViewAt(position: Int): RemoteViews? {
            val foreground = intent.getIntExtra("foreground", android.graphics.Color.BLACK)
            if (picking) return choices.getOrNull(position)?.let { choice -> widgetChoiceRow(this@WidgetRowsService, choice, selection, foreground) }
            val task = tasks.getOrNull(position) ?: return null
            return widgetTaskRow(this@WidgetRowsService, snapshot, task, selection, foreground)
        }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 2
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}
