@file:OptIn(UnstableApi::class)

package pt.iptvplayer.pro

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.security.KeyStore
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/*
 * IPTV Player Pro - ficheiro único (MainActivity.kt)
 *
 *  1. Tema e paleta
 *  2. Ícones vetoriais
 *  3. Modelos de dados
 *  4. Armazenamento seguro (Android Keystore) e sessão
 *  5. Cliente Xtream Codes / M3U (OkHttp, User-Agent "IPTVSmartersPlayer")
 *  6. ViewModel (dados, sessão, ExoPlayer)
 *  7. Componentes e ecrãs (Login, Dashboard, Navegação + leitor, Opções)
 *  8. MainActivity
 */

// =====================================================================
//  1. TEMA E PALETA
// =====================================================================

const val USER_AGENT = "IPTVSmartersPlayer"
const val CAT_ALL = "ALL"

val Bg = Color(0xFF05091A)
val Surf = Color(0xFF0D1428)
val SurfHigh = Color(0xFF172038)
val Accent = Color(0xFF00A8B5)
val Selected = Color(0xFF006F80)
val TextSec = Color(0xFFABB3C4)
val ErrorC = Color(0xFFEF5350)
val OkC = Color(0xFF27D09B)
val GradLive = listOf(Color(0xFF27D09B), Color(0xFF3458D5))
val GradMovies = listOf(Color(0xFFE92D61), Color(0xFFFFA52B))
val GradSeries = listOf(Color(0xFFA33BC7), Color(0xFF43AAE1))
val ButtonBrush = Brush.horizontalGradient(listOf(Color(0xFF006F80), Color(0xFF008A9A)))

private val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Selected,
    onSecondary = Color.White,
    background = Bg,
    onBackground = Color.White,
    surface = Surf,
    onSurface = Color.White,
    surfaceVariant = SurfHigh,
    onSurfaceVariant = TextSec,
    error = ErrorC,
    outline = Color(0xFF2B3654)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppTypography, shapes = AppShapes, content = content)
}

// ---------------------------------------------------------------------
//  ÍCONES VETORIAIS PRÓPRIOS (sem dependências extra)
// ---------------------------------------------------------------------

private fun vec(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black))
        .build()

