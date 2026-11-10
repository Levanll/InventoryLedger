package cn.levanll.inventory.domain

import android.util.Log
import androidx.room.withTransaction
import cn.levanll.inventory.data.db.AppDatabase
import cn.levanll.inventory.data.entity.BarcodeMemoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 条码商品查询。
 *
 * 三级数据源，按可靠性从高到低：
 *   1. 本地自学习库（barcode_memo）—— 用户自己填过或采用过的，最准
 *   2. 已入库商品（item.barcode）—— 之前录过的商品
 *   3. 联网：
 *      a. Open Food Facts（有 JSON API，中文名友好，覆盖欧美食品）
 *      b. go-upc（HTML 抓取，对国产 69 码覆盖明显更好）
 *
 * 设计纪律：
 *   - 只返回「建议」，绝不自动写入表单
 *   - 本地库优先于联网：用户亲手确认过的数据永远比第三方可靠
 *   - 任何网络异常都静默返回 null —— 离线时应用必须照常可用
 */
class BarcodeLookupService(private val db: AppDatabase) {

    companion object {
        private const val TAG = "BarcodeLookup"
        private const val TIMEOUT = 6000

        private const val OFF_ENDPOINT =
            "https://world.openfoodfacts.org/api/v2/product/%s.json" +
                "?fields=product_name,product_name_zh,brands,quantity,categories_tags"

        private const val GOUPC_ENDPOINT = "https://go-upc.com/search?q=%s"

        /** 常见浏览器 UA。go-upc 对空 UA 会返回 403。 */
        private const val UA =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }

    /** 数据来源，UI 可据此提示用户可信度 */
    enum class Source { LOCAL, ITEM, NET_OFF, NET_GOUPC }

    data class ProductInfo(
        val barcode: String,
        val name: String,
        val brand: String?,
        val spec: String?,
        val categoryHint: String?,
        val categoryId: Long? = null,
        val source: Source = Source.NET_OFF
    )

    /**
     * 负缓存哨兵。
     * ConcurrentHashMap 不允许 null value，直接把「查不到」存成 null 会抛 NPE。
     * 用一个固定实例代表「确实查过、确实没有」，与「还没查过」区分开。
     */
    private object NotFound

    /** 键 = 条码，值 = ProductInfo 或 NotFound */
    private val cache = ConcurrentHashMap<String, Any>()

    private val barcodeDao get() = db.barcodeDao()

    /**
     * 查询条码。顺序：本地自学习 → 已入库商品 → 联网。
     */
    suspend fun lookup(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        val code = barcode.trim()
        if (code.isEmpty()) return@withContext null

        // 1) 内存缓存
        when (val hit = cache[code]) {
            null -> Unit
            NotFound -> return@withContext null
            is ProductInfo -> return@withContext hit
        }

        // 2) 本地自学习库（最高优先）
        val memo = barcodeDao.get(code)
        if (memo != null) {
            // 命中计数是写操作，走事务（项目约定：所有写操作经 withTransaction）
            db.withTransaction { barcodeDao.bumpHit(code, System.currentTimeMillis()) }
            val info = ProductInfo(
                barcode = code,
                name = memo.name,
                brand = memo.brand,
                spec = memo.spec,
                categoryHint = null,
                categoryId = memo.categoryId,
                source = Source.LOCAL
            )
            cache[code] = info
            return@withContext info
        }

        // 3) 已入库商品（之前录过的条码）
        val item = db.itemDao().findByBarcode(code)
        if (item != null) {
            val path = item.categoryId?.let { catPath(it) }
            val info = ProductInfo(
                barcode = code,
                name = item.name,
                brand = null,
                spec = item.spec.ifEmpty { null },
                categoryHint = path,
                categoryId = item.categoryId,
                source = Source.ITEM
            )
            cache[code] = info
            return@withContext info
        }

        // 4) 联网：先 OFF，再 go-upc
        val net = try {
            fetchOff(code) ?: fetchGoUpc(code)
        } catch (e: Exception) {
            Log.d(TAG, "lookup $code failed: ${e.message}")
            null
        }
        cache[code] = net ?: NotFound
        net
    }

