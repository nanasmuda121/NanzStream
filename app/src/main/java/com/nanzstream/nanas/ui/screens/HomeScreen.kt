package com.nanzstream.nanas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nanzstream.nanas.ui.components.PortalGatewayFooter
import com.nanzstream.nanas.ui.components.PortalGatewayHeader
import com.nanzstream.nanas.ui.components.PortalGatewayIntro
import com.nanzstream.nanas.ui.components.UniverseCard
import com.nanzstream.nanas.ui.theme.CanvasBlack

@Composable
fun HomeScreen(
    onNavigateToCategory: (String) -> Unit,
    onNavigateToLiveTv: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBlack),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Portal Gateway Header (NanzStream + System Online)
        item {
            PortalGatewayHeader()
        }

        // 2. Multi-Verse Hub Intro (Pilih Universe Anda)
        item {
            PortalGatewayIntro()
        }

        // 3. 4 Universe Cards (Pure Gateway separating categories 100%)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Universe 1: Anime
                UniverseCard(
                    title = "ANIME",
                    subtitle = "Japanese Animation, OVA, Movies & Simulcast Mingguan.",
                    actionText = "Masuk Portal Anime",
                    badgeCount = "1.4k+ TITLES",
                    badgeHighlight = "SIMULCAST HD",
                    icon = Icons.Default.PlayArrow,
                    watermarkText = "▶",
                    onClick = { onNavigateToCategory("anime") }
                )

                // Universe 2: Komik
                UniverseCard(
                    title = "KOMIK",
                    subtitle = "Line Webtoon ID, Manga & Manhwa Terjemahan Indonesia.",
                    actionText = "Buka Portal Komik",
                    badgeCount = "3.8k+ TITLES",
                    badgeHighlight = "LINE WEBTOON",
                    icon = Icons.Default.MenuBook,
                    watermarkText = "📖",
                    onClick = { onNavigateToCategory("manga") }
                )

                // Universe 3: Donghua
                UniverseCard(
                    title = "DONGHUA",
                    subtitle = "Animasi 3D Chinese, Xianxia, Wuxia & Petualangan Spiritual.",
                    actionText = "Jelajahi Donghua",
                    badgeCount = "850+ TITLES",
                    badgeHighlight = "CULTIVATION 4K",
                    icon = Icons.Default.AutoAwesome,
                    watermarkText = "🐉",
                    onClick = { onNavigateToCategory("donghua") }
                )

                // Universe 4: Live TV
                UniverseCard(
                    title = "LIVE TV",
                    subtitle = "Siaran Langsung TV Nasional, Berita, Hiburan & Sports HD.",
                    actionText = "Akses Siaran Langsung",
                    badgeCount = "80+ SALURAN",
                    badgeHighlight = "HD 24/7",
                    icon = Icons.Default.LiveTv,
                    watermarkText = "📺",
                    onClick = onNavigateToLiveTv
                )
            }
        }

        // 4. Protocol & Ultimate Pass Footer
        item {
            Spacer(modifier = Modifier.height(10.dp))
            PortalGatewayFooter()
        }
    }
}
