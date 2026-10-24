# 上游来源

- 来源项目：DiPlay（本地副本）
- 上游提交：`eae4e7babafb91f2768687061b5f22515b8cf444`
- 初始源码复制方式：仅复制 Git 已跟踪的 `mobile`、`common`、`shared`、Gradle 配置及许可证/文档；未复制构建产物、Git 元数据、认证资产、签名文件或 local.properties。
- 原版目录保持不变。
- 本分支修改：APK 包名/启动入口、前台服务及服务面板、离屏渲染输出、内嵌网页、网络协议和测试；构建基线调整为已安装的 SDK 36 / AGP 9.1.0 / Gradle 9.3.1。
- 原版文档作为来源记录保留在 `docs/upstream`，其「安装到车机」、版本号、SDK 37 和 HUD 工作流不代表本分支用法；本分支以根目录 README 为准。

## 当前目录布局

Android Gradle 工程已提升至项目根目录：`mobile/`、`common/`、`shared/` 和 Gradle 配置直接位于根目录。原版文档归档到 `docs/upstream/`，原版项目 README 参考副本为 [PROJECT-README.md](upstream/PROJECT-README.md)。根目录保留当前项目说明及许可证。

## 本地运行时资产

认证资源未纳入 Git。交付构建通过 `.local/auth-assets` 或 `TESLAPLAY_AUTH_ASSETS_DIR` 提供独立输入，由 `:mobile:assembleProvisionedDebug` 验证资源实际入包；App 自动加载，不再提供用户导入页。只构建源码而缺少资源不能作为可用 CarPlay 交付。来源、摘要和许可边界见 [第三方声明](TESLAPLAY_THIRD_PARTY_NOTICES.md)。

## 上游 CarPlay 返回入口图标（历史记录）

上游当时将默认名称及图标改为 `NIO`，旧安装保存的默认 `BYD` 名称在读取时迁移为 `NIO`。相关商标及图标属于其原权利人，不纳入本项目代码许可证授权范围。此变更仅影响 CarPlay 返回入口的名称和默认图标，不集成蔚来 App 功能，也不改变点击行为。

## Material 界面组件

