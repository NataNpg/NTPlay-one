package com.ntplayone.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
}

// Muitos provedores só respondem a um formato ou exigem UA de player conhecido.
private fun urlCandidates(url: String): List<String> {
    val out = LinkedHashSet<String>()
    out.add(url)
    if (url.startsWith("https://")) out.add("http://" + url.removePrefix("https://"))
    val noQuery = url.substringBefore('?')
    val query = url.substringAfter('?', "")
    val suffix = if (query.isNotEmpty()) "?$query" else ""
    val lastSeg = noQuery.substringAfterLast('/')
    // gateway: /play/<token>/ts e /play/<token>/m3u8 são o mesmo stream em formatos diferentes
    when (lastSeg.lowercase()) {
        "ts" -> out.add(noQuery.dropLast(2) + "m3u8$suffix")
        "m3u8" -> out.add(noQuery.dropLast(4) + "ts$suffix")
        "hls" -> out.add(noQuery.dropLast(3) + "m3u8$suffix")
    }
    val ext = lastSeg.substringAfterLast('.', "")
    if (ext.isEmpty() || ext.length > 5) {
        // URL sem extensão costuma servir HLS: tenta deixar isso explícito
        if (!lastSeg.contains('.')) out.add("$noQuery.m3u8$suffix")
        return out.toList()
    }
    val base = noQuery.dropLast(ext.length + 1)
    when (ext.lowercase()) {
        "m3u8" -> out.add("$base.ts$suffix")
        "ts" -> out.add("$base.m3u8$suffix")
        "mp4" -> out.add("$base.mkv$suffix")
        "mkv" -> out.add("$base.mp4$suffix")
        "avi" -> out.add("$base.mp4$suffix")
    }
    return out.toList()
}

private data class Attempt(val url: String, val hls: Boolean)

// Primeiro deixa o Media3 decidir; depois força HLS, que é o caso dos links de gateway.
private fun buildAttempts(url: String): List<Attempt> {
    val cands = urlCandidates(url)
    val out = ArrayList<Attempt>(cands.size * 2)
    for (c in cands) out.add(Attempt(c, false))
    for (c in cands) out.add(Attempt(c, true))
    return out
}

