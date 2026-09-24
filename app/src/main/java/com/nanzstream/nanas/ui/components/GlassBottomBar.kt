package com.nanzstream.nanas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanzstream.nanas.ui.theme.*

sealed class BottomNavTab(val route: String, val title: String, val icon: ImageVector) {
    data object Home : BottomNavTab("home", "Beranda", Icons.Default.Home)
    data object Categories : BottomNavTab("categories", "Kategori", Icons.Default.GridView)
    data object LiveTv : BottomNavTab("livetv", "Live TV", Icons.Default.LiveTv)
    data object Watchlist : BottomNavTab("watchlist", "Koleksi", Icons.Default.BookmarkBorder)
    data object Settings : BottomNavTab("settings", "Setelan", Icons.Default.Settings)
}

@Composable
fun GlassBottomBar(
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val tabs = listOf(
        BottomNavTab.Home,
        BottomNavTab.Categories,
        BottomNavTab.LiveTv,
        BottomNavTab.Watchlist,
        BottomNavTab.Settings
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .border(1.dp, GlassBorder, shape)
            .background(Color(0xE60D0D12))
            .padding(vertical = 6.dp, horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = currentRoute.startsWith(tab.route)
                val tint = if (isSelected) Color.White else TextMuted

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(tab.route) }
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
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
