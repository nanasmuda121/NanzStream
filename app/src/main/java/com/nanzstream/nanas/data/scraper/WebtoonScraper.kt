package com.nanzstream.nanas.data.scraper

import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.MangaChapterItem
import com.nanzstream.nanas.data.model.MangaPageItem
import com.nanzstream.nanas.data.model.MediaDetail
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

object WebtoonScraper {

    private const val BASE_URL = "https://www.webtoons.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private suspend fun fetchHtml(url: String, referer: String = "$BASE_URL/id/"): String? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", referer)
                    .header("Cookie", "locale=id; needGDPR=false; countryCode=ID; pagGDPR=true")
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

    private val scheduleCache = java.util.concurrent.ConcurrentHashMap<String, List<MediaItem>>()

    suspend fun getHome(): List<MediaItem> = withContext(Dispatchers.IO) {
        scheduleCache["home"]?.let { if (it.isNotEmpty()) return@withContext it }
        val html = fetchHtml("$BASE_URL/id/originals") ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select("a.link._originals_title_a, a[href*='title_no=']").forEach { a ->
            val href = a.attr("href")
            val titleNo = a.attr("data-title-no").ifEmpty {
                Regex("""title_no=(\d+)""").find(href)?.groupValues?.get(1) ?: ""
            }
            if (titleNo.isEmpty() || items.any { it.id == titleNo }) return@forEach

            val title = a.selectFirst("strong.title, .subj, .title, p.title")?.text()?.trim() ?: ""
            val img = a.selectFirst("img")?.let {
                it.attr("src").ifBlank { it.attr("data-url") }.ifBlank { it.attr("data-src") }
            } ?: ""
            val genre = a.selectFirst(".genre")?.text()?.trim() ?: "Webtoon"
            val badge = a.selectFirst(".badge_up2")?.text()?.trim() ?: genre

            if (title.isNotBlank()) {
                val fullUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
                items.add(
                    MediaItem(
                        id = titleNo,
                        title = title,
                        category = CategoryType.MANGA,
                        thumbnail = img,
                        url = fullUrl,
                        slug = fullUrl,
                        badge = badge,
                        genres = listOf(genre)
                    )
                )
            }
        }
        if (items.isNotEmpty()) {
            scheduleCache["home"] = items
        }
        items
    }

    suspend fun getSchedule(daySlug: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanSlug = daySlug.lowercase().trim()
        scheduleCache[cleanSlug]?.let { if (it.isNotEmpty()) return@withContext it }

        val targetUrl = "$BASE_URL/id/originals/$cleanSlug"
        val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select("a.link._originals_title_a, a[href*='title_no=']").forEach { a ->
            val href = a.attr("href")
            val titleNo = a.attr("data-title-no").ifEmpty {
                Regex("""title_no=(\d+)""").find(href)?.groupValues?.get(1) ?: ""
            }
            if (titleNo.isEmpty() || items.any { it.id == titleNo }) return@forEach

            val title = a.selectFirst("strong.title, .subj, .title, p.title")?.text()?.trim() ?: ""
            val img = a.selectFirst("img")?.let {
                it.attr("src").ifBlank { it.attr("data-url") }.ifBlank { it.attr("data-src") }
            } ?: ""
            val genre = a.selectFirst(".genre")?.text()?.trim() ?: "Webtoon"
            val badge = a.selectFirst(".badge_up2")?.text()?.trim() ?: genre

            if (title.isNotBlank()) {
                val fullUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
                items.add(
                    MediaItem(
                        id = titleNo,
                        title = title,
                        category = CategoryType.MANGA,
                        thumbnail = img,
                        url = fullUrl,
                        slug = fullUrl,
                        badge = badge,
                        genres = listOf(genre)
                    )
                )
            }
        }
        if (items.isNotEmpty()) {
            scheduleCache[cleanSlug] = items
        }
        items
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val targetUrl = "$BASE_URL/id/search?keyword=${URLEncoder.encode(query, "UTF-8")}"
        val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select("a[href*='title_no=']").forEach { a ->
            val href = a.attr("href")
            val titleNo = Regex("""title_no=(\d+)""").find(href)?.groupValues?.get(1) ?: ""
            if (titleNo.isEmpty() || items.any { it.id == titleNo }) return@forEach

            val title = a.selectFirst("strong.title, .subj")?.text()?.trim() ?: ""
            val img = a.selectFirst("img")?.attr("src") ?: ""
            val genre = a.selectFirst(".genre")?.text()?.trim() ?: "Webtoon"

            if (title.isNotBlank() && img.isNotBlank()) {
                val fullUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
                items.add(
                    MediaItem(
                        id = titleNo,
                        title = title,
                        category = CategoryType.MANGA,
                        thumbnail = img,
                        url = fullUrl,
                        slug = fullUrl,
                        badge = genre,
                        genres = listOf(genre)
                    )
                )
            }
        }
        items
    }

    suspend fun getDetail(urlOrTitleNo: String): MediaDetail? = withContext(Dispatchers.IO) {
        val targetUrl = if (urlOrTitleNo.startsWith("http")) {
            urlOrTitleNo
        } else {
            // Webtoon episodeList route automatically 301-redirects to the correct canonical genre & title URL
            "$BASE_URL/episodeList?titleNo=$urlOrTitleNo"
        }

        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

        // Resolve real canonical URL in case of redirect or title_no query
        val canonicalHref = doc.selectFirst("link[rel='canonical']")?.attr("href")?.trim()
        val effectiveUrl = if (!canonicalHref.isNullOrEmpty() && canonicalHref.startsWith("http")) {
            canonicalHref
        } else {
            targetUrl
        }

        val title = doc.selectFirst("h1.subj, .subj_info .subj, meta[property='og:title']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }?.trim() ?: "Webtoon Detail"

        val img = doc.selectFirst(".detail_header img, .thmb img, meta[property='og:image']")?.let {
            if (it.tagName() == "meta") it.attr("content") else (it.attr("src").ifEmpty { it.attr("data-src") })
        } ?: ""

        val synopsis = doc.selectFirst("p.summary, .desc, meta[property='og:description']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }?.trim()

        val genre = doc.selectFirst(".genre, h2.genre")?.text()?.trim() ?: "Webtoon"

        fun parseChaptersFromDoc(d: org.jsoup.nodes.Document): List<MangaChapterItem> {
            val list = mutableListOf<MangaChapterItem>()
            d.select("li[id^='episode_'], #_episodeList li, ul#_listUl li, .detail_lst li").forEach { li ->
                val a = li.selectFirst("a[href*='viewer']")
                val viewerHref = a?.attr("href") ?: return@forEach
                val epNum = Regex("""episode_no=(\d+)""").find(viewerHref)?.groupValues?.get(1)
                    ?: li.attr("data-episode-no")
                val epTitle = li.selectFirst(".subj span, .subj")?.text()?.trim() ?: "Episode $epNum"
                val date = li.selectFirst(".date")?.text()?.trim()

                val fullViewerUrl = if (viewerHref.startsWith("http")) viewerHref else "$BASE_URL$viewerHref"

                list.add(
                    MangaChapterItem(
                        id = fullViewerUrl,
                        title = epTitle,
                        subtitle = epNum.ifEmpty { null }?.let { "Ep $it" },
                        date = date
                    )
                )
            }
            return list
        }

        val allChapters = mutableListOf<MangaChapterItem>()
        val page1Chapters = parseChaptersFromDoc(doc)
        allChapters.addAll(page1Chapters)

        // Calculate total pages based on highest episode number found on page 1
        val firstEpNum = page1Chapters.firstOrNull()?.subtitle?.replace("Ep ", "")?.trim()?.toIntOrNull()
            ?: page1Chapters.firstOrNull()?.title?.let { Regex("""\b(\d+)\b""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: 10

        // Only fetch up to page 3 (max 30 chapters) on initial load to ensure instant load time without Webtoon throttling
        val estTotalPages = if (firstEpNum > 10) minOf((firstEpNum + 9) / 10, 3) else 1

        if (estTotalPages > 1) {
            val cleanUrl = effectiveUrl.replace(Regex("""[&?]page=\d+"""), "")
            val separator = if (cleanUrl.contains("?")) "&" else "?"

            for (page in 2..estTotalPages) {
                try {
                    val pageHtml = fetchHtml("$cleanUrl${separator}page=$page")
                    if (pageHtml != null) {
                        val pageChapters = parseChaptersFromDoc(Jsoup.parse(pageHtml))
                        for (ch in pageChapters) {
                            if (!allChapters.any { it.id == ch.id }) {
                                allChapters.add(ch)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        MediaDetail(
            id = effectiveUrl,
            title = title,
            category = CategoryType.MANGA,
            thumbnail = img,
            synopsis = synopsis,
            genres = listOf(genre),
            status = "Ongoing",
            totalEpisodes = "${allChapters.size} Episode",
            chapters = allChapters
        )
    }

    suspend fun getPages(viewerUrl: String): List<MangaPageItem> = withContext(Dispatchers.IO) {
        val html = fetchHtml(viewerUrl, referer = "$BASE_URL/id/") ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val pages = mutableListOf<MangaPageItem>()
        val seenUrls = mutableSetOf<String>()

        doc.select("#_imageList img, .viewer_lst img, .viewer_img img").forEach { img ->
            val pageUrl = img.attr("data-url").ifBlank { img.attr("src") }.trim()
            if (pageUrl.startsWith("http")
                && !pageUrl.contains("transparency")
                && !pageUrl.contains("bg_transparency")
                && !pageUrl.contains("warning")
                && !pageUrl.contains("blank")
                && !pageUrl.contains(".svg")
                && !seenUrls.contains(pageUrl)
            ) {
                seenUrls.add(pageUrl)
                pages.add(
                    MangaPageItem(
                        page = pages.size + 1,
                        url = pageUrl
                    )
                )
            }
        }

        pages
    }
}
