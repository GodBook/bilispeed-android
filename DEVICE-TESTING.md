# 2026-10-10 1.2.9 单行控制栏与全屏切集检查点

需求：播放下栏按钮统一为一行；全屏点击下一集继续保持全屏；使用电脑连接的手机测试，完成后同步 GitHub 与正式 Release。

工作位置：main，起点 8aefb2e / 1.2.8，开始时工作区干净。设备为 10.93.192.96:5555 / 小米 9 SE / Android 15 API 35 / WebView 153，保留原签名、Cookies 和原偏好快照。

实现：竖屏上一集、下一集改为图标，音量与字幕从更多面板打开，按钮保留 44px 点击区域；320px 下隐藏可视总时长但保留完整无障碍说明。切集通过受限主框架消息交给 Android，在新文档加载期间保留横屏、沉浸显示和导航隐藏，新播放器铺满 WebView。退出按钮与系统返回恢复原先方向；非目标视频导航和加载错误清理全屏。

当前验证：播放器 22 项通过（119.327 秒，artifacts/device-1.2.9-player-regression.txt）；其余 73 项通过（253.409 秒，browser-regression.txt）；同机实际 320px 的 14 项通过（37.542 秒，320.txt）。95 项回归按 22 + 73 分组运行，没有重复计数。实站 21 状态通过：live-course.txt（11）、live-collection.txt（5）、live-boundaries.txt（3）、live-single.txt（2）。所有日志前缀为 artifacts/device-1.2.9-，JSON 与原生截图同前缀。

实测 APK 1814646 字节 / SHA-256 7E4B700EFFCA0795AE77F610AAFEA23DEB9F1F59CEB3A916BBDED7924AA028E5，已拉取手机安装包核对一致；存于 artifacts/tested-1.2.9。生产文件指纹 device-1.2.9-source-sha256.json，在实站检查和设置恢复后核对未变。

首轮 3 项中 1 项通过，两个退出断言错误地假定未锁定方向的手机一定回到竖屏；全屏状态和系统栏实际已正常恢复。已让播放器夹具明确从竖屏开始，之后 22 项全部通过，未降低退出断言。首次构建被 Windows 闲置 Gradle daemon 占用 classes.dex 阻止，停止 daemon 后构建与 lint 通过；首轮日志保留。

原偏好已恢复并自动删除快照（restored-final.txt）；检查会话已结束、调试已关闭、tcp9222 已移除。screen_off_timeout 恢复 600000，accelerometer_rotation 恢复 1、user_rotation 恢复 0；未修改 Wi-Fi。正式 Release 与正式包安装的校验结果在发布后追加。

# 2026-10-10 1.2.8 分集与播放栏检查点

当前需求：修复截图中的分集显示，在播放控制栏添加上一集、下一集与倍速按钮；在电脑连接的手机测试通过后更新 GitHub 与 Release。

工作位置：main，起点 621d316 / 1.2.7，开始时工作区干净。已连接小米 9 SE（10.93.192.96:5555）。已运行 DevicePreferencesProbe#savePreferences 保存原偏好；保留原签名和 Cookies，收尾必须恢复偏好并结束临时调试。

同一视频已找到：BV1T6VFzAE1c（Kira 概率论），73 P、2 个合集视频。基线真机复现固定 328px 列表无展开提示、长标题省略、1558:00 时长，以及第 2 P 时合集不再高亮且只显示「2 集」。基线截图为 artifacts/device-1.2.8-baseline-episodes.png。

当前状态：最终候选 7CBA4F46011ED08E9F1418E8C21A09F9FB051DDDDA1CAAC9DB672965EDBF8031 / 1813362 字节已取得完整 93 项通过（355.339 秒，release-93-regression.txt）、同机 320px 的 14 项通过（37.36 秒，release-320.txt）及实站 12 状态全部通过。播放器 20 项专项亦通过（104.2 秒，controls-release.txt）。手机拉取包 final-installed.apk 与候选一致，候选已归档 artifacts/tested-1.2.8。没有待修复或待复测的生产代码。

实站证据：device-1.2.8-verified-course.txt（7）、verified-boundaries.txt（3）、verified-single.txt（2）；最终 JSON 和原生截图统一前缀 device-1.2.8-final-。原偏好恢复成功（restored-final.txt），快照已自动删除，screen_off_timeout 恢复 600000；检查会话均结束，tcp9222 已移除。

