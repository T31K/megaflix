# Jellyfin-Style UI (TMDB posters + Detail Screen) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Megaflix's home page show real TMDB poster art and add a Jellyfin-style movie detail screen (backdrop hero, ratings, overview, Play button, cast row), then ship the demo APK to https://t31k.github.io/megaflix/.

**Architecture:** Stay 100% classic Android Views (NO Compose, NO Leanback — this is a hard project rule for speed on a low-end TCL TV). Add a tiny TMDB client (HttpURLConnection + org.json, no Retrofit), store fetched metadata in the existing Room `videos` table, load images with Coil. New `DetailActivity` copies the layout structure of Jellyfin ATV's `view_row_details.xml` (reference clone at `/tmp/claude-501/-Users-t31k-Projects-Megaflix/83debcd8-11c4-49aa-bae0-e35e3f6fe4e3/scratchpad/jellyfin-androidtv` — if missing, not needed; this plan contains everything).

**Tech Stack:** Kotlin, classic Views/XML, RecyclerView, Room 2.6.1, Coil 2.7.0, Media3 ExoPlayer, TMDB API v3.

**Spec:** No separate spec file — the Design Summary below is the approved design (approved in-conversation 2026-09-18).

## Global Constraints

- **NO Jetpack Compose. NO Leanback.** Classic Views + XML only.
- **No new heavyweight deps.** Only addition allowed: `io.coil-kt:coil:2.7.0`. HTTP via `java.net.HttpURLConnection`, JSON via `org.json` (built into Android).
- **Every gradle command needs this env first** (the `./gradlew` launcher requires a JVM on PATH):
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; export PATH="$JAVA_HOME/bin:$PATH"; export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools; export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"`
- **TMDB v3 API key:** `8b6f7e9a19bd57cca4cd213917274d13` (user-provided; personal-use project; it will be visible in the public repo/APK — user accepted this).
- **D-pad first:** every interactive element must be reachable/usable with D-pad only (TV remote). Focus states must be visible.
- Commit after every task. Follow the commit-attribution reminder active in the executing session.
- Working dir: `/Users/t31k/Projects/Megaflix`, branch `main`, pushes deploy GitHub Pages from `/docs`.

## Design Summary (what "done" looks like)

