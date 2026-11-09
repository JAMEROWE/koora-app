package com.koora.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Football news from official RSS feeds — trilingual by design.
 *
 * The registry below is the "formula": adding a source later is one line.
 * Each source carries its language + region so the News tab can badge items
 * and filter by the app language (EN / FR / AR).
 *
 * Regions covered for Arabic: pan-Arab (Sky News Arabia, CNN Arabic) +
 * North Africa via Egypt's FilGoal (strong African club coverage).
 * Sources that block automated readers (UEFA, Hespress, YallaKora) are
 * intentionally excluded — swap them from this registry if a proxy exists.
 */
data class NewsSource(val name: String, val lang: String, val region: String, val url: String)

data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val region: String,
    val date: Long
)

object NewsRepository {

    val sources = listOf(
        // --- English ---
        NewsSource("BBC Sport", "en", "Europe", "https://feeds.bbci.co.uk/sport/football/rss.xml"),
        NewsSource("Sky Sports", "en", "Europe", "https://www.skysports.com/rss/12040"),
        // --- French ---
        NewsSource("L'Équipe", "fr", "France", "https://dwh.lequipe.fr/api/edito/rss?path=/Football/"),
        NewsSource("RMC Sport", "fr", "France", "https://rmcsport.bfmtv.com/rss/football/"),
        // --- Arabic ---
        NewsSource("Sky News Arabia", "ar", "MENA", "https://www.skynewsarabia.com/rss"),
        NewsSource("CNN Arabic", "ar", "MENA", "https://arabic.cnn.com/api/v1/rss/sport/rss.xml"),
        NewsSource("FilGoal", "ar", "Egypt", "https://www.filgoal.com/rss"),
    )

    /** Fetch all sources for [lang] in parallel; one failing source never breaks the tab. */
    suspend fun news(lang: String): List<NewsItem> = coroutineScope {
        val jobs = sources.filter { it.lang == lang }.map { src ->
            async { runCatching { fetch(src) }.getOrDefault(emptyList()) }
        }
        jobs.flatMap { it.await() }
            .distinctBy { it.title.lowercase().take(60) }
            .sortedByDescending { it.date }
            .take(50)
    }

    private suspend fun fetch(src: NewsSource): List<NewsItem> = withContext(Dispatchers.IO) {
        val conn = URL(src.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; KooraApp/1.4)")
        try {
            val bytes = conn.inputStream.use { it.readBytes() }
            parseRss(bytes, src)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Lenient RSS/Atom parser. Handles both <item> and <entry>, RFC-822 and
     * ISO-8601 dates, and legacy Windows-1256 encoded Arabic feeds.
     */
    private fun parseRss(bytes: ByteArray, src: NewsSource): List<NewsItem> {
        val text = decodeSmart(bytes)
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), null)

        val items = mutableListOf<NewsItem>()
        var inItem = false
        var tag: String? = null
        var title = ""
        var link = ""
        var date = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name ?: ""
                    if (name.equals("item", true) || name.equals("entry", true)) {
                        inItem = true
                        title = ""; link = ""; date = ""
                    } else if (inItem && name.equals("link", true)) {
                        // Atom stores the URL in href=, RSS in the text node
                        parser.getAttributeValue(null, "href")?.let { link = it }
                    }
                    tag = name
                }

                XmlPullParser.TEXT -> if (inItem) when (tag?.lowercase()) {
                    "title" -> title += parser.text
                    "link" -> link += parser.text
                    "pubdate", "published", "updated", "date" -> date += parser.text
                }

                XmlPullParser.END_TAG -> {
                    val name = parser.name ?: ""
                    if (name.equals("item", true) || name.equals("entry", true)) {
                        inItem = false
                        val t = title.trim()
                        if (t.isNotEmpty()) {
                            items += NewsItem(
                                title = t,
                                link = link.trim(),
                                source = src.name,
                                region = src.region,
                                date = parseDate(date.trim())
                            )
                        }
                    }
                }
            }
            parser.next()
        }
        return items
    }

    private fun decodeSmart(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(bytes.size, 400), Charsets.ISO_8859_1)
        val declared = Regex("encoding\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .find(head)?.groupValues?.get(1)?.lowercase()
        val decoded = when {
            declared != null && declared.contains("1256") -> String(bytes, charset("windows-1256"))
            declared != null && declared.contains("8859") -> String(bytes, Charsets.ISO_8859_1)
            else -> String(bytes, Charsets.UTF_8)
        }
        // UTF-8 bytes that were actually Windows-1256 produce U+FFFD — retry.
        return if (decoded.contains('\uFFFD') && declared == null) {
            try { String(bytes, charset("windows-1256")) } catch (_: Exception) { decoded }
        } else decoded
    }

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    )

    private fun parseDate(raw: String): Long {
        val clean = raw.replace(Regex("\\s+"), " ").trim()
        dateFormats.forEach { fmt ->
            try {
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                val d = fmt.parse(clean)
                if (d != null) return d.time
            } catch (_: Exception) { /* try next */ }
        }
        return 0L
    }
}
