# Android 16 验收记录

## 1.2.6：底部设置、倍速整合与重复工作削减

验收日期：2026-10-10（北京时间）。按用户要求使用已连接无线调试的实体小米 9 SE：10.93.192.96:5555 / Android 15（API 35）/ WebView 153.0.8010.36 / 1080×2340 / density440。实站使用手机现有 Wi-Fi，未更改其联网方式。另用 Android 16 / WebView 133 模拟器验证 320px 窄屏。

- 三点与倍速浮钮在普通浏览、设置、输入和全屏中始终隐藏；底部设置位于「我的」右侧，并在电脑原版布局中保留。设置按播放、浏览与外观、常用工具、关于与更新分组，包含九档倍速、滑杆、自定义校验、记忆、布局、字幕外观、链接工具及更新开关。真机截图为 `artifacts/device-1.2.6-settings.png`、`device-1.2.6-settings-live.png`、`device-1.2.6-settings-video-native.png`。
- 新设置专项 5 项在真机组合运行中全部通过：真实原生触摸切换九档与输入、无效数值、持久化及忘记倍速、布局和更新开关、返回时网页身份 / 滚动位置 / 播放保留、动画变化过滤及视频替换。九档实际播放计时按 3 次采样取中位数，1 / 1.25 / 1.5 / 2 / 2.5 / 3 / 3.5 / 4 / 5x 分别约为 1.000 / 1.250 / 1.501 / 2.001 / 2.500 / 2.998 / 3.499 / 3.996 / 4.998x。原始日志为 `artifacts/device-1.2.6-real-rate-timing.txt`；这是本地测试媒体，不代替所有网络视频的解码表现。
- 对照手机原装 1.2.5 与候选 1.2.6，在相同 12 批 / 648 个弹幕元素的受控变化中，播放器发现的子树扫描从 216 次降到 0 次，两个版本都能发现随后加入的视频。探针为 `scripts/device-performance-probe.mjs`，结果为 `artifacts/device-performance-1.2.5-baseline.json` 和 `device-performance-1.2.6.json`。不把计数变化换算成整机帧率或续航提升。
- 真实首页、搜索、热门、我的、竖屏视频和播放中评论通过尺寸与截图检查，证据为 `artifacts/device-1.2.6-*-result.json` 和 `device-1.2.6-*-native.png`。在 `BV1qMp46wE1n` 的官方播放器中通过原生设置选择 3x，实际 rate=3；评论滚动时视频不遮挡内容。横屏全屏保持比例，无悬浮按钮；真实 ADB 双击可暂停 / 继续。3x 播放时全屏控件隐藏的 1.6 秒内观察到内部 0 次 DOM 变化，显示后同步控件。
- 真机完整组合运行 75 项，67 项通过、8 项失败，记录保留在 `artifacts/device-1.2.6-full-regression.txt`。针对失败项及个人页追加 10 项，9 项通过，见 `device-1.2.6-targeted-retest.txt`；最后的放大字幕全屏操作专项通过，见 `device-1.2.6-fullscreen-final.txt`。按方法去重，75 项全部取得通过结果；不声称同一次 75 项整组全通过。测试覆盖倍速、iframe / Shadow DOM、后台暂停、全屏 / 手势 / 音量 / 字幕、布局 / 评论 / 选集、个人页、更新校验和真实渲染进程恢复。
- 上述失败的修复限于测试夹具：新页面用唯一标识等待，页面提交丢弃旧 JavaScript 回调时在原超时内重新查询；个人页夜间开关按实际初值检查并恢复；全屏触摸等待横屏和系统栏 resize 完成。新 Chromium 会先压缩 data URL 历史，历史回归按真实序列化体积判断保留或降级，仍检查保存状态上限、HTTPS 链接和倍速。生产代码及断言目标未为复测改变。
- Android 16 窄屏组合 34 项中 33 项通过，新增的实际播放计时在模拟器 1x 测得 0.644x、未通过；对应真机九档计时全部通过。保留 `artifacts/emulator-1.2.6-narrow-final.txt`，不以模拟器计时失败代替实体手机结果。早期启动的模拟器出现 `System UI isn't responding` 拦截输入，记录为 `emulator-1.2.6-full-regression.txt` 和 `emulator-1.2.6-progress.png`；冷启动后完成上述窄屏检查。
- 发布构建、Android lint（0 errors / 4 个原有 warnings）、JavaScript 语法和原签名 v2 校验通过。真机安装包拉取后的 SHA-256 与候选包一致：`2D95BF94CBD3B1534B969417ABFCCBFAA2CE9A029866AA08E4915353585DE6B1`，大小 1804962 字节，versionName 1.2.6 / code 10，minSdk26 / targetSdk36。归档为 `artifacts/tested-1.2.6/`。正式包将从提交后的同一源码构建，并独立核对提交记录和公开附件。

