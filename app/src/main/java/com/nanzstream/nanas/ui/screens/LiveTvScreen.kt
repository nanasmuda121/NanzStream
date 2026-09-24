package com.nanzstream.nanas.ui.screens

import android.view.ViewGroup
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
    var channels by remember { mutableStateOf<List<LiveTvChannelItem>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<LiveTvChannelItem?>(null) }
    var selectedGenre by remember { mutableStateOf("Semua") }
    var isStreamLoading by remember { mutableStateOf(false) }

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

    // Fetch Channel list
    LaunchedEffect(Unit) {
        channels = repository.getLiveTvChannels()
        if (channels.isNotEmpty()) {
            selectedChannel = channels.first()
        }
    }

    // Play selected channel stream
    LaunchedEffect(selectedChannel) {
        val ch = selectedChannel ?: return@LaunchedEffect
        isStreamLoading = true
        try {
            val streamRes = repository.getStream(CategoryType.LIVETV, ch.slug ?: ch.id)
            val streamUrl = streamRes?.directHlsUrl
            if (!streamUrl.isNullOrEmpty()) {
                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isStreamLoading = false
        }
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

            // Channel Title Pill floating on player
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
