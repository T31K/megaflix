# ARVIO Component Transplant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task — unless the human has said to execute inline in-session, which they did for the previous plan; ask nothing, follow the same mode they request this time. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-skin Megaflix's three core surfaces — home rows, detail page, player controls — using ARVIO's open-source Compose design system (theme, TV focus handling, MediaCard, hero layout, TV player controls), while keeping 100% of Megaflix's existing data layer (scan → Room → TMDB enrichment → resume → OTA updater) untouched.

**Architecture:** Megaflix becomes a **Views + Compose hybrid**. Activities and navigation stay classic Views; each re-skinned surface is a `ComposeView` region inside the existing activity. ARVIO components are **copied** (Apache-2.0 permits it) into `app/src/main/java/com/megaflix/tv/ui/compose/…`, package-renamed, and **re-bound to Megaflix's `VideoEntity`** via a small bridge model. We take lego bricks, never their screens/ViewModels (`HomeScreen.kt` is 184 KB wired to 13 streaming repositories — explicitly off-limits).

**Tech Stack:** Kotlin, Jetpack Compose (BOM), coil-compose, Media3 ExoPlayer (already present), Room 2.6.1 (untouched), classic Views shell.

**Spec:** No separate spec file. The approved design is: "rip ARVIO's home/detail/player *look* into Megaflix" (user, 2026-09-18), refined to a component transplant after source inspection showed the screens themselves are fused to ARVIO's streaming data layer.

## Global Constraints

- Every gradle/adb command needs this env first:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; export PATH="$JAVA_HOME/bin:$PATH"; export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools; export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"`
- Work directly on `main` in `/Users/t31k/Projects/Megaflix` (repo convention). Commit per task; push only in the final ship task.
- **Do not touch** the data layer: `data/` (Room v2, `TmdbClient`, `LibraryRepository`), `media/` (sources, parser), `update/` (OTA). They are shipped and working (v0.3.1, versionCode 4).
- **Never copy** ARVIO screen/ViewModel files (`ui/screens/home/HomeScreen.kt`, `HomeViewModel.kt`, `DetailsScreen.kt`, `DetailsViewModel.kt`, `PlayerViewModel.kt`, anything importing `com.arflix.tv.data.repository.*`). Components, theme, focus, motion utilities only.
- **Apache-2.0 attribution:** every copied file keeps its original license header if present; add `THIRD_PARTY_LICENSES.md` crediting ARVIO (Task 2). Copied files get a one-line provenance comment: `// Adapted from ARVIO (https://github.com/ProdigyV21/ARVIO), Apache-2.0.`
- **D-pad first:** every slice must pass a D-pad-only walkthrough on the `megaflix_tv` emulator before its commit. Focus must travel Views↔Compose across the `ComposeView` boundary (nav tabs are Views; rows are Compose).
- APK budget: release APK ≤ **12 MB** after all slices (was 4.1 MB; Compose runtime + coil-compose is the growth). Check in Task 8.
- Demo build (`DEMO_MODE=true`) is the test vehicle; emulator plays H.264 only (bundled clip is fine).
- Each slice ships independently: after any task's commit the app must build, run, and look no worse than before.

## Reference source (read-only)

- ARVIO clone for copying: use `/tmp/claude-501/-Users-t31k-Projects-Megaflix/83debcd8-11c4-49aa-bae0-e35e3f6fe4e3/scratchpad/ARVIO` if it still exists, else re-clone: `git clone --depth 1 https://github.com/ProdigyV21/ARVIO.git ~/Projects/ARVIO-ref` (≈245 MB). Below, `$ARVIO` = the clone root; source of interest is `$ARVIO/app/src/main/kotlin/com/arflix/tv/`.
- Layout of interest:
  - `ui/theme/`, `ui/skin/`, `ui/motion/` — colors, typography, shapes, animations
  - `ui/focus/` — TV focus handling (the crown jewel)
  - `ui/components/` — `MediaCard.kt`, `ContinueWatchingCard.kt`, `DetailsTvHeroLayout.kt`, `AppTopBar.kt`, `LoadingIndicator.kt`, `CardLayoutMode.kt` and friends
  - `ui/screens/player/tv/` — `TvPlayerControls.kt`, `TvPlayerOverlays.kt`, `TvPlayerPanels.kt`, `TvSkipIntroButton.kt`
