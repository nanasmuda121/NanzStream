package com.nanzstream.nanas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanzstream.nanas.ui.theme.*
import java.util.Calendar

data class DayScheduleInfo(
    val index: Int,
    val dayName: String,
    val shortName: String,
    val webtoonSlug: String
)

val SCHEDULE_DAYS = listOf(
    DayScheduleInfo(0, "Senin", "SEN", "monday"),
    DayScheduleInfo(1, "Selasa", "SEL", "tuesday"),
    DayScheduleInfo(2, "Rabu", "RAB", "wednesday"),
    DayScheduleInfo(3, "Kamis", "KAM", "thursday"),
    DayScheduleInfo(4, "Jumat", "JUM", "friday"),
    DayScheduleInfo(5, "Sabtu", "SAB", "saturday"),
    DayScheduleInfo(6, "Minggu", "MIN", "sunday")
)

fun getTodayScheduleIndex(): Int {
    val cal = Calendar.getInstance()
    return when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }
}

@Composable
fun DayScheduleBar(
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayIndex = remember { getTodayScheduleIndex() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (selectedDayIndex in 0 until SCHEDULE_DAYS.size) {
            listState.animateScrollToItem(selectedDayIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(SCHEDULE_DAYS) { day ->
            val isSelected = day.index == selectedDayIndex
            val isToday = day.index == todayIndex

            val bg = if (isSelected) Color.White else SurfaceElevated
            val textCol = if (isSelected) CanvasBlack else TextPrimary
            val borderCol = when {
                isSelected -> Color.White
                isToday -> Color(0xFF10B981) // Emerald highlight for today
                else -> BorderHairline
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(bg)
                    .border(1.dp, borderCol, RoundedCornerShape(100.dp))
                    .clickable { onSelectDay(day.index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isToday) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF059669) else Color(0xFF10B981))
                    )
                }

                Text(
                    text = day.dayName,
                    color = textCol,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 0.2.sp
                )

                if (isToday && !isSelected) {
                    Text(
                        text = "• Hari Ini",
                        color = Color(0xFF10B981),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
