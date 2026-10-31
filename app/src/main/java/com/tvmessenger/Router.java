package com.tvmessenger;

/** What to do with one update. Strangers are ignored before anything else, so they never get a reply. */
final class Router {
    enum Action { IGNORE, SHOW_TEXT, SHOW_PHOTO, PLAY_VOICE, REPLY_STALE, REPLY_UNSUPPORTED, REPLY_HELP }

    private Router() {}

    static Action decide(Incoming in, long ownerId, long nowMillis) {
        if (in.kind == Incoming.Kind.NONE || in.fromId != ownerId) return Action.IGNORE;
        if (Rules.isStale(in.dateSec, nowMillis)) return Action.REPLY_STALE;
        switch (in.kind) {
            case START: return Action.REPLY_HELP;
            case TEXT: return Action.SHOW_TEXT;
            case VOICE: return Action.PLAY_VOICE;
            case PHOTO: return Action.SHOW_PHOTO;
            default: return Action.REPLY_UNSUPPORTED;
        }
    }
}
