# 1.3.0 执行检查点

更新于2026-10-11 22:08（Asia/Shanghai）。用户已授权执行PLAN-1.3.0.md、实机测试、更新GitHub、发布正式Release及覆盖安装公开包，无需重新询问许可。

## 当前状态

功能和本地/实机验收已完成，尚未提交、打标签或发布。main起点9076ccc；远程latest仍1.2.10/code14。当前1.3.0/code15测试APK为1834060字节，SHA256 **C7C25A38614E7F8E2FC77B91E159DA15CCC9EDB4A19D40FEDE8E8B8A4181934C**。生产内容已固定，27项指纹见artifacts/device-1.3.0-source-sha256.json，完整候选保存在artifacts/tested-1.3.0-final及candidate-1.3.0-dynamic-final。

已完成列表18px间距、导航/锚点保位、列表刷新保护、官方画质实际确认、统一三主题/旧夜间迁移、旧浮钮清理、原生弹窗同步、延迟评论Shadow DOM、取消导航后再次切换、大启动脚本固定注册、pagehide停止保位重试。最后一条产品修正仅在深色动态页隐藏官网直属.bg/.bgc装饰层；动态专项及浅色恢复已验收。

## 已验证结果

- 小米9 SE / Android15 / WebView153：完整120/120一次通过466.438s，device-1.3.0-release-final120.txt。
- Android16/API36模拟器：完整120/120一次通过351.779s，emulator-1.3.0-release-final120-ready.txt。
- 模拟器首组119/120的合集触摸超时保留在release-final120.txt；测试夹具补原生onPageFinished就绪等待，原断言全部保留。专项1/1通过7.544s；手机同步两项通过13.838s（device-1.3.0-ready-regression.txt）。这些修改没有改变生产APK。
- 最终1.3.0-release-final：20冷/20热及连续20轮全部完成，JSON complete/webPreferenceRestored均true，同一渲染器PID2681，无人工GC、内存通知、清缓存或中途重启。第15轮RSS自然回落，结束仍约1.50GiB，不能宣传持续低内存或无限时稳定；具体样本与性能表见VALIDATION.md。
- 官网40个状态：home10、course11、single2、boundaries3、collection5、新功能9。合集首轮超时日志/JSON/截图为player-collection-fullscreen-attempt1*，补齐点按前可见性/命中校验后完整5状态通过；没有重复实际按钮动作来掩盖失败。其后新功能9与深色6均通过，分别保留各阶段日志。
- 深色首页/搜索/热门/动态/历史/稍后再看6页通过；首页、设置、画质、动态等截图已目视核对。使用完成加载后的dark-home-native.png，避免早期home-dark的加载中画面。
- 构建/lint 0 errors/3既有warnings、JS/PowerShell语法及差异格式检查通过。字体缩放、TalkBack原生主要路径和原签名升级证据/范围见VALIDATION；没有声称全网页TalkBack探索。

所有instrumentation已结束；当前无9222转发，原偏好/系统设置已恢复。主应用未卸载/清数据，Cookies保留。原My夜间false已验证恢复，未验证备份已由成功旅程恢复后删除。

## 下一步（全部已授权）

1. 汇总最终README/DEVICE-TESTING/VALIDATION与源指纹，检查暂存差异；保留tested-1.3.0-final，提交推送main。
2. 从发布提交运行build-apk.ps1重建；compare-release-apks.ps1逐ZIP条目比较，仅允许META-INF/version-control-info.textproto不同。不得重置源代码或放宽比较规则。
3. 创建并推送v1.3.0标签，publish-release.ps1发布正式latest的APK/update.json/SHA256SUMS三个附件。标签目前不存在，若外部状态变化先查，不盲目覆盖。
4. verify-published-release.ps1匿名核对3附件/latest/原签名；公开APK经英文Temp路径覆盖安装手机，再拉取实际installed.apk核对SHA256。
5. 恢复原设置、确认无DEBUGGABLE/调试socket/9222，卸载临时test runner；核对正式首页。最终文档另提交main，不移动release标签。

## 环境与历史

Java D:/ssoftware/JAVA24；SDK D:/dev/android-sdk；GRADLE_USER_HOME=C:/Users/awxds/.gradle-desktile。手机10.93.192.96:5555已接AC、解锁；原timeout600000、rotation1/0、font_scale1.0。模拟器emulator-5580保留600×1400/density300覆盖值，不能用物理1080×2400坐标。

原签名SHA256 E2D8ED51E71288C8F2A0EA81E3F7AC23ABA499D59ED6D7EACE205E78960F94B3；.signing私密。基线code15包不发布、不留手机。早期长时原生SIGTRAP和诊断记录均保留，未改ABI/WebView提供程序/SELinux。详细旧检查点已保存artifacts/EXECUTION-1.3.0-implementation-log.md；当前事实以本文件为准。
