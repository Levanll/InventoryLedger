package cn.levanll.inventory.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分类树。
 *
 * 三级结构：一级（parent_id = null）→ 二级 → 三级。
 * 例如：美妆个护 → 护肤 → 精华。
 *
 * depth 是冗余字段，避免每次查三级分类都要递归 CTE。
 * 维护规则：depth = parent.depth + 1，一级为 1。
 */
@Entity(
    tableName = "category",
    indices = [
        Index(value = ["name", "parent_id"], unique = true),
        Index(value = ["parent_id"]),
        Index(value = ["depth"])
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    /** 1 = 一级，2 = 二级，3 = 三级 */
    val depth: Int = 1,
    val source: String = "user",
    val icon: String? = null,
    val sort: Int = 100,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/**
 * 商品。
 *
 * 保质期字段：
 *   - expiry_enabled 默认 false，用户自主开启
 *   - production_date 生产日期（毫秒时间戳，仅取日期部分）
 *   - shelf_life_days 保质期天数
 *   - 过期时间 = production_date + shelf_life_days，运行时不落库，
 *     避免「日期改了但过期时间没跟着改」的不一致。
 */
@Entity(
    tableName = "item",
    indices = [
        Index(value = ["category_id", "deleted_at"]),
        Index(value = ["name"]),
        Index(value = ["barcode"]),
        Index(value = ["expiry_enabled"])
    ]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val spec: String = "",
    val unit: String = "件",
    val barcode: String? = null,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    val quantity: Double = 0.0,
    @ColumnInfo(name = "low_threshold") val lowThreshold: Double = 0.0,
    @ColumnInfo(name = "image_uri") val imageUri: String? = null,
    val note: String? = null,

    // ---- 保质期 ----
    @ColumnInfo(name = "expiry_enabled") val expiryEnabled: Boolean = false,
    /** 生产日期（当天 00:00 的时间戳） */
    @ColumnInfo(name = "production_date") val productionDate: Long? = null,
    /** 保质期天数 */
    @ColumnInfo(name = "shelf_life_days") val shelfLifeDays: Int? = null,

    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
) {
    /** 过期时间戳；未启用或数据不全时返回 null */
    fun expiryAt(): Long? {
        if (!expiryEnabled) return null
        val p = productionDate ?: return null
        val d = shelfLifeDays ?: return null
        return p + d.toLong() * 24L * 60L * 60L * 1000L
    }
}

/**
 * 流水。只增不删。
 *
 * reverted_by 指向补偿记录 id，历史行数值永不修改 —— 保证可审计。
 * 月度库存与消耗统计全部基于本表，因此本表是「明细」的唯一事实来源。
 */
@Entity(
    tableName = "stock_record",
    indices = [
        Index(value = ["item_id", "created_at"]),
        Index(value = ["batch_id"]),
        Index(value = ["created_at"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.NO_ACTION
        )
    ]
)
data class StockRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val type: String,
    val delta: Double,
    @ColumnInfo(name = "before_qty") val beforeQty: Double,
    @ColumnInfo(name = "after_qty") val afterQty: Double,
    @ColumnInfo(name = "batch_id") val batchId: String? = null,
    val reason: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "reverted_by") val revertedBy: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** 名称→分类规则。学习结果写这里。 */
@Entity(
    tableName = "category_rule",
    indices = [Index(value = ["token"], unique = true)]
)
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val token: String,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val weight: Double = 1.0,
    val source: String = "system",
    @ColumnInfo(name = "hit_count") val hitCount: Int = 1,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** 表单记忆：上次单位、上次分类、默认阈值、保质期开关 */
@Entity(tableName = "form_memo")
data class FormMemoEntity(
    @PrimaryKey val key: String,
    val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/**
 * 条码 → 商品信息的本地自学习库。
 *
 * 为什么需要它：公开条码库对国产商品覆盖很差（实测 69 开头的多数查不到），
 * 但用户亲手填过一次之后，下次扫同一商品就应该自动带出。
 * 这是「越用越准」的唯一可靠来源 —— 不依赖任何第三方，也不会失效。
 */
@Entity(
    tableName = "barcode_memo",
    indices = [Index(value = ["barcode"], unique = true)]
)
data class BarcodeMemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String,
    val name: String,
    val brand: String? = null,
    val spec: String? = null,
    /** 用户上次为这个条码选的分类 */
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    /** 命中次数：用得多的优先采用，也便于将来做清理策略 */
    @ColumnInfo(name = "hit_count") val hitCount: Int = 1,
    /** 来源：local=用户手填后记住，net=联网查到后采用 */
    val source: String = "local",
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
