package com.nanzstream.nanas.ui.screens.portal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.repository.MediaRepository
import kotlinx.coroutines.launch

// 100% Authentic YouTube Mobile Theme Colors
private val YtDark = Color(0xFF0F0F0F)
private val YtRed = Color(0xFFFF0000)
private val YtChipInactive = Color(0xFF272727)
private val YtTextPrimary = Color(0xFFFFFFFF)
private val YtTextSecondary = Color(0xFFAAAAAA)
private val YtBorder = Color(0xFF272727)

private enum class YouTubeTab(val title: String, val icon: ImageVector) {
    HOME("Beranda", Icons.Default.Home),
    SHORTS("Shorts", Icons.Default.PlayArrow),
    TRENDING("Trending", Icons.Default.Whatshot),
    SEARCH("Cari", Icons.Default.Search)
}

@Composable
fun YouTubePortalScreen(
    repository: MediaRepository,
    onBackToPortal: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(YouTubeTab.HOME) }
    var selectedCategoryChip by remember { mutableStateOf("Semua") }

    var homeVideos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var shortsList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var trendingList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val categoryChips = remember {
        listOf(
            "Semua",
            "Trending",
            "Musik",
            "Gaming",
            "Berita",
            "Podcast",
            "Viral",
            "Film & Animasi",
            "Teknologi",
            "Komedi"
        )
    }

    // Load initial feed data
    LaunchedEffect(currentTab) {
        when (currentTab) {
            YouTubeTab.HOME -> {
                if (homeVideos.isEmpty()) {
                    isLoading = true
                    try {
                        homeVideos = repository.getYouTubeLatest(1)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            YouTubeTab.SHORTS -> {
                if (shortsList.isEmpty()) {
                    isLoading = true
                    try {
                        shortsList = repository.getYouTubeShorts()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            YouTubeTab.TRENDING -> {
                if (trendingList.isEmpty()) {
                    isLoading = true
                    try {
                        trendingList = repository.search(CategoryType.YOUTUBE, "trending indonesia viral")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            YouTubeTab.SEARCH -> {}
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(YtDark)
            ) {
                // Official YouTube Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Back button + YouTube Logo Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onBackToPortal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke Launcher",
                                tint = YtTextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // YouTube Official Icon + Wordmark
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.clickable {
                                currentTab = YouTubeTab.HOME
                                selectedCategoryChip = "Semua"
                            }
                        ) {
                            // YouTube Red Play Icon Pill
                            Box(
                                modifier = Modifier
                                    .size(width = 28.dp, height = 20.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(YtRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "YouTube",
                                    color = YtTextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                )
                                Text(
                                    text = "ID",
                                    color = YtTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }
                    }

                    // Right: Actions (Cast, Notifications, Search, Portal)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { currentTab = YouTubeTab.SEARCH },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari di YouTube",
                                tint = YtTextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Return to Portal launcher button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(YtChipInactive)
                                .border(1.dp, YtBorder, RoundedCornerShape(16.dp))
                                .clickable(onClick = onBackToPortal)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Portal",
                                    tint = YtTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "PORTAL",
                                    color = YtTextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                // Category Chips Bar (Shown on Home and Trending tabs)
                if (currentTab == YouTubeTab.HOME || currentTab == YouTubeTab.TRENDING) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        categoryChips.forEach { chipName ->
                            val isSelected = selectedCategoryChip == chipName
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color.White else YtChipInactive)
                                    .clickable {
                                        selectedCategoryChip = chipName
                                        isLoading = true
                                        coroutineScope.launch {
                                            try {
                                                val q = if (chipName == "Semua") "trending indonesia" else "indonesia $chipName"
                                                val res = repository.search(CategoryType.YOUTUBE, q)
                                                if (currentTab == YouTubeTab.HOME) {
                                                    homeVideos = res
                                                } else {
                                                    trendingList = res
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chipName,
                                    color = if (isSelected) Color.Black else YtTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Divider(color = YtBorder, thickness = 0.8.dp)
            }
        },
        bottomBar = {
            // Authentic YouTube Mobile Bottom Navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(YtDark)
            ) {
                Divider(color = YtBorder, thickness = 0.8.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    YouTubeTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        val tint = if (isSelected) YtTextPrimary else YtTextSecondary

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { currentTab = tab }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (tab == YouTubeTab.SHORTS) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) YtRed else YtChipInactive),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = tab.title,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Exit to NanzStream Portal
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onBackToPortal)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Keluar",
                            tint = YtTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Portal",
                            color = YtTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        },
        containerColor = YtDark
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(YtDark)
        ) {
            when (currentTab) {
                YouTubeTab.HOME -> {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = YtRed, strokeWidth = 2.5.dp)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(homeVideos, key = { it.id }) { item ->
                                YouTubeVideoCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    onChannelClick = {
                                        val chItem = MediaItem(
                                            id = item.channelTitle ?: item.id,
                                            title = item.channelTitle ?: "YouTube Channel",
                                            category = CategoryType.YOUTUBE,
                                            thumbnail = item.channelAvatar ?: item.thumbnail,
                                            badge = "Channel",
                                            genres = listOf("YouTube", "Channel"),
                                            channelAvatar = item.channelAvatar,
                                            channelTitle = item.channelTitle
                                        )
                                        onItemClick(chItem)
                                    }
                                )
                            }
                        }
                    }
                }

                YouTubeTab.SHORTS -> {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = YtRed, strokeWidth = 2.5.dp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(shortsList, key = { it.id }) { item ->
                                YouTubeShortsCard(
                                    item = item,
                                    onClick = { onItemClick(item) }
                                )
                            }
                        }
                    }
                }

                YouTubeTab.TRENDING -> {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = YtRed, strokeWidth = 2.5.dp)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(trendingList, key = { it.id }) { item ->
                                YouTubeVideoCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    onChannelClick = {
                                        val chItem = MediaItem(
                                            id = item.channelTitle ?: item.id,
                                            title = item.channelTitle ?: "YouTube Channel",
                                            category = CategoryType.YOUTUBE,
                                            thumbnail = item.channelAvatar ?: item.thumbnail,
                                            badge = "Channel",
                                            genres = listOf("YouTube", "Channel"),
                                            channelAvatar = item.channelAvatar,
                                            channelTitle = item.channelTitle
                                        )
                                        onItemClick(chItem)
                                    }
                                )
                            }
                        }
                    }
                }

                YouTubeTab.SEARCH -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // YouTube Search Input Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        text = "Telusuri YouTube...",
                                        color = YtTextSecondary,
                                        fontSize = 14.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = YtTextSecondary
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = {
                                            searchQuery = ""
                                            searchResults = emptyList()
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Hapus",
                                                tint = YtTextSecondary
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    focusManager.clearFocus()
                                    if (searchQuery.isNotBlank()) {
                                        isLoading = true
                                        coroutineScope.launch {
                                            try {
                                                searchResults = repository.search(CategoryType.YOUTUBE, searchQuery)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    }
                                }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = YtChipInactive,
                                    unfocusedContainerColor = YtChipInactive,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = YtTextPrimary,
                                    unfocusedTextColor = YtTextPrimary
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Quick Search Suggestions
                        if (searchQuery.isBlank() && searchResults.isEmpty()) {
                            Text(
                                text = "PENCARIAN POPULER",
                                color = YtTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                            )
                            val suggestions = listOf("Trending Hari Ini", "Trailer Film 2026", "Musik Viral Indonesia", "Shorts Lucu", "Gaming Indonesia", "Podcast")
                            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                                suggestions.forEach { term ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchQuery = term
                                                isLoading = true
                                                coroutineScope.launch {
                                                    try {
                                                        searchResults = repository.search(CategoryType.YOUTUBE, term)
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    } finally {
                                                        isLoading = false
                                                    }
                                                }
                                            }
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = YtTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(text = term, color = YtTextPrimary, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = YtRed, strokeWidth = 2.5.dp)
                            }
                        } else if (searchResults.isNotEmpty()) {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 24.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(searchResults, key = { it.id }) { item ->
                                    YouTubeVideoCard(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                        onChannelClick = {
                                            val chItem = MediaItem(
                                                id = item.channelTitle ?: item.id,
                                                title = item.channelTitle ?: "YouTube Channel",
                                                category = CategoryType.YOUTUBE,
                                                thumbnail = item.channelAvatar ?: item.thumbnail,
                                                badge = "Channel",
                                                genres = listOf("YouTube", "Channel"),
                                                channelAvatar = item.channelAvatar,
                                                channelTitle = item.channelTitle
                                            )
                                            onItemClick(chItem)
                                        }
                                    )
                                }
                            }
                        } else if (searchQuery.isNotBlank()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Tidak ada hasil untuk \"$searchQuery\"",
                                    color = YtTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 100% Faithful YouTube Mobile 1-Column Video Card
 * - 16:9 full-width thumbnail
 * - Duration badge in bottom right corner
 * - Channel Avatar circle (with photo from extractor)
 * - Video title (2 lines max)
 * - Channel name • views • time
 * - 3-dots more menu
 */
@Composable
private fun YouTubeVideoCard(
    item: MediaItem,
    onClick: () -> Unit,
    onChannelClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val channelName = item.channelTitle
        ?: item.genres.firstOrNull { it != "YouTube" && it != "Video" }
        ?: "YouTube Channel"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 16.dp)
    ) {
        // 16:9 Thumbnail with Duration Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF1E1E1E))
        ) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration badge at bottom right
            val durationText = item.badge?.ifBlank { null } ?: "Video"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = durationText,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Details Row: Avatar + Title/Channel + More Options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel Avatar (clickable to navigate to Channel Detail)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(enabled = onChannelClick != null) { onChannelClick?.invoke() }
            ) {
                if (!item.channelAvatar.isNullOrBlank()) {
                    AsyncImage(
                        model = item.channelAvatar,
                        contentDescription = channelName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .border(0.5.dp, YtBorder, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(YtChipInactive),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channelName.firstOrNull()?.uppercase() ?: "Y",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title and Channel Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = YtTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                val viewsText = item.rating?.ifBlank { "YouTube" } ?: "YouTube"
                Text(
                    text = "$channelName • $viewsText",
                    color = YtTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Three dots icon
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opsi",
                    tint = YtTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 100% Faithful YouTube Shorts Card
 * - 9:16 vertical poster
 * - YouTube Shorts red badge
 * - Bottom gradient overlay with title and views
 */
@Composable
private fun YouTubeShortsCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x33000000),
                            Color(0xCC000000)
                        ),
                        startY = 200f
                    )
                )
        )

        // Shorts Logo Badge at Top-Left
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCCFF0000))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "Shorts",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Title & Views Overlay at Bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            val viewCount = item.rating?.ifBlank { "Shorts" } ?: "Shorts"
            Text(
                text = viewCount,
                color = Color(0xFFDDDDDD),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
