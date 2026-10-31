package com.tvmessenger;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Plays one voice note at the TV's own volume. While it plays it holds "may duck" audio focus, so Android
 * lowers whatever else is playing and brings it back afterwards.
 */
final class VoicePlayer {
    private static final AudioAttributes SPEECH = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    private VoicePlayer() {}

    /**
     * onDone runs once with true when the note played to the end, false when it failed; by then focus is
     * released and the file deleted.
     */
    static void play(Context c, File oggOpus, Consumer<Boolean> onDone) {
        AudioManager am = c.getSystemService(AudioManager.class);
        AudioFocusRequest focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(SPEECH)
                .build();
        am.requestAudioFocus(focus);
        MediaPlayer player = new MediaPlayer();
        boolean[] finished = {false};
        Consumer<Boolean> finish = played -> {
            synchronized (finished) {
                if (finished[0]) return;
                finished[0] = true;
            }
            player.release();
            am.abandonAudioFocusRequest(focus);
            if (!oggOpus.delete()) oggOpus.deleteOnExit();
            onDone.accept(played);
        };
        player.setAudioAttributes(SPEECH);
        player.setOnCompletionListener(mp -> finish.accept(true));
        player.setOnErrorListener((mp, what, extra) -> {
            Log.w(MessengerService.TAG, "voice playback failed: " + what + "/" + extra);
            finish.accept(false);
            return true;
        });
        try {
            player.setDataSource(oggOpus.getAbsolutePath());
            player.prepare();
            player.start();
        } catch (IOException | RuntimeException e) {
            Log.w(MessengerService.TAG, "voice could not start: " + e.getClass().getSimpleName());
            finish.accept(false);
        }
    }
}
