# TeslaPlay

**English** · [简体中文](README.zh-CN.md)

Run CarPlay on an Android phone and use it from a Tesla browser. TeslaPlay builds on WheelPlay's CarPlay, streaming, pairing, and touch implementation, with Tesla window profiles, hotspot connections, iPhone Bluetooth audio, and DuckDNS HTTPS.

Repository: [cany7/TeslaPlay](https://github.com/cany7/TeslaPlay). The version is **0.1.1**. Application IDs are `io.github.cany7.teslaplay` and `io.github.cany7.teslaplay.debug`. No unverified release download link is provided; see the build instructions below for local artifacts.

## Getting started

Use an Android 9 or newer host phone, an iPhone, and a Tesla connected to the host's hotspot. Complete setup and checks while parked.

1. Install a provisioned TeslaPlay APK. CarPlay authentication resources are bundled at build time and loaded automatically on first use. The app no longer offers a DiPlay import page or onboarding flow; a build missing resources must be replaced with a complete build.
2. Enable the Android hotspot and connect the Tesla or computer. Hotspot is the default wireless connection method; configure its name and password as required by the app. Use external Shizzi if hotspot internet access is needed. TeslaPlay does not implement tethering or hotspot DNS control.
3. Open the Tesla network and HTTPS settings. The default connection uses a no-root VPN with the fixed entrance `100.96.0.1`; accept Android's VPN permission. A root connection option remains available for existing setups.
4. Select **DuckDNS** as the certificate type, enter your domain and token, accept the certificate service terms, and save/request a certificate. The app updates the domain's A record to the active entrance and manages DNS-01 issuance and renewal; no manual IPv4 update on DuckDNS is needed. Check the actual serving certificate and renewal state, rather than only the last task result.
5. Open the complete address shown by the app, for example `https://your-name.duckdns.org:8443/`. The default HTTPS port is `8443`; include both the scheme and port. DNS resolution does not relay the phone's service through the internet: the browser still needs network access to the phone.
6. Scan the browser's QR code or enter a pairing code. Keep the app in the foreground, connect the iPhone over USB or wirelessly, and complete Android permissions and the iPhone's CarPlay prompt.

Pairing restoration depends on the browser retaining site data and using the same address. HTTP and HTTPS are separate origins. Changing the address, clearing site data, or revoking a browser can require pairing again.

## Features and settings

- **Display:** WebRTC, HEVC, and 60 FPS settings remain available. HEVC defaults on and image compatibility quality defaults to 100. Tesla side and large windows store separate profiles. Window profiles are the single source of output dimensions; saving marks a pending change, and explicit apply reconnects. Browser resizing does not interrupt an active session automatically.
- **Display settings:** Appearance controls day/night/system behavior; icon and text size controls CarPlay UI density. Image format and quality affect image streaming and WebRTC fallback only. WebRTC stall tolerance is under advanced settings. Safe areas and right-hand-drive layout remain available.
- **Audio:** Choose Android, browser, or **iPhone Bluetooth output** in one selector. Bluetooth output lets the iPhone manage its own audio and microphone; choose Tesla or another target on the iPhone. Android does not relay this audio. Apply a changed selection by reconnecting; the UI distinguishes the saved choice from the active session.
- **Browser and touch:** QR pairing, multitouch, the standard toolbar and optional virtual buttons controlled only from the Android app’s browser settings. Browser microphone and GPS require trusted HTTPS and browser permission.
- **Location:** The iPhone's location is the default, with Android and browser options. Location settings contain no OEM card. The vehicle is fixed to Tesla internally, and the launcher and CarPlay return shortcut use the specified Tesla T image.
- **Maintenance:** Settings search and backup, paired-device management, selected Bluetooth device service startup, and local diagnostics. Ordinary settings backups exclude certificate private keys, DuckDNS tokens, and CarPlay authentication resources.

Experimental [Android Auto](docs/ANDROID_AUTO.md), [VNC](docs/VNC.md), and diagnostic sources remain available; one projection source and one controlling browser session can run at a time. ICCOA is removed from source selection. Android Auto uses the Android host for audio and microphone; the existing VNC transport does not support TLS/VeNCrypt.

Desktop checks do not replace Tesla and iPhone validation. Check window sizes, music/navigation/calls/Siri, gestures and Android background behavior on the actual devices while parked.

## Build

| Tool | Version |
| --- | --- |
| JDK | 25 |
| Gradle / Android Gradle Plugin | 9.3.1 / 9.1.0 |
| Android SDK / Build Tools | API 36 / 36.0.0 |
| Android NDK / CMake | 28.2.13676358 / 3.22.1 |
| Node.js | 22 or newer |

Set the SDK using `ANDROID_HOME` or `sdk.dir` in an untracked `local.properties`. The Gradle wrapper is included. The first native build fetches pinned libdatachannel and Mbed TLS sources.

Provide matching authentication resources as separate local build inputs:

```text
.local/auth-assets/offline-mfi/identity.pk8
.local/auth-assets/offline-mfi/certificate.p7b
```

Alternatively, set `TESLAPLAY_AUTH_ASSETS_DIR` to a directory containing `offline-mfi/`. Do not commit these resources or include them in source archives. Their provenance and hashes are recorded in [third-party notices](docs/TESLAPLAY_THIRD_PARTY_NOTICES.md). Build a provisioned debug APK with:

```sh
./gradlew :mobile:assembleProvisionedDebug
```

This task verifies that both resources are complete and match the APK contents. Output is `mobile/build/outputs/apk/debug/mobile-debug.apk`, version `0.1.1`, application ID `io.github.cany7.teslaplay.debug`. `assembleDebug` is useful for compilation checks, but a build without these resources is not a usable CarPlay delivery. There is no post-install resource import UI.

The new application ID installs as a separate app and cannot replace `moe.feng.wheelplay.debug`. Settings, valid certificates, and pairing data do not transfer automatically. An ordinary settings backup is not a complete migration. Retain old data until the new app is verified, and do not let both apps occupy the VPN entrance and service ports simultaneously.

Run checks relevant to your changes:

```sh
./gradlew :shared:testDebugUnitTest :common:testDebugUnitTest \
  :common:assembleDebugAndroidTest :mobile:lintDebug
npm ci
npm test
npm run check
```

`assembleDebugAndroidTest` only compiles the instrumented test APK and does not establish device validation. Run `npm run demo` for a synthetic desktop fixture at `http://localhost:8081` with pairing code `123456`; this fixture does not connect a real phone.

## Documentation and origins

- [Reused sources and commits](docs/UPSTREAM.md)
- [Third-party and artwork notices](docs/TESLAPLAY_THIRD_PARTY_NOTICES.md)
- [Icon source and exports](docs/APP_ICON.md)

`mobile/` packages the app, `common/` contains the host UI, web service, and projection sources, and `shared/` contains protocol components and shared resources. `website/` retains the separate website code; it is not a confirmed deployed TeslaPlay download service. Historical implementation documents retain upstream context; this README describes the current product flow.

TeslaPlay reuses WheelPlay, DiPlay / xcertplay, DiPlay-tesla, DashPlay, and other projects, retaining their authors and applicable licenses. See [LICENSE](LICENSE), [upstream third-party notices](docs/upstream/THIRD_PARTY_NOTICES.md), and About → License in the app. Code licenses do not relicense authentication resources or Tesla trademarks and images. This is not an official Tesla product.
