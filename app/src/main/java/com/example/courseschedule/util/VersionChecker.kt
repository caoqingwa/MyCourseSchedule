package com.example.courseschedule.util

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 从 GitHub Releases 查询最新版本号 */
object VersionChecker {
    private const val RELEASES_API = "https://api.github.com/repos/caoqingwa/MyCourseSchedule/releases/latest"

    /**
     * 查询最新版本号（如 "v2.7"）。网络失败返回 null。
     * 必须在后台线程调用。
     */
    fun fetchLatestVersion(): String? {
        return try {
            val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "CourseSchedule-App")
            try {
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body).optString("tag_name", "").takeIf { it.isNotBlank() }
                } else null
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** "v2.7" vs "2.7" → true；版本号解析失败返回 false */
    fun isNewer(latest: String, current: String): Boolean {
        fun parse(v: String): List<Int> =
            v.trim().trimStart('v', 'V').split('.')
                .mapNotNull { it.toIntOrNull() }
        val l = parse(latest)
        val c = parse(current)
        if (l.isEmpty() || c.isEmpty()) return false
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
