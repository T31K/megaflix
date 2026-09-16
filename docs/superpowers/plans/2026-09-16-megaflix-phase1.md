# Megaflix Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A working Megaflix app running in the Android TV emulator on macOS that scans local video files, shows them in a D-pad-navigable poster grid, plays one full-screen with ExoPlayer, and resumes from the saved position after quitting mid-playback.

**Architecture:** Single-module Kotlin app built on **classic Android Views** (no Compose, no Leanback stock widgets). Files are read through a `MediaSource` interface with two implementations: `DevMediaSource` (MediaStore, active now) and `UsbHddSource`/`SafMediaSource` (written now, disabled behind `BuildConfig.USE_USB_SOURCE`). A Room database indexes files and stores watch progress. Manual DI via a plain `Graph` object. Screens are plain `Activity`s wired with `Intent`s.

**Tech Stack:** Kotlin, Android Views + RecyclerView, Media3/ExoPlayer, Room, Kotlin Coroutines. (Coil + Retrofit/TMDB are Phase 2 — not this plan.)

**Spec:** `docs/megaflix-spec.md`

## Global Constraints

- **No Jetpack Compose. No Leanback stock widgets. No Flutter/RN.** Views only. Verbatim from spec v2.
- Min SDK 27, target SDK 35 (`targetSdk = 35`), compile SDK 35.
- Kotlin JVM target 17, JDK 17 for the build.
- Package / applicationId: `com.megaflix.tv`.
- App name / branding: **Megaflix**.
- Dark theme only.
- Dependencies limited to: AndroidX core/appcompat, RecyclerView, Media3 (ExoPlayer), Room, Kotlin coroutines, Material Components (for theming only, no Material UI widgets on TV surfaces). **No dependency outside this list without justification.**
- `BuildConfig.USE_USB_SOURCE` defaults to `false`. `UsbHddSource`/`SafMediaSource` must compile but never run in Phase 1.
- Video extensions everywhere: `.mp4, .mkv, .avi, .mov, .webm`.
- D-pad only. No touch/click assumptions in focus or navigation logic.
- Cold-start and 60fps rules from the spec's "Performance rules" apply to every UI task: stable RecyclerView ids, no allocation in `onBindViewHolder`, focus animation via `ViewPropertyAnimator` on a hardware layer, `clipChildren=false` for scale overflow.
- Commit after every task. Commit messages end with:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`

---

## File Structure

```
Megaflix/
  settings.gradle.kts            # single module :app
  build.gradle.kts               # root, plugin versions
  gradle.properties              # AndroidX, JVM args
  gradle/libs.versions.toml      # version catalog
  local.properties              # sdk.dir + (Phase 2) tmdb key — gitignored
  push-test-media.sh             # Task 10 helper
  .gitignore
  app/
    build.gradle.kts             # android config, deps, USE_USB_SOURCE flag
    src/main/AndroidManifest.xml
    src/main/res/values/themes.xml, colors.xml, strings.xml, styles.xml
    src/main/res/drawable/       # placeholder poster, focus selector, play/pause icons
    src/main/res/layout/         # activity_library.xml, item_poster.xml, activity_player.xml
    src/main/java/com/megaflix/tv/
      App.kt                     # Application, holds Graph
      Graph.kt                   # manual DI: db, source, repo
      media/
        VideoFile.kt             # data model (Task 2)
        MediaSource.kt           # interface (Task 2)
        FilenameParser.kt        # pure logic, unit tested (Task 2)
        DevMediaSource.kt        # MediaStore impl (Task 4)
        UsbHddSource.kt          # disabled, commented (Task 5)
        SafMediaSource.kt        # disabled, commented (Task 5)
      data/
        LibraryDatabase.kt       # Room db (Task 3)
        VideoEntity.kt           # Room entity (Task 3)
        VideoDao.kt              # Room dao (Task 3)
        LibraryRepository.kt     # scan pipeline (Task 6)
      ui/
        LibraryActivity.kt       # poster grid (Task 7)
        PosterAdapter.kt         # RecyclerView adapter (Task 7)
        PlayerActivity.kt        # ExoPlayer screen (Task 8)
    src/test/java/com/megaflix/tv/
      media/FilenameParserTest.kt   # JVM unit tests (Task 2)
```

Pure-logic files (`FilenameParser`) get real JVM unit tests. Android-integration files (Room, MediaStore, ExoPlayer, UI focus) are verified by running against the emulator — that verification *is* the Phase 1 exit criteria, so those tasks end with explicit manual-verification steps rather than JUnit assertions.

---

## Task 0: Environment setup (macOS)

This Mac has **no JDK and no Android SDK** (`java`, `adb`, `sdkmanager` all missing). Set them up before any Gradle work. This task has no code deliverable and no commit; it ends when the emulator boots.

**Files:** none.

- [ ] **Step 1: Install JDK 17**

```bash
brew install openjdk@17
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-17.jdk
/usr/libexec/java_home -v 17   # should print a path
```

- [ ] **Step 2: Install Android command-line tools + platform + emulator**

```bash
brew install --cask android-commandlinetools
# Point env at the SDK (add to ~/.zshrc too):
export ANDROID_HOME="$(brew --prefix)/share/android-commandlinetools"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
  "emulator" "system-images;android-34;android-tv;arm64-v8a"
```

(arm64 TV system image — this Mac is Apple Silicon, confirmed `arm64`.)

- [ ] **Step 3: Create + boot an Android TV AVD**

```bash
echo no | avdmanager create avd -n megaflix_tv \
  -k "system-images;android-34;android-tv;arm64-v8a" -d "tv_1080p"
emulator -avd megaflix_tv -no-snapshot -gpu host &
adb wait-for-device
adb shell getprop sys.boot_completed   # prints 1 when ready
```

Expected: an Android TV home screen appears. `adb devices` lists `emulator-5554`.

---

## Task 1: Project scaffold

Empty but launchable app: Gradle builds it, it installs, it shows a blank dark `LibraryActivity`. This locks in module layout, the version catalog, the theme, and the `USE_USB_SOURCE` flag.

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`, `local.properties`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/{themes.xml,colors.xml,strings.xml}`
- Create: `app/src/main/java/com/megaflix/tv/App.kt`, `Graph.kt`, `ui/LibraryActivity.kt`
- Create: `app/src/main/res/layout/activity_library.xml`

**Interfaces:**
- Produces: `com.megaflix.tv.App` (Application subclass holding `lateinit var graph: Graph`), `com.megaflix.tv.Graph` (empty for now, fields added in later tasks), `BuildConfig.USE_USB_SOURCE: Boolean`.

- [ ] **Step 1: Write the Gradle wrapper + root config**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Megaflix"
include(":app")
```

