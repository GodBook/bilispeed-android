# Android 16 验收记录

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
