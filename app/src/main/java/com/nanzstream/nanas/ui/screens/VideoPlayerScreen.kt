package com.nanzstream.nanas.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import android.content.Intent
import coil.compose.AsyncImage
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.nanzstream.nanas.NanzStreamApp
import com.nanzstream.nanas.PlaybackController
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.ContinueWatchingItem
import com.nanzstream.nanas.data.model.EpisodeItem
import com.nanzstream.nanas.data.model.MediaDetail
import com.nanzstream.nanas.data.model.StreamResult
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.data.scraper.StreamResolver
import com.nanzstream.nanas.ui.theme.*

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun setSystemFullscreen(activity: Activity?, fullscreen: Boolean) {
    activity ?: return
    val window = activity.window
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    if (fullscreen) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        insetsController.show(WindowInsetsCompat.Type.systemBars())
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    category: CategoryType,
    title: String,
    targetUrl: String,
    initialEpisode: Int,
    repository: MediaRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (category == CategoryType.DRACHINA) {
        VerticalDrachinaPlayerScreen(
            title = title,
            targetUrl = targetUrl,
            initialEpisode = initialEpisode,
            repository = repository,
            onBackClick = onBackClick,
            modifier = modifier
        )
        return
    }

    val isYouTubeShorts = category == CategoryType.YOUTUBE && (
        targetUrl.contains("/shorts/", ignoreCase = true) ||
        title.contains("#shorts", ignoreCase = true)
    )

    if (isYouTubeShorts) {
        VerticalShortsPlayerScreen(
            title = title,
            initialUrl = targetUrl,
            repository = repository,
            onBackClick = onBackClick,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    val lifecycleOwner = LocalLifecycleOwner.current

    var isInPiP by remember { mutableStateOf(activity?.isInPictureInPictureMode ?: false) }
    var currentEpisode by remember { mutableIntStateOf(initialEpisode) }
    var currentTargetUrl by remember { mutableStateOf(targetUrl) }
    var episodesList by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var mediaDetail by remember { mutableStateOf<MediaDetail?>(null) }

    var streamResult by remember { mutableStateOf<StreamResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var autoRetryCount by remember { mutableIntStateOf(0) }
    var currentServerIndex by remember { mutableIntStateOf(0) }
    var streamError by remember { mutableStateOf<String?>(null) }

    // Clean human-readable display title (resolves original title instead of raw IDs/numbers)
    val displayTitle = remember(title, streamResult) {
        val st = streamResult?.title?.trim().orEmpty()
        if (st.isNotBlank() &&
            !st.equals("Anime Episode", ignoreCase = true) &&
            !st.equals("Donghua Episode", ignoreCase = true) &&
            !st.equals("Episode", ignoreCase = true)
        ) {
            st
        } else {
            title
        }
    }

    // 100% Native ExoPlayer with custom LoadControl for lag resilience
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(25_000)
            .setAllowCrossProtocolRedirects(true)

        // Resilient buffer control: fast startup (1s) and auto-recovery on network drops without freezing
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

    // Reuse a single PlayerView instance across orientations & PiP transitions
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

    // PiP Mode Changed Listener: keeps playing seamlessly when entering PiP
    DisposableEffect(activity) {
        val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPiP = info.isInPictureInPictureMode
            if (info.isInPictureInPictureMode) {
                exoPlayer.play()
            }
        }
        activity?.addOnPictureInPictureModeChangedListener(pipListener)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(pipListener)
        }
    }

    // Stop background audio playback ONLY when user actually leaves app (NOT when in PiP)
    DisposableEffect(lifecycleOwner) {
        PlaybackController.stopAllPlayback = {
            if (!isInPiP && activity?.isInPictureInPictureMode != true) {
                exoPlayer.pause()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (activity?.isInPictureInPictureMode != true && !isInPiP) {
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (activity?.isInPictureInPictureMode != true && !isInPiP) {
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (exoPlayer.playbackState == Player.STATE_READY) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            PlaybackController.stopAllPlayback = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle back button when in fullscreen mode
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        setSystemFullscreen(activity, false)
    }

    // Player event listeners: bounded retry and fallback to prevent infinite spinner loop
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                val servers = streamResult?.servers.orEmpty()
                if (autoRetryCount < 2) {
                    autoRetryCount++
                    isLoading = true
                    exoPlayer.prepare()
                    exoPlayer.play()
                } else if (currentServerIndex + 1 < servers.size) {
                    // Automatically switch to next available server if current fails
                    currentServerIndex++
                    autoRetryCount = 0
                } else {
                    isLoading = false
                    streamError = "Gagal memutar video. Silakan coba beberapa saat lagi."
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isLoading = true
                    }
                    Player.STATE_READY -> {
                        isLoading = false
                        streamError = null
                        autoRetryCount = 0
                    }
                    Player.STATE_ENDED -> {
                        isLoading = false
                    }
                    Player.STATE_IDLE -> {}
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            setSystemFullscreen(activity, false)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Fetch full episode list and media detail
    LaunchedEffect(currentTargetUrl) {
        try {
            val detail = repository.getDetail(category, currentTargetUrl)
            if (detail != null) {
                mediaDetail = detail
                if (detail.episodes.isNotEmpty()) {
                    episodesList = detail.episodes
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Load stream data on episode / URL change (100% NATIVE RESOLVER)
    LaunchedEffect(currentTargetUrl, currentEpisode, currentServerIndex) {
        isLoading = true
        streamError = null
        try {
            val res = repository.getStream(category, currentTargetUrl, currentEpisode)
            streamResult = res

            val availableServers = res?.servers.orEmpty()
            val chosenServerItem = if (availableServers.isNotEmpty()) {
                availableServers.getOrNull(currentServerIndex) ?: availableServers.first()
            } else null

            val chosenServerUrl = chosenServerItem?.url ?: res?.directHlsUrl ?: res?.iframePlayerUrl ?: ""
            val chosenAudioUrl = chosenServerItem?.audioUrl ?: res?.audioUrl

            if (chosenServerUrl.isNotBlank()) {
                val defaultReferer = when (category) {
                    CategoryType.ANIME -> "https://desustream.net/"
                    CategoryType.DRACHINA -> "https://www.dracinema.com/"
                    CategoryType.MOVIES -> "https://themoviebox.xyz/"
                    CategoryType.YOUTUBE -> "https://www.youtube.com/"
                    else -> "https://anichin.ro/"
                }
                val playableUrl = StreamResolver.resolveToDirectStream(chosenServerUrl, defaultReferer)

                if (playableUrl.isNotBlank()) {
                    val isYouTubeOrGoogle = category == CategoryType.YOUTUBE || playableUrl.contains("googlevideo.com", ignoreCase = true)
                    val userAgent = if (isYouTubeOrGoogle) {
                        "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; id_ID)"
                    } else {
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                    }
                    val dsHeaders = mutableMapOf<String, String>()
                    if (category == CategoryType.MOVIES || playableUrl.contains("hakunaymatata.com", ignoreCase = true) || playableUrl.contains("aoneroom.com", ignoreCase = true)) {
                        dsHeaders["Referer"] = "https://themoviebox.xyz/"
                        dsHeaders["Origin"] = "https://themoviebox.xyz"
                    } else if (isYouTubeOrGoogle) {
                        dsHeaders["Referer"] = "https://www.youtube.com/"
                        dsHeaders["Origin"] = "https://www.youtube.com"
                    } else if (defaultReferer.isNotBlank()) {
                        dsHeaders["Referer"] = defaultReferer
                    }

                    val dsFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent(userAgent)
                        .setConnectTimeoutMs(20_000)
                        .setReadTimeoutMs(25_000)
                        .setAllowCrossProtocolRedirects(true)

                    if (dsHeaders.isNotEmpty()) {
                        dsFactory.setDefaultRequestProperties(dsHeaders)
                    }

                    if (!chosenAudioUrl.isNullOrBlank()) {
                        // Multi-stream playback (e.g. YouTube video + separate AAC audio stream)
                        val videoSource = ProgressiveMediaSource.Factory(dsFactory)
                            .createMediaSource(MediaItem.fromUri(playableUrl))
                        val audioSource = ProgressiveMediaSource.Factory(dsFactory)
                            .createMediaSource(MediaItem.fromUri(chosenAudioUrl))

                        val mergedSource = MergingMediaSource(videoSource, audioSource)
                        exoPlayer.setMediaSource(mergedSource)
                    } else {
                        val isHls = playableUrl.contains(".m3u8", ignoreCase = true) ||
                            playableUrl.contains("hls_variant", ignoreCase = true) ||
                            playableUrl.contains("hls_playlist", ignoreCase = true)
                        if (isHls) {
                            val mediaItem = MediaItem.Builder()
                                .setUri(playableUrl)
                                .setMimeType(MimeTypes.APPLICATION_M3U8)
                                .build()
                            val hlsSource = HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem)
                            exoPlayer.setMediaSource(hlsSource)
                        } else {
                            val mediaItemBuilder = MediaItem.Builder().setUri(playableUrl)
                            if (playableUrl.contains(".mpd", ignoreCase = true) || playableUrl.contains("manifest/dash", ignoreCase = true)) {
                                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                            } else {
                                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MP4)
                            }
                            val progSource = ProgressiveMediaSource.Factory(dsFactory).createMediaSource(mediaItemBuilder.build())
                            exoPlayer.setMediaSource(progSource)
                        }
                    }
                    exoPlayer.prepare()
                    exoPlayer.play()
                } else {
                    isLoading = false
                    streamError = "Tautan video tidak dapat diputar."
                }
            } else {
                isLoading = false
                streamError = "Tautan streaming tidak ditemukan."
            }

            // Save to Continue Watching
            val isSingleMovie = (category == CategoryType.MOVIES && episodesList.size <= 1) || (episodesList.size <= 1 && (displayTitle.contains("Movie", ignoreCase = true) || episodesList.firstOrNull()?.title?.contains("Movie", ignoreCase = true) == true))
            val lastTitle = when {
                category == CategoryType.YOUTUBE -> "YouTube Video"
                isSingleMovie -> "Full Movie"
                else -> {
                    val currentEpItem = episodesList.find { (it.episodeNumber.toIntOrNull() ?: -1) == currentEpisode }
                    if (currentEpItem != null && currentEpItem.title.isNotBlank() && !currentEpItem.title.equals("Episode 0", ignoreCase = true)) {
                        currentEpItem.title
                    } else {
                        "Episode ${if (currentEpisode <= 0) 1 else currentEpisode}"
                    }
                }
            }
            NanzStreamApp.storage.saveContinueWatching(
                ContinueWatchingItem(
                    mediaId = currentTargetUrl,
                    title = displayTitle,
                    thumbnail = "",
                    category = category,
                    lastItemTitle = lastTitle,
                    lastTargetUrl = currentTargetUrl
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
            streamError = "Gagal memuat video: ${e.message}"
        }
    }

    fun triggerPiP(act: Activity?) {
        if (act != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            isInPiP = true
            exoPlayer.play()
            val aspectRatio = Rational(16, 9)
            val paramsBuilder = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                paramsBuilder.setAutoEnterEnabled(true)
            }
            act.enterPictureInPictureMode(paramsBuilder.build())
        }
    }

    // MAIN LAYOUT
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        if (isInPiP) {
            // When inside PiP Mode, show ONLY the clean PlayerView filling the entire PiP window
            AndroidView(
                factory = {
                    playerViewInstance.apply {
                        useController = false
                    }
                },
                update = { pv ->
                    pv.useController = false
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top Header with Back button & Title (Hidden when Fullscreen)
                if (!isFullscreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(1.dp, GlassBorder, CircleShape)
                                .background(SurfaceElevated)
                                .clickable { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayTitle,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            val isMovieContent = (category == CategoryType.MOVIES && episodesList.size <= 1) || (episodesList.size <= 1 && (displayTitle.contains("Movie", ignoreCase = true) || episodesList.firstOrNull()?.title?.contains("Movie", ignoreCase = true) == true))
                            val isYouTubeContent = category == CategoryType.YOUTUBE
                            val epSubText = when {
                                isYouTubeContent -> "YouTube Video"
                                isMovieContent -> "Full Movie"
                                else -> {
                                    val currentEpItem = episodesList.find { (it.episodeNumber.toIntOrNull() ?: -1) == currentEpisode }
                                    if (currentEpItem != null && currentEpItem.title.isNotBlank() && !currentEpItem.title.equals("Episode 0", ignoreCase = true)) {
                                        currentEpItem.title
                                    } else {
                                        "Episode ${if (currentEpisode <= 0) 1 else currentEpisode}"
                                    }
                                }
                            }
                            Text(
                                text = epSubText,
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // 2. Fixed Video Player Area (100% Native ExoPlayer PlayerView - ZERO WEBVIEW)
                Box(
                    modifier = if (isFullscreen) {
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(235.dp)
                            .background(Color.Black)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = {
                            playerViewInstance.apply {
                                useController = true
                            }
                        },
                        update = { pv ->
                            pv.useController = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Buffering Spinner Overlay during initial load or lag
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x66000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp)
                        }
                    }

                    // Error Overlay inside Player Box with Retry & Server Switch
                    if (streamError != null && !isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xEE0D0D12))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = streamError ?: "Gagal memutar video",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .clickable {
                                            autoRetryCount = 0
                                            streamError = null
                                            isLoading = true
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Coba Lagi", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Floating Controls for PiP & Fullscreen Toggle overlay
                    if (isFullscreen) {
                        // Exit Fullscreen Floating Button (Top Left)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                                .border(1.dp, GlassBorder, CircleShape)
                                .clickable {
                                    isFullscreen = false
                                    setSystemFullscreen(activity, false)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FullscreenExit,
                                contentDescription = "Keluar Layar Penuh",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        // Overlay Actions (Top Right: PiP & Fullscreen)
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Picture-in-Picture Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                                    .border(1.dp, GlassBorder, CircleShape)
                                    .clickable { triggerPiP(activity) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Picture in Picture",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Quick Fullscreen button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x88000000))
                                    .border(1.dp, GlassBorder, CircleShape)
                                    .clickable {
                                        isFullscreen = true
                                        setSystemFullscreen(activity, true)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Layar Penuh",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Scrollable Content Below Player (Episode List & Metadata)
                if (!isFullscreen) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        val isSingleMovie = (category == CategoryType.MOVIES && episodesList.size <= 1) || (episodesList.size <= 1 && (displayTitle.contains("Movie", ignoreCase = true) || episodesList.firstOrNull()?.title?.contains("Movie", ignoreCase = true) == true))
                        val isYouTubeBelow = category == CategoryType.YOUTUBE

                        if (!isYouTubeBelow) {
                            // Title & Subtitle
                            Text(
                                text = displayTitle,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        if (isYouTubeBelow) {
                            // 100% Dedicated YouTube Player UI!
                            Text(
                                text = displayTitle,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${mediaDetail?.rating ?: "YouTube Video"} • ${mediaDetail?.status ?: ""}",
                                color = TextMuted,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Official YouTube Channel Bar (Avatar, Channel Name, Subscribe Button)
                            val channelName = mediaDetail?.channelTitle ?: "YouTube Channel"
                            val channelAvatar = mediaDetail?.channelAvatar
                            var isSubscribed by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceElevated)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (!channelAvatar.isNullOrBlank()) {
                                        AsyncImage(
                                            model = channelAvatar,
                                            contentDescription = channelName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF272727)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = channelName.firstOrNull()?.uppercase() ?: "Y",
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = channelName,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = mediaDetail?.status ?: "Channel YouTube",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // YouTube Subscribe Button (Red pill or Subscribed state)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSubscribed) Color(0xFF272727) else Color(0xFFCC0000))
                                        .clickable { isSubscribed = !isSubscribed }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isSubscribed) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Text(
                                            text = if (isSubscribed) "DISUBSCRIBE" else "SUBSCRIBE",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // YouTube Action Pills Row (Like, Dislike, Bagikan, Unduh MP4)
                            var isLiked by remember { mutableStateOf(false) }
                            var isDisliked by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Like Pill
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isLiked) Color.White else SurfaceElevated)
                                        .clickable {
                                            isLiked = !isLiked
                                            if (isLiked) isDisliked = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbUp,
                                        contentDescription = "Suka",
                                        tint = if (isLiked) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isLiked) "Disukai" else "Suka",
                                        color = if (isLiked) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Dislike Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isDisliked) Color.White else SurfaceElevated)
                                        .clickable {
                                            isDisliked = !isDisliked
                                            if (isDisliked) isLiked = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ThumbDown,
                                        contentDescription = "Tidak Suka",
                                        tint = if (isDisliked) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Share Pill
                                val shareIntent = remember(displayTitle, currentTargetUrl) {
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, displayTitle)
                                        putExtra(Intent.EXTRA_TEXT, "$displayTitle\n$currentTargetUrl")
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(SurfaceElevated)
                                        .clickable {
                                            try {
                                                context.startActivity(Intent.createChooser(shareIntent, "Bagikan Video YouTube"))
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Bagikan",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Bagikan",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Download Pill (Direct MP4 from NewPipe extractor)
                                val uriHandler = LocalUriHandler.current
                                val downloads = streamResult?.downloads.orEmpty()
                                if (downloads.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFF2E7D32))
                                            .clickable {
                                                try {
                                                    uriHandler.openUri(downloads.first().url)
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Unduh",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Unduh MP4",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Download options list if multiple resolutions available
                            val uriHandler = LocalUriHandler.current
                            val downloads = streamResult?.downloads.orEmpty()
                            if (downloads.size > 1) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(downloads) { dl ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                                                .background(SurfaceElevated)
                                                .clickable {
                                                    try {
                                                        uriHandler.openUri(dl.url)
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "📥 ${dl.name}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Expandable Description Box
                            val synopsisText = mediaDetail?.synopsis.orEmpty()
                            if (synopsisText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                var isExpanded by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceElevated)
                                        .clickable { isExpanded = !isExpanded }
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = synopsisText,
                                            color = Color(0xFFDDDDDD),
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                            maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (isExpanded) "Tampilkan lebih sedikit" else "...lebih banyak",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // YouTube Related / Up Next Videos (Horizontal 16:9 cards with thumbnail)
                            val relatedVids = episodesList.filter { it.url != currentTargetUrl }
                            if (relatedVids.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = "VIDEO BERIKUTNYA / TERKAIT",
                                    color = TextDim,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                relatedVids.forEach { videoItem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceElevated)
                                            .clickable {
                                                currentTargetUrl = videoItem.url
                                                currentEpisode = 1
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        // 16:9 Thumbnail
                                        Box(
                                            modifier = Modifier
                                                .width(115.dp)
                                                .height(65.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1E1E1E))
                                        ) {
                                            if (!videoItem.thumbnail.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = videoItem.thumbnail,
                                                    contentDescription = videoItem.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Title + Channel text
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = videoItem.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 17.sp
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = videoItem.channelTitle ?: channelName,
                                                color = TextMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Standard Anime, Donghua, Movies layout below player
                            val playingText = when {
                                isSingleMovie -> "Sedang Memutar: Full Movie"
                                else -> "Sedang Memutar: Episode ${if (currentEpisode <= 0) 1 else currentEpisode}"
                            }
                            Text(
                                text = playingText,
                                color = TextMuted,
                                fontSize = 13.sp
                            )

                            val uriHandler = LocalUriHandler.current
                            val downloads = streamResult?.downloads.orEmpty()
                            if (downloads.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "PILIHAN UNDUH (MP4 / AUDIO)",
                                    color = TextDim,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(downloads) { dl ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                                                .background(SurfaceElevated)
                                                .clickable {
                                                    try {
                                                        uriHandler.openUri(dl.url)
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "📥 ${dl.name}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            // Next / Prev Episode Nav Buttons (Anime / Donghua with multi-episodes)
                            if (!isSingleMovie && episodesList.size > 1) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Prev Ep Button
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                            .background(if (currentEpisode > 1) SurfaceElevated else Color(0x0AFFFFFF))
                                            .clickable(enabled = currentEpisode > 1) {
                                                val prevEp = currentEpisode - 1
                                                currentEpisode = prevEp
                                                val targetEp = episodesList.find {
                                                    (it.episodeNumber.toIntOrNull() ?: -1) == prevEp
                                                }
                                                if (targetEp != null && targetEp.url.isNotBlank()) {
                                                    currentTargetUrl = targetEp.url
                                                }
                                            }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Episode Sebelumnya",
                                            tint = if (currentEpisode > 1) Color.White else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Prev Ep",
                                            color = if (currentEpisode > 1) Color.White else TextMuted,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Next Ep Button
                                    val maxEp = if (episodesList.isNotEmpty()) {
                                        episodesList.maxOfOrNull { it.episodeNumber.toIntOrNull() ?: 0 } ?: 9999
                                    } else 9999

                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                            .background(if (currentEpisode < maxEp) SurfaceElevated else Color(0x0AFFFFFF))
                                            .clickable(enabled = currentEpisode < maxEp) {
                                                val nextEp = currentEpisode + 1
                                                currentEpisode = nextEp
                                                val targetEp = episodesList.find {
                                                    (it.episodeNumber.toIntOrNull() ?: -1) == nextEp
                                                }
                                                if (targetEp != null && targetEp.url.isNotBlank()) {
                                                    currentTargetUrl = targetEp.url
                                                }
                                            }
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Next Ep",
                                            color = if (currentEpisode < maxEp) Color.White else TextMuted,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Episode Selanjutnya",
                                            tint = if (currentEpisode < maxEp) Color.White else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (!isYouTubeBelow) {
                            if (isSingleMovie) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "INFORMASI FILM",
                                    color = TextDim,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SurfaceElevated)
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = "Film ini tersedia dalam format Full Movie langsung untuk ditonton. Nikmati pengalaman streaming lancar dan gunakan fitur PiP atau layar penuh untuk pengalaman terbaik.",
                                        color = TextMuted,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(24.dp))

                                // Scrollable Episode Grid Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DAFTAR EPISODE",
                                        color = TextDim,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    if (episodesList.isNotEmpty()) {
                                        Text(
                                            text = "${episodesList.size} Episode",
                                            color = TextMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Episode Chips Grid (5 columns per row, scrollable without disturbing player)
                                val displayEps = if (episodesList.isNotEmpty()) episodesList else {
                                    val count = maxOf(currentEpisode, 12)
                                    (1..count).map {
                                        EpisodeItem(id = "$it", episodeNumber = "$it", title = "Episode $it", url = targetUrl)
                                    }
                                }

                                displayEps.chunked(5).forEach { rowEps ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowEps.forEach { ep ->
                                            val epNum = ep.episodeNumber.toIntOrNull()
                                                ?: Regex("""\b(\d+)\b""").find(ep.title)?.groupValues?.get(1)?.toIntOrNull()
                                                ?: Regex("""episode-(\d+)""", RegexOption.IGNORE_CASE).find(ep.url)?.groupValues?.get(1)?.toIntOrNull()
                                                ?: 1
                                            val isSelected = epNum == currentEpisode

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(
                                                        1.5.dp,
                                                        if (isSelected) Color.White else BorderHairline,
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .background(if (isSelected) Color.White else SurfaceElevated)
                                                    .clickable {
                                                        currentEpisode = epNum
                                                        if (ep.url.isNotBlank() && ep.url.startsWith("http")) {
                                                            currentTargetUrl = ep.url
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val isMovieChip = ep.title.contains("Movie", ignoreCase = true) || ep.episodeNumber.equals("Movie", ignoreCase = true)
                                                val displayEpNumber = if (isMovieChip) "Movie" else if (ep.episodeNumber == "0" || epNum <= 0) "1" else ep.episodeNumber.ifBlank { "$epNum" }
                                                Text(
                                                    text = displayEpNumber,
                                                    color = if (isSelected) CanvasBlack else TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        repeat(5 - rowEps.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}
