package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Section 14 — "Neon Glass Future" Color Palette
val AmoledBlack = Color(0xFF000000)
val SurfaceDark = Color(0xFF0A0E1A)
val SurfaceGlass = Color(0xCC0A0E1A)       // #0A0E1A @ 80%
val SurfaceAlt = Color(0xB3121826)         // #121826 @ 70%
val ElevatedGlass = Color(0xE61A2133)      // #1A2133 @ 90%

val NeonCyan = Color(0xFF00E5FF)           // Primary
val NeonPurple = Color(0xFFB14EFF)         // Secondary
val HotPink = Color(0xFFFF2E93)            // Accent

val NeonGreen = Color(0xFF00FF88)          // Success
val AmberWarning = Color(0xFFFFB800)       // Warning
val NeonRed = Color(0xFFFF3B5C)            // Danger
val SkyBlue = Color(0xFF4EA8FF)            // Info

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B8C8)
val TextTertiary = Color(0xFF6B7280)

val GlassBorderLight = Color(0x26FFFFFF)
val GlassBorderCyan = Color(0x6600E5FF)
val GlassBorderPink = Color(0x66FF2E93)
val GlassBorderPurple = Color(0x66B14EFF)

object JarvisGradients {
    val PrimaryNeon = Brush.linearGradient(
        colors = listOf(NeonCyan, NeonPurple, HotPink)
    )

    val CyanPurple = Brush.linearGradient(
        colors = listOf(NeonCyan, NeonPurple)
    )

    val PinkPurple = Brush.linearGradient(
        colors = listOf(HotPink, NeonPurple)
    )

    val GlassSurface = Brush.linearGradient(
        colors = listOf(
            Color(0x14FFFFFF), // rgba(255,255,255,0.08)
            Color(0x05FFFFFF)  // rgba(255,255,255,0.02)
        )
    )

    val DarkCardGlass = Brush.linearGradient(
        colors = listOf(
            Color(0xD9121826),
            Color(0xB30A0E1A)
        )
    )
}
