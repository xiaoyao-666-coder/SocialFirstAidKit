package com.socialfirstaidkit.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UserAgreementDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = "用户协议与隐私政策",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Text(
                    text = "欢迎使用社交急救包！",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = "在使用本应用前，请您仔细阅读并同意以下条款：",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    SectionTitle("1. 服务说明")
                    SectionContent("本应用是一款社交辅助工具，提供 App 内消息助手、实时悬浮球、聊天截图 OCR、朋友圈创作、个人表达档案和大模型配置等功能。本应用提供的 AI 生成内容仅供参考，不构成专业建议。")

                    SectionTitle("2. 隐私保护")
                    SectionContent("我们非常重视您的隐私保护。应用不会把您的数据上传到自有服务器。为了生成回复建议、识别截图或生成朋友圈内容，必要的消息、截图、图片或提示词会通过加密连接发送给您当前选择或配置的 AI 服务提供商处理。")

                    SectionTitle("3. 内容合规")
                    SectionContent("使用生成内容前，请自行判断其准确性和适用性；上传或使用图片时，请确保您拥有相应权利，不侵犯他人隐私、肖像或知识产权。")

                    SectionTitle("4. 权限说明")
                    SectionContent("本应用可能需要网络、通知、无障碍、悬浮窗、通知监听、图片/媒体、文件读写和录音等权限。未授权时，对应功能可能不可用；您可以随时在系统设置中撤回权限。")

                    SectionTitle("5. 免责声明")
                    SectionContent("AI生成的内容可能存在错误或不准确之处，您应自行核实重要信息的准确性。我们不承担因使用AI建议而导致的任何损失。")

                    SectionTitle("6. 联系方式")
                    SectionContent("如有问题、建议或隐私相关请求，可通过 zhnxnlng@gmail.com 联系我们。")

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "点击同意即表示您已阅读并同意用户协议和隐私政策的全部条款。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAgree,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("同意并继续")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDisagree,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("不同意并退出")
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SectionContent(content: String) {
    Text(
        text = content,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}
