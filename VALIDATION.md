# Android 验收记录

## 1.3.2：新图标与运行开销优化（已发布并回装公开正式包）

验收日期：2026-10-14（北京时间）。使用 USB `1944b301` / 小米 9 SE / Android 15 API 35 / WebView 153.0.8010.36 / 1080×2340 / density 440。本次没有另行运行 Android 16 模拟器；APK 的 targetSdk 为 36，minSdk 为 26。

最终测试 APK 为 **1.3.2 / code17，2131604 字节，SHA-256 `298D28291F2DB3DBFD9E593CDF7E8880F0FCF0B355846213254E8E948FFA600C`**，归档于 `artifacts/tested-1.3.2-final`。36 个生产 / 构建文件及原图的指纹为 `artifacts/device-1.3.2-final-source-sha256.json`，专项复验后逐项核对未变。

| 检查 | 结果 | 主要证据（artifacts 下） |
| --- | --- | --- |
| 最终生产包核心集合 | 127 项去重均有通过结果：整组 126/127（544.014s），个人中心夹具一次页面回调超时；补充文档就绪信号后专项 1/1（2.544s），APK 哈希保持一致 | device-1.3.2-final127.txt、final-account.txt |
| 实际 320px 窄屏 | 5/5（36.366s），含自动隐藏、按住进度保护、单行控件与新增进度 / 字幕同步 | device-1.3.2-final-320.txt |
| 官网真实操作 | 56 个状态分阶段全部通过：首页 10、自动隐藏 10、课程 11、单视频 2、边界 3、全屏合集 5、导航 / 主题 / 画质 9、深色页面 6 | device-1.3.2-live-all-attempt3.txt、live-from-course-final.txt；各阶段 JSON / native.png |
| 新图标 | 系统应用信息页显示新的蓝色电视图标，目视核对主体与遮罩；首页、竖屏控件及深色全屏画质截图已核对 | device-1.3.2-icon-app-info.png、home-final-native.png、portrait-tap-controls-native.png、quality-fullscreen.png |
| 相同 DOM 工作量 | 20 次列表定位、12 批新增内容；新视频、开放 Shadow DOM 及移除后重新连接均成功发现 | device-1.3.1-baseline-overhead.json、device-1.3.2-candidate-overhead.json |
| 原生 Activity 显示 | 两组各 20 冷 / 20 热启动完成 | device-1.3.1-baseline-native-launch.json、device-1.3.2-candidate-native-launch.json |
| 构建与静态检查 | release、JS / PowerShell 语法、git diff --check 通过；lint 0 errors / 4 warnings（3 项既有，1 项为自适应 XML 与密度位图并存提示） | device-1.3.2-final-account-build.txt、lint-results-release.txt |
| 原签名覆盖安装 | 原 SHA-256 证书 `e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3`，主应用不卸载、不清除 Cookies | build-apk.ps1 / apksigner verify |

同机固定工作量的对照：

| 指标 | 原版 1.3.1 | 1.3.2 |
| --- | --- | --- |
| 600 项列表定位 20 次的几何读取 | 8,900 次 | 20 次 |
| 列表定位 JS 中位数 / P95 | 5.15 / 9.80ms | 0.50 / 0.70ms |
| 12 批新增内容的全节点扫描 / 访问节点 | 12 次 / 18,024 | 0 / 0 |
| 新视频发现及 Shadow DOM 重连 | 通过 | 通过 |
| 原生冷启动中位数 / P95 | 601.5 / 639ms | 595 / 725ms |
| 原生热启动中位数 / P95 | 0 / 0ms | 0 / 0ms |

探针使用安装后的生产代码，在本地个人页替换有限测试 DOM，统计该工作量的查询和 `performance.now()` 时间，不替换应用脚本。原版基线为未经功能修改的 1.3.1 原签名重建包；与公开 1.3.1 的 78 个 ZIP 条目逐项对照，仅 `META-INF/version-control-info.textproto` 不同（`device-1.3.2-baseline-source-compare.json`）。这些计数和时间不代表整机帧率、耗电、总内存或官网网络加载速度。原生启动数据是 `am start -W TotalTime`，热启动 0ms 表示已有 Activity 被带到前台；中位数接近，P95 本轮较高，未取得启动加速证据。

新增四项回归分别覆盖深层列表插入内容后的转义链接锚点恢复、嵌套 Shadow DOM 断开后重连、延迟注入后由缓存原生 `attachShadow` 创建的媒体，以及 100 次进度事件不查询字幕菜单、进度和字幕状态仍同步。弱引用路径只在文档开始阶段启用；旧引擎或延迟注入保留完整扫描，兼容网页此前缓存原生方法的情况。普通进度事件仅更新进度和时间，隐藏控件仍停止更新无障碍说明，重新显示时同步。

图标使用用户提供的 1254×1254 RGBA 原图，保留在 `assets/app-icon-source.png`，SHA-256 `F6DF443EF2B7BD9780FAEB7F0A939077D3B6A554C8F49CA9F2AB30A4BE2893B8`。生成器清理外侧散点并生成五种密度、432px 自适应前景，单色电视轮廓用于 Android 主题图标。圆形预览按 Android 中央 72dp 遮罩绘制；原图不放入 APK 的网页 assets。

保留的中间记录：前一候选 126/126（536.946s），随后完成单色图标与延迟注入兼容。新增测试最初存在 Java 等待 helper 参数错误、`0:02` 与原时间格式 `00:02` 的预期不符，记录为 `candidate-build.txt` 与 `overhead-regression.txt`，修正后最终包的新测试均通过。最终整组唯一失败为既有个人中心夹具加载后的首个 WebView 回调超时，补充原生 DOMContentLoaded 信号后保留全部边界与表单断言，专项通过，生产 APK 未改变。

官网初次两轮首页检查被手机前台切换中断：第一轮完成 9 个状态后返回桌面；第二轮刷新时前台为另一个被测应用，后台触屏排版已暂停。设备事件日志确认另一应用的自动 instrumentation 测试正在使用同一手机。记录保存在 `device-1.3.2-live-all-attempt1/2.txt`、`home-live-attempt1/2.json`；未计入完整通过结果。官网工具已增加输入 / 截图前的本应用焦点检查及最后点按记录，等待其他测试释放设备后完整复验。

课程前两轮的「上一集」检查超时，记录保留在 `player-course-attempt1/2-failure.json`。第二轮实际 pointerdown 命中 VIDEO，控件当时已隐藏，按钮动作未执行。工具补充可信原生触摸的目标核对，只对同文档中确认未命中的点按重新定位，已匹配的按钮动作保持一次执行；导航后的原分 P、选集、全屏与播放断言继续保留。随后手工诊断遇到另一应用再次占用前台，焦点检查阻止了该次输入（`touch-diagnostic.json`）。

另一应用完成设备恢复后，课程 / 单视频 / 边界 / 全屏合集 / 新功能 / 深色页面的 36 个状态完整通过，结合已完整通过的首页及自动隐藏 20 个状态，共 56 个。新功能包括实际 480P、360P、自动及横屏 480P 切换，检查播放位置、2.5x 和全屏状态保留；返回列表保位、重复入口保留文档及原生 / 网页统一主题也通过。深色覆盖首页、搜索、热门、动态、历史、稍后再看。

