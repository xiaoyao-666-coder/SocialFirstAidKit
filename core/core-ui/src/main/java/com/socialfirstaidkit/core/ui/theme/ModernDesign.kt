package com.socialfirstaidkit.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object SocialAidModernColors {
    val Ink = Color(0xFF191428)
    val Muted = Color(0xFF6E6685)
    val Purple = Color(0xFF6750D8)
    val PurpleDeep = Color(0xFF40319B)
    val PurpleSoft = Color(0xFFEFE9FF)
    val PurpleMist = Color(0xFFF7F2FF)
    val Mint = Color(0xFF43C86F)
    val MintSoft = Color(0xFFE7F8ED)
    val Glass = Color(0xEFFFFFFF)
    val GlassStrong = Color(0xFFFFFFFF)
    val GlassBorder = Color(0x99FFFFFF)
    val Hairline = Color(0xFFE3DCF7)
}

val SocialAidBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFEFF),
        Color(0xFFF8F6FF),
        Color(0xFFF1FAF5),
        Color(0xFFFFFBF6),
        Color(0xFFFFFFFF)
    )
)

val SocialAidHeroBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFFAF8FD),
        Color(0xFFF1EEF8)
    )
)

val SocialAidPrimaryBrush = Brush.linearGradient(
    colors = listOf(
        SocialAidModernColors.PurpleDeep,
        SocialAidModernColors.Purple,
        Color(0xFF8E78FF)
    )
)

@Composable
fun SocialAidPosterBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SocialAidBackgroundBrush)
    ) {
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(SocialAidModernColors.Glass)
            .border(1.dp, SocialAidModernColors.GlassBorder, RoundedCornerShape(cornerRadius))
    ) {
        content()
    }
}

@Composable
fun HeroPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.AutoAwesome,
    statusText: String? = null,
    action: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SocialAidHeroBrush)
            .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconBubble(icon = icon)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = SocialAidModernColors.Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 26.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = SocialAidModernColors.Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
            if (statusText != null) {
                Spacer(modifier = Modifier.height(12.dp))
                StatusPill(text = statusText)
            }
        }
        action()
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = SocialAidModernColors.Purple,
    background: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, SocialAidModernColors.Hairline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    leadingColor: Color = SocialAidModernColors.Mint
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.78f),
        shape = RoundedCornerShape(50),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, SocialAidModernColors.Hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(leadingColor)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = text,
                color = SocialAidModernColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = SocialAidModernColors.Ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (trailing != null) {
            StatusPill(text = trailing)
        }
    }
}