    /** 用户采用建议或手填保存后，记住这个条码的对应信息 */
    suspend fun remember(
        barcode: String,
        name: String,
        brand: String?,
        spec: String?,
        categoryId: Long?,
        source: String
    ) = withContext(Dispatchers.IO) {
        val code = barcode.trim()
        if (code.isEmpty() || name.isBlank()) return@withContext
        val now = System.currentTimeMillis()

        // 读-判断-写必须原子：否则并发扫码时两个请求都判定「不存在」，
        // 后一个 insert 会撞 barcode 唯一索引抛异常。
        db.withTransaction {
            if (barcodeDao.get(code) == null) {
                barcodeDao.insert(
                    BarcodeMemoEntity(
                        barcode = code,
                        name = name.trim(),
                        brand = brand?.trim()?.ifEmpty { null },
                        spec = spec?.trim()?.ifEmpty { null },
                        categoryId = categoryId,
                        hitCount = 1,
                        source = source,
                        updatedAt = now
                    )
                )
            } else {
                barcodeDao.update(
                    barcode = code,
                    name = name.trim(),
                    brand = brand?.trim()?.ifEmpty { null },
                    spec = spec?.trim()?.ifEmpty { null },
                    categoryId = categoryId,
                    source = source,
                    now = now
                )
            }
        }
        cache.remove(code)
    }

    fun clearMemoryCache() {
        cache.clear()
    }

    // ==========================================================
    // 数据源 1：Open Food Facts
    // ==========================================================

    private fun fetchOff(code: String): ProductInfo? {
        val url = URL(String.format(OFF_ENDPOINT, URLEncoder.encode(code, "UTF-8")))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) return null
            val text = BufferedReader(conn.inputStream.reader()).use { it.readText() }
            val root = JSONObject(text)
            if (root.optInt("status", 0) != 1) return null
            val p = root.optJSONObject("product") ?: return null

            val nameZh = p.optString("product_name_zh", "").trim()
            val name = nameZh.ifEmpty { p.optString("product_name", "").trim() }
            if (name.isEmpty()) return null

            val brand = p.optString("brands", "").trim().ifEmpty { null }
            val quantity = p.optString("quantity", "").trim().ifEmpty { null }

