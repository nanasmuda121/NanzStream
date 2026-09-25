package com.nanzstream.nanas.ui.screens.portal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
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
import com.nanzstream.nanas.ui.components.MediaItemCard
import com.nanzstream.nanas.ui.theme.*
import kotlinx.coroutines.launch

private enum class DrachinaTab(val title: String, val icon: ImageVector) {
    TERBARU("Terbaru", Icons.Default.Whatshot),
    KOLEKSI("Koleksi", Icons.Default.AutoAwesome),
    SEARCH("Cari", Icons.Default.Search)
}

@Composable
fun DrachinaPortalScreen(
    repository: MediaRepository,
    onBackToPortal: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(DrachinaTab.TERBARU) }
    var latestList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var collectionList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentTab) {
        when (currentTab) {
            DrachinaTab.TERBARU -> {
                if (latestList.isEmpty()) {
                    isLoading = true
                    try {
                        latestList = repository.getDrachinaLatest(1)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            DrachinaTab.KOLEKSI -> {
                if (collectionList.isEmpty()) {
                    isLoading = true
                    try {
                        collectionList = repository.getDrachinaLatest(2)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            DrachinaTab.SEARCH -> {}
        }
    }

    Scaffold(
        topBar = {
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
                            contentDescription = "Kembali ke Beranda",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "DRAMA CHINA UNIVERSE",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Dracinema • Drama Pendek & Romance Sub Indo",
                            color = TextDim,
                            fontSize = 10.sp
                        )
                    }
                }

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
                        contentDescription = "Beranda",
                        tint = TextDim,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "BERANDA",
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        },
        bottomBar = {
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
                    DrachinaTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        val tint = if (isSelected) Color.White else TextMuted

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { currentTab = tab }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
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

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBackToPortal)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Beranda",
                            tint = TextDim,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Beranda",
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
                DrachinaTab.TERBARU -> {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(latestList) { item ->
                                MediaItemCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(185.dp)
                                )
                            }
                        }
                    }
                }
                DrachinaTab.KOLEKSI -> {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(collectionList) { item ->
                                MediaItemCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(185.dp)
                                )
                            }
                        }
                    }
                }
                DrachinaTab.SEARCH -> {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari drama china...", color = TextMuted, fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                if (searchQuery.isNotBlank()) {
                                    isLoading = true
                                    coroutineScope.launch {
                                        try {
                                            searchResults = repository.search(CategoryType.DRACHINA, searchQuery)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            }),
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextDim)
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceElevated,
                                unfocusedContainerColor = SurfaceElevated,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        )
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                        }
                    } else if (searchResults.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(searchResults) { item ->
                                MediaItemCard(
                                    item = item,
                                    onClick = { onItemClick(item) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(185.dp)
                                )
                            }
                        }
                    } else if (searchQuery.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Tidak ditemukan drama dengan kata kunci tersebut.", color = TextMuted, fontSize = 13.sp)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Ketik judul drama china dan tekan Enter untuk mencari.", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
