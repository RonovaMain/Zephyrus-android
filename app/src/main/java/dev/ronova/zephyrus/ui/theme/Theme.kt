package dev.ronova.zephyrus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.ronova.zephyrus.data.Settings
import kotlin.math.abs

/** Акцентные оттенки для случая, когда динамические цвета выключены или недоступны (Android 11 и ниже). */
val AccentNames: Map<String, String> = linkedMapOf(
    "sakura" to "Сакура",
    "sky" to "Небо",
    "lavender" to "Лаванда",
    "mint" to "Мята",
    "peach" to "Персик",
)

// основной оттенок и «партнёрский» оттенок (для перелива), в градусах HSL
private val Hues: Map<String, Pair<Float, Float>> = mapOf(
    "sakura" to Pair(335f, 205f),
    "sky" to Pair(205f, 335f),
    "lavender" to Pair(262f, 205f),
    "mint" to Pair(160f, 205f),
    "peach" to Pair(18f, 45f),
)

/** HSL → Color (своя реализация, чтобы не зависеть от расширений Compose). h — градусы, s и l — 0..1. */
private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = (((h % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val rgb = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(rgb.first + m, rgb.second + m, rgb.third + m)
}

/** Фирменная палитра «Сакура» и её родственницы, собранные по тональной схеме Material 3. */
fun brandScheme(accent: String, dark: Boolean): ColorScheme {
    val (h, h2) = Hues[accent] ?: Hues.getValue("sakura")
    return if (!dark) {
        lightColorScheme(
            primary = hsl(h, .55f, .42f), onPrimary = Color.White,
            primaryContainer = hsl(h, .90f, .90f), onPrimaryContainer = hsl(h, .70f, .14f),
            secondary = hsl(h, .30f, .40f), onSecondary = Color.White,
            secondaryContainer = hsl(h, .50f, .91f), onSecondaryContainer = hsl(h, .45f, .14f),
            tertiary = hsl(h2, .60f, .38f), onTertiary = Color.White,
            tertiaryContainer = hsl(h2, .85f, .90f), onTertiaryContainer = hsl(h2, .70f, .13f),
            background = hsl(h, .60f, .985f), onBackground = hsl(h, .20f, .12f),
            surface = hsl(h, .60f, .985f), onSurface = hsl(h, .20f, .12f),
            surfaceVariant = hsl(h, .30f, .90f), onSurfaceVariant = hsl(h, .12f, .35f),
            outline = hsl(h, .10f, .50f), outlineVariant = hsl(h, .20f, .80f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = hsl(h, .55f, .97f),
            surfaceContainer = hsl(h, .50f, .955f),
            surfaceContainerHigh = hsl(h, .45f, .935f),
            surfaceContainerHighest = hsl(h, .40f, .915f),
        )
    } else {
        darkColorScheme(
            primary = hsl(h, .85f, .80f), onPrimary = hsl(h, .70f, .20f),
            primaryContainer = hsl(h, .55f, .30f), onPrimaryContainer = hsl(h, .90f, .90f),
            secondary = hsl(h, .35f, .78f), onSecondary = hsl(h, .40f, .20f),
            secondaryContainer = hsl(h, .25f, .30f), onSecondaryContainer = hsl(h, .50f, .90f),
            tertiary = hsl(h2, .75f, .78f), onTertiary = hsl(h2, .70f, .17f),
            tertiaryContainer = hsl(h2, .50f, .28f), onTertiaryContainer = hsl(h2, .85f, .90f),
            background = hsl(h, .16f, .08f), onBackground = hsl(h, .15f, .90f),
            surface = hsl(h, .16f, .08f), onSurface = hsl(h, .15f, .90f),
            surfaceVariant = hsl(h, .15f, .26f), onSurfaceVariant = hsl(h, .10f, .75f),
            outline = hsl(h, .08f, .60f), outlineVariant = hsl(h, .10f, .30f),
            surfaceContainerLowest = hsl(h, .16f, .06f),
            surfaceContainerLow = hsl(h, .16f, .10f),
            surfaceContainer = hsl(h, .16f, .12f),
            surfaceContainerHigh = hsl(h, .15f, .15f),
            surfaceContainerHighest = hsl(h, .15f, .18f),
        )
    }
}

private val ZephyrusShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun ZephyrusTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        brandScheme(settings.accent, dark)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, shapes = ZephyrusShapes, content = content)
}