发布完成：[v1.2.8](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.8)，源码/标签 9b6f8f5fdc862f9759db53c7f540fefcd9ff7734。正式包 C1E8C196C6E4CAED697C7F12FD88630BB73DB9E0E0D7CB45978CF1400A1F00D0 / 1813362 字节；76 个 ZIP 条目仅 META-INF/version-control-info.textproto 与实测候选不同。三个正式 Release 附件、匿名 latest 元数据、公开 APK 下载和 SHA256SUMS 均核对一致。正式 APK 已覆盖安装手机，拉取 published-installed.apk 核对一致；测试 runner 已卸载，DEBUGGABLE 标志不存在，当前应用 WebView 调试 socket 为 0、端口转发为 0。原生设置确认原倍速 1x、记住倍速和触屏布局均已恢复开启；手机停留在正式版设置页，截图 published-settings.png。验收后的文档记录另随 main 同步，不移动发布标签。

前一候选 0FBE84D904232906B746F8CC26C47B5C26F0AF77F4BB9B7B2BFCF0259E573F94 虽取得 320px 的 14 项和实站 12 状态通过，完整 93 项有 1 项在系统返回退出全屏后未显示控件，因此已淘汰。现已将全屏状态同步限定为原生进出通知与新页面加载完成，移除倍速 config 中可能迟到的全屏字段；新增回归拒绝旧倍速配置恢复全屏，最终整组已通过。

已修复的实站问题：全屏内选速后换页残留全屏标记，document.fullscreenElement=null，但新文档 __BILI_TOUCH_FULLSCREEN__=true，导致滚动后的官方 mini 播放器左边为 -11px。原因是全屏选速刷新了 document-start 脚本，退出时只通知现有页面而未更新缓存的启动状态。现在全屏变化同步更新启动脚本，每页加载完成后同步原生实际全屏状态；实站逐状态同时断言 DOM、布局标记与启动标记一致。

前一候选证据保留：device-1.2.8-fullscreen-navigation-final.txt、final-320.txt、player-course-release.txt（7 状态）、player-boundaries-release.txt（3 状态）、player-single-release.txt（2 状态）、final-93-regression.txt（92/93）及 fullscreen-back-failure.png；拉取旧包为 device-1.2.8-tested-installed.apk，不作为最终交付。

旧候选 DC6E636D7B4C813A2E704700243807DF2FB2D974FD95DD9E6BEEBBD58CBAF200 已被上述实站问题淘汰。其分集 11 项、320px 的 13 项及实站课程 7 状态虽通过，不作为最终候选验收。保留 device-1.2.8-details-final.txt、player-320.txt、player-course-final.txt 和 player-boundaries-final.txt。首轮 30 项 25 通过：三个断言仍测量已隐藏行/按钮，另两个标题断言遇到新「合集」标识；已校正可见性检查并保持 h2 只包含标题。第一次完整 92 项 91 通过：连续更换夹具文档时 evaluateJavascript 回调丢失，已使用测试专用 load 提交通知修复；保留首轮日志，不把失败轮次当作整组通过。

# 2026-10-10 1.2.7 全界面真机适配检查点（历史）

当前需求：修复「我的」中历史记录、我的收藏、稍后再看及动态界面；检查所有现有页面与功能，优化发现的问题，全部验证后更新 GitHub 和 Release。用户明确要求使用已连接的手机。

工作位置：本项目 main，起点 ac0e33a / 1.2.6（versionCode 10），开始时工作区干净。使用 long-project-execution 保存检查点，以本文件维护当前进度，以 VALIDATION.md 维护验收证据。

设备：无线 ADB 10.93.192.96:5555，小米 9 SE；已确认在线且安装 1.2.6。保留应用数据、Cookies 与原签名；旧测试夹具运行前后使用 DevicePreferencesProbe 保存及恢复设置。

当前状态：本轮修复、实体手机验收与[v1.2.7正式发布](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.7)已完成。历史/稍后再看/空间的1012/1060/1100px外壳和登录态动态556px主栏已按手机宽度修复；投稿/空间动态/分页/空间设置、「我的」弹窗返回、视频初始化和原生输入修复已纳入最终候选验证。最终87项整组通过、同机320px账号8项通过，不以第一候选结果代替最终结果。

