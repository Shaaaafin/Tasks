@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.quiettasks.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val tabColors = listOf("default", "red", "orange", "green", "teal", "blue", "purple", "pink")

@Composable fun tabAccent(key: String): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    return when (key) {
        "red" -> Color(if (dark) 0xFFFFB4AB else 0xFFBA1A1A)
        "orange" -> Color(if (dark) 0xFFFFB77C else 0xFF8B5000)
        "green" -> Color(if (dark) 0xFF9DD67D else 0xFF356A20)
        "teal" -> Color(if (dark) 0xFF80D5C8 else 0xFF006B5F)
        "blue" -> Color(if (dark) 0xFFA8C7FA else 0xFF0B57D0)
        "purple" -> Color(if (dark) 0xFFD0BCFF else 0xFF6750A4)
        "pink" -> Color(if (dark) 0xFFFFAFD4 else 0xFFA3336B)
        else -> MaterialTheme.colorScheme.primary
    }
}

@Composable fun TabSymbol(icon: String, fallback: Glyph, modifier: Modifier = Modifier) {
    val glyph = if (icon.startsWith("glyph:")) Glyph.entries.firstOrNull { it.name == icon.removePrefix("glyph:") } else null
    when {
        glyph != null -> TaskIcon(glyph, null, modifier.size(20.dp))
        icon.isNotEmpty() -> Text(icon, modifier, fontSize = 18.sp, maxLines = 1)
        else -> TaskIcon(fallback, null, modifier.size(20.dp))
    }
}

@Composable fun TabAppearanceDialog(name: String, color: String, icon: String, editableName: Boolean,
    dismiss: () -> Unit, save: (String, String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(name) }
    var chosenColor by rememberSaveable { mutableStateOf(color) }
    var chosenIcon by rememberSaveable { mutableStateOf(icon) }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (editableName && name.isEmpty()) "New list" else "Customize tab", style = MaterialTheme.typography.headlineSmall)
            if (editableName) DialogTextField(title, { title = it }, "List name")
            else Text(name, style = MaterialTheme.typography.titleMedium)
            Text("Color", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                tabColors.forEach { key ->
                    val accent = tabAccent(key)
                    Box(Modifier.size(32.dp).semantics { contentDescription = "$key tab color" }.clip(CircleShape)
                        .background(accent).clickable { chosenColor = key }, contentAlignment = Alignment.Center) {
                        if (chosenColor == key) TaskIcon(Glyph.CHECK, "Selected", Modifier.size(20.dp), if (accent.luminance() > .5f) Color.Black else Color.White)
                    }
                }
            }
            Text("Icon or emoji", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("glyph:LIST", "glyph:CALENDAR", "glyph:STAR", "🏠", "🛒", "💼", "🌿", "📚", "💪", "🎯").forEach { symbol ->
                    val accent = tabAccent(chosenColor)
                    Surface(shape = CircleShape, color = if (chosenIcon == symbol) accent.copy(alpha = .18f) else Color.Transparent) {
                        Box(Modifier.size(40.dp).semantics { contentDescription = "Choose $symbol icon" }.clickable { chosenIcon = symbol }, contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalContentColor provides accent) { TabSymbol(symbol, Glyph.LIST) }
                        }
                    }
                }
            }
            DialogTextField(if (chosenIcon.startsWith("glyph:")) "" else chosenIcon,
                { if (it.codePointCount(0, it.length) <= 12) chosenIcon = it }, "Your emoji")
            Text("Use your keyboard’s emoji picker", style = MaterialTheme.typography.bodySmall)
            TextButton({ chosenIcon = "" }) { Text("Use default icon") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompositionLocalProvider(LocalContentColor provides tabAccent(chosenColor)) {
                    TabSymbol(chosenIcon, Glyph.LIST); Text(title.ifBlank { "Preview" }, style = MaterialTheme.typography.titleMedium)
                }
            }
            Text("Colors and icons are saved on this device.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(dismiss) { Text("Cancel") }
                TextButton({ save(title.trim(), chosenColor, chosenIcon) }, enabled = title.isNotBlank()) { Text("Done") }
            }
        }
    }
}
