package com.micka.iptvpro

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class CategoryItem(val id: String, val name: String)
data class StreamItem(
    val name: String,
    val catId: String,
    val url: String = "",
    val seriesId: String = ""
)

enum class AppScreen {
    LOGIN, DASHBOARD, PLAYER, SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkColorScheme = darkColorScheme(
                primary = Color(0xFF0091FF),
                secondary = Color(0xFFFF8800),
                background = Color(0xFF0B0E14),
                surface = Color(0xFF151922),
                surfaceVariant = Color(0xFF1E2430),
                onPrimary = Color.White,
                onBackground = Color(0xFFF0F4F8),
                onSurface = Color(0xFFE1E8F0)
            )
            MaterialTheme(colorScheme = darkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    IPTVApp()
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IPTVApp() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val prefs = remember { context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    var serverInput by remember { mutableStateOf(prefs.getString("srv", "http://my.tele-go.net") ?: "http://my.tele-go.net") }
    var userInput by remember { mutableStateOf(prefs.getString("usr", "") ?: "") }
    var passInput by remember { mutableStateOf(prefs.getString("pwd", "") ?: "") }
    var saveCreds by remember { mutableStateOf(true) }
    var showPassword by remember { mutableStateOf(false) }
    var useExternalVlc by remember { mutableStateOf(prefs.getBoolean("vlc", false)) }

    var baseServer by remember { mutableStateOf("") }
    var activeUser by remember { mutableStateOf("") }
    var activePass by remember { mutableStateOf("") }

    var currentScreen by remember {
        mutableStateOf(if (userInput.isNotBlank()) AppScreen.DASHBOARD else AppScreen.LOGIN)
    }
    var previousScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
    var currentMode by remember { mutableStateOf("live") } // "live", "vod", "series"
    var isFullScreen by remember { mutableStateOf(false) }

    var statusText by remember { mutableStateOf("Pronto.") }
    var isLoadingGlobal by remember { mutableStateOf(false) }
    val isRefreshingSection = remember {
        mutableStateMapOf("live" to false, "vod" to false, "series" to false)
    }

    val categoriesMap = remember {
        mutableStateMapOf(
            "live" to listOf(CategoryItem("ALL", "Todas")),
            "vod" to listOf(CategoryItem("ALL", "Todas")),
            "series" to listOf(CategoryItem("ALL", "Todas"))
        )
    }
    val streamsMap = remember {
        mutableStateMapOf(
            "live" to emptyList<StreamItem>(),
            "vod" to emptyList<StreamItem>(),
            "series" to emptyList<StreamItem>()
        )
    }

    var selectedCatId by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    var activePlaylist by remember { mutableStateOf<List<StreamItem>>(emptyList()) }
    var activeIndex by remember { mutableStateOf(-1) }
    var currentPlayingTitle by remember { mutableStateOf("Nenhum fluxo ativo") }

    var viewingSeriesEpisodes by remember { mutableStateOf(false) }
    var currentSeriesTitle by remember { mutableStateOf("") }
    var episodeList by remember { mutableStateOf<List<StreamItem>>(emptyList()) }

    val exoPlayer = remember {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("IPTVSmartersPlayer")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "IPTVSmartersPlayer")
            .header("Accept", "*/*")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            resp.body?.string()?.trim() ?: ""
        }
    }

