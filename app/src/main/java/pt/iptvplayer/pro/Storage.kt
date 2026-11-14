package pt.iptvplayer.pro

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra segredos (URL do servidor e palavra-passe) com uma chave guardada no Android Keystore. */
object SecretStore {
    private const val ALIAS = "lusa_iptv_secret_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + data, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val raw = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val data = raw.copyOfRange(12, raw.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}

/** Fontes, favoritos e histórico (por fonte), guardados localmente. */
class SourceRepo(context: Context) {
    private val prefs = context.getSharedPreferences("lusa_sources", Context.MODE_PRIVATE)

    private fun fromJson(o: JSONObject): Source {
        val type = try {
            SourceType.valueOf(o.optString("type"))
        } catch (e: Exception) {
            SourceType.XTREAM
        }
        return Source(
            id = o.optString("id"),
            name = o.optString("name"),
            type = type,
            server = SecretStore.decrypt(o.optString("server")),
            user = o.optString("user"),
            password = SecretStore.decrypt(o.optString("password")),
            httpConsent = o.optBoolean("http", false),
            lastUpdate = o.optLong("updated", 0L)
        )
    }

    private fun toJson(s: Source): JSONObject = JSONObject()
        .put("id", s.id)
        .put("name", s.name)
        .put("type", s.type.name)
        .put("server", SecretStore.encrypt(s.server))
        .put("user", s.user)
        .put("password", SecretStore.encrypt(s.password))
        .put("http", s.httpConsent)
        .put("updated", s.lastUpdate)

    fun sources(): List<Source> {
        val arr = try {
            JSONArray(prefs.getString("sources", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Source>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(fromJson(o))
        }
        return out
    }

    private fun saveAll(list: List<Source>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("sources", arr.toString()).apply()
    }

    fun upsert(s: Source) {
        val list = sources().toMutableList()
        val i = list.indexOfFirst { it.id == s.id }
        if (i >= 0) list[i] = s else list.add(s)
        saveAll(list)
    }

    fun delete(id: String) {
        saveAll(sources().filter { it.id != id })
        val e = prefs.edit().remove("fav_$id").remove("hist_$id")
        if (activeId() == id) e.remove("active")
        e.apply()
    }

    fun activeId(): String? = prefs.getString("active", null)
    fun setActive(id: String) {
        prefs.edit().putString("active", id).apply()
    }

    fun favorites(id: String): Set<String> =
        HashSet(prefs.getStringSet("fav_$id", emptySet()) ?: emptySet())

    fun saveFavorites(id: String, set: Set<String>) {
        prefs.edit().putStringSet("fav_$id", HashSet(set)).apply()
    }

    fun history(id: String): List<String> =
        prefs.getString("hist_$id", "").orEmpty().split("\n").filter { it.isNotEmpty() }

    fun saveHistory(id: String, list: List<String>) {
        prefs.edit().putString("hist_$id", list.joinToString("\n")).apply()
    }

    fun clearHistory(id: String) {
        prefs.edit().remove("hist_$id").apply()
    }

    fun deleteAll() {
        prefs.edit().clear().apply()
    }
}
