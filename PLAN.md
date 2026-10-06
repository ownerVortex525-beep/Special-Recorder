# NYX-RECORDER — Complete App Development Plan

**Repo:** https://github.com/ownerVortex525-beep/Special-Recorder
**Package:** `com.ownervortex.nyxrecorder`
**Min SDK:** 26 (Android 8.0) → **Target/Compile SDK:** 36 (Android 16) — runs smoothly on the latest Android
**Language/UI:** Kotlin 2.0.21 + Jetpack Compose (Material 3), dark-first theme
**Build:** GitHub Actions (`gradle assembleDebug`) → APK artifact

---

## 1. Product Identity

| Item | Value |
|------|-------|
| App name | NYX-RECORDER |
| Output folder | `Movies/NYXRecorder/` |
| File naming | `NYX_yyyyMMdd_HHmmss.mp4` |
| Brand colors | BG `#0D0F12`, Surface `#171A1F`, Elevated `#22262D`, Primary `#6C63FF`, Record red `#FF3B30`, Success `#34C759`, Warn `#FFCC00`, Text `#FFFFFF` / `#A7ADB7` |

Design principles: one primary action per screen, recording state never hidden, haptics on record actions, smooth animated buttons, all data stays on-device.

---

## 2. Tech Stack

| Layer | Choice | Why |
|---|---|---|
| Capture | `MediaProjection` + `VirtualDisplay` | System screen-capture, consent per session |
| Video encode | `MediaCodec` (H.264/AVC) + `MediaMuxer` | Hardware encode, low CPU/heat on 3 GB devices |
| Audio | `AudioRecord` (mic + Android 10+ internal audio via `AudioPlaybackCaptureConfiguration`) + AAC `MediaCodec`, mixed with optional background music | Off/Mic/Internal/Mic+Internal + music bed |
| Service | Foreground service `foregroundServiceType="mediaProjection|microphone|camera"` (types passed dynamically at `startForeground`) | Android 14+ requirement, survives backgrounding |
| Playback | Media3 ExoPlayer 1.5.1 + `PlayerView` | In-app player, live effect preview via `setVideoEffects` |
| Editing/Export | Media3 Transformer 1.5.1 | Trim/crop/rotate/speed/filter/text/resize burn-in |
| Settings | `SharedPreferences` wrapper (`SettingsStore`) | Zero-dep, fast, synchronous |
| Library data | `MediaStore` scoped storage (API 29+) / legacy `File` + `MediaScanner` (API 26–28) | No storage permission needed for own files |
| Thumbnails | `MediaMetadataRetriever` → scaled JPEG cache in `cacheDir/thumbs` | No full decode in UI, smooth lists |

