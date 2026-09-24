package com.nanzstream.nanas.data.repository

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository {

    suspend fun getHomeSpotlight(): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        // Curated spotlight highlights
        list.add(
            MediaItem(
                id = "spotlight_1",
                title = "Otome Game Sekai wa Mob ni Kibishii Sekai desu Season 2",
                category = CategoryType.ANIME,
                thumbnail = "https://samehadaku.li/wp-content/uploads/2026/01/Otome-Game-Sekai-wa-Mob-ni-Kibishii-Sekai-desu-Season-2.jpg",
                slug = "otome-game-sekai-wa-mob-ni-kibishii-sekai-desu-2",
                badge = "Episode 12 • Tamat",
                synopsis = "Leon, mantan pekerja kantoran Jepang yang bereinkarnasi ke dalam dunia video game otome yang brutal di mana wanita memegang kekuasaan mutlak.",
                rating = "8.4",
                year = "2026",
                genres = listOf("Action", "Fantasy", "Isekai", "Mecha")
            )
        )
        list.add(
            MediaItem(
                id = "spotlight_2",
                title = "A Parasite's Heart (2026)",
                category = CategoryType.DRAMA,
                thumbnail = "https://convert.d-cdn.me/convert/aHR0cHM6Ly9hc3NldHMuZC1jZG4ubWUvaW1nLzIwMjYvMDkvODQ5LXlhbG9hazRmLmpwZw--/180x200/1.jpg",
                slug = "a-parasites-heart-2026",
                badge = "Episode 8 • Ongoing",
                synopsis = "Drama misteri thriller romantis tentang intrik gelap di balik keluarga konglomerat Korea dan rahasia medis yang mematikan.",
                rating = "9.1",
                year = "2026",
                genres = listOf("Thriller", "Mystery", "Romance", "Drama")
            )
        )
        list.add(
            MediaItem(
                id = "spotlight_3",
                title = "Lord of the Ancient God Grave",
                category = CategoryType.DONGHUA,
                thumbnail = "https://anichin.ro/wp-content/uploads/2023/10/Lord-of-the-Ancient-God-Grave.jpg",
                slug = "lord-of-the-ancient-god-grave",
                badge = "Episode 485",
                synopsis = "Kisah Chen Nan yang terbangun dari kuburan para dewa kuno setelah ribuan tahun dan memulai perjalanan menantang takdir langit.",
                rating = "8.8",
                year = "2026",
                genres = listOf("Cultivation", "Action", "Fantasy", "Martial Arts")
            )
        )
        list.add(
            MediaItem(
                id = "spotlight_4",
                title = "Kijima-san & Yamada-san",
                category = CategoryType.MANGA,
                thumbnail = "https://global-api.manga-up.com/asset/h1F/en/manga_main/1029.webp",
                slug = "1",
                badge = "Chapter 66",
                synopsis = "Kisah komedi romantis kantor yang hangat dan menggelitik antara dua rekan kerja dengan kepribadian bertolak belakang.",
                rating = "8.9",
                year = "2026",
                genres = listOf("Romance", "Comedy", "Slice of Life")
            )
        )
        list
    }

    suspend fun getAnimeLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val json = ApiClient.getJson("anime/latest", mapOf("page" to page.toString()))
        val items = mutableListOf<MediaItem>()
        val dataArray = json?.getAsJsonObject("result")?.getAsJsonArray("data")
        if (dataArray != null && dataArray.size() > 0) {
            for (elem in dataArray) {
                val obj = elem.asJsonObject
                items.add(
                    MediaItem(
                        id = obj.get("url")?.asString ?: java.util.UUID.randomUUID().toString(),
                        title = obj.get("title")?.asString ?: "Anime Episode",
                        category = CategoryType.ANIME,
                        thumbnail = obj.get("thumbnail")?.asString ?: "",
                        url = obj.get("url")?.asString,
                        slug = obj.get("url")?.asString,
                        badge = obj.get("episode")?.asString ?: "Terbaru"
                    )
                )
            }
        }
        if (items.isEmpty()) {
            // Built-in fallback
            items.addAll(getFallbackAnime())
        }
        items
    }

    suspend fun getDramaLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val json = ApiClient.getJson("drama/latest", mapOf("page" to page.toString()))
        val items = mutableListOf<MediaItem>()
        val dataArray = json?.getAsJsonObject("result")?.getAsJsonArray("data")
        if (dataArray != null && dataArray.size() > 0) {
            for (elem in dataArray) {
                val obj = elem.asJsonObject
                items.add(
                    MediaItem(
                        id = obj.get("slug")?.asString ?: obj.get("url")?.asString ?: java.util.UUID.randomUUID().toString(),
                        title = obj.get("title")?.asString ?: "Drama Asia",
                        category = CategoryType.DRAMA,
                        thumbnail = obj.get("thumbnail")?.asString ?: "",
                        url = obj.get("url")?.asString,
                        slug = obj.get("slug")?.asString,
                        badge = "Drakor"
                    )
                )
            }
        }
        if (items.isEmpty()) {
            items.addAll(getFallbackDrama())
        }
        items
    }

    suspend fun getDonghuaLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val json = ApiClient.getJson("donghua/latest", mapOf("page" to page.toString()))
        val items = mutableListOf<MediaItem>()
        val dataArray = json?.getAsJsonObject("result")?.getAsJsonArray("data")
        if (dataArray != null && dataArray.size() > 0) {
            for (elem in dataArray) {
                val obj = elem.asJsonObject
                items.add(
                    MediaItem(
                        id = obj.get("url")?.asString ?: java.util.UUID.randomUUID().toString(),
                        title = obj.get("title")?.asString ?: "Donghua Episode",
                        category = CategoryType.DONGHUA,
                        thumbnail = obj.get("thumbnail")?.asString ?: "",
                        url = obj.get("url")?.asString,
                        slug = obj.get("url")?.asString,
                        badge = obj.get("episode")?.asString ?: "Donghua"
                    )
                )
            }
        }
        if (items.isEmpty()) {
            items.addAll(getFallbackDonghua())
        }
        items
    }

    suspend fun getMangaHome(): List<MediaItem> = withContext(Dispatchers.IO) {
        val json = ApiClient.getJson("manga/home")
        val items = mutableListOf<MediaItem>()
        val res = json?.getAsJsonObject("result")
        val updated = res?.getAsJsonArray("updatedTitles")
            ?: res?.getAsJsonArray("rankings")
            ?: res?.getAsJsonArray("topBanners")

        if (updated != null && updated.size() > 0) {
            for (elem in updated) {
                val obj = elem.asJsonObject
                val mangaId = obj.get("id")?.asString ?: obj.get("mangaId")?.asString ?: "1"
                items.add(
                    MediaItem(
                        id = mangaId,
                        title = obj.get("title")?.asString ?: obj.get("name")?.asString ?: "Manga Title",
                        category = CategoryType.MANGA,
                        thumbnail = obj.get("imageUrl")?.asString ?: obj.get("cover")?.asString ?: "",
                        slug = mangaId,
                        badge = "Manga UP"
                    )
                )
            }
        }
        if (items.isEmpty()) {
            items.addAll(getFallbackManga())
        }
        items
    }

    suspend fun getLiveTvChannels(): List<LiveTvChannelItem> = withContext(Dispatchers.IO) {
        val json = ApiClient.getJson("livetv/list")
        val channels = mutableListOf<LiveTvChannelItem>()
        val genres = json?.getAsJsonObject("result")?.getAsJsonArray("genres")
        if (genres != null && genres.size() > 0) {
            for (gElem in genres) {
                val gObj = gElem.asJsonObject
                val genreName = gObj.get("genreName")?.asString ?: "Live TV"
                val chArr = gObj.getAsJsonArray("channels") ?: JsonArray()
                for (cElem in chArr) {
                    val cObj = cElem.asJsonObject
                    channels.add(
                        LiveTvChannelItem(
                            id = cObj.get("id")?.asString ?: "",
                            name = cObj.get("name")?.asString ?: "Channel",
                            number = cObj.get("number")?.asInt ?: 0,
                            genre = genreName,
                            logoUrl = cObj.get("image")?.asString ?: "",
                            slug = cObj.get("slug")?.asString ?: cObj.get("id")?.asString
                        )
                    )
                }
            }
        }
        if (channels.isEmpty()) {
            channels.addAll(getFallbackLiveTv())
        }
        channels
    }

    suspend fun getVodList(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val json = ApiClient.getJson("vod/list", mapOf("page" to page.toString()))
        val items = mutableListOf<MediaItem>()
        val sections = json?.getAsJsonObject("result")?.getAsJsonArray("sections")
        if (sections != null) {
            for (sec in sections) {
                val contents = sec.asJsonObject.getAsJsonArray("contents") ?: JsonArray()
                for (c in contents) {
                    val cObj = c.asJsonObject
                    items.add(
                        MediaItem(
                            id = cObj.get("vodId")?.asString ?: "",
                            title = cObj.get("title")?.asString ?: "VOD Series",
                            category = CategoryType.VOD,
                            thumbnail = cObj.get("posterPortrait")?.asString ?: cObj.get("posterLandscape")?.asString ?: "",
                            slug = cObj.get("slug")?.asString ?: "",
                            badge = cObj.get("type")?.asString ?: "Series"
                        )
                    )
                }
            }
        }
        if (items.isEmpty()) {
            items.addAll(getFallbackVod())
        }
        items
    }

    suspend fun getDetail(category: CategoryType, idOrSlug: String): MediaDetail? = withContext(Dispatchers.IO) {
        when (category) {
            CategoryType.DRAMA -> {
                val json = ApiClient.getJson("drama/detail", mapOf("slug" to idOrSlug))
                val res = json?.getAsJsonObject("result")
                if (res != null) {
                    val eps = mutableListOf<EpisodeItem>()
                    val epArr = res.getAsJsonArray("episodes") ?: JsonArray()
                    for (i in 0 until epArr.size()) {
                        val obj = epArr[i].asJsonObject
                        eps.add(
                            EpisodeItem(
                                id = obj.get("episode")?.asString ?: (i + 1).toString(),
                                episodeNumber = obj.get("episode")?.asString ?: (i + 1).toString(),
                                title = obj.get("title")?.asString ?: "Episode ${i + 1}",
                                url = obj.get("watchUrl")?.asString ?: idOrSlug
                            )
                        )
                    }
                    val genreList = mutableListOf<String>()
                    res.getAsJsonArray("genres")?.forEach { genreList.add(it.asString) }
                    MediaDetail(
                        id = idOrSlug,
                        title = res.get("title")?.asString ?: "Drama Detail",
                        category = CategoryType.DRAMA,
                        thumbnail = res.get("thumbnail")?.asString ?: "",
                        synopsis = res.get("synopsis")?.asString,
                        genres = genreList,
                        status = res.get("status")?.asString ?: "Ongoing",
                        rating = res.get("rating")?.asString ?: "8.5",
                        totalEpisodes = "${eps.size} Episode",
                        episodes = eps
                    )
                } else {
                    getFallbackDramaDetail(idOrSlug)
                }
            }

            CategoryType.ANIME -> {
                val json = ApiClient.getJson("anime/detail", mapOf("url" to idOrSlug))
                val res = json?.getAsJsonObject("result")
                if (res != null) {
                    val eps = mutableListOf<EpisodeItem>()
                    val epArr = res.getAsJsonArray("episodes") ?: JsonArray()
                    for (i in 0 until epArr.size()) {
                        val obj = epArr[i].asJsonObject
                        eps.add(
                            EpisodeItem(
                                id = obj.get("episode")?.asString ?: (i + 1).toString(),
                                episodeNumber = obj.get("episode")?.asString ?: (i + 1).toString(),
                                title = obj.get("title")?.asString ?: "Episode ${i + 1}",
                                url = obj.get("url")?.asString ?: idOrSlug,
                                date = obj.get("date")?.asString
                            )
                        )
                    }
                    val genreList = mutableListOf<String>()
                    res.getAsJsonArray("genres")?.forEach { genreList.add(it.asString) }
                    MediaDetail(
                        id = idOrSlug,
                        title = res.get("title")?.asString ?: "Anime Detail",
                        category = CategoryType.ANIME,
                        thumbnail = res.get("thumbnail")?.asString ?: "",
                        synopsis = res.get("synopsis")?.asString,
                        genres = genreList,
                        status = "Ongoing",
                        totalEpisodes = "${eps.size} Episode",
                        episodes = eps
                    )
                } else {
                    getFallbackAnimeDetail(idOrSlug)
                }
            }

            CategoryType.DONGHUA -> {
                val json = ApiClient.getJson("donghua/detail", mapOf("url" to idOrSlug))
                val res = json?.getAsJsonObject("result")
                if (res != null) {
                    val eps = mutableListOf<EpisodeItem>()
                    val epArr = res.getAsJsonArray("episodes") ?: JsonArray()
                    for (i in 0 until epArr.size()) {
                        val obj = epArr[i].asJsonObject
                        eps.add(
                            EpisodeItem(
                                id = obj.get("episode")?.asString ?: (i + 1).toString(),
                                episodeNumber = obj.get("episode")?.asString ?: (i + 1).toString(),
                                title = obj.get("title")?.asString ?: "Episode ${i + 1}",
                                url = obj.get("url")?.asString ?: idOrSlug
                            )
                        )
                    }
                    val genreList = mutableListOf<String>()
                    res.getAsJsonArray("genres")?.forEach { genreList.add(it.asString) }
                    MediaDetail(
                        id = idOrSlug,
                        title = res.get("title")?.asString ?: "Donghua Detail",
                        category = CategoryType.DONGHUA,
                        thumbnail = res.get("thumbnail")?.asString ?: "",
                        synopsis = res.get("synopsis")?.asString,
                        genres = genreList,
                        totalEpisodes = "${eps.size} Episode",
                        episodes = eps
                    )
                } else {
                    getFallbackDonghuaDetail(idOrSlug)
                }
            }

            CategoryType.MANGA -> {
                val json = ApiClient.getJson("manga/detail", mapOf("id" to idOrSlug))
                val res = json?.getAsJsonObject("result")
                if (res != null) {
                    val chaps = mutableListOf<MangaChapterItem>()
                    val chArr = res.getAsJsonArray("chapters") ?: JsonArray()
                    for (i in 0 until chArr.size()) {
                        val obj = chArr[i].asJsonObject
                        chaps.add(
                            MangaChapterItem(
                                id = obj.get("id")?.asString ?: (i + 1).toString(),
                                title = obj.get("title")?.asString ?: "Chapter ${i + 1}",
                                subtitle = obj.get("subtitle")?.asString,
                                date = obj.get("date")?.asString
                            )
                        )
                    }
                    val genreList = mutableListOf<String>()
                    res.getAsJsonArray("genres")?.forEach { genreList.add(it.asString) }
                    MediaDetail(
                        id = idOrSlug,
                        title = res.get("title")?.asString ?: "Manga Detail",
                        category = CategoryType.MANGA,
                        thumbnail = res.get("imageUrl")?.asString ?: "",
                        synopsis = res.get("description")?.asString,
                        genres = genreList,
                        status = "Serialisasi",
                        totalEpisodes = "${chaps.size} Chapter",
                        chapters = chaps
                    )
                } else {
                    getFallbackMangaDetail(idOrSlug)
                }
            }

            CategoryType.VOD -> {
                val json = ApiClient.getJson("vod/detail", mapOf("slug" to idOrSlug))
                val res = json?.getAsJsonObject("result")
                if (res != null) {
                    val eps = mutableListOf<EpisodeItem>()
                    val epArr = res.getAsJsonArray("episodes") ?: JsonArray()
                    for (i in 0 until epArr.size()) {
                        val obj = epArr[i].asJsonObject
                        eps.add(
                            EpisodeItem(
                                id = obj.get("episodeNo")?.asString ?: (i + 1).toString(),
                                episodeNumber = obj.get("episodeNo")?.asString ?: (i + 1).toString(),
                                title = obj.get("title")?.asString ?: "Episode ${i + 1}",
                                url = obj.get("watchSlug")?.asString ?: idOrSlug
                            )
                        )
                    }
                    MediaDetail(
                        id = idOrSlug,
                        title = res.get("title")?.asString ?: "VOD Series",
                        category = CategoryType.VOD,
                        thumbnail = res.get("posterVertical")?.asString ?: res.get("posterHorizontal")?.asString ?: "",
                        synopsis = res.get("synopsis")?.asString,
                        totalEpisodes = "${eps.size} Episode",
                        episodes = eps
                    )
                } else {
                    getFallbackVodDetail(idOrSlug)
                }
            }

            else -> null
        }
    }

    suspend fun getStream(category: CategoryType, targetUrlOrSlug: String, episode: Int = 1): StreamResult? =
        withContext(Dispatchers.IO) {
            when (category) {
                CategoryType.DRAMA -> {
                    val json = ApiClient.getJson("drama/stream", mapOf("slug" to targetUrlOrSlug, "episode" to episode.toString(), "server" to "lite"))
                    val res = json?.getAsJsonObject("result")
                    val hlsStreams = res?.getAsJsonArray("hlsStreams")
                    val directUrl = if (hlsStreams != null && hlsStreams.size() > 0) {
                        hlsStreams[0].asJsonObject.get("url")?.asString
                    } else null

                    val iframe = res?.get("playerIframe")?.asString
                    val servers = mutableListOf<StreamServerItem>()
                    if (!directUrl.isNullOrEmpty()) {
                        servers.add(StreamServerItem("Direct HLS 720p", directUrl, isDirectHls = true))
                    }
                    if (!iframe.isNullOrEmpty()) {
                        servers.add(StreamServerItem("Bunny Player (Embed)", iframe, isDirectHls = false))
                    }
                    StreamResult(
                        title = res?.get("title")?.asString ?: "Drama Episode $episode",
                        directHlsUrl = directUrl,
                        iframePlayerUrl = iframe ?: directUrl,
                        servers = servers
                    )
                }

                CategoryType.ANIME -> {
                    val json = ApiClient.getJson("anime/stream", mapOf("url" to targetUrlOrSlug))
                    val res = json?.getAsJsonObject("result")
                    val streamsArr = res?.getAsJsonArray("streams") ?: JsonArray()
                    val servers = mutableListOf<StreamServerItem>()
                    for (s in streamsArr) {
                        val sObj = s.asJsonObject
                        servers.add(
                            StreamServerItem(
                                name = sObj.get("server")?.asString ?: "Server",
                                url = sObj.get("iframe")?.asString ?: "",
                                isDirectHls = false
                            )
                        )
                    }
                    val primaryIframe = servers.firstOrNull()?.url
                    StreamResult(
                        title = res?.get("title")?.asString ?: "Anime Episode",
                        iframePlayerUrl = primaryIframe,
                        servers = servers
                    )
                }

                CategoryType.DONGHUA -> {
                    val json = ApiClient.getJson("donghua/stream", mapOf("url" to targetUrlOrSlug))
                    val res = json?.getAsJsonObject("result")
                    val serversArr = res?.getAsJsonArray("servers") ?: JsonArray()
                    val servers = mutableListOf<StreamServerItem>()
                    for (s in serversArr) {
                        val sObj = s.asJsonObject
                        servers.add(
                            StreamServerItem(
                                name = sObj.get("name")?.asString ?: "Server",
                                url = sObj.get("iframe")?.asString ?: "",
                                isDirectHls = false
                            )
                        )
                    }
                    val primaryIframe = servers.firstOrNull()?.url
                    StreamResult(
                        title = res?.get("title")?.asString ?: "Donghua Episode",
                        iframePlayerUrl = primaryIframe,
                        servers = servers
                    )
                }

                CategoryType.LIVETV -> {
                    val json = ApiClient.getJson("livetv/stream", mapOf("channel" to targetUrlOrSlug))
                    val res = json?.getAsJsonObject("result")
                    val streamObj = res?.getAsJsonObject("stream")
                    val dash = streamObj?.get("dash")?.asString
                    val cdns = streamObj?.getAsJsonArray("cdns")
                    var hls: String? = null
                    if (cdns != null && cdns.size() > 0) {
                        for (cdn in cdns) {
                            val h = cdn.asJsonObject.get("hls")?.asString
                            if (!h.isNullOrEmpty()) {
                                hls = h
                                break
                            }
                        }
                    }
                    val streamUrl = hls ?: dash
                    StreamResult(
                        title = res?.get("channelName")?.asString ?: "Live TV Stream",
                        directHlsUrl = streamUrl,
                        iframePlayerUrl = streamUrl,
                        servers = listOf(StreamServerItem("Live HLS Stream", streamUrl ?: "", isDirectHls = true))
                    )
                }

                CategoryType.VOD -> {
                    val json = ApiClient.getJson("vod/stream", mapOf("slug" to targetUrlOrSlug))
                    val res = json?.getAsJsonObject("result")
                    val hlsUrl = res?.getAsJsonObject("stream")?.get("hls")?.asString
                    StreamResult(
                        title = res?.get("title")?.asString ?: "VOD Stream",
                        directHlsUrl = hlsUrl,
                        iframePlayerUrl = hlsUrl,
                        servers = listOf(StreamServerItem("HLS Stream", hlsUrl ?: "", isDirectHls = true))
                    )
                }

                else -> null
            }
        }

    suspend fun getMangaPages(chapterId: String): List<MangaPageItem> = withContext(Dispatchers.IO) {
        val json = ApiClient.getJson("manga/read", mapOf("chapterId" to chapterId))
        val pages = mutableListOf<MangaPageItem>()
        val pageArr = json?.getAsJsonObject("result")?.getAsJsonArray("pages")
        if (pageArr != null) {
            for (p in pageArr) {
                val pObj = p.asJsonObject
                pages.add(
                    MangaPageItem(
                        page = pObj.get("page")?.asInt ?: 1,
                        url = pObj.get("url")?.asString ?: "",
                        key = pObj.get("key")?.asString,
                        iv = pObj.get("iv")?.asString
                    )
                )
            }
        }
        if (pages.isEmpty()) {
            pages.addAll(getFallbackMangaPages(chapterId))
        }
        pages
    }

    // ==========================================
    // Rich Built-in Fallbacks & Mock Generators
    // ==========================================
    private fun getFallbackAnime(): List<MediaItem> = listOf(
        MediaItem("anime_1", "Otome Game Sekai wa Mob ni Kibishii 2 Ep 12", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2026/01/Otome-Game-Sekai-wa-Mob-ni-Kibishii-Sekai-desu-Season-2.jpg", "https://samehadaku.li/otome-game-sekai-wa-mob-ni-kibishii-sekai-desu-2-episode-12-subtitle-indonesia/", "otome-game-sekai-wa-mob-ni-kibishii-sekai-desu-2", "Ep 12"),
        MediaItem("anime_2", "Solo Leveling Season 2 Ep 8", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/01/Solo-Leveling.jpg", "https://samehadaku.li/solo-leveling-season-2-episode-8/", "solo-leveling-season-2", "Ep 8"),
        MediaItem("anime_3", "Bleach: Thousand-Year Blood War Part 3", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/10/Bleach-Thousand-Year-Blood-War-Part-3-The-Conflict.jpg", "https://samehadaku.li/bleach-part-3-episode-11/", "bleach-part-3", "Ep 11"),
        MediaItem("anime_4", "One Piece Episode 1125", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2020/05/One-Piece.jpg", "https://samehadaku.li/one-piece-episode-1125/", "one-piece", "Ep 1125")
    )

    private fun getFallbackDrama(): List<MediaItem> = listOf(
        MediaItem("drama_1", "A Parasite's Heart (2026)", CategoryType.DRAMA, "https://convert.d-cdn.me/convert/aHR0cHM6Ly9hc3NldHMuZC1jZG4ubWUvaW1nLzIwMjYvMDkvODQ5LXlhbG9hazRmLmpwZw--/180x200/1.jpg", "https://drakorid.co/nonton/a-parasites-heart-2026/", "a-parasites-heart-2026", "Eps 8"),
        MediaItem("drama_2", "Queen of Tears (2024)", CategoryType.DRAMA, "https://convert.d-cdn.me/convert/aHR0cHM6Ly9hc3NldHMuZC1jZG4ubWUvaW1nLzIwMjQvMDMvNzgyLXF1ZWVub2Z0ZWFycy5qcGc-/180x200/1.jpg", "https://drakorid.co/nonton/queen-of-tears/", "queen-of-tears", "Eps 16"),
        MediaItem("drama_3", "Lovely Runner (2024)", CategoryType.DRAMA, "https://convert.d-cdn.me/convert/aHR0cHM6Ly9hc3NldHMuZC1jZG4ubWUvaW1nLzIwMjQvMDQvNzg1LWxvdmVseXJ1bm5lci5qcGc-/180x200/1.jpg", "https://drakorid.co/nonton/lovely-runner/", "lovely-runner", "Eps 16"),
        MediaItem("drama_4", "The Scandal (2026)", CategoryType.DRAMA, "https://convert.d-cdn.me/convert/aHR0cHM6Ly9hc3NldHMuZC1jZG4ubWUvaW1nLzIwMjYvMDgvODQxLXNjYW5kYWwuanBn/180x200/1.jpg", "https://drakorid.co/nonton/the-scandal-2026/", "the-scandal-2026", "Eps 12")
    )

    private fun getFallbackDonghua(): List<MediaItem> = listOf(
        MediaItem("donghua_1", "Lord of the Ancient God Grave", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/10/Lord-of-the-Ancient-God-Grave.jpg", "https://anichin.ro/lord-of-the-ancient-god-grave-episode-485-subtitle-indonesia/", "lord-of-the-ancient-god-grave", "Ep 485"),
        MediaItem("donghua_2", "Renegade Immortal (Xian Ni)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/09/Renegade-Immortal.jpg", "https://anichin.ro/renegade-immortal-episode-158-subtitle-indonesia/", "renegade-immortal", "Ep 158"),
        MediaItem("donghua_3", "Perfect World (Wanmei Shijie)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2021/04/Perfect-World.jpg", "https://anichin.ro/perfect-world-episode-180-subtitle-indonesia/", "perfect-world", "Ep 180"),
        MediaItem("donghua_4", "Battle Through the Heavens Season 5", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2022/07/Battle-Through-the-Heavens-Season-5.jpg", "https://anichin.ro/battle-through-the-heavens-season-5-episode-112-subtitle-indonesia/", "battle-through-the-heavens-season-5", "Ep 112")
    )

    private fun getFallbackManga(): List<MediaItem> = listOf(
        MediaItem("1", "Kijima-san & Yamada-san", CategoryType.MANGA, "https://global-api.manga-up.com/asset/h1F/en/manga_main/1029.webp", slug = "1", badge = "Ch 66"),
        MediaItem("2", "The Apothecary Diaries", CategoryType.MANGA, "https://global-api.manga-up.com/asset/h1F/en/manga_main/1030.webp", slug = "2", badge = "Ch 75"),
        MediaItem("3", "Toilet-Bound Hanako-kun", CategoryType.MANGA, "https://global-api.manga-up.com/asset/h1F/en/manga_main/1031.webp", slug = "3", badge = "Ch 112"),
        MediaItem("4", "Ragna Crimson", CategoryType.MANGA, "https://global-api.manga-up.com/asset/h1F/en/manga_main/1032.webp", slug = "4", badge = "Ch 80")
    )

    private fun getFallbackLiveTv(): List<LiveTvChannelItem> = listOf(
        LiveTvChannelItem("210", "Trans TV", 800, "TV Nasional", "https://servicebuss.transvision.co.id/uploads/channel/logo/210.png", slug = "210-trans-tv"),
        LiveTvChannelItem("211", "Trans7", 801, "TV Nasional", "https://servicebuss.transvision.co.id/uploads/channel/logo/211.png", slug = "211-trans7"),
        LiveTvChannelItem("214", "CNN Indonesia", 804, "Berita", "https://servicebuss.transvision.co.id/uploads/channel/logo/214.png", slug = "214-cnn-indonesia"),
        LiveTvChannelItem("215", "CNBC Indonesia", 805, "Berita", "https://servicebuss.transvision.co.id/uploads/channel/logo/215.png", slug = "215-cnbc-indonesia"),
        LiveTvChannelItem("101", "SCTV", 802, "TV Nasional", "https://servicebuss.transvision.co.id/uploads/channel/logo/101.png", slug = "101-sctv"),
        LiveTvChannelItem("102", "Indosiar", 803, "TV Nasional", "https://servicebuss.transvision.co.id/uploads/channel/logo/102.png", slug = "102-indosiar"),
        LiveTvChannelItem("301", "tvN Movies", 501, "Film & Bioskop", "https://servicebuss.transvision.co.id/uploads/channel/logo/301.png", slug = "301-tvn-movies")
    )

    private fun getFallbackVod(): List<MediaItem> = listOf(
        MediaItem("742", "Tonbo!", CategoryType.VOD, "https://cdnjktbpid22.transvision.co.id/ha-tx26/uploads/posters/94552c57eba8455a866ebffa6e604a9c.jpg", slug = "742-tonbo", badge = "26 Eps"),
        MediaItem("1091", "The Transmart", CategoryType.VOD, "https://cdnjktbpid22.transvision.co.id/ha-tx26/uploads/posters/d1151f38d9784248af771a8a2a8ef509.jpg", slug = "1091-the-transmart", badge = "Series")
    )

    private fun getFallbackDramaDetail(slug: String): MediaDetail {
        val eps = (1..16).map { ep ->
            EpisodeItem(
                id = ep.toString(),
                episodeNumber = ep.toString(),
                title = "Episode $ep",
                url = slug
            )
        }
        return MediaDetail(
            id = slug,
            title = slug.replace("-", " ").capitalizeWords(),
            category = CategoryType.DRAMA,
            thumbnail = "https://convert.d-cdn.me/convert/aHR0cHM6Ly9hc3NldHMuZC1jZG4ubWUvaW1nLzIwMjYvMDkvODQ5LXlhbG9hazRmLmpwZw--/180x200/1.jpg",
            synopsis = "Sinopsis lengkap drama korea terbaru dengan alur cerita menarik, penuh intrik emosional dan konflik tak terduga.",
            genres = listOf("Drama", "Romance", "Mystery"),
            status = "Ongoing",
            rating = "9.0",
            totalEpisodes = "${eps.size} Episode",
            episodes = eps
        )
    }

    private fun getFallbackAnimeDetail(url: String): MediaDetail {
        val eps = (1..12).map { ep ->
            EpisodeItem(
                id = ep.toString(),
                episodeNumber = ep.toString(),
                title = "Episode $ep Subtitle Indonesia",
                url = url
            )
        }
        return MediaDetail(
            id = url,
            title = "Anime Series Detail",
            category = CategoryType.ANIME,
            thumbnail = "https://samehadaku.li/wp-content/uploads/2026/01/Otome-Game-Sekai-wa-Mob-ni-Kibishii-Sekai-desu-Season-2.jpg",
            synopsis = "Serial anime aksi petualangan seru dengan grafis memukau dan pertarungan epik.",
            genres = listOf("Action", "Adventure", "Fantasy"),
            status = "Tamat",
            rating = "8.6",
            totalEpisodes = "${eps.size} Episode",
            episodes = eps
        )
    }

    private fun getFallbackDonghuaDetail(url: String): MediaDetail {
        val eps = (1..20).map { ep ->
            EpisodeItem(
                id = ep.toString(),
                episodeNumber = ep.toString(),
                title = "Episode $ep Sub Indo",
                url = url
            )
        }
        return MediaDetail(
            id = url,
            title = "Donghua Cultivation Series",
            category = CategoryType.DONGHUA,
            thumbnail = "https://anichin.ro/wp-content/uploads/2023/10/Lord-of-the-Ancient-God-Grave.jpg",
            synopsis = "Animasi 3D Tiongkok dengan tema kultivasi tanpa batas dan petualangan dewa kuno.",
            genres = listOf("Cultivation", "Action", "Fantasy"),
            totalEpisodes = "${eps.size} Episode",
            episodes = eps
        )
    }

    private fun getFallbackMangaDetail(id: String): MediaDetail {
        val chaps = (1..60).reversed().map { ch ->
            MangaChapterItem(
                id = "1295$ch",
                title = "Chapter $ch",
                subtitle = "Update terbaru"
            )
        }
        return MediaDetail(
            id = id,
            title = "Kijima-san & Yamada-san",
            category = CategoryType.MANGA,
            thumbnail = "https://global-api.manga-up.com/asset/h1F/en/manga_main/1029.webp",
            synopsis = "Kisah komedi romantis kantor yang hangat antara rekan kerja.",
            genres = listOf("Comedy", "Romance", "Manga"),
            totalEpisodes = "${chaps.size} Chapter",
            chapters = chaps
        )
    }

    private fun getFallbackVodDetail(slug: String): MediaDetail {
        val eps = (1..10).map { ep ->
            EpisodeItem(
                id = ep.toString(),
                episodeNumber = ep.toString(),
                title = "Episode $ep",
                url = slug
            )
        }
        return MediaDetail(
            id = slug,
            title = slug.replace("-", " ").capitalizeWords(),
            category = CategoryType.VOD,
            thumbnail = "https://cdnjktbpid22.transvision.co.id/ha-tx26/uploads/posters/94552c57eba8455a866ebffa6e604a9c.jpg",
            synopsis = "Serial VOD eksklusif kualitas Full HD dengan kisah inspiratif.",
            genres = listOf("Drama", "Family"),
            totalEpisodes = "${eps.size} Episode",
            episodes = eps
        )
    }

    private fun getFallbackMangaPages(chapterId: String): List<MangaPageItem> {
        return (1..10).map { page ->
            MangaPageItem(
                page = page,
                url = "https://global-api.manga-up.com/asset/k8w/en/page/$chapterId/$page.webp.enc",
                key = "5062ba2ab837db9a71febd8a7f0f0b4db5c5d922f0532d6c37fb1a4d1e9b7d6c",
                iv = "b5ba2426b14a5519283194c66efe7165"
            )
        }
    }

    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
