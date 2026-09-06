package com.disastermesh.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * DisasterMesh design system.
 *
 * Direction: a calm, high-contrast dark console. Credible during an emergency
 * rather than dramatic — colour is reserved for meaning, never decoration.
 *
 * Every colour has one semantic job. Components reference these tokens by name
 * and never inline raw hex, so the palette stays consistent and changeable.
 */
object Mesh {

    // --- Surfaces: four dark levels build hierarchy through contrast rather
    // --- than through a border on every single card.
    object Surface {
        /** The page itself. Not pure black — keeps depth readable on OLED. */
        val Backdrop = Color(0xFF020617)

        /** Standard card. */
        val Card = Color(0xFF0B1220)

        /** Raised or selected card. */
        val Raised = Color(0xFF141B2D)

        /** Pressed / track fill. */
        val Sunken = Color(0xFF080D1A)
    }

    /** Hairlines. Used sparingly — elevation is the primary separator. */
    object Line {
        val Subtle = Color(0xFF1B2537)
        val Strong = Color(0xFF334155)
    }

    object Text {
        val Primary = Color(0xFFF8FAFC)
        val Secondary = Color(0xFF94A3B8)
        val Tertiary = Color(0xFF64748B)

        /** For text sitting on a saturated fill. */
        val OnAccent = Color(0xFF020617)
    }

    /**
     * Semantic accents. Named for what they MEAN, not what they look like, so a
     * screen cannot accidentally use "the red one" for a non-emergency.
     */
    object Signal {
        /** The mesh itself: active links, live network, primary accent. */
        val Live = Color(0xFF38BDF8)
        val LiveDeep = Color(0xFF0EA5E9)

        /** Primary call to action. */
        val Action = Color(0xFF2563EB)

        /** Emergency / SOS only. */
        val Emergency = Color(0xFFEF4444)
        val EmergencyDeep = Color(0xFFB91C1C)

        /** Caution: degraded, waiting, cached. */
        val Warning = Color(0xFFF59E0B)

        /** Healthy, connected, safe. */
        val Ok = Color(0xFF22C55E)

        /** Inactive / off. */
        val Idle = Color(0xFF475569)
    }

    /**
     * One spacing scale. Every gap and pad in the app comes from here, which is
     * what stops the layout drifting into arbitrary padding values.
     */
    object Space {
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 20.dp
        val xxl = 24.dp
        val xxxl = 32.dp
    }

    object Radius {
        val sm = 10.dp
        val md = 14.dp
        val lg = 18.dp
        val xl = 24.dp
    }

    /** Android's minimum comfortable touch target. */
    val TouchTarget = 48.dp

    /** Node ids and coordinates: technical, fixed-width, still readable. */
    val Mono = FontFamily.Monospace
}

/**
 * Type scale. Body sits at 15sp with generous line height; nothing readable
 * drops below 12sp. Caps are reserved for short status labels only.
 */
private val MeshTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)

private val DarkColors = darkColorScheme(
    primary = Mesh.Signal.Live,
    onPrimary = Mesh.Text.OnAccent,
    secondary = Mesh.Signal.Action,
    onSecondary = Mesh.Text.Primary,
    error = Mesh.Signal.Emergency,
    onError = Mesh.Text.Primary,
    background = Mesh.Surface.Backdrop,
    onBackground = Mesh.Text.Primary,
    surface = Mesh.Surface.Card,
    onSurface = Mesh.Text.Primary,
    surfaceVariant = Mesh.Surface.Raised,
    onSurfaceVariant = Mesh.Text.Secondary,
    outline = Mesh.Line.Strong,
    outlineVariant = Mesh.Line.Subtle
)

/** Dark only: an emergency console should not change appearance by system setting. */
@Composable
fun DisasterMeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MeshTypography,
        content = content
    )
}
