package com.nanzstream.nanas.data.scraper

import android.util.Base64
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.EpisodeItem
import com.nanzstream.nanas.data.model.MediaDetail
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.model.StreamResult
import com.nanzstream.nanas.data.model.StreamServerItem
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

object AnimasuScraper {

    const val BASE_URL = "https://animasu.love"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private suspend fun fetchHtml(url: String, referer: String = "$BASE_URL/"): String? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", referer)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()
                val resp = ApiClient.okHttpClient.newCall(req).execute()
                resp.body?.string()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Get latest ongoing anime from animasu.love
     */
    suspend fun getLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val url = if (page <= 1) "$BASE_URL/" else "$BASE_URL/page/$page/"
        val html = fetchHtml(url) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".bsx, article.bs, .animepost").forEach { el ->
            val a = el.selectFirst("a") ?: return@forEach
            val href = a.attr("href").trim()
            if (href.isBlank() || !href.contains("/anime/")) return@forEach

            val title = el.selectFirst(".tt")?.text()?.trim()
                ?: a.attr("title").replace("Nonton Anime ", "").replace(" Sub Indo", "").trim()
                ?: el.selectFirst("h4")?.text()?.trim()
                ?: ""
            if (title.isBlank()) return@forEach

            val imgEl = el.selectFirst("img")
            val thumbnail = imgEl?.attr("data-src")?.ifEmpty { imgEl.attr("data-lazy-src") }?.ifEmpty { imgEl.attr("src") } ?: ""

            val epText = el.selectFirst(".epx")?.text()?.trim()
                ?: el.selectFirst(".bt .sb")?.text()?.trim()
                ?: el.selectFirst(".typez")?.text()?.trim()
                ?: "Anime"

            val rating = el.selectFirst(".rating strong")?.text()?.trim()
                ?: el.selectFirst(".typez")?.text()?.trim()

            val slug = href.trimEnd('/').substringAfterLast('/')

            if (items.none { it.id == slug || it.url == href }) {
                items.add(
                    MediaItem(
                        id = slug,
                        title = title,
                        category = CategoryType.ANIME,
                        thumbnail = thumbnail,
                        url = href,
                        slug = slug,
                        badge = epText,
                        rating = rating
                    )
                )
            }
        }
        items
    }

    /**
     * Get popular anime from animasu.love/populer/
     */
    suspend fun getPopular(): List<MediaItem> = withContext(Dispatchers.IO) {
        val html = fetchHtml("$BASE_URL/populer/") ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".bsx").forEach { el ->
            val a = el.selectFirst("a") ?: return@forEach
            val href = a.attr("href").trim()
            if (href.isBlank() || !href.contains("/anime/")) return@forEach

            val title = el.selectFirst(".tt")?.text()?.trim() ?: a.attr("title").trim()
            val imgEl = el.selectFirst("img")
            val thumbnail = imgEl?.attr("data-src")?.ifEmpty { imgEl.attr("data-lazy-src") }?.ifEmpty { imgEl.attr("src") } ?: ""
            val slug = href.trimEnd('/').substringAfterLast('/')

            if (items.none { it.id == slug || it.url == href }) {
                items.add(
                    MediaItem(
                        id = slug,
                        title = title,
                        category = CategoryType.ANIME,
                        thumbnail = thumbnail,
                        url = href,
                        slug = slug,
                        badge = "Populer 🔥"
                    )
                )
            }
        }
        items
    }

    /**
     * Get schedule by day from animasu.love/jadwal/
     */
    suspend fun getSchedule(dayIndex: Int = 0): List<MediaItem> = withContext(Dispatchers.IO) {
        val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val targetDay = days.getOrElse(dayIndex.coerceIn(0, 6)) { "Senin" }
        val html = fetchHtml("$BASE_URL/jadwal/") ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".bixbox").forEach { box ->
            val h3 = box.selectFirst("h3")?.text()?.trim() ?: ""
            if (h3.contains(targetDay, ignoreCase = true) || (targetDay == "Jumat" && h3.contains("Jum'at", ignoreCase = true))) {
                box.select(".bsx").forEach { el ->
                    val a = el.selectFirst("a") ?: return@forEach
                    val href = a.attr("href").trim()
                    val title = el.selectFirst(".tt")?.text()?.trim() ?: a.attr("title").trim()
                    val imgEl = el.selectFirst("img")
                    val thumbnail = imgEl?.attr("data-src")?.ifEmpty { imgEl.attr("data-lazy-src") }?.ifEmpty { imgEl.attr("src") } ?: ""
                    val slug = href.trimEnd('/').substringAfterLast('/')

                    if (items.none { it.id == slug || it.url == href }) {
                        items.add(
                            MediaItem(
                                id = slug,
                                title = title,
                                category = CategoryType.ANIME,
                                thumbnail = thumbnail,
                                url = href,
                                slug = slug,
                                badge = "$targetDay • Rilis"
                            )
                        )
                    }
                }
            }
        }
        if (items.isEmpty()) {
            getLatest(1).take(15)
        } else {
            items
        }
    }

    /**
     * Search anime on animasu.love
     */
    suspend fun search(query: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = if (page <= 1) "$BASE_URL/?s=$encoded" else "$BASE_URL/page/$page/?s=$encoded"
        val html = fetchHtml(url) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".bsx, article.bs, .animepost").forEach { el ->
            val a = el.selectFirst("a") ?: return@forEach
            val href = a.attr("href").trim()
            if (href.isBlank() || !href.contains("/anime/")) return@forEach

            val title = el.selectFirst(".tt")?.text()?.trim()
                ?: a.attr("title").replace("Nonton Anime ", "").replace(" Sub Indo", "").trim()
                ?: el.selectFirst("h4")?.text()?.trim()
                ?: ""
            if (title.isBlank()) return@forEach

            val imgEl = el.selectFirst("img")
            val thumbnail = imgEl?.attr("data-src")?.ifEmpty { imgEl.attr("data-lazy-src") }?.ifEmpty { imgEl.attr("src") } ?: ""
            val epText = el.selectFirst(".epx")?.text()?.trim() ?: "Anime"
            val slug = href.trimEnd('/').substringAfterLast('/')

            if (items.none { it.id == slug || it.url == href }) {
                items.add(
                    MediaItem(
                        id = slug,
                        title = title,
                        category = CategoryType.ANIME,
                        thumbnail = thumbnail,
                        url = href,
                        slug = slug,
                        badge = epText
                    )
                )
            }
        }
        items
    }

    /**
     * Get detail and episode list of an anime
     */
    suspend fun getDetail(idOrSlug: String): MediaDetail? = withContext(Dispatchers.IO) {
        val targetUrl = when {
            idOrSlug.startsWith("http") -> idOrSlug
            idOrSlug.startsWith("anime/") -> "$BASE_URL/$idOrSlug"
            else -> "$BASE_URL/anime/$idOrSlug"
        }
        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: doc.title().replace("Nonton Anime ", "").substringBefore(" Subtitle Indonesia").trim()

        val imgEl = doc.selectFirst(".thumb img") ?: doc.selectFirst(".infox img")
        val thumbnail = imgEl?.attr("data-src")?.ifEmpty { imgEl.attr("data-lazy-src") }?.ifEmpty { imgEl.attr("src") } ?: ""

        val synopsis = doc.selectFirst(".entry-content[itemprop=description]")?.text()?.trim()
            ?: doc.selectFirst(".entry-content")?.text()?.trim()
            ?: doc.selectFirst(".desc")?.text()?.trim()
            ?: ""

        val genres = doc.select("a[href*=/genre/], a[href*=/genres/]").map { it.text().trim() }
            .filter { it.isNotBlank() && !it.equals("donghua", ignoreCase = true) }
            .distinct()

        val rating = doc.selectFirst(".rating strong")?.text()?.trim()
            ?: doc.selectFirst(".rating .num")?.text()?.trim()

        val status = doc.selectFirst("span:contains(Status)")?.text()?.replace("Status:", "")?.trim()

        val episodes = mutableListOf<EpisodeItem>()

        // Episodes are in .bxcl ul li or .eplister li or a[href*=-episode-]
        doc.select(".bxcl ul li, .eplister li, .episodelist ul li").forEachIndexed { idx, li ->
            val a = li.selectFirst("a") ?: return@forEachIndexed
            val href = a.attr("href").trim()
            if (href.isBlank() || (!href.contains("episode") && !href.contains("nonton-"))) return@forEachIndexed

            val epTitle = a.text().trim().ifEmpty { "Episode ${idx + 1}" }
            val epNum = Regex("""\b(\d+)\b""").find(epTitle)?.groupValues?.get(1) ?: "${idx + 1}"
            val date = li.selectFirst(".dt")?.text()?.trim() ?: li.selectFirst(".date")?.text()?.trim()

            if (episodes.none { it.url == href }) {
                episodes.add(
                    EpisodeItem(
                        id = href,
                        episodeNumber = epNum,
                        title = epTitle,
                        url = href,
                        date = date,
                        thumbnail = thumbnail
                    )
                )
            }
        }

        // If no episodes found with the above, try searching any episode link in the page
        if (episodes.isEmpty()) {
            doc.select("a[href*=-episode-]").forEachIndexed { idx, a ->
                val href = a.attr("href").trim()
                if (href.isNotBlank() && episodes.none { it.url == href }) {
                    val epTitle = a.text().trim().ifEmpty { "Episode ${idx + 1}" }
                    val epNum = Regex("""\b(\d+)\b""").find(epTitle)?.groupValues?.get(1) ?: "${idx + 1}"
                    episodes.add(
                        EpisodeItem(
                            id = href,
                            episodeNumber = epNum,
                            title = epTitle,
                            url = href,
                            thumbnail = thumbnail
                        )
                    )
                }
            }
        }

        MediaDetail(
            id = targetUrl.trimEnd('/').substringAfterLast('/'),
            title = title,
            category = CategoryType.ANIME,
            thumbnail = thumbnail,
            synopsis = synopsis,
            genres = genres,
            status = status,
            rating = rating,
            episodes = episodes
        )
    }

    /**
     * Get streaming links for an episode.
     * Extracts direct HLS (.m3u8) from Vidhide mirrors and provides other servers as fallback.
     */
    suspend fun getStream(targetUrl: String): StreamResult = withContext(Dispatchers.IO) {
        val html = fetchHtml(targetUrl) ?: return@withContext StreamResult(
            title = "Episode",
            directHlsUrl = null,
            iframePlayerUrl = targetUrl
        )

        val doc = Jsoup.parse(html)
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: "Nonton Anime"

        val servers = mutableListOf<StreamServerItem>()
        var primaryHlsUrl: String? = null

        // 1. Collect all raw mirror options fast without blocking
        data class RawMirror(val label: String, val src: String)
        val rawMirrors = mutableListOf<RawMirror>()

        doc.select("select.mirror option").forEach { opt ->
            val b64 = opt.attr("value").trim()
            val label = opt.text().trim()
            if (b64.length > 8 && !label.contains("Pilih Server", ignoreCase = true)) {
                try {
                    val decodedHtml = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
                    val iframeDoc = Jsoup.parse(decodedHtml)
                    var iframeSrc = iframeDoc.selectFirst("iframe")?.attr("src")
                        ?: Regex("""src=["']([^"']+)["']""").find(decodedHtml)?.groupValues?.get(1)

                    if (!iframeSrc.isNullOrBlank() && iframeSrc.startsWith("http")) {
                        // Apply Animasu website domain rewrites
                        if (iframeSrc.contains("short.ink")) iframeSrc = iframeSrc.replace("short.ink", "player.abyssplayer.com")
                        if (iframeSrc.contains("short.icu")) iframeSrc = iframeSrc.replace("short.icu", "player.abyssplayer.com")
                        if (iframeSrc.contains("uservideo.in")) iframeSrc = iframeSrc.replace(".in", ".xyz")
                        if (iframeSrc.contains("nanime.yt")) iframeSrc = iframeSrc.replace(".yt", ".in")

                        rawMirrors.add(RawMirror(label, iframeSrc))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. Prioritize Vidhide for direct HLS extraction (prefer 720p or 1080p, else first Vidhide)
        val vidhideMirrors = rawMirrors.filter { it.src.contains("vidhide") || it.src.contains("odvidhide") }
        val preferredVidhide = vidhideMirrors.find { it.label.contains("720p", ignoreCase = true) }
            ?: vidhideMirrors.find { it.label.contains("1080p", ignoreCase = true) }
            ?: vidhideMirrors.firstOrNull()

        if (preferredVidhide != null) {
            val directHls = withTimeoutOrNull(3500) {
                try {
                    StreamResolver.extractVidhideHls(preferredVidhide.src, referer = "$BASE_URL/")
                } catch (e: Exception) {
                    null
                }
            }
            if (!directHls.isNullOrBlank()) {
                primaryHlsUrl = directHls
                servers.add(
                    StreamServerItem(
                        name = "Vidhide Direct HLS (${preferredVidhide.label})",
                        url = directHls,
                        isDirectHls = true
                    )
                )
            }
        }

        // 3. Add all collected mirrors to servers list
        rawMirrors.forEach { mirror ->
            val isExtractedVidhide = primaryHlsUrl != null && mirror == preferredVidhide
            if (!isExtractedVidhide) {
                val hostName = when {
                    mirror.src.contains("vidhide") || mirror.src.contains("odvidhide") -> "Vidhide"
                    mirror.src.contains("yourupload.com") -> "YourUpload"
                    mirror.src.contains("ok.ru") -> "OK.ru"
                    mirror.src.contains("blogger.com") -> "Blogger"
                    mirror.src.contains("mega.nz") -> "Mega"
                    mirror.src.contains("filedon.co") -> "Filedon"
                    mirror.src.contains("terabox.com") -> "TeraBox"
                    mirror.src.contains("abyssplayer") || mirror.src.contains("short.ink") -> "Abyss"
                    mirror.src.contains("berkasdrive.com") -> "BerkasDrive"
                    else -> "Server"
                }
                servers.add(
                    StreamServerItem(
                        name = "$hostName (${mirror.label})",
                        url = mirror.src,
                        isDirectHls = false
                    )
                )
            }
        }

        // 4. Also check direct iframes in #pembed or .player-embed
        doc.select("#pembed iframe, .player-embed iframe, .responsive-embed-stream iframe").forEach { iframe ->
            var src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.contains("short.ink")) src = src.replace("short.ink", "player.abyssplayer.com")
            if (src.contains("short.icu")) src = src.replace("short.icu", "player.abyssplayer.com")
            if (src.isNotBlank() && src.startsWith("http") && !src.contains("about:blank") && !src.contains("facebook.com") && !src.contains("cbox.ws")) {
                if (!servers.any { it.url == src }) {
                    servers.add(StreamServerItem(name = "Main Player (Web)", url = src, isDirectHls = false))
                }
            }
        }

        if (servers.isEmpty()) {
            servers.add(StreamServerItem(name = "Animasu Web Player", url = targetUrl, isDirectHls = false))
        }

        // Sort so Direct HLS streams are ALWAYS at the top
        val sortedServers = servers.sortedWith(
            compareBy<StreamServerItem>(
                { if (it.isDirectHls) 0 else 1 },
                { if (it.name.contains("720p", ignoreCase = true)) 0 else if (it.name.contains("1080p", ignoreCase = true)) 1 else 2 }
            )
        )

        val finalDirect = primaryHlsUrl
            ?: sortedServers.firstOrNull { it.isDirectHls }?.url

        StreamResult(
            title = title,
            directHlsUrl = finalDirect,
            iframePlayerUrl = targetUrl,
            servers = sortedServers
        )
    }
}
