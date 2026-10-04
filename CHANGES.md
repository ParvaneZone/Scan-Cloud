# Scan-Cloud Xray integration changes

This revision contains only the requested follow-up fixes to the Xray integration, project layout, DNS handling, release acquisition, and documentation.

## Files changed or added

- `app/src/main/java/com/example/cfscanner/MainActivity.kt` — split/reformatted the main Compose screen, keeps startup Xray cleanup in `onCreate`, and retains the DownloadManager update flow with app-specific external Downloads, `getUriForDownloadedFile()`, APK MIME type, and cursor `use {}`.
- `app/src/main/java/com/example/cfscanner/Strings.kt` — extracted and reformatted the bilingual `S` string map.
- `app/src/main/java/com/example/cfscanner/CfTab.kt` — extracted the existing IP-scan/config-test UI without removing behavior.
- `app/src/main/java/com/example/cfscanner/SniTab.kt` — extracted the existing SNI UI without removing behavior.
- `app/src/main/java/com/example/cfscanner/AboutTab.kt` — extracted the existing About UI without removing behavior.
- `app/src/main/java/com/example/cfscanner/Helpers.kt` — shared scan/update/config helpers and the SNI candidate list.
- `app/src/main/java/com/example/cfscanner/Icons.kt` — shared custom Compose icons.
- `app/src/main/java/com/example/cfscanner/xray/DohResolver.kt` — system DNS now runs on a dedicated executor with `Future.get(timeout)` and cancellation/timeout cancellation of the future; DoH still uses normal certificate and hostname verification.
- `app/src/main/java/com/example/cfscanner/xray/XrayRunner.kt` — removed cleanup from `init`; retry is now limited to Xray startup/bind failures and never retries an HTTP/proxy failure after the SOCKS port has opened.
- `app/src/main/java/com/example/cfscanner/xray/SniScanner.kt` — progress uses the distinct-host count and system/DoH address selection prefers IPv4.
- `scripts/fetch_xray.sh` — corrected `.dgst` parsing so it works without awk interval expressions, retained the GitHub `/releases/latest` selection, `XRAY_VERSION` override and `v26.9.9` fallback, and reports clear asset-download failures.
- `README.md` — updated release-selection and checksum documentation.
- `CHANGES.md` — updated to document this revision.

## Files intentionally not included in this ZIP

These repository-owned files are intentionally absent so an in-place replacement does not overwrite the original assets or signing identity:

- `app/src/main/res/drawable-nodpi/logo.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- `app/debug.keystore`

`app/build.gradle.kts` still contains the conditional `if (file("debug.keystore").exists())` signing logic. `arrange.sh` leaves destination copies untouched and can move the original files into their normal locations only when the destination does not already exist.

## Xray version selection and binary acquisition

`scripts/fetch_xray.sh` has one operational version variable, `XRAY_VERSION`. If it is supplied, that exact tag is used. Otherwise the script requests:

`https://api.github.com/repos/XTLS/Xray-core/releases/latest`

from the official GitHub API and takes its `tag_name`. This deliberately uses GitHub's own **Latest** release designation instead of filtering `prerelease=false`, because Xray-core release pages may mark ordinary numbered releases as pre-releases. If the API request fails or does not provide a tag, the script falls back to `v26.9.9`. The selected tag is printed before downloads begin.

For each supported ABI, the script downloads the configured Android archive and matching `.dgst`. The checksum parser matches a line beginning with `SHA2-256` or `SHA256` followed by optional whitespace and `=` or `:`, removes the separator and whitespace, and accepts the value only when it is exactly 64 hexadecimal characters. It deliberately avoids awk interval expressions such as `{64}`, so it works with mawk, gawk and busybox awk. The archive is compared with `sha256sum` before extraction. A failed archive or `.dgst` download prints the asset URL and exits non-zero; this includes a 404.

The extracted executable is installed as `libxray.so` under `app/src/main/jniLibs/<abi>/` and executed directly from `applicationInfo.nativeLibraryDir`; it is not loaded with `System.loadLibrary`.

## `.dgst` parser test

The final parser was run with `mawk 1.3.4` against a sample containing `MD5`, `SHA1`, `SHA2-256`, and `SHA2-512`, plus separate no-space and colon variants. Real test output:

```text
all-four: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
no-space: abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd
colon: fedcbafedcbafedcbafedcbafedcbafedcbafedcbafedcbafedcbafedcbafedc
```

The test used the same awk program now embedded in `scripts/fetch_xray.sh` and did not use interval expressions.

