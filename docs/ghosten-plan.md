# Ghosten → Megaflix: Implementation Plan

_Written 2026-09-18. This is a decision + implementation plan, not an executable TDD plan yet. Read the "Honest verdict" first — it changes what's realistic._

## TL;DR — honest verdict

Ghosten Player is **half open, half closed**:

- **Open (Dart/Flutter + Kotlin):** the entire UI, including the dedicated **TV UI** (`lib/pages_tv/`, `lib/main_tv.dart`) — this is the polished part you liked — plus the `video_player`, `file_picker`, `bluetooth`, `logger` plugins.
- **Closed (prebuilt binary):** the **core engine** ships as `lib-api-release.aar`, downloaded from their GitHub releases at build time (MD5-pinned). `ApiPlugin.kt` is just a thin bridge to a bound `ApiService` **inside that AAR**. The media database, **TMDB scraping, cloud-drive / WebDAV / Jellyfin / Emby integration, IPTV** — all of it lives in that binary. **Source not published.**

**What this means for "rip out just what we need":** you can't. The engine is one opaque `.aar` you either take whole or not at all. You **can** fork the whole app, build it, reskin the (open) TV UI, and rebrand — but you **cannot** change or slim what the engine does. You'd be shipping their closed engine under a Megaflix wrapper.

**Recommendation:** Only worth it if you want Ghosten's *features* (cloud drives, scraping, IPTV) badly enough to (a) learn Flutter, (b) depend on someone else's closed binary, and (c) abandon the working native Megaflix. If you mostly wanted the *look*, we can keep reproducing it natively (we already matched Jellyfin's detail screen). If you want the *features*, the lowest-effort path is to just **use Ghosten as-is** on the TV. A full fork is Plan A below; do it with eyes open.

---

## Global constraints / facts

- Ghosten: `GhostenEditor/Ghosten-Player` (Flutter, Dart SDK `^3.7.2`), **AGPL-3.0**. Personal use is unaffected; distributing a fork means publishing your source (fine — Megaflix is already public).
- Custom plugins: `GhostenEditor/Ghosten-Player-flutter-packages` @ `v0.0.9` (git-pinned in `pubspec.yaml`).
- Closed core: `lib-api-release.aar` @ `v0.0.9`, md5 `c1334a46cca192ca580ac1f839f7fdf3`, fetched by `api/android/build.gradle`'s `downloadDependencies` task. **Build requires network** to pull it.
- Already TV-ready: `android/app/build.gradle` has a `tv {}` product flavor; entry `lib/main_tv.dart`, screens in `lib/pages_tv/`.
- Toolchain NOT installed on this Mac: **Flutter SDK + Dart** are missing (`flutter`/`dart` not found). Android SDK is present (`/opt/homebrew/share/android-commandlinetools`), JDK 17 present.

---

## Phase 0 — Stand it up unchanged (prove the toolchain + see it live)

Goal: build the **tv flavor** and run it on the `megaflix_tv` emulator, untouched. If this doesn't build cleanly, nothing else matters.

1. **Install Flutter** (free, ~2 GB): `brew install --cask flutter`; then `flutter doctor --android-licenses` and `flutter doctor` (point it at the existing Android SDK). Expect to also let it manage/verify an Android cmdline-tools it likes.
2. **Fork both repos** to your account (so pins resolve to code you control): `T31K/Ghosten-Player` and `T31K/Ghosten-Player-flutter-packages`. Leave the AAR pin as-is for now (Phase 0 uses upstream's binary).
3. `cd Ghosten-Player && flutter pub get` (resolves git packages; needs network for the packages repo).
4. Build the TV flavor: `flutter build apk --flavor tv --release` (or `flutter run --flavor tv -t lib/main_tv.dart -d emulator-5554` to iterate). First build downloads `lib-api-release.aar` + Gradle deps — slow.
5. Install/run on `megaflix_tv`; confirm the TV UI launches and the engine service binds (add a local media source and see if it scrapes).

**Exit criteria:** the untouched Ghosten TV app runs on the emulator. Screenshot it. Decide, seeing it live + knowing the closed-engine constraint, whether to continue.

**Risk:** the `downloadDependencies` AAR fetch or the git-pinned packages could fail/change; the app may hard-require a phone-shaped setup flow. Budget real time for a first Flutter build.

---

## Phase 1 — Rebrand to Megaflix

Cosmetic ownership; no engine changes.

1. **App name / label:** `android/app/src/main/.../strings` + `AndroidManifest` label → "Megaflix".
2. **Application id:** decide — keep `com.ghosten.*`? No. Pick `com.megaflix.tv2` (NOT `com.megaflix.tv` — that's the native app; a different id lets both coexist on the TV during evaluation). Change in `android/app/build.gradle` `applicationId` (+ the `tv` flavor if it suffixes).
3. **Banner/icon:** drop your MEGAFLIX 16:9 banner into `assets/tv/images/` (Ghosten uses flavored TV assets) and/or `android:banner`; 320×180 as before.
4. **Theme colors / accent:** Ghosten centralizes theme in Dart (`lib/` theme/const files) — set the red (#E50914) accent, dark background.
5. **Strip sponsor/branding:** `sponsor_list.txt`, any "sponsor"/donate screens in `pages_tv`, upstream update-check (Ghosten has its own `install_plugin`-based updater — remove or repoint to our `version.json`).

---

## Phase 2 — Trim UI to what you want

The TV UI is open Dart in `lib/pages_tv/`. Remove pages/tabs you don't want (e.g. IPTV, live TV, DLNA settings) by deleting their routes/screens and nav entries. **Do not** touch the `api` calls the remaining screens depend on. Keep it a reskin/trim, not a rewrite.

---

## Phase 3 — Point it at your media

Ghosten's engine already supports local files + network sources (that's the whole appeal). Configure a **local/USB source** through its existing "add source" flow rather than writing new ingestion — the closed engine owns scanning + scraping. If you need it to auto-target the TCL's USB HDD, that's a config/first-run default in Dart, not engine work.

---

## Phase 4 — Ship via the existing pipeline

1. `flutter build apk --flavor tv --release`, then the **same** zipalign + apksigner (debug keystore) step we use now → `docs/megaflix.apk`.
2. **OTA:** two choices — (a) reuse our `version.json` + the in-app updater pattern (port the tiny checker to Dart), or (b) use Ghosten's built-in `install_plugin` updater repointed at our Pages. Reusing our `version.json` keeps one mental model.
3. Same GitHub Pages publish (push to `main`).

**Note:** Flutter TV APK will be **larger** (~15–25 MB vs our ~4 MB) and heavier at runtime — the TCL cost we keep flagging. Measure launch + scroll on the real panel in Phase 0 before investing in Phases 1–4.

---

## Decisions to make before Phase 1

1. **Depend on a closed binary you can't audit or fix?** If upstream abandons `lib-api-release.aar` or breaks the pinned API, you're stuck. (Big one.)
2. **Coexist or replace?** Keep native Megaflix (`com.megaflix.tv`) installed alongside the fork (`com.megaflix.tv2`) during evaluation, then choose.
3. **Flutter commitment?** Every future change is Dart, not Kotlin — a new stack to maintain.
4. **AGPL:** keep the fork's source public (already true for you).

## What I'd do

Phase 0 only, first — install Flutter, build the untouched TV flavor, run it on the emulator, and look at it next to native Megaflix on the real TCL. That's a few hours and answers the real question (is the closed-engine, heavier-runtime tradeoff worth it) before committing to Phases 1–4. If yes, proceed to fork + rebrand. If no, we keep enriching native Megaflix.
