package com.nanzstream.nanas.ui.screens

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nanzstream.nanas.NanzStreamApp
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.ContinueWatchingItem
import com.nanzstream.nanas.data.model.StreamResult
import com.nanzstream.nanas.data.model.StreamServerItem
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.theme.*

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
    var currentEpisode by remember { mutableIntStateOf(initialEpisode) }
    var streamResult by remember { mutableStateOf<StreamResult?>(null) }
    var selectedServer by remember { mutableStateOf<StreamServerItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Media3 ExoPlayer instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Load stream data on episode change
    LaunchedEffect(currentEpisode) {
        isLoading = true
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

            // Setup ExoPlayer if direct HLS available
            val activeDirectHls = if (firstServer?.isDirectHls == true) firstServer.url else res?.directHlsUrl
            if (!activeDirectHls.isNullOrEmpty() && (firstServer == null || firstServer.isDirectHls)) {
                val mediaItem = MediaItem.Builder()
                    .setUri(activeDirectHls)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // Switch stream when user selects different server
    LaunchedEffect(selectedServer) {
        val s = selectedServer ?: return@LaunchedEffect
        if (s.isDirectHls) {
            val mediaItem = MediaItem.Builder()
                .setUri(s.url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // 1. Top Header with Back button & Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(1.dp, GlassBorder, CircleShape)
                    .background(GlassBackground)
                    .clickable(onClick = onBackClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Kembali",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${category.displayName} • Episode $currentEpisode",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }

        // 2. Video Player Area (ExoPlayer or Sandboxed WebView)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            } else {
                val curServer = selectedServer
                val isDirect = if (curServer != null) {
                    curServer.isDirectHls
                } else {
                    !streamResult?.directHlsUrl.isNullOrEmpty()
                }
                val directHls = if (curServer?.isDirectHls == true) curServer.url else streamResult?.directHlsUrl
                val iframeUrl = if (curServer != null && !curServer.isDirectHls) curServer.url else streamResult?.iframePlayerUrl

                if (isDirect && !directHls.isNullOrEmpty()) {
                    // ExoPlayer Native View
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
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (!iframeUrl.isNullOrEmpty()) {
                    // Sandboxed WebView for Embed Player
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
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
                                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                }
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        // Prevent unwanted ad popups from redirecting out
                                        if (url != null && !url.contains("player") && !url.contains("embed") && !url.contains("video")) {
                                            return true
                                        }
                                        return false
                                    }
                                }
                                loadUrl(iframeUrl)
                            }
                        },
                        update = { webView ->
                            if (webView.url != iframeUrl) {
                                webView.loadUrl(iframeUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Video tidak dapat dimuat atau belum tersedia.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 3. Episode & Server Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Next / Prev Episode Nav Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev Ep
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .background(if (currentEpisode > 1) GlassBackground else Color(0x0AFFFFFF))
                        .clickable(enabled = currentEpisode > 1) { currentEpisode-- }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Episode Sebelumnya",
                        tint = if (currentEpisode > 1) Color.White else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Prev Ep",
                        color = if (currentEpisode > 1) Color.White else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "Episode $currentEpisode",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Next Ep
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .background(GlassBackground)
                        .clickable { currentEpisode++ }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Next Ep",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Episode Selanjutnya",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Server Selection
            val servers = streamResult?.servers ?: emptyList()
            if (servers.isNotEmpty()) {
                Text(
                    text = "Pilih Server Pemutar",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    servers.forEach { s ->
                        val isSelected = selectedServer?.name == s.name
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, if (isSelected) Color.White else GlassBorder, RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color.White else GlassBackground)
                                .clickable { selectedServer = s }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = s.name,
                                color = if (isSelected) Color.Black else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
