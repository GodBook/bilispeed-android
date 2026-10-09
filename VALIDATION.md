# Android 16 验收记录

## 1.2.2：页面恢复、排版开销与内容入口状态

验收日期：2026-10-09。以下功能记录对应原签名的发布前测试构建；正式发布附件与更新渠道验证在本节末另行记录。

- assembleRelease、lintRelease、三个 JavaScript 文件的语法检查和两个 PowerShell 构建 / 发布脚本的语法检查通过；Android lint 为 0 errors / 4 warnings。最终构建记录：artifacts/optimization-final-build.txt。
- 本轮分组、隔离复测后，按测试方法去重共 **51 项取得通过结果**：播放核心 16 项、触屏播放器 12 项、布局 5 项、更新校验 12 项、渲染进程恢复 3 项、真实 B 站 3 项。完整套件曾被模拟器中断，结果按下面的分轮记录汇总。
- 第一轮播放器、布局和恢复专项：OK (20 tests)，记录为 artifacts/optimization-player-recovery-check.txt 和 optimization-player-recovery-tests.txt。覆盖单指滑动、取消手势、音量、字幕、视频替换、全屏隐藏 / 唤出、横竖屏、真实竖向视频比例、原版布局缩放，以及评论 / 选集延迟加载和工具栏替换。
- 原正式 1.2.1 APK 的控件 / 弹幕更新测试记录为 artifacts/touch-optimization-baseline.txt；使用相同的 60 次文字更新，整页布局查询为 11 次，新版相应测试断言为 0 次。由 instrumentation 驱动的后台节点更新旧版查询为 3 次（artifacts/touch-background-baseline.txt），新版为 0 次；最终追加检查确认后台字幕轨道变化不会重建面板，返回后替换视频可播放且只有一套控制栏。
- 三项恢复检查调用 WebViewRenderProcess.terminate() 结束真实独立渲染进程，验证多次恢复、当前链接的分 P / 时间 / fragment、倍速、浮动按钮位置、Cookies、待重试时保存链接、重试和新页面脚本；同时检查真实播放时清除屏幕唤醒、退出全屏、文件选择取消一次、共享弹窗清理，以及旧页面全屏 / 文件选择回调被取消。最终候选中的通过结果见 artifacts/optimization-final-core-and-live-check.txt；独立复核和截图见 optimization-recovery-visual-check.txt、BiliSpeed-browser-recovery.png。
- 更新校验 12 项通过，播放核心中的 iframe、后台 iframe、页面批量更新和后台暂停等通过记录见 artifacts/optimization-core-recheck.txt。其余播放核心、倍速记忆、自定义值、Shadow DOM、换视频、后台迟到播放和分享解析的通过结果见 optimization-final-core-and-live-check.txt。大型历史状态最终隔离检查为 OK (1 test)，见 optimization-large-history-isolated-check.txt；不受信任来源的消息桥检查为 OK (1 test)，见 optimization-bridge-isolated-check.txt。
- 主机 GPU 模式下逐档实际播放计时检查通过，涵盖 1x、1.25x、1.5x、2x、2.5x、3x、3.5x、4x 和 5x。记录见 artifacts/optimization-rate-and-fullscreen-diagnostics.txt。测量中位数分别约为 1.001、1.247、1.490、2.006、2.513、3.010、2.922、3.991、4.982；均在现有测试容差内，3.5x 一档仍有明显偏差。尚无充分证据将此前计时波动的改善归因于本轮代码，实体设备仍需复核。
- 真实 B 站搜索、热门、动态和官方登录表单检查通过，记录见 artifacts/optimization-final-core-and-live-check.txt。真实首页、视频 5x、暂停 / 继续、滑动、音量、字幕入口、全屏和返回的最终隔离检查，与本地全屏回归一起为 OK (2 tests)，见 optimization-fullscreen-live-isolated-check.txt。
- 过程中的两次完整测试中断对应 Windows 的 qemu-system-x86_64-headless.exe 访问冲突（0xc0000005），记录为 artifacts/optimization-emulator-crashes.txt。模拟器还出现蓝牙服务崩溃弹窗和 System UI 无响应弹窗，截图及窗口焦点检查确认它们遮挡页面和触摸输入。重启模拟器、使用主机 GPU 并处理系统弹窗后，对受影响的测试做了隔离复测；初次失败与中断记录保留，未把它们计入通过结果。
- 发布前测试 APK：artifacts/BiliSpeed-1.2.2-tested-Android16.apk，versionName 1.2.2 / versionCode 6，1774321 字节，targetSdk 36 / minSdk 26。SHA-256：C2BC121D6402B2591B2D9CBD073ACEC69CA368F83D71338E6710810783839C3A。APK Signature Scheme v2 校验通过；证书 SHA-256 与 1.2.1 相同：e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3。已在测试设备覆盖安装；测试构建的 APK、update.json 和 SHA256SUMS.txt 的版本、文件大小和哈希一致。

