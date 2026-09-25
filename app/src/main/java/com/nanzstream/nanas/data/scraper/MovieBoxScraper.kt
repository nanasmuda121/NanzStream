package com.nanzstream.nanas.data.scraper

import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.EpisodeItem
import com.nanzstream.nanas.data.model.MediaDetail
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.model.StreamResult
import com.nanzstream.nanas.data.model.StreamServerItem
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup

object MovieBoxScraper {
    private const val BASE_URL = "https://themoviebox.xyz/id"
    private const val API_BASE = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private var cachedHomeSubjects: List<MediaItem>? = null

    private suspend fun fetchHtml(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) resp.body?.string() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun fetchJson(url: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .build()
            val resp = ApiClient.okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val str = resp.body?.string()
                if (!str.isNullOrBlank()) JSONObject(str) else null
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        if (cachedHomeSubjects != null && page == 1) {
            return@withContext cachedHomeSubjects!!
        }

        val items = mutableListOf<MediaItem>()
        try {
            val json = fetchJson("$API_BASE/home?host=themoviebox.xyz")
            val data = json?.optJSONObject("data")
            val opList = data?.optJSONArray("operatingList")
            if (opList != null) {
                for (i in 0 until opList.length()) {
                    val op = opList.getJSONObject(i)
                    val sectionTitle = op.optString("title")
                    val subjectsArr = op.optJSONArray("subjects")
                    if (subjectsArr != null) {
                        for (j in 0 until subjectsArr.length()) {
                            val sub = subjectsArr.getJSONObject(j)
                            val title = sub.optString("title")
                            val detailPath = sub.optString("detailPath")
                            val coverObj = sub.optJSONObject("cover")
                            val coverUrl = coverObj?.optString("url") ?: ""
                            val rating = sub.optString("imdbRatingValue").ifBlank { "7.8" }
                            val genres = sub.optString("genre").split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val year = sub.optString("releaseDate").take(4)

                            if (title.isNotBlank() && detailPath.isNotBlank() && !items.any { it.slug == detailPath }) {
                                items.add(
                                    MediaItem(
                                        id = "$BASE_URL/detail/$detailPath",
                                        title = title,
                                        category = CategoryType.MOVIES,
                                        thumbnail = coverUrl,
                                        url = "$BASE_URL/detail/$detailPath",
                                        slug = detailPath,
                                        badge = if (sectionTitle.contains("Indonesian", ignoreCase = true)) "Indo Movie" else "HD Movie",
                                        rating = rating,
                                        year = year,
                                        genres = genres
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (items.isNotEmpty()) {
            cachedHomeSubjects = items
        }
        val perPage = 20
        val startIndex = ((page - 1) * perPage).coerceAtLeast(0)
        items.drop(startIndex).take(perPage)
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanQ = query.trim()
        if (cleanQ.isBlank()) return@withContext emptyList()

        val allItems = if (cachedHomeSubjects.isNullOrEmpty()) getLatest(1) else cachedHomeSubjects.orEmpty()
        allItems.filter {
            it.title.contains(cleanQ, ignoreCase = true) ||
            it.genres.any { g -> g.contains(cleanQ, ignoreCase = true) }
        }
    }

    suspend fun getDetail(urlOrPath: String): MediaDetail? = withContext(Dispatchers.IO) {
        try {
            val slug = urlOrPath.removePrefix(BASE_URL)
                .removePrefix("https://themoviebox.xyz")
                .removePrefix("/id/detail/")
                .removePrefix("/detail/")
                .removePrefix("/id/")
                .trim('/')

            // 1. Check cached subjects from home
            if (cachedHomeSubjects.isNullOrEmpty()) {
                getLatest(1)
            }
            val cached = cachedHomeSubjects?.find { it.slug == slug || it.id.contains(slug) }

            // 2. Fetch full HTML SSR page for detail & video stream
            val detailUrl = "$BASE_URL/detail/$slug"
            val html = fetchHtml(detailUrl)

            var title = cached?.title ?: ""
            var synopsis = cached?.synopsis ?: ""
            var coverUrl = cached?.thumbnail ?: ""
            var rating = cached?.rating ?: "7.9"
            var year = cached?.year ?: "2024"
            var genres = cached?.genres ?: listOf("Movie", "Drama")
            var videoStreamUrl = ""

            if (!html.isNullOrBlank()) {
                val doc = Jsoup.parse(html)

                if (title.isBlank()) {
                    title = doc.selectFirst("h1")?.text()?.trim()
                        ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.replace(" - Moviebox", "")?.trim()
                        ?: "Film Layar Lebar"
                }

                if (synopsis.isBlank()) {
                    synopsis = doc.selectFirst("meta[property='og:description']")?.attr("content")
                        ?: doc.selectFirst("meta[name='description']")?.attr("content")
                        ?: ""
                }

                if (coverUrl.isBlank()) {
                    coverUrl = doc.selectFirst("meta[property='og:image']")?.attr("content")
                        ?: doc.selectFirst("img")?.attr("src")
                        ?: ""
                }

                // Extract direct Video stream from VideoObject json-ld
                doc.select("script[type='application/ld+json']").forEach { script ->
                    val data = script.data()
                    if (data.contains("VideoObject")) {
                        try {
                            val j = JSONObject(data)
                            if (j.optString("@type") == "VideoObject") {
                                val cUrl = j.optString("contentUrl")
                                if (cUrl.isNotBlank() && cUrl.startsWith("http")) {
                                    videoStreamUrl = cUrl
                                }
                                val desc = j.optString("description")
                                if (desc.isNotBlank() && synopsis.isBlank()) {
                                    synopsis = desc
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }

                // Regex fallback for macdn mp4 trailer/stream
                if (videoStreamUrl.isBlank()) {
                    val m = Regex("""\"(https://macdn\.aoneroom\.com/[^\"]+\.mp4)\"""").find(html)
                    if (m != null) {
                        videoStreamUrl = m.groupValues[1]
                    }
                }
            }

            if (title.isBlank()) {
                title = slug.replace("-", " ").capitalize()
            }

            val episodes = listOf(
                EpisodeItem(
                    id = "$BASE_URL/detail/$slug",
                    episodeNumber = "1",
                    title = "Full Movie",
                    url = if (videoStreamUrl.isNotBlank()) videoStreamUrl else "$BASE_URL/detail/$slug"
                )
            )

            MediaDetail(
                id = "$BASE_URL/detail/$slug",
                title = title,
                category = CategoryType.MOVIES,
                thumbnail = coverUrl,
                backdrop = coverUrl,
                synopsis = synopsis.ifBlank { "Tonton film layar lebar $title dalam kualitas HD Subtitle Indonesia." },
                genres = genres,
                status = "Released $year",
                rating = rating,
                releaseDate = year,
                totalEpisodes = "Full Movie",
                episodes = episodes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getStream(urlOrPath: String, episode: Int = 1): StreamResult? = withContext(Dispatchers.IO) {
        try {
            val slug = urlOrPath.removePrefix(BASE_URL)
                .removePrefix("https://themoviebox.xyz")
                .removePrefix("/id/detail/")
                .removePrefix("/detail/")
                .removePrefix("/id/")
                .trim('/')

            val detail = getDetail(slug)
            val streamUrl = detail?.episodes?.firstOrNull()?.url

            if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                StreamResult(
                    title = "${detail.title} - Full Movie",
                    directHlsUrl = streamUrl,
                    iframePlayerUrl = null,
                    servers = listOf(
                        StreamServerItem(
                            name = "MovieBox Direct Stream",
                            url = streamUrl,
                            isDirectHls = false
                        )
                    )
                )
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
