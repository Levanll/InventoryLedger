package cn.levanll.inventory.domain

import androidx.room.withTransaction
import cn.levanll.inventory.data.dao.CategoryPick
import cn.levanll.inventory.data.db.AppDatabase
import cn.levanll.inventory.data.entity.CategoryEntity

/**
 * 分类管理服务。
 *
 * 系统预置分类与用户自建分类能力完全平等：都能改名、删除、建子类。
 * 唯一约束是三级上限，避免分类树无限膨胀。
 */
class CategoryService(private val db: AppDatabase) {

    private val catDao get() = db.categoryDao()

    suspend fun listForPick(): List<CategoryPick> = catDao.listForPick()

    /**
     * 在指定父级下新建分类。
     * @param parentId null 表示新建一级分类
     */
    suspend fun create(parentId: Long?, name: String): Long {
        val clean = name.trim()
        if (clean.isEmpty()) throw AppException(
                ErrCode.INVALID_ARG,
                "分类名不能为空",
                en = "Category name cannot be empty"
            )
        if (clean.length > 20) throw AppException(
                ErrCode.INVALID_ARG,
                "分类名不能超过 20 字",
                en = "Category name cannot exceed 20 characters"
            )

        return db.withTransaction {
            val dup = catDao.findIdByName(clean, parentId)
            if (dup != null) {
                throw AppException(
                    ErrCode.INVALID_ARG,
                    "同一级下已有「$clean」",
                    en = "A category named \"$clean\" already exists at this level"
                )
            }

            val parentDepth = if (parentId == null) {
                0
            } else {
                val p = catDao.getById(parentId)
                    ?: throw AppException(
                        ErrCode.CATEGORY_NOT_FOUND,
                        "上级分类不存在",
                        en = "Parent category not found"
                    )
                p.depth
            }
            if (parentDepth >= 3) {
                throw AppException(
                ErrCode.INVALID_ARG,
                "最多支持三级分类",
                en = "Only three category levels are supported"
            )
            }

            // 排序值接在同级最大值之后，保证新建的排在最后
            val sort = catDao.maxSortUnder(parentId) + 1
            catDao.insert(
                CategoryEntity(
                    name = clean,
                    parentId = parentId,
                    depth = parentDepth + 1,
                    source = "user",
                    sort = sort,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** 重命名。系统预置与自建一视同仁。 */
    suspend fun rename(id: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) throw AppException(
                ErrCode.INVALID_ARG,
                "分类名不能为空",
                en = "Category name cannot be empty"
            )
        if (clean.length > 20) throw AppException(
                ErrCode.INVALID_ARG,
                "分类名不能超过 20 字",
                en = "Category name cannot exceed 20 characters"
            )

        db.withTransaction {
            val cur = catDao.getById(id)
                ?: throw AppException(
                    ErrCode.CATEGORY_NOT_FOUND,
                    "分类不存在",
                    en = "Category not found"
                )
            val dup = catDao.findIdByName(clean, cur.parentId)
            if (dup != null && dup != id) {
                throw AppException(
                    ErrCode.INVALID_ARG,
                    "同一级下已有「$clean」",
                    en = "A category named \"$clean\" already exists at this level"
                )
            }
            catDao.rename(id, clean)
        }
    }

    /**
     * 删除分类。
     * 有商品或子分类时拒绝删除 —— 静默迁移会让用户丢失数据归属，
     * 必须由用户明确选择迁移目标，走 migrateAndDelete()。
     */
    suspend fun delete(id: Long) {
        db.withTransaction {
            catDao.getById(id) ?: throw AppException(
                    ErrCode.CATEGORY_NOT_FOUND,
                    "分类不存在",
                    en = "Category not found"
                )

            val children = catDao.countChildrenOf(id)
            if (children > 0) {
                throw AppException(
                    ErrCode.CATEGORY_HAS_CHILDREN,
                    "该分类下还有 $children 个子分类",
                    en = "This category still has $children subcategories"
                )
            }
            val items = catDao.countItemsOf(id)
            if (items > 0) {
                throw AppException(
                    ErrCode.CATEGORY_HAS_CHILDREN,
                    "该分类下还有 $items 件商品",
                    en = "This category still has $items items"
                )
            }
            catDao.deleteById(id)
        }
    }

    /** 把商品迁移到目标分类后再删除自己 */
    suspend fun migrateAndDelete(fromId: Long, toId: Long) {
        if (fromId == toId) throw AppException(
            ErrCode.INVALID_ARG,
            "不能迁移到自己",
            en = "Cannot migrate a category to itself"
        )
        db.withTransaction {
            catDao.getById(fromId) ?: throw AppException(
                ErrCode.CATEGORY_NOT_FOUND,
                "源分类不存在",
                en = "Source category not found"
            )
            catDao.getById(toId) ?: throw AppException(
                ErrCode.CATEGORY_NOT_FOUND,
                "目标分类不存在",
                en = "Target category not found"
            )
            if (catDao.countChildrenOf(fromId) > 0) {
                throw AppException(
                    ErrCode.CATEGORY_HAS_CHILDREN,
                    "请先处理该分类下的子分类",
                    en = "Please handle the subcategories of this category first"
                )
            }
            catDao.migrateItems(fromId, toId, System.currentTimeMillis())
            catDao.deleteById(fromId)
        }
    }

    /** 删除前置检查：UI 据此决定是直接删还是引导迁移 */
    suspend fun canDelete(id: Long): DeleteCheck {
        val children = catDao.countChildrenOf(id)
        val items = catDao.countItemsOf(id)
        return DeleteCheck(
            categoryId = id,
            childCount = children,
            itemCount = items,
            deletable = children == 0 && items == 0
        )
    }

    data class DeleteCheck(
        val categoryId: Long,
        val childCount: Int,
        val itemCount: Int,
        val deletable: Boolean
    )
}
