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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import coil.request.ImageRequest
import com.nanzstream.nanas.NanzStreamApp
import com.nanzstream.nanas.crypto.MangaDecryptor
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.ContinueWatchingItem
import com.nanzstream.nanas.data.model.MangaPageItem
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.theme.*
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
    var currentChapterId by remember { mutableStateOf(initialChapterId) }
    var pages by remember { mutableStateOf<List<MangaPageItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }

    var isDownloaded by remember(currentChapterId) {
        mutableStateOf(NanzStreamApp.offlineManga.isChapterDownloaded(currentChapterId))
    }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf("") }

    LaunchedEffect(currentChapterId) {
        isLoading = true
        isDownloaded = NanzStreamApp.offlineManga.isChapterDownloaded(currentChapterId)

        // 1. Try loading from offline storage first
        if (isDownloaded) {
            val offlinePages = NanzStreamApp.offlineManga.getOfflinePages(currentChapterId)
            if (offlinePages.isNotEmpty()) {
                pages = offlinePages
                isLoading = false
            }
        }

        // 2. If not offline, fetch from network
        if (pages.isEmpty()) {
            try {
                pages = repository.getMangaPages(currentChapterId)
            } catch (e: Exception) {
                e.printStackTrace()
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
                lastItemTitle = chapterTitle,
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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        } else {
            // Continuous Vertical Webtoon Scroll
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 50.dp)
            ) {
                items(pages) { pageItem ->
                    MangaPageView(page = pageItem)
                }

                // End of Chapter Nav
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Akhir dari $chapterTitle",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                .background(GlassBackground)
                                .clickable { onBackClick() }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kembali ke Daftar Chapter",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = chapterTitle,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (isDownloaded) "${pages.size} Halaman • Mode Offline" else "${pages.size} Halaman • Webtoon Mode",
                            color = if (isDownloaded) Color(0xFF10B981) else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isDownloaded) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

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
                                        mangaTitle = "Komik Webtoon",
                                        chapterId = currentChapterId,
                                        chapterTitle = chapterTitle,
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
}

@Composable
fun MangaPageView(page: MangaPageItem) {
    var decryptedBitmap by remember(page.url) { mutableStateOf<Bitmap?>(null) }
    var isDecrypting by remember(page.url) { mutableStateOf(!page.key.isNullOrBlank()) }

    LaunchedEffect(page.url) {
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
            val imageModel = if (isLocalFile) {
                File(page.url.removePrefix("file://"))
            } else {
                ImageRequest.Builder(LocalContext.current)
                    .data(page.url)
                    .addHeader("Referer", "https://www.webtoons.com/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .crossfade(true)
                    .build()
            }

            AsyncImage(
                model = imageModel,
                contentDescription = "Halaman ${page.page}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        }
    }
}