每轮旧夹具运行前通过 DevicePreferencesProbe 在应用私有目录保存 playback / updates 全部偏好，结束后恢复并删除快照；保存与恢复专项均通过。未清理 Cookies，未卸载主应用。SettingsInstrumentationTest 自身也保存和恢复偏好。账号登录后的内容及会员字幕仍未使用真实账号验收。

## 1.2.5：热门、搜索、播放评论与实体手机适配

验收日期：2026-10-10（北京时间）。实体设备为小米 9 SE / Android 15（API 35）/ WebView 153.0.8010.36 / 1080×2340 / density440；实际竖屏 WebView 约392×717 CSS px。另用 Android 16 / WebView 133 模拟器验证320px窄屏。

- 在手机上的1.2.4基线复现搜索卡片被挤成28.84px、播放时查看评论被fixed小窗遮挡。修复后搜索卡片宽179.36px、热门列表宽368.73px，五个热门分类可见，排行榜单列、封面比例正常，播放量与长时长不再挤压。前后证据为 `artifacts/device-search-before.png`、`device-comments-before.png` 及对应 `device-*-native.png`。
- 首页、搜索、综合热门、每周必看、入站必刷、排行榜、动态、我的、登录、竖屏视频、评论、内嵌登录、分P和合集共14个真实页面状态通过尺寸检查并经截图核对。每页结果为 `artifacts/device-*-result.json`，原生截图为 `device-*-native.png`。
- 使用截图中的同一视频 `BV1qMp46wE1n`（柯洁围棋入门课2）验证播放中查看评论。视频readyState=4、paused=false，mini播放器保持在原页面位置并滚出视口，评论不被覆盖。Shadow DOM评论的日期、点赞与回复分行，新增内容和返回前台的夹具检查通过。
- 登录弹窗标题、协议与表单完整留在屏内。键盘弹出后WebView高约400px，弹窗高367.45px，输入框与关闭按钮仍可见。UIAutomator验证浮钮在输入时隐藏、收起键盘后恢复。截图为 `artifacts/device-login-keyboard-native.png`。
- 普通播放栏从132px降为88px，底部padding从误用的48px安全区恢复为4px；实际全屏继续避让系统边缘。原视频在横屏全屏中保持比例，真实ADB双击可继续播放和暂停，音量面板在屏内。原生倍速面板选择3x后视频rate=3，随后恢复原来的1x；未把这个检查当作逐档实际播放计时。
- 手机真实点击分P第二集后，地址为 `BV17x411w7KC/?p=2`，官方cid=275431，高亮同步。150集合集真实点击后切换到 `BV1xXtTeZEVR`，cid=25861685381，高亮同步。一次早期点击被站方自动登录窗拦截，关闭提示后重新实际点击通过，没有绕过登录限制。
- 最终发布构建、Android lint、JavaScript语法和原签名v2校验通过。lint为0 errors / 4个原有warnings。手机 `PhoneLayoutRegressionTest` 5项整组通过，见 `artifacts/device-final-regression.txt`。
- 最终APK的320px组合回归运行39项，38通过、普通双击1项间歇超时，保留 `artifacts/phone-layout-320-final.txt`。双击夹具改为明确注入两次50ms接触、间隔100ms的时间戳后，普通/全屏双击专项2项通过，见 `artifacts/phone-layout-doubletap-fixed.txt`。按方法去重，最终APK的39项均取得通过结果；这是分阶段结果，不能称为同一次39项整组通过。断言未降低，生产双击识别逻辑未改动。
- 早期模拟器38项检查中8项失败，原生截图显示 `System UI isn't responding` 弹窗拦截输入。关闭系统弹窗后，前一候选38项整组通过。早期失败保留在 `artifacts/phone-layout-320-systemui-failure.txt`、`emulator-player-failure.png`、`emulator-danmaku-failure.png`；前一候选通过记录为 `phone-layout-320-before-ime.txt`，不代替最终APK结果。

