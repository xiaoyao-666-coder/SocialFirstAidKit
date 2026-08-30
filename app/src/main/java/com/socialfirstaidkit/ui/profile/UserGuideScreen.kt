package com.socialfirstaidkit.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun UserGuideScreen(
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("使用指南") },
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
            // 应用简介
            GuideSection(
                title = "应用简介",
                content = """
                    社交急救包是一款面向日常沟通场景的 AI 社交辅助工具，帮助你更稳妥地回复消息，并完成朋友圈创作。

                    主要功能包括：
                    • App 内消息助手：管理联系人、会话、截图 OCR、上下文回复和单条回复
                    • 实时悬浮球：在微信、QQ 等聊天界面上快速识别当前屏幕并生成回复建议
                    • 截图识别：从聊天截图中提取真实消息，过滤状态栏、输入框、引用消息和无关文字
                    • 朋友圈创作：基于图片生成朋友圈文案和专属分享图
                    • 个人表达档案：用问卷与历史反馈建立你的表达偏好，辅助回复更像你
                    • 大模型配置：为 OCR、对话分析、回复生成、图片理解、图片生成等功能分别指定模型
                    • 数据与权限管理：管理无障碍服务、缓存、历史和本地数据
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 快速开始
            GuideSection(
                title = "快速开始",
                content = """
                    1. 打开底部导航栏选择功能
                       • 消息助手：处理聊天回复
                       • 朋友圈创作：生成朋友圈文案和分享图
                       • 个人中心：查看统计、资料和帮助

                    2. 按需授权系统权限
                       • 无障碍服务：用于聊天场景识别和消息读取
                       • 实时悬浮球启动时，如系统额外要求悬浮显示权限，请按提示授权

                    3. 配置大模型
                       • 点击个人中心右上角设置按钮，进入"大模型接入与功能分配"
                       • 在"服务商接入"里填写 vivo、MiMo、Agnes、Gemini、文心、蓝心、通义或自定义 OpenAI 兼容接口
                       • 在"功能分配"里为截图 OCR、截图对话分析、单条回复、上下文回复、身份风格描述、朋友圈图片理解、朋友圈文案和图片生成分别选择模型
                       • 自定义 OpenAI 兼容接口支持保存多个服务商配置，保存不同名称即可在功能分配中分别选择
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 消息助手使用指南
            GuideSection(
                title = "消息助手",
                content = """
                    消息助手提供两种回复方式：单条回复和对话上下文。

                    单条回复：
                    1. 选择"单条回复"
                    2. 粘贴一条对方消息
                    3. 选择对方身份、回复风格，并可填写补充说明
                    4. 点击生成后，系统会先分析对方信息，再生成回复建议
                    5. 点击建议卡片或复制按钮即可复制到剪贴板
                    6. 右下角历史按钮可查看已生成过的单条回复记录及各版本

                    对话上下文：
                    1. 选择"对话上下文"
                    2. 创建或选择一个对话（支持搜索）
                    3. 可上传聊天截图进行 OCR 识别，或从 OCR 历史导入，或手动添加消息
                    4. 系统会基于历史上下文分析场景、语气、情绪、意图和回复策略
                    5. 生成后可复制建议，也可以重新生成

                    实时悬浮球辅助：
                    1. 在设置中开启无障碍服务，并按系统提示完成必要授权
                    2. 进入微信、QQ 等聊天界面
                    3. 点击悬浮球主按钮，应用会读取当前聊天；如果无障碍无法直接读取，会自动尝试截图识别
                    4. 识别完成后生成多条回复建议，可直接复制
                    5. 星标按钮用于快速生成回复，齿轮按钮用于调整悬浮球设置，放大镜按钮用于识别当前屏幕
                    6. 长时间不用时悬浮球会自动恢复贴边状态，避免遮挡聊天内容

                    自定义选项：
                    • 对方身份和回复风格均支持自定义，可在设置中管理
                    • 支持 AI 自动生成身份/风格描述，调整展示顺序
                    • OCR 截图支持去重检测，可保存、导入和清空 OCR 历史
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 朋友圈创作使用指南
            GuideSection(
                title = "朋友圈创作",
                content = """
                    朋友圈创作用于把生活照片快速转成可发布内容。

                    使用方法：
                    1. 进入朋友圈创作页面
                    2. 选择一张生活场景图片
                    3. 等待 AI 识别主体、场景、氛围和主题标签
                    4. 补充想表达的信息并选择文案风格
                    5. 生成多条文案后可复制，也可选择图片加工风格生成分享图
                    6. 分享图生成后可调起系统分享面板发布

                    小贴士：
                    • 图片越清晰，识别和文案效果通常越稳定
                    • 文案风格支持自定义，可在设置 > 朋友圈创作风格中管理
                    • 分享图生成依赖图片生成接口，存在每日调用次数限制
                    • 生成结果仅供参考，发布前建议再检查一遍事实和图片效果
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 个人中心使用指南
            GuideSection(
                title = "个人中心",
                content = """
                    个人中心用于查看资料、统计和常用设置入口。

                    功能说明：
                    • 资料设置：点击头像可预览或更换，支持设置昵称、性别、年龄
                    • 个人表达档案：通过问卷建立你的表达风格画像，AI 会据此优化回复
                      - 进入"个人表达档案中心"可查看档案状态和标签摘要
                      - 可开启/关闭档案在消息助手和朋友圈创作中的应用
                    • 使用统计：查看消息助手、朋友圈创作等使用数据
                    • 关于：查看使用指南、隐私政策和用户协议
                    • 设置入口：点击右上角设置图标，进入权限与数据设置

                    数据安全：
                    • 个人资料和统计数据保存在本地
                    • 清除缓存不会删除头像和用户资料
                    • 清除所有本地数据会二次确认，请谨慎操作
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(16.dp))

            GuideSection(
                title = "设置与数据管理",
                content = """
                    设置页集中管理大模型配置、个性化选项、系统权限和本地数据。

                    大模型与 API 配置：
                    • 进入"大模型接入与功能分配"，为各功能配置 AI 模型
                    • 服务商接入：填写 Base URL、API Key、默认模型名，并可做连接测试
                    • 功能分配：按功能选择模型，不同功能可以使用不同服务商
                    • 多个 OpenAI 兼容配置：在自定义 OpenAI 区域用不同服务商名称保存，即可接入多个接口不同的模型
                    • 端侧模型：vivo 端侧模型用于离线或低延迟场景，依赖设备和模型文件支持
                    • 连接测试：可验证文本、图文、OCR、图片生成等能力是否可用

                    个性化与自定义：
                    • 个人表达档案开关：控制档案是否在各功能中生效
                    • 管理对方身份：自定义聊天对象角色，可排序、恢复、永久删除
                    • 管理回复风格：自定义语气态度，可排序、恢复、永久删除
                    • 朋友圈创作风格：自定义文案基调，可排序和管理

                    权限管理：
                    • 无障碍服务：读取聊天窗口完整上下文（可选增强）

                    隐私与安全：
                    • 可查看当前缓存大小并一键清除（保留头像和用户资料）
                    • 可清除所有本地数据（二次确认后执行）
                    • 消息缓存 24 小时自动过期，数据加密存储在设备本地
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(16.dp))

            GuideSection(
                title = "每个核心功能怎么用",
                content = """
                    1. 截图文字提取
                       • 入口：消息助手上传截图、悬浮球识别当前屏幕、OCR 历史导入
                       • 用途：把截图里的聊天气泡转成结构化消息
                       • 注意：请尽量截到完整气泡；引用消息、时间、状态栏和输入框会被过滤

                    2. 截图对话分析
                       • 入口：截图识别完成后自动执行
                       • 用途：判断联系人、会话主题、消息发送方和上下文
                       • 注意：识别到的联系人可按提示重命名，避免会话混淆

                    3. 单条语境分析
                       • 入口：消息助手 > 单条回复
                       • 用途：分析对方单条消息的意图、情绪和回复策略
                       • 注意：适合临时回复，不依赖完整聊天历史

                    4. 单条回复建议
                       • 入口：消息助手 > 单条回复 > 生成
                       • 用途：按正式、轻松、幽默等风格给出可复制回复
                       • 注意：可以补充"不要太热情""短一点"等具体要求

                    5. 上下文回复建议
                       • 入口：消息助手 > 对话上下文，或悬浮球主按钮
                       • 用途：结合多轮聊天历史生成更贴合场景的回复
                       • 注意：如果悬浮球无障碍读取失败，会尝试用当前屏幕截图识别后再生成

                    6. 身份/风格描述
                       • 入口：设置 > 管理对方身份 / 管理回复风格
                       • 用途：让 AI 帮你解释某种关系或语气，让回复策略更稳定
                       • 注意：自定义身份和风格会影响后续回复建议

                    7. 朋友圈图片理解
                       • 入口：朋友圈创作 > 选择图片
                       • 用途：识别照片主体、场景、氛围、适合表达的主题
                       • 注意：图片越清晰，识别和文案越稳定

                    8. 朋友圈文案生成
                       • 入口：朋友圈创作 > 图片理解后填写补充信息
                       • 用途：生成多条朋友圈文案，可按自然、文艺、幽默等风格调整
                       • 注意：发布前请自行核对事实、地点、人物和语气

                    9. 朋友圈分享图生成
                       • 入口：朋友圈创作 > 分享图
                       • 用途：基于图片和文案生成可分享图片
                       • 注意：图片生成可能消耗第三方额度，结果请以实际服务返回为准

                    10. 个人表达档案
                        • 入口：个人中心 > 个人表达档案中心
                        • 用途：记录你的表达习惯、常用语气和沟通偏好
                        • 注意：可单独控制是否用于消息助手或朋友圈创作
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 常见问题
            GuideSection(
                title = "常见问题",
                content = """
                    Q: 单条回复和对话上下文有什么区别？
                    A: 单条回复适合临时回复一条消息；对话上下文适合基于一段聊天历史生成更贴合语境的回复，支持截图 OCR 导入。

                    Q: AI 生成失败怎么办？
                    A: 请检查网络连接、权限状态，并稍后重试。也可以在设置中检查大模型配置是否正确。

                    Q: 个人表达档案是什么？
                    A: 通过一份简短问卷，AI 会归纳你的表达习惯和偏好标签。开启后，消息助手和朋友圈创作会参考你的表达风格优化回复。

                    Q: 数据会保存在哪里？
                    A: 个人资料、统计和缓存主要保存在设备本地。清除数据前请注意弹窗提示。

                    Q: 悬浮球提示未识别到当前聊天怎么办？
                    A: 请确认无障碍服务仍在开启，聊天界面中有可见消息气泡；如果聊天软件限制无障碍读取，悬浮球会尝试截图识别当前屏幕。

                    Q: 多个 OpenAI 兼容模型怎么接？
                    A: 在设置 > 大模型接入与功能分配 > 服务商接入中填写服务商名称、Base URL、API Key 和模型名。保存不同名称后，可在功能分配里分别选择。
                """.trimIndent()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 联系支持
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "需要帮助？",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "如果您在使用过程中遇到任何问题，或有功能建议，欢迎通过以下方式联系我们：",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 邮箱：zhnxnlng@gmail.com",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GuideSection(
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = content,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
