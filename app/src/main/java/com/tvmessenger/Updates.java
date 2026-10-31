package com.tvmessenger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Reads a getUpdates answer. A broken or refused answer yields nothing, so the offset stays where it was. */
final class Updates {
    static final class Batch {
        final List<Incoming> items;
        final long nextOffset; // highest update_id + 1, or -1 when there was nothing

        Batch(List<Incoming> items, long nextOffset) {
            this.items = items;
            this.nextOffset = nextOffset;
        }
    }

    private static final Batch EMPTY = new Batch(Collections.emptyList(), -1);

    private Updates() {}

    static Batch parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (!root.optBoolean("ok")) return EMPTY;
            JSONArray result = root.optJSONArray("result");
            if (result == null || result.length() == 0) return EMPTY;
            List<Incoming> items = new ArrayList<>();
            long maxId = -1;
            for (int i = 0; i < result.length(); i++) {
                JSONObject update = result.getJSONObject(i);
                long updateId = update.getLong("update_id");
                maxId = Math.max(maxId, updateId);
                JSONObject message = update.optJSONObject("message");
                items.add(message == null ? Incoming.none(updateId) : message(updateId, message));
            }
            return new Batch(items, maxId + 1);
        } catch (JSONException e) {
            return EMPTY;
        }
    }

    private static Incoming message(long updateId, JSONObject m) {
        JSONObject from = m.optJSONObject("from");
        JSONObject chat = m.optJSONObject("chat");
        long fromId = from == null ? 0 : from.optLong("id");
        long chatId = chat == null ? 0 : chat.optLong("id");
        long messageId = m.optLong("message_id");
        long date = m.optLong("date");
        JSONObject voice = m.optJSONObject("voice");
        if (voice != null) {
            return new Incoming(updateId, fromId, chatId, messageId, date, Incoming.Kind.VOICE,
                    null, voice.optString("file_id", null), voice.optInt("duration"));
        }
        if (m.has("text")) {
            String text = m.optString("text");
            Incoming.Kind kind = text.equals("/start") || text.startsWith("/start ")
                    ? Incoming.Kind.START : Incoming.Kind.TEXT;
            return new Incoming(updateId, fromId, chatId, messageId, date, kind, text, null, 0);
        }
        return new Incoming(updateId, fromId, chatId, messageId, date, Incoming.Kind.OTHER, null, null, 0);
    }
}
