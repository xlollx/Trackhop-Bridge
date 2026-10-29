package com.xlollx.songport.ytmbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xlollx.songport.ytmbridge.app.R

/*
 * Trackhop's look (its ui/Theme.kt and ui/Common.kt, 1.2.x): the same palette, shapes, type scale
 * and the beam, so the Bridge reads as part of Trackhop. Keep the numbers in step with Trackhop.
 */

/** The brand surface of the icon and the header: see Trackhop's Brand for the full description. */
object Brand {
    /** The band's tilt, degrees. */
    const val BeamAngle = 12f
    /** Across the band, in units of the surface's height from its edge: black, red glow, violet-blue edge, cyan, white. */
    val BeamStops: List<Pair<Float, Color>> = listOf(
        -0.45f to Color(0xFF000000), -0.008f to Color(0xFF400000), 0.008f to Color(0xFF421AFF),
        0.08f to Color(0xFF59FFFF), 0.61f to Color(0xFFFFFFFF),
    )
    /** The right end of the red sweep screened along the band (black on the left). */
    val BeamSweep = Color(0xFF990000)
}

/**
 * Paints the beam behind the content, the band's edge through the right border at [edgeY] of the
 * height. Painted once per size on the CPU and drawn as a plain image: the Screen blend of the
 * sweep through the GPU took the API 34 test emulators down. Never draw it with a GPU blend mode.
 */
fun Modifier.brandBeam(edgeY: Float = 0.35f): Modifier = drawWithCache {
    val w = (size.width * BEAM_SCALE).toInt().coerceAtLeast(1)
    val h = (size.height * BEAM_SCALE).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    paintBeam(android.graphics.Canvas(bitmap), w, h, edgeY)
    val image = bitmap.asImageBitmap()
    val dst = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
    onDrawBehind { drawImage(image, dstSize = dst, filterQuality = FilterQuality.Low) }
}

/** The beam is all soft gradients: half resolution is indistinguishable, and a quarter of the memory. */
private const val BEAM_SCALE = 0.5f

/** The beam on a software [android.graphics.Canvas] (a bitmap's) of [w]×[h]. */
private fun paintBeam(c: android.graphics.Canvas, w: Int, h: Int, edgeY: Float) {
    val rad = Math.toRadians(Brand.BeamAngle.toDouble())
    val nx = Math.sin(rad).toFloat(); val ny = -Math.cos(rad).toFloat()
    val ax = w.toFloat(); val ay = h * edgeY
    val first = Brand.BeamStops.first().first
    val span = Brand.BeamStops.last().first - first
    val colors = Brand.BeamStops.map { it.second.toArgb() }.toIntArray()
    val positions = Brand.BeamStops.map { (it.first - first) / span }.toFloatArray()
    val base = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(
            ax + nx * first * h, ay + ny * first * h, ax + nx * (first + span) * h, ay + ny * (first + span) * h,
            colors, positions, android.graphics.Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), base)
    val sweep = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, w.toFloat(), 0f, android.graphics.Color.BLACK, Brand.BeamSweep.toArgb(), android.graphics.Shader.TileMode.CLAMP)
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), sweep)
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3FD9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF1B0062),
    secondary = Color(0xFF615B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFFB4206A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E8),
    onTertiaryContainer = Color(0xFF3E0021),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFE7E0EE),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F2FC),
    surfaceContainer = Color(0xFFF0ECF7),
    surfaceContainerHigh = Color(0xFFEAE6F1),
    surfaceContainerHighest = Color(0xFFE4E0EB),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCBC4D1),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC8BFFF),
    onPrimary = Color(0xFF2B0A9A),
    primaryContainer = Color(0xFF4327C0),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFCBC3DB),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF494458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFFFB0D0),
    onTertiary = Color(0xFF5F1139),
    tertiaryContainer = Color(0xFF8E1B55),
    onTertiaryContainer = Color(0xFFFFD8E8),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E1EA),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1EA),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
)

/** Cards large and soft, elements inside them medium, as in Trackhop. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Trackhop's type scale on the system font: bold, slightly tighter titles. */
private val Display = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.merge(Display),
        displayMedium = t.displayMedium.merge(Display),
        displaySmall = t.displaySmall.merge(Display),
        headlineLarge = t.headlineLarge.merge(Display),
        headlineMedium = t.headlineMedium.merge(Display),
        headlineSmall = t.headlineSmall.merge(Display.copy(letterSpacing = (-0.25).sp)),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = t.bodyMedium.copy(lineHeight = 20.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Trackhop's brand colours, light or dark as the system is. */
@Composable
fun BridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

/** The mark in white on the glass tile: white at 18 % with a faint lighter edge, corners at 0.24 of the side. */
@Composable
fun BrandTile(size: Dp = 40.dp) {
    val shape = RoundedCornerShape(size * 0.24f)
    Box(
        Modifier.size(size).clip(shape).background(Color.White.copy(alpha = 0.18f)).border(1.dp, Color.White.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_brand_mark), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.8f))
    }
}

/**
 * The header on the beam: the tile, the title and a subtitle, white on the dark side of the band,
 * where it reads above 7:1. The beam is the brand's whatever the colour scheme.
 */
@Composable
fun HeroHeader(title: String, subtitle: String) {
    Box(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .brandBeam()
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandTile(40.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
