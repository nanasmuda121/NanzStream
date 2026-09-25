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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup

object DracinemaScraper {
    private const val BASE_URL = "https://www.dracinema.com"
    private const val API_KEY = "xb3MdwdLrZrpaDXvrLLwfP=="
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

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

    suspend fun getLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val targetUrl = "$BASE_URL/collections?page=$page"
            val html = fetchHtml(targetUrl) ?: return@withContext emptyList()
            val doc = Jsoup.parse(html)

            // Scrape cards: a[href^='/movie/']
            doc.select("a[href^='/movie/']").forEach { a ->
                val href = a.attr("href")
                val slug = href.removePrefix("/movie/").trimEnd('/')
                if (slug.isNotBlank() && !list.any { it.slug == slug }) {
                    val imgEl = a.selectFirst("img")
                    val rawAlt = imgEl?.attr("alt") ?: a.text()
                    val title = rawAlt.replace("Full Episode Subtitle Indonesia - Dracinema", "")
                        .replace("Subtitle Indonesia", "")
                        .replace("- Dracinema", "")
                        .trim()
                    val poster = imgEl?.attr("src")?.ifEmpty { null }
                        ?: imgEl?.attr("data-src") ?: ""

                    if (title.isNotBlank()) {
                        list.add(
                            MediaItem(
                                id = "$BASE_URL/movie/$slug",
                                title = title,
                                category = CategoryType.DRACHINA,
                                thumbnail = poster,
                                url = "$BASE_URL/movie/$slug",
                                slug = slug,
                                badge = "Sub Indo",
                                genres = listOf("Drama", "Romance", "Short TV")
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val cleanQ = query.trim()
            if (cleanQ.isBlank()) return@withContext emptyList()

            val apiUrl = "$BASE_URL/api/search?keyword=${java.net.URLEncoder.encode(cleanQ, "UTF-8")}"
            val req = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", USER_AGENT)
                .header("X-API-Key", API_KEY)
                .header("Referer", "$BASE_URL/")
                .build()

            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val jsonStr = resp.body?.string()
            if (!jsonStr.isNullOrBlank()) {
                val jsonObj = JSONObject(jsonStr)
                val dataArr = jsonObj.optJSONArray("data")
                if (dataArr != null) {
                    for (i in 0 until dataArr.length()) {
                        val item = dataArr.getJSONObject(i)
                        val name = item.optString("bookName")
                        val cover = item.optString("cover")
                        val intro = item.optString("introduction")
                        val origId = item.optString("originalBookId")
                        val slug = item.optString("movieKey").ifBlank {
                            name.lowercase()
                                .replace(Regex("[^a-z0-9]+"), "-")
                                .trim('-') + "-$origId"
                        }

                        if (name.isNotBlank()) {
                            list.add(
                                MediaItem(
                                    id = "$BASE_URL/movie/$slug",
                                    title = name,
                                    category = CategoryType.DRACHINA,
                                    thumbnail = cover,
                                    url = "$BASE_URL/movie/$slug",
                                    slug = slug,
                                    synopsis = intro,
                                    badge = "Drama China"
                                )
                            )
                        }
                    }
                }
            }

            // Fallback: if API returned empty, filter collections
            if (list.isEmpty()) {
                val p1 = getLatest(1)
                val p2 = getLatest(2)
                list.addAll((p1 + p2).filter { it.title.contains(cleanQ, ignoreCase = true) })
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun getDetail(urlOrSlug: String): MediaDetail? = withContext(Dispatchers.IO) {
        try {
            val slug = urlOrSlug.removePrefix(BASE_URL).removePrefix("/movie/").removePrefix("/play/").split("/").firstOrNull() ?: urlOrSlug
            val targetUrl = if (urlOrSlug.startsWith("http")) urlOrSlug else "$BASE_URL/movie/$slug"

            val html = fetchHtml(targetUrl) ?: return@withContext null
            val doc = Jsoup.parse(html)

            val rawTitle = doc.selectFirst("h1")?.text()?.trim()
                ?: doc.selectFirst("title")?.text()
                    ?.replace("Sub Indo - Nonton", "")
                    ?.replace(Regex("Episode.*"), "")
                    ?.replace("| Dracinema", "")
                    ?.trim()
                ?: "Drama China"

            val poster = doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst("img")?.attr("src") ?: ""

            val synopsis = doc.selectFirst("meta[property='og:description']")?.attr("content")
                ?: doc.selectFirst("p.text-sm, p.description")?.text()?.trim()

            val episodes = mutableListOf<EpisodeItem>()

            // Find all episode play links /play/{slug}/{epNum}
            val playLinks = doc.select("a[href*='/play/']")
            var maxEp = 1
            playLinks.forEach { a ->
                val href = a.attr("href")
                val epMatch = Regex("""/play/[^/]+/(\d+)""").find(href)
                val epNum = epMatch?.groupValues?.get(1)?.toIntOrNull()
                if (epNum != null) {
                    if (epNum > maxEp) maxEp = epNum
                    val fullUrl = if (href.startsWith("http")) href else "$BASE_URL$href"
                    if (!episodes.any { it.episodeNumber == epNum.toString() }) {
                        episodes.add(
                            EpisodeItem(
                                id = fullUrl,
                                episodeNumber = epNum.toString(),
                                title = "Episode $epNum",
                                url = fullUrl
                            )
                        )
                    }
                }
            }

            // Also check for total episodes from meta or title (e.g. "Episode 1-107")
            val rangeMatch = Regex("""Episode\s+(\d+)\s*-\s*(\d+)""", RegexOption.IGNORE_CASE).find(html)
            if (rangeMatch != null) {
                val total = rangeMatch.groupValues[2].toIntOrNull() ?: maxEp
                if (total > maxEp) maxEp = total
            }

            // Ensure all episodes from 1 to maxEp exist
            for (i in 1..maxEp) {
                val epStr = i.toString()
                if (!episodes.any { it.episodeNumber == epStr }) {
                    val epUrl = "$BASE_URL/play/$slug/$i"
                    episodes.add(
                        EpisodeItem(
                            id = epUrl,
                            episodeNumber = epStr,
                            title = "Episode $i",
                            url = epUrl
                        )
                    )
                }
            }

            episodes.sortBy { it.episodeNumber.toIntOrNull() ?: 1 }

            MediaDetail(
                id = targetUrl,
                title = rawTitle,
                category = CategoryType.DRACHINA,
                thumbnail = poster,
                backdrop = poster,
                synopsis = synopsis,
                genres = listOf("Drama China", "Short Series", "Romance"),
                status = "Completed",
                rating = "9.2",
                totalEpisodes = "${episodes.size} Episode",
                episodes = episodes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getStream(targetUrlOrSlug: String, episode: Int = 1): StreamResult? = withContext(Dispatchers.IO) {
        try {
            val cleanSlug = targetUrlOrSlug.removePrefix(BASE_URL)
                .removePrefix("/movie/")
                .removePrefix("/play/")
                .split("/")
                .firstOrNull() ?: targetUrlOrSlug

            val safeEp = if (episode <= 0) 1 else episode

            val jsonBody = JSONObject().apply {
                put("movieKey", cleanSlug)
                put("episode", safeEp)
            }.toString()

            val req = Request.Builder()
                .url("$BASE_URL/api/playback")
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("X-API-Key", API_KEY)
                .header("Referer", "$BASE_URL/play/$cleanSlug/$safeEp")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = ApiClient.okHttpClient.newCall(req).execute()
            val respStr = resp.body?.string() ?: return@withContext null
            val respJson = JSONObject(respStr)

            val token = respJson.optString("token")
            var directStreamUrl: String? = null
            var movieTitle: String? = null

            if (token.isNotBlank()) {
                val parts = token.split(".")
                if (parts.size >= 2) {
                    val payloadB64 = parts[1].replace('-', '+').replace('_', '/')
                    val padded = payloadB64 + "=".repeat((4 - (payloadB64.length % 4)) % 4)
                    val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
                    val payloadJson = JSONObject(String(decodedBytes, Charsets.UTF_8))

                    val dataObj = payloadJson.optJSONObject("data")
                    movieTitle = dataObj?.optJSONObject("meta")?.optString("movieName")

                    val detailObj = dataObj?.optJSONObject("detail")
                    val videoUrlsArr = detailObj?.optJSONArray("videoUrls")
                    if (videoUrlsArr != null && videoUrlsArr.length() > 0) {
                        directStreamUrl = videoUrlsArr.getJSONObject(0).optString("url")
                    }
                }
            }

            if (!directStreamUrl.isNullOrBlank()) {
                StreamResult(
                    title = movieTitle ?: "Drama China Episode $safeEp",
                    directHlsUrl = directStreamUrl,
                    iframePlayerUrl = null,
                    servers = listOf(
                        StreamServerItem(
                            name = "Dracinema Server (Direct HD)",
                            url = directStreamUrl,
                            isDirectHls = true
                        )
                    )
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
