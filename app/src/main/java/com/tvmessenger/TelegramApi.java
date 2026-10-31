package com.tvmessenger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The four Bot API calls the TV makes. URLs carry the token, so nothing here logs a URL or puts one in an
 * exception message.
 */
final class TelegramApi {
    private static final String HOST = "https://api.telegram.org";
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;

    private final String token;

    TelegramApi(String token) {
        this.token = token;
    }

    /** Waits up to timeoutSec for new messages. Any answer but 200 is an IOException, so the caller backs off. */
    String getUpdates(long offset, int timeoutSec) throws IOException {
        String query = "timeout=" + timeoutSec + "&allowed_updates=" + enc("[\"message\"]")
                + (offset > 0 ? "&offset=" + offset : "");
        return get(HOST + "/bot" + token + "/getUpdates?" + query, (timeoutSec + 10) * 1000);
    }

    /** Best effort: a reply that fails to send is not worth interrupting the TV for. */
    void sendMessage(long chatId, String text, long replyToMessageId) {
        try {
            String body = "chat_id=" + chatId + "&text=" + enc(text)
                    + "&reply_to_message_id=" + replyToMessageId + "&allow_sending_without_reply=true";
            HttpURLConnection c = open(HOST + "/bot" + token + "/sendMessage", READ_TIMEOUT_MILLIS);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            try (OutputStream out = c.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            c.getResponseCode();
            c.disconnect();
        } catch (IOException ignored) {
        }
    }

    /** The server-side path of an uploaded file, needed to download it. */
    String getFilePath(String fileId) throws IOException {
        String json = get(HOST + "/bot" + token + "/getFile?file_id=" + enc(fileId), READ_TIMEOUT_MILLIS);
        try {
            String path = new JSONObject(json).getJSONObject("result").optString("file_path", "");
            if (path.isEmpty()) throw new IOException("getFile returned no file_path");
            return path;
        } catch (JSONException e) {
            throw new IOException("getFile answer was not readable");
        }
    }

    void download(String filePath, File to) throws IOException {
        HttpURLConnection c = open(HOST + "/file/bot" + token + "/" + filePath, READ_TIMEOUT_MILLIS);
        try {
            if (c.getResponseCode() != 200) throw new IOException("download answered " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[16 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
    }

    private String get(String url, int readTimeoutMillis) throws IOException {
        HttpURLConnection c = open(url, readTimeoutMillis);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("Telegram answered " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return out.toString("UTF-8");
            }
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url, int readTimeoutMillis) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        c.setReadTimeout(readTimeoutMillis);
        return c;
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
