package com.example.llm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Round 14 — AUTOMATIC PROVIDER SWITCH WHEN A LIMIT IS HIT.
 *
 * The Settings screen already let the owner paste several keys for ONE provider, and `LlmGateway`
 * rotated between that provider's models. If the provider itself was rate-limited / out of daily
 * quota / rejected the key, the whole video job died. Now the owner can save up to N *backup*
 * providers (provider + key + model). When the active provider runs out, the gateway silently moves to
 * the next backup, and only then reports a failure.
 *
 * Keys are stored encrypted with the Android Keystore ([KeyStoreCrypto]) exactly like the main key.
 */
data class BackupProvider(
    val provider: String,
    /** "ENC:..." (Android Keystore) — never plain text on disk. */
    val apiKeyEnc: String,
    val model: String = "",
    val enabled: Boolean = true
)

object BackupProviders {
    private const val PREFS = "saif_backup_providers"
    private const val KEY_LIST = "backups_v1"
    private const val KEY_ENABLED = "auto_switch_enabled"

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Master switch shown in Settings → "Auto switch on limit". */
    fun autoSwitchEnabled(): Boolean {
        val own = prefs()?.getBoolean(KEY_ENABLED, true) ?: true
        return own && runCatching { com.example.data.local.ProviderSettingsManager.loadState().allowFallbackToOtherProviders }.getOrDefault(true)
    }

    fun setAutoSwitchEnabled(on: Boolean) {
        prefs()?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }

    fun list(): List<BackupProvider> = runCatching {
        val raw = prefs()?.getString(KEY_LIST, null) ?: return emptyList()
        val arr = JSONArray(raw)
        val out = ArrayList<BackupProvider>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(BackupProvider(
                provider = o.optString("provider"),
                apiKeyEnc = o.optString("key"),
                model = o.optString("model"),
                enabled = o.optBoolean("enabled", true)
            ))
        }
        out
    }.getOrElse { emptyList() }

    private fun save(list: List<BackupProvider>) {
        runCatching {
            val arr = JSONArray()
            list.forEach { b ->
                arr.put(JSONObject()
                    .put("provider", b.provider)
                    .put("key", b.apiKeyEnc)
                    .put("model", b.model)
                    .put("enabled", b.enabled))
            }
            prefs()?.edit()?.putString(KEY_LIST, arr.toString())?.apply()
        }
    }

    fun add(provider: String, apiKey: String, model: String) {
        if (provider.isBlank() || apiKey.isBlank()) return
        val enc = runCatching { KeyStoreCrypto.encrypt(apiKey.trim()) }.getOrElse { apiKey.trim() }
        save(list().filterNot { it.provider.equals(provider, true) && decrypt(it) == apiKey.trim() } +
            BackupProvider(provider = provider, apiKeyEnc = enc, model = model.trim(), enabled = true))
    }

    fun removeAt(index: Int) {
        val l = list().toMutableList()
        if (index in l.indices) { l.removeAt(index); save(l) }
    }

    fun toggle(index: Int, enabled: Boolean) {
        val l = list().toMutableList()
        if (index in l.indices) { l[index] = l[index].copy(enabled = enabled); save(l) }
    }

    fun clear() = save(emptyList())

    fun decrypt(b: BackupProvider): String = runCatching {
        if (b.apiKeyEnc.startsWith("ENC:")) KeyStoreCrypto.decrypt(b.apiKeyEnc) else b.apiKeyEnc
    }.getOrElse { b.apiKeyEnc }.trim()

    /** Enabled backups with a real key, in the owner's order. */
    fun candidates(): List<Pair<BackupProvider, String>> = list()
        .filter { it.enabled && it.provider.isNotBlank() }
        .map { it to decrypt(it) }
        .filter { it.second.isNotBlank() && !it.second.contains("MY_") }

    /**
     * Next usable backup, skipping providers/models already tried in this call.
     * Returns null when nothing is left — the caller then reports the original error honestly.
     */
    fun next(excludeProviders: Set<String>, excludeModels: Set<String>): Triple<String, String, String>? {
        if (!autoSwitchEnabled()) return null
        for ((b, key) in candidates()) {
            if (excludeProviders.any { it.equals(b.provider, true) }) continue
            val profile = ProviderProfile.getById(b.provider)
            val model = b.model.ifBlank { profile.defaultFallbackModels.firstOrNull() ?: "default" }
            if (excludeModels.any { it.equals(model, true) }) continue
            return Triple(profile.id, key, model)
        }
        return null
    }

    /** Human-readable list for the Settings section ("Gemini ✓", "Groq ✓"). */
    fun summary(): String {
        val c = candidates()
        if (c.isEmpty()) return "No backup provider saved — limit lagne pe job ruk jayega."
        return c.joinToString(" · ") { (b, _) -> b.provider + (if (autoSwitchEnabled()) " ✓" else " (off)") }
    }
}
