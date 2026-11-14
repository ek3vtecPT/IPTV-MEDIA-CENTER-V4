package pt.iptvplayer.pro

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// =====================================================================
//  ECRÃ INICIAL
// =====================================================================

@Composable
fun HomeScreen(vm: AppViewModel) {
    val wide = isWide()
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        HomeTopBar(vm)
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                vm.data.isEmpty() && vm.loading -> LoadingPanel(vm)
                vm.data.isEmpty() -> ErrorPanel(vm)
                else -> HomeCards(vm, wide)
            }
        }
        Spacer(Modifier.height(10.dp))
        HomeFooter(vm)
    }
}

@Composable
private fun HomeTopBar(vm: AppViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LogoMark(40.dp)
        Spacer(Modifier.width(10.dp))
        Text("Lusa IPTV", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        ClockText()
        Spacer(Modifier.width(8.dp))
        CircleIconButton(AppIcons.Search, "Pesquisar", { vm.openSearch() }, container = SurfHigh)
        Spacer(Modifier.width(8.dp))
        CircleIconButton(AppIcons.Settings, "Definições", { vm.openSettings() }, container = SurfHigh)
    }
}

@Composable
private fun ClockText() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val fmt = remember { SimpleDateFormat("EEE, d MMM  HH:mm", Locale("pt", "PT")) }
    Text(fmt.format(Date(now)), color = TextSec, fontSize = 13.sp)
}

@Composable
private fun LoadingPanel(vm: AppViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Accent)
        Spacer(Modifier.height(16.dp))
        Text(vm.status, color = TextSec, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        GradientButton("Cancelar", { vm.cancelLoad() })
    }
}

@Composable
private fun ErrorPanel(vm: AppViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (vm.sources.isEmpty()) "Sem fonte configurada" else "Fonte indisponível",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        if (vm.status.isNotEmpty()) Text(vm.status, color = TextSec, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        vm.activeSource?.let { s -> GradientButton("Tentar novamente", { vm.loadSource(s) }) }
        Spacer(Modifier.height(10.dp))
        GradientButton("Gerir fontes", { vm.openSettings() })
    }
}

@Composable
private fun HomeCards(vm: AppViewModel, wide: Boolean) {
    val cards = listOf(
        Triple(Mode.LIVE, GradLive, AppIcons.Tv),
        Triple(Mode.VOD, GradMovies, AppIcons.PlayCircle),
        Triple(Mode.SERIES, GradSeries, AppIcons.Series)
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                cards.forEach { (m, g, ic) -> ModeCard(vm, m, g, ic, Modifier.weight(1f).fillMaxHeight()) }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                cards.forEach { (m, g, ic) -> ModeCard(vm, m, g, ic, Modifier.weight(1f).fillMaxWidth()) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutChip("Favoritos", AppIcons.Star) { vm.openCatalog(Mode.LIVE, CAT_FAV) }
            ShortcutChip("Histórico", AppIcons.History) { vm.openCatalog(Mode.LIVE, CAT_HIST) }
        }
    }
}

@Composable
private fun ModeCard(vm: AppViewModel, mode: Mode, grad: List<Color>, icon: ImageVector, modifier: Modifier) {
    val count = vm.data[mode]?.items?.size ?: 0
    val available = count > 0
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
            .alpha(if (available) 1f else 0.45f)
            .clip(shape)
            .background(Brush.linearGradient(grad))
            .clickable(enabled = available) { vm.openCatalog(mode) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                if (available) "$count $noun" else "Não disponível nesta fonte",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ShortcutChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .tvFocus(shape)
            .clip(shape)
            .background(SurfHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White)
    }
}

@Composable
private fun HomeFooter(vm: AppViewModel) {
    val s = vm.activeSource
    val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale("pt", "PT")) }
    val fmtTime = remember { SimpleDateFormat("d MMM, HH:mm", Locale("pt", "PT")) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surf).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(if (vm.online) OkC else ErrorC)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                (s?.name ?: "Sem fonte") + " · " + if (vm.online) "Ligado" else "Sem ligação",
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp
            )
            val parts = ArrayList<String>()
            if (s != null && s.lastUpdate > 0L) parts.add("Atualizado: " + fmtTime.format(Date(s.lastUpdate)))
            vm.expiryMs?.let { parts.add("Conta válida até " + fmt.format(Date(it))) }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), color = TextSec, fontSize = 12.sp)
        }
        if (s != null) {
            CircleIconButton(AppIcons.Refresh, "Atualizar catálogos", { vm.refresh() }, container = SurfHigh)
        }
        if (vm.loading) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Accent)
        }
    }
}

