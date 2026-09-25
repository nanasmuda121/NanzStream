package com.nanzstream.nanas.data.scraper

import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
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

    suspend fun getStream(urlInput: String): StreamResult? = withContext(Dispatchers.IO) {
        val targetUrl = if (urlInput.startsWith("http")) urlInput else "$BASE_URL/episode/$urlInput/"
        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.posttl, h1")?.text()?.trim() ?: "Anime Episode"
        val servers = mutableListOf<StreamServerItem>()

        // 1. Direct iframes on the episode page
        doc.select("#pembed iframe, .responsive-embed-stream iframe, iframe").forEach { iframe ->
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.isNotBlank() && src.startsWith("http") && !src.contains("about:blank")) {
                // If it's a desustream player, resolve direct mp4
                if (src.contains("desustream.net")) {
                    try {
                        val desuHtml = fetchHtml(src, referer = "$BASE_URL/")
                        if (desuHtml != null) {
                            val directMatch = Regex("""videoURL\s*=\s*["']([^"']+)["']""").find(desuHtml)
                            val directUrl = directMatch?.groupValues?.get(1)
                            if (!directUrl.isNullOrBlank() && directUrl.startsWith("http")) {
                                servers.add(
                                    StreamServerItem(
                                        name = "Otaku Server (720p Direct)",
                                        url = directUrl
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Add embed iframe
                val name = when {
                    src.contains("blogger.com") -> "Blogger Player"
                    src.contains("desustream") -> "DesuStream Player"
                    src.contains("ok.ru") -> "OK.ru Player"
                    else -> "Server ${servers.size + 1}"
                }
                servers.add(StreamServerItem(name = name, url = src))
            }
        }

        // 2. Download / mirror stream links if available
        doc.select(".download ul li a").forEach { a ->
            val href = a.attr("href")
            val quality = a.parent()?.selectFirst("strong")?.text()?.trim() ?: "HD"
            val host = a.text().trim()
            if (href.startsWith("http") && (href.contains("mp4") || href.contains("stream") || href.contains("pixeldrain") || href.contains("gofile"))) {
                servers.add(StreamServerItem(name = "$host ($quality)", url = href))
            }
        }

        if (servers.isEmpty()) {
            servers.add(StreamServerItem(name = "Otakudesu Web Player", url = targetUrl))
        }

        StreamResult(
            title = title,
            servers = servers
        )
    }
}
