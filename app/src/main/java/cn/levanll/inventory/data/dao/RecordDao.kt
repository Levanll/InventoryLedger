package cn.levanll.inventory.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cn.levanll.inventory.data.entity.StockRecordEntity

/** 流水页投影：带商品名与单位 */
data class RecordView(
    val id: Long,
    val itemId: Long,
    val itemName: String?,
    val itemUnit: String?,
    val type: String,
    val delta: Double,
    val beforeQty: Double,
    val afterQty: Double,
    val batchId: String?,
    val reason: String?,
    val revertedBy: Long?,
    val createdAt: Long
)

/** 月度聚合行 */
data class MonthlyAgg(
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

/** 月度单条明细 */
data class MonthlyDetail(
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

/** 某月整体概览 */
data class MonthSummary(
    val inQty: Double,
    val outQty: Double,
    val inCount: Int,
    val outCount: Int,
    val activeItems: Int
)

@Dao
interface RecordDao {
    @Insert
    suspend fun insert(r: StockRecordEntity): Long

    @Insert
    suspend fun insertAll(list: List<StockRecordEntity>): List<Long>

    @Query("SELECT * FROM stock_record WHERE id = :id AND reverted_by IS NULL LIMIT 1")
    suspend fun getRevertable(id: Long): StockRecordEntity?

    @Query(
        """
        SELECT * FROM stock_record
        WHERE batch_id = :batchId AND reverted_by IS NULL
          AND (reason IS NULL OR reason <> 'REVERT')
        ORDER BY id ASC
        """
    )
    suspend fun getBatchRevertable(batchId: String): List<StockRecordEntity>

    @Query("UPDATE stock_record SET reverted_by = :byId WHERE id IN (:ids)")
    suspend fun markReverted(ids: List<Long>, byId: Long)

    @Query("SELECT IFNULL(MAX(id), 0) FROM stock_record")
    suspend fun maxId(): Long

    @Query("SELECT * FROM stock_record WHERE item_id = :itemId ORDER BY created_at DESC, id DESC LIMIT :limit")
    suspend fun listByItem(itemId: Long, limit: Int): List<StockRecordEntity>

    @Query(
        """
        SELECT r.id AS id, r.item_id AS itemId, i.name AS itemName, i.unit AS itemUnit,
               r.type AS type, r.delta AS delta, r.before_qty AS beforeQty,
               r.after_qty AS afterQty, r.batch_id AS batchId, r.reason AS reason,
               r.reverted_by AS revertedBy, r.created_at AS createdAt
        FROM stock_record r LEFT JOIN item i ON i.id = r.item_id
        WHERE (:type IS NULL OR r.type = :type)
        ORDER BY r.created_at DESC, r.id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun listPage(type: String?, limit: Int, offset: Int): List<RecordView>

    // ==========================================================
    // 月度统计
    //
    // 时间范围统一用半开区间 [from, to)，由调用方按自然月计算毫秒边界。
    // 撤销记录（reason='REVERT'）本身也是一条流水，会计入统计 —— 这是
    // 正确行为：用户撤销一次出库，等于「出库后又补回」，消耗应被抵消。
    // ==========================================================

    @Query(
        """
        SELECT
          IFNULL(SUM(CASE WHEN type = 'IN' THEN delta ELSE 0 END), 0) AS inQty,
          IFNULL(SUM(CASE WHEN type = 'OUT' THEN -delta ELSE 0 END), 0) AS outQty,
          CAST(SUM(CASE WHEN type = 'IN' THEN 1 ELSE 0 END) AS INTEGER) AS inCount,
          CAST(SUM(CASE WHEN type = 'OUT' THEN 1 ELSE 0 END) AS INTEGER) AS outCount,
          COUNT(DISTINCT item_id) AS activeItems
        FROM stock_record
        WHERE created_at >= :from AND created_at < :to
        """
    )
    suspend fun monthSummary(from: Long, to: Long): MonthSummary?

    /**
     * 月度按商品聚合。
     * endQty 取该月最后一条流水的 after_qty，即「月末结存」。
     */
    @Query(
        """
        SELECT
          r.item_id AS itemId,
          IFNULL(i.name, '（已删除）') AS itemName,
          IFNULL(i.unit, '') AS unit,
          IFNULL(SUM(CASE WHEN r.type = 'IN' THEN r.delta ELSE 0 END), 0) AS inQty,
          IFNULL(SUM(CASE WHEN r.type = 'OUT' THEN -r.delta ELSE 0 END), 0) AS outQty,
          IFNULL(SUM(CASE WHEN r.type = 'ADJUST' THEN r.delta ELSE 0 END), 0) AS adjustQty,
          IFNULL(SUM(r.delta), 0) AS netDelta,
          (SELECT r2.after_qty FROM stock_record r2
             WHERE r2.item_id = r.item_id AND r2.created_at >= :from AND r2.created_at < :to
             ORDER BY r2.created_at DESC, r2.id DESC LIMIT 1) AS endQty,
          CAST(COUNT(*) AS INTEGER) AS recordCount
        FROM stock_record r LEFT JOIN item i ON i.id = r.item_id
        WHERE r.created_at >= :from AND r.created_at < :to
        GROUP BY r.item_id
        ORDER BY outQty DESC, inQty DESC, itemId ASC
        """
    )
    suspend fun monthlyByItem(from: Long, to: Long): List<MonthlyAgg>

    /** 只看消耗（出库）的月度聚合 */
    @Query(
        """
        SELECT
          r.item_id AS itemId,
          IFNULL(i.name, '（已删除）') AS itemName,
          IFNULL(i.unit, '') AS unit,
          IFNULL(SUM(CASE WHEN r.type = 'IN' THEN r.delta ELSE 0 END), 0) AS inQty,
          IFNULL(SUM(CASE WHEN r.type = 'OUT' THEN -r.delta ELSE 0 END), 0) AS outQty,
          IFNULL(SUM(CASE WHEN r.type = 'ADJUST' THEN r.delta ELSE 0 END), 0) AS adjustQty,
          IFNULL(SUM(r.delta), 0) AS netDelta,
          (SELECT r2.after_qty FROM stock_record r2
             WHERE r2.item_id = r.item_id AND r2.created_at >= :from AND r2.created_at < :to
             ORDER BY r2.created_at DESC, r2.id DESC LIMIT 1) AS endQty,
          CAST(COUNT(*) AS INTEGER) AS recordCount
        FROM stock_record r LEFT JOIN item i ON i.id = r.item_id
        WHERE r.created_at >= :from AND r.created_at < :to AND r.type = 'OUT'
        GROUP BY r.item_id
        ORDER BY outQty DESC, itemId ASC
        """
    )
    suspend fun monthlyConsumption(from: Long, to: Long): List<MonthlyAgg>

    @Query(
        """
        SELECT r.id AS id, IFNULL(i.name, '（已删除）') AS itemName, IFNULL(i.unit, '') AS unit,
               r.type AS type, r.delta AS delta, r.before_qty AS beforeQty,
               r.after_qty AS afterQty, r.note AS note, r.created_at AS createdAt,
               r.reverted_by AS revertedBy
        FROM stock_record r LEFT JOIN item i ON i.id = r.item_id
        WHERE r.item_id = :itemId AND r.created_at >= :from AND r.created_at < :to
        ORDER BY r.created_at ASC, r.id ASC
        """
    )
    suspend fun monthDetailOfItem(itemId: Long, from: Long, to: Long): List<MonthlyDetail>

    @Query(
        """
        SELECT r.id AS id, IFNULL(i.name, '（已删除）') AS itemName, IFNULL(i.unit, '') AS unit,
               r.type AS type, r.delta AS delta, r.before_qty AS beforeQty,
               r.after_qty AS afterQty, r.note AS note, r.created_at AS createdAt,
               r.reverted_by AS revertedBy
        FROM stock_record r LEFT JOIN item i ON i.id = r.item_id
        WHERE r.created_at >= :from AND r.created_at < :to
        ORDER BY r.created_at DESC, r.id DESC
        """
    )
    suspend fun monthAllDetails(from: Long, to: Long): List<MonthlyDetail>

    /** 有数据的月份列表，返回形如 202611 的整数 */
    @Query(
        """
        SELECT DISTINCT CAST(strftime('%Y%m', created_at / 1000, 'unixepoch', 'localtime') AS INTEGER) AS ym
        FROM stock_record
        ORDER BY ym DESC
        """
    )
    suspend fun listMonthsWithData(): List<Int>

    @Query(
        """
        SELECT r.id AS id, r.item_id AS itemId, i.name AS itemName, i.unit AS itemUnit,
               r.type AS type, r.delta AS delta, r.before_qty AS beforeQty,
               r.after_qty AS afterQty, r.batch_id AS batchId, r.reason AS reason,
               r.reverted_by AS revertedBy, r.created_at AS createdAt
        FROM stock_record r LEFT JOIN item i ON i.id = r.item_id
        ORDER BY r.created_at ASC, r.id ASC
        """
    )
    suspend fun listAllForExport(): List<RecordView>
}
