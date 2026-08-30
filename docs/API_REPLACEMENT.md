# 赛事 API 与模型替换指南

## 为什么需要替换

本项目最初用于赛事开发。vivo 云端模型、vivo 端侧模型，以及部分 MiMo、Agnes 接口由赛事方提供。公开源码不代表这些服务、SDK、模型或配额也向公众开放，下载者不一定能够取得相同权限。

项目不会提供赛事账号、API Key、Token、Secret、端侧模型文件或未获公开再分发授权的 SDK。请仅使用自己有权访问的服务，并遵守对应服务商条款。

## 最简单的替换方式

对于文本生成和支持 OpenAI 消息格式的图文理解服务，通常无需改写 Provider：

1. 打开应用的“设置” -> “大模型接入与功能分配”。
2. 新建“自定义 OpenAI 兼容模型”。
3. 填写服务商提供的 `Base URL`、`API Key` 和模型名称。
4. 将单条分析、回复生成、上下文回复、朋友圈文案等功能槽位切换到该模型。
5. 图文理解仅在目标服务支持 OpenAI 兼容的图片消息格式时启用。

自定义兼容接口实现位于：

- `core/core-llm/src/main/java/com/socialfirstaidkit/core/llm/impl/CustomLlmProvider.kt`
- `core/core-llm/src/main/java/com/socialfirstaidkit/core/llm/routing/CustomOpenAiProfiles.kt`

## 需要关注的源码入口

| 能力 | 赛事实现或配置入口 | 下载者应如何处理 |
| --- | --- | --- |
| 模型清单及默认路由 | `core/core-llm/.../routing/AiModelRegistry.kt` | 删除无权限模型，修改 `defaultRoute()`，将默认槽位指向可用模型。 |
| Provider 路由 | `core/core-llm/.../routing/AiRouteResolver.kt` | 增加自己的 Provider，或让功能槽位使用 `CUSTOM_OPENAI`。 |
| API Key 与 Base URL 注入 | `app/.../di/LlmKeyModule.kt` | 不要写死密钥；继续从 `SecureStorage` 或用户设置读取。 |
| vivo 云端文本/视觉 | `core/core-llm/.../impl/VivoAigcProvider.kt` | 换为自有 OpenAI 兼容服务，或按新服务商协议实现 `LlmProvider`。 |
| vivo 端侧模型 | `core/core-llm/.../impl/VivoOnDeviceProvider.kt` | 无合法 SDK 和模型时移除该 Provider、路由及设置入口。 |
| vivo 端侧 AAR | `core/core-llm/build.gradle.kts`、`core/core-llm/libs/` | 公开仓库不得携带；使用公开依赖或自己有权获得的 SDK。 |
| MiMo | `core/core-llm/.../impl/MimoProvider.kt` | 使用自己的 MiMo 权限，或切换为自定义兼容 Provider。 |
| Agnes | `core/core-llm/.../impl/AgnesProvider.kt` | 使用自己的 Agnes 权限，或切换为自定义兼容 Provider。 |
| vivo OCR | `core/core-data/.../ocr/VivoOcrProcessor.kt` | 无权限时使用 `OcrProcessor.kt` 中的 ML Kit 本地 OCR，或接入其他 OCR。 |
| vivo 短语音识别 | `feature/chat-assist/.../voice/VivoShortVoiceInputEngine.kt` | 改接有权使用的 ASR 服务，并更新 `VOICE_SHORT_RECOGNITION` 路由。 |
| 图片生成 | `core/core-network/.../imagegen/ImageGenerationClient.kt` | 配置自己可用的图片生成接口，或实现对应服务商请求格式。 |
| 设置页面 | `app/.../ui/settings/ModelSettingsScreen.kt`、`ModelSettingsViewModel.kt` | 隐藏无法使用的赛事模型，展示下载者实际支持的模型。 |

表格中的 `...` 仅为缩短路径显示，实际包名前缀为 `src/main/java/com/socialfirstaidkit/`。

## vivo 端侧能力的特殊处理

`llm-sdk-release.aar` 不是 Maven Central 或 Google Maven 中的普通公开依赖。当前已知文件内部没有许可证或 NOTICE，且端侧模型还依赖特定设备能力和模型目录。因此，公开版本应采用下列方案之一：

1. 推荐：公开版移除 vivo 端侧 Provider、设置入口和默认路由，保留云端或自定义模型能力。
2. 可选模块：将端侧适配器拆到不默认启用的独立模块，由具备合法 SDK 的开发者自行接入。
3. 获得授权后分发：只有供应商书面确认允许公开再分发时，才可提交 AAR，并按授权要求附带许可证和 NOTICE。

仅在文档中注明来源，不能替代再分发授权。

## 替换后的最低验证

- 全新克隆中不含赛事 AAR、模型文件和任何真实凭据。
- `CHAT_SINGLE_REPLY_GENERATION` 与 `CHAT_CONTEXTUAL_REPLY_GENERATION` 可使用替代模型完成请求。
- OCR 无云端权限时仍可回退到 ML Kit 本地识别。
- 不可用模型不会出现在默认路由中，也不会导致应用启动或依赖注入失败。
- 日志、异常信息和示例配置中不输出 API Key、Token 或完整请求凭据。
