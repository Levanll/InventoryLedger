package cn.levanll.inventory.domain

import androidx.room.withTransaction
import cn.levanll.inventory.data.dao.ItemWithCategory
import cn.levanll.inventory.data.db.AppDatabase
import cn.levanll.inventory.data.entity.BarcodeMemoEntity
import cn.levanll.inventory.data.entity.FormMemoEntity
import cn.levanll.inventory.data.entity.ItemEntity
import cn.levanll.inventory.data.entity.StockRecordEntity
import cn.levanll.inventory.i18n.I18n

/**
 * 库存核心服务
 *
 * 事务纪律：
 *   - 所有写操作经 Room 的 withTransaction()
 *   - 撤销只写补偿记录，历史行永不改数值
 *   - 数量归零不自动删商品：归零后从列表消失，但数据保留
 */
class InventoryService(private val db: AppDatabase) {

    private val itemDao get() = db.itemDao()
    private val recordDao get() = db.recordDao()
    private val catDao get() = db.categoryDao()

    // ==========================================================
    // 入库
    // ==========================================================

    /**
     * 入库三分支：
     *   1. existingItemId 非空 → 追加到已有商品
     *   2. allowCreateSpec = true → 跳过同名同规格校验
     *   3. 默认 → 命中同名同规格且仍有库存时抛 DUPLICATE_ITEM
     *
     * 保质期：仅在 expiryEnabled 且日期/天数齐备时写入，否则一律置空。
     * 关掉开关后必须真正清掉旧值，否则会出现「未启用但有到期日」的矛盾状态。
     */
    suspend fun inbound(input: InboundInput): InboundResult {
        val name = input.name.trim()
        if (name.isEmpty()) throw AppException(
                ErrCode.INVALID_ARG,
                "名称不能为空",
                en = "Name cannot be empty"
            )
        if (input.qty <= Constants.QTY_MIN) throw AppException(
                ErrCode.INVALID_ARG,
                "数量必须大于 0",
                en = "Quantity must be greater than 0"
            )
        if (input.qty > Constants.QTY_MAX) {
            throw AppException(
                ErrCode.INVALID_ARG,
                "单次入库不能超过 ${Constants.QTY_MAX.toInt()}",
                en = "A single inbound cannot exceed ${Constants.QTY_MAX.toInt()}"
            )
        }

        val (expEnabled, prodDate, shelfDays) = normalizeExpiry(input)

        return db.withTransaction {
            val now = System.currentTimeMillis()
            val categoryId = input.categoryId ?: catDao.findIdByName("未分类", null)

            // ---- 分支 1：追加 ----
            if (input.existingItemId != null) {
                val item = itemDao.getById(input.existingItemId)
                    ?: throw AppException(
                        ErrCode.ITEM_NOT_FOUND, "待追加的商品不存在",
                        mapOf("itemId" to input.existingItemId),
                        en = "The item to append to does not exist"
                    )
                val before = item.quantity
                val after = round2(before + input.qty)
                if (after > Constants.QTY_MAX) {
                    throw AppException(
                    ErrCode.INVALID_ARG,
                    "库存不能超过 ${Constants.QTY_MAX.toInt()}",
                    en = "Stock cannot exceed ${Constants.QTY_MAX.toInt()}"
                )
                }
                // 追加时若用户填了新保质期信息则覆盖，否则保留原值
                val newProd = if (expEnabled) prodDate else item.productionDate
                val newShelf = if (expEnabled) shelfDays else item.shelfLifeDays
                val newEnabled = if (expEnabled) true else item.expiryEnabled

                itemDao.update(
                    item.copy(
                        quantity = after,
                        expiryEnabled = newEnabled && newProd != null && newShelf != null,
                        productionDate = newProd,
                        shelfLifeDays = newShelf,
                        categoryId = input.categoryId ?: item.categoryId,
                        updatedAt = now
                    )
                )
                val recId = recordDao.insert(
                    StockRecordEntity(
                        itemId = item.id, type = RecType.IN, delta = input.qty,
                        beforeQty = before, afterQty = after, createdAt = now
                    )
                )
                saveMemo(Constants.MEMO_LAST_UNIT, item.unit, now)
                (input.categoryId ?: item.categoryId)?.let {
                    saveMemo(Constants.MEMO_LAST_CATEGORY, it.toString(), now)
                }
                return@withTransaction InboundResult(item.id, after, recId)
            }

            // ---- 冲突检测 / 复活空条目 ----
            if (!input.allowCreateSpec) {
                val dup = itemDao.findByNameSpec(name, input.spec.trim())
                if (dup != null && dup.quantity > 0) {
                    throw AppException(
                        ErrCode.DUPLICATE_ITEM,
                        "已存在同名同规格商品",
                        mapOf(
                            "existingId" to dup.id,
                            "existingQty" to dup.quantity,
                            "unit" to dup.unit
                        ),
                        en = "An item with the same name and spec already exists"
                    )
                }
                if (dup != null && dup.quantity <= 0) {
                    // 已归零的旧条目：复活并累加，避免重复行堆积
                    val before = dup.quantity
                    val after = round2(before + input.qty)
                    itemDao.update(
                        dup.copy(
                            quantity = after,
                            categoryId = categoryId ?: dup.categoryId,
                            unit = input.unit,
                            lowThreshold = round2(input.lowThreshold),
                            expiryEnabled = expEnabled,
                            productionDate = prodDate,
                            shelfLifeDays = shelfDays,
                            updatedAt = now
                        )
                    )
                    val recId = recordDao.insert(
                        StockRecordEntity(
                            itemId = dup.id, type = RecType.IN, delta = input.qty,
                            beforeQty = before, afterQty = after,
                            note = "Reused zeroed item", createdAt = now
                        )
                    )
                    saveMemo(Constants.MEMO_LAST_UNIT, input.unit, now)
                    categoryId?.let { saveMemo(Constants.MEMO_LAST_CATEGORY, it.toString(), now) }
                    return@withTransaction InboundResult(dup.id, after, recId)
                }
            }

            // ---- 新建 ----
            val newId = itemDao.insert(
                ItemEntity(
                    name = name,
                    spec = input.spec.trim(),
                    unit = input.unit,
                    quantity = round2(input.qty),
                    categoryId = categoryId,
                    barcode = input.barcode,
                    imageUri = input.imageUri,
                    lowThreshold = round2(input.lowThreshold),
                    expiryEnabled = expEnabled,
                    productionDate = prodDate,
                    shelfLifeDays = shelfDays,
                    createdAt = now,
                    updatedAt = now
                )
            )
            val recId = recordDao.insert(
                StockRecordEntity(
                    itemId = newId, type = RecType.IN, delta = input.qty,
                    beforeQty = 0.0, afterQty = round2(input.qty), createdAt = now
                )
            )
            saveMemo(Constants.MEMO_LAST_UNIT, input.unit, now)
            categoryId?.let { saveMemo(Constants.MEMO_LAST_CATEGORY, it.toString(), now) }
            InboundResult(newId, round2(input.qty), recId)
        }
    }