Dependency versions (all proven in this repo's CI): AGP 8.7.3, Compose BOM 2025.01.00, core-ktx 1.15.0, activity-compose 1.10.0, lifecycle 2.8.7, media3 1.5.1, `material-icons-extended`. `android.suppressUnsupportedCompileSdk=36` in gradle.properties.

---

## 3. Feature Set (Full Powers)

### 3.1 Home
- Animated pulsing **Record** button (haptic feedback, state-aware: Start / Recording).
- Quality presets: **720p30 (4 Mbps)**, **1080p30 (6 Mbps)**, **1080p60 (10 Mbps)** — resolution scaled from device, forced even dimensions, capped at 1080p/720p long edge (heat/RAM safe).
- Toggles: Microphone, Internal audio (Android 10+), Floating bubble, FaceCam, Countdown (3-2-1), Touch indicator (writes `Settings.System.SHOW_TOUCHES` with `WRITE_SETTINGS` flow), background music picker.
- Pre-flight checks: free storage < 500 MB → warning dialog; thermal status ≥ SEVERE → warning chip; permission chaining (mic → notifications → overlay) before capture consent.
- Live recording banner (elapsed timer, pause/resume, stop) while service runs.
- Recent recordings carousel with cached thumbnails.
- Device health chips: free storage, thermal state.

### 3.2 Recording engine (`ScreenCaptureService`)
- Full pipeline: projection → VirtualDisplay → surface-input MediaCodec (H.264, bitrate/I-frame from preset) → `MediaMuxer`.
- **Crash-safe output**: API 29+ `MediaStore` insert with `IS_PENDING` (cleared on finalize); API 26–28 direct `File` in `Movies/NYXRecorder` + media scan. Idempotent `finalizeOutput()` guarded by flag; partial recordings finalized on any stop path.
- **Proper pause (timeline excision)**: drains continue while paused, samples discarded, presentation times re-based on resume → paused period is *removed* from the file (no frozen frames, no gaps). Same re-basing applied to audio track. Writes serialized under one muxer lock (video/audio threads).
- Audio pipeline (`AudioMixerEncoder`): per-source reader threads (mic / internal-audio playback capture / music decoder) feed bounded `BlockingQueue`s; mixer thread sums 16-bit stereo frames with clamps, feeds AAC encoder, discards+re-bases PTS while paused. Music decoded on demand into frames (bounded memory — never holds whole track).
- Crash recovery: EOS drain with 2 s latch on stop, all release steps wrapped, `MediaProjection.Callback.onStop` → same idempotent stop path.
- Notification: elapsed time (updates every second), pause/resume + stop actions, open-app action, "Paused" state visible.

### 3.3 Floating bubble (`BubbleController`, hosted inside the foreground service)
- Small draggable dot with snap-to-edge, `FLAG_SECURE` (never appears in the recording itself).
- Tap → control panel: elapsed time, Pause/Resume, Stop, Hide bubble.
- Optional FaceCam window (legacy `Camera` preview, **no** FLAG_SECURE so it *is* captured), only when enabled + `CAMERA` granted; failures degrade silently.

### 3.4 Library (Recordings)
- Grid of recordings from `Movies/NYXRecorder` (query-only, no file loads): thumbnail, name, duration, size, resolution.
- Search + filters (All / Favorites), sort (newest/oldest/largest/name).
- Item actions via bottom sheet/menu: Play (in-app), Edit, Rename (`DISPLAY_NAME` update), Share, Favorite, Details dialog, Delete (confirmed).
- **Import** any video via SAF (`ACTION_OPEN_DOCUMENT`) → straight to editor. No broad storage permission required.
- Favorites persisted in `SettingsStore`.

### 3.5 Player
- Media3 `PlayerView` in-app: play/pause, seek, fullscreen scaling, back releases player.
- Top actions: Edit, Share, Details.

### 3.6 Editor
- Preview: ExoPlayer with trim clipping + **live filter preview** (`ExoPlayer.setVideoEffects`).
- Tools:
  - **Trim** — dual sliders + precise ms labels (preview clipped to range).
  - **Speed** — 0.5× / 1× / 1.5× / 2× (export uses `SpeedChangeEffect` + `SpeedChangingAudioProcessor` so audio stays in sync; excluded from preview as Media3 documents it unsupported for previewing).
  - **Filters** — Original, Grayscale, Invert, Bright, Vivid, Warm, Cool, Contrast (`RgbFilter` / `HslAdjustment` / `Brightness` / `Contrast`).
  - **Rotate** — 0/90/180/270 (`ScaleAndRotateTransformation`).
  - **Crop** — Original, 1:1, 9:16, 16:9, 4:5 (`Crop` normalized rect).
  - **Text overlay** — text, color chips, size, top/center/bottom placement → rendered to bitmap → `BitmapOverlay` (pixels map 1:1, so sizes are predictable).
  - **Resize export** — Keep source / 720p (`Presentation.createForWidthAndHeight`).
  - **Mute audio** toggle.
- **Export**: single `Transformer` job into `cacheDir` → copy to `MediaStore Movies/NYXRecorder` → success sheet (Play / Share / Done). Progress polled via `Transformer.getProgress(ProgressHolder)` every 300 ms; Cancel via `Transformer.cancel()`; failures show message + stay in editor (draft state preserved).

### 3.7 Music tab
- Lists device audio (`READ_MEDIA_AUDIO`, requested only here), preview play/stop (player released on dispose), "Use for recording" persists selection for the audio mixer.

### 3.8 Settings
- Recording defaults (quality, countdown, mic, internal audio, bubble, FaceCam, touch indicator, music volume).
- Permission status cards with one-tap open: Microphone, Overlay, Notifications, Music access, Write settings.
- Storage: bytes used by app recordings, free space, thumbnail cache size + clear.
- About + privacy statement: no network permission at all; recordings never leave the device.

---

## 4. Architecture

```
app/src/main/java/com/ownervortex/nyxrecorder/
├── NixApp.kt                 Application: notification channels, SettingsStore init
├── MainActivity.kt           Edge-to-edge host, keep-screen-on while recording
├── ui/AppRoot.kt             Tab nav + overlay routes (Player/Editor), BackHandler
├── ui/theme/Theme.kt         Dark-first Material3 color scheme
├── ui/components/CommonUi.kt RecordButton (pulse), ToggleRow, chips, ThumbBox, dialogs, empty states
├── ui/home/HomeScreen.kt     Start flow, presets, toggles, health, recents, status banner
├── ui/library/RecordingsScreen.kt
├── ui/player/PlayerScreen.kt
├── ui/editor/EditorScreen.kt
├── ui/music/MusicScreen.kt
├── ui/settings/SettingsScreen.kt
├── ui/viewmodel/AppViewModels.kt   RecordingsViewModel, MusicViewModel
├── core/recording/RecordingConfig.kt      @Parcelize config
├── core/recording/RecordingController.kt  start/pause/resume/stop intents
├── core/recording/RecordingStatus.kt      global MutableStateFlow status
├── core/recording/ScreenCaptureService.kt capture engine + notification + lifecycle
├── core/recording/AudioMixerEncoder.kt    mic/internal/music mixing + AAC + PTS re-base
├── core/overlay/BubbleController.kt       draggable bubble + panel + FaceCam
├── core/util/DeviceHealth.kt              storage / thermal / size helpers
├── core/util/Constants.kt
├── data/Recording.kt / RecordingRepository.kt / MusicRepository.kt
├── data/SettingsStore.kt                  SharedPreferences (favorites, defaults)
├── data/ThumbnailCache.kt
└── export/ExportManager.kt                Transformer job + progress + MediaStore save
```

Single-activity Compose navigation (tabs + full-screen overlay routes) — no nav dependency.

---

## 5. Permissions (requested only when needed)

```
FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PROJECTION, FOREGROUND_SERVICE_MICROPHONE,
FOREGROUND_SERVICE_CAMERA, RECORD_AUDIO, CAMERA, SYSTEM_ALERT_WINDOW,
POST_NOTIFICATIONS, WRITE_EXTERNAL_STORAGE (≤32), READ_EXTERNAL_STORAGE (≤32),
READ_MEDIA_AUDIO, READ_MEDIA_VISUAL_USER_SELECTED, WRITE_SETTINGS, VIBRATE
```
No `INTERNET` — the app is fully offline. Own-folder MediaStore access needs no runtime permission; SAF used for import.

---

## 6. Low-RAM / Heat Rules (3 GB devices)

- Defaults: 1080p max, 30 FPS, 4–6 Mbps, hardware encoder, no live preview while recording.
- Thumbnails generated lazily, scaled to 480 px, cached on disk (never full-res bitmaps).
- One export at a time; music decoded frame-wise (bounded queues, never whole-file PCM).
- Memory-pressure check (`ActivityManager.MemoryInfo`) disables previews; thermal ≥ SEVERE shows warning and suggests pause.
- Service stops and releases all codecs/muxer/audio immediately on stop; output finalized exactly once.

---

## 7. Error Handling

| Case | Behavior |
|---|---|
| Capture consent denied | Snackbar: "Screen permission is required to record" + retry |
| Mic denied | Recording proceeds without mic; snackbar notice |
| Overlay not granted | Settings page opened before start; bubble auto-disabled if declined |
| Low storage (<500 MB) | Pre-flight dialog: Continue / Cancel / Manage storage |
| Projection revoked / service killed | Idempotent finalize keeps the partial file, marked in library |
| Export failure | Message stays in editor with draft intact, Retry available |
| Missing audio source (no RECORD_AUDIO) | Audio track silently skipped via start-time fallback |

---

## 8. Roadmap

- **Phase 1 (this build):** complete recorder (service, audio mixing, bubble, FaceCam, library, player, editor with all tools, export, music, settings, health checks) — production-quality MVP.
- **Phase 2:** timeline markers, multi-clip merge, transitions, voice-over recording, draft projects (Room).
- **Phase 3:** LUT filter packs + animated text (asset packs in `Other recorder files for analyse features/raw/*.json` as reference), export queue/background WorkManager export, tablet/foldable layouts, accessibility pass, crash analytics (opt-in).

---

## 9. CI/CD (GitHub Actions)

`.github/workflows/build-apk.yml` — on push to `main` + manual dispatch: Temurin JDK 17 → `gradle/actions/setup-gradle@v4` → `gradle assembleDebug --stacktrace` → upload `app-debug.apk` artifact (retained). Every push produces an installable APK from a clean tree.

## 10. Release Checklist

- [ ] Physical test: Android 8, 10, 13, 16
- [ ] 3 GB RAM device: 30-min 1080p30 recording, memory profile
- [ ] Pause/resume produces seamless timeline; no A/V drift
- [ ] Lock screen / rotate / app-switch during recording
- [ ] Notification pause & stop actions
- [ ] Permission denials (mic, overlay, notifications, write-settings)
- [ ] Low storage, projection revoke, process kill → partial file kept
- [ ] Export: trim+filter+speed+text+crop, cancel mid-export
- [ ] No recording continues after stop; temp files cleared
- [ ] Videos visible in system gallery; share/rename/delete work
