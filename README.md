# 社交急救包（Social First Aid Kit）

[![Android CI](https://github.com/xiaoyao-666-coder/SocialFirstAidKit/actions/workflows/android.yml/badge.svg)](https://github.com/xiaoyao-666-coder/SocialFirstAidKit/actions/workflows/android.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> 当前版本：V1.0  
> Android 包名：`com.socialfirstaidkit`  
> 仓库负责人：梁镇贤

## 项目简介

社交急救包是一款面向日常社交压力场景的 AI 辅助 Android 应用，帮助用户处理聊天回复、关系沟通和朋友圈发布中的表达难题。应用将 AI 能力嵌入手机端真实社交场景，通过消息助手、聊天截图识别、实时悬浮球、朋友圈创作和个人表达档案，形成“场景识别、语境理解、建议生成、用户决策”的辅助流程。

项目使用 Kotlin 与 Jetpack Compose 开发，并按应用入口、核心数据、网络能力、大模型能力、通用 UI、消息助手、朋友圈创作和无障碍悬浮服务拆分为多个 Gradle 模块。

## 项目背景

本项目最初以《社交急救包》参加 2026 年中国高校计算机大赛 AIGC 创新赛，参赛团队为 Just Do It，来自华南农业大学。

- 共同开发者：梁镇贤、刘亦达、崔兆飞
- 仓库负责人及主要维护者：梁镇贤
- 指导老师：朱凯

仓库管理权不等同于独占著作权。项目代码及共同成果的权利归属以三位共同开发者签署的书面协议为准。

## 核心功能

### 消息助手

- 单条回复建议：结合对方身份、回复风格和补充说明生成多条候选回复。
- 上下文对话：维护联系人、多轮聊天记录和 AI 推荐回复。
- 聊天截图识别：通过 OCR 或视觉模型解析聊天双方、消息顺序和可追加内容。
- 回复风格管理：管理关系身份和回复风格，支持自定义、排序、恢复和删除。
- 个人表达档案：通过问卷和使用反馈，让建议更接近用户自身语气。
- 历史管理：保存回复历史、OCR 历史和上下文会话。

### 实时悬浮球

- 在微信、QQ 等聊天界面上快速读取当前场景并生成回复建议。
- 支持无障碍服务、通知监听和悬浮窗权限联动。
- 支持拖动、贴边收起、截图识别和当前对话辅助。

### 朋友圈创作

- 分析图片中的文字、主体、场景和情绪倾向。
- 根据图片、文案风格和补充信息生成朋友圈文案。
- 管理自定义朋友圈文案风格。
- 基于原图生成不同视觉风格的分享图。

### 个人中心与设置

- 维护昵称、头像、性别、年龄和个人表达档案。
- 查看使用统计、历史版本和表达档案摘要。
- 为 OCR、对话分析、回复生成、图片理解和图片生成分别配置模型。
- 支持 vivo、MiMo、Agnes、Gemini、文心、蓝心、通义和自定义 OpenAI 兼容接口。
- 清理缓存、本地数据库、偏好配置和历史数据。

## 使用前准备

本仓库不提供任何第三方服务的 API Key、Token 或 Secret。需要云端能力时，请在应用内“大模型接入与功能分配”页面填写自己的服务商配置。

### 赛事专用模型与接口

项目中的 vivo 云端模型、vivo 端侧模型，以及部分 MiMo、Agnes 接口能力来自赛事方提供的开发资源。普通下载者不一定能够申请到相同的模型、接口权限、端侧模型文件或 SDK。

公开版本不会提供赛事账号、API Key、模型文件，也不应分发尚未取得公开再分发授权的 `llm-sdk-release.aar`。下载者需要将相关路由替换为自己有权使用的模型服务；文本和图文能力可优先使用应用内的“自定义 OpenAI 兼容模型”。具体源码入口及替换方法见 [赛事 API 与模型替换指南](docs/API_REPLACEMENT.md)。

部分能力需要用户主动授予以下 Android 系统权限：

- 网络访问：调用 AI、OCR、视觉理解和图片生成服务。
- 悬浮窗：在其他应用上显示实时辅助入口。
- 无障碍服务：按用户操作读取当前聊天界面的可见内容。
- 通知监听：读取聊天通知以提供实时辅助。
- 图片或媒体访问：选择聊天截图、朋友圈图片和头像。
- 录音：用于语音识别或短语音能力。

## 项目架构

| 模块 | 职责 |
| --- | --- |
| `:app` | 应用入口、导航、启动页、个人中心、设置页和 Hilt 装配。 |
| `:core:core-data` | Room 数据库、本地实体与 DAO、加密偏好存储、OCR、统计和个人表达档案数据层。 |
| `:core:core-network` | OkHttp/Retrofit 网络基础设施和图片生成客户端。 |
| `:core:core-llm` | 统一 LLM 抽象、模型路由、内置服务商和自定义 OpenAI 兼容接口。 |
| `:core:core-ui` | 通用 Compose UI、主题和悬浮回复组件。 |
| `:feature:chat-assist` | 消息助手、截图 OCR、会话管理、回复历史和身份/风格管理。 |
| `:feature:social-copy` | 朋友圈图片分析、文案生成、分享图生成和风格管理。 |
| `:service:accessibility` | 通知监听、无障碍服务、聊天场景识别和实时悬浮球。 |

## 技术栈

- Kotlin、Android SDK、Java 17
- Jetpack Compose、Material 3、Navigation Compose、Coil
- 多模块 Gradle、Hilt、ViewModel、StateFlow、Coroutine
- Room、EncryptedSharedPreferences、DataStore Preferences
- OkHttp、OkHttp SSE、Retrofit、kotlinx.serialization
- ML Kit、视觉理解、大语言模型、端侧模型、图片生成和人脸检测

## 构建与运行

环境要求：

- Android Studio
- JDK 17
- Android SDK 35
- Android 8.0（API 26）及以上测试设备或模拟器

使用 Android Studio 打开项目根目录，完成 Gradle 同步后运行 `app` 配置。Windows 命令行可执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

调试 APK 默认生成在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

主要构建参数：

- `applicationId`：`com.socialfirstaidkit`
- `versionCode`：`23`
- `versionName`：`1.0`
- `minSdk`：`26`
- `targetSdk`：`35`
- `compileSdk`：`35`

## 数据与隐私

应用遵循“本地优先、最小必要”的原则。用户资料、模型配置、统计、历史记录、缓存和个人表达档案主要保存在设备本地。调用第三方 AI 服务时，只提交当前功能所需的文本、截图、图片或上下文内容。

第三方服务的可用性、计费、限速和数据处理规则由对应服务商负责。提交截图、图片或聊天内容前，请确认拥有相应权利并尊重他人隐私。

## 参与贡献

请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。建议通过 Issue 讨论需求，在独立分支完成修改，并通过 Pull Request 合并到主分支。

共同开发者及维护信息见 [AUTHORS.md](AUTHORS.md)，社区协作请遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)，一般使用问题见 [SUPPORT.md](SUPPORT.md)。

发现安全问题时，请按照 [SECURITY.md](SECURITY.md) 私下报告，不要在公开 Issue 中发布凭据或漏洞细节。

第三方软件、外部服务和视觉素材的许可状态见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。公开 GitHub 仓库前请执行 [公开发布检查清单](docs/PUBLIC_RELEASE_CHECKLIST.md)。

## 开源许可证

本项目由梁镇贤、刘亦达、崔兆飞共同开发，采用 [Apache License 2.0](LICENSE) 开源。项目归属与致谢信息见 [NOTICE](NOTICE)。

Copyright 2026 梁镇贤、刘亦达、崔兆飞
