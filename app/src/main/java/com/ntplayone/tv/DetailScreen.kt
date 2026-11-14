package com.ntplayone.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

fun playEpisode(app: AppModel, p: Playlist?, item: Item, e: Episode) {
    val url = e.url.ifEmpty { if (p != null) app.episodeUrl(p, e) else "" }
    if (url.isEmpty()) {
        app.message = "Episódio sem link de vídeo."
        return
    }
    app.navigate(Screen.Player(url, "${seriesDisplayName(item)} · T${e.season} E${e.num}", item, e))
}

@Composable
fun DetailScreen(app: AppModel, item: Item) {
    val p = app.playlists.firstOrNull { it.id == item.playlistId }
    val isXtream = item.type == "series" && p?.kind == "xtream"
    var allEpisodes by remember(item.key) { mutableStateOf<List<Episode>>(emptyList()) }
    var loading by remember(item.key) { mutableStateOf(item.type == "series") }
    LaunchedEffect(item.key) {
        if (item.type == "series") {
            allEpisodes = withContext(Dispatchers.IO) {
                runCatching {
                    if (isXtream && p != null) app.episodes(p, item.id) else app.seriesEpisodesFor(item)
                }.getOrDefault(emptyList())
            }
        }
        loading = false
    }
    val seasons = remember(allEpisodes) { allEpisodes.map { it.season }.distinct().sorted() }
    var chosen by remember(item.key) { mutableStateOf(0) }
    val season = if (chosen in seasons) chosen else seasons.firstOrNull() ?: 0
    val eps = remember(allEpisodes, season) { allEpisodes.filter { it.season == season } }
    val title = if (item.type == "series") seriesDisplayName(item) else item.name
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { backFocus.requestFocus() }
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NpButton("Voltar", { app.back() }, primary = false, focusRequester = backFocus)
                Spacer(Modifier.width(16.dp))
                Text(title, color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Row {
                AsyncImage(
                    item.icon, null,
                    Modifier.width(150.dp).height(225.dp).clip(RoundedCornerShape(12.dp)).background(CardBg2),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOfNotNull(typeLabel(item.type), item.year.takeIf { it.isNotEmpty() }, item.rating.takeIf { it.isNotEmpty() }?.let { "nota $it" })
                            .joinToString(" · "),
                        color = Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(item.plot.ifEmpty { "Sem sinopse disponível." }, color = TextLo, fontSize = 15.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NpButton("Assistir", {
                            when (item.type) {
                                "series" -> {
                                    val first = eps.firstOrNull() ?: allEpisodes.firstOrNull()
                                    if (first != null) playEpisode(app, p, item, first)
                                    else if (item.url.isNotEmpty()) app.navigate(Screen.Player(item.url, title, item))
                                    else app.message = "Nenhum episódio disponível para esta série."
                                }
                                "movie" -> {
                                    if (item.url.isNotEmpty()) app.navigate(Screen.Player(item.url, title, item))
                                    else app.message = "Filme sem link de vídeo."
                                }
                                else -> {
                                    if (item.url.isNotEmpty()) app.navigate(Screen.Player(item.url, title, item, live = true))
                                    else app.message = "Canal sem link de vídeo."
                                }
                            }
                        })
                        NpButton(
                            if (item.key in app.favorites) "Favoritado" else "Favoritar",
                            { app.toggleFav(item.key) },
                            primary = false,
                        )
                    }
                }
            }
        }
        if (item.type == "series" && (loading || allEpisodes.isNotEmpty())) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
                    Spacer(Modifier.width(8.dp))
                    Text("TEMPORADAS", color = TextLo, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in seasons) {
                        Tab("Temporada $s", s == season, onClick = { chosen = s })
                    }
                }
            }
            if (loading) {
                item { CircularProgressIndicator(color = Cyan) }
            } else if (eps.isEmpty()) {
                item { Text("Nenhum episódio disponível.", color = TextLo, fontSize = 14.sp) }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(Accent))
                        Spacer(Modifier.width(8.dp))
                        Text("EPISÓDIOS", color = TextLo, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("${eps.size} na temporada $season", color = TextLo, fontSize = 11.sp)
                    }
                }
                items(eps, key = { "${it.season}:${it.num}:${it.id}" }) { e ->
                    FocusBox(
                        { playEpisode(app, p, item, e) },
                        Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        bg = SolidColor(CardBg),
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("T${e.season} E${e.num}", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(70.dp))
                            Text(
                                e.title.ifEmpty { "Episódio ${e.num}" },
                                color = TextHi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
