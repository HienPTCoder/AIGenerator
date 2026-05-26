package com.devmobile.AIGenerator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryNeonPurple,
    secondary = SecondaryNeonBlue,
    tertiary = AccentPremiumGold,
    background = DeepSpaceBlack,
    surface = CardSlateDark,
    onPrimary = TextPureWhite,
    onSecondary = DeepSpaceBlack,
    onBackground = TextPureWhite,
    onSurface = TextPureWhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = TextPureWhite,
    onSecondary = TextPureWhite,
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark mode as requested by user
  dynamicColor: Boolean = false, // Disable system dynamic color to preserve custom neon theme
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
