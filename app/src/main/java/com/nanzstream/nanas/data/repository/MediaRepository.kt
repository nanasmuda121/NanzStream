package com.nanzstream.nanas.data.repository

import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.scraper.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class MediaRepository {

    private val landingImageUrl =
        "https://lh3.googleusercontent.com/aida/AEtjO1XHJnWN9lRW-OQXDo6t_COw9w9vro0ZCbWwm220Z0onJDIPXpwObbXl4N2P1UIBLf2PNXgBF9Y0Ll1okKQcpJ3YpoETIxvNMHB0_45Zm-vBI1El7oPxJRTySdg_ffkKBg4tSwY27sRJZl0pIrDQutO9Ab6IzfvoGG7GZry1z729ZDHqE-HBvk7X0UYUnaxXbm92rOmiKT0g9O1js7PFy7WUleE0Pl7FxYFCxnKxYgqaoZIH_mmi_Rma8i0=s884"

    suspend fun getHomeSpotlight(): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()

        // 1. Always feature user's requested landing page banner
        list.add(
            MediaItem(
                id = "spotlight_landing",
                title = "NanzStream • Premium Entertainment",
                category = CategoryType.ALL,
                thumbnail = landingImageUrl,
                badge = "All-in-One Hub",
                synopsis = "Streaming Anime Samehadaku, Animasi Donghua Anichin, Baca Komik Webtoon, dan Live TV 80+ Channel dalam desain 100% Monochrome Glassmorphism.",
                rating = "9.9",
                year = "2026",
                genres = listOf("Anime", "Donghua", "Webtoon", "Live TV")
            )
        )

        try {
            val anime = try {
                val s = AnimasuScraper.getLatest(1)
                if (s.isNotEmpty()) s else OtakudesuScraper.getLatest(1)
            } catch (e: Exception) {
                OtakudesuScraper.getLatest(1)
            }
            val donghua = DonghuaScraper.getLatest(1)
            val komik = try {
                val s = BacakomikScraper.getLatest(1)
                if (s.isNotEmpty()) s else WebtoonScraper.getHome()
            } catch (e: Exception) {
                WebtoonScraper.getHome()
            }

            if (anime.isNotEmpty()) list.add(anime[0])
            if (donghua.isNotEmpty()) list.add(donghua[0])
            if (komik.isNotEmpty()) list.add(komik[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.size <= 1) {
            list.addAll(getFallbackSpotlight())
        }
        list
    }

    suspend fun getAnimeLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val res = kotlinx.coroutines.withTimeoutOrNull(4500) {
            try {
                val s = AnimasuScraper.getLatest(page)
                if (s.isNotEmpty()) s else null
            } catch (e: Exception) {
                null
            }
        }
        if (!res.isNullOrEmpty()) return@withContext res

        val res2 = kotlinx.coroutines.withTimeoutOrNull(3000) {
            try {
                val s = OtakudesuScraper.getLatest(page)
                if (s.isNotEmpty()) s else null
            } catch (e: Exception) {
                null
            }
        }
        if (!res2.isNullOrEmpty()) return@withContext res2

        getFallbackAnime()
    }

    suspend fun getDonghuaLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = DonghuaScraper.getLatest(page)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        getFallbackDonghua()
    }

    suspend fun getMangaHome(): List<MediaItem> = withContext(Dispatchers.IO) {
        val res = kotlinx.coroutines.withTimeoutOrNull(4500) {
            try {
                val s = BacakomikScraper.getHome()
                if (s.isNotEmpty()) s else null
            } catch (e: Exception) {
                null
            }
        }
        if (!res.isNullOrEmpty()) return@withContext res

        val res2 = kotlinx.coroutines.withTimeoutOrNull(3000) {
            try {
                val s = WebtoonScraper.getHome()
                if (s.isNotEmpty()) s else null
            } catch (e: Exception) {
                null
            }
        }
        if (!res2.isNullOrEmpty()) return@withContext res2

        getFallbackWebtoon()
    }

    suspend fun getWebtoonSchedule(daySlug: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val res = kotlinx.coroutines.withTimeoutOrNull(4500) {
            try {
                val s = BacakomikScraper.getLatest(1)
                if (s.isNotEmpty()) s else null
            } catch (e: Exception) {
                null
            }
        }
        if (!res.isNullOrEmpty()) return@withContext res

        getFallbackWebtoonSchedule(daySlug)
    }

    suspend fun getAnimeSchedule(dayIndex: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val res = kotlinx.coroutines.withTimeoutOrNull(4500) {
            try {
                val s = AnimasuScraper.getSchedule(dayIndex)
                if (s.isNotEmpty()) s else null
            } catch (e: Exception) {
                null
            }
        }
        if (!res.isNullOrEmpty()) return@withContext res

        val res2 = kotlinx.coroutines.withTimeoutOrNull(3000) {
            try {
                val s = OtakudesuScraper.getSchedule(dayIndex)
                if (s.isNotEmpty()) s else null
            } catch (e: Exception) {
                null
            }
        }
        if (!res2.isNullOrEmpty()) return@withContext res2

        getFallbackAnimeSchedule(dayIndex)
    }

    suspend fun getDonghuaSchedule(dayIndex: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = DonghuaScraper.getSchedule(dayIndex)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        getFallbackDonghuaSchedule(dayIndex)
    }

    suspend fun getLiveTvChannels(): List<LiveTvChannelItem> = withContext(Dispatchers.IO) {
        try {
            val res = CubMuScraper.getChannels()
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        getFallbackLiveTv()
    }

    suspend fun getVodList(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = CubMuScraper.getVodList(page)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        getFallbackVod()
    }

    suspend fun getDrachinaLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = DracinemaScraper.getLatest(page)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }

    suspend fun getMoviesLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = MovieBoxScraper.getLatest(page)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }

    suspend fun getYouTubeLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = YouTubeScraper.getLatest(page)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }

    suspend fun getYouTubeShorts(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = YouTubeScraper.getShorts()
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }

    // Real search across categories
    suspend fun search(category: CategoryType, query: String, page: Int = 1): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<MediaItem>()
            try {
                when (category) {
                    CategoryType.ANIME -> {
                        val ani = AnimasuScraper.search(query, page)
                        if (ani.isNotEmpty()) results.addAll(ani) else results.addAll(OtakudesuScraper.search(query))
                    }
                    CategoryType.DONGHUA -> results.addAll(DonghuaScraper.search(query, page))
                    CategoryType.MANGA -> {
                        val kom = BacakomikScraper.search(query, page)
                        if (kom.isNotEmpty()) results.addAll(kom) else results.addAll(WebtoonScraper.search(query))
                    }
                    CategoryType.DRACHINA -> results.addAll(DracinemaScraper.search(query))
                    CategoryType.MOVIES -> results.addAll(MovieBoxScraper.search(query))
                    CategoryType.YOUTUBE -> results.addAll(YouTubeScraper.search(query))
                    CategoryType.ALL -> {
                        val animeAsync = async {
                            try {
                                val s = AnimasuScraper.search(query, 1)
                                if (s.isNotEmpty()) s else OtakudesuScraper.search(query)
                            } catch (e: Exception) {
                                OtakudesuScraper.search(query)
                            }
                        }
                        val donghuaAsync = async { DonghuaScraper.search(query, 1) }
                        val mangaAsync = async {
                            try {
                                val s = BacakomikScraper.search(query, 1)
                                if (s.isNotEmpty()) s else WebtoonScraper.search(query)
                            } catch (e: Exception) {
                                WebtoonScraper.search(query)
                            }
                        }
                        val drachinAsync = async { DracinemaScraper.search(query) }
                        val movieAsync = async { MovieBoxScraper.search(query) }
                        val ytAsync = async { YouTubeScraper.search(query) }
                        results.addAll(animeAsync.await())
                        results.addAll(donghuaAsync.await())
                        results.addAll(mangaAsync.await())
                        results.addAll(drachinAsync.await())
                        results.addAll(movieAsync.await())
                        results.addAll(ytAsync.await())
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            results
        }

    suspend fun getSearchSuggestions(category: CategoryType, query: String): List<String> =
        withContext(Dispatchers.IO) {
            val cleanQ = query.trim()
            if (cleanQ.length < 2) return@withContext emptyList()
            val list = mutableListOf<String>()
            val seen = mutableSetOf<String>()

            fun add(title: String) {
                val clean = title
                    .replace(Regex("""Subtitle Indonesia|Sub Indo|Season \d+|Part \d+""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                if (clean.length > 2 && seen.add(clean.lowercase())) {
                    list.add(clean)
                }
            }

            try {
                when (category) {
                    CategoryType.ANIME -> {
                        val aniList = AnimasuScraper.search(cleanQ).take(8).map { it.title }
                        if (aniList.isNotEmpty()) {
                            aniList.forEach { add(it) }
                        } else {
                            val otList = OtakudesuScraper.search(cleanQ).take(8).map { it.title }
                            otList.forEach { add(it) }
                        }
                    }
                    CategoryType.DONGHUA -> {
                        val dhList = DonghuaScraper.search(cleanQ, 1).take(6).map { it.title }
                        dhList.forEach { add(it) }
                    }
                    CategoryType.MANGA -> {
                        val bkList = BacakomikScraper.search(cleanQ).take(6).map { it.title }
                        if (bkList.isNotEmpty()) {
                            bkList.forEach { add(it) }
                        } else {
                            val mbList = WebtoonScraper.search(cleanQ).take(6).map { it.title }
                            mbList.forEach { add(it) }
                        }
                    }
                    CategoryType.DRACHINA -> {
                        val dcList = DracinemaScraper.search(cleanQ).take(6).map { it.title }
                        dcList.forEach { add(it) }
                    }
                    CategoryType.MOVIES -> {
                        val mvList = MovieBoxScraper.search(cleanQ).take(6).map { it.title }
                        mvList.forEach { add(it) }
                    }
                    CategoryType.YOUTUBE -> {
                        val ytList = YouTubeScraper.search(cleanQ).take(6).map { it.title }
                        ytList.forEach { add(it) }
                    }
                    else -> {
                        val anime = try {
                            val s = AnimasuScraper.search(cleanQ, 1).take(2).map { it.title }
                            if (s.isNotEmpty()) s else OtakudesuScraper.search(cleanQ).take(2).map { it.title }
                        } catch (e: Exception) {
                            OtakudesuScraper.search(cleanQ).take(2).map { it.title }
                        }
                        val dh = DonghuaScraper.search(cleanQ, 1).take(2).map { it.title }
                        val wt = BacakomikScraper.search(cleanQ, 1).take(2).map { it.title }
                        val dc = DracinemaScraper.search(cleanQ).take(2).map { it.title }
                        val yt = YouTubeScraper.search(cleanQ).take(2).map { it.title }
                        (anime + dh + wt + dc + yt).forEach { add(it) }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            list.take(8)
        }

    suspend fun getDetail(category: CategoryType, idOrSlug: String): MediaDetail? =
        withContext(Dispatchers.IO) {
            try {
                when (category) {
                    CategoryType.ANIME -> {
                        // 1. Primary: Animasu
                        val aniDetail = kotlinx.coroutines.withTimeoutOrNull(4500) {
                            try {
                                AnimasuScraper.getDetail(idOrSlug)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (aniDetail != null && aniDetail.episodes.isNotEmpty()) return@withContext aniDetail

                        // 2. Secondary: Otakudesu
                        val otDetail = kotlinx.coroutines.withTimeoutOrNull(3000) {
                            try {
                                OtakudesuScraper.getDetail(idOrSlug)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (otDetail != null && otDetail.episodes.isNotEmpty()) return@withContext otDetail

                        // 3. Fallback: Samehadaku
                        val samDetail = kotlinx.coroutines.withTimeoutOrNull(2500) {
                            try {
                                AnimeScraper.getDetail(idOrSlug)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (samDetail != null && samDetail.episodes.isNotEmpty()) return@withContext samDetail

                        getFallbackAnimeDetail(idOrSlug)
                    }
                    CategoryType.DONGHUA -> DonghuaScraper.getDetail(idOrSlug)
                    CategoryType.MANGA -> {
                        // 1. Primary: Bacakomik
                        val bkDetail = kotlinx.coroutines.withTimeoutOrNull(4500) {
                            try {
                                BacakomikScraper.getDetail(idOrSlug)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (bkDetail != null && bkDetail.chapters.isNotEmpty()) return@withContext bkDetail

                        // 2. Secondary: Webtoon
                        val wtDetail = kotlinx.coroutines.withTimeoutOrNull(3000) {
                            try {
                                WebtoonScraper.getDetail(idOrSlug)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (wtDetail != null && wtDetail.chapters.isNotEmpty()) return@withContext wtDetail

                        getFallbackWebtoonDetail(idOrSlug)
                    }
                    CategoryType.DRACHINA -> DracinemaScraper.getDetail(idOrSlug)
                    CategoryType.MOVIES -> MovieBoxScraper.getDetail(idOrSlug)
                    CategoryType.YOUTUBE -> YouTubeScraper.getDetail(idOrSlug)
                    CategoryType.VOD -> CubMuScraper.getVodDetail(idOrSlug)
                    else -> null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (category == CategoryType.ANIME) getFallbackAnimeDetail(idOrSlug)
                else if (category == CategoryType.MANGA) getFallbackWebtoonDetail(idOrSlug)
                else null
            }
        }

    suspend fun getStream(category: CategoryType, targetUrlOrSlug: String, episode: Int = 1): StreamResult? =
        withContext(Dispatchers.IO) {
            try {
                when (category) {
                    CategoryType.ANIME -> {
                        var epUrl = targetUrlOrSlug

                        // 1. Primary: Animasu (Direct Vidhide HLS .m3u8 unblocked on high-speed CDN)
                        val isAnimasu = epUrl.contains("animasu", ignoreCase = true) || (!epUrl.contains("otakudesu", ignoreCase = true) && !epUrl.contains("samehadaku", ignoreCase = true))

                        if (isAnimasu) {
                            if (epUrl.contains("/anime/", ignoreCase = true) || (!epUrl.contains("/nonton-", ignoreCase = true) && !epUrl.contains("episode-", ignoreCase = true))) {
                                val detail = AnimasuScraper.getDetail(epUrl)
                                val foundEp = detail?.episodes?.find { it.episodeNumber.toIntOrNull() == episode }
                                    ?: detail?.episodes?.getOrNull((episode - 1).coerceAtLeast(0))
                                    ?: detail?.episodes?.firstOrNull()
                                if (foundEp != null && foundEp.url.isNotBlank()) {
                                    epUrl = foundEp.url
                                }
                            } else if (epUrl.contains("-episode-", ignoreCase = true)) {
                                val urlEpNum = Regex("""-episode-(\d+)""", RegexOption.IGNORE_CASE).find(epUrl)?.groupValues?.get(1)?.toIntOrNull()
                                if (urlEpNum != null && urlEpNum != episode && episode > 0) {
                                    epUrl = epUrl.replace(Regex("""-episode-\d+""", RegexOption.IGNORE_CASE), "-episode-$episode")
                                }
                            }
                            val stream = kotlinx.coroutines.withTimeoutOrNull(5000) {
                                try {
                                    AnimasuScraper.getStream(epUrl)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            if (stream != null && (stream.directHlsUrl != null || stream.servers.isNotEmpty())) {
                                return@withContext stream
                            }
                        }

                        // 2. Secondary: Otakudesu (Direct HLS/MP4)
                        val otStream = kotlinx.coroutines.withTimeoutOrNull(4000) {
                            try {
                                OtakudesuScraper.getStream(targetUrlOrSlug)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (otStream != null && (otStream.directHlsUrl != null || otStream.servers.isNotEmpty())) {
                            return@withContext otStream
                        }

                        // 3. Tertiary: Samehadaku
                        var samUrl = targetUrlOrSlug
                        if (samUrl.contains("/anime/", ignoreCase = true) || (!samUrl.contains("episode-", ignoreCase = true) && !samUrl.contains("-episode-", ignoreCase = true))) {
                            val detail = AnimeScraper.getDetail(samUrl)
                            val foundEp = detail?.episodes?.find { it.episodeNumber.toIntOrNull() == episode }
                                ?: detail?.episodes?.getOrNull((episode - 1).coerceAtLeast(0))
                                ?: detail?.episodes?.firstOrNull()
                            if (foundEp != null && foundEp.url.isNotBlank()) {
                                samUrl = foundEp.url
                            }
                        }
                        val samStream = kotlinx.coroutines.withTimeoutOrNull(4000) {
                            try {
                                AnimeScraper.getStream(samUrl)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (samStream != null) return@withContext samStream

                        // 4. Fallback direct stream result
                        StreamResult(
                            title = "Anime Episode $episode",
                            directHlsUrl = "https://ESrZaEKj9iFIuE8.dramiyos-cdn.com/hls2/01/08629/xywx8efqa0dk_h/master.m3u8?t=3l85zg_kBA9dxRSbsWi3NmnBLBLTl1MEB7pL7MOmrBs&s=1790406233&e=129600&f=43147180&srv=ocmb8ssa8kyo&i=0.4&sp=500&p1=ocmb8ssa8kyo&p2=ocmb8ssa8kyo&asn=152084",
                            servers = listOf(
                                StreamServerItem(
                                    "Animasu Vidhide HLS (720p Direct)",
                                    "https://ESrZaEKj9iFIuE8.dramiyos-cdn.com/hls2/01/08629/xywx8efqa0dk_h/master.m3u8?t=3l85zg_kBA9dxRSbsWi3NmnBLBLTl1MEB7pL7MOmrBs&s=1790406233&e=129600&f=43147180&srv=ocmb8ssa8kyo&i=0.4&sp=500&p1=ocmb8ssa8kyo&p2=ocmb8ssa8kyo&asn=152084",
                                    isDirectHls = true
                                )
                            )
                        )
                    }
                    CategoryType.DONGHUA -> {
                        var epUrl = targetUrlOrSlug
                        // If given a series page (e.g. /anime/ or does not contain episode), resolve episode URL from detail
                        if (epUrl.contains("/anime/", ignoreCase = true) || (!epUrl.contains("episode", ignoreCase = true) && !epUrl.contains("movie", ignoreCase = true))) {
                            val detail = DonghuaScraper.getDetail(epUrl)
                            val foundEp = detail?.episodes?.find { it.episodeNumber.toIntOrNull() == episode }
                                ?: detail?.episodes?.getOrNull((episode - 1).coerceAtLeast(0))
                                ?: detail?.episodes?.firstOrNull()
                            if (foundEp != null && foundEp.url.isNotBlank()) {
                                epUrl = foundEp.url
                            }
                        } else if (epUrl.contains("episode-", ignoreCase = true)) {
                            // If user explicitly navigated to a different episode number (e.g. Next Ep / Prev Ep)
                            val urlEpNum = Regex("""episode-(\d+)""", RegexOption.IGNORE_CASE).find(epUrl)?.groupValues?.get(1)?.toIntOrNull()
                            if (urlEpNum != null && urlEpNum != episode && episode > 0) {
                                epUrl = epUrl.replace(Regex("""episode-\d+""", RegexOption.IGNORE_CASE), "episode-$episode")
                            }
                        }
                        DonghuaScraper.getStream(epUrl)
                    }
                    CategoryType.DRACHINA -> DracinemaScraper.getStream(targetUrlOrSlug, episode)
                    CategoryType.MOVIES -> MovieBoxScraper.getStream(targetUrlOrSlug, episode)
                    CategoryType.YOUTUBE -> YouTubeScraper.getStream(targetUrlOrSlug, episode)
                    CategoryType.LIVETV -> CubMuScraper.getLiveStream(targetUrlOrSlug)
                    CategoryType.VOD -> CubMuScraper.getVodStream(targetUrlOrSlug)
                    else -> null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun getMangaPages(chapterId: String): List<MangaPageItem> = withContext(Dispatchers.IO) {
        val cleanId = chapterId.trim()
        val targetUrl = when {
            cleanId.startsWith("http") -> cleanId
            cleanId.contains("chapter") -> "https://bacakomik.my/$cleanId"
            cleanId.contains("title_no") || cleanId.contains("episode_no") -> "https://www.webtoons.com/id/$cleanId"
            else -> "https://bacakomik.my/$cleanId"
        }

        // 1. Primary: Bacakomik
        val res = kotlinx.coroutines.withTimeoutOrNull(5000) {
            try {
                val list = BacakomikScraper.getPages(targetUrl)
                if (list.isNotEmpty()) list else null
            } catch (e: Exception) {
                null
            }
        }
        if (!res.isNullOrEmpty()) return@withContext res

        // 2. Secondary: Webtoon
        val res2 = kotlinx.coroutines.withTimeoutOrNull(4000) {
            try {
                val list = WebtoonScraper.getPages(targetUrl)
                if (list.isNotEmpty()) list else null
            } catch (e: Exception) {
                null
            }
        }
        if (!res2.isNullOrEmpty()) return@withContext res2

        getFallbackMangaPages()
    }

    fun getFallbackMangaPages(): List<MangaPageItem> = listOf(
        MangaPageItem(1, "https://webtoon-phinf.pstatic.net/20221212_148/1670822776204rlFgs_PNG/thumb_16708227084656063_Serena_00_01_04.png"),
        MangaPageItem(2, "https://webtoon-phinf.pstatic.net/20221212_294/1670829623744sWIuU_PNG/thumb_16708295446226361_Serena_01_0121.png"),
        MangaPageItem(3, "https://webtoon-phinf.pstatic.net/20221212_291/1670841989803NbD9p_PNG/thumb_16708410697456929_Serena_2_12_01.png"),
        MangaPageItem(4, "https://webtoon-phinf.pstatic.net/20221212_16/1670822830880h3W0p_PNG/thumb_16708227084656063_Serena_00_01_05.png"),
        MangaPageItem(5, "https://webtoon-phinf.pstatic.net/20221212_65/1670829678129QzP4E_PNG/thumb_16708295446226361_Serena_01_0122.png")
    )

    // Fallbacks
    fun getFallbackSpotlight(): List<MediaItem> = listOf(
        MediaItem(
            id = "spotlight_anime",
            title = "Hell Mode: Gamer wa Hai Settei 2nd Season",
            category = CategoryType.ANIME,
            thumbnail = "https://otakudesu.blog/wp-content/uploads/2026/07/156314.jpg",
            url = "https://otakudesu.blog/anime/hell-mode-s2-sub-indo/",
            slug = "https://otakudesu.blog/anime/hell-mode-s2-sub-indo/",
            badge = "Episode 13 (End)",
            synopsis = "Petualangan gamer mode neraka di dunia lain dengan kasta terendah.",
            rating = "8.6",
            year = "2026",
            genres = listOf("Action", "Fantasy", "Isekai")
        )
    )

    fun getFallbackAnime(): List<MediaItem> = listOf(
        MediaItem("anime_1", "Hell Mode: Gamer wa Hai Settei 2nd Season", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/156314.jpg", "https://otakudesu.blog/anime/hell-mode-s2-sub-indo/", "https://otakudesu.blog/anime/hell-mode-s2-sub-indo/", "Ep 13 (End)"),
        MediaItem("anime_2", "One Piece", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2021/05/One-Piece-Sub-Indo.jpg", "https://otakudesu.blog/anime/1piece-sub-indo/", "https://otakudesu.blog/anime/1piece-sub-indo/", "Ep 1179"),
        MediaItem("anime_3", "Tensei shitara Slime Datta Ken 4th Season", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/156329.jpg", "https://otakudesu.blog/anime/slime-s4-sub-indo/", "https://otakudesu.blog/anime/slime-s4-sub-indo/", "Ongoing"),
        MediaItem("anime_4", "Re:Zero Season 3", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/158475.jpg", "https://otakudesu.blog/anime/rezero-s3-sub-indo/", "https://otakudesu.blog/anime/rezero-s3-sub-indo/", "Ongoing")
    )

    fun getFallbackDonghua(): List<MediaItem> = listOf(
        MediaItem("donghua_1", "Lord of the Ancient God Grave", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/Lord-of-the-Ancient-God-Grave-Subtitle-Indonesia.webp", "https://anichin.ro/lord-of-the-ancient-god-grave-episode-485-subtitle-indonesia/", "lord-of-the-ancient-god-grave", "Ep 485"),
        MediaItem("donghua_2", "Battle Through the Heavens Season 5", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/BTTH-Season-5-Subtitle-Indonesia.webp", "https://anichin.ro/battle-through-the-heavens-season-5-subtitle-indonesia/", "btth-season-5", "Ep 128")
    )

    fun getFallbackWebtoon(): List<MediaItem> = listOf(
        MediaItem("5001", "Serena", CategoryType.MANGA, "https://webtoon-phinf.pstatic.net/20260804_80/1785821073225nPwjs_JPEG/4Thumb_Poster.jpg?type=q90", "https://www.webtoons.com/id/romantic-fantasy/serena/list?title_no=5001", "5001", "Webtoon"),
        MediaItem("4834", "The Greatest Estate Developer", CategoryType.MANGA, "https://webtoon-phinf.pstatic.net/20250205_17/1738719483097MIbul_JPEG/4834.jpg?type=q90", "https://www.webtoons.com/id/fantasy/the-greatest-estate-developer/list?title_no=4834", "4834", "Webtoon")
    )

    fun getFallbackAnimeSchedule(dayIndex: Int): List<MediaItem> {
        val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val d = days.getOrElse(dayIndex) { "Senin" }
        return listOf(
            MediaItem("fb_ani_1", "Hell Mode: Gamer wa Hai Settei", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/156314.jpg", "https://otakudesu.blog/anime/hell-mode-s2-sub-indo/", "https://otakudesu.blog/anime/hell-mode-s2-sub-indo/", "$d • Ep 13"),
            MediaItem("fb_ani_2", "Tensei shitara Slime Datta Ken 4th", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/156329.jpg", "https://otakudesu.blog/anime/slime-s4-sub-indo/", "https://otakudesu.blog/anime/slime-s4-sub-indo/", "$d • Ongoing"),
            MediaItem("fb_ani_3", "One Piece", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2021/05/One-Piece-Sub-Indo.jpg", "https://otakudesu.blog/anime/1piece-sub-indo/", "https://otakudesu.blog/anime/1piece-sub-indo/", "$d • Ep 1179"),
            MediaItem("fb_ani_4", "Re:Zero Season 3", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/07/158475.jpg", "https://otakudesu.blog/anime/rezero-s3-sub-indo/", "https://otakudesu.blog/anime/rezero-s3-sub-indo/", "$d • Ongoing")
        )
    }

    fun getFallbackDonghuaSchedule(dayIndex: Int): List<MediaItem> {
        val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val d = days.getOrElse(dayIndex) { "Senin" }
        return listOf(
            MediaItem("fb_dh_1", "Lord of the Ancient God Grave", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/Lord-of-the-Ancient-God-Grave-Subtitle-Indonesia.webp", "https://anichin.ro/lord-of-the-ancient-god-grave-episode-485-subtitle-indonesia/", "lord-of-the-ancient-god-grave", "$d • Ep 485"),
            MediaItem("fb_dh_2", "Battle Through the Heavens Season 5", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/BTTH-Season-5-Subtitle-Indonesia.webp", "https://anichin.ro/battle-through-the-heavens-season-5-subtitle-indonesia/", "btth-season-5", "$d • Ep 128"),
            MediaItem("fb_dh_3", "Renegade Immortal (Xian Ni)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/Renegade-Immortal-Subtitle-Indonesia-2.webp", "https://anichin.ro/renegade-immortal-subtitle-indonesia/", "renegade-immortal", "$d • Ep 76"),
            MediaItem("fb_dh_4", "The Great Ruler 3D", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/The-Great-Ruler-3D-Subtitle-Indonesia.webp", "https://anichin.ro/the-great-ruler-subtitle-indonesia/", "the-great-ruler", "$d • Ep 82")
        )
    }

    fun getFallbackWebtoonSchedule(daySlug: String): List<MediaItem> {
        return listOf(
            MediaItem("5001", "Serena", CategoryType.MANGA, "https://webtoon-phinf.pstatic.net/20260804_80/1785821073225nPwjs_JPEG/4Thumb_Poster.jpg?type=q90", "https://www.webtoons.com/id/romantic-fantasy/serena/list?title_no=5001", "5001", "Webtoon"),
            MediaItem("11152", "The Corrupt Tyrant’s Obsession", CategoryType.MANGA, "https://webtoon-phinf.pstatic.net/20260903_139/1788413418613Asfyr_JPEG/9Thumb_Poster.jpg?type=q90", "https://www.webtoons.com/id/romantic-fantasy/the-corrupt-tyrants-obession/list?title_no=11152", "11152", "Webtoon"),
            MediaItem("3085", "WEE!!!", CategoryType.MANGA, "https://webtoon-phinf.pstatic.net/20250828_293/1756373680374gUKf3_JPEG/Thumb_Poster.jpg?type=q90", "https://www.webtoons.com/id/slice-of-life/wee/list?title_no=3085", "3085", "Webtoon"),
            MediaItem("4834", "The Greatest Estate Developer", CategoryType.MANGA, "https://webtoon-phinf.pstatic.net/20250205_17/1738719483097MIbul_JPEG/4834.jpg?type=q90", "https://www.webtoons.com/id/fantasy/the-greatest-estate-developer/list?title_no=4834", "4834", "Webtoon")
        )
    }

    private fun getFallbackAnimeDetail(idOrSlug: String): MediaDetail {
        return MediaDetail(
            id = idOrSlug,
            title = "Hell Mode: Gamer wa Hai Settei 2nd Season",
            category = CategoryType.ANIME,
            thumbnail = "https://otakudesu.blog/wp-content/uploads/2026/07/156314.jpg",
            synopsis = "Petualangan gamer mode neraka di dunia lain dengan kasta terendah.",
            genres = listOf("Action", "Fantasy", "Isekai"),
            status = "Ongoing",
            totalEpisodes = "13 Episode",
            episodes = listOf(
                EpisodeItem("https://otakudesu.blog/episode/hmode-s2-episode-1-sub-indo/", "1", "Episode 1", "https://otakudesu.blog/episode/hmode-s2-episode-1-sub-indo/"),
                EpisodeItem("https://otakudesu.blog/episode/hmode-s2-episode-2-sub-indo/", "2", "Episode 2", "https://otakudesu.blog/episode/hmode-s2-episode-2-sub-indo/"),
                EpisodeItem("https://otakudesu.blog/episode/hmode-s2-episode-13-sub-indo/", "13", "Episode 13 (Terbaru)", "https://otakudesu.blog/episode/hmode-s2-episode-13-sub-indo/")
            )
        )
    }

    private fun getFallbackWebtoonDetail(idOrSlug: String): MediaDetail {
        return MediaDetail(
            id = idOrSlug,
            title = "Serena",
            category = CategoryType.MANGA,
            thumbnail = "https://webtoon-phinf.pstatic.net/20260804_80/1785821073225nPwjs_JPEG/4Thumb_Poster.jpg?type=q90",
            synopsis = "Serena, satu-satunya pewaris keluarga Serenity, terpaksa menikah demi menyelamatkan keluarganya.",
            genres = listOf("Kerajaan", "Romance", "Drama"),
            status = "Ongoing",
            totalEpisodes = "100+ Episode",
            chapters = listOf(
                MangaChapterItem("https://www.webtoons.com/id/romantic-fantasy/serena/ep-136-menarik-napas-damai/viewer?title_no=5001&episode_no=137", "Episode 136", "Ep 136", "Terbaru"),
                MangaChapterItem("https://www.webtoons.com/id/romantic-fantasy/serena/ep-135-melainkan-kau/viewer?title_no=5001&episode_no=136", "Episode 135", "Ep 135", "Kemarin"),
                MangaChapterItem("https://www.webtoons.com/id/romantic-fantasy/serena/ep-134-hal-yang-kucintai/viewer?title_no=5001&episode_no=135", "Episode 134", "Ep 134", "Minggu lalu")
            )
        )
    }

    private fun getFallbackLiveTv(): List<LiveTvChannelItem> = listOf(
        LiveTvChannelItem("210", "Trans TV", 800, "TV Nasional", "https://servicebuss.transvision.co.id/uploads/channel/logo/210.png", slug = "210-trans-tv"),
        LiveTvChannelItem("211", "Trans7", 801, "TV Nasional", "https://servicebuss.transvision.co.id/uploads/channel/logo/211.png", slug = "211-trans7"),
        LiveTvChannelItem("214", "CNN Indonesia", 804, "Berita", "https://servicebuss.transvision.co.id/uploads/channel/logo/214.png", slug = "214-cnn-indonesia")
    )

    private fun getFallbackVod(): List<MediaItem> = listOf(
        MediaItem("742", "Tonbo!", CategoryType.VOD, "https://cdnjktbpid22.transvision.co.id/ha-tx26/uploads/posters/94552c57eba8455a866ebffa6e604a9c.jpg", slug = "742-tonbo", badge = "26 Eps")
    )
}
