package com.ntplayone.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun CatButton(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusBox(
        onClick,
        Modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(10.dp),
        bg = if (selected) Accent else SolidColor(CardBg),
    ) {
        Text(
            label,
            color = if (selected) Color.White else TextLo,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp).align(Alignment.CenterStart),
        )
    }
}

@Composable
fun BrowseScreen(app: AppModel, type: String) {
    var cat by remember(type) { mutableStateOf("all") }
    val cats = remember(app.categories, type) { app.categories.filter { it.type == type } }
    val items = remember(app.dataVersion, type, cat) { app.itemsOf(type, cat) }
    val shown = remember(app.dataVersion, type, cat) {
        if (type == "series") items.groupBy { seriesKey(it.name) }.values.mapNotNull { it.firstOrNull() }
        else items
    }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { backFocus.requestFocus() }
    }
    val title = when (type) {
        "live" -> "TV ao vivo"
        "movie" -> "Filmes"
        else -> "Séries"
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NpButton("Voltar", { app.back() }, primary = false, focusRequester = backFocus)
            Spacer(Modifier.width(16.dp))
            Text(title, color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${shown.size} itens", color = TextLo, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.weight(1f)) {
            LazyColumn(Modifier.width(250.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { CatButton("Todas", cat == "all") { cat = "all" } }
                items(cats, key = { it.id + it.type }) { c ->
                    CatButton(c.name, cat == c.id) {
                        if (app.isAdultCategory(c.id)) app.requireAdult { cat = c.id } else cat = c.id
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(118.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(shown, key = { it.key }) { item ->
                    SeriesPoster(app, item, 118.dp) { openItem(app, item) }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(app: AppModel) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Item>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val qFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { qFocus.requestFocus() }
    }
    LaunchedEffect(q, app.dataVersion) {
        val term = q.trim()
        if (term.length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(250)
        val found = withContext(Dispatchers.Default) {
            val out = ArrayList<Item>(64)
            for (i in app.catalog) {
                if (i.name.contains(term, ignoreCase = true)) {
                    out.add(i)
                    if (out.size >= 300) break
                }
            }
            out
        }
        if (q.trim() == term) {
            results = found
            searching = false
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NpButton("Voltar", { app.back() }, primary = false)
            Spacer(Modifier.width(16.dp))
            TextField(
                value = q,
                onValueChange = { q = it },
                placeholder = { Text("Buscar canal, filme ou série...", color = TextLo) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CardBg,
                    unfocusedContainerColor = CardBg,
                    focusedTextColor = TextHi,
                    unfocusedTextColor = TextHi,
                    cursorColor = Cyan,
                    focusedIndicatorColor = Cyan,
                    unfocusedIndicatorColor = Line,
                ),
                modifier = Modifier.weight(1f).focusRequester(qFocus),
            )
        }
        Spacer(Modifier.height(16.dp))
        if (q.trim().length < 2) {
            Text("Digite pelo menos 2 letras para buscar.", color = TextLo, fontSize = 15.sp)
        } else {
            Text(if (searching) "Buscando..." else "${results.size} resultados", color = TextLo, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(118.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(results, key = { it.key }) { item ->
                    SeriesPoster(app, item, 118.dp) { openItem(app, item) }
                }
            }
        }
    }
}