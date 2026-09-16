# Megaflix

A lightweight, hand-rolled Netflix-style media player for **Android TV** (TCL Google TV),
built in Kotlin with classic Android Views (no Compose) for near-instant cold start and
zero D-pad lag. Plays local video files directly — no server, no transcoding.

## Download (demo build)

**→ https://t31k.github.io/megaflix**

The demo APK is fully self-contained: it ships with a bundled sample clip and poster art,
needs no network and no storage permission. Install it on a Google TV to see the poster
grid, D-pad focus, and full-screen ExoPlayer playback.

## Tech

- Kotlin + Android Views (RecyclerView poster grid, custom D-pad focus)
- Media3 / ExoPlayer for direct file playback
- Room for the local library index + watch progress
- Swappable `MediaSource`: `DevMediaSource` (emulator), `UsbHddSource` (USB HDD, Phase 4),
  `DemoMediaSource` (bundled demo)

## Build

```bash
./gradlew assembleRelease
```

Release APK: ~3 MB. See `docs/megaflix-spec.md` for the full plan.
