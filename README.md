# Music Glass

An Android app for the Meizu MYVU / Star Air glasses (model XGA010C) that plays an online audio stream and shows the lyrics, line by line, on the glasses lens.

It is built on the reverse-engineered, unofficial MYVU Bluetooth protocol — no official app required on the phone (but force-stop the official app while using this one: the glasses accept a single BLE central at a time).

## Features

1. Connect to the glasses over BLE + classic-BT relay (inherited, hardware-verified connection stack).
2. Play a network audio stream (mp3/m4a direct URL).
3. Download an LRC lyric file, sync it to playback position, and push the current line to the lens via the teleprompter highlight API.

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
3. Enter a music URL and (optionally) an `.lrc` lyric URL, tap **Load**, then **Play**.
4. The current lyric line highlights on the lens as the song advances.

Music URL must be a direct file URL, not a streaming-service page. The lyric URL must return plain LRC text, e.g. `[00:12.34]歌词`.

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
