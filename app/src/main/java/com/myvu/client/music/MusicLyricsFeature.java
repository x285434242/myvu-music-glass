package com.myvu.client.music;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.myvu.client.R;
import com.myvu.client.feature.GlassesFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature #1: search a song by name, play its 30-second official preview, and
 * push time-synced lyrics to the glasses lens as playback advances.
 *
 * <p>Two ways to load a track:</p>
 * <ol>
 *   <li><b>Search (default):</b> type a song name → iTunes preview → LRCLIB
 *       synced lyrics, all automatic.</li>
 *   <li><b>Advanced:</b> paste a music URL + optional LRC URL manually (the
 *       original behaviour), for sources the search can't reach.</li>
 * </ol>
 *
 * <p>All Bluetooth goes through {@link FeatureContext#connection()}; all view
 * updates run on the UI thread via {@link FeatureContext#runOnUi(Runnable)}.</p>
 */
public final class MusicLyricsFeature implements GlassesFeature {

    private static final String TAG = "MusicLyrics";

    private FeatureContext ctx;
    private View view;
    // search UI
    private EditText txtSongName;
    private Button btnSearch, btnToggleAdvanced, btnLoad;
    private ListView listSongs;
    private TextView txtResultHeader;
    private ArrayAdapter<String> songAdapter;
    private List<MusicSearch.Song> searchHits = new ArrayList<>();
    // advanced UI
    private EditText txtMusicUrl, txtLyricsUrl;
    private LinearLayout advancedPanel;
    // playback UI
    private TextView txtNowPlaying, txtStatus;
    private Button btnPlay, btnPause, btnStop;

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

    @Override public String id() { return "music-lyrics"; }
    @Override public String label() { return "Music + lyrics"; }

    @Override
    public View createView(android.content.Context context) {
        view = LayoutInflater.from(context).inflate(R.layout.feature_music_lyrics, null, false);
        txtSongName = view.findViewById(R.id.txtSongName);
        btnSearch = view.findViewById(R.id.btnSearch);
        listSongs = view.findViewById(R.id.listSongs);
        txtResultHeader = view.findViewById(R.id.txtResultHeader);
        btnToggleAdvanced = view.findViewById(R.id.btnToggleAdvanced);
        advancedPanel = view.findViewById(R.id.advancedPanel);
        txtMusicUrl = view.findViewById(R.id.txtMusicUrl);
        txtLyricsUrl = view.findViewById(R.id.txtLyricsUrl);
        btnLoad = view.findViewById(R.id.btnLoadTrack);
        txtNowPlaying = view.findViewById(R.id.txtNowPlaying);
        txtStatus = view.findViewById(R.id.txtMusicStatus);
        btnPlay = view.findViewById(R.id.btnPlay);
        btnPause = view.findViewById(R.id.btnPause);
        btnStop = view.findViewById(R.id.btnStop);

        btnSearch.setOnClickListener(v -> doSearch());
        btnToggleAdvanced.setOnClickListener(v -> {
            int next = advancedPanel.getVisibility() == android.view.View.VISIBLE
                    ? android.view.View.GONE : android.view.View.VISIBLE;
            advancedPanel.setVisibility(next);
            String label = ctxString(R.string.advanced_url_mode);
            btnToggleAdvanced.setText(next == android.view.View.VISIBLE
                    ? label + " ▴" : label + " ▾");
        });
        listSongs.setOnItemClickListener((parent, v, pos, id) -> onSongSelected(pos));
        btnLoad.setOnClickListener(v -> loadFromUrl());
        btnPlay.setOnClickListener(v -> play());
        btnPause.setOnClickListener(v -> pause());
        btnStop.setOnClickListener(v -> stop());

        songAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, new ArrayList<String>());
        listSongs.setAdapter(songAdapter);
        return view;
    }

    @Override public void onAttach(FeatureContext ctx) { this.ctx = ctx; }

    @Override public void onConnected() {
        renderer = new TeleprompterLyricRenderer(ctx.connection());
        status("眼镜已连接 · 歌词将显示在镜片上");
    }

    @Override public void onDisconnected() {
        renderer = null;
        status("眼镜已断开 · 歌词不会推送");
    }

    @Override public void onDetach() {
        stopTicker();
        releasePlayer();
        if (renderer != null) renderer.close();
        renderer = null;
    }

    // ------------------------------------------------------------- search flow

    private void doSearch() {
        String keyword = text(txtSongName);
        if (keyword.isEmpty()) {
            status("请先输入歌名");
            return;
        }
        status(ctx != null ? ctxString(R.string.searching) : "正在搜索…");
        txtResultHeader.setText(R.string.searching);
        new Thread(() -> {
            try {
                final List<MusicSearch.Song> hits = MusicSearch.search(keyword, 20);
                ctx.runOnUi(() -> {
                    searchHits = hits;
                    List<String> labels = new ArrayList<>();
                    for (MusicSearch.Song s : hits) labels.add(s.display());
                    songAdapter.clear();
                    songAdapter.addAll(labels);
                    songAdapter.notifyDataSetChanged();
                    if (hits.isEmpty()) {
                        txtResultHeader.setText(R.string.no_results);
                        status("未找到带试听的歌曲");
                    } else {
                        txtResultHeader.setText(ctxString(R.string.search_results)
                                + " · " + hits.size() + " 首");
                        status("找到 " + hits.size() + " 首，点选即播放");
                    }
                });
            } catch (Exception e) {
                ctx.runOnUi(() -> {
                    txtResultHeader.setText(R.string.search_results);
                    status("搜索失败：" + e.getMessage());
                });
            }
        }).start();
    }

    /** User tapped a search result: play its preview + auto-fetch synced lyrics. */
    private void onSongSelected(int pos) {
        if (pos < 0 || pos >= searchHits.size()) return;
        MusicSearch.Song song = searchHits.get(pos);
        if (song.previewUrl == null || song.previewUrl.isEmpty()) {
            status("这首歌没有可播放的试听片段");
            return;
        }
        stop();
        txtNowPlaying.setText(song.display());
        status("正在加载试听 + 匹配歌词…");
        track = null;
        lastLine = -2;

        // Lyrics in background (LRCLIB exact match by artist + title + duration).
        final int durationSec = song.durationMillis > 0 ? song.durationMillis / 1000 : 0;
        new Thread(() -> {
            final LrcLib.Result result = LrcLib.lookup(song.name, song.artist, durationSec);
            ctx.runOnUi(() -> {
                track = result.track;
                if (track.isEmpty()) {
                    status("未找到同步歌词 · 仅播放试听（来源：" + result.source + "）");
                } else {
                    status("歌词已加载 " + track.lines.size() + " 行（来源：" + result.source + "）"
                            + " · 点播放开始");
                }
            });
        }).start();

        preparePlayer(song.previewUrl);
    }

    // ------------------------------------------------------------- advanced flow

    private void loadFromUrl() {
        String musicUrl = text(txtMusicUrl);
        String lyricsUrl = text(txtLyricsUrl);
        if (musicUrl.isEmpty()) {
            status("请先填写音乐 URL");
            return;
        }
        stop();
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

    // ------------------------------------------------------------- playback

    private void preparePlayer(String url) {
        try {
            player = new MediaPlayer();
            player.setDataSource(url);
            player.setOnPreparedListener(mp -> {
                status("已就绪 · 点击播放（30 秒试听）");
            });
            player.setOnErrorListener((mp, what, extra) -> {
                status("播放错误：" + what + "/" + extra);
                return true;
            });
            player.setOnCompletionListener(mp -> {
                stopTicker();
                status("试听结束");
                if (renderer != null) renderer.close();
            });
            player.prepareAsync();
        } catch (Exception e) {
            status("无法打开音频：" + e.getMessage());
        }
    }

    private void play() {
        if (player == null) {
            status("请先搜索并点选一首歌");
            return;
        }
        try {
            if (!player.isPlaying()) {
                player.start();
                if (renderer != null && track != null && !track.isEmpty()) {
                    renderer.open(Lrc.toTeleprompterText(track),
                            track.title == null ? "Lyrics" : track.title);
                    lastLine = -2;
                }
                startTicker();
                status("播放中 · 歌词同步到镜片");
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
                String lyricText = track.lines.get(line).text;
                txtNowPlaying.setText(lyricText);
                renderer.highlight(line);
            }
        } catch (IllegalStateException ignored) {
            // player released mid-tick
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

    private static String text(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    private String ctxString(int resId) {
        try { return view.getResources().getString(resId); }
        catch (Exception e) { return ""; }
    }

    private void status(String s) {
        ctx.log("[" + TAG + "] " + s);
        if (txtStatus != null) txtStatus.setText(s);
    }
}
