package com.example.courseschedule.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** 新版本 APK 的下载与安装。失败场景返回 null/false 而非抛异常，由调用方提示。 */
object AppUpdater {
    private const val RELEASE_DL = "https://github.com/caoqingwa/MyCourseSchedule/releases/download"

    /** Release 资产下载地址。CI 固定把 APK 命名为 {tag}.apk，URL 可按 tag 直接拼出 */
    fun apkUrl(tag: String): String = "$RELEASE_DL/$tag/$tag.apk"

    /** 下载到应用私有目录 files/updates/{tag}.apk，成功返回文件，失败返回 null。须在后台线程调用 */
    fun downloadApk(context: Context, tag: String): File? {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val target = File(dir, "$tag.apk")
        return try {
            val conn = URL(apkUrl(tag)).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "CourseSchedule-App")
            try {
                if (conn.responseCode != 200) return null
                conn.inputStream.use { input ->
                    FileOutputStream(target).use { out -> input.copyTo(out) }
                }
                target
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Android 8+ 从非应用商店安装前需先授予"安装未知应用"权限 */
    fun canRequestInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** 跳转系统"允许安装未知应用"授权页 */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 通过 FileProvider 唤起系统安装器，唤起失败返回 false */
    fun launchInstaller(context: Context, file: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
