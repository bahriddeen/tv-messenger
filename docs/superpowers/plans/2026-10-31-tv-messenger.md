# TV Messenger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An Android TV app that shows the owner's Telegram texts over the screen and plays their voice notes, with ticks back to Telegram.

**Architecture:** Pure-Java logic (`Rules`, `Updates`, `Router`) decides everything and is JVM-tested. Thin Android
classes do I/O: `TelegramApi` (HTTP), `MessengerService` (foreground long-poll loop + one-at-a-time queue),
`Overlay` (window), `Chime` and `VoicePlayer` (audio, focus ducking), `BootReceiver`, `StatusActivity`.

**Tech Stack:** Java 17 source, Android SDK 34, AGP 8.5.2, Gradle 8.9 wrapper; JUnit 4.13.2 + `org.json:json:20240303` (tests only).

**Spec:** `docs/design.md`

## Global Constraints

- Package `com.tvmessenger`, app name "TV Messenger". minSdk 26, compileSdk 34, targetSdk 34.
- No third-party runtime dependencies. Permissions only: `INTERNET`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`, `WAKE_LOCK`.
- `bot_token` and `owner_id` read from `secrets.properties` (gitignored) into `BuildConfig.BOT_TOKEN` (String)
  and `BuildConfig.OWNER_ID` (long). The token never appears in logs, commits or screenshots.
- Text duration `clamp(15 000 ms, 5 000 + codePoints × 1000 / 12, 60 000 ms)`; text cut at 600 code points + "…".
- Stale: message older than 120 s when fetched. Long-poll timeout 50 s, `allowed_updates=["message"]`.
  Backoff 5 s doubling to 60 s.
- Bot replies, exact copy: `✓ Shown on TV`, `✓ Played on TV`, `Not shown: the TV was off when this arrived`,
  `Only text and voice messages work`, help: `Send a text or a voice message and it appears on the TV.`
- Voice notice text: `🎤 Voice message (Ns)`. Overlay: not focusable, not touchable.
- Never raise or unmute the TV volume.

## Review Focus

1. A 4096-character text → shown cut to 600 + "…", for 60 s, never longer. (Task 1 test)
2. Cyrillic and emoji are counted as characters, not UTF-16 units: "Ужин готов 🍲" → 15 s. (Task 1 test)
3. Telegram answers `{"ok":false}` or broken JSON → no crash, no items, offset unchanged. (Task 2 test)
4. Updates the bot doesn't handle (no `message`, or a message with no `from`) still advance the offset,
   so they never loop forever. (Task 2 test)
5. TV clock slightly ahead or behind: a message dated 30 s in the future is not stale. (Task 1 test)

---

### Task 1: Project scaffold and `Rules`

**Files:**
- Create: `settings.gradle`, `build.gradle`, `gradle.properties` (copy the launcher's, `rootProject.name = "tvmessenger"`)
- Create: `gradlew`, `gradle/wrapper/*` (copy from `cleanyourtv/launcher`)
- Create: `app/build.gradle` (namespace/appId `com.tvmessenger`, versionCode 1, versionName "1.0", Java 17,
  `buildConfigField` from `secrets.properties`, build fails with a clear message if the file or a key is missing)
- Create: `app/src/main/AndroidManifest.xml` (application only, for now)
- Create: `app/src/main/java/com/tvmessenger/Rules.java`
- Test: `app/src/test/java/com/tvmessenger/RulesTest.java`

**Interfaces:**
- Produces: `Rules.displayMillis(String text) -> long`; `Rules.shorten(String text) -> String`;
  `Rules.isStale(long messageDateSec, long nowMillis) -> boolean`;
  `Rules.nextBackoffMillis(long currentMillis) -> long` (0 → 5000, then ×2, max 60 000).

- [ ] **Step 1: Write the failing tests** in `RulesTest`:

```java
@Test public void shortTextGetsMinimum() { assertEquals(15_000, Rules.displayMillis("Dinner!")); }
@Test public void cyrillicAndEmojiCountAsCharacters() { assertEquals(15_000, Rules.displayMillis("Ужин готов 🍲")); }
@Test public void mediumTextScales() { assertEquals(30_000, Rules.displayMillis("a".repeat(300))); }
@Test public void longTextCapsAtOneMinute() { assertEquals(60_000, Rules.displayMillis("a".repeat(4096))); }
@Test public void shortenCutsAt600() { String s = Rules.shorten("a".repeat(4096)); assertEquals(601, s.codePointCount(0, s.length())); assertTrue(s.endsWith("…")); }
@Test public void shortenKeepsShortText() { assertEquals("hi", Rules.shorten("hi")); }
@Test public void freshAt119s() { assertFalse(Rules.isStale(1_000, 1_119_000)); }
@Test public void staleAt121s() { assertTrue(Rules.isStale(1_000, 1_121_000)); }
@Test public void futureDateIsNotStale() { assertFalse(Rules.isStale(1_030, 1_000_000)); }
@Test public void backoff() { assertEquals(5_000, Rules.nextBackoffMillis(0)); assertEquals(10_000, Rules.nextBackoffMillis(5_000)); assertEquals(60_000, Rules.nextBackoffMillis(40_000)); }
```

- [ ] **Step 2: Run, expect FAIL** (no `Rules` class):
  `cd ~/personal-projects/tv-messenger && JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew --no-daemon testDebugUnitTest`
- [ ] **Step 3: Implement `Rules`** with the constants from Global Constraints (`displayMillis` measures the
  shortened text, so a cut text is timed as 600 characters).
- [ ] **Step 4: Run, expect PASS** (same command): `BUILD SUCCESSFUL`, 10 tests.
- [ ] **Step 5: Commit** (`git init` first; check `git status` shows no `secrets.properties`):
  `git add -A && git commit -m "Scaffold project and timing rules"`

### Task 2: `Updates` parser and `Router`

**Files:**
- Create: `app/src/main/java/com/tvmessenger/Incoming.java`, `Updates.java`, `Router.java`
- Test: `app/src/test/java/com/tvmessenger/UpdatesTest.java`, `RouterTest.java`

**Interfaces:**
- Produces: `final class Incoming { long updateId; long fromId /* 0 if none */; long chatId; long messageId;
  long dateSec; Kind kind; String text; String fileId; int durationSec; enum Kind { TEXT, VOICE, START, OTHER, NONE } }`
- `Updates.parse(String json) -> Batch` where `Batch { List<Incoming> items; long nextOffset /* max update_id + 1, or -1 if none */ }`.
  Any update without `message` becomes kind `NONE` (still counts for `nextOffset`). Bad JSON or `ok:false` → empty items, `nextOffset -1`.
- `Router.decide(Incoming in, long ownerId, long nowMillis) -> Action` where
  `enum Action { IGNORE, SHOW_TEXT, PLAY_VOICE, REPLY_STALE, REPLY_UNSUPPORTED, REPLY_HELP }`.
  Order: kind NONE or `fromId != ownerId` → IGNORE; stale → REPLY_STALE; START → REPLY_HELP; TEXT → SHOW_TEXT;
  VOICE → PLAY_VOICE; OTHER → REPLY_UNSUPPORTED. `/start` (exact, or `/start ` + anything) is START; other text, even
  starting with `/`, is TEXT.

- [ ] **Step 1: Write the failing tests.** `UpdatesTest`: a text update → TEXT with text, ids and date; a voice
  update → VOICE with `file_id` and `duration`; a photo → OTHER; `{"ok":true,"result":[{"update_id":7,"channel_post":{}}]}`
  → one NONE item, `nextOffset` 8; `{"ok":false}` and `"garbage"` → empty, `nextOffset` -1; a message without `from` →
  `fromId` 0. `RouterTest`: stranger's text → IGNORE; owner fresh text → SHOW_TEXT; owner voice → PLAY_VOICE;
  owner text 3 min old → REPLY_STALE; stranger's stale text → IGNORE (strangers never get replies); `/start` →
  REPLY_HELP; `/weather` → SHOW_TEXT; owner photo → REPLY_UNSUPPORTED; NONE → IGNORE.
- [ ] **Step 2: Run, expect FAIL** (classes missing), same command as Task 1.
- [ ] **Step 3: Implement** `Incoming`, `Updates` (with `org.json`), `Router`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit:** `git commit -am "Parse Telegram updates and route them" ` (add new files first).

### Task 3: Text path on the TV (service, overlay, chime, boot, status)

**Files:**
- Create: `TelegramApi.java`, `MessengerService.java`, `Overlay.java`, `Chime.java`, `BootReceiver.java`, `StatusActivity.java`
- Create: `app/src/main/res/layout/activity_status.xml`, `res/drawable/banner.xml` (320×180 dp shape with the app name), `res/values/strings.xml`
- Modify: `AndroidManifest.xml` (permissions from Global Constraints; service with
  `foregroundServiceType="dataSync"`; boot receiver; activity with `LEANBACK_LAUNCHER` and `android:banner`;
  `uses-feature android.software.leanback required=false`, `android.hardware.touchscreen required=false`)

**Interfaces:**
- Consumes: `Rules`, `Updates`, `Router`, `Incoming` (Tasks 1–2).
- Produces: `TelegramApi(String token)` with `String getUpdates(long offset, int timeoutSec) throws IOException`,
  `void sendMessage(long chatId, String text, long replyToMessageId)` (errors swallowed),
  `String getFilePath(String fileId) throws IOException`, `void download(String filePath, File to) throws IOException`.
  `Overlay.show(Context c, String text, long millis, Runnable onHidden)` and `Overlay.hide()`, main thread only.
  `Chime.play()` blocks ~600 ms. `MessengerService.start(Context)` static helper.
  `SharedPreferences "status"`: `connected` (boolean), `last` (long epoch ms).
- Service loop (worker thread): `getUpdates(offset, 50)` with read timeout 60 s → parse → for each item
  `Router.decide` → enqueue on a single-thread presenter; set `offset = nextOffset` when ≥ 0; on IOException mark
  disconnected and sleep `Rules.nextBackoffMillis`. Presenter for SHOW_TEXT: chime, show `Rules.shorten(text)` for
  `Rules.displayMillis(text)`, reply `✓ Shown on TV` when shown, wait until hidden. Holds a partial wake lock only
  while presenting. `START_STICKY`.

- [ ] **Step 1: Build:** `./gradlew --no-daemon testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL, all tests pass.
- [ ] **Step 2: Install** (TV_HOST=10.182.217.61, tools from cleanyourtv): push the APK with
  `tools/b64push.py` and `pm install -r`, then `appops set com.tvmessenger SYSTEM_ALERT_WINDOW allow`,
  then `am start -n com.tvmessenger/.StatusActivity`. Expected: `Success`, status screen says Connected.
