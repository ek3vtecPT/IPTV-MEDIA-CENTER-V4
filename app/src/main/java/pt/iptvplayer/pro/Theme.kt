package pt.iptvplayer.pro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

// ---- Paleta "Lusa IPTV" ----
val Bg = Color(0xFF05091A)
val Surf = Color(0xFF0D1428)
val SurfHigh = Color(0xFF172038)
val Accent = Color(0xFF00A8B5)
val Selected = Color(0xFF006F80)
val TextMain = Color(0xFFFFFFFF)
val TextSec = Color(0xFFABB3C4)
val ErrorC = Color(0xFFEF5350)
val OkC = Color(0xFF27D09B)
val GradLive = listOf(Color(0xFF27D09B), Color(0xFF3458D5))
val GradMovies = listOf(Color(0xFFE92D61), Color(0xFFFFA52B))
val GradSeries = listOf(Color(0xFFA33BC7), Color(0xFF43AAE1))
val ButtonBrush = Brush.horizontalGradient(listOf(Color(0xFF006F80), Color(0xFF008A9A)))

private val LusaColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Selected,
    onSecondary = Color.White,
    background = Bg,
    onBackground = TextMain,
    surface = Surf,
    onSurface = TextMain,
    surfaceVariant = SurfHigh,
    onSurfaceVariant = TextSec,
    error = ErrorC,
    outline = Color(0xFF2B3654),
    primaryContainer = Selected,
    onPrimaryContainer = Color.White,
    secondaryContainer = Selected,
    onSecondaryContainer = Color.White
)

private val LusaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LusaTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun LusaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LusaColors, typography = LusaTypography, shapes = LusaShapes, content = content)
}

@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

/** Foco visível (contorno branco e ligeiro aumento), sem mexer nos restantes elementos. */
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
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
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

/** Imagem remota sem deformação; mostra a inicial do nome enquanto não existe imagem. */
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
            Text(
                label.trim().take(1).uppercase(),
                color = TextSec,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
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
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            CircleIconButton(AppIcons.Back, "Voltar", onBack, container = SurfHigh)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = TextSec, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, color = TextSec)
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

/** Linha selecionável: usa contorno e ícone (não depende só da cor). */
@Composable
fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
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
