# Known limitations (by design or platform)

**UDP.** HTTP CONNECT and SOCKS5 CONNECT tunnels carry TCP only, and Xray endpoints are used the same
way. Tervyn therefore refuses non-DNS UDP with ICMP port-unreachable instead of leaking it: QUIC/HTTP3
falls back to TCP automatically, but UDP-only apps (some games, VoIP media) will not work through the
VPN. DNS (UDP/53) is answered locally. UDP relaying for Xray servers (SOCKS5 UDP ASSOCIATE) is not
implemented yet.

**IPv6.** Not tunnelled. In All Apps mode IPv6 is routed into the TUN and dropped (no leak); apps use IPv4.
Only My Sites returns no AAAA records for selected sites.

**Only My Sites** recognises sites by DNS name:
* Android *Private DNS* set to a specific provider bypasses Tervyn's resolver, so sites are not detected
  (All Apps mode is unaffected). Use Automatic or Off; diagnostics warn about it.
* Apps that connect to hard-coded IP addresses are only covered when a preset lists their IP ranges
  (Telegram does). Already-open connections and app DNS caches may need an app restart / page reload.
* App-level allow-listing on Android cannot select individual websites; this is why the DNS approach is used.

**Free public proxies** are short-lived and run by unknown operators. Every server is verified with a full
HTTPS test, but operators can see destination host names and unencrypted traffic. Countries are shown
only after Cloudflare geolocates the verified exit IP. Server *load* is not reported by these servers and
is shown as unavailable rather than estimated.

**Proxio.io**: the endpoint, `X-Key` header and `type`/`limit` parameters are implemented as specified; the
response body format is not documented publicly, so it is parsed defensively (text lines or common JSON
shapes). If Proxio returns a different shape, adjust `ProxioClient.parse`.

**Kill switch / always-on**: Android only lets the user enable these in system settings; Tervyn links there
and supports always-on starts (it keeps retrying while always-on is active). Apps on the exclusion list
lose network access when "Block connections without VPN" is on.

**Speed test** uses Cloudflare's speed endpoints; latency is HTTPS round-trip time and "request loss" is the
share of failed probe requests (ICMP is not available to apps), labelled as such in the UI.

**Updates**: there is no update server; "Rate our app" opens the store listing when installed from a store.

**Not verified in this environment**: running on a physical phone/emulator (no Android emulator or device is
available in the build sandbox) and connecting to real free proxies (the sandbox only allows specific hosts).
The engine, TCP stack, DNS, proxies, TLS checks and Xray are covered by JVM tests that run the real code;
the on-device tests in `app/src/androidTest` must be run on hardware (see README).
