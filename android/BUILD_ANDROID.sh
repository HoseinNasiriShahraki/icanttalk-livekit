#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

GRADLE_VERSION="8.13"
TOOLS_DIR="$PWD/.tools"
GRADLE_HOME="$TOOLS_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
ZIP_PATH="$TOOLS_DIR/gradle-$GRADLE_VERSION-bin.zip"

command -v java >/dev/null || { echo "Java/JDK 17 or newer is required." >&2; exit 1; }

if [[ ! -x "$GRADLE_BIN" ]]; then
  mkdir -p "$TOOLS_DIR"
  if [[ ! -f "$ZIP_PATH" ]]; then
    echo "Downloading Gradle $GRADLE_VERSION..."
    curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP_PATH"
  fi
  unzip -oq "$ZIP_PATH" -d "$TOOLS_DIR"
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "Warning: ANDROID_HOME is not set. Open the project once in Android Studio or export your SDK path." >&2
fi

"$GRADLE_BIN" --no-daemon clean :app:assembleDebug
APK="$PWD/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 1; }
echo "Build complete."
echo "APK: $APK"
