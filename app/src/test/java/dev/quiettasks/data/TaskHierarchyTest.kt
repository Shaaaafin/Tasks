package dev.quiettasks.data

import org.junit.Assert.*
import org.junit.Test

class TaskHierarchyTest {
    @Test fun allImportedLevelsRemainVisible() {
        val root = Task(id = "a", listId = "l", title = "A")
        val child = Task(id = "b", listId = "l", title = "B", parentId = "a")
        val grandchild = Task(id = "c", listId = "l", title = "C", parentId = "b")
        assertEquals(listOf("a", "b", "c"), taskHierarchy(listOf(grandchild, child, root)).map { it.id })
    }
    @Test fun malformedCyclesNeitherLoopNorHideTasks() {
        val a = Task(id = "a", listId = "l", title = "A", parentId = "b")
        val b = Task(id = "b", listId = "l", title = "B", parentId = "a")
        assertEquals(setOf("a", "b"), taskHierarchy(listOf(a, b)).map { it.id }.toSet())
    }
}
