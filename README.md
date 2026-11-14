# Tervyn VPN

A free, account-free Android VPN client (package `com.tervyn.vpn`, Android 8.0 / API 26+).
Kotlin · Jetpack Compose · MVVM · Room + DataStore · OkHttp · Android `VpnService` · Xray-core v26.9.30.

* **20 built-in servers, 20 countries** — community servers (United States, Canada, United Kingdom, Germany,
  Netherlands, France, Spain, Italy, Switzerland, Poland, Finland, Sweden, Turkey, UAE, India, Singapore,
  Hong Kong, Japan, South Korea, Australia) shipped with the app and validated against Xray at build time.
  Each one is verified on the device with an HTTPS test before it is shown as working; a slot that stays down is
  repaired automatically from the live Tervyn lists.
* **Fastest available** picks the fastest verified server and switches when a clearly faster one appears
  (≥ 40 % lower latency, confirmed twice, at most every 5 minutes). Selecting a specific server turns that off.
* **Secure DNS — independent of the VPN.** With Secure DNS on and no server connected, Tervyn runs a DNS-only
  tunnel: apps' lookups go to Cloudflare / Google / Quad9 / AdGuard / OpenDNS over DNS-over-HTTPS; all other traffic
  takes its normal path. When a server is connected, *only the server runs*: names are resolved through it and Secure
  DNS resumes after you disconnect.
* **Two routing modes** — *All apps* (full tunnel with DNS and IPv6 leak protection) and *Only my sites*
  (domain-aware split tunnel via synthetic DNS; presets for YouTube, Instagram, X, Discord, Telegram, ChatGPT, Claude,
  Netflix, WhatsApp, Spotify, Reddit, TikTok, Google). Per-app split tunnelling (bypass or allow-list).
* **Your own servers** — VLESS (REALITY, Vision, WS, gRPC, XHTTP, HTTPUpgrade), VMess, Trojan, Shadowsocks,
  Hysteria2, SOCKS5 and HTTP via share links, files or subscription URLs. Certificates are always verified.
* Share → *Tervyn VPN* adds a website or a server link; Quick Settings tile; always-on compatible; app lock with
  PIN/biometrics; Check my IP, speed test, diagnostics, live statistics, local history. No ads, no telemetry.

## Build

Requirements: JDK 21, Go 1.27 (for Xray) and the Android SDK (platform 37). Gradle is provided by the wrapper.

```bash
scripts/build-xray.sh            # builds Xray-core v26.9.30 into xray/android/<abi>/ and verifies the checksums
```

```bash
./gradlew assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Release APK/AAB and signing: see [docs/RELEASE.md](docs/RELEASE.md).
GitHub Actions (`.github/workflows/android.yml`) builds Xray, runs the unit tests and uploads debug APK, release APK
and AAB on every push. Without the Xray binaries the app still builds and works with SOCKS5/HTTP servers; Xray
protocols then report "Xray unavailable".

## Install & try it on a phone

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open Tervyn → *Get started* → tap **Connect** → accept Android's VPN request. The first connection tests the
built-in servers over HTTPS (typically 5–30 s). *Check my IP* shows what websites see.

## Tests

```bash
./gradlew testDebugUnitTest          # JVM: parsers, dialer, TLS checks, DNS, TCP stack, ranking, Xray, UI flows (Robolectric)
```

```bash
./gradlew connectedDebugAndroidTest  # on a device/emulator
```

The JVM suite runs the real engine code: raw IPv4/TCP packets are fed into `TunEngine` and travel through local
SOCKS5/HTTP proxies (with authentication) to real servers; DNS-only mode is exercised the same way; HTTPS verification
is tested against trusted, wrong-host and untrusted certificates; the built-in server table and generated Xray configs
are validated by the real Xray binary; UI flows run on API 26 and 35 via Robolectric and write screenshots to
`app/build/screenshots/` (a full screen gallery is in `app/build/screenshots/gallery/`).

## Architecture

```
app/src/main/java/com/tervyn/vpn/
  vpn/       TervynVpnService (server mode + Secure-DNS-only mode, health/failover), TunEngine (packet dispatch),
             TcpConn (TCP state machine, flow control, retransmission), Packets (IPv4/TCP/UDP/ICMP), VpnController (state)
  net/       ProxyDialer (SOCKS5 / HTTP CONNECT + auth), HttpsOverProxy, ProxyTester (Cloudflare trace),
             FakeDns (198.18.0.0/15), DnsResponder, DohForwarder, IpChecker, SpeedTest
  core/      ServerManager (pool, testing, ranking, selection, built-in repair), BuiltinServers (shipped table),
             BuiltinUpdater (replacement candidates), Ranking (auto-switch policy), Links (share links), Domains, Tuning
  xray/      XrayConfig (config generation), XrayProcess / XrayManager (lifecycle, local SOCKS5 endpoints, batch testing)
  data/ database/ security/   repositories, Room entities/DAOs (+ migrations), Keystore AES-GCM, PIN hashing
  services/ receivers/        notifications, Quick Settings tile, boot receiver
  ui/        theme (tokens, Manrope), components, navigation, screens, MainViewModel
```

How traffic flows: Android routes app traffic into the TUN. DNS queries get synthetic addresses from
`198.18.0.0/15`; when an app opens a TCP connection to one, `TcpConn` completes the handshake locally only after the
proxy connection to the **domain name** succeeded, then relays data with proper windowing. In Secure-DNS-only mode
only `198.18.0.2/32` is routed into the TUN, so the interface carries nothing but DNS. Tervyn's own UID is excluded
from the tunnel, so server tests, list downloads and Xray never loop. See [docs/XRAY.md](docs/XRAY.md) and
[docs/LIMITATIONS.md](docs/LIMITATIONS.md).

## Privacy

No accounts, ads or analytics. Credentials are encrypted with an Android Keystore key and excluded from backups.
Built-in servers are community servers operated by third parties — use HTTPS sites and don't rely on them for banking.
Tervyn does not claim anonymity or verify third-party logging policies.

## Licenses

Tervyn bundles [Xray-core](https://github.com/XTLS/Xray-core) (MPL-2.0, unmodified) as an executable and the
[Manrope](https://github.com/sharanda/manrope) typeface (SIL OFL 1.1). Other dependencies are Apache-2.0.
See *Settings → About → Open-source licences*.