- Megaflix current UI (will be partially replaced): `ui/LibraryActivity.kt` (+ `RowsAdapter`/`PosterAdapter`/`Row.kt`), `ui/DetailActivity.kt`, `ui/PlayerActivity.kt`, layouts `activity_library.xml`, `activity_detail.xml`, `activity_player.xml`, `item_row.xml`, `item_poster.xml`.

## Adaptation rules (apply to every copied file)

1. Package: `com.arflix.tv.ui.X` → `com.megaflix.tv.ui.compose.X`; fix imports accordingly.
2. Their model types → the bridge model (Task 3's `CardItem`) or plain parameters. A component wanting `MediaItem.posterUrl` gets `CardItem.posterUrl`. Never import anything from `com.arflix.tv.data.*` — if a component needs more than the bridge offers, trim the component, don't grow the bridge past what Megaflix can supply.
3. Their DI/hilt annotations, analytics calls, and `Modifier.clickable`-wrapped navigation into ARVIO routes: strip; expose plain `onClick: () -> Unit` / `onFocus: () -> Unit` lambdas.
4. Their string resources → hardcode or move into Megaflix `strings.xml` as needed (few expected).
5. Image loading: their coil-compose `AsyncImage` usage stays (we add coil-compose; Megaflix already uses coil for Views).
6. If a copied file drags in >3 additional ARVIO files, stop and reassess that component (report DONE_WITH_CONCERNS rather than vendoring half their tree).

---

### Task 1: Compose toolchain into Megaflix (hybrid enablement)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, root `build.gradle.kts` (read first — Kotlin version lives here or in `settings.gradle.kts`)

**Interfaces:**
- Produces: Compose compiles and a `ComposeView` renders inside a Views activity. Deps available to later tasks: `compose-bom`, `activity-compose`, `coil-compose`, `androidx.compose.material3`.

- [ ] **Step 1: Read the toolchain.** `cat build.gradle.kts settings.gradle.kts gradle/libs.versions.toml` — find the Kotlin plugin version. Expected: Kotlin 2.x (project scaffolded 2026 with KSP). **If Kotlin ≥ 2.0**, Compose needs the compiler plugin `org.jetbrains.kotlin.plugin.compose` at the *same version as Kotlin*. If Kotlin is 1.9.x, instead set `composeOptions { kotlinCompilerExtensionVersion }` per the official compatibility map — but prefer the 2.x route if that's what's there.
- [ ] **Step 2: Version catalog additions** (`gradle/libs.versions.toml`):

```toml
# under [versions]
composeBom = "2025.06.00"

# under [libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
activity-compose = { module = "androidx.activity:activity-compose", version = "1.9.3" }
coil-compose = { module = "io.coil-kt:coil-compose", version = "2.7.0" }
```

  (If `2025.06.00` doesn't resolve, use the newest BOM mavenCentral/google offers; it only needs to be recent enough for the copied code's APIs.)
- [ ] **Step 3: app/build.gradle.kts** — add the compose plugin id alongside existing plugins (Kotlin-2.x route): `id("org.jetbrains.kotlin.plugin.compose") version "<kotlin version>"` (or via the root/settings plugin block, matching how the other Kotlin plugins are declared — read first, match style). Inside `android {}`: `buildFeatures { compose = true }` (keep the existing `buildConfig = true`). Dependencies:

```kotlin
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.coil.compose)
```

- [ ] **Step 4: Smoke test.** Temporarily add to `DetailActivity.onCreate` a `ComposeView` overlay (`setContent { Text("compose ok") }` added to the root FrameLayout), `./gradlew installDebug`, launch a detail page on the emulator, screenshot, confirm the text renders, **then remove the smoke code**. This proves toolchain + runtime before any ARVIO code lands.
- [ ] **Step 5: Verify build clean:** `./gradlew assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** — `feat: enable Jetpack Compose (hybrid Views+Compose)`

---

### Task 2: Vendor ARVIO design system (theme, focus, motion)

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/ui/compose/theme/…`, `…/focus/…`, `…/motion/…` (copied+adapted from `$ARVIO/…/ui/theme|focus|motion`)
- Create: `THIRD_PARTY_LICENSES.md`

**Interfaces:**
- Produces: `MegaflixTheme { … }` composable wrapper (their theme renamed, accent forced to `#E50914`, dark scheme), focus utilities (whatever `ui/focus` exports — typically `Modifier` extensions / focus requesters used by MediaCard), motion specs.

- [ ] **Step 1: Inventory before copying.** `ls -la` + `wc -l` each file in the three dirs; read each file top-to-bottom. Files importing `com.arflix.tv.data.*` or DI graphs get trimmed or skipped per Adaptation rule 6. Record kept/skipped list in the report.
- [ ] **Step 2: Copy + adapt** per the Adaptation rules. Rename their top theme composable to `MegaflixTheme`; hardwire dark color scheme; accent/primary `#E50914`; background `#101010` (matches existing Views screens).
- [ ] **Step 3: `THIRD_PARTY_LICENSES.md`:** state that files under `ui/compose/` are adapted from ARVIO (ProdigyV21/ARVIO), Apache-2.0, with a copy of the license notice.
- [ ] **Step 4: Verify:** `./gradlew assembleDebug` compiles with the new package present (nothing uses it yet — that's fine, it must simply compile).
- [ ] **Step 5: Commit** — `feat: vendor ARVIO design system (theme/focus/motion), Apache-2.0 attribution`

---

### Task 3: Bridge model + MediaCard + Compose home rows (Slice ①)

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/ui/compose/CardItem.kt`
- Create: `app/src/main/java/com/megaflix/tv/ui/compose/components/MediaCard.kt` (adapted), plus any ≤3 helper files it needs (e.g. `CardLayoutMode.kt`)
- Create: `app/src/main/java/com/megaflix/tv/ui/compose/HomeRows.kt`
- Modify: `app/src/main/res/layout/activity_library.xml`, `app/src/main/java/com/megaflix/tv/ui/LibraryActivity.kt`

**Interfaces:**
- Consumes: `MegaflixTheme` + focus utils (Task 2), existing `LibraryActivity` flow (`repo.observeLibrary()` → `buildRows()` → `List<Row>` where `Row(title: String, items: List<VideoEntity>)`).
- Produces:

```kotlin
// CardItem.kt — the ONLY currency between Megaflix data and ARVIO-derived UI
data class CardItem(
    val id: Long,
    val title: String,
    val posterUrl: String?,   // TmdbClient.posterUrl(entity.posterPath)
    val year: Int?,
    val progressFraction: Float?,  // positionMs/durationMs when > 0, else null
)

fun VideoEntity.toCardItem() = CardItem(
    id = id,
    title = title,
    posterUrl = TmdbClient.posterUrl(posterPath),
    year = year,
    progressFraction = if (positionMs > 0 && durationMs > 0)
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else null,
)
```

  and `@Composable fun HomeRows(rows: List<Pair<String, List<CardItem>>>, onClick: (Long) -> Unit)` — vertical list of titled horizontal rows of `MediaCard`s with ARVIO focus scaling.

- [ ] **Step 1: Read** `$ARVIO/…/ui/components/MediaCard.kt` (+ `ContinueWatchingCard.kt` for the progress bar treatment) fully; copy + adapt per rules. The card must take `CardItem` + `onClick` + focus modifier only.
- [ ] **Step 2: Write `HomeRows.kt`** — `LazyColumn` of rows, each `LazyRow` of `MediaCard`s; use ARVIO focus/motion for the focused-card scale; row titles styled like ARVIO's (their type scale from Task 2).
- [ ] **Step 3: Host it.** In `activity_library.xml` replace the `rows` RecyclerView with `<androidx.compose.ui.platform.ComposeView android:id="@+id/rows_compose" …/>` (same layout params/weight). In `LibraryActivity`: delete the RecyclerView/`rowsAdapter` wiring; in the collect block map `buildRows(list)` → `rows_compose.setContent { MegaflixTheme { HomeRows(...) { id -> startActivity(DetailActivity…EXTRA_ID, id) } } }`. Keep `buildRows`, permission flow, update check, scan/enrich untouched. Simplify/remove `dispatchKeyEvent` focus bridging only if Compose↔Views traversal works without it (test first, then delete dead code).
- [ ] **Step 4: Emulator gate.** `installDebug`, launch, screenshot: posters render via coil-compose; D-pad: LEFT/RIGHT along a row, DOWN to next row, UP from first row reaches the Views nav tabs, CENTER opens detail. `adb logcat -d | grep -c "FATAL EXCEPTION"` = 0. Read the screenshot — the cards must show ARVIO's focus treatment (scale/glow), not the old white ring.
- [ ] **Step 5: Clean out dead Views:** delete `RowsAdapter.kt`, `PosterAdapter.kt`, `item_row.xml`, `item_poster.xml` **only if** nothing else references them (SearchActivity uses `PosterAdapter` — either port search results to a Compose grid of `MediaCard`s in this task, or keep `PosterAdapter`+`item_poster.xml` alive for search and note it; keeping is acceptable, deleting home-only files is required).
- [ ] **Step 6: Commit** — `feat: ARVIO-styled Compose home rows (slice 1)`

---

### Task 4: Detail hero (Slice ②)

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/ui/compose/components/DetailsHero.kt` (adapted from `$ARVIO/…/ui/components/DetailsTvHeroLayout.kt` + whatever ≤3 helpers)
- Create: `app/src/main/java/com/megaflix/tv/ui/compose/DetailScreen.kt`
- Modify: `app/src/main/java/com/megaflix/tv/ui/DetailActivity.kt`, delete-or-empty `activity_detail.xml` usage

**Interfaces:**
- Consumes: `VideoEntity` via existing `repo.byId(id)`; `TmdbClient.backdropUrl/posterUrl`.
- Produces: `@Composable fun DetailScreen(v: DetailData, onPlay: () -> Unit, onResume: (() -> Unit)?)` where `DetailData` extends the bridge with `backdropUrl`, `overview`, `genres`, `rating`, `seasonEpisode` — define it next to `CardItem`.

- [ ] **Step 1: Read `DetailsTvHeroLayout.kt` fully**; adapt. Keep their hero composition (backdrop, scrim, title treatment, badge row, buttons). Play/Resume buttons: use their focused-button styling; wire to the existing `startPlayer(v, fromStart)` logic moved into lambdas.
- [ ] **Step 2: `DetailActivity` becomes a thin host:** `setContent`-style via `ComposeView` (or `setContentView(ComposeView(this))`), load entity in `lifecycleScope` exactly as now, then set content. Rating/year/genre/S·E formatting logic moves from the old `bind()` into `DetailData` construction — same rules (`★ %.1f`, `S%02dE%02d`, hide-when-null).
- [ ] **Step 3: Emulator gate:** open a movie (Matrix) and the HotD episode: backdrop + poster + badges correct, Play focused by default, Resume appears only with progress (set progress by playing a few seconds first, back out, reopen). D-pad walk + zero crashes + screenshot read.
- [ ] **Step 4: Remove now-unused detail Views** (`activity_detail.xml`, `detail_scrim.xml`, `badge_bg.xml`, `btn_play_bg.xml`, `PlayButton`/`DetailBadge` styles) if nothing references them.
- [ ] **Step 5: Commit** — `feat: ARVIO-styled Compose detail hero (slice 2)`

---

### Task 5: TV player controls (Slice ③ — hardest, do last)

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/ui/compose/player/` — adapted `TvPlayerControls.kt`, `TvPlayerOverlays.kt` (skip `TvSkipIntroButton`, `TvPlayerPanels` unless free)
- Create: `app/src/main/java/com/megaflix/tv/ui/compose/player/PlayerControlState.kt`
- Modify: `app/src/main/java/com/megaflix/tv/ui/PlayerActivity.kt`, `app/src/main/res/layout/activity_player.xml`

**Interfaces:**
- Consumes: the existing `ExoPlayer` instance in `PlayerActivity` (do NOT copy ARVIO's `PlayerViewModel` or engine wrappers).
- Produces: `PlayerControlState` — a slim adapter exposing what the copied controls consume:

```kotlin
class PlayerControlState(private val player: ExoPlayer) {
    val isPlaying = MutableStateFlow(false)
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)
    fun playPause() { if (player.isPlaying) player.pause() else player.play() }
    fun seekBy(deltaMs: Long) = player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0))
    fun seekTo(ms: Long) = player.seekTo(ms)
    // tick positionMs on a 500ms loop while attached; update isPlaying/durationMs via Player.Listener
}
```

  Adapt the copied controls to read these flows instead of ARVIO's ViewModel. Rename/trim their callbacks to: play/pause, seek ±, scrub bar, back.

- [ ] **Step 1: Read `TvPlayerControls.kt` + `TvPlayerOverlays.kt` fully.** If they are deeply fused to ARVIO's `PlayerViewModel`/source-selection machinery (rule-6 trigger), fall back to: keep their *visual styling* (colors, bar, iconography, layout) but write our own slim control composable using it — report which route was taken.
- [ ] **Step 2:** `activity_player.xml`: keep the `PlayerView` (video surface, `use_controller=false`) and add a full-bleed `ComposeView` on top for the controls; controls auto-hide after ~4 s, any D-pad key shows them (this behavior likely exists in the copied code — keep theirs if so).
- [ ] **Step 3:** `PlayerActivity`: instantiate `PlayerControlState` next to the player; keep ALL existing resume/save-progress logic (`EXTRA_FROM_START`, `onStop` save) byte-for-byte.
- [ ] **Step 4: Emulator gate:** play the bundled clip: controls show on keypress, play/pause toggles, scrub works, BACK exits, progress still saves (reopen detail → Resume appears). Zero crashes. Screenshot the controls overlay for the user.
- [ ] **Step 5: Commit** — `feat: ARVIO-styled Compose player controls (slice 3)`

---

### Task 6: Whole-app D-pad regression + APK size check

- [ ] **Step 1:** Full remote-only walkthrough on the emulator: cold launch → rows scroll (hold RIGHT through 12 cards) → UP to tabs → Search tab → type, open result → detail → Play → controls → BACK ×3 to home. Zero crashes, no focus traps, no stale Views styling on re-skinned surfaces. Screenshot each surface; read them.
- [ ] **Step 2:** `./gradlew assembleRelease` (fix any `lintVitalRelease` errors the Compose additions surface), sign as usual, then `ls -la` the APK — **≤ 12 MB** or explain the excess in the report before proceeding.
- [ ] **Step 3:** RAM sanity: `adb shell dumpsys meminfo com.megaflix.tv | grep "TOTAL PSS"` while on home — record the number in the report (baseline pre-transplant was ~<100 MB; flag if it doubled).
- [ ] **Step 4: Commit** any fixes — `fix: <what the regression pass caught>`

---

### Task 7: Ship v0.4.0 OTA

**Files:** `app/build.gradle.kts`, `docs/megaflix.apk`, `docs/version.json`

- [ ] **Step 1:** `versionCode = 5`, `versionName = "0.4.0"`.
- [ ] **Step 2:** Build + sign exactly as v0.3.1 was shipped (assembleRelease → zipalign → apksigner with `~/.android/debug.keystore`, out to `docs/megaflix.apk`, delete `.idsig`). Verify `aapt2 dump badging` says 0.4.0. Sanity-install the signed APK on the emulator, screenshot home.
- [ ] **Step 3:** `docs/version.json` → `{"versionCode": 5, "versionName": "0.4.0", "apkUrl": "https://t31k.github.io/megaflix/megaflix.apk", "notes": "New look: ARVIO-style home, detail, and player UI."}`
- [ ] **Step 4:** Commit + push `main`. Poll `https://t31k.github.io/megaflix/version.json` until it serves versionCode 5 and the live APK sha256 matches `docs/megaflix.apk`.
- [ ] **Step 5:** Tell the human: relaunch Megaflix on the TV → OTA prompt to 0.4.0.

---

## Self-Review Notes

- **Coverage:** user asked for home + detail + player UI from ARVIO without the rest → Tasks 3/4/5; toolchain (1), design system + license (2), regression + size guard (6), OTA ship (7).
- **Known unknowns, handled in-plan:** exact Kotlin version (Task 1 Step 1 reads it, both routes given); actual internal structure of each ARVIO component (every copy task starts with a full read + rule-6 bail-out to styled-rewrite); Compose↔Views D-pad traversal at the nav-bar boundary (explicit gate in Task 3 Step 4, fallback = keep `dispatchKeyEvent` bridge).
- **Type consistency:** `CardItem`/`DetailData` defined once (Tasks 3/4) and consumed by name elsewhere; `PlayerControlState` API defined in Task 5 before use.
- **Search screen** intentionally left on Views `PosterAdapter` unless Task 3 Step 5 ports it cheaply — either outcome is compliant, must be stated in the report.
