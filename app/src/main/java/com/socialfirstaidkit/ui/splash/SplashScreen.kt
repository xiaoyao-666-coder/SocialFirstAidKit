package com.socialfirstaidkit.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.socialfirstaidkit.R
import com.socialfirstaidkit.core.ui.theme.SocialAidModernColors
import com.socialfirstaidkit.core.ui.theme.SocialAidPosterBackground
import com.socialfirstaidkit.core.ui.theme.SocialAidPrimaryBrush
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    viewModel: SplashViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val progress = remember { Animatable(0f) }
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.0" }
    }

    LaunchedEffect(Unit) {
        // 模拟加载进度
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            )
        )
        delay(300) // 加载完成后稍微停顿
        onSplashComplete()
    }

    SocialAidPosterBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部留白
            Spacer(modifier = Modifier.height(80.dp))

            // 中间内容区域
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 应用图标 - 与启动器 Logo 保持一致
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_full),
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 应用名称 - 中文
                Text(
                    text = "社交急救包",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = SocialAidModernColors.PurpleDeep,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth()
                )

                // 应用名称 - 英文
                Text(
                    text = "Social First Aid Kit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = SocialAidModernColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // 底部内容区域
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp)
            ) {
                // 标语
                Text(
                    text = "让每一次沟通都得体、自然、有趣",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = SocialAidModernColors.Ink
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Powered by
                Text(
                    text = "Powered by AI Engine 2026",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = SocialAidModernColors.Muted
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 进度条容器
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.7f))
                ) {
                    // 进度条
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.value)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SocialAidPrimaryBrush)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 版本信息
                Text(
                    text = "V$versionName",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = SocialAidModernColors.Muted
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "2026.06.24 更新",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = SocialAidModernColors.Muted
                )
            }
        }
    }
}