object AppIcons {
    val Tv: ImageVector by lazy { vec("Tv", "M21,3H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 1.99,-0.9 1.99,-2L23,5c0,-1.1 -0.9,-2 -2,-2zM21,17H3V5h18v12z") }
    val Movie: ImageVector by lazy { vec("Movie", "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4h-4z") }
    val Series: ImageVector by lazy { vec("Series", "M4,6H2v14c0,1.1 0.9,2 2,2h14v-2H4V6zm16,-4H8c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zm-8,12.5v-9l6,4.5 -6,4.5z") }
    val Search: ImageVector by lazy { vec("Search", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z") }
    val Close: ImageVector by lazy { vec("Close", "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z") }
    val Back: ImageVector by lazy { vec("Back", "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z") }
    val Logout: ImageVector by lazy { vec("Logout", "M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5zM4,5h8V3H4c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h8v-2H4V5z") }
    val Play: ImageVector by lazy { vec("Play", "M8,5v14l11,-7z") }
    val Pause: ImageVector by lazy { vec("Pause", "M6,19h4V5H6v14zm8,-14v14h4V5h-4z") }
    val SkipNext: ImageVector by lazy { vec("SkipNext", "M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z") }
    val SkipPrevious: ImageVector by lazy { vec("SkipPrevious", "M6,6h2v12H6zm3.5,6l8.5,6V6z") }
    val Subtitles: ImageVector by lazy { vec("Subtitles", "M20,4H4c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6c0,-1.1 -0.9,-2 -2,-2zM4,12h4v2H4v-2zm10,6H4v-2h10v2zm6,0h-4v-2h4v2zm0,-4H10v-2h10v2z") }
    val Audio: ImageVector by lazy { vec("Audio", "M12,3v9.28c-0.47,-0.17 -0.97,-0.28 -1.5,-0.28C8.01,12 6,14.01 6,16.5S8.01,21 10.5,21c2.31,0 4.2,-1.75 4.45,-4H15V6h4V3h-7z") }
    val Aspect: ImageVector by lazy { vec("Aspect", "M19,12h-2v3h-3v2h5v-5zM7,9h3V7H5v5h2V9zm14,-6H3c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,1.98 2,1.98h18c1.1,0 2,-0.88 2,-1.98V5c0,-1.1 -0.9,-2 -2,-2zm0,16.01H3V4.98h18v14.03z") }
    val Link: ImageVector by lazy { vec("Link", "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zm9,-6h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z") }
    val Person: ImageVector by lazy { vec("Person", "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z") }
    val Lock: ImageVector by lazy { vec("Lock", "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z") }
    val Check: ImageVector by lazy { vec("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41,-1.41z") }
    val Settings: ImageVector by lazy { vec("Settings", "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z") }
    val Refresh: ImageVector by lazy { vec("Refresh", "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z") }
    val Edit: ImageVector by lazy { vec("Edit", "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25zM20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z") }
    val Fullscreen: ImageVector by lazy { vec("Fullscreen", "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z") }
    val FullscreenExit: ImageVector by lazy { vec("FullscreenExit", "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,8V5h-2v5h5V8h-3z") }
    val Visibility: ImageVector by lazy { vec("Visibility", "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5 -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z") }
    val VisibilityOff: ImageVector by lazy { vec("VisibilityOff", "M12,7c2.76,0 5,2.24 5,5 0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92c1.51,-1.26 2.7,-2.89 3.43,-4.75 -1.73,-4.39 -6,-7.5 -11,-7.5 -1.4,0 -2.74,0.25 -3.98,0.7l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28 0.46,0.46C3.08,8.3 1.78,10.02 1,12c1.73,4.39 6,7.5 11,7.5 1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22 21,20.73 3.27,3 2,4.27zM7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53 -2.76,0 -5,-2.24 -5,-5 0,-0.79 0.2,-1.53 0.53,-2.2zM11.84,9.02l3.15,3.15 0.02,-0.16c0,-1.66 -1.34,-3 -3,-3l-0.17,0.01z") }
    val PlayCircle: ImageVector by lazy { vec("PlayCircle", "M10,16.5l6,-4.5 -6,-4.5v9zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z") }
}
// =====================================================================
//  3. MODELOS DE DADOS
// =====================================================================

enum class Screen { LOGIN, DASHBOARD, BROWSE, SETTINGS }

enum class Mode(val label: String, val catAction: String, val action: String) {
    LIVE("TV ao Vivo", "get_live_categories", "get_live_streams"),
    VOD("Filmes", "get_vod_categories", "get_vod_streams"),
    SERIES("Séries", "get_series_categories", "get_series")
}

data class Category(val id: String, val name: String)

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

data class Credentials(val base: String, val fullUrl: String, val user: String, val password: String)

data class Account(val server: String, val user: String, val password: String)

class TrackOpt(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

val RESIZE_MODES: List<Pair<String, Int>> = listOf(
    "Ajustar" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
    "Preencher" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
    "Ampliar" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM
)

// =====================================================================
//  4. ARMAZENAMENTO SEGURO E SESSÃO
// =====================================================================

/** Cifra segredos com uma chave guardada no Android Keystore (AES-GCM). */
object SecretStore {
    private const val ALIAS = "iptv_player_pro_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null)
        if (existing is SecretKey) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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

/** Login guardado no SharedPreferences (servidor e palavra-passe cifrados). */
class AccountStore(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("iptv_session", Context.MODE_PRIVATE)

    fun save(a: Account) {
        prefs.edit()
            .putString("server", SecretStore.encrypt(a.server))
            .putString("user", a.user)
            .putString("pass", SecretStore.encrypt(a.password))
            .apply()
    }

    fun load(): Account? {
        val server = SecretStore.decrypt(prefs.getString("server", "").orEmpty())
        if (server.isNotEmpty()) {
            return Account(
                server,
                prefs.getString("user", "").orEmpty(),
                SecretStore.decrypt(prefs.getString("pass", "").orEmpty())
            )
        }
        // Migração: login guardado em texto simples por versões anteriores da app
        val legacy = ctx.getSharedPreferences("iptv", Context.MODE_PRIVATE)
        val ls = legacy.getString("server", "").orEmpty()
        if (ls.isEmpty()) return null
        val acc = Account(ls, legacy.getString("user", "").orEmpty(), legacy.getString("pass", "").orEmpty())
        try {
            save(acc)
            legacy.edit().clear().apply()
        } catch (e: Exception) {
            // sem Keystore: não migra
        }
        return acc
    }

    /** Apaga o login e qualquer resto de sessão (incluindo ficheiros de versões anteriores). */
    fun clear() {
        prefs.edit().clear().apply()
        for (name in listOf("iptv", "lusa_sources", "lusa_settings")) {
            ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }
}

// =====================================================================
//  5. CLIENTE XTREAM CODES / M3U
// =====================================================================

private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)

class IptvClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)        // permite redirecionamentos http <-> https
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

    /** true = conta Xtream válida; false = não parece Xtream (tenta M3U); lança erro se recusar. */
    fun isXtream(c: Credentials): Boolean {
        val auth = try {
            getJson(apiUrl(c))
        } catch (e: IOException) {
            if (e.message?.startsWith("HTTP 4") == true) throw e
            return false
        }
        if (auth !is JSONObject || !auth.has("user_info")) return false
        val info = auth.optJSONObject("user_info")
        if (info != null && info.has("auth") && info.optInt("auth", 1) == 0) {
            throw IOException("Credenciais recusadas pelo servidor.")
        }
        return true
    }

    /** Carrega só as categorias e os itens de uma secção. */
    fun loadXtreamMode(c: Credentials, mode: Mode): ModeData {
        val cats = try {
            parseCategories(getJson("${apiUrl(c)}&action=${mode.catAction}"))
        } catch (e: IOException) {
            if (e.message?.startsWith("HTTP 4") == true) throw e
            emptyList()
        }
        val items = parseItems(mode, c, getJson("${apiUrl(c)}&action=${mode.action}"), null)
        return ModeData(cats, items)
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
        val content = get(url)
        if (!content.contains("#EXTM3U") && !content.contains("#EXTINF")) {
            val preview = content.take(120).trim().ifEmpty { "resposta vazia" }
            throw IOException("Lista M3U inválida: $preview")
        }
        return parseM3u(content)
    }

    private fun parseM3u(content: String): Map<Mode, ModeData> {
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

// =====================================================================
//  6. VIEWMODEL
// =====================================================================

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val store = AccountStore(ctx)
    private val client = IptvClient()

    // ---------------- Navegação geral ----------------
    var screen by mutableStateOf(Screen.LOGIN)
    private var settingsReturn = Screen.DASHBOARD

    // ---------------- Sessão / login ----------------
    var formServer by mutableStateOf("")
    var formUser by mutableStateOf("")
    var formPass by mutableStateOf("")
    var saveLogin by mutableStateOf(true)
    var loginBusy by mutableStateOf(false)
    var loginMsg by mutableStateOf("")
    var account by mutableStateOf<Account?>(null)

    // ---------------- Dados ----------------
    var data by mutableStateOf<Map<Mode, ModeData>>(emptyMap())
    var loadingModes by mutableStateOf<Set<Mode>>(emptySet())
    var modeErrors by mutableStateOf<Map<Mode, String>>(emptyMap())
    private var creds: Credentials? = null
    private var isXtream = false
    private var job: Job? = null

    private fun sanitize(text: String, secret: String): String {
        var t = text.replace(Regex("(?i)(password|username|token)=[^&\\s]+"), "$1=***")
        if (secret.length >= 3) t = t.replace(secret, "***")
        return t
    }

    private fun describeError(e: Exception, secret: String): String {
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
        return sanitize(msg, secret)
    }

    fun login(server: String, user: String, pass: String, save: Boolean) {
        if (loginBusy) return
        if (server.isBlank()) {
            loginMsg = "Indica o URL do servidor ou da lista M3U."
            return
        }
        loginBusy = true
        loginMsg = "A ligar ao servidor..."
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val c = client.normalize(server, user.trim(), pass.trim())
                val hasCreds = c.user.isNotEmpty() && c.password.isNotEmpty()
                val xtream = withContext(Dispatchers.IO) { hasCreds && client.isXtream(c) }
                var m3u: Map<Mode, ModeData>? = null
                if (!xtream) {
                    loginMsg = "A descarregar a lista M3U..."
                    m3u = withContext(Dispatchers.IO) { client.loadM3u(c) }
                }
                // ---- sucesso: substitui a sessão anterior ----
                stopPlayback()
                val acc = Account(server.trim(), user.trim(), pass.trim())
                creds = c
                isXtream = xtream
                account = acc
                if (save) {
                    try {
                        store.save(acc)
                    } catch (e: Exception) {
                        // sem Keystore: a sessão continua, mas não fica guardada
                    }
                } else {
                    store.clear()
                }
                data = m3u ?: emptyMap()
                modeErrors = emptyMap()
                loadingModes = if (xtream) Mode.values().toSet() else emptySet()
                resetBrowse()
                loginMsg = ""
                screen = Screen.DASHBOARD
                if (xtream) {
                    for (m in Mode.values()) loadMode(m)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loginMsg = describeError(e, pass.trim())
            } finally {
                loginBusy = false
            }
        }
    }

    /** (Re)carrega só uma secção; as outras não são tocadas. */
    private suspend fun loadMode(m: Mode) {
        val c = creds ?: return
        loadingModes = loadingModes + m
        modeErrors = modeErrors - m
        try {
            val md = withContext(Dispatchers.IO) {
                if (isXtream) client.loadXtreamMode(c, m) else (client.loadM3u(c)[m] ?: ModeData())
            }
            data = data + (m to md)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            modeErrors = modeErrors + (m to describeError(e, c.password))
        } finally {
            loadingModes = loadingModes - m
        }
    }

    fun refreshMode(m: Mode) {
        if (creds == null || loadingModes.contains(m)) return
        viewModelScope.launch { loadMode(m) }
    }

    /** Termina a sessão: pára o leitor, apaga listas em memória e dados guardados. */
    fun logout() {
        job?.cancel()
        stopPlayback()
        store.clear()
        creds = null
        isXtream = false
        account = null
        data = emptyMap()
        loadingModes = emptySet()
        modeErrors = emptyMap()
        resetBrowse()
        formServer = ""
        formUser = ""
        formPass = ""
        saveLogin = true
        loginMsg = ""
        loginBusy = false
        screen = Screen.LOGIN
    }

    fun openSettings() {
        settingsReturn = if (screen == Screen.BROWSE) Screen.BROWSE else Screen.DASHBOARD
        loginMsg = ""
        screen = Screen.SETTINGS
    }

    fun closeSettings() {
        screen = if (account == null) Screen.LOGIN else settingsReturn
    }

    // ---------------- Navegação TV / Filmes / Séries ----------------
    var browseMode by mutableStateOf(Mode.LIVE)
    var categoryId by mutableStateOf<String?>(null)      // null = lista de categorias
    var query by mutableStateOf("")
    var seriesItem by mutableStateOf<Item?>(null)
    var episodes by mutableStateOf<List<Episode>>(emptyList())
    var season by mutableIntStateOf(0)
    var busy by mutableStateOf(false)
    var browseMsg by mutableStateOf("")
    var fullscreen by mutableStateOf(false)

    private fun resetBrowse() {
        categoryId = null
        query = ""
        seriesItem = null
        episodes = emptyList()
        browseMsg = ""
        fullscreen = false
    }

    fun openBrowse(m: Mode) {
        browseMode = m
        resetBrowse()
        screen = Screen.BROWSE
    }

    fun switchMode(m: Mode) {
        browseMode = m
        resetBrowse()
    }

    fun leaveBrowse() {
        stopPlayback()
        screen = Screen.DASHBOARD
    }

    /** Ecrã inteiro -> nível anterior -> dashboard. */
    fun browseBack() {
        when {
            fullscreen -> fullscreen = false
            seriesItem != null -> {
                seriesItem = null
                episodes = emptyList()
                query = ""
            }
            categoryId != null -> {
                categoryId = null
                query = ""
            }
            else -> leaveBrowse()
        }
    }

    fun visibleCategories(): List<Category> {
        val cats = data[browseMode]?.categories.orEmpty()
        val q = query.trim()
        return if (q.isEmpty()) cats else cats.filter { it.name.contains(q, ignoreCase = true) }
    }

    fun visibleItems(): List<Item> {
        val md = data[browseMode] ?: return emptyList()
        val cid = categoryId ?: return emptyList()
        val q = query.trim()
        var list = if (cid == CAT_ALL) md.items else md.items.filter { it.catId == cid }
        if (q.isNotEmpty()) list = list.filter { it.name.contains(q, ignoreCase = true) }
        return list
    }

    fun selectCategory(id: String) {
        categoryId = id
        query = ""
        browseMsg = ""
        val c = creds
        if (!isXtream || c == null || id == CAT_ALL) return
        val m = browseMode
        if (data[m]?.items?.any { it.catId == id } == true) return
        viewModelScope.launch {
            busy = true
            try {
                val items = withContext(Dispatchers.IO) { client.fetchCategory(m, c, id) }
                val md = data[m] ?: ModeData()
                data = data + (m to md.copy(items = md.items + items))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                browseMsg = "Não foi possível carregar a categoria. " + describeError(e, c.password)
            } finally {
                busy = false
            }
        }
    }

    fun openSeries(item: Item) {
        val c = creds ?: return
        seriesItem = item
        episodes = emptyList()
        browseMsg = ""
        query = ""
        busy = true
        viewModelScope.launch {
            try {
                val eps = withContext(Dispatchers.IO) { client.loadEpisodes(c, item.seriesId) }
                episodes = eps
                season = eps.firstOrNull()?.season ?: 0
                if (eps.isEmpty()) browseMsg = "Esta série não tem episódios disponíveis."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                browseMsg = "Não foi possível carregar os episódios. " + describeError(e, c.password)
            } finally {
                busy = false
            }
        }
    }

    // ---------------- Leitor (Media3 ExoPlayer) ----------------
    var exo by mutableStateOf<ExoPlayer?>(null)
    var hasPlayer by mutableStateOf(false)
    var isLive by mutableStateOf(true)
    var playingName by mutableStateOf("")
    var playingUrl by mutableStateOf("")
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    var progress by mutableFloatStateOf(0f)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var resizeIdx by mutableIntStateOf(0)
    var playlistSize by mutableIntStateOf(0)

    private var playlist: List<Item> = emptyList()
    private var playIndex = 0
    private var retry = 0
    private var hlsTried = false
    private var playToken = 0

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                errorMsg = null
                retry = 0
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        exo?.let { return it }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        val p = ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http, extractors))
            .build()
        p.addListener(listener)
        p.playWhenReady = true
        exo = p
        return p
    }

    private fun releasePlayer() {
        val p = exo ?: return
        p.removeListener(listener)
        p.stop()
        p.clearMediaItems()
        p.release()
        exo = null
    }

    /** Pára e liberta o ExoPlayer e limpa o estado de reprodução. */
    fun stopPlayback() {
        playToken++
        releasePlayer()
        hasPlayer = false
        playing = false
        buffering = false
        errorMsg = null
        fullscreen = false
        playlist = emptyList()
        playlistSize = 0
        playIndex = 0
        playingName = ""
        playingUrl = ""
        progress = 0f
        positionMs = 0L
        durationMs = 0L
    }

    fun onAppStopped() {
        exo?.pause()
    }

    fun play(items: List<Item>, index: Int, live: Boolean) {
        if (items.isEmpty()) return
        playlist = items
        playlistSize = items.size
        playIndex = index.coerceIn(0, items.size - 1)
        isLive = live
        startCurrent(false)
        hasPlayer = true
    }

    private fun startCurrent(forceHls: Boolean) {
        val item = playlist.getOrNull(playIndex) ?: return
        playToken++
        retry = 0
        if (!forceHls) hlsTried = false
        playingName = item.name
        playingUrl = item.url
        errorMsg = null
        progress = 0f
        positionMs = 0L
        durationMs = 0L
        val builder = MediaItem.Builder().setUri(item.url)
        if (forceHls) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        val p = ensurePlayer()
        p.setMediaItem(builder.build())
        p.prepare()
        p.playWhenReady = true
    }

    /** Canal seguinte (+1) ou anterior (-1) na lista ativa. */
    fun step(delta: Int) {
        val n = playlist.size
        if (n < 2) return
        playIndex = ((playIndex + delta) % n + n) % n
        startCurrent(false)
    }

    fun togglePause() {
        val p = exo ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun seekFraction(f: Float) {
        val p = exo ?: return
        val d = durationMs
        if (d > 0) p.seekTo((d * f).toLong())
    }

    fun tick() {
        val p = exo ?: return
        positionMs = p.currentPosition
        val d = p.duration
        durationMs = if (d == C.TIME_UNSET || d < 0) 0L else d
        progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    private fun handleError(e: PlaybackException) {
        buffering = false
        // Sem extensão reconhecível: tenta uma vez como HLS (.m3u8)
        if (e.cause is UnrecognizedInputFormatException && !hlsTried) {
            hlsTried = true
            startCurrent(true)
            return
        }
        if (retry < 3) {
            retry++
            val attempt = retry
            val token = playToken
            errorMsg = "A tentar religar ($attempt/3)..."
            viewModelScope.launch {
                delay(2000L * attempt)
                val p = exo
                if (p != null && token == playToken && hasPlayer) {
                    p.prepare()
                    p.playWhenReady = true
                }
            }
        } else {
            errorMsg = describePlayerError(e)
        }
    }

    private fun describePlayerError(e: PlaybackException): String {
        val cause = e.cause
        return when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Falha de rede. Verifica a ligação à Internet."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                val code = (cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
                when (code) {
                    401, 403 -> "Acesso recusado (credenciais ou limite de ligações da conta)."
                    404 -> "Fluxo indisponível (não encontrado no servidor)."
                    else -> "O servidor respondeu com erro HTTP $code."
                }
            }
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                "Formato não suportado neste dispositivo."
            else -> "Fluxo indisponível (" + e.errorCodeName + ")."
        }
    }

    // ---- Faixas de áudio / legendas / proporção ----
    fun trackOptions(type: Int): List<TrackOpt> {
        val p = exo ?: return emptyList()
        val out = ArrayList<TrackOpt>()
        for (g in p.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                val lang = f.language?.let { Locale(it).displayLanguage }?.takeIf { it.isNotBlank() }
                val label = f.label ?: lang ?: ("Faixa " + (out.size + 1))
                out.add(TrackOpt(label, g, i, g.isTrackSelected(i)))
            }
        }
        return out
    }

    fun selectTrack(type: Int, opt: TrackOpt?) {
        val p = exo ?: return
        val b = p.trackSelectionParameters.buildUpon()
        if (opt == null) {
            b.setTrackTypeDisabled(type, true)
        } else {
            b.setTrackTypeDisabled(type, false)
            b.setOverrideForType(TrackSelectionOverride(opt.group.mediaTrackGroup, opt.index))
        }
        p.trackSelectionParameters = b.build()
    }

    override fun onCleared() {
        job?.cancel()
        try {
            releasePlayer()
        } catch (e: Exception) {
            // ignorado ao fechar
        }
        super.onCleared()
    }

    // ---------------- Arranque: login automático se existir sessão guardada ----------------
    init {
        val saved = try {
            store.load()
        } catch (e: Exception) {
            null
        }
        if (saved != null) {
            formServer = saved.server
            formUser = saved.user
            formPass = saved.password
            login(saved.server, saved.user, saved.password, true)
        }
    }
}

