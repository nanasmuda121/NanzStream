package com.nanzstream.nanas.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.nanzstream.nanas.NanzStreamApp
import com.nanzstream.nanas.PlaybackController
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.ContinueWatchingItem
import com.nanzstream.nanas.data.model.EpisodeItem
import com.nanzstream.nanas.data.model.StreamResult
import com.nanzstream.nanas.data.repository.MediaRepository
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
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    val lifecycleOwner = LocalLifecycleOwner.current

    var isInPiP by remember { mutableStateOf(activity?.isInPictureInPictureMode ?: false) }
    var currentEpisode by remember { mutableIntStateOf(initialEpisode) }
    var currentTargetUrl by remember { mutableStateOf(targetUrl) }
    var episodesList by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }

    var streamResult by remember { mutableStateOf<StreamResult?>(null) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var isDirectStream by remember { mutableStateOf(true) }

    var isLoading by remember { mutableStateOf(true) }
    var webViewError by remember { mutableStateOf(false) }
    var webViewErrorMessage by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Listen to Picture-in-Picture mode transitions
    DisposableEffect(activity) {
        val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPiP = info.isInPictureInPictureMode
        }
        activity?.addOnPictureInPictureModeChangedListener(pipListener)
        onDispose {
            activity?.removeOnPictureInPictureModeChangedListener(pipListener)
        }
    }

    val exoPlayer = remember {
        val defaultReferer = if (category == CategoryType.ANIME) "https://desustream.net/" else "https://anichin.ro/"
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to defaultReferer,
                    "Origin" to defaultReferer.trimEnd('/')
                )
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    // Stop background audio playback when app is paused/stopped/destroyed
    DisposableEffect(lifecycleOwner) {
        PlaybackController.stopAllPlayback = {
            exoPlayer.pause()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (activity?.isInPictureInPictureMode != true) {
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (activity?.isInPictureInPictureMode != true) {
                        exoPlayer.pause()
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

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                isLoading = false
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                    isLoading = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            setSystemFullscreen(activity, false)
            webViewInstance?.destroy()
            webViewInstance = null
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Fetch full episode list from series detail
    LaunchedEffect(targetUrl) {
        try {
            val detail = repository.getDetail(category, targetUrl)
            if (detail != null && detail.episodes.isNotEmpty()) {
                episodesList = detail.episodes
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Load stream data on episode / URL change (DIRECT STREAM ONLY)
    LaunchedEffect(currentTargetUrl, currentEpisode) {
        isLoading = true
        webViewError = false
        webViewErrorMessage = null
        try {
            val res = repository.getStream(category, currentTargetUrl, currentEpisode)
            streamResult = res

            // Choose direct stream automatically without server switcher
            val direct = res?.directHlsUrl
                ?: res?.servers?.firstOrNull { it.isDirectHls }?.url
                ?: res?.servers?.firstOrNull()?.url

            activeStreamUrl = direct

            // Save to Continue Watching
            NanzStreamApp.storage.saveContinueWatching(
                ContinueWatchingItem(
                    mediaId = currentTargetUrl,
                    title = title,
                    thumbnail = "",
                    category = category,
                    lastItemTitle = "Episode $currentEpisode",
                    lastTargetUrl = currentTargetUrl
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
        }
    }

    // Play active stream in native ExoPlayer (or fallback to WebView embed if direct not available)
    LaunchedEffect(activeStreamUrl) {
        val url = activeStreamUrl ?: return@LaunchedEffect
        val direct = url.contains(".mp4", ignoreCase = true) ||
                url.contains(".m3u8", ignoreCase = true) ||
                url.contains(".mpd", ignoreCase = true) ||
                (streamResult?.servers?.any { it.url == url && it.isDirectHls } == true)

        isDirectStream = direct

        if (direct) {
            isLoading = true
            webViewInstance?.stopLoading()
            webViewInstance?.loadUrl("about:blank")
            webViewInstance = null

            val mediaItemBuilder = MediaItem.Builder().setUri(url)
            if (url.contains(".mpd", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            } else if (url.contains(".m3u8", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (url.contains(".mp4", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MP4)
            }
            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.pause()
            exoPlayer.clearMediaItems()
            isLoading = true
            webViewInstance?.apply {
                stopLoading()
                loadUrl(url)
            }
        }
    }

    fun triggerPiP(act: Activity?) {
        if (act != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val paramsBuilder = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                paramsBuilder.setAutoEnterEnabled(true)
            }
            act.enterPictureInPictureMode(paramsBuilder.build())
        }
    }

    // When inside PiP Mode, show ONLY the clean PlayerView filling the entire PiP window
    if (isInPiP) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
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
                        text = title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Episode $currentEpisode",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. Fixed Video Player Area (ExoPlayer Native View)
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
            val directHls = if (isDirectStream) activeStreamUrl else null
            val iframeUrl = if (!isDirectStream) activeStreamUrl else null

            if (isDirectStream && !directHls.isNullOrEmpty()) {
                // ExoPlayer Native View - ZERO WEBVIEW
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!iframeUrl.isNullOrEmpty()) {
                // Fallback sandboxed WebView only if direct stream unavailable
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                                }
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val u = request?.url?.toString() ?: return false
                                        val scheme = request.url?.scheme?.lowercase() ?: ""
                                        if (scheme != "http" && scheme != "https") {
                                            return true
                                        }
                                        val lower = u.lowercase()
                                        if (lower.contains("adsterra") || lower.contains("popads") || lower.contains("bet") ||
                                            lower.contains("slot") || lower.contains("judi") || lower.contains("onclick") ||
                                            (lower.contains("track") && lower.contains("click"))) {
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        webViewError = false
                                        webViewErrorMessage = null
                                        isLoading = false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                    }

                                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            val desc = error?.description?.toString() ?: ""
                                            webViewError = true
                                            webViewErrorMessage = desc.ifEmpty { "Gagal memuat pemutar video" }
                                            isLoading = false
                                        }
                                    }
                                }
                                loadUrl(iframeUrl)
                            }
                        },
                        update = { webView ->
                            webViewInstance = webView
                            if (webView.url != iframeUrl) {
                                webView.stopLoading()
                                webView.loadUrl(iframeUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Error overlay
                    if (webViewError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF141419))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Peringatan",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gagal memuat video",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceElevated)
                                    .clickable {
                                        webViewError = false
                                        webViewInstance?.loadUrl(iframeUrl)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(text = "Muat Ulang", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else if (!isLoading) {
                Text(
                    text = "Video tidak dapat dimuat atau belum tersedia.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }

            // Elegant Loading Spinner Overlay on top of player
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xB3000000)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
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
        // Player stays FIXED at top, only this section scrolls!
        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Title & Subtitle
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Sedang Memutar: Episode $currentEpisode",
                    color = TextMuted,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Next / Prev Episode Nav Buttons
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

                // Episode Chips Grid (5 columns per row, completely scrollable without disturbing player)
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
                                Text(
                                    text = ep.episodeNumber.ifBlank { "$epNum" },
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

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
