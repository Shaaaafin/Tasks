package dev.quiettasks.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface PreferenceStore {
    val state: StateFlow<Preferences>
    fun update(transform: (Preferences) -> Preferences)
}

class SettingsStore(context: Context) : PreferenceStore {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val keyAlias = "quiettasks.caldav.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String = if (value.isEmpty()) "" else runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }.getOrDefault("")
    private inline fun <reified T : Enum<T>> enum(key: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == prefs.getString(key, null) } ?: default
    private fun read() = Preferences(
        theme = enum("theme", ThemeMode.SYSTEM), syncMode = enum("syncMode", SyncMode.WHILE_USING),
        wifiOnly = prefs.getBoolean("wifiOnly", false), serverUrl = prefs.getString("serverUrl", "")!!,
        username = prefs.getString("username", "")!!, password = decrypt(prefs.getString("secret", "")!!),
        lastSyncAt = prefs.getLong("lastSyncAt", 0), lastSyncMessage = prefs.getString("lastSyncMessage", "Not synced yet")!!,
        selectedList = if (prefs.getBoolean("summaryHomeEnabled", false)) prefs.getString("selectedList", ALL_TASKS_TAB)!! else ALL_TASKS_TAB, sort = enum("sort", SortMode.MY_ORDER),
        reminders = prefs.getBoolean("reminders", true),
        deviceColors = prefs.getBoolean("deviceColors", true),
        allTabColor = prefs.getString("allTabColor", "default")!!, allTabIcon = prefs.getString("allTabIcon", "")!!,
        starredTabColor = prefs.getString("starredTabColor", "default")!!, starredTabIcon = prefs.getString("starredTabIcon", "")!!,
        summarySort = enum("summarySort", SortMode.DATE),
        listSorts = runCatching { org.json.JSONObject(prefs.getString("listSorts", "{}")!!).let { json -> json.keys().asSequence().mapNotNull { key -> SortMode.entries.firstOrNull { it.name == json.getString(key) }?.let { key to it } }.toMap() } }.getOrDefault(emptyMap()),
        longPressAction = enum("longPressAction", LongPressAction.REORDER),
        showAllTab = prefs.getBoolean("showAllTab", true), showStarredTab = prefs.getBoolean("showStarredTab", true),
    )
    private val mutable = MutableStateFlow(read())
    override val state = mutable.asStateFlow()
    @Synchronized override fun update(transform: (Preferences) -> Preferences) {
        val previous = mutable.value
        val next = transform(previous)
        val editor = prefs.edit().putBoolean("summaryHomeEnabled", true).putString("theme", next.theme.name).putString("syncMode", next.syncMode.name)
            .putBoolean("wifiOnly", next.wifiOnly).putString("serverUrl", next.serverUrl)
            .putString("username", next.username).putLong("lastSyncAt", next.lastSyncAt)
            .putString("lastSyncMessage", next.lastSyncMessage).putString("selectedList", next.selectedList)
            .putString("sort", next.sort.name).putBoolean("reminders", next.reminders).putBoolean("deviceColors", next.deviceColors)
            .putString("allTabColor", next.allTabColor).putString("allTabIcon", next.allTabIcon)
            .putString("starredTabColor", next.starredTabColor).putString("starredTabIcon", next.starredTabIcon)
            .putString("summarySort", next.summarySort.name).putString("listSorts", org.json.JSONObject(next.listSorts.mapValues { it.value.name }).toString())
            .putString("longPressAction", next.longPressAction.name).putBoolean("showAllTab", next.showAllTab).putBoolean("showStarredTab", next.showStarredTab)
        if (previous.password != next.password) editor.putString("secret", encrypt(next.password))
        check(editor.commit()) { "Could not save settings" }
        mutable.value = next
    }
}