1. **Home page:** existing top tabs + rows stay; poster cards now show real TMDB poster art (Coil), no title text over posters that have art. Demo library expanded to 12 well-known titles so the page looks rich.
2. **Detail screen (new, modeled on Jellyfin):** full-bleed blurred-look backdrop image behind a dark scrim; movie title (30sp, `sans-serif-light`); info row (year · runtime · ★ rating badges); genre line; 6-line overview; button row **Play** (+ **Resume** when there's saved progress); poster image on the right; horizontal **Cast & Crew** row at bottom with headshots + names. Clicking a home poster opens Detail; Play inside Detail starts `PlayerActivity`.
3. **Shipped:** debug-key-signed release APK (DEMO_MODE=true) at `docs/megaflix.apk`, pushed to `main` → https://t31k.github.io/megaflix/.

---

### Task 1: TMDB plumbing — INTERNET permission, Coil dependency, API key BuildConfig

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `BuildConfig.TMDB_API_KEY: String`, Coil's `ImageView.load(url)` available app-wide, network access permitted.

- [ ] **Step 1: Add Coil to the version catalog.** In `gradle/libs.versions.toml` under `[libraries]` add:

```toml
coil = { module = "io.coil-kt:coil", version = "2.7.0" }
```

- [ ] **Step 2: Wire dependency + BuildConfig key.** In `app/build.gradle.kts`: add `implementation(libs.coil)` to `dependencies { }`. In the same file find `defaultConfig { }` (or the block where `DEMO_MODE` buildConfigField already lives — read the file first to match its style) and add:

```kotlin
buildConfigField("String", "TMDB_API_KEY", "\"8b6f7e9a19bd57cca4cd213917274d13\"")
```

- [ ] **Step 3: Manifest.** In `app/src/main/AndroidManifest.xml` add above `<application>`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 4: Verify it builds.** Run (with the env exports from Global Constraints):

```bash
cd /Users/t31k/Projects/Megaflix && ./gradlew assembleDebug -q
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** — `feat: TMDB key, Coil, INTERNET permission`

---

### Task 2: Extend VideoEntity + DAO for TMDB metadata

**Files:**
- Modify: `app/src/main/java/com/megaflix/tv/data/VideoEntity.kt`
- Modify: `app/src/main/java/com/megaflix/tv/data/VideoDao.kt`
- Modify: `app/src/main/java/com/megaflix/tv/data/LibraryDatabase.kt`

**Interfaces:**
- Produces: new nullable fields on `VideoEntity`: `tmdbId: Long?`, `posterPath: String?`, `backdropPath: String?`, `overview: String?`, `rating: Double?`, `runtimeMin: Int?`, `genres: String?` (comma-joined display string), and `tmdbChecked: Boolean = false`; DAO methods `unenriched(): List<VideoEntity>`, `update(v: VideoEntity)`, `byId(id: Long): VideoEntity?`.

- [ ] **Step 1: Add fields to `VideoEntity`** (append to the data class, all with defaults so existing constructor call-sites compile unchanged):

```kotlin
    // TMDB enrichment (Phase 2). tmdbChecked=true once a lookup ran (hit or miss)
    // so we never re-query for the same file.
    val tmdbId: Long? = null,
    val posterPath: String? = null,     // e.g. "/abc.jpg" — prepend image base URL
    val backdropPath: String? = null,
    val overview: String? = null,
    val rating: Double? = null,         // TMDB vote_average, 0..10
    val runtimeMin: Int? = null,
    val genres: String? = null,         // display-ready, e.g. "Action / Sci-Fi"
    val tmdbChecked: Boolean = false,
```

- [ ] **Step 2: Add DAO methods** to `VideoDao.kt` (read the file first; add alongside existing queries, matching its style):

```kotlin
    @Query("SELECT * FROM videos WHERE tmdbChecked = 0")
    suspend fun unenriched(): List<VideoEntity>

    @Update
    suspend fun update(video: VideoEntity)

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun byId(id: Long): VideoEntity?
```
(Import `androidx.room.Update` if missing.)

- [ ] **Step 3: Bump DB version with destructive migration** (demo/personal app — data is rebuilt by scan anyway). In `LibraryDatabase.kt`: change `version = 1` to `version = 2` in the `@Database` annotation, and where the database is built (`Room.databaseBuilder(...)` — it may live here or in `Graph.kt`; grep for `databaseBuilder`) chain `.fallbackToDestructiveMigration()`.

- [ ] **Step 4: Verify build:** `./gradlew assembleDebug -q` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** — `feat: TMDB metadata columns in Room (v2, destructive migration)`

---

### Task 3: TmdbClient — search + parse (TDD on the pure parser)

**Files:**
- Create: `app/src/main/java/com/megaflix/tv/data/TmdbClient.kt`
- Test: `app/src/test/java/com/megaflix/tv/data/TmdbClientTest.kt`

**Interfaces:**
- Consumes: `BuildConfig.TMDB_API_KEY` (Task 1).
- Produces:
  - `data class TmdbMatch(val tmdbId: Long, val posterPath: String?, val backdropPath: String?, val overview: String?, val rating: Double?, val genres: String?)`
  - `data class TmdbCastMember(val name: String, val role: String?, val profilePath: String?)`
  - `object TmdbClient` with `suspend fun search(title: String, year: Int?, isTv: Boolean): TmdbMatch?`, `suspend fun credits(tmdbId: Long, isTv: Boolean): List<TmdbCastMember>`, and pure functions `parseSearch(json: String): TmdbMatch?`, `parseCredits(json: String): List<TmdbCastMember>`.
  - Image URL helpers: `posterUrl(path: String?) = path?.let { "https://image.tmdb.org/t/p/w342$it" }`, `backdropUrl(path: String?) = path?.let { "https://image.tmdb.org/t/p/w1280$it" }`, `profileUrl(path: String?) = path?.let { "https://image.tmdb.org/t/p/w185$it" }`.

**Note:** `org.json` classes are stubs in local unit tests by default. `app/build.gradle.kts` must get, inside `android { }`:
```kotlin
testOptions { unitTests.isReturnDefaultValues = false }
```
…actually simpler and more reliable: add the real JSON impl for tests only in `dependencies { }`:
```kotlin
testImplementation("org.json:json:20240303")
```
Use that (the `testImplementation` line), not `testOptions`.

- [ ] **Step 1: Write the failing test** `app/src/test/java/com/megaflix/tv/data/TmdbClientTest.kt`:

```kotlin
package com.megaflix.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbClientTest {

    @Test
    fun `parseSearch picks first result and maps fields`() {
        val json = """
        {"results":[{"id":603,"poster_path":"/p.jpg","backdrop_path":"/b.jpg",
        "overview":"A hacker learns the truth.","vote_average":8.2,"genre_ids":[28,878]}]}
        """.trimIndent()
        val m = TmdbClient.parseSearch(json)!!
        assertEquals(603L, m.tmdbId)
        assertEquals("/p.jpg", m.posterPath)
        assertEquals("/b.jpg", m.backdropPath)
        assertEquals("A hacker learns the truth.", m.overview)
        assertEquals(8.2, m.rating!!, 0.001)
        assertEquals("Action / Science Fiction", m.genres)
    }

    @Test
    fun `parseSearch returns null on empty results`() {
        assertNull(TmdbClient.parseSearch("""{"results":[]}"""))
    }

    @Test
    fun `parseCredits maps top cast`() {
        val json = """
        {"cast":[{"name":"Keanu Reeves","character":"Neo","profile_path":"/k.jpg"},
                 {"name":"Carrie-Anne Moss","character":"Trinity","profile_path":null}]}
        """.trimIndent()
        val cast = TmdbClient.parseCredits(json)
        assertEquals(2, cast.size)
        assertEquals("Keanu Reeves", cast[0].name)
        assertEquals("Neo", cast[0].role)
        assertEquals("/k.jpg", cast[0].profilePath)
        assertNull(cast[1].profilePath)
    }
}
```

- [ ] **Step 2: Run it, verify it fails:** `./gradlew testDebugUnitTest --tests '*TmdbClientTest*' -q` → FAIL (unresolved reference `TmdbClient`).

- [ ] **Step 3: Implement** `app/src/main/java/com/megaflix/tv/data/TmdbClient.kt`:

```kotlin
package com.megaflix.tv.data

import com.megaflix.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TmdbMatch(
    val tmdbId: Long,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val rating: Double?,
    val genres: String?,
)

data class TmdbCastMember(val name: String, val role: String?, val profilePath: String?)

/**
 * Minimal TMDB v3 client. No SDK deps: HttpURLConnection + org.json.
 * Pure parse functions are unit-tested; network wrappers are thin.
 */
object TmdbClient {

    private const val BASE = "https://api.themoviedb.org/3"

    fun posterUrl(path: String?) = path?.let { "https://image.tmdb.org/t/p/w342$it" }
    fun backdropUrl(path: String?) = path?.let { "https://image.tmdb.org/t/p/w1280$it" }
    fun profileUrl(path: String?) = path?.let { "https://image.tmdb.org/t/p/w185$it" }

    suspend fun search(title: String, year: Int?, isTv: Boolean): TmdbMatch? {
        val kind = if (isTv) "tv" else "movie"
        val yearParam = when {
            year == null -> ""
            isTv -> "&first_air_date_year=$year"
            else -> "&year=$year"
        }
        val q = URLEncoder.encode(title, "UTF-8")
        val url = "$BASE/search/$kind?api_key=${BuildConfig.TMDB_API_KEY}&query=$q$yearParam"
        return get(url)?.let(::parseSearch)
    }

    suspend fun credits(tmdbId: Long, isTv: Boolean): List<TmdbCastMember> {
        val kind = if (isTv) "tv" else "movie"
        val url = "$BASE/$kind/$tmdbId/credits?api_key=${BuildConfig.TMDB_API_KEY}"
        return get(url)?.let(::parseCredits) ?: emptyList()
    }

    fun parseSearch(json: String): TmdbMatch? {
        val results = JSONObject(json).optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val r = results.getJSONObject(0)
        val genreNames = r.optJSONArray("genre_ids")?.let { ids ->
            (0 until ids.length()).mapNotNull { GENRES[ids.getInt(it)] }
        }?.takeIf { it.isNotEmpty() }?.joinToString(" / ")
        return TmdbMatch(
            tmdbId = r.getLong("id"),
            posterPath = r.optString("poster_path").ifEmpty { null }
                ?.takeIf { it != "null" },
            backdropPath = r.optString("backdrop_path").ifEmpty { null }
                ?.takeIf { it != "null" },
            overview = r.optString("overview").ifEmpty { null },
            rating = r.optDouble("vote_average").takeIf { !it.isNaN() && it > 0.0 },
            genres = genreNames,
        )
    }

    fun parseCredits(json: String): List<TmdbCastMember> {
        val cast = JSONObject(json).optJSONArray("cast") ?: return emptyList()
        return (0 until minOf(cast.length(), 12)).map { i ->
            val c = cast.getJSONObject(i)
            TmdbCastMember(
                name = c.getString("name"),
                role = c.optString("character").ifEmpty { null },
                profilePath = c.optString("profile_path").ifEmpty { null }
                    ?.takeIf { it != "null" },
            )
        }
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            try {
                if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText()
                else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull() // offline / TMDB down → no enrichment, never crash
    }

    /** TMDB genre ids → names (movie + TV merged; duplicates share names). */
    private val GENRES = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
        80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
        14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction",
        10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western",
        10759 to "Action & Adventure", 10762 to "Kids", 10763 to "News",
        10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap",
        10767 to "Talk", 10768 to "War & Politics",
    )
}
```

- [ ] **Step 4: Add the test-only JSON dep** (see Note above) to `app/build.gradle.kts` dependencies: `testImplementation("org.json:json:20240303")`.

- [ ] **Step 5: Run tests, verify pass:** `./gradlew testDebugUnitTest --tests '*TmdbClientTest*' -q` → PASS (3 tests).

- [ ] **Step 6: Commit** — `feat: minimal TMDB v3 client with tested parsers`

---

### Task 4: Enrichment pipeline — fetch TMDB data after scan

**Files:**
- Modify: `app/src/main/java/com/megaflix/tv/data/LibraryRepository.kt`
- Modify: `app/src/main/java/com/megaflix/tv/ui/LibraryActivity.kt`

**Interfaces:**
- Consumes: `TmdbClient.search` (Task 3), `dao.unenriched()/update()` (Task 2), existing parsed `title`/`year`/`season` on entities.
- Produces: `suspend fun LibraryRepository.enrichMissing()` — safe to call repeatedly; no-ops offline.

- [ ] **Step 1: Add `enrichMissing()` to `LibraryRepository`** (read the file first; it exposes the DAO — match its property name):

```kotlin
    /**
     * Fetch TMDB art/metadata for entries that haven't been looked up yet.
     * A row is marked tmdbChecked even on a miss so we only ever query once
     * per file. Runs after scan; UI updates arrive via the existing Flow.
     */
    suspend fun enrichMissing() {
        for (v in dao.unenriched()) {
            val match = TmdbClient.search(
                title = v.title,
                year = v.year,
                isTv = v.season != null,
            )
            dao.update(
                if (match == null) v.copy(tmdbChecked = true)
                else v.copy(
                    tmdbChecked = true,
                    tmdbId = match.tmdbId,
                    posterPath = match.posterPath,
                    backdropPath = match.backdropPath,
                    overview = match.overview,
                    rating = match.rating,
                    genres = match.genres,
                )
            )
        }
    }
```
(If the DAO property has a different name than `dao`, adapt. If `search` returned null because we're offline the row still gets marked checked — acceptable for demo; note it in the commit message.)

- [ ] **Step 2: Call it after scan.** In `LibraryActivity.rescanAndObserve()` change `repo.scan()` to:

```kotlin
            repo.scan()
            repo.enrichMissing()
```
And in `onResume()` the same (`repo.scan()` → `repo.scan(); repo.enrichMissing()` inside the existing launch).

- [ ] **Step 3: Verify build:** `./gradlew assembleDebug -q` → BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** — `feat: TMDB enrichment pass after library scan`

---

### Task 5: Real posters on the home page (Coil in PosterAdapter)

**Files:**
- Modify: `app/src/main/java/com/megaflix/tv/ui/PosterAdapter.kt`

**Interfaces:**
- Consumes: `VideoEntity.posterPath` (Task 2), `TmdbClient.posterUrl` (Task 3), Coil (Task 1).

- [ ] **Step 1: Replace the bind logic.** In `PosterAdapter.onBindViewHolder`, replace the whole `TEST_POSTERS` branch with:

```kotlin
    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val url = TmdbClient.posterUrl(item.posterPath)
        if (url != null) {
            holder.title.visibility = View.GONE
            holder.poster.load(url) { crossfade(true) }
        } else {
            holder.poster.setImageDrawable(null) // gradient placeholder shows through
            holder.title.visibility = View.VISIBLE
            holder.title.text = when {
                item.season != null && item.episode != null ->
                    "${item.title} S${item.season}E${item.episode}"
                item.year != null -> "${item.title} (${item.year})"
                else -> item.title
            }
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }
```
Add imports `import coil.load` and `import com.megaflix.tv.data.TmdbClient`. Delete the `TEST_POSTERS` map and its comment (keep the drawable files; they're harmless). Keep `holder.poster.scaleType` as the XML defines it — but verify `item_poster.xml`'s ImageView uses `android:scaleType="centerCrop"`; if not, set it so TMDB 2:3 posters fill the card.

- [ ] **Step 2: Verify build:** `./gradlew assembleDebug -q` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit** — `feat: home posters load real TMDB art via Coil`

---

### Task 6: Expand demo library to 12 titles

**Files:**
- Modify: `app/src/main/java/com/megaflix/tv/media/DemoMediaSource.kt`

**Interfaces:**
- Consumes: existing `demo(uri, filename)` helper; `FilenameParser` already extracts title/year/SxxExx from these patterns.

- [ ] **Step 1: Replace the list in `listVideoFiles()`** with:

```kotlin
    override suspend fun listVideoFiles(): List<VideoFile> = listOf(
        demo("demo://hotd", "House.of.the.Dragon.S03E05.1080p.HEVC.x265-MeGusta.mkv"),
        demo("demo://spacejam", "Space.Jam.1996.720p.mp4"),
        demo("demo://matrix", "The.Matrix.1999.1080p.mp4"),
        demo("demo://inception", "Inception.2010.1080p.mp4"),
        demo("demo://interstellar", "Interstellar.2014.1080p.mp4"),
        demo("demo://darkknight", "The.Dark.Knight.2008.1080p.mp4"),
        demo("demo://spirited", "Spirited.Away.2001.1080p.mp4"),
        demo("demo://pulpfiction", "Pulp.Fiction.1994.1080p.mp4"),
        demo("demo://forrestgump", "Forrest.Gump.1994.1080p.mp4"),
        demo("demo://gladiator", "Gladiator.2000.1080p.mp4"),
        demo("demo://fightclub", "Fight.Club.1999.1080p.mp4"),
        demo("demo://shawshank", "The.Shawshank.Redemption.1994.1080p.mp4"),
    )
```

- [ ] **Step 2: Verify build:** `./gradlew assembleDebug -q` → BUILD SUCCESSFUL.

- [ ] **Step 3: Commit** — `feat: 12-title demo library for a rich home page`

---

### Task 7: Detail screen — layout + activity (the Jellyfin copy)

**Files:**
- Create: `app/src/main/res/layout/activity_detail.xml`
- Create: `app/src/main/res/drawable/detail_scrim.xml`
- Create: `app/src/main/res/drawable/badge_bg.xml`
- Create: `app/src/main/res/layout/item_cast.xml`
- Create: `app/src/main/java/com/megaflix/tv/ui/DetailActivity.kt`
- Create: `app/src/main/java/com/megaflix/tv/ui/CastAdapter.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/megaflix/tv/ui/LibraryActivity.kt`

**Interfaces:**
- Consumes: `dao.byId` via a repository passthrough (add `suspend fun byId(id: Long) = dao.byId(id)` to `LibraryRepository`), `TmdbClient.backdropUrl/posterUrl/profileUrl/credits`, `PlayerActivity.EXTRA_URI`.
- Produces: `DetailActivity` with `EXTRA_ID: Long`; home click now opens Detail instead of Player.

- [ ] **Step 1: Scrim drawable** `app/src/main/res/drawable/detail_scrim.xml` (dark left-to-right + bottom-heavy gradient so text reads over any backdrop — Jellyfin does the same):

```xml
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape>
            <gradient android:angle="0"
                android:startColor="#F2101010"
                android:centerColor="#B3101010"
                android:endColor="#40101010" />
        </shape>
    </item>
    <item>
        <shape>
            <gradient android:angle="90"
                android:startColor="#E6101010"
                android:endColor="#00000000" />
        </shape>
    </item>
</layer-list>
```

- [ ] **Step 2: Badge background** `app/src/main/res/drawable/badge_bg.xml`:

```xml
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#33FFFFFF" />
    <corners android:radius="4dp" />
    <padding android:left="8dp" android:right="8dp" android:top="2dp" android:bottom="2dp" />
</shape>
```

- [ ] **Step 3: Cast card layout** `app/src/main/res/layout/item_cast.xml`:

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="110dp"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:layout_marginEnd="12dp">

    <ImageView
        android:id="@+id/cast_photo"
        android:layout_width="110dp"
        android:layout_height="150dp"
        android:scaleType="centerCrop"
        android:background="#22FFFFFF"
        android:contentDescription="@null" />

    <TextView
        android:id="@+id/cast_name"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingTop="6dp"
        android:textColor="#FFFFFF"
        android:textSize="13sp"
        android:maxLines="1"
        android:ellipsize="end" />

    <TextView
        android:id="@+id/cast_role"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textColor="#99FFFFFF"
        android:textSize="12sp"
        android:maxLines="1"
        android:ellipsize="end" />
</LinearLayout>
```

- [ ] **Step 4: Detail layout** `app/src/main/res/layout/activity_detail.xml` — structure copied from Jellyfin's `view_row_details.xml` (title over info row over genres; overview center; buttons under overview; poster right; cast bottom), simplified to plain ConstraintLayout:

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#101010">

    <ImageView
        android:id="@+id/backdrop"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="centerCrop"
        android:alpha="0.55"
        android:contentDescription="@null" />

    <View
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@drawable/detail_scrim" />

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="48dp">

        <TextView
            android:id="@+id/title"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:fontFamily="sans-serif-light"
            android:textColor="#FFFFFF"
            android:textSize="34sp"
            android:maxLines="2"
            android:ellipsize="end"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toStartOf="@id/poster"
            android:layout_marginEnd="32dp" />

        <LinearLayout
            android:id="@+id/info_row"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="10dp"
            android:orientation="horizontal"
            app:layout_constraintTop_toBottomOf="@id/title"
            app:layout_constraintStart_toStartOf="parent">

            <TextView
                android:id="@+id/badge_rating"
                style="@style/DetailBadge"
                android:textColor="#FFD54F" />

            <TextView
                android:id="@+id/badge_year"
                style="@style/DetailBadge" />

            <TextView
                android:id="@+id/badge_meta"
                style="@style/DetailBadge" />
        </LinearLayout>

        <TextView
            android:id="@+id/genres"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:textColor="#B3FFFFFF"
            android:textSize="14sp"
            app:layout_constraintTop_toBottomOf="@id/info_row"
            app:layout_constraintStart_toStartOf="parent" />

        <TextView
            android:id="@+id/overview"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="18dp"
            android:fontFamily="sans-serif-light"
            android:textColor="#E6FFFFFF"
            android:textSize="16sp"
            android:lineSpacingMultiplier="1.15"
            android:maxLines="6"
            android:ellipsize="end"
            app:layout_constraintTop_toBottomOf="@id/genres"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toStartOf="@id/poster"
            android:layout_marginEnd="32dp" />

        <LinearLayout
            android:id="@+id/button_row"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="22dp"
            android:orientation="horizontal"
            app:layout_constraintTop_toBottomOf="@id/overview"
            app:layout_constraintStart_toStartOf="parent">

            <Button
                android:id="@+id/btn_play"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/detail_play"
                android:layout_marginEnd="12dp" />

            <Button
                android:id="@+id/btn_resume"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/detail_resume"
                android:visibility="gone" />
        </LinearLayout>

        <ImageView
            android:id="@+id/poster"
            android:layout_width="220dp"
            android:layout_height="330dp"
            android:scaleType="centerCrop"
            android:background="#22FFFFFF"
            android:contentDescription="@null"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />

        <TextView
            android:id="@+id/cast_label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/detail_cast"
            android:textColor="#FFFFFF"
            android:textSize="18sp"
            android:layout_marginTop="28dp"
            app:layout_constraintTop_toBottomOf="@id/button_row"
            app:layout_constraintStart_toStartOf="parent" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/cast_row"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:clipToPadding="false"
            app:layout_constraintTop_toBottomOf="@id/cast_label"
            app:layout_constraintStart_toStartOf="parent"
            app:layout_constraintEnd_toEndOf="parent" />
    </androidx.constraintlayout.widget.ConstraintLayout>
</FrameLayout>
```

Add to `app/src/main/res/values/strings.xml`:
```xml
    <string name="detail_play">▶  Play</string>
    <string name="detail_resume">Resume</string>
    <string name="detail_cast">Cast &amp; Crew</string>
```
Add to `app/src/main/res/values/styles.xml`:
```xml
    <style name="DetailBadge">
        <item name="android:layout_width">wrap_content</item>
        <item name="android:layout_height">wrap_content</item>
        <item name="android:background">@drawable/badge_bg</item>
        <item name="android:textColor">#E6FFFFFF</item>
        <item name="android:textSize">13sp</item>
        <item name="android:layout_marginEnd">8dp</item>
    </style>
```
**Dependency check:** the layout uses ConstraintLayout. If `androidx.constraintlayout` isn't already a dependency (grep `app/build.gradle.kts`), add to the catalog `constraintlayout = { module = "androidx.constraintlayout:constraintlayout", version = "2.1.4" }` and `implementation(libs.constraintlayout)`.

- [ ] **Step 5: CastAdapter** `app/src/main/java/com/megaflix/tv/ui/CastAdapter.kt`:

```kotlin
package com.megaflix.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.megaflix.tv.R
import com.megaflix.tv.data.TmdbCastMember
import com.megaflix.tv.data.TmdbClient

class CastAdapter : RecyclerView.Adapter<CastAdapter.VH>() {

    private var items: List<TmdbCastMember> = emptyList()

    fun submit(list: List<TmdbCastMember>) {
        items = list
        notifyDataSetChanged() // small fixed list; DiffUtil is overkill here
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val photo: ImageView = view.findViewById(R.id.cast_photo)
        val name: TextView = view.findViewById(R.id.cast_name)
        val role: TextView = view.findViewById(R.id.cast_role)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_cast, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]
        holder.name.text = c.name
        holder.role.text = c.role ?: ""
        val url = TmdbClient.profileUrl(c.profilePath)
        if (url != null) holder.photo.load(url) { crossfade(true) }
        else holder.photo.setImageDrawable(null)
    }
}
```

- [ ] **Step 6: DetailActivity** `app/src/main/java/com/megaflix/tv/ui/DetailActivity.kt`:

```kotlin
package com.megaflix.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.megaflix.tv.App
import com.megaflix.tv.R
import com.megaflix.tv.data.TmdbClient
import com.megaflix.tv.data.VideoEntity
import kotlinx.coroutines.launch

