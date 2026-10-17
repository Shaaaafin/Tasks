package dev.quiettasks.sync

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DavCollection(val url: String, val name: String)
data class Discovery(val home: String, val collections: List<DavCollection>)
data class DavResource(val href: String, val etag: String)
data class DavReply(val code: Int, val text: String, val etag: String?, val location: String? = null)
class DavException(val code: Int, message: String) : IOException(message)

class DavClient(server: String, private val username: String, private val password: String,
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build(),
    allowLocalHttp: Boolean = false,
) {
    private val base = server.toHttpUrl()
    init { require(base.username.isEmpty() && base.password.isEmpty() && (base.isHttps || (allowLocalHttp && base.host in setOf("localhost", "127.0.0.1")))) { "Use an HTTPS server URL without embedded credentials" } }
    fun resolve(url: String, href: String): String {
        val resolved = url.toHttpUrl().resolve(href) ?: error("Invalid server path")
        require(resolved.scheme == base.scheme && resolved.host == base.host && resolved.port == base.port && resolved.username.isEmpty() && resolved.password.isEmpty()) { "Server redirected to another origin; credentials were not sent" }
        return resolved.toString()
    }
    private suspend fun execute(request: Request): DavReply = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val source = response.body?.source()
                        val text = if (response.code in 300..399) "" else source?.let {
                            require(!it.request(8L * 1024 * 1024 + 1)) { "Server response is too large" }; it.readUtf8()
                        }.orEmpty()
                        if (continuation.isActive) continuation.resume(DavReply(response.code, text, response.header("ETag"), response.header("Location")))
                    }
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
    suspend fun request(method: String, url: String, body: String? = null, headers: Map<String, String> = emptyMap()): DavReply {
        var address = resolve(base.toString(), url)
        repeat(6) {
            val contentType = if (method == "PUT") "text/calendar; charset=utf-8" else "application/xml; charset=utf-8"
            val request = Request.Builder().url(address).header("Authorization", Credentials.basic(username, password, Charsets.UTF_8))
                .header("User-Agent", "QuietTasks/0.1").apply { headers.forEach { (key, value) -> header(key, value) } }
                .method(method, body?.toRequestBody(contentType.toMediaType())).build()
            val response = execute(request)
                if (response.code in setOf(301, 302, 307, 308)) {
                    address = resolve(address, response.location ?: error("Missing redirect address"))
                } else {
                    if (response.code in 200..299 || response.code in setOf(404, 410, 412)) return response
                    throw DavException(response.code, when (response.code) {
                        401 -> "Sign-in failed. Check your username and app password."
                        403 -> "The server denied access to this calendar."
                        429 -> "The server is busy. Try syncing later."
                        else -> "The DAV server returned HTTP ${response.code}."
                    })
                }
        }
        error("Too many DAV redirects")
    }
    private fun xml(text: String): Element {
        require(!text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true)) { "Unsafe XML response" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // Android XML implementations do not all expose these SAX switches.
            // The mandatory DTD/entity rejection above also protects those implementations.
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(text.byteInputStream()).documentElement
    }
    private fun Element.children(ns: String, name: String) = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.namespaceURI == ns && it.localName == name }
    private fun Element.child(ns: String, name: String) = children(ns, name).firstOrNull()
    private fun Element.desc(ns: String, name: String) = getElementsByTagNameNS(ns, name).let { if (it.length > 0) it.item(0) as Element else null }
    private data class Entry(val href: String, val props: List<Element>, val status: Int?) {
        fun prop(ns: String, name: String) = props.firstNotNullOfOrNull { it.getElementsByTagNameNS(ns, name).let { nodes -> if (nodes.length > 0) nodes.item(0) as Element else null } }
    }
    private fun entries(reply: DavReply): List<Entry> {
        require(reply.code == 207) { "Expected a complete DAV multi-status response (HTTP ${reply.code})" }
        val root = xml(reply.text)
        require(root.namespaceURI == DAV && root.localName == "multistatus") { "Invalid DAV response" }
        return root.children(DAV, "response").map { element ->
            val href = element.child(DAV, "href")?.textContent?.trim() ?: error("Missing resource path")
            val props = element.children(DAV, "propstat").filter { it.child(DAV, "status")?.textContent?.contains(" 200 ") == true }.mapNotNull { it.child(DAV, "prop") }
            val status = element.child(DAV, "status")?.textContent?.split(' ')?.getOrNull(1)?.toIntOrNull()
            Entry(href, props, status)
        }
    }
    private suspend fun inspect(url: String, depth: String = "0") = entries(request("PROPFIND", url, """
        <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:prop>
        <d:resourcetype/><d:displayname/><d:current-user-principal/><c:calendar-home-set/><c:supported-calendar-component-set/>
        </d:prop></d:propfind>
    """.trimIndent(), mapOf("Depth" to depth)))
    suspend fun discover(): Discovery {
        var address = base.toString()
        var found = runCatching { inspect(address) }.getOrElse { first ->
            if (first is CancellationException) throw first
            if (first is DavException && first.code in setOf(401, 403)) throw first
            address = resolve(address, "/.well-known/caldav"); inspect(address)
        }
        val direct = found.firstOrNull { it.prop(DAV, "resourcetype")?.desc(CAL, "calendar") != null }
        if (direct != null) {
            val url = resolve(address, direct.href)
            require(supportsTasks(direct)) { "This calendar does not support tasks (VTODO)" }
            return Discovery(url.toHttpUrl().resolve("../")!!.toString(), listOf(DavCollection(url, direct.prop(DAV, "displayname")?.textContent.orEmpty().ifBlank { "Tasks" })))
        }
        var home = found.firstNotNullOfOrNull { it.prop(CAL, "calendar-home-set")?.desc(DAV, "href")?.textContent }
        if (home == null) {
            found.firstNotNullOfOrNull { it.prop(DAV, "current-user-principal")?.desc(DAV, "href")?.textContent }?.let { principal ->
                address = resolve(address, principal); found = inspect(address)
                home = found.firstNotNullOfOrNull { it.prop(CAL, "calendar-home-set")?.desc(DAV, "href")?.textContent }
            }
        }
        val homeUrl = resolve(address, home ?: base.toString()).let { if (it.endsWith('/')) it else "$it/" }
        val collections = inspect(homeUrl, "1").filter { it.prop(DAV, "resourcetype")?.desc(CAL, "calendar") != null && supportsTasks(it) }
            .map { DavCollection(resolve(homeUrl, it.href), it.prop(DAV, "displayname")?.textContent.orEmpty().ifBlank { "Tasks" }) }
        return Discovery(homeUrl, collections)
    }
    private fun supportsTasks(entry: Entry): Boolean {
        val supported = entry.prop(CAL, "supported-calendar-component-set") ?: return true
        return supported.getElementsByTagNameNS(CAL, "comp").let { nodes -> (0 until nodes.length).any { (nodes.item(it) as Element).getAttribute("name") == "VTODO" } }
    }
    suspend fun inventory(url: String): List<DavResource> = entries(request("REPORT", url,
        "<c:calendar-query xmlns:c=\"$CAL\" xmlns:d=\"DAV:\"><d:prop><d:getetag/></d:prop><c:filter><c:comp-filter name=\"VCALENDAR\"><c:comp-filter name=\"VTODO\"/></c:comp-filter></c:filter></c:calendar-query>", mapOf("Depth" to "1")))
        .filter { it.prop(DAV, "resourcetype")?.desc(DAV, "collection") == null && resolve(url, it.href).trimEnd('/') != url.trimEnd('/') }
        .map { require(it.status == null || it.status == 200) { "Could not read every calendar resource" }; DavResource(resolve(url, it.href), it.prop(DAV, "getetag")?.textContent?.takeIf(String::isNotBlank) ?: error("Missing resource ETag")) }
    suspend fun fetch(url: String): DavReply = request("GET", url)
    suspend fun create(home: String, id: String, name: String): String {
        val url = home.toHttpUrl().newBuilder().addPathSegment("quiettasks-$id").addPathSegment("").build().toString()
        val result = request("MKCALENDAR", url, "<c:mkcalendar xmlns:c=\"$CAL\" xmlns:d=\"DAV:\"><d:set><d:prop><d:displayname>${escapeXml(name)}</d:displayname><c:supported-calendar-component-set><c:comp name=\"VTODO\"/></c:supported-calendar-component-set></d:prop></d:set></c:mkcalendar>")
        require(result.code in 200..299) { "Could not create the remote list" }; return url
    }
    suspend fun rename(url: String, name: String) {
        val reply = request("PROPPATCH", url, "<d:propertyupdate xmlns:d=\"DAV:\"><d:set><d:prop><d:displayname>${escapeXml(name)}</d:displayname></d:prop></d:set></d:propertyupdate>")
        require(entries(reply).any { it.prop(DAV, "displayname") != null }) { "The server could not rename this list" }
    }
    companion object {
        const val DAV = "DAV:"
        const val CAL = "urn:ietf:params:xml:ns:caldav"
        fun escapeXml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    }
}
