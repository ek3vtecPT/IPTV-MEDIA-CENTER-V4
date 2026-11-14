package pt.iptvplayer.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@Composable
fun PlayerScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    var controlsVisible by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf("") }   // "", "audio", "subs", "aspect", "rec"
    val focusRequester = remember { FocusRequester() }

    BackHandler { vm.leavePlayer() }

    // Ecrã inteiro, horizontal e sempre ligado enquanto o leitor está aberto
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        view.keepScreenOn = true
        val controller = activity?.window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val layout = remember { VLCVideoLayout(context) }
    // A ordem importa: primeiro liga a vista, depois arranca a reprodução.
    DisposableEffect(layout) {
        vm.attach(layout)
        onDispose { vm.detach() }
    }
    LaunchedEffect(vm.playToken) { vm.startCurrent() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        while (true) {
            vm.tick()
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.ChannelUp -> { vm.step(1); true }
                    Key.ChannelDown -> { vm.step(-1); true }
                    Key.MediaPlayPause -> { vm.togglePause(); true }
                    Key.DirectionCenter, Key.Enter, Key.DirectionUp, Key.DirectionDown,
                    Key.DirectionLeft, Key.DirectionRight ->
                        if (!controlsVisible) { controlsVisible = true; true } else false
                    else -> false
                }
            }
    ) {
        AndroidView(factory = { layout }, modifier = Modifier.fillMaxSize())

        // Camada transparente: um toque mostra/esconde os controlos
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    controlsVisible = !controlsVisible
                }
        )

        if (vm.buffering) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Accent)
        vm.errorMsg?.let {
            Text(
                it, color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC000000))
                    .padding(16.dp)
            )
        }

        RecIndicator(vm, controlsVisible, Modifier.align(Alignment.TopEnd))

        if (!vm.recording && vm.recStatus.isNotEmpty()) {
            Text(
                vm.recStatus, color = Color.White, fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ---- barra de cima ----
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIconButton(AppIcons.Back, "Voltar", { vm.leavePlayer() })
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            vm.currentName, color = Color.White, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (vm.isLive) Text("Programação indisponível", color = TextSec, fontSize = 12.sp)
                    }
                    CircleIconButton(AppIcons.Audio, "Faixa de áudio", { dialog = "audio" })
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(AppIcons.Subtitles, "Legendas", { dialog = "subs" })
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(AppIcons.Aspect, "Proporção do ecrã", { dialog = "aspect" })
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(
                        if (vm.recording) AppIcons.Stop else AppIcons.Record,
                        if (vm.recording) "Gravação em curso" else "Gravar",
                        { dialog = "rec" },
                        tint = ErrorC
                    )
                }

                // ---- centro ----
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (vm.hasChannelList()) {
                        CircleIconButton(AppIcons.SkipPrevious, "Anterior", { vm.step(-1) }, size = 52.dp)
                    }
                    Box(
                        modifier = Modifier
                            .tvFocus(CircleShape)
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(ButtonBrush)
                            .clickable { vm.togglePause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (vm.playing) AppIcons.Pause else AppIcons.Play,
                            contentDescription = if (vm.playing) "Pausa" else "Reproduzir",
                            tint = Color.White, modifier = Modifier.size(36.dp)
                        )
                    }
                    if (vm.hasChannelList()) {
                        CircleIconButton(AppIcons.SkipNext, "Seguinte", { vm.step(1) }, size = 52.dp)
                    }
                }

                // ---- barra de baixo ----
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!vm.isLive && vm.lengthMs > 0) {
                        Text(fmt(vm.timeMs), color = Color.White, fontSize = 13.sp)
                        Slider(
                            value = vm.progress,
                            onValueChange = { vm.seek(it) },
                            colors = SliderDefaults.colors(
                                thumbColor = Accent, activeTrackColor = Accent,
                                inactiveTrackColor = Color(0x55FFFFFF)
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                        )
                        Text(fmt(vm.lengthMs), color = Color.White, fontSize = 13.sp)
                    } else if (vm.isLive) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x66000000))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(ErrorC))
                            Spacer(Modifier.width(8.dp))
                            Text("AO VIVO", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "audio" -> {
            val tracks = remember { vm.audioTracks() }
            val current = remember { vm.currentAudio() }
            PanelDialog("Faixa de áudio", { dialog = "" }) {
                if (tracks.isEmpty()) Text("Sem faixas de áudio alternativas.", color = TextSec)
                tracks.forEach { t ->
                    OptionRow(t.second, t.first == current, { vm.setAudio(t.first); dialog = "" })
                }
            }
        }
        "subs" -> {
            val tracks = remember { vm.subTracks() }
            val current = remember { vm.currentSub() }
            PanelDialog("Legendas", { dialog = "" }) {
                if (tracks.isEmpty()) Text("Sem legendas neste conteúdo.", color = TextSec)
                tracks.forEach { t ->
                    OptionRow(t.second, t.first == current, { vm.setSub(t.first); dialog = "" })
                }
            }
        }
        "aspect" -> PanelDialog("Proporção do ecrã", { dialog = "" }) {
            ASPECTS.forEachIndexed { i, a ->
                OptionRow(a.first, vm.aspectIndex == i, { vm.setAspect(i); dialog = "" })
            }
        }
        "rec" -> RecordPanel(vm) { dialog = "" }
    }
}

@Composable
private fun RecIndicator(vm: AppViewModel, controlsVisible: Boolean, modifier: Modifier) {
    if (!vm.recording) return
    val transition = rememberInfiniteTransition(label = "rec")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recAlpha"
    )
    Row(
        modifier = modifier
            .padding(top = if (controlsVisible) 72.dp else 14.dp, end = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(ErrorC.copy(alpha = alpha)))
        Spacer(Modifier.width(8.dp))
        Text("REC  " + vm.recLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** O "submenu" de gravação: qualidade, formato, pasta e iniciar/parar. */
@Composable
private fun RecordPanel(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // se não der para persistir, continua a funcionar nesta sessão
            }
            vm.setRecFolder(uri)
        }
    }
    PanelDialog("Gravação", onDismiss) {
        Text("Qualidade", color = Accent, fontSize = 13.sp)
        REC_QUALITIES.forEachIndexed { i, q ->
            OptionRow(q.label, vm.recQuality == i, { vm.chooseQuality(i) })
        }
        Spacer(Modifier.height(4.dp))
        Text("Formato", color = Accent, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REC_FORMATS.forEachIndexed { i, f ->
                OptionRow(f.label, vm.recFormat == i, { vm.chooseFormat(i) }, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Pasta de destino", color = Accent, fontSize = 13.sp)
        OptionRow(vm.recFolderLabel(), false, { folderPicker.launch(null) })
        Text("Toca na pasta para a alterar.", color = TextSec, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        GradientButton(
            if (vm.recording) "Parar gravação" else "Iniciar gravação",
            {
                if (vm.recording) vm.stopRecording() else vm.startRecording()
                onDismiss()
            },
            Modifier.fillMaxWidth(),
            danger = vm.recording
        )
    }
}
