@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.quiettasks.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.quiettasks.TaskViewModel
import dev.quiettasks.data.*
import dev.quiettasks.sync.SyncStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun SettingsScreen(prefs: Preferences, status: SyncStatus, model: TaskViewModel, back: () -> Unit, onArchived: () -> Unit = {}) {
    BackHandler(onBack = back)
    val context = LocalContext.current
    var account by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(false) }
    var longPress by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> model.updateSettings { it.copy(reminders = granted) } }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(back) { TaskIcon(Glyph.BACK, "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SettingsSection("Appearance") {
                SettingsRow(Glyph.THEME, "Theme", prefs.theme.label(), { theme = true })
                if (Build.VERSION.SDK_INT >= 31) SettingsRow(Glyph.THEME, "Use device colors", "Match your wallpaper’s color palette", trailing = { Switch(prefs.deviceColors, { value -> model.updateSettings { it.copy(deviceColors = value) } }) })
            }
            SettingsSection("Tasks") {
                SettingsRow(Glyph.DRAG, "Long press", if (prefs.longPressAction == LongPressAction.REORDER) "Reorder tasks in My order" else "Show task menu", { longPress = true })
            }
            SettingsSection("Tabs") {
                SettingsRow(Glyph.LIST, "Show All", "", trailing = { Switch(prefs.showAllTab, { value -> model.updateSettings { it.copy(showAllTab = value) } }, Modifier.semantics { contentDescription = "Show All tab" }) })
                SettingsRow(Glyph.STAR, "Show Starred", "", trailing = { Switch(prefs.showStarredTab, { value -> model.updateSettings { it.copy(showStarredTab = value) } }, Modifier.semantics { contentDescription = "Show Starred tab" }) })
                SettingsRow(Glyph.LIST, "Archived tasks", "", onArchived)
            }
            SettingsSection("Sync") {
                SettingsRow(Glyph.CLOUD, if (prefs.connected) prefs.username else "Add CalDAV account", if (prefs.connected) Uri.parse(prefs.serverUrl).host.orEmpty() else "Sync with your own server", { account = true })
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                Column(Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                    SyncOption("While using the app", "Sync on opening and after edits. Stops when you leave.", prefs.syncMode == SyncMode.WHILE_USING) { model.updateSettings { it.copy(syncMode = SyncMode.WHILE_USING) } }
                    SyncOption("Only while charging", "Sync when plugged in, then at most every 6 hours while charging.", prefs.syncMode == SyncMode.CHARGING_ONLY) { model.updateSettings { it.copy(syncMode = SyncMode.CHARGING_ONLY) } }
                    SyncOption("Manual only", "Sync only when you tap Sync now.", prefs.syncMode == SyncMode.MANUAL) { model.updateSettings { it.copy(syncMode = SyncMode.MANUAL) } }
                }
                SettingsRow(Glyph.WIFI, "Wi-Fi only", "Applies to automatic and manual syncing", trailing = { Switch(prefs.wifiOnly, { value -> model.updateSettings { it.copy(wifiOnly = value) } }) })
                SettingsRow(Glyph.SYNC, if (status.running) "Syncing…" else "Sync now", if (status.running) "" else prefs.lastSyncMessage,
                    click = if (prefs.connected && !status.running) ({ model.sync() }) else null,
                    trailing = { if (status.running) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) })
                if (prefs.lastSyncAt > 0) Text("Last synced ${DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(prefs.lastSyncAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 64.dp, bottom = 16.dp, end = 16.dp))
                if (prefs.connected) TextButton({ disconnect = true }, Modifier.padding(start = 52.dp, bottom = 8.dp), enabled = !status.running) { Text("Disconnect account") }
            }
            SettingsSection("Reminders") {
                SettingsRow(Glyph.NOTIFICATION, "Task reminders", "Date-only tasks remind you at 9:00 AM", trailing = {
                    Switch(prefs.reminders, { value ->
                        if (value && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else model.updateSettings { it.copy(reminders = value) }
                    })
                })
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) SettingsRow(Glyph.NOTIFICATION, "Allow notifications", "Enable Android notification permission", { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                if (Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) SettingsRow(Glyph.TIME, "Precise reminders", "Allow exact alarms, or Android may delay reminders", {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                })
            }
            Text("Tasks · 0.2.0", Modifier.padding(start = 28.dp, top = 20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Stored on your device. No ads, analytics, or Google account.", Modifier.padding(horizontal = 28.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Made with ♥\nAhnaf Hasan Shafin", Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
    if (longPress) AlertDialog(onDismissRequest = { longPress = false }, title = { Text("Long press") }, text = { Column {
        LongPressAction.entries.forEach { action -> Row(Modifier.fillMaxWidth().clickable { model.updateSettings { it.copy(longPressAction = action) }; longPress = false }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(prefs.longPressAction == action, { model.updateSettings { it.copy(longPressAction = action) }; longPress = false })
            Text(if (action == LongPressAction.REORDER) "Reorder tasks" else "Show task menu")
        } }
    } }, confirmButton = { TextButton({ longPress = false }) { Text("Cancel") } })
    if (theme) AlertDialog(onDismissRequest = { theme = false }, title = { Text("Choose theme") }, text = { Column {
        ThemeMode.entries.forEach { mode -> Row(Modifier.fillMaxWidth().clickable { model.updateSettings { it.copy(theme = mode) }; theme = false }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(prefs.theme == mode, { model.updateSettings { it.copy(theme = mode) }; theme = false }); Text(mode.label())
        } }
    } }, confirmButton = { TextButton({ theme = false }) { Text("Cancel") } })
    if (account) AccountDialog(prefs, { account = false }) { url, username, password -> model.connect(url, username, password); account = false }
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect account?") }, text = { Text("Your tasks will stay on this device. Server data will be kept.") },
        confirmButton = { TextButton({ model.disconnect(); disconnect = false }) { Text("Disconnect") } }, dismissButton = { TextButton({ disconnect = false }) { Text("Cancel") } })
}

@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, Modifier.padding(start = 28.dp, top = 24.dp, bottom = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) { Column(content = content) }
}
@Composable private fun SettingsRow(glyph: Glyph, title: String, subtitle: String, click: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (click != null) Modifier.clickable(onClick = click) else Modifier).padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskIcon(glyph, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp)); Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) Text(subtitle, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}
@Composable private fun SyncOption(title: String, description: String, selected: Boolean, select: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = select).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, select); Spacer(Modifier.width(4.dp)); Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun AccountDialog(prefs: Preferences, dismiss: () -> Unit, save: (String, String, String) -> Unit) {
    var url by remember { mutableStateOf(prefs.serverUrl) }
    var user by remember { mutableStateOf(prefs.username) }
    var password by remember { mutableStateOf(prefs.password) }
    val valid = runCatching { java.net.URI(url.trim()).let { it.scheme == "https" && !it.host.isNullOrEmpty() && it.userInfo == null && it.query == null && it.fragment == null } }.getOrDefault(false)
    AlertDialog(onDismissRequest = dismiss, title = { Text("CalDAV account") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connect a task-enabled CalDAV server, such as Nextcloud, Radicale, or Baïkal.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(url, { url = it }, label = { Text("Server URL") }, placeholder = { Text("https://cloud.example.com") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
        OutlinedTextField(password, { password = it }, label = { Text("App password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (prefs.connected && (url.trim() != prefs.serverUrl || user.trim() != prefs.username)) Text("Changing the account keeps your local tasks and uploads them to the new account on the next sync.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text("Use your server’s CalDAV URL and an app password if available.", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton({ save(url, user, password) }, enabled = valid && user.isNotBlank() && password.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}
