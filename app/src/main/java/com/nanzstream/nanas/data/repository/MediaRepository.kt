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
            val anime = OtakudesuScraper.getLatest(1)
            val donghua = DonghuaScraper.getLatest(1)
            val webtoon = WebtoonScraper.getHome()

            if (anime.isNotEmpty()) list.add(anime[0])
            if (donghua.isNotEmpty()) list.add(donghua[0])
            if (webtoon.isNotEmpty()) list.add(webtoon[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.size <= 1) {
            list.addAll(getFallbackSpotlight())
        }
        list
    }

    suspend fun getAnimeLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = OtakudesuScraper.getLatest(page)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        try {
            val res = WebtoonScraper.getHome()
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        getFallbackWebtoon()
    }

    suspend fun getWebtoonSchedule(daySlug: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = WebtoonScraper.getSchedule(daySlug)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        getMangaHome()
    }

    suspend fun getAnimeSchedule(dayIndex: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = OtakudesuScraper.getSchedule(dayIndex)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        OtakudesuScraper.getSchedule(dayIndex)
    }

    suspend fun getDonghuaSchedule(dayIndex: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = DonghuaScraper.getSchedule(dayIndex)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        DonghuaScraper.getSchedule(dayIndex)
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

    // Real search across categories
    suspend fun search(category: CategoryType, query: String, page: Int = 1): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<MediaItem>()
            try {
                when (category) {
                    CategoryType.ANIME -> results.addAll(OtakudesuScraper.search(query))
                    CategoryType.DONGHUA -> results.addAll(DonghuaScraper.search(query, page))
                    CategoryType.MANGA -> results.addAll(WebtoonScraper.search(query))
                    CategoryType.ALL -> {
                        val otakuAsync = async { OtakudesuScraper.search(query) }
                        val donghuaAsync = async { DonghuaScraper.search(query, 1) }
                        val webtoonAsync = async { WebtoonScraper.search(query) }
                        results.addAll(otakuAsync.await())
                        results.addAll(donghuaAsync.await())
                        results.addAll(webtoonAsync.await())
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
                        val otList = OtakudesuScraper.search(cleanQ).take(8).map { it.title }
                        otList.forEach { add(it) }
                    }
                    CategoryType.DONGHUA -> {
                        val dhList = DonghuaScraper.search(cleanQ, 1).take(6).map { it.title }
                        dhList.forEach { add(it) }
                    }
                    CategoryType.MANGA -> {
                        val mbList = WebtoonScraper.search(cleanQ).take(6).map { it.title }
                        mbList.forEach { add(it) }
                    }
                    else -> {
                        val anime = OtakudesuScraper.search(cleanQ).take(3).map { it.title }
                        val dh = DonghuaScraper.search(cleanQ, 1).take(3).map { it.title }
                        val wt = WebtoonScraper.search(cleanQ).take(3).map { it.title }
                        (anime + dh + wt).forEach { add(it) }
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
                    CategoryType.ANIME -> OtakudesuScraper.getDetail(idOrSlug)
                    CategoryType.DONGHUA -> DonghuaScraper.getDetail(idOrSlug)
                    CategoryType.MANGA -> WebtoonScraper.getDetail(idOrSlug)
                    CategoryType.VOD -> CubMuScraper.getVodDetail(idOrSlug)
                    else -> null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun getStream(category: CategoryType, targetUrlOrSlug: String, episode: Int = 1): StreamResult? =
        withContext(Dispatchers.IO) {
            try {
                when (category) {
                    CategoryType.ANIME -> OtakudesuScraper.getStream(targetUrlOrSlug)
                    CategoryType.DONGHUA -> DonghuaScraper.getStream(targetUrlOrSlug)
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
        try {
            val res = WebtoonScraper.getPages(chapterId)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }

    // Fallbacks
    private fun getFallbackSpotlight(): List<MediaItem> = listOf(
        MediaItem(
            id = "spotlight_anime",
            title = "World Is Dancing",
            category = CategoryType.ANIME,
            thumbnail = "https://otakudesu.blog/wp-content/uploads/2026/06/158709.jpg",
            slug = "https://otakudesu.blog/anime/world-is-dancing-sub-indo/",
            badge = "Episode 12",
            synopsis = "Perjalanan ritmik dan petualangan seni tari dalam dunia penuh pesona.",
            rating = "8.4",
            year = "2026",
            genres = listOf("Action", "Fantasy")
        )
    )

    private fun getFallbackAnime(): List<MediaItem> = listOf(
        MediaItem("anime_1", "World Is Dancing", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2026/06/158709.jpg", "https://otakudesu.blog/anime/world-is-dancing-sub-indo/", "https://otakudesu.blog/anime/world-is-dancing-sub-indo/", "Ep 12"),
        MediaItem("anime_2", "One Piece", CategoryType.ANIME, "https://otakudesu.blog/wp-content/uploads/2021/05/One-Piece-Sub-Indo.jpg", "https://otakudesu.blog/anime/1piece-sub-indo/", "https://otakudesu.blog/anime/1piece-sub-indo/", "Ep 1179")
    )

    private fun getFallbackDonghua(): List<MediaItem> = listOf(
        MediaItem("donghua_1", "Lord of the Ancient God Grave", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/Lord-of-the-Ancient-God-Grave-Subtitle-Indonesia.webp", "https://anichin.ro/lord-of-the-ancient-god-grave-episode-485-subtitle-indonesia/", "lord-of-the-ancient-god-grave", "Ep 485"),
        MediaItem("donghua_2", "Battle Through the Heavens Season 5", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2026/02/BTTH-Season-5-Subtitle-Indonesia.webp", "https://anichin.ro/battle-through-the-heavens-season-5-subtitle-indonesia/", "btth-season-5", "Ep 128")
    )

    private fun getFallbackWebtoon(): List<MediaItem> = listOf(
        MediaItem("4834", "The Greatest Estate Developer", CategoryType.MANGA, "https://webtoon-phinf.pstatic.net/20250205_17/1738719483097MIbul_JPEG/4834.jpg?type=q90", "https://www.webtoons.com/id/fantasy/the-greatest-estate-developer/list?title_no=4834", "4834", "Webtoon")
    )

    private fun getFallbackLiveTv(): List<LiveTvChannelItem> = listOf(
        LiveTvChannelItem("210", "Trans TV", 800, "TV Nasional", "https://servicebuss.transvision.co.id/uploads/channel/logo/210.png", slug = "210-trans-tv"),
        LiveTvChannelItem("211", "Trans7", 801, "TV Nasional", "https://servicebuss.transvision.co.id/uploads/channel/logo/211.png", slug = "211-trans7"),
        LiveTvChannelItem("214", "CNN Indonesia", 804, "Berita", "https://servicebuss.transvision.co.id/uploads/channel/logo/214.png", slug = "214-cnn-indonesia")
    )

    private fun getFallbackVod(): List<MediaItem> = listOf(
        MediaItem("742", "Tonbo!", CategoryType.VOD, "https://cdnjktbpid22.transvision.co.id/ha-tx26/uploads/posters/94552c57eba8455a866ebffa6e604a9c.jpg", slug = "742-tonbo", badge = "26 Eps")
    )
}
