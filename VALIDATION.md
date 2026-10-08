# Android 16 验收记录

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
