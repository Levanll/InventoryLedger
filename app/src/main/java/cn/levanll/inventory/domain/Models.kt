package cn.levanll.inventory.domain

/** 流水类型 */
object RecType {
    const val IN = "IN"
    const val OUT = "OUT"
    const val ADJUST = "ADJUST"
}

/** 统一业务错误码 */
object ErrCode {
    const val DUPLICATE_ITEM = "DUPLICATE_ITEM"
    const val INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK"
    const val ITEM_NOT_FOUND = "ITEM_NOT_FOUND"
    const val ALREADY_REVERTED = "ALREADY_REVERTED"
    const val CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND"
    const val CATEGORY_HAS_CHILDREN = "CATEGORY_HAS_CHILDREN"
    const val INVALID_ARG = "INVALID_ARG"
    const val DB_ERROR = "DB_ERROR"
}

/**
 * 业务异常。
 * payload 用 Map 承载上下文（如 existingId / available），UI 据此生成具体文案。
 */
class AppException(
    val code: String,
    override val message: String,
    val payload: Map<String, Any> = emptyMap()
) : Exception(message)

data class InboundInput(
    val name: String,
    val spec: String,
    val unit: String,
    val qty: Double,
    val categoryId: Long?,
    val barcode: String? = null,
    val imageUri: String? = null,
    val lowThreshold: Double = 0.0,
    val existingItemId: Long? = null,
    val allowCreateSpec: Boolean = false,
    // ---- 保质期 ----
    val expiryEnabled: Boolean = false,
    val productionDate: Long? = null,
    val shelfLifeDays: Int? = null
)

data class InboundResult(
    val itemId: Long,
    val quantityAfter: Double,
    val recordId: Long
)

data class ClassifyResult(
    /** 命中的分类 id；可能是一级/二级/三级 */
    val categoryId: Long?,
    /** 完整路径，如「美妆个护 / 护肤 / 精华」 */
    val categoryPath: String?,
    val confidence: Double,
    val needConfirm: Boolean
)

/** 月份标识 */
data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {

    fun startMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun endMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.add(java.util.Calendar.MONTH, 1)
        return cal.timeInMillis
    }

    fun prev(): YearMonth = if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)
    fun next(): YearMonth = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
    fun label(): String = if (cn.levanll.inventory.i18n.I18n.lang == cn.levanll.inventory.i18n.Lang.ZH) {
        "${year}年${month}月"
    } else {
        java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.ENGLISH)
            .format(java.util.Date(startMillis()))
    }

    override fun compareTo(other: YearMonth): Int =
        if (year != other.year) year - other.year else month - other.month

    companion object {
        fun now(): YearMonth {
            val cal = java.util.Calendar.getInstance()
            return YearMonth(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
        }

        fun fromCompact(v: Int): YearMonth = YearMonth(v / 100, v % 100)
    }
}

/** 月度汇总行 */
data class MonthItemRow(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val inQty: Double,
    val outQty: Double,
    val adjustQty: Double,
    val netDelta: Double,
    val endQty: Double,
    val recordCount: Int
)

data class DetailRow(
    val id: Long,
    val itemName: String,
    val unit: String,
    val type: String,
    val delta: Double,
    val beforeQty: Double,
    val afterQty: Double,
    val note: String?,
    val createdAt: Long,
    val revertedBy: Long?
)

data class MonthOverview(
    val inQty: Double = 0.0,
    val outQty: Double = 0.0,
    val inCount: Int = 0,
    val outCount: Int = 0,
    val activeItems: Int = 0
)

/**
 * 保质期状态。
 * 分档依据「剩余天数」，因为用户对「还剩 3 天」的反应远快于一个日期。
 */
enum class ExpiryStatus {
    NONE,
    EXPIRED,
    URGENT,
    SOON,
    NEAR,
    NORMAL;

    companion object {
        fun of(daysLeft: Int?): ExpiryStatus {
            if (daysLeft == null) return NONE
            return when {
                daysLeft < 0 -> EXPIRED
                daysLeft <= 3 -> URGENT
                daysLeft <= 7 -> SOON
                daysLeft <= 30 -> NEAR
                else -> NORMAL
            }
        }
    }
}

object Constants {
    const val CONF_AUTO_ACCEPT = 0.75
    const val CONF_NEED_CONFIRM = 0.40
    const val CONF_CEILING = 0.90
    const val CONF_AMBIGUOUS_GAP = 0.15

    const val SUGGEST_LIMIT = 5
    const val RECENT_ITEM_LIMIT = 3
    const val PAGE_SIZE = 100

    const val QTY_MIN = 0.0
    const val QTY_MAX = 999999.0

    /** 临期提醒阈值（天）：首页提示条用 */
    const val EXPIRING_SOON_DAYS = 7

    val UNITS = listOf("件", "个", "瓶", "包", "盒", "袋", "支", "克", "毫升")

    fun stepOfUnit(unit: String): Double = when (unit) {
        "克", "毫升" -> 10.0
        else -> 1.0
    }

    const val MEMO_LAST_CATEGORY = "last_category_id"
    const val MEMO_LAST_UNIT = "last_unit"
    const val MEMO_THRESHOLD = "default_low_threshold"
    const val MEMO_EXPIRY_ENABLED = "last_expiry_enabled"
}

fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

/** 当天 00:00:00.000 的时间戳 */
fun startOfDay(ms: Long): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = ms
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** 从「已选天数」反推生产日期 */
fun productionDateFromDaysLeft(daysLeft: Int): Long {
    val today = startOfDay(System.currentTimeMillis())
    return today + daysLeft.toLong() * 86400000L
}