@Composable
fun PlayerScreen(app: AppModel, s: Screen.Player) {
    val ctx = LocalContext.current
    var activeUrl by remember(s.url) { mutableStateOf(s.url) }
    var activeTitle by remember(s.url) { mutableStateOf(s.title) }
    var showControls by remember { mutableStateOf(true) }
    var tick by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showChannels by remember { mutableStateOf(false) }
    // true quando o foco está em um botão dentro do player (não no vídeo)
    var childFocused by remember { mutableStateOf(false) }
    var panelFocused by remember { mutableStateOf(false) }
    var error by remember(s.url) { mutableStateOf("") }
    var attempt by remember(s.url) { mutableStateOf(0) }
    var retries by remember(s.url) { mutableStateOf(0) }
    var reload by remember(s.url) { mutableStateOf(0) }
    val overlayFocus = remember { FocusRequester() }
    val channelsFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val exitFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { overlayFocus.requestFocus() }
    }
    // O painel é criado na hora do clique: tenta de novo até o primeiro canal receber o foco.
    LaunchedEffect(showChannels) {
        if (showChannels) {
            showControls = true
            panelFocused = false
            repeat(12) {
                delay(70)
                runCatching { channelsFocus.requestFocus() }
                if (panelFocused) return@LaunchedEffect
            }
        } else {
            // O nó focado do painel é descartado: zera o estado e devolve o foco ao vídeo.
            childFocused = false
            panelFocused = false
            repeat(8) {
                delay(70)
                runCatching { overlayFocus.requestFocus() }
            }
        }
    }
    LaunchedEffect(showControls) {
        if (!showControls) {
            childFocused = false
            runCatching { overlayFocus.requestFocus() }
        }
    }
    // BackHandler declarado por último tem prioridade: sair < controles < painel.
    BackHandler(enabled = !showChannels && !showControls) {
        app.back()
    }
    BackHandler(enabled = showControls && !showChannels) {
        showControls = false
        tick++
    }
    BackHandler(enabled = showChannels) {
        showChannels = false
        tick++
    }

    val candidates = remember(activeUrl) { buildAttempts(activeUrl) }
    val progressKey = remember(activeUrl) {
        s.item?.key ?: app.progress.firstOrNull { it.url == activeUrl }?.key ?: activeUrl
    }
    val resumeAt = remember(activeUrl) {
        if (s.live) 0L else app.progress.firstOrNull { it.key == progressKey }
            ?.let { if (it.position > 5000 && it.position < it.duration - 1500) it.position else 0L } ?: 0L
    }

    val httpFactory = remember {
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
    }
    val player = remember(s.url) {
        ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(httpFactory))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                if (s.live) setWakeMode(C.WAKE_MODE_NETWORK)
            }
    }

    DisposableEffect(s.url) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                val next = attempt + 1
                when {
                    next < candidates.size -> {
                        attempt = next
                        error = "Ajustando conexão (${next + 1} de ${candidates.size})..."
                    }
                    retries < 3 -> {
                        retries++
                        attempt = 0
                        error = "Conexão falhou. Reconectando ($retries/3)..."
                        reload++
                    }
                    else -> error = "Não foi possível reproduzir.\nO canal pode estar fora do ar ou o link expirou."
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) error = ""
            }
        }
        player.addListener(listener)
        onDispose {
            runCatching {
                if (!s.live && player.duration > 0 && player.currentPosition > 5000) {
                    app.saveProgress(
                        Progress(
                            progressKey, s.item?.playlistId ?: "", s.item?.id ?: "",
                            activeTitle, s.item?.icon ?: "", s.item?.type ?: "movie", activeUrl,
                            player.currentPosition, player.duration, System.currentTimeMillis(),
                        )
                    )
                }
            }
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(activeUrl, attempt, reload) {
        if (reload > 0) delay(2500)
        val a = candidates.getOrElse(attempt) { Attempt(activeUrl, false) }
        val item = MediaItem.fromUri(a.url)
        val source = if (a.hls) {
            HlsMediaSource.Factory(httpFactory).setAllowChunklessPreparation(false).createMediaSource(item)
        } else {
            DefaultMediaSourceFactory(ctx).setDataSourceFactory(httpFactory).createMediaSource(item)
        }
        player.setMediaSource(source)
        if (resumeAt > 0 && attempt == 0) player.seekTo(resumeAt)
        player.prepare()
        player.playWhenReady = true
    }

    // Só precisa atualizar a barra quando os controles estão visíveis.
    LaunchedEffect(showControls) {
        while (showControls) {
            runCatching {
                position = player.currentPosition.coerceAtLeast(0)
                duration = player.duration.coerceAtLeast(0)
            }
            delay(500)
        }
    }
    // Não esconde os controles enquanto o controle remoto estiver navegando nos botões.
    LaunchedEffect(showControls, tick, childFocused, showChannels) {
        if (showControls && !childFocused && !showChannels) {
            delay(6000)
            showControls = false
        }
    }
    LaunchedEffect(activeUrl) {
        while (true) {
            delay(15000)
            runCatching {
                if (!s.live && player.isPlaying && player.duration > 0) {
                    app.saveProgress(
                        Progress(
                            progressKey, s.item?.playlistId ?: "", s.item?.id ?: "",
                            activeTitle, s.item?.icon ?: "", s.item?.type ?: "movie", activeUrl,
                            player.currentPosition, player.duration, System.currentTimeMillis(),
                        )
                    )
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Escopo de foco único do player: o vídeo é o alvo focável e os botões ficam
        // dentro dele, então o preview vê a tecla antes do botão e o OK chega no botão certo.
        Box(
            Modifier.fillMaxSize()
                .focusRequester(overlayFocus)
                .onFocusChanged { childFocused = it.hasFocus && !it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    // Com um botão focado, o botão trata o OK/D-pad.
                    val onVideo = !childFocused
                    when (e.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (!onVideo) {
                                false
                            } else {
                                showControls = !showControls
                                tick++
                                true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> if (onVideo && !s.live) {
                            player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                            showControls = true
                            tick++
                            true
                        } else {
                            tick++
                            false
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> if (onVideo && !s.live) {
                            player.seekTo(player.currentPosition + 10000)
                            showControls = true
                            tick++
                            true
                        } else {
                            tick++
                            false
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            player.playWhenReady = !player.playWhenReady
                            tick++
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            tick++
                            if (showChannels && onVideo) {
                                runCatching { channelsFocus.requestFocus() }
                                true
                            } else if (onVideo && showControls) {
                                runCatching { playFocus.requestFocus() }
                                true
                            } else false
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            tick++
                            if (showChannels && onVideo) {
                                runCatching { channelsFocus.requestFocus() }
                                true
                            } else if (onVideo && showControls) {
                                runCatching { exitFocus.requestFocus() }
                                true
                            } else false
                        }
                        else -> {
                            tick++
                            false
                        }
                    }
                }
                .focusable()
        ) {
        if (showControls) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassPill {
                    Image(painterResource(R.drawable.logo), null, Modifier.size(30.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("NTPlayone", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(activeTitle, color = TextHi, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.live) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(Pink, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text("AO VIVO", color = PinkLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        }
                    }
                }
                if (s.live) {
                    NpButton("Canais", { showChannels = !showChannels; tick++ }, primary = false)
                    Spacer(Modifier.width(12.dp))
                }
                NpButton("Voltar", { app.back() }, primary = false, focusRequester = exitFocus)
            }
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                if (!s.live && duration > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(position), color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = position.toFloat().coerceIn(0f, duration.toFloat()),
                            onValueChange = { player.seekTo(it.toLong()); tick++ },
                            valueRange = 0f..duration.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = Line),
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        )
                        Text(fmt(duration), color = TextLo, fontSize = 13.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NpButton(
                        if (playing) "Pausar" else "Assistir",
                        { player.playWhenReady = !player.playWhenReady; tick++ },
                        focusRequester = playFocus,
                    )
                    if (!s.live) {
                        NpButton("-10s", { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)); tick++ }, primary = false)
                        NpButton("+10s", { player.seekTo(player.currentPosition + 10000); tick++ }, primary = false)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            error.isNotEmpty() -> error.lineSequence().first()
                            playing -> "Reproduzindo"
                            else -> "Pausado"
                        },
                        color = if (error.isNotEmpty()) Amber else TextLo,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (error.isNotEmpty() && retries >= 3) {
            Column(
                Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xF20B0E24))
                    .border(1.dp, Brush.horizontalGradient(listOf(Cyan, Violet, Pink)), RoundedCornerShape(24.dp))
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error, color = TextHi, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                NpButton("Tentar novamente", {
                    error = ""
                    retries = 0
                    attempt = 0
                    reload++
                })
            }
        }
        if (showChannels && s.live) {
            val channels = app.liveItems
            Column(
                Modifier.align(Alignment.CenterEnd).width(340.dp).fillMaxHeight()
                    .background(Color(0xF20B0E24))
                    .border(1.dp, Line, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                    .padding(16.dp),
            ) {
                Text("Canais ao vivo", color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val firstKey = channels.firstOrNull()?.key
                    items(channels, key = { it.key }) { c ->
                        FocusBox(
                            {
                                activeUrl = c.url
                                activeTitle = c.name
                                attempt = 0
                                retries = 0
                                reload = 0
                                error = ""
                                showChannels = false
                                tick++
                            },
                            Modifier.fillMaxWidth().height(46.dp).onFocusChanged {
                                if (it.isFocused) panelFocused = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            bg = SolidColor(CardBg2),
                            focusRequester = if (c.key == firstKey) channelsFocus else null,
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (c.icon.isNotEmpty()) {
                                    AsyncImage(
                                        model = c.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(c.name, color = TextHi, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun GlassPill(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x660B0E24))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
