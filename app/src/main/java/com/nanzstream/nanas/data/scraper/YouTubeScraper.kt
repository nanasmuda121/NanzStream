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
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object YouTubeScraper {
    private const val INNERTUBE_API = "https://www.youtube.com/youtubei/v1"
    private const val WEB_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    const val IOS_USER_AGENT = "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; id_ID)"

    private fun fixUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "https://www.youtube.com$trimmed"
            else -> trimmed
        }
    }

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

                            // 1. Standard Video / Short
                            val vr = it.optJSONObject("videoRenderer")
                            if (vr != null) {
                                val vId = vr.optString("videoId")
                                val title = vr.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                val channel = vr.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                val thumbArr = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                val thumb = fixUrl(thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url"))
                                val dur = vr.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                                val views = vr.optJSONObject("viewCountText")?.optString("simpleText") ?: ""

                                // Extract channel avatar for video
                                val chThumbRaw = vr.optJSONObject("channelThumbnailSupportedRenderers")
                                    ?.optJSONObject("channelThumbnailWithLinkRenderer")
                                    ?.optJSONObject("thumbnail")
                                    ?.optJSONArray("thumbnails")
                                    ?.let { arr -> arr.optJSONObject(arr.length() - 1)?.optString("url") }
                                val chThumb = fixUrl(chThumbRaw)

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
                                val cThumb = fixUrl(cThumbArr?.optJSONObject(cThumbArr.length() - 1)?.optString("url"))

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
        search("shorts indonesia trending viral")
    }

    suspend fun getDetail(videoIdOrChannelId: String): MediaDetail? = withContext(Dispatchers.IO) {
        try {
            val cleanId = when {
                videoIdOrChannelId.contains("/shorts/") -> Regex("""/shorts/([a-zA-Z0-9_\-]+)""").find(videoIdOrChannelId)?.groupValues?.get(1) ?: videoIdOrChannelId
                videoIdOrChannelId.contains("v=") -> Regex("""v=([a-zA-Z0-9_\-]+)""").find(videoIdOrChannelId)?.groupValues?.get(1) ?: videoIdOrChannelId
                videoIdOrChannelId.contains("youtu.be/") -> Regex("""youtu\.be/([a-zA-Z0-9_\-]+)""").find(videoIdOrChannelId)?.groupValues?.get(1) ?: videoIdOrChannelId
                videoIdOrChannelId.contains("youtube.com/channel/") -> videoIdOrChannelId.removePrefix("https://www.youtube.com/channel/").split("/").firstOrNull() ?: videoIdOrChannelId
                else -> videoIdOrChannelId.removePrefix("https://www.youtube.com/watch?v=").trim()
            }

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
            val thumb = fixUrl(
                thumbArr?.optJSONObject(thumbArr.length() - 1)?.optString("url")
                    ?: "https://i.ytimg.com/vi/$cleanId/hqdefault.jpg"
            )

            // Also fetch next endpoint to get related videos and channel avatar
            var channelAvatar: String? = null
            val relatedVideos = mutableListOf<EpisodeItem>()
            try {
                val nextBody = JSONObject().apply {
                    put("context", createWebContext())
                    put("videoId", cleanId)
                }
                val nextJson = postJson("next", nextBody)
                val nextStr = nextJson?.toString() ?: ""
                val avatarMatch = Regex("""\"avatar\":\{.*?\"url\":\"([^\"]+)\"""").find(nextStr)
                if (avatarMatch != null) {
                    channelAvatar = fixUrl(avatarMatch.groupValues[1])
                }

                // Extract related videos
                val vidsMatches = Regex("""\"videoId\":\"([a-zA-Z0-9_\-]{11})\".*?\"text\":\"([^\"]+)\"""").findAll(nextStr)
                var count = 1
                for (m in vidsMatches) {
                    val rId = m.groupValues[1]
                    val rTitle = m.groupValues[2]
                    if (rId != cleanId && !relatedVideos.any { it.url.contains(rId) }) {
                        count++
                        relatedVideos.add(
                            EpisodeItem(
                                id = "https://www.youtube.com/watch?v=$rId",
                                episodeNumber = count.toString(),
                                title = rTitle,
                                url = "https://www.youtube.com/watch?v=$rId"
                            )
                        )
                        if (relatedVideos.size >= 15) break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val episodes = mutableListOf<EpisodeItem>().apply {
                add(
                    EpisodeItem(
                        id = "https://www.youtube.com/watch?v=$cleanId",
                        episodeNumber = "1",
                        title = title,
                        url = "https://www.youtube.com/watch?v=$cleanId"
                    )
                )
                addAll(relatedVideos)
            }

            MediaDetail(
                id = cleanId,
                title = title,
                category = CategoryType.YOUTUBE,
                thumbnail = thumb,
                backdrop = channelAvatar ?: thumb,
                synopsis = desc.ifBlank { "Channel: $author\nTotal tayangan: $views views\nDurasi: $durMinutes" },
                genres = listOf("YouTube", author),
                status = "Durasi: $durMinutes",
                rating = "${views}x ditonton",
                totalEpisodes = if (relatedVideos.isNotEmpty()) "${episodes.size} Video" else "Video",
                episodes = episodes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getChannelDetail(channelId: String): MediaDetail? = withContext(Dispatchers.IO) {
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

            // Modern YouTube WEB stores avatar in pageHeaderViewModel
            val vm = pageH?.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")
            val avatarSources = vm?.optJSONObject("image")
                ?.optJSONObject("decoratedAvatarViewModel")
                ?.optJSONObject("avatar")
                ?.optJSONObject("avatarViewModel")
                ?.optJSONObject("image")
                ?.optJSONArray("sources")

            val bannerSources = vm?.optJSONObject("banner")
                ?.optJSONObject("imageBannerViewModel")
                ?.optJSONObject("image")
                ?.optJSONArray("sources")

            val rawAvatar = avatarSources?.optJSONObject(avatarSources.length() - 1)?.optString("url")
                ?: c4H?.optJSONObject("avatar")?.optJSONArray("thumbnails")?.let { it.optJSONObject(it.length() - 1)?.optString("url") }
                ?: ""

            val rawBanner = bannerSources?.optJSONObject(bannerSources.length() - 1)?.optString("url")
                ?: c4H?.optJSONObject("banner")?.optJSONArray("thumbnails")?.let { it.optJSONObject(it.length() - 1)?.optString("url") }
                ?: rawAvatar

            val avatar = fixUrl(rawAvatar)
            val banner = fixUrl(rawBanner)
            val subs = c4H?.optJSONObject("subscriberCountText")?.optString("simpleText") ?: "Channel"

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
            val vId = when {
                videoIdOrUrl.contains("/shorts/") -> Regex("""/shorts/([a-zA-Z0-9_\-]+)""").find(videoIdOrUrl)?.groupValues?.get(1) ?: videoIdOrUrl
                videoIdOrUrl.contains("v=") -> Regex("""v=([a-zA-Z0-9_\-]+)""").find(videoIdOrUrl)?.groupValues?.get(1) ?: videoIdOrUrl
                videoIdOrUrl.contains("youtu.be/") -> Regex("""youtu\.be/([a-zA-Z0-9_\-]+)""").find(videoIdOrUrl)?.groupValues?.get(1) ?: videoIdOrUrl
                videoIdOrUrl.startsWith("http") -> videoIdOrUrl.substringAfterLast("/").substringBefore("?")
                else -> videoIdOrUrl.trim()
            }

            val body = JSONObject().apply {
                put("context", createIosContext())
                put("videoId", vId)
            }

            val json = postJson("player", body, isIos = true) ?: return@withContext null
            val sd = json.optJSONObject("streamingData")
            val title = json.optJSONObject("videoDetails")?.optString("title") ?: "YouTube Video"

            val hlsManifest = sd?.optString("hlsManifestUrl")
            val adaptiveArr = sd?.optJSONArray("adaptiveFormats")
            val progressiveArr = sd?.optJSONArray("formats")

            var bestAudioUrl: String? = null
            val videoFormats = mutableListOf<Triple<String, String, Int>>() // label, url, itag
            val downloadItems = mutableListOf<DownloadItem>()

            if (adaptiveArr != null) {
                // Find best audio URL (prefer AAC 128k, itag 140)
                for (i in 0 until adaptiveArr.length()) {
                    val af = adaptiveArr.getJSONObject(i)
                    val url = af.optString("url")
                    val mime = af.optString("mimeType")
                    if (url.isNotBlank() && mime.contains("audio/mp4")) {
                        if (bestAudioUrl == null || af.optString("audioQuality") == "AUDIO_QUALITY_MEDIUM") {
                            bestAudioUrl = url
                        }
                    }
                }

                // If no audio/mp4 found, take any audio format with url
                if (bestAudioUrl == null) {
                    for (i in 0 until adaptiveArr.length()) {
                        val af = adaptiveArr.getJSONObject(i)
                        val url = af.optString("url")
                        val mime = af.optString("mimeType")
                        if (url.isNotBlank() && mime.startsWith("audio/")) {
                            bestAudioUrl = url
                            break
                        }
                    }
                }

                // Collect video formats with direct MP4 url
                for (i in 0 until adaptiveArr.length()) {
                    val af = adaptiveArr.getJSONObject(i)
                    val url = af.optString("url")
                    val mime = af.optString("mimeType")
                    val qLabel = af.optString("qualityLabel")
                    val itag = af.optInt("itag")

                    if (url.isNotBlank() && mime.contains("video/mp4") && qLabel.isNotBlank()) {
                        if (!videoFormats.any { it.first == qLabel }) {
                            videoFormats.add(Triple(qLabel, url, itag))
                            downloadItems.add(
                                DownloadItem(
                                    name = "Video MP4 ($qLabel)",
                                    url = url,
                                    quality = qLabel
                                )
                            )
                        }
                    }
                }
            }

            // Also add audio download if present
            if (!bestAudioUrl.isNullOrBlank()) {
                downloadItems.add(
                    DownloadItem(
                        name = "Audio M4A / AAC",
                        url = bestAudioUrl,
                        quality = "128kbps"
                    )
                )
            }

            // Check progressive formats (audio+video in 1 stream)
            var directProgressiveUrl: String? = null
            if (progressiveArr != null) {
                for (i in 0 until progressiveArr.length()) {
                    val pf = progressiveArr.getJSONObject(i)
                    val url = pf.optString("url")
                    if (url.isNotBlank()) {
                        directProgressiveUrl = url
                        break
                    }
                }
            }

            val servers = mutableListOf<StreamServerItem>()

            // 1. If progressive direct MP4 exists (single file with video + audio)
            if (!directProgressiveUrl.isNullOrBlank()) {
                servers.add(
                    StreamServerItem(
                        name = "YouTube Direct MP4",
                        url = directProgressiveUrl,
                        isDirectHls = false,
                        audioUrl = null
                    )
                )
            }

            // 2. Add adaptive MP4 servers (merged video + audio via ExoPlayer MergingMediaSource)
            // Sort by resolution: 1080p, 720p, 480p, 360p, 240p
            videoFormats.sortedByDescending { triple ->
                Regex("""(\d+)p""").find(triple.first)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }.forEach { triple ->
                servers.add(
                    StreamServerItem(
                        name = "YouTube ${triple.first} MP4 (Direct)",
                        url = triple.second,
                        isDirectHls = false,
                        audioUrl = bestAudioUrl
                    )
                )
            }

            // 3. If HLS manifest is available, add as fallback
            if (!hlsManifest.isNullOrBlank() && hlsManifest.startsWith("http")) {
                servers.add(
                    0,
                    StreamServerItem(
                        name = "Google HLS Direct Stream",
                        url = hlsManifest,
                        isDirectHls = true,
                        audioUrl = null
                    )
                )
            }

            if (servers.isNotEmpty()) {
                val primaryServer = servers.first()
                StreamResult(
                    title = title,
                    directHlsUrl = primaryServer.url,
                    iframePlayerUrl = null,
                    servers = servers,
                    downloads = downloadItems,
                    audioUrl = primaryServer.audioUrl
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