    /** 校验并规整保质期字段；任一缺失则整体视为未启用 */
    private fun normalizeExpiry(input: InboundInput): Triple<Boolean, Long?, Int?> {
        if (!input.expiryEnabled) return Triple(false, null, null)
        val p = input.productionDate
        val d = input.shelfLifeDays
        if (p == null || d == null) {
            throw AppException(
                ErrCode.INVALID_ARG,
                "已开启保质期，请填写生产日期与保质期天数",
                en = "Expiry tracking is on; please fill in the production date and shelf life"
            )
        }
        if (d <= 0) throw AppException(
                ErrCode.INVALID_ARG,
                "保质期天数必须大于 0",
                en = "Shelf life days must be greater than 0"
            )
        if (d > 36500) throw AppException(
                ErrCode.INVALID_ARG,
                "保质期天数过大",
                en = "Shelf life days is too large"
            )
        return Triple(true, startOfDay(p), d)
    }

    private suspend fun saveMemo(key: String, value: String, now: Long) {
        if (catDao.getMemo(key) == null) {
            catDao.insertMemo(FormMemoEntity(key, value, now))
        } else {
            catDao.updateMemo(key, value, now)
        }
    }

    // ==========================================================
    // 扫码
    // ==========================================================

    /** 按条码查已有商品；命中则 UI 进入「追加模式」 */
    suspend fun getByBarcode(barcode: String): ItemEntity? {
        val b = barcode.trim()
        if (b.isEmpty()) return null
        return itemDao.findByBarcode(b)
    }

    /**
     * 检查条码冲突：该条码是否已被其他商品占用。
     * 不阻止，只告知，由用户决定这个条码以后关联哪个商品。
     */
    suspend fun barcodeConflict(barcode: String, currentItemId: Long?): ItemEntity? {
        val b = barcode.trim()
        if (b.isEmpty()) return null
        val existing = itemDao.findByBarcode(b) ?: return null
        if (currentItemId != null && existing.id == currentItemId) return null
        return existing
    }

