package com.nanzstream.nanas.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.nanzstream.nanas.NanzStreamApp
import com.nanzstream.nanas.crypto.MangaDecryptor
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.ContinueWatchingItem
import com.nanzstream.nanas.data.model.MangaChapterItem
import com.nanzstream.nanas.data.model.MangaPageItem
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.theme.*
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MangaReaderScreen(
    mangaId: String,
    initialChapterId: String,
    chapterTitle: String,
    repository: MediaRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var currentChapterId by remember { mutableStateOf(initialChapterId) }
    var activeChapterTitle by remember { mutableStateOf(chapterTitle) }
    var chapters by remember { mutableStateOf<List<MangaChapterItem>>(emptyList()) }
    var pages by remember { mutableStateOf<List<MangaPageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var useWebReader by remember { mutableStateOf(false) }

    var isDownloaded by remember(currentChapterId) {
        mutableStateOf(NanzStreamApp.offlineManga.isChapterDownloaded(currentChapterId))
    }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf("") }

    // Fetch chapters list for previous/next chapter navigation
    LaunchedEffect(mangaId, currentChapterId) {
        val targetMangaId = mangaId.ifBlank {
            val slugFromCh = currentChapterId.trimEnd('/').substringAfterLast('/')
                .substringBefore("-chapter-")
                .substringBefore("-ch-")
            if (slugFromCh.isNotBlank()) slugFromCh else "1"
        }
        if (targetMangaId.isNotBlank() && chapters.isEmpty()) {
            try {
                val detail = repository.getDetail(CategoryType.MANGA, targetMangaId)
                if (detail != null && detail.chapters.isNotEmpty()) {
                    chapters = detail.chapters
                    val currentCh = detail.chapters.find { it.id == currentChapterId }
                    if (currentCh != null && (activeChapterTitle.isBlank() || activeChapterTitle == "Komik")) {
                        activeChapterTitle = currentCh.title
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Determine current index and previous / next chapters
    val currentIdx = chapters.indexOfFirst { it.id == currentChapterId }
    val isDescending = remember(chapters) {
        if (chapters.size >= 2) {
            val num0 = Regex("""\b(\d+)\b""").find(chapters[0].subtitle ?: chapters[0].title)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val num1 = Regex("""\b(\d+)\b""").find(chapters[1].subtitle ?: chapters[1].title)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            num0 > num1
        } else true
    }

    val prevChapter = if (currentIdx != -1) {
        if (isDescending) chapters.getOrNull(currentIdx + 1) else chapters.getOrNull(currentIdx - 1)
    } else null

    val nextChapter = if (currentIdx != -1) {
        if (isDescending) chapters.getOrNull(currentIdx - 1) else chapters.getOrNull(currentIdx + 1)
    } else null

    // Track current visible page
    val currentPageNumber by remember {
        derivedStateOf {
            if (pages.isEmpty()) 0 else (listState.firstVisibleItemIndex + 1).coerceAtMost(pages.size)
        }
    }

    // Load pages when chapter changes
    LaunchedEffect(currentChapterId) {
        isLoading = true
        pages = emptyList() // Clear previous chapter pages so new chapter loads freshly
        isDownloaded = NanzStreamApp.offlineManga.isChapterDownloaded(currentChapterId)

        // Reset scroll position to top
        listState.scrollToItem(0)

        // 1. Try loading from offline storage first
        if (isDownloaded) {
            val offlinePages = NanzStreamApp.offlineManga.getOfflinePages(currentChapterId)
            if (offlinePages.isNotEmpty()) {
                pages = offlinePages
                isLoading = false
            }
        }

        // 2. If not offline, fetch from network with bounded timeout
        if (pages.isEmpty()) {
            try {
                val fetched = kotlinx.coroutines.withTimeoutOrNull(20000) {
                    repository.getMangaPages(currentChapterId)
                }
                pages = fetched.orEmpty()
            } catch (e: Exception) {
                e.printStackTrace()
                pages = emptyList()
            } finally {
                isLoading = false
            }
        }

        // Save to Continue Watching / Reading
        NanzStreamApp.storage.saveContinueWatching(
            ContinueWatchingItem(
                mediaId = mangaId,
                title = "Komik Manga",
                thumbnail = "",
                category = CategoryType.MANGA,
                lastItemTitle = activeChapterTitle,
                lastTargetUrl = currentChapterId
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        if (useWebReader) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        setBackgroundColor(android.graphics.Color.BLACK)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowContentAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val u = request?.url?.toString().orEmpty()
                                if (u.contains("bacakomik") || u.contains(".lol") || u.contains(".lat") || u.contains(".pics") || u.contains("wp.com")) {
                                    return false
                                }
                                return true
                            }
                        }
                    }
                },
                update = { wv ->
                    val target = if (currentChapterId.startsWith("http")) currentChapterId else "https://bacakomik.my/$currentChapterId"
                    if (wv.url != target) {
                        wv.loadUrl(target)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (showControls) 56.dp else 0.dp)
            )
        } else if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        } else if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Halaman komik tidak dapat dimuat",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Pastikan koneksi internet aktif atau coba muat ulang.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .clickable {
                                    coroutineScope.launch {
                                        isLoading = true
                                        try {
                                            pages = repository.getMangaPages(currentChapterId)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Muat Ulang", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentCyan)
                                .clickable { useWebReader = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🌐 Mode Web", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Continuous Vertical Scroll
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 56.dp, bottom = 90.dp)
            ) {
                items(pages) { pageItem ->
                    MangaPageView(page = pageItem)
                }

                // End of Chapter Nav Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Akhir dari $activeChapterTitle",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Selesai membaca seluruh ${pages.size} halaman",
                            color = TextMuted,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Chapter Nav Buttons (Sebelumnya & Selanjutnya)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Tombol Sebelumnya
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, if (prevChapter != null) GlassBorder else Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                                    .background(if (prevChapter != null) SurfaceElevated else Color(0x0AFFFFFF))
                                    .clickable(enabled = prevChapter != null) {
                                        prevChapter?.let {
                                            currentChapterId = it.id
                                            activeChapterTitle = it.title
                                        }
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Sebelumnya",
                                        tint = if (prevChapter != null) Color.White else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sebelumnya",
                                        color = if (prevChapter != null) Color.White else TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Tombol Selanjutnya
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, if (nextChapter != null) Color.White else Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                                    .background(if (nextChapter != null) Color.White else Color(0x0AFFFFFF))
                                    .clickable(enabled = nextChapter != null) {
                                        nextChapter?.let {
                                            currentChapterId = it.id
                                            activeChapterTitle = it.title
                                        }
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Selanjutnya",
                                        color = if (nextChapter != null) CanvasBlack else TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Selanjutnya",
                                        tint = if (nextChapter != null) CanvasBlack else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .background(GlassBackground)
                                .clickable { onBackClick() }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Kembali ke Daftar Chapter",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Top Overlay Bar with Offline Download Button
        if (showControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .background(Color(0xCC090A0E))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(1.dp, GlassBorder, CircleShape)
                            .background(GlassBackground)
                            .clickable(onClick = onBackClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = activeChapterTitle,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (isDownloaded) "${pages.size} Halaman • Mode Offline" else "${pages.size} Halaman • Mode Vertikal",
                            color = if (isDownloaded) Color(0xFF10B981) else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isDownloaded) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Reader Mode Switcher Toggle Button (⚡ Native vs 🌐 Web)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (useWebReader) AccentCyan.copy(alpha = 0.85f) else Color(0x33FFFFFF))
                            .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                            .clickable { useWebReader = !useWebReader }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (useWebReader) "🌐 Web" else "⚡ Native",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Offline Download Action Button
                    if (isDownloading) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, GlassBorder, RoundedCornerShape(100.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (downloadProgress.isNotEmpty()) downloadProgress else "Unduh...",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isDownloaded) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color(0x2210B981))
                                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(100.dp))
                                .clickable {
                                    val deleted = NanzStreamApp.offlineManga.deleteOfflineChapter(currentChapterId)
                                    if (deleted) {
                                        isDownloaded = false
                                        Toast.makeText(context, "Dihapus dari penyimpanan offline", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Tersedia Offline",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Offline ✓",
                                color = Color(0xFF10B981),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(Color.White)
                                .clickable(enabled = pages.isNotEmpty()) {
                                    coroutineScope.launch {
                                        isDownloading = true
                                        downloadProgress = "0%"
                                        val success = NanzStreamApp.offlineManga.downloadChapter(
                                            mangaId = mangaId,
                                            mangaTitle = "Komik",
                                            chapterId = currentChapterId,
                                            chapterTitle = activeChapterTitle,
                                            thumbnail = pages.firstOrNull()?.url ?: "",
                                            pages = pages
                                        ) { downloaded, total ->
                                            downloadProgress = "$downloaded/$total"
                                        }
                                        isDownloading = false
                                        if (success) {
                                            isDownloaded = true
                                            Toast.makeText(
                                                context,
                                                "Chapter berhasil disimpan ke offline! ($downloadProgress halaman)",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Gagal menyimpan ke offline. Periksa koneksi internet.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Tambahkan ke Offline",
                                tint = CanvasBlack,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Tambahkan ke Offline",
                                color = CanvasBlack,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Control Bar with Prev/Next Chapter and Current Page/Chapter Info
        if (showControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .background(Color(0xE6090A0E))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Tombol Sebelumnya
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, if (prevChapter != null) GlassBorder else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                        .background(if (prevChapter != null) SurfaceElevated else Color(0x0AFFFFFF))
                        .clickable(enabled = prevChapter != null) {
                            prevChapter?.let {
                                currentChapterId = it.id
                                activeChapterTitle = it.title
                            }
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Sebelumnya",
                            tint = if (prevChapter != null) Color.White else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sebelumnya",
                            color = if (prevChapter != null) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Info Chapter & Halaman Sekarang
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = activeChapterTitle,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (pages.isNotEmpty()) "Halaman $currentPageNumber dari ${pages.size}" else "Memuat...",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Tombol Selanjutnya
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, if (nextChapter != null) Color.White else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                        .background(if (nextChapter != null) Color.White else Color(0x0AFFFFFF))
                        .clickable(enabled = nextChapter != null) {
                            nextChapter?.let {
                                currentChapterId = it.id
                                activeChapterTitle = it.title
                            }
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Selanjutnya",
                            color = if (nextChapter != null) CanvasBlack else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Selanjutnya",
                            tint = if (nextChapter != null) CanvasBlack else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MangaPageView(page: MangaPageItem) {
    var decryptedBitmap by remember(page.url) { mutableStateOf<Bitmap?>(null) }
    var isDecrypting by remember(page.url) { mutableStateOf(!page.key.isNullOrBlank()) }
    var reloadTrigger by remember(page.url) { mutableIntStateOf(0) }

    LaunchedEffect(page.url, reloadTrigger) {
        if (!page.key.isNullOrBlank() && !page.iv.isNullOrBlank()) {
            isDecrypting = true
            decryptedBitmap = MangaDecryptor.loadAndDecryptBitmap(page.url, page.key, page.iv)
            isDecrypting = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        if (decryptedBitmap != null) {
            Image(
                bitmap = decryptedBitmap!!.asImageBitmap(),
                contentDescription = "Halaman ${page.page}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isDecrypting) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        } else {
            val isLocalFile = page.url.startsWith("file://")
            val context = LocalContext.current
            val imageModel = remember(page.url, reloadTrigger) {
                if (isLocalFile) {
                    File(page.url.removePrefix("file://"))
                } else {
                    val referer = if (page.url.contains("animasu")) {
                        "https://animasu.love/"
                    } else {
                        "https://bacakomik.my/"
                    }
                    ImageRequest.Builder(context)
                        .data(page.url)
                        .addHeader("Referer", referer)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .crossfade(true)
                        .bitmapConfig(Bitmap.Config.RGB_565)
                        .allowHardware(false)
                        .build()
                }
            }

            SubcomposeAsyncImage(
                model = imageModel,
                contentDescription = "Halaman ${page.page}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(Color(0xFF141414)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Memuat Halaman ${page.page}...",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(Color(0xFF1A1A1A))
                            .clickable { reloadTrigger++ }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Halaman ${page.page} gagal dimuat",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Ketuk untuk memuat ulang",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        }
    }
}
