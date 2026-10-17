package dev.quiettasks.ui

import androidx.compose.ui.unit.*
import dev.quiettasks.data.*
import org.junit.Assert.*
import org.junit.Test

class TaskGesturesTest {
    private val parent = Task(id = "parent", listId = "a", title = "Parent")
    private val child = Task(id = "child", listId = "a", title = "Child", parentId = "parent")
    private val sibling = child.copy(id = "sibling", title = "Sibling")
    private val other = Task(id = "other", listId = "b", title = "Other")
    @Test fun childReorderingStaysInsideItsParentAndRootMovesKeepHierarchy() {
        val tasks = listOf(parent, child, sibling, other)
        assertEquals(listOf(child, sibling), taskReorderPeers(child, tasks))
        assertEquals(listOf(parent, other), taskReorderPeers(parent, tasks))
        var saved = emptyList<String>()
        val drag = TaskDragState { saved = it }
        assertTrue(drag.accessibleMove(sibling, -1, tasks))
        assertEquals(listOf("parent", "sibling", "child", "other"), saved)
        assertTrue(drag.accessibleMove(parent, 1, tasks))
        val ordered = saved.map { id -> tasks.first { it.id == id } }
        assertEquals(listOf("other", "parent", "child", "sibling"), taskHierarchy(ordered).map { it.id })
        assertFalse(drag.accessibleMove(child, -1, tasks))
    }
    @Test fun canceledLiftDoesNotWriteOrder() {
        var saved = false
        val drag = TaskDragState { saved = true }
        assertTrue(drag.start(parent, listOf(parent, other), 100, 80, 140f))
        drag.cancel()
        assertNull(drag.dragging); assertNull(drag.ids); assertFalse(saved)
    }
    @Test fun menuStartsAtFingerAndClampsAtWindowEdges() {
        val window = IntSize(1000, 1800); val popup = IntSize(300, 400)
        assertEquals(IntOffset(230, 450), FingerMenuPosition(IntOffset(230, 450), 8).calculatePosition(IntRect.Zero, window, LayoutDirection.Ltr, popup))
        assertEquals(IntOffset(692, 1392), FingerMenuPosition(IntOffset(990, 1790), 8).calculatePosition(IntRect.Zero, window, LayoutDirection.Ltr, popup))
        assertEquals(IntOffset(8, 8), FingerMenuPosition(IntOffset(-2, -3), 8).calculatePosition(IntRect.Zero, window, LayoutDirection.Rtl, popup))
    }
    @Test fun earlyTabsStayAtStartAndLaterTabsCenterWithTheSamePageProgress() {
        val centers = listOf(40f, 130f, 260f, 380f, 500f)
        assertEquals(0f, tabScrollTarget(centers, 0f, 400f, 300), .01f)
        assertEquals(0f, tabScrollTarget(centers, 1f, 400f, 300), .01f)
        assertEquals(30f, tabScrollTarget(centers, 1.5f, 400f, 300), .01f)
        assertEquals(180f, tabScrollTarget(centers, 3f, 400f, 300), .01f)
        assertEquals(100f, tabScrollTarget(centers, 4f, 400f, 100), .01f)
    }

    @Test fun parentLiftIncludesItsVisibleFamilyAndKeepsOneTranslationDuringDragAndRelease() {
        val drag = TaskDragState { }
        val tasks = listOf(parent, child, sibling, other)
        drag.measure(child.id, 60); drag.measure(sibling.id, 70)
        assertTrue(drag.start(parent, tasks, 100, 100, 140f))
        assertTrue(drag.isLifted(parent.id)); assertTrue(drag.isLifted(child.id)); assertTrue(drag.isLifted(sibling.id))
        assertFalse(drag.isLifted(other.id))
        assertTrue(drag.isFamilyStart(parent.id)); assertTrue(drag.isFamilyEnd(sibling.id))
        assertEquals(100f, drag.familyOffset(child.id), .01f)
        assertEquals(160f, drag.familyOffset(sibling.id), .01f)
        val original = layout(listOf(parent.id to 100, child.id to 200, sibling.id to 260, other.id to 330))
        drag.dragBy(40f, original)
        assertEquals(40f, drag.translation(original, parent.id), .01f)
        assertEquals(40f, drag.translation(original, child.id), .01f)
        val moved = layout(listOf(parent.id to 300, child.id to 400, sibling.id to 460))
        assertEquals(-160f, drag.translation(moved, parent.id), .01f)
        assertEquals(-160f, drag.translation(moved, sibling.id), .01f)
        val scrolled = layout(listOf(child.id to 400, sibling.id to 460))
        assertEquals(-160f, drag.translation(scrolled, child.id), .01f)
        drag.end(scrolled)
        assertTrue(drag.isReleasing(parent.id)); assertTrue(drag.isReleasing(child.id)); assertTrue(drag.isReleasing(sibling.id))
        assertEquals(-160f, drag.releasedOffset, .01f)
        drag.finishRelease(parent.id)
        assertFalse(drag.isReleasing(child.id))
    }
    @Test fun aSingleFamilyCanLiftAndLiftingAChildLeavesItsSiblingsInPlace() {
        val drag = TaskDragState { fail("Unchanged order must not be saved") }
        val tasks = listOf(parent, child, sibling)
        assertTrue(drag.start(parent, tasks, 0, 100, 50f))
        assertTrue(drag.isLifted(child.id)); drag.cancel()
        assertTrue(drag.start(child, tasks, 100, 60, 120f))
        assertTrue(drag.isLifted(child.id)); assertFalse(drag.isLifted(parent.id)); assertFalse(drag.isLifted(sibling.id))
        drag.end()
    }

    @Test fun pressWaveCoversTheFullFamilyFromTheRecordedFingerPosition() {
        val drag = TaskDragState { }
        drag.measure(parent.id, 100, 400); drag.measure(child.id, 60, 400); drag.measure(sibling.id, 70, 400)
        assertEquals(.25f, drag.pressFraction(parent.id, 120f, 440), .01f)
        drag.start(parent, listOf(parent, child, sibling), 100, 100, 140f, .25f)
        assertEquals(230f, drag.familyHeight, .01f)
        val rootWave = drag.waveGeometry(parent.id, 400f)
        val childWave = drag.waveGeometry(sibling.id, 400f)
        assertEquals(100f, rootWave.center.x, .01f)
        assertEquals(40f, rootWave.center.y, .01f)
        assertEquals(-120f, childWave.center.y, .01f)
        assertEquals(rootWave.radius, childWave.radius, .01f)
        val farCorner = kotlin.math.hypot(400f - childWave.center.x, 70f - childWave.center.y)
        assertTrue("The expanding wave reaches the far corner of the last child", childWave.radius >= farCorner)
    }
    private fun layout(rows: List<Pair<String, Int>>): androidx.compose.foundation.lazy.LazyListLayoutInfo {
        val items = rows.map { (id, offset) ->
            java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(androidx.compose.foundation.lazy.LazyListItemInfo::class.java)) { _, method, _ ->
                when (method.name) { "getKey" -> id; "getOffset" -> offset; "getSize" -> 100; else -> null }
            } as androidx.compose.foundation.lazy.LazyListItemInfo
        }
        return java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(androidx.compose.foundation.lazy.LazyListLayoutInfo::class.java)) { _, method, _ ->
            when (method.name) { "getVisibleItemsInfo" -> items; else -> null }
        } as androidx.compose.foundation.lazy.LazyListLayoutInfo
    }

}
