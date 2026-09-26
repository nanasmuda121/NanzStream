package com.nanzstream.nanas.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import com.nanzstream.nanas.NanzStreamApp
import com.nanzstream.nanas.crypto.MangaDecryptor
import com.nanzstream.nanas.crypto.WebtoonBitmapDecoder
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.ContinueWatchingItem
import com.nanzstream.nanas.data.model.MangaChapterItem
import com.nanzstream.nanas.data.model.MangaPageItem
import com.nanzstream.nanas.data.remote.ApiClient
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.theme.*
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONArray
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

    var isDownloaded by remember(currentChapterId) {
        mutableStateOf(NanzStreamApp.offlineManga.isChapterDownloaded(currentChapterId))
    }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf("") }

    var useIframeReader by remember { mutableStateOf(!isDownloaded) }
    var iframeCurrentPage by remember { mutableIntStateOf(1) }

    LaunchedEffect(isDownloaded) {
        if (isDownloaded) {
            useIframeReader = false
        }
    }

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
            .clickable(enabled = !useIframeReader || isDownloaded) { showControls = !showControls }
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        } else if (pages.isEmpty() && (!useIframeReader || isDownloaded)) {
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
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Muat Ulang", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (useIframeReader && !isDownloaded) {
            WebtoonHtmlReaderView(
                chapterUrl = currentChapterId,
                chapterTitle = activeChapterTitle,
                pages = pages,
                prevChapter = prevChapter,
                nextChapter = nextChapter,
                onPrevClick = {
                    prevChapter?.let {
                        currentChapterId = it.id
                        activeChapterTitle = it.title
                    }
                },
                onNextClick = {
                    nextChapter?.let {
                        currentChapterId = it.id
                        activeChapterTitle = it.title
                    }
                },
                onBackClick = onBackClick,
                onToggleControls = { showControls = !showControls },
                onPagesDetected = { detectedPages ->
                    if (pages.isEmpty() && detectedPages.isNotEmpty()) {
                        pages = detectedPages
                    }
                },
                onPageVisible = { pageNum ->
                    iframeCurrentPage = pageNum
                },
                modifier = Modifier.fillMaxSize()
            )
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
                            text = when {
                                isDownloaded -> "${pages.size} Halaman • Mode Offline"
                                useIframeReader -> "${pages.size} Halaman • Tampil Iframe ⚡"
                                else -> "${pages.size} Halaman • Mode Native"
                            },
                            color = when {
                                isDownloaded -> Color(0xFF10B981)
                                useIframeReader -> Color(0xFF60A5FA)
                                else -> TextMuted
                            },
                            fontSize = 11.sp,
                            fontWeight = if (isDownloaded || useIframeReader) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mode Switcher Toggle: Iframe vs Native
                    if (!isDownloaded) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .border(
                                    1.dp,
                                    if (useIframeReader) Color(0xFF60A5FA) else GlassBorder,
                                    RoundedCornerShape(100.dp)
                                )
                                .background(if (useIframeReader) Color(0x333B82F6) else SurfaceElevated)
                                .clickable {
                                    useIframeReader = !useIframeReader
                                    Toast.makeText(
                                        context,
                                        if (useIframeReader) "Beralih ke Tampil Iframe (Cepat & Anti Error)" else "Beralih ke Mode Native",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (useIframeReader) "⚡ Iframe" else "📱 Native",
                                color = if (useIframeReader) Color.White else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                    val displayPageNumber = if (useIframeReader && !isDownloaded) iframeCurrentPage else currentPageNumber
                    Text(
                        text = if (pages.isNotEmpty()) "Halaman $displayPageNumber dari ${pages.size}" else "Memuat...",
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
    val context = LocalContext.current
    var slices by remember(page.url) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember(page.url) { mutableStateOf(true) }
    var isError by remember(page.url) { mutableStateOf(false) }
    var reloadTrigger by remember(page.url) { mutableIntStateOf(0) }

    LaunchedEffect(page.url, reloadTrigger) {
        isLoading = true
        isError = false
        try {
            val result = WebtoonBitmapDecoder.loadPageSlices(
                context = context,
                pageUrl = page.url,
                keyHex = page.key,
                ivHex = page.iv
            )
            if (result.isNotEmpty()) {
                slices = result
                isError = false
            } else {
                isError = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isError = true
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        if (slices.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                slices.forEach { sliceBitmap ->
                    Image(
                        bitmap = sliceBitmap.asImageBitmap(),
                        contentDescription = "Halaman ${page.page}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else if (isLoading) {
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
        } else if (isError) {
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
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebtoonHtmlReaderView(
    chapterUrl: String,
    chapterTitle: String,
    pages: List<MangaPageItem>,
    prevChapter: MangaChapterItem?,
    nextChapter: MangaChapterItem?,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onBackClick: () -> Unit,
    onToggleControls: () -> Unit,
    onPagesDetected: (List<MangaPageItem>) -> Unit,
    onPageVisible: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastLoadedKey by remember { mutableStateOf("") }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun toggleControls() {
                        post { onToggleControls() }
                    }

                    @JavascriptInterface
                    fun prevChapter() {
                        post { onPrevClick() }
                    }

                    @JavascriptInterface
                    fun nextChapter() {
                        post { onNextClick() }
                    }

                    @JavascriptInterface
                    fun backToList() {
                        post { onBackClick() }
                    }

                    @JavascriptInterface
                    fun reportPage(pageNum: Int) {
                        post { onPageVisible(pageNum) }
                    }

                    @JavascriptInterface
                    fun onImagesFound(jsonArrayStr: String) {
                        try {
                            val jsonArray = JSONArray(jsonArrayStr)
                            val list = mutableListOf<MangaPageItem>()
                            for (i in 0 until jsonArray.length()) {
                                val u = jsonArray.optString(i)
                                if (u.isNotBlank()) {
                                    list.add(MangaPageItem(page = i + 1, url = u))
                                }
                            }
                            if (list.isNotEmpty()) {
                                post { onPagesDetected(list) }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }, "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return true
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        return true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString().orEmpty()
                        val isImage = reqUrl.endsWith(".webp", ignoreCase = true) ||
                                reqUrl.endsWith(".jpg", ignoreCase = true) ||
                                reqUrl.endsWith(".jpeg", ignoreCase = true) ||
                                reqUrl.endsWith(".png", ignoreCase = true) ||
                                reqUrl.contains("/media/", ignoreCase = true) ||
                                reqUrl.contains("/data/", ignoreCase = true) ||
                                reqUrl.contains(".lol", ignoreCase = true) ||
                                reqUrl.contains(".lat", ignoreCase = true) ||
                                reqUrl.contains(".pics", ignoreCase = true)

                        if (isImage && (reqUrl.startsWith("http://") || reqUrl.startsWith("https://"))) {
                            try {
                                val referer = when {
                                    reqUrl.contains("animasu") -> "https://animasu.love/"
                                    else -> "https://bacakomik.my/"
                                }
                                val okReq = Request.Builder()
                                    .url(reqUrl)
                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                                    .header("Referer", referer)
                                    .header("sec-fetch-dest", "image")
                                    .header("sec-fetch-mode", "no-cors")
                                    .header("sec-fetch-site", "cross-site")
                                    .build()

                                val resp = ApiClient.okHttpClient.newCall(okReq).execute()
                                if (resp.isSuccessful && resp.body != null) {
                                    val body = resp.body!!
                                    val mimeType = when {
                                        reqUrl.contains(".webp", ignoreCase = true) -> "image/webp"
                                        reqUrl.contains(".png", ignoreCase = true) -> "image/png"
                                        else -> "image/jpeg"
                                    }
                                    val responseHeaders = mutableMapOf(
                                        "Access-Control-Allow-Origin" to "*",
                                        "Cache-Control" to "public, max-age=31536000"
                                    )
                                    return WebResourceResponse(
                                        mimeType,
                                        null,
                                        200,
                                        "OK",
                                        responseHeaders,
                                        body.byteStream()
                                    )
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val jsClean = """
                            (function() {
                                var s = document.createElement('style');
                                s.innerHTML = `
                                    header, footer, nav, .header, .footer, .sidebar, #sidebar, .comments, #comments, .announcement, .iklan, .ads, [class*="ads"], [id*="ads"], .nav_ch, .bcrumb, .entry-header {
                                        display: none !important;
                                    }
                                    html, body {
                                        background-color: #000000 !important;
                                        color: #FFFFFF !important;
                                        margin: 0 !important;
                                        padding: 56px 0 90px 0 !important;
                                    }
                                    #chimg-auh, #readerarea, .chapter-content, .chapter-area {
                                        display: block !important;
                                        width: 100% !important;
                                        margin: 0 !important;
                                        padding: 0 !important;
                                    }
                                    #chimg-auh img, #readerarea img, .chapter-content img, .chapter-area img {
                                        display: block !important;
                                        width: 100% !important;
                                        height: auto !important;
                                        margin: 0 !important;
                                        padding: 0 !important;
                                        background: #000000 !important;
                                    }
                                `;
                                document.head.appendChild(s);

                                var imgs = document.querySelectorAll('#chimg-auh img, #readerarea img, .chapter-content img, .chapter-area img');
                                var found = [];
                                imgs.forEach(function(img) {
                                    var src = img.getAttribute('data-lazy-src') || img.getAttribute('data-src') || img.getAttribute('src');
                                    if (src && src.startsWith('http') && !src.includes('data:image') && !src.includes('blank.gif') && !found.includes(src)) {
                                        found.push(src);
                                    }
                                });
                                if (found.length > 0 && window.AndroidBridge && window.AndroidBridge.onImagesFound) {
                                    window.AndroidBridge.onImagesFound(JSON.stringify(found));
                                }
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(jsClean, null)
                    }
                }
            }
        },
        update = { webView ->
            val currentKey = "$chapterUrl:${pages.size}"
            if (lastLoadedKey != currentKey) {
                lastLoadedKey = currentKey
                if (pages.isNotEmpty()) {
                    val html = buildMangaHtml(
                        title = chapterTitle,
                        pages = pages,
                        hasPrev = prevChapter != null,
                        hasNext = nextChapter != null
                    )
                    webView.loadDataWithBaseURL("https://bacakomik.my/", html, "text/html", "UTF-8", null)
                } else if (chapterUrl.isNotBlank()) {
                    val headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                        "Referer" to "https://bacakomik.my/"
                    )
                    webView.loadUrl(chapterUrl, headers)
                }
            }
        }
    )
}

fun buildMangaHtml(
    title: String,
    pages: List<MangaPageItem>,
    hasPrev: Boolean,
    hasNext: Boolean
): String {
    val imgTags = StringBuilder()
    pages.forEachIndexed { index, page ->
        val loadingAttr = if (index < 3) "loading=\"eager\"" else "loading=\"lazy\""
        imgTags.append(
            """
            <div class="page-container" data-page="${page.page}">
                <img src="${page.url}" 
                     $loadingAttr 
                     decoding="async" 
                     alt="Halaman ${page.page}"
                     onerror="if(!this.dataset.retried){this.dataset.retried='1';this.src='${page.url}';}" />
                <div class="page-badge">${page.page} / ${pages.size}</div>
            </div>
            """.trimIndent()
        ).append("\n")
    }

    val prevBtn = if (hasPrev) {
        """<button class="btn btn-prev" onclick="AndroidBridge.prevChapter()">◀ Sebelumnya</button>"""
    } else {
        """<button class="btn btn-prev disabled" disabled>◀ Sebelumnya</button>"""
    }

    val nextBtn = if (hasNext) {
        """<button class="btn btn-next" onclick="AndroidBridge.nextChapter()">Selanjutnya ▶</button>"""
    } else {
        """<button class="btn btn-next disabled" disabled>Selanjutnya ▶</button>"""
    }

    return """
        <!DOCTYPE html>
        <html lang="id">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
            <style>
                * {
                    box-sizing: border-box;
                    margin: 0;
                    padding: 0;
                    -webkit-tap-highlight-color: transparent;
                }
                html, body {
                    background-color: #000000;
                    color: #FFFFFF;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    width: 100%;
                    min-height: 100%;
                    overflow-x: hidden;
                }
                .reader-content {
                    width: 100%;
                    max-width: 900px;
                    margin: 0 auto;
                    padding-top: 56px;
                    padding-bottom: 90px;
                    background-color: #000000;
                }
                .page-container {
                    position: relative;
                    width: 100%;
                    margin: 0;
                    padding: 0;
                    background-color: #000000;
                    line-height: 0;
                }
                .page-container img {
                    display: block;
                    width: 100%;
                    height: auto;
                    margin: 0;
                    padding: 0;
                    border: none;
                    background-color: #000000;
                    vertical-align: bottom;
                }
                .page-badge {
                    position: absolute;
                    bottom: 8px;
                    right: 10px;
                    background: rgba(0, 0, 0, 0.65);
                    color: rgba(255, 255, 255, 0.75);
                    font-size: 11px;
                    font-weight: 700;
                    padding: 3px 8px;
                    border-radius: 6px;
                    backdrop-filter: blur(4px);
                    pointer-events: none;
                    line-height: 1.2;
                }
                .chapter-end-card {
                    padding: 40px 20px 60px 20px;
                    text-align: center;
                    background: #090A0E;
                    border-top: 1px solid rgba(255, 255, 255, 0.1);
                    margin-top: 24px;
                }
                .end-title {
                    font-size: 16px;
                    font-weight: 700;
                    margin-bottom: 4px;
                }
                .end-desc {
                    font-size: 12px;
                    color: rgba(255, 255, 255, 0.5);
                    margin-bottom: 24px;
                }
                .btn-row {
                    display: flex;
                    gap: 12px;
                    justify-content: center;
                    max-width: 420px;
                    margin: 0 auto 16px auto;
                }
                .btn {
                    flex: 1;
                    height: 46px;
                    border-radius: 12px;
                    font-size: 13px;
                    font-weight: 700;
                    border: none;
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    transition: opacity 0.2s;
                }
                .btn-prev {
                    background: #181920;
                    color: #FFFFFF;
                    border: 1px solid rgba(255, 255, 255, 0.15);
                }
                .btn-next {
                    background: #FFFFFF;
                    color: #000000;
                }
                .btn.disabled {
                    opacity: 0.3;
                    cursor: not-allowed;
                }
                .btn-back {
                    display: inline-block;
                    margin-top: 10px;
                    padding: 10px 20px;
                    background: rgba(255, 255, 255, 0.08);
                    border: 1px solid rgba(255, 255, 255, 0.15);
                    border-radius: 10px;
                    color: rgba(255, 255, 255, 0.7);
                    font-size: 12px;
                    font-weight: 600;
                    cursor: pointer;
                }
            </style>
        </head>
        <body>
            <div class="reader-content">
                $imgTags
                <div class="chapter-end-card">
                    <div class="end-title">Akhir dari $title</div>
                    <div class="end-desc">Selesai membaca seluruh ${pages.size} halaman</div>
                    <div class="btn-row">
                        $prevBtn
                        $nextBtn
                    </div>
                    <div class="btn-back" onclick="AndroidBridge.backToList()">Kembali ke Daftar Chapter</div>
                </div>
            </div>
            <script>
                // Track visible page with IntersectionObserver
                if ('IntersectionObserver' in window) {
                    const observer = new IntersectionObserver((entries) => {
                        entries.forEach(entry => {
                            if (entry.isIntersecting) {
                                const p = entry.target.getAttribute('data-page');
                                if (p && window.AndroidBridge && window.AndroidBridge.reportPage) {
                                    window.AndroidBridge.reportPage(parseInt(p, 10));
                                }
                            }
                        });
                    }, { threshold: 0.35 });

                    document.querySelectorAll('.page-container').forEach(el => observer.observe(el));
                }

                // Smooth tap handling (distinguish between scroll/pan and tap)
                let touchStartX = 0;
                let touchStartY = 0;
                let touchStartTime = 0;

                document.addEventListener('touchstart', function(e) {
                    if (e.touches.length === 1) {
                        touchStartX = e.touches[0].clientX;
                        touchStartY = e.touches[0].clientY;
                        touchStartTime = Date.now();
                    }
                }, { passive: true });

                document.addEventListener('touchend', function(e) {
                    if (e.changedTouches.length === 1) {
                        let dx = Math.abs(e.changedTouches[0].clientX - touchStartX);
                        let dy = Math.abs(e.changedTouches[0].clientY - touchStartY);
                        let dt = Date.now() - touchStartTime;
                        if (dx < 14 && dy < 14 && dt < 350) {
                            let target = e.target;
                            if (target && (target.tagName.toLowerCase() === 'button' || target.closest('button') || target.classList.contains('btn-back'))) {
                                return;
                            }
                            if (window.AndroidBridge && window.AndroidBridge.toggleControls) {
                                window.AndroidBridge.toggleControls();
                            }
                        }
                    }
                }, { passive: true });
            </script>
        </body>
        </html>
    """.trimIndent()
}
