# BotPay

BotPay is an Android SMS-to-Telegram payment logger. After the user opens the app and grants SMS permission once, the app stores that consent, keeps a foreground service active, and forwards matching bank payment SMS messages to Telegram.

## What Happens

1. The user installs the app and opens it once.
2. Android asks for SMS and notification permissions.
3. When SMS permission is granted, BotPay saves `forwarding_enabled=true`.
4. `SMSReceiver` wakes up for incoming SMS messages.
5. The message is ignored unless:
   - forwarding was enabled by the user,
   - the sender matches the sender regex,
   - the SMS body matches the body regex,
   - the UTR has not already been logged.
6. Matching messages are sent to Telegram with the configured bot token and chat ID.
7. A local message log powers the Today, Week, Summary, and History screens.

## Default Filters

The defaults live in `app/src/main/java/com/qwe7002/telegram_sms/config/Config.kt`.

```kotlin
const val DEFAULT_SENDER_REGEX = ".*546.*"
const val DEFAULT_BODY_REGEX = ".*(credited|debited).*"
```

This means the default setup forwards only SMS messages from sender IDs containing `546` where the message text contains `credited` or `debited`.

You can change these inside the app from Settings:

- Sender Regex: matches the SMS sender ID.
- Body Regex: matches the SMS text.

Examples:

```text
Sender Regex: .*(546|HDFC|SBI).*
Body Regex: .*(credited|debited|received|paid).*
```

## Telegram Config

The checked-in source uses fake placeholders:

```kotlin
private const val DEFAULT_BOT_TOKEN = "0000000000:FAKE_BOT_TOKEN_REPLACE_ME"
private const val DEFAULT_CHAT_ID = "-1000000000000"
```

Set the real bot token and chat/channel ID in the app Settings screen after installing.

For `build.sh`, pass the values as environment variables:

```bash
CHAT_ID="-1001234567890" BOT_TOKEN="123456:ABC..." bash build.sh
```

## Build

Debug APK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release APK and Telegram upload:

```bash
CHAT_ID="..." BOT_TOKEN="..." bash build.sh
```

## Files

- `SMSReceiver.kt`: SMS filter, duplicate check, Telegram forwarding.
- `Config.kt`: fake defaults and regex filter helpers.
- `MainActivity.kt`: dashboard and permission flow.
- `SettingsActivity.kt`: bot config, regex filters, manual insert, dump controls.
- `MessageLog.kt`: local message history and summaries.
- `build.sh`: release build and APK upload helper.

## Notes

- APKs, Gradle caches, local Android config, and signing keys are ignored by git.
- `app/keys.jks` is intentionally not committed.
- Keep real bot tokens out of git.
