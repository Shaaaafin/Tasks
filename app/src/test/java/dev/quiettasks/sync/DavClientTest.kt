package dev.quiettasks.sync

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class DavClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DavClient
    @Before fun start() { server = MockWebServer(); server.start(); client = DavClient(server.url("/").toString(), "alice", "secret", allowLocalHttp = true) }
    @After fun stop() { server.shutdown() }
    private fun multistatus(inner: String) = MockResponse().setResponseCode(207).setBody("<d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\">$inner</d:multistatus>")
    private fun resource(href: String, props: String) = "<d:response><d:href>$href</d:href><d:propstat><d:prop>$props</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
    @Test fun followsPrincipalAndFindsOnlyTaskCapableCalendars() = runBlocking {
        server.enqueue(multistatus(resource("/", "<d:current-user-principal><d:href>/alice/</d:href></d:current-user-principal>")))
        server.enqueue(multistatus(resource("/alice/", "<c:calendar-home-set><d:href>/calendars/alice/</d:href></c:calendar-home-set>")))
        server.enqueue(multistatus(resource("/calendars/alice/tasks/", "<d:resourcetype><d:collection/><c:calendar/></d:resourcetype><d:displayname>My &amp; Tasks</d:displayname><c:supported-calendar-component-set><c:comp name=\"VTODO\"/></c:supported-calendar-component-set>") + resource("/calendars/alice/events/", "<d:resourcetype><c:calendar/></d:resourcetype><c:supported-calendar-component-set><c:comp name=\"VEVENT\"/></c:supported-calendar-component-set>")))
        val result = client.discover()
        assertEquals(server.url("/calendars/alice/").toString(), result.home)
        assertEquals(1, result.collections.size); assertEquals("My & Tasks", result.collections.single().name)
        val request = server.takeRequest(); assertEquals("PROPFIND", request.method); assertTrue(request.getHeader("Authorization")!!.startsWith("Basic "))
    }
    @Test fun inventoryParsesEtagsWithoutDownloadingTasks() = runBlocking {
        server.enqueue(multistatus(resource("/tasks/", "<d:resourcetype><d:collection/></d:resourcetype>") + resource("/tasks/a.ics", "<d:getetag>&quot;version-2&quot;</d:getetag><d:resourcetype/>")))
        val result = client.inventory(server.url("/tasks/").toString())
        assertEquals(1, result.size); assertEquals("\"version-2\"", result.single().etag)
        val request = server.takeRequest()
        assertEquals("1", request.getHeader("Depth")); assertEquals("REPORT", request.method)
        assertTrue(request.body.readUtf8().contains("name=\"VTODO\""))
    }
    @Test fun incompleteInventoryCannotLookLikeAnEmptyCalendar() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>not a calendar</html>"))
        try { client.inventory(server.url("/tasks/").toString()); fail("Should reject partial inventory") } catch (_: IllegalArgumentException) { }
    }
    @Test fun conflictResponsesAreSurfacedForSafeResolution() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(412))
        val reply = client.request("PUT", server.url("/tasks/a.ics").toString(), "BEGIN:VCALENDAR", mapOf("If-Match" to "\"old\""))
        assertEquals(412, reply.code); assertEquals("\"old\"", server.takeRequest().getHeader("If-Match"))
    }
    @Test fun credentialsNeverFollowCrossOriginRedirects() = runBlocking {
        val other = MockWebServer(); other.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/capture")))
            try { client.fetch(server.url("/").toString()); fail("Cross origin redirect must fail") } catch (_: IllegalArgumentException) { }
            assertNull(other.takeRequest(50, TimeUnit.MILLISECONDS))
        } finally { other.shutdown() }
    }
    @Test fun rejectsUnencryptedCredentials() {
        assertThrows(IllegalArgumentException::class.java) { DavClient("http://example.com/", "alice", "secret") }
    }
    @Test fun cancelsNetworkWhenForegroundScopeStops() = runBlocking {
        server.enqueue(MockResponse().setBody("waiting").setHeadersDelay(2, TimeUnit.SECONDS))
        val job = launch(Dispatchers.IO) { client.fetch(server.url("/").toString()) }
        assertNotNull(server.takeRequest(3, TimeUnit.SECONDS)); withTimeout(1000) { job.cancelAndJoin() }; assertTrue(job.isCancelled)
    }
    @Test fun rejectsExternalXmlEntities() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207).setBody("<!DOCTYPE x [<!ENTITY xxe SYSTEM 'file:///private'>]><d:multistatus xmlns:d=\"DAV:\"/>"))
        try { client.inventory(server.url("/").toString()); fail("Unsafe XML accepted") } catch (_: IllegalArgumentException) { }
    }
}
