package com.myvu.client.music;

import org.junit.Test;

import static org.junit.Assert.*;

public class LrcTest {

    @Test
    public void parsesStandardLinesAndSorts() {
        String lrc = "[ti:Test Song]\n[ar:Someone]\n"
                + "[00:05.00]second line\n[00:01.50]first line\n[00:10]third line\n";
        Lrc.Track t = Lrc.parse(lrc);
        assertEquals("Test Song", t.title);
        assertEquals("Someone", t.artist);
        assertEquals(3, t.lines.size());
        assertEquals(1500L, t.lines.get(0).timeMs);
        assertEquals("first line", t.lines.get(0).text);
        assertEquals(10000L, t.lines.get(2).timeMs);
    }

    @Test
    public void indexAtFindsCurrentLine() {
        Lrc.Track t = Lrc.parse("[00:01.00]a\n[00:02.00]b\n[00:03.00]c\n");
        assertEquals(-1, t.indexAt(0));
        assertEquals(0, t.indexAt(1000));
        assertEquals(1, t.indexAt(2500));
        assertEquals(2, t.indexAt(3000));
        assertEquals(2, t.indexAt(99999));
    }

    @Test
    public void handlesMultipleTagsAndOffset() {
        Lrc.Track t = Lrc.parse("[offset:100][00:01.00]duo[00:02.00]\n");
        // offset 100 hundredths = +1000ms => 2s and 3s, two lines, same text
        assertEquals(2, t.lines.size());
        assertEquals(2000L, t.lines.get(0).timeMs);
        assertEquals("duo", t.lines.get(0).text);
    }

    @Test
    public void teleprompterTextIsOneLinePerParagraph() {
        Lrc.Track t = Lrc.parse("[00:01.00]hello\n[00:02.00]world\n");
        assertEquals("hello\nworld", Lrc.toTeleprompterText(t));
    }
}
