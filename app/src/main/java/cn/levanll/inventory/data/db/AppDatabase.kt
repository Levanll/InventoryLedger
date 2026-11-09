package cn.levanll.inventory.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.levanll.inventory.data.dao.BarcodeDao
import cn.levanll.inventory.data.dao.CategoryDao
import cn.levanll.inventory.data.dao.ItemDao
import cn.levanll.inventory.data.dao.RecordDao
import cn.levanll.inventory.data.entity.BarcodeMemoEntity
import cn.levanll.inventory.data.entity.CategoryEntity
import cn.levanll.inventory.data.entity.CategoryRuleEntity
import cn.levanll.inventory.data.entity.FormMemoEntity
import cn.levanll.inventory.data.entity.ItemEntity
import cn.levanll.inventory.data.entity.StockRecordEntity

@Database(
    entities = [
        CategoryEntity::class,
        ItemEntity::class,
        StockRecordEntity::class,
        CategoryRuleEntity::class,
        FormMemoEntity::class,
        BarcodeMemoEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao
    abstract fun recordDao(): RecordDao
    abstract fun barcodeDao(): BarcodeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "inventory.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS ux_item_barcode")
                db.execSQL("DROP TABLE IF EXISTS count_line")
                db.execSQL("DROP TABLE IF EXISTS count_session")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_item_barcode ON item(barcode)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_stock_record_created_at " +
                        "ON stock_record(created_at)"
                )
            }
        }

        /** v2 → v3：三级分类 + 保质期字段 */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE category ADD COLUMN depth INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    "UPDATE category SET depth = 2 " +
                        "WHERE parent_id IS NOT NULL AND parent_id IN " +
                        "(SELECT id FROM category WHERE parent_id IS NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_category_parent_id ON category(parent_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_category_depth ON category(depth)")

                db.execSQL("ALTER TABLE item ADD COLUMN expiry_enabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE item ADD COLUMN production_date INTEGER")
                db.execSQL("ALTER TABLE item ADD COLUMN shelf_life_days INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_item_expiry_enabled ON item(expiry_enabled)"
                )
            }
        }

        /** v3 → v4：新增条码自学习表 */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS barcode_memo (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        barcode TEXT NOT NULL,
                        name TEXT NOT NULL,
                        brand TEXT,
                        spec TEXT,
                        category_id INTEGER,
                        hit_count INTEGER NOT NULL DEFAULT 1,
                        source TEXT NOT NULL DEFAULT 'local',
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_barcode_memo_barcode " +
                        "ON barcode_memo(barcode)"
                )
            }
        }
    }
}

/**
 * 三级分类种子数据 + 名称识别规则
 *
 * 设计原则：
 *   - 分类只做「够用的骨架」，不追求穷举。用户可增删改。
 *   - 三级只出现在真正需要区分的二级下（如 护肤 → 精华/面霜/面膜），
 *     不是每个二级都硬塞三级，否则分类树会臃肿难用。
 *   - 规则尽量指向最具体的层级（三级优先），识别不准时用户可改。
 */
object SeedData {

    /** 当前种子版本，用于增量补种 */
    const val SEED_VERSION = 2

    const val KEY_SEED_VERSION = "seed_version"

    data class SeedCat(
        val name: String,
        val sort: Int,
        val children: List<SeedCat> = emptyList()
    )