    fun normalizeCredentials(rawSrv: String, usr: String, pwd: String): Array<String> {
        var clean = rawSrv.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "http://$clean"
        }
        val uri = Uri.parse(clean)
        val base = "${uri.scheme}://${uri.authority}"
        val finalUsr = if (usr.isBlank()) (uri.getQueryParameter("username") ?: "") else usr.trim()
        val finalPwd = if (pwd.isBlank()) (uri.getQueryParameter("password") ?: "") else pwd.trim()
        return arrayOf(base, clean, finalUsr, finalPwd)
    }

    fun parseM3uContent(content: String, filterSection: String? = null) {
        val liveCats = mutableSetOf<CategoryItem>()
        val vodCats = mutableSetOf<CategoryItem>()
        val seriesCats = mutableSetOf<CategoryItem>()
        val liveStreams = mutableListOf<StreamItem>()
        val vodStreams = mutableListOf<StreamItem>()
        val seriesStreams = mutableListOf<StreamItem>()

        var curName = "Canal"
        var curGrp = "Geral"
        val groupRegex = Regex("""group-title="([^"]+)"""")

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("#EXTINF:")) {
                val match = groupRegex.find(line)
                curGrp = match?.groupValues?.get(1)?.trim().takeUnless { it.isNullOrBlank() } ?: "Geral"
                val commaIdx = line.lastIndexOf(',')
                curName = if (commaIdx != -1 && commaIdx + 1 < line.length) line.substring(commaIdx + 1).trim() else "Canal"
            } else if (line.startsWith("http")) {
                when {
                    line.contains("/movie/") || line.endsWith(".mp4") || line.endsWith(".mkv") -> {
                        vodCats.add(CategoryItem(curGrp, curGrp))
                        vodStreams.add(StreamItem(curName, curGrp, url = line))
                    }
                    line.contains("/series/") -> {
                        seriesCats.add(CategoryItem(curGrp, curGrp))
                        seriesStreams.add(StreamItem(curName, curGrp, url = line))
                    }
                    else -> {
                        liveCats.add(CategoryItem(curGrp, curGrp))
                        liveStreams.add(StreamItem(curName, curGrp, url = line))
                    }
                }
            }
        }
        if (filterSection == null || filterSection == "live") {
            categoriesMap["live"] = listOf(CategoryItem("ALL", "Todas")) + liveCats.toList()
            streamsMap["live"] = liveStreams
        }
        if (filterSection == null || filterSection == "vod") {
            categoriesMap["vod"] = listOf(CategoryItem("ALL", "Todas")) + vodCats.toList()
            streamsMap["vod"] = vodStreams
        }
        if (filterSection == null || filterSection == "series") {
            categoriesMap["series"] = listOf(CategoryItem("ALL", "Todas")) + seriesCats.toList()
            streamsMap["series"] = seriesStreams
        }
    }

    fun loadCategoryStreamsOnDemand(mode: String, catId: String, forceReload: Boolean = false) {
        if (catId == "ALL" || baseServer.isBlank() || activeUser.isBlank()) return
        if (!forceReload && streamsMap[mode].orEmpty().any { it.catId == catId }) return

        val action = when (mode) {
            "live" -> "get_live_streams"
            "vod" -> "get_vod_streams"
            else -> "get_series"
        }

        scope.launch {
            isLoadingGlobal = true
            statusText = "A carregar itens da categoria..."
            try {
                val url = "$baseServer/player_api.php?username=$activeUser&password=$activePass&action=$action&category_id=$catId"
                val body = httpGet(url)
                val arr = JSONArray(body)
                val newItems = mutableListOf<StreamItem>()

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "Item")
                    when (mode) {
                        "live" -> {
                            val sid = obj.optString("stream_id")
                            val sUrl = "$baseServer/live/$activeUser/$activePass/$sid.ts"
                            newItems.add(StreamItem(name, catId, url = sUrl))
                        }
                        "vod" -> {
                            val sid = obj.optString("stream_id")
                            val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
                            val sUrl = "$baseServer/movie/$activeUser/$activePass/$sid.$ext"
                            newItems.add(StreamItem(name, catId, url = sUrl))
                        }
                        "series" -> {
                            val seriesId = obj.optString("series_id")
                            newItems.add(StreamItem(name, catId, seriesId = seriesId))
                        }
                    }
                }
                val existingFiltered = streamsMap[mode].orEmpty().filterNot { it.catId == catId }
                streamsMap[mode] = existingFiltered + newItems
                statusText = "Categoria atualizada (${newItems.size} itens)."
            } catch (e: Exception) {
                statusText = "Erro ao carregar categoria: ${e.message}"
            } finally {
                isLoadingGlobal = false
            }
        }
    }

    // Atualizar apenas uma secção específica (botão de refresh na vinheta do Dashboard)
    fun refreshSingleSection(section: String) {
        val norm = normalizeCredentials(serverInput, userInput, passInput)
        baseServer = norm[0]
        val fullUrl = norm[1]
        activeUser = norm[2]
        activePass = norm[3]

        scope.launch {
            isRefreshingSection[section] = true
            statusText = "A atualizar ${section.uppercase()}..."
            try {
                var updatedViaApi = false
                if (activeUser.isNotBlank() && activePass.isNotBlank()) {
                    val catAction = when (section) {
                        "live" -> "get_live_categories"
                        "vod" -> "get_vod_categories"
                        else -> "get_series_categories"
                    }
                    try {
                        val baseApi = "$baseServer/player_api.php?username=$activeUser&password=$activePass"
                        val arr = JSONArray(httpGet("$baseApi&action=$catAction"))
                        val catList = mutableListOf(CategoryItem("ALL", "Todas"))
                        for (i in 0 until arr.length()) {
                            val c = arr.optJSONObject(i) ?: continue
                            catList.add(
                                CategoryItem(
                                    c.optString("category_id").trim(),
                                    c.optString("category_name", "Categoria").trim()
                                )
                            )
                        }
                        categoriesMap[section] = catList
                        streamsMap[section] = emptyList()
                        val firstCat = catList.firstOrNull { it.id != "ALL" }
                        if (firstCat != null) {
                            loadCategoryStreamsOnDemand(section, firstCat.id, forceReload = true)
                        }
                        updatedViaApi = true
                    } catch (_: Exception) {}
                }

                if (!updatedViaApi) {
                    val m3uUrl = if (activeUser.isNotBlank() && activePass.isNotBlank() && !fullUrl.contains("get.php")) {
                        "$baseServer/get.php?username=$activeUser&password=$activePass&type=m3u_plus&output=ts"
                    } else fullUrl
                    val m3uText = httpGet(m3uUrl)
                    parseM3uContent(m3uText, filterSection = section)
                }
                statusText = "${section.uppercase()} atualizado com sucesso!"
            } catch (e: Exception) {
                statusText = "Erro ao atualizar $section: ${e.message}"
            } finally {
                isRefreshingSection[section] = false
            }
        }
    }

    fun startAccountLogin(navigateToDashboard: Boolean = true) {
        if (saveCreds) {
            prefs.edit()
                .putString("srv", serverInput.trim())
                .putString("usr", userInput.trim())
                .putString("pwd", passInput.trim())
                .apply()
        } else {
            prefs.edit().remove("srv").remove("usr").remove("pwd").apply()
        }

        viewingSeriesEpisodes = false
        selectedCatId = "ALL"

        scope.launch {
            isLoadingGlobal = true
            statusText = "A autenticar e sincronizar fontes..."
            try {
                val norm = normalizeCredentials(serverInput, userInput, passInput)
                baseServer = norm[0]
                val fullUrl = norm[1]
                activeUser = norm[2]
                activePass = norm[3]

                streamsMap["live"] = emptyList()
                streamsMap["vod"] = emptyList()
                streamsMap["series"] = emptyList()

                var loadedApi = false
                if (activeUser.isNotBlank() && activePass.isNotBlank()) {
                    try {
                        val baseApi = "$baseServer/player_api.php?username=$activeUser&password=$activePass"
                        val authBody = httpGet(baseApi)
                        val authJson = JSONObject(authBody)
                        if (authJson.has("user_info")) {
                            suspend fun fetchCats(action: String): List<CategoryItem> {
                                val list = mutableListOf(CategoryItem("ALL", "Todas"))
                                val arr = JSONArray(httpGet("$baseApi&action=$action"))
                                for (i in 0 until arr.length()) {
                                    val c = arr.optJSONObject(i) ?: continue
                                    list.add(
                                        CategoryItem(
                                            c.optString("category_id").trim(),
                                            c.optString("category_name", "Categoria").trim()
                                        )
                                    )
                                }
                                return list
                            }
                            categoriesMap["live"] = fetchCats("get_live_categories")
                            categoriesMap["vod"] = fetchCats("get_vod_categories")
                            categoriesMap["series"] = fetchCats("get_series_categories")
                            loadedApi = true
                        }
                    } catch (_: Exception) {}
                }

                if (!loadedApi) {
                    val m3uUrl = if (activeUser.isNotBlank() && activePass.isNotBlank() && !fullUrl.contains("get.php")) {
                        "$baseServer/get.php?username=$activeUser&password=$activePass&type=m3u_plus&output=ts"
                    } else fullUrl
                    val m3uText = httpGet(m3uUrl)
                    parseM3uContent(m3uText)
                }

                statusText = "Conta sincronizada com sucesso!"
                if (navigateToDashboard) {
                    currentScreen = AppScreen.DASHBOARD
                }
            } catch (e: Exception) {
                statusText = "Erro de ligação: ${e.message}"
            } finally {
                isLoadingGlobal = false
            }
        }
    }

    // Carregar automaticamente se já houver conta guardada ao abrir a app
    LaunchedEffect(Unit) {
        if (userInput.isNotBlank() && baseServer.isBlank()) {
            startAccountLogin(navigateToDashboard = false)
        }
    }

    fun performLogout() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        prefs.edit().clear().apply()
        userInput = ""
        passInput = ""
        baseServer = ""
        activeUser = ""
        activePass = ""
        streamsMap["live"] = emptyList()
        streamsMap["vod"] = emptyList()
        streamsMap["series"] = emptyList()
        categoriesMap["live"] = listOf(CategoryItem("ALL", "Todas"))
        categoriesMap["vod"] = listOf(CategoryItem("ALL", "Todas"))
        categoriesMap["series"] = listOf(CategoryItem("ALL", "Todas"))
        activePlaylist = emptyList()
        activeIndex = -1
        currentPlayingTitle = "Nenhum fluxo ativo"
        isFullScreen = false
        statusText = "Sessão terminada. Insere uma nova conta."
        currentScreen = AppScreen.LOGIN
    }

    fun playStream(item: StreamItem, idx: Int, list: List<StreamItem>) {
        activePlaylist = list
        activeIndex = idx
        currentPlayingTitle = item.name
        statusText = "A reproduzir: ${item.name}"

        if (useExternalVlc) {
            exoPlayer.pause()
            try {
                val vlcIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(item.url), "video/*")
                    setPackage("org.videolan.vlc")
                }
                context.startActivity(vlcIntent)
            } catch (e: Exception) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vlc://${item.url}")))
                } catch (ex: Exception) {
                    statusText = "App VLC não encontrada."
                }
            }
            return
        }

        val mediaItem = MediaItem.fromUri(item.url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun stepChannel(step: Int) {
        if (activePlaylist.isEmpty()) return
        val newIdx = (activeIndex + step + activePlaylist.size) % activePlaylist.size
        playStream(activePlaylist[newIdx], newIdx, activePlaylist)
    }

    fun loadSeriesEpisodes(seriesId: String, seriesName: String) {
        scope.launch {
            isLoadingGlobal = true
            statusText = "A carregar episódios de $seriesName..."
            try {
                val url = "$baseServer/player_api.php?username=$activeUser&password=$activePass&action=get_series_info&series_id=$seriesId"
                val body = httpGet(url)
                val root = JSONObject(body)
                val epsObj = root.optJSONObject("episodes")
                val loadedEps = mutableListOf<StreamItem>()

                if (epsObj != null) {
                    val seasonKeys = epsObj.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }
                    for (sKey in seasonKeys) {
                        val epArr = epsObj.optJSONArray(sKey) ?: continue
                        for (i in 0 until epArr.length()) {
                            val ep = epArr.optJSONObject(i) ?: continue
                            val epId = ep.optString("id")
                            val ext = ep.optString("container_extension", "mp4").ifBlank { "mp4" }
                            val epNum = ep.optString("episode_num", "?")
                            val title = ep.optString("title", "Episódio $epNum")
                            val epUrl = "$baseServer/series/$activeUser/$activePass/$epId.$ext"
                            loadedEps.add(
                                StreamItem(
                                    "T${sKey.padStart(2, '0')}E${epNum.padStart(2, '0')} - $title",
                                    sKey,
                                    url = epUrl
                                )
                            )
                        }
                    }
                }
                episodeList = loadedEps
                currentSeriesTitle = seriesName
                viewingSeriesEpisodes = true
                statusText = "${loadedEps.size} episódios carregados."
            } catch (e: Exception) {
                statusText = "Erro ao carregar episódios: ${e.message}"
            } finally {
                isLoadingGlobal = false
            }
        }
    }

    fun openSectionFromDashboard(mode: String) {
        currentMode = mode
        viewingSeriesEpisodes = false
        isFullScreen = false
        val cats = categoriesMap[mode].orEmpty()
        val firstCat = cats.firstOrNull { it.id != "ALL" }
        if (streamsMap[mode].isNullOrEmpty() && firstCat != null) {
            selectedCatId = firstCat.id
            loadCategoryStreamsOnDemand(mode, firstCat.id)
        } else {
            selectedCatId = "ALL"
        }
        currentScreen = AppScreen.PLAYER
    }

    // Gestão inteligente do botão Back do Android
    BackHandler(enabled = currentScreen != AppScreen.DASHBOARD && currentScreen != AppScreen.LOGIN) {
        when {
            isFullScreen -> isFullScreen = false
            viewingSeriesEpisodes -> viewingSeriesEpisodes = false
            currentScreen == AppScreen.SETTINGS -> currentScreen = previousScreen
            currentScreen == AppScreen.PLAYER -> currentScreen = AppScreen.DASHBOARD
        }
    }

    // --- NAVEGAÇÃO DE ECRÃS ---
    when (currentScreen) {
        AppScreen.LOGIN -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF0091FF), Color(0xFF0051D5)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("IPTV Player Pro", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("Inicia sessão com a tua conta Xtream Codes ou M3U", fontSize = 13.sp, color = Color(0xFF8A94A6))
                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151922))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        OutlinedTextField(
                            value = serverInput,
                            onValueChange = { serverInput = it },
                            label = { Text("Servidor / URL M3U") },
                            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = userInput,
                            onValueChange = { userInput = it },
                            label = { Text("Utilizador") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = passInput,
                            onValueChange = { passInput = it },
                            label = { Text("Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = saveCreds, onCheckedChange = { saveCreds = it })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guardar dados", fontSize = 13.sp)
                            }
                            Button(
                                onClick = { startAccountLogin(navigateToDashboard = true) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091FF))
                            ) {
                                if (isLoadingGlobal) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.Login, contentDescription = null)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Entrar", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(statusText, fontSize = 12.sp, color = Color(0xFF0091FF))
            }
        }

        AppScreen.DASHBOARD -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Cabeçalho do Ecrã Inicial
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF0091FF), Color(0xFF0051D5)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("IPTV Player Pro", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text(
                                text = if (activeUser.isNotBlank()) "Conta: $activeUser" else "Servidor ligado",
                                fontSize = 12.sp,
                                color = Color(0xFF8A94A6)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                previousScreen = AppScreen.DASHBOARD
                                currentScreen = AppScreen.SETTINGS
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Opções", tint = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(statusText, fontSize = 12.sp, color = Color(0xFF0091FF))
                Spacer(modifier = Modifier.height(16.dp))

                // 3 Vinhetas Principais com botão de Refresh no canto inferior direito
                val cardsData = listOf(
                    Triple("live", "TV ao Vivo", Icons.Default.LiveTv),
                    Triple("vod", "Filmes", Icons.Default.Movie),
                    Triple("series", "Séries", Icons.Default.VideoLibrary)
                )

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        cardsData.forEach { (modeKey, title, icon) ->
                            DashboardVignetteCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(210.dp),
                                modeKey = modeKey,
                                title = title,
                                subtitle = "${(categoriesMap[modeKey]?.size ?: 1) - 1} categorias",
                                icon = icon,
                                isRefreshing = isRefreshingSection[modeKey] == true,
                                onOpen = { openSectionFromDashboard(modeKey) },
                                onRefresh = { refreshSingleSection(modeKey) }
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        cardsData.forEach { (modeKey, title, icon) ->
                            DashboardVignetteCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(145.dp),
                                modeKey = modeKey,
                                title = title,
                                subtitle = "${(categoriesMap[modeKey]?.size ?: 1) - 1} categorias disponíveis",
                                icon = icon,
                                isRefreshing = isRefreshingSection[modeKey] == true,
                                onOpen = { openSectionFromDashboard(modeKey) },
                                onRefresh = { refreshSingleSection(modeKey) }
                            )
                        }
                    }
                }
            }
        }

        AppScreen.SETTINGS -> {
            // Menu de Opções totalmente acessível na vertical (com verticalScroll) e na horizontal
            var editSourcesExpanded by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentScreen = previousScreen }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Menu de Opções", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Botão para Editar as Fontes
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151922))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fontes de IPTV (Servidor / M3U)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                Text(serverInput, fontSize = 12.sp, color = Color(0xFF8A94A6), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = { editSourcesExpanded = !editSourcesExpanded },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091FF))
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (editSourcesExpanded) "Fechar" else "Editar Fontes")
                            }
                        }

                        if (editSourcesExpanded) {
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = serverInput,
                                onValueChange = { serverInput = it },
                                label = { Text("URL do Servidor ou Lista M3U") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = userInput,
                                onValueChange = { userInput = it },
                                label = { Text("Utilizador") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = passInput,
                                onValueChange = { passInput = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    editSourcesExpanded = false
                                    startAccountLogin(navigateToDashboard = false)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0091FF))
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guardar e Recarregar Fonte", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Preferências de Reprodução
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151922))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reprodutor VLC Externo", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            Text("Abrir canais e filmes diretamente na aplicação VLC", fontSize = 12.sp, color = Color(0xFF8A94A6))
                        }
                        Switch(
                            checked = useExternalVlc,
                            onCheckedChange = {
                                useExternalVlc = it
                                prefs.edit().putBoolean("vlc", it).apply()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Botão de Logout (Terminar Sessão e Entrar noutra Conta)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1215))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Sessão da Conta", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFFF6B6B))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Termina a sessão atual para limpar os dados guardados e entrar com outra conta IPTV.",
                            fontSize = 12.sp,
                            color = Color(0xFFE0B4B4)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { performLogout() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Logout / Mudar de Conta", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        AppScreen.PLAYER -> {
            val currentCategories = categoriesMap[currentMode].orEmpty()
            val filteredStreams = remember(currentMode, selectedCatId, searchQuery, streamsMap[currentMode]) {
                streamsMap[currentMode].orEmpty().filter { item ->
                    val matchCat = (selectedCatId == "ALL" || item.catId == selectedCatId)
                    val matchQuery = (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true))
                    matchCat && matchQuery
                }
            }

            // Reprodutor de Vídeo com suporte a 1 clique para Full Screen e Swipe em Live TV
            @Composable
            fun VideoPlayerBox(modifier: Modifier = Modifier) {
                var accumulatedDrag by remember { mutableStateOf(0f) }

                Box(
                    modifier = modifier
                        .background(Color.Black)
                        .pointerInput(currentMode, isFullScreen, activePlaylist) {
                            detectTapGestures(
                                onTap = {
                                    // 1 clique na zona de transmissão alterna Full Screen
                                    isFullScreen = !isFullScreen
                                }
                            )
                        }
                        .pointerInput(currentMode, activePlaylist) {
                            if (currentMode == "live") {
                                detectHorizontalDragGestures(
                                    onDragStart = { accumulatedDrag = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedDrag += dragAmount
                                    },
                                    onDragEnd = {
                                        if (accumulatedDrag > 90f) {
                                            // Swipe para a direita -> Canal anterior
                                            stepChannel(-1)
                                        } else if (accumulatedDrag < -90f) {
                                            // Swipe para a esquerda -> Canal seguinte
                                            stepChannel(1)
                                        }
                                        accumulatedDrag = 0f
                                    }
                                )
                            }
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false // Desativado para permitir o clique único de Full Screen e o Swipe limpos
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay discreto no topo do vídeo com nome do canal e botões rápidos
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentPlayingTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentMode == "live") {
                                Text(
                                    text = "Desliza (Swipe) para mudar de canal • Toca para ${if (isFullScreen) "sair do" else "modo"} Ecrã Inteiro",
                                    color = Color(0xFFB0BAC9),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { stepChannel(-1) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (exoPlayer.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { stepChannel(1) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Seguinte", tint = Color.White)
                            }
                            IconButton(onClick = { isFullScreen = !isFullScreen }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Ecrã Inteiro",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            if (isFullScreen) {
                // MODO FULL SCREEN TOTAL
                VideoPlayerBox(modifier = Modifier.fillMaxSize())
            } else if (isLandscape) {
                // MODO HORIZONTAL (SPLIT-VIEW):
                // Esquerda: Submenu vertical de categorias e canais | Centro + Direita unidos: Transmissão do fluxo
                Row(modifier = Modifier.fillMaxSize()) {
                    // Painel Esquerdo (Submenu vertical com categorias e lista de canais/fluxos)
                    Column(
                        modifier = Modifier
                            .weight(0.38f)
                            .fillMaxHeight()
                            .background(Color(0xFF10141C))
                            .padding(10.dp)
                    ) {
                        // Topo do menu esquerdo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { currentScreen = AppScreen.DASHBOARD },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Home, contentDescription = "Início", tint = Color(0xFF0091FF))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (currentMode) {
                                        "live" -> "TV ao Vivo"
                                        "vod" -> "Filmes"
                                        else -> "Séries"
                                    },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    previousScreen = AppScreen.PLAYER
                                    currentScreen = AppScreen.SETTINGS
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Opções", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Pesquisa compacta
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filtrar...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Categorias deslizáveis
                        if (!viewingSeriesEpisodes && currentCategories.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                items(currentCategories) { cat ->
                                    FilterChip(
                                        selected = selectedCatId == cat.id,
                                        onClick = {
                                            selectedCatId = cat.id
                                            loadCategoryStreamsOnDemand(currentMode, cat.id)
                                        },
                                        label = { Text(cat.name, fontSize = 11.sp) },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0091FF),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // Lista vertical de canais / episódios no submenu esquerdo
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (viewingSeriesEpisodes) {
                                item {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewingSeriesEpisodes = false },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261E14))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color(0xFFFF8800))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Voltar ($currentSeriesTitle)", color = Color(0xFFFF8800), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                                itemsIndexed(episodeList) { idx, ep ->
                                    StreamRowCard(
                                        title = ep.name,
                                        isPlaying = currentPlayingTitle == ep.name,
                                        icon = Icons.Default.PlayCircleFilled,
                                        onClick = { playStream(ep, idx, episodeList) }
                                    )
                                }
                            } else {
                                itemsIndexed(filteredStreams.take(500)) { idx, stream ->
                                    val itemIcon = when (currentMode) {
                                        "live" -> Icons.Default.LiveTv
                                        "vod" -> Icons.Default.Movie
                                        else -> Icons.Default.VideoLibrary
                                    }
                                    StreamRowCard(
                                        title = stream.name,
                                        isPlaying = currentPlayingTitle == stream.name,
                                        icon = itemIcon,
                                        onClick = {
                                            if (currentMode == "series" && stream.seriesId.isNotBlank()) {
                                                loadSeriesEpisodes(stream.seriesId, stream.name)
                                            } else {
                                                playStream(stream, idx, filteredStreams)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Frame Central e Direita Unificadas (Exibição do Fluxo)
                    Box(
                        modifier = Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        VideoPlayerBox(modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                // MODO VERTICAL (PORTRAIT)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { currentScreen = AppScreen.DASHBOARD }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Início", tint = Color.White)
                            }
                            Text(
                                text = when (currentMode) {
                                    "live" -> "TV ao Vivo"
                                    "vod" -> "Filmes"
                                    else -> "Séries"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                previousScreen = AppScreen.PLAYER
                                currentScreen = AppScreen.SETTINGS
                            }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Opções", tint = Color.White)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        VideoPlayerBox(modifier = Modifier.fillMaxSize())
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!viewingSeriesEpisodes && currentCategories.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            items(currentCategories) { cat ->
                                FilterChip(
                                    selected = selectedCatId == cat.id,
                                    onClick = {
                                        selectedCatId = cat.id
                                        loadCategoryStreamsOnDemand(currentMode, cat.id)
                                    },
                                    label = { Text(cat.name, fontSize = 12.sp) },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF0091FF),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Pesquisar...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (viewingSeriesEpisodes) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewingSeriesEpisodes = false },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF261E14))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color(0xFFFF8800))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Voltar às Séries ($currentSeriesTitle)", color = Color(0xFFFF8800), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            itemsIndexed(episodeList) { idx, ep ->
                                StreamRowCard(
                                    title = ep.name,
                                    isPlaying = currentPlayingTitle == ep.name,
                                    icon = Icons.Default.PlayCircleFilled,
                                    onClick = { playStream(ep, idx, episodeList) }
                                )
                            }
                        } else {
                            itemsIndexed(filteredStreams.take(500)) { idx, stream ->
                                val itemIcon = when (currentMode) {
                                    "live" -> Icons.Default.LiveTv
                                    "vod" -> Icons.Default.Movie
                                    else -> Icons.Default.VideoLibrary
                                }
                                StreamRowCard(
                                    title = stream.name,
                                    isPlaying = currentPlayingTitle == stream.name,
                                    icon = itemIcon,
                                    onClick = {
                                        if (currentMode == "series" && stream.seriesId.isNotBlank()) {
                                            loadSeriesEpisodes(stream.seriesId, stream.name)
                                        } else {
                                            playStream(stream, idx, filteredStreams)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardVignetteCard(
    modifier: Modifier = Modifier,
    modeKey: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    isRefreshing: Boolean,
    onOpen: () -> Unit,
    onRefresh: () -> Unit
) {
    val gradientColors = when (modeKey) {
        "live" -> listOf(Color(0xFF0062E6), Color(0xFF0A2A5E))
        "vod" -> listOf(Color(0xFFE65100), Color(0xFF4E1D04))
        else -> listOf(Color(0xFF6A1B9A), Color(0xFF280B3A))
    }

    Card(
        modifier = modifier.clickable { onOpen() },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradientColors))
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            }

            // Botão de Refresh individual no canto inferior direito de cada vinheta
            FilledTonalIconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(40.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.35f)
                )
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Atualizar $title",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun StreamRowCard(
    title: String,
    isPlaying: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) Color(0xFF102A45) else Color(0xFF151922)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) Color(0xFF0091FF) else Color(0xFF1E2430)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Equalizer else icon,
                    contentDescription = null,
                    tint = if (isPlaying) Color.White else Color(0xFF0091FF),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) Color(0xFF58B9FF) else Color(0xFFE1E8F0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF5A6578),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