发布前验证包：`artifacts/tested-1.2.5/BiliSpeed-1.2.5-Android16.apk`，versionName1.2.5 / versionCode9，1795346字节，minSdk26 / targetSdk36，关闭调试。SHA-256：324A8F9066FE5BE93F844EDEF766EBCBB5A3956804F13A0D2D0DF5D8E7882325。从手机拉取已安装APK后哈希完全一致；归档的update.json与校验和文件使用相同版本和哈希。GitHub正式发布附件与提交后构建的校验另行记录。

范围限制：手机没有已登录账号。实际登录后的更多评论、历史、收藏及会员字幕未使用真实账号验收；个人页已登录、失效和网络失败由受控夹具覆盖。手机最初未联网，实站测试通过仅本机监听的USB HTTPS隧道使用电脑网络，TLS仍由官方服务器验证。CDP截图不包含Android硬件视频层，视频画面使用ADB screencap验收。收尾状态与复现命令见 [DEVICE-TESTING.md](DEVICE-TESTING.md)。

正式发布与附件验证（2026-10-10）：

- [v1.2.5 Release](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.5) 于北京时间 12:29:47 发布为最新正式版本，非草稿、非预发布。源码标签与包内提交记录均为 4ac76eb42d34045c9b5a9ab76991fefd6e0b92d1，已推送至 GitHub。
- 从已提交源码重新执行 assembleRelease 和 lintRelease 通过，正式 APK 为 1795346 字节，SHA-256：07CDFE10301CF75A92CA246A76964E7796AE9CFA2BC0F42942C01E5DA2EEBB98。APK Signature Scheme v2 校验通过，原证书 SHA-256 为 e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3。
- 对比真机验证包的 76 个解包条目，仅 META-INF/version-control-info.textproto 的 Git 提交记录变化；应用代码、资源和脚本完全一致。没有因重新构建而重复计入界面测试结果。
- 正式附件齐全：BiliSpeed-1.2.5-Android16.apk、update.json 与 SHA256SUMS.txt。全部重新下载后，文件大小、SHA-256、本机原文件与 GitHub 附件摘要均匹配，发布说明与 release-notes.md 一致。update.json 为 1498 字节、SHA-256：A03DE88051D5665852899DE7D6C61B35A7C250DC7025DF2E2E224C0768F212B1；SHA256SUMS.txt 为 97 字节、SHA-256：D12742D7DB1385E4E52D198B51243E14B20F932A645020E6D988F2383F05F0C9。下载保存在 artifacts/release-verify-1.2.5/。
- 未使用 GitHub 登录凭据请求应用实际使用的 https://github.com/GodBook/bilispeed-android/releases/latest/download/update.json，确认返回 1.2.5 / code 9，内容与正式附件一致。再从其中的公开 APK 地址下载，大小、哈希、原签名与 v2 校验全部通过，下载保存在 artifacts/public-update-verify-1.2.5/。发布后渠道检查在主机完成，没有另行重跑 Android 下载 instrumentation；上方记录的真实账号与计时验证限制仍保留。

## 1.2.4：视频内容加载、分集切换与直接进入的「我的」页

验收日期：2026-10-09。本轮「我的」采用参考图的头像资料、动态 / 关注 / 粉丝和四个常用入口，按追加要求移除大会员横幅。

