package com.socialfirstaidkit.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialfirstaidkit.core.ui.theme.*

/**
 * 回复建议卡片组件
 *
 * 用于展示 AI 生成的单条回复建议。
 */
@Composable
fun ReplySuggestionCard(
    label: String,
    text: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCopyClick: (() -> Unit)? = null
) {
    val copyAction = onCopyClick ?: onClick

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor.copy(alpha = 0.92f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.86f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 标签和复制按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = SocialAidModernColors.PurpleDeep,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = copyAction,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        modifier = Modifier.size(18.dp),
                        tint = SocialAidModernColors.Purple
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                fontSize = 15.sp,
                color = OverlayColors.TextPrimary,
                lineHeight = 22.sp
            )
        }
    }
}

/**
 * 加载中指示器
 */
@Composable
fun AiThinkingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = SocialAidModernColors.Glass,
                shape = RoundedCornerShape(20.dp)
            )
            .border(1.dp, Color.White.copy(alpha = 0.82f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(SocialAidModernColors.PurpleSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = SocialAidModernColors.Purple,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = SocialAidModernColors.Purple,
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "AI 正在组织高情商回应...",
            fontSize = 14.sp,
            color = SocialAidModernColors.Muted,
            fontWeight = FontWeight.Medium
        )
    }
}
