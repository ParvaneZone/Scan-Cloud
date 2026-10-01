#!/usr/bin/env bash
set -euo pipefail

# The repository is kept in normal Android/Gradle layout. This script also accepts
# the original flat layout used by the first Scan-Cloud GitHub Actions workflow.
# Existing repository assets and debug.keystore are intentionally preserved.
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

mkdir -p app/src/main/java/com/example/cfscanner/xray
mkdir -p app/src/main/res/mipmap-xxxhdpi app/src/main/res/drawable-nodpi
mkdir -p app/src/main/jniLibs

if [[ -f MainActivity.kt ]]; then mv MainActivity.kt app/src/main/java/com/example/cfscanner/MainActivity.kt; fi
if [[ -f ConfigParser.kt ]]; then mv ConfigParser.kt app/src/main/java/com/example/cfscanner/xray/ConfigParser.kt; fi
if [[ -f XrayConfigBuilder.kt ]]; then mv XrayConfigBuilder.kt app/src/main/java/com/example/cfscanner/xray/XrayConfigBuilder.kt; fi
if [[ -f XrayRunner.kt ]]; then mv XrayRunner.kt app/src/main/java/com/example/cfscanner/xray/XrayRunner.kt; fi
if [[ -f SniScanner.kt ]]; then mv SniScanner.kt app/src/main/java/com/example/cfscanner/xray/SniScanner.kt; fi
if [[ -f DohResolver.kt ]]; then mv DohResolver.kt app/src/main/java/com/example/cfscanner/xray/DohResolver.kt; fi
if [[ -f AndroidManifest.xml ]]; then mv AndroidManifest.xml app/src/main/AndroidManifest.xml; fi
if [[ -f app-build.gradle.kts ]]; then mv app-build.gradle.kts app/build.gradle.kts; fi

# These files belong to the repository and must not be replaced by CI arrangement.
# If they already exist, leave them untouched.
if [[ -f ic_launcher.png && ! -f app/src/main/res/mipmap-xxxhdpi/ic_launcher.png ]]; then
  mv ic_launcher.png app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
fi
if [[ -f logo.png && ! -f app/src/main/res/drawable-nodpi/logo.png ]]; then
  mv logo.png app/src/main/res/drawable-nodpi/logo.png
fi
if [[ -f debug.keystore && ! -f app/debug.keystore ]]; then
  mv debug.keystore app/debug.keystore
fi

chmod -R u+rwX app/src/main/jniLibs 2>/dev/null || true
