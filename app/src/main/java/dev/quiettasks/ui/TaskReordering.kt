package dev.quiettasks.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.hypot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.quiettasks.data.Task
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

internal fun taskReorderPeers(task: Task, tasks: List<Task>): List<Task> {
    val ids = tasks.mapTo(HashSet()) { it.id }
    return if (task.parentId in ids) tasks.filter { it.parentId == task.parentId && it.listId == task.listId }
        else tasks.filter { it.parentId !in ids }
}

internal data class TaskWaveGeometry(val center: Offset, val radius: Float)

/** Keep pointer movement in memory and persist the final order once on release. */
@Stable internal class TaskDragState(private val save: (List<String>) -> Unit) {
    var ids by mutableStateOf<List<String>?>(null)
        private set
    var dragging by mutableStateOf<String?>(null)
        private set
    var distance by mutableFloatStateOf(0f)
        private set
    var releasedId by mutableStateOf<String?>(null)
        private set
    var releasedOffset by mutableFloatStateOf(0f)
        private set
    var releasedDistance by mutableFloatStateOf(0f)
        private set
    var pressFractionX by mutableFloatStateOf(.5f)
        private set
    var pressFractionY by mutableFloatStateOf(.5f)
        private set
    private var original = emptyList<String>()
    private var family by mutableStateOf<List<String>>(emptyList())
    private val rowHeights = mutableMapOf<String, Int>()
    private val rowWidths = mutableMapOf<String, Int>()
    private var rootHeight = 0
    private var familyOffsets by mutableStateOf<Map<String, Float>>(emptyMap())
    var familyHeight by mutableFloatStateOf(0f)
        private set
    private fun measureFamily() {
        var offset = 0f
        familyOffsets = buildMap { family.forEach { id -> put(id, offset); offset += rowHeights[id] ?: rootHeight } }
        familyHeight = offset
    }
    fun measure(id: String, height: Int, width: Int? = null) {
        val previous = rowHeights.put(id, height)
        if (width != null) rowWidths[id] = width
        if (previous != height && id in family) measureFamily()
    }
    fun pressFraction(id: String, x: Float, viewportWidth: Int): Float {
        val width = rowWidths[id]?.takeIf { it > 0 } ?: viewportWidth.coerceAtLeast(1)
        return ((x - (viewportWidth - width) / 2f) / width).coerceIn(0f, 1f)
    }
    fun isLifted(id: String) = dragging != null && id in family
    fun isReleasing(id: String) = releasedId != null && id in family
    fun isFamilyStart(id: String) = family.firstOrNull() == id
    fun isFamilyEnd(id: String) = family.lastOrNull() == id
    fun familyOffset(id: String): Float = familyOffsets[id] ?: 0f
    fun waveGeometry(id: String, width: Float): TaskWaveGeometry {
        val x = width * pressFractionX
        val y = rootHeight * pressFractionY
        val radius = hypot(max(x, width - x), max(y, familyHeight - y)) * 1.15f
        return TaskWaveGeometry(Offset(x, y - familyOffset(id)), radius)
    }
    fun pivotY(id: String, rowHeight: Float) = (rootHeight * pressFractionY - familyOffset(id)) / rowHeight.coerceAtLeast(1f)
    private var peers = emptySet<String>()
    private var initialTop = 0f
    private var initialPointer = 0f
    private var height = 0
    val pointerY get() = initialPointer + distance
    fun start(task: Task, tasks: List<Task>, top: Int, rowHeight: Int, pointer: Float, pointerFractionX: Float = .5f): Boolean {
        val siblings = taskReorderPeers(task, tasks)
        if (siblings.isEmpty()) return false
        releasedId = null; releasedOffset = 0f; releasedDistance = 0f
        pressFractionX = pointerFractionX.coerceIn(0f, 1f)
        pressFractionY = ((pointer - top) / rowHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
        val members = hashSetOf(task.id)
        do { val added = members.addAll(tasks.filter { it.parentId in members }.map { it.id }) } while (added)
        family = dev.quiettasks.data.taskHierarchy(tasks).filter { it.id in members }.map { it.id }
        rootHeight = rowHeight; rowHeights[task.id] = rowHeight; measureFamily()
        original = tasks.map { it.id }; ids = original; peers = siblings.mapTo(HashSet()) { it.id }
        initialTop = top.toFloat(); initialPointer = pointer; height = rowHeight; distance = 0f; dragging = task.id
        return true
    }
    fun translation(layout: LazyListLayoutInfo, id: String): Float {
        if (!isLifted(id)) return 0f
        val anchor = layout.visibleItemsInfo.firstOrNull { it.key == dragging }
            ?: layout.visibleItemsInfo.firstOrNull { it.key in family } ?: return 0f
        val top = anchor.offset - familyOffset(anchor.key as String)
        return initialTop + distance - top
    }
    fun dragBy(delta: Float, layout: LazyListLayoutInfo) {
        if (dragging == null) return
        distance += delta
        updateTarget(layout, delta)
    }
    fun updateTarget(layout: LazyListLayoutInfo, direction: Float) {
        val current = ids ?: return
        val id = dragging ?: return
        val from = current.indexOf(id)
        val center = initialTop + distance + height / 2f
        val targets = layout.visibleItemsInfo.filter { it.key in peers && it.key != id }
        val target = if (direction > 0) targets.filter { current.indexOf(it.key) > from && center > it.offset + it.size / 2f }.maxByOrNull { current.indexOf(it.key) }
            else if (direction < 0) targets.filter { current.indexOf(it.key) < from && center < it.offset + it.size / 2f }.minByOrNull { current.indexOf(it.key) } else null
        if (target != null) moveTo(target.key as String)
    }
    private fun moveTo(target: String) {
        val current = ids ?: return
        val order = current.filter { it in peers }.toMutableList()
        val from = order.indexOf(dragging); val to = order.indexOf(target)
        if (from < 0 || to < 0 || from == to) return
        order.add(to, order.removeAt(from))
        val iterator = order.iterator()
        ids = current.map { if (it in peers) iterator.next() else it }
    }
    fun end(layout: LazyListLayoutInfo? = null) {
        releasedId = dragging
        releasedDistance = distance
        releasedOffset = dragging?.let { id -> layout?.let { translation(it, id) } } ?: 0f
        val result = ids; dragging = null; distance = 0f
        if (result != null && result != original) save(result) else ids = null
    }
    fun cancel(layout: LazyListLayoutInfo? = null) {
        if (dragging != null) {
            releasedId = dragging
            releasedDistance = distance
            releasedOffset = dragging?.let { id -> layout?.let { translation(it, id) } } ?: 0f
        }
        dragging = null; ids = null; distance = 0f
    }
    fun finishRelease(id: String) { if (releasedId == id) { releasedId = null; releasedOffset = 0f; releasedDistance = 0f } }
    fun ordered(tasks: List<Task>): List<Task> {
        val ranks = ids?.withIndex()?.associate { it.value to it.index } ?: return tasks
        return tasks.sortedBy { ranks[it.id] ?: Int.MAX_VALUE }
    }
    fun accessibleMove(task: Task, direction: Int, tasks: List<Task>): Boolean {
        val siblings = taskReorderPeers(task, tasks)
        val index = siblings.indexOfFirst { it.id == task.id }
        val next = index + direction
        if (index < 0 || next !in siblings.indices) return false
        val order = tasks.map { it.id }.toMutableList()
        java.util.Collections.swap(order, order.indexOf(task.id), order.indexOf(siblings[next].id))
        save(order); return true
    }
}

@Composable internal fun rememberTaskDrag(tasks: List<Task>, save: (List<String>) -> Unit): TaskDragState {
    val latestSave by rememberUpdatedState(save)
    val state = remember { TaskDragState { latestSave(it) } }
    LaunchedEffect(tasks) { if (state.dragging == null || tasks.none { it.id == state.dragging }) state.cancel() }
    LaunchedEffect(state.releasedId) {
        state.releasedId?.let { id -> delay(180); state.finishRelease(id) }
    }
    return state
}

@Composable internal fun Modifier.taskReorderContainer(tasks: List<Task>, state: TaskDragState, list: LazyListState, enabled: Boolean): Modifier {
    val latestTasks by rememberUpdatedState(tasks)
    val haptic = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    val maxSpeed = with(LocalDensity.current) { 720.dp.toPx() }
    LaunchedEffect(state.dragging) {
        if (state.dragging == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive && state.dragging != null) {
            val now = withFrameNanos { it }
            val elapsed = ((now - previous) / 1_000_000_000f).coerceAtMost(.032f); previous = now
            val layout = list.layoutInfo
            val top = layout.viewportStartOffset + layout.beforeContentPadding
            val bottom = layout.viewportEndOffset
            val velocity = when {
                state.pointerY < top + edge && list.canScrollBackward -> -((top + edge - state.pointerY) / edge).coerceIn(0f, 1f) * maxSpeed
                state.pointerY > bottom - edge && list.canScrollForward -> ((state.pointerY - bottom + edge) / edge).coerceIn(0f, 1f) * maxSpeed
                else -> 0f
            }
            if (velocity != 0f) {
                val consumed = list.scrollBy(velocity * elapsed)
                if (consumed != 0f) state.updateTarget(list.layoutInfo, consumed)
            }
        }
    }
    return if (!enabled) this else pointerInput(list, enabled) {
        detectDragGesturesAfterLongPress(onDragStart = { point ->
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { point.y >= it.offset && point.y < it.offset + it.size }
            val task = latestTasks.firstOrNull { it.id == item?.key }
            if (task != null && item != null && state.start(task, latestTasks, item.offset, item.size, point.y, state.pressFraction(task.id, point.x, list.layoutInfo.viewportSize.width))) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }, onDragEnd = { state.end(list.layoutInfo) }, onDragCancel = { state.cancel(list.layoutInfo) }) { change, amount ->
            if (state.dragging != null) { change.consume(); state.dragBy(amount.y, list.layoutInfo) }
        }
    }
}

@Composable internal fun Modifier.taskDragVisual(task: Task, tasks: List<Task>, state: TaskDragState, list: LazyListState): Modifier {
    val lifted = state.isLifted(task.id)
    val releasing = state.isReleasing(task.id)
    val lift = remember(task.id) { Animatable(0f) }
    val settle = remember(task.id) { Animatable(0f) }
    val wave = remember(task.id) { Animatable(0f) }
    val halo = MaterialTheme.colorScheme.primary
    LaunchedEffect(lifted, releasing) {
        if (lifted) {
            settle.snapTo(0f)
            coroutineScope {
                launch { lift.animateTo(1f, tween(120, easing = FastOutSlowInEasing)) }
                launch { wave.snapTo(0f); wave.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
            }
        } else {
            if (releasing) settle.snapTo(state.releasedOffset)
            coroutineScope {
                launch { lift.animateTo(0f, tween(150, easing = FastOutSlowInEasing)) }
                launch { settle.animateTo(0f, tween(150, easing = FastOutSlowInEasing)) }
            }
        }
    }
    return onSizeChanged { state.measure(task.id, it.height, it.width) }.zIndex(if (lifted || releasing) 1f else 0f).graphicsLayer {
        val amount = lift.value.coerceIn(0f, 1f)
        translationY = if (lifted) state.translation(list.layoutInfo, task.id) else settle.value
        scaleX = 1f + amount * .010f
        scaleY = 1f + amount * .006f
        transformOrigin = TransformOrigin(.5f, if (lifted || releasing) state.pivotY(task.id, size.height) else .5f)
        val topRadius = if (state.isFamilyStart(task.id)) 20f * amount else 0f
        val bottomRadius = if (state.isFamilyEnd(task.id)) 20f * amount else 0f
        shape = RoundedCornerShape(topStart = topRadius.dp, topEnd = topRadius.dp, bottomStart = bottomRadius.dp, bottomEnd = bottomRadius.dp)
        clip = amount > .001f
        shadowElevation = if (state.isFamilyStart(task.id)) 8.dp.toPx() * amount else 0f
    }.drawWithCache {
        val geometry = state.waveGeometry(task.id, size.width)
        val brush = Brush.radialGradient(listOf(halo.copy(alpha = .14f), halo.copy(alpha = .08f), Color.Transparent),
            geometry.center, geometry.radius.coerceAtLeast(1f))
        onDrawWithContent {
            drawContent()
            val amount = lift.value.coerceIn(0f, 1f)
            val expansion = wave.value.coerceIn(0f, 1f)
            if (amount > .001f && expansion > .001f) withTransform({ scale(expansion, expansion, geometry.center) }) {
                drawCircle(brush, geometry.radius, geometry.center, alpha = amount)
            }
        }
    }.semantics {
        customActions = listOf(
            CustomAccessibilityAction("Move up") { state.accessibleMove(task, -1, tasks) },
            CustomAccessibilityAction("Move down") { state.accessibleMove(task, 1, tasks) },
        )
    }
}

@Composable internal fun taskDragContainerColor(id: String, state: TaskDragState): Color =
    animateColorAsState(if (state.isLifted(id)) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
        tween(130, easing = FastOutSlowInEasing), label = "Task lift tone").value
