package com.tvmessenger;

/** The numbers from the design: how long a text stays, how much of it fits, when a message is too old. */
final class Rules {
    static final long MIN_MILLIS = 15_000;
    static final long MAX_MILLIS = 60_000;
    static final long BASE_MILLIS = 5_000;
    static final int CHARS_PER_SECOND = 12;
    static final int MAX_CHARS = 600;
    static final long STALE_MILLIS = 120_000;
    static final long FIRST_BACKOFF_MILLIS = 5_000;
    static final long MAX_BACKOFF_MILLIS = 60_000;
    static final int POLL_TIMEOUT_SEC = 50;

    private Rules() {}

    /** 15 s at least, 1 s more per 12 characters, 60 s at most. Characters are code points, so emoji count once. */
    static long displayMillis(String text) {
        long chars = text.codePointCount(0, text.length());
        long millis = BASE_MILLIS + chars * 1000 / CHARS_PER_SECOND;
        return Math.max(MIN_MILLIS, Math.min(MAX_MILLIS, millis));
    }

    /** The first 600 characters and "…", or the text itself when it fits. */
    static String shorten(String text) {
        if (text.codePointCount(0, text.length()) <= MAX_CHARS) return text;
        return text.substring(0, text.offsetByCodePoints(0, MAX_CHARS)) + "…";
    }

    /** True when the message is more than 2 minutes old. A date ahead of the TV's clock counts as fresh. */
    static boolean isStale(long messageDateSec, long nowMillis) {
        return nowMillis - messageDateSec * 1000 > STALE_MILLIS;
    }

    /** 5 s after the first failure, then double, never more than a minute. */
    static long nextBackoffMillis(long currentMillis) {
        if (currentMillis <= 0) return FIRST_BACKOFF_MILLIS;
        return Math.min(MAX_BACKOFF_MILLIS, currentMillis * 2);
    }

    /**
     * Until Telegram has answered once since starting (or since a failure), ask it to answer at once, so the
     * status screen shows Connected straight away instead of after a full long poll.
     */
    static int pollTimeoutSec(boolean answeredSinceStart) {
        return answeredSinceStart ? POLL_TIMEOUT_SEC : 0;
    }
}
