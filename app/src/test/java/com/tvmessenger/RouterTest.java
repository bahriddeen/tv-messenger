package com.tvmessenger;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RouterTest {
    static final long OWNER = 555;
    static final long STRANGER = 999;
    static final long NOW_SEC = 1_700_000_000;
    static final long NOW = NOW_SEC * 1000;

    static Router.Action route(long fromId, long dateSec, String body) {
        Incoming in = Updates.parse(UpdatesTest.update(1, UpdatesTest.message(fromId, dateSec, body))).items.get(0);
        return Router.decide(in, OWNER, NOW);
    }

    @Test public void strangerTextIsIgnored() { assertEquals(Router.Action.IGNORE, route(STRANGER, NOW_SEC, "\"text\":\"hi\"")); }
    @Test public void ownerFreshTextIsShown() { assertEquals(Router.Action.SHOW_TEXT, route(OWNER, NOW_SEC, "\"text\":\"hi\"")); }
    @Test public void ownerVoiceIsPlayed() { assertEquals(Router.Action.PLAY_VOICE, route(OWNER, NOW_SEC, "\"voice\":{\"file_id\":\"a\",\"duration\":3}")); }
    @Test public void ownerOldTextGetsStaleReply() { assertEquals(Router.Action.REPLY_STALE, route(OWNER, NOW_SEC - 180, "\"text\":\"hi\"")); }
    @Test public void strangerOldTextIsIgnored() { assertEquals(Router.Action.IGNORE, route(STRANGER, NOW_SEC - 180, "\"text\":\"hi\"")); }
    @Test public void startGetsHelp() { assertEquals(Router.Action.REPLY_HELP, route(OWNER, NOW_SEC, "\"text\":\"/start\"")); }
    @Test public void startWithPayloadGetsHelp() { assertEquals(Router.Action.REPLY_HELP, route(OWNER, NOW_SEC, "\"text\":\"/start abc\"")); }
    @Test public void otherSlashTextIsShown() { assertEquals(Router.Action.SHOW_TEXT, route(OWNER, NOW_SEC, "\"text\":\"/weather\"")); }
    @Test public void ownerPhotoIsShown() { assertEquals(Router.Action.SHOW_PHOTO, route(OWNER, NOW_SEC, "\"photo\":[{\"file_id\":\"x\",\"width\":800}]")); }
    @Test public void ownerVideoIsUnsupported() { assertEquals(Router.Action.REPLY_UNSUPPORTED, route(OWNER, NOW_SEC, "\"video\":{\"file_id\":\"v\"}")); }
    @Test public void strangerPhotoIsIgnored() { assertEquals(Router.Action.IGNORE, route(STRANGER, NOW_SEC, "\"photo\":[{\"file_id\":\"x\",\"width\":800}]")); }
    @Test public void noneIsIgnored() {
        Incoming in = Updates.parse("{\"ok\":true,\"result\":[{\"update_id\":7,\"channel_post\":{}}]}").items.get(0);
        assertEquals(Router.Action.IGNORE, Router.decide(in, OWNER, NOW));
    }
}
