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
import java.util.ArrayList;
import java.util.List;

/**
 * Search iTunes for songs and return 30-second official preview clips.
 *
 * <p>Why iTunes previews: it is the only fully legal, key-free source that
 * returns a playable audio URL. Full-length MP3s from other platforms are
 * copyright-restricted and their free APIs now return empty links (verified).
 * A 30-second preview is enough to validate lyric syncing on the lens.</p>
 *
 * <p>Chinese songs require {@code country=HK} (or TW); the CN store returns no
 * results. We query HK first, then fall back to US/global if empty.</p>
 *
 * <p>Blocking network I/O — call off the UI/connection thread. No third-party
 * dependencies: HttpURLConnection + Android's built-in org.json.</p>
 */
public final class MusicSearch {
    private MusicSearch() {}

    private static final int TIMEOUT_MS = 15000;
    private static final String USER_AGENT = "MusicGlass/1.0 (Android; myvu lyric glasses)";

    /** One search hit. */
    public static final class Song {
        public final String trackId;      // iTunes trackId, used as stable id
        public final String name;
        public final String artist;
        public final String album;
        public final String previewUrl;   // 30s m4a, may be empty for some tracks
        public final String artworkUrl;   // cover thumbnail, may be empty
        public final int durationMillis;

        Song(String trackId, String name, String artist, String album,
             String previewUrl, String artworkUrl, int durationMillis) {
            this.trackId = trackId;
            this.name = name;
            this.artist = artist;
            this.album = album;
            this.previewUrl = previewUrl;
            this.artworkUrl = artworkUrl;
            this.durationMillis = durationMillis;
        }

        /** "歌名 — 歌手" for display. */
        public String display() {
            String a = artist == null ? "" : artist.trim();
            String n = name == null ? "" : name.trim();
            return a.isEmpty() ? n : n + " — " + a;
        }
    }

    /**
     * Search by keyword. Tries HK store (best for Chinese) then US.
     * Returns hits that actually have a playable previewUrl, up to {@code limit}.
     */
    public static List<Song> search(String keyword, int limit) throws IOException {
        if (keyword == null || keyword.trim().isEmpty()) return new ArrayList<>();
        if (limit <= 0) limit = 20;

        String q = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8.name());
        IOException lastError = null;
        // HK covers Chinese (incl. Traditional); US covers international songs.
        String[] countries = {"HK", "US"};
        for (String country : countries) {
            String url = "https://itunes.apple.com/search?term=" + q
                    + "&entity=song&limit=" + Math.min(limit * 3, 200)
                    + "&country=" + country;
            try {
                List<Song> songs = parse(httpGet(url), limit);
                if (!songs.isEmpty()) return songs;
            } catch (IOException e) {
                lastError = e;
            }
        }
        if (lastError != null) throw lastError;
        return new ArrayList<>();
    }

    /** Parse an iTunes Search JSON body into Song hits that have a preview. */
    static List<Song> parse(String json, int limit) {
        List<Song> out = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            JSONArray results = root.optJSONArray("results");
            if (results == null) return out;
            for (int i = 0; i < results.length() && out.size() < limit; i++) {
                JSONObject r = results.optJSONObject(i);
                if (r == null) continue;
                String preview = optString(r, "previewUrl");
                if (preview.isEmpty()) continue; // can't play without a preview
                String name = optString(r, "trackName");
                if (name.isEmpty()) name = optString(r, "collectionName");
                out.add(new Song(
                        String.valueOf(r.optLong("trackId")),
                        name,
                        optString(r, "artistName"),
                        optString(r, "collectionName"),
                        preview,
                        optString(r, "artworkUrl100"),
                        r.optInt("trackTimeMillis", 0)
                ));
            }
        } catch (Exception ignored) {
            // Malformed / non-JSON response: return whatever we parsed so far.
        }
        return out;
    }

    private static String optString(JSONObject o, String key) {
        Object v = o.opt(key);
        return v == null || v == JSONObject.NULL ? "" : String.valueOf(v);
    }

    /** Minimal GET with BOM stripping; shared shape with LrcFetcher. */
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
                throw new IOException("HTTP " + code + " searching music");
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
