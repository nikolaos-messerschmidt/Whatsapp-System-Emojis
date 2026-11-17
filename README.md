# Wasysmoji

LSPosed module that makes WhatsApp render chat emojis with Android's system emoji font instead of WhatsApp's bundled emoji graphics.

## Features

- System-rendered emojis in normal WhatsApp messages
- Emoji sequences, skin tones, and emoji mixed with text
- Static system rendering for animated single-emoji messages
- Special handling for WhatsApp's large single-emoji message view

## Build

```bash
./gradlew :app:assembleRelease
```

The unsigned APK is generated at:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Sign the APK with your own keystore before installing it.

## Install

1. Install the signed APK.
2. Enable **Wasysmoji** in LSPosed.
3. Scope the module to WhatsApp.
4. Force-stop and reopen WhatsApp.

## Compatibility

The module targets the WhatsApp build whose obfuscated classes are currently configured in `Hook.kt`. WhatsApp updates may rename those classes; update the constants and test again if the hooks stop loading.

This project is not affiliated with WhatsApp, Meta, Android, or LSPosed.
