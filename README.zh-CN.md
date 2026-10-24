# TeslaPlay

[English](README.md) · **简体中文**

在 Android 手机上运行 CarPlay，通过 Tesla 浏览器查看和操控。TeslaPlay 基于 WheelPlay，复用现有 CarPlay、网页串流、配对和触控实现，补充 Tesla 热点连接、窗口配置、iPhone 蓝牙输出及 DuckDNS HTTPS。

项目仓库：[cany7/TeslaPlay](https://github.com/cany7/TeslaPlay)。当前版本为 **0.1.1**，正式应用 ID 为 `io.github.cany7.teslaplay`，调试应用 ID 为 `io.github.cany7.teslaplay.debug`。本仓库不提供未经发布验证的下载入口；本地构建及产物位置见下文。

## 使用流程

准备 Android 9 或更高版本的宿主手机、iPhone，以及可连接宿主热点的 Tesla。首次配置和验证在停车状态进行。

1. 安装包含认证资源的 TeslaPlay APK。认证资源由构建时预置并在首次运行自动加载，App 不再提供从 DiPlay 导入的设置或引导；资源缺失时需使用完整构建。
2. 打开 Android 手机热点，使电脑或 Tesla 接入。无线连接默认使用热点，按 App 要求配置热点名称和密码；需要热点联网时使用外部 Shizzi，TeslaPlay 本身不提供网络共享或热点 DNS 控制。
3. 在“Tesla 网络与 HTTPS”选择连接方式，默认免 Root VPN，并完成 Android 的 VPN 授权。兼容入口固定为 `100.96.0.1`；保留 Root 连接方式供已有环境使用。
4. 选择 **DuckDNS** 证书类型，填写自己的 DuckDNS 域名和 token，接受证书服务条款后保存并申请。App 自动更新该域名的 A 记录到当前入口，管理 DNS-01 证书申请和续期，无需在 DuckDNS 页面手填 IPv4。查看实际服务证书和续期状态，不能仅凭“上次任务成功”判断当前证书。
5. 在 Tesla 浏览器打开 App 显示的完整地址，例如 `https://你的域名.duckdns.org:8443/`。默认 HTTPS 端口为 `8443`，不能省略协议或端口。DuckDNS 解析不会把手机服务转发到公网，浏览器仍须能到达手机入口。
6. 扫描网页二维码或输入配对码完成配对。保持 App 前台，通过 USB 或无线方式连接 iPhone，并完成 Android 权限及 iPhone CarPlay 授权。

恢复配对需要保留浏览器站点数据及访问地址。HTTP 和 HTTPS 是不同来源；更换地址、清除浏览器数据或撤销设备后可能需要重新配对。

## 功能与设置

- **画面**：保留 WebRTC、HEVC 和 60 FPS 设置；HEVC 默认开启，图片兼容串流质量默认 100。Tesla 右侧／大窗口分别保存配置，输出尺寸统一由窗口配置管理；保存后明确应用才重连，浏览器尺寸变化不会自动中断运行中的会话。
- **画面页面**：“外观”管理日间／夜间／跟随系统；“图标与文字大小”管理 CarPlay 界面密度。图片格式和质量只作用于图片兼容串流及 WebRTC 回退；高级设置提供 WebRTC 卡顿容忍时间。安全区域和右舵布局保留。
- **音频**：统一选择 Android 设备、浏览器或 **iPhone 蓝牙输出**。蓝牙选项让 iPhone 自己管理蓝牙音频与麦克风；在 iPhone 中选择 Tesla 等目标设备，不由 Android 转发。改变选择后按提示应用重连，界面区分所选偏好与当前会话输出。
- **浏览器与触控**：二维码配对、多点触控、普通顶栏和可选虚拟按钮。虚拟按钮开关仅在手机 App“浏览器设置”中配置。浏览器麦克风和 GPS 需要可信 HTTPS 及浏览器授权。
- **定位**：默认使用 iPhone 自身定位，可按需选择 Android 或浏览器定位。“定位设置”不再显示 OEM 信息；车型内部固定 Tesla，App 和 CarPlay 返回入口使用指定 Tesla T 图标。
- **维护**：设置搜索、设置备份、已配对设备管理、指定蓝牙设备触发服务启动和本地诊断。普通设置备份不包含证书私钥、DuckDNS token、认证资源等凭据。

保留实验性 [Android Auto](docs/ANDROID_AUTO.md)、[VNC](docs/VNC.md) 与诊断输入源；同一时间仅一个投影输入源及一个浏览器控制会话。ICCOA 不再出现在源选择中。Android Auto 音频和麦克风使用 Android 宿主；VNC 的既有传输不支持 TLS/VeNCrypt。

电脑检查不能替代 Tesla 和 iPhone 实测。请在停车状态检查窗口尺寸、音乐／导航／通话／Siri、地图手势及手机后台运行。

## 构建

| 工具 | 版本 |
| --- | --- |
| JDK | 25 |
| Gradle／Android Gradle Plugin | 9.3.1／9.1.0 |
| Android SDK／Build Tools | API 36／36.0.0 |
| Android NDK／CMake | 28.2.13676358／3.22.1 |
| Node.js | 22 或更高版本 |

使用 `ANDROID_HOME` 或未提交的 `local.properties` 中的 `sdk.dir` 指定 SDK。仓库包含 Gradle wrapper；首次原生构建需要获取固定版本的 libdatachannel 与 Mbed TLS。

将匹配的认证资源作为本地独立输入放置：

```text
.local/auth-assets/offline-mfi/identity.pk8
.local/auth-assets/offline-mfi/certificate.p7b
```

也可将 `TESLAPLAY_AUTH_ASSETS_DIR` 指向包含 `offline-mfi/` 的目录。资源不提交 Git，不随源码归档；来源和摘要见[第三方声明](docs/TESLAPLAY_THIRD_PARTY_NOTICES.md)。生成可直接使用 CarPlay 的调试包：

```sh
./gradlew :mobile:assembleProvisionedDebug
```

任务检查认证资源是否完整且与 APK 内容一致。产物为 `mobile/build/outputs/apk/debug/mobile-debug.apk`，版本 `0.1.1`、应用 ID `io.github.cany7.teslaplay.debug`。`assembleDebug` 可用于普通编译检查，但没有上述资源时不能据此交付可用的 CarPlay 包。App 不再提供安装后导入入口。

新包名会被 Android 视为独立 App，不能覆盖旧的 `moe.feng.wheelplay.debug`。旧设置、有效证书和配对不会自动继承；普通设置备份不能替代完整迁移。保留旧数据直到新包验证完成，避免两个包同时占用 VPN 和服务端口。

按改动范围运行相关检查：

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest \
  :common:assembleDebugAndroidTest :mobile:lintDebug
npm ci
npm test
npm run check
```

`assembleDebugAndroidTest` 只编译仪器测试 APK；不代表已完成设备验证。桌面合成画面演示使用 `npm run demo`，访问 `http://localhost:8081`，配对码 `123456`。该演示不连接真实手机。

## 文档与来源

- [实际复用来源与提交](docs/UPSTREAM.md)
- [第三方和素材声明](docs/TESLAPLAY_THIRD_PARTY_NOTICES.md)
- [图标来源与导出方式](docs/APP_ICON.md)

`mobile/` 为应用打包入口，`common/` 为宿主 UI、网页服务和投影源，`shared/` 为协议组件与共享资源。`website/` 保留独立网站代码，不作为已经发布的 TeslaPlay 下载服务。历史实现文档保留上游来源；当前产品流程以本 README 为准。

TeslaPlay 复用 WheelPlay、DiPlay／xcertplay、DiPlay-tesla、DashPlay 等项目，保留原作者及适用许可。参见 [LICENSE](LICENSE)、[上游第三方声明](docs/upstream/THIRD_PARTY_NOTICES.md)及 APK“关于 → License”。代码许可不重新授权认证资源或 Tesla 商标图片；本项目不是 Tesla 官方产品。
