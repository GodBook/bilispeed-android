# 2026-10-10 真机适配修复检查点

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
