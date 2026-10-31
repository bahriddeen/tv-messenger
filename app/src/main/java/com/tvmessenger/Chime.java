package com.tvmessenger;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** A soft two-note bell, made in code so the APK carries no sound file. Blocks until it finishes. */
final class Chime {
    static final int RATE = 44_100;

    private Chime() {}

    static void play() {
        short[] pcm = samples();
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

    /** Two soft bell notes, G4 then C5, the second starting as the first fades. About one second, quiet. */
    static short[] samples() {
        double[] mix = new double[RATE];
        bell(mix, 0, 392.00);
        bell(mix, RATE / 4, 523.25);
        int edge = RATE / 50; // 20 ms fade at the very end, so the last sample is silent
        short[] out = new short[mix.length];
        for (int i = 0; i < mix.length; i++) {
            double tail = Math.min(1.0, (mix.length - 1 - i) / (double) edge);
            out[i] = (short) (mix[i] * tail * Short.MAX_VALUE);
        }
        return out;
    }

    /** Adds a note that starts softly and dies away like a struck bell, with a faint overtone for warmth. */
    private static void bell(double[] mix, int start, double hz) {
        int attack = RATE * 12 / 1000;
        for (int i = 0; start + i < mix.length; i++) {
            double t = i / (double) RATE;
            double env = Math.min(1.0, i / (double) attack) * Math.exp(-t / 0.22);
            double wave = Math.sin(2 * Math.PI * hz * t) + 0.15 * Math.sin(4 * Math.PI * hz * t);
            mix[start + i] += wave * env * 0.13;
        }
    }
}
