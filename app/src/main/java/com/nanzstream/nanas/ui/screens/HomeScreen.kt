package com.nanzstream.nanas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanzstream.nanas.NanzStreamApp
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.ContinueWatchingItem
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.components.GlassPill
import com.nanzstream.nanas.ui.components.HeroBanner
import com.nanzstream.nanas.ui.components.MediaItemCard
import com.nanzstream.nanas.ui.theme.*

@Composable
fun HomeScreen(
    repository: MediaRepository,
    onMediaClick: (MediaItem) -> Unit,
    onCategoryViewAll: (CategoryType) -> Unit,
    onLiveTvClick: () -> Unit,
    onContinueClick: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(CategoryType.ALL) }
    var spotlightItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var animeList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var dramaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var donghuaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var mangaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var vodList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var continueList by remember { mutableStateOf<List<ContinueWatchingItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        continueList = NanzStreamApp.storage.getContinueWatching()
        try {
            spotlightItems = repository.getHomeSpotlight()
            animeList = repository.getAnimeLatest(1)
            dramaList = repository.getDramaLatest(1)
            donghuaList = repository.getDonghuaLatest(1)
            mangaList = repository.getMangaHome()
            vodList = repository.getVodList(1)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // 1. Hero Spotlight Carousel
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                if (spotlightItems.isNotEmpty()) {
                    HeroBanner(
                        items = spotlightItems,
                        onItemClick = onMediaClick
                    )
                }
            }
        }

        // 2. Category Selector Pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.width(16.dp))
                CategoryType.entries.forEach { cat ->
                    GlassPill(
                        title = cat.displayName,
                        icon = cat.icon,
                        isSelected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = cat
                            if (cat != CategoryType.ALL) {
                                if (cat == CategoryType.LIVETV) {
                                    onLiveTvClick()
                                } else {
                                    onCategoryViewAll(cat)
                                }
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
        }

        // 3. Continue Watching & Reading Bar (if any)
        if (continueList.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                    SectionHeader(
                        title = "Lanjutkan Nonton & Baca",
                        subtitle = "Aktivitas terakhir Anda",
                        onViewAllClick = null
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(continueList) { item ->
                            ContinueItemCard(item = item, onClick = { onContinueClick(item) })
                        }
                    }
                }
            }
        }

        // 4. Loading indicator or Rows
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                }
            }
        } else {
            // Row: Anime Terbaru
            item {
                MediaSectionRow(
                    title = "⚡ Anime Terbaru",
                    subtitle = "Rilis Samehadaku Sub Indo",
                    items = animeList,
                    onMediaClick = onMediaClick,
                    onViewAllClick = { onCategoryViewAll(CategoryType.ANIME) }
                )
            }

            // Row: Drama Korea & Asia
            item {
                MediaSectionRow(
                    title = "🎭 Drama Korea & Asia",
                    subtitle = "Episode update Drakor.id",
                    items = dramaList,
                    onMediaClick = onMediaClick,
                    onViewAllClick = { onCategoryViewAll(CategoryType.DRAMA) }
                )
            }

            // Row: Donghua (Animasi 3D China)
            item {
                MediaSectionRow(
                    title = "🐉 Donghua Terbaru",
                    subtitle = "Kultivasi & Petualangan Anichin",
                    items = donghuaList,
                    onMediaClick = onMediaClick,
                    onViewAllClick = { onCategoryViewAll(CategoryType.DONGHUA) }
                )
            }

            // Row: Komik & Manga Populer
            item {
                MediaSectionRow(
                    title = "📖 Komik & Manga Pilihan",
                    subtitle = "Manga UP Reader resmi",
                    items = mangaList,
                    onMediaClick = onMediaClick,
                    onViewAllClick = { onCategoryViewAll(CategoryType.MANGA) }
                )
            }

            // Row: Live TV Quick Banner
            item {
                LiveTvHighlightBanner(onLiveTvClick = onLiveTvClick)
            }

            // Row: VOD Movies & Series
            item {
                MediaSectionRow(
                    title = "🎬 Serial & Film VOD",
                    subtitle = "Katalog tayangan CubMu",
                    items = vodList,
                    onMediaClick = onMediaClick,
                    onViewAllClick = { onCategoryViewAll(CategoryType.VOD) }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onViewAllClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (onViewAllClick != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onViewAllClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lihat Semua",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Lihat Semua",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun MediaSectionRow(
    title: String,
    subtitle: String,
    items: List<MediaItem>,
    onMediaClick: (MediaItem) -> Unit,
    onViewAllClick: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        SectionHeader(title = title, subtitle = subtitle, onViewAllClick = onViewAllClick)
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onMediaClick(item) },
                    modifier = Modifier.size(width = 135.dp, height = 195.dp)
                )
            }
        }
    }
}

@Composable
fun LiveTvHighlightBanner(
    onLiveTvClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .background(DarkCard)
            .clickable(onClick = onLiveTvClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SIARAN LANGSUNG",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "80+ Saluran Live TV",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Trans TV, Trans7, CNN, SCTV, Indosiar, tvN Movies",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Tonton TV",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ContinueItemCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x30FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Lanjut",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = item.lastItemTitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}
