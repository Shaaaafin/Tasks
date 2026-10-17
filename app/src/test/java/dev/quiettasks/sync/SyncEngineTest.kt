package dev.quiettasks.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.quiettasks.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SyncEngineTest {
    private lateinit var server: MockWebServer
    private lateinit var store: TaskStore
    private lateinit var engine: SyncEngine
    private lateinit var list: TaskList
    private val remote = ConcurrentHashMap<String, Pair<String, String>>()
    private var puts = 0
    private var gets = 0
    private var deletes = 0
    private var invalidInventory = false
    private var duringPut: (() -> Unit)? = null
    private class MemoryPreferences(value: Preferences) : PreferenceStore {
        override val state = MutableStateFlow(value)
        override fun update(transform: (Preferences) -> Preferences) { state.value = transform(state.value) }
    }
    private fun response(href: String, props: String) = "<d:response><d:href>$href</d:href><d:propstat><d:prop>$props</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
    private fun multi(xml: String) = MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">$xml</d:multistatus>")
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("tasks.db"); store = TaskStore(context); store.load()
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path!!
                return when (request.method) {
                    "PROPFIND", "REPORT" -> {
                        if (request.getHeader("Depth") == "0") multi(response("/tasks/", "<d:resourcetype><d:collection/><c:calendar/></d:resourcetype><d:displayname>My Tasks</d:displayname><c:supported-calendar-component-set><c:comp name=\"VTODO\"/></c:supported-calendar-component-set>"))
                        else if (invalidInventory) MockResponse().setResponseCode(207).setBody("<html>partial</html>")
                        else multi(response("/tasks/", "<d:resourcetype><d:collection/></d:resourcetype>") + remote.entries.joinToString("") { (href, data) -> response(href, "<d:getetag>${DavClient.escapeXml(data.second)}</d:getetag><d:resourcetype/>") })
                    }
                    "GET" -> { gets++; remote[path]?.let { MockResponse().setHeader("ETag", it.second).setBody(it.first) } ?: MockResponse().setResponseCode(404) }
                    "PUT" -> {
                        puts++
                        val existing = remote[path]
                        if ((request.getHeader("If-None-Match") == "*" && existing != null) || (request.getHeader("If-Match") != null && request.getHeader("If-Match") != existing?.second)) MockResponse().setResponseCode(412)
                        else {
                            val etag = "\"$puts\""; remote[path] = request.body.readUtf8() to etag
                            duringPut?.also { duringPut = null; it() }
                            MockResponse().setResponseCode(if (existing == null) 201 else 204).setHeader("ETag", etag)
                        }
                    }
                    "DELETE" -> { deletes++; if (request.getHeader("If-Match") != remote[path]?.second) MockResponse().setResponseCode(412) else { remote.remove(path); MockResponse().setResponseCode(204) } }
                    else -> MockResponse().setResponseCode(405)
                }
            }
        }
        server.start()
        list = store.snapshot().lists.single().copy(remoteUrl = server.url("/tasks/").toString(), dirty = false)
        store.applyRemote(list)
        val prefs = MemoryPreferences(Preferences(syncMode = SyncMode.MANUAL, serverUrl = server.url("/tasks/").toString(), username = "alice", password = "secret"))
        engine = SyncEngine(context, store, prefs, { DavClient(it.serverUrl, it.username, it.password, allowLocalHttp = true) }, { true }).apply { visible = true }
    }
    @After fun close() { server.shutdown(); store.close() }
    @Test fun twoWaySyncIsIncrementalAndDeletesWithEtags() = runBlocking {
        store.saveTask(Task(id = "one", listId = list.id, title = "Local task", priority = TaskPriority.HIGH, tags = listOf("work", "errands")))
        assertEquals("Up to date", engine.sync(SyncTrigger.MANUAL)); assertEquals(1, puts)
        assertFalse(store.snapshot().tasks.single().dirty)
        assertTrue(remote.values.single().first.contains("CATEGORIES:work,errands"))
        assertTrue(remote.values.single().first.contains("PRIORITY:1"))
        engine.sync(SyncTrigger.MANUAL); assertEquals(1, puts); assertEquals(0, gets)
        val path = remote.keys.single(); remote[path] = remote[path]!!.first.replace("SUMMARY:Local task", "SUMMARY:Remote edit").replace("PRIORITY:1", "PRIORITY:5").replace("CATEGORIES:work,errands", "CATEGORIES:personal") to "\"remote\""
        engine.sync(SyncTrigger.MANUAL); assertEquals("Remote edit", store.snapshot().tasks.single().title); assertEquals(1, gets)
        assertEquals(TaskPriority.MEDIUM, store.snapshot().tasks.single().priority); assertFalse(store.snapshot().tasks.single().starred)
        assertEquals(listOf("personal"), store.snapshot().tasks.single().tags)
        store.deleteTask("one"); engine.sync(SyncTrigger.MANUAL)
        assertEquals(1, deletes); assertTrue(remote.isEmpty()); assertTrue(store.snapshot().tasks.isEmpty())
    }
    @Test fun concurrentEditsPreserveBothVersions() = runBlocking {
        store.saveTask(Task(id = "one", listId = list.id, title = "Original")); engine.sync(SyncTrigger.MANUAL)
        store.saveTask(store.snapshot().tasks.single().copy(title = "Local edit"))
        val path = remote.keys.single(); remote[path] = remote[path]!!.first.replace("SUMMARY:Original", "SUMMARY:Remote edit") to "\"remote\""
        val result = engine.sync(SyncTrigger.MANUAL)
        assertTrue(result.contains("conflict")); assertEquals(2, remote.size)
        assertEquals(setOf("Remote edit", "Local edit (local copy)"), store.snapshot().tasks.map { it.title }.toSet())
    }
    @Test fun malformedInventoryNeverErasesLocalTasks() = runBlocking {
        store.saveTask(Task(id = "one", listId = list.id, title = "Keep this")); engine.sync(SyncTrigger.MANUAL)
        invalidInventory = true
        engine.sync(SyncTrigger.MANUAL)
        assertEquals("Keep this", store.snapshot().tasks.single().title); assertEquals(0, deletes)
    }
    @Test fun editingDuringUploadDoesNotClearTheNewerChange() = runBlocking {
        store.saveTask(Task(id = "one", listId = list.id, title = "First edit"))
        duringPut = { store.saveTask(store.snapshot().tasks.single().copy(title = "Newer edit")) }
        engine.sync(SyncTrigger.MANUAL)
        assertEquals("Newer edit", store.snapshot().tasks.single().title); assertTrue(store.snapshot().tasks.single().dirty)
        engine.sync(SyncTrigger.MANUAL); assertFalse(store.snapshot().tasks.single().dirty)
        assertTrue(remote.values.single().first.contains("SUMMARY:Newer edit"))
    }
}
