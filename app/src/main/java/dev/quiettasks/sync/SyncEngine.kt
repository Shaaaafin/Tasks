package dev.quiettasks.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import dev.quiettasks.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.coroutines.coroutineContext

enum class SyncTrigger { FOREGROUND, CHARGING, MANUAL }
data class SyncStatus(val running: Boolean = false, val message: String = "")

fun maySync(mode: SyncMode, trigger: SyncTrigger, visible: Boolean, charging: Boolean): Boolean = when (trigger) {
    SyncTrigger.FOREGROUND -> mode == SyncMode.WHILE_USING && visible
    SyncTrigger.CHARGING -> mode == SyncMode.CHARGING_ONLY && charging
    SyncTrigger.MANUAL -> visible
}

class SyncEngine(private val context: Context, private val store: TaskStore, private val settings: PreferenceStore,
    private val clientFactory: (Preferences) -> DavClient = { DavClient(it.serverUrl, it.username, it.password) },
    private val networkAvailable: ((Boolean) -> Boolean)? = null,
) {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(SyncStatus())
    val state = mutable.asStateFlow()
    @Volatile var visible = false
    private fun online(wifiOnly: Boolean): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && (!wifiOnly || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
    }
    suspend fun sync(trigger: SyncTrigger): String = withContext(Dispatchers.IO) {
        val config = settings.state.value
        val charging = context.getSystemService(BatteryManager::class.java).isCharging
        if (!maySync(config.syncMode, trigger, visible, charging)) return@withContext "Sync is paused by your sync preference"
        if (!config.connected) return@withContext "Add your CalDAV account in Settings"
        if (!(networkAvailable?.invoke(config.wifiOnly) ?: online(config.wifiOnly))) return@withContext if (config.wifiOnly) "Waiting for Wi-Fi" else "No network connection"
        if (!lock.tryLock()) return@withContext "Sync already in progress"
        mutable.value = SyncStatus(true, "Syncing…")
        try {
            val dav = clientFactory(config)
            val discovery = dav.discover()
            var conflicts = 0
            var unsupported = 0
            discovery.collections.forEach { remote ->
                val snapshot = store.snapshot()
                val existing = snapshot.lists.firstOrNull { it.remoteUrl?.trimEnd('/') == remote.url.trimEnd('/') }
                    ?: snapshot.lists.firstOrNull { it.remoteUrl == null && remote.url.toHttpUrl().pathSegments.lastOrNull(String::isNotEmpty) == "quiettasks-${it.id}" }?.also {
                        store.applyRemote(it.copy(remoteUrl = remote.url))
                    }
                if (existing == null) {
                    val emptyDefault = snapshot.lists.singleOrNull()?.takeIf { it.remoteUrl == null && snapshot.tasks.isEmpty() }
                    store.applyRemote((emptyDefault ?: TaskList(name = remote.name)).copy(name = remote.name, remoteUrl = remote.url, dirty = false))
                } else if (!existing.dirty && !existing.deleted && existing.name != remote.name) store.applyRemote(existing.copy(name = remote.name))
            }
            store.snapshot().lists.forEach { list ->
                coroutineContext.ensureActive()
                var url = list.remoteUrl
                if (url == null && !list.deleted) {
                    val expectedUrl = discovery.home.toHttpUrl().newBuilder().addPathSegment("quiettasks-${list.id}").addPathSegment("").build().toString()
                    // A prior MKCALENDAR may have succeeded before the process was interrupted.
                    url = discovery.collections.firstOrNull { it.url.trimEnd('/') == expectedUrl.trimEnd('/') }?.url ?: dav.create(discovery.home, list.id, list.name)
                    store.markListSynced(list, url)
                } else if (url != null && list.dirty && !list.deleted) { dav.rename(url, list.name); store.markListSynced(list, url) }
                if (url == null) return@forEach
                val collection = url
                val resources = dav.inventory(collection)
                val hrefs = resources.map { it.href }.toSet()
                val before = store.snapshot().tasks.filter { it.listId == list.id }
                for (resource in resources) {
                    coroutineContext.ensureActive()
                    val local = before.firstOrNull { it.remoteHref == resource.href }
                    if (list.deleted && local == null) continue
                    if (local?.etag == resource.etag) continue
                    val fetched = dav.fetch(resource.href)
                    if (fetched.code == 404 || fetched.code == 410) continue
                    val decoded = runCatching { ICalendar.decode(fetched.text, list.id, resource.href, fetched.etag ?: resource.etag) }.getOrNull()
                    if (decoded == null) { unsupported++; continue }
                    val remoteId = local?.id ?: store.snapshot().tasks.firstOrNull { it.id == decoded.id }?.let {
                        if (it.listId == list.id && it.remoteHref == null) it.id else UUID.nameUUIDFromBytes("${list.id}/${decoded.id}".toByteArray()).toString()
                    } ?: decoded.id
                    val remote = decoded.copy(id = remoteId)
                    if (local?.dirty == true) {
                        if (list.deleted) store.applyRemote(list.copy(deleted = false, dirty = false))
                        store.preserveConflict(local, remote); conflicts++
                    }
                    else store.applyRemote(remote)
                }
                val imported = store.snapshot().tasks.filter { it.listId == list.id && !it.deleted }
                val uids = imported.associate { task -> (task.rawCalendar?.let { runCatching { ICalendar.uid(it) }.getOrNull() } ?: task.id) to task.id }
                imported.filter { !it.dirty && it.parentId != null }.forEach { task ->
                    uids[task.parentId]?.takeIf { it != task.parentId && it != task.id }?.let { parent -> store.applyRemote(task.copy(parentId = parent)) }
                }
                before.filter { it.remoteHref != null && it.remoteHref !in hrefs }.forEach { missing ->
                    if (missing.dirty && !missing.deleted) { store.preserveConflict(missing, null); conflicts++ }
                    else if (missing.deleted) store.discardSyncedDeletion(missing)
                    else store.remoteDeleted(missing.id)
                }
                for (task in store.snapshot().tasks.filter { it.listId == list.id && it.dirty }) {
                    coroutineContext.ensureActive()
                    if (task.deleted) {
                        if (task.remoteHref == null) { store.discardSyncedDeletion(task); continue }
                        require(task.etag != null) { "Missing ETag; deletion deferred for safety" }
                        val reply = dav.request("DELETE", task.remoteHref, headers = mapOf("If-Match" to task.etag))
                        if (reply.code == 412) {
                            val latest = dav.fetch(task.remoteHref)
                            val remote = ICalendar.decode(latest.text, list.id, task.remoteHref, latest.etag)
                            if (list.deleted) store.applyRemote(list.copy(deleted = false, dirty = false))
                            store.preserveConflict(task, remote); conflicts++
                        } else store.discardSyncedDeletion(task)
                        continue
                    }
                    val href = task.remoteHref ?: collection.toHttpUrl().newBuilder().addPathSegment("${task.id}.ics").build().toString()
                    val parent = task.parentId?.let { id -> store.snapshot().tasks.firstOrNull { it.id == id } }?.let { it.rawCalendar?.let(ICalendar::uid) ?: it.id } ?: task.parentId
                    val raw = ICalendar.encode(task, parent)
                    val headers = if (task.remoteHref == null) mapOf("If-None-Match" to "*") else mapOf("If-Match" to (task.etag ?: error("Missing ETag; update deferred for safety")))
                    val reply = dav.request("PUT", href, raw, headers)
                    if (reply.code == 412) {
                        val latest = dav.fetch(href)
                        val remote = if (latest.code in setOf(404, 410)) null else ICalendar.decode(latest.text, list.id, href, latest.etag)
                        store.preserveConflict(task, remote); conflicts++
                    } else {
                        require(reply.code in 200..299) { "Task upload failed (HTTP ${reply.code})" }
                        val etag = reply.etag ?: dav.inventory(collection).firstOrNull { it.href == href }?.etag
                        require(etag != null) { "The server did not provide an ETag" }
                        store.markSynced(task, href, etag, raw)
                    }
                }
            }
            val result = buildString {
                append("Up to date")
                if (conflicts > 0) append(" · $conflicts conflict(s) preserved")
                if (unsupported > 0) append(" · $unsupported unsupported resource(s) left unchanged")
            }
            settings.update { it.copy(lastSyncAt = System.currentTimeMillis(), lastSyncMessage = result) }
            mutable.value = SyncStatus(false, result); result
        } catch (cancel: CancellationException) {
            mutable.value = SyncStatus(false, "Sync paused"); throw cancel
        } catch (error: Exception) {
            val message = error.message?.take(240) ?: "Could not sync. Check your connection and account."
            settings.update { it.copy(lastSyncMessage = message) }
            mutable.value = SyncStatus(false, message); message
        } finally { lock.unlock() }
    }
}
