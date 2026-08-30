# vivo 端侧 SDK 放置说明

本目录在赛事开发环境中曾放置赛事方提供的 `llm-sdk-release.aar`。该文件包含 `com.vivo.llmsdk` 类及原生库，但文件内部没有可确认的开源许可证或 `NOTICE`。

因此：

- 本项目的 Apache License 2.0 不适用于该 AAR、其原生库或配套模型文件。
- 未取得供应商明确的公开再分发授权前，不得将该 AAR 提交到公开仓库或公开发行包。
- `.gitignore` 已排除本目录下的 AAR 文件。
- 有合法 SDK 获取渠道的开发者，可按供应商文档在本地放置对应文件并自行承担合规责任。
- 无法取得该 SDK 的开发者，应移除或替换 vivo 端侧能力，参见 [`docs/API_REPLACEMENT.md`](../../../docs/API_REPLACEMENT.md)。

此说明不提供 SDK、模型文件、下载地址、账号或密钥。
