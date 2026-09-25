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
            val anime = AnimeScraper.getLatest(1)
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
            val samehadakuDeferred = async { try { AnimeScraper.getLatest(page) } catch (e: Exception) { emptyList() } }
            val otakudesuDeferred = async { try { OtakudesuScraper.getLatest(page) } catch (e: Exception) { emptyList() } }

            val samehadaku = samehadakuDeferred.await()
            val otakudesu = otakudesuDeferred.await()

            val merged = mutableListOf<MediaItem>()
            val seen = mutableSetOf<String>()
            fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

            val maxLen = maxOf(samehadaku.size, otakudesu.size)
            for (i in 0 until maxLen) {
                if (i < samehadaku.size) {
                    val item = samehadaku[i]
                    if (seen.add(norm(item.title))) merged.add(item)
                }
                if (i < otakudesu.size) {
                    val item = otakudesu[i]
                    if (seen.add(norm(item.title))) merged.add(item)
                }
            }
            if (merged.isNotEmpty()) return@withContext merged
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
        getAnimeScheduleList(dayIndex)
    }

    suspend fun getDonghuaSchedule(dayIndex: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        getDonghuaScheduleList(dayIndex)
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
                    CategoryType.ANIME -> {
                        val samehadakuDeferred = async { try { AnimeScraper.search(query, page) } catch (e: Exception) { emptyList() } }
                        val otakudesuDeferred = async { try { OtakudesuScraper.search(query) } catch (e: Exception) { emptyList() } }

                        val samehadaku = samehadakuDeferred.await()
                        val otakudesu = otakudesuDeferred.await()

                        val seen = mutableSetOf<String>()
                        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

                        for (item in samehadaku) {
                            if (seen.add(norm(item.title))) results.add(item)
                        }
                        for (item in otakudesu) {
                            if (seen.add(norm(item.title))) results.add(item)
                        }
                    }
                    CategoryType.DONGHUA -> results.addAll(DonghuaScraper.search(query, page))
                    CategoryType.MANGA -> results.addAll(WebtoonScraper.search(query))
                    CategoryType.ALL -> {
                        val animeAsync = async { AnimeScraper.search(query, 1) }
                        val otakuAsync = async { OtakudesuScraper.search(query) }
                        val donghuaAsync = async { DonghuaScraper.search(query, 1) }
                        val webtoonAsync = async { WebtoonScraper.search(query) }

                        val seen = mutableSetOf<String>()
                        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

                        for (item in animeAsync.await()) {
                            if (seen.add(norm(item.title))) results.add(item)
                        }
                        for (item in otakuAsync.await()) {
                            if (seen.add(norm(item.title))) results.add(item)
                        }
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
                        val shList = AnimeScraper.search(cleanQ, 1).take(5).map { it.title }
                        val otList = OtakudesuScraper.search(cleanQ).take(5).map { it.title }
                        (shList + otList).forEach { add(it) }
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
                        val anime = AnimeScraper.search(cleanQ, 1).take(3).map { it.title }
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
                    CategoryType.ANIME -> {
                        if (idOrSlug.contains("otakudesu")) {
                            OtakudesuScraper.getDetail(idOrSlug)
                        } else {
                            AnimeScraper.getDetail(idOrSlug)
                        }
                    }
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
                    CategoryType.ANIME -> {
                        if (targetUrlOrSlug.contains("otakudesu")) {
                            OtakudesuScraper.getStream(targetUrlOrSlug)
                        } else {
                            AnimeScraper.getStream(targetUrlOrSlug)
                        }
                    }
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
            title = "Otome Game Sekai wa Mob ni Kibishii 2",
            category = CategoryType.ANIME,
            thumbnail = "https://i3.wp.com/samehadaku.li/wp-content/uploads/2026/07/1783509113-1566-158337.jpg",
            slug = "otome-game-sekai-wa-mob-ni-kibishii-sekai-desu-2",
            badge = "Episode 12",
            synopsis = "Leon bereinkarnasi ke dalam dunia video game otome yang brutal.",
            rating = "8.4",
            year = "2026",
            genres = listOf("Action", "Fantasy", "Isekai")
        )
    )

    private fun getFallbackAnime(): List<MediaItem> = listOf(
        MediaItem("anime_1", "Otome Game Sekai wa Mob ni Kibishii 2 Ep 12", CategoryType.ANIME, "https://i3.wp.com/samehadaku.li/wp-content/uploads/2026/07/1783509113-1566-158337.jpg", "https://samehadaku.li/otome-game-sekai-wa-mob-ni-kibishii-sekai-desu-2-episode-12-subtitle-indonesia/", "otome-game-sekai-wa-mob-ni-kibishii-sekai-desu-2", "Ep 12"),
        MediaItem("anime_2", "Solo Leveling Season 2 Ep 8", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/01/Solo-Leveling.jpg", "https://samehadaku.li/solo-leveling-season-2-episode-8/", "solo-leveling-season-2", "Ep 8")
    )

    private fun getFallbackDonghua(): List<MediaItem> = listOf(
        MediaItem("donghua_1", "Lord of the Ancient God Grave", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/10/Lord-of-the-Ancient-God-Grave.jpg", "https://anichin.ro/lord-of-the-ancient-god-grave-episode-485-subtitle-indonesia/", "lord-of-the-ancient-god-grave", "Ep 485")
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

    private fun getAnimeScheduleList(dayIndex: Int): List<MediaItem> {
        return when (dayIndex) {
            0 -> listOf( // Senin
                MediaItem("anime_sch_1", "Bleach: Thousand-Year Blood War", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/bleach-sennen-kessen-hen-kashin-tan.jpg", slug = "bleach-sennen-kessen-hen-kashin-tan", badge = "Senin • 23:00 WIB", genres = listOf("Action", "Supernatural")),
                MediaItem("anime_sch_2", "Tower of God Season 2", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/07/kami-no-tou-ouji-no-kikan.jpg", slug = "kami-no-tou-ouji-no-kikan", badge = "Senin • 21:00 WIB", genres = listOf("Action", "Fantasy")),
                MediaItem("anime_sch_3", "Tsukimichi: Moonlit Fantasy S2", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/01/tsuki-ga-michibiku-isekai-douchuu-2nd-season.jpg", slug = "tsuki-ga-michibiku-isekai-douchuu-2nd-season", badge = "Senin • 22:00 WIB", genres = listOf("Isekai", "Fantasy")),
                MediaItem("anime_sch_4", "Natsume Yuujinchou Shichi", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/natsume-yuujinchou-shichi.jpg", slug = "natsume-yuujinchou-shichi", badge = "Senin • 23:30 WIB", genres = listOf("Slice of Life", "Supernatural")),
                MediaItem("anime_sch_5", "Ron Kamonohashi Season 2", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/kamonohashi-ron-no-kindan-suiri-2nd-season.jpg", slug = "kamonohashi-ron-no-kindan-suiri-2nd-season", badge = "Senin • 21:30 WIB", genres = listOf("Mystery", "Comedy"))
            )
            1 -> listOf( // Selasa
                MediaItem("anime_sch_6", "Dandadan", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/dandadan.jpg", slug = "dandadan", badge = "Selasa • 23:00 WIB", genres = listOf("Action", "Supernatural")),
                MediaItem("anime_sch_7", "Seirei Gensouki Season 2", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/seirei-gensouki-2nd-season.jpg", slug = "seirei-gensouki-2nd-season", badge = "Selasa • 00:30 WIB", genres = listOf("Action", "Isekai")),
                MediaItem("anime_sch_8", "Tying the Knot with an Amagami Sister", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/amagami-san-chi-no-enmusubi.jpg", slug = "amagami-san-chi-no-enmusubi", badge = "Selasa • 22:00 WIB", genres = listOf("Comedy", "Romance")),
                MediaItem("anime_sch_9", "You Are Ms. Servant", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/kimi-wa-meido-sama.jpg", slug = "kimi-wa-meido-sama", badge = "Selasa • 01:00 WIB", genres = listOf("Romance", "Comedy")),
                MediaItem("anime_sch_10", "Yakuza Fiancé: Raise wa Tanin ga Ii", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/raise-wa-tanin-ga-ii.jpg", slug = "raise-wa-tanin-ga-ii", badge = "Selasa • 21:00 WIB", genres = listOf("Drama", "Romance"))
            )
            2 -> listOf( // Rabu
                MediaItem("anime_sch_11", "Re:Zero Season 3", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/rezero-kara-hajimeru-isekai-seikatsu-3rd-season.jpg", slug = "rezero-kara-hajimeru-isekai-seikatsu-3rd-season", badge = "Rabu • 21:30 WIB", genres = listOf("Drama", "Fantasy")),
                MediaItem("anime_sch_12", "Acro Trip", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/acro-trip.jpg", slug = "acro-trip", badge = "Rabu • 20:00 WIB", genres = listOf("Comedy", "Magic")),
                MediaItem("anime_sch_13", "The Prince of Tennis II Semifinal", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/shin-tennis-no-ouji-sama-u-17-world-cup-semifinal.jpg", slug = "shin-tennis-no-ouji-sama-u-17-world-cup-semifinal", badge = "Rabu • 22:00 WIB", genres = listOf("Sports")),
                MediaItem("anime_sch_14", "Sengoku Youko", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/07/sengoku-youko-senma-konton-hen.jpg", slug = "sengoku-youko-senma-konton-hen", badge = "Rabu • 23:00 WIB", genres = listOf("Action", "Fantasy"))
            )
            3 -> listOf( // Kamis
                MediaItem("anime_sch_15", "Dr. STONE Science Future", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/dr-stone-science-future.jpg", slug = "dr-stone-science-future", badge = "Kamis • 21:30 WIB", genres = listOf("Adventure", "Sci-Fi")),
                MediaItem("anime_sch_16", "Rurouni Kenshin Season 2", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/rurouni-kenshin-meiji-kenkaku-romantan-kyoto-douran.jpg", slug = "rurouni-kenshin-meiji-kenkaku-romantan-kyoto-douran", badge = "Kamis • 23:55 WIB", genres = listOf("Action", "Samurai")),
                MediaItem("anime_sch_17", "Trillion Game", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/trillion-game.jpg", slug = "trillion-game", badge = "Kamis • 22:30 WIB", genres = listOf("Drama", "Business")),
                MediaItem("anime_sch_18", "Arifureta Season 3", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/arifureta-shokugyou-de-sekai-saikyou-season-3.jpg", slug = "arifureta-shokugyou-de-sekai-saikyou-season-3", badge = "Kamis • 22:00 WIB", genres = listOf("Action", "Isekai"))
            )
            4 -> listOf( // Jumat
                MediaItem("anime_sch_19", "Blue Lock Season 2", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/blue-lock-vs-u-20-japan.jpg", slug = "blue-lock-vs-u-20-japan", badge = "Jumat • 22:00 WIB", genres = listOf("Sports", "Soccer")),
                MediaItem("anime_sch_20", "SAO Alternative: GGO II", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/sword-art-online-alternative-gun-gale-online-ii.jpg", slug = "sword-art-online-alternative-gun-gale-online-ii", badge = "Jumat • 22:30 WIB", genres = listOf("Action", "Sci-Fi")),
                MediaItem("anime_sch_21", "DanMachi Season 5", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/dungeon-ni-deai-wo-motomeru-no-wa-machigatteiru-darou-ka-v-houjou-no-megami-hen.jpg", slug = "dungeon-ni-deai-wo-motomeru-no-wa-machigatteiru-darou-ka-v", badge = "Jumat • 21:30 WIB", genres = listOf("Action", "Fantasy")),
                MediaItem("anime_sch_22", "2.5 Dimensional Seduction", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/07/2-5-jigen-no-ririsa.jpg", slug = "2-5-jigen-no-ririsa", badge = "Jumat • 20:30 WIB", genres = listOf("Comedy", "Cosplay"))
            )
            5 -> listOf( // Sabtu
                MediaItem("anime_sch_23", "Dragon Ball Daima", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/dragon-ball-daima.jpg", slug = "dragon-ball-daima", badge = "Sabtu • 22:40 WIB", genres = listOf("Action", "Adventure")),
                MediaItem("anime_sch_24", "Blue Exorcist: Beyond the Snow", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/ao-no-exorcist-yuki-no-hate-hen.jpg", slug = "ao-no-exorcist-yuki-no-hate-hen", badge = "Sabtu • 23:30 WIB", genres = listOf("Action", "Supernatural")),
                MediaItem("anime_sch_25", "Ranma 1/2 (2024)", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/ranma-1-2-2024.jpg", slug = "ranma-1-2-2024", badge = "Sabtu • 23:55 WIB", genres = listOf("Action", "Martial Arts")),
                MediaItem("anime_sch_26", "Touhai: Ura Rate Mahjong", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/touhai-ura-rate-mahjong-touhai-roku.jpg", slug = "touhai-ura-rate-mahjong-touhai-roku", badge = "Sabtu • 21:00 WIB", genres = listOf("Game", "Suspense"))
            )
            else -> listOf( // Minggu
                MediaItem("anime_sch_27", "One Piece (Egghead Arc)", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2020/04/one-piece-2.jpg", slug = "one-piece", badge = "Minggu • 10:00 WIB", genres = listOf("Action", "Shounen")),
                MediaItem("anime_sch_28", "Shangri-La Frontier Season 2", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/shangri-la-frontier-kusoge-hunter-kamige-ni-idoman-to-su-2nd-season.jpg", slug = "shangri-la-frontier-kusoge-hunter-kamige-ni-idoman-to-su-2nd-season", badge = "Minggu • 16:00 WIB", genres = listOf("Action", "Gaming")),
                MediaItem("anime_sch_29", "MF Ghost Season 2", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/09/mf-ghost-2nd-season.jpg", slug = "mf-ghost-2nd-season", badge = "Minggu • 23:00 WIB", genres = listOf("Racing", "Sports")),
                MediaItem("anime_sch_30", "Fairy Tail: 100 Years Quest", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/07/fairy-tail-100-nen-quest.jpg", slug = "fairy-tail-100-nen-quest", badge = "Minggu • 16:30 WIB", genres = listOf("Action", "Magic"))
            )
        }
    }

    private fun getDonghuaScheduleList(dayIndex: Int): List<MediaItem> {
        return when (dayIndex) {
            0 -> listOf( // Senin
                MediaItem("dh_sch_1", "Renegade Immortal (Xian Ni)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/09/Renegade-Immortal.jpg", slug = "renegade-immortal", badge = "Senin • 10:00 WIB", genres = listOf("Action", "Cultivation")),
                MediaItem("dh_sch_2", "Peerless Battle Spirit", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/08/Peerless-Battle-Spirit.jpg", slug = "peerless-battle-spirit", badge = "Senin • 09:00 WIB", genres = listOf("Action", "Cultivation")),
                MediaItem("dh_sch_3", "The Peak of True Martial Arts", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2022/04/The-Peak-of-True-Martial-Arts.jpg", slug = "the-peak-of-true-martial-arts", badge = "Senin • 11:00 WIB", genres = listOf("Martial Arts")),
                MediaItem("dh_sch_4", "Martial Master (Wu Shen Zhu Zai)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2020/03/Wu-Shen-Zhu-Zai.jpg", slug = "wu-shen-zhu-zai", badge = "Senin • 09:30 WIB", genres = listOf("Action", "Fantasy"))
            )
            1 -> listOf( // Selasa
                MediaItem("dh_sch_5", "Wu Geng Ji Season 4", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2021/07/Wu-Geng-Ji-Season-4.jpg", slug = "wu-geng-ji-season-4", badge = "Selasa • 09:00 WIB", genres = listOf("Action", "Historical")),
                MediaItem("dh_sch_6", "Big Brother (Shi Xiong A Shi Xiong)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/01/My-Senior-Brother-is-Too-Steady.jpg", slug = "my-senior-brother-is-too-steady", badge = "Selasa • 10:00 WIB", genres = listOf("Comedy", "Cultivation")),
                MediaItem("dh_sch_7", "The Legend of Sword Domain", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2021/05/Jian-Yu-Feng-Yun.jpg", slug = "jian-yu-feng-yun", badge = "Selasa • 11:00 WIB", genres = listOf("Action", "Fantasy")),
                MediaItem("dh_sch_8", "Against the Sky Supreme", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2021/04/Ni-Tian-Zhi-Zun.jpg", slug = "ni-tian-zhi-zun", badge = "Selasa • 09:30 WIB", genres = listOf("Action", "Cultivation"))
            )
            2 -> listOf( // Rabu
                MediaItem("dh_sch_9", "Shrouding the Heavens (Zhe Tian)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/05/Zhe-Tian.jpg", slug = "zhe-tian", badge = "Rabu • 10:00 WIB", genres = listOf("Action", "Sci-Fi", "Cultivation")),
                MediaItem("dh_sch_10", "Snow Eagle Lord Season 3", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2021/12/Snow-Eagle-Lord-Season-3.jpg", slug = "snow-eagle-lord-season-3", badge = "Rabu • 09:00 WIB", genres = listOf("Action", "Fantasy")),
                MediaItem("dh_sch_11", "100,000 Years of Qi Refining", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/02/Lian-Qi-Shi-Wan-Nian.jpg", slug = "lian-qi-shi-wan-nian", badge = "Rabu • 10:30 WIB", genres = listOf("Action", "Cultivation")),
                MediaItem("dh_sch_12", "Ten Thousand Worlds", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2020/11/Wan-Jie-Fa-Shen.jpg", slug = "wan-jie-fa-shen", badge = "Rabu • 11:00 WIB", genres = listOf("Action", "Magic"))
            )
            3 -> listOf( // Kamis
                MediaItem("dh_sch_13", "The Demon Hunter (Cang Yuan Tu)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/06/Cang-Yuan-Tu.jpg", slug = "cang-yuan-tu", badge = "Kamis • 10:00 WIB", genres = listOf("Action", "Demons")),
                MediaItem("dh_sch_14", "Against the Gods", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/12/Ni-Tian-Xie-Shen.jpg", slug = "ni-tian-xie-shen", badge = "Kamis • 09:30 WIB", genres = listOf("Action", "Fantasy")),
                MediaItem("dh_sch_15", "Tomb of Fallen Gods (Shen Mu)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2022/07/Shen-Mu.jpg", slug = "shen-mu", badge = "Kamis • 10:30 WIB", genres = listOf("Action", "Cultivation"))
            )
            4 -> listOf( // Jumat
                MediaItem("dh_sch_16", "Perfect World (Wanmei Shijie)", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2021/04/Wanmei-Shijie.jpg", slug = "wanmei-shijie", badge = "Jumat • 10:00 WIB", genres = listOf("Action", "Cultivation")),
                MediaItem("dh_sch_17", "A Will Eternal Season 3", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2024/07/A-Will-Eternal-Season-3.jpg", slug = "a-will-eternal-season-3", badge = "Jumat • 09:30 WIB", genres = listOf("Comedy", "Cultivation")),
                MediaItem("dh_sch_18", "Peerless Martial Spirit", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2021/01/Jue-Shi-Wu-Hun.jpg", slug = "jue-shi-wu-hun", badge = "Jumat • 10:30 WIB", genres = listOf("Action", "Martial Arts"))
            )
            5 -> listOf( // Sabtu
                MediaItem("dh_sch_19", "Soul Land 2: The Peerless Tang Clan", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/06/Soul-Land-2.jpg", slug = "soul-land-2", badge = "Sabtu • 10:00 WIB", genres = listOf("Action", "Fantasy")),
                MediaItem("dh_sch_20", "A Record of a Mortal's Journey", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2020/07/A-Record-of-a-Mortals-Journey-to-Immortality.jpg", slug = "a-record-of-a-mortals-journey-to-immortality", badge = "Sabtu • 11:00 WIB", genres = listOf("Action", "Cultivation")),
                MediaItem("dh_sch_21", "Stellar Transformation Season 5", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2022/12/Xingchen-Bian-Season-5.jpg", slug = "xingchen-bian-season-5", badge = "Sabtu • 09:30 WIB", genres = listOf("Action", "Sci-Fi"))
            )
            else -> listOf( // Minggu
                MediaItem("dh_sch_22", "Battle Through the Heavens Season 5", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2022/07/BTTH-Season-5.jpg", slug = "btth-season-5", badge = "Minggu • 10:00 WIB", genres = listOf("Action", "Cultivation")),
                MediaItem("dh_sch_23", "Swallowed Star Season 4", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2020/11/Swallowed-Star.jpg", slug = "swallowed-star", badge = "Minggu • 10:00 WIB", genres = listOf("Action", "Sci-Fi")),
                MediaItem("dh_sch_24", "Martial Universe Season 4", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/11/Martial-Universe-Season-4.jpg", slug = "martial-universe-season-4", badge = "Minggu • 09:30 WIB", genres = listOf("Action", "Fantasy")),
                MediaItem("dh_sch_25", "The Great Ruler", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/07/The-Great-Ruler.jpg", slug = "the-great-ruler", badge = "Minggu • 10:30 WIB", genres = listOf("Action", "Fantasy"))
            )
        }
    }
}
