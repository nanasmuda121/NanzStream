package com.nanzstream.nanas.ui.screens.portal

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.MediaDetail
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.data.scraper.StreamResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 100% Authentic YouTube Mobile Theme Colors
private val YtDark = Color(0xFF0F0F0F)
private val YtRed = Color(0xFFFF0000)
private val YtChipInactive = Color(0xFF272727)
private val YtTextPrimary = Color(0xFFFFFFFF)
private val YtTextSecondary = Color(0xFFAAAAAA)
private val YtBorder = Color(0xFF272727)
private val YtMiniplayerBg = Color(0xFF212121)

private enum class YouTubeTab(val title: String, val icon: ImageVector) {
    HOME("Beranda", Icons.Default.Home),
    SHORTS("Shorts", Icons.Default.PlayArrow),
    TRENDING("Trending", Icons.Default.Whatshot),
    SEARCH("Cari", Icons.Default.Search)
}

@OptIn(UnstableApi::class)
@Composable
fun YouTubePortalScreen(
    repository: MediaRepository,
    onBackToPortal: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    // Active YouTube Video Player & Miniplayer State (Scoped ONLY to YouTube Portal)
    var playingVideo by remember { mutableStateOf<MediaItem?>(null) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isPlayerPlaying by remember { mutableStateOf(true) }
    var isPlayerBuffering by remember { mutableStateOf(false) }
    var playerProgress by remember { mutableFloatStateOf(0f) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var isPlayerLoading by remember { mutableStateOf(false) }

    // Channel Detail View State (Viewing a Channel inside YouTube portal)
    var selectedChannelItem by remember { mutableStateOf<MediaItem?>(null) }

    // Initialize ExoPlayer with fast startup & resilient load control
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; id_ID)")
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
            }
    }

    val playerViewInstance = remember {
        PlayerView(context).apply {
            player = exoPlayer
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    // Safely cleanup player when leaving YouTube portal
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isPlayerBuffering = true
                    Player.STATE_READY -> {
                        isPlayerBuffering = false
                        isPlayerPlaying = exoPlayer.isPlaying
                        playerError = null
                    }
                    Player.STATE_ENDED -> {
                        isPlayerBuffering = false
                        isPlayerPlaying = false
                    }
                    Player.STATE_IDLE -> isPlayerBuffering = false
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayerPlaying = isPlaying
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isPlayerBuffering = false
                isPlayerPlaying = false
                playerError = "Gagal memutar video. Silakan coba lagi."
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Pause when app goes to background (no background audio playback as requested)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                    isPlayerPlaying = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (playingVideo != null && exoPlayer.playbackState == Player.STATE_READY) {
                        exoPlayer.play()
                        isPlayerPlaying = true
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Playback progress ticker for Miniplayer progress bar
    LaunchedEffect(playingVideo, isPlayerPlaying) {
        while (playingVideo != null && isPlayerPlaying) {
            val dur = exoPlayer.duration
            val pos = exoPlayer.currentPosition
            if (dur > 0) {
                playerProgress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            }
            delay(500)
        }
    }

    // Stream resolver & media source loader on video change
    LaunchedEffect(playingVideo) {
        val current = playingVideo ?: return@LaunchedEffect
        isPlayerLoading = true
        playerError = null
        try {
            val targetUrl = current.url ?: "https://www.youtube.com/watch?v=${current.id}"
            val res = repository.getStream(CategoryType.YOUTUBE, targetUrl, 1)
            if (res != null) {
                val serverUrl = res.directHlsUrl ?: res.servers.firstOrNull()?.url ?: ""
                val audioUrl = res.audioUrl ?: res.servers.firstOrNull()?.audioUrl

                val defaultReferer = "https://www.youtube.com/"
                val playableUrl = StreamResolver.resolveToDirectStream(serverUrl, defaultReferer)

                val dsHeaders = mapOf(
                    "Referer" to defaultReferer,
                    "Origin" to "https://www.youtube.com"
                )

                val dsFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; id_ID)")
                    .setConnectTimeoutMs(20_000)
                    .setReadTimeoutMs(25_000)
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(dsHeaders)

                if (!audioUrl.isNullOrBlank() && !playableUrl.contains(".m3u8") && !playableUrl.contains("hls_variant")) {
                    val videoSource = ProgressiveMediaSource.Factory(dsFactory)
                        .createMediaSource(ExoMediaItem.fromUri(playableUrl))
                    val audioSource = ProgressiveMediaSource.Factory(dsFactory)
                        .createMediaSource(ExoMediaItem.fromUri(audioUrl))
                    val merged = MergingMediaSource(videoSource, audioSource)
                    exoPlayer.setMediaSource(merged)
                } else {
                    val isHls = playableUrl.contains(".m3u8", ignoreCase = true) ||
                            playableUrl.contains("hls_variant", ignoreCase = true) ||
                            playableUrl.contains("hls_playlist", ignoreCase = true)
                    if (isHls) {
                        val hlsItem = ExoMediaItem.Builder()
                            .setUri(playableUrl)
                            .setMimeType(MimeTypes.APPLICATION_M3U8)
                            .build()
                        val hlsSource = HlsMediaSource.Factory(dsFactory).createMediaSource(hlsItem)
                        exoPlayer.setMediaSource(hlsSource)
                    } else {
                        val progSource = ProgressiveMediaSource.Factory(dsFactory)
                            .createMediaSource(ExoMediaItem.fromUri(playableUrl))
                        exoPlayer.setMediaSource(progSource)
                    }
                }
                exoPlayer.prepare()
                exoPlayer.play()
                isPlayerPlaying = true
            } else {
                playerError = "Gagal mendapatkan tautan video YouTube."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            playerError = "Terjadi kesalahan memuat video."
        } finally {
            isPlayerLoading = false
        }
    }

    // Back handling: minimize full player if open; or exit channel view if open
    BackHandler(enabled = isPlayerExpanded) {
        isPlayerExpanded = false
    }

    BackHandler(enabled = selectedChannelItem != null && !isPlayerExpanded) {
        selectedChannelItem = null
    }

    // Function to safely exit portal and wipe player/miniplayer completely
    val handleExitPortal = {
        exoPlayer.stop()
        exoPlayer.release()
        playingVideo = null
        isPlayerExpanded = false
        onBackToPortal()
    }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YtDark)
    ) {
        Scaffold(
            topBar = {
                if (selectedChannelItem == null) {
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
                                        .clickable(onClick = handleExitPortal),
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

                            // Right: Search & Portal launcher button
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

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(YtChipInactive)
                                        .border(1.dp, YtBorder, RoundedCornerShape(16.dp))
                                        .clickable(onClick = handleExitPortal)
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

                        HorizontalDivider(color = YtBorder, thickness = 0.8.dp)
                    }
                }
            },
            bottomBar = {
                // Docked Miniplayer + Bottom Navigation Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(YtDark)
                ) {
                    // Floating YouTube Miniplayer (Docked directly above the navigation bar)
                    if (playingVideo != null && !isPlayerExpanded) {
                        YouTubeMiniplayerBar(
                            item = playingVideo!!,
                            isPlaying = isPlayerPlaying,
                            isBuffering = isPlayerBuffering,
                            progress = playerProgress,
                            onExpand = { isPlayerExpanded = true },
                            onTogglePlay = {
                                if (isPlayerPlaying) {
                                    exoPlayer.pause()
                                    isPlayerPlaying = false
                                } else {
                                    exoPlayer.play()
                                    isPlayerPlaying = true
                                }
                            },
                            onClose = {
                                exoPlayer.stop()
                                playingVideo = null
                                isPlayerExpanded = false
                            }
                        )
                    }

                    HorizontalDivider(color = YtBorder, thickness = 0.8.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        YouTubeTab.entries.forEach { tab ->
                            val isSelected = currentTab == tab && selectedChannelItem == null
                            val tint = if (isSelected) YtTextPrimary else YtTextSecondary

                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedChannelItem = null
                                        currentTab = tab
                                    }
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
                                .clickable(onClick = handleExitPortal)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(YtDark)
            ) {
                // If viewing a channel inside the YouTube Portal
                if (selectedChannelItem != null) {
                    YouTubeChannelDetailView(
                        channelItem = selectedChannelItem!!,
                        repository = repository,
                        onBackClick = { selectedChannelItem = null },
                        onVideoClick = { vidItem ->
                            playingVideo = vidItem
                            isPlayerExpanded = true
                        }
                    )
                } else {
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
                                            onClick = {
                                                playingVideo = item
                                                isPlayerExpanded = true
                                            },
                                            onChannelClick = {
                                                selectedChannelItem = MediaItem(
                                                    id = item.channelId ?: item.channelTitle ?: item.id,
                                                    title = item.channelTitle ?: item.title,
                                                    category = CategoryType.YOUTUBE,
                                                    thumbnail = item.channelAvatar ?: item.thumbnail,
                                                    badge = "Channel",
                                                    genres = listOf("YouTube", "Channel"),
                                                    channelAvatar = item.channelAvatar,
                                                    channelTitle = item.channelTitle,
                                                    channelId = item.channelId
                                                )
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
                                            onClick = {
                                                if (isPlayerPlaying) {
                                                    exoPlayer.pause()
                                                    isPlayerPlaying = false
                                                }
                                                onItemClick(item)
                                            }
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
                                            onClick = {
                                                playingVideo = item
                                                isPlayerExpanded = true
                                            },
                                            onChannelClick = {
                                                selectedChannelItem = MediaItem(
                                                    id = item.channelId ?: item.channelTitle ?: item.id,
                                                    title = item.channelTitle ?: item.title,
                                                    category = CategoryType.YOUTUBE,
                                                    thumbnail = item.channelAvatar ?: item.thumbnail,
                                                    badge = "Channel",
                                                    genres = listOf("YouTube", "Channel"),
                                                    channelAvatar = item.channelAvatar,
                                                    channelTitle = item.channelTitle,
                                                    channelId = item.channelId
                                                )
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
                                            val isChannel = item.badge.equals("Channel", ignoreCase = true) ||
                                                    item.genres.contains("Channel") ||
                                                    item.id.startsWith("UC")

                                            if (isChannel) {
                                                // Dedicated YouTube Channel Result Card
                                                YouTubeChannelCard(
                                                    item = item,
                                                    onClick = {
                                                        selectedChannelItem = item
                                                    }
                                                )
                                            } else {
                                                YouTubeVideoCard(
                                                    item = item,
                                                    onClick = {
                                                        playingVideo = item
                                                        isPlayerExpanded = true
                                                    },
                                                    onChannelClick = {
                                                        selectedChannelItem = MediaItem(
                                                            id = item.channelId ?: item.channelTitle ?: item.id,
                                                            title = item.channelTitle ?: item.title,
                                                            category = CategoryType.YOUTUBE,
                                                            thumbnail = item.channelAvatar ?: item.thumbnail,
                                                            badge = "Channel",
                                                            genres = listOf("YouTube", "Channel"),
                                                            channelAvatar = item.channelAvatar,
                                                            channelTitle = item.channelTitle,
                                                            channelId = item.channelId
                                                        )
                                                    }
                                                )
                                            }
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

        // Full Screen Video Player Overlay
        if (playingVideo != null && isPlayerExpanded) {
            YouTubeFullPlayerView(
                item = playingVideo!!,
                playerView = playerViewInstance,
                isLoading = isPlayerLoading,
                isBuffering = isPlayerBuffering,
                error = playerError,
                repository = repository,
                onMinimize = { isPlayerExpanded = false },
                onChannelClick = { ch ->
                    selectedChannelItem = ch
                    isPlayerExpanded = false
                },
                onSelectRelatedVideo = { rel ->
                    playingVideo = rel
                }
            )
        }
    }
}

/**
 * 100% Faithful YouTube Mobile Miniplayer Bar
 * - Docked right above the bottom navigation bar
 * - Displays thin red playback progress bar on top
 * - 16:9 thumbnail preview on the left
 * - Video title and channel name in the center
 * - Play/Pause and Close buttons on the right
 * - Tap anywhere on card to expand to full player
 */
@Composable
private fun YouTubeMiniplayerBar(
    item: MediaItem,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Float,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(YtMiniplayerBg)
            .clickable(onClick = onExpand)
    ) {
        // Linear Progress bar (YouTube Red indicator)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp),
            color = YtRed,
            trackColor = Color(0x33FFFFFF)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail / Preview
            Box(
                modifier = Modifier
                    .size(width = 86.dp, height = 48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isBuffering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = YtRed,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title & Channel
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = YtTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.channelTitle ?: "YouTube",
                    color = YtTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls: Play/Pause and Close
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = YtTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = YtTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 100% Faithful YouTube Full Screen Video Player
 */
@Composable
private fun YouTubeFullPlayerView(
    item: MediaItem,
    playerView: PlayerView,
    isLoading: Boolean,
    isBuffering: Boolean,
    error: String?,
    repository: MediaRepository,
    onMinimize: () -> Unit,
    onChannelClick: (MediaItem) -> Unit,
    onSelectRelatedVideo: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var relatedVideos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var channelDetail by remember { mutableStateOf<MediaDetail?>(null) }
    var isSubscribed by remember { mutableStateOf(false) }

    LaunchedEffect(item.id) {
        try {
            val detail = repository.getDetail(CategoryType.YOUTUBE, item.id)
            if (detail != null) {
                channelDetail = detail
                val vids = detail.episodes.filter { !it.id.contains(item.id) }.map { ep ->
                    MediaItem(
                        id = ep.url.removePrefix("https://www.youtube.com/watch?v="),
                        title = ep.title,
                        category = CategoryType.YOUTUBE,
                        thumbnail = ep.thumbnail ?: "https://i.ytimg.com/vi/${ep.id}/hqdefault.jpg",
                        url = ep.url,
                        channelTitle = ep.channelTitle ?: detail.channelTitle ?: "YouTube",
                        channelAvatar = detail.channelAvatar,
                        badge = ep.duration ?: "Video"
                    )
                }
                relatedVideos = vids
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YtDark)
            .statusBarsPadding()
    ) {
        // Player Top Navigation Bar (Down arrow to minimize to miniplayer)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimalkan ke Miniplayer",
                    tint = YtTextPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = item.title,
                color = YtTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opsi",
                    tint = YtTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 16:9 Video Player View Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { playerView },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading || isBuffering) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x55000000)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = YtRed, strokeWidth = 3.dp)
                }
            }

            if (error != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Scrollable Video Details & Channel & Related Videos
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                // Video Title
                Text(
                    text = item.title,
                    color = YtTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Views & Date stats
                val viewsText = item.rating?.ifBlank { "YouTube" } ?: "YouTube"
                Text(
                    text = "$viewsText • YouTube Indonesia",
                    color = YtTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Channel Info Row
                val channelName = item.channelTitle
                    ?: channelDetail?.channelTitle
                    ?: "YouTube Channel"
                val channelAvatarUrl = item.channelAvatar
                    ?: channelDetail?.channelAvatar

                val chItem = MediaItem(
                    id = item.channelId ?: channelDetail?.channelId ?: channelName,
                    title = channelName,
                    category = CategoryType.YOUTUBE,
                    thumbnail = channelAvatarUrl ?: item.thumbnail,
                    badge = "Channel",
                    genres = listOf("YouTube", "Channel"),
                    channelAvatar = channelAvatarUrl,
                    channelTitle = channelName,
                    channelId = item.channelId ?: channelDetail?.channelId
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clickable Channel Avatar
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { onChannelClick(chItem) }
                    ) {
                        if (!channelAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = channelAvatarUrl,
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

                    Spacer(modifier = Modifier.width(10.dp))

                    // Clickable Channel Name
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onChannelClick(chItem) }
                    ) {
                        Text(
                            text = channelName,
                            color = YtTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = channelDetail?.status ?: "Channel Resmi",
                            color = YtTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Subscribe Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSubscribed) YtChipInactive else Color.White)
                            .clickable { isSubscribed = !isSubscribed }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSubscribed) "Disubscribe" else "Subscribe",
                            color = if (isSubscribed) Color.White else Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Row: Like, Share, Download, Simpan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    YouTubeActionButton(icon = Icons.Default.ThumbUp, text = "Suka")
                    YouTubeActionButton(icon = Icons.Default.Share, text = "Bagikan")
                    YouTubeActionButton(icon = Icons.Default.Download, text = "Download")
                    YouTubeActionButton(icon = Icons.Default.BookmarkBorder, text = "Simpan")
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = YtBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Video Terkait",
                    color = YtTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Related Videos List
            items(relatedVideos) { related ->
                YouTubeVideoCard(
                    item = related,
                    onClick = { onSelectRelatedVideo(related) },
                    onChannelClick = {
                        val relatedCh = MediaItem(
                            id = related.channelId ?: related.channelTitle ?: related.id,
                            title = related.channelTitle ?: "YouTube Channel",
                            category = CategoryType.YOUTUBE,
                            thumbnail = related.channelAvatar ?: related.thumbnail,
                            badge = "Channel",
                            genres = listOf("YouTube", "Channel"),
                            channelAvatar = related.channelAvatar,
                            channelTitle = related.channelTitle,
                            channelId = related.channelId
                        )
                        onChannelClick(relatedCh)
                    }
                )
            }
        }
    }
}

@Composable
private fun YouTubeActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(YtChipInactive)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = YtTextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                color = YtTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 100% Authentic YouTube Channel Detail View inside YouTube Portal
 */
@Composable
private fun YouTubeChannelDetailView(
    channelItem: MediaItem,
    repository: MediaRepository,
    onBackClick: () -> Unit,
    onVideoClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var channelDetail by remember { mutableStateOf<MediaDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSubscribed by remember { mutableStateOf(false) }
    var isDescExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(channelItem.id) {
        isLoading = true
        try {
            val targetId = channelItem.channelId ?: channelItem.id
            channelDetail = repository.getDetail(CategoryType.YOUTUBE, targetId)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YtDark)
            .statusBarsPadding()
    ) {
        // Channel Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = YtTextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = channelDetail?.title ?: channelItem.title,
                color = YtTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Cari di Channel",
                    tint = YtTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = YtRed, strokeWidth = 2.5.dp)
            }
        } else {
            val detail = channelDetail
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Channel Banner
                item {
                    val bannerUrl = detail?.backdrop?.ifBlank { null }
                    if (!bannerUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 5f)
                                .background(Color(0xFF1E1E1E))
                        ) {
                            AsyncImage(
                                model = bannerUrl,
                                contentDescription = "Channel Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Channel Profile Info
                    val chName = detail?.title ?: channelItem.title
                    val chAvatar = detail?.channelAvatar ?: channelItem.channelAvatar ?: channelItem.thumbnail
                    val chSubs = detail?.status ?: "Channel Resmi"
                    val chRating = detail?.rating ?: ""

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Big Round Avatar
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(YtChipInactive)
                                .border(1.dp, YtBorder, CircleShape)
                        ) {
                            if (chAvatar.isNotBlank()) {
                                AsyncImage(
                                    model = chAvatar,
                                    contentDescription = chName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = chName.firstOrNull()?.uppercase() ?: "Y",
                                        color = Color.White,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Title with verified icon
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chName,
                                color = YtTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Terverifikasi",
                                tint = YtTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Handle and Subscribers Info
                        val metaText = if (chRating.startsWith("@")) "$chRating • $chSubs" else chSubs
                        Text(
                            text = metaText,
                            color = YtTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Full Width Subscribe Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSubscribed) YtChipInactive else Color.White)
                                .clickable { isSubscribed = !isSubscribed }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSubscribed) "DISUBSCRIBE" else "SUBSCRIBE",
                                color = if (isSubscribed) Color.White else Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Description preview snippet
                        if (!detail?.synopsis.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = detail?.synopsis.orEmpty(),
                                color = YtTextSecondary,
                                fontSize = 12.sp,
                                maxLines = if (isDescExpanded) 15 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { isDescExpanded = !isDescExpanded }
                            )
                        }
                    }

                    HorizontalDivider(color = YtBorder, thickness = 0.8.dp)

                    // Video Tab Title Header
                    Text(
                        text = "DAFTAR VIDEO",
                        color = YtTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)
                    )
                }

                // Channel Videos List
                val episodes = detail?.episodes.orEmpty()
                if (episodes.isNotEmpty()) {
                    items(episodes) { ep ->
                        val vItem = MediaItem(
                            id = ep.url.removePrefix("https://www.youtube.com/watch?v="),
                            title = ep.title,
                            category = CategoryType.YOUTUBE,
                            thumbnail = ep.thumbnail ?: "https://i.ytimg.com/vi/${ep.id}/hqdefault.jpg",
                            url = ep.url,
                            channelTitle = detail?.title ?: channelItem.title,
                            channelAvatar = detail?.channelAvatar ?: channelItem.channelAvatar,
                            badge = ep.duration ?: "Video"
                        )
                        YouTubeVideoCard(
                            item = vItem,
                            onClick = { onVideoClick(vItem) }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tidak ada video ditemukan",
                                color = YtTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Authentic YouTube Channel Card (Used in Search Results)
 */
@Composable
private fun YouTubeChannelCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isSubscribed by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Large Channel Avatar
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(YtChipInactive)
                .border(0.5.dp, YtBorder, CircleShape)
        ) {
            val avatarUrl = item.channelAvatar ?: item.thumbnail
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.title.firstOrNull()?.uppercase() ?: "Y",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Channel Metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = YtTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            val subText = item.rating?.ifBlank { "Channel Resmi" } ?: "Channel Resmi"
            Text(
                text = subText,
                color = YtTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Channel",
                color = YtTextSecondary,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Subscribe Pill Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (isSubscribed) YtChipInactive else Color.White)
                .clickable { isSubscribed = !isSubscribed }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSubscribed) "Disubscribe" else "Subscribe",
                color = if (isSubscribed) Color.White else Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = onChannelClick != null) { onChannelClick?.invoke() }
            ) {
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
