# Building and signing releases

Package ID: `com.tervyn.vpn` · versionName/versionCode live in `app/build.gradle.kts` (`defaultConfig`).
Bump `versionCode` for every store upload.

## 1. Prerequisites
- JDK 21, Android SDK with platform 37 (`ANDROID_HOME` or `local.properties` → `sdk.dir`).
- Xray-core binaries in `xray/android/<abi>/libxray.so` — run **one** of:
  - `scripts/build-xray.sh` (builds the v26.9.30 tag from source with Go; verifies against the committed `xray/android/SHA256SUMS`), or
  - `scripts/fetch-xray.sh` (downloads the official release archives and verifies their SHA-256 `.dgst`).
  Every build runs `verifyXray`, which fails on any checksum mismatch.

## 2. Create an upload/signing key (once)
```bash
keytool -genkeypair -v -keystore release.jks -alias tervyn -keyalg RSA -keysize 4096 -validity 10000
```
Copy `keystore.properties.example` → `keystore.properties` and fill in the values.
Both `*.jks` and `keystore.properties` are git-ignored — keep backups somewhere safe. **Every future update must be
signed with the same key**, or Android refuses to install it over the existing app.

GitHub Actions signs automatically when these repository secrets exist: `KEYSTORE_BASE64` (`base64 -w0 release.jks`),
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The workflow writes `keystore.properties` only for the duration of the
build and deletes it afterwards.

## 3. Build
```bash
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease               # app/build/outputs/apk/release/app-release.apk (R8 + resource shrinking)
./gradlew bundleRelease                 # app/build/outputs/bundle/release/app-release.aab (Play Store)
./gradlew assembleRelease -PsplitApks   # per-ABI APKs (~15 MB each) + a universal APK
```
Without `keystore.properties` the release APK/AAB is produced **unsigned** (`*-unsigned.apk`); sign it with
`apksigner` or add the properties file.

## 4. Play Store notes
- Upload the AAB; Play delivers only the matching ABI's `libxray.so` to each device.
- The VPN service uses `foregroundServiceType="specialUse"` (subtype `vpn`); the Play Console asks for a
  justification of the foreground service and the VpnService declaration form.
- Privacy text is in the app (Settings → About → Privacy); host a copy for the listing.
- Open-source notices (Xray-core MPL-2.0, Manrope OFL-1.1) are in the app under About → Open-source licences.
- When the built-in server table (`core/BuiltinServers.kt`) changes, bump `BuiltinServers.VERSION` so installed apps
  pick up the new links; `BuiltinServersTest` validates the table against the real Xray binary.
