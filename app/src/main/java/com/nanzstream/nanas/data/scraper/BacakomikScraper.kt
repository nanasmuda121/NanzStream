package com.nanzstream.nanas.data.scraper

import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.MangaChapterItem
import com.nanzstream.nanas.data.model.MangaPageItem
import com.nanzstream.nanas.data.model.MediaDetail
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder

object BacakomikScraper {

    const val BASE_URL = "https://bacakomik.my"
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
     * Get latest comics from https://bacakomik.my/komik-terbaru/
     */
    suspend fun getLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val url = if (page <= 1) "$BASE_URL/komik-terbaru/" else "$BASE_URL/komik-terbaru/page/$page/"
        val html = fetchHtml(url) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".animepost").forEach { post ->
            val a = post.selectFirst("a[itemprop=url]") ?: post.selectFirst("a") ?: return@forEach
            val href = a.attr("href").trim()
            if (href.isBlank() || !href.contains("/komik/")) return@forEach

            val title = post.selectFirst(".tt h4")?.text()?.trim()
                ?: post.selectFirst(".tt")?.text()?.trim()
                ?: a.attr("title").replace("Komik ", "").trim()
            if (title.isBlank()) return@forEach

            val imgEl = post.selectFirst("img")
            val thumbnail = imgEl?.attr("data-lazy-src")
                ?.ifEmpty { imgEl.attr("data-src") }
                ?.ifEmpty { post.selectFirst("noscript img")?.attr("src") }
                ?.ifEmpty { imgEl?.attr("src") }
                ?: ""

            val chText = post.selectFirst(".lsch a")?.text()?.trim() ?: "Manga"
            val typeText = post.selectFirst(".typeflag")?.classNames()?.firstOrNull { it != "typeflag" } ?: "Komik"
            val slug = href.trimEnd('/').substringAfterLast('/')

            if (items.none { it.id == slug || it.url == href }) {
                items.add(
                    MediaItem(
                        id = slug,
                        title = title,
                        category = CategoryType.MANGA,
                        thumbnail = thumbnail,
                        url = href,
                        slug = slug,
                        badge = chText,
                        rating = typeText
                    )
                )
            }
        }
        items
    }

    /**
     * Homepage feed
     */
    suspend fun getHome(): List<MediaItem> = getLatest(1)

    /**
     * Get comics by type (manga, manhwa, manhua) from https://bacakomik.my/daftar-komik/?type=$type
     */
    suspend fun getByType(type: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanType = type.lowercase().trim()
        val url = if (page <= 1) {
            "$BASE_URL/daftar-komik/?type=$cleanType"
        } else {
            "$BASE_URL/daftar-komik/page/$page/?type=$cleanType"
        }
        val html = fetchHtml(url) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".animepost").forEach { post ->
            val a = post.selectFirst("a[itemprop=url]") ?: post.selectFirst("a") ?: return@forEach
            val href = a.attr("href").trim()
            if (href.isBlank() || !href.contains("/komik/")) return@forEach

            val title = post.selectFirst(".tt h4")?.text()?.trim()
                ?: post.selectFirst(".tt")?.text()?.trim()
                ?: a.attr("title").replace("Komik ", "").trim()
            if (title.isBlank()) return@forEach

            val imgEl = post.selectFirst("img")
            val thumbnail = imgEl?.attr("data-lazy-src")
                ?.ifEmpty { imgEl.attr("data-src") }
                ?.ifEmpty { post.selectFirst("noscript img")?.attr("src") }
                ?.ifEmpty { imgEl?.attr("src") }
                ?: ""

            val chText = post.selectFirst(".lsch a")?.text()?.trim() ?: type.replaceFirstChar { it.uppercase() }
            val slug = href.trimEnd('/').substringAfterLast('/')

            if (items.none { it.id == slug || it.url == href }) {
                items.add(
                    MediaItem(
                        id = slug,
                        title = title,
                        category = CategoryType.MANGA,
                        thumbnail = thumbnail,
                        url = href,
                        slug = slug,
                        badge = chText,
                        rating = type.replaceFirstChar { it.uppercase() }
                    )
                )
            }
        }
        items
    }

    /**
     * Search comics on bacakomik.my
     */
    suspend fun search(query: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = if (page <= 1) "$BASE_URL/?s=$encoded" else "$BASE_URL/page/$page/?s=$encoded"
        val html = fetchHtml(url) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val items = mutableListOf<MediaItem>()

        doc.select(".animepost").forEach { post ->
            val a = post.selectFirst("a[itemprop=url]") ?: post.selectFirst("a") ?: return@forEach
            val href = a.attr("href").trim()
            if (href.isBlank() || !href.contains("/komik/")) return@forEach

            val title = post.selectFirst(".tt h4")?.text()?.trim()
                ?: post.selectFirst(".tt")?.text()?.trim()
                ?: a.attr("title").replace("Komik ", "").trim()
            if (title.isBlank()) return@forEach

            val imgEl = post.selectFirst("img")
            val thumbnail = imgEl?.attr("data-lazy-src")
                ?.ifEmpty { imgEl.attr("data-src") }
                ?.ifEmpty { post.selectFirst("noscript img")?.attr("src") }
                ?.ifEmpty { imgEl?.attr("src") }
                ?: ""

            val chText = post.selectFirst(".lsch a")?.text()?.trim() ?: "Manga"
            val slug = href.trimEnd('/').substringAfterLast('/')

            if (items.none { it.id == slug || it.url == href }) {
                items.add(
                    MediaItem(
                        id = slug,
                        title = title,
                        category = CategoryType.MANGA,
                        thumbnail = thumbnail,
                        url = href,
                        slug = slug,
                        badge = chText
                    )
                )
            }
        }
        items
    }

    /**
     * Get detail and chapters list of a comic
     */
    suspend fun getDetail(idOrSlug: String): MediaDetail? = withContext(Dispatchers.IO) {
        val targetUrl = when {
            idOrSlug.startsWith("http") -> idOrSlug
            idOrSlug.startsWith("komik/") -> "$BASE_URL/$idOrSlug"
            else -> "$BASE_URL/komik/$idOrSlug"
        }
        val html = fetchHtml(targetUrl) ?: return@withContext null
        val doc = Jsoup.parse(html)

        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst(".entry-title")?.text()?.trim()
            ?: doc.selectFirst("h1")?.text()?.trim()
            ?: doc.title().replace("Komik ", "").substringBefore(" Bahasa Indonesia").trim()

        val imgEl = doc.selectFirst(".thumb img") ?: doc.selectFirst(".infox img")
        val thumbnail = imgEl?.attr("data-lazy-src")
            ?.ifEmpty { imgEl.attr("data-src") }
            ?.ifEmpty { doc.selectFirst(".thumb noscript img")?.attr("src") }
            ?.ifEmpty { imgEl?.attr("src") }
            ?: ""

        val synopsis = doc.selectFirst(".desc")?.text()?.trim()
            ?: doc.selectFirst(".entry-content")?.text()?.trim()
            ?: ""

        val genres = doc.select("a[href*=/genres/]").map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val status = doc.selectFirst(".spe span:contains(Status)")?.text()?.replace("Status:", "")?.trim()
            ?: doc.selectFirst("span:contains(Status)")?.text()?.replace("Status:", "")?.trim()

        val rating = doc.selectFirst(".rating strong")?.text()?.trim()

        val chapters = mutableListOf<MangaChapterItem>()

        // Chapters list in .bxcl ul li, .clstyle li, or li with chapter links
        doc.select(".bxcl ul li, .clstyle li, .eps_lst ul li").forEach { li ->
            val a = li.selectFirst("a[href*=-chapter-], a[href*=-ch-], a[href*=chapter]") ?: li.selectFirst("a") ?: return@forEach
            val href = a.attr("href").trim()
            if (href.isBlank() || href == "$BASE_URL/" || href.contains("/komik-terbaru/")) return@forEach

            val chTitle = a.selectFirst(".lch a")?.text()?.trim()
                ?: a.text().trim()
            if (chTitle.isBlank()) return@forEach

            val date = li.selectFirst(".datech")?.text()?.trim() ?: li.selectFirst(".dt")?.text()?.trim()

            if (chapters.none { it.id == href }) {
                chapters.add(
                    MangaChapterItem(
                        id = href,
                        title = chTitle.replace("\n", " ").replace(Regex("""\s+"""), " ").trim(),
                        date = date
                    )
                )
            }
        }

        // Fallback for chapter list
        if (chapters.isEmpty()) {
            doc.select("a[href*=-chapter-]").forEach { a ->
                val href = a.attr("href").trim()
                if (href.isNotBlank() && chapters.none { it.id == href }) {
                    chapters.add(
                        MangaChapterItem(
                            id = href,
                            title = a.text().replace("\n", " ").replace(Regex("""\s+"""), " ").trim()
                        )
                    )
                }
            }
        }

        MediaDetail(
            id = targetUrl.trimEnd('/').substringAfterLast('/'),
            title = title,
            category = CategoryType.MANGA,
            thumbnail = thumbnail,
            synopsis = synopsis,
            genres = genres,
            status = status,
            rating = rating,
            chapters = chapters
        )
    }

    /**
     * Get chapter reader images
     */
    suspend fun getPages(chapterUrl: String): List<MangaPageItem> = withContext(Dispatchers.IO) {
        val html = fetchHtml(chapterUrl) ?: return@withContext emptyList()
        val doc = Jsoup.parse(html)
        val pages = mutableListOf<String>()

        fun extractCandidate(img: org.jsoup.nodes.Element): String {
            val candidates = listOf(
                img.attr("data-lazy-src"),
                img.attr("data-src"),
                img.attr("data-original"),
                img.attr("data-url"),
                img.attr("data-wpfc-original-src"),
                Regex("""src=['"]([^'"]+)['"]""").find(img.attr("onerror") + " " + img.attr("onError"))?.groupValues?.get(1).orEmpty(),
                img.attr("src")
            )
            for (cand in candidates) {
                val clean = cand.trim()
                if (clean.startsWith("http") &&
                    !clean.contains("data:image", ignoreCase = true) &&
                    !clean.contains("blank.gif", ignoreCase = true) &&
                    !clean.contains("placeholder", ignoreCase = true)
                ) {
                    val isImage = clean.contains(".webp", ignoreCase = true) ||
                            clean.contains(".jpg", ignoreCase = true) ||
                            clean.contains(".jpeg", ignoreCase = true) ||
                            clean.contains(".png", ignoreCase = true) ||
                            clean.contains("/media/", ignoreCase = true) ||
                            clean.contains("/data/", ignoreCase = true)

                    val isIgnored = clean.contains("ikon", ignoreCase = true) ||
                            clean.contains("logo", ignoreCase = true) ||
                            clean.contains("svg", ignoreCase = true) ||
                            clean.contains("banner", ignoreCase = true) ||
                            clean.contains("ads", ignoreCase = true) ||
                            clean.contains("resize=", ignoreCase = true)

                    if (isImage && !isIgnored) {
                        return clean
                    }
                }
            }
            return ""
        }

        // Try primary containers first
        val containers = listOfNotNull(
            doc.selectFirst("#anjay_ini_id_kh"),
            doc.selectFirst("#readerarea"),
            doc.selectFirst(".oi_ada_class_skrng"),
            doc.selectFirst(".entry-content"),
            doc.selectFirst(".main-reading-area"),
            doc.selectFirst("#chapter-images"),
            doc.selectFirst(".chapter-content")
        )

        for (container in containers) {
            container.select("img").forEach { img ->
                val src = extractCandidate(img)
                if (src.isNotBlank() && !pages.contains(src)) {
                    pages.add(src)
                }
            }
            if (pages.isNotEmpty()) break
        }

        // Fallback 1: search all img tags in document if container was missing
        if (pages.isEmpty()) {
            doc.select("img").forEach { img ->
                val src = extractCandidate(img)
                if (src.isNotBlank() && !pages.contains(src)) {
                    pages.add(src)
                }
            }
        }

        // Fallback 2: Regex extraction from raw HTML (for obfuscated script tags, ts_reader, or dynamic layouts)
        if (pages.isEmpty()) {
            val imgRegex = Regex("""https?://[^\s"'<>]+\.(?:jpg|jpeg|png|webp)(?:\?[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
            imgRegex.findAll(html).forEach { match ->
                val u = match.value
                val isChapterData = u.contains("/data/") || u.contains("/media/") || u.contains(".lol") || u.contains(".lat") || u.contains(".pics")
                val isIgnored = u.contains("banner") || u.contains("logo") || u.contains("ikon") || u.contains("resize=") || u.contains("avatar")
                if (isChapterData && !isIgnored && !pages.contains(u)) {
                    pages.add(u)
                }
            }
        }

        pages.mapIndexed { index, url ->
            MangaPageItem(
                page = index + 1,
                url = url
            )
        }
    }
}