测试结束后，手机实际安装包 SHA-256 核对为上述 `298D2829…`（`device-1.3.2-tested-installed-sha256.txt`），生产文件指纹未改变。原生偏好及每次会话进入时的系统设置已恢复；最终系统读取为休眠 600000、自动旋转 0、user_rotation 0、字体 1.0。官网通过本任务 localhost HTTPS 隧道联网，TLS 在官方服务器终止，没有记录账号或请求正文；真实账号的提交、删除与购买未执行。

发布完成：[v1.3.2](https://github.com/GodBook/bilispeed-android/releases/tag/v1.3.2)，源码与标签提交为 `0d6022f1d392fad9b129a105e5268ec882bf719e`。从该提交重建的正式 APK 为 **2131604 字节，SHA-256 `F754F6C965820D12CC1AA012F8E710D14D0E333D0EEF7492FD5E65269008455B`**。84 个 ZIP 条目逐项比较，仅 `META-INF/version-control-info.textproto` 与手机实测候选不同（`device-1.3.2-release-compare.json`）。

匿名 GitHub API 读取返回 403，元数据通过 `verify-published-release.ps1 -AuthenticatedMetadata` 的已登录 CLI 核对；三个 Release 附件及 `latest/download/update.json` 仍通过匿名公开 URL 下载。正式 / latest 标签、源码提交、三个资产摘要、公开更新 schema / 包名 / 版本 / 大小 / APK URL、SHA256SUMS 和原签名全部通过，记录为 `device-1.3.2-public-verification.json` 与 `github-release.json`。

公开下载的正式 APK 已覆盖安装同一手机，再拉取 `installed.apk` 核对上述正式包哈希及 1.3.2 / code17 / 无 DEBUGGABLE，记录为 `device-1.3.2-installed-public.json`，下载及拉取副本归档于 `artifacts/published-1.3.2`。SDK 安装使用的两份英文临时路径 APK 副本保留在本机 Temp，避免中文路径兼容问题；不影响手机运行。

收尾完成：一次性测试 runner 已卸载，可从源码重新构建；正式应用调试 socket、本任务 9222 forward、8877 reverse 和 localhost 隧道均为 0 / 已关闭（`device-1.3.2-final-cleanup.json`）。手机停留在正式版设置页，1x 与记住倍速保持选中，截图 / XML 为 `device-1.3.2-published-settings.png/xml`。主应用与 Cookies 保留，最终验收记录单独提交 main，发布标签保持不动。

## 1.3.1：竖屏控制条自动隐藏与触摸动效（已发布并回装公开正式包）

验收日期：2026-10-14（北京时间）。连接设备为 USB `1944b301` / 小米 9 SE / Android 15 API 35 / WebView 153.0.8010.36 / 1080×2340 / density 440。本版使用这部连接手机验收，没有另行运行 Android 16 模拟器；APK 的 targetSdk 仍为 36。

最终生产代码的测试 APK 为 **1.3.1 / code16，1835256 字节，SHA-256 `28E4FB75B800124F7484DB109137FFEB35395362180DF3230FA813931B44073B`**。27 个生产 / 构建文件指纹记录在 `artifacts/device-1.3.1-final-source-sha256.json`。

| 检查 | 结果 | 主要证据（artifacts 下） |
| --- | --- | --- |
| 连接手机核心集合 | 最终 APK 的 123 项去重均有通过结果：整组 122/123（534.209s），个人中心夹具一次 WebView 回调超时；该项原测试独立 1/1 通过（2.561s），生产 APK 哈希未变化 | device-1.3.1-final123.txt、device-1.3.1-final-account.txt |
| 实际 320px 窄屏 | 4/4 通过（33.959s），含三项新增触摸检查及单行选集 / 更多入口 | device-1.3.1-final-320.txt |
| 官网触摸场景 | 33/33 分阶段通过：浅色自动隐藏 10、课程 / 全屏切集 11、单视频 2、深色自动隐藏 10；均检查信息区与操作栏分离 | device-1.3.1-live-autohide/course/single/dark-autohide.txt 及对应 player JSON / native.png |
| 全屏深色画质面板 | 真实 ADB 点击打开，6 个官网画质选项；面板完全位于 821×392 CSS 视口内，关闭及系统返回正常 | device-1.3.1-fullscreen-quality-panel.json / native.png |
| 构建与静态检查 | release 构建、全部应用 JS、检查脚本语法及 git diff --check 通过；lint 0 errors / 3 既有 warnings | lint-results-release.txt |
| 原签名 | 原 SHA-256 证书 `e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3`，覆盖安装保留应用数据 | apksigner verify --print-certs |

竖屏控制条与进度条在约 3 秒无操作后隐藏，单击显隐不改变播放状态，双击继续暂停 / 播放。拖动、按住当前进度点、打开面板及键盘操作保护沿用同一计时逻辑；修正没有 input/change 事件的进度按压结束后未清理交互状态的问题。隐藏同时设置 aria-hidden/inert 并停止不可见进度更新。

UI 使用细轨道、统一 SVG 图标、轻量显隐 / 面板 / 字幕避让动效和触摸反馈。缩放作用于图标，按钮保留至少 44px 点击区域；选集当前项及按压反馈使用浅 / 深色主题对应颜色。信息区改为按内容自动撑高，避免长标题下播放数据、授权说明与点赞栏重叠。减少动态效果的 CSS 路径通过真实页面的 CDP 媒体偏好模拟检查，不将其表述为系统设置或 TalkBack 的完整验收。

测试流程先唤醒屏幕，播放器夹具等待原生竖屏尺寸与 DOMContentLoaded 的原生信号，字幕避让和退出全屏后的边界断言等待动画 / 布局后的原几何条件，保留至少 44px 的点击区域与原边界要求。前期 `device-1.3.1-player-regression.txt`（休眠时启动）、`player-awake.txt` / `isolated-*`（页面加载回调竞争）、`player-ready.txt`（28/29，字幕动画尚未结束时立即检查）和前一候选的 `full-regression.txt`（122/123，布局瞬态；专项 `layout-settled.txt` 1/1）均保留，不计入最终构建的通过结果。实站 `live-autohide-attempt1.txt` 记录动画中 43.999988px 的浮点边界误判，随后改为等待控制条过渡完成再测量，尺寸断言保持 44px。早期 `device-1.2.9-player-autohide.json` 是未设置证据前缀的中间构建检查，最终证据使用 1.3.1 前缀。

实站初次加载时隐藏状态曾在两次读取间改变（`player-autohide-attempt2-failure.json`），检查改为同时确认隐藏、inert、aria-hidden 和淡出完成的状态持续 600ms，随后浅 / 深色各 10 项通过。目视核对最终浅色 / 深色隐藏画面、唤出控制条、倍速面板及横屏画质面板；三行长标题的信息区实测 140.5px，授权说明在容器内，点赞栏位于其下方。

额外沿用 1.3.0 官网导航 / 画质专项的两轮结果单独保留，不计入上述 33 个通过场景：`live-navigation-quality-attempt1.txt` 为 8/9（480P / 360P / 自动通过，全屏旋转中点按准备未命中）；完善点按前的几何等待与命中复核后，第二轮为 5/9，官网 480P 请求未完成。应用在 45 秒后显示可重试说明并保留当前画质与播放，记录在 `new-live-attempt2.json`。全屏画质面板的实际显示已另行通过；最终版本没有取得该 9 项专项一次完整通过的结果。

官网检查通过已有 USB localhost HTTPS 隧道联网，TLS 在官方服务器终止，不记录账号或请求正文；手机没有默认网络。检查使用安装后的生产 APK 和真实 ADB 输入，不注入替换版应用脚本。测试保存 / 恢复原偏好，主应用不卸载、不清除 Cookies。复现入口见 [DEVICE-TESTING.md](DEVICE-TESTING.md)。

最终测试 APK 已从手机拉取核对同一 SHA-256，保存在 `artifacts/tested-1.3.1`。27 个生产 / 构建文件在实站结束后核对未变。原偏好及系统设置已恢复（休眠 600000、自动旋转 1、user_rotation 0），检查会话、9222 转发、8877 reverse 和本任务 HTTPS 隧道均已结束。

发布完成：[v1.3.1](https://github.com/GodBook/bilispeed-android/releases/tag/v1.3.1)，源码与标签提交为 `1d54da92657af17e2cdf466da5ee13e21604ea99`。从该提交重建的正式包为 **1835256 字节，SHA-256 `4818A63A740DB085FAF30DE0006BDA317A010C66F2905DB21B0885AB0812392A`**。78 个 ZIP 条目逐项比较，只有 `META-INF/version-control-info.textproto` 与手机实测候选不同；比较记录为 `device-1.3.1-release-compare.json`。

GitHub 匿名 API 在检查时返回额度已耗尽，原 `verify-published-release.ps1` 未通过其元数据读取步骤。使用已登录 GitHub CLI 核对正式 / latest 标签、源码提交、三个资产摘要；三个 Release 附件及 `latest/download/update.json` 仍通过匿名公开 URL 下载，大小与 SHA-256 全部一致。公开更新 schema、minSdk、包名、版本、APK URL、校验文件内容和原签名均通过核对；记录为 `device-1.3.1-public-verification.json` 和 `device-1.3.1-github-release.json`。应用更新入口使用该直接公开 JSON URL。

公开下载的正式 APK 已覆盖安装到同一手机，再拉取 `installed.apk` 核对上述正式包哈希，记录为 `device-1.3.1-installed-public.json`，下载及安装副本保存在 `artifacts/published-1.3.1`。已移除一次性测试 runner，正式包无 DEBUGGABLE，运行中的正式应用调试 socket 和本任务转发均为 0。手机停留在正式版设置页，原倍速 1x 已选中；截图 / XML 为 `device-1.3.1-published-settings.png/xml`。主应用及 Cookies 保留。

## 1.3.0：导航保位、触屏画质与统一主题（已发布并回装公开正式包）

验收日期：2026-10-11（北京时间）。需求见[PLAN-1.3.0.md](PLAN-1.3.0.md)，恢复状态见[EXECUTION-1.3.0.md](EXECUTION-1.3.0.md)。实机为小米9 SE / Android15 API35 / WebView153 / 1080×2340 / density440；Android16 API36使用模拟器，沿用600×1400/density300覆盖值，不当作Android16实体机。

最终测试APK **1.3.0/code15，1834060字节，SHA256 C7C25A38614E7F8E2FC77B91E159DA15CCC9EDB4A19D40FEDE8E8B8A4181934C**，保存在artifacts/tested-1.3.0-final。27个生产/构建文件指纹见device-1.3.0-source-sha256.json。正式提交后重建的1834132字节APK已逐ZIP条目比较、公开发布、匿名下载并回装核验，详见下方发布记录。

| 检查 | 结果 | 主要证据 |
| --- | --- | --- |
| 小米9 SE完整核心集合 | **120/120一次全部通过，466.438s** | device-1.3.0-release-final120.txt |
| Android16完整核心集合 | **120/120一次全部通过，351.779s** | emulator-1.3.0-release-final120-ready.txt |
| 连续浏览/切集/全屏/主题 | **连续20/20通过，20冷/20热完成** | device-1.3.0-release-final-* |
| 官网交互 | **40个状态通过** | home10、course11、single2、boundaries3、collection5、new-live9 |
| 深色主要页面 | **6个页面通过** | dark-home/search/popular/dynamic/account-history/watchlater |
| 构建与静态检查 | 通过，lint 0 errors/3既有warnings | release构建、JS/PowerShell语法、git diff --check |
| 原签名覆盖升级 | 原1.2.10/code14→1.3.0/code15通过 | upgrade-prepare/verify；Cookies、倍速、字幕和更新偏好保留 |

核心集合包括0.25–5x实际媒体计时、字幕/手势、全屏切集、账号/评论结构、更新校验、安全边界、生命周期与真实渲染进程恢复；本版新增导航/锚点、320/360/392/430px、刷新保护、画质确认/拒绝、统一主题和打开中弹窗。保留5x实际计时标准，没有放宽断言。

官网40状态按各阶段独立记录：实际下拉刷新、短拉/普通滚动不重载、顶部搜索和五项导航；73P课程、合集边界、全屏双向切集/两种退出、单视频。新功能9状态包含18px首页行距、重复入口保留文档、等待懒加载后的返回保位、原生/网页同一主题，以及实际480P/360P/自动/全屏480P；进度45s、2.5x与全屏保留均断言。真实账号的提交、删除及购买未执行，相关写路径用夹具验证。

截图复核确认浅/深色首页、设置、动态及横屏画质可用；主要深色页面同时检查body背景、几何和动态独立装饰层。曾发现只检查body会漏掉官网浅色.bg/.bgc，现仅在深色动态页隐藏这两层，专项1/1通过2.382s，现有回归同时验证切回浅色恢复，媒体图片颜色正常。早期home-dark捕获过加载中画面，已加强就绪等待；完成加载后的证据使用dark-home-native.png。

夹具与失败记录保留：

- 模拟器首组119/120（479.64s），合集返回后点按显示控件超时。补充原生onPageFinished委托后的就绪信号，保留原触摸/显示/退出断言；专项1/1通过7.544s，随后完整120一次通过。手机同步分P/合集两项通过13.838s（ready-regression.txt），生产APK未改变。
- 官网合集首轮返回超时，保留player-collection-fullscreen-attempt1.txt/json/截图。点按准备补充可见性/实际命中校验，未发送按钮动作时才重新定位；失败现场增加DOM/触摸记录。完整合集5状态随后通过，其余已通过阶段未混入失败状态，新功能9和深色6继续完成。
- 中间候选的118/119/120成绩与EFC官网40均保留，但未替代上述最终C7记录。较早5x实测失败保留bootstrap-final119.txt，最终两端完整集合仍使用原标准。

旧E1223B候选两次在13/12轮、拆分后的1FB392候选在12轮退出，均为WebView153的32位ARM渲染器SIGTRAP/code5、ThreadPoolForeg，缺少符号化根因。手机当时在线、亮屏且解锁；没有归因为锁屏/ADB，也未改变ABI、WebView提供程序或SELinux。pagehide取消保位重试后，前一9CC及最终C7均取得各自完整20轮，不将短诊断清缓存/内存通知或失败轮次计为正式组。

最终20轮使用同一渲染器PID2681，JSON complete/webPreferenceRestored均true。第5/10/15/20轮RSS为1210624/1547396/978300/1576136KiB；第15轮自然回落，第20轮再次上升。结束VSZ3334200KiB、RSS1575840KiB、网页堆382817652B。没有人工GC、内存通知、清缓存或中途重启。20轮完成不代表持续低内存占用，32位WebView长时资源仍是后续优化项。

同机配对各20次，数值为中位数/P95，单位ms：

| 操作 | 1.2.10基线 | 1.3.0最终组 |
| --- | --- | --- |
| 原生冷启动 | 596.5 / 625 | 607 / 630 |
| 原生热启动（复用Activity） | 0 / 0 | 0 / 0 |
| 官网首页首次可用 | 1660.5 / 1886 | 1790 / 2232 |
| 返回列表首次可用 | 2234.5 / 2716 | 2420 / 2745 |
| 全屏切到下一P | 8093 / 8731 | 8923.5 / 10275 |
| 主题切换 | 312 / 425（仅My） | 308.5 / 390（全应用） |

原始数据为device-1.2.10-baseline-*和device-1.3.0-release-final-*。同一手机/WebView、HTTP缓存禁用，壁钟包含ADB/CDP开销；官网推荐、网络、供电/温度会变化，候选检查期间AC供电且曾温度46°C。主题作用范围不同，不能据此宣传统一性能提升、帧率或续航。首次可用返回不是最终锚点恢复时间，保位另等官网懒加载后检查。

字体1.0/1.3/1.5各2项、实际系统night切换原生弹窗、TalkBack原生设置/倍速/主题/My路径在本版较早候选验证；相关功能随后有最终核心回归覆盖。网页触摸探索未取得可靠证据，不声称全网页TalkBack验收。原签名升级保留数据，合成Cookie已移除，主应用未卸载/清数据。

正式发布：[v1.3.0](https://github.com/GodBook/bilispeed-android/releases/tag/v1.3.0)，源码/标签指向 **0d1b4f79521aaac5c29c3ac9c8592530a37c1a09**。原签名重建的正式APK为 **1834132字节 / SHA256 B8BAE355F995AF81CAC2C3ECA66B228B281DC6AD0ABDA7F862D4D077E45DA8EA**；与测试包78个ZIP条目相比，仅META-INF/version-control-info.textproto不同（device-1.3.0-release-zip-diff.txt）。证书SHA256沿用E2D8ED51E71288C8F2A0EA81E3F7AC23ABA499D59ED6D7EACE205E78960F94B3。

三个Release附件的GitHub摘要、匿名下载、latest/download/update.json、大小和原签名均核对通过，记录public-verification.json/txt，公开文件保存在artifacts/published-1.3.0。公开APK已覆盖安装小米9 SE，重新拉取installed.apk与上述B8BAE…哈希完全一致（public-installation.json）。未卸载或清除主应用数据。

最终正式首页与设置已目视核对（published-home/settings.png），原1x选中态在原生XML再次确认。手机曾熄屏但未锁定，唤醒后完成界面核对。原偏好、timeout600000、rotation1/0、font_scale1.0和My原夜间false已恢复；正式应用无DEBUGGABLE，调试socket=0、9222转发=0，手机/模拟器测试runner均卸载，可由构建脚本重新安装。final-cleanup.json记录最终状态。此处验证公开更新渠道和安装包，没有称为手机内完整在线下载安装流程。最终验收文档另提交main，不移动发布标签。

电脑Temp目录bilispeed-published-1.3.0-2092e894f4cb4871a2ded0a936956d7b内的4个APK/截图副本保留；自动审批审核拒绝删除，仅返回“blocked by policy”，没有提供更具体原因。经核验的正式文件和截图也已保存artifacts，未将临时目录清理写为完成。

## 1.2.10：首页下拉刷新、五项导航与轮播精简

验收日期：2026-10-11（北京时间）。使用电脑连接的小米 9 SE / 10.93.192.96:5555 / Android 15 API 35 / 1080×2340 / density 440，正常竖屏约 392 CSS px。320px 检查限制刷新容器宽度，没有修改系统分辨率或密度。保留原签名和 Cookies，使用 DevicePreferencesProbe 保存、恢复 playback / updates 原偏好。

首页在顶部下拉并松手后刷新，使用原生加载指示，页面加载期间阻止重复触发；加载完成、失败、进入设置/全屏、离开首页或切到后台时清理刷新状态。底部变为「首页 / 热门 / 动态 / 我的 / 设置」，同步调整图标和选中态；顶部官方搜索仍可使用。触屏首页隐藏 recommended-swipe 并清除显式网格行，首行视频从 y≈64px 开始；电脑原版布局保留官网轮播。

回归同时修复两条恢复路径：快速 HTTP 错误可能早于 onPageStarted，到达的错误保留到开始回调，避免失败状态被清除；SwipeRefreshLayout 缓存目标子视图，网页渲染进程退出后随新 WebView 一起重建刷新容器，避免引用旧网页。

已完成验证：

- 最终生产代码完成 101 项回归的逐项通过结果：整组 101 项中 100 项通过（392.474 秒，artifacts/device-1.2.10-all-regression.txt），倍速持久化的重启夹具因导航期间 evaluateJavascript 回调丢失而失败；补上具体文档加载信号后，原断言单独通过（4.386 秒，restart-verified.txt）。没有将整组称为单次 101 项全通过。首页六项、全屏切集、原生设置/键盘及三项真实渲染进程恢复均在整组通过。
- 实际 320px 的 14 项通过（35.986 秒，device-1.2.10-320.txt）：视频详情 11 项、单行栏/更多面板、倍速自定义/持久化和音量恢复；重启后的倍速夹具也确认实际 innerWidth=320±1。
- B 站真实首页 10 个状态通过（device-1.2.10-home-live-complete.txt）。通过实际 ADB 下拉确认文档重新加载，普通滚动与短下拉保持文档；顶部输入搜索并实际点击按钮得到对应结果；依次点击首页、热门、动态、我的、设置，核对 URL 和原生选中态。几何数据见 device-1.2.10-live-home-live.json，手机截图为同前缀 home-*-native.png。已目视核对首页，顶部大轮播消失，搜索栏下为双列视频，底部五项显示正确。
- 真实 73 P 课程的 11 个播放状态通过（device-1.2.10-live-course.txt），覆盖展开/收起、竖屏上一集/下一集、倍速保持、全屏 P2→P3→P4→P3、切集后实际播放与系统返回。已目视核对 live-course-fullscreen-next-p3-native.png：横屏全屏、单行控件、1.5x 倍速，底部导航未遮挡视频。实站总计 21 状态，未重复计算。
- 构建、lintRelease（0 errors / 4 项既有 warnings）、JavaScript 语法、补丁格式及原签名校验通过。最终候选 1827387 字节 / SHA-256 11F661253F768A3D6AE75CF3AB26E1BC2DC9C5088CBA943A564D86F314869D3B，拉取手机安装包核对相同，保存在 artifacts/tested-1.2.10；24 个生产文件的指纹见 device-1.2.10-source-sha256.json。

此前密码锁屏导致 Activity 挂起的检查不计为通过；用户解锁后重新保存原偏好并验证。原 95 项首轮在检查到刷新容器缓存旧 WebView 后主动中止，最终生产代码随后重新运行上述 101 项。首次实站脚本遇到空文档读取、截图缓冲区上限及短暂无线 ADB 断线，修正检查脚本并恢复连接后，首页 10 状态完整通过；没有向网页注入替换版生产脚本。实体设备为 Android 15，未声称在实体 Android 16 上测试。

实站检查后原偏好恢复成功（device-1.2.10-restored-final.txt），休眠超时恢复 600000，旋转设置仍为 1 / 0，Wi-Fi 未修改。已结束临时检查并移除 tcp9222；24 个生产文件的指纹在实站结束后再次核对一致。

正式发布：[v1.2.10](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.10)，源码/标签为 cc35dff1c4d63dc4e42844086b76990368cedc43。提交后原签名重建，正式 APK 为 1827387 字节 / SHA-256 1B3DD752244EF3C3FEA080E5585801551C742A3AB92994C29C41C106F9C39D20；与候选的 77 个 ZIP 条目比较，只有 META-INF/version-control-info.textproto 不同，记录指向发布提交（device-1.2.10-release-zip-diff.txt）。签名证书 SHA-256 沿用 E2D8ED51E71288C8F2A0EA81E3F7AC23ABA499D59ED6D7EACE205E78960F94B3。

GitHub latest 已指向 v1.2.10。三个附件的 GitHub 摘要、匿名公开下载和 latest/download/update.json 均核对一致（device-1.2.10-public-verification.txt、github-release.json）；公开文件保存在 artifacts/published-1.2.10。匿名下载的正式 APK 已覆盖安装手机，重新拉取 installed.apk 与上述哈希相同。

正式应用无 DEBUGGABLE，测试 runner 已卸载，当前应用调试 socket=0、本任务 tcp9222=0；原生设置确认恢复的 1x 仍选中。最终手机停留在新版首页，已目视核对 published-home.png，截图和 XML 均带 device-1.2.10- 前缀；cleanup-verified.txt 和 final-cleanup.json 记录收尾结果。此处校验公开更新渠道和正式安装包，没有称为手机内完整在线下载安装流程。最终验收记录另行提交 main，不移动发布标签。

## 1.2.9：单行控制栏与全屏切集

验收日期：2026-10-10（北京时间）。使用用户连接的小米 9 SE / 10.93.192.96:5555 / Android 15 API 35 / WebView 153 / 1080×2340 / density 440。正常竖屏约 392px，全屏约 821×392 CSS px；320px 检查仅限制同一手机 WebView 宽度，没有修改系统分辨率或密度。保留原签名和 Cookies，旧夹具与实站检查前后分别保存、恢复 playback / updates 偏好。

播放下栏按钮统一为一行。竖屏切集使用图标，音量与字幕从更多面板打开，按钮保留 44px 点击区域；320px 下显示当前时间，完整时长仍保留在进度说明与无障碍标签。全屏切集通过受限主框架消息在 Android 保留横屏、沉浸显示和导航隐藏，新文档中的官方播放器填满 WebView；退出按钮、系统返回、非目标视频导航与加载错误清理状态。

B站换文档会结束 HTML Fullscreen API。因此切集后 document.fullscreenElement 可以为空，Android 仍处于全屏；验收同时检查原生方向、导航占位为 0、播放器填满实际横屏视口、实际触摸与手机截图，不能只凭 JavaScript 标记认定全屏。

已完成验证：

- 播放器 22 项通过（119.327 秒，artifacts/device-1.2.9-player-regression.txt）。新检查覆盖单行控制栏、更多面板的真实点击、分 P 连续下一集/上一集、切集后播放与倍速、合集跨视频、系统返回与退出按钮。保留原有手势、字幕、音量、自动隐藏、前后台、替换视频与旋转回归。
- 其余 73 项通过（253.409 秒，device-1.2.9-browser-regression.txt），包括播放核心、桌面/触屏布局、视频详情、我的、更新、渲染恢复、手机布局、账号页和原生设置。95 项按 22 + 73 分组运行，没有声称单次整组 95 项。
- 同机实际 320px 的 14 项通过（37.542 秒，device-1.2.9-320.txt）：视频详情 11 项、单行栏与更多面板、倍速自定义/持久化、音量恢复；每个夹具确认实际 innerWidth 为 320±1。
- 实际 B 站网页 21 个状态通过。device-1.2.9-live-course.txt（11）包括 73 P 课程的分集展开、竖屏切集、全屏调速、P2→P3→P4→P3、切集后实际播放与系统返回；live-collection.txt（5）包括 35 P 课程末尾下一集进入另一视频、返回合集前一视频、退出按钮和离开目标视频清理全屏；live-boundaries.txt（3）检查首尾集；live-single.txt（2）检查单视频与倍速。JSON 为 device-1.2.9-player-*.json，原生截图为同前缀 *-native.png。
- 已目视核对 course-controls-native.png 的竖屏单行栏，以及 course-fullscreen-next-p3-native.png 的实际横屏画面。播放器区域全屏为 x/y≈0、宽/高≈821/392，切集后没有退出为竖屏。
- 构建、lintRelease、JavaScript 语法、原签名与安装包校验通过。实测包 1814646 字节，SHA-256 7E4B700EFFCA0795AE77F610AAFEA23DEB9F1F59CEB3A916BBDED7924AA028E5，手机拉取包一致，保存在 artifacts/tested-1.2.9；生产文件指纹 device-1.2.9-source-sha256.json 在最终实站验证后核对未变。
- 原设置恢复成功（device-1.2.9-restored-final.txt），快照自动删除；检查会话结束后关闭调试并移除 tcp9222。休眠超时恢复 600000，自动旋转恢复 1，user_rotation 恢复 0；Wi-Fi 沿用原配置。

过程记录：首次构建遇到 Windows 闲置 Gradle daemon 占用 classes.dex，停止 daemon 后重建成功。初次三个专项中两个退出断言失败：当时夹具未锁定起始方向，自动旋转手机在退出后按物理方向保持横屏，但全屏标记与系统栏已正确恢复。将播放器夹具明确从竖屏开始后，22 项全部通过，退出断言保持不变。失败日志保留在 build-save.txt 与 targeted.txt，不计为通过。

本次重点为播放下栏和手动切集；实体设备为 Android 15，未声称在实体 Android 16 上测试。

正式发布：[v1.2.9](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.9)，源码与标签为 0c8361c10b79aec610edf02e919f3492522fdf7d。提交后由相同生产源码重建，正式 APK 为 1814646 字节 / SHA-256 9F3AF90F67483FB7DFFCFC6F7F2F5AC8387096D0D53F793F2149B0FEAF476FE7。76 个 ZIP 条目只有 META-INF/version-control-info.textproto 与实测候选不同，该记录指向上述发布提交；见 release-build.txt、release-zip-diff.txt、release-vcs.txt（均带 device-1.2.9- 前缀）。

GitHub latest 已指向正式 v1.2.9，三个附件的 GitHub SHA-256 摘要与本机一致；从公开地址匿名下载 APK、update.json、SHA256SUMS.txt 后逐字节哈希核对一致，匿名 latest/download/update.json 的版本、URL 和哈希一致。记录 device-1.2.9-public-verification.txt；公开下载保存在 artifacts/published-1.2.9。签名证书 SHA-256 沿用 E2D8ED51E71288C8F2A0EA81E3F7AC23ABA499D59ED6D7EACE205E78960F94B3。

公开下载的正式 APK 已覆盖安装到该手机，重新拉取 installed.apk 与 Release 哈希一致。测试 runner 已卸载，正式包无 DEBUGGABLE，当前应用 WebView 调试 socket=0、任务 tcp9222 转发=0；原休眠和旋转设置均恢复。正式版原生设置中 1x 仍选中，手机停留在设置页，证据 device-1.2.9-published-settings.xml/png、published-install.txt、final-cleanup.txt。在线更新渠道的匿名下载校验在电脑完成，没有将其称为手机内完整下载安装流程。

## 1.2.8：分集显示、上一集/下一集与播放栏倍速

验收日期：2026-10-10（北京时间）。使用用户连接的小米 9 SE：10.93.192.96:5555 / Android 15 API 35 / WebView 153 / 1080×2340 / density 440。保留原签名、Cookies 与原偏好快照，未清理主应用数据。全屏约 821×392 CSS px，正常竖屏约 392px 宽；320px 验证直接限制同一实体手机 WebView 宽度，不修改手机分辨率或密度。

复现与修复：截图中的 BV1T6VFzAE1c（Kira 概率论）含 73 个分 P 和 2 个合集视频。旧版分集固定 328px 高且没有展开提示，长标题单行省略，合集时长显示 1558:00；切到 P2 后合集当前项高亮与计数丢失。新版本分开显示分集与合集，标题两行、带序号，时长显示 25:58:00；长列表提供展开/收起和当前集定位，多 P 下合集仍正确显示 2 / 2。

播放栏从同一官方元数据计算上一集/下一集，优先当前分 P，再衔接合集及其章节；不存在对应集时置灰，无相邻集的单个视频隐藏切集按钮，换路由立即拒绝旧地址。新增 0.25–5x 预设与自定义速度，通过仅接受 HTTPS B 站主框架消息的原生入口同步设置和保存；原生仍校验数值范围，直播保持 1x。竖屏两行、宽屏一行，按钮至少 44×44 CSS px。全屏调速保留全屏；全屏切集先退出全屏并打开对应官方页面。

实站追加发现并修复全屏状态残留：全屏选速更新了 document-start 启动脚本，退出时原先只通知当前页面；新文档因此仍取到 __BILI_TOUCH_FULLSCREEN__=true，查看末集时官方 mini 播放器变成左边 -11px 的固定框。现于每次原生全屏变化时更新缓存启动脚本，并在新页面加载完成后同步实际全屏状态。倍速 config 不携带全屏字段，避免迟到的旧消息覆盖退出状态。新增回归执行全屏选速、真实下一集导航到新文档，再模拟官方 mini 状态，并检查旧配置不能恢复全屏标记。

最终候选验收证据：

- 完整 93 项整组通过（355.339 秒）：`artifacts/device-1.2.8-release-93-regression.txt`。覆盖播放核心 16、布局 7、分集 11、我的 5、播放器 20、更新 12、渲染恢复 3、手机布局 5、账号页 8、设置 6。包含系统返回退出全屏、全屏选速后下一集新文档、mini 槽位及旧配置不得恢复全屏状态；未替换安装包中的业务脚本。播放器 20 项专项另通过（104.2 秒，`device-1.2.8-controls-release.txt`）。
- 同一手机实际 320px 的 14 项通过（37.36 秒）：`artifacts/device-1.2.8-release-320.txt`。额外断言 actual innerWidth 为 320±1；包含全部 11 个分集专项、最大 150% 字幕按钮、倍速的实际 Android 点击/验证/保存/重启、旋转与全屏选速后换页。
- 实站 12 状态全部通过：`device-1.2.8-verified-course.txt`（7）、`verified-boundaries.txt`（3）、`verified-single.txt`（2）及 `device-1.2.8-final-player-course/boundaries/single.json` 与对应 `final-*-native.png`。课程展开、真实列表滑动、定位、P1/P2 上下集、2x 跨 P 保持、全屏 1.5x 与面板保持、全屏下一集到 P3、P73 当前项和下一集置灰、返回 P72、首 P 切到合集另一视频（35 P 的 BV1Q4iJBAEsr）、无分集视频隐藏按钮及选择 1.25x 均通过。每个状态额外断言全屏启动标记、布局标记与 DOM 一致，全部可见按钮至少 44px 且不偏屏。截图使用原生 screencap，包含实际视频层。
- 候选 1813362 字节，SHA-256 `7CBA4F46011ED08E9F1418E8C21A09F9FB051DDDDA1CAAC9DB672965EDBF8031`；已从手机拉取 `device-1.2.8-final-installed.apk`，与电脑候选完全一致，候选和元数据归档于 `artifacts/tested-1.2.8/`。
- 真实页面检查前后恢复原偏好成功，最终记录 `device-1.2.8-restored-final.txt`，快照已自动删除。临时调试会话结束、tcp9222 已移除，休眠超时恢复原值 600000；保留原 Wi-Fi 与无线 ADB。

正式发布：[v1.2.8](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.8)。源码/标签为 `9b6f8f5fdc862f9759db53c7f540fefcd9ff7734`，正式 APK 1813362 字节，SHA-256 `C1E8C196C6E4CAED697C7F12FD88630BB73DB9E0E0D7CB45978CF1400A1F00D0`。从提交重构并通过 lint/签名检查；与候选的 76 个 ZIP 条目逐项比对，仅 `META-INF/version-control-info.textproto` 不同，包内提交号与标签一致，代码和资源相同。

三个正式附件齐全，GitHub APK 摘要、匿名 `releases/latest/download/update.json`、公开 APK 下载和 SHA256SUMS 全部核对一致；`published-1.2.8-*` 为电脑匿名下载证据。正式 APK 使用原签名覆盖安装手机后，拉取 `device-1.2.8-published-installed.apk` 再核对完全一致。测试 runner 已卸载，正式包无 DEBUGGABLE 标志，当前应用 WebView 调试 socket 为 0，ADB 转发为 0，休眠超时恢复 600000。原生设置确认恢复原倍速 1x、记住倍速和触屏布局开启，见 `device-1.2.8-published-settings.png`。保留已有 Cookies、原 Wi-Fi 与无线 ADB，手机停留在正式版设置页。

保留失败记录：首轮播放器/分集 30 项为 25 通过（`device-1.2.8-targeted-build.txt`），因新增隐藏项和合集标识需调整原断言；可见项几何断言提高到 44px。第一轮完整 92 项为 91 通过（`device-1.2.8-full-regression.txt`），连续 fixture 切文档时 evaluateJavascript 回调丢失，已加测试专用 load 提交通知。候选 0FBE84D9 的 93 项为 92 通过（`final-93-regression.txt`），系统返回退出全屏后控件未显示；已去除倍速 config 的全屏字段，使用原生通知与每页加载同步，最终 93 项整组通过。实站初次检查受到旋转夹具留下的电脑布局和 smooth scroll 影响，已先恢复原设置并瞬时定位；另有一次 CDP 换页期间 execution context 消失，检查脚本仅对导航上下文错误做有界重试，最终 12 状态均通过，业务断言未降低。

## 1.2.7：历史、收藏、稍后再看、动态与全部主要入口

验收日期：2026-10-10（北京时间）。按用户要求使用已连接的实体小米 9 SE：无线ADB / Android15 API35 / WebView153.0.8010.36 / 1080×2340 / density440，正常WebView约392×717 CSS px。使用手机原有Wi-Fi和已登录账号；未清理应用数据或Cookies，测试前保存全部playback/updates偏好。

修复范围：历史的新旧地址、日期分组、网格/列表和筛选搜索；收藏夹选择、收藏视频与搜索；稍后再看的两种列表、工具栏和空状态；登录态动态、图片、视频、转发、详情评论；空间首页、投稿、关注/粉丝、追番、分页与空间设置；「我的」弹窗返回。账号弹窗保留完整遮罩，内容与确认/取消按钮适配手机。

额外发现并修复视频页初始化竞态。旧版触屏模式下，官方脚本抛`HierarchyRequestError`，`#app.__vue__`不存在，`#commentapp`为空；实际在原生设置关闭触屏布局后，同一视频正常mounted且创建评论。原因是Vue在开始hydration时移除SSR标记，原判断过早认为页面就绪，播放器控制栏同样会提前追加子节点。现以官方根组件mounted为依据，播放器、内容入口与选集共用此门槛；脚本明确失败时保留延迟兜底，暂停后台等待。

已完成的验证与证据：

- 视频/账号16项通过，含初始化延迟超过旧3.5秒兜底、SSR标记已移除但尚未mounted、脚本失败后兜底、播放器控件不得提前插入，以及账号布局与真实Android触摸。记录：`artifacts/device-1.2.7-hydration-regression.txt`。
- 同一官方竖屏视频实装复测mounted=true、comments=1、readyState=4，播放中查看评论不被mini播放器遮挡。记录：`device-1.2.7-mounted-portrait-result.json`、`device-1.2.7-mounted-comments-result.json`与对应native.png。
- 实际原生设置选择3x后，官方视频rate=3；真实触摸暂停、继续、横屏全屏（821×392 CSS px）和系统返回通过。记录：`device-1.2.7-live-pause.json`、`live-resume.json`、`live-fullscreen.json`、`live-fullscreen-back.json`及原生全屏截图。「我的」弹窗按系统返回后留在原页且dialogOpen=false，见`device-1.2.7-my-back-live.json`。
- 包含最终原生输入修复的完整87项整组通过（340.554秒），记录`artifacts/device-1.2.7-final-87-regression.txt`。覆盖：播放核心16、布局7、视频详情8、我的5、播放器17、更新12、渲染恢复3、手机布局5、账号页面8、设置6。没有用先前84/86项候选结果代替最终验证。
- 同一最终候选APK在同一手机执行layoutWidth=320，账号8项整组通过（15.227秒），每个夹具额外断言实际innerWidth为320±1。记录`device-1.2.7-final-320.txt`，没有修改手机系统分辨率或密度。
- 网页适配最终23状态全部通过，记录`device-1.2.7-release-live-pages.txt`及`device-1.2.7-release-*-result.json/native.png`。包括首页、搜索、4个热门分类页面、历史、稍后再看、收藏、空间首页/投稿/关注/粉丝/动态/设置/合集/追番、动态首页、竖屏视频、播放中评论、分P、合集和我的。公开UP有内容的投稿另通过`device-1.2.7-public-final-uploads-result.json`检查。
- 最终候选为1810586字节，SHA-256 `20122027DF7C2B535A5BAC11AA711D9CF4366EB6D6B0349F43C5AEE74D43FE90`，从手机拉取完全一致，保存在`artifacts/tested-1.2.7/`。对应生产源码指纹`device-1.2.7-final-source-sha256.json`，最终实站检查后再次核对未变。追加原生输入修复前的候选另存`tested-1.2.7-before-native-input/`，不作为最终交付依据。
- 实际原生搜索发现键盘遗留，后续WebView缩到400px；新增提交/取消时的输入清理与焦点恢复，底部导航切换清理键盘，原生搜索兼容硬件Enter。新增原生IME专项实际显示手机键盘，搜索按钮/硬件回车/取消三路径全部验证IME隐藏、视口恢复和URL保持。记录`device-1.2.7-native-keyboard-final.txt`（1项含3流程）、`native-keyboard-flows.txt`。首次键盘事件未提供真实uptime与SOURCE_KEYBOARD而被系统拒绝，修正事件构造后通过，断言未降低。
- 最终干净会话通过实际原生点击核对首页、热门、动态、我的、设置及设置返回；搜索输入后按当前弹窗按钮打开真实结果页，视口恢复392×717。记录`device-1.2.7-final-native-entries.json`与`final-native-search.json`。动态详情实站内容/视频卡片/评论可见，scrollWidth=392，无内容溢出，见`device-1.2.7-final-dynamic-detail.json/native.png`。

过程中的失败与定位：第一轮83项有3项失败，日志`device-1.2.7-full-regression.txt`保留。DesktopLayout与Settings夹具在文档替换期间过早evaluateJavascript而丢回调，现等待该夹具load提交；「我的」返回夹具的原生历史地址为about:blank，现使用真实MY_PAGE并断言原生URL。修正后19项布局/我的/账号及13项账号/设置专项通过。随后84项整组通过，但实站又发现上述评论初始化问题，已重新打开验证。第一轮实站18状态通过；粉丝选择器与评论失败截图仍保留，不计为通过。

验证边界：真实账号稍后再看为空，带内容的网格/列表用官方结构夹具检查；当前粉丝、投稿与个人动态等空状态实测，公开UP有内容的投稿另行检查。没有在真实账号发送评论/动态、删除记录/收藏或修改隐私；这些官方写操作没有被宣称已实测。会员专属字幕未验。离线缓存仍为明确的说明入口，没有宣称新增下载能力。

正式构建、GitHub发布、附件下载与最终手机清理（2026-10-10）：

- [v1.2.7 Release](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.7) 于北京时间16:16:50发布为最新正式版本，非草稿、非预发布。源码标签指向`8b792cc244baa6680fdaf6047a76733402b5fa38`；main与标签已推送。
- 从该提交重新构建assembleRelease与lintRelease通过，lint为0 errors / 4原有warnings。正式APK为1810586字节，SHA-256 `E8C11A20B8F8DA5FA6A5C7B0CC96C79786CCAEA6EF2442ABC2BF55631328DDED`，versionName1.2.7 / code11 / minSdk26 / targetSdk36，未启用DEBUGGABLE；v2签名通过，证书SHA-256仍为`e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3`。
- 正式包与最终已测候选逐项比较，76个ZIP条目仅`META-INF/version-control-info.textproto`变化，应用代码与资源完全相同，包内Git记录等于上述源码提交。记录`artifacts/release-1.2.7-build-verification.json`，没有将候选哈希冒充正式附件。
- 三个正式附件重新下载后，逐个与本地文件、GitHub digest及大小核对一致。APK为上述哈希；update.json为1799字节，SHA-256 `C2F05F4F4C55DAA85AC7C838B1F1F8096806D9267FB233081B497CF73A59C4DB`；SHA256SUMS.txt为97字节，SHA-256 `6FA0A3C9761310CE7CF40F6B1F9DF3890CF0BB76E06D8F07C348E409FAFD19F6`。版本、包名、大小、APK哈希、发布说明与校验文件匹配，见`release-1.2.7-download-verification.json`。
- 无GitHub登录凭据请求应用实际使用的`https://github.com/GodBook/bilispeed-android/releases/latest/download/update.json`，返回1.2.7 / code11，逐字节等于发布附件；继续从其中的公开APK地址匿名下载，哈希与大小一致。下载保存在`artifacts/release-verify-1.2.7/`。
- 手机额外运行PublishedUpdateSmokeTest，在读取GitHub更新信息时出现SocketTimeoutException，1项失败，记录`device-1.2.7-published-update.txt`。电脑匿名公开渠道校验通过，但不能代替手机当前Wi-Fi上的完整在线更新下载；保留原网络与HTTPS校验，没有把这项额外网络检查计入87项回归通过。
- 从公开地址下载的正式APK已原签名覆盖安装到同一手机；拉取已安装base.apk后哈希与正式附件完全一致。原始偏好已恢复并删除快照，实际设置1x仍选中，登录态历史有内容。一次性runner已卸载，WebView调试socket及tcp9222转发均为0，休眠超时恢复600000，Wi-Fi与无线ADB保留；手机停留在新版历史记录页。证据`release-1.2.7-device-install.json`、`release-1.2.7-final-device-state.json`及最终原生截图。

## 1.2.6：底部设置、倍速整合与重复工作削减

验收日期：2026-10-10（北京时间）。按用户要求使用已连接无线调试的实体小米 9 SE：10.93.192.96:5555 / Android 15（API 35）/ WebView 153.0.8010.36 / 1080×2340 / density440。实站使用手机现有 Wi-Fi，未更改其联网方式。另用 Android 16 / WebView 133 模拟器验证 320px 窄屏。

- 三点与倍速浮钮在普通浏览、设置、输入和全屏中始终隐藏；底部设置位于「我的」右侧，并在电脑原版布局中保留。设置按播放、浏览与外观、常用工具、关于与更新分组，包含九档倍速、滑杆、自定义校验、记忆、布局、字幕外观、链接工具及更新开关。真机截图为 `artifacts/device-1.2.6-settings.png`、`device-1.2.6-settings-live.png`、`device-1.2.6-settings-video-native.png`。
- 新设置专项 5 项在真机组合运行中全部通过：真实原生触摸切换九档与输入、无效数值、持久化及忘记倍速、布局和更新开关、返回时网页身份 / 滚动位置 / 播放保留、动画变化过滤及视频替换。九档实际播放计时按 3 次采样取中位数，1 / 1.25 / 1.5 / 2 / 2.5 / 3 / 3.5 / 4 / 5x 分别约为 1.000 / 1.250 / 1.501 / 2.001 / 2.500 / 2.998 / 3.499 / 3.996 / 4.998x。原始日志为 `artifacts/device-1.2.6-real-rate-timing.txt`；这是本地测试媒体，不代替所有网络视频的解码表现。
- 对照手机原装 1.2.5 与候选 1.2.6，在相同 12 批 / 648 个弹幕元素的受控变化中，播放器发现的子树扫描从 216 次降到 0 次，两个版本都能发现随后加入的视频。探针为 `scripts/device-performance-probe.mjs`，结果为 `artifacts/device-performance-1.2.5-baseline.json` 和 `device-performance-1.2.6.json`。不把计数变化换算成整机帧率或续航提升。
- 真实首页、搜索、热门、我的、竖屏视频和播放中评论通过尺寸与截图检查，证据为 `artifacts/device-1.2.6-*-result.json` 和 `device-1.2.6-*-native.png`。在 `BV1qMp46wE1n` 的官方播放器中通过原生设置选择 3x，实际 rate=3；评论滚动时视频不遮挡内容。横屏全屏保持比例，无悬浮按钮；真实 ADB 双击可暂停 / 继续。3x 播放时全屏控件隐藏的 1.6 秒内观察到内部 0 次 DOM 变化，显示后同步控件。
- 真机完整组合运行 75 项，67 项通过、8 项失败，记录保留在 `artifacts/device-1.2.6-full-regression.txt`。针对失败项及个人页追加 10 项，9 项通过，见 `device-1.2.6-targeted-retest.txt`；最后的放大字幕全屏操作专项通过，见 `device-1.2.6-fullscreen-final.txt`。按方法去重，75 项全部取得通过结果；不声称同一次 75 项整组全通过。测试覆盖倍速、iframe / Shadow DOM、后台暂停、全屏 / 手势 / 音量 / 字幕、布局 / 评论 / 选集、个人页、更新校验和真实渲染进程恢复。
- 上述失败的修复限于测试夹具：新页面用唯一标识等待，页面提交丢弃旧 JavaScript 回调时在原超时内重新查询；个人页夜间开关按实际初值检查并恢复；全屏触摸等待横屏和系统栏 resize 完成。新 Chromium 会先压缩 data URL 历史，历史回归按真实序列化体积判断保留或降级，仍检查保存状态上限、HTTPS 链接和倍速。生产代码及断言目标未为复测改变。
- Android 16 窄屏组合 34 项中 33 项通过，新增的实际播放计时在模拟器 1x 测得 0.644x、未通过；对应真机九档计时全部通过。保留 `artifacts/emulator-1.2.6-narrow-final.txt`，不以模拟器计时失败代替实体手机结果。早期启动的模拟器出现 `System UI isn't responding` 拦截输入，记录为 `emulator-1.2.6-full-regression.txt` 和 `emulator-1.2.6-progress.png`；冷启动后完成上述窄屏检查。
- 发布构建、Android lint（0 errors / 4 个原有 warnings）、JavaScript 语法和原签名 v2 校验通过。真机安装包拉取后的 SHA-256 与候选包一致：`2D95BF94CBD3B1534B969417ABFCCBFAA2CE9A029866AA08E4915353585DE6B1`，大小 1804962 字节，versionName 1.2.6 / code 10，minSdk26 / targetSdk36。归档为 `artifacts/tested-1.2.6/`。正式包已从提交后的同一源码构建，提交记录和公开附件的独立校验见下。

每轮旧夹具运行前通过 DevicePreferencesProbe 在应用私有目录保存 playback / updates 全部偏好，结束后恢复并删除快照；保存与恢复专项均通过。未清理 Cookies，未卸载主应用。SettingsInstrumentationTest 自身也保存和恢复偏好。账号登录后的内容及会员字幕仍未使用真实账号验收。

正式发布与附件验证（2026-10-10）：

- [v1.2.6 Release](https://github.com/GodBook/bilispeed-android/releases/tag/v1.2.6) 于北京时间 13:37:51 发布为最新正式版本，非草稿、非预发布。已推送的源码标签与 APK 内提交记录均为 `fcaf0f7d70ec48780536b46aac237cb1dbe25457`。
- 提交后重新构建及 lint 通过。正式 APK 为 1804962 字节，SHA-256：`E0C8FCECB63491280D5B28DEA587A580735F135F472A4AAB5E14B6756D5001D3`，版本 1.2.6 / code10，minSdk26 / targetSdk36，关闭调试。v2 签名验证通过，原证书 SHA-256：`e2d8ed51e71288c8f2a0ea81e3f7ac23aba499d59ed6d7eace205e78960f94b3`。
- 对比候选包的 76 个解包条目，仅 `META-INF/version-control-info.textproto` 的 Git 记录变化；代码、资源和脚本完全一致。对照记录为 `artifacts/release-1.2.6-content-comparison.json`，未把重新构建当作又一轮界面测试。
- 三个附件全部重新下载，SHA-256、大小、GitHub 附件摘要与本机文件一致；发布说明与 release-notes.md 完全一致。update.json 为1561字节、SHA-256：`2C7BD71A13375C9986D8F981BF8F5C8DDDBCD1AAFA3D6F25BA91BB104F4611D0`；SHA256SUMS.txt 为97字节、SHA-256：`0FEC16A164D83AE6ED1D10855DD30BF195C638C512814DD911710FC28BC58600`。下载和元数据保存在 `artifacts/release-verify-1.2.6/` 与 `release-1.2.6-github-metadata.json`。
- 电脑不携带 GitHub 登录凭据访问 `https://github.com/GodBook/bilispeed-android/releases/latest/download/update.json`，返回1.2.6 / code10 / 正式包相同哈希。手机现有 Wi-Fi 直连 GitHub 的额外 PublishedUpdateSmokeTest 在读取响应时超时，保留 `artifacts/device-1.2.6-published-update-check.txt`；没有声称该网络下手机在线下载通过。应用在线更新仍要求手机能够访问 GitHub。
- 手机已原签名覆盖安装正式包，重新拉取的已安装 APK 与正式附件哈希一致；实际设置页高亮1x和底部设置入口，最终截图为 `artifacts/device-1.2.6-final-settings-native.png`。测试 runner 已卸载，无 WebView 调试 socket 或本轮 ADB 转发；休眠超时恢复原值600000，保留原无线调试与 Wi-Fi。模拟器尺寸恢复720×1600后关闭。

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