- assembleRelease、lintRelease、五个 JavaScript 文件以及两个 PowerShell 脚本的语法检查通过；Android lint 为 0 errors / 4 warnings，均为原有警告。
- 320px 窄屏（600 × 1400 / density 300）组合回归运行 50 项，49 项通过、1 项全屏双击超时，记录为 artifacts/detail-profile-320-regression.txt。通过项包括布局 7 项、选集与加载 6 项、个人页 4 项、触屏播放器 16 项、渲染进程恢复 3 项、更新校验 12 项和视频替换后倍速保持 1 项。不能把该组合写成全部通过。
- 最终候选复核的 9 项中，新增选集与加载 6 项、真实 B站分 P 和合集 2 项均通过，全屏双击仍超时；记录为 artifacts/detail-profile-final-candidate.txt。本轮按方法去重共有 51 项取得通过结果，是分阶段汇总，未证明完整套件一次通过。
- 视频页等待官方服务器页面初始化后再添加下方内容入口；官方初始化持续等待且已有视频工具栏时，约 3.5 秒后使用现有元数据兜底。检查覆盖等待初始化、分 P 地址、合集内不同 BV、当前集高亮、时长、失败重试、迟到请求隔离、服务器合集数据不完整时重新获取，以及原按钮可操作性。
- 真实合集「英雄联盟整活小剧场」显示 150 集；通过真实触屏输入选择另一视频，地址切为 BV1xXtTeZEVR，官方 cid 为 25861685381，标题和选集高亮同步更新。真实 BV17x411w7KC 显示 10 个分 P；点击第二集后，地址为 ?p=2，官方 cid 为 275431，高亮指向第二集。UP 主头像实际加载成功，简介有完整内容。截图为 artifacts/BiliSpeed-collection-fixed.png 和 BiliSpeed-video-parts-fixed.png。
- 实站导航检查限制自动播放，主要验证地址、官方 cid、选集高亮及内容加载。早期附加媒体 readyState 检查遇到 WebView 响应超时，见 artifacts/detail-parts-live-isolated.txt；最终检查聚焦本轮分集导航，没有把这次超时当作视频解码验证通过。
- 「我的」的实际本机页面入口、样式和脚本加载、夜间模式、未登录、网络失败重试、长昵称文本安全、登录失效时清除资料、统计与账号对应的空间 / 收藏地址均通过。登录资料使用受控接口夹具，没有使用真实账号登录。实际输出不包含大会员横幅；截图为 artifacts/BiliSpeed-my-without-vip.png 和 BiliSpeed-my-without-vip-320.png。四个入口中，离线缓存显示暂不支持下载的说明，没有新增离线下载功能。
- 全屏双击的播放状态与控件可见性联合断言，在 320px 组合、隔离运行及恢复 384px 后仍超时，记录保留。最终 player-controls.js 与原 1.2.3 源码完全一致，Git blob 均为 3e0e3387387fc719a3872068cdf6f2ddd4bbff7c；该项未列为本轮修复或通过。普通双击、滑动进度、取消手势、字幕、弹幕、音量、全屏显示隐藏、换视频和旋转的回归取得通过结果。

测试环境：Android 16 / API 36 x86_64，WebView 133.0.6943.137，软件 GPU。窄屏检查后恢复 720 × 1600 / density 300。未连接实体手机，未验证真实账号、会员字幕、逐档实际播放计时或耗电。旧版已有的相关限制继续保留。

最终候选包：artifacts/tested-1.2.4/BiliSpeed-1.2.4-Android16.apk，versionName 1.2.4 / versionCode 8，1793518 字节，targetSdk 36 / minSdk 26，发布构建关闭调试。SHA-256：77B868CC61B48803294EAEB3870F9A8BBF07169FA59D028B312C07A1B0DE9BE7。APK Signature Scheme v2 校验通过，原证书 SHA-256：e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3。APK、update.json 与 SHA256SUMS.txt 的版本、大小和哈希一致，已在模拟器覆盖安装。候选包与正式发布包的源码提交记录及附件校验另行记录。

正式发布与附件验证（2026-10-09）：

- [v1.2.4 Release](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.4) 于北京时间 22:43:43 发布为最新正式版本，非草稿、非预发布。源码标签与包内提交记录均为 714a0e386e4e6d28673ddcc299eb9ce4e6635c7f，已推送至 GitHub。
- 从已提交源码重新构建并通过 lint、原签名与 v2 签名校验。正式 APK 为 1793518 字节，SHA-256：6CB842D540F1EDAAC422A89829C143DF0EFDC22B39E1D7394E5E060FD33E8D8F。对比候选 APK 的 76 个解包条目，仅 META-INF/version-control-info.textproto 的 Git 提交记录变化，代码、资源与脚本一致。
- 正式附件齐全：BiliSpeed-1.2.4-Android16.apk、update.json 与 SHA256SUMS.txt。全部重新下载并核对本机文件、GitHub 附件摘要与大小，均匹配。update.json 为 1686 字节、SHA-256：893799427CFA37AC0E6B47756BA8629A029D172D146520FE5790F205033F3A01；SHA256SUMS.txt 为 97 字节、SHA-256：5C888A051D9646EE11C5D0C92E25134D1BEAA06F50D2E4157BF0CF07274FD23B。记录保存在 artifacts/release-verify-1.2.4/。
- 未使用 GitHub 登录凭据请求应用实际使用的 https://github.com/GodBook/bilispeed-android/releases/latest/download/update.json，返回 1.2.4 / code 8，内容与正式附件一致；再从其中的公开 APK 地址下载，大小、哈希和原签名均匹配。记录保存在 artifacts/public-update-verify-1.2.4/。发布后渠道检查在主机完成，没有另行重跑 Android 下载 instrumentation；前述全屏双击、解码和真实账号限制仍保留。

