package com.myvu.client.music;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** Pure-JVM tests for the iTunes search response parser. */
public class MusicSearchTest {

    @Test
    public void parsesHitsAndKeepsOnlyOnesWithPreview() {
        String json = "{\"resultCount\":3,\"results\":["
                + "{\"trackId\":123,\"trackName\":\"Sunny Day\",\"artistName\":\"CIP Music\","
                + "\"collectionName\":\"Album\",\"trackTimeMillis\":30000,"
                + "\"previewUrl\":\"https://audio-ssl.itunes.apple.com/a.m4a\","
                + "\"artworkUrl100\":\"https://is1-ssl.mzstatic.com/cover.jpg\"},"
                + "{\"trackId\":456,\"trackName\":\"No Preview Song\",\"artistName\":\"X\","
                + "\"collectionName\":\"Y\"},"
                + "{\"trackId\":789,\"trackName\":\"\",\"collectionName\":\"Fallback Album\","
                + "\"artistName\":\"Z\",\"previewUrl\":\"https://audio-ssl.itunes.apple.com/b.m4a\"}"
                + "]}";
        List<MusicSearch.Song> songs = MusicSearch.parse(json, 20);
        assertEquals(2, songs.size()); // third has preview, second does not
        assertEquals("Sunny Day", songs.get(0).name);
        assertEquals("CIP Music", songs.get(0).artist);
        assertEquals("Sunny Day — CIP Music", songs.get(0).display());
        assertEquals("123", songs.get(0).trackId);
        assertEquals("https://audio-ssl.itunes.apple.com/a.m4a", songs.get(0).previewUrl);
        // empty trackName falls back to collectionName
        assertEquals("Fallback Album", songs.get(1).name);
        assertEquals("Fallback Album — Z", songs.get(1).display());
    }

    @Test
    public void respectsLimit() {
        StringBuilder sb = new StringBuilder("{\"results\":[");
        for (int i = 0; i < 5; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"trackId\":").append(i)
              .append(",\"trackName\":\"S").append(i)
              .append("\",\"previewUrl\":\"http://x/").append(i).append("\"}");
        }
        sb.append("]}");
        assertEquals(2, MusicSearch.parse(sb.toString(), 2).size());
        assertEquals(5, MusicSearch.parse(sb.toString(), 20).size());
    }

    @Test
    public void malformedJsonReturnsEmpty() {
        assertTrue(MusicSearch.parse("not json", 10).isEmpty());
        assertTrue(MusicSearch.parse("{}", 10).isEmpty());
    }

    @Test
    public void emptyKeywordReturnsEmpty() {
        // search() short-circuits before any network call on blank input
        try {
            assertTrue(MusicSearch.search("  ", 5).isEmpty());
        } catch (Exception e) {
            fail("blank keyword should not throw: " + e.getMessage());
        }
    }
}
