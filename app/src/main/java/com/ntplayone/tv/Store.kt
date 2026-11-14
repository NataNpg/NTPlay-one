package com.ntplayone.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.JsonReader
import android.util.JsonToken
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AppModel(val ctx: Context) {
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
    var activeId by mutableStateOf("")
    private var _catalog: List<Item> = emptyList()
    var catalog: List<Item>
        get() = _catalog
        set(value) {
            _catalog = value
            applyIndex(value)
        }
    // listas por tipo já prontas: evita varrer o catálogo inteiro a cada composição
    var liveItems by mutableStateOf<List<Item>>(emptyList())
        private set
    var movieItems by mutableStateOf<List<Item>>(emptyList())
        private set
    var seriesCards by mutableStateOf<List<Item>>(emptyList())
        private set
    var seriesByKey by mutableStateOf<Map<String, List<Item>>>(emptyMap())
        private set
    private var catIndex by mutableStateOf<Map<String, Map<String, List<Item>>>>(emptyMap())
    // muda só quando o catálogo é trocado; serve de chave estável para remember()
    var dataVersion by mutableStateOf(0)
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
    var favorites by mutableStateOf<Set<String>>(emptySet())
    var progress by mutableStateOf<List<Progress>>(emptyList())
    var loading by mutableStateOf(false)
    var message by mutableStateOf("")
    var screen by mutableStateOf<Screen>(Screen.Setup)
    var pairUrl by mutableStateOf("")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val main = Handler(Looper.getMainLooper())
    private val stack = mutableListOf<Screen>()

    val active: Playlist? get() = playlists.firstOrNull { it.id == activeId }

    // Uma única passada no catálogo monta tudo o que as telas consomem.
    private fun applyIndex(items: List<Item>) {
        if (items.isEmpty()) {
            liveItems = emptyList()
            movieItems = emptyList()
            seriesCards = emptyList()
            seriesByKey = emptyMap()
            catIndex = emptyMap()
            dataVersion++
            return
        }
        val live = ArrayList<Item>()
        val movies = ArrayList<Item>()
        val series = HashMap<String, MutableList<Item>>()
        val byType = HashMap<String, HashMap<String, MutableList<Item>>>()
        for (i in items) {
            when (i.type) {
                "live" -> live.add(i)
                "movie" -> movies.add(i)
                "series" -> series.getOrPut(seriesKey(i.name)) { mutableListOf() }.add(i)
            }
            if (i.categoryId.isNotEmpty()) {
                byType.getOrPut(i.type) { HashMap() }
                    .getOrPut(i.categoryId) { mutableListOf() }.add(i)
            }
        }
        liveItems = live
        movieItems = movies
        seriesByKey = series
        seriesCards = series.values.mapNotNull { it.firstOrNull() }
        catIndex = byType
        dataVersion++
    }

    fun allOf(type: String): List<Item> = when (type) {
        "live" -> liveItems
        "movie" -> movieItems
        "series" -> seriesCards
        else -> emptyList()
    }

    fun itemsOf(type: String, categoryId: String): List<Item> =
        if (categoryId == "all") allOf(type) else catIndex[type]?.get(categoryId) ?: emptyList()

    fun seriesEpisodesFor(item: Item): List<Episode> {
        val key = seriesKey(item.name)
        // episódios ficam fora do cache principal: carrega só o grupo da série aberta
        val fromFile = readEpisodeGroup(item.playlistId, key)
        if (fromFile != null) return m3uEpisodes(fromFile)
        val group = seriesByKey[key] ?: return emptyList()
        return m3uEpisodes(group)
    }

    private var episodesFile: RandomAccessFile? = null
    private var episodesIndex: HashMap<String, LongArray>? = null
    private var episodesOwner: String? = null

    private fun closeEpisodes() {
        runCatching { episodesFile?.close() }
        episodesFile = null
        episodesIndex = null
        episodesOwner = null
    }

    @Synchronized
    private fun ensureEpisodesIndex(playlistId: String): Boolean {
        if (episodesOwner == playlistId && episodesIndex != null) return true
        closeEpisodes()
        val f = file("episodes_$playlistId.dat")
        if (!f.exists()) return false
        return try {
            val raf = RandomAccessFile(f, "r")
            if (raf.length() < 12) {
                raf.close()
                return false
            }
            raf.seek(raf.length() - 8)
            val indexPos = raf.readLong()
            raf.seek(indexPos)
            val n = raf.readInt()
            if (n < 0 || n > 1_000_000) {
                raf.close()
                return false
            }
            val idx = HashMap<String, LongArray>(n * 2)
            repeat(n) {
                val k = readStrRaf(raf)
                idx[k] = longArrayOf(raf.readLong(), raf.readInt().toLong())
            }
            episodesFile = raf
            episodesIndex = idx
            episodesOwner = playlistId
            true
        } catch (e: Exception) {
            closeEpisodes()
            false
        }
    }

    @Synchronized
    private fun readEpisodeGroup(playlistId: String, key: String): List<Item>? {
        if (!ensureEpisodesIndex(playlistId)) return null
        val meta = episodesIndex?.get(key) ?: return emptyList()
        val raf = episodesFile ?: return emptyList()
        return try {
            raf.seek(meta[0])
            val n = raf.readInt()
            if (n < 0 || n > 100_000) return emptyList()
            val out = ArrayList<Item>(n)
            repeat(n) {
                val iid = readStrRaf(raf)
                val name = readStrRaf(raf)
                val icon = readStrRaf(raf)
                val cat = readStrRaf(raf)
                val url = readStrRaf(raf)
                out.add(Item("$playlistId:series:$iid", playlistId, iid, name, "series", icon, cat, url = url))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun readStrRaf(raf: RandomAccessFile): String {
        val n = raf.readInt()
        if (n <= 0) return ""
        val b = ByteArray(n)
        raf.readFully(b)
        return String(b, Charsets.UTF_8)
    }

    fun navigate(s: Screen) {
        stack.add(screen)
        screen = s
    }

    fun back() {
        screen = stack.removeLastOrNull() ?: if (playlists.isEmpty()) Screen.Setup else Screen.Home
    }

    fun goHome() {
        stack.clear()
        screen = if (playlists.isEmpty()) Screen.Setup else Screen.Home
    }

    private fun file(n: String) = File(ctx.filesDir, n)
    private fun read(n: String) = runCatching { file(n).readText() }.getOrDefault("")
    private fun write(n: String, t: String) {
        runCatching { file(n).writeText(t) }
    }

    fun load() {
        playlists = runCatching {
            val a = JSONArray(read("playlists.json"))
            (0 until a.length()).map { Playlist.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        activeId = read("active.txt").ifBlank { playlists.firstOrNull()?.id ?: "" }
        if (activeId.isNotEmpty() && playlists.none { it.id == activeId }) activeId = playlists.firstOrNull()?.id ?: ""
        favorites = runCatching {
            val a = JSONArray(read("favs.json"))
            (0 until a.length()).map { a.getString(it) }.toSet()
        }.getOrDefault(emptySet())
        progress = runCatching {
            val a = JSONArray(read("progress.json"))
            (0 until a.length()).map { Progress.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        if (playlists.isNotEmpty()) {
            screen = Screen.Home
            scope.launch {
                loadCache(activeId)
                active?.let { p -> if (shouldAutoRefresh(p.id)) runCatching { refresh(p) } }
            }
        }
    }

    // Listas gigantes demoram para baixar: só atualiza sozinho quando o cache falhou
    // ou quando é pequeno e está velho. A atualização manual fica em "Listas".
    private fun shouldAutoRefresh(id: String): Boolean {
        val bin = file("catalog_$id.dat")
        val f = if (bin.exists()) bin else file("catalog_$id.json")
        if (!f.exists()) return true
        val age = System.currentTimeMillis() - f.lastModified()
        return age > DAY_MS && _catalog.size < 20_000
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val CACHE_MAGIC = 0x4E545031
        const val EPISODES_MAGIC = 0x4E545032
        const val PLOT_MAX = 800
    }

    private fun cut(s: String) = if (s.length <= PLOT_MAX) s else s.substring(0, PLOT_MAX)

    // rede instável em TV box: tenta duas vezes antes de desistir
    private inline fun <T> twice(f: () -> T): T {
        var last: Throwable? = null
        for (i in 0 until 2) {
            try {
                return f()
            } catch (e: OutOfMemoryError) {
                throw e
            } catch (e: Throwable) {
                last = e
            }
        }
        throw last ?: IllegalStateException("falha de rede")
    }

    private fun liveCount(items: List<Item>): Int {
        var n = 0
        for (i in items) if (i.type == "live") n++
        return n
    }

    private fun savePlaylists() = write("playlists.json", JSONArray(playlists.map { it.toJson() }).toString())

    private fun readStr(r: JsonReader): String = when (r.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
        JsonToken.NULL -> { r.nextNull(); "" }
        JsonToken.BOOLEAN -> r.nextBoolean().toString()
        else -> { r.skipValue(); "" }
    }

    private fun loadCache(id: String) {
        if (id.isEmpty()) return
        val bin = file("catalog_$id.dat")
        if (bin.exists()) {
            val ok = runCatching { readCacheBin(id, bin) }.getOrDefault(false)
            if (ok) {
                splitIfNeeded(id)
                return
            }
            bin.delete()
        }
        // cache antigo em JSON: lê uma vez e converte para binário
        val json = file("catalog_$id.json")
        if (!json.exists()) return
        try {
            val (items, cats) = readCacheJson(id, json)
            val compact = saveCache(id, items, cats)
            if (id == activeId) catalog = compact
            json.delete()
        } catch (e: OutOfMemoryError) {
            if (id == activeId) {
                catalog = emptyList()
                categories = emptyList()
            }
        } catch (e: Exception) {
            // cache corrompido ou incompleto: a próxima atualização regrava
        }
    }

    // cache antigo (com episódios dentro) -> separa em catálogo enxuto + episódios
    private fun splitIfNeeded(id: String) {
        val p = playlists.firstOrNull { it.id == id } ?: return
        if (p.kind == "xtream") return
        if (file("episodes_$id.dat").exists()) return
        val all = _catalog
        var seriesCount = 0
        for (i in all) if (i.type == "series") seriesCount++
        if (seriesCount <= seriesCards.size) return
        val compact = saveCache(id, all, categories)
        if (id == activeId) catalog = compact
    }

    private fun readCacheBin(id: String, f: File): Boolean {
        val items = ArrayList<Item>(4096)
        val buf = ByteArray(1024)
        var cats: List<Category> = emptyList()
        var published = false
        DataInputStream(BufferedInputStream(f.inputStream(), 1 shl 16)).use { i ->
            if (i.readInt() != CACHE_MAGIC) return false
            val n = i.readInt()
            if (n < 0 || n > 5_000_000) return false
            repeat(n) {
                val key = readStr(i, buf)
                val pid = readStr(i, buf)
                val iid = readStr(i, buf)
                val name = readStr(i, buf)
                val type = readStr(i, buf)
                val icon = readStr(i, buf)
                val cat = readStr(i, buf)
                val plot = readStr(i, buf)
                val year = readStr(i, buf)
                val rating = readStr(i, buf)
                val ext = readStr(i, buf)
                val url = readStr(i, buf)
                // os canais vêm primeiro: deixa a TV utilizável antes de ler o resto
                if (!published && type != "live" && items.isNotEmpty() && id == activeId) {
                    catalog = ArrayList(items)
                    published = true
                }
                items.add(Item(key, pid, iid, name, type, icon, cat, plot, year, rating, ext, url))
            }
            val cn = i.readInt()
            if (cn < 0 || cn > 100_000) return false
            val list = ArrayList<Category>(cn)
            repeat(cn) { list.add(Category(readStr(i, buf), readStr(i, buf), readStr(i, buf))) }
            cats = list
        }
        if (id == activeId) {
            catalog = items
            categories = cats
        }
        return true
    }

    private fun readCacheJson(id: String, f: File): Pair<List<Item>, List<Category>> {
        val items = ArrayList<Item>(1024)
        var cats: List<Category> = emptyList()
        f.bufferedReader().use { reader ->
            JsonReader(reader).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "items" -> {
                            r.beginArray()
                            while (r.hasNext()) {
                                var key = ""; var pid = ""; var iid = ""; var name = ""; var type = ""
                                var icon = ""; var cat = ""; var plot = ""; var year = ""; var rating = ""
                                var ext = ""; var url = ""
                                r.beginObject()
                                while (r.hasNext()) {
                                    when (r.nextName()) {
                                        "key" -> key = readStr(r)
                                        "playlistId" -> pid = readStr(r)
                                        "id" -> iid = readStr(r)
                                        "name" -> name = readStr(r)
                                        "type" -> type = readStr(r)
                                        "icon" -> icon = readStr(r)
                                        "categoryId" -> cat = readStr(r)
                                        "plot" -> plot = cut(readStr(r))
                                        "year" -> year = readStr(r)
                                        "rating" -> rating = readStr(r)
                                        "ext" -> ext = readStr(r)
                                        "url" -> url = readStr(r)
                                        else -> r.skipValue()
                                    }
                                }
                                r.endObject()
                                items.add(Item(key, pid, iid, name, type, icon, cat, plot, year, rating, ext, url))
                            }
                            r.endArray()
                        }
                        "cats" -> {
                            val list = ArrayList<Category>()
                            r.beginArray()
                            while (r.hasNext()) {
                                var cid = ""; var cname = ""; var ctype = ""
                                r.beginObject()
                                while (r.hasNext()) {
                                    when (r.nextName()) {
                                        "id" -> cid = readStr(r)
                                        "name" -> cname = readStr(r)
                                        "type" -> ctype = readStr(r)
                                        else -> r.skipValue()
                                    }
                                }
                                r.endObject()
                                list.add(Category(cid, cname, ctype))
                            }
                            r.endArray()
                            cats = list
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
        }
        if (id == activeId) {
            catalog = items
            categories = cats
        }
        return items to cats
    }

    private fun writeStr(o: DataOutputStream, s: String): Int {
        val b = s.toByteArray(Charsets.UTF_8)
        o.writeInt(b.size)
        o.write(b)
        return 4 + b.size
    }

    private fun readStr(i: DataInputStream, buf: ByteArray): String {
        val n = i.readInt()
        if (n <= 0) return ""
        if (n <= buf.size) {
            i.readFully(buf, 0, n)
            return String(buf, 0, n, Charsets.UTF_8)
        }
        val b = ByteArray(n)
        i.readFully(b)
        return String(b, Charsets.UTF_8)
    }

    // cache principal: canais, filmes e uma capa por série (episódios vão para outro arquivo)
    private fun saveCache(id: String, items: List<Item>, cats: List<Category>): List<Item> {
        val kind = playlists.firstOrNull { it.id == id }?.kind
        val split = kind != null && kind != "xtream"
        val live = ArrayList<Item>()
        val movies = ArrayList<Item>()
        val cards = ArrayList<Item>()
        val groups = if (split) LinkedHashMap<String, MutableList<Item>>() else null
        for (i in items) {
            when (i.type) {
                "live" -> live.add(i)
                "movie" -> movies.add(i)
                "series" -> if (groups != null) {
                    groups.getOrPut(seriesKey(i.name)) { mutableListOf() }.add(i)
                } else cards.add(i)
            }
        }
        if (groups != null) {
            for (g in groups.values) g.firstOrNull()?.let { cards.add(it) }
            saveEpisodes(id, groups)
        } else {
            file("episodes_$id.dat").delete()
        }
        val compact = ArrayList<Item>(live.size + movies.size + cards.size)
        compact.addAll(live)
        compact.addAll(movies)
        compact.addAll(cards)
        runCatching {
            val target = file("catalog_$id.dat")
            val tmp = file("catalog_$id.tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { o ->
                o.writeInt(CACHE_MAGIC)
                o.writeInt(compact.size)
                for (i in compact) {
                    writeStr(o, i.key)
                    writeStr(o, i.playlistId)
                    writeStr(o, i.id)
                    writeStr(o, i.name)
                    writeStr(o, i.type)
                    writeStr(o, i.icon)
                    writeStr(o, i.categoryId)
                    writeStr(o, cut(i.plot))
                    writeStr(o, i.year)
                    writeStr(o, i.rating)
                    writeStr(o, i.ext)
                    writeStr(o, i.url)
                }
                o.writeInt(cats.size)
                for (c in cats) {
                    writeStr(o, c.id)
                    writeStr(o, c.name)
                    writeStr(o, c.type)
                }
            }
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
        return compact
    }

    // agrupado por série com índice no fim: abrir uma série lê só o grupo dela
    private fun saveEpisodes(id: String, groups: Map<String, List<Item>>) {
        runCatching {
            val target = file("episodes_$id.dat")
            if (groups.isEmpty()) {
                target.delete()
                return
            }
            val tmp = file("episodes_$id.tmp")
            var pos = 8L
            val keys = ArrayList<String>(groups.size)
            val offsets = ArrayList<LongArray>(groups.size)
            DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { o ->
                o.writeInt(EPISODES_MAGIC)
                o.writeInt(groups.size)
                for ((k, list) in groups) {
                    keys.add(k)
                    offsets.add(longArrayOf(pos, list.size.toLong()))
                    o.writeInt(list.size)
                    pos += 4
                    for (i in list) {
                        pos += writeStr(o, i.id)
                        pos += writeStr(o, i.name)
                        pos += writeStr(o, i.icon)
                        pos += writeStr(o, i.categoryId)
                        pos += writeStr(o, i.url)
                    }
                }
                val indexPos = pos
                o.writeInt(groups.size)
                for (j in keys.indices) {
                    writeStr(o, keys[j])
                    o.writeLong(offsets[j][0])
                    o.writeInt(offsets[j][1].toInt())
                }
                o.writeLong(indexPos)
            }
            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            closeEpisodes()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // credenciais vão no path do stream; precisam de escape para % / ? # e espaços
    private fun seg(s: String) = android.net.Uri.encode(s)

    private fun api(p: Playlist, action: String, extra: String = "") =
        "${p.server}/player_api.php?username=${enc(p.user)}&password=${enc(p.pass)}&action=$action$extra"

    fun addXtream(name: String, serverRaw: String, user: String, pass: String): String? {
        var server = serverRaw.trim().trimEnd('/')
        val userT = user.trim()
        val passT = pass.trim()
        if (server.isEmpty() || userT.isEmpty() || passT.isEmpty()) return "Preencha servidor, usuário e senha."
        if (!server.startsWith("http://") && !server.startsWith("https://")) server = "http://$server"
        return try {
            val auth = JSONObject(Net.get("$server/player_api.php?username=${enc(userT)}&password=${enc(passT)}"))
            val ui = auth.optJSONObject("user_info") ?: return "O servidor não respondeu como Xtream Codes."
            if (ui.optInt("auth", 0) != 1) return "Usuário ou senha inválidos."
            val exp = ui.optLong("exp", 0)
            val expires = if (exp > 0) SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(exp * 1000)) else ""
            val p = Playlist(UUID.randomUUID().toString(), name.ifBlank { "Xtream" }, "xtream", server, userT, passT, expires = expires)
            val (items, cats) = fetchXtream(p)
            if (items.isEmpty()) return "A lista não retornou nenhum conteúdo."
            playlists = playlists + p
            activeId = p.id
            savePlaylists()
            write("active.txt", p.id)
            val stored = saveCache(p.id, items, cats)
            catalog = stored
            categories = cats
            val noLive = liveCount(items) == 0
            main.post {
                goHome()
                message = if (noLive) "Lista adicionada, mas o servidor não enviou canais ao vivo."
                else "Lista adicionada: ${p.name}"
            }
            null
        } catch (e: OutOfMemoryError) {
            "Memória insuficiente para esta lista. Tente usar uma lista menor."
        } catch (e: Exception) {
            "Falha ao conectar: ${e.message}"
        }
    }

    fun fetchXtream(p: Playlist): Pair<List<Item>, List<Category>> {
        val cats = mutableListOf<Category>()
        for ((type, action) in listOf("live" to "get_live_categories", "movie" to "get_vod_categories", "series" to "get_series_categories")) {
            try {
                val a = JSONArray(twice { Net.get(api(p, action), 60000) })
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    cats.add(Category(o.optString("category_id"), o.optString("category_name"), type))
                }
            } catch (e: Exception) {
            }
        }
        val items = ArrayList<Item>(2048)
        val seen = HashSet<String>()
        try {
            twice {
            Net.open(api(p, "get_live_streams"), 60000).reader(Charsets.UTF_8).use { input ->
                JsonReader(input).use { r ->
                    r.beginArray()
                    while (r.hasNext()) {
                        var id = ""; var name = ""; var icon = ""; var cat = ""
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "stream_id" -> id = readStr(r)
                                "name" -> name = readStr(r)
                                "stream_icon" -> icon = readStr(r)
                                "category_id" -> cat = readStr(r)
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        if (id.isEmpty()) continue
                        val key = "${p.id}:live:$id"
                        if (seen.add(key)) {
                            items.add(
                                Item(key, p.id, id, name, "live", icon = icon, categoryId = cat,
                                    url = "${p.server}/live/${seg(p.user)}/${seg(p.pass)}/$id.m3u8")
                            )
                        }
                    }
                    r.endArray()
                }
            }
            }
        } catch (e: Exception) {
        }
        try {
            twice {
            Net.open(api(p, "get_vod_streams"), 60000).reader(Charsets.UTF_8).use { input ->
                JsonReader(input).use { r ->
                    r.beginArray()
                    while (r.hasNext()) {
                        var id = ""; var name = ""; var icon = ""; var cat = ""; var plot = ""
                        var rating = ""; var year = ""; var ext = "mp4"
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "stream_id" -> id = readStr(r)
                                "name" -> name = readStr(r)
                                "stream_icon" -> icon = readStr(r)
                                "category_id" -> cat = readStr(r)
                                "plot" -> plot = readStr(r)
                                "rating" -> rating = readStr(r)
                                "year" -> year = readStr(r)
                                "container_extension" -> ext = readStr(r).ifBlank { "mp4" }
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        if (id.isEmpty()) continue
                        val key = "${p.id}:movie:$id"
                        if (seen.add(key)) {
                            items.add(
                                Item(key, p.id, id, name, "movie", icon = icon, categoryId = cat,
                                    plot = cut(plot), year = year, rating = rating, ext = ext,
                                    url = "${p.server}/movie/${seg(p.user)}/${seg(p.pass)}/$id.$ext")
                            )
                        }
                    }
                    r.endArray()
                }
            }
            }
        } catch (e: Exception) {
        }
        try {
            twice {
            Net.open(api(p, "get_series"), 60000).reader(Charsets.UTF_8).use { input ->
                JsonReader(input).use { r ->
                    r.beginArray()
                    while (r.hasNext()) {
                        var id = ""; var name = ""; var icon = ""; var cat = ""; var plot = ""
                        var year = ""; var rating = ""
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "series_id" -> id = readStr(r)
                                "name" -> name = readStr(r)
                                "cover" -> icon = readStr(r)
                                "category_id" -> cat = readStr(r)
                                "plot" -> plot = readStr(r)
                                "releaseDate" -> year = readStr(r).take(4)
                                "rating" -> rating = readStr(r)
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        if (id.isEmpty()) continue
                        val key = "${p.id}:series:$id"
                        if (seen.add(key)) {
                            items.add(
                                Item(key, p.id, id, name, "series", icon = icon, categoryId = cat,
                                    plot = cut(plot), year = year, rating = rating)
                            )
                        }
                    }
                    r.endArray()
                }
            }
            }
        } catch (e: Exception) {
        }
        return items to cats
    }

    fun episodes(p: Playlist, seriesId: String): List<Episode> {
        val o = JSONObject(Net.get(api(p, "get_series_info", "&series_id=$seriesId")))
        val ep = o.optJSONObject("episodes") ?: return emptyList()
        val out = mutableListOf<Episode>()
        for (seasonKey in ep.keys()) {
            val a = ep.optJSONArray(seasonKey) ?: continue
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i)
                out.add(
                    Episode(e.optString("id"), seasonKey.toIntOrNull() ?: 1, e.optInt("episode_num"),
                        e.optString("title"), e.optString("container_extension", "mp4"),
                        e.optJSONObject("info")?.optString("plot") ?: "")
                )
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.num }))
    }

    fun episodeUrl(p: Playlist, e: Episode) = "${p.server}/series/${seg(p.user)}/${seg(p.pass)}/${e.id}.${e.ext}"

    private val logoRe = Regex("tvg-logo=\"([^\"]*)\"")
    private val groupRe = Regex("group-title=\"([^\"]*)\"")
    private val vodExts = setOf("mp4", "mkv", "avi", "m4v", "mov", "wmv", "flv", "webm", "mpg", "mpeg", "3gp", "ogv")

    private fun m3uType(group: String, url: String, name: String): String {
        val g = group.lowercase(Locale.ROOT)
        val u = url.lowercase(Locale.ROOT)
        val ext = u.substringBefore('?').substringAfterLast('.', "")
        // URLs de gateway (/play/<token>/ts) não dizem o tipo; só o grupo e o nome dizem
        val seriesPath = u.contains("/series/")
        val vodPath = u.contains("/movie/") || u.contains("/vod/")
        val moviePath = vodPath || ext in vodExts
        if (seriesTag(name) != null || seriesPath) return "series"
        val seriesWord = g.contains("serie") || g.contains("série") || g.contains("novela") ||
            g.contains("temporada") || g.contains("anime")
        val movieWord = g.contains("filme") || g.contains("movie") || g.contains("vod") ||
            g.contains("cinema") || g.contains("lançamento") || g.contains("lancamento")
        // grupos de canais ("CANAIS", "24 HORAS", "ESPORTES") vencem o ".mp4" do link
        val liveWord = g.contains("canal") || g.contains("canais") || g.contains("ao vivo") ||
            g.contains("24h") || g.contains("24 horas") || g.contains("abert") ||
            g.contains("esporte") || g.contains("notici") || g.contains("infantil") ||
            g.contains("religios") || g.contains("documentar")
        if (liveWord && !seriesWord && !vodPath) return "live"
        // grupos mistos ("FILMES E SERIES") costumam trazer canais; episódio/URL mandam mais que o grupo
        if (seriesWord && !movieWord) return "series"
        if (moviePath) return "movie"
        if (movieWord && !seriesWord) return "movie"
        return "live"
    }

    private fun parseM3u(pid: String, input: InputStream): Pair<List<Item>, List<Category>> {
        val items = ArrayList<Item>(2048)
        val cats = mutableListOf<Category>()
        val catByGroup = HashMap<String, String>()
        var logo = ""
        var group = ""
        var cname = ""
        BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16).use { br ->
            while (true) {
                val line = br.readLine() ?: break
                val l = line.trim()
                if (l.startsWith("#EXTINF")) {
                    logo = logoRe.find(l)?.groupValues?.get(1) ?: ""
                    group = groupRe.find(l)?.groupValues?.get(1) ?: ""
                    cname = l.substringAfterLast(",").trim()
                } else if (l.isNotEmpty() && !l.startsWith("#") && cname.isNotEmpty()) {
                    val id = items.size.toString()
                    val type = m3uType(group, l, cname)
                    if (group.isNotEmpty()) {
                        val catId = catByGroup.getOrPut("$type\u0000$group") {
                            val cid = "$type:$group".hashCode().toString()
                            cats.add(Category(cid, group, type))
                            cid
                        }
                        items.add(Item("$pid:$type:$id", pid, id, cname, type, icon = logo, categoryId = catId, url = l))
                    } else {
                        items.add(Item("$pid:$type:$id", pid, id, cname, type, icon = logo, url = l))
                    }
                    cname = ""
                }
            }
        }
        return items to cats
    }

    fun addM3u(name: String, url: String): String? {
        val u = url.trim()
        if (u.isEmpty()) return "Informe o link da lista."
        return try {
            val pid = UUID.randomUUID().toString()
            val (items, cats) = twice { Net.open(u, 60000).use { parseM3u(pid, it) } }
            if (items.isEmpty()) return "Nenhum canal encontrado no link."
            val p = Playlist(pid, name.ifBlank { "M3U" }, "m3u", url = u)
            playlists = playlists + p
            activeId = pid
            savePlaylists()
            write("active.txt", pid)
            val stored = saveCache(pid, items, cats)
            catalog = stored
            categories = cats
            val noLive = liveCount(items) == 0
            main.post {
                goHome()
                message = if (noLive) "Lista adicionada, mas não encontrei canais ao vivo nela."
                else "Lista adicionada: ${p.name}"
            }
            null
        } catch (e: OutOfMemoryError) {
            "Memória insuficiente para esta lista. Tente usar uma lista menor."
        } catch (e: Exception) {
            "Falha ao baixar a lista: ${e.message}"
        }
    }

    fun refresh(p: Playlist): String? = try {
        val isActive = p.id == activeId
        val (items, cats) = if (p.kind == "xtream") fetchXtream(p)
        else twice { Net.open(p.url, 60000).use { parseM3u(p.id, it) } }
        if (items.isEmpty()) {
            if (isActive) loadCache(p.id)
            "A lista voltou vazia."
        } else null.also {
            val stored = saveCache(p.id, items, cats)
            if (isActive) {
                catalog = stored
                categories = cats
            }
            val noLive = isActive && liveCount(items) == 0
            main.post {
                message = if (noLive) "Lista atualizada, mas sem canais ao vivo."
                else "Lista atualizada: ${p.name}"
            }
        }
    } catch (e: OutOfMemoryError) {
        if (p.id == activeId) loadCache(p.id)
        "Memória insuficiente ao atualizar a lista."
    } catch (e: Exception) {
        if (p.id == activeId) loadCache(p.id)
        "Falha ao atualizar: ${e.message}"
    }

    fun setActive(id: String) {
        activeId = id
        write("active.txt", id)
        catalog = emptyList()
        categories = emptyList()
        goHome()
        scope.launch {
            loadCache(id)
            playlists.firstOrNull { it.id == id }?.let { p ->
                if (shouldAutoRefresh(p.id)) runCatching { refresh(p) }
            }
        }
    }

    fun removePlaylist(p: Playlist) {
        playlists = playlists.filter { it.id != p.id }
        file("catalog_${p.id}.dat").delete()
        file("catalog_${p.id}.json").delete()
        file("episodes_${p.id}.dat").delete()
        savePlaylists()
        if (activeId == p.id) {
            activeId = playlists.firstOrNull()?.id ?: ""
            write("active.txt", activeId)
            if (activeId.isEmpty()) {
                catalog = emptyList()
                categories = emptyList()
                stack.clear()
                screen = Screen.Setup
            } else {
                catalog = emptyList()
                categories = emptyList()
                scope.launch { loadCache(activeId) }
            }
        }
    }

    fun toggleFav(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
        write("favs.json", JSONArray(favorites.toList()).toString())
    }

    fun saveProgress(pr: Progress) {
        progress = if (pr.duration > 0 && pr.position >= pr.duration - 1500) {
            progress.filter { it.key != pr.key }
        } else {
            (listOf(pr) + progress.filter { it.key != pr.key }).take(20)
        }
        write("progress.json", JSONArray(progress.map { it.toJson() }).toString())
    }

    fun clearProgress(key: String) {
        progress = progress.filter { it.key != key }
        write("progress.json", JSONArray(progress.map { it.toJson() }).toString())
    }
}