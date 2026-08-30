# 公开 GitHub 发布检查清单

## 原则

旧 Gitee 历史中出现过 API Key、Token 或内置凭据。删除当前文件或新增 `.gitignore` 不会清除旧提交，因此公开 GitHub 仓库应使用全新的 Git 历史，而不是推送、镜像或导入旧 Gitee 历史。

## 发布前

- 撤销并重新生成所有曾经提交到 Git 的凭据。
- 删除公开快照中的 `core/core-llm/libs/llm-sdk-release.aar`、模型文件和赛事材料副本。
- 按 [`API_REPLACEMENT.md`](API_REPLACEMENT.md) 处理赛事专用 Provider。
- 确认应用图标、图片和字体来源及再分发权。
- 确认 `LICENSE`、`NOTICE`、`THIRD_PARTY_NOTICES.md`、`README.md`、`SECURITY.md` 和 `CONTRIBUTING.md` 内容一致。
- 在无赛事 AAR、无本地配置、无缓存的环境中验证公开版能够构建。

## 推荐的干净首次提交方式

1. 在当前工程之外新建一个公开快照目录，不复制 `.git`。
2. 复制需要公开的源码和文档，同时排除 `.gradle`、`build`、`.idea`、`local.properties`、`submission`、AAR、APK、模型、密钥和缓存。
3. 对快照目录执行源码和二进制敏感信息扫描。
4. 在 GitHub 创建空仓库，不勾选自动生成 README、LICENSE 或 `.gitignore`。
5. 只在快照目录初始化 Git，并创建单个首次提交：

```powershell
git init -b main
git add .
git status
git commit -m "Initial open-source release"
git remote add origin https://github.com/OWNER/REPOSITORY.git
git push -u origin main
```

6. 推送后检查 GitHub 文件列表和提交历史，确认只有新的干净提交。

## 扫描重点

至少搜索以下内容：

- `API_KEY`、`apiKey`、`token`、`secret`、`password`、`Authorization`
- `.env`、`local.properties`、签名文件、私钥和服务账号 JSON
- AAR、SO、模型权重、赛事压缩包和带个人信息的材料
- 图片 EXIF、身份证、手机号、住址和调试日志

扫描工具没有报警也不等于可以跳过人工复核。尤其要检查二进制文件、压缩包和生成文档。

## 贡献归属

全新历史不会保留旧 Gitee 的提交记录。README、NOTICE 和共同开发协议应继续写明梁镇贤、刘亦达、崔兆飞三位共同开发者；首次提交者或 GitHub 仓库负责人不因此成为唯一著作权人。
