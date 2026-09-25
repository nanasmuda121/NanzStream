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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object YouTubeScraper {
    private const val INNERTUBE_API = "https://www.youtube.com/youtubei/v1"
    private const val WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val IOS_USER_AGENT = "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; id_ID)"

    private suspend fun postJson(endpoint: String, body: JSONObject, isIos: Boolean = false): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url("$INNERTUBE_API/$endpoint")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))

            if (isIos) {
                reqBuilder.header("User-Agent", IOS_USER_AGENT)
                    .header("X-YouTube-Client-Name", "5")
                    .header("X-YouTube-Client-Version", "21.03.2")
            } else {
                reqBuilder.header("User-Agent", WEB_USER_AGENT)
            }

            val resp = ApiClient.okHttpClient.newCall(reqBuilder.build()).execute()
            val str = resp.body?.string()
            if (!str.isNullOrBlank()) JSONObject(str) else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createWebContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB")
                put("clientVersion", "2.20240313.01.00")
                put("hl", "id")
                put("gl", "ID")
            })
        }
    }

    private fun createIosContext(): JSONObject {
        return JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "IOS")
                put("clientVersion", "21.03.2")
                put("deviceModel", "iPhone16,2")
                put("osName", "iOS")
                put("osVersion", "18.7.2.22H124")
                put("hl", "id")
                put("gl", "ID")
            })
        }
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val cleanQ = query.trim()
            if (cleanQ.isBlank()) return@withContext emptyList()

            val body = JSONObject().apply {
                put("context", createWebContext())
                put("query", cleanQ)
            }

            val json = postJson("search", body) ?: return@withContext emptyList()
            val primaryContents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (primaryContents != null) {
                for (i in 0 until primaryContents.length()) {
                    val sec = primaryContents.getJSONObject(i)
                    val itemSection = sec.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                    if (itemSection != null) {
                        for (j in 0 until itemSection.length()) {
                            val it = itemSection.getJSONObject(j)

                            // 1. Standard Video
                            val vr = it.optJSONObject("videoRenderer")
                            if (vr != null) {
                                val vId = vr.optString("videoId")
                                val title = vr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                val channel = vr.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                val channelId = vr.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)
                                    ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                                val thumbArr = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: ""
                                val dur = vr.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                                val views = vr.optJSONObject("viewCountText")?.optString("simpleText") ?: ""

                                if (vId.isNotBlank() && !title.isNullOrBlank() && !list.any { it.id == vId }) {
                                    list.add(
                                        MediaItem(
                                            id = vId,
                                            title = title,
                                            category = CategoryType.YOUTUBE,
                                            thumbnail = thumb,
                                            url = "https://www.youtube.com/watch?v=$vId",
                                            slug = vId,
                                            badge = dur.ifBlank { "Video" },
                                            rating = views,
                                            synopsis = if (!channel.isNullOrBlank()) "Channel: $channel" else "",
                                            genres = listOf("YouTube", channel ?: "Video")
                                        )
                                    )
                                }
                            }

                            // 2. Channel
                            val cr = it.optJSONObject("channelRenderer")
                            if (cr != null) {
                                val cId = cr.optString("channelId")
                                val cTitle = cr.optJSONObject("title")?.optString("simpleText")
                                val subs = cr.optJSONObject("subscriberCountText")?.optString("simpleText") ?: "Channel"
                                val cThumbArr = cr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                val cThumb = cThumbArr?.optJSONObject(cThumbArr.length() - 1)?.optString("url") ?: ""

                                if (cId.isNotBlank() && !cTitle.isNullOrBlank() && !list.any { it.id == cId }) {
                                    list.add(
                                        MediaItem(
                                            id = cId,
                                            title = cTitle,
                                            category = CategoryType.YOUTUBE,
                                            thumbnail = cThumb,
                                            url = "https://www.youtube.com/channel/$cId",
                                            slug = cId,
                                            badge = "Channel",
                                            rating = subs,
                                            genres = listOf("YouTube", "Channel")
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun getLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val defaultQueries = listOf("trending indonesia", "trailer movie 2026", "viral indonesia", "music video indonesia")
        val q = defaultQueries.getOrElse((page - 1) % defaultQueries.size) { "trending indonesia" }
        search(q)
    }

    suspend fun getShorts(): List<MediaItem> = withContext(Dispatchers.IO) {
        search("#shorts indonesia trending")
    }

    suspend fun getDetail(videoIdOrChannelId: String): MediaDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanId = videoIdOrChannelId.removePrefix("https://www.youtube.com/watch?v=")
                .removePrefix("https://youtu.be/")
                .removePrefix("https://www.youtube.com/channel/")
                .split("&").firstOrNull()?.trim() ?: videoIdOrChannelId

            // If it's a channel ID (starts with UC)
            if (cleanId.startsWith("UC")) {
                return@withContext getChannelDetail(cleanId)
            }

            // Video Detail via player API
            val body = JSONObject().apply {
                put("context", createWebContext())
                put("videoId", cleanId)
            }

            val json = postJson("player", body) ?: return@withContext null
            val vd = json.optJSONObject("videoDetails") ?: return@withContext null

            val title = vd.optString("title").ifBlank { "YouTube Video" }
            val author = vd.optString("author")
            val channelId = vd.optString("channelId")
            val desc = vd.optString("shortDescription")
            val views = vd.optString("viewCount")
            val lengthSec = vd.optString("lengthSeconds").toIntOrNull() ?: 0
            val durMinutes = "${lengthSec / 60}:${(lengthSec % 60).toString().padStart(2, '0')}"

            val thumbArr = vd.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumb = thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url")
                ?: "https://i.ytimg.com/vi/$cleanId/hqdefault.jpg"

            val episodes = listOf(
                EpisodeItem(
                    id = "https://www.youtube.com/watch?v=$cleanId",
                    episodeNumber = "1",
                    title = title,
                    url = "https://www.youtube.com/watch?v=$cleanId"
                )
            )

            MediaDetail(
                id = cleanId,
                title = title,
                category = CategoryType.YOUTUBE,
                thumbnail = thumb,
                backdrop = thumb,
                synopsis = desc.ifBlank { "Diunggah oleh: $author\nTotal tayangan: $views views" },
                genres = listOf("YouTube", author),
                status = "Duration: $durMinutes",
                rating = "${views}x ditonton",
                totalEpisodes = "Video",
                episodes = episodes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun getChannelDetail(channelId: String): MediaDetail? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("context", createWebContext())
                put("browseId", channelId)
            }

            val json = postJson("browse", body) ?: return@withContext null
            val header = json.optJSONObject("header")
            val pageH = header?.optJSONObject("pageHeaderRenderer")
            val c4H = header?.optJSONObject("c4TabbedHeaderRenderer")

            val title = pageH?.optString("pageTitle")
                ?: c4H?.optString("title")
                ?: "YouTube Channel"

            val avatarArr = c4H?.optJSONObject("avatar")?.optJSONArray("thumbnails")
            val avatar = avatarArr?.optJSONObject(avatarArr.length() - 1)?.optString("url") ?: ""
            val bannerArr = c4H?.optJSONObject("banner")?.optJSONArray("thumbnails")
            val banner = bannerArr?.optJSONObject(bannerArr.length() - 1)?.optString("url") ?: avatar

            val subs = c4H?.optJSONObject("subscriberCountText")?.optString("simpleText") ?: ""

            // Scrape videos in channel tabs
            val episodes = mutableListOf<EpisodeItem>()
            val tabs = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")

            if (tabs != null) {
                for (i in 0 until tabs.length()) {
                    val tab = tabs.getJSONObject(i).optJSONObject("tabRenderer")
                    val secList = tab?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                    if (secList != null) {
                        for (s in 0 until secList.length()) {
                            val isec = secList.getJSONObject(s).optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                            if (isec != null) {
                                for (k in 0 until isec.length()) {
                                    val item = isec.getJSONObject(k)
                                    val grid = item.optJSONObject("gridRenderer")?.optJSONArray("items")
                                        ?: item.optJSONObject("shelfRenderer")?.optJSONObject("content")?.optJSONObject("horizontalListRenderer")?.optJSONArray("items")

                                    if (grid != null) {
                                        for (g in 0 until grid.length()) {
                                            val gvr = grid.getJSONObject(g).optJSONObject("gridVideoRenderer")
                                                ?: grid.getJSONObject(g).optJSONObject("videoRenderer")
                                            if (gvr != null) {
                                                val vId = gvr.optString("videoId")
                                                val vTitle = gvr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                                    ?: gvr.optJSONObject("title")?.optString("simpleText") ?: "Video"
                                                if (vId.isNotBlank() && !episodes.any { it.id.contains(vId) }) {
                                                    episodes.add(
                                                        EpisodeItem(
                                                            id = "https://www.youtube.com/watch?v=$vId",
                                                            episodeNumber = (episodes.size + 1).toString(),
                                                            title = vTitle,
                                                            url = "https://www.youtube.com/watch?v=$vId"
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (episodes.isEmpty()) {
                // If channel tab didn't load videos directly, fetch search by channel name
                val searchVids = search(title)
                searchVids.forEachIndexed { idx, vid ->
                    episodes.add(
                        EpisodeItem(
                            id = vid.url ?: "https://www.youtube.com/watch?v=${vid.id}",
                            episodeNumber = (idx + 1).toString(),
                            title = vid.title,
                            url = vid.url ?: "https://www.youtube.com/watch?v=${vid.id}"
                        )
                    )
                }
            }

            MediaDetail(
                id = channelId,
                title = title,
                category = CategoryType.YOUTUBE,
                thumbnail = avatar,
                backdrop = banner,
                synopsis = "Channel YouTube Resmi: $title\nSubscribers: $subs",
                genres = listOf("YouTube", "Channel"),
                status = subs,
                rating = "Channel",
                totalEpisodes = "${episodes.size} Video",
                episodes = episodes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getStream(videoIdOrUrl: String, episode: Int = 1): StreamResult? = withContext(Dispatchers.IO) {
        try {
            val vId = if (videoIdOrUrl.contains("v=")) {
                Regex("""v=([a-zA-Z0-9_\-]+)""").find(videoIdOrUrl)?.groupValues?.get(1) ?: videoIdOrUrl
            } else if (videoIdOrUrl.contains("youtu.be/")) {
                Regex("""youtu\.be/([a-zA-Z0-9_\-]+)""").find(videoIdOrUrl)?.groupValues?.get(1) ?: videoIdOrUrl
            } else {
                videoIdOrUrl.removePrefix("https://www.youtube.com/watch?v=").trim()
            }

            val body = JSONObject().apply {
                put("context", createIosContext())
                put("videoId", vId)
            }

            val json = postJson("player", body, isIos = true) ?: return@withContext null
            val sd = json.optJSONObject("streamingData")
            val hlsManifest = sd?.optString("hlsManifestUrl")
            val title = json.optJSONObject("videoDetails")?.optString("title") ?: "YouTube Video"

            if (!hlsManifest.isNullOrBlank() && hlsManifest.startsWith("http")) {
                StreamResult(
                    title = title,
                    directHlsUrl = hlsManifest,
                    iframePlayerUrl = null,
                    servers = listOf(
                        StreamServerItem(
                            name = "Google HLS Direct Stream",
                            url = hlsManifest,
                            isDirectHls = true
                        )
                    )
                )
            } else {
                // Fallback to WEB embed if HLS manifest is unavailable
                StreamResult(
                    title = title,
                    directHlsUrl = null,
                    iframePlayerUrl = "https://www.youtube.com/embed/$vId?autoplay=1",
                    servers = listOf(
                        StreamServerItem(
                            name = "YouTube Player",
                            url = "https://www.youtube.com/embed/$vId?autoplay=1",
                            isDirectHls = false
                        )
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
