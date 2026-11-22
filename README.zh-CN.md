# TeslaPlay

[English](README.md) · **简体中文**

通过 Tesla 浏览器使用 iPhone 的 CarPlay。Android 手机运行 TeslaPlay，将 CarPlay 画面传送到车机浏览器，并把触控操作传回 iPhone。

**连接方式：iPhone → Android 手机上的 TeslaPlay → Tesla 浏览器。**

## 主要功能

- USB 或无线连接 iPhone，支持浏览器多点触控。
- WebRTC 视频、HEVC 编码与 60 FPS 设置，支持 JPEG／WebP 图片兼容串流。
- Tesla 右侧窗口和大窗口独立配置，支持分辨率预设、自定义尺寸与缩放。
- 日间、夜间及跟随系统外观，可调整 CarPlay 图标与文字大小。
- Android、浏览器或 iPhone 蓝牙音频输出；定位可选择 iPhone、Android 或浏览器。
- 主页、返回、Siri、上一曲、播放／暂停、下一曲虚拟按键。
- DuckDNS HTTPS 证书申请与自动续期、二维码配对、设备管理、设置备份及蓝牙触发启动。

另提供实验性 [Android Auto](docs/ANDROID_AUTO.md) 和 [VNC](docs/VNC.md) 输入源。同一时间支持一个投影输入源和一个浏览器控制会话。

## 设备要求

- Android 9 或更高版本的手机，支持热点和所选的 USB／无线连接方式。
- 支持 CarPlay 的 iPhone。
- 能连接 Android 手机热点并访问其服务的 Tesla 浏览器。
- 使用 DuckDNS HTTPS 时，需要自己的 DuckDNS 域名和 token，以及可用的互联网连接。

测试设备：

| 设备 | 系统 |
| --- | --- |
| iPhone Air | iOS 27.2 RC |
| 小米 15 Ultra | HyperOS 4.0.0.18 Beta |

## 首次连接

1. 安装包含 CarPlay 认证资源的 TeslaPlay APK，打开 App 并完成权限授权。自行构建的方法见下文。
2. 打开 Android 手机热点，让 Tesla 接入。无线 CarPlay 使用热点时，在 App 中填写对应的热点名称和密码。热点联网可搭配外部 Shizzi 使用。
3. 在“Tesla 网络与 HTTPS”中选择连接方式。默认使用免 Root VPN，按提示完成 VPN 授权；Root 模式适用于已配置相应权限的设备。
4. 使用 DuckDNS HTTPS 时，选择 **DuckDNS** 证书类型，填写域名和 token，接受证书服务条款并申请证书。App 自动更新域名解析、申请和续期证书。
5. 通过 USB 或无线方式连接 iPhone，完成 iPhone 上的 CarPlay 授权。
6. 在 Tesla 浏览器打开 App 显示的完整地址，例如 `https://your-name.duckdns.org:8443/`，扫描网页二维码或输入配对码，然后开始显示。

首次配置请在停车状态进行。连接期间保持热点和 TeslaPlay 服务运行。DuckDNS 提供域名解析，浏览器仍须能够访问手机；默认 HTTPS 端口为 **8443**，地址中需包含 `https://` 和端口。

## 常用设置

- **窗口与画面**：在手机 App 中选择右侧／大窗口及输出尺寸。保存后使用应用并重连使配置生效。
- **音频**：使用“iPhone 蓝牙输出”时，在 iPhone 上选择 Tesla 等蓝牙音频设备；音频和麦克风由 iPhone 管理。切换音频输出后应用并重连。
- **虚拟按键**：在手机 App 的“浏览器设置”中开启，按键外观跟随 CarPlay 日／夜模式。
- **浏览器权限**：浏览器麦克风和定位需要可信 HTTPS 及浏览器授权。
- **配对与备份**：保留站点数据和访问地址可恢复配对；更换地址或清除数据后可能需要重新配对。设置备份不包含证书私钥、DuckDNS token 或认证资源。

## 构建

| 工具 | 版本 |
| --- | --- |
| JDK | 25 |
| Gradle／Android Gradle Plugin | 9.3.1／9.1.0 |
| Android SDK／Build Tools | API 36／36.0.0 |
| Android NDK／CMake | 28.2.13676358／3.22.1 |

通过 `ANDROID_HOME` 或 `local.properties` 中的 `sdk.dir` 指定 Android SDK。首次构建需要下载依赖及原生库源码。

将配套的 CarPlay 认证资源放在以下位置：

```text
.local/auth-assets/offline-mfi/identity.pk8
.local/auth-assets/offline-mfi/certificate.p7b
```

也可将 `TESLAPLAY_AUTH_ASSETS_DIR` 指向包含 `offline-mfi/` 的目录。认证资源不包含在源码中，来源说明见[第三方声明](docs/TESLAPLAY_THIRD_PARTY_NOTICES.md)。

```sh
./gradlew :mobile:assembleProvisionedDebug
```

产物为 `mobile/build/outputs/apk/debug/mobile-debug.apk`，调试应用 ID 为 `io.github.cany7.teslaplay.debug`。该任务会检查认证资源是否完整并正确打包。

## 文档与致谢

- [架构说明](docs/ARCHITECTURE.md)
- [Android Auto](docs/ANDROID_AUTO.md) · [VNC](docs/VNC.md)
- [第三方与素材声明](docs/TESLAPLAY_THIRD_PARTY_NOTICES.md) · [图标说明](docs/APP_ICON.md)

TeslaPlay 基于 WheelPlay，使用或参考了 DiPlay／xcertplay、DiPlay-tesla 和 DashPlay 的实现。感谢各项目作者与贡献者。

许可见 [LICENSE](LICENSE) 和[第三方声明](docs/upstream/THIRD_PARTY_NOTICES.md)，各组件保留其原始许可。认证资源、Tesla 商标和图片遵循各自的授权条件。TeslaPlay 是独立项目，非 Tesla 官方产品。
