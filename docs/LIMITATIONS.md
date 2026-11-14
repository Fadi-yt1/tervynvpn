# Known limitations (by design or platform)

**UDP.** HTTP CONNECT and SOCKS5 CONNECT tunnels carry TCP only, and Xray endpoints are used the same
way. Tervyn therefore refuses non-DNS UDP with ICMP port-unreachable instead of leaking it: QUIC/HTTP3
falls back to TCP automatically, but UDP-only apps (some games, VoIP media) will not work through the
VPN. DNS (UDP/53) is answered locally. UDP relaying for Xray servers (SOCKS5 UDP ASSOCIATE) is not implemented.

**IPv6.** Not tunnelled. In All apps mode IPv6 is routed into the TUN and dropped (no leak); apps use IPv4.
Only my sites returns no AAAA records for selected sites.

**Secure DNS** (DNS-only mode) encrypts lookups; it does not hide your IP address or encrypt other traffic.
* It needs the same VPN consent as a server connection, because Android only lets a VPN replace the DNS servers of
  all apps. The persistent status notification is required while it runs.
* Android *Private DNS* set to a specific provider (DNS-over-TLS) bypasses Tervyn's resolver. Use Automatic or Off;
  the DNS screen and Diagnostics warn about it.
* It is paused while a server is connected: the server resolves names then. This is deliberate — the two features are
  independent and never run at the same time.

**Only my sites** recognises sites by DNS name:
* Android *Private DNS* set to a specific provider bypasses Tervyn's resolver, so sites are not detected
  (All apps mode is unaffected).
* Apps that connect to hard-coded IP addresses are only covered when a preset lists their IP ranges
  (Telegram does). Already-open connections and app DNS caches may need an app restart / page reload.

**Built-in servers** are free community servers (from the FlareFeed lists) run by unknown operators.
* Every server is verified with a full HTTPS test before it is shown as working; a slot that stays down is replaced
  from the live lists by a verified server of the same country. Availability can still change between checks.
* Operators can see destination host names and unencrypted traffic. Use HTTPS websites.
* The listed country is what the provider announces; the *verified* country comes from the exit IP (Cloudflare `loc`)
  and is shown separately when they differ.
* Server *load* is not reported by these servers and is not shown.

**Kill switch / always-on**: Android only lets the user enable these in system settings; Tervyn links there
and supports always-on starts (it keeps retrying while always-on is active). Apps on the exclusion list
lose network access when "Block connections without VPN" is on.

**Speed test** uses Cloudflare's speed endpoints; latency is HTTPS round-trip time and "loss" is the share of failed
probe requests (ICMP is not available to apps), labelled as such in the UI.

**Not verified in this environment**: running on a physical phone/emulator and connecting to the real built-in
servers (the build sandbox has no Android device and only reaches specific hosts). The engine, TCP stack, DNS modes,
proxies, TLS checks and Xray configuration are covered by JVM tests that run the real code; the on-device tests in
`app/src/androidTest` must be run on hardware.
