# Music Glass

An Android app for the Meizu MYVU / Star Air glasses (model XGA010C) that plays a song and shows the lyrics, line by line, on the glasses lens.

It is built on the reverse-engineered, unofficial MYVU Bluetooth protocol — no official app required on the phone (but force-stop the official app while using this one: the glasses accept a single BLE central at a time).

## Features

1. Connect to the glasses over BLE + classic-BT relay (inherited, hardware-verified connection stack).
2. **Search by song name** — type a title (e.g. `晴天 周杰伦`) and pick from results. Uses the official iTunes Search API for a **30-second legal preview** clip.
3. **Automatic lyrics** — time-synced LRC fetched from [LRCLIB](https://lrclib.net) (free, key-free, open source) matched by artist + title + duration.
4. **Line-by-line on the lens** — the current lyric line highlights on the glasses via the teleprompter highlight API as playback advances.
5. **Advanced mode** — paste a direct mp3/m4a URL and an optional LRC URL for sources search can't reach.

> Why 30-second previews? Full-length MP3s from the Chinese streaming platforms are copyright-restricted; their free APIs now return empty links (verified 2026-11). iTunes previews are the only fully legal, key-free playable audio source — enough to validate the lyric-sync experience. For full songs, extend `MusicSearch` with your own authorized music source.

## Build

The project compiles in the cloud with GitHub Actions (no local Android SDK needed):

- Push to `main`/`master`, or run the **Build APK** workflow manually.
- Download the `music-glass-debug-apk` artifact from the completed run.

Local build (if you have JDK 17 + Android SDK 34):

```sh
./gradlew assembleDebug
```

## Use

1. Force-stop the official glasses app and disconnect other paired phones.
2. Open the app, enter the glasses MAC, tap **Connect**.
3. Type a song name in the search box (e.g. `晴天 周杰伦`) and tap **搜索**.
4. Tap a result → it loads the 30-second preview and auto-fetches synced lyrics.
5. Tap **播放** — the current lyric line highlights on the lens as the song advances.

Notes:
- Search uses the HK iTunes store first (best Chinese coverage), then US. A song needs a preview clip to appear in results.
- Lyrics come from LRCLIB. Chinese songs may have no entry there — in that case only the preview plays. You can still use **高级：手动输入 URL** to paste your own LRC link.
- Manual mode: music URL must be a direct file URL; the LRC URL must return plain LRC text, e.g. `[00:12.34]歌词`.

## Adding features later (extension points)

The app is deliberately structured for growth:

- **`GlassesFeature`** (`com.myvu.client.feature`): implement it to add a capability. It receives a `FeatureContext` giving access to the live connection, UI-thread posting, and logging.
- **`FeatureRegistry`**: change lifecycle dispatch. Register your feature in `MusicActivity.onCreate()`:

  ```java
  registry.add(new YourFeature());
  LinearLayout container = findViewById(R.id.featureContainer);
  View v = yourFeature.createView(this);
  if (v != null) container.addView(v);
  ```

- **`LyricRenderer`** (`com.myvu.client.music`): swap how lyrics reach the lens. The default `TeleprompterLyricRenderer` uses the teleprompter scene; a future renderer could use the caption scene.

Every feature goes through `ConnectionManager`, which marshals all protocol traffic onto a single thread — no extra locking needed.

## Credits

Connection, protocol, crypto and teleprompter code come from [Panny777/Meizu-Myvu-Client](https://github.com/Panny777/Meizu-Myvu-Client) (MIT). This project adds the music/lyrics feature and the plugin structure.
