package com.blooddragon.ducnhan.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Master Color Palette ────────────────────────────────────
object DragonColors {
    val DarkVoid      = Color(0xFF04000A)
    val SurfaceLayer  = Color(0xFF0D0018)
    val CardLayer     = Color(0xFF160028)
    val NeonPurple    = Color(0xFFCC00FF)
    val NeonPurpleDim = Color(0xFF7700AA)
    val NeonPurpleGlow= Color(0x40CC00FF)
    val BloodRed      = Color(0xFFFF0033)
    val MatrixGreen   = Color(0xFF00FF41)
    val CyberGold     = Color(0xFFFFAA00)
    val CyberWhite    = Color(0xFFE8D0FF)
    val DimText       = Color(0xFF9966BB)
    val BorderGlow    = Color(0xFF8800CC)
}

private val DragonColorScheme = darkColorScheme(
    primary          = DragonColors.NeonPurple,
    onPrimary        = DragonColors.DarkVoid,
    primaryContainer = Color(0xFF4A0070),
    secondary        = DragonColors.BloodRed,
    onSecondary      = Color.White,
    tertiary         = DragonColors.MatrixGreen,
    background       = DragonColors.DarkVoid,
    onBackground     = DragonColors.CyberWhite,
    surface          = DragonColors.SurfaceLayer,
    onSurface        = DragonColors.CyberWhite,
    surfaceVariant   = DragonColors.CardLayer,
    outline          = DragonColors.BorderGlow,
    error            = DragonColors.BloodRed,
    onError          = Color.White
)

private val DragonTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize   = 13.sp,
        lineHeight = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize   = 11.sp,
        lineHeight = 15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize   = 9.sp,
        lineHeight = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize   = 8.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize   = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize   = 14.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize   = 12.sp
    )
)

@Composable
fun BloodDragonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DragonColorScheme,
        typography  = DragonTypography,
        content     = content
    )
}