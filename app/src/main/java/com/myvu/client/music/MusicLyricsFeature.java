package com.myvu.client.music;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.myvu.client.R;
import com.myvu.client.feature.GlassesFeature;

/**
 * Feature #1: play an online audio stream and push the current lyric line to
 * the glasses lens, synced to playback position.
 *
 * <p>Registered in {@code MusicActivity}. Everything that touches Bluetooth
 * goes through {@link GlassesFeature.FeatureContext#connection()}; everything
 * that touches views runs on the UI thread.</p>
 */
public final class MusicLyricsFeature implements GlassesFeature {

    private static final String TAG = "MusicLyrics";

    private FeatureContext ctx;
    private View view;
    private EditText txtMusicUrl, txtLyricsUrl;
    private TextView txtNowPlaying, txtStatus;
    private Button btnLoad, btnPlay, btnPause, btnStop;

    private MediaPlayer player;
    private LyricRenderer renderer;
    private Lrc.Track track;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            syncLyric();
            ticker.postDelayed(this, 250);
        }
    };
    private int lastLine = -2;
    private boolean playing;

    @Override public String id() { return "music-lyrics"; }
    @Override public String label() { return "Music + lyrics"; }

    @Override
    public View createView(android.content.Context context) {
        view = LayoutInflater.from(context).inflate(R.layout.feature_music_lyrics, null, false);
        txtMusicUrl = view.findViewById(R.id.txtMusicUrl);
        txtLyricsUrl = view.findViewById(R.id.txtLyricsUrl);
        txtNowPlaying = view.findViewById(R.id.txtNowPlaying);
        txtStatus = view.findViewById(R.id.txtMusicStatus);
        btnLoad = view.findViewById(R.id.btnLoadTrack);
        btnPlay = view.findViewById(R.id.btnPlay);
        btnPause = view.findViewById(R.id.btnPause);
        btnStop = view.findViewById(R.id.btnStop);

        btnLoad.setOnClickListener(v -> loadTrack());
        btnPlay.setOnClickListener(v -> play());
        btnPause.setOnClickListener(v -> pause());
        btnStop.setOnClickListener(v -> stop());
        return view;
    }

    @Override
    public void onAttach(FeatureContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onConnected() {
        renderer = new TeleprompterLyricRenderer(ctx.connection());
        status("眼镜已连接 · 歌词将显示在镜片上");
    }

    @Override
    public void onDisconnected() {
        renderer = null;
        status("眼镜已断开 · 歌词不会推送");
    }

    @Override
    public void onDetach() {
        stopTicker();
        releasePlayer();
        if (renderer != null) renderer.close();
        renderer = null;
    }

    // ------------------------------------------------------------- actions

    private void loadTrack() {
        String musicUrl = text(txtMusicUrl);
        String lyricsUrl = text(txtLyricsUrl);
        if (musicUrl.isEmpty()) {
            status("请先填写音乐 URL");
            return;
        }
        releasePlayer();
        track = null;
        lastLine = -2;
        if (!lyricsUrl.isEmpty()) {
            status("正在下载歌词...");
            new Thread(() -> {
                try {
                    String lrc = LrcFetcher.fetch(lyricsUrl);
                    Lrc.Track parsed = Lrc.parse(lrc);
                    ctx.runOnUi(() -> {
                        track = parsed;
                        status(parsed.isEmpty()
                                ? "歌词为空或格式不支持（需要 LRC）"
                                : "歌词已加载：" + parsed.lines.size() + " 行");
                    });
                } catch (Exception e) {
                    ctx.runOnUi(() -> status("歌词下载失败：" + e.getMessage()));
                }
            }).start();
        } else {
            status("未填歌词 URL · 将只播放音乐");
        }
        preparePlayer(musicUrl);
    }

    private void preparePlayer(String url) {
        try {
            player = new MediaPlayer();
            player.setDataSource(url);
            player.setOnPreparedListener(mp -> {
                status("已就绪 · 点击播放");
                txtNowPlaying.setText(safeTitle());
            });
            player.setOnErrorListener((mp, what, extra) -> {
                status("播放错误：" + what + "/" + extra);
                return true;
            });
            player.setOnCompletionListener(mp -> {
                stopTicker();
                playing = false;
                status("播放结束");
                if (renderer != null) renderer.close();
            });
            player.prepareAsync();
        } catch (Exception e) {
            status("无法打开音频：" + e.getMessage());
        }
    }

    private void play() {
        if (player == null) {
            status("请先加载音乐");
            return;
        }
        try {
            if (!player.isPlaying()) {
                player.start();
                playing = true;
                if (renderer != null && track != null && !track.isEmpty()) {
                    renderer.open(Lrc.toTeleprompterText(track),
                            track.title == null ? "Lyrics" : track.title);
                    lastLine = -2;
                }
                startTicker();
                status("播放中");
            }
        } catch (IllegalStateException e) {
            status("播放失败：" + e.getMessage());
        }
    }

    private void pause() {
        if (player != null && player.isPlaying()) {
            player.pause();
            stopTicker();
            status("已暂停");
        }
    }

    private void stop() {
        if (player != null) {
            try { player.stop(); } catch (IllegalStateException ignored) {}
        }
        stopTicker();
        playing = false;
        lastLine = -2;
        if (renderer != null) renderer.close();
        status("已停止");
    }

    /** Called ~4x/sec while playing: find current lyric line and push on change. */
    private void syncLyric() {
        if (player == null || track == null || track.isEmpty() || renderer == null) return;
        try {
            int idx = track.indexAt(player.getCurrentPosition());
            if (idx >= 0 && idx != lastLine) {
                lastLine = idx;
                final int line = idx;
                String text = track.lines.get(line).text;
                txtNowPlaying.setText(text);
                // Push to the lens off the UI thread would be fine too, but the
                // upstream ConnectionManager marshals to its own thread, so call
                // straight through.
                renderer.highlight(line);
            }
        } catch (IllegalStateException ignored) {
            // player was released mid-tick
        }
    }

    private void startTicker() {
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    private void stopTicker() {
        ticker.removeCallbacks(tick);
    }

    private void releasePlayer() {
        stopTicker();
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
            } catch (IllegalStateException ignored) {}
            player.release();
            player = null;
        }
    }

    private String safeTitle() {
        if (track == null) return "准备播放";
        if (track.title != null) return track.title;
        String url = text(txtMusicUrl);
        int slash = url.lastIndexOf('/');
        return slash >= 0 && slash < url.length() - 1 ? url.substring(slash + 1) : "在线音乐";
    }

    private static String text(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    private void status(String s) {
        ctx.log("[" + TAG + "] " + s);
        if (txtStatus != null) txtStatus.setText(s);
    }
}
