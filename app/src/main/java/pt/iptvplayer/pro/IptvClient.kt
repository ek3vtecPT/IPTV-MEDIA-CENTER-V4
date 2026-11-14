package pt.iptvplayer.pro

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

const val USER_AGENT = "IPTVSmartersPlayer"

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

/** Carregamento Xtream Codes / M3U. Não regista URLs nem credenciais. */
class IptvClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun get(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    private fun getJson(url: String): Any? {
        val text = get(url).trim()
        if (text.isEmpty()) return null
        return JSONTokener(text).nextValue()
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun normalize(rawServer: String, user: String, password: String): Credentials {
        var raw = rawServer.trim()
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) raw = "http://$raw"
        val uri = Uri.parse(raw)
        val base = "${uri.scheme}://${uri.authority}"
        val u = user.ifBlank { uri.getQueryParameter("username").orEmpty() }
        val p = password.ifBlank { uri.getQueryParameter("password").orEmpty() }
        return Credentials(base, raw, u, p)
    }

    private fun apiUrl(c: Credentials) =
        "${c.base}/player_api.php?username=${enc(c.user)}&password=${enc(c.password)}"

    // ---------------- Xtream ----------------

    /** Devolve null se o servidor não responder como uma conta Xtream. */
    fun loadXtream(c: Credentials, status: (String) -> Unit): LoadResult? {
        val auth = try {
            getJson(apiUrl(c))
        } catch (e: IOException) {
            if (e.message?.startsWith("HTTP 4") == true) throw e
            null
        }
        if (auth !is JSONObject || !auth.has("user_info")) return null
        val info = auth.optJSONObject("user_info")
        if (info != null && info.has("auth") && info.optInt("auth", 1) == 0) {
            throw IOException("Credenciais recusadas pelo servidor.")
        }
        val exp = info?.str("exp_date")?.toLongOrNull()
        val expiryMs = if (exp != null && exp > 0L) exp * 1000L else null

        val result = LinkedHashMap<Mode, ModeData>()
        for (mode in Mode.values()) {
            status("A importar ${mode.label}...")
            val cats = try {
                parseCategories(getJson("${apiUrl(c)}&action=${mode.catAction}"))
            } catch (e: Exception) {
                emptyList()
            }
            val items = try {
                parseItems(mode, c, getJson("${apiUrl(c)}&action=${mode.action}"), null)
            } catch (e: Exception) {
                emptyList()
            }
            result[mode] = ModeData(cats, items)
        }
        return LoadResult(result, expiryMs, true)
    }

    fun fetchCategory(mode: Mode, c: Credentials, catId: String): List<Item> {
        val json = getJson("${apiUrl(c)}&action=${mode.action}&category_id=${enc(catId)}")
        return parseItems(mode, c, json, catId)
    }

    private fun parseCategories(json: Any?): List<Category> {
        val out = ArrayList<Category>()
        if (json is JSONArray) {
            for (i in 0 until json.length()) {
                val o = json.optJSONObject(i) ?: continue
                out.add(Category(o.str("category_id").trim(), o.str("category_name").trim().ifEmpty { "Sem nome" }))
            }
        }
        return out
    }

    private fun parseItems(mode: Mode, c: Credentials, json: Any?, forcedCat: String?): List<Item> {
        val out = ArrayList<Item>()
        if (json !is JSONArray) return out
        for (i in 0 until json.length()) {
            val o = json.optJSONObject(i) ?: continue
            val cid = forcedCat ?: o.str("category_id").trim()
            when (mode) {
                Mode.LIVE -> {
                    val sid = o.str("stream_id")
                    out.add(
                        Item(
                            sid, o.str("name").ifEmpty { "Canal" }, cid,
                            "${c.base}/live/${c.user}/${c.password}/$sid.ts",
                            icon = o.str("stream_icon")
                        )
                    )
                }
                Mode.VOD -> {
                    val sid = o.str("stream_id")
                    val ext = o.str("container_extension").ifEmpty { "mp4" }
                    out.add(
                        Item(
                            sid, o.str("name").ifEmpty { "Filme" }, cid,
                            "${c.base}/movie/${c.user}/${c.password}/$sid.$ext",
                            icon = o.str("stream_icon")
                        )
                    )
                }
                Mode.SERIES -> {
                    val sid = o.str("series_id")
                    out.add(
                        Item(
                            sid, o.str("name").ifEmpty { "Série" }, cid, "", sid,
                            o.str("cover").ifEmpty { o.str("stream_icon") }
                        )
                    )
                }
            }
        }
        return out
    }

    fun loadEpisodes(c: Credentials, seriesId: String): List<Episode> {
        val json = getJson("${apiUrl(c)}&action=get_series_info&series_id=${enc(seriesId)}") as? JSONObject
            ?: return emptyList()
        val eps = json.opt("episodes")
        val out = ArrayList<Episode>()

        fun addSeason(season: String, arr: JSONArray) {
            val sn = season.toIntOrNull() ?: 0
            for (i in 0 until arr.length()) {
                val ep = arr.optJSONObject(i) ?: continue
                val id = ep.str("id")
                val ext = ep.str("container_extension").ifEmpty { "mp4" }
                val num = ep.str("episode_num").ifEmpty { "?" }
                val title = ep.str("title").ifEmpty { "Episódio $num" }
                out.add(
                    Episode(
                        sn, "E${num.padStart(2, '0')} - $title",
                        "${c.base}/series/${c.user}/${c.password}/$id.$ext", id
                    )
                )
            }
        }

        if (eps is JSONObject) {
            val keys = ArrayList<String>()
            val it = eps.keys()
            while (it.hasNext()) keys.add(it.next())
            keys.sortBy { k -> k.toIntOrNull() ?: 0 }
            for (k in keys) {
                val arr = eps.optJSONArray(k) ?: continue
                addSeason(k, arr)
            }
        } else if (eps is JSONArray) {
            for (i in 0 until eps.length()) {
                val arr = eps.optJSONArray(i) ?: continue
                addSeason((i + 1).toString(), arr)
            }
        }
        return out
    }

    // ---------------- M3U ----------------

    fun loadM3u(c: Credentials): Map<Mode, ModeData> {
        val url = if (c.user.isNotEmpty() && c.password.isNotEmpty() &&
            !c.fullUrl.contains("get.php") && !c.fullUrl.endsWith(".m3u") && !c.fullUrl.endsWith(".m3u8")
        ) {
            "${c.base}/get.php?username=${enc(c.user)}&password=${enc(c.password)}&type=m3u_plus&output=ts"
        } else {
            c.fullUrl
        }
        return parseM3uText(get(url))
    }

    fun parseM3uText(content: String): Map<Mode, ModeData> {
        if (!content.contains("#EXTM3U") && !content.contains("#EXTINF")) {
            val preview = content.take(120).trim().ifEmpty { "resposta vazia" }
            throw IOException("Lista M3U inválida: $preview")
        }
        val groupRe = Regex("group-title=\"([^\"]*)\"")
        val logoRe = Regex("tvg-logo=\"([^\"]*)\"")
        val cats = Mode.values().associateWith { LinkedHashMap<String, Category>() }
        val items = Mode.values().associateWith { ArrayList<Item>() }
        var name = "Canal"
        var group = "Geral"
        var logo = ""
        for (raw in content.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF:")) {
                group = groupRe.find(line)?.groupValues?.get(1)?.trim().orEmpty().ifEmpty { "Geral" }
                logo = logoRe.find(line)?.groupValues?.get(1)?.trim().orEmpty()
                val idx = line.lastIndexOf(',')
                name = if (idx >= 0) line.substring(idx + 1).trim().ifEmpty { "Canal" } else "Canal"
            } else if (line.startsWith("http")) {
                val mode = when {
                    line.contains("/movie/") || line.endsWith(".mp4") ||
                        line.endsWith(".mkv") || line.endsWith(".avi") -> Mode.VOD
                    line.contains("/series/") -> Mode.SERIES
                    else -> Mode.LIVE
                }
                cats.getValue(mode)[group] = Category(group, group)
                items.getValue(mode).add(Item("m" + line.hashCode().toString(), name, group, line, "", logo))
            }
        }
        return Mode.values().associateWith { m ->
            ModeData(cats.getValue(m).values.toList(), items.getValue(m))
        }
    }
}
