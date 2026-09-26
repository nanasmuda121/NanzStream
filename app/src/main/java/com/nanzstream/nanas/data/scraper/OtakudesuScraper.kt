package com.nanzstream.nanas.data.scraper

import android.util.Base64
import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder

object OtakudesuScraper {

    private const val BASE_URL = "https://otakudesu.blog"
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

    private suspend fun postAjax(params: Map<String, String>): String? = withContext(Dispatchers.IO) {
        try {
            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }
            val req = Request.Builder()
                .url("$BASE_URL/wp-admin/admin-ajax.php")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(formBuilder.build())
                .build()
            val res = ApiClient.okHttpClient.newCall(req).execute()
            if (res.isSuccessful) res.body?.string() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val targetUrl = if (page > 1) "$BASE_URL/ongoing-anime/page/$page/" else "$BASE_URL/ongoing-anime/"
        val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".venz .detpost, .detpost").forEach { el ->
            val linkEl = el.selectFirst("a[href*='/anime/']") ?: el.selectFirst("a")
            val href = linkEl?.attr("href") ?: return@forEach
            if (!href.startsWith("http") || items.any { it.url == href }) return@forEach

            val title = el.selectFirst("h2.jdlflm")?.text()?.trim()
                ?: el.selectFirst(".thumb a")?.attr("title")?.trim()
                ?: linkEl.attr("title").ifEmpty { linkEl.text().trim() }

            val img = el.selectFirst("img")
            var thumb = img?.attr("src")?.ifEmpty { null }
                ?: img?.attr("data-src") ?: ""

            val epText = el.selectFirst(".epz")?.text()?.trim() ?: "Ongoing"
            val dayText = el.selectFirst(".epztipe")?.text()?.trim() ?: ""
            val badge = if (dayText.isNotBlank()) "$epText • $dayText" else epText

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

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val targetUrl = "$BASE_URL/?s=$encodedQuery&post_type=anime"
        val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select("ul.chivsrc li").forEach { el ->
            val linkEl = el.selectFirst("h2 a")
            val href = linkEl?.attr("href") ?: return@forEach
            if (!href.startsWith("http") || items.any { it.url == href }) return@forEach

            val title = linkEl.text().trim()
            val img = el.selectFirst("img")
            val thumb = img?.attr("src")?.ifEmpty { null }
                ?: img?.attr("data-src") ?: ""

            val genres = mutableListOf<String>()
            el.select(".set:contains(Genres) a").forEach { g ->
                val gt = g.text().trim()
                if (gt.isNotBlank() && !genres.contains(gt)) genres.add(gt)
            }

            val status = el.selectFirst(".set:contains(Status)")?.text()
                ?.replace("Status :", "")?.replace("Status:", "")?.trim() ?: "Otakudesu"

            if (title.isNotBlank()) {
                items.add(
                    MediaItem(
                        id = href,
                        title = title,
                        category = CategoryType.ANIME,
                        thumbnail = thumb,
                        url = href,
                        slug = href,
                        badge = status,
                        genres = genres
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

        // If targetUrl is an episode URL, find link back to anime series page
        if (!targetUrl.contains("/anime/")) {
            val seriesLink = doc.selectFirst("a[href*='/anime/']")?.attr("href")
            if (!seriesLink.isNullOrEmpty() && seriesLink.contains("/anime/")) {
                val seriesHtml = fetchHtml(seriesLink)
                if (seriesHtml != null) {
                    targetUrl = seriesLink
                    html = seriesHtml
                    doc = Jsoup.parse(seriesHtml)
                }
            }
        }

        val title = doc.selectFirst(".infozingle p:contains(Judul) span")?.text()
            ?.replace("Judul:", "")?.replace("Judul :", "")?.trim()
            ?: doc.selectFirst(".jdlrx h1, h1")?.text()?.trim()
            ?: "Anime Detail"

        val imgEl = doc.selectFirst(".fotoanime img, img.attachment-post-thumbnail")
        val thumbnail = imgEl?.attr("src")?.ifEmpty { null }
            ?: imgEl?.attr("data-src") ?: ""

        val synopsis = doc.selectFirst(".sinopc, .desc, .entry-content")?.text()?.trim()

        val genres = mutableListOf<String>()
        doc.select(".infozingle a[href*='/genres/'], .infozingle p:contains(Genre) a").forEach { g ->
            val t = g.text().trim()
            if (t.isNotBlank() && !genres.contains(t)) genres.add(t)
        }

        val score = doc.selectFirst(".infozingle p:contains(Skor)")?.text()
            ?.replace("Skor:", "")?.replace("Skor :", "")?.trim() ?: "8.5"

        val status = doc.selectFirst(".infozingle p:contains(Status)")?.text()
            ?.replace("Status:", "")?.replace("Status :", "")?.trim() ?: "Ongoing"

        val episodes = mutableListOf<EpisodeItem>()
        doc.select(".episodelist ul li").forEach { li ->
            val link = li.selectFirst("a[href*='/episode/']")
            val epUrl = link?.attr("href") ?: return@forEach
            val epTitle = link.text().trim()
            val epDate = li.selectFirst(".zeebr")?.text()?.trim()

            val epNum = Regex("""episode-(\d+)""", RegexOption.IGNORE_CASE).find(epUrl)?.groupValues?.get(1)
                ?: Regex("""\b(\d+)\b""").find(epTitle)?.groupValues?.get(1) ?: ""

            if (!episodes.any { it.url == epUrl }) {
                episodes.add(
                    EpisodeItem(
                        id = epUrl,
                        episodeNumber = epNum.ifBlank { (episodes.size + 1).toString() },
                        title = epTitle,
                        url = epUrl,
                        date = epDate
                    )
                )
            }
        }

        // Sort so Episode 1 is at the top
        episodes.sortBy { it.episodeNumber.toIntOrNull() ?: 0 }

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

        MediaDetail(
            id = targetUrl,
            title = title,
            category = CategoryType.ANIME,
            thumbnail = thumbnail,
            synopsis = synopsis,
            genres = genres,
            status = status,
            rating = score,
            totalEpisodes = "${episodes.size} Episode",
            episodes = episodes
        )
    }

    private val weeklyScheduleCache = java.util.concurrent.ConcurrentHashMap<Int, List<MediaItem>>()

    suspend fun getSchedule(dayIndex: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val targetDay = dayNames.getOrElse(dayIndex) { "Senin" }

        // 1. Check memory cache first
        weeklyScheduleCache[dayIndex]?.let {
            if (it.isNotEmpty()) return@withContext it
        }

        // 2. Fetch official Jadwal Rilis page (fast & complete)
        try {
            val html = fetchHtml("$BASE_URL/jadwal-rilis/")
            if (!html.isNullOrBlank()) {
                val doc = Jsoup.parse(html)
                val dayMap = mutableMapOf<Int, MutableList<MediaItem>>()
                (0..6).forEach { dayMap[it] = mutableListOf() }

                doc.select(".kglist321").forEach { block ->
                    val header = block.selectFirst("h2")?.text()?.trim() ?: ""
                    val matchedDayIdx = dayNames.indexOfFirst { header.contains(it, ignoreCase = true) }
                    if (matchedDayIdx != -1) {
                        val currentDayName = dayNames[matchedDayIdx]
                        block.select("ul li a").forEach { a ->
                            val href = a.attr("href").trim()
                            val title = a.text().trim()
                            if (href.startsWith("http") && title.isNotBlank()) {
                                dayMap[matchedDayIdx]?.add(
                                    MediaItem(
                                        id = href,
                                        title = title,
                                        category = CategoryType.ANIME,
                                        thumbnail = "", // Cached/updated or fallback
                                        url = href,
                                        slug = href,
                                        badge = "$currentDayName • Ongoing"
                                    )
                                )
                            }
                        }
                    }
                }

                // Cache all days in memory
                dayMap.forEach { (idx, list) ->
                    if (list.isNotEmpty()) {
                        weeklyScheduleCache[idx] = list
                    }
                }

                val result = dayMap[dayIndex].orEmpty()
                if (result.isNotEmpty()) {
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: parse from ongoing anime page
        try {
            val p1 = getLatest(1)
            val dayItems = p1.filter { it.badge?.contains(targetDay, ignoreCase = true) == true }
            if (dayItems.isNotEmpty()) {
                weeklyScheduleCache[dayIndex] = dayItems
                return@withContext dayItems
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        getFallbackSchedule(dayIndex)
    }

    private fun getFallbackSchedule(dayIndex: Int): List<MediaItem> {
        return when (dayIndex) {
            0 -> listOf( // Senin
                MediaItem("otaku_sch_1", "Kuroneko to Majo no Kyoushitsu", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/04/Kuroneko-to-Majo-no-Kyoushitsu.jpg", "https://otakudesu.blog/anime/kuroneko-to-majo-no-kyoushitsu-sub-indo/", "https://otakudesu.blog/anime/kuroneko-to-majo-no-kyoushitsu-sub-indo/", "Senin • Ep 24"),
                MediaItem("otaku_sch_2", "Sayonara Lara", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/Sayonara-Lara.jpg", "https://otakudesu.blog/anime/sayonara-lara-sub-indo/", "https://otakudesu.blog/anime/sayonara-lara-sub-indo/", "Senin • Ep 12")
            )
            1 -> listOf( // Selasa
                MediaItem("otaku_sch_3", "Toumei na Yoru ni Kakeru Kimi", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/Toumei-na-Yoru-ni-Kakeru-Kimi-to-Me-ni-Mienai-Koi-wo-Shita.-Sub.jpg", "https://otakudesu.blog/anime/toumei-yoru-kakeru-kimi-sub-indo/", "https://otakudesu.blog/anime/toumei-yoru-kakeru-kimi-sub-indo/", "Selasa • Ep 12"),
                MediaItem("otaku_sch_4", "Grand Blue Season 3", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/06/158709.jpg", "https://otakudesu.blog/anime/grand-blue-s3-sub-indo/", "https://otakudesu.blog/anime/grand-blue-s3-sub-indo/", "Selasa • Ep 10")
            )
            2 -> listOf( // Rabu
                MediaItem("otaku_sch_5", "Clevatess Season 2", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/158340.jpg", "https://otakudesu.blog/anime/clevatess-s2-sub-indo/", "https://otakudesu.blog/anime/clevatess-s2-sub-indo/", "Rabu • Ep 8"),
                MediaItem("otaku_sch_6", "Re:Zero Season 3", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/158475.jpg", "https://otakudesu.blog/anime/rezero-s3-sub-indo/", "https://otakudesu.blog/anime/rezero-s3-sub-indo/", "Rabu • Ep 12")
            )
            3 -> listOf( // Kamis
                MediaItem("otaku_sch_7", "Hanazakari no Kimitachi e S2", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/158475.jpg", "https://otakudesu.blog/anime/hanazakari-kimitachi-s2-sub-indo/", "https://otakudesu.blog/anime/hanazakari-kimitachi-s2-sub-indo/", "Kamis • Ep 11"),
                MediaItem("otaku_sch_8", "Katainaka Ossan Kensei", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/06/158709.jpg", "https://otakudesu.blog/anime/katainaka-ossan-sub-indo/", "https://otakudesu.blog/anime/katainaka-ossan-sub-indo/", "Kamis • Ep 9")
            )
            4 -> listOf( // Jumat
                MediaItem("otaku_sch_9", "Dr. Stone Science Future Part 3", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/Gaikotsu-Kishi-sama-Tadaima-Isekai-e-Odekakechuu-Season-2-Sub-Indo.jpg", "https://otakudesu.blog/anime/ds-future-part3-sub-indo/", "https://otakudesu.blog/anime/ds-future-part3-sub-indo/", "Jumat • Ep 6"),
                MediaItem("otaku_sch_10", "Blue Lock Season 2", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/04/Kuroneko-to-Majo-no-Kyoushitsu.jpg", "https://otakudesu.blog/anime/blue-lock-s2-sub-indo/", "https://otakudesu.blog/anime/blue-lock-s2-sub-indo/", "Jumat • Ep 14")
            )
            5 -> listOf( // Sabtu
                MediaItem("otaku_sch_11", "World Is Dancing", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/06/158709.jpg", "https://otakudesu.blog/anime/world-is-dancing-sub-indo/", "https://otakudesu.blog/anime/world-is-dancing-sub-indo/", "Sabtu • Ep 12"),
                MediaItem("otaku_sch_12", "Gaikotsu Kishi-sama Season 2", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/Gaikotsu-Kishi-sama-Tadaima-Isekai-e-Odekakechuu-Season-2-Sub-Indo.jpg", "https://otakudesu.blog/anime/gaikotsu-kishi-s2-sub-indo/", "https://otakudesu.blog/anime/gaikotsu-kishi-s2-sub-indo/", "Sabtu • Ep 12")
            )
            else -> listOf( // Minggu
                MediaItem("otaku_sch_13", "One Piece", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2021/05/One-Piece-Sub-Indo.jpg", "https://otakudesu.blog/anime/1piece-sub-indo/", "https://otakudesu.blog/anime/1piece-sub-indo/", "Minggu • Ep 1179"),
                MediaItem("otaku_sch_14", "Mushoku Tensei Season 3", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/158340.jpg", "https://otakudesu.blog/anime/mushoku-ni-tensei-s3-sub-indo/", "https://otakudesu.blog/anime/mushoku-ni-tensei-s3-sub-indo/", "Minggu • Ep 13"),
                MediaItem("otaku_sch_15", "Sekai Saikyou no Kouei", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/Sekai-Saikyou-no-Kouei-Sub.jpg", "https://otakudesu.blog/anime/sekai-saikyou-kouei-sub-indo/", "https://otakudesu.blog/anime/sekai-saikyou-kouei-sub-indo/", "Minggu • Ep 12")
            )
        }
    }

    suspend fun getStream(urlInput: String): StreamResult? = withContext(Dispatchers.IO) {
        var targetUrl = if (urlInput.startsWith("http")) urlInput else "$BASE_URL/episode/$urlInput/"

        // If targetUrl is an anime series URL (e.g. /anime/), resolve the first episode from getDetail
        if (targetUrl.contains("/anime/", ignoreCase = true) || !targetUrl.contains("/episode/", ignoreCase = true)) {
            val detail = getDetail(targetUrl)
            val firstEp = detail?.episodes?.firstOrNull()?.url
            if (!firstEp.isNullOrBlank() && firstEp != targetUrl) {
                targetUrl = firstEp
            }
        }

        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.posttl, h1")?.text()?.trim() ?: "Anime Episode"
        val servers = mutableListOf<StreamServerItem>()
        var primaryHlsUrl: String? = null

        // 1. Prioritize Vidhide & Dynamic mirrors from .mirrorstream (Provides 100% UNBLOCKED DIRECT HLS .m3u8)
        try {
            val dataContents = doc.select(".mirrorstream a[data-content]")
            if (dataContents.isNotEmpty()) {
                val nonceJson = postAjax(mapOf("action" to "aa1208d27f29ca340c92c66d1926f13f"))
                val nonce = nonceJson?.let { JSONObject(it).optString("data") }
                if (!nonce.isNullOrBlank()) {
                    for (el in dataContents) {
                        try {
                            val b64 = el.attr("data-content")
                            if (b64.isBlank()) continue
                            val contentJson = JSONObject(String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8))
                            val q = contentJson.optString("q")
                            val isVidhide = el.text().contains("vidhide", ignoreCase = true)
                            val isFiledon = el.text().contains("filedon", ignoreCase = true)
                            val isMega = el.text().contains("mega", ignoreCase = true)

                            // Prioritize Vidhide for direct HLS .m3u8
                            if (isVidhide || isFiledon || isMega) {
                                val mirrorResp = postAjax(
                                    mapOf(
                                        "id" to contentJson.optString("id"),
                                        "i" to contentJson.optString("i"),
                                        "q" to q,
                                        "nonce" to nonce,
                                        "action" to "2a3505c93b0035d3f455df82bf976b84"
                                    )
                                )
                                val rawB64 = mirrorResp?.let { JSONObject(it).optString("data") }
                                if (!rawB64.isNullOrBlank()) {
                                    val mHtml = String(Base64.decode(rawB64, Base64.DEFAULT), Charsets.UTF_8)
                                    val mSrc = Regex("""src=["']([^"']+)["']""").find(mHtml)?.groupValues?.get(1)
                                    if (!mSrc.isNullOrBlank()) {
                                        if (isVidhide) {
                                            val directHls = StreamResolver.extractVidhideHls(mSrc, "$BASE_URL/")
                                            if (!directHls.isNullOrBlank() && !servers.any { it.url == directHls }) {
                                                if (primaryHlsUrl == null && (q == "720p" || q == "480p")) {
                                                    primaryHlsUrl = directHls
                                                }
                                                servers.add(
                                                    StreamServerItem(
                                                        name = "Otaku Server ($q Direct HLS)",
                                                        url = directHls,
                                                        isDirectHls = true
                                                    )
                                                )
                                            }
                                        } else {
                                            val name = if (isFiledon) "Filedon ($q)" else "Mega ($q)"
                                            if (!servers.any { it.url == mSrc }) {
                                                servers.add(StreamServerItem(name = name, url = mSrc, isDirectHls = false))
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore mirror parse error
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Direct iframes on the episode page (DesuStream, OK.ru, Blogger)
        doc.select("#pembed iframe, .responsive-embed-stream iframe, iframe").forEach { iframe ->
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.isNotBlank() && src.startsWith("http") && !src.contains("about:blank")) {
                if (src.contains("desustream.net")) {
                    try {
                        val desuHtml = fetchHtml(src, referer = "$BASE_URL/")
                        if (desuHtml != null) {
                            val directMatch = Regex("""videoURL\s*=\s*["']([^"']+)["']""").find(desuHtml)
                                ?: Regex("""(https?://[^\s"']+\.mp4[^\s"']*)""").find(desuHtml)
                                ?: Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""").find(desuHtml)
                            val directUrl = directMatch?.groupValues?.get(1)
                            if (!directUrl.isNullOrBlank() && directUrl.startsWith("http") && !directUrl.endsWith("/.mp4") && !directUrl.contains("/download/.mp4")) {
                                servers.add(
                                    StreamServerItem(
                                        name = "DesuStream (Direct MP4)",
                                        url = directUrl,
                                        isDirectHls = true
                                    )
                                )
                            } else {
                                servers.add(StreamServerItem(name = "DesuStream Player", url = src, isDirectHls = false))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (src.contains("ok.ru/videoembed/")) {
                    val okDirect = StreamResolver.extractOkRuDirect(src, "$BASE_URL/")
                    if (!okDirect.isNullOrBlank()) {
                        servers.add(StreamServerItem("OK.ru Direct Stream", okDirect, isDirectHls = true))
                    }
                    servers.add(StreamServerItem("OK.ru Player", src, isDirectHls = false))
                } else {
                    val name = when {
                        src.contains("blogger.com") -> "Blogger Player"
                        src.contains("ok.ru") -> "OK.ru Player"
                        else -> "Server ${servers.size + 1}"
                    }
                    servers.add(StreamServerItem(name = name, url = src, isDirectHls = false))
                }
            }
        }

        // 3. Fallback to download links if needed
        doc.select(".download ul li a").forEach { a ->
            val href = a.attr("href")
            val quality = a.parent()?.selectFirst("strong")?.text()?.trim() ?: "HD"
            val host = a.text().trim()
            if (href.startsWith("http") && (href.contains(".mp4") || href.contains("stream") || href.contains("pixeldrain") || href.contains("gofile"))) {
                val isMp4 = href.contains(".mp4") && !href.endsWith("/.mp4") && !href.contains("/download/.mp4")
                if (!servers.any { it.url == href }) {
                    servers.add(StreamServerItem(name = "$host ($quality)", url = href, isDirectHls = isMp4))
                }
            }
        }

        if (servers.isEmpty()) {
            servers.add(StreamServerItem(name = "Otakudesu Web Player", url = targetUrl, isDirectHls = false))
        }

        // Sort so Otaku Direct HLS servers are ALWAYS first!
        val sortedServers = servers.sortedWith(
            compareBy<StreamServerItem>(
                { if (it.name.contains("Direct HLS", ignoreCase = true)) 0 else if (it.isDirectHls) 1 else 2 },
                { if (it.name.contains("720p", ignoreCase = true)) 0 else if (it.name.contains("480p", ignoreCase = true)) 1 else 2 }
            )
        )

        val finalDirect = sortedServers.firstOrNull { it.name.contains("Direct HLS", ignoreCase = true) }?.url
            ?: primaryHlsUrl
            ?: sortedServers.firstOrNull { it.isDirectHls }?.url

        StreamResult(
            title = title,
            directHlsUrl = finalDirect,
            iframePlayerUrl = sortedServers.firstOrNull { !it.isDirectHls }?.url,
            servers = sortedServers
        )
    }
}
