#!/bin/sh
# PocketDoor Gradle bootstrapper for macOS/Linux.
# Downloads Gradle 9.5.1 on first use and reuses it locally.
set -eu
BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
VERSION="9.5.1"
LOCAL_DIR="$BASE_DIR/.gradle-local/gradle-$VERSION"
ZIP="$BASE_DIR/.gradle-local/gradle-$VERSION-bin.zip"
GRADLE="$LOCAL_DIR/bin/gradle"

if [ ! -x "$GRADLE" ]; then
  mkdir -p "$BASE_DIR/.gradle-local"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $VERSION..."
    curl -L --fail --output "$ZIP" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  fi
  echo "Extracting Gradle $VERSION..."
  unzip -oq "$ZIP" -d "$BASE_DIR/.gradle-local"
  rm -f "$ZIP"
fi

exec "$GRADLE" "$@"
