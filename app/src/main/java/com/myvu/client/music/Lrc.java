package com.myvu.client.music;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal LRC lyric parser. Handles standard {@code [mm:ss.xx]text} lines,
 * multiple timestamps on one line, ID3-style metadata tags, and an
 * {@code [offset:]} shift. Pure Java (no Android dependency) so it can be unit
 * tested on the JVM.
 */
public final class Lrc {
    private Lrc() {}

    /** One timed lyric line. */
    public static final class Line {
        public final long timeMs;
        public final String text;

        Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    /** A parsed, time-sorted lyric track. */
    public static final class Track {
        public final List<Line> lines;
        public final String title;
        public final String artist;

        Track(List<Line> lines, String title, String artist) {
            this.lines = lines;
            this.title = title;
            this.artist = artist;
        }

        public boolean isEmpty() { return lines.isEmpty(); }

        /**
         * Index of the line that should be showing at {@code positionMs}, or -1
         * before the first line.
         */
        public int indexAt(long positionMs) {
            int lo = 0, hi = lines.size() - 1, ans = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (lines.get(mid).timeMs <= positionMs) {
                    ans = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return ans;
        }
    }

    /** Any bracketed tag, e.g. [00:12.34], [offset:100], [ti:Song]. */
    private static final Pattern ANY_TAG = Pattern.compile("\\[([^]]*)\\]");
    /** Body of a metadata tag (without brackets). */
    private static final Pattern META_BODY =
            Pattern.compile("(ar|ti|al|by|offset|length|re|ve):([^]]*)", Pattern.CASE_INSENSITIVE);
    /** Body of a timestamp tag (without brackets). */
    private static final Pattern TIME_BODY =
            Pattern.compile("(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?");

    /**
     * Parse LRC text. Lines with no timestamp are ignored. Metadata is captured
     * for display only. {@code offset} (hundredths of a second, may be negative)
     * is applied to every timestamp.
     */
    public static Track parse(String lrc) {
        List<Line> lines = new ArrayList<>();
        String title = null, artist = null;
        int offsetMs = 0;
        if (lrc == null) return new Track(lines, title, artist);

        try (BufferedReader reader = new BufferedReader(new StringReader(lrc))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                String line = raw.trim();
                if (line.isEmpty()) continue;

                // Walk every bracketed tag in order. Metadata updates state;
                // timestamps remember where they sit so we can slice the text
                // that follows each one (up to the next tag).
                Matcher any = ANY_TAG.matcher(line);
                java.util.List<int[]> spans = new ArrayList<>(); // {start, end}
                while (any.find()) {
                    String body = any.group(1);
                    Matcher meta = META_BODY.matcher(body);
                    if (meta.matches()) {
                        String key = meta.group(1).toLowerCase();
                        String val = meta.group(2).trim();
                        if ("ti".equals(key)) title = val;
                        else if ("ar".equals(key)) artist = val;
                        else if ("offset".equals(key)) {
                            try { offsetMs = Integer.parseInt(val) * 10; }
                            catch (NumberFormatException ignored) {}
                        }
                        continue;
                    }
                    Matcher t = TIME_BODY.matcher(body);
                    if (t.matches()) {
                        long min = Long.parseLong(t.group(1));
                        long sec = Long.parseLong(t.group(2));
                        String frac = t.group(3);
                        long ms = 0;
                        if (frac != null) {
                            ms = Long.parseLong(frac);
                            for (int i = frac.length(); i < 3; i++) ms *= 10;
                        }
                        long timeMs = min * 60000L + sec * 1000L + ms + offsetMs;
                        spans.add(new int[]{(int) timeMs, any.start(), any.end()});
                    }
                }

                for (int i = 0; i < spans.size(); i++) {
                    int[] cur = spans.get(i);
                    int textStart = cur[2];
                    int textEnd = (i + 1 < spans.size()) ? spans.get(i + 1)[1] : line.length();
                    String text = textEnd > textStart
                            ? line.substring(textStart, textEnd).trim() : "";
                    lines.add(new Line(Math.max(0, cur[0]), text));
                }
            }
        } catch (IOException ignored) {
            // StringReader never throws; keep the signature honest for callers.
        }

        lines.sort(Comparator.comparingLong(l -> l.timeMs));
        return new Track(lines, title, artist);
    }

    /** Join lyric texts into one teleprompter document, one line per paragraph. */
    public static String toTeleprompterText(Track track) {
        if (track == null || track.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < track.lines.size(); i++) {
            if (i > 0) sb.append('\n');
            String text = track.lines.get(i).text;
            sb.append(text.isEmpty() ? "♪" : text);
        }
        return sb.toString();
    }
}
