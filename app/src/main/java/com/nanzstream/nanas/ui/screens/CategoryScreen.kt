package com.nanzstream.nanas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.data.repository.MediaRepository
import com.nanzstream.nanas.ui.components.GlassPill
import com.nanzstream.nanas.ui.components.MediaItemCard
import com.nanzstream.nanas.ui.theme.DarkBg
import com.nanzstream.nanas.ui.theme.TextMuted
import com.nanzstream.nanas.ui.theme.TextPrimary

@Composable
fun CategoryScreen(
    initialCategory: CategoryType,
    repository: MediaRepository,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val categories = listOf(
        CategoryType.ANIME,
        CategoryType.DONGHUA,
        CategoryType.MANGA,
        CategoryType.VOD
    )

    LaunchedEffect(selectedCategory) {
        isLoading = true
        try {
            items = when (selectedCategory) {
                CategoryType.ANIME -> repository.getAnimeLatest(1)
                CategoryType.DONGHUA -> repository.getDonghuaLatest(1)
                CategoryType.MANGA -> repository.getMangaHome()
                CategoryType.VOD -> repository.getVodList(1)
                else -> repository.getAnimeLatest(1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Category Pills Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
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

        // Section Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedCategory.icon} Katalog ${selectedCategory.displayName}",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(${items.size} judul)",
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items) { item ->
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
