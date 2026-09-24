package com.nanzstream.nanas.data.scraper

import android.util.Base64
import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

object AnimeScraper {

    private const val BASE_URL = "https://samehadaku.li"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private suspend fun fetchHtml(url: String, referer: String = "$BASE_URL/"): String? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", referer)
                    .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
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

        doc.select(".listupd article.bs, .excstf article, article.bs, .bsx, .post-show ul li").forEach { el ->
            val linkEl = if (el.tagName() == "a") el else el.selectFirst("a[href*='samehadaku.li']")
            val href = linkEl?.attr("href") ?: return@forEach
            if (!href.startsWith("http") || items.any { it.url == href }) return@forEach

            val title = linkEl.attr("title").ifEmpty {
                el.selectFirst(".tt h2, h2[itemprop='headline'], .title, h2")?.text()?.trim()
            } ?: linkEl.text().trim()

            val img = el.selectFirst("img")
            var thumb = img?.attr("data-src")?.ifEmpty { null }
                ?: img?.attr("data-lazy-src")?.ifEmpty { null }
                ?: img?.attr("src") ?: ""

            // Avoid svg placeholders
            if (thumb.startsWith("data:image")) {
                thumb = img?.attr("data-src") ?: ""
            }

            val epText = el.selectFirst(".bt .epx, .epx, .author")?.text()?.trim() ?: "Terbaru"

            if (title.isNotBlank()) {
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
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val targetUrl = if (page > 1) "$BASE_URL/page/$page/?s=$encodedQuery" else "$BASE_URL/?s=$encodedQuery"
        val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".listupd article.bs, article.bs, .animpost, .animepost, article").forEach { el ->
            val linkEl = if (el.tagName() == "a") el else el.selectFirst("a[href*='samehadaku.li']")
            val href = linkEl?.attr("href") ?: return@forEach
            if (!href.startsWith("http") || items.any { it.url == href }) return@forEach

            val title = linkEl.attr("title").ifEmpty {
                el.selectFirst(".tt h2, h2[itemprop='headline'], .title, h2")?.text()?.trim()
            } ?: linkEl.text().trim()

            val img = el.selectFirst("img")
            var thumb = img?.attr("data-src")?.ifEmpty { null }
                ?: img?.attr("data-lazy-src")?.ifEmpty { null }
                ?: img?.attr("src") ?: ""

            if (thumb.startsWith("data:image")) {
                thumb = img?.attr("data-src") ?: ""
            }

            val badge = el.selectFirst(".typez, .bt .epx, .type, .status")?.text()?.trim() ?: "Anime"

            if (title.isNotBlank()) {
                items.add(
                    MediaItem(
                        id = href,
                        title = title,
                        category = CategoryType.ANIME,
                        thumbnail = thumb,
                        url = href,
                        slug = href,
                        badge = badge
                    )
                )
            }
        }
        items
    }

    suspend fun getDetail(urlInput: String): MediaDetail? = withContext(Dispatchers.IO) {
        var targetUrl = if (urlInput.startsWith("http")) urlInput else "$BASE_URL/anime/$urlInput/"
        var html = fetchHtml(targetUrl) ?: return@withContext null
        var doc = Jsoup.parse(html)

        // If targetUrl is an episode page, check if there's a link to the anime series page
        if (!targetUrl.contains("/anime/")) {
            val seriesLink = doc.selectFirst("a[href*='/anime/']")?.attr("href")
                ?: doc.selectFirst(".naveps a[href*='/anime/']")?.attr("href")
            if (!seriesLink.isNullOrEmpty() && seriesLink.contains("/anime/")) {
                val seriesHtml = fetchHtml(seriesLink)
                if (seriesHtml != null) {
                    targetUrl = seriesLink
                    html = seriesHtml
                    doc = Jsoup.parse(seriesHtml)
                }
            }
        }

        val title = doc.selectFirst("h1.entry-title, h1, .title")?.text()?.trim() ?: "Anime Detail"
        val imgEl = doc.selectFirst(".thumb img, .poster img")
        var thumbnail = imgEl?.attr("data-src")?.ifEmpty { null }
            ?: imgEl?.attr("data-lazy-src")?.ifEmpty { null }
            ?: imgEl?.attr("src") ?: ""
        if (thumbnail.startsWith("data:image")) {
            thumbnail = imgEl?.attr("data-src") ?: ""
        }

        val synopsis = doc.selectFirst(".entry-content, .desc, .synopsis, .mindes")?.text()?.trim()

        val genres = mutableListOf<String>()
        doc.select(".genxed a, .genre-info a, .genre a").forEach { g ->
            val t = g.text().trim()
            if (t.isNotBlank() && !genres.contains(t)) genres.add(t)
        }

        val episodes = mutableListOf<EpisodeItem>()
        doc.select(".eplister ul li, .episodelst ul li").forEach { li ->
            val epNum = li.selectFirst(".epl-num")?.text()?.trim() ?: ""
            val epTitle = li.selectFirst(".epl-title")?.text()?.trim() ?: "Episode $epNum"
            val epDate = li.selectFirst(".epl-date")?.text()?.trim()
            val epLink = li.selectFirst("a")?.attr("href") ?: ""

            if (epLink.isNotBlank() && !episodes.any { it.url == epLink }) {
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

        // If no episodes found (e.g. standalone movie or direct episode page)
        if (episodes.isEmpty()) {
            episodes.add(
                EpisodeItem(
                    id = targetUrl,
                    episodeNumber = "1",
                    title = title,
                    url = targetUrl
                )
            )
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

        // 1. Direct iframes on page
        doc.select("#pembed iframe, .player-embed iframe, iframe").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.startsWith("http") && !src.contains("about:blank") && !servers.any { it.url == src }) {
                val name = when {
                    src.contains("blogger") -> "Blogger Player"
                    src.contains("ok.ru") -> "OK.ru"
                    src.contains("dailymotion") -> "Dailymotion"
                    else -> "Server Utama"
                }
                servers.add(StreamServerItem(name, src, isDirectHls = false))
            }
        }

        // 2. Select mirror options with base64 encoded iframe tags
        doc.select("select.mirror option, .server option, .mirror option, select option").forEachIndexed { i, opt ->
            val name = opt.text().trim()
            val rawVal = opt.attr("value")
            if (rawVal.length > 15) {
                try {
                    val decoded = String(Base64.decode(rawVal, Base64.DEFAULT), Charsets.UTF_8)
                    val match = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(decoded)
                    val iframeUrl = match?.groupValues?.get(1) ?: if (decoded.startsWith("http")) decoded else null
                    if (!iframeUrl.isNullOrEmpty() && !servers.any { it.url == iframeUrl }) {
                        val sName = if (name.isNotBlank() && !name.contains("Server", true)) name else "Server ${i + 1}"
                        servers.add(StreamServerItem(sName, iframeUrl, isDirectHls = false))
                    }
                } catch (e: Exception) {
                    // Ignore decoding error
                }
            }
        }

        val primaryUrl = servers.firstOrNull()?.url

        StreamResult(
            title = title,
            directHlsUrl = null,
            iframePlayerUrl = primaryUrl,
            servers = servers
        )
    }
}