// =====================================================================
//  7. COMPONENTES COMUNS
// =====================================================================

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun isWide(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.screenWidthDp >= 600 || cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
}

private fun fmtTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

/** Foco visível (contorno e ligeiro aumento) para comando de Android TV. */
fun Modifier.tvFocus(shape: Shape = RoundedCornerShape(16.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "focusScale")
    this
        .onFocusChanged { focused = it.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (focused) Modifier.border(BorderStroke(3.dp, Color.White), shape) else Modifier)
}

@Composable
fun LogoMark(size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(Color(0xFF27D09B), Color(0xFF006F80)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(AppIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val brush: Brush = when {
        !enabled -> Brush.horizontalGradient(listOf(Color(0xFF2A3350), Color(0xFF2A3350)))
        danger -> Brush.horizontalGradient(listOf(Color(0xFFB3261E), Color(0xFFD64545)))
        else -> ButtonBrush
    }
    Box(
        modifier = modifier
            .tvFocus(RoundedCornerShape(26.dp))
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = Color(0x66000000),
    tint: Color = Color.White
) {
    Box(
        modifier = modifier
            .tvFocus(CircleShape)
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Imagem remota sem deformação; mostra a inicial do nome até existir imagem. */
@Composable
fun RemoteImage(
    url: String,
    label: String,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop
) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(Color(0xFF1E2A4A), Color(0xFF172038)))),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Text(label.trim().take(1).uppercase(), color = TextSec, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = scale,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PanelDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Surf,
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(0.92f).heightIn(max = maxH)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                content()
            }
        }
    }
}

