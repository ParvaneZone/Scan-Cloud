Xray-core binaries are downloaded from the official XTLS/Xray-core release during GitHub Actions by `scripts/fetch_xray.sh`.

The workflow verifies the matching `.dgst` SHA-256 value and places the Android executable at:

- `arm64-v8a/libxray.so`
- `x86_64/libxray.so`

`libxray.so` is intentionally executed directly with `ProcessBuilder`; it is not loaded with `System.loadLibrary`.
