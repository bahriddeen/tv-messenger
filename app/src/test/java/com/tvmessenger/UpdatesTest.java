package com.tvmessenger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdatesTest {
    static String update(long updateId, String message) {
        return "{\"ok\":true,\"result\":[{\"update_id\":" + updateId + ",\"message\":" + message + "}]}";
    }

    static String message(long fromId, long date, String body) {
        return "{\"message_id\":42,\"from\":{\"id\":" + fromId + ",\"first_name\":\"B\"},"
                + "\"chat\":{\"id\":" + fromId + ",\"type\":\"private\"},\"date\":" + date + "," + body + "}";
    }

    @Test public void textMessage() {
        Updates.Batch b = Updates.parse(update(100, message(555, 1_700_000_000, "\"text\":\"Dinner is ready\"")));
        assertEquals(1, b.items.size());
        Incoming in = b.items.get(0);
        assertEquals(Incoming.Kind.TEXT, in.kind);
        assertEquals("Dinner is ready", in.text);
        assertEquals(100, in.updateId);
        assertEquals(555, in.fromId);
        assertEquals(555, in.chatId);
        assertEquals(42, in.messageId);
        assertEquals(1_700_000_000, in.dateSec);
        assertEquals(101, b.nextOffset);
    }

    @Test public void voiceMessage() {
        Incoming in = Updates.parse(update(5, message(555, 1, "\"voice\":{\"file_id\":\"AwAD\",\"duration\":7}"))).items.get(0);
        assertEquals(Incoming.Kind.VOICE, in.kind);
        assertEquals("AwAD", in.fileId);
        assertEquals(7, in.durationSec);
    }

    @Test public void photoPicksSizeNearest1280() {
        String sizes = "\"photo\":[{\"file_id\":\"s\",\"width\":90,\"height\":51},"
                + "{\"file_id\":\"m\",\"width\":800,\"height\":450},{\"file_id\":\"l\",\"width\":1280,\"height\":720},"
                + "{\"file_id\":\"xl\",\"width\":2560,\"height\":1440}],\"caption\":\"Dinner\\n  soon\"";
        Incoming in = Updates.parse(update(5, message(555, 1, sizes))).items.get(0);
        assertEquals(Incoming.Kind.PHOTO, in.kind);
        assertEquals("l", in.fileId);
        assertEquals("Dinner\n  soon", in.text);
    }

    @Test public void photoOnlyLargerThan1280TakesSmallest() {
        String sizes = "\"photo\":[{\"file_id\":\"a\",\"width\":1600,\"height\":900},{\"file_id\":\"b\",\"width\":3000,\"height\":2000}]";
        Incoming in = Updates.parse(update(5, message(555, 1, sizes))).items.get(0);
        assertEquals("a", in.fileId);
        assertEquals(null, in.text);
    }

    @Test public void photoSentAsFileIsOther() {
        Incoming in = Updates.parse(update(5, message(555, 1, "\"document\":{\"file_id\":\"d\",\"mime_type\":\"image/jpeg\"}"))).items.get(0);
        assertEquals(Incoming.Kind.OTHER, in.kind);
    }

    @Test public void updateWithoutMessageStillAdvancesOffset() {
        Updates.Batch b = Updates.parse("{\"ok\":true,\"result\":[{\"update_id\":7,\"channel_post\":{}}]}");
        assertEquals(1, b.items.size());
        assertEquals(Incoming.Kind.NONE, b.items.get(0).kind);
        assertEquals(8, b.nextOffset);
    }

    @Test public void notOkGivesNothing() {
        Updates.Batch b = Updates.parse("{\"ok\":false}");
        assertTrue(b.items.isEmpty());
        assertEquals(-1, b.nextOffset);
    }

    @Test public void garbageGivesNothing() {
        Updates.Batch b = Updates.parse("garbage");
        assertTrue(b.items.isEmpty());
        assertEquals(-1, b.nextOffset);
    }

    @Test public void messageWithoutFromHasZeroSender() {
        String noFrom = "{\"message_id\":1,\"chat\":{\"id\":-100,\"type\":\"channel\"},\"date\":1,\"text\":\"hi\"}";
        assertEquals(0, Updates.parse(update(3, noFrom)).items.get(0).fromId);
    }
}