/** Linha selecionável: usa fundo e ícone (não depende só da cor). */
@Composable
fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocus(shape)
            .clip(shape)
            .background(if (selected) Selected else SurfHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        if (selected) Icon(AppIcons.Check, contentDescription = "Selecionado", tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Color(0xFF2B3654),
    focusedContainerColor = Surf,
    unfocusedContainerColor = Surf,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextSec,
    cursorColor = Accent
)

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, color = TextSec) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null, tint = TextSec) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                CircleIconButton(AppIcons.Close, "Limpar", { onChange("") }, size = 32.dp, container = Color.Transparent, tint = TextSec)
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surf).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Accent)
        content()
    }
}

// =====================================================================
//  ECRÃ DE LOGIN
// =====================================================================

@Composable
fun LoginScreen(vm: AppViewModel) {
    var showPass by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("IPTV Player Pro", style = MaterialTheme.typography.headlineMedium)
                    Text("Xtream Codes ou lista M3U", color = TextSec, fontSize = 13.sp)
                }
            }
            OutlinedTextField(
                value = vm.formServer, onValueChange = { vm.formServer = it },
                label = { Text("URL do servidor ou da lista M3U") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingIcon = { Icon(AppIcons.Link, contentDescription = null) },
                shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.formUser, onValueChange = { vm.formUser = it },
                label = { Text("Utilizador") }, singleLine = true,
                leadingIcon = { Icon(AppIcons.Person, contentDescription = null) },
                shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vm.formPass, onValueChange = { vm.formPass = it },
                label = { Text("Palavra-passe") }, singleLine = true,
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = { Icon(AppIcons.Lock, contentDescription = null) },
                trailingIcon = {
                    CircleIconButton(
                        if (showPass) AppIcons.VisibilityOff else AppIcons.Visibility,
                        if (showPass) "Ocultar palavra-passe" else "Mostrar palavra-passe",
                        { showPass = !showPass }, size = 36.dp, container = Color.Transparent, tint = TextSec
                    )
                },
                shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.saveLogin, onCheckedChange = { vm.saveLogin = it })
                Spacer(Modifier.width(10.dp))
                Text("Guardar login neste dispositivo (cifrado)")
            }
            if (vm.formServer.isNotBlank() && !vm.formServer.trim().startsWith("https://")) {
                Text("Aviso: esta ligação (HTTP) não é cifrada.", color = TextSec, fontSize = 12.sp)
            }
            if (vm.loginMsg.isNotEmpty()) {
                Text(vm.loginMsg, color = if (vm.loginBusy) TextSec else ErrorC)
            }
            if (vm.loginBusy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Accent)
                }
            }
            GradientButton(
                "Ligar", { vm.login(vm.formServer, vm.formUser, vm.formPass, vm.saveLogin) },
                Modifier.fillMaxWidth(), enabled = !vm.loginBusy
            )
            Text(
                "Usa apenas fontes e conteúdos a que tens direito de aceder.",
                color = TextSec, fontSize = 12.sp
            )
        }
    }
}

