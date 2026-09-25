package com.nanzstream.nanas.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
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
import com.nanzstream.nanas.data.model.StreamResult
import com.nanzstream.nanas.data.model.StreamServerItem
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
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentEpisode by remember { mutableIntStateOf(initialEpisode) }
    var streamResult by remember { mutableStateOf<StreamResult?>(null) }
    var selectedServer by remember { mutableStateOf<StreamServerItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewError by remember { mutableStateOf(false) }
    var webViewErrorMessage by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        val defaultReferer = if (category == CategoryType.ANIME) "https://desustream.net/" else "https://anichin.ro/"
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
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
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    exoPlayer.pause()
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

    // Load stream data on episode change
    LaunchedEffect(currentEpisode) {
        isLoading = true
        webViewError = false
        webViewErrorMessage = null
        try {
            val res = repository.getStream(category, targetUrl, currentEpisode)
            streamResult = res
            val firstServer = res?.servers?.firstOrNull()
            selectedServer = firstServer

            // Save to Continue Watching
            NanzStreamApp.storage.saveContinueWatching(
                ContinueWatchingItem(
                    mediaId = targetUrl,
                    title = title,
                    thumbnail = "",
                    category = category,
                    lastItemTitle = "Episode $currentEpisode",
                    lastTargetUrl = targetUrl
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
        } finally {
            isLoading = false
        }
    }

    // Switch stream when user selects different server
    LaunchedEffect(selectedServer) {
        webViewError = false
        webViewErrorMessage = null
        val s = selectedServer ?: return@LaunchedEffect
        val serverIsDirect = s.isDirectHls || s.url.contains(".mp4", ignoreCase = true) || s.url.contains(".m3u8", ignoreCase = true) || s.url.contains(".mpd", ignoreCase = true)
        if (serverIsDirect) {
            isLoading = true
            webViewInstance?.stopLoading()
            webViewInstance?.loadUrl("about:blank")
            webViewInstance = null

            val mediaItemBuilder = MediaItem.Builder().setUri(s.url)
            if (s.url.contains(".mpd", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            } else if (s.url.contains(".m3u8", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (s.url.contains(".mp4", ignoreCase = true)) {
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
                loadUrl(s.url)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // 1. Top Header with Back button, Title & Fullscreen toggle (Hidden when Fullscreen)
        if (!isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${category.displayName} • Episode $currentEpisode",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Fullscreen Button in Header
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, GlassBorder, CircleShape)
                        .background(SurfaceElevated)
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
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 2. Video Player Area (ExoPlayer or Sandboxed WebView)
        Box(
            modifier = if (isFullscreen) {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color.Black)
            },
            contentAlignment = Alignment.Center
        ) {
            val curServer = selectedServer
            val isDirect = if (curServer != null) {
                curServer.isDirectHls || curServer.url.contains(".mp4", ignoreCase = true) || curServer.url.contains(".m3u8", ignoreCase = true) || curServer.url.contains(".mpd", ignoreCase = true)
            } else {
                !streamResult?.directHlsUrl.isNullOrEmpty()
            }
            val directHls = if (curServer != null && isDirect) curServer.url else streamResult?.directHlsUrl
            val iframeUrl = if (curServer != null && !isDirect) curServer.url else streamResult?.iframePlayerUrl

            if (isDirect && !directHls.isNullOrEmpty()) {
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
                // Sandboxed WebView for Embed Player with error interceptor and retry
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
                                            webViewErrorMessage = if (desc.contains("REFUSED", ignoreCase = true) || desc.contains("ERR_CONNECTION", ignoreCase = true) || desc.contains("NAME_NOT_RESOLVED", ignoreCase = true)) {
                                                "Server menolak koneksi (Offline / Diblokir ISP)"
                                            } else {
                                                desc.ifEmpty { "Gagal memuat pemutar video" }
                                            }
                                            isLoading = false
                                        }
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                        super.onReceivedError(view, errorCode, description, failingUrl)
                                        webViewError = true
                                        webViewErrorMessage = if (description?.contains("REFUSED", ignoreCase = true) == true) {
                                            "Server menolak koneksi (Offline / Diblokir ISP)"
                                        } else {
                                            description ?: "Gagal memuat pemutar video"
                                        }
                                        isLoading = false
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

                    // Elegant Fallback Overlay when WebView encounters an error
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
                                text = "Server Pemutar Offline / Gangguan",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = webViewErrorMessage ?: "Server pemutar ini tidak merespons. Silakan ganti ke server cadangan di bawah.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            val allServers = streamResult?.servers ?: emptyList()
                            val curIndex = allServers.indexOfFirst { it == selectedServer || (it.url == selectedServer?.url && it.name == selectedServer?.name) }
                            val nextIndex = if (curIndex >= 0 && allServers.size > 1) (curIndex + 1) % allServers.size else -1

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (nextIndex >= 0 && nextIndex != curIndex) {
                                    val nextServer = allServers[nextIndex]
                                    Box(
                                        modifier = Modifier
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White)
                                            .clickable {
                                                webViewError = false
                                                webViewErrorMessage = null
                                                selectedServer = nextServer
                                            }
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Beralih ke ${nextServer.name}",
                                            color = CanvasBlack,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                        .background(SurfaceElevated)
                                        .clickable {
                                            webViewError = false
                                            webViewErrorMessage = null
                                            webViewInstance?.loadUrl(iframeUrl)
                                        }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Muat Ulang",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
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
        }

            // Floating Controls for Fullscreen Toggle overlay
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
                // Quick Fullscreen button on video player overlay (Top Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(38.dp)
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

        // 3. Episode & Server Controls (Only visible when NOT in Fullscreen)
        if (!isFullscreen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Next / Prev Episode Nav Buttons (ENLARGED)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prev Ep Button (Larger)
                    Row(
                        modifier = Modifier
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .background(if (currentEpisode > 1) SurfaceElevated else Color(0x0AFFFFFF))
                            .clickable(enabled = currentEpisode > 1) { currentEpisode-- }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Episode Sebelumnya",
                            tint = if (currentEpisode > 1) Color.White else TextDim,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Prev Ep",
                            color = if (currentEpisode > 1) Color.White else TextDim,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Current Episode Title
                    Text(
                        text = "Episode $currentEpisode",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // Next Ep Button (Larger)
                    Row(
                        modifier = Modifier
                            .height(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, Color.White, RoundedCornerShape(12.dp))
                            .background(SurfaceElevated)
                            .clickable { currentEpisode++ }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Next Ep",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Episode Selanjutnya",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Server Selection (ENLARGED PILLS)
                val servers = streamResult?.servers ?: emptyList()
                if (servers.isNotEmpty()) {
                    Text(
                        text = "PILIH SERVER PEMUTAR",
                        color = TextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        servers.forEach { s ->
                            val isSelected = (selectedServer == s) || (selectedServer?.name == s.name && selectedServer?.url == s.url)
                            Box(
                                modifier = Modifier
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        1.5.dp,
                                        if (isSelected) Color.White else BorderHairline,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .background(if (isSelected) Color.White else SurfaceElevated)
                                    .clickable {
                                        webViewError = false
                                        webViewErrorMessage = null
                                        if (selectedServer != s) {
                                            selectedServer = s
                                        } else {
                                            val serverIsDirect = s.isDirectHls || s.url.contains(".mp4", ignoreCase = true) || s.url.contains(".m3u8", ignoreCase = true) || s.url.contains(".mpd", ignoreCase = true)
                                            if (serverIsDirect) {
                                                isLoading = true
                                                exoPlayer.seekToDefaultPosition()
                                                exoPlayer.prepare()
                                                exoPlayer.play()
                                            } else {
                                                isLoading = true
                                                webViewInstance?.stopLoading()
                                                webViewInstance?.loadUrl(s.url)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = s.name,
                                    color = if (isSelected) CanvasBlack else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
