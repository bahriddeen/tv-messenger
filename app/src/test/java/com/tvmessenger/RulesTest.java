package com.tvmessenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RulesTest {
    @Test public void shortTextGetsMinimum() { assertEquals(15_000, Rules.displayMillis("Dinner!")); }
    @Test public void cyrillicAndEmojiCountAsCharacters() { assertEquals(15_000, Rules.displayMillis("Ужин готов 🍲")); }
    @Test public void mediumTextScales() { assertEquals(30_000, Rules.displayMillis("a".repeat(300))); }
    @Test public void longTextCapsAtOneMinute() { assertEquals(60_000, Rules.displayMillis("a".repeat(4096))); }
    @Test public void shortenCutsAt600() {
        String s = Rules.shorten("a".repeat(4096));
        assertEquals(601, s.codePointCount(0, s.length()));
        assertTrue(s.endsWith("…"));
    }
    @Test public void shortenKeepsShortText() { assertEquals("hi", Rules.shorten("hi")); }
    @Test public void freshAt119s() { assertFalse(Rules.isStale(1_000, 1_119_000)); }
    @Test public void staleAt121s() { assertTrue(Rules.isStale(1_000, 1_121_000)); }
    @Test public void futureDateIsNotStale() { assertFalse(Rules.isStale(1_030, 1_000_000)); }
    @Test public void backoff() {
        assertEquals(5_000, Rules.nextBackoffMillis(0));
        assertEquals(10_000, Rules.nextBackoffMillis(5_000));
        assertEquals(60_000, Rules.nextBackoffMillis(40_000));
    }
    @Test public void firstPollAnswersAtOnce() { assertEquals(0, Rules.pollTimeoutSec(false)); }
    @Test public void laterPollsWait() { assertEquals(50, Rules.pollTimeoutSec(true)); }
    @Test public void photoWithoutCaptionTenSeconds() { assertEquals(10_000, Rules.photoMillis(null)); }
    @Test public void photoWithShortCaptionTenSeconds() { assertEquals(10_000, Rules.photoMillis("Look at this")); }
    @Test public void photoWithLongCaptionGetsReadingTime() { assertEquals(30_000, Rules.photoMillis("a".repeat(300))); }
    @Test public void photoCapsAtOneMinute() { assertEquals(60_000, Rules.photoMillis("a".repeat(4096))); }
    @Test public void smallPhotoNotScaled() { assertEquals(1, Rules.sampleSize(1280, 720, 1280, 720)); }
    @Test public void photoTwiceScreenHalved() { assertEquals(2, Rules.sampleSize(2560, 1440, 1280, 720)); }
    @Test public void hugePhotoScaledButNotBelowScreen() { assertEquals(4, Rules.sampleSize(6000, 4000, 1280, 720)); }
    @Test public void tallPhotoStillFillsScreenHeight() { assertEquals(4, Rules.sampleSize(1600, 3000, 1280, 720)); }
}
