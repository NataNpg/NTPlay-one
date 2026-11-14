package com.ntplayone.tv

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object Net {
    fun get(url: String, timeoutMs: Int = 20000): String {
        open(url, timeoutMs).bufferedReader().use { return it.readText() }
    }

    fun open(url: String, timeoutMs: Int = 20000): InputStream {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "NTPlayone/1.0")
        val code = c.responseCode
        if (code >= 400) {
            c.disconnect()
            throw Exception("HTTP $code")
        }
        return c.inputStream
    }

    fun localIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress && it.hostAddress?.contains(":") == false }
            ?.hostAddress ?: "127.0.0.1"
    } catch (e: Exception) {
        "127.0.0.1"
    }

    // capa de série vinda da TVMaze (API pública, sem chave)
    fun findSeriesCover(name: String): String? = runCatching {
        var q = name
            .replace(Regex("""(?i)\b(fhd|uhd|4k|hd|sd)\b"""), " ")
            .replace(Regex("""[\[\](){}]+"""), " ")
            .replace(Regex("""[\s._\-|]+"""), " ")
            .trim()
        q = q.replace(Regex("""\s*[(\[]?\b(?:19|20)\d{2}\b[)\]]?\s*$"""), "").trim()
        if (q.length < 3) return null
        val arr = JSONArray(get("https://api.tvmaze.com/search/shows?q=${URLEncoder.encode(q, "UTF-8")}", 12000))
        val target = q.lowercase(Locale.ROOT)
        var best: String? = null
        for (i in 0 until arr.length()) {
            val show = arr.optJSONObject(i)?.optJSONObject("show") ?: continue
            val img = show.optJSONObject("image") ?: continue
            val url = img.optString("original").ifBlank { img.optString("medium") }
            if (url.isBlank()) continue
            val n = show.optString("name").lowercase(Locale.ROOT)
            if (n == target || n.startsWith(target) || target.startsWith(n)) return url
            if (best == null) best = url
        }
        best
    }.getOrNull()
}

object Qr {
    fun bitmap(text: String, size: Int = 512): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }
}

private val adultPattern = Regex(
    """(?i)\b(adult\w*|xxx|porn\w*|er[oó]tic\w*|erotism\w*|sexy|sexo|sensual)\b|18\s*\+|\+\s*18|\b18\s*anos\b"""
)

fun isAdultText(text: String): Boolean = text.isNotBlank() && adultPattern.containsMatchIn(text)