Material Components for Android 1.13.0（Apache-2.0），使用官方 Material 3 主题与控件。[底部导航开发文档](https://github.com/material-components/material-components-android/blob/master/docs/components/BottomNavigation.md)。本机 `.local/maven/` 仅为下载连接受限时的本地缓存，依赖来自 Google Maven / Maven Central，不提交到仓库。


## 离线二维码

扫码使用 [Quickie 1.12.0](https://github.com/G00fY2/quickie)，基于 CameraX 与 ML Kit，采用 bundled flavor，将识别模型随应用打包，因此扫码不依赖 Google Play 服务或网络下载。Quickie 使用 MIT 许可，见 [QUICKIE-LICENSE.txt](QUICKIE-LICENSE.txt)。本地生成配对二维码仍使用 [ZXing Core 3.5.3](https://github.com/zxing/zxing)（Apache-2.0）；依赖均来自 Maven Central。

本地 HTTPS 证书签发使用 [Bouncy Castle PKIX](https://www.bouncycastle.org/download/bouncy-castle-java/)；[许可证](https://github.com/bcgit/bc-java/blob/main/LICENSE.md) 基于 MIT。证书私钥在应用本地生成并保存在私有非备份目录，不包含在仓库或 APK 中。


## 应用品牌与身份

上游品牌为 WheelPlay。TeslaPlay 首次整改版已将主要用户界面改为 TeslaPlay，但仍保留应用 ID `moe.feng.wheelplay`（Debug 追加 `.debug`）、源码命名空间 `com.shilapi.xcertplay` 及旧持久化键；工程、mDNS 等内部名称尚未全部统一。

第二轮已确认采用 `io.github.cany7.teslaplay`（Debug 追加 `.debug`）、0.1.0／0.1.0-debug 和 TeslaPlay 工程名称，并替换用户指定的 Tesla T 图标、迁移必要设置和有效证书。设置、图标和命名代码已实现；当前构建与使用流程见 [README](../README.zh-CN.md)。来源、原作者、许可证和历史资产记录继续保留真实名称。


## Material 官方图标

应用功能图标统一采用 Google 官方 [Material Icons](https://github.com/google/material-design-icons) 的 `materialicons/24px.svg`（Filled）系列，保留原始路径与填充规则，转换为 24dp Android VectorDrawable；网页箭头使用内嵌 SVG，无网络字体或 CDN 依赖。Apache-2.0 许可见 [MATERIAL-ICONS-LICENSE.txt](MATERIAL-ICONS-LICENSE.txt)。

| 用途 / 资源 | 官方图标源 |
| --- | --- |
| 启动 Tab · `ic_server_launch` | [play_circle_filled](https://github.com/google/material-design-icons/blob/master/src/av/play_circle_filled/materialicons/24px.svg) |
| 车机访问卡片、服务通知 · `ic_server_server` / `ic_diplay_notification` | [cast](https://github.com/google/material-design-icons/blob/master/src/hardware/cast/materialicons/24px.svg) |
| iPhone Tab、会话和状态卡片 · `ic_server_phone` | [phone_iphone](https://github.com/google/material-design-icons/blob/master/src/hardware/phone_iphone/materialicons/24px.svg) |
| 扫描二维码配对按钮 · `ic_server_scan` | [qr_code_scanner](https://github.com/google/material-design-icons/blob/master/src/communication/qr_code_scanner/materialicons/24px.svg) |
| 浏览器配对卡片 · `ic_server_qr` | [qr_code_2](https://github.com/google/material-design-icons/blob/master/src/communication/qr_code_2/materialicons/24px.svg) |
| 无线连接卡片 · `ic_server_wifi` | [wifi](https://github.com/google/material-design-icons/blob/master/src/notification/wifi/materialicons/24px.svg) |
| USB 连接卡片 · `ic_server_usb` | [usb](https://github.com/google/material-design-icons/blob/master/src/device/usb/materialicons/24px.svg) |
| 设置 Tab · `ic_server_settings` | [settings](https://github.com/google/material-design-icons/blob/master/src/action/settings/materialicons/24px.svg) |
| 设置选择器、其他地址展开箭头 · `ic_settings_chevron` | [chevron_right](https://github.com/google/material-design-icons/blob/master/src/navigation/chevron_right/materialicons/24px.svg) |
| 网页连接按钮 · 内嵌 SVG | [arrow_forward](https://github.com/google/material-design-icons/blob/master/src/navigation/arrow_forward/materialicons/24px.svg) |
| 媒体通知上一首 · `ic_media_skip_previous` | [skip_previous](https://github.com/google/material-design-icons/blob/master/src/av/skip_previous/materialicons/24px.svg) |
| 媒体通知播放 · `ic_media_play` | [play_arrow](https://github.com/google/material-design-icons/blob/master/src/av/play_arrow/materialicons/24px.svg) |
| 媒体通知暂停 · `ic_media_pause` | [pause](https://github.com/google/material-design-icons/blob/master/src/av/pause/materialicons/24px.svg) |
| 媒体通知下一首 · `ic_media_skip_next` | [skip_next](https://github.com/google/material-design-icons/blob/master/src/av/skip_next/materialicons/24px.svg) |
| 连接通知停止服务 · `ic_media_stop` | [stop](https://github.com/google/material-design-icons/blob/master/src/av/stop/materialicons/24px.svg) |

上游启动使用播放圆形图标，车机访问及服务通知使用投屏图标。通知采用白色遮罩；页面图标跟随 Material 主题、卡片颜色和导航选中状态。方向箭头保留展开旋转动画，设置选择器支持 RTL 镜像。NIO 返回入口与原有应用品牌位图不属于自绘功能图标；二维码矩阵、裁剪网格及安全区域辅助线是功能内容，不作图标替换。

## VPN Hotspot Root 路由适配

来源：[Mygod/VPNHotspot](https://github.com/Mygod/VPNHotspot)，Apache-2.0，许可全文见 [VPNHOTSPOT-LICENSE.txt](VPNHOTSPOT-LICENSE.txt)。适配基线为 v2.17.1，提交 `58ef509708a84f4da84fc54d8008895d02adef34`：

- `mobile/src/main/java/be/mygod/vpnhotspot/net/Routing.kt`：按下游接口安装策略路由、源地址限定的回程查询、成对增删 iptables 规则。
- `mobile/src/main/java/be/mygod/vpnhotspot/util/RootSession.kt`：成功安装后保存逆序撤销命令、异常回滚及服务停止后撤销。
- `mobile/src/main/java/be/mygod/vpnhotspot/net/TetheringManager.kt`：系统热点状态广播及 `tetherArray` 字段。

修改集中于 `RootHttpRouting.kt` 和 `TeslaHttpCompatibility.kt`：将上游的通用 VPN 上网共享收窄为 WheelPlay 本地 HTTP 入口，通过 Root 分配 loopback `/32` 共享地址、安装局部投递及回程路由；Root shell 持有规则事务，App 管道关闭后撤销。未复制上游 App、UI、DNS 代理、客户端数据库或 Rust daemon，不提供通用 VPN Internet 转发。此处描述 Root 路由的上游适配。TeslaPlay 默认使用另一路免 Root VPN 入口；有线 CarPlay 沿用原有实现，本次不安排共存专项。

热点下游采用 [AOSP RouteController](https://android.googlesource.com/platform/system/netd/+/refs/heads/main/server/RouteController.cpp) 的 `local_network`（97）路由表；上游 `1000 + ifindex` 的物理上网接口表不适用于热点下游。

`RootWebPorts.kt` 生成低位端口转发规则及占用检查，由 `RootHttpRouting.kt` 与共享 IPv4 地址、热点投递和回程规则在同一事务 shell 中管理。端口权限依据 [Linux 内核 ip_unprivileged_port_start 文档](https://docs.kernel.org/networking/ip-sysctl.html)；按 [Netfilter DNAT 文档](https://ipset.netfilter.org/iptables-extensions.man.html) 只修改 TCP 目标端口，限定到共享地址 `/32` 并保留目标 IP。普通 Web 监听使用无需 Root 的高位端口。此端口转发是 WheelPlay 的本地服务扩展。

目标地址已限定为成功安装的本地 `/32`，端口规则无需额外的 `addrtype --dst-type LOCAL` 匹配。移除此依赖以兼容缺少 `addrtype` 内核模块的 ROM；TCP、comment、conntrack 及 DNAT 行为保留。事务 shell 记录具体失败命令和原始错误，回滚后仍保留首次失败的诊断。

## TeslaPlay F02–F12 适配（2026-10-23）

工作基线为 WheelPlay `45d1740a093d5f43345bce6d22a63ee03d55092b`。保留其 Android／网页投屏架构、配对和 TLS 导入；没有移植参考项目整套网页或网络共享实现。源文件原有版权和 SPDX 声明保持不变。表中 `common/.../` 与 `shared/.../` 分别展开为 `common/src/main/java/com/shilapi/xcertplay/` 与 `shared/src/main/java/com/shilapi/xcertplay/`；对应测试原位于同模块 `src/test/java/com/shilapi/xcertplay/`。上述为参考文件的原始命名；0.1.0 中本仓库源码与测试已迁至 `io/github/cany7/teslaplay/`，不改写参考仓库的历史路径。

| 功能 | 实际参考版本与文件 | 本次适配 |
| --- | --- | --- |
| F03 | DiPlay `4487c689bfdd3baab12de67a993a671b67939eb7` 的 `common/.../AirPlayPersistence.kt`、`CarPlayHostActivity.kt`；引入点 `593ac23641e231e1cb0876125f2b772ea9d48b3f` | 将兼容存储键 `car_bluetooth_audio` 接入统一三项音频输出和 `disableAudioOutput`；复用 WheelPlay `BrowserAudioBridge`、`CarPlayMediaSessionBridge` 与网页音频模块，统一停用焦点、麦克风与系统媒体控制，保留歌曲信息及旧偏好。 |
| F04 | 同版 DiPlay `shared/.../network/CarPlayBonjour.kt`；引入点 `88e847ec865f7d8b4ad2cf36e0a4999fd00c8aed` | 仅移植明确主机名的地址对象包装，保留 IPv6 scope 和 WheelPlay 原接口生命周期。 |
| F05 | 同版 DiPlay `shared/.../orchestration/{WiredStartAgain,WiredStartWatchdog,CarPlayController}.kt`、`transport/{Iap2WiredControlClient,NcmTrafficCounter,NcmUsbBridge}.kt` 及对应测试；引入点 `e63b7b3d27cdde7944aee8273fa63b3be310be21` | 用 NCM 收到数据及连接尝试证据触发最多一次补发；watchdog、取消和现有恢复入口协调，预算耗尽不自动创建无限重试。 |
| F06 | 同版 DiPlay `shared/.../airplay/{AirPlayTouchSlots,AirPlayHid,AirPlaySession}.kt` 及测试；引入点 `48c971711f2413403121c11f573490c84ef976f6` | 保留固定槽位和最后有效坐标；WheelPlay `CarPlaySourceSession` 不再填零；网页 pointerup 使用最后有效事件坐标，保留原 TouchLease 和事件队列。 |
| F07 | 同版 DiPlay `common/.../CrashRecord.kt`；引入点 `5b6dead5694907dfc8de1fb94cda22b6d59b9625`；DiPlay-tesla `a8aa630c79387675aba209e825fd0fd3e0e3c6cf` 的 `browser/src/main/assets/tesla/app.js` | 适配持久崩溃记录、版本、容量／7天保留与清除；诊断字段参考，实际 FPS／传输仍读取 WheelPlay 既有计数器，未知数据不伪造。 |
| F08 | DiPlay `4487c689bfdd3baab12de67a993a671b67939eb7` 的 `common/.../backup/SettingsBackupCodec.kt`；包含 `2651bf8d`、`5820f626`、`068fbffc`、`d0a67cea` 后续修正 | 白名单 JSON 格式设计参考；使用 Android 文件选择器、预览、一次校验及单偏好文件提交；不复制其 HTTP 备份服务器。 |
| F09 | 同版 DiPlay `common/.../DiPlayActivity.kt` 设置搜索（引入 `3ce0a18165c7dd514f495c408485c35c6b82f04c` 及后续修正） | 仅参考行为，在 WheelPlay Compose 分类页实现三语索引、滚动和高亮，不复制其 Activity 布局体系。 |
| F10 | 同版 DiPlay `common/.../PhoneBluetoothReceiver.kt`；引入点 `fe1a59db28a5522267b66b2d1b95e3660f387978` | ACL 设备匹配、持久去抖；与开机启动共用幂等服务入口，不强行打开 Activity。 |
| F11/F12 | DiPlay-tesla `a8aa630c79387675aba209e825fd0fd3e0e3c6cf` 的 `browser/src/main/assets/tesla/{display-presets.js,app.js,index.html,style.css}`；对照 `browser/src/main/java/com/diplay/browser/BrowserDisplay.kt`、`tesla/src/main/java/com/diplay/tesla/TeslaBrowserOutput.kt` | 尺寸比例／缩放参考；手机保留两组显示偏好，网页配置菜单已删除并恢复 WheelPlay 顶栏。快捷按钮沿用原触控映射、WebSocket、HID 和受控重连，不改变编码或 FPS。 |
| F02 | DashPlay `562e5b102d3ed63f593acc45c35d1f455b60f771` 的 `scripts/onecloud/{own_domain.py,boxnet.py}` | 复用 DuckDNS 私网地址、DNS-01、TXT 传播等待、提前续期与旧证书保留策略；Android 新增 RFC 8555 客户端和 JobScheduler，复用 WheelPlay `LanTls` 与 `WebSession` 原子证书／监听器替换。未复制或打包 acme.sh、网络共享或热点 DNS。 |

各参考仓库 LICENSE 均为 GPL-3.0，全文见根目录 [LICENSE](../LICENSE)。既有 Compose／网页衍生部分的 AGPL-3.0-only 声明保留，全文见 [DiAuto-AGPL-3.0.txt](upstream/licenses/DiAuto-AGPL-3.0.txt)。本次第三方说明见 [TESLAPLAY_THIRD_PARTY_NOTICES.md](TESLAPLAY_THIRD_PARTY_NOTICES.md)。

### 2026-10-24 整改

在上述基线与参考版本上补齐产品整合，不引入新的协议栈：

- F02：`ManagedHttps.kt`、`LanTls.kt`、`WebListenSettings.kt`、`WebSession.kt` 协调 DuckDNS 生效模式、证书持久化和监听器切换；`AcmeSettingsGroup.kt`、`WebSettingsScreen.kt`、`TeslaSettingsScreen.kt` 合并配置；`TeslaHttpConfig.kt`／`TeslaHttpCompatibility.kt` 固定默认 VPN 入口为 `100.96.0.1`；网页 HTTPS 链接改用证书域名。
- F03：沿用前述 DiPlay 音频协商与 WheelPlay 会话；`CarPlayExperienceSettings.kt`／`AudioSettingsScreen.kt` 统一三项输出，`CarPlaySourceSession.kt` 保存实际会话选择，原存储键及退出蓝牙后的偏好保留。
- 默认值与产品界面：`TeslaDefaults.kt` 一次迁移，`SettingsBackupCodec.kt` 同步默认值；`DiPlayActivity.kt`、`SettingsScreen.kt`／`SettingsSearch.kt`／`HelpSettingsScreen.kt` 收敛导航、认证和许可入口。首次整改版车型图标为自行排版的 TESLA 文字；第二轮已替换用户指定 T 图标，来源记录见第三方声明。
- 认证：参考上述 DiPlay 提交的 `DiPlayBootstrap.kt`／`mobile/build.gradle.kts` 资源装载方式；实际 APK 资源来自官方 DiPlay 0.2.21 发布包。完整来源、文件摘要及许可边界见 [本轮第三方声明](TESLAPLAY_THIRD_PARTY_NOTICES.md#2026-10-24-认证与界面收敛)。

## TeslaPlay 0.1.0 身份与设置整理（2026-10-24）

- 本仓库统一 `io.github.cany7.teslaplay` 命名空间及应用 ID，Debug 追加 `.debug`；版本 0.1.0／0.1.0-debug，versionCode 1。源码包目录、Manifest、JNI、原生库加载、CI 与网页消息标识同步迁移；来源与版权不改名。
- `TeslaPlayActivity`、`TeslaPlaySessionService`、`TeslaPlayBootstrap` 为本仓库对应旧 DiPlay 类的新名称；`teslaplay_airplay` 与 `teslaplay_browser_devices` 为新偏好文件名。旧普通设置备份和浏览器 localStorage 有必要的兼容映射，不把凭据纳入普通备份。
- 定位页移除 OEM／比亚迪导航展示；画面页去掉第二套分辨率／自适应控件，统一窗口尺寸路径。保留 HEVC、WebRTC 与 FPS 行为；外观、图标与文字大小、图片兼容分组与浏览器虚拟按钮完成整理，输入源只显示 CarPlay。
- Tesla 图标由用户指定，原图字节及摘要见 [图标说明](APP_ICON.md)。新包独立安装，不自动继承旧包私有数据。

### 2026-10-24 UI and codec negotiation follow-up

TeslaPlay 0.1.1 keeps the existing upstream transport implementation. The bounded
HEVC-to-H.264 reconnect uses WheelPlay-derived `CarPlayHostActivity.kt`,
`web/WebSession.kt`, `web/LanWebServer.kt` and `assets/web/app.js`; no new third-party
codec or transcoder was added. The UI copy, draft/active-state fixes, neutral theme
and image padding are local TeslaPlay changes. Earlier pinned source revisions
and their license notices above remain applicable. The exact user-supplied icon
and generated files are documented in `APP_ICON.md`.
