package com.nanzstream.nanas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nanzstream.nanas.data.model.MediaItem
import com.nanzstream.nanas.ui.theme.*

@Composable
fun MediaItemCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, GlassBorder, shape)
            .background(DarkCard)
            .clickable(onClick = onClick)
    ) {
        // Poster Image
        val context = LocalContext.current
        val imageModel = remember(item.thumbnail) {
            val thumb = item.thumbnail
            val referer = when {
                thumb.contains("anichin") -> "https://anichin.ro/"
                thumb.contains("otakudesu") -> "https://otakudesu.blog/"
                thumb.contains("webtoon") -> "https://www.webtoons.com/"
                else -> "https://www.google.com/"
            }
            ImageRequest.Builder(context)
                .data(thumb)
                .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .setHeader("Referer", referer)
                .crossfade(true)
                .build()
        }

        AsyncImage(
            model = imageModel,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient Shadow Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x40000000),
                            Color(0xE608080A)
                        ),
                        startY = 100f
                    )
                )
        )

        // Top Badge (Episode / Chapter / Live)
        if (!item.badge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.5.dp, Color(0x60FFFFFF), RoundedCornerShape(6.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Category Tag
        Box(
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x80202028))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = item.category.icon,
                fontSize = 11.sp
            )
        }

        // Bottom Info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(
                text = item.title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            if (!item.rating.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "★ ${item.rating}",
                        color = Color(0xFFE4E4E7),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