`build.gradle.kts` (root):
```kotlin
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
```

Generate the wrapper so no system Gradle is needed:
```bash
cd /Users/t31k/Projects/Megaflix
gradle wrapper --gradle-version 8.10.2   # if no system gradle: brew install gradle first, or copy a wrapper in
```

- [ ] **Step 2: Version catalog**

`gradle/libs.versions.toml`:
```toml
[versions]
media3 = "1.5.1"
room = "2.6.1"
coroutines = "1.9.0"
lifecycle = "2.8.7"

[libraries]
androidx-core = { module = "androidx.core:core-ktx", version = "1.15.0" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version = "1.7.0" }
androidx-recyclerview = { module = "androidx.recyclerview:recyclerview", version = "1.3.2" }
androidx-activity = { module = "androidx.activity:activity-ktx", version = "1.9.3" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", ref = "lifecycle" }
material = { module = "com.google.android.material:material", version = "1.12.0" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit = { module = "junit:junit", version = "4.13.2" }
```

Fix the `ref`→`version.ref` typo when transcribing (`androidx-lifecycle-runtime` uses `version.ref = "lifecycle"`).

- [ ] **Step 3: App module build file with the USB flag**

`app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.megaflix.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.megaflix.tv"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-phase1"
        // Phase 4 flips this to true. Phase 1 = DevMediaSource.
        buildConfigField("boolean", "USE_USB_SOURCE", "false")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.material)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
```

Create `app/proguard-rules.pro` (empty file is fine for now).

- [ ] **Step 4: Manifest — TV leanback launcher, no touchscreen required**

`app/src/main/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.software.leanback" android:required="true" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <!-- Phase 1: read pushed videos from shared storage via MediaStore. -->
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <!-- Pre-33 devices use the legacy permission; maxSdk keeps 33+ clean. -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <application
        android:name=".App"
        android:allowBackup="true"
        android:banner="@drawable/app_banner"
        android:icon="@drawable/app_banner"
        android:label="@string/app_name"
        android:theme="@style/Theme.Megaflix">

        <activity
            android:name=".ui.LibraryActivity"
            android:exported="true"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".ui.PlayerActivity"
            android:exported="false"
            android:screenOrientation="landscape"
            android:configChanges="orientation|screenSize|keyboardHidden" />
    </application>
</manifest>
```

Provide `app/src/main/res/drawable/app_banner.xml` as a simple placeholder (a dark rounded-rect vector with "MEGAFLIX" is fine) so the manifest resolves — a TV app needs a banner or install fails.

- [ ] **Step 5: Theme, colors, strings**

`app/src/main/res/values/colors.xml`:
```xml
<resources>
    <color name="mf_bg">#0B0B0F</color>
    <color name="mf_surface">#16161D</color>
    <color name="mf_text">#F5F5F7</color>
    <color name="mf_text_dim">#9A9AA5</color>
    <color name="mf_accent">#E50914</color>
    <color name="mf_focus">#FFFFFF</color>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.Megaflix" parent="Theme.Material3.Dark.NoActionBar">
        <item name="android:windowBackground">@color/mf_bg</item>
        <item name="android:colorBackground">@color/mf_bg</item>
        <item name="colorPrimary">@color/mf_accent</item>
    </style>
</resources>
```

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Megaflix</string>
    <string name="empty_library">No videos found. Push some with push-test-media.sh.</string>
</resources>
```

- [ ] **Step 6: App, Graph, LibraryActivity stubs**

`App.kt`:
```kotlin
package com.megaflix.tv

import android.app.Application

class App : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
    }
}
```

`Graph.kt` (fields filled in later tasks; keep it minimal now):
```kotlin
package com.megaflix.tv

import android.content.Context

/** Manual DI. No framework. Later tasks add db, source, repository here. */
class Graph(private val appContext: Context)
```

`ui/LibraryActivity.kt`:
```kotlin
package com.megaflix.tv.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.megaflix.tv.R

class LibraryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)
    }
}
```

`res/layout/activity_library.xml`:
```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/mf_bg" />
```

- [ ] **Step 7: Build, install, verify launch**

```bash
./gradlew assembleDebug
./gradlew installDebug   # emulator from Task 0 must be running
adb shell monkey -p com.megaflix.tv -c android.intent.category.LEANBACK_LAUNCHER 1
```
Expected: BUILD SUCCESSFUL; a blank dark screen launches with no crash. `adb logcat -d | grep -i megaflix` shows no fatal exception.

- [ ] **Step 8: Commit**

```bash
git init
# .gitignore must include: /build, /app/build, .gradle, local.properties, *.iml, .idea/
git add -A
git commit -m "chore: scaffold Megaflix TV app (Views, no Compose)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: VideoFile model, MediaSource interface, and filename parser (TDD)