## 1.2.3：按钮外观、双击和弹幕 / 个人中心适配

验收日期：2026-10-09。以下功能记录对应原签名的发布前候选 APK；正式发布附件与更新渠道验证在本节末另行记录。

- assembleRelease、lintRelease 与三个 JavaScript 文件语法检查通过；Android lint 为 0 errors / 4 warnings，均为原有警告。
- 本轮按测试方法去重，**45 项取得通过结果**：触屏播放器 17 项、布局与按钮设置 7 项、渲染进程恢复 3 项、播放核心 15 项、真实 B 站 3 项。这是分组及隔离复测的汇总，完整 46 项组合没有一次全部通过；逐档倍速计时的限制见下文。
- 新增按钮设置检查覆盖六个滑杆、即时预览、字幕按钮实际 CSS、重启后保存、恢复默认、最大尺寸及原生按钮边界。三点和倍速按钮缩小后仍保留至少 44 × 48dp 的触摸范围。截图：artifacts/BiliSpeed-button-appearance-320.png。
- 真实单指输入覆盖普通 / 全屏的双击播放与暂停、暂停遮罩、单击不暂停、屏蔽官方兼容点击、滑动、双指、长按、取消及换视频。实站繁忙播放时发现按 JS 处理时间识别触摸会误判，已改为事件原始时间并保留一个单击时间记录；最新实站复核为 OK (1 test)，见 artifacts/touch-refinement-live-delivery-check.txt。
- 320px 窄屏（600 × 1400 / density 300）分轮确认全部 24 项播放器 / 布局方法通过，记录为 artifacts/touch-refinement-320px-check.txt 和 touch-refinement-320px-recheck.txt；后者为 OK (2 tests)，补齐原组合中受输入时序影响的全屏双击与弹幕关闭检查。最大字幕按钮下，音量面板、字幕和控制栏均在播放器边界内。截图：BiliSpeed-large-subtitle-fullscreen-320.png。
- 弹幕适配使用官方弹幕控件，修复外层位置和内部 BUI 面板的固定宽高，按当前基础 / 高级面板内容排版；新增完成、再次点击图标和面板外关闭。真实 B 站检查通过不透明度调节、基础 / 高级切换、关闭后继续滑动进度、播放和全屏；滑杆检查选择不同于上次保存的数值，允许重复运行。截图：artifacts/BiliSpeed-live-danmaku-settings.png、BiliSpeed-live-danmaku-advanced.png。
- 个人中心使用官方公开容器结构和固定尺寸的本地夹具，验证横向菜单、长昵称、头像区域、每日奖励、昵称表单边界及菜单 / 输入操作。截图：artifacts/BiliSpeed-account-mobile-320.png。未使用真实账号登录，不能把该夹具检查当作登录后全部账号页面的实测。
- 最终候选的三项真实渲染进程恢复检查通过，见 artifacts/touch-refinement-delivery-check.txt；该轮实站滑杆曾因点击与已保存数值相同而失败，之后在上述实站隔离检查中通过。搜索 / 热门在 touch-refinement-live-final-check.txt 中通过，动态 / 官方登录表单在 touch-refinement-final-build.txt 中通过。
- 窄屏检查后已恢复模拟器原有的 720 × 1600 / density 300 设置。测试使用 Android 16 / API 36 x86_64、WebView 133.0.6943.137；未连接实体手机，未验收登录账号、会员字幕和耗电。

保留的失败与环境记录：首次回归遇到 System UI 无响应遮挡触摸；长组合检查中的逐档播放计时在 3x 测到 0，随后主机 GPU 模式的模拟器退出，日志出现 bad color buffer handle。恢复后使用软件渲染进行界面与实站复测，未重新证明全部档位的实际时间比例。初次失败记录保存在 touch-refinement-first-tests.txt、touch-refinement-regression-first-tests.txt 等文件中，未算作通过。触摸时序、面板固定高度 / 关闭、设置消息及重复滑杆值的问题已分别修正并复测；不能将剩余计时波动称为已修复。

