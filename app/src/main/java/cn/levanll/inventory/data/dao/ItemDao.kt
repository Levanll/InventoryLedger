package cn.levanll.inventory.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cn.levanll.inventory.data.entity.ItemEntity

/**
 * 列表投影。
 *
 * 分类路径拆成三段而不是拼成一个字符串：
 *   - UI 需要分段着色（一级深、二级中、三级浅）
 *   - 排序与分组需要按层级判定
 *
 * 过期相关：expiryAt / daysLeft 由 SQL 现算，不落库，
 * 避免日期改了过期时间不跟着变。null 表示未启用或数据不全。
 */
data class ItemWithCategory(
    val id: Long,
    val name: String,
    val spec: String,
    val unit: String,
    val barcode: String?,
    val categoryId: Long?,
    val quantity: Double,
    val lowThreshold: Double,
    val imageUri: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // 分类路径
    val catL1: String?,
    val catL2: String?,
    val catL3: String?,
    // 保质期
    val expiryEnabled: Boolean,
    val productionDate: Long?,
    val shelfLifeDays: Int?,
    val expiryAt: Long?,
    /** 距过期天数：负数表示已过期 */
    val daysLeft: Int?
) {
    /** 用于 UI 展示的分类路径，如「美妆个护 / 护肤 / 精华」 */
    fun categoryPath(): String {
        val parts = mutableListOf<String>()
        catL1?.let { parts.add(it) }
        catL2?.let { parts.add(it) }
        catL3?.let { parts.add(it) }
        return parts.joinToString(" / ")
    }

    /** 最末级分类名，列表主标签用 */
    fun leafCategory(): String? = catL3 ?: catL2 ?: catL1
}

@Dao
interface ItemDao {

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("SELECT * FROM item WHERE id = :id AND deleted_at IS NULL LIMIT 1")
    suspend fun getById(id: Long): ItemEntity?

    @Query("SELECT * FROM item WHERE barcode = :barcode AND deleted_at IS NULL LIMIT 1")
    suspend fun findByBarcode(barcode: String): ItemEntity?

    @Query("SELECT * FROM item WHERE name = :name AND spec = :spec AND deleted_at IS NULL LIMIT 1")
    suspend fun findByNameSpec(name: String, spec: String): ItemEntity?

    /**
     * 入库联想：只联想还在用的商品（quantity > 0），
     * 否则补货时会联想出一堆已经空了的历史条目。
     */
    @Query(
        """
        SELECT i.id AS id, i.name AS name, i.spec AS spec, i.unit AS unit,
               i.barcode AS barcode, i.category_id AS categoryId, i.quantity AS quantity,
               i.low_threshold AS lowThreshold, i.image_uri AS imageUri, i.note AS note,
               i.created_at AS createdAt, i.updated_at AS updatedAt,
               c1.name AS catL1, c2.name AS catL2, c3.name AS catL3,
               i.expiry_enabled AS expiryEnabled, i.production_date AS productionDate,
               i.shelf_life_days AS shelfLifeDays,
               CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                         AND i.shelf_life_days IS NOT NULL
                    THEN i.production_date + i.shelf_life_days * 86400000
                    ELSE NULL END AS expiryAt,
               CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                         AND i.shelf_life_days IS NOT NULL
                    THEN CAST((i.production_date + i.shelf_life_days * 86400000 - :now) / 86400000 AS INTEGER)
                    ELSE NULL END AS daysLeft
        FROM item i
        LEFT JOIN category c3 ON c3.id = i.category_id
        LEFT JOIN category c2 ON c2.id = c3.parent_id
        LEFT JOIN category c1 ON c1.id = c2.parent_id
        WHERE i.deleted_at IS NULL AND i.quantity > 0 AND i.name LIKE '%' || :kw || '%'
        ORDER BY (CASE WHEN i.name LIKE :kw || '%' THEN 0 ELSE 1 END), i.updated_at DESC
        LIMIT :limit
        """
    )
    suspend fun suggestByName(kw: String, now: Long, limit: Int): List<ItemWithCategory>