// =====================================================================
//  DASHBOARD PRINCIPAL
// =====================================================================

@Composable
fun DashboardScreen(vm: AppViewModel) {
    val wide = isWide()
    val cards = listOf(
        Triple(Mode.LIVE, GradLive, AppIcons.Tv),
        Triple(Mode.VOD, GradMovies, AppIcons.PlayCircle),
        Triple(Mode.SERIES, GradSeries, AppIcons.Series)
    )
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(40.dp)
            Spacer(Modifier.width(10.dp))
            Text("IPTV Player Pro", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            CircleIconButton(AppIcons.Settings, "Opções", { vm.openSettings() }, container = SurfHigh)
        }
        Spacer(Modifier.height(14.dp))
        if (wide) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                cards.forEach { (m, g, ic) -> DashCard(vm, m, g, ic, Modifier.weight(1f).fillMaxHeight()) }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                cards.forEach { (m, g, ic) -> DashCard(vm, m, g, ic, Modifier.weight(1f).fillMaxWidth()) }
            }
        }
        Spacer(Modifier.height(12.dp))
        val acc = vm.account
        if (acc != null) {
            val host = Uri.parse(if (acc.server.contains("://")) acc.server else "http://" + acc.server).host ?: acc.server
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surf).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (vm.modeErrors.isEmpty()) OkC else ErrorC))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Fonte: $host" + if (acc.user.isNotEmpty()) "  ·  ${acc.user}" else "",
                    color = TextSec, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DashCard(
    vm: AppViewModel, mode: Mode, grad: List<Color>, icon: ImageVector, modifier: Modifier
) {
    val loading = vm.loadingModes.contains(mode)
    val count = vm.data[mode]?.items?.size ?: 0
    val err = vm.modeErrors[mode]
    val enabled = !loading && count > 0
    val title = when (mode) {
        Mode.LIVE -> "TV AO VIVO"
        Mode.VOD -> "FILMES"
        Mode.SERIES -> "SÉRIES"
    }
    val noun = when (mode) {
        Mode.LIVE -> "canais"
        Mode.VOD -> "filmes"
        Mode.SERIES -> "séries"
    }
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .tvFocus(shape)
            .clip(shape)
            .background(Brush.linearGradient(grad))
            .alpha(if (enabled || loading) 1f else 0.6f)
            .clickable(enabled = enabled) { vm.openBrowse(mode) }
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                when {
                    loading -> "A carregar..."
                    err != null -> err
                    count > 0 -> "$count $noun"
                    else -> "Sem conteúdos nesta fonte"
                },
                color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp, textAlign = TextAlign.Center
            )
        }
        // Botão de atualizar só esta secção (canto inferior direito)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0x66000000))
                .clickable(enabled = !loading) { vm.refreshMode(mode) },
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
            } else {
                Icon(AppIcons.Refresh, contentDescription = "Atualizar $title", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// =====================================================================
//  NAVEGAÇÃO (menu à esquerda) + LEITOR (à direita)
// =====================================================================

@Composable
fun BrowseScreen(vm: AppViewModel) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current

    BackHandler { vm.browseBack() }

    // Ecrã inteiro: esconde as barras do sistema
    DisposableEffect(vm.fullscreen) {
        val activity = view.context.findActivity()
        val controller = activity?.window?.let { WindowInsetsControllerCompat(it, view) }
        if (vm.fullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    if (vm.fullscreen && vm.hasPlayer) {
        PlayerArea(vm, Modifier.fillMaxSize())
    } else if (landscape) {
        Row(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            LeftPane(vm, Modifier.width(340.dp).fillMaxHeight())
            if (vm.hasPlayer) {
                PlayerArea(vm, Modifier.weight(1f).fillMaxHeight())
            } else {
                PlayerPlaceholder(Modifier.weight(1f).fillMaxHeight())
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            if (vm.hasPlayer) PlayerArea(vm, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            LeftPane(vm, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun PlayerPlaceholder(modifier: Modifier) {
    Column(
        modifier = modifier.background(Color.Black).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(AppIcons.PlayCircle, contentDescription = null, tint = TextSec, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("Escolhe uma categoria e um item para começar a ver.", color = TextSec, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LeftPane(vm: AppViewModel, modifier: Modifier) {
    val level = when {
        vm.seriesItem != null -> 2
        vm.categoryId != null -> 1
        else -> 0
    }
    val title = when (level) {
        2 -> vm.seriesItem?.name ?: ""
        1 -> if (vm.categoryId == CAT_ALL) "Todos" else
            vm.data[vm.browseMode]?.categories?.firstOrNull { it.id == vm.categoryId }?.name ?: "Categoria"
        else -> vm.browseMode.label
    }
    Column(modifier = modifier.background(Surf)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(AppIcons.Back, "Voltar", { vm.browseBack() }, container = SurfHigh, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                title, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            CircleIconButton(AppIcons.Settings, "Opções", { vm.openSettings() }, container = SurfHigh, size = 40.dp)
        }
        if (vm.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Color.Transparent)
        if (level == 0) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Mode.values().size) { i ->
                    val m = Mode.values()[i]
                    ChipTab(m.label, m == vm.browseMode) { vm.switchMode(m) }
                }
            }
        }
        if (level < 2) {
            SearchField(vm.query, if (level == 0) "Pesquisar categorias" else "Pesquisar") { vm.query = it }
        }
        if (vm.browseMsg.isNotEmpty()) {
            Text(vm.browseMsg, color = ErrorC, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (level) {
                0 -> CategoryList(vm)
                1 -> ItemList(vm)
                else -> EpisodeList(vm)
            }
        }
    }
}

@Composable
private fun ChipTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .tvFocus(shape)
            .clip(shape)
            .background(if (selected) Selected else SurfHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun NavRow(label: String, count: Int?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocus(shape)
            .clip(shape)
            .background(SurfHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count != null) Text(count.toString(), color = TextSec, fontSize = 12.sp)
    }
}

@Composable
private fun CategoryList(vm: AppViewModel) {
    val cats = remember(vm.data, vm.browseMode, vm.query) { vm.visibleCategories() }
    val counts = remember(vm.data, vm.browseMode) {
        vm.data[vm.browseMode]?.items.orEmpty().groupingBy { it.catId }.eachCount()
    }
    val total = vm.data[vm.browseMode]?.items?.size ?: 0
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { NavRow("Todos", total) { vm.selectCategory(CAT_ALL) } }
        items(cats.size) { i ->
            val c = cats[i]
            NavRow(c.name, counts[c.id]) { vm.selectCategory(c.id) }
        }
    }
}

@Composable
private fun ItemList(vm: AppViewModel) {
    val list = remember(vm.data, vm.browseMode, vm.categoryId, vm.query) { vm.visibleItems() }
    if (list.isEmpty()) {
        Text(
            if (vm.busy) "A carregar..." else "Sem resultados",
            color = TextSec, modifier = Modifier.padding(16.dp)
        )
        return
    }
    val live = vm.browseMode == Mode.LIVE
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(list.size) { i ->
            val item = list[i]
            val shape = RoundedCornerShape(14.dp)
            val isPlaying = item.url.isNotEmpty() && item.url == vm.playingUrl
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocus(shape)
                    .clip(shape)
                    .background(if (isPlaying) Selected else SurfHigh)
                    .clickable {
                        if (vm.browseMode == Mode.SERIES && item.seriesId.isNotEmpty()) vm.openSeries(item)
                        else vm.play(list, i, live)
                    }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RemoteImage(
                    item.icon, item.name,
                    Modifier.size(width = 44.dp, height = if (live) 44.dp else 62.dp).clip(RoundedCornerShape(8.dp)),
                    if (live) ContentScale.Fit else ContentScale.Crop
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isPlaying) {
                    Icon(AppIcons.Play, contentDescription = "A reproduzir", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(vm: AppViewModel) {
    val seasons = remember(vm.episodes) { vm.episodes.map { it.season }.distinct() }
    val shown = remember(vm.episodes, vm.season) {
        vm.episodes.filter { it.season == vm.season }.map { Item(it.id, it.title, "", it.url) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        if (seasons.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(seasons.size) { i ->
                    val s = seasons[i]
                    ChipTab("Temporada $s", vm.season == s) { vm.season = s }
                }
            }
        }
        if (shown.isEmpty()) {
            Text(
                if (vm.busy) "A carregar episódios..." else "Sem episódios",
                color = TextSec, modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(shown.size) { i ->
                    val ep = shown[i]
                    val shape = RoundedCornerShape(14.dp)
                    val isPlaying = ep.url == vm.playingUrl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocus(shape)
                            .clip(shape)
                            .background(if (isPlaying) Selected else SurfHigh)
                            .clickable { vm.play(shown, i, false) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.PlayCircle, contentDescription = null, tint = Accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(ep.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// =====================================================================
//  LEITOR (ExoPlayer) com toque = ecrã inteiro e swipe = mudar de canal
// =====================================================================

@Composable
private fun PlayerArea(vm: AppViewModel, modifier: Modifier) {
    var showControls by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var dialog by remember { mutableStateOf("") }          // "", "audio", "subs", "resize"
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val swipePx = with(LocalDensity.current) { 80.dp.toPx() }
    val small = !vm.fullscreen

    LaunchedEffect(lastTouch) {
        showControls = true
        delay(3500)
        showControls = false
    }
    LaunchedEffect(Unit) {
        while (true) {
            vm.tick()
            delay(500)
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(Color.Black.toArgb())
                }
            },
            update = { v ->
                v.player = vm.exo
                v.resizeMode = RESIZE_MODES[vm.resizeIdx.coerceIn(0, RESIZE_MODES.lastIndex)].second
            },
            modifier = Modifier.fillMaxSize()
        )

        // Camada de gestos: 1 toque = ecrã inteiro / restaurar; swipe = canal seguinte/anterior (só TV ao Vivo)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        vm.fullscreen = !vm.fullscreen
                    })
                }
                .pointerInput(vm.isLive) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (vm.isLive) {
                                if (total <= -swipePx) {
                                    vm.step(1)
                                    lastTouch = System.currentTimeMillis()
                                } else if (total >= swipePx) {
                                    vm.step(-1)
                                    lastTouch = System.currentTimeMillis()
                                }
                            }
                            total = 0f
                        },
                        onDragCancel = { total = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            total += amount
                        }
                    )
                }
        )

        if (vm.buffering) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Accent)
        vm.errorMsg?.let {
            Text(
                it, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC000000))
                    .padding(14.dp)
            )
        }

        AnimatedVisibility(
            visible = showControls || !vm.playing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ---- barra de cima ----
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (vm.fullscreen) {
                        CircleIconButton(AppIcons.Back, "Voltar", { vm.fullscreen = false }, size = 40.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        vm.playingName, color = Color.White, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = if (small) 13.sp else 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    CircleIconButton(AppIcons.Audio, "Faixa de áudio", { dialog = "audio"; lastTouch = System.currentTimeMillis() }, size = if (small) 34.dp else 40.dp)
                    Spacer(Modifier.width(6.dp))
                    CircleIconButton(AppIcons.Subtitles, "Legendas", { dialog = "subs"; lastTouch = System.currentTimeMillis() }, size = if (small) 34.dp else 40.dp)
                    Spacer(Modifier.width(6.dp))
                    CircleIconButton(AppIcons.Aspect, "Proporção do ecrã", { dialog = "resize"; lastTouch = System.currentTimeMillis() }, size = if (small) 34.dp else 40.dp)
                    Spacer(Modifier.width(6.dp))
                    CircleIconButton(
                        if (vm.fullscreen) AppIcons.FullscreenExit else AppIcons.Fullscreen,
                        if (vm.fullscreen) "Sair de ecrã inteiro" else "Ecrã inteiro",
                        { vm.fullscreen = !vm.fullscreen }, size = if (small) 34.dp else 40.dp
                    )
                }

                // ---- barra de baixo ----
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 8.dp)
                ) {
                    if (!vm.isLive && vm.durationMs > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fmtTime(vm.positionMs), color = Color.White, fontSize = 12.sp)
                            Slider(
                                value = dragValue ?: vm.progress,
                                onValueChange = { dragValue = it; lastTouch = System.currentTimeMillis() },
                                onValueChangeFinished = {
                                    dragValue?.let { vm.seekFraction(it) }
                                    dragValue = null
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Accent, activeTrackColor = Accent,
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                ),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )
                            Text(fmtTime(vm.durationMs), color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (vm.playlistSize > 1) {
                            CircleIconButton(AppIcons.SkipPrevious, "Anterior", { vm.step(-1); lastTouch = System.currentTimeMillis() }, size = if (small) 38.dp else 46.dp)
                            Spacer(Modifier.width(16.dp))
                        }
                        CircleIconButton(
                            if (vm.playing) AppIcons.Pause else AppIcons.Play,
                            if (vm.playing) "Pausa" else "Reproduzir",
                            { vm.togglePause(); lastTouch = System.currentTimeMillis() },
                            size = if (small) 46.dp else 56.dp, container = Selected
                        )
                        if (vm.playlistSize > 1) {
                            Spacer(Modifier.width(16.dp))
                            CircleIconButton(AppIcons.SkipNext, "Seguinte", { vm.step(1); lastTouch = System.currentTimeMillis() }, size = if (small) 38.dp else 46.dp)
                        }
                        if (vm.isLive) {
                            Spacer(Modifier.width(16.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x66000000))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(ErrorC))
                                Spacer(Modifier.width(6.dp))
                                Text("AO VIVO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "audio" -> {
            val opts = remember { vm.trackOptions(C.TRACK_TYPE_AUDIO) }
            PanelDialog("Faixa de áudio", { dialog = "" }) {
                if (opts.isEmpty()) Text("Sem faixas de áudio alternativas.", color = TextSec)
                opts.forEach { o ->
                    OptionRow(o.label, o.selected) { vm.selectTrack(C.TRACK_TYPE_AUDIO, o); dialog = "" }
                }
            }
        }
        "subs" -> {
            val opts = remember { vm.trackOptions(C.TRACK_TYPE_TEXT) }
            PanelDialog("Legendas", { dialog = "" }) {
                if (opts.isEmpty()) {
                    Text("Sem legendas neste conteúdo.", color = TextSec)
                } else {
                    OptionRow("Sem legendas", opts.none { it.selected }) { vm.selectTrack(C.TRACK_TYPE_TEXT, null); dialog = "" }
                    opts.forEach { o ->
                        OptionRow(o.label, o.selected) { vm.selectTrack(C.TRACK_TYPE_TEXT, o); dialog = "" }
                    }
                }
            }
        }
        "resize" -> PanelDialog("Proporção do ecrã", { dialog = "" }) {
            RESIZE_MODES.forEachIndexed { i, r ->
                OptionRow(r.first, vm.resizeIdx == i) { vm.resizeIdx = i; dialog = "" }
            }
        }
    }
}

// =====================================================================
//  OPÇÕES / DEFINIÇÕES (funciona em vertical: tudo com scroll)
// =====================================================================

@Composable
fun SettingsScreen(vm: AppViewModel) {
    BackHandler { vm.closeSettings() }
    val acc = vm.account
    var server by remember(acc) { mutableStateOf(acc?.server ?: "") }
    var user by remember(acc) { mutableStateOf(acc?.user ?: "") }
    var pass by remember(acc) { mutableStateOf(acc?.password ?: "") }
    var showPass by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(AppIcons.Back, "Voltar", { vm.closeSettings() }, container = SurfHigh)
            Spacer(Modifier.width(12.dp))
            Text("Opções", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard("Conta atual") {
                if (acc == null) {
                    Text("Sem sessão iniciada.", color = TextSec)
                } else {
                    val host = Uri.parse(if (acc.server.contains("://")) acc.server else "http://" + acc.server).host ?: acc.server
                    Text("Servidor: $host", color = Color.White)
                    Text("Utilizador: " + acc.user.ifEmpty { "—" }, color = Color.White)
                }
            }

            SectionCard("Editar Fontes") {
                Text(
                    "Altera o URL do servidor Xtream Codes / lista M3U, o utilizador ou a palavra-passe. Ao guardar, as listas são recarregadas.",
                    color = TextSec, fontSize = 13.sp
                )
                OutlinedTextField(
                    value = server, onValueChange = { server = it },
                    label = { Text("URL do servidor ou da lista M3U") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    leadingIcon = { Icon(AppIcons.Link, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Utilizador") }, singleLine = true,
                    leadingIcon = { Icon(AppIcons.Person, contentDescription = null) },
                    shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Palavra-passe") }, singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(AppIcons.Lock, contentDescription = null) },
                    trailingIcon = {
                        CircleIconButton(
                            if (showPass) AppIcons.VisibilityOff else AppIcons.Visibility,
                            if (showPass) "Ocultar palavra-passe" else "Mostrar palavra-passe",
                            { showPass = !showPass }, size = 36.dp, container = Color.Transparent, tint = TextSec
                        )
                    },
                    shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                )
                if (vm.loginMsg.isNotEmpty()) {
                    Text(vm.loginMsg, color = if (vm.loginBusy) TextSec else ErrorC)
                }
                if (vm.loginBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Accent)
                GradientButton(
                    "Guardar e recarregar",
                    { vm.login(server, user, pass, true) },
                    Modifier.fillMaxWidth(), enabled = !vm.loginBusy
                )
            }

            SectionCard("Sessão") {
                Text(
                    "Termina a sessão neste dispositivo: pára o leitor, apaga as listas em memória e o login guardado, e volta ao ecrã de login.",
                    color = TextSec, fontSize = 13.sp
                )
                GradientButton(
                    "Logout / Mudar de conta",
                    { vm.logout() },
                    Modifier.fillMaxWidth(), danger = true
                )
            }

            Text(
                "IPTV Player Pro  ·  Media3 ExoPlayer\nUsa apenas fontes e conteúdos a que tens direito de aceder.",
                color = TextSec, fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// =====================================================================
//  8. MAIN ACTIVITY
// =====================================================================

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    contentColor = Color.White
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xFF0A1330), Bg)))
                    ) {
                        when (vm.screen) {
                            Screen.LOGIN -> LoginScreen(vm)
                            Screen.DASHBOARD -> DashboardScreen(vm)
                            Screen.BROWSE -> BrowseScreen(vm)
                            Screen.SETTINGS -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.onAppStopped()
    }
}