测试环境为 Android 16 / API 36 x86_64 模拟器，WebView 133.0.6943.137；未连接实体手机，未使用真实账号验证登录或会员字幕，未测量耗电。查询次数的下降仅表示脚本减少了相应扫描，不能换算为整机性能或续航提升百分比。

## 1.2.1：触摸进度、音量、字幕与全屏适配

发布前功能验收日期：2026-10-09。以下功能记录对应原签名的发布前测试构建；正式发布附件的校验另行记录。

- assembleRelease、lintRelease、三个 JavaScript 文件的语法检查和构建脚本 PowerShell 语法检查通过；Android lint 为 0 errors / 4 warnings。
- 播放器与布局专项：OK (14 tests)，包含 10 项新播放器检查和 4 项原有布局检查。通过真实单指滑动预览、松手跳转、前后边界、垂直滑动、双指与取消手势、音量与静音恢复、字幕语言与关闭、换视频、全屏默认隐藏、点击唤出、3 秒隐藏、设置面板保持显示、按住进度条保持显示、松手后隐藏，以及真实竖向视频比例和横竖屏适配。记录为 artifacts/player-final-build-and-check.txt。
- 播放核心与实站检查：OK (11 tests)，其中 8 项覆盖网站重置速度、替换视频、iframe、Shadow DOM、全屏倍速与屏幕唤醒、后台暂停、迟到的自动播放、大型页面状态恢复和批量 DOM 更新；3 项真实 B 站检查覆盖首页、视频、搜索、热门、动态和官方登录表单。实际视频上通过暂停、滑动跳转、音量、进度滑杆、继续播放、全屏唤出与隐藏和返回；5x 属性及实际播放进度推进通过。记录为 artifacts/player-regression-and-live-check.txt。
- 窄屏追加检查：600 × 1400 / density 300，CSS 宽度 320，音量、旋转后控件边界和原版缩放设置检查：OK (2 tests)。记录为 artifacts/player-small-screen-check.txt。
- 宽屏追加检查：1800 × 1200 / density 160，CSS 全屏宽度 1800，覆盖超过原来 1000px 排版断点后的隐藏、唤出、滑动与滑杆操作：OK (1 test)。记录为 artifacts/player-wide-landscape-check.txt。测试完成后已恢复模拟器原来的 720 × 1600 / density 300 设置。
- 实站全屏尺寸额外复核：OK (1 test)，记录为 artifacts/player-live-fullscreen-layout-check.txt。确认官方全屏容器、视频区域、画面元素均为 808 × 384 CSS 像素，匹配当时的全屏视口；触屏控制栏位于该区域底部。
- 合计 29 项相关检查通过。初始模拟器的 System UI 无响应弹窗、首次全屏系统提示和触摸测试的系统返回手势边缘影响过输入检查；最终记录使用恢复后的系统状态、明确的单指输入，并避开系统返回手势起点。Android 16 大屏可能保留当前屏幕方向，最初的宽屏竖屏检查进入了全屏并隐藏控件，但未满足测试中的横屏断言；最终在宽屏横屏窗口完成上述操作验收。
- APK Signature Scheme v2 校验通过；与 1.2.0 的证书 SHA-256 一致：e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3。已经成功覆盖安装，APK、update.json 与 SHA256SUMS.txt 的版本、大小和哈希一致。
- 版本：versionName 1.2.1，versionCode 5，targetSdk 36，minSdk 26。发布前测试 APK：artifacts/BiliSpeed-1.2.1-tested-Android16.apk，1772229 字节。
- 测试 APK SHA-256：43DD2BE2376DB4E6C262EF554CE9EDDFE8728DE2E2D182C7A2116D3A9D2BB90B。

