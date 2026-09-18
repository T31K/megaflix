package com.megaflix.tv.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String?,
)

/**
 * Self-update for the sideloaded APK. Reads a small version.json off GitHub
 * Pages, compares to the installed versionCode, and downloads the new APK.
 * The actual install is user-confirmed (Android requirement) — see
 * [UpdateInstaller]. All network is best-effort: offline just means no prompt.
 */
object UpdateChecker {

    private const val VERSION_URL = "https://t31k.github.io/megaflix/version.json"

    /** Returns update info only when the published versionCode is newer. */
    suspend fun check(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = httpGet(VERSION_URL) ?: return@withContext null
        val info = runCatching { parse(json) }.getOrNull() ?: return@withContext null
        if (info.versionCode > currentVersionCode) info else null
    }

    fun parse(json: String): UpdateInfo {
        val o = JSONObject(json)
        return UpdateInfo(
            versionCode = o.getInt("versionCode"),
            versionName = o.optString("versionName").ifEmpty { "?" },
            apkUrl = o.getString("apkUrl"),
            notes = o.optString("notes").ifEmpty { null },
        )
    }

    /** Downloads the APK to cacheDir/updates/; returns the file, or null on failure. */
    suspend fun download(context: Context, info: UpdateInfo): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "megaflix-${info.versionCode}.apk")
            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            try {
                if (conn.responseCode != 200) return@runCatching null
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            out
        }.getOrNull()
    }

    private fun httpGet(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().readText() else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
