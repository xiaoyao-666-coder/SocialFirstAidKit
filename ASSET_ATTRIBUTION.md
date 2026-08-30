# Asset Attribution

本文件记录仓库中随源码分发的静态视觉素材及其许可状态。审计日期：2026-08-30。

## 审计范围与结论

仓库内未发现随应用分发的照片、第三方插画、视频、音频或字体文件。界面图标主要来自 Jetpack Compose Material Icons；除此之外，静态二进制视觉素材仅为同一启动图标生成的不同尺寸版本。

项目代码和团队原创文档继续按 Apache License 2.0 发布。下列 Android 机器人图标不属于团队原创作品，也不因进入本仓库而改用 Apache-2.0；它按照 Creative Commons Attribution 3.0 单独许可。

## Android 机器人启动图标

涉及文件：

- `app/src/main/ic_launcher-playstore.png`
- `app/src/main/res/drawable/ic_launcher_full.webp`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/mipmap-*/ic_launcher.webp`
- `app/src/main/res/mipmap-*/ic_launcher_round.webp`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

来源与修改：图标包含 Google 创建并共享的 Android 机器人图形；项目对其进行了背景组合、尺寸适配和 Android 启动图标格式转换。

许可：[Creative Commons Attribution 3.0 Unported](https://creativecommons.org/licenses/by/3.0/)

Google 要求的归属声明：

> The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.

该声明同时显示在应用的“个人中心 → 关于”区域，使源码仓库和构建后的应用均保留归属信息。

Android is a trademark of Google LLC. Google retains trademark rights in the Android robot. 本项目与 Google 不存在赞助、认可或隶属关系。

## Material Icons

界面中通过 `androidx.compose.material:material-icons-extended` 使用 Material Icons。它们由 AndroidX/Jetpack 依赖提供，不以独立图片文件复制到本仓库中，适用 Apache License 2.0。实际版本以 `gradle/libs.versions.toml` 和构建解析结果为准。

## 运行时内容

用户头像、聊天截图、朋友圈图片以及模型生成图片均由用户在运行时提供或生成，不属于本仓库分发的静态素材，也不受本项目 Apache-2.0 许可证重新授权。使用者应自行确保拥有相应内容的使用权。

## 后续维护

新增图片、字体、音频或其他素材时，应在合并前记录来源、作者、许可证、修改情况和对应文件路径。无法证明允许公开再分发的素材不得提交到公开仓库。