候选安装包：artifacts/tested-1.2.3/BiliSpeed-1.2.3-Android16.apk，versionName 1.2.3 / versionCode 7，1782609 字节，targetSdk 36 / minSdk 26，发布构建关闭调试。SHA-256：0A73F1B6DAD1468073542990775BCC7F519BEA2E517EDAD1A9FADC590194B381。APK Signature Scheme v2 校验通过；证书 SHA-256 与原版本一致：e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3。APK、update.json 和 SHA256SUMS.txt 来自同一构建，已在模拟器覆盖安装验证。

正式发布与附件验证（2026-10-09）：

- [v1.2.3 Release](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.3) 于北京时间 20:44:56 发布为最新正式版本，非草稿、非预发布；附件齐全：BiliSpeed-1.2.3-Android16.apk、update.json 和 SHA256SUMS.txt。源码标签指向 072bd7c5487c838d6ecd0608ec7eed1e2ffb2908。
- 从已提交源码重新执行 assembleRelease 和 lintRelease 通过，lint 为 0 errors / 4 warnings。正式 APK 为 1782609 字节，SHA-256：A3C5DED1F5E6E9AA4CD6B5F36C9748467116A96C2A6AD9158E776A21FFE1AA5E。原签名与 APK Signature Scheme v2 校验通过，versionName 1.2.3 / versionCode 7，targetSdk 36 / minSdk 26，关闭调试。
- 正式 APK 与上述候选 APK 按解包条目比较，72 个条目中仅 META-INF/version-control-info.textproto 的 Git 提交记录发生变化，应用代码、资源和播放器脚本一致；正式包内的提交记录与源码标签一致。候选包及其更新信息、校验文件保存在 artifacts/tested-1.2.3/。
- 重新下载全部三个正式附件，逐个核对本机文件、GitHub 附件摘要及文件大小，均一致；更新版本、包名、发布说明、APK 哈希和校验文件匹配。update.json 为 1405 字节，SHA-256：5775D70DBB1A0780C5CD0FEAD6E645B23C1A7593544E603EA92725599CF65179；SHA256SUMS.txt 为 97 字节，SHA-256：27E807A49854007CA72821627CF302955E7FA6449A557EC19A60796944FB8944。下载文件保存在 artifacts/release-verify-1.2.3/。
- 不使用 GitHub 登录凭据请求应用实际使用的 https://github.com/GodBook/bilispeed-android/releases/latest/download/update.json 地址，确认返回 1.2.3 / code 7，与发布附件逐字节一致；再从其中的正式公开 APK 地址下载，哈希、大小、版本和原签名校验通过。
- 本轮发布后验证在主机执行，没有另行重跑 Android 上的正式附件下载 instrumentation；上述实体设备、真实账号和逐档播放计时限制仍保留。

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

正式发布与附件验证（2026-10-09）：

- [v1.2.2 Release](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.2) 已发布为最新正式版本，附件齐全：BiliSpeed-1.2.2-Android16.apk、update.json 和 SHA256SUMS.txt。源码标签指向 4e19cfe2bb7821eed14e1e3ef7e969095243563f。
- 正式 APK 为 1774321 字节，SHA-256：C79BF7DA803B11492842450EE0A39A43B172B8D27FCC0D281A9951674E5EB0E2。原签名与 APK Signature Scheme v2 校验通过。
- 正式 APK 与上述测试 APK 按解包条目比较，72 个条目中仅 META-INF/version-control-info.textproto 的 Git 提交记录发生变化，应用代码、资源和播放器脚本一致。正式构建的提交信息对应发布源码标签。
- GitHub 返回的三个附件摘要与本机文件一致；重新下载全部三个附件后，版本、大小、SHA-256、更新信息和校验文件全部匹配。下载文件保存在 artifacts/release-verify-1.2.2/。
- 已请求应用实际使用的 https://github.com/GodBook/bilispeed-android/releases/latest/download/update.json 地址，确认返回 1.2.2 / code 6，下载地址、大小和哈希均与正式 APK 匹配。本轮发布后验证在主机执行，没有另行重跑 Android 上的正式附件下载 instrumentation。

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
