package cn.levanll.inventory.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** 主题色 */
val Brand = Color(0xFF007DFF)
val BrandDark = Color(0xFF3F97FF)
val Warning = Color(0xFFE84026)
val WarningDark = Color(0xFFFF6B54)
val Success = Color(0xFF41BA41)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Brand,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF182431),
    surface = Color.White,
    onSurface = Color(0xFF182431),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0x99182431),
    error = Warning
)

private val DarkColors = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color.White,
    secondary = BrandDark,
    background = Color.Black,
    onBackground = Color(0xFFE5E5E5),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFE5E5E5),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0x99E5E5E5),
    error = WarningDark
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp)
)

@Composable
fun InventoryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