测试使用 Android 16 / API 36 x86_64 模拟器，未连接实体手机。字幕语言切换已用真实 HTML5 TextTrack 与官方菜单结构的本地夹具验证；此次实站样本没有可用字幕，验证的是无字幕提示，未使用真实账号验证登录后或会员字幕资源。本次未重新验收所有倍速档位的实际时间比例，1.2.0 记录的逐档计时问题仍未确认修复。

界面已截图检查，包括 artifacts/BiliSpeed-player-volume.png、BiliSpeed-player-portrait-video.png、BiliSpeed-player-small-volume.png、BiliSpeed-player-wide-fullscreen-controls.png、BiliSpeed-live-volume.png、BiliSpeed-live-subtitles.png 和 BiliSpeed-live-fullscreen-controls.png。

正式发布与更新渠道验证（2026-10-09）：

- [v1.2.1 Release](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.1) 已发布为最新正式版本，三个附件齐全：BiliSpeed-1.2.1-Android16.apk、update.json 和 SHA256SUMS.txt。源码标签指向 00aaa20895cb8254b22aba3a0e25fba7d95d3411。
- 正式 APK 为 1772229 字节，SHA-256：DBD66AAA7B07BABD7D19421E4F405B0F02A187496321C9B11E57FACC93B946E5。原签名与 APK Signature Scheme v2 校验通过。
- 正式 APK 与上述测试 APK 解包逐项比较，仅 META-INF/version-control-info.textproto 的 Git 版本记录发生变化，应用代码、资源和播放器脚本一致。正式构建的 Git 版本信息对应发布源码提交。
- GitHub 服务器给出的文件摘要与本机构建一致；重新下载全部三个附件，版本、文件大小、SHA-256、更新信息和校验文件全部匹配。
- Android 16 上读取真实 GitHub 更新渠道并实际下载正式 APK，按上一版本 code 4 校验新版 code 5 的版本、包名、大小、哈希和原签名：OK (1 test)。记录为 artifacts/published-update-test-1.2.1.txt。测试设备使用能够访问 GitHub 的临时代理，全程保留 HTTPS 证书校验；测试完成后恢复网络设置。

## 1.2.0：电脑端网页与触屏操作

- 默认入口和 User-Agent 改为电脑端；旧版手机网页偏好不再影响启动。手机端首页、视频、番剧、搜索、动态和空间链接转到相应电脑端，保留 query 和 fragment。
- 触屏布局提供两列推荐 / 搜索列表、纵向视频页面、底部导航，以及直接作用于官方 HTML5 视频的暂停、进度、时间和全屏控件。电脑原版布局仍可从菜单切换。
- 正式签名 APK 的 assembleRelease、lintRelease、JavaScript 与 PowerShell 语法检查通过；lint 为 0 errors / 4 warnings。
- 最后交付检查为 OK (7 tests)：4 项布局、链接及偏好检查，3 项真实 B 站检查。验证首页、搜索、热门、动态、官方登录表单的手机宽度，以及实际视频的 5x 设置、播放、暂停、拖动进度、继续、全屏和返回。结果为 artifacts/desktop-delivery-check.txt。
- 32 项本地播放、布局和更新回归中，31 项通过；全屏持续播放的唤醒标记已通过复测。逐档实际播放时间的测量尚未通过：串行回归出现 3x 实测约 1.94x，重启后隔离检查出现 1.25x 实测约 0.39x；电脑原版布局对照也出现 1.5x 实测约 0.43x。原因未确定，不能将倍速属性正确视为所有档位实际计时均已通过。
- 完整运行中的一次实站 JavaScript 回调超时，在重启后的隔离检查及最后 7 项交付检查中通过。原始记录保存在 artifacts/desktop-regression-and-live-tests.txt、artifacts/desktop-recheck.txt 和 artifacts/desktop-original-rate-probe.txt。
- 已成功覆盖安装；新旧 APK 的签名证书与 1.1.1 一致，APK Signature Scheme v2 校验通过。APK、update.json 与 SHA256SUMS.txt 相互匹配。
- 版本：versionName 1.2.0，versionCode 4，targetSdk 36，minSdk 26。APK：artifacts/BiliSpeed-1.2.0-Android16.apk，1766158 字节。
- SHA-256：C0539F347291D1694C997A7F7801E84F5CA9BFCDABEEB5FA92346DFC3905043B。

