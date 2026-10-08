# B站倍速浏览器

面向 Android 16（API 36）的轻量 WebView 浏览器，启动默认打开 `https://m.bilibili.com/`。页面使用 B 站官方移动网页，额外提供原生倍速浮动按钮。

[下载最新 APK](https://github.com/GodBook/bilispeed-android/releases/latest) · [源码](https://github.com/GodBook/bilispeed-android)

## 安装与使用

在 Releases 中下载 BiliSpeed-1.1.1-Android16.apk，传到手机后安装。如系统询问，允许当前文件管理器安装此 APK。无需 root，也不会替换官方 B 站 App。已有 1.1.0 可在应用内检查更新；已有 1.0.0 时手动覆盖安装本版，即可获得在线更新入口。

- 点粉色「倍速」按钮选择 1x、1.25x、1.5x、2x、2.5x、3x、3.5x、4x、5x。
- 滑杆支持 0.25–5x，步长 0.05；输入框支持两位小数，例如 2.75x。
- 默认记住上次选择；关闭「记住上次倍速」后，下次启动恢复 1x。
- 拖动浮动按钮调整位置；竖屏和全屏分别记住位置。
- 点「···」返回首页、后退、刷新、打开链接 / BV 号、复制链接，或切换电脑版网页。
- 官方 App 的分享链接也可以分享给本浏览器。
- 「打开链接」支持粘贴完整分享文本、短链接或 BV 号，自动提取网址；HTTP 链接会升级为 HTTPS。
- 全屏时保留倍速按钮；系统返回键先退出全屏，再返回上一页。
- 系统恢复页面时保留倍速；页面历史状态超过 256 KiB 时保留当前 HTTPS 链接并重新加载，避免切后台崩溃。
- B 站网页可按播放器设置自动播放，切到后台会持续暂停视频，并阻止延迟播放和新视频自动播放；返回后可手动继续。其他网站保持手动触发播放。
- 视频页自动显示 B 站已有的 H5 播放器，替换默认的「立即播放 / 打开 App」展示布局。

## 在线更新

点「··· → 检查更新」，应用从本项目 GitHub Releases 获取最新正式版本，显示版本、大小和更新说明。确认后下载 APK，检查完整性与原签名，再打开 Android 系统安装器。

首次更新需要允许「B站倍速浏览器」安装应用：按提示进入系统设置，开启后返回，继续安装已下载版本。安装仍需系统确认；覆盖安装保留倍速、按钮位置与网页登录状态。取消安装后，可再次从菜单继续。

启动检查默认开启，成功检查后 24 小时内不重复检查；失败后可在下次进入应用时重试，自动重试间隔至少 15 分钟。发现新版只提示，不自动下载。可在「··· → 启动时检查更新」关闭。检查和下载要求手机网络能够访问 GitHub；失败时可以手动重试或打开发布页下载。取消操作会停止后续请求。应用自动清理旧更新包和未完成下载，保留待安装版本。

更新渠道为本仓库 Releases 中的 update.json，不用 GitHub 登录，也没有自建更新服务器。只接受版本号递增、包名一致、SHA-256 和文件大小匹配、签名与当前应用完全相同的 APK。拒绝 HTTP、第三方下载地址和不受信任的跳转；APK 下载到应用私有目录。

## 实际范围

这是个人第三方浏览器。界面和操作沿用官方网页，不能完整复刻原生 App 的动态、离线缓存、后台播放等功能。网页的「打开 App」跳转会被拦截，能解析的视频链接会在网页打开。会员、付费和地区限制仍按 B 站账号权限执行。

倍速控制直接作用于 HTML5 视频，保留音调；页面重新设置速度、换视频、加载新播放器时会重新应用所选值。网页自带倍速菜单会被浮动按钮所选的速度覆盖，请以浮动面板显示的实际应用状态为准。直播保持 1x。5x 对网络、设备解码和播放器有更高要求；网站将来改动播放器后，可能需要更新控制脚本。

登录沿用官方网页，Cookies 保存在本机应用数据中。应用申请联网、网络状态和安装更新权限；不读取官方 App 数据，不上传账号信息到自建服务，不绕过视频权限。安全证书出错时停止加载。网页与原生层的消息接口仅向 HTTPS B 站域名开放，没有可访问文件或执行系统命令的 JavaScript 接口。网页不能触发原生更新或安装。

## 构建

安装 JDK 17–24、Android SDK Platform 36、Build Tools 36 和 Platform Tools。Gradle Wrapper 使用 8.14.2，Android Gradle Plugin 使用 8.10.1。

```powershell
$env:ANDROID_HOME = 'D:\dev\android-sdk'
.\scripts\build-apk.ps1
```

Windows PowerShell 默认执行策略限制脚本时：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-apk.ps1
```

首次构建会生成个人发布签名。签名密钥位于 `.signing/bilispeed-release.jks`，随机密码经 Windows DPAPI 加密保存在 `.signing/password.clixml`。保留这两个文件，后续 APK 才能覆盖安装；加密密码文件绑定当前 Windows 用户。它们和本机路径均不提交到 Git。发布 APK 关闭调试。

自行克隆构建会使用自己的签名，不能覆盖本项目 Releases 的项目签名版本。发布者需要一直保留原密钥；换电脑前需安全迁移密钥及其密码。

版本和更新仓库配置位于 version.properties。构建会生成 APK、artifacts/update.json 和 artifacts/SHA256SUMS.txt。它们必须来自同一次签名构建。

已有 Android 16 模拟器或 USB 调试设备时可同时验证：

```powershell
.\scripts\build-apk.ps1 -RunTests
```

同时连接多个设备时，用 `-DeviceSerial emulator-5580` 显式指定测试设备（编号以 `adb devices` 为准）。

可用 `-TestFilter` 精确运行测试类或方法，例如 `-TestFilter 'app.bilispeed.browser.PlaybackInstrumentationTest#largeWebViewHistoryUsesBoundedStateAndPreservesUrlAndRate'`，其余构建、签名和安装检查仍会执行。

测试使用独立的测试 APK 和测试视频，覆盖真实媒体播放速度、网站重置、替换视频、iframe、Shadow DOM、自定义数值、速度持久化、全屏、后台延迟播放、批量页面更新与分享文本解析，以及更新版本、地址、哈希、包名、签名、取消、失败重试和安装文件隔离。测试媒体不会包含在交付的 APK 中。

构建脚本通过 ADB 在指定设备上运行测试；测试包会临时复制到英文路径，避免 Windows 安卓工具对中文项目路径的兼容问题。结果保存在 `artifacts/instrumentation-tests.txt`。

额外验证真实 B 站首页和公开视频的 5x 播放（需要设备能够联网）：

```powershell
.\scripts\build-apk.ps1 -RunLiveCheck -DeviceSerial emulator-5580
```

正式发布后，可用原签名验证真实 GitHub 更新渠道的元数据、APK 下载和签名：

~~~powershell
.\scripts\build-apk.ps1 -RunUpdateCheck -DeviceSerial emulator-5580
~~~

此检查依赖本仓库已有正式 Release，并需要设备能够联网访问 GitHub。

主要代码：MainActivity.java 管理浏览器与原生面板，assets/speed-controller.js 负责播放器倍速，AppUpdater.java 管理在线更新。targetSdk 为 36，minSdk 为 26；验收优先使用 Android 16。

## 发布后续更新

1. 在 version.properties 递增 versionCode 并更新 versionName，例如 4 / 1.1.2。
2. 修改 release-notes.md，使用原来的 .signing 密钥构建并验证 APK。
3. 提交、推送源码并创建对应标签，再发布三个 Release 附件：

~~~powershell
.\scripts\build-apk.ps1 -RunTests -DeviceSerial emulator-5580
git add .
git commit -m "Release 1.1.2"
git tag v1.1.2
git push origin main v1.1.2
.\scripts\publish-release.ps1
~~~

发布脚本需要已登录的 GitHub CLI；它校验 APK 与更新信息是否一致，并创建最新正式 Release，不替换已有版本。预发布测试可用 -Draft，草稿不会进入在线更新渠道。无需把签名密钥上传 GitHub。

本机若 GitHub CLI 因 HTTP/2 网络代理报 EOF，可先设置会话环境变量：$env:GODEBUG = 'http2client=0'。