验收范围：历史/收藏/稍后再看、动态列表与详情、个人空间及关注/粉丝、首页/热门/搜索、视频/评论/选集/播放器、我的/登录、设置/链接/返回/前后台、更新与恢复；以真实手机页面与现有自动化测试交叉验证。真实账号不可访问的状态另用官方结构夹具检查，不将夹具当作实站账号验收。

最近结果：第一轮完整83项中80通过；失败日志保存在 artifacts/device-1.2.7-full-regression.txt。两处是切换夹具文档时过早 evaluateJavascript 导致回调丢失，已让 DesktopLayout/Settings 夹具等待实际 load 提交；「我的」返回夹具曾以 about:blank 为历史地址，已改用真实 MY_PAGE 并新增原生地址断言。布局/个人页/账号专项19项通过（device-1.2.7-targeted-regression.txt），账号/设置专项13项通过（device-1.2.7-account-settings-final.txt）。未降低断言、未隐去第一轮失败。

最近补充：完整84项通过（device-1.2.7-final-full-regression.txt）；最终账号8项及同机实际320px的8项通过（device-1.2.7-account-final.txt、device-1.2.7-account-320.txt、device-1.2.7-narrow-viewports.txt）。真实页面第一轮18个状态通过，粉丝页使用不同 .space-fans 结构、视频评论为空；保留失败截图。粉丝页已补齐样式，脚本的粉丝选择器已校正。

已解决的实站问题：触屏布局中视频页抛 HierarchyRequestError，#app.__vue__ 未挂载、评论未创建；使用原生设置关闭触屏布局后同一视频正常挂载，bili-comments=1。官方 Vue 在开始 hydration 时就移除 data-server-rendered，旧判断和播放器控件提前追加 DOM 会打断初始化。已改为等待官方 Vue 根组件实际 mounted；播放器、选集和内容入口使用同一门槛，明确脚本错误才保留3.5秒兜底。新增延迟挂载超过3.5秒及失败兜底回归。

最新验证：视频/账号16项通过（device-1.2.7-hydration-regression.txt）。同一官方竖屏视频实装复测 mounted=true、comments=1、readyState=4；播放中查看评论通过，证据 device-1.2.7-mounted-portrait/comments-result.json 与原生截图。分P、合集、粉丝及我的通过（device-1.2.7-mounted-pages.txt）。真实原生设置选择3x后官方视频rate=3，实际触摸暂停/继续、横屏全屏和系统返回均通过（device-1.2.7-live-*.json、live-fullscreen-native.png）。「我的」弹窗实际返回关闭并留在原页（device-1.2.7-my-back-live.json）。

前一候选验证：86项完整回归通过（325.525秒，device-1.2.7-release-full-regression.txt），该候选同机320px账号8项通过（device-1.2.7-release-account-320.txt），23状态最终实站全部通过（device-1.2.7-release-live-pages.txt）。候选0CB1E984... / 1809802字节已从手机拉取核对，另存tested-1.2.7-before-native-input。公开UP有内容投稿也通过。

最终追加修复：实际原生搜索后键盘遗留，后续WebView高度降到400px；键盘/焦点未正确结束输入。已为搜索和打开链接增加提交/取消时的键盘清理、焦点恢复，底部导航切换清理输入；搜索支持IME搜索与实体Enter。新增SettingsInstrumentationTest原生IME专项，用本地搜索响应夹具，三轮实际打开键盘，再验证搜索按钮、硬件回车和取消后IME隐藏、视口恢复和URL。专项及追加后的完整87项已通过，先前86项不代替新实现验收。

IME专项通过：device-1.2.7-native-keyboard-final.txt，三个实际IME流程button/enter/cancel全部keyboardHidden=true、viewportRestored=true（native-keyboard-flows.txt）。首次专项的回车注入使用无时间/来源的KeyEvent而被系统拒绝，已按真实键盘事件提供uptime、虚拟键盘设备及SOURCE_KEYBOARD，保留断言；Windows增量dex文件锁曾导致一次构建失败，停止闲置Gradle daemon后重建通过，失败日志保留。

最终结果：87项完整回归通过340.554秒（device-1.2.7-final-87-regression.txt）；同机320px账号8项通过15.227秒（final-320.txt）。最终候选20122027DF7C2B535A5BAC11AA711D9CF4366EB6D6B0349F43C5AEE74D43FE90 / 1810586字节与手机拉取包一致，保存在tested-1.2.7；生产源码指纹final-source-sha256.json核对未变。网页23状态通过，最终原生入口/真实搜索与动态详情通过，见final-native-entries.json、final-native-search.json及final-dynamic-detail.json/native.png。

