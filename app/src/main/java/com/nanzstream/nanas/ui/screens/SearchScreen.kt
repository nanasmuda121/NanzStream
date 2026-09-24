package com.nanzstream.nanas.ui.screens

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.components.GlassPill
import com.nanzstream.nanas.ui.components.MediaItemCard
import com.nanzstream.nanas.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    repository: MediaRepository,
    onBackClick: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(CategoryType.ALL) }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val categories = listOf(
        CategoryType.ALL,
        CategoryType.DRAMA,
        CategoryType.MANGA,
        CategoryType.ANIME,
        CategoryType.DONGHUA
    )

    // Debounced search
    LaunchedEffect(query, selectedCategory) {
        if (query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(400) // debounce
        isSearching = true
        try {
            val allItems = mutableListOf<MediaItem>()
            allItems.addAll(repository.getDramaLatest(1).filter { it.title.contains(query, ignoreCase = true) })
            allItems.addAll(repository.getAnimeLatest(1).filter { it.title.contains(query, ignoreCase = true) })
            allItems.addAll(repository.getDonghuaLatest(1).filter { it.title.contains(query, ignoreCase = true) })
            allItems.addAll(repository.getMangaHome().filter { it.title.contains(query, ignoreCase = true) })

            results = if (selectedCategory == CategoryType.ALL) {
                allItems
            } else {
                allItems.filter { it.category == selectedCategory }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Search Bar Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
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

            // Text input container
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Cari judul anime, drama, komik...",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Hapus",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { query = "" }
                    )
                }
            }
        }

        // Category Pills Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(modifier = Modifier.width(16.dp))
            categories.forEach { cat ->
                GlassPill(
                    title = cat.displayName,
                    icon = cat.icon,
                    isSelected = selectedCategory == cat,
                    onClick = { selectedCategory = cat }
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Results Content
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        } else if (results.isEmpty() && query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tidak ada hasil untuk \"$query\"",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results) { item ->
                    MediaItemCard(
                        item = item,
                        onClick = { onMediaClick(item) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp)
                    )
                }
            }
        }
    }
}
