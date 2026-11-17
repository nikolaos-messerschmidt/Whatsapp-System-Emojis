# WA-sysmoji


# WARNING: (YOU NEED ROOT TO USE THIS☹️☹️☹️) #

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

## How does this work?

The module targets the WhatsApp build whose obfuscated classes are currently configured in `Hook.kt`. WhatsApp updates may rename those classes.
I'll update this in case it breaks again.


## Tested on: 
- Google Pixel 10 (Unlocked Bootloader)
- Android 17 (CP4A.261105.040.A)
- Magisk 31.1
- Whatsapp 2.26.44.90 (At the time newest version)

## Showcase:

<table>
  <tr>
    <td align="center">
      <b>WhatsApp Base (No module)</b><br>
      <img width="286" src="https://github.com/user-attachments/assets/51b5bd37-e60a-4ff3-a941-d28898f6fc47" />
    </td>
    <td align="center">
      <b>Noto Emojis</b><br>
      <img width="286" src="https://github.com/user-attachments/assets/acb1055b-7e3d-44c9-91e5-0c4667b81709" />
    </td>
    <td align="center">
      <b>Blobmojis (Best Emojis of all time)</b><br>
      <img width="286" src="https://github.com/user-attachments/assets/8dcbdbe6-c382-45f0-84bf-0cd57f43dc45" />
    </td>
  </tr>
</table>


Please don't sue me Meta this does not unlock any paid features ☹️☹️☹️ (This just gets rid of your Shitty iOS-like knockoff emojis)
This project is not affiliated with WhatsApp, Meta, Android, or LSPosed.
