package com.nanzstream.nanas.ui.screens.portal

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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nanzstream.nanas.LocalIsInPipMode
import com.nanzstream.nanas.R
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.theme.*
import kotlinx.coroutines.delay

private fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun enterPipMode(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = context.findActivity() ?: return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        activity.enterPictureInPictureMode(params)
    }
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
    val backupUrl: String? = null,
    val genre: String,
    val logoRes: Int,
    val number: Int
)

private val VERIFIED_CHANNELS = listOf(
    // === NASIONAL (TV Populer Indonesia) ===
    VerifiedChannel(
        id = "gtv",
        name = "GTV HD",
        streamUrl = "http://hometv.biz.id:80/play/-3G1b2ud-O59f7x_ScusEw",
        backupUrl = "http://hometv.biz.id:80/play/KrVYNUtc63yvQKQCwg8miHqQ4_EdcTLriwuH5Hq6Ngh6Cq6OmchTWJed4cF8PLO8",
        genre = "Nasional",
        logoRes = R.drawable.ch_gtv,
        number = 1
    ),
    VerifiedChannel(
        id = "mnctv",
        name = "MNC TV HD",
        streamUrl = "http://hometv.biz.id:80/play/1fxSpuJb44YPc2Gs5H_nOw",
        backupUrl = "http://hometv.biz.id:80/play/KrVYNUtc63yvQKQCwg8miHqQ4_EdcTLriwuH5Hq6NgicGCOcnuwUnXooTDu7lj6R",
        genre = "Nasional",
        logoRes = R.drawable.ch_mnctv,
        number = 2
    ),
    VerifiedChannel(
        id = "rcti",
        name = "RCTI HD",
        streamUrl = "http://hometv.biz.id:80/play/FgEpb2OcjoJ1Zpi78OicBw",
        backupUrl = "http://hometv.biz.id:80/play/KrVYNUtc63yvQKQCwg8miHqQ4_EdcTLriwuH5Hq6Ngj0fHpRbhNUS-OX-_Lp8lZi",
        genre = "Nasional",
        logoRes = R.drawable.ch_rcti,
        number = 3
    ),
    VerifiedChannel(
        id = "sctv",
        name = "SCTV HD",
        streamUrl = "http://hometv.biz.id:80/play/ncQ61p33CjA2O64BU5S1Yw",
        backupUrl = "http://filex.me:8080/akkvdGtMUWkvVnMvaWx3V2hXa2NacE9Ra0g0dTlhc29keDE1OHU4Vm0zV3MvcU5CUjJCSWZTR1FJRnF2VXEyQQ",
        genre = "Nasional",
        logoRes = R.drawable.ch_sctv,
        number = 4
    ),
    VerifiedChannel(
        id = "indosiar",
        name = "Indosiar HD",
        streamUrl = "http://hometv.biz.id:80/play/0r_OP_D-Uq2ONHZBG7-N4g",
        backupUrl = "http://filex.me:8080/akkvdGtMUWkvVnMvaWx3V2hXa2NaZ0x0ZXFPbysvMVBvYjc1eXFMemc1a3UxOWR6NmYxUTllMi80N1IzM2tGWQ",
        genre = "Nasional",
        logoRes = R.drawable.ch_indosiar,
        number = 5
    ),
    VerifiedChannel(
        id = "inews",
        name = "iNews HD",
        streamUrl = "https://live.i-news.tv/hls/1/stream.m3u8",
        backupUrl = "http://hometv.biz.id:80/play/qOz6vuu0J_sIrtobe3YdWg",
        genre = "Nasional",
        logoRes = R.drawable.ch_inews,
        number = 6
    ),
    VerifiedChannel("transtv", "Trans TV HD", "https://green-night-d2b4.iontv.workers.dev/transtv.m3u8", null, "Nasional", R.drawable.ch_transtv, 7),
    VerifiedChannel("trans7", "Trans7 HD", "https://green-night-d2b4.iontv.workers.dev/trans7.m3u8", null, "Nasional", R.drawable.ch_trans7, 8),
    VerifiedChannel(
        id = "antv",
        name = "ANTV HD",
        streamUrl = "https://dusk.biz.id/vinteo-lokal/prodeo.m3u8?id=782&type=hls",
        backupUrl = "https://flv.intechmedia.net/live/ch107.m3u8",
        genre = "Nasional",
        logoRes = R.drawable.ch_antv,
        number = 9
    ),
    VerifiedChannel("kompastv", "Kompas TV HD", "https://op-flashcon-digdayahd-1.dens.tv/s/s104/index.m3u8", null, "Nasional", R.drawable.ch_kompastv, 10),
    VerifiedChannel("rtv", "RTV HD", "https://rtvstream.rtv.co.id:4555/hls/rtv.m3u8", null, "Nasional", R.drawable.ch_rtv, 11),
    VerifiedChannel("metrotv", "Metro TV HD", "https://edge.medcom.id/live-edge/smil:metro.smil/playlist.m3u8", null, "Nasional", R.drawable.ch_metrotv, 12),
    VerifiedChannel("tvri_nasional", "TVRI Nasional", "https://ott-balancer.tvri.go.id/live/eds/Nasional/hls/Nasional.m3u8", null, "Nasional", R.drawable.ch_tvri, 13),
    VerifiedChannel("tvri_world", "TVRI World", "https://ott-balancer.tvri.go.id/live/eds/TVRIWorld/hls/TVRIWorld.m3u8", null, "Nasional", R.drawable.ch_tvri, 14),
    VerifiedChannel("tvri_dki", "TVRI Jakarta", "https://ott-balancer.tvri.go.id/live/eds/DKI/hls/DKI.m3u8", null, "Nasional", R.drawable.ch_tvri, 15),
    VerifiedChannel("tvri_jabar", "TVRI Jawa Barat", "https://ott-balancer.tvri.go.id/live/eds/Jabar/hls/Jabar.m3u8", null, "Nasional", R.drawable.ch_tvri, 16),
    VerifiedChannel("tvri_jatim", "TVRI Jawa Timur", "https://ott-balancer.tvri.go.id/live/eds/Jatim/hls/Jatim.m3u8", null, "Nasional", R.drawable.ch_tvri, 17),
    VerifiedChannel("daai_tv", "DAAI TV HD", "https://pull.daaiplus.com/live-DAAIPLUS/live-DAAIPLUS_HD.m3u8", null, "Nasional", R.drawable.ch_daaitv, 18),
    VerifiedChannel("garuda_tv", "Garuda TV", "https://op-flashcon-digdayahd-1.dens.tv/h/h10/01.m3u8", null, "Nasional", R.drawable.ch_garudatv, 19),
    VerifiedChannel("magna_channel", "MAGNA Channel", "https://edge.medcom.id/live-edge/smil:magna.smil/chunklist_w521170343_b1128000_sleng.m3u8", null, "Nasional", R.drawable.ch_metrotv, 20),
    VerifiedChannel("bn_channel", "BN Channel", "https://flv.intechmedia.net/live/ch112.m3u8", null, "Nasional", R.drawable.ch_metrotv, 21),
    VerifiedChannel("jawapos_tv", "Jawa Pos TV", "http://122.248.43.242:1935/JAWAPOSTVJKT/_definst_/myStream/playlist.m3u8", null, "Nasional", R.drawable.ch_jawapostv, 22),
    VerifiedChannel("banjar_tv", "Banjar TV", "https://banjartv.siar.us/banjartv/live/playlist.m3u8", null, "Nasional", R.drawable.ch_jawapostv, 23),
    VerifiedChannel("caruban_tv", "Caruban TV", "https://stream.carubantv.id/hls/0/stream.m3u8", null, "Nasional", R.drawable.ch_tvri, 24),
    VerifiedChannel("dhoho_tv", "Dhoho TV", "https://dhohotv.siar.us/dhohotv/live/playlist.m3u8", null, "Nasional", R.drawable.ch_jawapostv, 25),

    // === INTERNASIONAL ===
    VerifiedChannel("nhk_world", "NHK World Japan HD", "https://media-tyo.hls.nhkworld.jp/hls/w/live/master.m3u8", null, "Internasional", R.drawable.ch_nhkworld, 26),
    VerifiedChannel("cna_asia", "CNA (Channel News Asia)", "https://amg01082-cna-amg01082c1-rlaxx-us-11304.playouts.now.amagi.tv/playlist.m3u8", null, "Internasional", R.drawable.ch_cna, 27),
    VerifiedChannel("bloomberg", "Bloomberg Originals HD", "https://bloomberg.com/media-manifest/streams/qt.m3u8", null, "Internasional", R.drawable.ch_bloomberg, 28),
    VerifiedChannel("dw_english", "DW English HD", "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8", null, "Internasional", R.drawable.ch_dw, 29),
    VerifiedChannel("dw_deutsch", "DW Deutsch HD", "https://dwamdstream104.akamaized.net/hls/live/2015530/dwstream104/index.m3u8", null, "Internasional", R.drawable.ch_dw, 30),
    VerifiedChannel("arirang_tv", "Arirang TV Korea HD", "https://amdlive-ch01-ctnd-com.akamaized.net/arirang_1ch/smil:arirang_1ch.smil/playlist.m3u8", null, "Internasional", R.drawable.ch_arirang, 31),
    VerifiedChannel("trt_world", "TRT World HD", "https://tv-trtworld.medya.trt.com.tr/master.m3u8", null, "Internasional", R.drawable.ch_trt, 32),

    // === HIBURAN & OLAHRAGA ===
    VerifiedChannel("bein_sports", "beIN Sports XTRA", "https://bein-xtra-bein.amagi.tv/playlist.m3u8", null, "Hiburan", R.drawable.ch_sports, 33),
    VerifiedChannel("fox_sports", "FOX Sports HD", "https://d1jzu95oc8fgt3.cloudfront.net/FOX_Sports.m3u8", null, "Hiburan", R.drawable.ch_sports, 34),
    VerifiedChannel("redbull_tv", "Red Bull TV HD", "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8", null, "Hiburan", R.drawable.ch_sports, 35),
    VerifiedChannel("bbc_earth", "BBC Earth HD", "https://amg00793-amg00793c6-xumo-us-2669.playouts.now.amagi.tv/BBCStudios-BBCEarthA-hls/playlist.m3u8", null, "Hiburan", R.drawable.ch_bbcearth, 36),
    VerifiedChannel("kdrama_plus", "K-Drama+ 24/7", "https://stream-us-east-1.getpublica.com/playlist.m3u8?network_id=425", null, "Hiburan", R.drawable.ch_kmovies, 37),
    VerifiedChannel("kmovies", "NEW KMOVIES HD", "https://stream-us-east-1.getpublica.com/playlist.m3u8?network_id=7737", null, "Hiburan", R.drawable.ch_kmovies, 38),
    VerifiedChannel("action_movies", "Movies Action HD", "https://shd-amg-fast.edgenextcdn.net/tx011/playlist.m3u8", null, "Hiburan", R.drawable.ch_action, 39),
    VerifiedChannel("thriller_movies", "Movies Thriller HD", "https://shd-amg-fast.edgenextcdn.net/tx012/playlist.m3u8", null, "Hiburan", R.drawable.ch_action, 40),
    VerifiedChannel("anime_retro", "Anime Retro Channel", "https://2-fss-2.streamhoster.com/pl_138/205510-3094608-1/playlist.m3u8", null, "Hiburan", R.drawable.ch_animax, 41),

    // === KIDS ===
    VerifiedChannel("cartoon_network", "Cartoon Network HD", "https://shls-cartoon-net-prod-dub.shahid.net/out/v1/dc4aa87372374325a66be458f29eab0f/index.m3u8", null, "Kids", R.drawable.ch_cartoon, 42),
    VerifiedChannel("baby_shark", "Baby Shark TV HD", "https://newidco-babysharktv-1-us.roku.wurl.tv/playlist.m3u8", null, "Kids", R.drawable.ch_kids, 43),
    VerifiedChannel("moonbug_kids", "Moonbug Kids HD", "https://moonbug-rokuus.amagi.tv/playlist.m3u8", null, "Kids", R.drawable.ch_kids, 44),
    VerifiedChannel("toon_goggles", "Toon Goggles Kids", "https://amg01329-otterainc-toongoggles-samsungau-ad-4c.amagi.tv/playlist/amg01329-otterainc-toongoggles-samsungau/playlist.m3u8", null, "Kids", R.drawable.ch_cartoon, 45),
    VerifiedChannel("mojitv_cartoon", "MojiTV Cartoon", "https://odmedia-mojitv-1-be.samsung.wurl.tv/playlist.m3u8", null, "Kids", R.drawable.ch_kids, 46),
    VerifiedChannel("kidsflix", "KidsFlix 24/7", "https://stream-us-east-1.getpublica.com/playlist.m3u8?network_id=50", null, "Kids", R.drawable.ch_cartoon, 47),
    VerifiedChannel("vtv_digital", "VTV Digital (ANTV/VIVA)", "https://flv.intechmedia.net/live/ch107.m3u8", null, "Kids", R.drawable.ch_antv, 48),

    // === RELIGI ===
    VerifiedChannel("ahsan_tv", "Ahsan TV", "https://5bf7b725107e5.streamlock.net/ahsantv/ahsantv/playlist.m3u8", null, "Religi", R.drawable.ch_religi, 49),
    VerifiedChannel("alwafa_tv", "Alwafa Tarim TV", "https://ammedia.siar.us/ammedia/live/playlist.m3u8", null, "Religi", R.drawable.ch_religi, 50),
    VerifiedChannel("tawaf_tv", "Tawaf TV", "https://tvstreamcast.com/tawaftv.m3u8", null, "Religi", R.drawable.ch_tawaf, 51),
    VerifiedChannel("salam_tv", "Salam TV", "https://live.salamtelevisi.com/hls/0/stream.m3u8", null, "Religi", R.drawable.ch_religi, 52)
)

