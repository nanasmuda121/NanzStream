package com.nanzstream.nanas.ui.screens.portal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.components.DayScheduleBar
import com.nanzstream.nanas.ui.components.MediaItemCard
import com.nanzstream.nanas.ui.components.getTodayScheduleIndex
import com.nanzstream.nanas.ui.theme.*

private enum class AnimeTab(val title: String, val icon: ImageVector) {
    TERBARU("Terbaru", Icons.Default.FlashOn),
    JADWAL("Jadwal", Icons.Default.CalendarToday),
    POPULER("Populer", Icons.Default.Star),
    SEARCH("Cari", Icons.Default.Search)
}

@Composable
fun AnimePortalScreen(
    repository: MediaRepository,
    onBackToPortal: () -> Unit,
    onAnimeClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(AnimeTab.TERBARU) }
    val todayIndex = remember { getTodayScheduleIndex() }
    var selectedDayIndex by remember { mutableIntStateOf(todayIndex) }
    var scheduleCache by remember { mutableStateOf<Map<Int, List<MediaItem>>>(emptyMap()) }
    var scheduleList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var latestList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var popularList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(currentTab, selectedDayIndex, refreshTrigger) {
        when (currentTab) {
            AnimeTab.TERBARU -> {
                if (latestList.isEmpty() || refreshTrigger > 0) {
                    isLoading = true
                    try {
                        latestList = repository.getAnimeLatest(1)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            AnimeTab.JADWAL -> {
                val cached = scheduleCache[selectedDayIndex]
                if (cached != null && cached.isNotEmpty() && refreshTrigger == 0) {
                    scheduleList = cached
                } else {
                    isLoading = true
                    try {
                        val items = repository.getAnimeSchedule(selectedDayIndex)
                        scheduleList = items
                        if (items.isNotEmpty()) {
                            scheduleCache = scheduleCache + (selectedDayIndex to items)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            AnimeTab.POPULER -> {
                if (popularList.isEmpty() || refreshTrigger > 0) {
                    isLoading = true
                    try {
                        popularList = repository.getAnimeLatest(2)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            AnimeTab.SEARCH -> {}
        }
    }

    Scaffold(
        topBar = {
            // Dedicated TopBar for Anime Universe
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
                            text = "ANIME UNIVERSE",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Otakudesu • Direct MP4 & Sub Indo",
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
            // Dedicated BottomBar for Anime Sub-App
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
                    AnimeTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        val tint = if (isSelected) Color.White else TextMuted

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { currentTab = tab }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = tint,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // Portal Tab Button
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBackToPortal)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Portal Gateway",
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
            when (currentTab) {
                AnimeTab.TERBARU, AnimeTab.POPULER -> {
                    val displayItems = if (currentTab == AnimeTab.TERBARU) latestList else popularList
                    if (isLoading && displayItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                        }
                    } else if (!isLoading && displayItems.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Gagal memuat daftar anime",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { refreshTrigger++ },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SurfaceElevated,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(100.dp),
                                border = BorderStroke(1.dp, GlassBorder)
                            ) {
                                Text("Coba Lagi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayItems) { item ->
                                MediaItemCard(
                                    item = item,
                                    onClick = { onAnimeClick(item) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(185.dp)
                                )
                            }
                        }
                    }
                }

                AnimeTab.JADWAL -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        DayScheduleBar(
                            selectedDayIndex = selectedDayIndex,
                            onSelectDay = { selectedDayIndex = it },
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )

                        if (isLoading && scheduleList.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                            }
                        } else if (!isLoading && scheduleList.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Gagal memuat jadwal anime",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { refreshTrigger++ },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SurfaceElevated,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(100.dp),
                                    border = BorderStroke(1.dp, GlassBorder)
                                ) {
                                    Text("Coba Lagi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(scheduleList) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = { onAnimeClick(item) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(185.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                AnimeTab.SEARCH -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari judul anime di Otakudesu...", color = TextDim, fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TextDim)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { 
                                        searchQuery = "" 
                                        searchSuggestions = emptyList()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = TextDim)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    focusManager.clearFocus()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = BorderHairline,
                                focusedContainerColor = SurfaceElevated,
                                unfocusedContainerColor = SurfaceCharcoal,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        LaunchedEffect(searchQuery) {
                            if (searchQuery.length >= 2) {
                                isLoading = true
                                try {
                                    val sugg = repository.getSearchSuggestions(CategoryType.ANIME, searchQuery)
                                    searchSuggestions = sugg
                                    searchResults = repository.search(CategoryType.ANIME, searchQuery, 1)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    isLoading = false
                                }
                            } else {
                                searchResults = emptyList()
                                searchSuggestions = emptyList()
                            }
                        }

                        if (searchSuggestions.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(searchSuggestions) { suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(100.dp))
                                            .background(SurfaceElevated)
                                            .border(1.dp, BorderHairline, RoundedCornerShape(100.dp))
                                            .clickable {
                                                searchQuery = suggestion
                                                focusManager.clearFocus()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = TextDim,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = suggestion,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                            }
                        } else if (searchResults.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(searchResults) { item ->
                                    MediaItemCard(
                                        item = item,
                                        onClick = { onAnimeClick(item) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(185.dp)
                                    )
                                }
                            }
                        } else if (searchQuery.length >= 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Tidak ada anime yang cocok dengan \"$searchQuery\"", color = TextMuted, fontSize = 13.sp)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Ketik judul anime untuk mulai mencari", color = TextDim, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