The pure-logic core. `FilenameParser` extracts a clean title, optional year, and optional season/episode from messy release filenames — including the real first example, `House.of.the.Dragon.S03E05.1080p.HEVC.x265-MeGusta[EZTVx.to].mkv`. This is the one task with real JVM unit tests; write the tests first.

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/media/VideoFile.kt`
- Create: `app/src/main/java/com/megaflix/tv/media/MediaSource.kt`
- Create: `app/src/main/java/com/megaflix/tv/media/FilenameParser.kt`
- Test: `app/src/test/java/com/megaflix/tv/media/FilenameParserTest.kt`

**Interfaces:**
- Produces:
  - `data class VideoFile(val uri: String, val filename: String, val sizeBytes: Long, val modifiedEpochSec: Long)` — `uri` is a string (content:// or file://) so it survives Room without a TypeConverter.
  - `interface MediaSource { suspend fun listVideoFiles(): List<VideoFile>; fun openPlayableUri(file: VideoFile): android.net.Uri }`
  - `data class ParsedName(val title: String, val year: Int?, val season: Int?, val episode: Int?)`
  - `object FilenameParser { fun parse(filename: String): ParsedName }`
  - `val VIDEO_EXTENSIONS: Set<String>` (lowercase, no dot: `mp4, mkv, avi, mov, webm`).

- [ ] **Step 1: Write the failing tests**

`FilenameParserTest.kt`:
```kotlin
package com.megaflix.tv.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameParserTest {

    @Test fun `tv episode with release junk`() {
        val p = FilenameParser.parse(
            "House.of.the.Dragon.S03E05.1080p.HEVC.x265-MeGusta[EZTVx.to].mkv"
        )
        assertEquals("House of the Dragon", p.title)
        assertEquals(3, p.season)
        assertEquals(5, p.episode)
        assertNull(p.year)
    }

    @Test fun `movie with year in parens`() {
        val p = FilenameParser.parse("Blade Runner 2049 (2017) 1080p BluRay x264.mp4")
        assertEquals("Blade Runner 2049", p.title)
        assertEquals(2017, p.year)
        assertNull(p.season)
    }

    @Test fun `movie with dots and bare year`() {
        val p = FilenameParser.parse("The.Matrix.1999.720p.WEB-DL.mp4")
        assertEquals("The Matrix", p.title)
        assertEquals(1999, p.year)
    }

    @Test fun `strips scene tags without year`() {
        val p = FilenameParser.parse("Some_Movie_Name.PROPER.REPACK.HDTV.x264-GROUP.mkv")
        assertEquals("Some Movie Name", p.title)
        assertNull(p.year)
    }

    @Test fun `lowercase sxxexx variant`() {
        val p = FilenameParser.parse("severance.s02e01.2160p.mp4")
        assertEquals("Severance", p.title)
        assertEquals(2, p.season)
        assertEquals(1, p.episode)
    }

    @Test fun `plain title no junk`() {
        val p = FilenameParser.parse("Amelie.mkv")
        assertEquals("Amelie", p.title)
        assertNull(p.year)
        assertNull(p.season)
    }

    // Guard: a 4-digit number that is part of a real title, not a year,
    // when it appears before any scene tag. 2049 above already covers the
    // in-title case; this checks a leading-year-like number is kept as title.
    @Test fun `number that is really the title`() {
        val p = FilenameParser.parse("1917.2019.1080p.BluRay.mp4")
        assertEquals("1917", p.title)
        assertEquals(2019, p.year)
    }
}
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.megaflix.tv.media.FilenameParserTest"`
Expected: compilation error / FAIL — `FilenameParser` not defined.

- [ ] **Step 3: Implement VideoFile + MediaSource**

`VideoFile.kt`:
```kotlin
package com.megaflix.tv.media

/** One video on some backing store. [uri] is a string so it persists in Room directly. */
data class VideoFile(
    val uri: String,
    val filename: String,
    val sizeBytes: Long,
    val modifiedEpochSec: Long,
)

/** Lowercase, no leading dot. */
val VIDEO_EXTENSIONS: Set<String> = setOf("mp4", "mkv", "avi", "mov", "webm")
```

`MediaSource.kt`:
```kotlin
package com.megaflix.tv.media

import android.net.Uri

/**
 * The swap point between the Mac test build (DevMediaSource) and the real
 * TCL/USB build (UsbHddSource). Graph picks one via BuildConfig.USE_USB_SOURCE.
 */
interface MediaSource {
    suspend fun listVideoFiles(): List<VideoFile>
    fun openPlayableUri(file: VideoFile): Uri
}
```

- [ ] **Step 4: Implement FilenameParser**

`FilenameParser.kt`:
```kotlin
package com.megaflix.tv.media

data class ParsedName(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
)

/**
 * Turns "House.of.the.Dragon.S03E05.1080p.HEVC.x265-MeGusta[EZTVx.to].mkv"
 * into ParsedName("House of the Dragon", null, 3, 5).
 *
 * Strategy: strip extension and bracket groups, normalise separators to
 * spaces, then find the first "junk boundary" — a scene tag, a SxxExx token,
 * or a standalone 4-digit year — and treat everything before it as the title.
 */
object FilenameParser {

    private val SXXEXX = Regex("""[sS](\d{1,2})[eE](\d{1,3})""")
    private val YEAR = Regex("""\b(19\d{2}|20\d{2})\b""")
    private val BRACKETS = Regex("""[\[(][^\])]*[\])]""")
    // Common release/scene tags that mark the end of a title.
    private val TAGS = setOf(
        "1080p", "720p", "480p", "2160p", "4k", "uhd",
        "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx",
        "bluray", "brrip", "bdrip", "webrip", "web-dl", "webdl", "web",
        "hdtv", "hdrip", "dvdrip", "dvdscr", "cam", "ts", "hdr", "hdr10",
        "aac", "ac3", "dts", "dd5", "atmos", "truehd", "10bit",
        "proper", "repack", "extended", "remastered", "internal", "limited",
    )

    fun parse(filename: String): ParsedName {
        val noExt = filename.substringBeforeLast('.').let {
            if (it.isBlank()) filename else it
        }
        val noBrackets = BRACKETS.replace(noExt, " ")
        // Normalise ., _ and multiple spaces to single spaces.
        val normalized = noBrackets.replace(Regex("""[._]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val season = SXXEXX.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        val episode = SXXEXX.find(normalized)?.groupValues?.get(2)?.toIntOrNull()

        val tokens = normalized.split(' ').filter { it.isNotBlank() }
        var cutIndex = tokens.size
        var year: Int? = null

        for ((i, raw) in tokens.withIndex()) {
            val t = raw.lowercase().trimEnd('-')
            if (i == 0) continue // never cut before the first token; a title can start with a number (1917)
            if (SXXEXX.matches(raw)) { cutIndex = minOf(cutIndex, i); continue }
            if (t.trimStart('-') in TAGS || t.substringBefore('-') in TAGS) {
                cutIndex = minOf(cutIndex, i); continue
            }
            val ym = YEAR.matchEntire(raw)
            if (ym != null) {
                if (year == null) year = raw.toInt()
                cutIndex = minOf(cutIndex, i)
            }
        }

        val title = tokens.take(cutIndex).joinToString(" ")
            .replace(Regex("""[-–]\s*[A-Za-z0-9]+$"""), "") // trailing "-GROUP"
            .trim()
            .ifBlank { tokens.firstOrNull().orEmpty() }

        return ParsedName(
            title = titleCase(title),
            year = year,
            season = season,
            episode = episode,
        )
    }

    private fun titleCase(s: String): String =
        s.split(' ').joinToString(" ") { w ->
            if (w.isEmpty()) w
            else if (w.any { it.isDigit() }) w // keep 1917, 2049 as-is
            else w.replaceFirstChar { it.uppercase() }
        }
}
```

