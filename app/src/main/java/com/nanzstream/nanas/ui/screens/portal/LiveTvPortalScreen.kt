package com.nanzstream.nanas.ui.screens.portal

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.theme.*

private enum class LiveTvCategory(val title: String, val genreTag: String, val icon: ImageVector) {
    SEMUA("Semua", "Semua", Icons.Default.LiveTv),
    NASIONAL("Nasional", "Nasional", Icons.Default.Tv),
    INTERNASIONAL("Internasional", "Internasional", Icons.Default.Public),
    HIBURAN("Hiburan & Sport", "Hiburan", Icons.Default.Movie),
    KIDS("Kids", "Kids", Icons.Default.ChildCare),
    RELIGI("Religi", "Religi", Icons.Default.Mosque)
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
    // === NASIONAL ===
    VerifiedChannel("gtv", "GTV HD", "https://cdnjktcyber05.transvision.co.id/riutx01-439abf566997b187117993103dfd8508/dash/R1RWLUNIQU5ORUw/manifest.mpd", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/GTV_%28Indonesia%29_2017_logo.svg/512px-GTV_%28Indonesia%29_2017_logo.svg.png", 1),
    VerifiedChannel("mnctv", "MNC TV HD", "https://cdnjktcyber05.transvision.co.id/riutx01-439abf566997b187117993103dfd8508/dash/TU5DVFY/manifest.mpd", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/MNCTV_logo_2020.svg/512px-MNCTV_logo_2020.svg.png", 2),
    VerifiedChannel("transtv", "Trans TV HD", "https://green-night-d2b4.iontv.workers.dev/transtv.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/14/Trans_TV_2013.svg/512px-Trans_TV_2013.svg.png", 3),
    VerifiedChannel("trans7", "Trans7 HD", "https://green-night-d2b4.iontv.workers.dev/trans7.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/66/Trans7_logo_2013.svg/512px-Trans7_logo_2013.svg.png", 4),
    VerifiedChannel("sctv", "SCTV HD", "https://op-flashcon-digdayahd-1.dens.tv/h/h217/01.m3u8?app_type=web&userid=lite&chname=SCTV", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/02/SCTV_logo_2005.svg/512px-SCTV_logo_2005.svg.png", 5),
    VerifiedChannel("indosiar", "Indosiar HD", "https://op-flashcon-digdayahd-1.dens.tv/h/h207/01.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/77/Indosiar_logo_2015.svg/512px-Indosiar_logo_2015.svg.png", 6),
    VerifiedChannel("antv", "ANTV HD", "https://op-flashcon-digdayahd-1.dens.tv/h/h235/01.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/af/ANTV_logo_2017.svg/512px-ANTV_logo_2017.svg.png", 7),
    VerifiedChannel("inews", "iNews HD", "https://live.i-news.tv/hls/stream.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8b/INews_2023.svg/512px-INews_2023.svg.png", 8),
    VerifiedChannel("kompastv", "Kompas TV HD", "https://op-flashcon-digdayahd-1.dens.tv/s/s104/index.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b3/Kompas_TV_2018.svg/512px-Kompas_TV_2018.svg.png", 9),
    VerifiedChannel("rtv", "RTV HD", "https://rtvstream.rtv.co.id:4555/hls/rtv.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7c/RTV_logo.svg/512px-RTV_logo.svg.png", 10),
    VerifiedChannel("metrotv", "Metro TV HD", "https://edge.medcom.id/live-edge/smil:metro.smil/playlist.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c2/MetroTV_2010.svg/512px-MetroTV_2010.svg.png", 11),
    VerifiedChannel("tvri_nasional", "TVRI Nasional", "https://ott-balancer.tvri.go.id/live/eds/Nasional/hls/Nasional.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/TVRI_2019.svg/512px-TVRI_2019.svg.png", 12),
    VerifiedChannel("tvri_world", "TVRI World", "https://ott-balancer.tvri.go.id/live/eds/TVRIWorld/hls/TVRIWorld.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/63/TVRI_World_2019.svg/512px-TVRI_World_2019.svg.png", 13),
    VerifiedChannel("tvri_dki", "TVRI Jakarta", "https://ott-balancer.tvri.go.id/live/eds/DKI/hls/DKI.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/53/TVRI_Jakarta.svg/512px-TVRI_Jakarta.svg.png", 14),
    VerifiedChannel("tvri_jabar", "TVRI Jawa Barat", "https://ott-balancer.tvri.go.id/live/eds/Jabar/hls/Jabar.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/TVRI_Jawa_Barat.svg/512px-TVRI_Jawa_Barat.svg.png", 15),
    VerifiedChannel("tvri_jatim", "TVRI Jawa Timur", "https://ott-balancer.tvri.go.id/live/eds/Jatim/hls/Jatim.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/15/TVRI_Jawa_Timur.svg/512px-TVRI_Jawa_Timur.svg.png", 16),
    VerifiedChannel("daai_tv", "DAAI TV HD", "https://pull.daaiplus.com/live-DAAIPLUS/live-DAAIPLUS_HD.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/DAAI_TV_logo.svg/512px-DAAI_TV_logo.svg.png", 17),
    VerifiedChannel("garuda_tv", "Garuda TV", "https://op-flashcon-digdayahd-1.dens.tv/h/h10/01.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ae/Garuda_TV.png/512px-Garuda_TV.png", 18),
    VerifiedChannel("magna_channel", "MAGNA Channel", "https://edge.medcom.id/live-edge/smil:magna.smil/chunklist_w521170343_b1128000_sleng.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/40/Magna_Channel.svg/512px-Magna_Channel.svg.png", 19),
    VerifiedChannel("bn_channel", "BN Channel", "https://flv.intechmedia.net/live/ch112.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/id/thumb/2/25/BN_Channel.png/512px-BN_Channel.png", 20),
    VerifiedChannel("jawapos_tv", "Jawa Pos TV", "http://122.248.43.242:1935/JAWAPOSTVJKT/_definst_/myStream/playlist.m3u8", "Nasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3c/Jawa_Pos_TV_logo.svg/512px-Jawa_Pos_TV_logo.svg.png", 21),
    VerifiedChannel("banjar_tv", "Banjar TV", "https://banjartv.siar.us/banjartv/live/playlist.m3u8", "Nasional", "https://banjartv.siar.us/assets/img/logo.png", 22),
    VerifiedChannel("caruban_tv", "Caruban TV", "https://stream.carubantv.id/hls/0/stream.m3u8", "Nasional", "https://i.postimg.cc/bJpyzPbB/afbtv.png", 23),
    VerifiedChannel("dhoho_tv", "Dhoho TV", "https://dhohotv.siar.us/dhohotv/live/playlist.m3u8", "Nasional", "https://dhohotv.siar.us/assets/img/logo.png", 24),

    // === INTERNASIONAL ===
    VerifiedChannel("nhk_world", "NHK World Japan HD", "https://media-tyo.hls.nhkworld.jp/hls/w/live/master.m3u8", "Internasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/79/NHK_World-Japan.svg/512px-NHK_World-Japan.svg.png", 25),
    VerifiedChannel("cna_asia", "CNA (Channel News Asia)", "https://amg01082-cna-amg01082c1-rlaxx-us-11304.playouts.now.amagi.tv/playlist.m3u8", "Internasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/CNA_%28TV_network%29_logo.svg/512px-CNA_%28TV_network%29_logo.svg.png", 26),
    VerifiedChannel("bloomberg", "Bloomberg Originals HD", "https://bloomberg.com/media-manifest/streams/qt.m3u8", "Internasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/56/Bloomberg_Television_logo.svg/512px-Bloomberg_Television_logo.svg.png", 27),
    VerifiedChannel("dw_english", "DW English HD", "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8", "Internasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Deutsche_Welle_Logo.svg/512px-Deutsche_Welle_Logo.svg.png", 28),
    VerifiedChannel("dw_deutsch", "DW Deutsch HD", "https://dwamdstream104.akamaized.net/hls/live/2015530/dwstream104/index.m3u8", "Internasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Deutsche_Welle_Logo.svg/512px-Deutsche_Welle_Logo.svg.png", 29),
    VerifiedChannel("arirang_tv", "Arirang TV Korea HD", "https://amdlive-ch01-ctnd-com.akamaized.net/arirang_1ch/smil:arirang_1ch.smil/playlist.m3u8", "Internasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ae/Arirang_TV_logo.svg/512px-Arirang_TV_logo.svg.png", 30),
    VerifiedChannel("trt_world", "TRT World HD", "https://tv-trtworld.medya.trt.com.tr/master.m3u8", "Internasional", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/TRT_World_logo.svg/512px-TRT_World_logo.svg.png", 31),

    // === HIBURAN & OLAHRAGA ===
    VerifiedChannel("bein_sports", "beIN Sports XTRA", "https://bein-xtra-bein.amagi.tv/playlist.m3u8", "Hiburan", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b3/BeIN_Sports_logo.svg/512px-BeIN_Sports_logo.svg.png", 32),
    VerifiedChannel("fox_sports", "FOX Sports HD", "https://d1jzu95oc8fgt3.cloudfront.net/FOX_Sports.m3u8", "Hiburan", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/be/Fox_Sports_logo.svg/512px-Fox_Sports_logo.svg.png", 33),
    VerifiedChannel("cbs_sports", "CBS Sports HQ", "https://stitcher-ipv4.pluto.tv/v1/stitch/embed/hls/channel/5d8a98f1f7d451cb5fa0b463/master.m3u8", "Hiburan", "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/CBS_Sports_HQ_logo.svg/512px-CBS_Sports_HQ_logo.svg.png", 34),
    VerifiedChannel("redbull_tv", "Red Bull TV HD", "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8", "Hiburan", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f5/Red_Bull_TV_logo.svg/512px-Red_Bull_TV_logo.svg.png", 35),
    VerifiedChannel("bbc_earth", "BBC Earth HD", "https://amg00793-amg00793c6-xumo-us-2669.playouts.now.amagi.tv/BBCStudios-BBCEarthA-hls/playlist.m3u8", "Hiburan", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4f/BBC_Earth_logo.svg/512px-BBC_Earth_logo.svg.png", 36),
    VerifiedChannel("kdrama_plus", "K-Drama+ 24/7", "https://stream-us-east-1.getpublica.com/playlist.m3u8?network_id=425", "Hiburan", "https://i.imgur.com/xO8vG4u.png", 37),
    VerifiedChannel("kmovies", "NEW KMOVIES HD", "https://stream-us-east-1.getpublica.com/playlist.m3u8?network_id=7737", "Hiburan", "https://i.imgur.com/8QG3X7T.png", 38),
    VerifiedChannel("action_movies", "Movies Action HD", "https://shd-amg-fast.edgenextcdn.net/tx011/playlist.m3u8", "Hiburan", "https://i.imgur.com/Y3aA5W4.png", 39),
    VerifiedChannel("thriller_movies", "Movies Thriller HD", "https://shd-amg-fast.edgenextcdn.net/tx012/playlist.m3u8", "Hiburan", "https://i.imgur.com/2s4PsmU.png", 40),
    VerifiedChannel("anime_retro", "Anime Retro Channel", "https://2-fss-2.streamhoster.com/pl_138/205510-3094608-1/playlist.m3u8", "Hiburan", "https://i.imgur.com/5XhFp7Z.png", 41),

    // === KIDS ===
    VerifiedChannel("disney_channel", "Disney Channel HD", "http://15.204.246.24:8080/DisneyHD/index.m3u8", "Kids", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d2/2019_Disney_Channel_logo.svg/512px-2019_Disney_Channel_logo.svg.png", 42),
    VerifiedChannel("disney_junior", "Disney Junior HD", "http://190.93.224.42/DISNEY-JR/index.m3u8", "Kids", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Disney_Junior_logo.svg/512px-Disney_Junior_logo.svg.png", 43),

    // === RELIGI ===
    VerifiedChannel("ahsan_tv", "Ahsan TV", "https://5bf7b725107e5.streamlock.net/ahsantv/ahsantv/playlist.m3u8", "Religi", "https://i.imgur.com/dZdUbYd.png", 44),
    VerifiedChannel("alwafa_tv", "Alwafa Tarim TV", "https://ammedia.siar.us/ammedia/live/playlist.m3u8", "Religi", "https://ammedia.siar.us/assets/img/logo.png", 45),
    VerifiedChannel("tawaf_tv", "Tawaf TV", "https://tvstreamcast.com/tawaftv.m3u8", "Religi", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/27/Tawaf_TV.png/512px-Tawaf_TV.png", 46),
    VerifiedChannel("salam_tv", "Salam TV", "https://live.salamtelevisi.com/hls/0/stream.m3u8", "Religi", "https://live.salamtelevisi.com/wp-content/uploads/2021/04/cropped-Logo-Salam-TV-1-1.png", 47)
)

@OptIn(UnstableApi::class)
@Composable
fun LiveTvPortalScreen(
    repository: MediaRepository,
    onBackToPortal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var currentFilter by remember { mutableStateOf(LiveTvCategory.SEMUA) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedChannel by remember { mutableStateOf<VerifiedChannel?>(VERIFIED_CHANNELS.first()) }
    var isStreamLoading by remember { mutableStateOf(false) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

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
                streamError = "Gagal memuat siaran live. Coba lagi atau pilih saluran lain."
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isStreamLoading = false
                        streamError = null
                    }
                    Player.STATE_BUFFERING -> {
                        isStreamLoading = true
                    }
                    Player.STATE_ENDED -> {
                        isStreamLoading = false
                    }
                    Player.STATE_IDLE -> {
                        isStreamLoading = false
                    }
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
    LaunchedEffect(selectedChannel, reloadTrigger) {
        val ch = selectedChannel ?: return@LaunchedEffect
        isStreamLoading = true
        streamError = null
        try {
            val mediaItemBuilder = MediaItem.Builder().setUri(ch.streamUrl)
            if (ch.streamUrl.contains(".mpd")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            } else if (ch.streamUrl.contains(".m3u8")) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            exoPlayer.stop()
            exoPlayer.setMediaItem(mediaItemBuilder.build())
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            e.printStackTrace()
            isStreamLoading = false
            streamError = "Error: ${e.message}"
        }
    }

    val filteredChannels = remember(currentFilter, searchQuery) {
        VERIFIED_CHANNELS.filter { ch ->
            val matchesCategory = when (currentFilter) {
                LiveTvCategory.SEMUA -> true
                else -> ch.genre.equals(currentFilter.genreTag, ignoreCase = true)
            }
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                ch.name.contains(searchQuery, ignoreCase = true) ||
                ch.genre.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
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
                            text = "Nasional & Internasional • 24/7 HD",
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
        containerColor = CanvasBlack
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CanvasBlack)
        ) {
            // 1. Live Player View (16:9 responsive display)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Menghubungkan siaran...",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Stream Error Banner Overlay
                if (streamError != null && !isStreamLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xCC000000))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = "Error",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = streamError ?: "Gagal memutar siaran",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2563EB))
                                    .clickable { reloadTrigger++ }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Coba Lagi",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Channel Badge overlay (top-left)
                selectedChannel?.let { ch ->
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE • ${ch.name} (CH ${ch.number})",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .background(SurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextDim,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Cari saluran (contoh: GTV, Trans, Disney, beIN)...",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextDim,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }

            // 3. Category Horizontal Pills Filter
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                items(LiveTvCategory.entries) { cat ->
                    val isSelected = currentFilter == cat
                    val count = if (cat == LiveTvCategory.SEMUA) {
                        VERIFIED_CHANNELS.size
                    } else {
                        VERIFIED_CHANNELS.count { it.genre.equals(cat.genreTag, ignoreCase = true) }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .border(
                                1.dp,
                                if (isSelected) Color.White else BorderHairline,
                                RoundedCornerShape(100.dp)
                            )
                            .background(if (isSelected) Color.White else SurfaceElevated)
                            .clickable { currentFilter = cat }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = cat.title,
                            tint = if (isSelected) Color.Black else TextDim,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${cat.title} ($count)",
                            color = if (isSelected) Color.Black else TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // 4. Section Count Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Daftar Saluran (${filteredChannels.size})",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (searchQuery.isNotBlank()) "Pencarian: '$searchQuery'" else "Kategori: ${currentFilter.title}",
                    color = TextDim,
                    fontSize = 11.sp
                )
            }

            // 5. Channels Grid or Empty State
            if (filteredChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Saluran tidak ditemukan",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Coba kata kunci pencarian lain atau ganti kategori",
                            color = TextDim,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = { searchQuery = "" }) {
                            Text("Reset Pencarian", color = Color.White)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp, top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredChannels, key = { it.id }) { ch ->
                        val isCurrent = selectedChannel?.id == ch.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.5.dp,
                                    if (isCurrent) Color(0xFF38BDF8) else BorderHairline,
                                    RoundedCornerShape(12.dp)
                                )
                                .background(if (isCurrent) SurfaceActive else SurfaceElevated)
                                .clickable { selectedChannel = ch }
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
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

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ch.name,
                                        color = if (isCurrent) Color(0xFF38BDF8) else TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCurrent) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF22C55E))
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "PLAYING",
                                                color = Color(0xFF22C55E),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        } else {
                                            Text(
                                                text = "CH ${ch.number} • ${ch.genre}",
                                                color = TextMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