/**
 * Jellyfin-style detail screen: full-bleed backdrop under a scrim, title +
 * badges + overview on the left, poster on the right, cast row at the bottom.
 */
class DetailActivity : AppCompatActivity() {

    private val repo by lazy { (application as App).graph.repository }
    private val castAdapter = CastAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val castRow = findViewById<RecyclerView>(R.id.cast_row)
        castRow.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        castRow.adapter = castAdapter

        val id = intent.getLongExtra(EXTRA_ID, -1)
        lifecycleScope.launch {
            val video = repo.byId(id) ?: run { finish(); return@launch }
            bind(video)
        }
    }

    private fun bind(v: VideoEntity) {
        findViewById<ImageView>(R.id.backdrop).load(TmdbClient.backdropUrl(v.backdropPath))
        findViewById<ImageView>(R.id.poster).load(TmdbClient.posterUrl(v.posterPath))
        findViewById<TextView>(R.id.title).text = v.title
        findViewById<TextView>(R.id.genres).text = v.genres ?: ""
        findViewById<TextView>(R.id.overview).text = v.overview ?: ""

        findViewById<TextView>(R.id.badge_rating).apply {
            visibility = if (v.rating != null) View.VISIBLE else View.GONE
            text = v.rating?.let { "★ %.1f".format(it) }
        }
        findViewById<TextView>(R.id.badge_year).apply {
            visibility = if (v.year != null) View.VISIBLE else View.GONE
            text = v.year?.toString()
        }
        findViewById<TextView>(R.id.badge_meta).apply {
            val meta = when {
                v.season != null && v.episode != null -> "S%02dE%02d".format(v.season, v.episode)
                v.runtimeMin != null -> "${v.runtimeMin} min"
                else -> null
            }
            visibility = if (meta != null) View.VISIBLE else View.GONE
            text = meta
        }

        val play = findViewById<Button>(R.id.btn_play)
        val resume = findViewById<Button>(R.id.btn_resume)
        play.setOnClickListener { startPlayer(v, fromStart = true) }
        if (v.positionMs > 0) {
            resume.visibility = View.VISIBLE
            resume.text = getString(R.string.detail_resume)
            resume.setOnClickListener { startPlayer(v, fromStart = false) }
            resume.requestFocus()
        } else {
            play.requestFocus()
        }

        val tmdbId = v.tmdbId
        if (tmdbId != null) lifecycleScope.launch {
            castAdapter.submit(TmdbClient.credits(tmdbId, isTv = v.season != null))
        }
    }

    private fun startPlayer(v: VideoEntity, fromStart: Boolean) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URI, v.uri)
                .putExtra(EXTRA_FROM_START, fromStart)
        )
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_FROM_START = "extra_from_start"
    }
}
```
**Note:** `EXTRA_FROM_START` is best-effort — read `PlayerActivity` first: if it already resumes from `positionMs` automatically, honor the flag by seeking to 0 when `fromStart` is true and a saved position exists (small edit inside `PlayerActivity` where it seeks; keep it minimal). If wiring this cleanly needs more than ~10 lines in PlayerActivity, skip the flag entirely (both buttons just start playback) and note it in the commit message.

- [ ] **Step 7: Manifest + navigation.** Register in `AndroidManifest.xml` inside `<application>`:

```xml
        <activity
            android:name=".ui.DetailActivity"
            android:exported="false" />