- [ ] **Step 3: Verify on the TV:** owner sends "Dinner is ready" → chime, box top right, gone after 15 s
  (`tools/tv.py --screenshot` at ~3 s and ~17 s), `✓ Shown on TV` arrives in Telegram. Remote keys still move
  focus on Home while the box is up. Repeat over YouTube; over Live TV the owner looks (screenshots are empty).
  Check over the Clock screensaver (`am start -n com.android.systemui/.Somnambulator` or wait); record the result.
- [ ] **Step 4: Commit:** `git add -A && git commit -m "Show owner texts over the screen"`

### Task 4: Voice path

**Files:**
- Create: `VoicePlayer.java`
- Modify: `MessengerService.java` (PLAY_VOICE, REPLY_* actions)

**Interfaces:**
- Produces: `VoicePlayer.play(Context c, File oggOpus, Runnable onDone)`: requests
  `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with `USAGE_MEDIA` / `CONTENT_TYPE_SPEECH` attributes,
  plays with `MediaPlayer`, abandons focus and deletes the file in `onDone` (also on error).
- Presenter for PLAY_VOICE: download via `getFilePath` + `download` to `cacheDir`, request focus, chime, show
  `🎤 Voice message (Ns)` for the voice length + 2 s, play, reply `✓ Played on TV` on completion. On download or
  playback failure reply nothing and log. REPLY_STALE / REPLY_UNSUPPORTED / REPLY_HELP send the exact copy, reply-to the message.

- [ ] **Step 1: Build and install** as Task 3 steps 1–2.
- [ ] **Step 2: Verify on the TV:** owner sends a voice note → chime, notice, voice plays; `✓ Played on TV`.
  With YouTube playing: owner confirms the video got quieter and came back. With Live TV: owner reports whether it ducked.
  Photo → `Only text and voice messages work`. `/start` → help line.
- [ ] **Step 3: Verify boot and staleness:** reboot the TV with the app not opened → status Connected and a text
  works. Send a text, then let the TV be off (or kill network) > 2 min, bring it back → not shown, stale reply.
- [ ] **Step 4: Commit:** `git add -A && git commit -m "Play owner voice notes with ducking"`

### Task 5: Hand-off

- [ ] **Step 1:** Write `README.md` (what it does, how to rebuild/reinstall, how to change the token or owner,
  how to remove it: `pm uninstall com.tvmessenger`), and record the device results (ducking, screensaver) in it.
- [ ] **Step 2:** Optionally hide the tile: add `com.tvmessenger` to the launcher's `hidden_apps` only if the owner asks.
- [ ] **Step 3:** Commit. Then the cleanyourtv close-out: owner revokes authorisations and turns USB debugging
  off, restarts the TV; `tools/adb-closed.py 10.182.217.61` reports 5555 closed; one last text from Telegram
  still appears (proves the app works without ADB).
