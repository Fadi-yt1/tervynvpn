# Xray-core integration

* Version: **v26.9.30** (tag commit `b26a91de4f3294e26a0ad0a970b81a386a41f789`), official source https://github.com/XTLS/Xray-core, license MPL-2.0 (full text bundled in `app/src/main/assets/licenses`).
* Packaged as an executable named `libxray.so` per ABI under `xray/android/<abi>/` so Android extracts it to
  `nativeLibraryDir` (the only place app executables may run from on Android 10+). `useLegacyPackaging = true`
  in Gradle ensures extraction.
* ABIs: `arm64-v8a` (GOOS=android), `armeabi-v7a`, `x86_64`, `x86` (static GOOS=linux binaries, which run on Android).
* `SHA256SUMS` records the binaries built from source with `scripts/build-xray.sh` (Go 1.27.0,
  `-trimpath -buildvcs=false -ldflags "-checklinkname=0 -s -w -buildid="`, matching the upstream release flags);
  Gradle's `verifyXray` task checks them on every build.
* Why a source build is supported: the official Android release assets are built with cgo + NDK. Building
  from source is reproducible with only Go. Either way the binary is unmodified upstream code.
  `scripts/fetch-xray.sh` installs the official prebuilt archives instead (verified against their `.dgst`).

## Runtime model
For each share-link server Tervyn writes a config (`xray/XrayConfig.kt`) with one inbound:
SOCKS5 on `127.0.0.1:<random port>`, `auth: password` with a random 8+24 character user/password, UDP off.
The config file is deleted as soon as Xray is listening. The VPN engine treats that endpoint like any
SOCKS5 proxy. Server host names are resolved by Xray's own DoH client (`1.1.1.1`, `8.8.8.8`) with
`sockopt.domainStrategy = UseIP`, so the static binaries don't depend on the platform resolver.
Tervyn's own UID is excluded from the tunnel, so Xray's connections never loop back into the VPN.

Testing uses temporary Xray instances (max 4 at once) that are stopped after the HTTPS trace test.

## Security
`allowInsecure` no longer exists in Xray-core v26; links that request it are flagged and the flag is ignored.
Certificate pinning is supported through `pcs` (`pinnedPeerCertSha256`) and `vcn` (`verifyPeerCertByName`).
Unencrypted VLESS to public addresses is rejected at import (Xray-core refuses it too).

## Tests
`XrayConfigTest` validates every generated config with the real binary (`xray run -test`), and
`XrayIntegrationTest` performs real VLESS and Shadowsocks round trips through a loopback Xray server and
Tervyn's authenticated local SOCKS5 endpoint.
