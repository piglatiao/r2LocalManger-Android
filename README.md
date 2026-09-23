# R2 Manager Android

面向 Cloudflare R2 的 Android 存储管理器，提供对象浏览、上传、下载、预览、传输任务管理以及 R2 管理面配置能力。

项目由桌面版 `r2LocalManger` 的核心能力延伸而来，使用 Kotlin 和传统 XML 布局实现，当前处于持续开发与验证阶段，暂未发布正式 APK 或稳定版 Release。

## 功能特性

- **凭证配置**：支持 Cloudflare Account ID、API Token、R2 Access Key ID 和 Secret Access Key 配置，并提供连接测试。
- **对象浏览**：浏览存储桶和对象，支持文件夹下钻、面包屑返回、搜索、筛选、排序、列表/网格展示和多选操作。
- **上传文件**：支持系统 Photo Picker、SAF 文档选择、拍照上传和系统分享上传；支持批量上传、同名覆盖确认及大文件 Multipart 上传。
- **下载文件**：统一使用 Android Storage Access Framework 选择目标目录，支持批量下载、目录结构保留和后台传输通知。
- **传输中心**：集中查看进行中、已完成和失败任务，支持取消、重试、清理已完成任务，进程重启后可恢复未完成任务状态。
- **文件预览**：支持图片、文本、PDF、视频和其他文件信息预览，并提供缩略图加载与缓存。
- **R2 管理**：支持存储桶列表、存储桶切换，以及 Cloudflare 管理面相关配置能力。
- **公开链接**：支持根据自定义域名、`r2.dev` 托管域名或 S3 端点生成对象链接。
- **本地安全**：凭证使用 Android Keystore 保护，本地列表和缩略图缓存使用 AES-256-GCM 加密，网络请求仅允许 HTTPS。

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 开发语言 | Kotlin 1.9.24 |
| Android 构建 | AGP 8.2.2、Gradle 8.5 |
| UI | XML、ViewBinding、ConstraintLayout、Material Components |
| 架构基础 | AndroidX、Lifecycle、ViewModel、Navigation、ViewPager2 |
| 网络 | OkHttp 4.12.0，自实现 S3 Signature V4 请求与 XML 解析 |
| 异步处理 | Kotlin Coroutines |
| 文件访问 | SAF、Photo Picker、FileProvider |
| 数据安全 | Android Keystore、AES-256-GCM |
| 测试 | JUnit、MockWebServer、AndroidX Test、Espresso |

## 环境要求

- JDK 17
- Android Studio Hedgehog 或更高版本
- Android SDK 34
- Android 设备或模拟器 API 26 及以上

工程配置如下：

- `minSdk = 26`
- `compileSdk = 34`
- `targetSdk = 34`
- `applicationId = com.r2manager.android`

## 快速开始

1. 使用 Android Studio 打开项目根目录。
2. 等待 Gradle 同步完成，并确保本机已安装 JDK 17 和 Android SDK 34。
3. 连接 Android 设备或启动 API 26 及以上的模拟器。
4. 运行 `app` 模块。
5. 首次启动后，在凭证配置页填写 Cloudflare R2 信息并测试连接。

也可以使用 Gradle Wrapper 执行常用任务：

```bash
# Windows
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:connectedDebugAndroidTest

# macOS / Linux
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

其中 `connectedDebugAndroidTest` 需要已连接设备或正在运行的模拟器。

## R2 配置说明

应用首次使用时需要配置以下信息：

| 配置项 | 用途 |
| --- | --- |
| Account ID | 生成 R2 S3 端点并访问 Cloudflare 管理面 |
| API Token | 存储桶、域名等 Cloudflare 管理操作 |
| Access Key ID | R2 S3 数据面认证 |
| Secret Access Key | R2 S3 数据面签名认证 |

请勿将真实凭证写入源码、提交到 Git 或粘贴到公开 Issue。应用不会在日志中输出完整凭证和带签名参数的 URL。

## 项目结构

```text
r2-manager-android/
├── app/
│   └── src/
│       ├── main/java/com/r2manager/android/
│       │   ├── core/       # 常量、错误、加密、日志和通用工具
│       │   ├── data/       # S3、Cloudflare、本地存储和仓库层
│       │   ├── domain/     # 传输、安全、缩略图和领域模型
│       │   └── ui/         # 启动、浏览、预览、设置和传输页面
│       ├── main/res/       # XML 布局、导航、主题、字符串和图标
│       ├── test/           # JVM 单元测试
│       └── androidTest/    # Android 仪器测试
├── docs/                   # 需求、接口、原型、PRD 和架构文档
├── prototype/              # 可点击 HTML 原型
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 原型


可直接用浏览器打开 [`prototype/index.html`](prototype/index.html) 查看可点击原型。原型为纯静态 HTML/CSS/JavaScript，无需安装额外依赖。

## 当前状态

- Android 工程、主要页面和核心数据层已经建立。
- S3 数据面、Cloudflare 管理面、缓存、安全和传输模块已按文档落地。
- 单元测试和部分仪器测试已提供，完整测试结果需要在本地 Android 环境中验证。
- 当前重点仍是功能联调、真机适配和发布前质量验证。
- 一期暂不包含分片级断点续传、暗色模式以及部分高级域名批量管理能力。

## 许可证

当前仓库暂未提供 `LICENSE` 文件。项目正式公开或分发前，请根据实际授权方式补充许可证说明。
