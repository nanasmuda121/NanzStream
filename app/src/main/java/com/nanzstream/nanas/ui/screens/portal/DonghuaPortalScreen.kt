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

private enum class DonghuaTab(val title: String, val icon: ImageVector) {
    TERBARU("Terbaru", Icons.Default.Whatshot),
    POPULER("Populer", Icons.Default.AutoAwesome),
    SEARCH("Cari", Icons.Default.Search)
}

@Composable
fun DonghuaPortalScreen(
    repository: MediaRepository,
    onBackToPortal: () -> Unit,
    onDonghuaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(DonghuaTab.TERBARU) }
    var latestList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var popularList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(currentTab) {
        when (currentTab) {
            DonghuaTab.TERBARU -> {
                if (latestList.isEmpty()) {
                    isLoading = true
                    try {
                        latestList = repository.getDonghuaLatest(1)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            DonghuaTab.POPULER -> {
                if (popularList.isEmpty()) {
                    isLoading = true
                    try {
                        popularList = repository.getDonghuaLatest(2)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }
            }
            DonghuaTab.SEARCH -> {}
        }
    }

    Scaffold(
        topBar = {
            // Dedicated TopBar for Donghua Universe
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
                            text = "DONGHUA UNIVERSE",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Anichin • Animasi 3D Cultivation & Xianxia",
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
            // Dedicated BottomBar for Donghua Sub-App
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
                    DonghuaTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        val tint = if (isSelected) Color.White else TextMuted

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { currentTab = tab }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
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
            when (currentTab) {
                DonghuaTab.TERBARU, DonghuaTab.POPULER -> {
                    val displayItems = if (currentTab == DonghuaTab.TERBARU) latestList else popularList
                    if (isLoading && displayItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
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
                                    onClick = { onDonghuaClick(item) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(185.dp)
                                )
                            }
                        }
                    }
                }

                DonghuaTab.SEARCH -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari judul donghua di Anichin...", color = TextDim, fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TextDim)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = TextDim)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { focusManager.clearFocus() }
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
                                    searchResults = repository.search(CategoryType.DONGHUA, searchQuery, 1)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    isLoading = false
                                }
                            } else if (searchQuery.isEmpty()) {
                                searchResults = emptyList()
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

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
                                        onClick = { onDonghuaClick(item) },
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
                                Text("Tidak ada donghua yang cocok dengan \"$searchQuery\"", color = TextMuted, fontSize = 13.sp)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Ketik judul donghua untuk mulai mencari", color = TextDim, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
