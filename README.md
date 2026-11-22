# TeslaPlay

**English** · [简体中文](README.zh-CN.md)

Use iPhone CarPlay through your Tesla browser. TeslaPlay runs on an Android phone, streams CarPlay to the car's browser, and sends touch input back to your iPhone.

**Connection: iPhone → TeslaPlay on Android → Tesla browser.**

## Features

- USB or wireless iPhone connection with browser multitouch.
- WebRTC video, HEVC encoding, and a 60 FPS setting, with JPEG/WebP image streaming for compatibility.
- Separate Tesla side-window and large-window profiles, resolution presets, custom dimensions, and scaling.
- Day, night, and system appearance, with adjustable CarPlay icon and text size.
- Android, browser, or iPhone Bluetooth audio output; iPhone, Android, or browser location.
- On-screen Home, Back, Siri, Previous, Play/Pause, and Next buttons.
- DuckDNS HTTPS certificate issuance and automatic renewal, QR pairing, device management, settings backup, and Bluetooth-triggered startup.

Experimental [Android Auto](docs/ANDROID_AUTO.md) and [VNC](docs/VNC.md) sources are also available. One projection source and one controlling browser session are supported at a time.

## Requirements

- An Android 9 or newer phone supporting a hotspot and your chosen USB/wireless connection method.
- A CarPlay-compatible iPhone.
- A Tesla browser that can connect to the Android hotspot and reach the phone's service.
- For DuckDNS HTTPS: your own DuckDNS domain and token, plus internet access.

Tested devices:

| Device | OS |
| --- | --- |
| iPhone Air | iOS 27.2 RC |
| Xiaomi 15 Ultra | HyperOS 4.0.0.18 Beta |

## Getting started

1. Install a TeslaPlay APK containing the CarPlay authentication resources, open the app, and grant the required permissions. For a local build, see below.
2. Enable the Android hotspot and connect your Tesla. When using the hotspot for wireless CarPlay, enter its name and password in the app. External Shizzi can be used for hotspot internet sharing.
3. Choose a connection method in the Tesla network and HTTPS settings. The default is a no-root VPN; approve Android's VPN prompt. Root mode is available for devices with the required access configured.
4. For DuckDNS HTTPS, select the **DuckDNS** certificate type, enter your domain and token, accept the certificate service terms, and request a certificate. The app manages DNS updates, certificate issuance, and renewal.
5. Connect the iPhone over USB or wirelessly and approve its CarPlay prompt.
6. Open the complete address shown by the app in the Tesla browser, such as `https://your-name.duckdns.org:8443/`. Scan the page's QR code or enter a pairing code, then start the display.

Complete initial setup while parked. Keep the hotspot and TeslaPlay service running during use. DuckDNS provides DNS resolution; the browser still needs network access to the phone. The default HTTPS port is **8443**—include both `https://` and the port in the address.

## Common settings

- **Window and display:** Choose the side/large window and output dimensions in the Android app. Save, then apply and reconnect to activate the configuration.
- **Audio:** For iPhone Bluetooth output, select Tesla or another Bluetooth audio device on the iPhone. The iPhone handles audio and the microphone. Apply and reconnect after changing the audio output.
- **On-screen buttons:** Enable them in the Android app's browser settings. Their appearance follows CarPlay's day/night mode.
- **Browser permissions:** Browser microphone and location require trusted HTTPS and browser permission.
- **Pairing and backup:** Retain site data and use the same address to restore pairing. Changing the address or clearing data may require pairing again. Settings backups exclude certificate private keys, DuckDNS tokens, and authentication resources.

## Build

| Tool | Version |
| --- | --- |
| JDK | 25 |
| Gradle / Android Gradle Plugin | 9.3.1 / 9.1.0 |
| Android SDK / Build Tools | API 36 / 36.0.0 |
| Android NDK / CMake | 28.2.13676358 / 3.22.1 |

Set the Android SDK path using `ANDROID_HOME` or `sdk.dir` in `local.properties`. The first build downloads dependencies and native library sources.

Place matching CarPlay authentication resources at:

```text
.local/auth-assets/offline-mfi/identity.pk8
.local/auth-assets/offline-mfi/certificate.p7b
```

Alternatively, set `TESLAPLAY_AUTH_ASSETS_DIR` to a directory containing `offline-mfi/`. These resources are not included in the source tree; see the [third-party notices](docs/TESLAPLAY_THIRD_PARTY_NOTICES.md) for provenance.

```sh
./gradlew :mobile:assembleProvisionedDebug
```

The output is `mobile/build/outputs/apk/debug/mobile-debug.apk`, with debug application ID `io.github.cany7.teslaplay.debug`. This task verifies that the authentication resources are complete and correctly packaged.

## Documentation and credits

- [Architecture](docs/ARCHITECTURE.md)
- [Android Auto](docs/ANDROID_AUTO.md) · [VNC](docs/VNC.md)
- [Third-party and artwork notices](docs/TESLAPLAY_THIRD_PARTY_NOTICES.md) · [App icon](docs/APP_ICON.md)

TeslaPlay is based on WheelPlay and uses or draws on implementations from DiPlay / xcertplay, DiPlay-tesla, and DashPlay. Thanks to their authors and contributors.

See [LICENSE](LICENSE) and the [third-party notices](docs/upstream/THIRD_PARTY_NOTICES.md). Components retain their original licenses. Authentication resources, Tesla trademarks, and artwork remain subject to their respective terms. TeslaPlay is an independent project and is not an official Tesla product.
