package cn.levanll.inventory.domain

import androidx.room.withTransaction
import cn.levanll.inventory.data.dao.CategoryDao
import cn.levanll.inventory.data.db.AppDatabase
import cn.levanll.inventory.data.entity.CategoryRuleEntity
import cn.levanll.inventory.data.entity.FormMemoEntity

/**
 * 分类识别服务
 *
 * 识别单元是「任意层级分类」：规则表存的是具体分类 id，
 * 可能是一级、二级或三级。返回结果带完整路径。
 */
class ClassifyService(private val db: AppDatabase) {

    private val catDao: CategoryDao get() = db.categoryDao()

    companion object {
        /** 中文切词：整串 + 2~4 字 n-gram，长 token 优先，上限 24 */
        fun tokens(name: String): List<String> {
            val clean = name.trim().replace(Regex("\\s+"), "")
            if (clean.isEmpty()) return emptyList()
            val set = LinkedHashSet<String>()
            set.add(clean)
            for (len in 4 downTo 2) {
                for (i in 0..(clean.length - len)) {
                    set.add(clean.substring(i, i + len))
                }
            }
            return set.sortedByDescending { it.length }.take(24)
        }
    }

    suspend fun classify(name: String): ClassifyResult {
        val empty = ClassifyResult(null, null, 0.0, false)
        val tk = tokens(name)
        if (tk.isEmpty()) return empty

        val rules = catDao.findRulesByTokens(tk)
        if (rules.isEmpty()) return empty

        // 深度缓存，避免每条规则都查一次分类
        val depthOf = HashMap<Long, Int>()
        for (r in rules) {
            depthOf[r.categoryId] = catDao.getById(r.categoryId)?.depth ?: 1
        }

        // 加权：weight × 长度加成(≥3字 ×1.2) × 学习加成(learned ×1.3) × 深度加成
        val scoreMap = HashMap<Long, Double>()
        for (r in rules) {
            val lenBonus = if (r.token.length >= 3) 1.2 else 1.0
            val learnBonus = if (r.source == "learned") 1.3 else 1.0
            val depthBonus = 1.0 + (depthOf[r.categoryId] ?: 1).coerceAtMost(3) * 0.08
            val add = r.weight * lenBonus * learnBonus * depthBonus
            scoreMap[r.categoryId] = (scoreMap[r.categoryId] ?: 0.0) + add
        }

        val ranked = scoreMap.entries.sortedByDescending { it.value }
        val best = ranked.first()
        val second = if (ranked.size > 1) ranked[1].value else 0.0

        val conf = Math.min(Constants.CONF_CEILING, best.value / tk.size)
        val gap = if (best.value == 0.0) 0.0 else (best.value - second) / best.value
        val ambiguous = ranked.size > 1 && gap < Constants.CONF_AMBIGUOUS_GAP

        if (conf < Constants.CONF_NEED_CONFIRM) {
            return ClassifyResult(null, null, conf, false)
        }

        return ClassifyResult(
            categoryId = best.key,
            categoryPath = pathOf(best.key),
            confidence = conf,
            needConfirm = conf < Constants.CONF_AUTO_ACCEPT || ambiguous
        )
    }

    /** 分类 id → 「一级 / 二级 / 三级」 */
    suspend fun pathOf(categoryId: Long): String? {
        val chain = ArrayList<String>(3)
        var cur = catDao.getById(categoryId) ?: return null
        chain.add(cur.name)
        var guard = 0
        while (cur.parentId != null && guard < 5) {
            cur = catDao.getById(cur.parentId!!) ?: break
            chain.add(cur.name)
            guard++
        }
        return chain.reversed().joinToString(" / ")
    }

    /**
     * 表单预填：三级回退
     *   ① 已有商品分类（由调用方传入）
     *   ② 名称规则识别
     *   ③ form_memo 上次使用分类
     */
    suspend fun prefill(name: String, existingCategoryId: Long? = null): ClassifyResult {
        if (existingCategoryId != null) {
            return ClassifyResult(existingCategoryId, pathOf(existingCategoryId), 1.0, false)
        }
        val r = classify(name)
        if (r.categoryId != null) return r

        val lastId = catDao.getMemo(Constants.MEMO_LAST_CATEGORY)?.toLongOrNull() ?: return r
        val display = pathOf(lastId) ?: return r
        return ClassifyResult(lastId, display, 0.5, true)
    }

    /** 人工校正回写：学习名称分词 → 分类 */
    suspend fun learn(name: String, categoryId: Long) {
        val tk = tokens(name)
        if (tk.isEmpty()) return
        val now = System.currentTimeMillis()

        db.withTransaction {
            for (t in tk) {
                if (t.length < 2 || t.length > 6) continue
                val existing = catDao.findRulesByTokens(listOf(t))
                if (existing.isEmpty()) {
                    catDao.insertRule(
                        CategoryRuleEntity(
                            token = t,
                            categoryId = categoryId,
                            weight = 1.2,
                            source = "learned",
                            hitCount = 1,
                            updatedAt = now
                        )
                    )
                } else {
                    val old = existing.first()
                    val newWeight = Math.min(3.0, old.weight + 0.2)
                    catDao.updateRuleLearned(t, categoryId, newWeight, now)
                }
            }
            rememberCategory(categoryId)
        }
    }

    suspend fun rememberCategory(categoryId: Long) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            if (catDao.getMemo(Constants.MEMO_LAST_CATEGORY) == null) {
                catDao.insertMemo(
                    FormMemoEntity(Constants.MEMO_LAST_CATEGORY, categoryId.toString(), now)
                )
            } else {
                catDao.updateMemo(Constants.MEMO_LAST_CATEGORY, categoryId.toString(), now)
            }
        }
    }
}
