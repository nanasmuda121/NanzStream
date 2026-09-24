package com.nanzstream.nanas.data.scraper

import android.util.Base64
import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

object AnimeScraper {

    private const val BASE_URL = "https://samehadaku.li"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private suspend fun fetchHtml(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .build()
            val res = ApiClient.okHttpClient.newCall(req).execute()
            if (res.isSuccessful) res.body?.string() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val targetUrl = if (page > 1) "$BASE_URL/page/$page/" else "$BASE_URL/"
        val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".post-show ul li, .listpost li, article").forEach { el ->
            val linkEl = el.selectFirst("a[href*='samehadaku.li']")
            val href = linkEl?.attr("href") ?: return@forEach
            val title = el.selectFirst(".entry-title, .title, h2")?.text()?.trim() ?: linkEl.text().trim()
            val img = el.selectFirst("img")
            val thumb = img?.attr("data-src")?.ifEmpty { null } ?: img?.attr("src") ?: ""
            val epText = el.selectFirst(".dtla .epx, .epx, .author")?.text()?.trim() ?: "Terbaru"

            if (title.isNotBlank() && !items.any { it.url == href }) {
                items.add(
                    MediaItem(
                        id = href,
                        title = title,
                        category = CategoryType.ANIME,
                        thumbnail = thumb,
                        url = href,
                        slug = href,
                        badge = epText
                    )
                )
            }
        }
        items
    }

    suspend fun search(query: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val targetUrl = if (page > 1) {
            "$BASE_URL/page/$page/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
        } else {
            "$BASE_URL/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
        }
        val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".animpost, .animepost, article, .post-show ul li").forEach { el ->
            val linkEl = el.selectFirst("a[href*='samehadaku.li']")
            val href = linkEl?.attr("href") ?: return@forEach
            val title = el.selectFirst(".entry-title, .title, h2")?.text()?.trim() ?: linkEl.text().trim()
            val img = el.selectFirst("img")
            val thumb = img?.attr("data-src")?.ifEmpty { null } ?: img?.attr("src") ?: ""
            val epText = el.selectFirst(".type, .status, .score")?.text()?.trim() ?: "Anime"

            if (title.isNotBlank() && !items.any { it.url == href }) {
                items.add(
                    MediaItem(
                        id = href,
                        title = title,
                        category = CategoryType.ANIME,
                        thumbnail = thumb,
                        url = href,
                        slug = href,
                        badge = epText
                    )
                )
            }
        }
        items
    }

    suspend fun getDetail(urlInput: String): MediaDetail? = withContext(Dispatchers.IO) {
        val targetUrl = if (urlInput.startsWith("http")) urlInput else "$BASE_URL/anime/$urlInput/"
        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.entry-title, h1, .title")?.text()?.trim() ?: "Anime Detail"
        val imgEl = doc.selectFirst(".thumb img, .poster img")
        val thumbnail = imgEl?.attr("data-src")?.ifEmpty { null } ?: imgEl?.attr("src") ?: ""
        val synopsis = doc.selectFirst(".entry-content, .desc, .synopsis")?.text()?.trim()

        val genres = mutableListOf<String>()
        doc.select(".genxed a, .genre-info a").forEach { g ->
            val t = g.text().trim()
            if (t.isNotBlank() && !genres.contains(t)) genres.add(t)
        }

        val episodes = mutableListOf<EpisodeItem>()
        doc.select(".eplister ul li").forEach { li ->
            val epNum = li.selectFirst(".epl-num")?.text()?.trim() ?: ""
            val epTitle = li.selectFirst(".epl-title")?.text()?.trim() ?: "Episode $epNum"
            val epDate = li.selectFirst(".epl-date")?.text()?.trim()
            val epLink = li.selectFirst("a")?.attr("href") ?: ""

            if (epLink.isNotBlank()) {
                episodes.add(
                    EpisodeItem(
                        id = epLink,
                        episodeNumber = epNum.ifBlank { (episodes.size + 1).toString() },
                        title = epTitle,
                        url = epLink,
                        date = epDate
                    )
                )
            }
        }

        episodes.sortBy { it.episodeNumber.toIntOrNull() ?: 0 }

        MediaDetail(
            id = targetUrl,
            title = title,
            category = CategoryType.ANIME,
            thumbnail = thumbnail,
            synopsis = synopsis,
            genres = genres,
            status = "Ongoing",
            rating = "8.6",
            totalEpisodes = "${episodes.size} Episode",
            episodes = episodes
        )
    }

    suspend fun getStream(urlInput: String): StreamResult? = withContext(Dispatchers.IO) {
        val targetUrl = if (urlInput.startsWith("http")) urlInput else "$BASE_URL/$urlInput/"
        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.entry-title, h1")?.text()?.trim() ?: "Anime Episode"

        val servers = mutableListOf<StreamServerItem>()

        // 1. Default embed
        val defaultIframe = doc.selectFirst("#pembed iframe, .player-embed iframe")
        var defaultSrc = defaultIframe?.attr("data-litespeed-src")?.ifEmpty { null }
            ?: defaultIframe?.attr("data-src")?.ifEmpty { null }
            ?: defaultIframe?.attr("src") ?: ""
        if (defaultSrc == "about:blank") defaultSrc = ""

        if (defaultSrc.isNotBlank()) {
            servers.add(StreamServerItem("Server Utama", defaultSrc, isDirectHls = false))
        }

        // 2. Mirrors in select or links
        doc.select("select.mirror option, .server option, .mirror option").forEachIndexed { i, opt ->
            val name = opt.text().trim()
            val rawVal = opt.attr("value")
            if (rawVal.length > 10) {
                try {
                    val decoded = String(Base64.decode(rawVal, Base64.DEFAULT), Charsets.UTF_8)
                    val match = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(decoded)
                    val iframeUrl = match?.groupValues?.get(1) ?: if (decoded.startsWith("http")) decoded else null
                    if (!iframeUrl.isNullOrEmpty() && !servers.any { it.url == iframeUrl }) {
                        servers.add(StreamServerItem(if (name.isNotBlank()) name else "Server ${i + 1}", iframeUrl, isDirectHls = false))
                    }
                } catch (e: Exception) {
                    // Ignore malformed base64
                }
            }
        }

        val primaryUrl = servers.firstOrNull()?.url ?: defaultSrc

        StreamResult(
            title = title,
            directHlsUrl = null,
            iframePlayerUrl = primaryUrl,
            servers = servers
        )
    }
}
