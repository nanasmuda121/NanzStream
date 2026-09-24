package com.nanzstream.nanas.data.scraper

import android.util.Base64
import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

object DrakorScraper {

    private const val BASE_URL = "https://drakorid.co"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private suspend fun fetchHtml(url: String, referer: String = BASE_URL): String? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", referer)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()
                val res = ApiClient.okHttpClient.newCall(req).execute()
                if (res.isSuccessful) res.body?.string() else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun getLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val targetUrl = "$BASE_URL/list/$page"
        val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".grid-item, .item, article, a[href*='/nonton/']").forEach { el ->
            val linkEl = if (el.tagName() == "a") el else el.selectFirst("a[href*='/nonton/']")
            val href = linkEl?.attr("href") ?: ""
            if (href.contains("/nonton/")) {
                val slugMatch = Regex("""/nonton/([^/]+)""").find(href)
                val slug = slugMatch?.groupValues?.get(1) ?: return@forEach
                if (items.any { it.slug == slug }) return@forEach

                val title = el.selectFirst(".title, h2, h3, .entry-title")?.text()?.trim()
                    ?: linkEl.text().trim()
                val img = el.selectFirst("img")
                val thumb = img?.attr("data-src")?.ifEmpty { null }
                    ?: img?.attr("src") ?: ""

                val cleanThumb = if (thumb.startsWith("//")) "https:$thumb" else thumb

                if (title.isNotBlank()) {
                    items.add(
                        MediaItem(
                            id = slug,
                            title = title,
                            category = CategoryType.DRAMA,
                            thumbnail = cleanThumb,
                            slug = slug,
                            url = href,
                            badge = "Drakor"
                        )
                    )
                }
            }
        }
        items
    }

    suspend fun search(query: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val targetUrl = "$BASE_URL/cari.html?q=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page"
        val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".grid-item, .item, article, a[href*='/nonton/']").forEach { el ->
            val linkEl = if (el.tagName() == "a") el else el.selectFirst("a[href*='/nonton/']")
            val href = linkEl?.attr("href") ?: ""
            if (href.contains("/nonton/")) {
                val slugMatch = Regex("""/nonton/([^/]+)""").find(href)
                val slug = slugMatch?.groupValues?.get(1) ?: return@forEach
                if (items.any { it.slug == slug }) return@forEach

                val title = el.selectFirst(".title, h2, h3, .entry-title")?.text()?.trim()
                    ?: linkEl.text().trim()
                val img = el.selectFirst("img")
                val thumb = img?.attr("data-src")?.ifEmpty { null }
                    ?: img?.attr("src") ?: ""

                val cleanThumb = if (thumb.startsWith("//")) "https:$thumb" else thumb

                if (title.isNotBlank()) {
                    items.add(
                        MediaItem(
                            id = slug,
                            title = title,
                            category = CategoryType.DRAMA,
                            thumbnail = cleanThumb,
                            slug = slug,
                            url = href,
                            badge = "Drakor"
                        )
                    )
                }
            }
        }
        items
    }

    suspend fun getDetail(slugInput: String): MediaDetail? = withContext(Dispatchers.IO) {
        val cleanSlug = if (slugInput.contains("/nonton/")) {
            Regex("""/nonton/([^/]+)""").find(slugInput)?.groupValues?.get(1) ?: slugInput
        } else slugInput

        val targetUrl = "$BASE_URL/nonton/$cleanSlug/"
        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.entry-title, h1, .title")?.text()?.trim() ?: cleanSlug
        val imgEl = doc.selectFirst(".thumb img, .poster img, .entry-content img")
        val thumbnail = imgEl?.attr("data-src")?.ifEmpty { null } ?: imgEl?.attr("src") ?: ""
        val synopsis = doc.selectFirst(".sinopsis, .entry-content p, .desc")?.text()?.trim()

        val genres = mutableListOf<String>()
        doc.select(".genre a, .genres a").forEach { g ->
            val t = g.text().trim()
            if (t.isNotBlank() && !genres.contains(t)) genres.add(t)
        }

        val episodes = mutableListOf<EpisodeItem>()
        // Parse episodes from watch links
        doc.select("a[href*='/watch-'], a[href*='/download-'], .episodes a").forEach { a ->
            val href = a.attr("href")
            val epMatch = Regex("""/watch-[^/]+/([^/]+)/(\d+)""").find(href)
            if (epMatch != null) {
                val epNum = epMatch.groupValues[2]
                if (!episodes.any { it.episodeNumber == epNum }) {
                    episodes.add(
                        EpisodeItem(
                            id = epNum,
                            episodeNumber = epNum,
                            title = "Episode $epNum",
                            url = cleanSlug
                        )
                    )
                }
            }
        }

        // Fallback: if episodes list is empty, generate from total episodes text
        if (episodes.isEmpty()) {
            val totalMatch = Regex("""(\d+)\s*eps|episode\s*(\d+)""", RegexOption.IGNORE_CASE).find(doc.text())
            val count = totalMatch?.groupValues?.get(1)?.toIntOrNull()
                ?: totalMatch?.groupValues?.get(2)?.toIntOrNull() ?: 16
            for (i in 1..count.coerceIn(1, 32)) {
                episodes.add(EpisodeItem(id = i.toString(), episodeNumber = i.toString(), title = "Episode $i", url = cleanSlug))
            }
        }

        episodes.sortBy { it.episodeNumber.toIntOrNull() ?: 0 }

        MediaDetail(
            id = cleanSlug,
            title = title,
            category = CategoryType.DRAMA,
            thumbnail = thumbnail,
            synopsis = synopsis,
            genres = genres,
            status = "Ongoing",
            rating = "9.0",
            totalEpisodes = "${episodes.size} Episode",
            episodes = episodes
        )
    }

    suspend fun getStream(slugInput: String, episode: Int, server: String = "lite"): StreamResult? =
        withContext(Dispatchers.IO) {
            val cleanSlug = if (slugInput.contains("/nonton/")) {
                Regex("""/nonton/([^/]+)""").find(slugInput)?.groupValues?.get(1) ?: slugInput
            } else slugInput

            val watchUrl = "$BASE_URL/watch-$server/$cleanSlug/$episode"
            val html = fetchHtml(watchUrl, referer = "$BASE_URL/nonton/$cleanSlug/") ?: return@withContext null
            val doc = Jsoup.parse(html)

            var directHls: String? = null
            var iframePlayer: String? = null
            val servers = mutableListOf<StreamServerItem>()

            doc.select("iframe").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.contains("player/bunny.php") || src.contains("player/")) {
                    if (iframePlayer == null) iframePlayer = src

                    try {
                        val uri = android.net.Uri.parse(src)
                        val vParam = uri.getQueryParameter("v")
                        if (!vParam.isNullOrEmpty()) {
                            val decodedBytes = Base64.decode(vParam, Base64.DEFAULT)
                            val decodedStream = String(decodedBytes, Charsets.UTF_8)
                            if (directHls == null) directHls = decodedStream
                            servers.add(StreamServerItem("Direct HLS (Bunny)", decodedStream, isDirectHls = true))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (iframePlayer != null) {
                servers.add(StreamServerItem("Server $server (Embed)", iframePlayer ?: "", isDirectHls = false))
            }

            // Also check fast & max servers
            if (server == "lite") {
                servers.add(StreamServerItem("Server Fast (Alternative)", "$BASE_URL/watch-fast/$cleanSlug/$episode", isDirectHls = false))
                servers.add(StreamServerItem("Server Max (Alternative)", "$BASE_URL/watch-max/$cleanSlug/$episode", isDirectHls = false))
            }

            StreamResult(
                title = "Drama $cleanSlug - Episode $episode",
                directHlsUrl = directHls,
                iframePlayerUrl = iframePlayer ?: directHls,
                servers = servers
            )
        }
}
