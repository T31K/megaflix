# Ghosten → Megaflix: Detailed Implementation Plan

_Rewritten 2026-09-18 (v2, expanded). Grounded in source inspection of `GhostenEditor/Ghosten-Player` and `Ghosten-Player-flutter-packages @ v0.0.9` (clones examined locally). Phased with hard decision gates — each phase ends with a go/no-go before money-time is spent on the next._

---

## 1. Honest verdict (unchanged, sharpened by deeper reading)

Ghosten is **open UI on a closed engine**:

| Layer | Status | What's in it |
|---|---|---|
| TV UI (`lib/pages_tv/`: `home.dart`, `detail/`, `media/`, `player/`, `settings/`, `views/`, `components/`) | **Open** (Dart) | Every screen you saw and liked — reskinnable, trimmable |
| Phone UI (`lib/pages/`), shared `providers/`, `models/`, `theme.dart`, `l10n/` (EN+ZH) | **Open** (Dart) | App scaffolding |
| `video_player` plugin | **Open** (Kotlin + Dart) | **Media3/ExoPlayer 1.8.0** (+ RTMP datasource, session) — same player family as native Megaflix; optional **libmpv** backend downloaded at runtime from their `libmpv-build` releases |
| `file_picker`, `bluetooth`, `logger` plugins | **Open** | Utilities |
| **Core engine** (`api` plugin) | **CLOSED** | `lib-api-release.aar` (md5-pinned, fetched from their GitHub releases at build time). Contains the bound `ApiService`: media library DB, scanning, **TMDB scraping, Aliyun/Quark/WebDAV drives, Jellyfin/Emby, IPTV**. `ApiPlugin.kt` is only a MethodChannel↔Service bridge. **No published source.** |

**Consequences:**
- "Rip out just what we need" ⇒ possible for **UI** (delete Dart screens), **impossible for the engine** (one opaque AAR, take it whole).
- You can restyle everything the user sees; you cannot change what the engine does, fix its bugs, or slim its features.
- The app **hard-gates on the engine**: `main_tv.dart` awaits `Api.initialized()` before showing anything.
- Two runtime-download dependencies on GhostenEditor's GitHub: the engine AAR (build time) and optionally libmpv (runtime). If those releases vanish, builds break.
- License AGPL-3.0 — fork must stay public (Megaflix already is).

**Useful discovery:** Ghosten's own OTA updater is **repoint-able without code changes** — `lib/const.dart` reads `REPO_AUTHOR` / `REPO_NAME` from `--dart-define`, and `utils/check_update.dart` polls `https://api.github.com/repos/$repoAuthor/$repoName/releases`. Point it at a fork and publish GitHub Releases → self-updates from *our* repo for free.

---

## 2. Environment facts (this Mac)

