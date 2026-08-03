package com.hanifedma.ponder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.ponder.i18n.Lang
import com.hanifedma.ponder.i18n.Tr

val LocalPonderColors: ProvidableCompositionLocal<PonderColors> =
    staticCompositionLocalOf { DarkColors }

/** The active translator, so any composable can read a translated string. */
val LocalTr: ProvidableCompositionLocal<Tr> = staticCompositionLocalOf { Tr(Lang.EN) }

/** Shared measurements, taken from the web app's CSS custom properties. */
object Dimens {
    val radiusLg = 18.dp
    val radius = 14.dp
    val radiusSm = 11.dp

    /** `--maxw`: the reading column never grows past this. */
    val contentMaxWidth = 720.dp

    /** Inline media stays smaller than the column so it never dominates a card. */
    val mediaMaxWidth = 520.dp

    /** Height shared by the top-bar controls, buttons and inputs. */
    val controlHeight = 44.dp

    val screenPadding = 16.dp
    val cardPadding = 16.dp
}

object PonderTheme {
    val colors: PonderColors
        @Composable @ReadOnlyComposable get() = LocalPonderColors.current

    val tr: Tr
        @Composable @ReadOnlyComposable get() = LocalTr.current
}

/**
 * Type scale mirroring styles.css: 16.5px body at 1.62 line height, 13px meta,
 * 12px badges. No custom font — the system face keeps the app light and covers
 * Latin and Hangul without shipping anything.
 */
private val PonderTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(
            fontFamily = FontFamily.Default,
            fontSize = 16.5.sp,
            lineHeight = 26.7.sp,
        ),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun PonderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    tr: Tr = Tr(Lang.EN),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    // Material components (dialogs, snackbars, the text cursor and selection
    // handles) read the M3 scheme, so map the palette onto it rather than
    // letting them fall back to purple defaults.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentContrast,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accent,
            secondary = colors.accent,
            onSecondary = colors.accentContrast,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.muted,
            surfaceContainer = colors.elevated,
            surfaceContainerHigh = colors.surface2,
            surfaceContainerHighest = colors.surface3,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.danger,
            onError = Color.White,
            inverseSurface = colors.surface3,
            inverseOnSurface = colors.text,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentContrast,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.accent,
            secondary = colors.accent,
            onSecondary = colors.accentContrast,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.muted,
            surfaceContainer = colors.elevated,
            surfaceContainerHigh = colors.surface2,
            surfaceContainerHighest = colors.surface3,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.danger,
            onError = Color.White,
            inverseSurface = colors.surface3,
            inverseOnSurface = colors.text,
        )
    }

    CompositionLocalProvider(
        LocalPonderColors provides colors,
        LocalTr provides tr,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PonderTypography,
            content = content,
        )
    }
}

/** Body style used for entry text everywhere it appears. */
val EntryTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(color = PonderTheme.colors.text)
