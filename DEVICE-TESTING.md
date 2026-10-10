# 2026-10-10 1.2.6 设置与流畅度检查点

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