    /** 把条码从旧商品上解绑，让它可以给新商品使用 */
    suspend fun detachBarcode(itemId: Long) {
        db.withTransaction {
            val item = itemDao.getById(itemId) ?: return@withTransaction
            itemDao.update(item.copy(barcode = null, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * 条码冲突「改为新名称」：旧商品改名，同时更新条码记忆。
     *
     * 两步必须原子：只改名不更新条码记忆，下次扫码会带回旧名字；
     * 只更新记忆不改名，商品列表里还是旧名。任一失败整体回滚。
     */
    suspend fun renameItemAndMemo(
        itemId: Long,
        newName: String,
        barcode: String,
        categoryId: Long?
    ) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val item = itemDao.getById(itemId) ?: return@withTransaction
            itemDao.update(item.copy(name = newName, updatedAt = now))

            val memoDao = db.barcodeDao()
            if (memoDao.get(barcode) == null) {
                memoDao.insert(
                    BarcodeMemoEntity(
                        barcode = barcode,
                        name = newName,
                        categoryId = categoryId,
                        hitCount = 1,
                        source = "local",
                        updatedAt = now
                    )
                )
            } else {
                memoDao.update(
                    barcode = barcode,
                    name = newName,
                    brand = null,
                    spec = null,
                    categoryId = categoryId,
                    source = "local",
                    now = now
                )
            }
        }
    }

    /** 记住上次使用的单位（表单记忆，写操作走事务） */
    suspend fun saveLastUnit(unit: String) {
        db.withTransaction { saveMemo(Constants.MEMO_LAST_UNIT, unit, System.currentTimeMillis()) }
    }

    // ==========================================================
    // 列表内加减
    // ==========================================================

    suspend fun adjustQuantity(itemId: Long, delta: Double): Double {
        if (delta == 0.0) {
            val cur = itemDao.getById(itemId)
                ?: throw AppException(
                    ErrCode.ITEM_NOT_FOUND,
                    "商品不存在",
                    en = "Item not found"
                )
            return cur.quantity
        }
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val item = itemDao.getById(itemId)
                ?: throw AppException(
                    ErrCode.ITEM_NOT_FOUND,
                    "商品不存在",
                    en = "Item not found"
                )
            val before = item.quantity
            val after = round2(before + delta)
            if (after > Constants.QTY_MAX) {
                throw AppException(
                    ErrCode.INVALID_ARG,
                    "库存不能超过 ${Constants.QTY_MAX.toInt()}",
                    en = "Stock cannot exceed ${Constants.QTY_MAX.toInt()}"
                )
            }
            if (after < 0) {
                throw AppException(
                    ErrCode.INSUFFICIENT_STOCK, "库存不足",
                    mapOf("itemId" to itemId, "name" to item.name, "available" to before),
                    en = "Insufficient stock"
                )
            }
            itemDao.setQuantity(itemId, after, now)
            recordDao.insert(
                StockRecordEntity(
                    itemId = itemId,
                    type = if (delta > 0) RecType.IN else RecType.OUT,
                    delta = delta,
                    beforeQty = before,
                    afterQty = after,
                    reason = "MANUAL",
                    note = if (delta > 0) "Quick add" else "Quick remove",
                    createdAt = now
                )
            )
            after
        }
    }

    suspend fun deleteZeroItem(itemId: Long) {
        db.withTransaction {
            val item = itemDao.getById(itemId)
                ?: throw AppException(
                    ErrCode.ITEM_NOT_FOUND,
                    "商品不存在",
                    en = "Item not found"
                )
            if (item.quantity > 0) {
                throw AppException(
                    ErrCode.INVALID_ARG,
                    "该商品仍有库存，不能直接删除",
                    en = "This item still has stock and cannot be deleted"
                )
            }
            itemDao.softDelete(itemId, System.currentTimeMillis())
        }
    }

    suspend fun restoreItem(itemId: Long) {
        db.withTransaction {
            itemDao.restore(itemId, System.currentTimeMillis())
        }
    }

    // ==========================================================
    // 撤销
    // ==========================================================

    suspend fun revertRecord(recordId: Long) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val rec = recordDao.getRevertable(recordId)
                ?: throw AppException(
                    ErrCode.ALREADY_REVERTED,
                    "该记录已撤销或不存在",
                    en = "This record was already reverted or does not exist"
                )

            val cur = itemDao.getById(rec.itemId)
                ?: throw AppException(
                    ErrCode.ITEM_NOT_FOUND,
                    "商品已删除，无法撤销",
                    en = "The item has been deleted and cannot be reverted"
                )

            val before = cur.quantity
            val after = round2(before - rec.delta)
            if (after < 0) {
                throw AppException(
                    ErrCode.INSUFFICIENT_STOCK,
                    "库存已被消耗，无法撤销",
                    mapOf("itemId" to cur.id, "name" to cur.name, "available" to before),
                    en = "Stock has already been consumed and cannot be reverted"
                )
            }
            itemDao.setQuantity(rec.itemId, after, now)
            val compId = recordDao.insert(
                StockRecordEntity(
                    itemId = rec.itemId,
                    type = if (rec.delta > 0) RecType.OUT else RecType.IN,
                    delta = -rec.delta,
                    beforeQty = before,
                    afterQty = after,
                    batchId = rec.batchId,
                    reason = "REVERT",
                    note = "Revert #$recordId",
                    createdAt = now
                )
            )
            recordDao.markReverted(listOf(recordId), compId)
        }
    }

    // ==========================================================
    // 查询
    // ==========================================================

    /**
     * 库存列表。
     * @param categoryId 选中分类；会展开成整棵子树（含三级）
     */
    suspend fun search(kw: String, categoryId: Long?, offset: Int = 0): List<ItemWithCategory> {
        val now = System.currentTimeMillis()
        return if (categoryId == null) {
            itemDao.search(kw, 0, listOf(-1L), now, Constants.PAGE_SIZE, offset)
        } else {
            val ids = catDao.listDescendantIds(categoryId)
            itemDao.search(kw, 1, ids.ifEmpty { listOf(-1L) }, now, Constants.PAGE_SIZE, offset)
        }
    }

    suspend fun getItem(id: Long): ItemEntity? = itemDao.getById(id)

    /**
     * 「已用完」清单：数量归零但未删除的商品。
     * 与 [search] 共用筛选条件，保证切换筛选时两个分区一致。
     */
    suspend fun listUsedUp(kw: String, categoryId: Long?): List<ItemWithCategory> {
        val now = System.currentTimeMillis()
        return if (categoryId == null) {
            itemDao.listUsedUp(kw, 0, listOf(-1L), now)
        } else {
            val ids = catDao.listDescendantIds(categoryId)
            itemDao.listUsedUp(kw, 1, ids.ifEmpty { listOf(-1L) }, now)
        }
    }

    /**
     * 把已用完的商品恢复到库存：数量加回指定值，并写一条入库流水。
     * 复用 inbound 的追加分支语义，保证流水与数量一致。
     */
    suspend fun restoreUsedUp(itemId: Long, qty: Double) {
        if (qty <= 0) throw AppException(
            ErrCode.INVALID_ARG,
            "恢复数量必须大于 0",
            en = "Restore quantity must be greater than 0"
        )
        db.withTransaction {
            val now = System.currentTimeMillis()
            val item = itemDao.getById(itemId)
                ?: throw AppException(
                    ErrCode.ITEM_NOT_FOUND,
                    "商品不存在",
                    en = "Item not found"
                )
            if (item.deletedAt != null) throw AppException(
                ErrCode.ITEM_NOT_FOUND,
                "该商品已被删除，无法恢复",
                en = "This item was deleted and cannot be restored"
            )
            val before = item.quantity
            val after = round2(before + qty)
            itemDao.setQuantity(itemId, after, now)
            recordDao.insert(
                StockRecordEntity(
                    itemId = itemId,
                    type = RecType.IN,
                    delta = qty,
                    beforeQty = before,
                    afterQty = after,
                    reason = "MANUAL",
                    note = "Restored from used-up",
                    createdAt = now
                )
            )
        }
    }

    suspend fun suggestByName(kw: String): List<ItemWithCategory> =
        itemDao.suggestByName(kw, System.currentTimeMillis(), Constants.SUGGEST_LIMIT)

    suspend fun listRecentUsed(): List<ItemWithCategory> =
        itemDao.listRecentUsed(System.currentTimeMillis(), Constants.RECENT_ITEM_LIMIT)

    suspend fun listLowStock(): List<ItemEntity> = itemDao.listLowStock()
    suspend fun countLowStock(): Int = itemDao.countLowStock()
    suspend fun totalQuantity(): Double = itemDao.totalQuantity()
    suspend fun countInStock(): Int = itemDao.countInStock()

    suspend fun countExpiringSoon(): Int =
        itemDao.countExpiringSoon(System.currentTimeMillis(), Constants.EXPIRING_SOON_DAYS)

    /** 已过期商品清单：进入应用时弹窗提醒 */
    suspend fun listExpired(): List<ItemWithCategory> =
        itemDao.listExpired(System.currentTimeMillis())
}
