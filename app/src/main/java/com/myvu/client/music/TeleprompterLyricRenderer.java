package com.myvu.client.music;

import com.myvu.client.service.ConnectionManager;

/**
 * Renders lyrics by loading the full lyric text into the glasses' teleprompter
 * ("tici") scene once, then moving the highlight to the current paragraph as
 * the song advances.
 *
 * <p>Why teleprompter and not the live-caption scene: the caption protocol
 * requires opening the glasses-side translation twin, a two-phase handshake and
 * voice-activity state machine -- fragile for a music timeline. The teleprompter
 * highlight API is a single simple action that the upstream client already
 * drives successfully.</p>
 */
public final class TeleprompterLyricRenderer implements LyricRenderer {
    private static final String TITLE = "Lyrics";

    private final ConnectionManager connection;
    private String title = TITLE;
    private boolean opened;

    public TeleprompterLyricRenderer(ConnectionManager connection) {
        this.connection = connection;
    }

    @Override
    public void open(String fullText, String title) {
        if (connection == null) return;
        this.title = (title == null || title.isEmpty()) ? TITLE : title;
        connection.openTeleprompter(fullText, this.title);
        opened = true;
    }

    @Override
    public void highlight(int lineIndex) {
        if (connection == null || !opened) return;
        connection.teleprompterHighlight(lineIndex, title);
    }

    @Override
    public void close() {
        opened = false;
    }
}