## Build

GitHub Actions runs Java 17, Gradle 8.7, `arrange.sh`, `scripts/fetch_xray.sh`, and `gradle assembleDebug`. The original `logo.png`, `ic_launcher.png`, and `debug.keystore` must already be present in the repository checkout for an in-place build/update workflow.

Local equivalent:

```bash
bash arrange.sh
bash scripts/fetch_xray.sh
gradle assembleDebug --no-daemon
```

## Runtime changes

1. SNI progress now reports against `hosts.distinct().size`, matching the number of actual scan tasks.
2. SNI address selection prefers IPv4 addresses before IPv6 when both are available.
3. System DNS resolution is performed on a separate executor because `withTimeout` cannot interrupt a blocking `InetAddress.getAllByName()` call. The future is cancelled on timeout or coroutine cancellation.
4. Xray port retries happen only before the local SOCKS proxy successfully opens: process-start failure, early process exit, or failure to observe the SOCKS listener. Once the proxy has opened, HTTP failures and timeouts return failure without another Xray attempt.
5. `XrayRunner.cleanupLeftovers()` is called once from `MainActivity.onCreate()` instead of every time an `XrayRunner` instance is constructed. This prevents one runner from deleting another runner's active temporary config.
6. The split Compose files preserve the existing UI, Persian/English strings, RTL behavior, theme switching, scan modes, config testing, SNI scanning, update flow, saving, and About controls.

## Known limitations

- A full Android Gradle build was not available in this execution environment because the Android SDK/dependency cache and external build access required by Gradle were unavailable.
- The actual Xray Android binaries are obtained during CI rather than stored in this ZIP.
- A live future Xray release `.dgst` was not downloaded in this environment; the parser was tested locally with mawk 1.3.4 against the requested separator variants.
- The exact CLI flags and output wording of the `xray tls ping` binary used by CI still require device/binary-level verification. The source integration continues to use `tls ping -ip <resolved-ip> <domain>`.
- DoH currently consumes A records, so IPv6-only domains may be skipped when a DoH provider is selected.
- The official Android artifacts used by this project provide `arm64-v8a` and `x86_64`; no Linux binary is substituted for `armeabi-v7a`.

## Manual test checklist

### Source/build

- [ ] Keep the original `logo.png`, `ic_launcher.png`, and `debug.keystore` in the checkout.
- [ ] Run `bash arrange.sh` and confirm those three files are unchanged.
- [ ] Run `bash scripts/fetch_xray.sh` and confirm the chosen tag is printed.
- [ ] Test the script with a real Xray `.dgst` and confirm the SHA-256 line is parsed.
- [ ] Run `gradle assembleDebug`.
- [ ] Confirm `arm64-v8a` and/or `x86_64` contains `libxray.so` in the APK.

### Xray runner

- [ ] Confirm a startup/bind failure causes a new local port attempt.
- [ ] Confirm an HTTP connection timeout after the SOCKS port opens does not trigger a second Xray attempt.
- [ ] Cancel during Xray startup, HTTP, and speed measurement and confirm no Xray process remains.
- [ ] Start two concurrent scans and confirm one runner cannot delete the other's temporary config.

### SNI/DNS

- [ ] Scan duplicate SNI inputs and confirm progress total equals the distinct-host count.
- [ ] Use system DNS for a hostname with both A and AAAA records and confirm IPv4 is preferred.
- [ ] Test Cloudflare and Google DoH.
- [ ] Cancel during system DNS resolution and confirm the resolver task is cancelled.
- [ ] Verify TLS version display and ALPN/h2 behavior on API 24–28 and API 29+.

### Updater

- [ ] Confirm the APK is downloaded to the app-specific external Downloads directory.
- [ ] Confirm installation uses the `content://` URI returned by DownloadManager and never a `file://` URI.
- [ ] Verify in-place updates with the original `debug.keystore`.

## IPv6 / extra ranges / SNI update

- `Helpers.kt`: added Cloudflare IPv6 ranges (7), Fastly IPv6 ranges (2) and two extra Fastly blocks (`87.81.224.0/19`, `8.18.217.0/24`); `liveRanges` now also reads `ips-v6` and `ipv6_addresses`; `randomIps` supports IPv6; IPv6 hosts are bracketed in generated vless/trojan/ss configs; 46 new SNI domains.
- `CfTab.kt` / `Strings.kt`: new "Include IPv6" checkbox (off by default).
- Not built or run here (no Android SDK); please build via CI and test on a device.