    /** 分类树：一级 → 二级 → 三级 */
    val TREE: List<SeedCat> = listOf(
        SeedCat(
            "服装", 10, listOf(
                SeedCat(
                    "上装", 11, listOf(
                        SeedCat("T恤", 111), SeedCat("衬衫", 112),
                        SeedCat("卫衣", 113), SeedCat("外套", 114)
                    )
                ),
                SeedCat(
                    "下装", 12, listOf(
                        SeedCat("裤装", 121), SeedCat("裙装", 122)
                    )
                ),
                SeedCat(
                    "鞋靴", 13, listOf(
                        SeedCat("运动鞋", 131), SeedCat("皮鞋", 132), SeedCat("拖鞋", 133)
                    )
                ),
                SeedCat(
                    "配饰", 14, listOf(
                        SeedCat("帽子", 141), SeedCat("袜类", 142)
                    )
                )
            )
        ),
        SeedCat(
            "食品", 20, listOf(
                SeedCat(
                    "生鲜", 21, listOf(
                        SeedCat("蔬菜", 211), SeedCat("水果", 212),
                        SeedCat("肉禽蛋", 213), SeedCat("乳制品", 214)
                    )
                ),
                SeedCat(
                    "零食", 22, listOf(
                        SeedCat("膨化", 221), SeedCat("饼干糕点", 222), SeedCat("坚果糖果", 223)
                    )
                ),
                SeedCat(
                    "饮料", 23, listOf(
                        SeedCat("碳酸", 231), SeedCat("果汁", 232), SeedCat("茶咖", 233)
                    )
                ),
                SeedCat(
                    "调味", 24, listOf(
                        SeedCat("酱料", 241), SeedCat("粮油", 242)
                    )
                ),
                SeedCat(
                    "主食", 25, listOf(
                        SeedCat("米面", 251), SeedCat("速食", 252)
                    )
                )
            )
        ),
        SeedCat(
            "美妆个护", 30, listOf(
                SeedCat(
                    "护肤", 31, listOf(
                        SeedCat("精华", 311), SeedCat("面霜", 312), SeedCat("面膜", 313),
                        SeedCat("洁面", 314), SeedCat("水乳", 315)
                    )
                ),
                SeedCat(
                    "彩妆", 32, listOf(
                        SeedCat("口红", 321), SeedCat("底妆", 322), SeedCat("眼妆", 323)
                    )
                ),
                SeedCat(
                    "洗护", 33, listOf(
                        SeedCat("洗发", 331), SeedCat("沐浴", 332), SeedCat("口腔", 333)
                    )
                ),
                SeedCat(
                    "香氛", 34, listOf(
                        SeedCat("香水", 341), SeedCat("香膏", 342)
                    )
                )
            )
        ),
        SeedCat(
            "日用家居", 40, listOf(
                SeedCat(
                    "清洁", 41, listOf(
                        SeedCat("衣物清洁", 411), SeedCat("厨卫清洁", 412)
                    )
                ),
                SeedCat(
                    "纸品", 42, listOf(
                        SeedCat("抽纸", 421), SeedCat("卷纸", 422)
                    )
                ),
                SeedCat(
                    "收纳", 43, listOf(
                        SeedCat("收纳箱", 431), SeedCat("收纳袋", 432)
                    )
                )
            )
        ),
        SeedCat(
            "数码配件", 50, listOf(
                SeedCat(
                    "线材", 51, listOf(
                        SeedCat("数据线", 511), SeedCat("转接头", 512)
                    )
                ),
                SeedCat(
                    "充电", 52, listOf(
                        SeedCat("充电器", 521), SeedCat("移动电源", 522)
                    )
                )
            )
        ),
        SeedCat(
            "药品保健", 60, listOf(
                SeedCat(
                    "常备药", 61, listOf(
                        SeedCat("感冒药", 611), SeedCat("肠胃药", 612), SeedCat("外用药", 613)
                    )
                ),
                SeedCat(
                    "保健品", 62, listOf(
                        SeedCat("维生素", 621), SeedCat("营养品", 622)
                    )
                )
            )
        ),
        SeedCat("未分类", 999)
    )

    /**
     * 名称识别规则：token → 分类路径
     * path 用 "/" 分隔，可指向任意层级，越具体越优先。
     */
    data class SeedRule(val token: String, val path: String, val weight: Double)

