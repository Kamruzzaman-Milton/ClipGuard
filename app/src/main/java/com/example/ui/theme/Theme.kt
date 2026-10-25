package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

private val ClipGuardLightColors = lightColorScheme(
    primary = HyperBlue,
    onPrimary = Color.White,
    primaryVariant = HyperBlueDark,
    onPrimaryVariant = Color.White,
    disabledPrimary = Color(0xFFB5D6FF),
    primaryContainer = HyperBlueSoftLight,
    onPrimaryContainer = HyperBlueDark,
    secondary = LightSecondarySurface,
    onSecondary = LightPrimaryText,
    secondaryVariant = LightSecondarySurface,
    onSecondaryVariant = LightSecondaryText,
    disabledSecondary = LightSecondarySurface,
    secondaryContainer = LightSecondarySurface,
    onSecondaryContainer = LightPrimaryText,
    background = LightBackground,
    onBackground = LightPrimaryText,
    onBackgroundVariant = LightSecondaryText,
    surface = LightCardSurface,
    onSurface = LightPrimaryText,
    surfaceVariant = LightCardSurface,
    onSurfaceVariantSummary = LightSecondaryText,
    onSurfaceVariantActions = LightSecondaryText,
    disabledOnSecondaryVariant = Color(0xFFB0B5C0),
    surfaceContainer = LightCardSurface,
    onSurfaceContainer = LightPrimaryText,
    onSurfaceContainerVariant = LightSecondaryText,
    surfaceContainerHigh = LightSecondarySurface,
    onSurfaceContainerHigh = LightPrimaryText,
    surfaceContainerHighest = LightSecondarySurface,
    onSurfaceContainerHighest = LightPrimaryText,
    outline = LightDivider,
    dividerLine = LightDivider,
    windowDimming = Color(0x66000000)
)

/**
 * True AMOLED Dark Theme override for Miuix:
 * - Background pure #000000
 * - Cards #0D0D0D
 * - Subtle dividers (#121212), no elevation tint, no surface lighter than #121212
 */
private val ClipGuardAmoledDarkColors = darkColorScheme(
    primary = HyperBlue,
    onPrimary = Color.White,
    primaryVariant = HyperBlueDark,
    onPrimaryVariant = Color.White,
    disabledPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF121212),
    onPrimaryContainer = HyperBlue,
    secondary = Color(0xFF121212),
    onSecondary = AmoledPrimaryText,
    secondaryVariant = Color(0xFF121212),
    onSecondaryVariant = AmoledSecondaryText,
    disabledSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Color(0xFF121212),
    onSecondaryContainer = AmoledPrimaryText,
    background = AmoledBackground,
    onBackground = AmoledPrimaryText,
    onBackgroundVariant = AmoledSecondaryText,
    surface = AmoledCardSurface,
    onSurface = AmoledPrimaryText,
    surfaceVariant = AmoledCardSurface,
    onSurfaceVariantSummary = AmoledSecondaryText,
    onSurfaceVariantActions = AmoledSecondaryText,
    disabledOnSecondaryVariant = Color(0xFF454A54),
    surfaceContainer = AmoledCardSurface,
    onSurfaceContainer = AmoledPrimaryText,
    onSurfaceContainerVariant = AmoledSecondaryText,
    surfaceContainerHigh = AmoledElevatedSurface,
    onSurfaceContainerHigh = AmoledPrimaryText,
    surfaceContainerHighest = AmoledElevatedSurface,
    onSurfaceContainerHighest = AmoledPrimaryText,
    outline = AmoledDivider,
    dividerLine = AmoledDivider,
    windowDimming = Color(0xB3000000)
)

@Composable
fun ClipGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) ClipGuardAmoledDarkColors else ClipGuardLightColors
    MiuixTheme(
        colors = colors,
        content = content
    )
}