@OptIn(UnstableApi::class)
@Composable
fun LiveTvPortalScreen(
    repository: MediaRepository,
    onBackToPortal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isInPipMode = LocalIsInPipMode.current

    var currentFilter by remember { mutableStateOf(LiveTvCategory.SEMUA) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedChannel by remember { mutableStateOf<VerifiedChannel?>(VERIFIED_CHANNELS.first()) }
    var useBackupServer by remember { mutableStateOf(false) }
    var isStreamLoading by remember { mutableStateOf(false) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // Live TV Feature States: Fullscreen, Controls Auto-hide, Resize Mode, Play/Pause
    var isFullscreen by remember { mutableStateOf(false) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var controlsTrigger by remember { mutableIntStateOf(0) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(httpDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 2_000,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
            }
    }

    // Stop background audio playback when app is paused/stopped (except in PiP mode)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity?.isInPictureInPictureMode == true
            } else false

            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    if (!inPip) {
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (!inPip && !exoPlayer.isPlaying) {
                        if (exoPlayer.isCurrentMediaItemLive) {
                            exoPlayer.seekToDefaultPosition()
                        }
                        exoPlayer.play()
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

    // Auto-hide controls (channel badge, server switch, play/pause, player buttons) after 3 seconds of inactivity
    LaunchedEffect(showPlayerControls, controlsTrigger) {
        if (showPlayerControls) {
            delay(3000L)
            showPlayerControls = false
        }
    }

    // Watchdog to auto-recover when live stream gets stuck buffering > 10 seconds
    LaunchedEffect(isStreamLoading) {
        if (isStreamLoading) {
            delay(10000L)
            if (isStreamLoading && exoPlayer.playbackState == Player.STATE_BUFFERING) {
                try {
                    if (exoPlayer.isCurrentMediaItemLive) {
                        exoPlayer.seekToDefaultPosition()
                    }
                    exoPlayer.prepare()
                    exoPlayer.play()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Handle back button when in fullscreen mode
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        setSystemFullscreen(activity, false)
    }

    // Player error and state listeners
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()

                // If playback fell behind sliding live window, jump back to current live edge and resume
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    exoPlayer.play()
                    return
                }

                isStreamLoading = false

                // Automatic failover to backup URL if Server 1 encountered an issue
                val ch = selectedChannel
                if (!useBackupServer && ch?.backupUrl != null) {
                    useBackupServer = true
                } else {
                    streamError = "Gagal memuat siaran live. Coba server cadangan atau pilih saluran lain."
                }
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
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        isStreamLoading = false
                    }
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

    // Play selected channel stream
    LaunchedEffect(selectedChannel, useBackupServer, reloadTrigger) {
        val ch = selectedChannel ?: return@LaunchedEffect
        isStreamLoading = true
        streamError = null
        showPlayerControls = true
        try {
            val targetUrl = if (useBackupServer && !ch.backupUrl.isNullOrBlank()) ch.backupUrl else ch.streamUrl
            val mediaItemBuilder = MediaItem.Builder().setUri(targetUrl)

            // Configure live stream auto-catch-up
            val liveConfig = MediaItem.LiveConfiguration.Builder()
                .setMaxPlaybackSpeed(1.02f)
                .setMinPlaybackSpeed(0.98f)
                .build()
            mediaItemBuilder.setLiveConfiguration(liveConfig)

            when {
                targetUrl.contains(".mpd") -> {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                }
                targetUrl.contains(".m3u8") -> {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                targetUrl.contains("/play/") || targetUrl.contains("filex.me") || targetUrl.contains(".ts") -> {
                    mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
                }
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

    // 0. Dedicated PiP Viewport (Pure video filling floating window)
    if (isInPipMode) {
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
                        this.resizeMode = resizeMode
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Scaffold(
        topBar = {
            // Hide TopBar completely if Fullscreen is enabled
            if (!isFullscreen) {
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
            }
        },
        containerColor = CanvasBlack
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
                .background(CanvasBlack)
        ) {
            // 1. Live Video Player View
            Box(
                modifier = if (isFullscreen) {
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showPlayerControls = !showPlayerControls
                        if (showPlayerControls) {
                            controlsTrigger++
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            this.resizeMode = resizeMode
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        view.resizeMode = resizeMode
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
                                text = if (useBackupServer) "Menghubungkan ke Server Cadangan..." else "Menghubungkan siaran...",
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                                if (selectedChannel?.backupUrl != null) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF059669))
                                            .clickable { useBackupServer = !useBackupServer }
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (useBackupServer) "Ganti Server 1" else "Ganti Server 2",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Controls Overlay with 3-Second Auto-Hide, Center Play/Pause, and Fullscreen Controls
                LiveTvControlsOverlay(
                    visible = showPlayerControls,
                    isPlaying = isPlaying,
                    selectedChannel = selectedChannel,
                    useBackupServer = useBackupServer,
                    resizeMode = resizeMode,
                    isFullscreen = isFullscreen,
                    onPlayPauseClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.isCurrentMediaItemLive) {
                                exoPlayer.seekToDefaultPosition()
                            }
                            exoPlayer.play()
                        }
                        controlsTrigger++
                    },
                    onToggleServer = {
                        useBackupServer = !useBackupServer
                        controlsTrigger++
                    },
                    onCycleAspectRatio = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        controlsTrigger++
                    },
                    onEnterPip = {
                        enterPipMode(context)
                    },
                    onToggleFullscreen = {
                        isFullscreen = !isFullscreen
                        setSystemFullscreen(activity, isFullscreen)
                        controlsTrigger++
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // If Fullscreen is active, hide everything below the player!
            if (!isFullscreen) {
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
                                    text = "Cari saluran (contoh: GTV, SCTV, Disney, beIN)...",
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
                                    .clickable {
                                        if (selectedChannel?.id != ch.id) {
                                            useBackupServer = false
                                            selectedChannel = ch
                                        }
                                    }
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
                                        Image(
                                            painter = painterResource(id = ch.logoRes),
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
                                                    text = if (useBackupServer) "LIVE • SVR 2" else "PLAYING",
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
}

@Composable
private fun LiveTvControlsOverlay(
    visible: Boolean,
    isPlaying: Boolean,
    selectedChannel: VerifiedChannel?,
    useBackupServer: Boolean,
    resizeMode: Int,
    isFullscreen: Boolean,
    onPlayPauseClick: () -> Unit,
    onToggleServer: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onEnterPip: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Top-Left: Channel Name badge & Live status (+ Back button in Fullscreen)
            selectedChannel?.let { ch ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFullscreen) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC000000))
                                .border(1.dp, Color(0x66FFFFFF), CircleShape)
                                .clickable(onClick = onToggleFullscreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
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
                        if (ch.backupUrl != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (useBackupServer) "• SVR 2" else "• SVR 1",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Top-Right: Server Switcher Button
            selectedChannel?.let { ch ->
                if (ch.backupUrl != null) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(Color(0xCC1E293B))
                            .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(100.dp))
                            .clickable(onClick = onToggleServer)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Switch Server",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (useBackupServer) "Server 2 (Cadangan)" else "Server 1",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Center: Large Play / Pause Button
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .border(1.5.dp, Color(0x66FFFFFF), CircleShape)
                    .clickable(onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Bottom-Right: Advanced Player Controls (Aspect Ratio, PiP, Fullscreen)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aspect Ratio Toggle Button (FIT, ZOOM, FILL)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .border(1.dp, Color(0x66FFFFFF), CircleShape)
                        .clickable(onClick = onCycleAspectRatio),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "FIT"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ZOOM"
                            else -> "FILL"
                        },
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Picture-in-Picture (PiP) Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .border(1.dp, Color(0x66FFFFFF), CircleShape)
                        .clickable(onClick = onEnterPip),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "PiP Mode",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Fullscreen Toggle Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000))
                        .border(1.dp, Color(0x66FFFFFF), CircleShape)
                        .clickable(onClick = onToggleFullscreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