    /**
     * 库存主列表。
     *
     * 排序是三级键，顺序不能错：
     *   1. 分类树（一级 → 二级 → 三级）—— 同分类的商品必须挨在一起
     *   2. 组内：有保质期的排在无保质期前面
     *   3. 组内保质期商品按剩余天数升序（已过期为负数，排最前）
     *
     * 为什么分类必须排第一：曾把保质期放在首位，导致有保质期的商品
     * 跳出自己的分类分组跑到整个列表顶部，同分类的商品被拆散。
     */
    @Query(
        """
        SELECT i.id AS id, i.name AS name, i.spec AS spec, i.unit AS unit,
               i.barcode AS barcode, i.category_id AS categoryId, i.quantity AS quantity,
               i.low_threshold AS lowThreshold, i.image_uri AS imageUri, i.note AS note,
               i.created_at AS createdAt, i.updated_at AS updatedAt,
               c1.name AS catL1, c2.name AS catL2, c3.name AS catL3,
               i.expiry_enabled AS expiryEnabled, i.production_date AS productionDate,
               i.shelf_life_days AS shelfLifeDays,
               CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                         AND i.shelf_life_days IS NOT NULL
                    THEN i.production_date + i.shelf_life_days * 86400000
                    ELSE NULL END AS expiryAt,
               CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                         AND i.shelf_life_days IS NOT NULL
                    THEN CAST((i.production_date + i.shelf_life_days * 86400000 - :now) / 86400000 AS INTEGER)
                    ELSE NULL END AS daysLeft
        FROM item i
        LEFT JOIN category c3 ON c3.id = i.category_id
        LEFT JOIN category c2 ON c2.id = c3.parent_id
        LEFT JOIN category c1 ON c1.id = c2.parent_id
        WHERE i.deleted_at IS NULL AND i.quantity > 0
          AND (:kw = '' OR i.name LIKE '%' || :kw || '%' OR i.spec LIKE '%' || :kw || '%')
          AND (:filterCat = 0 OR i.category_id IN (:categoryIds))
        ORDER BY
          IFNULL(c1.sort, 9999) ASC,
          IFNULL(c2.sort, 9999) ASC,
          IFNULL(c3.sort, 9999) ASC,
          i.category_id ASC,
          (CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                     AND i.shelf_life_days IS NOT NULL THEN 0 ELSE 1 END) ASC,
          (CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                     AND i.shelf_life_days IS NOT NULL
                THEN i.production_date + i.shelf_life_days * 86400000 ELSE 0 END) ASC,
          i.updated_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun search(
        kw: String,
        filterCat: Int,
        categoryIds: List<Long>,
        now: Long,
        limit: Int,
        offset: Int
    ): List<ItemWithCategory>

    /** 首页「最近」：只取在库 */
    @Query(
        """
        SELECT i.id AS id, i.name AS name, i.spec AS spec, i.unit AS unit,
               i.barcode AS barcode, i.category_id AS categoryId, i.quantity AS quantity,
               i.low_threshold AS lowThreshold, i.image_uri AS imageUri, i.note AS note,
               i.created_at AS createdAt, i.updated_at AS updatedAt,
               c1.name AS catL1, c2.name AS catL2, c3.name AS catL3,
               i.expiry_enabled AS expiryEnabled, i.production_date AS productionDate,
               i.shelf_life_days AS shelfLifeDays,
               CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                         AND i.shelf_life_days IS NOT NULL
                    THEN i.production_date + i.shelf_life_days * 86400000
                    ELSE NULL END AS expiryAt,
               CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                         AND i.shelf_life_days IS NOT NULL
                    THEN CAST((i.production_date + i.shelf_life_days * 86400000 - :now) / 86400000 AS INTEGER)
                    ELSE NULL END AS daysLeft
        FROM item i
        LEFT JOIN category c3 ON c3.id = i.category_id
        LEFT JOIN category c2 ON c2.id = c3.parent_id
        LEFT JOIN category c1 ON c1.id = c2.parent_id
        WHERE i.deleted_at IS NULL AND i.quantity > 0 AND i.id IN (
            SELECT item_id FROM stock_record WHERE type IN ('IN','OUT')
            GROUP BY item_id ORDER BY MAX(created_at) DESC LIMIT :limit
        )
        ORDER BY i.updated_at DESC
        """
    )
    suspend fun listRecentUsed(now: Long, limit: Int): List<ItemWithCategory>

    @Query("UPDATE item SET quantity = :qty, updated_at = :now WHERE id = :id")
    suspend fun setQuantity(id: Long, qty: Double, now: Long)

    @Query("UPDATE item SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE item SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    /** 低库存：数量 > 0 且 <= 阈值 */
    @Query(
        "SELECT * FROM item WHERE deleted_at IS NULL AND low_threshold > 0 " +
            "AND quantity > 0 AND quantity <= low_threshold"
    )
    suspend fun listLowStock(): List<ItemEntity>

    @Query(
        "SELECT COUNT(*) FROM item WHERE deleted_at IS NULL AND low_threshold > 0 " +
            "AND quantity > 0 AND quantity <= low_threshold"
    )
    suspend fun countLowStock(): Int

    /** 临期商品数量：剩余天数 <= thresholdDays，含已过期 */
    @Query(
        """
        SELECT COUNT(*) FROM item
        WHERE deleted_at IS NULL AND quantity > 0
          AND expiry_enabled = 1 AND production_date IS NOT NULL AND shelf_life_days IS NOT NULL
          AND ((production_date + shelf_life_days * 86400000 - :now) / 86400000) <= :thresholdDays
        """
    )
    suspend fun countExpiringSoon(now: Long, thresholdDays: Int): Int

    @Query("SELECT IFNULL(SUM(quantity), 0) FROM item WHERE deleted_at IS NULL AND quantity > 0")
    suspend fun totalQuantity(): Double

    /**
     * 已过期商品清单：进入应用时弹窗提醒用。
     * 只取真正过期的（expiry < now），按过期最久的排前。
     */
    @Query(
        """
        SELECT i.id AS id, i.name AS name, i.spec AS spec, i.unit AS unit,
               i.barcode AS barcode, i.category_id AS categoryId, i.quantity AS quantity,
               i.low_threshold AS lowThreshold, i.image_uri AS imageUri, i.note AS note,
               i.created_at AS createdAt, i.updated_at AS updatedAt,
               c1.name AS catL1, c2.name AS catL2, c3.name AS catL3,
               i.expiry_enabled AS expiryEnabled, i.production_date AS productionDate,
               i.shelf_life_days AS shelfLifeDays,
               CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                         AND i.shelf_life_days IS NOT NULL
                    THEN i.production_date + i.shelf_life_days * 86400000
                    ELSE NULL END AS expiryAt,
               CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                         AND i.shelf_life_days IS NOT NULL
                    THEN CAST((i.production_date + i.shelf_life_days * 86400000 - :now) / 86400000 AS INTEGER)
                    ELSE NULL END AS daysLeft
        FROM item i
        LEFT JOIN category c3 ON c3.id = i.category_id
        LEFT JOIN category c2 ON c2.id = c3.parent_id
        LEFT JOIN category c1 ON c1.id = c2.parent_id
        WHERE i.deleted_at IS NULL AND i.quantity > 0
          AND i.expiry_enabled = 1 AND i.production_date IS NOT NULL
          AND i.shelf_life_days IS NOT NULL
          AND (i.production_date + i.shelf_life_days * 86400000) < :now
        ORDER BY (i.production_date + i.shelf_life_days * 86400000) ASC
        """
    )
    suspend fun listExpired(now: Long): List<ItemWithCategory>

    @Query("SELECT COUNT(*) FROM item WHERE deleted_at IS NULL AND quantity > 0")
    suspend fun countInStock(): Int

    @Query(
        """
        SELECT i.id AS id, i.name AS name, i.spec AS spec, i.unit AS unit,
               i.barcode AS barcode, i.category_id AS categoryId, i.quantity AS quantity,
               i.low_threshold AS lowThreshold, i.image_uri AS imageUri, i.note AS note,
               i.created_at AS createdAt, i.updated_at AS updatedAt,
               c1.name AS catL1, c2.name AS catL2, c3.name AS catL3,
               i.expiry_enabled AS expiryEnabled, i.production_date AS productionDate,
               i.shelf_life_days AS shelfLifeDays,
               CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                         AND i.shelf_life_days IS NOT NULL
                    THEN i.production_date + i.shelf_life_days * 86400000
                    ELSE NULL END AS expiryAt,
               CASE WHEN i.expiry_enabled = 1 AND i.production_date IS NOT NULL
                         AND i.shelf_life_days IS NOT NULL
                    THEN CAST((i.production_date + i.shelf_life_days * 86400000 - :now) / 86400000 AS INTEGER)
                    ELSE NULL END AS daysLeft
        FROM item i
        LEFT JOIN category c3 ON c3.id = i.category_id
        LEFT JOIN category c2 ON c2.id = c3.parent_id
        LEFT JOIN category c1 ON c1.id = c2.parent_id
        WHERE i.deleted_at IS NULL AND i.quantity > 0
        ORDER BY c1.sort ASC, c2.sort ASC, c3.sort ASC, i.name ASC
        """
    )
    suspend fun listAllForExport(now: Long): List<ItemWithCategory>
}