```
In `LibraryActivity.onCreate`, change the `RowsAdapter { video -> ... }` click lambda to:

```kotlin
        rowsAdapter = RowsAdapter { video ->
            startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_ID, video.id)
            )
        }
```

- [ ] **Step 8: Verify build:** `./gradlew assembleDebug -q` → BUILD SUCCESSFUL.

- [ ] **Step 9: Commit** — `feat: Jellyfin-style detail screen (backdrop, badges, overview, cast)`

---

### Task 8: Emulator verification (visual gate — do not skip)

**Files:** none (verification only). Screenshots go to the session scratchpad directory.

- [ ] **Step 1: Boot emulator** (skip if `adb devices` already shows one):

```bash
nohup emulator -avd megaflix_tv -no-snapshot -gpu swiftshader_indirect > /dev/null 2>&1 &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 3; done
```

- [ ] **Step 2: Confirm DEMO_MODE is on for debug builds** — grep `DEMO_MODE` in `app/build.gradle.kts`; it must be `true` for the debug variant (it normally is). Install: `./gradlew installDebug -q`.

- [ ] **Step 3: Launch + let enrichment run** (TMDB fetch needs ~5–15s on first run):

```bash
adb shell am start -n com.megaflix.tv/.ui.LibraryActivity   # adjust if launcher activity name differs — check AndroidManifest
sleep 20
adb exec-out screencap -p > <scratchpad>/verify_home.png
```
**Read the screenshot.** PASS = poster cards show real movie art (Matrix, Inception, etc.), not gray placeholders with text. If posters are missing: `adb logcat -d | grep -iE "tmdb|coil"` and fix before continuing (common causes: cleartext/HTTPS is fine — TMDB is HTTPS; key typo; `tmdbChecked` marked true while offline → clear app data `adb shell pm clear com.megaflix.tv` and relaunch).

- [ ] **Step 4: Detail screen.** D-pad to first card and open it:

```bash
adb shell input keyevent KEYCODE_DPAD_CENTER
sleep 6
adb exec-out screencap -p > <scratchpad>/verify_detail.png
```
**Read the screenshot.** PASS = backdrop visible behind scrim, title, ★ rating badge, overview text, Play button focused, poster on right, cast photos at bottom.

- [ ] **Step 5: Playback.** Press center on Play (`adb shell input keyevent KEYCODE_DPAD_CENTER`), sleep 4, screenshot → the bundled clip should be playing (PlayerActivity). Press BACK twice to return home.

- [ ] **Step 6: Commit any fixes** made during verification — `fix: <what verification caught>`

---

### Task 9: Package signed demo APK and publish to GitHub Pages

**Files:**
- Modify: `docs/megaflix.apk` (binary, replaced)

**Reference:** the release-unsigned APK won't install; it must be zipaligned + signed with the debug keystore (established 2026-09-16).

- [ ] **Step 1: Build release:**

```bash
./gradlew assembleRelease -q
ls -la app/build/outputs/apk/release/   # expect app-release-unsigned.apk
```
(If the release variant fails on DEMO_MODE or minify config, read the error — release config was known-good on 2026-09-16; do not change minify settings without need.)

- [ ] **Step 2: zipalign + sign with the debug keystore:**

```bash
BT=$ANDROID_HOME/build-tools/35.0.0
$BT/zipalign -f 4 app/build/outputs/apk/release/app-release-unsigned.apk /tmp/megaflix-aligned.apk
$BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android \
  --key-pass pass:android --out docs/megaflix.apk /tmp/megaflix-aligned.apk
