# ARVIO Fork → Megaflix (Local-Files-Only) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or superpowers:executing-plans. The user has been executing inline in-session — follow that unless told otherwise. Steps use `- [ ]` checkboxes.

**Goal:** Fork ARVIO (native Kotlin/Compose Android TV app), strip every streaming/online feature, add a **local-file library** that feeds ARVIO's existing UI, and rebrand to Megaflix — so mom gets ARVIO's exact home/detail/player screens playing files off the TV's storage.

**Architecture:** Keep ARVIO's UI + its TMDB-metadata pipeline. ARVIO's `MediaItem` is keyed by **TMDB id**, so local files matched to TMDB flow through ARVIO's detail/poster/metadata machinery unchanged. Only two things are net-new: **(a) a local-library catalog** (scan storage → filename→TMDB match → `MediaItem`s with a local URI) and **(b) local playback** (when an item is local, play its file via ARVIO's Media3 player instead of `StreamRepository`'s addon resolution). Everything else is deletion + rebrand.

**Tech Stack:** Kotlin, Jetpack Compose (+ Compose-for-TV), Hilt, Media3/ExoPlayer 1.8, Room/DataStore (ARVIO's), TMDB v3. Gradle 8.13, AGP for compileSdk 36, JDK 17.

**Spec:** Approved in-session 2026-09-18 ("clone arvio, rip out what we don't need, make it local files only"). Validated: ARVIO builds from source here (102 MB debug, ~8 min) and runs on the `megaflix_tv` emulator.

## Reality check (read once)

This is the heaviest of every option considered. The result is a **~90 MB Compose app** — the opposite of the original "lightweight for a weak TCL" goal. Native Megaflix (4 MB, shipped at v0.3.1) still exists and still works; this fork is a parallel bet. The payoff is ARVIO's genuinely beautiful, finished screens. Gate 1 below measures it on the real TCL before deep investment — if it's sluggish there, stop and keep native Megaflix.

## Global Constraints

- Env preamble for every shell:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools; export PATH="$JAVA_HOME/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"`
- SDK 36 + build-tools 36.0.0 already installed. `secrets.properties` already created in the ref clone with the TMDB key `8b6f7e9a19bd57cca4cd213917274d13`.
- **Debug builds only** until Phase 5 (release needs a `key.properties`). Build: `./gradlew :app:assembleSideloadDebug` (the `sideload` flavor — NOT `play`).
- Fork's applicationId = `com.megaflix.tv2` so it coexists with native Megaflix (`com.megaflix.tv`) on the TCL during evaluation.
- Reference clone (read-only, source of truth for original code): `~/Projects/ARVIO-ref`. The working fork clone: `~/Projects/megaflix-tv2` (Phase 0 creates it).
- Emulator is unstable in this environment — it dies between sessions; reboot with `emulator -avd megaflix_tv -no-snapshot -gpu swiftshader_indirect` and wait for `sys.boot_completed=1`.
- Emulator plays **H.264 only** (no HEVC); the TCL hardware-decodes HEVC. Test playback with an H.264 file.
- Package base stays `com.arflix.tv` internally (renaming 354 files buys nothing); only `applicationId`, label, and branding assets change.
- License: ARVIO is **Apache-2.0** — fork may be public; keep `LICENSE` + add attribution in README.

## Key source map (in `~/Projects/ARVIO-ref/app/src/main/kotlin/com/arflix/tv/`)

- `data/model/Models.kt` — `MediaItem` (id=TMDB int, title, overview, year, rating, image=poster, backdrop, progress, mediaType, genreIds…). **No local path field — Phase 2 adds one.**
- `data/repository/CatalogRepository.kt` — `getCatalogs()` / `getCatalogsForProfile()` return `CatalogConfig` list that drives home rows.
- `data/repository/MediaRepository.kt` — fetches item details/metadata by TMDB id (works for local items once matched).
- `data/repository/StreamRepository.kt` — resolves playable streams from addons by imdb/tmdb id. **Local playback bypasses this.**
- `ui/screens/home/HomeViewModel.kt` (257 KB) + `HomeScreen.kt` (184 KB) — consume catalogs → `MediaItem`s. Do NOT rewrite; feed them.
- `ui/screens/details/` , `ui/screens/player/` — detail + player screens (kept).
- `app/src/sideload/kotlin/…/core/plugin/cloudstream/` — the addon/plugin system (deleted in Phase 3).

---

## Phase 0 — Fork + baseline (mostly done; formalize it)

**Goal:** our own buildable fork on disk.

- [ ] **Step 1:** `gh repo fork ProdigyV21/ARVIO --clone=false` → then `git clone https://github.com/T31K/ARVIO ~/Projects/megaflix-tv2` (or clone ref and re-point origin). Copy `secrets.properties` from `~/Projects/ARVIO-ref`.
- [ ] **Step 2:** Build baseline: `cd ~/Projects/megaflix-tv2 && ./gradlew :app:assembleSideloadDebug`. Expect SUCCESS (~8 min first time). APK at `app/build/outputs/apk/sideload/debug/`.
- [ ] **Step 3:** Install + run on emulator, create a profile, confirm Home/Search/Library/TV/Settings render (validated in-session already).
- [ ] **Step 4:** Commit the un-forked baseline as the fork's starting point (no code changes yet).

---

## Phase 1 — Local library ingestion (the core new subsystem)

**Goal:** a "My Library" home row populated by files on the device, matched to TMDB for art/metadata. **Playback not required yet** — this phase proves local files can appear in ARVIO's UI.

- [ ] **Step 1 — Add a local path to the model.** In `data/model/Models.kt`, add to `MediaItem`: `val localUri: String? = null` (default null keeps every existing call-site compiling). This is the flag that marks an item as local and carries its playable URI.
- [ ] **Step 2 — Write `LocalLibraryRepository`.** New file `data/repository/LocalLibraryRepository.kt` (Hilt `@Singleton`, injected like the others — copy the annotation/constructor pattern from `MediaRepository`). Responsibilities:
  - Scan video files via `MediaStore.Video` (READ_MEDIA_VIDEO permission — add to manifest) — mirror Megaflix's `media/` scanner logic (available in the native repo at `~/Projects/Megaflix/app/src/main/java/com/megaflix/tv/media/` for reference).
  - For each file: parse title/year from filename (port Megaflix's `FilenameParser`), call TMDB search (reuse ARVIO's TMDB client — find it via `grep -rl "api_key\|themoviedb" data/`), build a `MediaItem` with the TMDB id + metadata + `localUri = file uri`.
  - Cache results (Room or in-memory) so scans aren't repeated every home load.
  - Expose `suspend fun getLocalCatalog(): List<MediaItem>`.
- [ ] **Step 3 — Inject a local catalog into home.** In `CatalogRepository.getCatalogsForProfile()`, prepend a synthetic `CatalogConfig` (id e.g. `"local_library"`, title "My Library"). Where catalogs are resolved into rows (trace `getCatalogs()` consumers in `HomeViewModel.kt` — grep `local_library`/`CatalogConfig` usage), branch: if the catalog id is `local_library`, source its items from `LocalLibraryRepository` instead of the TMDB/addon path.
- [ ] **Step 4 — GATE 1 (measure on the TCL):** build, sideload on the emulator AND the real TCL. Confirm the "My Library" row shows local files with real posters. Measure launch time, scroll smoothness, and `dumpsys meminfo` on the TCL vs native Megaflix. **If it's sluggish on the TCL, STOP** — the weight isn't worth it; keep native Megaflix. If acceptable, continue.
- [ ] **Step 5:** Commit.

---

## Phase 2 — Local playback

**Goal:** selecting a local item plays the file (not an addon stream).

- [ ] **Step 1 — Trace playback.** From `DetailsScreen`/`PlayerViewModel`, find where a Play action calls `StreamRepository` to resolve a stream URL. (grep `StreamRepository` + `PlayerViewModel` for the resolve/launch path.)
- [ ] **Step 2 — Divert for local.** At that call site: `if (mediaItem.localUri != null) { play localUri directly }` else the existing stream-resolution path. ARVIO's player is Media3/ExoPlayer (`ui/screens/player/engine/exoplayer/`), so feed the local URI as a `MediaItem`/`MediaSource` the same way it feeds a resolved stream URL — reuse their player launch, just swap the URL source.
- [ ] **Step 3 — Progress/resume.** ARVIO tracks `progress` on `MediaItem` and has a continue-watching store; verify local items persist progress through the same path (they should, since it's keyed by item id). Fix if the continue-watching store assumes online items.
- [ ] **Step 4:** Emulator gate — play an H.264 local file end to end, back out, confirm resume. Commit.

---

## Phase 3 — Strip everything online

**Goal:** remove features mom will never use; shrink surface + APK.

Delete/disable, each its own commit + rebuild-green:
- [ ] **Step 1 — Addons/plugins:** remove the `sideload` flavor's `core/plugin/cloudstream/` and addon repositories (`AddonRuntime*`, `PluginStreamSource`, `ExternalExtension*`). Remove addon UI from Settings.
- [ ] **Step 2 — IPTV / Live TV:** remove `Iptv*` repositories, the TV nav destination, EPG code.
- [ ] **Step 3 — Trakt / Simkl / MDBList:** remove those repositories + their Settings › Accounts entries.
- [ ] **Step 4 — Cloud sync / Supabase / Discord SDK:** remove `CloudSync*`, `AuthRepository`, Supabase deps, Discord social SDK, the "Connect to ARVIO Cloud" / sign-in flows.
- [ ] **Step 5 — Home servers:** remove `HomeServerRepository` (Jellyfin/Emby/Plex) + Settings entries.
- [ ] **Step 6 — Telegram, sports, web module, mobile flavor:** remove `Telegram*`, sports repos, the `netlify-*`/`web` dirs (not part of the Android build but drop from the fork), and the `mobile` product flavor + `pages`/mobile-only UI.
- [ ] After each: `./gradlew :app:assembleSideloadDebug` must stay green (deletions cascade — expect to remove references, DI bindings, nav entries). Reduce Settings to: Playback, Language, Subtitles, Profiles, About/Update.
- [ ] **Step 7:** Rip the remaining online catalogs so Home shows ONLY "My Library" (and optionally a TMDB "Discover" row if wanted — decide with user; default: local only). Commit.

---

## Phase 4 — Rebrand to Megaflix

- [ ] **Step 1:** `applicationId = "com.megaflix.tv2"` in `app/build.gradle.kts`.
- [ ] **Step 2:** App label "Megaflix"; replace `res/drawable*/ic_banner*` with the MEGAFLIX 320×180 banner and launcher icons.
- [ ] **Step 3:** In-app name/wordmark: replace the "ARVIO" logo asset + any `appName` string. Optionally set accent to Megaflix red (`#E50914`) in the Compose theme, or keep ARVIO's Arctic Fuse white — user's call.
- [ ] **Step 4:** README: credit ARVIO (Apache-2.0). Commit.

---

## Phase 5 — Ship + OTA

- [ ] **Step 1 — Signing:** add `key.properties` pointing at the debug keystore (`~/.android/debug.keystore`, alias `androiddebugkey`, pass `android`) — ARVIO's `signingConfigs.release` reads it (see `app/build.gradle.kts`).
- [ ] **Step 2 — Release build:** `./gradlew :app:assembleSideloadRelease`. Note the APK size (target: well under upstream's 92 MB after stripping — report actual).
- [ ] **Step 3 — Distribute:** ARVIO's updater reads `REPO_AUTHOR`/`REPO_NAME` and polls that repo's GitHub Releases (`lib/const.dart`… no — ARVIO is Kotlin; find its updater in `updater/` and its repo config). Point it at `T31K/megaflix-tv2` and publish via `gh release create`. OR reuse Megaflix's `version.json`/Pages pattern — user's call. Sideload the signed APK on the TCL.
- [ ] **Step 4:** Decide with user: does this replace native Megaflix on the TV, or coexist?

---

## Risk register

| Risk | Severity | Mitigation |
|---|---|---|
| ~90 MB Compose app too heavy for the low-end TCL | **High** | Gate 1 measures on the real panel before deep work; fall back to native Megaflix |
| Local ingestion doesn't cleanly fit ARVIO's catalog model | **High** | Phase 1 is the feasibility gate; the TMDB-id keying makes detail/metadata free, only ingestion+playback are new |
| Stripping cascades break Hilt graph / nav | Medium | One feature per commit, rebuild green each time |
| TMDB client in ARVIO differs from Megaflix's | Low | Reuse ARVIO's own TMDB client for matching (find via grep) |
| USB storage access on the TCL (scoped storage) | Medium | Same MediaStore/permission story Megaflix already solved; port that |
| Effort is days, not hours | — | Phases are independently valuable; stop at any gate |

## Effort estimate

| Phase | Effort | Gate |
|---|---|---|
| 0 fork + baseline | done / ~1 h | builds |
| 1 local ingestion | 1–2 days | **Gate 1: looks good + fast on TCL** |
| 2 local playback | 0.5–1 day | plays a file |
| 3 strip online | 1–2 days | stays green, surface reduced |
| 4 rebrand | 2–4 h | branded |
| 5 ship + OTA | 2–4 h | self-updating APK on TV |

**Bottom line:** feasible because ARVIO is native + Apache-2.0 + TMDB-keyed. The make-or-break is Gate 1 (Phase 1) — get local files into ARVIO's home and judge speed on the real TCL before committing to the strip. Native Megaflix keeps running regardless.