    val RULES: List<SeedRule> = listOf(
        // ---- 服装 ----
        SeedRule("T恤", "服装/上装/T恤", 1.0),
        SeedRule("t恤", "服装/上装/T恤", 1.0),
        SeedRule("短袖", "服装/上装/T恤", 1.0),
        SeedRule("衬衫", "服装/上装/衬衫", 1.0),
        SeedRule("卫衣", "服装/上装/卫衣", 1.0),
        SeedRule("毛衣", "服装/上装", 1.0),
        SeedRule("外套", "服装/上装/外套", 1.0),
        SeedRule("夹克", "服装/上装/外套", 1.0),
        SeedRule("羽绒服", "服装/上装/外套", 1.0),
        SeedRule("裤", "服装/下装/裤装", 0.8),
        SeedRule("牛仔裤", "服装/下装/裤装", 1.0),
        SeedRule("裙", "服装/下装/裙装", 0.8),
        SeedRule("运动鞋", "服装/鞋靴/运动鞋", 1.0),
        SeedRule("跑鞋", "服装/鞋靴/运动鞋", 1.0),
        SeedRule("皮鞋", "服装/鞋靴/皮鞋", 1.0),
        SeedRule("拖鞋", "服装/鞋靴/拖鞋", 1.0),
        SeedRule("帽", "服装/配饰/帽子", 0.9),
        SeedRule("袜", "服装/配饰/袜类", 0.9),

        // ---- 食品 ----
        SeedRule("牛奶", "食品/生鲜/乳制品", 1.0),
        SeedRule("酸奶", "食品/生鲜/乳制品", 1.0),
        SeedRule("鸡蛋", "食品/生鲜/肉禽蛋", 1.0),
        SeedRule("蔬菜", "食品/生鲜/蔬菜", 0.9),
        SeedRule("水果", "食品/生鲜/水果", 0.9),
        SeedRule("苹果", "食品/生鲜/水果", 0.9),
        SeedRule("香蕉", "食品/生鲜/水果", 0.9),
        SeedRule("薯片", "食品/零食/膨化", 1.0),
        SeedRule("饼干", "食品/零食/饼干糕点", 1.0),
        SeedRule("面包", "食品/零食/饼干糕点", 1.0),
        SeedRule("巧克力", "食品/零食/坚果糖果", 1.0),
        SeedRule("坚果", "食品/零食/坚果糖果", 1.0),
        SeedRule("糖", "食品/零食/坚果糖果", 0.5),
        SeedRule("可乐", "食品/饮料/碳酸", 1.0),
        SeedRule("汽水", "食品/饮料/碳酸", 1.0),
        SeedRule("果汁", "食品/饮料/果汁", 1.0),
        SeedRule("咖啡", "食品/饮料/茶咖", 1.0),
        SeedRule("茶", "食品/饮料/茶咖", 0.7),
        SeedRule("酱油", "食品/调味/酱料", 1.0),
        SeedRule("醋", "食品/调味/酱料", 1.0),
        SeedRule("食用油", "食品/调味/粮油", 1.0),
        SeedRule("盐", "食品/调味/粮油", 0.9),
        SeedRule("米", "食品/主食/米面", 0.7),
        SeedRule("面粉", "食品/主食/米面", 1.0),
        SeedRule("面条", "食品/主食/米面", 1.0),
        SeedRule("方便面", "食品/主食/速食", 1.0),

        // ---- 美妆个护 ----
        SeedRule("精华", "美妆个护/护肤/精华", 1.0),
        SeedRule("面霜", "美妆个护/护肤/面霜", 1.0),
        SeedRule("乳液", "美妆个护/护肤/水乳", 1.0),
        SeedRule("爽肤水", "美妆个护/护肤/水乳", 1.0),
        SeedRule("面膜", "美妆个护/护肤/面膜", 1.0),
        SeedRule("洗面奶", "美妆个护/护肤/洁面", 1.0),
        SeedRule("洁面", "美妆个护/护肤/洁面", 1.0),
        SeedRule("口红", "美妆个护/彩妆/口红", 1.0),
        SeedRule("唇釉", "美妆个护/彩妆/口红", 1.0),
        SeedRule("粉底", "美妆个护/彩妆/底妆", 1.0),
        SeedRule("气垫", "美妆个护/彩妆/底妆", 1.0),
        SeedRule("眼影", "美妆个护/彩妆/眼妆", 1.0),
        SeedRule("睫毛膏", "美妆个护/彩妆/眼妆", 1.0),
        SeedRule("洗发水", "美妆个护/洗护/洗发", 1.0),
        SeedRule("护发素", "美妆个护/洗护/洗发", 1.0),
        SeedRule("沐浴露", "美妆个护/洗护/沐浴", 1.0),
        SeedRule("牙膏", "美妆个护/洗护/口腔", 1.0),
        SeedRule("香水", "美妆个护/香氛/香水", 1.0),

        // ---- 日用家居 ----
        SeedRule("洗衣液", "日用家居/清洁/衣物清洁", 1.0),
        SeedRule("柔顺剂", "日用家居/清洁/衣物清洁", 1.0),
        SeedRule("洗洁精", "日用家居/清洁/厨卫清洁", 1.0),
        SeedRule("抽纸", "日用家居/纸品/抽纸", 1.0),
        SeedRule("卷纸", "日用家居/纸品/卷纸", 1.0),
        SeedRule("卫生纸", "日用家居/纸品", 0.9),
        SeedRule("收纳箱", "日用家居/收纳/收纳箱", 1.0),
        SeedRule("收纳袋", "日用家居/收纳/收纳袋", 1.0),

        // ---- 数码配件 ----
        SeedRule("数据线", "数码配件/线材/数据线", 1.0),
        SeedRule("充电线", "数码配件/线材/数据线", 1.0),
        SeedRule("转接头", "数码配件/线材/转接头", 1.0),
        SeedRule("充电器", "数码配件/充电/充电器", 1.0),
        SeedRule("移动电源", "数码配件/充电/移动电源", 1.0),
        SeedRule("充电宝", "数码配件/充电/移动电源", 1.0),

        // ---- 药品保健 ----
        SeedRule("感冒药", "药品保健/常备药/感冒药", 1.0),
        SeedRule("退烧药", "药品保健/常备药/感冒药", 1.0),
        SeedRule("肠胃药", "药品保健/常备药/肠胃药", 1.0),
        SeedRule("创可贴", "药品保健/常备药/外用药", 1.0),
        SeedRule("碘伏", "药品保健/常备药/外用药", 1.0),
        SeedRule("维生素", "药品保健/保健品/维生素", 1.0),
        SeedRule("钙片", "药品保健/保健品/营养品", 1.0)
    )