- [ ] **Step 5: Run tests, verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.megaflix.tv.media.FilenameParserTest"`
Expected: PASS (7 tests). If `number that is really the title` or the HotD case fails, adjust the cut logic — do **not** weaken the assertions.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/megaflix/tv/media app/src/test
git commit -m "feat: VideoFile, MediaSource, filename parser with TV+movie support

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Room database — entity, DAO, watch progress

Indexes videos and stores resume positions. Verified by an instrumented smoke test path plus the app run in later tasks (Room requires an Android runtime, so no JVM unit test here — verification folds into Task 6/9).

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/data/VideoEntity.kt`
- Create: `app/src/main/java/com/megaflix/tv/data/VideoDao.kt`
- Create: `app/src/main/java/com/megaflix/tv/data/LibraryDatabase.kt`
- Modify: `app/src/main/java/com/megaflix/tv/Graph.kt`

**Interfaces:**
- Consumes: `VideoFile` (Task 2).
- Produces:
  - `VideoEntity(id: Long, uri, filename, title, year: Int?, season: Int?, episode: Int?, sizeBytes, modifiedEpochSec, positionMs: Long = 0, durationMs: Long = 0, addedEpochSec: Long)` with `uri` UNIQUE.
  - `VideoDao`: `suspend fun upsertAll(items: List<VideoEntity>)`, `suspend fun getByUri(uri: String): VideoEntity?`, `fun observeAll(): Flow<List<VideoEntity>>`, `suspend fun getAll(): List<VideoEntity>`, `suspend fun updateProgress(uri: String, positionMs: Long, durationMs: Long)`.
  - `LibraryDatabase.get(context): LibraryDatabase` singleton with `videoDao()`.
  - `Graph.db: LibraryDatabase`, `Graph.videoDao: VideoDao`.

- [ ] **Step 1: Entity**

`VideoEntity.kt`:
```kotlin
package com.megaflix.tv.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "videos", indices = [Index(value = ["uri"], unique = true)])
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val filename: String,
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val sizeBytes: Long,
    val modifiedEpochSec: Long,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val addedEpochSec: Long,
)
```

- [ ] **Step 2: DAO**

`VideoDao.kt`:
```kotlin
package com.megaflix.tv.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    // ignore-on-conflict so a rescan never wipes positionMs of known files;
    // new files insert, known files are updated via updateMeta below.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(items: List<VideoEntity>)

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): VideoEntity?

    @Query("SELECT * FROM videos ORDER BY addedEpochSec DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY addedEpochSec DESC")
    suspend fun getAll(): List<VideoEntity>

    @Query("UPDATE videos SET positionMs = :positionMs, durationMs = :durationMs WHERE uri = :uri")
    suspend fun updateProgress(uri: String, positionMs: Long, durationMs: Long)

    @Query("SELECT uri FROM videos")
    suspend fun allUris(): List<String>
}
```

- [ ] **Step 3: Database**

`LibraryDatabase.kt`:
```kotlin
package com.megaflix.tv.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VideoEntity::class], version = 1, exportSchema = false)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    companion object {
        @Volatile private var instance: LibraryDatabase? = null
        fun get(context: Context): LibraryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "megaflix.db"
                ).build().also { instance = it }
            }
    }
}
```

- [ ] **Step 4: Wire into Graph**

Update `Graph.kt`:
```kotlin
package com.megaflix.tv

import android.content.Context
import com.megaflix.tv.data.LibraryDatabase

class Graph(appContext: Context) {
    val db = LibraryDatabase.get(appContext)
    val videoDao = db.videoDao()
}
```

- [ ] **Step 5: Build to verify Room codegen**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, KSP generates `VideoDao_Impl`. A schema error here means the entity/DAO annotations are wrong — fix before committing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/megaflix/tv/data app/src/main/java/com/megaflix/tv/Graph.kt
git commit -m "feat: Room library database with watch-progress columns

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: DevMediaSource (MediaStore scan)

Lists videos the emulator can see under shared storage via MediaStore. Verified against the emulator with a pushed file.

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/media/DevMediaSource.kt`
- Modify: `Graph.kt`

**Interfaces:**
- Consumes: `MediaSource`, `VideoFile`, `VIDEO_EXTENSIONS` (Task 2).
- Produces: `class DevMediaSource(context: Context) : MediaSource`. `Graph.source: MediaSource`.

- [ ] **Step 1: Implement DevMediaSource**

`DevMediaSource.kt`:
```kotlin
package com.megaflix.tv.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 1 source. Reads whatever the emulator has indexed under shared
 * storage (we push files into Movies/ then trigger a media scan — see
 * push-test-media.sh). MediaStore avoids raw /sdcard File reads, which
 * scoped storage blocks on API 30+ without all-files access.
 */
class DevMediaSource(private val context: Context) : MediaSource {

    override suspend fun listVideoFiles(): List<VideoFile> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
        val result = ArrayList<VideoFile>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in VIDEO_EXTENSIONS) continue
                val id = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, id)
                result += VideoFile(
                    uri = contentUri.toString(),
                    filename = name,
                    sizeBytes = c.getLong(sizeCol),
                    modifiedEpochSec = c.getLong(modCol),
                )
            }
        }
        result
    }

    override fun openPlayableUri(file: VideoFile): Uri = Uri.parse(file.uri)
}
```

- [ ] **Step 2: Select source in Graph (the one-line swap)**

