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
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build()
                ApiClient.okHttpClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) res.body?.string() else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private val ongoingCache = java.util.concurrent.ConcurrentHashMap<String, List<MediaItem>>()

    suspend fun getOngoing(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val cacheKey = "ongoing_$page"
        ongoingCache[cacheKey]?.let { if (it.isNotEmpty()) return@withContext it }

        val targetUrl = if (page > 1) "$BASE_URL/anime/page/$page/?status=ongoing&order=update" else "$BASE_URL/anime/?status=ongoing&order=update"
        val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".listupd article.bs, article.bs").forEach { el ->
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

            val badge = el.selectFirst(".typez, .bt .epx, .epx, .type, .status")?.text()?.trim() ?: "Ongoing"

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
        if (items.isNotEmpty()) {
            ongoingCache[cacheKey] = items
        }
        items
    }

    suspend fun getSchedule(dayIndex: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val cacheKey = "schedule_$dayIndex"
        ongoingCache[cacheKey]?.let { if (it.isNotEmpty()) return@withContext it }

        val ongoing = getOngoing(1)
        val latest = getLatest(1)

        val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val currentDayName = days.getOrElse(dayIndex) { "Hari Ini" }

        val scheduleList = mutableListOf<MediaItem>()
        val partitioned = ongoing.filterIndexed { index, _ -> (index % 7) == dayIndex }
        for (item in partitioned) {
            scheduleList.add(item.copy(badge = "$currentDayName • Ongoing"))
        }

        if (scheduleList.isEmpty() && latest.isNotEmpty()) {
            val latestPartition = latest.filterIndexed { index, _ -> (index % 7) == dayIndex }
            scheduleList.addAll(latestPartition.ifEmpty { latest.take(6) })
        }

        if (scheduleList.isNotEmpty()) {
            ongoingCache[cacheKey] = scheduleList
        }
        scheduleList
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

        fun isBlockedOrDead(u: String): Boolean {
            val lower = u.lowercase()
            return lower.contains("kotaksb") ||
                    lower.contains("about:blank") ||
                    lower.contains("javascript:") ||
                    !lower.startsWith("http")
        }

        fun extractServerName(url: String, fallback: String): String {
            val lower = url.lowercase()
            return when {
                lower.contains("blogger.com") || lower.contains("google.com/video") -> "Blogger Player"
                lower.contains("ok.ru") -> "OK.ru Player"
                lower.contains("dailymotion.com") || lower.contains("dai.ly") -> "Dailymotion"
                lower.contains("rumble.com") -> "Rumble"
                lower.contains("gofile.io") -> "Gofile"
                lower.contains("pixeldrain.com") -> "Pixeldrain"
                lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
                lower.contains("mp4upload") -> "Mp4Upload"
                fallback.isNotBlank() && !fallback.contains("Server", ignoreCase = true) && !fallback.contains("Select", ignoreCase = true) -> fallback
                else -> "Server ${servers.size + 1}"
            }
        }

        // 1. Direct iframes on page (checking data-litespeed-src first for Samehadaku caching, then data-src, then src)
        doc.select("#pembed iframe, .player-embed iframe, iframe").forEach { iframe ->
            val litespeed = iframe.attr("data-litespeed-src").trim()
            val dataSrc = iframe.attr("data-src").trim()
            val rawSrc = iframe.attr("src").trim()

            val src = when {
                litespeed.isNotBlank() && !litespeed.contains("about:blank") -> litespeed
                dataSrc.isNotBlank() && !dataSrc.contains("about:blank") -> dataSrc
                rawSrc.isNotBlank() && !rawSrc.contains("about:blank") -> rawSrc
                else -> ""
            }

            if (src.startsWith("http") && !isBlockedOrDead(src) && !servers.any { it.url == src }) {
                servers.add(StreamServerItem(extractServerName(src, "Blogger Player"), src, isDirectHls = false))
            }
        }

        // 2. Select mirror options with base64 encoded iframe tags
        doc.select("select.mirror option, .server option, .mirror option, select option").forEachIndexed { i, opt ->
            val name = opt.text().trim()
            val rawVal = opt.attr("value").trim()
            if (rawVal.length > 15) {
                try {
                    val decoded = String(Base64.decode(rawVal, Base64.DEFAULT), Charsets.UTF_8)
                    val match = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(decoded)
                    val iframeUrl = match?.groupValues?.get(1)?.trim() ?: if (decoded.startsWith("http")) decoded.trim() else null
                    if (!iframeUrl.isNullOrEmpty() && !isBlockedOrDead(iframeUrl) && !servers.any { it.url == iframeUrl }) {
                        servers.add(StreamServerItem(extractServerName(iframeUrl, name), iframeUrl, isDirectHls = false))
                    }
                } catch (e: Exception) {
                    // Ignore decoding error
                }
            }
        }

        // Prioritize reliable players first (Blogger, OK.ru, Dailymotion, Rumble)
        val sortedServers = servers.sortedWith(
            compareBy<StreamServerItem> { item ->
                when {
                    item.url.contains("blogger.com") || item.url.contains("google.com/video") -> 0
                    item.url.contains("ok.ru") -> 1
                    item.url.contains("dailymotion.com") -> 2
                    item.url.contains("rumble.com") -> 3
                    else -> 4
                }
            }
        )

        val primaryUrl = sortedServers.firstOrNull()?.url

        StreamResult(
            title = title,
            directHlsUrl = null,
            iframePlayerUrl = primaryUrl,
            servers = sortedServers
        )
    }
}
