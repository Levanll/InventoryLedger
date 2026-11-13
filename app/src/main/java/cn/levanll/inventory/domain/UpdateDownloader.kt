package cn.levanll.inventory.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 更新包下载、校验与安装。
 *
 * 文件位置：`cacheDir/updates/`。
 * 用 cacheDir 而不是 filesDir —— cacheDir 在系统存储紧张时会被自动回收，
 * 是「可以随时丢」的语义。写进 filesDir 就会永久占用用户空间。
 *
 * 清理策略是「下次启动时删 24 小时前的」而不是「安装完立刻删」：
 * 安装 Intent 是异步的，startActivity 之后本进程可能立刻被切到后台，
 * 此时删文件会和系统安装器抢读，导致「解析包时出现问题」。
 */
class UpdateDownloader(private val context: Context) {

    companion object {
        private const val TAG = "UpdateDownloader"

        private const val TIMEOUT = 15000
        private const val DIR = "updates"
        private const val APK = "update.apk"
        private const val TMP = "update.apk.tmp"

        /** 保留 24 小时，给同一会话内的重试留余地 */
        private const val STALE_MS = 24L * 60 * 60 * 1000

        /** 下载前要求的最小剩余空间倍数：APK 大小 × 这个系数 */
        private const val SPACE_FACTOR = 2
    }

    private fun dir(): File = File(context.cacheDir, DIR).apply { if (!exists()) mkdirs() }

    fun apkFile(): File = File(dir(), APK)

    /**
     * 下载 APK。
     *
     * 先写 .tmp 再改名：否则下载中断留下的半截文件会被 listFiles() 扫到，
     * 可能被误判成「已下载完成」。
     *
     * @param onProgress (百分比 0-100, 已下载字节, 总字节)
     */
    suspend fun download(
        url: String,
        expectedSize: Long,
        onProgress: (Int, Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val target = apkFile()
        val tmp = File(dir(), TMP)
        tmp.delete()   // 清掉上次中断的残留

        if (!hasSpace(expectedSize)) {
            throw AppException(
                ErrCode.INVALID_ARG,
                "存储空间不足，无法下载更新",
                en = "Not enough storage space to download the update"
            )
        }

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("User-Agent", "InventoryLedger (Android)")
            instanceFollowRedirects = true
        }

        try {
            if (conn.responseCode != 200) {
                throw AppException(
                    ErrCode.INVALID_ARG,
                    "下载失败（HTTP ${conn.responseCode}）",
                    en = "Download failed (HTTP ${conn.responseCode})"
                )
            }

            val total = if (expectedSize > 0) expectedSize else conn.contentLengthLong
            var done = 0L

            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        // 取消时立刻中断，不留半截文件
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            onProgress(((done * 100) / total).toInt(), done, total)
                        }
                    }
                }
            }

            // 大小校验：防止下到一半断流却返回 200
            if (total > 0 && done != total) {
                tmp.delete()
                throw AppException(
                    ErrCode.INVALID_ARG,
                    "下载不完整（${done}/${total} 字节），请重试",
                    en = "Download incomplete (${done}/${total} bytes), please retry"
                )
            }

            target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw AppException(
                    ErrCode.INVALID_ARG,
                    "无法保存安装包",
                    en = "Could not save the package"
                )
            }
            target
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 校验 APK 签名与本应用一致。
     *
     * 系统安装器本来就会校验签名，不符会拒绝安装。这里提前查一次，
     * 是为了在下载完成时就能告诉用户「文件损坏」，
     * 而不是让用户走到安装器才看到看不懂的系统报错。
     */
    fun verifySignature(apk: File): Boolean {
        return try {
            val pm = context.packageManager

            @Suppress("DEPRECATION")
            val self = pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
                .signatures ?: return false

            @Suppress("DEPRECATION")
            val archive = pm.getPackageArchiveInfo(apk.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES)
                ?.signatures ?: return false

            self.isNotEmpty() && archive.isNotEmpty() &&
                self.first().toCharsString() == archive.first().toCharsString()
        } catch (e: Exception) {
            Log.d(TAG, "verify failed: ${e.message}")
            false
        }
    }

    /** Android 8+ 是否已允许本应用安装 APK */
    fun canInstall(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** 跳到「安装未知应用」授权页。用户回来后需要重新点一次安装 */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.d(TAG, "open settings failed: ${e.message}")
        }
    }

    /** 唤起系统安装器 */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /**
     * 清理过期安装包。在 App 启动时调用。
     *
     * 只删 24 小时前的：用户点了安装但系统弹窗放着没操作、
     * 过几分钟又回来点的情况要能复用同一个文件。
     *
     * 不递归、不删目录，只动 updates/ 这一层，
     * 避免路径拼接出错删到别处。
     */
    fun cleanupStale() {
        try {
            val d = File(context.cacheDir, DIR)
            if (!d.exists() || !d.isDirectory) return
            val now = System.currentTimeMillis()
            d.listFiles()?.forEach { f ->
                if (f.isFile && now - f.lastModified() > STALE_MS) {
                    if (!f.delete()) Log.d(TAG, "could not delete ${f.name}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "cleanup failed: ${e.message}")
        }
    }

    /** 取消 / 失败后立即清掉半截文件 */
    fun discard() {
        File(dir(), TMP).delete()
    }

    private fun hasSpace(need: Long): Boolean {
        if (need <= 0) return true
        return try {
            dir().usableSpace > need * SPACE_FACTOR
        } catch (e: Exception) {
            true   // 查不出来就别拦着用户
        }
    }
}
