package com.socialfirstaidkit.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAgreementScreen(
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户协议") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                text = "用户协议",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "生效日期：2026年6月24日",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle("一、协议接受")
            SectionContent("欢迎使用社交急救包。使用本应用即表示您已阅读、理解并同意本协议。若您不同意本协议内容，请停止使用本应用。")

            SectionTitle("二、服务内容")
            SectionContent("本应用面向日常社交沟通场景，提供 App 内消息助手、实时悬浮球、聊天截图 OCR、上下文回复建议、单条回复建议、朋友圈创作、个人中心、表达档案、大模型配置和本地数据管理等功能。AI 生成内容仅供参考，不构成专业建议，用户应自行判断是否采用。")

            SectionContent("部分功能依赖网络、无障碍、悬浮窗、通知监听、图片/媒体读取、文件读写、录音等系统权限。未授权时，对应功能可能无法正常使用。您可以随时在系统设置中撤回权限。")

            SectionTitle("三、用户行为规范")
            SectionContent("您承诺遵守法律法规和聊天软件、AI 服务商的相关规则，不利用本应用生成、传播违法违规、虚假误导、侵权、骚扰、诈骗、恶意诱导或侵犯他人隐私的内容。上传用于识别、分析或生成的截图、照片、头像或音频时，应确保您拥有相应权利，并尊重他人的隐私、肖像和知识产权。")

            SectionTitle("四、用户内容")
            SectionContent("您在使用本应用过程中输入的消息、上传的截图或图片、识别到的聊天内容、设置的风格、个人表达档案和个人资料等内容归您或原权利人所有。我们仅在提供功能所需范围内处理这些内容，不会将其用于训练 AI 模型、对外展示或商业目的。")

            SectionTitle("五、第三方服务")
            SectionContent("本应用的 AI 能力可能调用您配置或选择的第三方服务，包括 vivo、MiMo、Agnes、Gemini、文心、蓝心、通义以及自定义 OpenAI 兼容接口。第三方服务的可用性、质量、计费、速率限制和数据处理规则由对应服务提供商负责，请您自行了解并遵守其服务条款。")

            SectionContent("如果您在设置中填写自定义 Base URL、API Key 或多个 OpenAI 兼容模型配置，请确保这些接口由您合法持有或有权使用。因错误配置、额度消耗、接口不可用或第三方服务返回异常导致的问题，由对应服务或配置使用者承担。")

            SectionTitle("六、免责声明")
            SectionContent("AI 生成内容可能存在错误、不完整、不适当、语气不准确或与事实不符之处。发布朋友圈、发送回复、使用生成图片或复制悬浮球建议前，请自行核实事实、语气、图片效果和可能产生的影响。因使用生成内容造成的后果由用户自行承担。")

            SectionTitle("七、数据与权限")
            SectionContent("个人资料、配置、统计、历史和缓存主要保存在设备本地。API Key 等敏感配置会加密存储。您可以在系统设置中撤回权限，也可以在应用设置中清除缓存或本地数据。清除所有本地数据后，联系人、会话、OCR 历史、表达档案、模型配置和统计可能无法恢复。")

            SectionTitle("八、功能边界")
            SectionContent("本应用用于辅助组织语言，不保证识别结果、模型回复、图片理解或图片生成结果完全准确。实时悬浮球和无障碍识别能力受系统版本、聊天软件界面、设备性能和权限状态影响，可能出现识别失败、延迟或结果不完整。")

            SectionTitle("九、协议变更")
            SectionContent("我们可能根据功能调整更新本协议。更新后的协议会在应用中展示，继续使用本应用即表示您接受更新后的协议。")

            SectionTitle("十、联系我们")
            SectionContent("如有任何问题、建议、投诉或协议相关咨询，可通过 zhnxnlng@gmail.com 联系我们。")

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "感谢您阅读本用户协议。",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SectionContent(content: String) {
    Text(
        text = content,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}
