# Third-Party Notices

本文件记录“社交急救包（Social First Aid Kit）”直接使用的重要第三方软件、外部服务和素材。项目根目录的 Apache License 2.0 只授权项目共同开发者有权授权的内容，不会改变第三方组件自身的许可条款。

依赖版本以 `gradle/libs.versions.toml` 和各模块 `build.gradle.kts` 为准；传递依赖应以最终构建产物生成的依赖清单为准。

## 开源运行时依赖

| 组件 | 项目中使用的主要版本 | 许可证 | 项目或许可证地址 |
| --- | --- | --- | --- |
| AndroidX / Jetpack / Compose / Material 3 / Room / Lifecycle / Navigation / WorkManager / DataStore / Security Crypto | 多版本 | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin 与 Kotlin 标准库 | 2.0.21 | Apache-2.0 | https://github.com/JetBrains/kotlin |
| kotlinx.coroutines | 1.9.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization | 1.7.3 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| Dagger / Hilt | 2.52 | Apache-2.0 | https://github.com/google/dagger |
| OkHttp / Okio | 4.12.0 / 传递版本 | Apache-2.0 | https://github.com/square/okhttp |
| Retrofit | 2.11.0 | Apache-2.0 | https://github.com/square/retrofit |
| Coil | 2.6.0 | Apache-2.0 | https://github.com/coil-kt/coil |

## 测试及构建工具

| 组件 | 用途 | 许可证或条款 |
| --- | --- | --- |
| JUnit 4.13.2 | 单元测试，不进入正式 APK | Eclipse Public License 1.0 |
| Android Gradle Plugin、Android SDK、Android Studio | 构建工具 | 适用 Google/Android SDK 条款；不由本项目许可证重新授权 |
| Gradle Wrapper | 构建工具 | Apache-2.0 |

## 非 Apache 开源依赖或受服务条款约束的组件

### Google ML Kit

项目使用中文文本识别、图像标签和人脸检测相关 ML Kit 组件。ML Kit SDK、模型及服务受 Google APIs Terms of Service 和 ML Kit Additional Terms 约束，不应笼统标注为本项目的 Apache-2.0 代码。使用者还应根据 ML Kit 的数据披露说明更新应用隐私政策。

- https://developers.google.com/ml-kit/terms
- https://developers.google.com/ml-kit/android-data-disclosure

### 阿里云 NLS

项目声明了阿里云 NLS SDK 依赖。SDK 和语音服务受阿里云相应产品协议与服务条款约束；账号、AppKey 和 Token 均须由使用者自行申请，不能随源码分发。

## 赛事方提供或访问受限的能力

| 能力 | 当前状态 | 公开仓库要求 |
| --- | --- | --- |
| vivo 云端模型、OCR、ASR、图片生成接口 | 赛事方提供访问条件 | 不提供账号或密钥；下载者自行更换为有权使用的服务。 |
| vivo 端侧 `llm-sdk-release.aar` 及模型文件 | 赛事方提供；AAR 内未发现许可证或 NOTICE | 在取得书面再分发授权前不得进入公开仓库或公开发行包。 |
| 小米 MiMo 接口 | 赛事环境中接入 | 不提供赛事凭据；下载者自行申请或替换服务。 |
| Agnes 接口 | 赛事环境中接入 | 不提供赛事凭据；下载者自行申请或替换服务。 |

这些接口的替换位置见 [`docs/API_REPLACEMENT.md`](docs/API_REPLACEMENT.md)。调用外部 API 不会使对方服务或模型自动受本项目 Apache-2.0 许可证约束。

## 其他云端服务

代码还包含 Gemini、文心、蓝心、通义、讯飞及自定义 OpenAI 兼容接口的适配入口。使用者必须自行取得账号与凭据，并遵守各服务商届时有效的服务条款、模型许可、隐私规则、内容政策和计费规则。

## 应用图标与视觉素材

仓库内未发现随应用分发的照片、第三方插画或字体文件。界面图标主要通过 Jetpack Compose Material Icons 依赖使用。

启动图标包含 Android 机器人图形，适用 Creative Commons Attribution 3.0，不属于本项目 Apache-2.0 许可范围。仓库已在 `NOTICE` 和 [`ASSET_ATTRIBUTION.md`](ASSET_ATTRIBUTION.md) 中保留 Google 要求的归属声明、修改说明和商标提示。

以后新增来源于设计平台、生成式工具、素材网站或赛事方的视觉素材时，必须记录来源和适用条款；无法证明允许公开再分发的素材不得进入公开仓库。

## 发布维护要求

新增或升级依赖时，应重新检查直接及传递依赖的许可证，并同步更新本文件。发布 APK 时，还应保留依赖要求的许可证和归属声明；本文件不能替代第三方许可证全文或供应商服务条款。