Update `Graph.kt`:
```kotlin
package com.megaflix.tv

import android.content.Context
import com.megaflix.tv.data.LibraryDatabase
import com.megaflix.tv.media.DevMediaSource
import com.megaflix.tv.media.MediaSource
// import com.megaflix.tv.media.UsbHddSource  // enabled in Phase 4

class Graph(appContext: Context) {
    val db = LibraryDatabase.get(appContext)
    val videoDao = db.videoDao()

    // The swap point. Phase 4 flips USE_USB_SOURCE and the import above.
    val source: MediaSource =
        if (BuildConfig.USE_USB_SOURCE) TODO("Phase 4: UsbHddSource(appContext)")
        else DevMediaSource(appContext)
}
```

(Task 5 replaces the `TODO` with the real `UsbHddSource(appContext)` call once that class exists — still guarded by the `false` flag, so it never runs.)

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/megaflix/tv/media/DevMediaSource.kt app/src/main/java/com/megaflix/tv/Graph.kt
git commit -m "feat: DevMediaSource scanning MediaStore videos

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: UsbHddSource + SafMediaSource (written now, disabled behind the flag)

Full real-hardware implementation, compiled but never run in Phase 1. Heavy inline comments so Phase 4 is a flag flip. This is a spec requirement ("WRITE IT NOW, FULLY IMPLEMENTED").

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/media/UsbHddSource.kt`
- Create: `app/src/main/java/com/megaflix/tv/media/SafMediaSource.kt`
- Modify: `Graph.kt` (replace the `TODO` with a guarded real call)
- Modify: `AndroidManifest.xml` (add `MANAGE_EXTERNAL_STORAGE`, commented purpose)

**Interfaces:**
- Consumes: `MediaSource`, `VideoFile`, `VIDEO_EXTENSIONS`.
- Produces: `class UsbHddSource(context: Context) : MediaSource` with `fun hasAllFilesAccess(): Boolean` and `fun requestAllFilesAccessIntent(): Intent`; `class SafMediaSource(context, treeUri: Uri) : MediaSource`.

- [ ] **Step 1: UsbHddSource**

`UsbHddSource.kt`:
```kotlin
package com.megaflix.tv.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PHASE 4 SOURCE — compiled now, never instantiated while USE_USB_SOURCE=false.
 *
 * Reads video files directly off the removable USB HDD plugged into the TCL TV.
 * Two things it must handle on real hardware:
 *   1. Finding the removable volume among StorageManager.storageVolumes.
 *   2. Getting read access — MANAGE_EXTERNAL_STORAGE (all-files) is fine for a
 *      sideloaded personal app; SafMediaSource is the folder-picker fallback.
 */
class UsbHddSource(private val context: Context) : MediaSource {

    /** True once the user has granted all-files access in system settings. */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else true // pre-R: covered by READ_EXTERNAL_STORAGE granted at install/runtime

    /** Intent to send the user to the all-files-access toggle for this app. */
    fun requestAllFilesAccessIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData(Uri.parse("package:${context.packageName}"))

    override suspend fun listVideoFiles(): List<VideoFile> = withContext(Dispatchers.IO) {
        val root = findRemovableVolumeRoot()
            ?: return@withContext emptyList()
        val out = ArrayList<VideoFile>()
        // Recursive walk; USB drives are shallow enough that a plain walk is fine.
        root.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
            .forEach { f ->
                out += VideoFile(
                    uri = Uri.fromFile(f).toString(),
                    filename = f.name,
                    sizeBytes = f.length(),
                    modifiedEpochSec = f.lastModified() / 1000,
                )
            }
        out
    }

    /**
     * Picks the first removable, mounted volume's directory. On the TCL the USB
     * HDD shows up here once mounted; internal storage is filtered out by
     * isRemovable. Uses StorageVolume.directory on API 30+, reflection-free.
     */
    private fun findRemovableVolumeRoot(): File? {
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        for (vol in sm.storageVolumes) {
            if (!vol.isRemovable) continue
            if (vol.state != Environment.MEDIA_MOUNTED) continue
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory else null
            if (dir != null) return dir
        }
        return null
    }

    override fun openPlayableUri(file: VideoFile): Uri = Uri.parse(file.uri)
}
```

- [ ] **Step 2: SafMediaSource (fallback)**

`SafMediaSource.kt`:
```kotlin
package com.megaflix.tv.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PHASE 4 FALLBACK — used if all-files access is refused. The user picks the
 * USB drive's folder via ACTION_OPEN_DOCUMENT_TREE; we persist the treeUri and
 * enumerate through DocumentFile. Requires the documentfile dependency
 * (add androidx.documentfile:documentfile:1.0.1 when enabling in Phase 4).
 */
class SafMediaSource(
    private val context: Context,
    private val treeUri: Uri,
) : MediaSource {

    override suspend fun listVideoFiles(): List<VideoFile> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val out = ArrayList<VideoFile>()
        fun walk(dir: DocumentFile) {
            for (child in dir.listFiles()) {
                if (child.isDirectory) walk(child)
                else {
                    val name = child.name ?: continue
                    if (name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS) {
                        out += VideoFile(
                            uri = child.uri.toString(),
                            filename = name,
                            sizeBytes = child.length(),
                            modifiedEpochSec = child.lastModified() / 1000,
                        )
                    }
                }
            }
        }
        walk(root)
        out
    }

    override fun openPlayableUri(file: VideoFile): Uri = Uri.parse(file.uri)
}
```

Note in the file header comment: `documentfile` dep is intentionally **not** added to `build.gradle.kts` in Phase 1 to keep the dependency set minimal; add it in Phase 4. Because `SafMediaSource` imports it, either add the dep now (commented dependency line, uncommented in Phase 4) **or** keep the class body but guard the import. Chosen approach: add `// implementation("androidx.documentfile:documentfile:1.0.1")` commented in `build.gradle.kts`, and in Phase 1 comment out the *body* of `SafMediaSource` with a `TODO("Phase 4")` so it compiles without the dep. Do the same for any `DocumentFile` reference. (UsbHddSource has no extra dep and stays fully compiled.)

- [ ] **Step 3: Manifest permission (present, unused in Phase 1)**

