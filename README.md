# TV Messenger

Send a text, a voice note or a photo to a private Telegram bot from anywhere, and it appears on the family TV
at home: a soft chime, then the message in the middle of the screen over whatever is playing. Made for one
Artel ATV R1 (Android TV 12, 1.5 GB RAM); should work on any Android TV 8.0 or newer.

- **Text**: shown as typed (line breaks and spacing kept) for 15 s, longer for long texts, at most 60 s.
- **Voice**: plays through the TV speakers with a "🎤 Voice message" notice; other apps' sound is lowered
  meanwhile. The TV's own volume is respected: a muted TV stays muted.
- **Photo**: shown for 10 s with its caption under it (a long caption adds reading time).
- The bot answers "✓ Shown on TV" / "✓ Played on TV". No tick: the TV was off or offline.
- A message that arrived while the TV was off is never shown later; the bot says "Not shown: the TV was off".
- Only the owner's Telegram account is accepted. Anything else is ignored without a reply.
- It starts by itself after every boot. No ports are opened at home: the TV asks Telegram for new messages.
- Each message is dropped from memory and its file deleted as soon as it closes; no message content is
  stored on the TV. About 11 MB of memory while waiting.

Design: [docs/design.md](docs/design.md). Plan: [docs/superpowers/plans/](docs/superpowers/plans/).

## Build

Needs JDK 17 and Android SDK platform 34 with build-tools 34.0.0. Create `secrets.properties` next to this file
(it is gitignored, never commit it):

```properties
bot_token=123456789:AA...        # from @BotFather
owner_id=123456789               # your numeric Telegram user id
```

To find `owner_id`: send your bot any message, then open
`https://api.telegram.org/bot<token>/getUpdates` and read `message.from.id`. Do this before installing the app:
once it runs on the TV, it is the only reader of the bot's messages.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest assembleDebug
```

## Install (needs ADB on the TV once)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.tvmessenger SYSTEM_ALERT_WINDOW allow   # lets it draw over other apps
adb shell am start -n com.tvmessenger/.StatusActivity             # first start; after that it starts at boot
```

The "TV Messenger" tile shows whether it is connected and when the last message came. ADB can be switched off
afterwards; the app does not need it. To change the bot or the owner: edit `secrets.properties`, rebuild,
reinstall (ADB on for that). To remove it: `adb uninstall com.tvmessenger`.

## Known limits

- Lowering the show's sound uses Android audio focus. Streaming apps (YouTube) duck; Live TV and HDMI sound on
  TV chips may bypass Android's mixer and not get quieter. Not separately confirmed on the Artel yet.
- Whether a message shows on top of the screensaver was not checked.
- Videos, stickers and photos sent as files are not supported; the bot says so.
