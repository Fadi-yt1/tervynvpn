# Tervyn VPN

A free, account-free Android VPN client (package `com.tervyn.vpn`, Android 8.0 / API 26+).
Kotlin · Jetpack Compose · MVVM · Room + DataStore · OkHttp · Android `VpnService` · Xray-core v26.9.30.

* **One-tap connect** to the fastest *verified* server; automatic failover and auto-switch (≥40 % faster, with hysteresis).
* **Two routing modes** — *All Apps* (full tunnel, DNS and IPv6 leak protection) and *Only My Sites*
  (domain-aware split tunnel via fake DNS; presets for YouTube, Instagram, X, Discord, Telegram, ChatGPT, Claude, Netflix, …).
* **Server sources** — 18 public free-proxy lists, Proxio.io (API key), your own servers (VLESS / VMess / Trojan /
  Shadowsocks / SOCKS5 / HTTP via links, QR images, subscriptions or manual config), provider code snippets
  (cURL / Python / Node.js), and `.txt` proxy lists.
* Share → *Tervyn VPN* adds a website from Chrome; Quick Settings tile; always-on compatible; app lock with PIN/biometrics;
  speed test, diagnostics, live statistics, local history & analytics. No ads, no telemetry.

## Build

Requirements: JDK 21 and the Android SDK (platform 37). Gradle is provided by the wrapper.

```bash
scripts/build-xray.sh            # or: scripts/fetch-xray.sh — puts Xray-core into xray/android/<abi>/
```

```bash
./gradlew assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Release APK/AAB and signing: see [docs/RELEASE.md](docs/RELEASE.md).
Without the Xray binaries the app still builds and works with HTTP/SOCKS5 servers; advanced protocols then report
"Xray unavailable".

## Install & try it on a phone

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Tervyn → *Get started & connect* → accept Android's VPN request. The first connection downloads the proxy
lists and tests servers over HTTPS (typically 10–40 s on free servers). Check *Check My IP* afterwards.

## Tests

```bash
./gradlew testDebugUnitTest          # JVM: parsers, dialer, TLS checks, DNS, TCP stack, ranking, Xray, UI flows (Robolectric)
```

```bash
./gradlew connectedDebugAndroidTest  # on a device/emulator (see VpnServiceInstrumentedTest for options)
```

The JVM suite runs the real engine code: raw IPv4/TCP packets are fed into `TunEngine` and travel through local
SOCKS5/HTTP proxies (with authentication) to real servers; HTTPS verification is tested against trusted, wrong-host
and untrusted certificates; generated Xray configs are validated by the real Xray binary and VLESS/Shadowsocks
round-trips run through actual Xray processes; UI flows run on API 26 and 35 via Robolectric and write screenshots
to `app/build/screenshots/`.

## Architecture

```
app/src/main/java/com/tervyn/vpn/
  vpn/       TervynVpnService (TUN, lifecycle, health/failover), TunEngine (packet dispatch), TcpConn (TCP state machine,
             flow control, retransmission), Packets (IPv4/TCP/UDP/ICMP), VpnController (state)
  net/       ProxyDialer (SOCKS5 / HTTP CONNECT + auth), HttpsOverProxy, ProxyTester (Cloudflare trace),
             FakeDns (198.18.0.0/15), DnsResponder, DohForwarder, IpChecker, SpeedTest
  core/      Core.kt (ServerManager: discovery, testing, ranking, selection), Ranking (auto-switch policy),
             ProxyList, ProxySnippet, Proxio, Links (share links), ProxySources, Domains
  xray/      XrayConfig (config generation), XrayProcess / XrayManager (lifecycle, local SOCKS5 endpoints)
  data/ database/ security/   repositories, Room entities/DAOs, Keystore AES-GCM, PIN hashing
  services/ receivers/        notifications, Quick Settings tile, boot receiver
  ui/        theme (design tokens), components (reusable), navigation, screens (36+), MainViewModel
```

How traffic flows: Android routes app traffic into the TUN. DNS queries get synthetic addresses from
`198.18.0.0/15`; when an app opens a TCP connection to one, `TcpConn` completes the handshake locally only after the
proxy connection to the **domain name** succeeded, then relays data with proper windowing. Tervyn's own UID is
excluded from the tunnel, so proxy tests, list downloads and Xray never loop. See [docs/XRAY.md](docs/XRAY.md) and
[docs/LIMITATIONS.md](docs/LIMITATIONS.md).

## Progress checklist

| Phase | Status |
|---|---|
| 1 Inspect & plan | ✅ Repository was empty (no browser-extension sources to reuse); 18 public list URLs verified individually |
| 2 UI foundation | ✅ Theme/tokens, vector "T" logo (adaptive + monochrome), 20 reusable components, 40 routes |
| 3 VPN engine | ✅ VpnService, TUN, user-space TCP, SOCKS5, HTTP CONNECT, fake DNS, both routing modes |
| 4 Proxy discovery | ✅ 18 sources, dedupe, 64-way HTTPS testing with cert validation, ranking, backups, country from verified exit IP |
| 5 Xray | ✅ v26.9.30 built for 4 ABIs + checksums, link parsing, config generation, authenticated local SOCKS5, temp test instances |
| 6 Advanced features | ✅ Share-to-add, auto-switch, Proxio, list import, snippets, subscriptions, DNS settings, notifications, QS tile |
| 7 Security & testing | ✅ JVM + Robolectric suites green; ⚠️ device tests written, not run here (no emulator in the build sandbox) |
| 8 Release | ✅ Debug/release APK and AAB build; signing via `keystore.properties` |

## Privacy

No accounts, ads or analytics. Credentials are encrypted with an Android Keystore key and excluded from backups.
Free public proxies are operated by unknown third parties — don't use them for banking or sensitive logins.
Tervyn does not claim anonymity or verify third-party logging policies.

## Licenses

Tervyn bundles [Xray-core](https://github.com/XTLS/Xray-core) (MPL-2.0, unmodified) as an executable. Other
dependencies are Apache-2.0. See *Settings → About → Open Source Licenses*.
