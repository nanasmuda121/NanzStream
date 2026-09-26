package com.nanzstream.nanas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nanzstream.nanas.NanzStreamApp
import com.nanzstream.nanas.data.model.*
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.components.GlassCard
import com.nanzstream.nanas.ui.theme.*

@Composable
fun DetailScreen(
    category: CategoryType,
    idOrSlug: String,
    repository: MediaRepository,
    onBackClick: () -> Unit,
    onPlayEpisode: (MediaDetail, EpisodeItem) -> Unit,
    onReadChapter: (MediaDetail, MangaChapterItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var detail by remember { mutableStateOf<MediaDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isBookmarked by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(idOrSlug, retryTrigger) {
        isLoading = true
        isBookmarked = NanzStreamApp.storage.isBookmarked(idOrSlug)
        try {
            detail = repository.getDetail(category, idOrSlug)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
        return
    }

    val currentDetail = detail
    if (currentDetail == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBg)
                .statusBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, GlassBorder, CircleShape)
                        .background(SurfaceElevated)
                        .clickable(onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gagal Memuat Detail Konten",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Konten tidak dapat dimuat atau koneksi terputus. Silakan coba kembali.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable { retryTrigger++ }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Coba Lagi",
                        color = CanvasBlack,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        // 1. Hero Backdrop Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                // Backdrop Image
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentDetail.backdrop ?: currentDetail.thumbnail)
                        .crossfade(true)
                        .build(),
                    contentDescription = currentDetail.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark Scrim Gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x60000000),
                                    Color(0xA0060608),
                                    DarkBg
                                )
                            )
                        )
                )

                // Back Button & Bookmark Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .background(Color(0x80000000))
                            .clickable(onClick = onBackClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .background(Color(0x80000000))
                            .clickable {
                                val item = WatchlistItem(
                                    mediaId = currentDetail.id,
                                    title = currentDetail.title,
                                    thumbnail = currentDetail.thumbnail,
                                    category = currentDetail.category,
                                    slugOrUrl = currentDetail.id
                                )
                                isBookmarked = NanzStreamApp.storage.toggleBookmark(item)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Simpan",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Poster & Metadata Info floating at bottom
                val isYouTubeChannel = currentDetail.category == CategoryType.YOUTUBE &&
                        (currentDetail.genres.contains("Channel") || currentDetail.id.startsWith("UC"))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    if (isYouTubeChannel) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, GlassBorder, CircleShape)
                                .background(DarkCard)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentDetail.channelAvatar ?: currentDetail.thumbnail)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = currentDetail.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 95.dp, height = 135.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                .background(DarkCard)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentDetail.thumbnail)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = currentDetail.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentDetail.title,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = currentDetail.category.displayName,
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (!currentDetail.rating.isNullOrBlank()) {
                                Text(
                                    text = "★ ${currentDetail.rating}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (!currentDetail.totalEpisodes.isNullOrBlank()) {
                                Text(
                                    text = "• ${currentDetail.totalEpisodes}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Genres Tags
        if (currentDetail.genres.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currentDetail.genres.take(4).forEach { g ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, GlassBorderSubtle, RoundedCornerShape(6.dp))
                                .background(GlassBackground)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = g,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Synopsis
        if (!currentDetail.synopsis.isNullOrBlank()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Sinopsis",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentDetail.synopsis ?: "",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 4. Content List Header (Episodes or Chapters)
        item {
            val isSingleMovie = (currentDetail.category == CategoryType.MOVIES && currentDetail.episodes.size <= 1) ||
                    currentDetail.totalEpisodes.equals("Full Movie", ignoreCase = true) ||
                    (currentDetail.episodes.size <= 1 && currentDetail.title.contains("Movie", ignoreCase = true))
            val isYouTube = currentDetail.category == CategoryType.YOUTUBE
            val listTitle = when {
                currentDetail.category == CategoryType.MANGA -> "Daftar Chapter"
                isYouTube -> "Daftar Video"
                isSingleMovie -> "Film / Movie"
                else -> "Pilihan Episode"
            }
            val countText = when {
                currentDetail.category == CategoryType.MANGA -> "${currentDetail.chapters.size} Chapter"
                isYouTube -> "${currentDetail.episodes.size} Video"
                isSingleMovie -> "Full Movie"
                else -> "${currentDetail.episodes.size} Episode"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = listTitle,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = countText,
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        // 5. Episode List (for Video)
        if (currentDetail.category != CategoryType.MANGA) {
            val isSingleMovie = (currentDetail.category == CategoryType.MOVIES && currentDetail.episodes.size <= 1) ||
                    currentDetail.totalEpisodes.equals("Full Movie", ignoreCase = true) ||
                    (currentDetail.episodes.size <= 1 && currentDetail.title.contains("Movie", ignoreCase = true))
            val isYouTube = currentDetail.category == CategoryType.YOUTUBE
            items(currentDetail.episodes) { ep ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .clickable { onPlayEpisode(currentDetail, ep) }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Putar",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                val epItemTitle = when {
                                    isYouTube -> ep.title
                                    isSingleMovie -> if (ep.title.isNotBlank() && !ep.title.equals("Episode 1", ignoreCase = true)) ep.title else "Full Movie"
                                    ep.title.equals("Episode 0", ignoreCase = true) || ep.episodeNumber == "0" -> "Episode 1"
                                    ep.title.isNotBlank() -> ep.title
                                    else -> "Episode ${ep.episodeNumber}"
                                }
                                Text(
                                    text = epItemTitle,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!ep.date.isNullOrBlank()) {
                                    Text(
                                        text = ep.date,
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Chapter List (for Manga)
            items(currentDetail.chapters) { ch ->
                val isDownloaded = remember(ch.id) { NanzStreamApp.offlineManga.isChapterDownloaded(ch.id) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, if (isDownloaded) Color(0x6610B981) else GlassBorder, RoundedCornerShape(12.dp))
                        .background(if (isDownloaded) Color(0x1410B981) else DarkCard)
                        .clickable { onReadChapter(currentDetail, ch) }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = ch.title,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (isDownloaded) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "✓ Offline",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (!ch.subtitle.isNullOrBlank()) {
                                Text(
                                    text = ch.subtitle,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDownloaded) Color(0xFF10B981) else Color.White)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isDownloaded) "Baca Offline" else "Baca",
                                color = if (isDownloaded) Color.White else Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