Add to `AndroidManifest.xml` above `<application>`:
```xml
    <!-- Phase 4 only: read the USB HDD directly. Unused while USE_USB_SOURCE=false. -->
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />
```
Add `xmlns:tools="http://schemas.android.com/tools"` to the `<manifest>` tag.

- [ ] **Step 4: Replace the Graph TODO with a guarded real call**

In `Graph.kt`, uncomment the import and change:
```kotlin
    val source: MediaSource =
        if (BuildConfig.USE_USB_SOURCE) UsbHddSource(appContext) // Phase 4 path, dormant now
        else DevMediaSource(appContext)
```

- [ ] **Step 5: Build — everything compiles, nothing USB runs**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Confirm no USB code path is reachable: `USE_USB_SOURCE` is `false`, so `UsbHddSource` is constructed only in the dead branch.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/megaflix/tv/media/UsbHddSource.kt \
        app/src/main/java/com/megaflix/tv/media/SafMediaSource.kt \
        app/src/main/AndroidManifest.xml app/src/main/java/com/megaflix/tv/Graph.kt
git commit -m "feat: USB HDD + SAF sources (Phase 4, behind USE_USB_SOURCE flag)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Library scan pipeline (source → parse → Room)

Wires `MediaSource` → `FilenameParser` → `VideoDao`. New files insert; known files keep their `positionMs`.

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/data/LibraryRepository.kt`
- Modify: `Graph.kt`

**Interfaces:**
- Consumes: `Graph.source`, `Graph.videoDao`, `FilenameParser`, `VideoFile`, `VideoEntity`.
- Produces: `class LibraryRepository(source, dao)` with `suspend fun scan(): Int` (returns count of newly added), `fun observeLibrary(): Flow<List<VideoEntity>>`, `suspend fun saveProgress(uri, positionMs, durationMs)`, `suspend fun getByUri(uri): VideoEntity?`. `Graph.repository: LibraryRepository`.

- [ ] **Step 1: Implement repository**

`LibraryRepository.kt`:
```kotlin
package com.megaflix.tv.data

import com.megaflix.tv.media.FilenameParser
import com.megaflix.tv.media.MediaSource
import kotlinx.coroutines.flow.Flow

class LibraryRepository(
    private val source: MediaSource,
    private val dao: VideoDao,
) {
    /** Scans the source, inserts unseen files, returns how many were new. */
    suspend fun scan(): Int {
        val files = source.listVideoFiles()
        val known = dao.allUris().toHashSet()
        val now = System.currentTimeMillis() / 1000
        val fresh = files.filter { it.uri !in known }.map { f ->
            val p = FilenameParser.parse(f.filename)
            VideoEntity(
                uri = f.uri,
                filename = f.filename,
                title = p.title,
                year = p.year,
                season = p.season,
                episode = p.episode,
                sizeBytes = f.sizeBytes,
                modifiedEpochSec = f.modifiedEpochSec,
                addedEpochSec = now,
            )
        }
        if (fresh.isNotEmpty()) dao.insertNew(fresh)
        return fresh.size
    }

    fun observeLibrary(): Flow<List<VideoEntity>> = dao.observeAll()
    suspend fun getByUri(uri: String) = dao.getByUri(uri)
    suspend fun saveProgress(uri: String, positionMs: Long, durationMs: Long) =
        dao.updateProgress(uri, positionMs, durationMs)
}
```

- [ ] **Step 2: Add to Graph**

Append to `Graph.kt`:
```kotlin
    val repository = com.megaflix.tv.data.LibraryRepository(source, videoDao)
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`  → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/megaflix/tv/data/LibraryRepository.kt app/src/main/java/com/megaflix/tv/Graph.kt
git commit -m "feat: library scan pipeline (source to parser to Room)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Poster grid UI + D-pad focus

`LibraryActivity` shows a scanning-triggered grid of poster cards. Placeholder posters (Phase 1). Focus scales the card + shows a glow, per the performance rules. Runtime `READ_MEDIA_VIDEO` permission is requested here.

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/ui/PosterAdapter.kt`
- Modify: `app/src/main/java/com/megaflix/tv/ui/LibraryActivity.kt`
- Create: `app/src/main/res/layout/item_poster.xml`
- Modify: `app/src/main/res/layout/activity_library.xml`
- Create: `app/src/main/res/drawable/poster_placeholder.xml`, `card_focus.xml`

**Interfaces:**
- Consumes: `App.graph.repository`, `VideoEntity`.
- Produces: `PosterAdapter(onClick: (VideoEntity) -> Unit)` with `submit(list)`; `LibraryActivity` launches `PlayerActivity` with `PlayerActivity.EXTRA_URI`.

- [ ] **Step 1: Drawables**

`poster_placeholder.xml` (gradient card):
```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="10dp" />
    <gradient android:angle="90" android:startColor="#23232C" android:endColor="#14141A" />
</shape>
```

`card_focus.xml` (a `layer-list` selector for the focused border — the adapter toggles it; simplest is a stateful selector drawable):
```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_focused="true">
        <shape android:shape="rectangle">
            <corners android:radius="10dp" />
            <stroke android:width="3dp" android:color="@color/mf_focus" />
        </shape>
    </item>
    <item android:drawable="@android:color/transparent" />
</selector>
```

- [ ] **Step 2: Item layout**

`item_poster.xml`:
```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/card"
    android:layout_width="150dp"
    android:layout_height="225dp"
    android:layout_margin="10dp"
    android:background="@drawable/poster_placeholder"
    android:foreground="@drawable/card_focus"
    android:focusable="true"
    android:focusableInTouchMode="false"
    android:clipChildren="false">

    <TextView
        android:id="@+id/title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:padding="8dp"
        android:maxLines="2"
        android:ellipsize="end"
        android:textColor="@color/mf_text"
        android:textSize="13sp"
        android:background="#99000000" />
</FrameLayout>
```

- [ ] **Step 3: activity_library.xml — grid**

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/mf_bg">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/grid"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="32dp"
        android:clipToPadding="false"
        android:clipChildren="false" />

    <TextView
        android:id="@+id/empty"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:textColor="@color/mf_text_dim"
        android:text="@string/empty_library"
        android:visibility="gone" />
