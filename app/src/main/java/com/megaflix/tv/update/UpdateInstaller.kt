package com.megaflix.tv.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Launches the system package installer for a downloaded APK. The install is
 * always user-confirmed; a sideloaded app can't install silently without being
 * device owner. On Android 8+ the app also needs "install unknown apps"
 * permission, so we route the user to that setting if it isn't granted yet.
 */
object UpdateInstaller {

    fun install(context: Context, apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            // First run: send the user to enable "install unknown apps" for us.
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
