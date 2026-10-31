package com.tvmessenger;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Two short rising tones, made in code so the APK carries no sound file. Blocks until they finish. */
final class Chime {
    private static final int RATE = 44_100;

    private Chime() {}

    static void play() {
        short[] pcm = concat(tone(880, 180), silence(60), tone(1320, 260));
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length * 2)
                .build();
        try {
            track.write(pcm, 0, pcm.length);
            track.play();
            Thread.sleep(pcm.length * 1000L / RATE + 50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            track.release();
        }
    }

    /** A sine tone with a short fade in and out, so it doesn't click. */
    private static short[] tone(double hz, int millis) {
        int n = RATE * millis / 1000;
        int fade = RATE / 100;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double env = Math.min(1.0, Math.min(i, n - 1 - i) / (double) fade);
            out[i] = (short) (Math.sin(2 * Math.PI * hz * i / RATE) * env * 0.45 * Short.MAX_VALUE);
        }
        return out;
    }

    private static short[] silence(int millis) {
        return new short[RATE * millis / 1000];
    }

    private static short[] concat(short[]... parts) {
        int len = 0;
        for (short[] p : parts) len += p.length;
        short[] out = new short[len];
        int at = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }
}