发布与收尾：源码标签v1.2.7为8b792cc244baa6680fdaf6047a76733402b5fa38，正式包E8C11A20B8F8DA5FA6A5C7B0CC96C79786CCAEA6EF2442ABC2BF55631328DDED / 1810586字节。正式包76个ZIP条目只有Git版本记录与候选不同。三个Release附件、GitHub摘要、匿名latest更新地址与公开APK下载全部核对通过，正式APK已覆盖安装手机并拉取核对一致；完整记录见VALIDATION.md的1.2.7节。

测试环境：原始偏好已成功恢复，快照自动删除，正式包中原倍速1x仍选中，登录态历史有内容。临时检查会话已结束，runner已卸载，WebView调试socket与tcp9222转发均为0，休眠超时已恢复600000；保留原Wi-Fi与无线ADB。手机停留在新版历史记录页。截图/几何数据保存在artifacts/device-1.2.7-*；带preview标记的结果仅供迭代，不算安装包验收。再次运行会清空偏好的旧夹具前，必须重新保存偏好快照。

验证限制：真实账号的写操作和会员字幕未执行；稍后再看有内容状态使用夹具。额外的手机PublishedUpdateSmokeTest读取GitHub时超时，保留失败日志device-1.2.7-published-update.txt，没有计入87项通过；电脑匿名公开渠道下载与校验通过。

运行中与外部动作：无待执行测试会话；源码、正式Release和手机安装均为1.2.7，发布后验收记录随文档提交同步到main。

# 2026-10-10 1.2.6 设置与流畅度检查点（历史）

任务：隐藏三点和倍速浮钮，在底部「我的」右侧新增设置，整合倍速与浏览选项，削减重复脚本工作；真机通过后更新 GitHub 与 Release。

工作位置：本项目 main，起点 16b9183（1.2.5），开始时工作区干净。生产实现、实体手机验收与 [1.2.6 正式发布](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.6) 已完成；源码标签为 fcaf0f7。最终附件与公开更新渠道结果见 VALIDATION.md 的 1.2.6 节。

设备：无线 ADB 10.93.192.96:5555，小米 9 SE / Android15 API35 / WebView153.0.8010.36 / 1080×2340 / density440。本轮使用手机已有 Wi-Fi，无需网络代理。测试时临时把 screen_off_timeout 从600000调至1800000，收尾已恢复600000；保留无线调试连接。

证据：`artifacts/device-1.2.6-full-regression.txt`（75项组合67通过）、`device-1.2.6-targeted-retest.txt`（10项9通过）、`device-1.2.6-fullscreen-final.txt`（余下1项通过），按方法去重75项均取得通过结果。新设置5项及九档真实播放计时在真机通过。扫描对照为 `device-performance-1.2.5-baseline.json` 与 `device-performance-1.2.6.json`。完整范围、失败原因及模拟器计时限制见 VALIDATION.md。

SettingsInstrumentationTest 可独立在用户手机运行，自动恢复偏好且不清理 Cookies。旧夹具会重置偏好，必须在会话前后显式运行：

```powershell
adb -s <serial> shell am instrument -w -r -e class app.bilispeed.browser.DevicePreferencesProbe#savePreferences app.bilispeed.browser.test/androidx.test.runner.AndroidJUnitRunner
# 运行所需旧夹具；即使失败也执行下面的恢复。
adb -s <serial> shell am instrument -w -r -e class app.bilispeed.browser.DevicePreferencesProbe#restorePreferences app.bilispeed.browser.test/androidx.test.runner.AndroidJUnitRunner
```

快照位于应用私有 files/device-test-preferences.json，不包含 Cookies；恢复成功后自动删除，存在旧快照时拒绝覆盖。真实页面检查使用 DeviceUiInspectionTest 临时开启调试、ADB forward tcp:9222，以及 `BILISPEED_EVIDENCE_PREFIX=1.2.6-` 的 device-ui-smoke.mjs；不覆写上一版的界面证据。停止会话后移除转发并卸载一次性测试 runner，主应用继续保留。

