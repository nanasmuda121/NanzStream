package com.nanzstream.nanas.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Pure Monochrome Glassmorphism Palette
val DarkBg = Color(0xFF0A0A0C)
val DarkSurface = Color(0xFF0D0D12)
val DarkCard = Color(0xFF121214)
val DarkCardElevated = Color(0xFF18181B)

// Tailwind HTML Reference Palette
val CanvasBlack = Color(0xFF0A0A0C)
val SurfaceCharcoal = Color(0xFF121214)
val SurfaceElevated = Color(0xFF18181B)
val SurfaceHighlight = Color(0xFF27272A)
val SurfaceActive = Color(0xFF3F3F46)
val TextDim = Color(0xFF71717A)
val BorderHairline = Color(0x14FFFFFF)
val BorderProminent = Color(0x33FFFFFF)

// Frosted Glass Colors
val GlassBackground = Color(0x12FFFFFF)
val GlassBackgroundHover = Color(0x22FFFFFF)
val GlassBackgroundActive = Color(0x35FFFFFF)
val GlassSurface = Color(0x18FFFFFF)

// Glass Borders & Highlights
val GlassBorder = Color(0x2EFFFFFF)
val GlassBorderSubtle = Color(0x18FFFFFF)
val GlassBorderActive = Color(0x80FFFFFF)
val GlassBorderHighlight = Color(0x55FFFFFF)

// Monochrome Typography
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFD1D1D6)
val TextMuted = Color(0xFF8E8E93)
val TextDark = Color(0xFF060608)
val AccentCyan = Color(0xFF00E5FF)

// Monochrome Gradients
val GlassBorderGradient = Brush.verticalGradient(
    listOf(
        Color(0x45FFFFFF),
        Color(0x10FFFFFF)
    )
)

val GlassCardGradient = Brush.verticalGradient(
    listOf(
        Color(0x1EFFFFFF),
        Color(0x0AFFFFFF)
    )
)

val HeroOverlayGradient = Brush.verticalGradient(
    listOf(
        Color.Transparent,
        Color(0x99060608),
        Color(0xFF060608)
    )
)

val SilverGradient = Brush.linearGradient(
    listOf(
        Color(0xFFFFFFFF),
        Color(0xFFE0E0E6),
        Color(0xFFB0B0B8)
    )
)
