package com.nanzstream.nanas.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nanzstream.nanas.data.model.CategoryType
import com.nanzstream.nanas.ui.theme.*

private const val PORTAL_LOGO_URL =
    "https://lh3.googleusercontent.com/aida/AEtjO1VBFej54aDr46OTJZgvJKWpfVEX2gDaW7El5aD6_l7VtD3FN5xR2foQCzgYZmGmKtCwp4rU5JMnOL7nnkx1eELyyFQktiArdAr7zZr1o2mRsMZmWPwiVYq2odSSNw-gwiN-FrT0NcBt9asLWzBPdRMicYkVuGTMbsdCi_khctLVzzkYWAbQ9WWDKpee95vh0Ub5EtbwZaIEk4LnV-NUsKFvwPYYrNz4Z3eB07w2HGKwp5fBch6Qi861zGEO"

@Composable
fun PortalGatewayHeader(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Logo + Branding
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceCharcoal)
                    .border(1.dp, BorderHairline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = PORTAL_LOGO_URL,
                    contentDescription = "NanzStream Symbol",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column {
                Text(
                    text = "NANZSTREAM",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    lineHeight = 16.sp
                )
                Text(
                    text = "PORTAL GATEWAY",
                    color = TextDim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
            }
        }

        // Right: System Online Pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(SurfaceElevated)
                .border(1.dp, BorderHairline, RoundedCornerShape(100.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = pulseAlpha))
            )
            Text(
                text = "SYSTEM ONLINE",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun PortalGatewayIntro(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(SurfaceCharcoal)
                .border(1.dp, BorderHairline, RoundedCornerShape(100.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "MULTI-VERSE HUB",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pilih Universe Anda",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Akses tak terbatas ke tiga dimensi visual narrative dalam satu platform monolitik.",
            color = TextDim,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp)
        )
    }
}

@Composable
fun UniverseCard(
    title: String,
    subtitle: String,
    actionText: String,
    badgeCount: String,
    badgeHighlight: String,
    icon: ImageVector,
    watermarkText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.98f else 1.0f, label = "cardScale")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPressed) SurfaceHighlight else SurfaceElevated)
            .border(1.dp, if (isPressed) BorderProminent else BorderHairline, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(16.dp)
    ) {
        // Background Watermark Symbol
        Text(
            text = watermarkText,
            color = Color.White.copy(alpha = 0.04f),
            fontSize = 80.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 20.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Icon + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCharcoal)
                        .border(1.dp, BorderHairline, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Count Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(SurfaceCharcoal)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeCount,
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Highlight Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (title == "KOMIK") SurfaceActive else Color.White)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = badgeHighlight,
                            color = if (title == "KOMIK") Color.White else CanvasBlack,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Middle: Title & Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = subtitle,
                    color = TextDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            // Bottom: Action Button Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = actionText,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isPressed) Color.White else TextDim)
                )
            }
        }
    }
}

@Composable
fun PortalGatewayFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Account Pass Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceCharcoal)
                .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SurfaceHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Pro Sovereign",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ULTIMATE PASS • ACTIVE",
                        color = TextDim,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderHairline, RoundedCornerShape(100.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ACTIVE",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Protocol Info
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "1 AKUN • 3 DUNIA HIBURAN",
                color = TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "NANZSTREAM PROTOCOL V4.2",
                color = TextDim.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
