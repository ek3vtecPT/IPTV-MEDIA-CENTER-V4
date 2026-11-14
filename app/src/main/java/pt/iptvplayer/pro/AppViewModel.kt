package pt.iptvplayer.pro

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ASPECTS: List<Pair<String, MediaPlayer.ScaleType>> = listOf(
    "Ajustar" to MediaPlayer.ScaleType.SURFACE_BEST_FIT,
    "Preencher" to MediaPlayer.ScaleType.SURFACE_FILL,
    "16:9" to MediaPlayer.ScaleType.SURFACE_16_9,
    "4:3" to MediaPlayer.ScaleType.SURFACE_4_3,
    "Original" to MediaPlayer.ScaleType.SURFACE_ORIGINAL
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val prefs = ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE)
    private val settings = ctx.getSharedPreferences("lusa_settings", Context.MODE_PRIVATE)
    private val repo = SourceRepo(ctx)
    private val client = IptvClient()

    // ---------------- Navegação e estado geral ----------------
    var screen by mutableStateOf(Screen.ADD_SOURCE)
    private var returnScreen = Screen.HOME
    private var seriesReturn = Screen.CATALOG

    var sources by mutableStateOf<List<Source>>(emptyList())
    var activeSource by mutableStateOf<Source?>(null)
    var loading by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var online by mutableStateOf(true)
    var expiryMs by mutableStateOf<Long?>(null)
    var historyEnabled by mutableStateOf(settings.getBoolean("history", true))

    var data by mutableStateOf<Map<Mode, ModeData>>(emptyMap())
    private var lookup: Map<String, Item> = emptyMap()
    private var creds: Credentials? = null
    private var isXtream = false
    private var loadJob: Job? = null

    var favorites by mutableStateOf<Set<String>>(emptySet())
    var history by mutableStateOf<List<String>>(emptyList())

    fun keyOf(m: Mode, i: Item) = "${m.name}:${i.id}"

    fun goBack() {
        when (screen) {
            Screen.CATALOG, Screen.SEARCH, Screen.SETTINGS, Screen.ADD_SOURCE -> screen = Screen.HOME
            Screen.SERIES -> screen = seriesReturn
            else -> {}
        }
    }

    fun canGoBack(): Boolean = when (screen) {
        Screen.CATALOG, Screen.SEARCH, Screen.SETTINGS, Screen.SERIES -> true
        Screen.ADD_SOURCE -> activeSource != null
        else -> false
    }

    fun openSettings() { screen = Screen.SETTINGS }
    fun openAddSource() { screen = Screen.ADD_SOURCE }
    fun openSearch() {
        globalQuery = ""
        screen = Screen.SEARCH
    }

    // ---------------- Fontes ----------------
    private fun sanitize(text: String, s: Source?): String {
        var t = text.replace(Regex("(?i)(password|username|token)=[^&\\s]+"), "$1=***")
        if (s != null) {
            if (s.password.length >= 3) t = t.replace(s.password, "***")
            if (s.user.length >= 3) t = t.replace(s.user, "***")
        }
        return t
    }

    private fun describeError(e: Exception, s: Source?): String {
        val raw = e.message.orEmpty()
        val msg = when {
            e is UnknownHostException || e is ConnectException || e is SocketTimeoutException ->
                "Sem ligação ao servidor. Verifica a Internet e o endereço."
            raw.contains("HTTP 401") || raw.contains("HTTP 403") || raw.contains("Credenciais") ->
                "Credenciais recusadas pelo servidor."
            raw.startsWith("HTTP") -> "O servidor respondeu com erro ($raw)."
            raw.isNotEmpty() -> raw
            else -> "Fonte indisponível."
        }
        return sanitize(msg, s)
    }

    private fun fetch(s: Source): LoadResult {
        return when (s.type) {
            SourceType.XTREAM -> {
                val c = client.normalize(s.server, s.user, s.password)
                creds = c
                status = "A autenticar..."
                client.loadXtream(c) { status = it }
                    ?: throw IOException("O servidor não respondeu como uma conta Xtream válida.")
            }
            SourceType.M3U_URL -> {
                val c = client.normalize(s.server, s.user, s.password)
                creds = c
                status = "A descarregar a lista..."
                LoadResult(client.loadM3u(c), null, false)
            }
            SourceType.M3U_FILE -> {
                creds = null
                status = "A ler o ficheiro..."
                val text = ctx.contentResolver.openInputStream(Uri.parse(s.server))
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw IOException("Não foi possível abrir o ficheiro.")
                LoadResult(client.parseM3uText(text), null, false)
            }
        }
    }

    private fun applyResult(s: Source, r: LoadResult, goHome: Boolean) {
        val updated = s.copy(lastUpdate = System.currentTimeMillis())
        repo.upsert(updated)
        repo.setActive(updated.id)
        sources = repo.sources()
        activeSource = updated
        isXtream = r.isXtream
        expiryMs = r.expiryMs
        lookup = buildLookup(r.data)
        data = r.data
        favorites = repo.favorites(updated.id)
        history = repo.history(updated.id)
        online = true
        status = "Atualização concluída."
        if (goHome) screen = Screen.HOME
    }

    private fun buildLookup(d: Map<Mode, ModeData>): Map<String, Item> {
        val map = HashMap<String, Item>()
        for ((m, md) in d) for (it in md.items) map[keyOf(m, it)] = it
        return map
    }

    fun loadSource(s: Source, goHome: Boolean = true) {
        loadJob?.cancel()
        loading = true
        status = "A ligar a ${s.name}..."
        loadJob = viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { fetch(s) }
                applyResult(s, r, goHome)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                online = false
                status = describeError(e, s)
                if (data.isNotEmpty()) status += " Foram mantidos os dados anteriores."
            } finally {
                loading = false
            }
        }
    }

    fun cancelLoad() {
        loadJob?.cancel()
        loading = false
        status = "Operação cancelada."
    }

    fun saveSource(s: Source) {
        try {
            repo.upsert(s)
        } catch (e: Exception) {
            status = "Não foi possível guardar a fonte de forma segura neste dispositivo."
            return
        }
        sources = repo.sources()
        loadSource(s, goHome = true)
    }

    fun refresh() {
        val s = activeSource ?: return
        loadSource(s, goHome = false)
    }

    fun switchSource(s: Source) {
        data = emptyMap()
        lookup = emptyMap()
        activeSource = s
        screen = Screen.HOME
        loadSource(s, goHome = true)
    }

    fun deleteSource(id: String) {
        repo.delete(id)
        sources = repo.sources()
        if (activeSource?.id == id) {
            data = emptyMap()
            lookup = emptyMap()
            activeSource = null
            favorites = emptySet()
            history = emptyList()
            val next = sources.firstOrNull()
            if (next != null) switchSource(next) else screen = Screen.ADD_SOURCE
        }
    }

    fun deleteAllData() {
        loadJob?.cancel()
        repo.deleteAll()
        prefs.edit().clear().apply()
        settings.edit().clear().apply()
        sources = emptyList()
        activeSource = null
        data = emptyMap()
        lookup = emptyMap()
        favorites = emptySet()
        history = emptyList()
        recFolderUri = null
        screen = Screen.ADD_SOURCE
        status = "Todos os dados foram eliminados."
    }

    fun setHistoryEnabled(on: Boolean) {
        historyEnabled = on
        settings.edit().putBoolean("history", on).apply()
    }

    fun clearHistory() {
        val s = activeSource ?: return
        repo.clearHistory(s.id)
        history = emptyList()
    }

    // ---------------- Catálogo ----------------
    var catalogMode by mutableStateOf(Mode.LIVE)
    var categoryId by mutableStateOf(CAT_ALL)
    var query by mutableStateOf("")
    var sort by mutableIntStateOf(0)           // 0 original, 1 A-Z, 2 Z-A
    var gridView by mutableStateOf(true)
    var globalQuery by mutableStateOf("")

    fun openCatalog(m: Mode, cat: String = CAT_ALL) {
        catalogMode = m
        categoryId = cat
        query = ""
        screen = Screen.CATALOG
    }

    fun changeCatalogMode(m: Mode) {
        catalogMode = m
        categoryId = CAT_ALL
        query = ""
    }

    fun visibleItems(): List<Item> {
        val m = catalogMode
        val md = data[m] ?: return emptyList()
        val q = query.trim()
        var list: List<Item> = when (categoryId) {
            CAT_ALL -> md.items
            CAT_FAV -> md.items.filter { favorites.contains(keyOf(m, it)) }
            CAT_HIST -> history.mapNotNull { k -> if (k.startsWith(m.name + ":")) lookup[k] else null }
            else -> md.items.filter { it.catId == categoryId }
        }
        if (q.isNotEmpty()) list = list.filter { it.name.contains(q, ignoreCase = true) }
        list = when (sort) {
            1 -> list.sortedBy { it.name.lowercase() }
            2 -> list.sortedByDescending { it.name.lowercase() }
            else -> list
        }
        return list
    }

    fun globalResults(): List<Pair<Mode, Item>> {
        val q = globalQuery.trim()
        if (q.length < 2) return emptyList()
        val out = ArrayList<Pair<Mode, Item>>()
        for (m in Mode.values()) {
            val items = data[m]?.items ?: continue
            var n = 0
            for (it in items) {
                if (it.name.contains(q, ignoreCase = true)) {
                    out.add(m to it)
                    n++
                    if (n >= 30) break
                }
            }
        }
        return out
    }

    fun selectCategory(id: String) {
        categoryId = id
        val c = creds
        if (!isXtream || c == null || id == CAT_ALL || id == CAT_FAV || id == CAT_HIST) return
        val m = catalogMode
        if (data[m]?.items?.any { it.catId == id } == true) return
        viewModelScope.launch {
            busy = true
            try {
                val items = withContext(Dispatchers.IO) { client.fetchCategory(m, c, id) }
                val md = data[m] ?: ModeData()
                val merged = data + (m to md.copy(items = md.items + items))
                lookup = buildLookup(merged)
                data = merged
            } catch (e: Exception) {
                status = "Não foi possível carregar a categoria. " + describeError(e, activeSource)
            } finally {
                busy = false
            }
        }
    }

    fun isFav(m: Mode, i: Item) = favorites.contains(keyOf(m, i))

    fun toggleFav(m: Mode, i: Item) {
        val s = activeSource ?: return
        val k = keyOf(m, i)
        favorites = if (favorites.contains(k)) favorites - k else favorites + k
        repo.saveFavorites(s.id, favorites)
    }

    private fun recordHistory(m: Mode, i: Item) {
        val s = activeSource ?: return
        if (!historyEnabled) return
        val k = keyOf(m, i)
        history = (listOf(k) + history.filter { it != k }).take(60)
        repo.saveHistory(s.id, history)
    }

    // ---------------- Séries ----------------
    var episodes by mutableStateOf<List<Episode>>(emptyList())
    var seriesItem by mutableStateOf<Item?>(null)
    var season by mutableIntStateOf(0)

    fun openSeries(item: Item) {
        val c = creds ?: return
        seriesReturn = if (screen == Screen.SEARCH) Screen.SEARCH else Screen.CATALOG
        seriesItem = item
        episodes = emptyList()
        screen = Screen.SERIES
        recordHistory(Mode.SERIES, item)
        busy = true
        viewModelScope.launch {
            try {
                val eps = withContext(Dispatchers.IO) { client.loadEpisodes(c, item.seriesId) }
                episodes = eps
                season = eps.firstOrNull()?.season ?: 0
                if (eps.isEmpty()) status = "Esta série não tem episódios disponíveis."
            } catch (e: Exception) {
                status = "Não foi possível carregar os episódios. " + describeError(e, activeSource)
            } finally {
                busy = false
            }
        }
    }

    // ---------------- Leitor (LibVLC) ----------------
    var playToken by mutableIntStateOf(0)
    var currentName by mutableStateOf("")
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    var progress by mutableFloatStateOf(0f)
    var lengthMs by mutableLongStateOf(0L)
    var timeMs by mutableLongStateOf(0L)
    var isLive by mutableStateOf(true)

    private var playItems: List<Item> = emptyList()
    private var playIndex = 0
    private var playMode: Mode? = null
    private var retry = 0

    private val vlcLazy = lazy {
        LibVLC(ctx, arrayListOf("--http-user-agent=$USER_AGENT", "--network-caching=1500"))
    }
    private val libVLC: LibVLC get() = vlcLazy.value

    private val playerLazy = lazy {
        MediaPlayer(libVLC).also { p -> p.setEventListener { e -> onPlayerEvent(e) } }
    }
    private val player: MediaPlayer get() = playerLazy.value

    fun attach(layout: VLCVideoLayout) {
        player.attachViews(layout, null, false, false)
    }

    fun detach() {
        if (playerLazy.isInitialized()) player.detachViews()
    }

    fun play(items: List<Item>, index: Int, mode: Mode? = null) {
        if (items.isEmpty()) return
        playItems = items
        playIndex = index.coerceIn(0, items.size - 1)
        playMode = mode
        returnScreen = screen
        screen = Screen.PLAYER
        playToken++
    }

    fun currentItemName(): String = playItems.getOrNull(playIndex)?.name.orEmpty()
    fun hasChannelList(): Boolean = playItems.size > 1

    fun startCurrent() {
        val item = playItems.getOrNull(playIndex) ?: return
        retry = 0
        playMode?.let { recordHistory(it, item) }
        isLive = playMode == Mode.LIVE
        startPlayback(item, null)
    }

    private fun startPlayback(item: Item, sout: String?) {
        currentName = item.name
        errorMsg = null
        val media = Media(libVLC, Uri.parse(item.url))
        if (sout != null) media.addOption(":sout=$sout")
        player.setMedia(media)
        media.release()
        player.play()
    }

    fun step(delta: Int) {
        if (playItems.isEmpty()) return
        finishRecording(restart = false)
        val n = playItems.size
        playIndex = ((playIndex + delta) % n + n) % n
        playToken++
    }

    fun leavePlayer() {
        finishRecording(restart = false)
        if (playerLazy.isInitialized()) player.stop()
        playing = false
        buffering = false
        screen = if (returnScreen == Screen.PLAYER) Screen.HOME else returnScreen
    }

    fun togglePause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seek(fraction: Float) {
        player.setPosition(fraction)
    }

    fun tick() {
        if (!playerLazy.isInitialized()) return
        val p = player
        playing = p.isPlaying
        val len = p.getLength()
        lengthMs = len
        timeMs = p.getTime()
        progress = if (len > 0) p.getPosition() else 0f
        if (recording) {
            val secs = (SystemClock.elapsedRealtime() - recStartMs) / 1000
            val mb = (recFile?.length() ?: 0L) / (1024.0 * 1024.0)
            recLabel = String.format(
                Locale.US, "%02d:%02d:%02d · %.1f MB",
                secs / 3600, (secs % 3600) / 60, secs % 60, mb
            )
        }
    }

    private fun onPlayerEvent(e: MediaPlayer.Event) {
        when (e.type) {
            MediaPlayer.Event.EncounteredError -> {
                buffering = false
                if (recording) {
                    errorMsg = "Fluxo interrompido."
                    finishRecording(restart = false, auto = true)
                } else if (retry < 3 && playItems.isNotEmpty()) {
                    retry++
                    val attempt = retry
                    val token = playToken
                    errorMsg = "A tentar religar ($attempt/3)..."
                    viewModelScope.launch {
                        delay(2000L * attempt)
                        if (screen == Screen.PLAYER && token == playToken && !recording) {
                            playItems.getOrNull(playIndex)?.let { startPlayback(it, null) }
                        }
                    }
                } else {
                    errorMsg = "Fluxo indisponível ou formato não suportado neste dispositivo."
                }
            }
            MediaPlayer.Event.EndReached -> {
                if (recording) finishRecording(restart = false, auto = true)
            }
            MediaPlayer.Event.Buffering -> buffering = e.buffering < 100f
            MediaPlayer.Event.Playing -> {
                buffering = false
                errorMsg = null
                retry = 0
            }
        }
    }

    // ---- Faixas, legendas, proporção ----
    fun audioTracks(): List<Pair<Int, String>> =
        player.getAudioTracks()?.map { it.id to it.name } ?: emptyList()

    fun currentAudio(): Int = player.getAudioTrack()
    fun setAudio(id: Int) {
        player.setAudioTrack(id)
    }

    fun subTracks(): List<Pair<Int, String>> =
        player.getSpuTracks()?.map { it.id to it.name } ?: emptyList()

    fun currentSub(): Int = player.getSpuTrack()
    fun setSub(id: Int) {
        player.setSpuTrack(id)
    }

    var aspectIndex by mutableIntStateOf(0)
    fun setAspect(i: Int) {
        aspectIndex = i
        player.setVideoScale(ASPECTS[i].second)
    }

    // ---------------- Gravação (uma só ligação: ecrã + ficheiro) ----------------
    var recQuality by mutableIntStateOf(prefs.getInt("recQuality", 0).coerceIn(0, REC_QUALITIES.lastIndex))
    var recFormat by mutableIntStateOf(prefs.getInt("recFormat", 0).coerceIn(0, REC_FORMATS.lastIndex))
    var recFolderUri by mutableStateOf<String?>(prefs.getString("recFolder", null))
    var recording by mutableStateOf(false)
    var recLabel by mutableStateOf("")
    var recStatus by mutableStateOf("")
    private var recFile: File? = null
    private var recStartMs = 0L

    fun chooseQuality(i: Int) {
        recQuality = i
        prefs.edit().putInt("recQuality", i).apply()
    }

    fun chooseFormat(i: Int) {
        recFormat = i
        prefs.edit().putInt("recFormat", i).apply()
    }

    fun setRecFolder(uri: Uri) {
        recFolderUri = uri.toString()
        prefs.edit().putString("recFolder", uri.toString()).apply()
    }

    fun recFolderLabel(): String {
        val u = recFolderUri ?: return "Pasta da app (Android/data/…/files/Movies)"
        return Uri.parse(u).lastPathSegment ?: u
    }

    private fun tempDir(): File {
        val d = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir
        d.mkdirs()
        return d
    }

    private fun buildSout(path: String, q: RecQuality, mux: String): String {
        val std = "std{access=file,mux=$mux,dst='$path'}"
        val branch = if (q.height == 0) {
            if (mux == "mp4") "transcode{acodec=mp4a,ab=128,channels=2}:$std" else std
        } else {
            "transcode{vcodec=h264,venc=x264{preset=veryfast},vb=${q.videoKbps},height=${q.height}," +
                "acodec=mp4a,ab=${q.audioKbps},channels=2}:$std"
        }
        return "#duplicate{dst=display,dst=\"$branch\"}"
    }

    fun startRecording() {
        if (recording) return
        val item = playItems.getOrNull(playIndex) ?: return
        val q = REC_QUALITIES[recQuality]
        val f = REC_FORMATS[recFormat]
        val safe = item.name
            .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), "_")
            .trim(' ', '.', '_')
            .take(80)
            .ifEmpty { "gravacao" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(tempDir(), "${safe}_$stamp.${f.ext}")
        recFile = file
        recStartMs = SystemClock.elapsedRealtime()
        recStatus = ""
        recording = true
        player.stop()
        startPlayback(item, buildSout(file.absolutePath, q, f.mux))
    }

    fun stopRecording() {
        finishRecording(restart = true)
    }

    private fun finishRecording(restart: Boolean, auto: Boolean = false) {
        if (!recording) return
        recording = false
        recLabel = ""
        val file = recFile
        val item = playItems.getOrNull(playIndex)
        if (playerLazy.isInitialized()) player.stop()
        recStatus = if (auto) "A emissão terminou. A guardar a gravação..." else "A guardar a gravação..."
        viewModelScope.launch {
            delay(600)
            val msg = if (file == null) "" else withContext(Dispatchers.IO) { saveRecording(file) }
            recStatus = msg
        }
        if (restart && item != null) {
            viewModelScope.launch {
                delay(500)
                if (!recording && screen == Screen.PLAYER) startPlayback(item, null)
            }
        }
    }

    private fun saveRecording(file: File): String {
        if (!file.exists() || file.length() == 0L) return "A gravação ficou vazia (nada foi guardado)."
        val tree = recFolderUri?.let { Uri.parse(it) }
            ?: return "Gravação guardada em: ${file.absolutePath}"
        return try {
            val resolver = ctx.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val mime = when (file.extension) {
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                else -> "video/mp2t"
            }
            val dest = DocumentsContract.createDocument(resolver, parent, mime, file.name)
                ?: throw IOException("não foi possível criar o ficheiro na pasta escolhida")
            resolver.openOutputStream(dest)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            file.delete()
            "Gravação guardada na pasta escolhida: ${file.name}"
        } catch (e: Exception) {
            "Gravação guardada em ${file.absolutePath} (não foi possível copiar para a pasta escolhida)"
        }
    }

    override fun onCleared() {
        if (playerLazy.isInitialized()) {
            try {
                player.stop()
                player.release()
            } catch (e: Exception) {
                // ignorado ao fechar
            }
        }
        if (vlcLazy.isInitialized()) {
            try {
                libVLC.release()
            } catch (e: Exception) {
                // ignorado ao fechar
            }
        }
        super.onCleared()
    }

    // ---------------- Arranque ----------------
    init {
        sources = repo.sources()
        val active = sources.firstOrNull { it.id == repo.activeId() } ?: sources.firstOrNull()
        if (active != null) {
            activeSource = active
            screen = Screen.HOME
            loadSource(active, goHome = true)
        } else {
            screen = Screen.ADD_SOURCE
        }
    }
}