- **Missing:** Flutter SDK + Dart (`flutter`/`dart` not on PATH). Everything below that needs them starts with the install.
- **Present:** Android SDK `/opt/homebrew/share/android-commandlinetools` (platform-tools, platforms;android-35, build-tools;35.0.0, emulator), JDK 17 at `/opt/homebrew/opt/openjdk@17/...`, AVD `megaflix_tv`, debug keystore `~/.android/debug.keystore` (alias `androiddebugkey`, both passwords `android`).
- Standard env preamble for every shell below:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
```

- Native Megaflix stays untouched at `com.megaflix.tv` (v0.3.1, OTA via `docs/version.json`). The fork uses a **different applicationId** so both coexist on the TCL during evaluation.

---

## Phase 0 — Build it untouched, see it on the emulator and the TCL

**Goal:** prove the toolchain, then judge the real thing on the real TV. No forks, no edits.
**Estimated effort:** 2–4 h (dominated by Flutter install + first Gradle/AAR downloads).

1. **Install Flutter** (free): `brew install --cask flutter` (~1–2 GB).
2. **Doctor:** run the env preamble, then `flutter doctor`. If it can't find the SDK: `flutter config --android-sdk "$ANDROID_HOME"`. Accept licenses: `yes | flutter doctor --android-licenses`. Exit criteria: doctor shows Android toolchain ✓ (Xcode/Chrome ✗ are fine).
3. **Fresh working clone** (keep the scratchpad copy as reference): `git clone https://github.com/GhostenEditor/Ghosten-Player.git ~/Projects/ghosten-eval && cd ~/Projects/ghosten-eval`.
4. **Resolve packages:** `flutter pub get` (pulls the five git-pinned plugins @ v0.0.9).
5. **Debug build of the TV flavor** (debug first — the release signingConfig reads a `key.properties` that doesn't exist and has a null `storeFile`):

```bash
flutter build apk --flavor tv --debug -t lib/main_tv.dart
```

   First run also triggers the `downloadDependencies` Gradle task → fetches `lib-api-release.aar` (md5-verified). Output lands at `build/app/outputs/flutter-apk/app-tv-debug.apk`.
   *Failure modes:* AAR download blocked (needs network to github.com); Gradle/AGP version clash with JDK 17 (Ghosten uses AGP 8.7.3 — compatible); NDK auto-install prompt (let it).
6. **Run on the emulator:** boot `megaflix_tv` (`emulator -avd megaflix_tv -no-snapshot -gpu swiftshader_indirect`), then `adb install -r build/app/outputs/flutter-apk/app-tv-debug.apk` and launch from the leanback launcher (or `flutter run --flavor tv -t lib/main_tv.dart -d emulator-5554` for hot reload).
7. **Exercise it:** first-run flow → add a **local** media source (Settings → file source; the engine scans + scrapes via its own TMDB integration) → confirm posters appear → play an **H.264** file (emulator can't decode HEVC — same caveat as always; the TCL can).
8. **Measure on the emulator, then sideload the same debug APK on the TCL** and measure where it counts:
   - cold launch time vs native Megaflix
   - poster-row scroll smoothness (D-pad held down)
   - `adb shell dumpsys meminfo com.ghosten.player | grep "TOTAL PSS"` vs native (~135 MB was Jellyfin's; native Megaflix is far lower)
   - APK size (expect ~20–30 MB debug, ~15 MB release vs native 4 MB)

**GATE 0 (the big one):** side-by-side on the TCL — does Ghosten feel fast enough, and is the look worth adopting a closed engine + Flutter? **No → stop here**, keep enriching native Megaflix (total sunk cost: one afternoon). **Yes → Phase 1.**

---

## Phase 1 — Fork + rebrand to Megaflix

**Goal:** our repo, our name, our banner, our update channel. No feature changes.
**Estimated effort:** 3–5 h.

1. **Fork** `GhostenEditor/Ghosten-Player` → `T31K/megaflix-tv2` (`gh repo fork --clone`). *Leave the plugin repo un-forked for now* — `pubspec.yaml` pins `GhostenEditor/Ghosten-Player-flutter-packages @ v0.0.9` tags, which are stable; fork it only if we ever need to patch a plugin. Note the standing risk: upstream deleting tags/releases breaks builds → mitigate later by vendoring (`git subtree` the packages + committing the AAR into the fork).
2. **applicationId:** `android/app/build.gradle` → `applicationId = "com.megaflix.tv2"`. Keep `namespace = "com.ghosten.player"` (changing the namespace means touching every Kotlin package path for zero user-visible gain).
3. **App label + banner:** in `android/app/src/main/AndroidManifest.xml` set the label to Megaflix; replace the `@drawable/ic_banner` asset (`android/app/src/main/res/drawable*/ic_banner*`) with the MEGAFLIX 320×180 banner (same one the user generates for the native app) and the `ic_launcher` mipmaps.
4. **In-app name:** `lib/const.dart` → `appName = 'Megaflix'`; replace `assets/common/images/logo.png` with a Megaflix logo (keep dimensions).
5. **Accent/theme:** `lib/theme.dart` — set seed/accent to `#E50914`, verify dark scheme.
6. **Strings:** sweep `lib/l10n/app_en.arb` (and `app_zh.arb`) for "Ghosten" → "Megaflix"; regenerate l10n (`flutter gen-l10n`, config in `l10n.yaml`).
7. **Sponsor page out:** delete `lib/pages_tv/settings/settings_sponsor.dart` and its route/entry in the TV settings screen (grep `SettingsSponsor` under `lib/pages_tv/settings/`); drop `sponsor_list.txt`.
8. **Update channel:** build with `--dart-define=REPO_AUTHOR=T31K --dart-define=REPO_NAME=megaflix-tv2` so `checkUpdate()` polls **our fork's** GitHub Releases. (Alternative if we prefer one mental model: rip `utils/check_update.dart` + `settings_update.dart` and port our 60-line `version.json` checker to Dart. Recommended: keep theirs — it exists, it's tested, and Releases are free.)
9. **Version zero:** `pubspec.yaml` `version: 2.0.0+1` (the fork is a separate app line; its versionCode never collides with native Megaflix because the applicationId differs).
10. Build debug, install over the Phase-0 eval on emulator + TCL, verify branding everywhere (launcher banner, settings/about, update screen).

---

## Phase 2 — Trim the UI to what mom needs

**Goal:** hide the power-user surface; keep home → detail → play brutally simple.
**Estimated effort:** 1–2 days (unfamiliar codebase, D-pad regression testing).

1. Inventory the TV settings surface: `ls lib/pages_tv/settings/` and map each screen to keep/drop. Likely drops: IPTV/live (grep `iptv\|live` in `lib/pages_tv/`), DLNA, diagnostics/logs, prerelease-update toggle. Likely keeps: media sources, playback settings, update.
2. For each drop: remove the settings-menu entry (the screen files can stay — dead Dart code is tree-shaken out of the release build; removing the *entry* is the low-risk edit).
3. **Never** remove calls into `package:api` from screens that stay — the engine API surface (`api.dart`, `enums.dart`, `errors.dart`) is fixed and the closed service expects its init/config flow.
4. Home screen (`lib/pages_tv/home.dart`): keep default rows; optionally reorder to mirror native Megaflix (Recently Added first).
5. Full D-pad regression after each batch of removals: home ↔ detail ↔ player ↔ settings, no focus traps. (`scaled_app` renders at a 960-wide design scale — test at the TCL's real resolution, not just the emulator.)

**GATE 2:** demo to mom on the TCL. Her verdict outranks ours.

---

## Phase 3 — Wire up the real library

**Goal:** the TCL's USB HDD as the media source.
**Estimated effort:** 1–3 h (mostly on-TV fiddling).

1. On the TCL: Settings → media source → **local/file source** → point at the USB mount (`/storage/<uuid>/…`). The closed engine owns scan + scrape (its own TMDB access — no key of ours involved).
2. Confirm: HEVC files play (TCL hardware-decodes; if a file stutters, the player's optional **libmpv** backend is downloadable in settings — it fetches from GhostenEditor's `libmpv-build` releases, one more upstream dependency to be aware of).
3. Confirm resume/watch-state persists across restarts (engine DB, not ours — we can't migrate native Megaflix's Room watch-progress into it; accepted loss).
4. Set first-run defaults in Dart (`providers/user_config.dart`) if mom's TV should skip setup ceremony.

---

## Phase 4 — Ship + updates

**Goal:** signed release APK, published, self-updating.
**Estimated effort:** 2–4 h.

1. **Signing:** create `android/key.properties` in the fork pointing at the debug keystore we already ship native Megaflix with (keeps one key story):

```properties
storeFile=/Users/t31k/.android/debug.keystore
storePassword=android
keyAlias=androiddebugkey
keyPassword=android
```

   (Gradle's release `signingConfig` reads exactly these keys — no zipalign/apksigner step needed; Flutter's Gradle signs + aligns.) `.gitignore` already excludes `key.properties`.
2. **Release build:**

```bash
flutter build apk --flavor tv --release -t lib/main_tv.dart \
  --dart-define=REPO_AUTHOR=T31K --dart-define=REPO_NAME=megaflix-tv2 \
  --dart-define=BUILD_VERSION=2.0.0
```

   → `build/app/outputs/flutter-apk/app-tv-release.apk`.
3. **Distribute during evaluation:** copy to `docs/megaflix2.apk` in the existing Pages repo (separate link on the download page; native `megaflix.apk` stays the default until the switch decision).
4. **OTA:** `gh release create v2.0.0 app-tv-release.apk --repo T31K/megaflix-tv2` — installed apps poll that releases feed (Step 1.8) and self-update through their built-in flow (`install_plugin`; same one-tap install consent as our native updater). Test by publishing a `v2.0.1` with a trivial change.
5. **The switch decision** (only after weeks of real use): if the fork wins, point the download page at it and retire the native app — or keep both, since they coexist.

---

## 5. Risk register

| Risk | Severity | Mitigation |
|---|---|---|
| Closed engine AAR: unfixable bugs, opaque behavior, upstream abandonment | **High** | Accept consciously at Gate 0; vendor the AAR + plugin sources into the fork so builds never depend on upstream availability |
| Flutter runtime weight on the low-end TCL (the original reason Megaflix is native Views) | **High** | Measured empirically at Gate 0 on the real panel — numbers, not vibes |
| New stack (Dart) for all future changes | Medium | Phase 2 is deliberately entry-level Dart (deleting routes); deeper work only if the fork is adopted |
| Upstream tags/releases (plugins AAR, libmpv) disappear | Medium | Vendor after Gate 0 passes |
| Engine scrapes via its own TMDB path — if their infra/key breaks, posters stop | Medium | Nothing we can do (closed); note as accepted dependency |
| Watch-progress not migratable from native Room DB | Low | Accepted; library rescans fresh |
| AGPL obligations | Low | Fork stays public |

## 6. Effort summary

| Phase | Effort | Gate |
|---|---|---|
| 0 — build untouched + measure on TCL | 2–4 h | **Go/no-go on the whole idea** |
| 1 — fork + rebrand | 3–5 h | branding review |
| 2 — trim UI | 1–2 days | mom demo |
| 3 — real library on USB | 1–3 h | plays the actual files |
| 4 — sign, ship, OTA | 2–4 h | v2.0.1 self-update observed |

**Recommendation stands:** run Phase 0 next time we have an afternoon; decide at Gate 0 with the TCL in front of us. Native Megaflix keeps shipping (it's at v0.3.1 with working OTA) either way — nothing here blocks it.
