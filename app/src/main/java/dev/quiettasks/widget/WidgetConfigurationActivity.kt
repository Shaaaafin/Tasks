package dev.quiettasks.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.quiettasks.TasksApplication
import dev.quiettasks.ui.TasksTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val app = application as TasksApplication
        lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) { app.store.snapshot() }
            val prefs = app.settings.state.value
            val lists = widgetChoices(snapshot)
            val current = getSharedPreferences("widgets", MODE_PRIVATE).getString(id.toString(), null)
            setContent {
                var selected by remember { mutableStateOf(current?.takeIf { value -> lists.any { it.first == value } } ?: dev.quiettasks.data.ALL_TASKS_TAB) }
                TasksTheme(prefs.theme, prefs.deviceColors) {
                    Surface(Modifier.fillMaxWidth().heightIn(max = 560.dp), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                            Text("Choose a list", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(20.dp))
                            lists.forEach { list -> Row(Modifier.fillMaxWidth().clickable { selected = list.first }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected == list.first, { selected = list.first }); Text(list.second)
                            } }
                            Spacer(Modifier.height(20.dp))
                            Button({
                                getSharedPreferences("widgets", MODE_PRIVATE).edit().putString(id.toString(), selected).putBoolean("picker_$id", false).apply()
                                TasksWidget.update(this@WidgetConfigurationActivity, snapshot, prefs, intArrayOf(id))
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
                            }, enabled = selected.isNotEmpty(), modifier = Modifier.align(Alignment.End)) { Text("Done") }
                        }
                    }
                }
            }
        }
    }
}