// =====================================================================
//  ADICIONAR FONTE
// =====================================================================

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
fun AddSourceScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(SourceType.XTREAM) }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var fileUri by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var askHttp by remember { mutableStateOf<Source?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // sem permissão persistente: funciona só nesta sessão
            }
            fileUri = uri.toString()
            fileName = uri.lastPathSegment ?: "ficheiro"
        }
    }

    fun submit() {
        error = null
        if (name.isBlank()) { error = "Indica um nome para a fonte."; return }
        when (type) {
            SourceType.XTREAM -> {
                if (server.isBlank() || user.isBlank() || pass.isBlank()) {
                    error = "Preenche o servidor, o utilizador e a palavra-passe."; return
                }
            }
            SourceType.M3U_URL -> {
                if (server.isBlank()) { error = "Indica o URL da lista M3U ou M3U8."; return }
            }
            SourceType.M3U_FILE -> {
                if (fileUri.isBlank()) { error = "Escolhe um ficheiro M3U."; return }
            }
        }
        val src = Source(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            type = type,
            server = if (type == SourceType.M3U_FILE) fileUri else server.trim(),
            user = user.trim(),
            password = pass.trim()
        )
        val plainHttp = type != SourceType.M3U_FILE && !server.trim().startsWith("https://")
        if (plainHttp) askHttp = src else vm.saveSource(src)
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.canGoBack()) {
                    CircleIconButton(AppIcons.Back, "Voltar", { vm.goBack() }, container = SurfHigh)
                    Spacer(Modifier.width(12.dp))
                } else {
                    LogoMark(40.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text("Adicionar fonte IPTV", style = MaterialTheme.typography.titleLarge)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SourceType.values().forEach { t ->
                    val sel = type == t
                    val shape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .tvFocus(shape)
                            .clip(shape)
                            .background(if (sel) Selected else SurfHigh)
                            .clickable { type = t; error = null }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t.label, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Nome da fonte") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )

            when (type) {
                SourceType.XTREAM -> {
                    OutlinedTextField(
                        value = server, onValueChange = { server = it },
                        label = { Text("URL do servidor") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        leadingIcon = { Icon(AppIcons.Link, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = user, onValueChange = { user = it },
                        label = { Text("Nome de utilizador") }, singleLine = true,
                        leadingIcon = { Icon(AppIcons.Person, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it },
                        label = { Text("Palavra-passe") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(AppIcons.Lock, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                    )
                }
                SourceType.M3U_URL -> {
                    OutlinedTextField(
                        value = server, onValueChange = { server = it },
                        label = { Text("URL da lista M3U ou M3U8") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        leadingIcon = { Icon(AppIcons.Link, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()
                    )
                }
                SourceType.M3U_FILE -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocus(RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surf)
                            .clickable { picker.launch(arrayOf("*/*")) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Folder, contentDescription = null, tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (fileName.isEmpty()) "Escolher ficheiro M3U..." else fileName,
                            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (type != SourceType.XTREAM) {
                Text(
                    "Uma lista M3U simples pode não disponibilizar filmes, séries ou dados de conta.",
                    color = TextSec, fontSize = 12.sp
                )
            }
            Text(
                "Usa apenas fontes e conteúdos a que tens direito de aceder. A palavra-passe e o endereço ficam guardados cifrados neste dispositivo.",
                color = TextSec, fontSize = 12.sp
            )

            (error ?: if (!vm.loading && vm.status.isNotEmpty() && vm.data.isEmpty()) vm.status else null)?.let {
                Text(it, color = ErrorC)
            }

            if (vm.loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Accent)
                    Spacer(Modifier.width(12.dp))
                    Text(vm.status, color = TextSec, modifier = Modifier.weight(1f))
                }
                GradientButton("Cancelar", { vm.cancelLoad() }, Modifier.fillMaxWidth(), danger = true)
            } else {
                GradientButton("Ligar e importar", { submit() }, Modifier.fillMaxWidth())
            }
        }
    }

    askHttp?.let { src ->
        AlertDialog(
            onDismissRequest = { askHttp = null },
            title = { Text("Ligação não cifrada") },
            text = {
                Text(
                    "Esta fonte usa HTTP, por isso o tráfego (incluindo o utilizador e a palavra-passe) não é cifrado. Queres continuar?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askHttp = null
                    vm.saveSource(src.copy(httpConsent = true))
                }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { askHttp = null }) { Text("Cancelar") } }
        )
    }
}

// =====================================================================
//  DEFINIÇÕES
// =====================================================================

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var confirmDelete by remember { mutableStateOf<Source?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("pt", "PT")) }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        ScreenTopBar("Definições", { vm.goBack() })
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Fontes", style = MaterialTheme.typography.titleMedium, color = Accent)
            vm.sources.forEach { s ->
                val active = vm.activeSource?.id == s.id
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surf).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(s.name + if (active) "  ·  ativa" else "", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        s.type.label + if (s.lastUpdate > 0L) " · atualizada " + fmt.format(Date(s.lastUpdate)) else "",
                        color = TextSec, fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!active) {
                            GradientButton("Usar", { vm.switchSource(s) })
                        } else {
                            GradientButton("Atualizar", { vm.refresh() }, enabled = !vm.loading)
                        }
                        GradientButton("Eliminar", { confirmDelete = s }, danger = true)
                    }
                }
            }
            GradientButton("Adicionar fonte", { vm.openAddSource() }, Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            Text("Privacidade", style = MaterialTheme.typography.titleMedium, color = Accent)
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surf).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Guardar histórico", color = Color.White)
                    Text("Guardado só neste dispositivo.", color = TextSec, fontSize = 12.sp)
                }
                Switch(checked = vm.historyEnabled, onCheckedChange = { vm.setHistoryEnabled(it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("Limpar histórico", { vm.clearHistory() }, enabled = vm.activeSource != null)
                GradientButton("Eliminar todos os dados", { confirmAll = true }, danger = true)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Eliminar fonte?") },
            text = { Text("A fonte \"${s.name}\", os favoritos e o histórico associados serão eliminados deste dispositivo.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSource(s.id); confirmDelete = null }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } }
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Eliminar todos os dados?") },
            text = { Text("Todas as fontes, favoritos, histórico e preferências serão eliminados deste dispositivo.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAllData(); confirmAll = false }) { Text("Eliminar tudo") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancelar") } }
        )
    }
}
