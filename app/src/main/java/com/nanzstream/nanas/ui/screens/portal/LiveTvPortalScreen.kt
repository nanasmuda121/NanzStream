package com.nanzstream.nanas.ui.screens.portal

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nanzstream.nanas.data.model.LiveTvChannelItem
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.theme.*

private enum class LiveTvCategory(val title: String, val icon: ImageVector) {
    SEMUA("Semua", Icons.Default.LiveTv),
    NASIONAL("Nasional", Icons.Default.Tv),
    RELIGI("Religi", Icons.Default.Mosque)
}

data class VerifiedChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val genre: String,
    val logoUrl: String,
    val number: Int
)

private val VERIFIED_CHANNELS = listOf(
    VerifiedChannel("tvri_world", "TVRI World", "https://ott-balancer.tvri.go.id/live/eds/TVRIWorld/hls/TVRIWorld.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/63/TVRI_World_2019.svg/512px-TVRI_World_2019.svg.png", 1),
    VerifiedChannel("tvri_dki", "TVRI Jakarta", "https://ott-balancer.tvri.go.id/live/eds/DKI/hls/DKI.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/53/TVRI_Jakarta.svg/512px-TVRI_Jakarta.svg.png", 2),
    VerifiedChannel("tvri_jabar", "TVRI Jawa Barat", "https://ott-balancer.tvri.go.id/live/eds/Jabar/hls/Jabar.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/TVRI_Jawa_Barat.svg/512px-TVRI_Jawa_Barat.svg.png", 3),
    VerifiedChannel("tvri_jatim", "TVRI Jawa Timur", "https://ott-balancer.tvri.go.id/live/eds/Jatim/hls/Jatim.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/15/TVRI_Jawa_Timur.svg/512px-TVRI_Jawa_Timur.svg.png", 4),
    VerifiedChannel("daai_tv", "DAAI TV", "https://pull.daaiplus.com/live-DAAIPLUS/live-DAAIPLUS_HD.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/DAAI_TV_logo.svg/512px-DAAI_TV_logo.svg.png", 5),
    VerifiedChannel("bn_channel", "BN Channel", "https://flv.intechmedia.net/live/ch112.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/id/thumb/2/25/BN_Channel.png/512px-BN_Channel.png", 6),
    VerifiedChannel("ahsan_tv", "Ahsan TV", "https://5bf7b725107e5.streamlock.net/ahsantv/ahsantv/playlist.m3u8", "Religi", "https://i.imgur.com/dZdUbYd.png", 7),
    VerifiedChannel("albahjah_tv", "Al-Bahjah TV", "http://202.150.161.117:8000/play/AlBahjahTV", "Religi", "https://i.imgur.com/52dlDZk.png", 8),
    VerifiedChannel("am_media", "Alwafa Tarim TV", "https://ammedia.siar.us/ammedia/live/playlist.m3u8", "Religi", "https://ammedia.siar.us/assets/img/logo.png", 9),
    VerifiedChannel("tawaf_tv", "Tawaf TV", "https://tvstreamcast.com/tawaftv.m3u8", "Religi", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/27/Tawaf_TV.png/512px-Tawaf_TV.png", 10),
    VerifiedChannel("caruban_tv", "Caruban TV", "https://stream.carubantv.id/hls/0/stream.m3u8", "Nasional", "https://i.postimg.cc/bJpyzPbB/afbtv.png", 11),
    VerifiedChannel("dhoho_tv", "Dhoho TV", "https://dhohotv.siar.us/dhohotv/live/playlist.m3u8", "Nasional", "https://dhohotv.siar.us/assets/img/logo.png", 12)
)

@OptIn(UnstableApi::class)
@Composable
fun LiveTvPortalScreen(
    repository: MediaRepository,
    onBackToPortal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentFilter by remember { mutableStateOf(LiveTvCategory.SEMUA) }
    var selectedChannel by remember { mutableStateOf<VerifiedChannel?>(VERIFIED_CHANNELS.first()) }
    var isStreamLoading by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                isStreamLoading = false
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
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

    // Play selected channel stream
    LaunchedEffect(selectedChannel) {
        val ch = selectedChannel ?: return@LaunchedEffect
        isStreamLoading = true
        try {
            val mediaItemBuilder = MediaItem.Builder().setUri(ch.streamUrl)
            if (ch.streamUrl.contains(".mpd")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            } else if (ch.streamUrl.contains(".m3u8")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            e.printStackTrace()
            isStreamLoading = false
        }
    }

    val filteredChannels = remember(currentFilter) {
        when (currentFilter) {
            LiveTvCategory.SEMUA -> VERIFIED_CHANNELS
            LiveTvCategory.NASIONAL -> VERIFIED_CHANNELS.filter { it.genre == "Nasional" }
            LiveTvCategory.RELIGI -> VERIFIED_CHANNELS.filter { it.genre == "Religi" }
        }
    }

    Scaffold(
        topBar = {
            // Dedicated TopBar for Live TV Universe
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(CanvasBlack)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .background(SurfaceElevated)
                            .clickable(onClick = onBackToPortal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke Portal",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "LIVE TV UNIVERSE",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Siaran Langsung TV Indonesia • 24/7 HD",
                            color = TextDim,
                            fontSize = 10.sp
                        )
                    }
                }

                // Portal Exit Button Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(SurfaceCharcoal)
                        .border(1.dp, BorderHairline, RoundedCornerShape(100.dp))
                        .clickable(onClick = onBackToPortal)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Portal",
                        tint = TextDim,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "PORTAL",
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        },
        bottomBar = {
            // Dedicated BottomBar for Live TV Sub-App
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .background(Color(0xE60D0D12))
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiveTvCategory.entries.forEach { cat ->
                        val isSelected = currentFilter == cat
                        val tint = if (isSelected) Color.White else TextMuted

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { currentFilter = cat }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = cat.title,
                                tint = tint,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cat.title,
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Portal Exit Tab
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBackToPortal)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Portal",
                            tint = TextDim,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Portal",
                            color = TextDim,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        },
        containerColor = CanvasBlack
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CanvasBlack)
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

                // Channel Badge overlay
                selectedChannel?.let { ch ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
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
            }

            // 2. Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Daftar Saluran (${filteredChannels.size})",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Kategori: ${currentFilter.title}",
                    color = TextDim,
                    fontSize = 12.sp
                )
            }

            // 3. Channels Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
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
                                1.5.dp,
                                if (isCurrent) Color.White else BorderHairline,
                                RoundedCornerShape(12.dp)
                            )
                            .background(if (isCurrent) SurfaceActive else SurfaceElevated)
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
}
