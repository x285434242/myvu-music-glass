package com.myvu.client.music;

/**
 * Abstraction for "show this lyric line on the lens". The first implementation
 * drives the teleprompter scene; a future one could use the caption scene.
 * Keeping this behind an interface is the second extension point of the app.
 */
public interface LyricRenderer {
    /** Load the whole lyric document once, before playback starts. */
    void open(String fullText, String title);

    /** Highlight the current line (0-based paragraph index). */
    void highlight(int lineIndex);

    /** Clear / end display. */
    void close();
}
