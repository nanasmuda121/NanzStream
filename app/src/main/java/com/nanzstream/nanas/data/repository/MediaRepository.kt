package com.nanzstream.nanas.data.repository

import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.scraper.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class MediaRepository {

    suspend fun getHomeSpotlight(): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val drakor = DrakorScraper.getLatest(1)
            val anime = AnimeScraper.getLatest(1)
            val donghua = DonghuaScraper.getLatest(1)
            val manga = MangaScraper.getHome()

            if (anime.isNotEmpty()) list.add(anime[0])
            if (drakor.isNotEmpty()) list.add(drakor[0])
            if (donghua.isNotEmpty()) list.add(donghua[0])
            if (manga.isNotEmpty()) list.add(manga[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isEmpty()) {
            list.addAll(getFallbackSpotlight())
        }
        list
    }

    suspend fun getAnimeLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = AnimeScraper.getLatest(page)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        getFallbackAnime()
    }

    suspend fun getDramaLatest(page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val res = DrakorScraper.getLatest(page)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        getFallbackDrama()
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
            val res = MangaScraper.getHome()
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        getFallbackManga()
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
                    CategoryType.DRAMA -> results.addAll(DrakorScraper.search(query, page))
                    CategoryType.ANIME -> results.addAll(AnimeScraper.search(query, page))
                    CategoryType.DONGHUA -> results.addAll(DonghuaScraper.search(query, page))
                    CategoryType.MANGA -> results.addAll(MangaScraper.search(query))
                    CategoryType.ALL -> {
                        val drakorAsync = async { DrakorScraper.search(query, 1) }
                        val animeAsync = async { AnimeScraper.search(query, 1) }
                        val donghuaAsync = async { DonghuaScraper.search(query, 1) }
                        val mangaAsync = async { MangaScraper.search(query) }

                        results.addAll(animeAsync.await())
                        results.addAll(drakorAsync.await())
                        results.addAll(donghuaAsync.await())
                        results.addAll(mangaAsync.await())
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            results
        }

    suspend fun getDetail(category: CategoryType, idOrSlug: String): MediaDetail? =
        withContext(Dispatchers.IO) {
            try {
                when (category) {
                    CategoryType.DRAMA -> DrakorScraper.getDetail(idOrSlug)
                    CategoryType.ANIME -> AnimeScraper.getDetail(idOrSlug)
                    CategoryType.DONGHUA -> DonghuaScraper.getDetail(idOrSlug)
                    CategoryType.MANGA -> MangaScraper.getDetail(idOrSlug)
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
                    CategoryType.DRAMA -> DrakorScraper.getStream(targetUrlOrSlug, episode)
                    CategoryType.ANIME -> AnimeScraper.getStream(targetUrlOrSlug)
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
            val res = MangaScraper.getPages(chapterId)
            if (res.isNotEmpty()) return@withContext res
        } catch (e: Exception) {
            e.printStackTrace()
        }
        emptyList()
    }

    // Fallbacks
    private fun getFallbackSpotlight(): List<MediaItem> = listOf(
        MediaItem(
            id = "spotlight_1",
            title = "A Parasite's Heart (2026)",
            category = CategoryType.DRAMA,
            thumbnail = "https://convert.d-cdn.me/convert/aHR0cHM6Ly9hc3NldHMuZC1jZG4ubWUvaW1nLzIwMjYvMDkvODQ5LXlhbG9hazRmLmpwZw--/180x200/1.jpg",
            slug = "a-parasites-heart-2026",
            badge = "Episode 8 • Ongoing",
            synopsis = "Drama misteri thriller romantis tentang intrik gelap di balik keluarga konglomerat Korea.",
            rating = "9.1",
            year = "2026",
            genres = listOf("Thriller", "Mystery", "Romance")
        ),
        MediaItem(
            id = "spotlight_2",
            title = "Otome Game Sekai wa Mob ni Kibishii 2",
            category = CategoryType.ANIME,
            thumbnail = "https://samehadaku.li/wp-content/uploads/2026/01/Otome-Game-Sekai-wa-Mob-ni-Kibishii-Sekai-desu-Season-2.jpg",
            slug = "otome-game-sekai-wa-mob-ni-kibishii-sekai-desu-2",
            badge = "Episode 12",
            synopsis = "Leon bereinkarnasi ke dalam dunia video game otome yang brutal.",
            rating = "8.4",
            year = "2026",
            genres = listOf("Action", "Fantasy", "Isekai")
        )
    )

    private fun getFallbackAnime(): List<MediaItem> = listOf(
        MediaItem("anime_1", "Otome Game Sekai wa Mob ni Kibishii 2 Ep 12", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2026/01/Otome-Game-Sekai-wa-Mob-ni-Kibishii-Sekai-desu-Season-2.jpg", "https://samehadaku.li/otome-game-sekai-wa-mob-ni-kibishii-sekai-desu-2-episode-12-subtitle-indonesia/", "otome-game-sekai-wa-mob-ni-kibishii-sekai-desu-2", "Ep 12"),
        MediaItem("anime_2", "Solo Leveling Season 2 Ep 8", CategoryType.ANIME, "https://samehadaku.li/wp-content/uploads/2024/01/Solo-Leveling.jpg", "https://samehadaku.li/solo-leveling-season-2-episode-8/", "solo-leveling-season-2", "Ep 8")
    )

    private fun getFallbackDrama(): List<MediaItem> = listOf(
        MediaItem("drama_1", "A Parasite's Heart (2026)", CategoryType.DRAMA, "https://convert.d-cdn.me/convert/aHR0cHM6Ly9hc3NldHMuZC1jZG4ubWUvaW1nLzIwMjYvMDkvODQ5LXlhbG9hazRmLmpwZw--/180x200/1.jpg", "https://drakorid.co/nonton/a-parasites-heart-2026/", "a-parasites-heart-2026", "Eps 8")
    )

    private fun getFallbackDonghua(): List<MediaItem> = listOf(
        MediaItem("donghua_1", "Lord of the Ancient God Grave", CategoryType.DONGHUA, "https://anichin.ro/wp-content/uploads/2023/10/Lord-of-the-Ancient-God-Grave.jpg", "https://anichin.ro/lord-of-the-ancient-god-grave-episode-485-subtitle-indonesia/", "lord-of-the-ancient-god-grave", "Ep 485")
    )

    private fun getFallbackManga(): List<MediaItem> = listOf(
        MediaItem("1", "Kijima-san & Yamada-san", CategoryType.MANGA, "https://global-api.manga-up.com/asset/h1F/en/manga_main/1029.webp", slug = "1", badge = "Ch 66")
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
