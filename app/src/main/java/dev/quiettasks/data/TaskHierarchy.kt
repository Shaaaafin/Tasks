package dev.quiettasks.data

/** Render imported hierarchy levels while preventing cycles. */
fun taskHierarchy(tasks: List<Task>): List<Task> {
    val ids = tasks.mapTo(HashSet()) { it.id }
    val children = tasks.groupBy { it.parentId }
    val seen = HashSet<String>()
    return buildList {
        fun appendBranch(root: Task) {
            val pending = ArrayDeque<Task>().apply { addLast(root) }
            while (pending.isNotEmpty()) {
                val next = pending.removeLast()
                if (seen.add(next.id)) {
                    add(next)
                    children[next.id]?.asReversed()?.forEach { pending.addLast(it) }
                }
            }
        }
        tasks.filter { it.parentId == null || it.parentId !in ids }.forEach(::appendBranch)
        tasks.forEach(::appendBranch)
    }
}
