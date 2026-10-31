package com.tvmessenger;

/** One Telegram update, reduced to what the TV needs. */
final class Incoming {
    enum Kind { TEXT, VOICE, START, OTHER, NONE }

    final long updateId;
    final long fromId; // 0 when the update has no sender
    final long chatId;
    final long messageId;
    final long dateSec;
    final Kind kind;
    final String text;
    final String fileId;
    final int durationSec;

    Incoming(long updateId, long fromId, long chatId, long messageId, long dateSec, Kind kind,
             String text, String fileId, int durationSec) {
        this.updateId = updateId;
        this.fromId = fromId;
        this.chatId = chatId;
        this.messageId = messageId;
        this.dateSec = dateSec;
        this.kind = kind;
        this.text = text;
        this.fileId = fileId;
        this.durationSec = durationSec;
    }

    static Incoming none(long updateId) {
        return new Incoming(updateId, 0, 0, 0, 0, Kind.NONE, null, null, 0);
    }
}