本轮收尾已完成：偏好快照均恢复并删除，实际倍速恢复1x；正式包已覆盖安装到手机，哈希与 Release 附件一致。runner 已卸载，临时调试与端口转发已关闭，模拟器恢复原尺寸后结束。手机停留在新版设置页。电脑匿名访问公开更新渠道通过；额外的手机在线更新检查直连 GitHub 超时，作为网络限制保留记录。

# 2026-10-10 1.2.5 真机适配修复检查点（历史）

当前任务：修复热门、搜索及播放时查看评论的显示异常，在连接的手机上逐页检查适配。完整验收证据以 [VALIDATION.md](VALIDATION.md) 的1.2.5节为准。

工作位置：本项目 main，起点0090a22（1.2.4），开始时工作区干净。本轮修复与实测已提交并发布为 [v1.2.5](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.5)；源码标签、三个附件与公开更新渠道校验记录见 [VALIDATION.md](VALIDATION.md)。

当前状态：修复、实际可访问界面验收及测试环境清理已完成。发布前验证APK已安装手机，SHA-256为324A8F9066FE5BE93F844EDEF766EBCBB5A3956804F13A0D2D0DF5D8E7882325，归档在 artifacts/tested-1.2.5/BiliSpeed-1.2.5-Android16.apk。正式包从提交后的同一源码重新构建，包内Git记录与正式发布的对应关系另见验收记录。没有未编译的生产代码或待处理的实测显示问题。

设备：1944b301 / 小米9 SE / Android15 API35 / WebView153.0.8010.36 / 1080×2340 / density440。最初无本应用，使用原签名安装基线并升级到1.2.5。保留Cookies与已有设置；实际倍速面板检查后恢复1x。手机原Wi-Fi关闭，没有默认网络。

最近验证：

- 真机布局夹具5项整组通过：artifacts/device-final-regression.txt。
- 14个真实页面状态的结果为 artifacts/device-*-result.json，实际手机截图为 device-*-native.png。原截图视频BV1qMp46wE1n的播放中评论、全屏、横屏、双击、音量、倍速、分P和合集实际点击已验证。
- 320px最终组合39项中38通过，普通双击断言间歇失败；固定测试输入时间戳后普通/全屏双击专项2项通过。按方法去重39项都有通过证据，没有声称一次组合全部通过，失败日志未删除。
- 构建、lint、语法、原签名与手机已安装APK哈希通过。真实账号登录后的内容和会员字幕未验。

测试入口：

```powershell
.\scripts\build-apk.ps1 -DeviceSerial 1944b301 -TestFilter app.bilispeed.browser.PhoneLayoutRegressionTest
```

上述夹具不清理偏好或Cookies。其他现有测试会清空偏好，应在独立模拟器运行。本轮模拟器emulator-5580的窄屏设置600×1400/density300，原设置720×1600/density300。

可选USB实站检查（仅在测试APK中临时启用WebView调试）：

1. 启动 node scripts/device-network-proxy.mjs，再执行 adb -s <serial> reverse tcp:8877 tcp:8877。仅监听电脑127.0.0.1，只允许B站相关HTTPS域名，不记录账号或请求正文。
2. 运行 DeviceUiInspectionTest，参数 inspectionProxy=127.0.0.1:8877；inspectionSeconds 默认900秒。
3. 查询应用pid后执行 adb -s <serial> forward tcp:9222 localabstract:webview_devtools_remote_<pid>。
4. 执行 node scripts/device-ui-smoke.mjs <stage> <serial>。stage支持home、search、popular、weekly、history、rank、dynamic、me、login、portrait、comments、inline-login、parts、collection。comments/inline-login基于当前视频页。
5. node scripts/device-cdp.mjs tap <selector> <serial> 和 double-tap 使用真实ADB输入。视频画面须看原生截图，CDP截图不包含硬件视频层。

停止会话：在手机创建 /sdcard/Android/data/app.bilispeed.browser/files/stop-ui-inspection。测试finally关闭调试和临时代理。然后移除端口转发、卸载 app.bilispeed.browser.test、停止网络通道，并恢复原Wi-Fi与模拟器尺寸。

收尾完成：检查会话与电脑网络通道已结束，端口8877不再监听；手机tcp:9222与reverse tcp:8877已移除，一次性测试runner已卸载，Wi-Fi恢复原关闭状态。正常启动发布应用后确认无WebView调试socket、无DEBUGGABLE标记、无本次进程崩溃日志，随后回到系统桌面。模拟器恢复720×1600/density300后关闭。保留原签名1.2.5应用、Cookies与设置。
