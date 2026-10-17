@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package dev.quiettasks.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiettasks.data.STARRED_TAB
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class TaskTabSpec(val id: String, val title: String, val color: String, val icon: String)

/** Clamp early tabs at the leading edge and interpolate later positions with the page gesture. */
internal fun tabScrollTarget(centers: List<Float>, page: Float, viewport: Float, maximum: Int): Float {
    if (centers.isEmpty() || viewport <= 0) return 0f
    val position = page.coerceIn(0f, centers.lastIndex.toFloat())
    val from = floor(position).toInt(); val to = (from + 1).coerceAtMost(centers.lastIndex)
    fun target(index: Int) = (centers[index] - viewport / 2).coerceIn(0f, maximum.coerceAtLeast(0).toFloat())
    return target(from) + (target(to) - target(from)) * (position - from)
}

@Composable internal fun TaskTabs(tabs: List<TaskTabSpec>, pager: PagerState, customize: (String) -> Unit, newList: () -> Unit) {
    val scroll = rememberScrollState()
    val measured = remember { mutableStateMapOf<String, Int>() }
    var viewport by remember { mutableIntStateOf(0) }
    val widths = tabs.map { measured[it.id] ?: 0 }
    val positions = widths.runningFold(0) { offset, width -> offset + width }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val edge = with(density) { 8.dp.toPx() }
    val colors = tabs.map { tabAccent(it.color) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(widths, viewport) {
        if (widths.any { it == 0 } || viewport == 0) return@LaunchedEffect
        val centers = widths.mapIndexed { index, width -> edge + positions[index] + width / 2f }
        snapshotFlow { (pager.currentPage + pager.currentPageOffsetFraction) to scroll.maxValue }.collect { (page, maximum) ->
            scroll.scrollTo(tabScrollTarget(centers, page, viewport.toFloat(), maximum).roundToInt())
        }
    }
    Box(Modifier.fillMaxWidth().onSizeChanged { viewport = it.width }) {
        Row(Modifier.horizontalScroll(scroll).height(49.dp).padding(horizontal = 8.dp).drawBehind {
            if (widths.all { it > 0 } && tabs.isNotEmpty()) {
                val position = (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, tabs.lastIndex.toFloat())
                val from = floor(position).toInt(); val to = (from + 1).coerceAtMost(tabs.lastIndex); val fraction = position - from
                val inset = 12.dp.toPx()
                val left = positions[from] + (positions[to] - positions[from]) * fraction + inset
                val width = widths[from] + (widths[to] - widths[from]) * fraction - inset * 2
                drawRoundRect(lerp(colors[from], colors[to], fraction), Offset(left, size.height - 3.dp.toPx()), Size(width.coerceAtLeast(0f), 3.dp.toPx()), CornerRadius(3.dp.toPx()))
            }
        }, verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, tab ->
                val selected = pager.currentPage == index
                val color = if (selected || tab.color != "default") colors[index] else MaterialTheme.colorScheme.onSurfaceVariant
                Column(Modifier.onSizeChanged { if (measured[tab.id] != it.width) measured[tab.id] = it.width }.height(49.dp).width(IntrinsicSize.Max).widthIn(max = 240.dp)
                    .semantics { contentDescription = "${tab.title} tab"; this.selected = selected; role = Role.Tab }
                    .combinedClickable(onClick = { scope.launch { pager.animateScrollToPage(index, animationSpec = tween(180, easing = FastOutSlowInEasing)) } }, onLongClickLabel = "Customize tab", onLongClick = { customize(tab.id) }), horizontalAlignment = Alignment.CenterHorizontally) {
                    CompositionLocalProvider(LocalContentColor provides color) {
                        Row(Modifier.weight(1f).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tab.icon.isNotEmpty() || tab.id in listOf(dev.quiettasks.data.ALL_TASKS_TAB, STARRED_TAB)) {
                                TabSymbol(tab.icon, if (tab.id == STARRED_TAB) Glyph.STAR else Glyph.LIST); Spacer(Modifier.width(6.dp))
                            }
                            Text(tab.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                }
            }
            TextButton(newList, contentPadding = PaddingValues(horizontal = 12.dp)) { TaskIcon(Glyph.ADD, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("New list") }
        }
    }
}
