package cn.levanll.inventory.domain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检查。
 *
 * 数据源是 GitHub Releases API，零后端、零维护，版本号与 Release 天然同源，
 * 不会出现「说有新版但没得下」。
 *
 * 纪律（与 BarcodeLookupService 一致）：
 *   - 任何异常静默返回 null，绝不弹错误。断网、限流、DNS 失败都只是「没查到」
 *   - 只在用户允许时才发请求；拒绝就一次都不发
 *   - 固定 User-Agent，不带机型、不带系统版本，尽量少暴露信息
 *   - 24 小时闸：一天最多查一次，避免频繁开关应用产生大量请求
 *
 * 隐私：请求是 `GET /releases/latest`，不带任何本地数据。
 * 但 IP、请求时间对 GitHub 可见，这一点在 README 隐私说明里如实写了。
 */
class UpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "UpdateChecker"

        private const val ENDPOINT =
            "https://api.github.com/repos/Levanll/InventoryLedger/releases/latest"

        private const val TIMEOUT = 6000

        /** 24 小时闸：距上次检查不足这个间隔就不再请求 */
        private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        private const val PREF = "update_check"
        private const val KEY_ENABLED = "enabled"          // 三态：null=未询问, true=允许, false=拒绝
        private const val KEY_LAST_CHECK = "last_check_at"

        /** Release 描述里能显示的最大字符数，弹窗放不下更多 */
        private const val NOTES_MAX = 160
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /**
     * 用户是否已表态。
     * null 表示首次启动还没问过 —— 调用方据此弹一次询问框。
     */
    fun consentState(): Boolean? =
        if (prefs.contains(KEY_ENABLED)) prefs.getBoolean(KEY_ENABLED, false) else null

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /**
     * 检查是否有新版本。
     *
     * @param force 用户手动点「立即检查」时传 true，跳过 24 小时闸
     * @return 有新版本时返回 [UpdateInfo]；已是最新、被关闭、被闸门拦截、或任何失败都返回 null
     */
    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext null

        val now = System.currentTimeMillis()
        if (!force) {
            val last = prefs.getLong(KEY_LAST_CHECK, 0L)
            if (now - last < CHECK_INTERVAL_MS) return@withContext null
        }

        // 无论成功失败都要记时间，否则断网时会每次启动都重试
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        try {
            fetchLatest()
        } catch (e: Exception) {
            Log.d(TAG, "check failed: ${e.message}")
            null
        }
    }

    private fun fetchLatest(): UpdateInfo? {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("User-Agent", "InventoryLedger/${appVersionName()} (Android)")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            // 404 意味着仓库不存在或无 Release；403 是限流。都当作「查不到」
            if (conn.responseCode != 200) return null

            val text = BufferedReader(conn.inputStream.reader()).use { it.readText() }
            val root = JSONObject(text)

            val remoteVersion = root.optString("tag_name", "").trim()
            if (remoteVersion.isEmpty()) return null
            if (!isNewer(remoteVersion, appVersionName())) return null

            val apk = root.optJSONArray("assets")
                ?.let { arr ->
                    (0 until arr.length())
                        .map { arr.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                } ?: return null

            val url = apk.optString("browser_download_url", "").trim()
            if (url.isEmpty()) return null

            return UpdateInfo(
                version = remoteVersion.trimStart('v'),
                downloadUrl = url,
                sizeBytes = apk.optLong("size", 0L),
                notes = firstParagraph(root.optString("body", ""))
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun appVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** 取 Release 描述的第一段有意义文字，去掉 Markdown 标题与空行 */
    private fun firstParagraph(body: String): String {
        val line = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("---") }
            .firstOrNull() ?: ""
        val clean = line.replace(Regex("\\*\\*"), "").replace(Regex("`"), "")
        return if (clean.length > NOTES_MAX) clean.take(NOTES_MAX).trimEnd() + "…" else clean
    }
}

/**
 * 检测到的新版本信息。
 *
 * [downloadUrl] 取自 API 返回的 assets，不要自己按版本号拼 ——
 * 文件名带版本号，拼错就 404。
 */
data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val notes: String
)

/**
 * 语义化版本比较。
 *
 * 不能用字符串比较：「1.10.0」按字典序小于「1.9.0」，会漏掉更新。
 * 逐段比数字，缺段补 0，「1.3」与「1.3.0」视为相等。
 *
 * 解析不出数字的段一律当 0，宁可漏报也不误报 ——
 * 误报会让用户看到一个装不上的「新版本」。
 */
fun isNewer(remote: String, local: String): Boolean {
    val r = remote.trim().trimStart('v').split('.')
    val l = local.trim().trimStart('v').split('.')
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        val b = l.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        if (a != b) return a > b
    }
    return false
}
