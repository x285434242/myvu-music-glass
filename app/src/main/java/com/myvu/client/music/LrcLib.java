package com.myvu.client.music;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Fetch time-synced lyrics from LRCLIB (free, key-free, open-source lyrics DB).
 *
 * <p>Strategy: exact match via {@code /api/get} with artist + track (+ duration
 * when available) first; if that misses, fall back to fuzzy {@code /api/search}
 * and take the first result that has {@code syncedLyrics}. LRCLIB returns LRC
 * with timestamps in {@code syncedLyrics}, which we feed straight into
 * {@link Lrc#parse(String)}.</p>
 *
 * <p>LRCLIB asks clients to send a descriptive User-Agent. Blocking I/O —
 * call off the UI/connection thread.</p>
 */
public final class LrcLib {
    private LrcLib() {}

    private static final String BASE = "https://lrclib.net/api";
    private static final int TIMEOUT_MS = 15000;
    private static final String USER_AGENT = "MusicGlass/1.0 (Android; myvu lyric glasses)";

    /** Result of a lyric lookup: the parsed track plus which source produced it. */
    public static final class Result {
        public final Lrc.Track track;
        public final String source; // "lrclib:get", "lrclib:search", or "none"
        Result(Lrc.Track track, String source) {
            this.track = track;
            this.source = source;
        }
    }

    /**
     * Look up synced lyrics. {@code artist}, {@code track} and optionally
     * {@code durationSec} come from the iTunes search hit.
     */
    public static Result lookup(String track, String artist, int durationSec) {
        // 1) Exact match first (most accurate).
        try {
            StringBuilder u = new StringBuilder(BASE + "/get?");
            u.append("artist_name=").append(enc(artist == null ? "" : artist));
            u.append("&track_name=").append(enc(track == null ? "" : track));
            if (durationSec > 0) u.append("&duration=").append(durationSec);
            String body = httpGet(u.toString());
            String synced = extractSynced(body);
            if (synced != null && !synced.trim().isEmpty()) {
                return new Result(Lrc.parse(synced), "lrclib:get");
            }
        } catch (Exception ignored) {
            // try fuzzy below
        }

        // 2) Fuzzy search: "artist track" tends to rank the right song first.
        try {
            String q = ((artist == null ? "" : artist) + " " + (track == null ? "" : track)).trim();
            String body = httpGet(BASE + "/search?q=" + enc(q) + "&limit=5");
            JSONArray arr = safeArray(body);
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String synced = optString(o, "syncedLyrics");
                    if (synced != null && !synced.trim().isEmpty()) {
                        return new Result(Lrc.parse(synced), "lrclib:search");
                    }
                }
                // last resort: plain lyrics (unsynced) still show words, though
                // they won't scroll — better than nothing.
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String plain = optString(o, "plainLyrics");
                    if (plain != null && !plain.trim().isEmpty()) {
                        return new Result(Lrc.parse(plain), "lrclib:search:plain");
                    }
                }
            }
        } catch (Exception ignored) {
            // offline / rate-limited — return empty below
        }
        return new Result(new Lrc.Track(new java.util.ArrayList<>(), track, artist), "none");
    }

    /** Pull syncedLyrics out of a /api/get JSON object. */
    private static String extractSynced(String body) {
        try {
            JSONObject o = new JSONObject(body);
            return optString(o, "syncedLyrics");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JSONArray safeArray(String body) {
        try { return new JSONArray(body); } catch (Exception ignored) { return null; }
    }

    private static String optString(JSONObject o, String key) {
        Object v = o.opt(key);
        return v == null || v == JSONObject.NULL ? "" : String.valueOf(v);
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
    }

    private static String httpGet(String urlString) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlString).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code + " fetching lyrics");
            }
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            in.close();
            byte[] bytes = out.toByteArray();
            if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                    && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
                return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