</FrameLayout>
```

- [ ] **Step 4: PosterAdapter (stable ids, no per-bind allocation, focus animation)**

`PosterAdapter.kt`:
```kotlin
package com.megaflix.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.megaflix.tv.R
import com.megaflix.tv.data.VideoEntity

class PosterAdapter(
    private val onClick: (VideoEntity) -> Unit,
) : ListAdapter<VideoEntity, PosterAdapter.VH>(DIFF) {

    init { setHasStableIds(true) }
    override fun getItemId(position: Int): Long = getItem(position).id

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poster, parent, false)
        val vh = VH(v)
        // Focus animation set once at create time — no allocation per bind.
        v.setOnFocusChangeListener { view, focused ->
            val s = if (focused) 1.12f else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(150).start()
            if (focused) view.bringToFront()
        }
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.title.text = when {
            item.season != null && item.episode != null ->
                "${item.title} S${item.season}E${item.episode}"
            item.year != null -> "${item.title} (${item.year})"
            else -> item.title
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VideoEntity>() {
            override fun areItemsTheSame(a: VideoEntity, b: VideoEntity) = a.id == b.id
            override fun areContentsTheSame(a: VideoEntity, b: VideoEntity) = a == b
        }
    }
}
```

- [ ] **Step 5: LibraryActivity — permission, scan, observe**

`LibraryActivity.kt`:
```kotlin
package com.megaflix.tv.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.megaflix.tv.App
import com.megaflix.tv.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryActivity : AppCompatActivity() {

    private val repo by lazy { (application as App).graph.repository }
    private lateinit var adapter: PosterAdapter
    private lateinit var empty: TextView

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { rescanAndObserve() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        empty = findViewById(R.id.empty)
        adapter = PosterAdapter { video ->
            startActivity(Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URI, video.uri))
        }
        findViewById<RecyclerView>(R.id.grid).apply {
            layoutManager = GridLayoutManager(this@LibraryActivity, 6)
            adapter = this@LibraryActivity.adapter
            setHasFixedSize(true)
        }
        ensurePermissionThenScan()
    }

    private fun ensurePermissionThenScan() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
                   else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            rescanAndObserve()
        } else {
            permLauncher.launch(perm)
        }
    }

    private fun rescanAndObserve() {
        lifecycleScope.launch {
            repo.scan()
            repo.observeLibrary().collectLatest { list ->
                adapter.submitList(list)
                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-scan on return so newly pushed files and updated progress show up.
        lifecycleScope.launch { repo.scan() }
    }
}
```

- [ ] **Step 6: Run + verify on emulator**

```bash
# ensure at least one H.264 mp4 is present (see Task 10); then:
./gradlew installDebug
adb shell monkey -p com.megaflix.tv -c android.intent.category.LEANBACK_LAUNCHER 1
```
Expected: permission dialog (accept via D-pad), then poster cards appear. D-pad left/right/up/down moves focus; the focused card scales up with a white border. Empty message shows only when no videos exist.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/megaflix/tv/ui/PosterAdapter.kt \
        app/src/main/java/com/megaflix/tv/ui/LibraryActivity.kt \
        app/src/main/res/layout app/src/main/res/drawable
git commit -m "feat: poster grid with D-pad focus scaling

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: ExoPlayer playback screen + D-pad controls

`PlayerActivity` plays the selected file full-screen with Media3's `PlayerView` (its default controls are D-pad friendly: play/pause, seek bar, and the OK button toggles controls). Back exits.

**Files:**
- Create: `app/src/main/res/layout/activity_player.xml`
- Modify: `app/src/main/java/com/megaflix/tv/ui/PlayerActivity.kt`

**Interfaces:**
- Consumes: `App.graph.repository`, `App.graph.source.openPlayableUri` (via repo not needed — Player builds `MediaItem` from the uri string directly).
- Produces: `PlayerActivity` with `const val EXTRA_URI`. Task 9 extends it with resume/save.

- [ ] **Step 1: Player layout**

`activity_player.xml`:
```xml
<androidx.media3.ui.PlayerView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/player"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000"
    app:use_controller="true"
    app:show_buffering="when_playing"
    xmlns:app="http://schemas.android.com/apk/res-auto" />
```

- [ ] **Step 2: PlayerActivity (playback only; resume added in Task 9)**

`PlayerActivity.kt`:
```kotlin
package com.megaflix.tv.ui

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.megaflix.tv.R

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var uri: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        uri = intent.getStringExtra(EXTRA_URI) ?: run { finish(); return }
    }

    override fun onStart() {
        super.onStart()
        val exo = ExoPlayer.Builder(this).build()
        findViewById<PlayerView>(R.id.player).player = exo
        exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        exo.playWhenReady = true
        exo.prepare()
        player = exo
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URI = "extra_uri"
    }
}
```

- [ ] **Step 3: Run + verify playback**

Install, open a poster, press OK. Expected: the H.264 `.mp4` plays full-screen; D-pad down shows controls, left/right seeks, OK toggles play/pause, Back returns to the grid.
**HEVC note:** the HotD `.mkv` (HEVC/x265) may show a black screen or decoder error *on the Mac emulator* — that is an emulator codec limitation, not an app bug. Verify playback with the H.264 mp4 on the emulator; the HEVC file is validated in Phase 4 on the TCL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/megaflix/tv/ui/PlayerActivity.kt app/src/main/res/layout/activity_player.xml
git commit -m "feat: ExoPlayer full-screen playback with D-pad controls

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: Watch progress (save on pause/exit, resume on reopen)

Extends `PlayerActivity` to seek to the saved position on open and persist position on stop. This is the Phase 1 exit criterion.

**Files:**
- Modify: `app/src/main/java/com/megaflix/tv/ui/PlayerActivity.kt`

**Interfaces:**
- Consumes: `App.graph.repository.getByUri`, `repository.saveProgress`.

- [ ] **Step 1: Load saved position before prepare, persist on stop**

Replace `PlayerActivity.kt` body with:
```kotlin
package com.megaflix.tv.ui

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.megaflix.tv.App
import com.megaflix.tv.R
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    private val repo by lazy { (application as App).graph.repository }
    private var player: ExoPlayer? = null
    private lateinit var uri: String
    private var startPositionMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        uri = intent.getStringExtra(EXTRA_URI) ?: run { finish(); return }
        lifecycleScope.launch {
            val existing = repo.getByUri(uri)
            // Resume unless we were within ~5s of the end (treat as finished).
            startPositionMs = existing
                ?.takeIf { it.durationMs == 0L || it.positionMs < it.durationMs - 5000 }
                ?.positionMs ?: 0
            preparePlayer()
        }
    }

    private fun preparePlayer() {
        val exo = ExoPlayer.Builder(this).build()
        findViewById<PlayerView>(R.id.player).player = exo
        exo.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        if (startPositionMs > 0) exo.seekTo(startPositionMs)
        exo.playWhenReady = true
        exo.prepare()
        player = exo
    }

    override fun onStop() {
        super.onStop()
        player?.let { p ->
            val pos = p.currentPosition
            val dur = if (p.duration > 0) p.duration else 0
            // Fire-and-forget against the app scope-less repo; use a blocking
            // save so it completes before the process can be reclaimed.
            kotlinx.coroutines.runBlocking { repo.saveProgress(uri, pos, dur) }
            p.release()
        }
        player = null
    }

    companion object { const val EXTRA_URI = "extra_uri" }
}
```
(If `runBlocking` on the main thread during `onStop` is a concern, an alternative is a `GlobalScope.launch` save; `runBlocking` is chosen here for guaranteed persistence before teardown and the write is a single indexed UPDATE.)

- [ ] **Step 2: Verify the exit criterion end-to-end**

1. Launch app, open the mp4, let it play ~30s.
2. Press Back (or Home) to leave mid-playback.
3. Relaunch app, open the same title.
   Expected: playback resumes near where you left off (within a second or two).
4. Confirm via db: `adb shell "run-as com.megaflix.tv sqlite3 databases/megaflix.db 'select title,positionMs from videos'"` shows a non-zero `positionMs`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/megaflix/tv/ui/PlayerActivity.kt
git commit -m "feat: save and resume watch progress

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: push-test-media.sh + test media docs

Helper that pushes videos from a Mac folder into the emulator and makes MediaStore index them. Include a note to keep one H.264 mp4 for emulator smoke tests alongside the real HEVC HotD file.

**Files:**
- Create: `push-test-media.sh`
- Modify: `docs/megaflix-spec.md` (add a "Test media" note) — optional, or a short `docs/testing.md`.

- [ ] **Step 1: Write the script**

`push-test-media.sh`:
```bash
#!/usr/bin/env bash
# Push local videos into the running Android TV emulator and index them.
# Usage: ./push-test-media.sh [~/megaflix-test-media]
set -euo pipefail

