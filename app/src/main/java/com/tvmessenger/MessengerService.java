package com.tvmessenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asks Telegram for the owner's messages and presents them one at a time. The poll thread only reads and
 * routes; the presenter thread does the slow part (chime, overlay, replies), so a long message never delays
 * the next poll.
 */
public final class MessengerService extends Service {
    static final String TAG = "TvMessenger";
    static final String PREFS = "status";
    static final String KEY_CONNECTED = "connected";
    static final String KEY_LAST = "last";
    static final String KEY_OFFSET = "offset";
    private static final String CHANNEL = "service";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService presenter = Executors.newSingleThreadExecutor();
    private TelegramApi api;
    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;
    private Thread poller;
    private volatile boolean running;

    static void start(Context c) {
        c.startForegroundService(new Intent(c, MessengerService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        api = new TelegramApi(BuildConfig.BOT_TOKEN);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        wakeLock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TvMessenger:present");
        wakeLock.setReferenceCounted(false);
        goForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (poller == null || !poller.isAlive()) {
            running = true;
            poller = new Thread(this::pollLoop, "telegram-poll");
            poller.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (poller != null) poller.interrupt();
        presenter.shutdownNow();
        setConnected(false);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.app_name),
                NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, n);
        }
    }

    private void pollLoop() {
        long offset = prefs.getLong(KEY_OFFSET, 0);
        long backoff = 0;
        boolean answered = false;
        while (running) {
            try {
                Updates.Batch batch = Updates.parse(api.getUpdates(offset, Rules.pollTimeoutSec(answered)));
                answered = true;
                setConnected(true);
                backoff = 0;
                long now = System.currentTimeMillis();
                for (Incoming in : batch.items) {
                    Router.Action action = Router.decide(in, BuildConfig.OWNER_ID, now);
                    if (action != Router.Action.IGNORE) presenter.execute(() -> present(in, action));
                }
                if (batch.nextOffset >= 0) {
                    offset = batch.nextOffset;
                    prefs.edit().putLong(KEY_OFFSET, offset).apply();
                }
            } catch (IOException e) {
                answered = false;
                setConnected(false);
                backoff = Rules.nextBackoffMillis(backoff);
                Log.w(TAG, "poll failed (" + e.getClass().getSimpleName() + "), retrying in " + backoff + " ms");
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private void present(Incoming in, Router.Action action) {
        wakeLock.acquire(2 * 60_000L);
        try {
            switch (action) {
                case SHOW_TEXT:
                    showText(in);
                    break;
                case SHOW_PHOTO:
                    showPhoto(in);
                    break;
                case PLAY_VOICE:
                    playVoice(in);
                    break;
                case REPLY_STALE:
                    api.sendMessage(in.chatId, getString(R.string.reply_stale), in.messageId);
                    break;
                case REPLY_UNSUPPORTED:
                    api.sendMessage(in.chatId, getString(R.string.reply_unsupported), in.messageId);
                    break;
                case REPLY_HELP:
                    api.sendMessage(in.chatId, getString(R.string.reply_help), in.messageId);
                    break;
                default:
                    break;
            }
        } finally {
            wakeLock.release();
        }
    }

    private void showText(Incoming in) {
        Chime.play();
        CountDownLatch hidden = new CountDownLatch(1);
        long millis = Rules.displayMillis(in.text);
        if (onScreen(() -> Overlay.show(this, Rules.shorten(in.text), millis, hidden::countDown))) {
            api.sendMessage(in.chatId, getString(R.string.reply_shown), in.messageId);
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();
        }
        awaitQuietly(hidden, millis + 10_000);
    }

    /** Download the photo, scale it down to screen size, then chime and show it with its caption. */
    private void showPhoto(Incoming in) {
        File file = new File(getCacheDir(), "photo-" + in.messageId + ".jpg");
        Bitmap photo;
        try {
            api.download(api.getFilePath(in.fileId), file);
            photo = decodeForScreen(file);
        } catch (IOException e) {
            Log.w(TAG, "photo download failed (" + e.getClass().getSimpleName() + ")");
            photo = null;
        } finally {
            if (!file.delete()) file.deleteOnExit();
        }
        if (photo == null) return;
        Chime.play();
        CountDownLatch hidden = new CountDownLatch(1);
        long millis = Rules.photoMillis(in.text);
        String caption = in.text == null ? null : Rules.shorten(in.text);
        Bitmap shown = photo;
        if (onScreen(() -> Overlay.showPhoto(this, shown, caption, millis, hidden::countDown))) {
            api.sendMessage(in.chatId, getString(R.string.reply_shown), in.messageId);
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();
        }
        awaitQuietly(hidden, millis + 10_000);
        // The box is gone: give the photo's memory back now rather than whenever the collector runs.
        main.post(() -> {
            Overlay.hide();
            shown.recycle();
        });
    }

    /** Decodes just large enough for the screen, so a big photo can't run a 1.5 GB TV out of memory. */
    private Bitmap decodeForScreen(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = Rules.sampleSize(bounds.outWidth, bounds.outHeight,
                getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        opts.inPreferredConfig = Bitmap.Config.RGB_565; // half the memory of ARGB; a photo needs no transparency
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    /** Download, chime, a notice on screen for the note's length + 2 s, then the voice with the show ducked. */
    private void playVoice(Incoming in) {
        File file = new File(getCacheDir(), "voice-" + in.messageId + ".ogg");
        try {
            api.download(api.getFilePath(in.fileId), file);
        } catch (IOException e) {
            Log.w(TAG, "voice download failed (" + e.getClass().getSimpleName() + ")");
            if (!file.delete()) file.deleteOnExit();
            return;
        }
        int seconds = Math.max(1, in.durationSec);
        Chime.play();
        CountDownLatch hidden = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean played = new AtomicBoolean(false);
        String notice = getString(R.string.voice_notice, seconds);
        main.post(() -> Overlay.show(this, notice, (seconds + 2) * 1000L, hidden::countDown));
        VoicePlayer.play(this, file, ok -> {
            played.set(ok);
            done.countDown();
        });
        awaitQuietly(done, (seconds + 30) * 1000L);
        if (played.get()) {
            api.sendMessage(in.chatId, getString(R.string.reply_played), in.messageId);
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply();
        }
        awaitQuietly(hidden, 5_000);
    }

    /** Runs a show call on the main thread and waits for its answer, so a tick is only sent for a real box. */
    private boolean onScreen(java.util.function.BooleanSupplier show) {
        AtomicBoolean shown = new AtomicBoolean(false);
        CountDownLatch answered = new CountDownLatch(1);
        main.post(() -> {
            shown.set(show.getAsBoolean());
            answered.countDown();
        });
        awaitQuietly(answered, 5_000);
        return shown.get();
    }

    private static void awaitQuietly(CountDownLatch latch, long millis) {
        try {
            latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void setConnected(boolean connected) {
        if (prefs.getBoolean(KEY_CONNECTED, false) != connected) {
            prefs.edit().putBoolean(KEY_CONNECTED, connected).apply();
        }
    }
}
