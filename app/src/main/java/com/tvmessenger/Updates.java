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

    private static final int MAX_PHOTO_WIDTH = 1280;
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

    /**
     * Telegram sends each photo in several sizes. Take the largest no wider than 1280 px: sharp on this TV,
     * light on mobile data. If every size is wider, take the narrowest.
     */
    private static String photoSize(JSONArray sizes) {
        String fit = null, narrowest = null;
        int fitWidth = -1, narrowestWidth = Integer.MAX_VALUE;
        for (int i = 0; i < sizes.length(); i++) {
            JSONObject size = sizes.optJSONObject(i);
            if (size == null) continue;
            int w = size.optInt("width");
            String id = size.optString("file_id", null);
            if (id == null) continue;
            if (w <= MAX_PHOTO_WIDTH && w > fitWidth) {
                fit = id;
                fitWidth = w;
            }
            if (w < narrowestWidth) {
                narrowest = id;
                narrowestWidth = w;
            }
        }
        return fit != null ? fit : narrowest;
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
        JSONArray photo = m.optJSONArray("photo");
        if (photo != null && photo.length() > 0) {
            return new Incoming(updateId, fromId, chatId, messageId, date, Incoming.Kind.PHOTO,
                    m.has("caption") ? m.optString("caption") : null, photoSize(photo), 0);
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