测试设备为 Android 16 / API 36 x86_64 模拟器，720 × 1600、density 300。未连接实体手机；登录仅检查官方表单显示，未使用真实账号验证登录、会员、收藏和评论。本版发布渠道为 GitHub Releases 的 v1.2.0。

截图为 artifacts/BiliSpeed-desktop-home.png、BiliSpeed-desktop-video.png、BiliSpeed-desktop-search.png、BiliSpeed-desktop-popular.png、BiliSpeed-desktop-dynamic.png、BiliSpeed-desktop-login.png 和 BiliSpeed-desktop-speed-panel.png。

发布后已确认 GitHub 最新正式版本为 v1.2.0，三个附件齐全；重新下载 APK、update.json 和 SHA256SUMS.txt，版本、文件大小与 SHA-256 均与本机构建一致。Android 16 上读取正式更新渠道、实际下载发布 APK 并校验包名、版本、哈希和原签名：OK (1 test)，记录为 artifacts/published-update-test-1.2.0.txt。

## 1.1.1：性能与稳定性

- 正式签名 APK 的 assembleRelease、lintRelease、JavaScript 语法和发布脚本 PowerShell 语法检查通过；lint 为 0 errors / 4 warnings。
- Android 16 / API 36 模拟器上验证正式 APK：16 项播放及浏览器测试、12 项更新测试。
- 400 个新增节点的整页播放器查询从旧版 401 次降到 1 次；保留真实播放时间、所有倍速档位、换播放器、iframe、Shadow DOM、全屏与屏幕唤醒检查。
- 后台延迟 play()、新增 autoplay 视频、后台创建的 Shadow DOM iframe、重新挂载的播放器、完整分享文本、合法 URL 括号与 IPv6 地址验证通过。
- 捕获旧逻辑保存约 8.3 MB WebView 状态造成的 TransactionTooLargeException。恢复状态上限为 256 KiB；大型内嵌测试页保存状态为 400 字节，有效 HTTPS 页面链接和 3.5x 倍速保留验证通过。
- 更新取消后不跟随重定向，后续请求可重新开始；中断下载删除未完成文件；自动检查成功与失败间隔、时钟回调、待安装包保留和旧文件清理验证通过。
- 同一正式 APK 已多次完成真实 B 站首页和公开视频的 5x 播放检查；重复检查中也出现模拟器系统卡顿和网站响应超时。实站检查依赖设备和网络，不视为覆盖所有视频或登录场景。
- 从 GitHub 下载上一版 1.1.0 APK，核对新旧签名一致。证书 SHA-256：e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3。APK Signature Scheme v2 校验通过。
- 版本：versionName 1.1.1，versionCode 3，targetSdk 36，minSdk 26。
- APK：artifacts/BiliSpeed-1.1.1-Android16.apk，1758513 字节。
- SHA-256：385AEE75B7CEA6464B09B1E70D89E0F30A5592AFC70A847A0DC792190B193679。

测试设备为 x86_64 模拟器，720 × 1600、density 300，使用宿主 GPU 并关闭 Vulkan。原环境出现解码停滞、软件图形后端系统卡顿及 System UI 无响应弹窗，已通过日志与界面检查定位；没有降低倍速测量断言。未连接实体手机，也未使用真实用户账号验证登录。

