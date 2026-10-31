# TV Messenger: design

Date: 2026-10-31. Target: one TV, an Artel ATV R1 (SCBC R1, Realtek), Android TV 12 (API 31), 1.5 GB RAM,
1280x720 UI on a 1080p panel.

## Why

The owner is often away, sometimes in another city, and wants to reach the family at home through the TV:
"dinner's ready", "pick up your phone", or a voice note calling someone by name. It is a doorbell / intercom,
not a chat. The family reacts by calling back on the phone. It must be noticed by whoever is in the room,
whatever is on screen.

## What it does

- The owner sends a **text** or a **voice message** to a private Telegram bot (@home_tv_26_bot) from any
  phone or computer, anywhere. No ports are opened at home; the TV fetches messages from Telegram.
- **Text**: a short chime, then a large text box over whatever is on screen (Home, YouTube, Frame, Live TV,
  HDMI). It stays at least **15 s**, longer for longer text, at most **60 s**, then disappears by itself.
- **Voice**: a short chime, then the voice note plays through the TV speakers, with the **show's sound
  lowered** while it plays and restored afterwards.
- The bot answers the owner in Telegram: "✓ Shown on TV" (text, once it is on screen) or "✓ Played on TV"
  (voice, once playback finished). No tick means the TV did not get it.
- Only the owner's Telegram account is accepted.

## Goals, non-goals, anti-goals

- Goals: reliable delivery while the TV is on; noticeable; readable from across the room; works with ADB
  switched off; starts by itself after every boot; light on a 1.5 GB TV and on mobile data.
- Non-goals: replies from the TV, other senders, history, photos/video/stickers, a settings UI, Play Store.
- Anti-goals (a failure even if it works): stealing the remote (keys must keep going to the show); a message
  from an hour ago popping up when the TV is switched on; anyone but the owner being able to put text or
  sound on the TV; noticeable slowdown of the TV; becoming part of the cleanyourtv repo or its launcher.

## Decisions (owner's)

- Text box in the **center** of the screen (changed from top-right after the first test on the TV).
- One-way only. Only the owner can send. Text minimum 15 s, scaling with length up to 1 minute.
  Show's volume lowered while the voice plays. The TV's own volume is respected: nothing is unmuted or
  raised, so on a muted TV a voice message is silent and only its on-screen notice is seen.

## Decisions (my calls; the owner can override)

- **Where**: its own project at `~/personal-projects/tv-messenger`, own git repo, outside cleanyourtv
  (that repo forbids network access in its apps).
- **Stack**: plain Java Android app like the launcher; no third-party runtime libraries. `HttpURLConnection`
  + `org.json` for the Telegram Bot API, `MediaPlayer` for voice (Telegram voice is OGG/Opus), a
  `TYPE_APPLICATION_OVERLAY` window for text. minSdk 26, compile/target SDK 34, JDK 17, built on the Mac
  with Gradle (same toolchain as the launcher). Test-only dependency: JUnit 4 and `org.json:json` for JVM tests.
- **Secrets**: the bot token and the owner's numeric Telegram user id come from `secrets.properties`
  (gitignored, mode 600) and are compiled into `BuildConfig`. Changing either means rebuild + reinstall.
  The owner id is read once from the Mac with `getUpdates` after the owner messages the bot.
- **Receiving**: a foreground service long-polls `getUpdates` (timeout 50 s, `allowed_updates=["message"]`),
  acknowledging with `offset`. Network errors: retry with backoff 5 s → 60 s. Started on `BOOT_COMPLETED`
  and when the app is opened. Nothing else may poll the bot once the TV app runs (Telegram allows one consumer).
- **Filtering**: messages whose `from.id` is not the owner are dropped silently, no reply.
  Messages older than **2 minutes** when fetched (TV was off or offline) are not shown; the bot replies
  "Not shown: the TV was off when this arrived". Other message types get "Only text and voice messages work".
  `/start` from the owner gets a one-line help reply.
- **Queue**: one item at a time, in order. A new message while one is showing waits its turn.
- **Text box**: centered, about 60% of the screen width, text centered, dark translucent rounded box, white text,
  font size shrinks with length (large for a few words). Text longer than about 600 characters is cut with "…".
  Window flags: not focusable, not touchable, so the remote keeps controlling the show.
  Duration: `clamp(15 s, 5 s + characters / 12 per second, 60 s)`.
- **Voice notice**: while a voice note plays, the same text box shows "🎤 Voice message" with its length,
  so a muted room still notices.
- **Package**: `com.tvmessenger`, app name "TV Messenger".
- **Chime**: two short tones generated in code (no audio asset), on the media stream.
- **Ducking**: request transient audio focus "may duck" while the chime + voice play, release afterwards.
  Android lowers other apps' sound for that time.
- **Status tile**: one small activity with a Leanback launcher entry ("TV Messenger") showing Connected /
  Not connected and the time of the last message. Opening it also (re)starts the service. It can be hidden
  from the launcher grid with the launcher's `hidden_apps` if the owner prefers.
- **Permissions**: `INTERNET`, `FOREGROUND_SERVICE` (+ type for Android 14 targets), `RECEIVE_BOOT_COMPLETED`,
  `SYSTEM_ALERT_WINDOW` (granted once over ADB: `appops set <pkg> SYSTEM_ALERT_WINDOW allow`), `WAKE_LOCK`.
- **Install**: over ADB while it is still on, then open the app once (an installed app gets no boot broadcast
  until it has been started once). Then ADB is switched off for good.

## Known risks the builder must check on the real TV

1. **Live TV / HDMI sound may not duck.** On TV chips the tuner and HDMI audio often bypass Android's mixer,
   and on Android TV every audio stream shares one volume, so lowering the show without lowering the voice
   may be impossible there. Verify with YouTube, Frame and Live TV; report what works rather than force it.
2. **Screensaver.** The overlay may draw under the Clock screensaver. Check; if hidden, find a way to end
   the screensaver when a message arrives, or report it.
3. **Opus playback** through `MediaPlayer` on this firmware: verify a real Telegram voice note plays.
4. Overlay above Live TV and HDMI video surfaces: verify (screenshots of Live TV come back empty on this
   TV, so the owner looks at the screen).

## Done means

On the real TV, with ADB still on for testing: a text from the owner shows over Home, YouTube and Live TV
and leaves on time; a long text stays longer, at most 60 s; a voice note plays with the show quieter where
the TV allows; the ticks arrive in Telegram; a message from another account does nothing; a message sent
while the TV was off is not shown later and gets the "not shown" reply; after a reboot it works without
opening the app; the remote keeps working while a message is on screen. Then ADB is switched off and port
5555 checked closed with the cleanyourtv tool.