            return ProductInfo(
                barcode = code,
                name = name,
                brand = brand,
                spec = quantity,
                categoryHint = guessCategoryFromTags(p.optJSONArray("categories_tags")),
                source = Source.NET_OFF
            )
        } finally {
            conn.disconnect()
        }
    }

    // ==========================================================
    // 数据源 2：go-upc（HTML 抓取）
    // ==========================================================

    /**
     * go-upc 对国产 69 码的覆盖明显好于 Open Food Facts。
     *
     * 页面结构（2026-11 实测）：
     *   <h1 class="product-name">商品名</h1>
     *   <tr><td>Brand</td><td>品牌</td></tr>
     *   <tr><td>Category</td><td>类目</td></tr>
     *
     * 抓不到就返回 null，绝不猜测。这个源是逆向解析，站点改版会失效，
     * 但失效时只是「查不到」，不影响应用其他功能。
     */
    private fun fetchGoUpc(code: String): ProductInfo? {
        val url = URL(String.format(GOUPC_ENDPOINT, URLEncoder.encode(code, "UTF-8")))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "text/html")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        }
        try {
            if (conn.responseCode != 200) return null
            val html = BufferedReader(conn.inputStream.reader()).use { it.readText() }

            // 明确标记找不到时直接返回，避免把 "Product Not Found" 当成商品名
            if (html.contains("Product Not Found", ignoreCase = true)) return null

            val name = extractFirst(html, """<h1[^>]*class="[^"]*product-name[^"]*"[^>]*>\s*([^<]+?)\s*</h1>""")
                ?: extractFirst(html, """<h1[^>]*>\s*([^<]+?)\s*</h1>""")
                ?: return null

            val decodedName = cleanHtml(name)
            if (decodedName.isEmpty() || decodedName.equals("Product Not Found", ignoreCase = true)) {
                return null
            }

            val brand = cleanHtml(extractTableValue(html, "Brand") ?: "").ifEmpty { null }
            val category = cleanHtml(extractTableValue(html, "Category") ?: "").ifEmpty { null }

            return ProductInfo(
                barcode = code,
                name = decodedName,
                brand = brand,
                spec = null,
                categoryHint = mapGoUpcCategory(category),
                source = Source.NET_GOUPC
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun extractFirst(html: String, pattern: String): String? {
        val m = Pattern.compile(pattern, Pattern.DOTALL).matcher(html)
        return if (m.find()) m.group(1) else null
    }

    /** 从 <tr><td>键</td><td>值</td></tr> 里取值 */
    private fun extractTableValue(html: String, key: String): String? {
        val p = """<tr[^>]*>\s*<td[^>]*>\s*$key\s*</td>\s*<td[^>]*>\s*(.*?)\s*</td>"""
        val m = Pattern.compile(p, Pattern.DOTALL or Pattern.CASE_INSENSITIVE).matcher(html)
        return if (m.find()) m.group(1) else null
    }

    /**
     * HTML 实体解码 + 乱码修复。
     *
     * go-upc 返回的中文可能是 UTF-8 字节按 latin-1 解释后的乱码
     * （如「帮扶专区」变成「甯壎涓撳尯」），用「按 ISO-8859-1 取字节
     * 再按 UTF-8 解」可以还原。
     */
    private fun cleanHtml(s: String): String {
        var t = s.trim()
        t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        if (t.any { it.code in 0x80..0xFF }) {
            try {
                val fixed = String(t.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                if (!fixed.contains('\uFFFD')) t = fixed
            } catch (_: Exception) {
            }
        }
        t = t.replace(Regex("\\s+"), " ").trim()
        t = Normalizer.normalize(t, Normalizer.Form.NFC)
        return t
    }

    /** OFF 的英文类目标签 → 本应用分类路径提示 */
    private fun guessCategoryFromTags(tags: org.json.JSONArray?): String? {
        if (tags == null) return null
        val sb = StringBuilder()
        for (i in 0 until tags.length()) sb.append(tags.optString(i, "")).append(' ')
        val s = sb.toString().lowercase()
        return when {
            s.contains("beverage") || s.contains("drink") || s.contains("water") ||
                s.contains("juice") || s.contains("soda") -> "食品 / 饮料"
            s.contains("dairy") || s.contains("milk") || s.contains("yogurt") -> "食品 / 生鲜 / 乳制品"
            s.contains("snack") || s.contains("biscuit") || s.contains("chocolate") ||
                s.contains("candy") -> "食品 / 零食"
            s.contains("cereal") || s.contains("noodle") || s.contains("rice") -> "食品 / 主食"
            s.contains("sauce") || s.contains("oil") || s.contains("vinegar") -> "食品 / 调味"
            s.contains("cosmetic") || s.contains("skincare") || s.contains("shampoo") ||
                s.contains("soap") || s.contains("beauty") -> "美妆个护"
            s.contains("cleaning") || s.contains("detergent") || s.contains("paper") -> "日用家居"
            s.contains("medicine") || s.contains("supplement") || s.contains("vitamin") -> "药品保健"
            else -> null
        }
    }

    /** go-upc 的英文类目 → 本应用分类路径提示 */
    private fun mapGoUpcCategory(cat: String?): String? {
        if (cat.isNullOrBlank()) return null
        val s = cat.lowercase()
        return when {
            s.contains("food") || s.contains("grocery") || s.contains("beverage") ||
                s.contains("drink") -> "食品"
            s.contains("beauty") || s.contains("personal care") || s.contains("cosmetic") -> "美妆个护"
            s.contains("health") || s.contains("medicine") -> "药品保健"
            s.contains("clothing") || s.contains("apparel") || s.contains("fashion") -> "服装"
            s.contains("electronic") || s.contains("computer") || s.contains("software") -> "数码配件"
            s.contains("home") || s.contains("household") || s.contains("cleaning") -> "日用家居"
            else -> null
        }
    }

    /** 分类 id → 完整路径（用于 ITEM 源的提示） */
    private suspend fun catPath(id: Long): String? {
        val chain = ArrayList<String>(3)
        var cur = db.categoryDao().getById(id) ?: return null
        chain.add(cur.name)
        var guard = 0
        while (cur.parentId != null && guard < 5) {
            cur = db.categoryDao().getById(cur.parentId!!) ?: break
            chain.add(cur.name)
            guard++
        }
        return chain.reversed().joinToString(" / ")
    }
}
