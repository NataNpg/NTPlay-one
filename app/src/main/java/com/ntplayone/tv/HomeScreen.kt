package com.ntplayone.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

fun typeLabel(t: String) = when (t) {
    "live" -> "CANAL AO VIVO"
    "movie" -> "FILME"
    else -> "SÉRIE"
}

fun openItem(app: AppModel, item: Item) {
    val go: () -> Unit = {
        when (item.type) {
            "live" -> {
                if (item.url.isNotEmpty()) app.navigate(Screen.Player(item.url, item.name, item, live = true))
                else app.message = "Canal sem link de vídeo."
            }
            else -> app.navigate(Screen.Detail(item))
        }
    }
    if (app.isAdult(item)) app.requireAdult(go) else go()
}

@Composable
fun Tab(label: String, selected: Boolean, onClick: () -> Unit, focusRequester: FocusRequester? = null) {
    FocusBox(
        onClick,
        Modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        bg = if (selected) Accent else SolidColor(Glass),
        focusRequester = focusRequester,
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF060A1A) else TextLo,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
fun HomeScreen(app: AppModel) {
    val live = app.liveItems
    val movies = app.movieItems
    val seriesCards = app.seriesCards
    val favs = remember(app.dataVersion, app.favorites) {
        val out = ArrayList<Item>(app.favorites.size)
        for (i in app.catalog) if (i.key in app.favorites) out.add(i)
        out
    }
    val homeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { homeFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp)) {
        NpHeader {
            FocusBox(
                { app.navigate(Screen.Search) },
                Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
                bg = SolidColor(Glass),
            ) {
                Text(
                    "Buscar canal, filme ou série...",
                    color = TextLo,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart),
                )
            }
            Spacer(Modifier.width(14.dp))
            NpButton("+ Adicionar lista", { app.navigate(Screen.Lists) })
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tab("Início", true, {}, homeFocus)
            Tab("TV ao vivo", false, onClick = { app.navigate(Screen.Browse("live")) })
            Tab("Filmes", false, onClick = { app.navigate(Screen.Browse("movie")) })
            Tab("Séries", false, onClick = { app.navigate(Screen.Browse("series")) })
            Tab("Listas", false, onClick = { app.navigate(Screen.Lists) })
            Spacer(Modifier.weight(1f))
            if (app.catalog.isNotEmpty()) {
                Text(
                    "${live.size} canais · ${movies.size} filmes · ${seriesCards.size} séries",
                    color = TextLo,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ContactBanner() }
            item { Hero(app) }
            if (favs.isNotEmpty()) item { CardRow(app, "Favoritos", favs.take(40)) }
            if (live.isNotEmpty()) item { CardRow(app, "Canais ao vivo", live.take(40), "Ver tudo") { app.navigate(Screen.Browse("live")) } }
            if (movies.isNotEmpty()) item { CardRow(app, "Filmes", movies.take(40), "Ver tudo") { app.navigate(Screen.Browse("movie")) } }
            if (seriesCards.isNotEmpty()) item { CardRow(app, "Séries", seriesCards.take(40), "Ver tudo") { app.navigate(Screen.Browse("series")) } }
            if (app.catalog.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Cyan)
                        Spacer(Modifier.height(16.dp))
                        Text("Carregando conteúdo da lista...", color = TextLo, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun Hero(app: AppModel) {
    val pr = app.progress.firstOrNull()
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth().height(260.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1B2358), Color(0xFF2A1150), Color(0xFF0B1030))))
            .border(1.dp, Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.5f), Violet.copy(alpha = 0.4f), Pink.copy(alpha = 0.5f))), shape)
    ) {
        if (pr != null && pr.icon.isNotEmpty()) {
            AsyncImage(pr.icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xF2070A18), Color(0xCC070A18), Color.Transparent)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0x99070A18)))
            )
        )
        Column(
            Modifier.align(Alignment.CenterStart).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Accent)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    if (pr != null) "CONTINUE ASSISTINDO" else "BEM-VINDO",
                    color = Color(0xFF060A1A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                )
            }
            Text(
                pr?.name ?: "NTPlayone",
                color = TextHi, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (pr != null) "${pr.percent}% assistido · ${typeLabel(pr.type)}"
                else if (app.playlists.isEmpty()) "Adicione uma lista para começar"
                else "Escolha um canal, filme ou série para assistir",
                color = TextLo, fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pr != null) {
                    NpButton("Continuar assistindo", {
                        val item = app.catalog.firstOrNull { it.key == pr.key }
                        app.navigate(Screen.Player(pr.url, pr.name, item, live = pr.type == "live"))
                    })
                    NpButton(
                        if (pr.key in app.favorites) "Favoritado" else "Favoritar",
                        { app.toggleFav(pr.key) },
                        primary = false,
                    )
                } else {
                    NpButton("Ver canais ao vivo", { app.navigate(Screen.Browse("live")) })
                    if (app.playlists.isNotEmpty()) {
                        NpButton("Ver filmes", { app.navigate(Screen.Browse("movie")) }, primary = false)
                    }
                }
            }
        }
    }
}

@Composable
fun ContactBanner() {
    val bmp = remember { Qr.bitmap(WhatsAppUrl, 384) }
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF131D48), Color(0xFF29114F), Color(0xFF0B1030))))
            .border(1.dp, Brush.horizontalGradient(listOf(Cyan.copy(alpha = 0.6f), Violet.copy(alpha = 0.45f), Pink.copy(alpha = 0.6f))), shape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Accent)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "SUA LISTA EM 1 MINUTO",
                    color = Color(0xFF060A1A), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp,
                )
            }
            Text(
                "Sua TV inteira começa com um oi.",
                color = TextHi, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "Para solicitar sua lista, entre em contato pelo número $WhatsAppNumber",
                color = TextLo, fontSize = 15.sp,
            )
            Text(
                "Chame no WhatsApp e receba o teste do aplicativo ou da sua lista.",
                color = Cyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(132.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(8.dp)) {
                Image(bmp.asImageBitmap(), "QR code do WhatsApp $WhatsAppNumber", Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
            Text("WhatsApp $WhatsAppNumber", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CardRow(app: AppModel, title: String, items: List<Item>, action: String? = null, onAction: () -> Unit = {}) {
    Column {
        SectionTitle(title, action, onAction)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.key }) { item ->
                SeriesPoster(app, item, 118.dp) { openItem(app, item) }
            }
        }
    }
}