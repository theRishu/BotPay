#!/bin/bash
set -e

APK_NAME="sms-forwarder.apk"
CHAT_ID="${CHAT_ID:?Set CHAT_ID before running build.sh}"
BOT_TOKEN="${BOT_TOKEN:?Set BOT_TOKEN before running build.sh}"

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
chmod +x gradlew

echo "[1/3] Building release APK..."
./gradlew assembleRelease -q

APK_SRC="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_SRC" ]; then
    echo "ERROR: APK not found at $APK_SRC"
    exit 1
fi

cp "$APK_SRC" "$APK_NAME"
echo "[2/3] APK ready: $APK_NAME ($(du -h "$APK_NAME" | cut -f1))"

echo "[3/3] Sending to Telegram..."
RESULT=$(curl -s -F "chat_id=$CHAT_ID" \
                 -F "document=@$APK_NAME" \
                 -F "caption=SMS Forwarder — $(date '+%d %b %Y %H:%M')" \
                 "https://api.telegram.org/bot$BOT_TOKEN/sendDocument")

if echo "$RESULT" | grep -q '"ok":true'; then
    echo "Sent successfully!"
else
    echo "Send failed: $RESULT"
fi

echo "=== Done ==="
