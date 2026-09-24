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
        val targetUrl = if (urlInput.startsWith("http")) urlInput else "$BASE_URL/$urlInput/"
        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

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
        // Check .eplister or .episodes-ul
        doc.select(".eplister ul li, .episodes-ul a, a[href*='episode']").forEachIndexed { i, el ->
            val href = if (el.tagName() == "a") el.attr("href") else el.selectFirst("a")?.attr("href") ?: ""
            val epNum = el.selectFirst(".epl-num")?.text()?.trim() ?: (i + 1).toString()
            val epTitle = el.selectFirst(".epl-title")?.text()?.trim() ?: "Episode $epNum"

            if (href.isNotBlank() && !episodes.any { it.url == href }) {
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

        MediaDetail(
            id = targetUrl,
            title = title,
            category = CategoryType.DONGHUA,
            thumbnail = thumbnail,
            synopsis = synopsis,
            genres = genres,
            status = "Ongoing",
            rating = "8.8",
            totalEpisodes = "${episodes.size} Episode",
            episodes = episodes
        )
    }

    suspend fun getStream(urlInput: String): StreamResult? = withContext(Dispatchers.IO) {
        val targetUrl = if (urlInput.startsWith("http")) urlInput else "$BASE_URL/$urlInput/"
        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.entry-title, h1")?.text()?.trim() ?: "Donghua Episode"
        val servers = mutableListOf<StreamServerItem>()

        // Check iframes
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.isNotBlank() && !src.contains("about:blank") && !servers.any { it.url == src }) {
                val name = if (src.contains("ok.ru")) "OK.ru Player"
                else if (src.contains("dailymotion")) "Dailymotion"
                else "Default Player"
                servers.add(StreamServerItem(name, src, isDirectHls = false))
            }
        }

        // Check server tabs
        doc.select(".server-item a, ul.mirror li a").forEachIndexed { i, el ->
            val serverName = el.text().trim().ifBlank { "Server ${i + 1}" }
            val dataHash = el.attr("data-hash").ifEmpty { el.attr("data-video") }

            if (dataHash.isNotBlank()) {
                try {
                    var decoded = dataHash
                    if (dataHash.length > 20) {
                        val bytes = Base64.decode(dataHash, Base64.DEFAULT)
                        decoded = String(bytes, Charsets.UTF_8)
                    }
                    val match = Regex("""src=["']([^"']+)["']""").find(decoded)
                    val iframeUrl = match?.groupValues?.get(1) ?: if (decoded.startsWith("http")) decoded else null

                    if (!iframeUrl.isNullOrEmpty() && !servers.any { it.url == iframeUrl }) {
                        servers.add(StreamServerItem(serverName, iframeUrl, isDirectHls = false))
                    }
                } catch (e: Exception) {
                    // Ignore
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
