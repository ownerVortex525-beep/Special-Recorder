# NYX-RECORDER

Full-featured screen recorder + video editor for Android 8.0+ (built for Android 16, SDK 36).

## Features

**Recording**
- Screen capture via MediaProjection, up to device resolution (Auto / 1080p / 720p / 480p at 30 fps)
- Pause / resume with clean timestamps (paused time is excised from the file)
- Audio: microphone, internal device audio (Android 10+), background music with volume control — mixed into one AAC track
- Floating control bubble (hidden from the recording itself) with pause / stop / hide
- Optional face-cam window (front camera, captured into the video)
- Foreground service with elapsed-time notification
- Storage-aware: refuses to start below 500 MB free, writes to `Movies/NYXRecorder`
- Countdown, touch indicator, low-storage / thermal checks

**Library**
- Grid list with thumbnails, duration, resolution and size
- Play, rename, share, favorite, delete

**Editor** (media3 Transformer)
- Trim, speed (0.5x–2x), filters (B&W, invert, warm, cool, vivid, dark)
- Brightness / contrast, crop presets (4:3, 1:1, 9:16), 720p downscale, watermark
- Export with live progress to `Movies/NYXRecorder`

## Tech

- 100% Kotlin + Jetpack Compose (Material 3), single-activity
- No Room / DataStore / navigation framework — SharedPreferences + manual routes
- media3 1.5.1 (ExoPlayer, Transformer, effects), minSdk 26, compile/targetSdk 36
- No INTERNET permission — nothing ever leaves the device

## Build

```bash
gradle assembleDebug          # locally, or push to `main` and let CI build
```

CI: `.github/workflows/build-apk.yml` builds on every push to `main` and uploads
`NYX-RECORDER-debug-<run>.apk` as an artifact.

See [PLAN.md](PLAN.md) for the full architecture plan.
