package dev.quiettasks.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.quiettasks.data.Task

@Composable internal fun InlineSubtasks(
    parent: Task,
    children: List<Task>,
    onChange: (List<Task>) -> Unit,
    enabled: Boolean = true,
    onCheck: ((List<Task>) -> Unit)? = null,
) {
    var focusId by rememberSaveable(parent.id) { mutableStateOf<String?>(null) }
    var focusRequest by remember { mutableIntStateOf(0) }
    fun add() {
        focusRequest++
        val empty = children.firstOrNull { it.title.isBlank() }
        if (empty != null) focusId = empty.id
        else {
            val child = Task(listId = parent.listId, title = "", parentId = parent.id,
                position = (children.maxOfOrNull { it.position } ?: -1024L) + 1024L)
            focusId = child.id
            onChange(children + child)
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Subtasks", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
        children.forEach { child -> key(child.id) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(focusId, focusRequest) { if (focusId == child.id) focus.requestFocus() }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({
                    val updated = children.map { if (it.id == child.id) it.copy(completedAt = if (it.completed) null else System.currentTimeMillis()) else it }
                    (onCheck ?: onChange)(updated)
                }, enabled = enabled && child.title.isNotBlank()) {
                    TaskIcon(if (child.completed) Glyph.CHECK else Glyph.CIRCLE,
                        if (child.completed) "Mark incomplete ${child.title}" else "Complete ${child.title}",
                        tint = if (child.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(child.title, { title -> onChange(children.map { if (it.id == child.id) it.copy(title = title) else it }) },
                    Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = "Subtask title" },
                    enabled = enabled, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (child.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (child.completed) TextDecoration.LineThrough else TextDecoration.None),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { add() }),
                    decorationBox = { field -> Box { if (child.title.isEmpty()) Text("Subtask", color = MaterialTheme.colorScheme.onSurfaceVariant); field() } })
                IconButton({ onChange(children.filterNot { it.id == child.id }) }, enabled = enabled) {
                    TaskIcon(Glyph.CLOSE, "Remove subtask ${child.title}", Modifier.size(18.dp))
                }
            }
        } }
        TextButton({ add() }, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)) {
            TaskIcon(Glyph.SUBTASK, null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Add subtask")
        }
    }
}
