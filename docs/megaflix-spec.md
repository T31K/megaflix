# Megaflix — Spec (v2, Views pivot)

Personal Netflix-style Android TV app. Final form: sideloaded APK on a TCL Google TV, reading movie files directly from a USB HDD plugged into the TV. No server, no network playback — local files only. TMDB used once (online) to fetch posters/metadata, then cached so browsing works fully offline.

**Build order matters: Phase 1 must run entirely on a Mac using the Android TV emulator and a folder of test .mp4 files. The real USB-HDD code gets written now but stays disabled behind a flag until Phase 4.**

## v2 decision — stack pivot (2026-09-16)

**Jetpack Compose is OUT. The UI is hand-rolled with classic Android Views** (custom-styled RecyclerViews, no Leanback stock widgets, no Compose runtime). Reason: the target is a low-end TCL Google TV SoC and the owner's top priorities are near-instant cold start and zero D-pad lag. The View system is the lightest practical toolkit on Android; Compose ships a composition runtime that costs cold-start time and first-composition jank on weak chips. We accept ~30–40% more UI code in exchange.

Everything else from v1 stands: ExoPlayer, Room, Coil, the MediaSource abstraction, the phase order.

## Tech stack

- Kotlin, classic Android Views (RecyclerView-based rows/grids, custom focus handling) — **no Compose, no Leanback widgets, no Flutter/RN**
- Media3 / ExoPlayer for playback
- Room for the local library DB (file index, metadata, watch progress)
- Retrofit or Ktor client for TMDB API (Phase 2 only, used only during library scan)
- Coil for image loading (Phase 2; posters cached to disk)
- Min SDK 27 (Android TV), target latest
- Single module, manual DI (a plain `Graph` object). Boring and readable.

## Performance rules (the point of the pivot)

- Cold start budget: ≤ ~600 ms to first frame on the TCL. No splash-screen busywork, no init that can be lazy.
- 60 fps D-pad scrolling: RecyclerView with stable ids + DiffUtil, no allocation in `onBindViewHolder`, focus animations via `ViewPropertyAnimator` (hardware layer), `clipChildren=false` for scale overflow instead of re-layout.
- Release builds: R8/minify on. No baseline profile needed (that was a Compose mitigation).
- No libraries beyond the list above. Every dependency must justify itself.

## Architecture: the MediaSource abstraction (key design decision)

Everything reads files through one interface so the Mac test version and the real HDD version are swappable:

```kotlin
interface MediaSource {
    suspend fun listVideoFiles(): List<VideoFile> // path, filename, size, modified
    fun openPlayableUri(file: VideoFile): Uri
}
```

Two implementations:

1. **DevMediaSource** — ACTIVE in Phase 1. Lists videos under `Movies/` inside the Android TV emulator via MediaStore (files pushed via adb from the Mac). MediaStore (not raw `File` I/O) because scoped storage on API 30+ blocks raw reads of `/sdcard` without all-files access; on the emulator the `READ_MEDIA_VIDEO` runtime permission + content URIs is the clean path and exercises the same permission UX.
2. **UsbHddSource** — WRITTEN NOW, FULLY IMPLEMENTED, disabled behind `BuildConfig.USE_USB_SOURCE`. It:
   - Enumerates mounted external volumes via `StorageManager.storageVolumes`, picks the removable USB volume
   - Recursively scans for video extensions (.mp4, .mkv, .avi, .mov, .webm)
   - Permission path: `MANAGE_EXTERNAL_STORAGE` (fine for a sideloaded personal app; settings-intent flow included), with a Storage Access Framework folder-picker fallback (`SafMediaSource`)
   - Every block commented explaining what and why, so Phase 4 is "flip the flag", zero rewrites

Selection is one line in `Graph`:

```kotlin
val source: MediaSource = if (BuildConfig.USE_USB_SOURCE) UsbHddSource(ctx) else DevMediaSource(ctx)
```

## Phase 1 — Mac test version (do this first, end-to-end)

Goal: full working app in the Android TV emulator on macOS, playing a handful of local .mp4s.

1. Scaffold the project (plain Views, no template baggage).
2. Implement DevMediaSource (MediaStore over `Movies/`).
3. Helper script `push-test-media.sh`: pushes every .mp4 from `~/megaflix-test-media/` into the emulator and triggers a media scan.
4. Library scan pipeline: list files → parse title/year from filename → store in Room.
5. Basic UI: poster grid (placeholder posters fine), click → ExoPlayer full-screen playback with D-pad controls (play/pause, seek, back).
6. Watch progress: save position on pause/exit, resume on reopen.

Exit criteria: launch emulator, open Megaflix, see the test movies, play one, quit mid-way, reopen, resume works.

Implementation plan: `docs/superpowers/plans/2026-09-16-megaflix-phase1.md`

## Phase 2 — TMDB metadata

1. Filename parser: strip quality tags/release junk (`1080p`, `x264`, `WEB-DL`, dots→spaces), extract title + year. (Parser is actually built in Phase 1; Phase 2 consumes it for search.)
2. TMDB `/search/movie` per title (API key from `local.properties`, never hardcoded).
3. Store poster URL, backdrop, overview, rating, genres, runtime in Room; download poster images to app storage so the library browses offline afterwards.
4. Unmatched files still appear, with filename + generated placeholder card.
5. Rescan action in settings (picks up newly added files, only queries TMDB for new ones).

## Phase 3 — the Netflix-style UI (this is the point of the app)

Hand-rolled with Views, aiming for genuinely premium — real spacing, gradient scrims, no default-Material look, no Leanback stock look:

- Home screen: full-bleed hero banner (random/recent pick, backdrop image, title, Play + Info buttons), then horizontal rows: Continue Watching, Recently Added, one row per genre. Structure: outer vertical `RecyclerView`, each row an inner horizontal `RecyclerView` with a shared `RecycledViewPool`.
- Focus behavior is everything on TV: cards scale up slightly + border glow on D-pad focus (ViewPropertyAnimator + selector foreground), smooth row scrolling with custom `smoothScrollToPosition` timing.
- Detail screen: backdrop, poster, overview, rating, runtime, Play/Resume.
- Search screen (on-screen keyboard, filters local library).
- Dark theme only. App name/branding: **Megaflix**.

## Phase 4 — real TV deployment (later, not now)

1. Flip `USE_USB_SOURCE = true` (one line in `app/build.gradle.kts`).
2. Build release APK, sideload to the TCL TV (adb over LAN or USB stick + file manager).
3. First-run: grant all-files access, run scan against the real drive (TV needs internet once for TMDB).
4. Handle drive-not-mounted state gracefully ("Plug in your drive" screen, auto-refresh on mount broadcast).

## Notes / constraints

- Playback must be direct file playback only — no transcoding, no server. If a codec isn't hardware-supported, surface ExoPlayer's error; don't fix it in-app.
- Everything must be navigable by D-pad remote only (no touch assumptions).
- One module, boring and readable. No multi-module, no DI framework.
- Commit after each phase so it's easy to bisect.
