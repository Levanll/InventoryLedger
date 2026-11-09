package cn.levanll.inventory.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cn.levanll.inventory.data.entity.BarcodeMemoEntity

@Dao
interface BarcodeDao {

    @Query("SELECT * FROM barcode_memo WHERE barcode = :barcode LIMIT 1")
    suspend fun get(barcode: String): BarcodeMemoEntity?

    @Insert
    suspend fun insert(m: BarcodeMemoEntity): Long

    @Query(
        "UPDATE barcode_memo SET name = :name, brand = :brand, spec = :spec, " +
            "category_id = :categoryId, source = :source, " +
            "hit_count = hit_count + 1, updated_at = :now WHERE barcode = :barcode"
    )
    suspend fun update(
        barcode: String,
        name: String,
        brand: String?,
        spec: String?,
        categoryId: Long?,
        source: String,
        now: Long
    )

    @Query("UPDATE barcode_memo SET hit_count = hit_count + 1, updated_at = :now WHERE barcode = :barcode")
    suspend fun bumpHit(barcode: String, now: Long)

    @Query("SELECT COUNT(*) FROM barcode_memo")
    suspend fun count(): Int

    /** 清理策略预留：只保留最近常用的 N 条 */
    @Query(
        "DELETE FROM barcode_memo WHERE barcode NOT IN " +
            "(SELECT barcode FROM barcode_memo ORDER BY updated_at DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM barcode_memo WHERE barcode = :barcode")
    suspend fun delete(barcode: String)
}
