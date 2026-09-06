package com.disastermesh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The DisasterMesh palette: dark emergency-response console.
 *
 * One place for every colour so the screens stay consistent. Deliberately flat
 * colours rather than gradients — this has to stay readable in bad light.
 */
object MeshColors {
    /** Page background: near-black navy. */
    val Background = Color(0xFF050A14)

    /** Card background. */
    val Surface = Color(0xFF0C1524)

    /** Raised card / selected chip. */
    val SurfaceHigh = Color(0xFF14243A)

    /** Hairline borders around cards. */
    val Border = Color(0xFF1E3350)

    /** Primary accent — live mesh, links, active state. */
    val Cyan = Color(0xFF22D3EE)
    val CyanDim = Color(0xFF0E7490)

    /** Emergency / SOS. */
    val Red = Color(0xFFF43F5E)
    val RedDeep = Color(0xFF9F1239)

    /** Caution. */
    val Amber = Color(0xFFF59E0B)

    /** Safe / connected. */
    val Green = Color(0xFF22C55E)

    val TextPrimary = Color(0xFFE8F1FA)
    val TextSecondary = Color(0xFF93AECB)
    val TextDim = Color(0xFF5E7793)
}

private val DarkColors = darkColorScheme(
    primary = MeshColors.Cyan,
    onPrimary = MeshColors.Background,
    secondary = MeshColors.Amber,
    error = MeshColors.Red,
    background = MeshColors.Background,
    onBackground = MeshColors.TextPrimary,
    surface = MeshColors.Surface,
    onSurface = MeshColors.TextPrimary,
    surfaceVariant = MeshColors.SurfaceHigh,
    onSurfaceVariant = MeshColors.TextSecondary,
    outline = MeshColors.Border
)

private val MeshTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.8.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 1.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
)

/**
 * Always dark. This is an emergency console, not a themeable app, so it does not
 * follow the system light/dark setting.
 */
@Composable
fun DisasterMeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MeshTypography,
        content = content
    )
}
