package com.ntplayone.tv

import java.util.Locale

data class SeriesTag(val season: Int, val num: Int)

private val seasonEpisodePatterns = listOf(
    Regex("""(?i)\bS(\d{1,2})\s*[._\-]?\s*E(?:P)?\s*(\d{1,3})\b"""),
    Regex("""(?i)\bT(\d{1,2})\s*[._\-]?\s*E(?:P)?\s*(\d{1,3})\b"""),
    Regex("""(?i)\b(\d{1,2})\s*[xX]\s*(\d{1,3})\b"""),
    Regex("""(?i)temporada\s*(\d{1,2})\s*[-–—: ]*\s*(?:epis[oó]dio|ep)\s*(\d{1,3})\b"""),
)

private val seasonOnlyPattern = Regex("""(?i)\b(?:temporada|season)\s*(\d{1,2})\b""")
private val episodeOnlyPattern = Regex("""(?i)\b(?:epis[oó]dio|ep)\s*(\d{1,3})\b""")

fun seriesTag(name: String): SeriesTag? {
    for (re in seasonEpisodePatterns) {
        val m = re.find(name) ?: continue
        val season = m.groupValues[1].toIntOrNull() ?: continue
        val num = m.groupValues[2].toIntOrNull() ?: continue
        return SeriesTag(season, num)
    }
    val ep = episodeOnlyPattern.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    val season = seasonOnlyPattern.find(name)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    return SeriesTag(season, ep)
}

fun seriesBase(name: String): String {
    var s = name
    val m = seasonEpisodePatterns.firstNotNullOfOrNull { it.find(s) }
    if (m != null) s = s.removeRange(m.range)
    s = s.replace(Regex("""(?i)[\s._\-|:]*\b(?:temporada|season)\s*\d{1,2}\b"""), "")
    s = s.replace(Regex("""(?i)[\s._\-|:]*\b(?:dublado|legendado|nacional)\b"""), "")
    s = s.replace(Regex("""[\s._\-|:]+$"""), "")
    s = s.trim(' ', '-', '_', '.', '|', ':', '[', ']', '(', ')')
    return s.ifBlank { name }
}

fun seriesKey(name: String) = seriesBase(name).lowercase(Locale.ROOT)

fun seriesDisplayName(item: Item): String =
    if (item.type == "series" && seriesTag(item.name) != null) seriesBase(item.name) else item.name

fun m3uEpisodes(group: List<Item>): List<Episode> = group.mapNotNull { item ->
    val tag = seriesTag(item.name) ?: return@mapNotNull null
    Episode(item.id, tag.season, tag.num, item.name, item.ext, "", item.url)
}.sortedWith(compareBy({ it.season }, { it.num }))