优化说明与后续优先级见 OPTIMIZATION.md。

发布后已确认 GitHub 最新正式版本为 v1.1.1，APK、update.json 和 SHA256SUMS.txt 三个附件齐全，GitHub 的文件摘要与本机构建一致。Android 上读取正式更新信息、实际下载 APK 并验证哈希、版本、包名和原签名：OK (1 test)，结果为 artifacts/published-update-test-1.1.1.txt。初次直接连接遇到 TLS 中断，使用能够访问 GitHub 的测试代理后通过；全程保留系统证书校验。

## 1.1.0：在线更新

- 正式签名 APK 的 assembleRelease 和 lintRelease 通过，关闭调试。
- Android 16 / API 36 模拟器上运行正式发布 APK，测试 APK 使用相同发布签名：OK (18 tests)。
- 10 项播放测试覆盖所有指定倍速、自定义速度、持久化、换播放器、iframe、Shadow DOM、后台暂停与全屏操作。
- 8 项更新测试覆盖递增版本、拒绝降级、HTTPS 与每次跳转校验、异常元数据、错误哈希、伪造版本、其他包名、不同签名、安装文件 URI 隔离；正常签名包通过校验。
- 已用原 1.0.0 的发布密钥覆盖安装 1.1.0，Android 安装成功。
- 版本：versionName 1.1.0，versionCode 2，targetSdk 36，minSdk 26。
- APK：artifacts/BiliSpeed-1.1.0-Android16.apk，1756233 字节。
- SHA-256：3D675C63A3BCD9DC34ADB924239DCFE778E8E05BF6B7AD63EF07A1B8CC7BE05C。
- APK Signature Scheme v2 校验成功；update.json 与 SHA256SUMS.txt 来自同一次签名构建。
- GitHub 发布后的在线集成测试通过：在 Android 16 上读取正式更新信息、实际下载发布 APK 并核对原签名，OK (1 test)。
- 原生更新完整流程通过：自动识别新版 → 显示说明 → 下载并校验 → 首次安装权限设置 → 返回继续 → 系统确认更新 → 安装成功，实际安装版本为 1.1.0 / code 2。
- 此流程使用同签名、code 1 的本地低版本验收包，其代码已包含更新入口；原始 1.0.0 用户仍需先手动覆盖安装本版。
- 升级前后数据探针通过：3.5x 倍速、记住倍速开关和 B 站域名下的合成 Cookie 保留，准备和升级后校验各 OK (1 test)。未使用真实用户账号。

验收环境为 x86_64 模拟器，未连接实体手机。网页登录状态的保留依赖 Android 覆盖安装保留应用数据，未使用真实用户账号验证。

## 1.0.0：原始播放验收

- 构建：`assembleRelease`、`lintRelease` 成功；JavaScript 语法检查成功。
- 安装：个人签名的发布 APK 已在 Android 16 / API 36 模拟器安装并启动，关闭调试。
- 自动测试：`OK (11 tests)`，涵盖所有倍速档位的实际播放时间、自定义输入、网页重置、播放器替换、iframe、Shadow DOM、重启记忆、后台暂停、全屏调速及返回继续播放。
- 实站：默认打开 B 站官方移动首页；公开视频在可见的 H5 播放器中完成 5x 播放验证。
- 界面：发布包的首页、浮动按钮和倍速面板已截图检查；面板首次打开不会自动弹出键盘。
- 签名：APK Signature Scheme v2 校验成功。

交付文件：`artifacts/BiliSpeed-1.0.0-Android16.apk`，931035 字节。

SHA-256：`0F08410097858796621546260D6DD9A6A1DF0D45690D382548748849FF8319FC`。

原始结果位于 `artifacts/instrumentation-tests.txt`，预览图为 `artifacts/BiliSpeed-home.png` 和 `artifacts/BiliSpeed-speed-panel.png`。

测试设备为 x86_64 Android 16 模拟器；没有连接实体手机。APK 使用 Java / WebView，不包含架构专属的原生库。B 站登录、会员、付费和地区权限仍由网站决定，未使用真实用户账号进行验证。