$BT/apksigner verify docs/megaflix.apk && echo SIGNED_OK
```

- [ ] **Step 3: Sanity-install the signed APK on the emulator:**

```bash
adb uninstall com.megaflix.tv || true
adb install docs/megaflix.apk
adb shell am start -n com.megaflix.tv/.ui.LibraryActivity
sleep 20
adb exec-out screencap -p > <scratchpad>/verify_release.png
```
Read the screenshot — same PASS bar as Task 8 Step 3.

- [ ] **Step 4: Push everything:**

```bash
git add -A
git commit -m "feat: Jellyfin-style UI demo build — TMDB posters + detail screen"
git push origin main
```
(Attribution lines per the session's commit reminder. Pages rebuilds automatically from `/docs`; no further action.)

- [ ] **Step 5: Confirm the download is live** (Pages can take 1–3 min):

```bash
sleep 90
curl -sI https://t31k.github.io/megaflix/megaflix.apk | head -3   # expect HTTP 200
curl -s https://t31k.github.io/megaflix/megaflix.apk -o /tmp/pagecheck.apk && shasum /tmp/pagecheck.apk docs/megaflix.apk
```
PASS = 200 and matching checksums. If still the old file after ~5 min, check `gh api repos/T31K/megaflix/pages/builds/latest`.

---

## Self-Review Notes

- **Spec coverage:** home posters (T1–T6), detail screen (T7), emulator visual gate (T8), APK on Pages (T9). Resume-from-position exists in entity (`positionMs`) and is surfaced via the Resume button; deep PlayerActivity seek wiring is explicitly allowed to be skipped (noted in T7 Step 6) — the core ask is visual.
- **Type consistency:** `TmdbMatch`/`TmdbCastMember`/field names checked across T2/T3/T4/T5/T7; `EXTRA_ID` consumed where produced.
- **Known risks for the executor:** (a) exact property/launcher names (`dao`, `graph.repository`, launcher activity) must be confirmed by reading the named files — flagged inline where relevant; (b) demo `positionMs` will be 0 for fresh installs so Resume stays hidden — fine; (c) TMDB fetch requires emulator network — if enrichment silently no-ops, clear app data and re-run (T8 Step 3).
