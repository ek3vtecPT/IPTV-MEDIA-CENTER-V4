package pt.iptvplayer.pro

enum class Screen { ADD_SOURCE, HOME, CATALOG, SERIES, SEARCH, SETTINGS, PLAYER }

enum class Mode(val label: String, val catAction: String, val action: String) {
    LIVE("TV ao Vivo", "get_live_categories", "get_live_streams"),
    VOD("Filmes", "get_vod_categories", "get_vod_streams"),
    SERIES("Séries", "get_series_categories", "get_series")
}

enum class SourceType(val label: String) {
    XTREAM("Xtream"),
    M3U_URL("Lista M3U (URL)"),
    M3U_FILE("Ficheiro M3U")
}

/** Categorias fixas do painel lateral. */
const val CAT_ALL = "ALL"
const val CAT_FAV = "FAV"
const val CAT_HIST = "HIST"

data class Source(
    val id: String,
    val name: String,
    val type: SourceType,
    val server: String,          // URL do servidor/lista, ou URI do ficheiro M3U
    val user: String = "",
    val password: String = "",
    val httpConsent: Boolean = false,
    val lastUpdate: Long = 0L
)

data class Category(val id: String, val name: String)

/** Canal, filme ou série. [id] é estável entre atualizações (stream_id / series_id / hash). */
data class Item(
    val id: String,
    val name: String,
    val catId: String,
    val url: String = "",
    val seriesId: String = "",
    val icon: String = ""
)

data class Episode(val season: Int, val title: String, val url: String, val id: String)

data class ModeData(
    val categories: List<Category> = emptyList(),
    val items: List<Item> = emptyList()
)

data class Credentials(
    val base: String,
    val fullUrl: String,
    val user: String,
    val password: String
)

data class LoadResult(
    val data: Map<Mode, ModeData>,
    val expiryMs: Long?,
    val isXtream: Boolean
)

// ---- Opções de gravação ----
data class RecQuality(val label: String, val height: Int, val videoKbps: Int, val audioKbps: Int)
data class RecFormat(val label: String, val ext: String, val mux: String)

val REC_QUALITIES = listOf(
    RecQuality("Original (sem recodificar)", 0, 0, 0),
    RecQuality("Alta - 1080p (~4 Mbps)", 1080, 4000, 192),
    RecQuality("Média - 720p (~2 Mbps)", 720, 2000, 128),
    RecQuality("Baixa - 480p (~1 Mbps)", 480, 1000, 96),
    RecQuality("Muito baixa - 360p (~500 kbps)", 360, 500, 64)
)

val REC_FORMATS = listOf(
    RecFormat("MKV (.mkv)", "mkv", "mkv"),
    RecFormat("MP4 (.mp4)", "mp4", "mp4"),
    RecFormat("TS (.ts)", "ts", "ts")
)
