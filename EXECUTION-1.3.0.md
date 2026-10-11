# 1.3.0 执行检查点

更新于2026-10-11 22:30（Asia/Shanghai）。用户授权的PLAN-1.3.0.md P0/P1、实机验收、GitHub更新、正式Release与公开APK回装均已完成。P2继续保留为后续候选，不自动扩展本次任务。

## 发布与安装

正式Release：[v1.3.0](https://github.com/GodBook/bilispeed-android/releases/tag/v1.3.0)，已成为latest。源码/标签为0d1b4f79521aaac5c29c3ac9c8592530a37c1a09；最终验收文档另外提交main，不移动标签。

正式APK：1.3.0/code15，1834132字节，SHA256 **B8BAE355F995AF81CAC2C3ECA66B228B281DC6AD0ABDA7F862D4D077E45DA8EA**。匿名下载的三个附件、latest更新清单、大小、摘要和原签名均验证通过。公开APK已覆盖安装小米9 SE，实际installed.apk拉回后哈希完全一致；未卸载或清除主应用数据。

测试APK为C7C25A38614E7F8E2FC77B91E159DA15CCC9EDB4A19D40FEDE8E8B8A4181934C，1834060字节，保存在artifacts/tested-1.3.0-final。正式包78个ZIP条目只有META-INF/version-control-info.textproto不同。27项生产指纹及两包关联见device-1.3.0-source-sha256.json / release-zip-diff.txt。

## 验收结果

- 小米9 SE / Android15 / WebView153：完整120/120一次通过466.438s。
- Android16/API36模拟器：完整120/120一次通过351.779s；合集夹具原生就绪等待修正后专项及整组通过，原失败119/120日志保留。手机同步两项通过13.838s。
- 最终连续20/20、20冷/20热完成，同一渲染器PID2681，没有人为GC/内存通知/清缓存/中途重启。资源有自然回落，但结束RSS仍约1.50GiB，32位WebView长时资源是后续优化项；具体数据和测量边界见VALIDATION.md。
- 官网40个状态及6个主要深色页面全部通过。合集首轮超时保留attempt1日志/JSON/截图；点按准备补充实际可见性/命中校验后完整合集5状态通过，再完成新功能9及深色6。没有将失败状态计作通过。
- 构建/lint 0 errors/3既有warnings、JS/PowerShell语法、补丁格式和原签名升级通过。字体/TalkBack原生主要路径的版本范围见VALIDATION.md，未声称全网页TalkBack探索。

功能已交付：18px列表间距、导航/锚点保位与重复入口回顶部、列表刷新保护、官方画质实际确认、统一三主题及旧夜间迁移、旧浮钮清理、原生弹窗同步、延迟评论Shadow DOM、pagehide取消保位任务。深色动态页独立装饰背景已补齐，浅色恢复及媒体颜色已验证。

## 最终设备与清理状态

公开包的首页和原生设置已目视核对，1x恢复选中态有XML证据。timeout600000、rotation1/0、font_scale1.0恢复，My原夜间false验证恢复；Cookies保留。正式包无DEBUGGABLE，调试socket=0、9222=0，手机/模拟器test runner均卸载，源代码可重建。无运行中的instrumentation或检查会话。

4个电脑Temp安装/截图副本仍在C:/Users/awxds/AppData/Local/Temp/bilispeed-published-1.3.0-2092e894f4cb4871a2ded0a936956d7b。自动审批审核拒绝删除，仅返回“blocked by policy”，未给更具体原因；已保留副本，未将此项写为清理完成。正式包与截图已另存artifacts。此限制不需要重新发布。

主要证据均在artifacts：device-1.3.0-release-final120.txt、emulator-1.3.0-release-final120-ready.txt、release-final-*、public-verification.json、public-installation.json、final-cleanup.json、published-home/settings.png。完整验收和失败范围以VALIDATION.md为准，旧详细检查点归档为EXECUTION-1.3.0-implementation-log.md。

工作区C:/Users/awxds/Documents/ChatGPT/b站手机端。Java D:/ssoftware/JAVA24，SDK D:/dev/android-sdk，GRADLE_USER_HOME=C:/Users/awxds/.gradle-desktile。手机10.93.192.96:5555；模拟器emulator-5580保留600×1400/density300覆盖值。原签名SHA256 E2D8ED51E71288C8F2A0EA81E3F7AC23ABA499D59ED6D7EACE205E78960F94B3；.signing私密。没有更改ABI、WebView提供程序或SELinux。
