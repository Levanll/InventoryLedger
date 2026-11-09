package cn.levanll.inventory.domain

import cn.levanll.inventory.data.db.AppDatabase

/**
 * 月度统计服务
 *
 * 两个视角：
 *   1. 月度库存量 —— 这个月进出多少、月末结存
 *   2. 消耗明细   —— 这个月用掉了什么，按消耗量排序
 *
 * 不用「库存快照表」而是从流水实时聚合：个人/小店量级下一次月度查询
 * 只有几十行流水，实时算完全够快；而快照表要处理补录历史、撤销等
 * 一致性，复杂度远高于收益。
 *
 * 撤销记录计入统计：用户撤销一次出库 = 出库后补回，消耗被抵消。
 */
class StatsService(private val db: AppDatabase) {

    private val recordDao get() = db.recordDao()

    /** 有流水的月份，倒序。用于月份选择器 */
    suspend fun availableMonths(): List<YearMonth> =
        recordDao.listMonthsWithData().map { YearMonth.fromCompact(it) }

    suspend fun overview(ym: YearMonth): MonthOverview {
        val s = recordDao.monthSummary(ym.startMillis(), ym.endMillis())
            ?: return MonthOverview()
        return MonthOverview(
            inQty = round2(s.inQty),
            outQty = round2(s.outQty),
            inCount = s.inCount,
            outCount = s.outCount,
            activeItems = s.activeItems
        )
    }

    /** 月度库存明细：按商品聚合，含进/出/净变化/月末结存 */
    suspend fun monthlyInventory(ym: YearMonth): List<MonthItemRow> {
        val from = ym.startMillis()
        val to = ym.endMillis()
        return recordDao.monthlyByItem(from, to).map {
            MonthItemRow(
                itemId = it.itemId,
                itemName = it.itemName,
                unit = it.unit,
                inQty = round2(it.inQty),
                outQty = round2(it.outQty),
                adjustQty = round2(it.adjustQty),
                netDelta = round2(it.netDelta),
                endQty = round2(it.endQty),
                recordCount = it.recordCount
            )
        }
    }

    /** 消耗明细：只看 OUT，按消耗量排序 */
    suspend fun monthlyConsumption(ym: YearMonth): List<MonthItemRow> {
        val from = ym.startMillis()
        val to = ym.endMillis()
        return recordDao.monthlyConsumption(from, to).map {
            MonthItemRow(
                itemId = it.itemId,
                itemName = it.itemName,
                unit = it.unit,
                inQty = round2(it.inQty),
                outQty = round2(it.outQty),
                adjustQty = round2(it.adjustQty),
                netDelta = round2(it.netDelta),
                endQty = round2(it.endQty),
                recordCount = it.recordCount
            )
        }
    }

    suspend fun detailOfItem(itemId: Long, ym: YearMonth): List<DetailRow> {
        return recordDao.monthDetailOfItem(itemId, ym.startMillis(), ym.endMillis()).map {
            DetailRow(
                id = it.id,
                itemName = it.itemName,
                unit = it.unit,
                type = it.type,
                delta = round2(it.delta),
                beforeQty = round2(it.beforeQty),
                afterQty = round2(it.afterQty),
                note = it.note,
                createdAt = it.createdAt,
                revertedBy = it.revertedBy
            )
        }
    }

    suspend fun detailOfMonth(ym: YearMonth): List<DetailRow> {
        return recordDao.monthAllDetails(ym.startMillis(), ym.endMillis()).map {
            DetailRow(
                id = it.id,
                itemName = it.itemName,
                unit = it.unit,
                type = it.type,
                delta = round2(it.delta),
                beforeQty = round2(it.beforeQty),
                afterQty = round2(it.afterQty),
                note = it.note,
                createdAt = it.createdAt,
                revertedBy = it.revertedBy
            )
        }
    }

    /**
     * 与上月对比的环比百分比。
     * 上月为 0 时返回 null —— UI 显示「—」而不是除零的 Infinity。
     */
    suspend fun consumptionMoM(ym: YearMonth): Double? {
        val cur = overview(ym).outQty
        val prev = overview(ym.prev()).outQty
        if (prev <= 0.0) return null
        return round2((cur - prev) / prev * 100.0)
    }
}
