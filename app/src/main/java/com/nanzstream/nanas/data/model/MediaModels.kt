package com.nanzstream.nanas.data.model

import com.google.gson.annotations.SerializedName

enum class CategoryType(val id: String, val displayName: String, val icon: String) {
    ALL("all", "Semua", "⚡"),
    ANIME("anime", "Anime", "⚔️"),
    DONGHUA("donghua", "Donghua", "🐉"),
    MANGA("manga", "Webtoon", "📖"),
    LIVETV("livetv", "Live TV", "📺"),
    VOD("vod", "VOD Series", "🎬");

    companion object {
        fun fromId(id: String?): CategoryType =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: ALL
    }
}

data class MediaItem(
    val id: String,
    val title: String,
    val category: CategoryType,
    val thumbnail: String,
    val url: String? = null,
    val slug: String? = null,
    val badge: String? = null,
    val synopsis: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val genres: List<String> = emptyList()
)

data class MediaDetail(
    val id: String,
    val title: String,
    val category: CategoryType,
    val thumbnail: String,
    val backdrop: String? = null,
    val synopsis: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val rating: String? = null,
    val releaseDate: String? = null,
    val totalEpisodes: String? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val chapters: List<MangaChapterItem> = emptyList()
)

data class EpisodeItem(
    val id: String,
    val episodeNumber: String,
    val title: String,
    val url: String,
    val date: String? = null
)

data class MangaChapterItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val date: String? = null
)

data class MangaPageItem(
    val page: Int,
    val url: String,
    val key: String? = null,
    val iv: String? = null
)

data class StreamServerItem(
    val name: String,
    val url: String,
    val isDirectHls: Boolean = false
)

data class DownloadItem(
    val name: String,
    val url: String,
    val quality: String? = null
)

data class StreamResult(
    val title: String,
    val directHlsUrl: String? = null,
    val iframePlayerUrl: String? = null,
    val servers: List<StreamServerItem> = emptyList(),
    val downloads: List<DownloadItem> = emptyList()
)

data class LiveTvChannelItem(
    val id: String,
    val name: String,
    val number: Int,
    val genre: String,
    val logoUrl: String,
    val streamUrl: String? = null,
    val slug: String? = null
)

data class ContinueWatchingItem(
    val mediaId: String,
    val title: String,
    val thumbnail: String,
    val category: CategoryType,
    val lastItemTitle: String,
    val lastTargetUrl: String,
    val progressPercent: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class WatchlistItem(
    val mediaId: String,
    val title: String,
    val thumbnail: String,
    val category: CategoryType,
    val slugOrUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)
