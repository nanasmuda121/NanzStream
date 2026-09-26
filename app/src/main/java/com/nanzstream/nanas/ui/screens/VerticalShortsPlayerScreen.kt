package com.nanzstream.nanas.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.MediaItem as AppMediaItem
import com.nanzstream.nanas.data.model.StreamResult
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.data.scraper.YouTubeScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 100% Authentic YouTube Shorts Portrait (Vertical) Player
 * - Fullscreen 9:16 vertical video
 * - VerticalPager swipe up/down to seamlessly transition between Shorts
 * - Like, Dislike, Share, Channel Subscribe actions
 * - Tap to toggle Play/Pause
 * - Auto-loop playback (Shorts loop behavior)
 */
@OptIn(UnstableApi::class)
@Composable
fun VerticalShortsPlayerScreen(
    title: String,
    initialUrl: String,
    repository: MediaRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val initialCleanId = remember(initialUrl) {
        when {
            initialUrl.contains("/shorts/") -> Regex("""/shorts/([a-zA-Z0-9_\-]+)""").find(initialUrl)?.groupValues?.get(1) ?: initialUrl
            initialUrl.contains("v=") -> Regex("""v=([a-zA-Z0-9_\-]+)""").find(initialUrl)?.groupValues?.get(1) ?: initialUrl
            initialUrl.contains("youtu.be/") -> Regex("""youtu\.be/([a-zA-Z0-9_\-]+)""").find(initialUrl)?.groupValues?.get(1) ?: initialUrl
            else -> initialUrl.removePrefix("https://www.youtube.com/watch?v=").trim()
        }
    }

    var shortsList by remember {
        mutableStateOf(
            listOf(
                AppMediaItem(
                    id = initialCleanId,
                    title = title,
                    category = CategoryType.YOUTUBE,
                    thumbnail = "https://i.ytimg.com/vi/$initialCleanId/hqdefault.jpg",
                    url = "https://www.youtube.com/shorts/$initialCleanId",
                    slug = initialCleanId,
                    badge = "Shorts",
                    channelTitle = "YouTube Creator"
                )
            )
        )
    }

    // Preload & Append trending Shorts in background
    LaunchedEffect(Unit) {
        try {
            val moreShorts = withContext(Dispatchers.IO) {
                repository.getYouTubeShorts()
            }
            if (moreShorts.isNotEmpty()) {
                val combined = shortsList.toMutableList()
                moreShorts.forEach { ms ->
                    if (!combined.any { it.id == ms.id }) {
                        combined.add(ms)
                    }
                }
                shortsList = combined
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { shortsList.size })

    var isPlaying by remember { mutableStateOf(true) }
    var isLoadingStream by remember { mutableStateOf(true) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var isLiked by remember { mutableStateOf(false) }
    var isDisliked by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(false) }

    // ExoPlayer Setup with YouTube iOS User-Agent
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5000, 30000, 1000, 2000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(YouTubeScraper.IOS_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE // YouTube Shorts auto-loops
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Playback state listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isLoadingStream = false
                        streamError = null
                    }
                    Player.STATE_BUFFERING -> {
                        isLoadingStream = true
                    }
                    Player.STATE_ENDED -> {
                        // Loop current or advance
                    }
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoadingStream = false
                streamError = "Gagal memutar Shorts. Menghubungkan ulang..."
                exoPlayer.prepare()
                exoPlayer.play()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // When page changes, resolve stream & play the new Short
    LaunchedEffect(pagerState.currentPage, shortsList.size) {
        if (shortsList.isEmpty()) return@LaunchedEffect
        val currentShort = shortsList.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val vId = currentShort.id

        isLoadingStream = true
        streamError = null
        isLiked = false
        isDisliked = false

        try {
            val res: StreamResult? = withContext(Dispatchers.IO) {
                repository.getStream(CategoryType.YOUTUBE, "https://www.youtube.com/shorts/$vId", 1)
            }

            val streamUrl = res?.directHlsUrl ?: res?.servers?.firstOrNull()?.url
            if (!streamUrl.isNullOrBlank()) {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(YouTubeScraper.IOS_USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)
                    .setDefaultRequestProperties(mapOf("Origin" to "https://www.youtube.com", "Referer" to "https://www.youtube.com/"))

                val audioUrl = res?.audioUrl
                if (!audioUrl.isNullOrBlank() && audioUrl.startsWith("http")) {
                    // Merging Adaptive Video + AAC Audio
                    val videoSource = ProgressiveMediaSource.Factory(httpFactory)
                        .createMediaSource(MediaItem.fromUri(streamUrl))
                    val audioSource = ProgressiveMediaSource.Factory(httpFactory)
                        .createMediaSource(MediaItem.fromUri(audioUrl))
                    val mergedSource = MergingMediaSource(videoSource, audioSource)
                    exoPlayer.setMediaSource(mergedSource)
                } else if (streamUrl.contains(".m3u8", ignoreCase = true) || streamUrl.contains("manifest/hls", ignoreCase = true)) {
                    val hlsSource = HlsMediaSource.Factory(httpFactory)
                        .createMediaSource(MediaItem.fromUri(streamUrl))
                    exoPlayer.setMediaSource(hlsSource)
                } else {
                    val progSource = ProgressiveMediaSource.Factory(httpFactory)
                        .createMediaSource(MediaItem.fromUri(streamUrl))
                    exoPlayer.setMediaSource(progSource)
                }
                exoPlayer.prepare()
                exoPlayer.play()
            } else {
                isLoadingStream = false
                streamError = "Video Shorts tidak dapat dimuat."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isLoadingStream = false
            streamError = "Terjadi kesalahan saat memuat Shorts."
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Vertical Pager with full-screen pages
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val shortItem = shortsList.getOrNull(page) ?: return@VerticalPager
            val isCurrentPage = pagerState.currentPage == page

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
            ) {
                // Video Surface
                if (isCurrentPage) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Placeholder thumbnail while swiping
                    AsyncImage(
                        model = shortItem.thumbnail,
                        contentDescription = shortItem.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top & Bottom Gradient Shadows
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x99000000),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color(0xB3000000)
                                )
                            )
                        )
                )

                // Play / Pause Indicator overlay
                AnimatedVisibility(
                    visible = !isPlaying && !isLoadingStream && streamError == null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Putar",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Loading Spinner
                if (isLoadingStream && isCurrentPage) {
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFF0000),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Error Message
                if (streamError != null && isCurrentPage) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xCC000000))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = streamError ?: "Error",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
                            ) {
                                Text("Coba Lagi", color = Color.White)
                            }
                        }
                    }
                }

                // Right Action Sidebar (Like, Dislike, Share, Audio)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Channel Avatar with Subscribe Badge
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!shortItem.channelAvatar.isNullOrBlank()) {
                            AsyncImage(
                                model = shortItem.channelAvatar,
                                contentDescription = shortItem.channelTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color.White, CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF333333))
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (shortItem.channelTitle ?: "Y").firstOrNull()?.uppercase() ?: "Y",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Subscribe "+" badge if not subscribed
                        if (!isSubscribed) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 6.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF0000))
                                    .clickable { isSubscribed = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Subscribe",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Like Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            isLiked = !isLiked
                            if (isLiked) isDisliked = false
                        }
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                            contentDescription = "Like",
                            tint = if (isLiked) Color(0xFFFF0000) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isLiked) "Disukai" else "Suka",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Dislike Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            isDisliked = !isDisliked
                            if (isDisliked) isLiked = false
                        }
                    ) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                            contentDescription = "Dislike",
                            tint = if (isDisliked) Color(0xFFFF0000) else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dislike",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Share Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, shortItem.title)
                                    putExtra(Intent.EXTRA_TEXT, "${shortItem.title}\nhttps://www.youtube.com/shorts/${shortItem.id}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Bagikan Shorts"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Bagikan",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bagikan",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Spinning Sound/Music Disc
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .border(1.dp, Color(0x66FFFFFF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Suara Asli",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Bottom Info Overlay: Channel Name + Title + Subscribe Button
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 32.dp, end = 88.dp)
                ) {
                    // Channel Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val chName = shortItem.channelTitle?.ifBlank { "YouTube Creator" } ?: "YouTube Creator"
                        Text(
                            text = "@$chName",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Subscribe Pill Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSubscribed) Color(0x33FFFFFF) else Color.White)
                                .clickable { isSubscribed = !isSubscribed }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isSubscribed) "Disubscribe" else "Subscribe",
                                color = if (isSubscribed) Color.White else Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Video Title / Caption
                    Text(
                        text = shortItem.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                // Top Bar: Back Button & "Shorts" Logo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 16.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "Shorts",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Cari",
                        tint = Color.White,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onBackClick() }
                    )
                }
            }
        }
    }
}
