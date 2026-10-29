#!/bin/zsh
# Builds the APK and copies it to dist/ next to this script.
# With keystore.properties: release-signed build -> dist/BooxQR-<versionName>.apk
# Without it: debug build -> dist/BooxQR-<versionName>-debug.apk
# Set JAVA_HOME (JDK 17 or newer) and ANDROID_HOME (Android SDK) if they are not already set.
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

DEFAULT_JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
DEFAULT_ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"

if [[ -z "$JAVA_HOME" && -x "$DEFAULT_JAVA_HOME/bin/java" ]]; then
  JAVA_HOME="$DEFAULT_JAVA_HOME"
fi
if [[ -z "$JAVA_HOME" || ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "JAVA_HOME is not set to a Java install. Set JAVA_HOME to your JDK folder and run this again." >&2
  exit 1
fi

if [[ -z "$ANDROID_HOME" && -d "$DEFAULT_ANDROID_HOME" ]]; then
  ANDROID_HOME="$DEFAULT_ANDROID_HOME"
fi
if [[ -z "$ANDROID_HOME" || ! -d "$ANDROID_HOME" ]]; then
  echo "ANDROID_HOME is not set to an Android SDK folder. Set ANDROID_HOME to your SDK folder and run this again." >&2
  exit 1
fi

export JAVA_HOME ANDROID_HOME
export PATH="$JAVA_HOME/bin:$PATH"

VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)

if [[ -f keystore.properties ]]; then
  ./gradlew --no-daemon assembleRelease testDebugUnitTest
  SRC="app/build/outputs/apk/release/app-release.apk"
  DEST="$SCRIPT_DIR/dist/BooxQR-${VERSION}.apk"
else
  echo "No keystore found, so a debug build was made."
  ./gradlew --no-daemon assembleDebug testDebugUnitTest
  SRC="app/build/outputs/apk/debug/app-debug.apk"
  DEST="$SCRIPT_DIR/dist/BooxQR-${VERSION}-debug.apk"
fi

mkdir -p "$SCRIPT_DIR/dist"
cp "$SRC" "$DEST"
echo "APK: $DEST"
echo "Size: $(du -h "$DEST" | cut -f1)"
