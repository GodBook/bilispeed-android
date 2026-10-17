# B站倍速浏览器

Android WebView 浏览器，默认打开 B 站电脑端网页，并提供手机触屏布局、底部导航和倍速控制。内容与播放权限由 B 站官方网页决定。

[下载 APK](https://github.com/GodBook/bilispeed-android/releases/latest) · [1.3.2 发布页](https://github.com/GodBook/bilispeed-android/releases/tag/v1.3.2)

当前版本为 **1.3.2（versionCode 17）**。最低 Android 8（API 26），目标和编译 SDK 为 Android 16（API 36）。

## 安装与使用

下载 APK 后通过手机文件管理器安装，按系统提示允许安装未知来源应用。无需 root，也不会替换官方 B 站 App。正式版使用相同签名，可以覆盖升级并保留设置和 Cookies；1.0.0 用户需先手动安装新版，之后可使用应用内更新。

底部提供“首页 / 热门 / 动态 / 我的 / 设置”。首页顶部可以搜索；“设置”中可调整播放速度、主题、布局和字幕按钮外观，也可打开链接、刷新页面或转到系统浏览器。

### 播放控制

- 倍速范围 0.25–5x，滑杆步长 0.05，也可输入两位小数。默认记住上次选择；关闭记忆后，下次启动恢复 1x。直播保持 1x。
- 播放栏提供暂停、进度、上一集 / 下一集、倍速、音量、字幕和全屏；竖屏下部分操作位于“更多”。
- 单击视频显示或收起控件，双击暂停或继续；左右滑动预览进度，松手跳转，一次滑动最多调节 120 秒。
- 播放栏约 3 秒无操作后隐藏，拖动进度或打开面板时保持显示。
- 分 P 和合集分别显示，长选集列表可展开、收起或定位当前集；全屏切集保持横屏。
- 画质和字幕选项来自官方播放器，是否可用取决于视频、登录状态和账号权限。
- 切到后台会暂停视频，返回后可手动继续。

### 浏览与外观

支持浅色、深色和跟随系统，手机触屏布局与电脑原版布局可切换。触屏布局适配首页、搜索、视频、动态、个人空间、历史记录、收藏和稍后再看；电脑原版支持双指缩放。

首页、热门和动态列表支持下拉刷新。切换入口或从视频返回列表时会尝试恢复位置；官网重新加载内容或系统回收网页后，可能只能恢复到附近。

可以粘贴完整分享文本、短链接或 BV 号，也可接收官方 App 的分享。系统返回键优先关闭设置或弹窗，再退出全屏、返回上一页。

## 更新

进入“设置 → 检查更新”，查看版本、大小和说明后下载 APK，再由 Android 系统确认安装。首次应用内更新需要允许本应用安装未知来源应用。

启动检查默认开启，成功检查后 24 小时内不重复检查；发现新版只提示，不自动下载。检查失败可以重试，也可以从 Releases 手动下载。

更新使用本仓库 Releases 的 `update.json`，不需要 GitHub 登录。安装前检查版本、包名、文件大小、SHA-256 和签名；网络需能访问 GitHub。

## 限制与隐私

这是第三方浏览器，不提供原生 App 的离线缓存或后台播放，不绕过会员、付费和地区限制。5x 播放对网络、设备解码能力和播放器有更高要求。

官方网页调整结构后可能需要更新适配脚本。无法正常显示的页面可尝试关闭触屏布局。网页渲染进程退出时，应用会保留当前链接和设置，提供重新加载入口。

登录沿用官方网页，Cookies 保存在本机。应用申请联网、网络状态和安装更新权限，不读取官方 App 数据。网页与原生层的消息接口只向 HTTPS B 站域名开放；证书错误时停止加载，网页不能触发原生安装。

## 本地构建

需要 JDK 17–24、Android SDK Platform 36、Build Tools 36 和 Platform Tools。仓库使用 Gradle Wrapper 8.14.2 与 Android Gradle Plugin 8.10.1。

配置本机 SDK 路径后，在 Windows PowerShell 中运行：

```powershell
.\scripts\build-apk.ps1
```

若执行策略阻止脚本运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-apk.ps1
```

首次构建会生成本机发布签名。`.signing/bilispeed-release.jks` 和 `.signing/password.clixml` 必须保留，密码文件绑定当前 Windows 用户。自行构建的签名与 Releases 版本不同，不能直接覆盖官方发布包。

版本配置位于 `version.properties`。脚本输出签名 APK、`artifacts/update.json` 和 `artifacts/SHA256SUMS.txt`，三者需来自同一次构建。

## 测试与发布

```powershell
.\scripts\build-apk.ps1 -RunTests -DeviceSerial DEVICE_SERIAL
.\scripts\build-apk.ps1 -RunLiveCheck -DeviceSerial DEVICE_SERIAL
.\scripts\build-apk.ps1 -RunUpdateCheck -DeviceSerial DEVICE_SERIAL
```

用 `adb devices` 查询序列号并替换 `DEVICE_SERIAL`。实站和更新检查需要设备联网，更新检查还要求仓库中已有正式 Release。测试媒体仅打包进测试 APK。

- [设备测试说明](DEVICE-TESTING.md)：测试筛选、性能和无障碍检查命令。
- [验证记录](VALIDATION.md)：各版的设备范围、失败记录和结果。
- [发布脚本](scripts/publish-release.ps1)：上传 APK、更新元数据和校验文件，支持草稿发布。
- [发布核验脚本](scripts/verify-published-release.ps1)：下载并核对公开附件及更新信息。

发布时递增版本、更新 `release-notes.md`，使用原签名构建和测试，再从已提交的源码重建正式包。可用 `scripts/compare-release-apks.ps1` 比较测试包与发布包，发布后核验附件。

## 代码位置

代码位于 `app/src/main/`。Java 中的 `MainActivity`、`SettingsPanel`、`NavigationState` 和 `AppUpdater` 分别管理浏览器、设置、导航状态和更新。

`assets/` 中的 `desktop-touch.js` 负责触屏布局，`player-controls.js` 负责播放控件，`speed-controller.js` 负责倍速，`video-details.js` 负责选集，`my-page.html/css/js` 提供个人页。
