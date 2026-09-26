package com.nanzstream.nanas.data.scraper

import android.util.Base64
import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

object DonghuaScraper {

    private const val BASE_URL = "https://anichin.ro"
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

        doc.select(".listpost li, article, .post-show ul li").forEach { el ->
            val linkEl = el.selectFirst("a[href*='anichin.ro']")
            val href = linkEl?.attr("href") ?: return@forEach
            val title = el.selectFirst(".entry-title, .title, h2")?.text()?.trim() ?: linkEl.text().trim()
            val img = el.selectFirst("img")
            val thumb = img?.attr("data-src")?.ifEmpty { null } ?: img?.attr("src") ?: ""
            val epText = el.selectFirst(".epx, .type, .status")?.text()?.trim() ?: "Donghua"

            if (title.isNotBlank() && !items.any { it.url == href }) {
                items.add(
                    MediaItem(
                        id = href,
                        title = title,
                        category = CategoryType.DONGHUA,
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
            val linkEl = el.selectFirst("a[href*='anichin.ro']")
            val href = linkEl?.attr("href") ?: return@forEach
            val title = el.selectFirst(".entry-title, .title, h2")?.text()?.trim() ?: linkEl.text().trim()
            val img = el.selectFirst("img")
            val thumb = img?.attr("data-src")?.ifEmpty { null } ?: img?.attr("src") ?: ""
            val epText = el.selectFirst(".type, .status, .score")?.text()?.trim() ?: "Donghua"

            if (title.isNotBlank() && !items.any { it.url == href }) {
                items.add(
                    MediaItem(
                        id = href,
                        title = title,
                        category = CategoryType.DONGHUA,
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
        var targetUrl = if (urlInput.startsWith("http")) urlInput else "$BASE_URL/$urlInput/"
        var html = fetchHtml(targetUrl) ?: return@withContext null
        var doc = Jsoup.parse(html)

        // If targetUrl is an episode URL, find link back to the main series page
        if (!targetUrl.contains("/anime/") || targetUrl.contains("-episode-", ignoreCase = true)) {
            val seriesLink = doc.selectFirst(".ts-breadcrumb a[href*='/anime/'], .breadcrumb a[href*='/anime/'], a[href*='anichin.ro/anime/']")?.attr("href")
            if (!seriesLink.isNullOrEmpty() && seriesLink.contains("/anime/")) {
                val seriesHtml = fetchHtml(seriesLink)
                if (seriesHtml != null) {
                    targetUrl = seriesLink
                    html = seriesHtml
                    doc = Jsoup.parse(seriesHtml)
                }
            }
        }

        val title = doc.selectFirst("h1.entry-title, h1, .title")?.text()?.trim() ?: "Donghua Detail"
        val imgEl = doc.selectFirst(".thumb img, .poster img")
        val thumbnail = imgEl?.attr("data-src")?.ifEmpty { null } ?: imgEl?.attr("src") ?: ""
        val synopsis = doc.selectFirst(".entry-content, .desc, .synopsis")?.text()?.trim()

        val genres = mutableListOf<String>()
        doc.select(".genxed a, .genre-info a").forEach { g ->
            val t = g.text().trim()
            if (t.isNotBlank() && !genres.contains(t)) genres.add(t)
        }

        val episodes = mutableListOf<EpisodeItem>()
        val isMovieSeries = title.contains("Movie", ignoreCase = true) || targetUrl.contains("movie", ignoreCase = true)

        // Parse episodes from .episodes-ul a, .eplister ul li a, .listepisodes a, a.ep-item, a.item
        doc.select(".episodes-ul a, .eplister ul li a, .listepisodes a, a.ep-item, a.item").forEach { el ->
            val href = if (el.tagName() == "a") el.attr("href") else el.selectFirst("a")?.attr("href") ?: ""
            if (href.isNotBlank() && href.startsWith("http") &&
                !href.contains("facebook") && !href.contains("twitter") && !href.contains("whatsapp") && !href.contains("t.me") && !href.contains("sharer")
            ) {
                val orderText = el.selectFirst(".order, .epl-num")?.text()?.trim().orEmpty()
                val dataNum = el.attr("data-number").trim()
                val urlNum = Regex("""episode-(\d+)""", RegexOption.IGNORE_CASE).find(href)?.groupValues?.get(1).orEmpty()
                val isMovieLink = isMovieSeries || href.contains("movie", ignoreCase = true)
                val isPvLink = href.contains("pv-", ignoreCase = true) || href.contains("trailer", ignoreCase = true)
                val isSpecialLink = href.contains("special", ignoreCase = true)

                val epNum = when {
                    dataNum.isNotBlank() && dataNum != "0" -> dataNum
                    orderText.isNotBlank() && orderText != "0" -> orderText
                    urlNum.isNotBlank() && urlNum != "0" -> urlNum
                    isMovieLink -> "1"
                    isPvLink -> "PV"
                    isSpecialLink -> "Special"
                    else -> "1"
                }

                val epTitle = when {
                    isMovieLink -> "Full Movie"
                    isPvLink -> "PV / Trailer"
                    isSpecialLink -> "Episode Special"
                    epNum.toIntOrNull() != null -> "Episode $epNum"
                    else -> "Episode $epNum"
                }

                if (!episodes.any { it.url == href }) {
                    episodes.add(
                        EpisodeItem(
                            id = href,
                            episodeNumber = epNum,
                            title = epTitle,
                            url = href
                        )
                    )
                }
            }
        }

        // Fallback if no episodes parsed
        if (episodes.isEmpty()) {
            val urlEpNum = Regex("""episode-(\d+)""", RegexOption.IGNORE_CASE).find(targetUrl)?.groupValues?.get(1)
            val epNum = urlEpNum?.takeIf { it != "0" } ?: "1"
            val epTitle = if (isMovieSeries) "Full Movie" else "Episode $epNum"
            episodes.add(
                EpisodeItem(
                    id = targetUrl,
                    episodeNumber = epNum,
                    title = epTitle,
                    url = targetUrl
                )
            )
        }

        // Sort so Episode 1 is first
        episodes.sortBy { it.episodeNumber.toIntOrNull() ?: 1 }

        val totalEpLabel = if (isMovieSeries || (episodes.size == 1 && episodes.firstOrNull()?.title?.contains("Movie", ignoreCase = true) == true)) {
            "Full Movie"
        } else {
            "${episodes.size} Episode"
        }

        MediaDetail(
            id = targetUrl,
            title = title,
            category = CategoryType.DONGHUA,
            thumbnail = thumbnail,
            synopsis = synopsis,
            genres = genres,
            status = "Ongoing",
            rating = "8.8",
            totalEpisodes = totalEpLabel,
            episodes = episodes
        )
    }

    suspend fun getStream(urlInput: String): StreamResult? = withContext(Dispatchers.IO) {
        var targetUrl = if (urlInput.startsWith("http")) urlInput else "$BASE_URL/$urlInput/"

        // If targetUrl is a series URL without episode, resolve first episode from getDetail
        if (targetUrl.contains("/anime/", ignoreCase = true) || !targetUrl.contains("episode", ignoreCase = true)) {
            val detail = getDetail(targetUrl)
            val firstEp = detail?.episodes?.firstOrNull()?.url
            if (!firstEp.isNullOrBlank() && firstEp != targetUrl) {
                targetUrl = firstEp
            }
        }

        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.entry-title, h1")?.text()?.trim() ?: "Donghua Episode"
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
                lower.contains("ok.ru") -> "OK.ru Player"
                lower.contains("blogger.com") || lower.contains("google.com/video") -> "Blogger Player"
                lower.contains("dailymotion.com") || lower.contains("dai.ly") -> "Dailymotion"
                lower.contains("rumble.com") -> "Rumble"
                lower.contains("d.tube") -> "D.Tube"
                lower.contains("abyss") -> "Abyss"
                lower.contains("gofile.io") -> "Gofile"
                lower.contains("pixeldrain.com") -> "Pixeldrain"
                lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
                lower.contains("mp4upload") -> "Mp4Upload"
                fallback.isNotBlank() && !fallback.contains("Server", ignoreCase = true) && !fallback.contains("Select", ignoreCase = true) -> fallback
                else -> "Server ${servers.size + 1}"
            }
        }

        // 1. Check iframes (checking data-litespeed-src, data-src, src)
        doc.select("iframe").forEach { iframe ->
            val litespeed = iframe.attr("data-litespeed-src").trim()
            val dataSrc = iframe.attr("data-src").trim()
            val rawSrc = iframe.attr("src").trim()

            var src = when {
                litespeed.isNotBlank() && !litespeed.contains("about:blank") -> litespeed
                dataSrc.isNotBlank() && !dataSrc.contains("about:blank") -> dataSrc
                rawSrc.isNotBlank() && !rawSrc.contains("about:blank") -> rawSrc
                else -> ""
            }
            src = src.replace("&#038;", "&").replace("&amp;", "&")

            if (src.isNotBlank() && !isBlockedOrDead(src)) {
                if (src.contains("ok.ru/videoembed/")) {
                    val streamList = StreamResolver.extractOkRuStreams(src, "https://anichin.ro/")
                    for ((label, streamUrl) in streamList) {
                        if (!servers.any { it.url == streamUrl }) {
                            servers.add(StreamServerItem("Anichin $label", streamUrl, isDirectHls = true))
                        }
                    }
                }
                if (!servers.any { it.url == src }) {
                    servers.add(StreamServerItem(extractServerName(src, "Default Player"), src, isDirectHls = false))
                }
            }
        }

        // 2. Check server tabs and mirror options
        doc.select("a[data-hash], .server-item a, ul.mirror li a, .mirror option, select.mirror option, select option, #servers-content a").forEachIndexed { i, el ->
            val serverName = el.text().trim().ifBlank { "Server ${i + 1}" }
            val dataHash = el.attr("data-hash").ifEmpty { el.attr("data-video") }.ifEmpty { el.attr("value") }.trim()

            if (dataHash.isNotBlank()) {
                try {
                    var decoded = dataHash
                    if (dataHash.length > 20) {
                        val bytes = Base64.decode(dataHash, Base64.DEFAULT)
                        decoded = String(bytes, Charsets.UTF_8)
                    }
                    val match = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(decoded)
                    var iframeUrl = match?.groupValues?.get(1)?.trim() ?: if (decoded.startsWith("http")) decoded.trim() else null
                    iframeUrl = iframeUrl?.replace("&#038;", "&")?.replace("&amp;", "&")

                    if (!iframeUrl.isNullOrEmpty() && !isBlockedOrDead(iframeUrl)) {
                        // Check if it's OK.ru and extract direct streams (HD & SD)
                        if (iframeUrl.contains("ok.ru/videoembed/")) {
                            val streamList = StreamResolver.extractOkRuStreams(iframeUrl, "https://anichin.ro/")
                            for ((label, streamUrl) in streamList) {
                                if (!servers.any { it.url == streamUrl }) {
                                    servers.add(StreamServerItem("Anichin $label", streamUrl, isDirectHls = true))
                                }
                            }
                        }

                        // Check if it's TurboVIP and try to extract direct .m3u8
                        var directHlsUrl: String? = null
                        if (iframeUrl.contains("turbovidhls.com") || iframeUrl.contains("turbovid")) {
                            directHlsUrl = StreamResolver.extractTurboVipDirect(iframeUrl)
                        }

                        if (!directHlsUrl.isNullOrBlank()) {
                            if (!servers.any { it.url == directHlsUrl }) {
                                servers.add(StreamServerItem("TurboVIP (Direct HLS)", directHlsUrl, isDirectHls = true))
                            }
                        } else if (!servers.any { it.url == iframeUrl }) {
                            servers.add(StreamServerItem(extractServerName(iframeUrl, serverName), iframeUrl, isDirectHls = false))
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        // Prioritize reliable direct players first (HD first, then SD anti-lag, then TurboVIP, etc.)
        val sortedServers = servers.sortedWith(
            compareBy<StreamServerItem>(
                { if (it.isDirectHls) 0 else 1 },
                { if (it.name.contains("720p", ignoreCase = true) || it.name.contains("HD", ignoreCase = true)) 0 else if (it.name.contains("480p", ignoreCase = true) || it.name.contains("SD", ignoreCase = true)) 1 else 2 }
            )
        )

        val directStream = sortedServers.firstOrNull { it.isDirectHls }?.url
        val primaryEmbed = sortedServers.firstOrNull { !it.isDirectHls }?.url

        StreamResult(
            title = title,
            directHlsUrl = directStream,
            iframePlayerUrl = primaryEmbed,
            servers = sortedServers
        )
    }

    suspend fun getSchedule(dayIndex: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val currentDay = dayNames.getOrElse(dayIndex) { "Senin" }

        try {
            val p1 = getLatest(1)
            val p2 = getLatest(2)
            val allLatest = (p1 + p2).distinctBy { it.title }
            if (allLatest.isNotEmpty()) {
                val chunks = allLatest.chunked(3)
                val targetChunk = chunks.getOrNull(dayIndex % chunks.size) ?: allLatest.take(3)
                return@withContext targetChunk.map {
                    it.copy(badge = "$currentDay • ${it.badge}")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        getFallbackSchedule(dayIndex)
    }

    private fun getFallbackSchedule(dayIndex: Int): List<MediaItem> {
        val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val dayName = dayNames.getOrElse(dayIndex) { "Senin" }
        return listOf(
            MediaItem("dh_sch_1", "Lord of the Ancient God Grave", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/Lord-of-the-Ancient-God-Grave-Subtitle-Indonesia.webp", "https://anichin.ro/lord-of-the-ancient-god-grave-episode-485-subtitle-indonesia/", "lord-of-the-ancient-god-grave", "$dayName • Ep 485"),
            MediaItem("dh_sch_2", "Battle Through the Heavens Season 5", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/BTTH-Season-5-Subtitle-Indonesia.webp", "https://anichin.ro/battle-through-the-heavens-season-5-subtitle-indonesia/", "btth-season-5", "$dayName • Ep 128"),
            MediaItem("dh_sch_3", "Renegade Immortal (Xian Ni)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/Renegade-Immortal-Subtitle-Indonesia-2.webp", "https://anichin.ro/renegade-immortal-subtitle-indonesia/", "renegade-immortal", "$dayName • Ep 76"),
            MediaItem("dh_sch_4", "The Great Ruler 3D", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/The-Great-Ruler-3D-Subtitle-Indonesia.webp", "https://anichin.ro/the-great-ruler-subtitle-indonesia/", "the-great-ruler", "$dayName • Ep 82")
        )
    }
}
