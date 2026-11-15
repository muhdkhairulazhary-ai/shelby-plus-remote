package dev.tvremote.core.web
import dev.tvremote.core.scenes.VoicePhrases
/**
 * The page all three remotes serve (app/shared/web), read through [open]: this app's assets, or the folder itself on the computer. The computer's server sends the very same
 * files; [core] tells the page which of the three it is talking to ("tv" or "phone" here, see js/core.js).
 */
class WebPage(private val open: (String) -> java.io.InputStream, private val core: String, private val stamp: String) {
    private val cache = HashMap<String, ByteArray>()
    private val zipped = HashMap<String, ByteArray>()                // gzip of the same bytes, made once: a weak TV must not compress per request

    /** null when the path is not one of the page's files. [acceptEncoding] is the request's header: text files go out gzipped when it allows (1.2 MB -> 0.3 MB on the first load). */
    fun serve(path: String, acceptEncoding: String = ""): HttpServer.Response? {
        val name = if (path == "/" || path == "/index.html") "index.html" else path.drop(1)   // drop: "GET  HTTP/1.1" has an empty path
        if (!FILE.matches(name)) return null
        val bytes = (if (name == VoicePhrases.NAME) VoicePhrases.json() else null) ?: synchronized(cache) {   // the voice phrases: the freshest signed list the app holds, not a copy made once
            cache[name] ?: try {
                val asset = if (core == "tv") TV_ASSETS[name] ?: name else name
                var b = open(asset).use { it.readBytes() }
                if (core == "tv" && asset == "manifest-shelby-v1.json") b = String(b, Charsets.UTF_8)
                    .replace(".png\"", ".png?v=$stamp\"").toByteArray(Charsets.UTF_8)
                if (name == "index.html") b = page(String(b, Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
                b.also { if (room(cache, it.size)) cache[name] = it }
            } catch (e: Exception) { null }
        } ?: return null
        val type = when (name.substringAfterLast('.')) {
            "html" -> "text/html; charset=utf-8"; "css" -> "text/css; charset=utf-8"; "png" -> "image/png"; "ico" -> "image/x-icon"
            "json" -> if (name.startsWith("i18n/") || name.startsWith("voice/")) "application/json; charset=utf-8" else "application/manifest+json"; else -> "text/javascript; charset=utf-8"
        }
        val icon = name.endsWith(".png") || name.endsWith(".ico")
        val gz = if (icon || bytes.size < 1024 || !GZIP.containsMatchIn(acceptEncoding)) null else gzip(name, bytes)
        val out = HttpServer.Response.of(type, gz ?: bytes)
        if (!icon) out.headers["Vary"] = "Accept-Encoding"     // a cache must not hand the plain copy to a browser that wants gzip, or the reverse
        if (gz != null) out.headers["Content-Encoding"] = "gzip"
        // the page names its files with a stamp that changes with every update of the app, so they can be kept; the page itself never is
        out.headers["Cache-Control"] = if (name == "index.html" || name == "manifest.json" || name == "manifest-shelby-v1.json") "no-store"
            else if (core == "tv" && TV_ASSETS.containsKey(name)) "no-cache"
            else if (name == VoicePhrases.NAME) "max-age=600" else "max-age=604800"
        return out
    }

    private fun gzip(name: String, plain: ByteArray): ByteArray? = synchronized(zipped) {
        zipped[name] ?: run {
            val b = java.io.ByteArrayOutputStream(plain.size / 3)
            java.util.zip.GZIPOutputStream(b).use { it.write(plain) }
            b.toByteArray().takeIf { it.size < plain.size }.also { if (it != null && room(zipped, it.size)) zipped[name] = it }
        }
    }

    /** Whether one more file fits: by bytes, not a count (the page has ~80 files, 1.4 MB, and a count that falls short re-reads and re-zips the rest on every request). */
    private fun room(m: Map<String, ByteArray>, add: Int) = m.size < 256 && m.values.sumOf { it.size } + add <= BUDGET

    private fun page(html: String): String =
        LINK.replace((if (core == "tv") brand(html) else html).replaceFirst("<html ", "<html data-core=\"$core\" ")) { it.groupValues[1] + "?v=" + stamp + "\"" }

    private fun brand(html: String): String {
        var out = html.replace("TV Remote", "Shelby+ Remote")
            .replace("content=\"#111418\"", "content=\"#080808\"")
            .replace("rel=\"icon\" type=\"image/png\" href=\"icon-192.png\"", "rel=\"icon\" type=\"image/x-icon\" href=\"icon-shelbyfaviconv1.ico\"")
        for ((old, asset) in TV_ASSETS) out = out.replace(old, asset)
        return out
    }

    private companion object {
        const val BUDGET = 4 shl 20
        val TV_ASSETS = mapOf("manifest.json" to "manifest-shelby-v1.json", "icon-180.png" to "icon-shelby180v1.png",
            "icon-192.png" to "icon-shelby192v1.png", "icon-512.png" to "icon-shelby512v1.png", "icon-maskable.png" to "icon-shelbymaskablev1.png")
        val FILE = Regex("index\\.html|css/[a-z0-9_-]+\\.css|stats\\.js|jmuxer\\.min\\.js|js/[a-z0-9_-]+\\.js|manifest(?:-shelby-v1)?\\.json|remote\\.json|i18n/(?:[a-z]{2,3}|langs)\\.json|icon-[a-z0-9]+\\.(?:png|ico)|voice/phrases\\.json")   // manifest and icons: "Add to Home screen"; remote.json: the remote's keys, which the Phone app reads too
        val GZIP = Regex("(?i)(^|[\\s,])gzip\\s*(,|;(?!\\s*q=0(\\.0*)?\\s*(,|$))|$)")   // "gzip" as a token, not "gzip;q=0"
        val LINK = Regex("((?:src|href)=\"(?:css/[^\"?]+|stats\\.js|js/[^\"?]+|manifest(?:-shelby-v1)?\\.json|icon-[a-z0-9]+\\.(?:png|ico)))\"")
    }
}
