package com.nanzstream.nanas.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.LiveTvChannelItem
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.components.GlassPill
import com.nanzstream.nanas.ui.theme.*

@OptIn(UnstableApi::class)
@Composable
fun LiveTvScreen(
    repository: MediaRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var isInPiP by remember { mutableStateOf(activity?.isInPictureInPictureMode ?: false) }

    var channels by remember { mutableStateOf<List<LiveTvChannelItem>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<LiveTvChannelItem?>(null) }
    var selectedGenre by remember { mutableStateOf("Semua") }
    var isStreamLoading by remember { mutableStateOf(false) }
    var isStreamError by remember { mutableStateOf(false) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var autoRetryCount by remember { mutableIntStateOf(0) }

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

    // Configure ExoPlayer with live-tuned buffer control to prevent skipping/looping
    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://www.cubmu.com/",
                    "Origin" to "https://www.cubmu.com"
                )
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10_000, true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                isStreamLoading = false
                isStreamError = true
                if (autoRetryCount < 3) {
                    autoRetryCount++
                    retryTrigger++
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isStreamLoading = false
                    isStreamError = false
                    autoRetryCount = 0
                } else if (playbackState == Player.STATE_ENDED) {
                    isStreamLoading = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Fetch Channel list
    LaunchedEffect(Unit) {
        channels = repository.getLiveTvChannels()
        if (channels.isNotEmpty() && selectedChannel == null) {
            selectedChannel = channels.first()
        }
    }

    // Play selected channel stream with fixed live configuration
    LaunchedEffect(selectedChannel, retryTrigger) {
        val ch = selectedChannel ?: return@LaunchedEffect
        isStreamLoading = true
        isStreamError = false
        try {
            val streamRes = repository.getStream(CategoryType.LIVETV, ch.slug ?: ch.id)
            val streamUrl = streamRes?.directHlsUrl
            if (!streamUrl.isNullOrEmpty()) {
                val liveConfig = MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(8_000) // Keep a stable 8s buffer behind live edge
                    .setMinOffsetMs(4_000)
                    .setMaxOffsetMs(20_000)
                    .setMinPlaybackSpeed(1.0f) // Never speed up or slow down
                    .setMaxPlaybackSpeed(1.0f)
                    .build()

                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setLiveConfiguration(liveConfig)

                if (streamUrl.contains(".mpd")) {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                } else if (streamUrl.contains(".m3u8")) {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                exoPlayer.setMediaItem(mediaItemBuilder.build())
                exoPlayer.prepare()
                exoPlayer.play()
            } else {
                isStreamLoading = false
                isStreamError = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isStreamLoading = false
            isStreamError = true
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

    // When inside PiP Mode, show ONLY the clean PlayerView filling the whole frame
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

    val genres = remember(channels) {
        listOf("Semua") + channels.map { it.genre }.distinct()
    }

    val filteredChannels = remember(channels, selectedGenre) {
        if (selectedGenre == "Semua") channels else channels.filter { it.genre == selectedGenre }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // 1. Live Player View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
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

            if (isStreamLoading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }

            if (isStreamError && !isStreamLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xD9000000))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Siaran terputus / perlu dimuat ulang",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable {
                                autoRetryCount = 0
                                retryTrigger++
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Muat Ulang Siaran",
                            color = CanvasBlack,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Channel Title Pill floating on player (Top Left)
            selectedChannel?.let { ch ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE • ${ch.name}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Picture-in-Picture Button floating on player (Top Right)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000))
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
        }

        // 2. Genre Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            genres.forEach { g ->
                GlassPill(
                    title = g,
                    icon = "📺",
                    isSelected = selectedGenre == g,
                    onClick = { selectedGenre = g }
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        // 3. Channels Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredChannels) { ch ->
                val isCurrent = selectedChannel?.id == ch.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (isCurrent) Color.White else GlassBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .background(if (isCurrent) Color(0x35FFFFFF) else DarkCard)
                        .clickable { selectedChannel = ch }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(ch.logoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = ch.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = ch.name,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "CH ${ch.number} • ${ch.genre}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
