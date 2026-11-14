package com.ntplayone.tv

import org.json.JSONObject

data class Playlist(
    val id: String,
    val name: String,
    val kind: String,
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val url: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val expires: String = "",
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("kind", kind); put("server", server)
        put("user", user); put("pass", pass); put("url", url); put("addedAt", addedAt)
        put("expires", expires)
    }

    companion object {
        fun fromJson(o: JSONObject) = Playlist(
            o.optString("id"), o.optString("name"), o.optString("kind"),
            o.optString("server"), o.optString("user"), o.optString("pass"),
            o.optString("url"), o.optLong("addedAt"), o.optString("expires"),
        )
    }
}

data class Category(val id: String, val name: String, val type: String) {
    fun toJson() = JSONObject().apply { put("id", id); put("name", name); put("type", type) }
    companion object {
        fun fromJson(o: JSONObject) = Category(o.optString("id"), o.optString("name"), o.optString("type"))
    }
}

data class Item(
    val key: String,
    val playlistId: String,
    val id: String,
    val name: String,
    val type: String,
    val icon: String = "",
    val categoryId: String = "",
    val plot: String = "",
    val year: String = "",
    val rating: String = "",
    val ext: String = "",
    val url: String = "",
) {
    fun toJson() = JSONObject().apply {
        put("key", key); put("playlistId", playlistId); put("id", id); put("name", name)
        put("type", type); put("icon", icon); put("categoryId", categoryId); put("plot", plot)
        put("year", year); put("rating", rating); put("ext", ext); put("url", url)
    }

    companion object {
        fun fromJson(o: JSONObject) = Item(
            o.optString("key"), o.optString("playlistId"), o.optString("id"), o.optString("name"),
            o.optString("type"), o.optString("icon"), o.optString("categoryId"), o.optString("plot"),
            o.optString("year"), o.optString("rating"), o.optString("ext"), o.optString("url"),
        )
    }
}

data class Episode(
    val id: String,
    val season: Int,
    val num: Int,
    val title: String,
    val ext: String,
    val plot: String = "",
    val url: String = "",
)

data class Progress(
    val key: String,
    val playlistId: String,
    val itemId: String,
    val name: String,
    val icon: String,
    val type: String,
    val url: String,
    val position: Long,
    val duration: Long,
    val updatedAt: Long,
) {
    val percent: Int get() = if (duration > 0) ((position * 100) / duration).toInt().coerceIn(0, 100) else 0

    fun toJson() = JSONObject().apply {
        put("key", key); put("playlistId", playlistId); put("itemId", itemId); put("name", name)
        put("icon", icon); put("type", type); put("url", url); put("position", position)
        put("duration", duration); put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = Progress(
            o.optString("key"), o.optString("playlistId"), o.optString("itemId"), o.optString("name"),
            o.optString("icon"), o.optString("type"), o.optString("url"), o.optLong("position"),
            o.optLong("duration"), o.optLong("updatedAt"),
        )
    }
}

sealed class Screen {
    data object Setup : Screen()
    data object Home : Screen()
    data object Lists : Screen()
    data object Search : Screen()
    data object XtreamForm : Screen()
    data object M3uForm : Screen()
    data class Browse(val type: String) : Screen()
    data class Detail(val item: Item) : Screen()
    data class Player(
        val url: String,
        val title: String,
        val item: Item? = null,
        val episode: Episode? = null,
        val live: Boolean = false,
    ) : Screen()
}