SRC="${1:-$HOME/megaflix-test-media}"
DEST="/sdcard/Movies"

if ! adb get-state >/dev/null 2>&1; then
  echo "No emulator/device. Boot the AVD first (Task 0)." >&2
  exit 1
fi

adb shell mkdir -p "$DEST"

shopt -s nullglob nocaseglob
count=0
for f in "$SRC"/*.{mp4,mkv,avi,mov,webm}; do
  echo "Pushing $(basename "$f")"
  adb push "$f" "$DEST/"
  # Make MediaStore see the new file immediately.
  adb shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d file://$DEST/$(basename "$f")" >/dev/null
  count=$((count+1))
done

if [ "$count" -eq 0 ]; then
  echo "No videos found in $SRC" >&2
  exit 1
fi
echo "Pushed $count file(s). Trigger a full rescan:"
adb shell "cmd media_scanner scan $DEST" >/dev/null 2>&1 || true
echo "Done. Open Megaflix and it will pick them up on resume."
```

```bash
chmod +x push-test-media.sh
```

- [ ] **Step 2: Seed test media + document**

```bash
mkdir -p ~/megaflix-test-media
# Put at least one small H.264 mp4 here for emulator playback (e.g. a Big Buck
# Bunny 720p H.264 sample) AND copy the real example for parser/metadata testing:
cp "/Users/t31k/Downloads/House.of.the.Dragon.S03E05.1080p.HEVC.x265-MeGusta[EZTVx.to].mkv" ~/megaflix-test-media/
./push-test-media.sh
```
Add a short note to the spec's Notes section: *emulator = H.264 for playback; HEVC validates on the TCL in Phase 4.*

- [ ] **Step 3: Commit**

```bash
git add push-test-media.sh docs/megaflix-spec.md
git commit -m "chore: push-test-media helper and test-media notes

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Phase 1 Exit Verification (run after Task 10)

- [ ] Emulator boots; `./gradlew installDebug` succeeds.
- [ ] `./push-test-media.sh` pushes the H.264 mp4 + HotD mkv; both appear as poster cards.
- [ ] The HotD card title reads **"House of the Dragon S3E5"** (parser correctness on real data).
- [ ] D-pad navigates the grid; focused card scales + glows; no dropped-frame stutter.
- [ ] Playing the H.264 mp4 works full-screen with D-pad controls.
- [ ] Quit mid-playback, reopen → resumes from saved position.
- [ ] `./gradlew :app:testDebugUnitTest` green (FilenameParser).
- [ ] Release build compiles with minify: `./gradlew :app:assembleRelease`.

---

## Self-Review notes

- **Spec coverage:** scaffold (T1), DevMediaSource (T4), push script (T10), scan pipeline (T6), poster grid + D-pad playback (T7/T8), watch progress (T9), UsbHddSource written-now-disabled (T5) — all Phase 1 spec items mapped. Filename parser (spec lists it under Phase 2) is pulled into T2 because the first real file is a TV episode and the grid needs clean titles now; Phase 2 reuses it for TMDB.
- **Deferred to Phase 2 (not this plan):** TMDB, Coil, real posters, genre rows, hero banner, detail/search screens. Placeholder posters are intentional here.
- **Known trade-off flagged for the executor:** HEVC won't reliably play on the Mac emulator; H.264 mp4 is the Phase 1 playback fixture, HotD mkv is the parser fixture + Phase 4 playback fixture.
- **Type consistency:** `VideoFile(uri,filename,sizeBytes,modifiedEpochSec)`, `ParsedName(title,year,season,episode)`, and `VideoEntity` field names are used identically across T2/T3/T6/T7/T9.