    /**
     * 幂等补种：只补缺失的分类与规则，不动用户已有的数据。
     *
     * 两种场景共用：
     *   1. 全新安装（表为空）
     *   2. 从 v2 升级（已有二级分类，需要补三级）
     */
    suspend fun ensureSeeded(db: AppDatabase) {
        val catDao = db.categoryDao()
        val memo = catDao.getMemo(KEY_SEED_VERSION)
        if (memo != null && memo.toIntOrNull() == SEED_VERSION) {
            return
        }

        val now = System.currentTimeMillis()

        for ((idx, root) in TREE.withIndex()) {
            ensureCategory(catDao, root, null, 1, now, idx * 1000)
        }

        for (r in RULES) {
            val existing = catDao.findRulesByTokens(listOf(r.token))
            if (existing.isNotEmpty()) continue
            val catId = resolvePath(catDao, r.path) ?: continue
            catDao.insertRule(
                CategoryRuleEntity(
                    token = r.token,
                    categoryId = catId,
                    weight = r.weight,
                    source = "system",
                    hitCount = 1,
                    updatedAt = now
                )
            )
        }

        val prev = catDao.getMemo(KEY_SEED_VERSION)
        if (prev == null) {
            catDao.insertMemo(FormMemoEntity(KEY_SEED_VERSION, SEED_VERSION.toString(), now))
        } else {
            catDao.updateMemo(KEY_SEED_VERSION, SEED_VERSION.toString(), now)
        }
    }

    private suspend fun ensureCategory(
        dao: CategoryDao,
        node: SeedCat,
        parentId: Long?,
        depth: Int,
        now: Long,
        sort: Int
    ): Long {
        val existingId = dao.findIdByName(node.name, parentId)
        val id = existingId ?: dao.insert(
            CategoryEntity(
                name = node.name,
                parentId = parentId,
                depth = depth,
                source = "system",
                sort = node.sort,
                createdAt = now
            )
        )
        if (existingId != null) {
            dao.setDepthIfWrong(existingId, depth)
        }
        for (child in node.children) {
            ensureCategory(dao, child, id, depth + 1, now, sort)
        }
        return id
    }

    /** 把 "美妆个护/护肤/精华" 解析成分类 id，任一级缺失返回 null */
    suspend fun resolvePath(dao: CategoryDao, path: String): Long? {
        val parts = path.split("/").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        var parentId: Long? = null
        var id: Long? = null
        for (p in parts) {
            id = dao.findIdByName(p, parentId) ?: return null
            parentId = id
        }
        return id
    }
}
