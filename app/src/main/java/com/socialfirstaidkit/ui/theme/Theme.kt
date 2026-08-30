package com.socialfirstaidkit.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors

private val LightColorScheme = lightColorScheme(
    primary = SocialAidModernColors.Purple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFE9FF),
    onPrimaryContainer = SocialAidModernColors.Ink,
    secondary = Color(0xFF43C86F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7F8ED),
    onSecondaryContainer = SocialAidModernColors.Ink,
    tertiary = Color(0xFF8E78FF),
    tertiaryContainer = Color(0xFFFFF4EA),
    background = Color(0xFFFAF7FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF3EEFF),
    onBackground = SocialAidModernColors.Ink,
    onSurface = SocialAidModernColors.Ink,
    onSurfaceVariant = SocialAidModernColors.Muted,
    outline = Color(0xFFD7CDEF),
)

private val SocialAidShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

@Composable
fun SocialFirstAidTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context)
        }
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = SocialAidShapes,
        content = content,
    )
}
