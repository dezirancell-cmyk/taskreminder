package com.example.taskreminder

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection

object Neon {
    val bg = Color(0xFF0A0E1A)
    val surface = Color(0xFF111827)
    val surface2 = Color(0xFF18223A)
    val cyan = Color(0xFF22D3EE)
    val magenta = Color(0xFFE879F9)
    val lime = Color(0xFFA3E635)
    val amber = Color(0xFFFBBF24)
    val red = Color(0xFFFB7185)
    val text = Color(0xFFF1F5F9)
    val muted = Color(0xFF94A3B8)
}

/** فونت وزیرمتن از assets/fonts (در زمان build دانلود می‌شود)؛ در نبودش فونت پیش‌فرض */
fun loadFontFamily(ctx: Context): FontFamily {
    return try {
        val names = ctx.assets.list("fonts")?.toSet() ?: emptySet()
        val fonts = mutableListOf<Font>()
        if ("Vazirmatn-Regular.ttf" in names) {
            fonts += Font("fonts/Vazirmatn-Regular.ttf", ctx.assets, FontWeight.Normal)
        }
        if ("Vazirmatn-Bold.ttf" in names) {
            fonts += Font("fonts/Vazirmatn-Bold.ttf", ctx.assets, FontWeight.Bold)
        }
        if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
    } catch (e: Exception) {
        FontFamily.Default
    }
}

private fun typographyWith(ff: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = ff),
        displayMedium = d.displayMedium.copy(fontFamily = ff),
        displaySmall = d.displaySmall.copy(fontFamily = ff),
        headlineLarge = d.headlineLarge.copy(fontFamily = ff),
        headlineMedium = d.headlineMedium.copy(fontFamily = ff),
        headlineSmall = d.headlineSmall.copy(fontFamily = ff),
        titleLarge = d.titleLarge.copy(fontFamily = ff),
        titleMedium = d.titleMedium.copy(fontFamily = ff),
        titleSmall = d.titleSmall.copy(fontFamily = ff),
        bodyLarge = d.bodyLarge.copy(fontFamily = ff),
        bodyMedium = d.bodyMedium.copy(fontFamily = ff),
        bodySmall = d.bodySmall.copy(fontFamily = ff),
        labelLarge = d.labelLarge.copy(fontFamily = ff),
        labelMedium = d.labelMedium.copy(fontFamily = ff),
        labelSmall = d.labelSmall.copy(fontFamily = ff)
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val family = remember { loadFontFamily(ctx) }
    val scheme = darkColorScheme(
        primary = Neon.cyan,
        secondary = Neon.magenta,
        tertiary = Neon.lime,
        background = Neon.bg,
        surface = Neon.surface,
        onPrimary = Neon.bg,
        onBackground = Neon.text,
        onSurface = Neon.text,
        error = Neon.red
    )
    MaterialTheme(colorScheme = scheme, typography = typographyWith(family)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
