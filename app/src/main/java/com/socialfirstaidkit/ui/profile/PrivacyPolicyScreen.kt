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
fun PrivacyPolicyScreen(
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私政策") },
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
                text = "隐私政策",
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

            SectionTitle("引言")
            SectionContent("社交急救包重视您的隐私。本政策说明我们如何收集、使用、存储和保护您在使用本应用过程中产生的信息。")

            SectionTitle("一、我们收集的信息")
            SectionContent("您主动提供的信息包括昵称、性别、年龄、头像、表达档案问卷、自定义身份、自定义回复风格、朋友圈文案风格、图片生成描述、模型接口地址、模型名称和 API Key 等。")

            SectionContent("功能相关数据包括待回复消息、聊天截图、OCR 识别结果、聊天会话上下文、联系人与会话名称、悬浮球识别结果、朋友圈图片、头像，以及相关生成结果。")

            SectionContent("本应用会在本地记录使用统计、图片生成调用次数、缓存大小、模型配置状态和必要的错误日志，用于功能展示、限额控制、连接测试和问题排查。")

            SectionTitle("二、我们如何使用信息")
            SectionContent("我们使用这些信息来生成单条回复、上下文回复、识别聊天截图、过滤引用消息、生成朋友圈文案和分享图、建立个人表达档案、执行模型连接测试、维护本地统计和改进应用体验。")

            SectionTitle("三、数据存储与安全")
            SectionContent("个人资料、配置、统计、历史和缓存主要保存在您的设备本地。API Key 等敏感配置使用加密存储。消息缓存会按应用策略过期，您也可以在设置中清除缓存或所有本地数据。")

            SectionContent("发送给第三方 AI 服务的内容仅包含当前功能所需的最小必要信息，例如当前消息、必要的上下文、待识别截图、待理解图片或生成提示词。传输时使用加密连接。我们不单独维护服务器副本，也不会将您的内容用于训练 AI 模型。")

            SectionTitle("四、第三方服务")
            SectionContent("本应用可能调用大语言模型、端侧模型、视觉理解、图片生成、OCR、语音识别等服务，用于消息回复、朋友圈创作、截图识别、连接测试和后续扩展能力。您在设置中选择或填写的服务商可能包括 vivo、MiMo、Agnes、Gemini、文心、蓝心、通义以及自定义 OpenAI 兼容接口。第三方服务提供商受其各自隐私政策和服务条款约束。")

            SectionTitle("五、您的权利")
            SectionContent("您可以随时查看和修改个人资料、管理表达档案、调整自定义身份和风格、修改或删除模型配置、撤回系统权限、清除缓存或删除本地数据。卸载应用会删除系统管理范围内的本地数据。")

            SectionTitle("六、权限说明")
            SectionContent("本应用可能按需请求网络访问、系统通知、悬浮窗、无障碍服务、通知监听、录音、图片/媒体读取、文件读写和管理文件权限。网络用于连接 AI 服务；悬浮窗用于展示实时辅助入口；无障碍用于识别当前聊天界面、读取可见消息和执行粘贴；通知监听用于辅助识别聊天通知；图片/媒体与文件权限用于选择截图、头像、朋友圈图片、端侧模型文件和保存生成结果；录音用于语音识别等扩展能力。")

            SectionTitle("七、悬浮球与无障碍说明")
            SectionContent("实时悬浮球只在您主动开启相关权限后工作。无障碍服务用于识别当前聊天界面中的可见文字、按钮和输入框，以便生成回复建议或辅助粘贴。若无障碍无法读取当前聊天，本应用可能在 Android 系统允许的情况下调用无障碍截图能力，仅用于当前屏幕识别。")

            SectionTitle("八、儿童隐私")
            SectionContent("本应用不面向 14 岁以下未成年人。如监护人发现未成年人向本应用提供了个人信息，可联系我们处理。")

            SectionTitle("九、政策更新")
            SectionContent("我们可能根据功能调整更新本隐私政策。更新后的政策会在应用中展示，继续使用本应用即表示您接受更新后的政策。")

            SectionTitle("十、联系我们")
            SectionContent("如有隐私相关问题、数据删除请求或功能建议，可通过 zhnxnlng@gmail.com 联系我们。")

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "感谢您使用社交急救包。",
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
