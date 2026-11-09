package cn.levanll.inventory.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cn.levanll.inventory.data.entity.CategoryEntity
import cn.levanll.inventory.data.entity.CategoryRuleEntity
import cn.levanll.inventory.data.entity.FormMemoEntity

/** 分类统计投影：动态 chips 的唯一数据源 */
data class CategoryStat(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val depth: Int,
    val count: Int
)

/** 分类管理页投影，pathName 是「一级 / 二级 / 三级」可读路径 */
data class CategoryNode(
    val id: Long,
    val name: String,
    val parentId: Long?,
    val depth: Int,
    val sort: Int,
    val source: String,
    val itemCount: Int,
    val childCount: Int,
    val pathName: String
)

/** 分类选择用的轻量行（含路径与深度，便于缩进展示） */
data class CategoryPick(
    val id: Long,
    val name: String,
    val depth: Int,
    val parentId: Long?,
    val pathName: String,
    val sort: Int
)

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(c: CategoryEntity): Long

    @Update
    suspend fun update(c: CategoryEntity)

    @Query("SELECT * FROM category WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT id FROM category WHERE name = :name AND parent_id IS :parentId LIMIT 1")
    suspend fun findIdByName(name: String, parentId: Long?): Long?

    /** 修正 depth（历史数据可能是错的） */
    @Query("UPDATE category SET depth = :depth WHERE id = :id AND depth <> :depth")
    suspend fun setDepthIfWrong(id: Long, depth: Int)

    @Query("SELECT COUNT(*) FROM category WHERE parent_id = :parentId")
    suspend fun countChildren(parentId: Long?): Int

    @Query(
        """
        SELECT p.name AS pname, c.name AS cname
        FROM category c LEFT JOIN category p ON p.id = c.parent_id
        WHERE c.id = :id LIMIT 1
        """
    )
    suspend fun getDisplayNameRaw(id: Long): DisplayNamePair?

    /** 动态分类条：只统计在库商品，且只取一级分类（总览用） */
    /**
     * 动态分类条：只统计在库商品，且只取一级分类（总览用）。
     *
     * 不能写成 `JOIN item ON i.category_id = c.id WHERE c.depth = 1`：
     * 商品的 category_id 指向的是**最末级**分类（如「T恤」），
     * 永远不等于一级分类（「服装」）的 id，那样查出来永远是空。
     *
     * 正确做法是把每个商品沿 parent_id 向上回溯到自己所属的一级分类，
     * 再按一级分类聚合。用递归 CTE 完成回溯。
     */
    @Query(
        """
        WITH RECURSIVE up(item_id, cat_id, parent_id, depth) AS (
            SELECT i.id, c.id, c.parent_id, c.depth
            FROM item i JOIN category c ON c.id = i.category_id
            WHERE i.quantity > 0 AND i.deleted_at IS NULL
            UNION ALL
            SELECT up.item_id, p.id, p.parent_id, p.depth
            FROM up JOIN category p ON p.id = up.parent_id
            WHERE up.depth > 1
        )
        SELECT c.id AS id, c.name AS name, c.parent_id AS parentId, c.depth AS depth,
               COUNT(DISTINCT up.item_id) AS count
        FROM up JOIN category c ON c.id = up.cat_id
        WHERE up.depth = 1
        GROUP BY c.id
        HAVING COUNT(DISTINCT up.item_id) > 0
        ORDER BY c.sort ASC, c.id ASC
        """
    )
    suspend fun listRootStats(): List<CategoryStat>

    /**
     * 某分类下所有子孙分类 id（含自身）。
     * 用递归 CTE，SQLite 3.8.3+ 支持（Android 5.0+ 满足）。
     */
    @Query(
        """
        WITH RECURSIVE sub(id) AS (
            SELECT :rootId
            UNION ALL
            SELECT c.id FROM category c JOIN sub ON c.parent_id = sub.id
        )
        SELECT id FROM sub
        """
    )
    suspend fun listDescendantIds(rootId: Long): List<Long>

    /**
     * 全部分类（含空类），带路径名与用量，按树形先序排列。
     *
     * 不能用 ORDER BY sort：sort 里二级的值（11,12）与一级（10,20）交错，
     * 排出来会变成「服装 → 上装 → 下装 → 食品 → 生鲜」这种层级错乱。
     * 用 path 字符串排序（如「002服装/001上装/001T恤」）天然得到先序 DFS。
     */
    @Query(
        """
        WITH RECURSIVE tree(id, name, depth, parent_id, sort, source, path) AS (
            SELECT id, name, depth, parent_id, sort, source,
                   printf('%05d', sort) || '/' || name
            FROM category WHERE parent_id IS NULL
            UNION ALL
            SELECT c.id, c.name, c.depth, c.parent_id, c.sort, c.source,
                   tree.path || '/' || printf('%05d', c.sort) || '/' || c.name
            FROM category c JOIN tree ON c.parent_id = tree.id
        )
        SELECT t.id AS id, t.name AS name, t.parent_id AS parentId, t.depth AS depth,
               t.sort AS sort, t.source AS source,
               (SELECT COUNT(*) FROM item i
                 WHERE i.category_id = t.id AND i.deleted_at IS NULL AND i.quantity > 0) AS itemCount,
               (SELECT COUNT(*) FROM category ch WHERE ch.parent_id = t.id) AS childCount,
               CASE
                 WHEN t.depth = 1 THEN t.name
                 WHEN t.depth = 2 THEN p.name || ' / ' || t.name
                 ELSE gp.name || ' / ' || p.name || ' / ' || t.name
               END AS pathName
        FROM tree t
        LEFT JOIN category p ON p.id = t.parent_id
        LEFT JOIN category gp ON gp.id = p.parent_id
        ORDER BY t.path ASC
        """
    )
    suspend fun listAllWithUsage(): List<CategoryNode>

    /** 选择器用：按树形先序，含三级 */
    @Query(
        """
        WITH RECURSIVE tree(id, name, depth, parent_id, sort, path) AS (
            SELECT id, name, depth, parent_id, sort,
                   printf('%05d', sort) || '/' || name
            FROM category WHERE parent_id IS NULL
            UNION ALL
            SELECT c.id, c.name, c.depth, c.parent_id, c.sort,
                   tree.path || '/' || printf('%05d', c.sort) || '/' || c.name
            FROM category c JOIN tree ON c.parent_id = tree.id
        )
        SELECT t.id AS id, t.name AS name, t.depth AS depth, t.parent_id AS parentId,
               CASE
                 WHEN t.depth = 1 THEN t.name
                 WHEN t.depth = 2 THEN p.name || ' / ' || t.name
                 ELSE gp.name || ' / ' || p.name || ' / ' || t.name
               END AS pathName,
               t.sort AS sort
        FROM tree t
        LEFT JOIN category p ON p.id = t.parent_id
        LEFT JOIN category gp ON gp.id = p.parent_id
        ORDER BY t.path ASC
        """
    )
    suspend fun listForPick(): List<CategoryPick>

    @Query("SELECT * FROM category ORDER BY sort ASC, id ASC")
    suspend fun listAll(): List<CategoryEntity>

    @Query("UPDATE category SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE category SET sort = :sort WHERE id = :id")
    suspend fun updateSort(id: Long, sort: Int)

    /** 删除分类。调用方必须先确保其下无商品、无子分类。 */
    @Query("DELETE FROM category WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 把该分类下的商品迁移到另一个分类 */
    @Query("UPDATE item SET category_id = :toId, updated_at = :now WHERE category_id = :fromId")
    suspend fun migrateItems(fromId: Long, toId: Long, now: Long)

    @Query("SELECT IFNULL(MAX(sort), 99) FROM category WHERE parent_id IS :parentId")
    suspend fun maxSortUnder(parentId: Long?): Int

    @Query("SELECT COUNT(*) FROM category WHERE parent_id = :id")
    suspend fun countChildrenOf(id: Long): Int

    @Query("SELECT COUNT(*) FROM item WHERE category_id = :id AND deleted_at IS NULL")
    suspend fun countItemsOf(id: Long): Int

    @Query("SELECT COUNT(*) FROM item WHERE category_id = :id AND deleted_at IS NULL AND quantity > 0")
    suspend fun countItems(id: Long): Int

    // ---- 规则表 ----

    @Query("SELECT * FROM category_rule WHERE token IN (:tokens)")
    suspend fun findRulesByTokens(tokens: List<String>): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rule WHERE category_id = :categoryId ORDER BY weight DESC")
    suspend fun listRulesByCategory(categoryId: Long): List<CategoryRuleEntity>

    @Insert
    suspend fun insertRule(rule: CategoryRuleEntity): Long

    @Query(
        "UPDATE category_rule SET category_id = :categoryId, weight = :weight, " +
            "source = 'learned', hit_count = hit_count + 1, updated_at = :now WHERE token = :token"
    )
    suspend fun updateRuleLearned(token: String, categoryId: Long, weight: Double, now: Long)

    @Query("UPDATE category_rule SET category_id = :categoryId, updated_at = :now WHERE id = :ruleId")
    suspend fun moveRule(ruleId: Long, categoryId: Long, now: Long)

    @Query("DELETE FROM category_rule WHERE id = :ruleId")
    suspend fun deleteRuleById(ruleId: Long)

    @Query("DELETE FROM category_rule WHERE category_id = :categoryId")
    suspend fun deleteRulesOfCategory(categoryId: Long)

    @Query("DELETE FROM category_rule WHERE category_id = :categoryId AND token = :token")
    suspend fun deleteRuleByToken(categoryId: Long, token: String)

    // ---- 表单记忆 ----

    @Query("SELECT value FROM form_memo WHERE `key` = :key LIMIT 1")
    suspend fun getMemo(key: String): String?

    @Insert
    suspend fun insertMemo(m: FormMemoEntity)

    @Query("UPDATE form_memo SET value = :value, updated_at = :now WHERE `key` = :key")
    suspend fun updateMemo(key: String, value: String, now: Long)

    @Query("DELETE FROM form_memo WHERE `key` = :key")
    suspend fun deleteMemo(key: String)
}

data class DisplayNamePair(val pname: String?, val cname: String)
