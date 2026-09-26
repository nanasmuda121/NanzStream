package com.nanzstream.nanas.data.scraper

import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.DownloadItem
import com.nanzstream.nanas.data.model.EpisodeItem
import com.nanzstream.nanas.data.model.MediaDetail
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.model.StreamResult
import com.nanzstream.nanas.data.model.StreamServerItem
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

object MovieBoxScraper {
    private const val BASE_URL = "https://themoviebox.xyz/id"
    private const val API_BASE = "https://h5-api.aoneroom.com/wefeed-h5api-bff"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0L
    private val tokenMutex = Mutex()

    /**
     * Bootstrap session token from detail endpoint
     */
    private suspend fun getSessionToken(forceRefresh: Boolean = false): String = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh && !cachedToken.isNullOrBlank() && tokenExpiresAt > now + 60000L) {
            return@withLock cachedToken!!
        }

        // Try getting token from known detail endpoints
        val candidatePaths = listOf("lucifer-indonesian-YwF1Ii2H3B5", "avatar-WLDIi21IUBa", "the-furious-6lxRH1LLAe5")
        for (dp in candidatePaths) {
            try {
                val req = Request.Builder()
                    .url("$API_BASE/detail?detailPath=$dp")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("X-Request-Lang", "id")
                    .header("Origin", "https://themoviebox.xyz")
                    .header("Referer", "https://themoviebox.xyz/id")
                    .build()

                val resp = ApiClient.okHttpClient.newCall(req).execute()
                val xUser = resp.header("x-user")
                if (!xUser.isNullOrBlank()) {
                    val token = JSONObject(xUser).optString("token")
                    if (token.isNotBlank()) {
                        cachedToken = token
                        tokenExpiresAt = now + 6 * 3600 * 1000L
                        return@withLock token
                    }
                }

                val setCookies = resp.headers("set-cookie")
                for (c in setCookies) {
                    val match = Regex("""token=([^;]+)""").find(c)
                    if (match != null) {
                        val token = match.groupValues[1]
                        cachedToken = token
                        tokenExpiresAt = now + 6 * 3600 * 1000L
                        return@withLock token
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        cachedToken ?: ""
    }

    /**
     * 1. Get Trending Movies and Series
     */
    suspend fun getLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        try {
            val req = Request.Builder()
                .url("$API_BASE/subject/trending?page=$page&perPage=20")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("X-Request-Lang", "id")
                .header("Origin", "https://themoviebox.xyz")
                .header("Referer", "https://themoviebox.xyz/id")
                .build()

            val resp = ApiClient.okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val str = resp.body?.string()
                if (!str.isNullOrBlank()) {
                    val json = JSONObject(str)
                    val data = json.optJSONObject("data")
                    val list = data?.optJSONArray("subjectList") ?: data?.optJSONArray("items")
                    if (list != null) {
                        for (i in 0 until list.length()) {
                            val item = list.getJSONObject(i)
                            val title = item.optString("title")
                            val detailPath = item.optString("detailPath")
                            val subType = item.optInt("subjectType", 1)
                            val coverUrl = item.optJSONObject("cover")?.optString("url") ?: ""
                            val releaseDate = item.optString("releaseDate")
                            val year = if (releaseDate.length >= 4) releaseDate.take(4) else ""
                            val rating = item.optString("imdbRatingValue").ifBlank { "7.8" }
                            val genres = item.optString("genre").split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val desc = item.optString("description")

                            if (title.isNotBlank() && detailPath.isNotBlank() && !result.any { it.slug == detailPath }) {
                                result.add(
                                    MediaItem(
                                        id = detailPath,
                                        title = title,
                                        category = CategoryType.MOVIES,
                                        thumbnail = coverUrl,
                                        url = "$BASE_URL/detail/$detailPath",
                                        slug = detailPath,
                                        badge = if (subType == 2) "Series" else "Movie",
                                        rating = rating,
                                        year = year,
                                        genres = genres,
                                        synopsis = desc
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
        result
    }

    /**
     * 2. Search Movies and Series
     */
    suspend fun search(query: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanQ = query.trim()
        if (cleanQ.isBlank()) return@withContext emptyList()

        try {
            var token = getSessionToken()

            suspend fun doSearch(t: String): JSONObject? {
                val bodyJson = JSONObject().apply {
                    put("keyword", cleanQ)
                    put("page", page)
                    put("perPage", 20)
                    put("subjectType", 0)
                }
                val reqBuilder = Request.Builder()
                    .url("$API_BASE/subject/search")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Client-Info", "{\"timezone\":\"Asia/Jakarta\"}")
                    .header("X-Request-Lang", "id")
                    .header("Origin", "https://themoviebox.xyz")
                    .header("Referer", "https://themoviebox.xyz/id/web/searchResult?keyword=${URLEncoder.encode(cleanQ, "UTF-8")}")
                    .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))

                if (t.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $t")
                    reqBuilder.header("Cookie", "token=$t; mb_token=\"$t\"")
                }

                val resp = ApiClient.okHttpClient.newCall(reqBuilder.build()).execute()
                if (resp.code == 400 || resp.code == 401) {
                    return null
                }
                val str = resp.body?.string() ?: return null
                return JSONObject(str)
            }

            var json = doSearch(token)
            if (json == null) {
                token = getSessionToken(forceRefresh = true)
                json = doSearch(token)
            }

            val data = json?.optJSONObject("data")
            val itemsArr = data?.optJSONArray("items") ?: data?.optJSONArray("subjectList")
            val result = mutableListOf<MediaItem>()

            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val item = itemsArr.getJSONObject(i)
                    val title = item.optString("title")
                    val detailPath = item.optString("detailPath")
                    val subType = item.optInt("subjectType", 1)
                    val coverUrl = item.optJSONObject("cover")?.optString("url") ?: ""
                    val releaseDate = item.optString("releaseDate")
                    val year = if (releaseDate.length >= 4) releaseDate.take(4) else ""
                    val rating = item.optString("imdbRatingValue").ifBlank { "7.5" }
                    val genreList = item.optString("genre").split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val desc = item.optString("description")

                    if (title.isNotBlank() && detailPath.isNotBlank() && !result.any { it.slug == detailPath }) {
                        result.add(
                            MediaItem(
                                id = detailPath,
                                title = title,
                                category = CategoryType.MOVIES,
                                thumbnail = coverUrl,
                                url = "$BASE_URL/detail/$detailPath",
                                slug = detailPath,
                                badge = if (subType == 2) "Series" else "Movie",
                                rating = rating,
                                year = year,
                                genres = genreList,
                                synopsis = desc
                            )
                        )
                    }
                }
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 3. Get Movie / Series Detail
     */
    suspend fun getDetail(urlOrPath: String): MediaDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanPath = urlOrPath.removePrefix(BASE_URL)
                .removePrefix("https://themoviebox.xyz")
                .removePrefix("/id/detail/")
                .removePrefix("/detail/")
                .removePrefix("/id/")
                .split("?")
                .first()
                .trim('/')

            val req = Request.Builder()
                .url("$API_BASE/detail?detailPath=${URLEncoder.encode(cleanPath, "UTF-8")}")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("X-Request-Lang", "id")
                .header("Origin", "https://themoviebox.xyz")
                .header("Referer", "https://themoviebox.xyz/id")
                .build()

            val resp = ApiClient.okHttpClient.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null
            val str = resp.body?.string() ?: return@withContext null
            val json = JSONObject(str)
            val data = json.optJSONObject("data") ?: return@withContext null
            val subject = data.optJSONObject("subject") ?: return@withContext null
            val resource = data.optJSONObject("resource")

            val title = subject.optString("title")
            val desc = subject.optString("description")
            val releaseDate = subject.optString("releaseDate")
            val year = if (releaseDate.length >= 4) releaseDate.take(4) else ""
            val subType = subject.optInt("subjectType", 1) // 1=Movie, 2=Series
            val isMovie = subType == 1
            val subjectId = subject.optString("subjectId")
            val coverUrl = subject.optJSONObject("cover")?.optString("url") ?: ""
            val stillsUrl = subject.optJSONObject("stills")?.optString("url") ?: coverUrl
            val rating = subject.optString("imdbRatingValue").ifBlank { "7.8" }
            val genres = subject.optString("genre").split(",").map { it.trim() }.filter { it.isNotBlank() }

            val seasonsArr = resource?.optJSONArray("seasons")
            val episodes = mutableListOf<EpisodeItem>()

            if (isMovie || seasonsArr == null || seasonsArr.length() == 0) {
                // Movies: 1 episode
                episodes.add(
                    EpisodeItem(
                        id = "$cleanPath?se=0&ep=0&subId=$subjectId",
                        episodeNumber = "1",
                        title = "Full Movie",
                        url = "$cleanPath?se=0&ep=0&subId=$subjectId"
                    )
                )
            } else {
                // Series: loop seasons & episodes
                var count = 0
                for (sIdx in 0 until seasonsArr.length()) {
                    val sObj = seasonsArr.getJSONObject(sIdx)
                    val seNum = sObj.optInt("se", 1)
                    val allEpStr = sObj.optString("allEp")
                    val maxEp = sObj.optInt("maxEp", 0)

                    val epList = when {
                        allEpStr.isNotBlank() -> allEpStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                        maxEp > 0 -> (1..maxEp).toList()
                        else -> listOf(1)
                    }

                    for (epNum in epList) {
                        count++
                        episodes.add(
                            EpisodeItem(
                                id = "$cleanPath?se=$seNum&ep=$epNum&subId=$subjectId",
                                episodeNumber = count.toString(),
                                title = "S${seNum} Episode $epNum",
                                url = "$cleanPath?se=$seNum&ep=$epNum&subId=$subjectId"
                            )
                        )
                    }
                }
            }

            MediaDetail(
                id = cleanPath,
                title = title,
                category = CategoryType.MOVIES,
                thumbnail = coverUrl,
                backdrop = stillsUrl.ifBlank { coverUrl },
                synopsis = desc.ifBlank { "Tonton $title dalam kualitas HD Subtitle Indonesia." },
                genres = genres,
                status = if (isMovie) "Film Layar Lebar ($year)" else "Serial TV ($year)",
                rating = rating,
                releaseDate = year,
                totalEpisodes = if (isMovie) "Full Movie" else "${episodes.size} Episode",
                episodes = episodes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 4. Get Direct Playable Stream (MP4) from TheMovieBox API
     */
    suspend fun getStream(urlOrPath: String, episode: Int = 1): StreamResult? = withContext(Dispatchers.IO) {
        try {
            // 1. Direct playable stream URL bypass
            if (urlOrPath.contains(".mp4", ignoreCase = true) ||
                urlOrPath.contains(".m3u8", ignoreCase = true) ||
                urlOrPath.contains("hakunaymatata.com", ignoreCase = true) ||
                urlOrPath.contains("aoneroom.com", ignoreCase = true)
            ) {
                return@withContext StreamResult(
                    title = "TheMovieBox HD",
                    directHlsUrl = urlOrPath,
                    servers = listOf(
                        StreamServerItem(name = "TheMovieBox Direct Stream", url = urlOrPath, isDirectHls = urlOrPath.contains(".m3u8"))
                    )
                )
            }

            // 2. Parse query parameters or slug
            var cleanPath = urlOrPath.removePrefix(BASE_URL)
                .removePrefix("https://themoviebox.xyz")
                .removePrefix("/id/detail/")
                .removePrefix("/detail/")
                .removePrefix("/id/")
                .trim('/')

            var seParam: Int? = null
            var epParam: Int? = null
            var subIdParam: String? = null

            if (cleanPath.contains("?")) {
                val parts = cleanPath.split("?")
                cleanPath = parts[0]
                val queryParams = parts[1].split("&").associate {
                    val kv = it.split("=")
                    if (kv.size == 2) kv[0] to kv[1] else kv[0] to ""
                }
                seParam = queryParams["se"]?.toIntOrNull()
                epParam = queryParams["ep"]?.toIntOrNull()
                subIdParam = queryParams["subId"]
            }

            // Fetch detail to get subjectId, subjectType, trailer
            val detail = getDetail(cleanPath) ?: return@withContext null
            val subId = if (!subIdParam.isNullOrBlank()) subIdParam else {
                // Try extracting subId from first episode url
                val firstUrl = detail.episodes.firstOrNull()?.url ?: ""
                Regex("""subId=([0-9]+)""").find(firstUrl)?.groupValues?.get(1) ?: detail.id
            }
            val isMovie = detail.status?.contains("Film Layar Lebar") == true || detail.totalEpisodes == "Full Movie"

            var se = seParam ?: if (isMovie) 0 else 1
            var ep = epParam ?: if (isMovie) 0 else 1

            var token = getSessionToken()

            suspend fun fetchPlay(sId: String, s: Int, e: Int): JSONObject? {
                val playUrl = "$API_BASE/subject/play?subjectId=${URLEncoder.encode(sId, "UTF-8")}&se=$s&ep=$e&detailPath=${URLEncoder.encode(cleanPath, "UTF-8")}&streamSignType=1"
                val reqBuilder = Request.Builder()
                    .url(playUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .header("X-Request-Lang", "id")
                    .header("Origin", "https://themoviebox.xyz")
                    .header("Referer", "https://themoviebox.xyz/id/spa/videoPlayPage/movies/$cleanPath")

                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                    reqBuilder.header("Cookie", "token=$token; mb_token=\"$token\"")
                }

                val resp = ApiClient.okHttpClient.newCall(reqBuilder.build()).execute()
                val str = resp.body?.string() ?: return null
                return JSONObject(str)
            }

            var playJson = fetchPlay(subId, se, ep)
            var rawStreams = playJson?.optJSONObject("data")?.optJSONArray("streams")

            // Fallback 1: If 0 streams and was not se=0, ep=0 -> try 0, 0
            if ((rawStreams == null || rawStreams.length() == 0) && (se != 0 || ep != 0)) {
                val fb0 = fetchPlay(subId, 0, 0)
                val fb0Streams = fb0?.optJSONObject("data")?.optJSONArray("streams")
                if (fb0Streams != null && fb0Streams.length() > 0) {
                    playJson = fb0
                    rawStreams = fb0Streams
                }
            }

            // Fallback 2: If 0 streams and was se=0, ep=0 -> try 1, 1
            if ((rawStreams == null || rawStreams.length() == 0) && se == 0 && ep == 0) {
                val fb1 = fetchPlay(subId, 1, 1)
                val fb1Streams = fb1?.optJSONObject("data")?.optJSONArray("streams")
                if (fb1Streams != null && fb1Streams.length() > 0) {
                    playJson = fb1
                    rawStreams = fb1Streams
                }
            }

            val servers = mutableListOf<StreamServerItem>()
            val downloads = mutableListOf<DownloadItem>()

            if (rawStreams != null && rawStreams.length() > 0) {
                for (i in 0 until rawStreams.length()) {
                    val s = rawStreams.getJSONObject(i)
                    val sUrl = s.optString("url")
                    val res = s.optString("resolutions")
                    val label = if (res.isNotBlank()) "${res}p" else "HD"
                    if (sUrl.isNotBlank()) {
                        servers.add(
                            StreamServerItem(
                                name = "TheMovieBox $label (MP4)",
                                url = sUrl,
                                isDirectHls = sUrl.contains(".m3u8")
                            )
                        )
                        downloads.add(
                            DownloadItem(
                                name = "Unduh MP4 $label",
                                url = sUrl
                            )
                        )
                    }
                }
            }

            // Fallback: HLS streams if available
            val rawHls = playJson?.optJSONObject("data")?.optJSONArray("hls")
            if (rawHls != null && rawHls.length() > 0) {
                for (i in 0 until rawHls.length()) {
                    val h = rawHls.getJSONObject(i)
                    val hUrl = h.optString("url")
                    if (hUrl.isNotBlank()) {
                        servers.add(
                            StreamServerItem(
                                name = "TheMovieBox HLS Stream",
                                url = hUrl,
                                isDirectHls = true
                            )
                        )
                    }
                }
            }

            if (servers.isNotEmpty()) {
                StreamResult(
                    title = "${detail.title} - Full Movie",
                    directHlsUrl = servers.first().url,
                    servers = servers,
                    downloads = downloads
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
