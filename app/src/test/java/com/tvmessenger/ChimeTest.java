package com.tvmessenger;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** "Calm" as numbers: quiet, low, no click at either end, about a second with a soft fade. */
public class ChimeTest {
    private static final short[] PCM = Chime.samples();

    @Test public void quietPeak() {
        int peak = 0;
        for (short s : PCM) peak = Math.max(peak, Math.abs(s));
        assertTrue("peak " + peak, peak <= Short.MAX_VALUE / 4);
    }

    @Test public void lowPitch() {
        int crossings = 0;
        for (int i = 1; i < PCM.length; i++) if ((PCM[i - 1] < 0) != (PCM[i] < 0)) crossings++;
        double hz = crossings / 2.0 / (PCM.length / (double) Chime.RATE);
        assertTrue("average pitch " + hz + " Hz", hz < 700);
    }

    @Test public void noClickAtEdges() {
        assertTrue(Math.abs(PCM[0]) < Short.MAX_VALUE / 100);
        assertTrue(Math.abs(PCM[PCM.length - 1]) < Short.MAX_VALUE / 100);
    }

    @Test public void aboutASecond() {
        double seconds = PCM.length / (double) Chime.RATE;
        assertTrue("length " + seconds + " s", seconds >= 0.6 && seconds <= 1.2);
    }
}
